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
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    private static final String SYSTEM_PROMPT = ConfigLoader.lang.systemPrompt;
    private static final String FEEDBACK_PROMPT = ConfigLoader.lang.feedbackPROMPT;
    private static final String SUMMARY_PROMPT = ConfigLoader.lang.summaryPROMPT;

    // To LLM
    // Moderation
    public static CompletableFuture<ModerationDecision> moderateAsync(String playerName, String message) {
        String prompt = SYSTEM_PROMPT.formatted(SYSTEM_RULES, playerName, message);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // build json
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        // Make request
        HttpRequest req = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        // Input logging
        String jsonBody = GSON.toJson(body);
        String filename = ConfigLoader.config.llmLogFilename+".log";
        if (ConfigLoader.config.llmLogging) logToFile(filename, "[" + timestamp + "] Request:\n" + jsonBody);

        // Get response
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    // Ollama /api/generate (stream=false) -> {"model":"...","created_at":"...","response":"...","done":true,...}
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    // Output logging
                    if (ConfigLoader.config.llmLogging) logToFile(filename, "[" + timestamp + "] Response:\n" + responseText);
                    return parseDecision(responseText);
                });
    }

    // Feedback to LLM
    public static CompletableFuture<ModerationDecision> sendFeedbackAsync(String feedback) {
        String prompt = FEEDBACK_PROMPT.formatted(SYSTEM_RULES, feedback);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        // Input logging
        String jsonBody = GSON.toJson(body);
        String filename = ConfigLoader.config.llmLogFilename+".log";
        if (ConfigLoader.config.llmLogging) logToFile(filename, "[" + timestamp + "] Feedback Request:\n" + jsonBody);

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    // Output logging

                    if (ConfigLoader.config.llmLogging) logToFile(filename, "[" + timestamp + "] Feedback Response:\n" + responseText);
                    return parseDecision(responseText);
                });
    }

    // Summary to LLM
    public static CompletableFuture<ModerationDecision> sendSummaryAsync(String feedback) {
        String prompt = SUMMARY_PROMPT.formatted(SYSTEM_RULES, feedback);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        String jsonBody = GSON.toJson(body);

        // Input logging
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String logTimeStamp = LocalDateTime.now().format(formatter);
        String filename = ConfigLoader.config.scheduleLogFilename + logTimeStamp + ".log";
        if (ConfigLoader.config.scheduleLogging) logToFile(filename, "Summary:\n" + jsonBody);

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    // Output logging

                    if (ConfigLoader.config.llmLogging) logToFile(filename, "[" + timestamp + "] Feedback Response:\n" + responseText);
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
                case "STOP" -> ModerationDecision.Action.STOP;
                default -> ModerationDecision.Action.IGNORE;
            };
            return new ModerationDecision(action, value, value2, value3);
        } catch (Exception e) {
            // If the LLM does not strictly deliver JSON
            LOGGER.info("Unclear output from llm model.");
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

    public static void logToFile(String filename, String content) {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        Path logFile = logDir.resolve(filename);
        try {
            Files.createDirectories(logDir);
            Files.writeString(logFile, content + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Logfile save error!");
        }
    }

    private LlmClient() {}
}