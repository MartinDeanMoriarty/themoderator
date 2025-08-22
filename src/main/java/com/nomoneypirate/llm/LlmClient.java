package com.nomoneypirate.llm;

import com.google.gson.*;
import com.nomoneypirate.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import static com.nomoneypirate.Themoderator.LOGGER;

public final class LlmClient {
    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConfigLoader.config.connectionTimeout))
            .build();
    private static final Gson GSON = new GsonBuilder().create();

    // Set model name + prompts
    private static final String MODEL = ConfigLoader.config.model; // Ollama-Model
    private static final String SYSTEM_RULES = ConfigLoader.config.systemRules;
    private static final String SYSTEM_PROMPT = ConfigLoader.config.systemPrompt;
    private static final String FEEDBACK_PROMPT = ConfigLoader.config.feedbackPROMPT;

    // To LLM
    // Moderation
    public static CompletableFuture<ModerationDecision> moderateAsync(String playerName, String message) {
        String prompt = SYSTEM_PROMPT.formatted(SYSTEM_RULES, playerName, message);

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

        // Get response
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    // Ollama /api/generate (stream=false) -> {"model":"...","created_at":"...","response":"...","done":true,...}
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    return parseDecision(responseText);
                });
    }

    // Feedback
    public static CompletableFuture<ModerationDecision> sendFeedbackAsync(String feedback) {
        String prompt = FEEDBACK_PROMPT.formatted(SYSTEM_RULES, feedback);

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

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    return parseDecision(responseText);
                });
    }

    // Parse response
    private static ModerationDecision parseDecision(String responseText) {
        String value = "";
        String value2 = "";
        try {
            JsonObject o = JsonParser.parseString(responseText).getAsJsonObject();
            String actionStr = o.get("action").getAsString().toUpperCase();

            // Does value exist?
            if (o.has("value") && !o.get("value").isJsonNull()) {
                value = o.get("value").getAsString();
            }
            // Does value2 exist?
            if (o.has("value2") && !o.get("value2").isJsonNull()) {
                value2 = o.get("value2").getAsString();
            }

            ModerationDecision.Action action = switch (actionStr) {
                case "CHAT" -> ModerationDecision.Action.CHAT;
                case "WHISPER" -> ModerationDecision.Action.WHISPER;
                case "WARN" -> ModerationDecision.Action.WARN;
                case "KICK" -> ModerationDecision.Action.KICK;
                case "BAN" -> ModerationDecision.Action.BAN;
                case "IGNORE" -> ModerationDecision.Action.IGNORE;
                case "PLAYERLIST" -> ModerationDecision.Action.PLAYERLIST;
                case "SPAWNAVATAR" -> ModerationDecision.Action.SPAWNAVATAR;
                case "DESPAWNAVATAR" -> ModerationDecision.Action.DESPAWNAVATAR;
                default -> ModerationDecision.Action.IGNORE;
            };
            return new ModerationDecision(action, value, value2);
        } catch (Exception e) {
            // If the LLM does not strictly deliver JSON, fallback to IGNORE
            // and log it just in case we want to debug what the llm response was
            LOGGER.info("[themoderator] {}",  "Unclear output from llm model.");
            return new ModerationDecision(ModerationDecision.Action.IGNORE, "Unclear output from llm model.","");

        }
    }

    private LlmClient() {}
}