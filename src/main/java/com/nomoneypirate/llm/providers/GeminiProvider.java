package com.nomoneypirate.llm.providers;

import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import com.google.gson.*;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.events.ModEvents.logErrorToChat;

public class GeminiProvider implements LlmProvider {
    private static final String GEMINI_URI = ConfigLoader.config.geminiURI;
    private static final String API_KEY = ConfigLoader.config.geminiApiKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    // Set llm model token limit
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg) {
        // Add to context manager (cache)
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall", arg);
        // Set Action Mode
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.SUMMARY) ModEvents.actionMode = false;
        if (type == LlmClient.ModerationType.MODERATION) ModEvents.actionMode = true;
        // Build the prompt with context manager (cache)
        String prompt = contextManager.buildPrompt("recall");
        String fullPrompt = type.buildPrompt(SYSTEM_RULES, prompt);
        // Log this
        PromptLogger.logPrompt(type, fullPrompt, "Gemini");
        // Build prompt for gemini
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
        // Build http request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URI + "?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        // Send http request and get response async
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Ollama HTTP {}: {}", resp.statusCode(), resp.body());
                        Text errorMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.llmErrorMessage, Formatting.BLUE, Formatting.YELLOW, false, true, false);
                        if (ConfigLoader.config.logLlmErrorsToChat) logErrorToChat(errorMessage);
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonObject candidate = json.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject();

                    JsonObject contentObj = candidate
                            .getAsJsonObject("content");

                    JsonArray partsArray = contentObj
                            .getAsJsonArray("parts");

                    String content = partsArray
                            .get(0).getAsJsonObject()
                            .get("text").getAsString()
                            .trim();


                    // Add action to context manager (cache)
                    if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall", ConfigLoader.lang.responseContext.formatted(content));
                    // return response
                    return LlmClient.parseDecision(content);
                });
    }
}