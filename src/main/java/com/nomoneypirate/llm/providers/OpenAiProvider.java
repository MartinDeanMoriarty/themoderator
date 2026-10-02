package com.nomoneypirate.llm.providers;

import com.google.gson.*;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.*;
import com.nomoneypirate.llm.tools.ActionRegistry;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.nomoneypirate.Themoderator.LOGGER;

/**
 * Talks to the OpenAI Chat Completions API using real chat-role messages and native
 * function/tool calling instead of stuffing the whole conversation into one "user" message.
 */
public class OpenAiProvider implements LlmProvider {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    static ConversationHistory history = new ConversationHistory(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<LlmResult> moderateAsync(LlmClient.ModerationType type, String arg) {
        var turns = history.record(type, arg);
        boolean nativeTools = ConfigLoader.config.openAiUseNativeTools;

        JsonObject body = new JsonObject();
        body.addProperty("model", ConfigLoader.config.openAiModel); // for example: "gpt-4.1"
        body.add("messages", ChatMessages.build(SystemPrompt.base(), turns, true));
        if (nativeTools) {
            body.add("tools", ActionRegistry.toFunctionTools());
        }

        PromptLogger.logPrompt(type, GSON.toJson(body.get("messages")), ConfigLoader.config.openAiModel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigLoader.config.OpenAiURI))
                .header("Authorization", "Bearer " + ConfigLoader.config.openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        LOGGER.warn("OpenAI HTTP {}: {}", resp.statusCode(), resp.body());
                        // The error shows up in chat once, via the central handler in ModDecisions.moderateAndApply
                        throw new RuntimeException("OpenAI HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                    LlmResult result = parseMessage(message);
                    PromptLogger.logPrompt(type, "Last used action/output: " + message, ConfigLoader.config.openAiModel);
                    history.recordAssistantReply(type, result);
                    return result;
                });
    }

    private static LlmResult parseMessage(JsonObject message) {
        String content = message.has("content") && !message.get("content").isJsonNull() ? message.get("content").getAsString() : "";
        JsonArray toolCalls = message.has("tool_calls") ? message.getAsJsonArray("tool_calls") : null;

        if (toolCalls != null && !toolCalls.isEmpty()) {
            JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
            JsonObject function = toolCall.getAsJsonObject("function");
            String name = function.get("name").getAsString();
            // OpenAI encodes arguments as a JSON string, not an object.
            JsonObject args = JsonParser.parseString(function.get("arguments").getAsString()).getAsJsonObject();
            Map<String, String> namedArgs = ChatMessages.argsToStringMap(args);
            String[] positional = ActionRegistry.toPositionalValues(name, namedArgs);
            String id = toolCall.has("id") ? toolCall.get("id").getAsString() : null;
            return new LlmResult(content.isBlank() ? null : content, new LlmToolCall(id, name, positional[0], positional[1], positional[2]));
        }

        return LlmClient.parseFreeText(content);
    }

}
