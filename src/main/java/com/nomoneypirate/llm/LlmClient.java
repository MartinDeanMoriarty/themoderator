package com.nomoneypirate.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.providers.AnthropicProvider;
import com.nomoneypirate.llm.providers.GeminiProvider;
import com.nomoneypirate.llm.providers.OllamaProvider;
import com.nomoneypirate.llm.providers.OpenAiProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nomoneypirate.Themoderator.LOGGER;

public final class LlmClient {

    private static final LlmProvider PROVIDER;

    // Choose provider
    static {
        if (ConfigLoader.config.useOpenAi) {
            PROVIDER = new OpenAiProvider();
        } else if (ConfigLoader.config.useGemini) {
            PROVIDER = new GeminiProvider();
        } else if (ConfigLoader.config.useAnthropic) {
            PROVIDER = new AnthropicProvider();
        } else {
            PROVIDER = new OllamaProvider();
        }
    }

    // We have different situations so let's react to them
    public enum ModerationType {

        MODERATION,
        FEEDBACK,
        SUMMARY;

        /** Read live, so /moderatorreload takes effect. */
        public boolean loggingEnabled() {
            return this == SUMMARY ? ConfigLoader.config.scheduleLogging : ConfigLoader.config.llmLogging;
        }

        public String logFilenamePrefix() {
            return this == SUMMARY ? ConfigLoader.config.scheduleLogFilename : ConfigLoader.config.llmLogFilename;
        }

        /** Whether this turn becomes part of the rolling conversation history. Summaries are one-shot. */
        public boolean isPersistent() {
            return this != SUMMARY;
        }

        /** The chat role this turn's input takes on when added to history. */
        public ConversationTurn.Role role() {
            return this == FEEDBACK ? ConversationTurn.Role.TOOL : ConversationTurn.Role.USER;
        }
    }

    public static CompletableFuture<ModerationDecision> moderateAsync(ModerationType type, String arg) {
        // A moderation request starts a chain of actions during which the moderator is busy. The chain
        // ends in ModDecisions (IGNORE, error or chain limit) - feedback and summary calls are part of
        // that chain or independent of it, so they must not touch the flag.
        if (type == ModerationType.MODERATION) ModEvents.setBusy(true);
        return PROVIDER.moderateAsync(type, arg)
                .thenApply(LlmClient::toDecision)
                .whenComplete((decision, error) -> {
                    // A failed call (timeout, HTTP error, ...) never reaches the code that would end the chain.
                    if (error != null && type != ModerationType.SUMMARY) ModEvents.setBusy(false);
                });
    }

    /** Turns a provider's normalized result into a {@link ModerationDecision}, broadcasting any chat reply. */
    private static ModerationDecision toDecision(LlmResult result) {
        MinecraftServer server = ModEvents.SERVER;
        // A model that writes its action into the chat ("KICK Eve ...", "GIVEPLAYER{...}") instead of calling it
        // must not show that to the players, and the text is never executed - it goes back as a usage error.
        if (!result.hasToolCall() && result.hasText() && LEAKED_ACTION.matcher(result.replyText()).matches()) {
            LOGGER.warn("The model wrote an action as chat text instead of calling it: '{}'", result.replyText());
            return new ModerationDecision(ModerationDecision.Action.SELFFEEDBACK, ConfigLoader.lang.feedback_02, "", "", false);
        }
        if (result.hasText() && server != null) {
            Component message = com.nomoneypirate.actions.ModDecisions.formatChatOutput(
                    ConfigLoader.config.moderatorName + ": ",
                    result.replyText(),
                    ChatFormatting.BLUE, ChatFormatting.WHITE,
                    false, false, false
            );
            // This runs on the HTTP client's thread - the player list belongs to the server thread.
            server.execute(() -> server.getPlayerList().broadcastSystemMessage(message, false));
        }

        if (!result.hasToolCall()) {
            return new ModerationDecision(ModerationDecision.Action.IGNORE, "", "", "", result.hasText());
        }

        LlmToolCall call = result.toolCall();
        try {
            ModerationDecision.Action action = ModerationDecision.Action.valueOf(call.actionName().toUpperCase());
            return new ModerationDecision(action,
                    nullToEmpty(call.value()), nullToEmpty(call.value2()), nullToEmpty(call.value3()), result.hasText());
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.warn("Unclear LLM output: unknown action '{}'", call.actionName());
            return new ModerationDecision(ModerationDecision.Action.SELFFEEDBACK, ConfigLoader.lang.feedback_02, "", "", result.hasText());
        }
    }

    // "KICK Bob ...", "GIVEPLAYER{...}" or "... [SERVERRULES]" - an action name in capitals where a sentence should be
    private static final Pattern LEAKED_ACTION = Pattern.compile(
            "(?s)^\\s*\\[?(?:" + actionNames() + ")\\b.*|.*\\[(?:" + actionNames() + ")\\].*");

    private static String actionNames() {
        return java.util.Arrays.stream(ModerationDecision.Action.values())
                .filter(a -> a != ModerationDecision.Action.IGNORE && a != ModerationDecision.Action.SELFFEEDBACK)
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Finds the first top-level {@code {...}} JSON object in free text and parses it, tracking
     * string/escape state properly - unlike a naive regex this handles nested objects and braces
     * that appear inside string values (e.g. a KICK reason containing "}").
     */
    public static JsonObject extractJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return JsonParser.parseString(text.substring(start, i + 1)).getAsJsonObject();
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Parses a plain-text model response into a normalized result. Used for the JSON-fallback
     * path (where the whole response is expected to be one such object, e.g. via Ollama's
     * schema-constrained {@code format}) and defensively for native tool-calling models that
     * occasionally answer in prose instead of calling a tool.
     */
    // Case-sensitive on purpose: a model writing the action name uses capitals, ordinary prose ("just ignore it") doesn't
    private static final Pattern IGNORE_AS_TEXT = Pattern.compile("^(.*?)(?:^|\\s|[\\[(])IGNORE[\\])\\s.]*$", Pattern.DOTALL);

    public static LlmResult parseFreeText(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        JsonObject json = extractJsonObject(text);
        if (json == null || !json.has("action")) {
            // Some models write the action name into the chat instead of calling it ("... Viel Spaß! IGNORE") -
            // that must not end up in front of the players, it means "nothing to do".
            Matcher ignore = IGNORE_AS_TEXT.matcher(text);
            if (ignore.matches()) {
                String rest = ignore.group(1).trim();
                return new LlmResult(rest.isEmpty() ? null : rest, new LlmToolCall(null, "IGNORE", null, null, null));
            }
            return LlmResult.textOnly(text);
        }

        String reply = json.has("reply") && !json.get("reply").isJsonNull() ? json.get("reply").getAsString() : null;
        if (reply == null) {
            // Legacy shape without a "reply" field: whatever surrounds the JSON object is the reply.
            String outside = (text.substring(0, text.indexOf('{')) + text.substring(text.lastIndexOf('}') + 1)).trim();
            reply = outside.isEmpty() ? null : outside;
        }

        String action = json.get("action").getAsString();
        String value = stringOrNull(json, "value");
        String value2 = stringOrNull(json, "value2");
        String value3 = stringOrNull(json, "value3");
        return new LlmResult(reply, new LlmToolCall(null, action, value, value2, value3));
    }

    private static String stringOrNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private LlmClient() {
    }

}
