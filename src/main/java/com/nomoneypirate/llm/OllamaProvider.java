package com.nomoneypirate.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nomoneypirate.config.ConfigLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class OllamaProvider implements LlmProvider {

    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConfigLoader.config.connectionTimeout))
            .build();
    private static final Gson GSON = new GsonBuilder().create();
    // Set model name + prompts
    private static final String MODEL = ConfigLoader.config.ollamaModel; // Ollama-Model
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    // Set llm model token limit
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // Add to context manager (cache)
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall",  arg);
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

        // Log this
        String jsonBody = GSON.toJson(body);
        String filename = type.logFilenamePrefix + (type == LlmClient.ModerationType.SUMMARY
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log"
                : ".log");
        if (type.loggingEnabled) LlmClient.logToFile(filename, "[" + timestamp + "] Request:\n" + jsonBody);

        return HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String responseText = json.get("response").getAsString().trim();
                    // Log this
                    if (type.loggingEnabled) LlmClient.logToFile(filename, "[" + timestamp + "] Response:\n" + responseText);
                    return LlmClient.parseDecision(responseText);
                });
    }

}