package com.nomoneypirate.llm;

import com.nomoneypirate.config.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import com.google.gson.*;

public class GeminiProvider implements LlmProvider {
    private static final String GEMINI_URI = ConfigLoader.config.geminiApiUri;
    private static final String API_KEY = ConfigLoader.config.geminiApiKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION)
            contextManager.addMessage("recall", arg);

        String prompt = contextManager.buildPrompt("recall");
        String fullPrompt = type.buildPrompt(SYSTEM_RULES, prompt);

        JsonObject part = new JsonObject();
        part.addProperty("text", fullPrompt);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject message = new JsonObject();
        message.add("parts", parts);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.add("contents", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URI + "?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        String jsonBody = GSON.toJson(body);
        String filename = type.logFilenamePrefix + (type == LlmClient.ModerationType.SUMMARY
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log"
                : ".log");
        if (type.loggingEnabled) LlmClient.logToFile(filename, "[" + timestamp + "] Request:\n" + jsonBody);

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Gemini HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String content = json.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonArray("content")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString().trim();

                    if (type.loggingEnabled) LlmClient.logToFile(filename, "[" + timestamp + "] Response:\n" + content);
                    return LlmClient.parseDecision(content);
                });
    }
}