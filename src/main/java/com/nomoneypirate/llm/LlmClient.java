package com.nomoneypirate.llm;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.*;

public final class LlmClient {

    private static final LlmProvider PROVIDER;
    private static final String SYSTEM_PROMPT = ConfigLoader.lang.systemPrompt;
    private static final Gson GSON = new GsonBuilder().create();
    // Ollama warm up
    private static final AtomicBoolean isWarmedUp = new AtomicBoolean(false);
    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final String MODEL = ConfigLoader.config.ollamaModel;

    // Choose provider
    static {
        if (ConfigLoader.config.useOpenAi) {
            PROVIDER = new OpenAiProvider();
        }
        else if (ConfigLoader.config.useGemini) {
            PROVIDER = new GeminiProvider();
        }
        else {
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
    static ModerationDecision parseDecision(String responseText) {
        String value = "";
        String value2 = "";
        String value3 = "";
        try {
            // Pre parse -> See extractJson()
            JsonObject o = JsonParser.parseString(extractJson(responseText)).getAsJsonObject();
            String actionStr = o.get("action").getAsString().toUpperCase();

            // Does value exist?
            if (o.has("value") && !o.get("value").isJsonNull()) {
                value = o.get("value").getAsString();
            }
            // Does value2 exist?
            if (o.has("value2") && !o.get("value2").isJsonNull()) {
                value2 = o.get("value2").getAsString();
            }
            // Does value3 exist?
            if (o.has("value3") && !o.get("value3").isJsonNull()) {
                value3 = o.get("value3").getAsString();
            }

            ModerationDecision.Action action = switch (actionStr) {
                case "CHAT" -> ModerationDecision.Action.CHAT;
                case "WHISPER" -> ModerationDecision.Action.WHISPER;
                case "WARN" -> ModerationDecision.Action.WARN;
                case "KICK" -> ModerationDecision.Action.KICK;
                case "BAN" -> ModerationDecision.Action.BAN;
                case "WHEREIS" -> ModerationDecision.Action.WHEREIS;
                case "WHOIS" -> ModerationDecision.Action.WHOIS;
                case "PLAYERMEM" -> ModerationDecision.Action.PLAYERMEM;
                case "FEEDBACK" -> ModerationDecision.Action.FEEDBACK;
                case "SERVERRULES" -> ModerationDecision.Action.SERVERRULES;
                case "ACTIONEXAMPLES" -> ModerationDecision.Action.ACTIONEXAMPLES;
                case "IGNORE" -> ModerationDecision.Action.IGNORE;
                case "PLAYERLIST" -> ModerationDecision.Action.PLAYERLIST;
                case "TELEPORTPLAYER" -> ModerationDecision.Action.TELEPORTPLAYER;
                case "TELEPORTPOSITION" -> ModerationDecision.Action.TELEPORTPOSITION;
                case "SPAWNAVATAR" -> ModerationDecision.Action.SPAWNAVATAR;
                case "DESPAWNAVATAR" -> ModerationDecision.Action.DESPAWNAVATAR;
                case "FOLLOWPLAYER" -> ModerationDecision.Action.FOLLOWPLAYER;
                case "LOOKATPLAYER" -> ModerationDecision.Action.LOOKATPLAYER;
                case "GOTOPLAYER" -> ModerationDecision.Action.GOTOPLAYER;
                case "GOTOPOSITION" -> ModerationDecision.Action.GOTOPOSITION;
                case "MOVEAROUND" -> ModerationDecision.Action.MOVEAROUND;
                case "DAMAGEPLAYER" -> ModerationDecision.Action.DAMAGEPLAYER;
                case "CLEARINVENTORY" -> ModerationDecision.Action.CLEARINVENTORY;
                case "KILLPLAYER" -> ModerationDecision.Action.KILLPLAYER;
                case "GIVEPLAYER" -> ModerationDecision.Action.GIVEPLAYER;
                case "CHANGEWEATHER" -> ModerationDecision.Action.CHANGEWEATHER;
                case "CHANGETIME" -> ModerationDecision.Action.CHANGETIME;
                case "SLEEP" -> ModerationDecision.Action.SLEEP;
                case "LISTLOCATIONS" -> ModerationDecision.Action.LISTLOCATIONS;
                case "GETLOCATION" -> ModerationDecision.Action.GETLOCATION;
                case "SETLOCATION" -> ModerationDecision.Action.SETLOCATION;
                case "REMLOCATION" -> ModerationDecision.Action.REMLOCATION;
                case "STOPACTION" -> ModerationDecision.Action.STOPACTION;
                case "STOPCHAIN" -> ModerationDecision.Action.STOPCHAIN;
                default -> ModerationDecision.Action.IGNORE;
            };
            return new ModerationDecision(action, value, value2, value3);
        } catch (Exception e) {
            // If the LLM does not strictly deliver JSON
            if (ConfigLoader.config.modLogging) LOGGER.info("Unclear output from llm model.");
            // Feedback
            String feedback = ConfigLoader.lang.feedback_12;
            return new ModerationDecision(ModerationDecision.Action.FEEDBACK, feedback,"", "");
        }
    }
    // Pre parse
    private static String extractJson(String rawText) {
        Pattern pattern = Pattern.compile("\\{.*?}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);
        if (matcher.find()) {
            return matcher.group();
        }
        return "{}";
    }

    // Used at mod init so ollama has a chance to be ready when the world is loaded
    public static void warmupModel() {
        if (isWarmedUp.get()) {
            CompletableFuture.completedFuture(null);
            return;
        }
        isWarmedUp.set(true);
        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("Ollama warm-up!");
        // An empty prompt should just load a model
        String prompt = " ";
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(OLLAMA_URI)
                        .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                        .build();
                HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                if (ConfigLoader.config.modLogging) LOGGER.warn("Ollama Warmup failed: {}", e.getMessage());
            }
        });
    }

    private LlmClient() {}

}