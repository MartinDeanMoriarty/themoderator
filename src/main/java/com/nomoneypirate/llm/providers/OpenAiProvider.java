package com.nomoneypirate.llm.providers;

import com.nomoneypirate.config.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.*;

public class OpenAiProvider implements LlmProvider {

    private static final String OPENAI_URI = ConfigLoader.config.OpenAiURI;
    private static final String API_KEY = ConfigLoader.config.openAiApiKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    // Set llm model token limit
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg) {
        // Add to context manager (cache)
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall",  arg);
        // Set Action Mode
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.SUMMARY) ModEvents.actionMode = false;
        if (type == LlmClient.ModerationType.MODERATION) ModEvents.actionMode = true;
        // Build a prompt with token limit and context manager (cache)
        String prompt = contextManager.buildPrompt("recall");
        String fullPrompt = type.buildPrompt(SYSTEM_RULES, prompt);
        // Log this
        PromptLogger.logPrompt(type, fullPrompt, ConfigLoader.config.openAiModel);
        // Build prompt for openai
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", fullPrompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", ConfigLoader.config.openAiModel); // for example: "gpt-4"
        body.add("messages", messages);
        // Build http request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URI))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        // Send http request and get response async
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("OpenAI HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String content = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString().trim();
                    // Add action to context manager (cache)
                    if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall", ConfigLoader.lang.responseContext.formatted(content));
                    // return response
                    return LlmClient.parseDecision(content);
                });
    }

}