package com.nomoneypirate.llm.providers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.*;
import com.nomoneypirate.llm.tools.ActionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.events.ModEvents.logErrorToChat;

/**
 * Talks to Ollama's {@code /api/chat} endpoint using real chat-role messages instead of one
 * raw text blob. By default it offers the moderator's actions as native tools (Ollama applies
 * the model's own tool-calling template and, per testing, does this even for models Ollama
 * doesn't officially tag as tool-capable). Set {@code ollamaUseNativeTools=false} in the config
 * for a model that handles plain JSON-schema-constrained output more reliably than tool syntax -
 * in that mode the action list is described in the system prompt and the response is
 * grammar-constrained via {@code format} instead.
 */
public class OllamaProvider implements LlmProvider {

    private static final URI OLLAMA_URI = URI.create(ConfigLoader.config.ollamaURI);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConfigLoader.config.connectionTimeout))
            .build();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String MODEL = ConfigLoader.config.ollamaModel;
    static ConversationHistory history = new ConversationHistory(ConfigLoader.config.tokenLimit);
    // Warm up
    private static final AtomicBoolean isWarmedUp = new AtomicBoolean(false);

    @Override
    public CompletableFuture<LlmResult> moderateAsync(LlmClient.ModerationType type, String arg) {
        var turns = history.record(type, arg);
        boolean nativeTools = ConfigLoader.config.ollamaUseNativeTools;

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("stream", false);
        body.add("messages", ChatMessages.build(systemText(nativeTools), turns, false));
        if (nativeTools) {
            body.add("tools", ActionRegistry.toFunctionTools());
        } else {
            body.add("format", ActionRegistry.toFallbackContentSchema());
        }

        PromptLogger.logPrompt(type, GSON.toJson(body), MODEL);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(OLLAMA_URI)
                .timeout(Duration.ofSeconds(ConfigLoader.config.responseTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        return HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Ollama HTTP {}: {}", resp.statusCode(), resp.body());
                        Component errorMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.llmErrorMessage, ChatFormatting.BLUE, ChatFormatting.YELLOW, false, true, false);
                        if (ConfigLoader.config.logLlmErrorsToChat) logErrorToChat(errorMessage);
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonObject message = json.getAsJsonObject("message");
                    LlmResult result = parseMessage(message);
                    PromptLogger.logPrompt(type, "Last used action/output: " + message, MODEL);
                    history.recordAssistantReply(type, result);
                    return result;
                });
    }

    private static LlmResult parseMessage(JsonObject message) {
        String content = message.has("content") && !message.get("content").isJsonNull() ? message.get("content").getAsString() : "";
        JsonArray toolCalls = message.has("tool_calls") ? message.getAsJsonArray("tool_calls") : null;

        if (toolCalls != null && !toolCalls.isEmpty()) {
            JsonObject function = toolCalls.get(0).getAsJsonObject().getAsJsonObject("function");
            String name = function.get("name").getAsString();
            Map<String, String> namedArgs = ChatMessages.argsToStringMap(function.getAsJsonObject("arguments"));
            String[] positional = ActionRegistry.toPositionalValues(name, namedArgs);
            String id = toolCalls.get(0).getAsJsonObject().has("id") ? toolCalls.get(0).getAsJsonObject().get("id").getAsString() : null;
            return new LlmResult(content.isBlank() ? null : content, new LlmToolCall(id, name, positional[0], positional[1], positional[2]));
        }

        // No structured tool call - either JSON-schema-fallback content, or the model just chatted.
        return LlmClient.parseFreeText(content);
    }

    private static String systemText(boolean nativeTools) {
        if (nativeTools) return ConfigLoader.lang.systemRules;
        return ConfigLoader.lang.systemRules + "\n\n" + ActionRegistry.toFallbackPromptText() + "\n" + ConfigLoader.lang.actionFewShotExamples;
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
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", " ");
        body.addProperty("stream", false);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(ConfigLoader.config.ollamaURI.replace("/api/chat", "/api/generate")))
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
