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
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Gson GSON = new GsonBuilder().create();

    // Set model name + prompts
    private static final String MODEL = ConfigLoader.config.model; // Ollama-Model
    private static final String SYSTEM_RULES = ConfigLoader.config.systemRules;

    public static CompletableFuture<ModerationDecision> moderateAsync(String playerName, String message) {
        String prompt = ConfigLoader.config.systemPrompt.formatted(SYSTEM_RULES, playerName, message);

        // build json
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        // Make request
        HttpRequest req = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(8))
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

    // Parse response
    private static ModerationDecision parseDecision(String responseText) {
        String text = "";
        String playerName = "";
        try {
            JsonObject o = JsonParser.parseString(responseText).getAsJsonObject();
            String actionStr = o.get("action").getAsString().toUpperCase();

            // Does text exist?
            if (o.has("text") && !o.get("text").isJsonNull()) {
                text = o.get("text").getAsString();
            }
            // Does playerName exist?
            if (o.has("playerName") && !o.get("playerName").isJsonNull()) {
                playerName = o.get("playerName").getAsString();
            }

            ModerationDecision.Action action = switch (actionStr) {
                case "CHAT" -> ModerationDecision.Action.CHAT;
                case "WHISPER" -> ModerationDecision.Action.WHISPER;
                case "WARN" -> ModerationDecision.Action.WARN;
                case "KICK" -> ModerationDecision.Action.KICK;
                case "IGNORE" -> ModerationDecision.Action.IGNORE;
                default -> ModerationDecision.Action.CHAT;
            };
            return new ModerationDecision(action, playerName, text);
        } catch (Exception e) {
            // If the LLM does not strictly deliver JSON, fallback to IGNORE
            // and log it just in case we want to debug what the llm response was
            LOGGER.info("[themoderator]{}", text);
            return new ModerationDecision(ModerationDecision.Action.IGNORE, playerName,"Unclear output from llm model.");

        }
    }

    private LlmClient() {}
}
