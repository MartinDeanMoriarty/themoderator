package com.nomoneypirate.llm;

import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.*;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.providers.GeminiProvider;
import com.nomoneypirate.llm.providers.OllamaProvider;
import com.nomoneypirate.llm.providers.OpenAiProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.nomoneypirate.Themoderator.LOGGER;

public final class LlmClient {

    private static final LlmProvider PROVIDER;
    private static final String SYSTEM_PROMPT = ConfigLoader.lang.systemPrompt;

    // Choose provider
    static {
        if (ConfigLoader.config.useOpenAi) {
            PROVIDER = new OpenAiProvider();
        } else if (ConfigLoader.config.useGemini) {
            PROVIDER = new GeminiProvider();
        } else {
            PROVIDER = new OllamaProvider();
        }
    }

    // We have different situations so let's react to them
    public enum ModerationType {

        MODERATION((rules, a) -> SYSTEM_PROMPT.formatted(rules, a), ConfigLoader.config.llmLogFilename, ConfigLoader.config.llmLogging),
        FEEDBACK((rules, a) -> SYSTEM_PROMPT.formatted(rules, a), ConfigLoader.config.llmLogFilename, ConfigLoader.config.llmLogging),
        SUMMARY((rules, a) -> SYSTEM_PROMPT.formatted(rules, a), ConfigLoader.config.scheduleLogFilename, ConfigLoader.config.scheduleLogging);

        public interface PromptBuilder {
            String build(String rules, String arg);
        }

        final PromptBuilder builder;
        final String logFilenamePrefix;
        final boolean loggingEnabled;

        ModerationType(PromptBuilder builder, String logFilenamePrefix, boolean loggingEnabled) {
            this.builder = builder;
            this.logFilenamePrefix = logFilenamePrefix;
            this.loggingEnabled = loggingEnabled;
        }

        public String buildPrompt(String rules, String arg) {
            return builder.build(rules, arg);
        }

    }

    public static CompletableFuture<ModerationDecision> moderateAsync(ModerationType type, String arg) {
        return PROVIDER.moderateAsync(type, arg);
    }

    // Parse response
    public static ModerationDecision parseDecision(String responseText) {
        // Get Text
        String parsedResponse = extractString(responseText);

        // Get Json
        String jsonString = extractJson(responseText);

        boolean hasText = parsedResponse != null && !parsedResponse.isBlank();
        boolean hasJson = jsonString != null && !jsonString.equals("{}");

        // Send message if text was found
        if (hasText) {
            Text message = ModDecisions.formatChatOutput(
                    ConfigLoader.config.moderatorName + ": ",
                    parsedResponse,
                    Formatting.BLUE,
                    Formatting.WHITE,
                    false, false, false
            );
            ModEvents.SERVER.getPlayerManager().broadcast(message, false);
        }

        // Do action if json was found
        if (hasJson) {
            try {
                JsonObject o = JsonParser.parseString(jsonString).getAsJsonObject();
                String actionStr = o.get("action").getAsString().toUpperCase();

                String value = o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsString() : "";
                String value2 = o.has("value2") && !o.get("value2").isJsonNull() ? o.get("value2").getAsString() : "";
                String value3 = o.has("value3") && !o.get("value3").isJsonNull() ? o.get("value3").getAsString() : "";

                ModerationDecision.Action action = switch (actionStr) {
                    case "WARN" -> ModerationDecision.Action.WARN;
                    case "KICK" -> ModerationDecision.Action.KICK;
                    case "BAN" -> ModerationDecision.Action.BAN;
                    case "PARDON" -> ModerationDecision.Action.PARDON;
                    case "WHEREIS" -> ModerationDecision.Action.WHEREIS;
                    case "WHOIS" -> ModerationDecision.Action.WHOIS;
                    case "PLAYERMEM" -> ModerationDecision.Action.PLAYERMEM;
                    case "SERVERRULES" -> ModerationDecision.Action.SERVERRULES;
                    case "SERVERINFO" -> ModerationDecision.Action.SERVERINFO;
                    case "PLAYERLIST" -> ModerationDecision.Action.PLAYERLIST;
                    case "TELEPORT" -> ModerationDecision.Action.TELEPORT;
                    case "DAMAGEPLAYER" -> ModerationDecision.Action.DAMAGEPLAYER;
                    case "CLEARINVENTORY" -> ModerationDecision.Action.CLEARINVENTORY;
                    case "KILLPLAYER" -> ModerationDecision.Action.KILLPLAYER;
                    case "GIVEPLAYER" -> ModerationDecision.Action.GIVEPLAYER;
                    case "CHANGEWEATHER" -> ModerationDecision.Action.CHANGEWEATHER;
                    case "CHANGETIME" -> ModerationDecision.Action.CHANGETIME;
                    case "LISTLOCATIONS" -> ModerationDecision.Action.LISTLOCATIONS;
                    case "GETLOCATION" -> ModerationDecision.Action.GETLOCATION;
                    case "SETLOCATION" -> ModerationDecision.Action.SETLOCATION;
                    case "REMLOCATION" -> ModerationDecision.Action.REMLOCATION;
                    case "TPTOLOCATION" -> ModerationDecision.Action.TPTOLOCATION;
                    case "SELFFEEDBACK" -> ModerationDecision.Action.SELFFEEDBACK;
                    default -> ModerationDecision.Action.IGNORE;
                };
                return new ModerationDecision(action, value, value2, value3);
            } catch (Exception e) {
                if (ConfigLoader.config.modLogging) LOGGER.info("Unclear LLM Output: {}", e.getMessage());
                // Let the LLM know there was a mistake
                String feedback = ConfigLoader.lang.feedback_02;
                return new ModerationDecision(ModerationDecision.Action.SELFFEEDBACK, feedback, "", "");
            }
        }

        // Nothing to do, fall back to IGNORE
        return new ModerationDecision(ModerationDecision.Action.IGNORE, "", "", "");
    }


    private static String extractJson(String rawText) {
        Pattern pattern = Pattern.compile("\\{.*?}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);
        // Just get the fist match
        if (matcher.find()) {
            return matcher.group();
        }
        return "{}";
    }

    private static String extractString(String rawText) {
        // Just remove all Json and return it
        String removed = rawText.replaceAll("\\{.*?}", "").trim();
        if (!removed.isEmpty()) {
            return rawText.replaceAll("\\{.*?}", "").trim();
        }
        return null;
    }

    private LlmClient() {
    }

}