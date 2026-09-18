package com.nomoneypirate.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.providers.AnthropicProvider;
import com.nomoneypirate.llm.providers.GeminiProvider;
import com.nomoneypirate.llm.providers.OllamaProvider;
import com.nomoneypirate.llm.providers.OpenAiProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.concurrent.CompletableFuture;

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

        MODERATION(ConfigLoader.config.llmLogFilename, ConfigLoader.config.llmLogging),
        FEEDBACK(ConfigLoader.config.llmLogFilename, ConfigLoader.config.llmLogging),
        SUMMARY(ConfigLoader.config.scheduleLogFilename, ConfigLoader.config.scheduleLogging);

        public final String logFilenamePrefix;
        public final boolean loggingEnabled;

        ModerationType(String logFilenamePrefix, boolean loggingEnabled) {
            this.logFilenamePrefix = logFilenamePrefix;
            this.loggingEnabled = loggingEnabled;
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
        // Set Action Mode - a moderation request expects the LLM to look at fresh input,
        // feedback/summary calls are just reporting back what already happened.
        ModEvents.actionMode = type == ModerationType.MODERATION;
        return PROVIDER.moderateAsync(type, arg).thenApply(LlmClient::toDecision);
    }

    /** Turns a provider's normalized result into a {@link ModerationDecision}, broadcasting any chat reply. */
    private static ModerationDecision toDecision(LlmResult result) {
        if (result.hasText()) {
            Component message = com.nomoneypirate.actions.ModDecisions.formatChatOutput(
                    ConfigLoader.config.moderatorName + ": ",
                    result.replyText(),
                    ChatFormatting.BLUE, ChatFormatting.WHITE,
                    false, false, false
            );
            ModEvents.SERVER.getPlayerList().broadcastSystemMessage(message, false);
        }

        if (!result.hasToolCall()) {
            return new ModerationDecision(ModerationDecision.Action.IGNORE, "", "", "");
        }

        LlmToolCall call = result.toolCall();
        try {
            ModerationDecision.Action action = ModerationDecision.Action.valueOf(call.actionName().toUpperCase());
            return new ModerationDecision(action,
                    nullToEmpty(call.value()), nullToEmpty(call.value2()), nullToEmpty(call.value3()));
        } catch (IllegalArgumentException | NullPointerException e) {
            if (ConfigLoader.config.modLogging) LOGGER.info("Unclear LLM Output: unknown action '{}'", call.actionName());
            return new ModerationDecision(ModerationDecision.Action.SELFFEEDBACK, ConfigLoader.lang.feedback_02, "", "");
        }
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
    public static LlmResult parseFreeText(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        JsonObject json = extractJsonObject(text);
        if (json == null || !json.has("action")) {
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
