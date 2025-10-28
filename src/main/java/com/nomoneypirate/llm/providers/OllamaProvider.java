package com.nomoneypirate.llm.providers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.events.ModEvents.logErrorToChat;

public class OllamaProvider implements LlmProvider {

    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConfigLoader.config.connectionTimeout))
            .build();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String MODEL = ConfigLoader.config.ollamaModel; // Ollama-Model
    private static final String SYSTEM_RULES = ConfigLoader.lang.systemRules;
    // Set llm model token limit
    static ContextManager contextManager = new ContextManager(ConfigLoader.config.tokenLimit);
    // Warm up
    private static final AtomicBoolean isWarmedUp = new AtomicBoolean(false);

    @Override
    public CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg) {
        // Add to context manager (cache)
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall",  arg);
        // Set Action Mode
        if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.SUMMARY) ModEvents.actionMode = false;
        if (type == LlmClient.ModerationType.MODERATION) ModEvents.actionMode = true;
        // Build the prompt with context manager (cache)
        String prompt = contextManager.buildPrompt("recall");
        String fullPrompt = type.buildPrompt(SYSTEM_RULES, prompt);
        // Log this
        PromptLogger.logPrompt(type, fullPrompt, ConfigLoader.config.ollamaModel);
        // Build prompt for ollama
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", fullPrompt);
        body.addProperty("stream", false);
        // Build http request
        HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(OLLAMA_URI)
        .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
        .build();
        // Send http request and get response async
        return HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        .thenApply(resp -> {
            if (resp.statusCode() / 100 != 2) {
                if (ConfigLoader.config.modLogging) LOGGER.info("Ollama HTTP {}: {}", resp.statusCode(), resp.body());
                Text errorMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.llmErrorMessage, Formatting.BLUE, Formatting.YELLOW, false, true, false);
                if (ConfigLoader.config.logLlmErrorsToChat) logErrorToChat(errorMessage);
                throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            String responseText = json.get("response").getAsString().trim();
            // Separates output for extra logging
            PromptLogger.logPrompt(type, "Last used action/output: " + responseText, ConfigLoader.config.ollamaModel);
            // Add to context manager (cache)
            if (type == LlmClient.ModerationType.FEEDBACK || type == LlmClient.ModerationType.MODERATION) contextManager.addMessage("recall", ConfigLoader.lang.responseContext.formatted(responseText));
            // return response
            return LlmClient.parseDecision(responseText);
        });
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

}