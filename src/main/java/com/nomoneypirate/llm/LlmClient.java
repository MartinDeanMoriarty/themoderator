package com.nomoneypirate.llm;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

public final class LlmClient {

    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConfigLoader.config.connectionTimeout))
            .build();
    private static final Gson GSON = new GsonBuilder().create();
    // Set model name + prompts
    private static final String MODEL = ConfigLoader.config.model; // Ollama-Model
    private static final String SYSTEM_PROMPT = ConfigLoader.lang.systemPrompt;
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    // Set llm model token limit
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);
    // Make a hard decision
    private static final AtomicBoolean isWarmedUp = new AtomicBoolean(false);

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

    // To LLM
    public static CompletableFuture<ModerationDecision> moderateAsync(ModerationType type, String arg) {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // Add to context manager (cache)
        if (type == ModerationType.FEEDBACK || type == ModerationType.MODERATION) contextManager.addMessage("recall",  arg);
        // Build a prompt with token limit and context manager (cache)
        String prompt = contextManager.buildPrompt("recall");
        String fullPrompt = type.buildPrompt(SYSTEM_RULES, prompt);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", fullPrompt);
        body.addProperty("stream", false);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        String jsonBody = GSON.toJson(body);
        String filename = type.logFilenamePrefix + (type == ModerationType.SUMMARY
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log"
                : ".log");

        if (type.loggingEnabled) logToFile(filename, "[" + timestamp + "] Request:\n" + jsonBody);

        return HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    if (type.loggingEnabled) logToFile(filename, "[" + timestamp + "] Response:\n" + responseText);
                    return parseDecision(responseText);
                });

    }

    // Parse response
    private static ModerationDecision parseDecision(String responseText) {
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
                case "FEEDBACK" -> ModerationDecision.Action.FEEDBACK;
                case "SERVERRULES" -> ModerationDecision.Action.SERVERRULES;
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

    // Used at mod init so the llm has a chance to be ready when the world is loaded
    public static void warmupModel() {
        if (isWarmedUp.get()) {
            CompletableFuture.completedFuture(null);
            return;
        }
        isWarmedUp.set(true);
        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("Model warm-up!");
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
                if (ConfigLoader.config.modLogging) LOGGER.warn("LLM Warmup failed: {}", e.getMessage());
            }
        });
    }

    public static void logToFile(String filename, String content) {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        Path logFile = logDir.resolve(filename);
        try {
            Files.createDirectories(logDir);
            Files.writeString(logFile, content + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.warn("Logfile save error!");
        }
    }

    private LlmClient() {}
}