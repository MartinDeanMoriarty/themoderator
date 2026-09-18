package com.nomoneypirate.llm.providers;

import com.google.gson.*;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.*;
import com.nomoneypirate.llm.tools.ActionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.events.ModEvents.logErrorToChat;

/**
 * Talks to the Anthropic Messages API using real "user"/"assistant" turns and native tool use.
 * Structurally close to OpenAI's shape, but with two real differences: the system prompt is its
 * own top-level {@code system} field (not a message), and there is no dedicated "tool" role -
 * a tool result is a {@code tool_result} content block inside a "user" message, just like a tool
 * call is a {@code tool_use} block inside an "assistant" message.
 */
public class AnthropicProvider implements LlmProvider {

    private static final String ANTHROPIC_URI = ConfigLoader.config.anthropicURI;
    private static final String API_KEY = ConfigLoader.config.anthropicApiKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    static ConversationHistory history = new ConversationHistory(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<LlmResult> moderateAsync(LlmClient.ModerationType type, String arg) {
        var turns = history.record(type, arg);
        boolean nativeTools = ConfigLoader.config.anthropicUseNativeTools;

        JsonObject body = new JsonObject();
        body.addProperty("model", ConfigLoader.config.anthropicModel);
        body.addProperty("max_tokens", 2048);
        body.addProperty("system", ConfigLoader.lang.systemRules);
        body.add("messages", buildMessages(turns));
        if (nativeTools) {
            body.add("tools", ActionRegistry.toAnthropicTools());
        }

        PromptLogger.logPrompt(type, GSON.toJson(body), ConfigLoader.config.anthropicModel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URI))
                .header("x-api-key", API_KEY)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Anthropic HTTP {}: {}", resp.statusCode(), resp.body());
                        Component errorMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.llmErrorMessage, ChatFormatting.BLUE, ChatFormatting.YELLOW, false, true, false);
                        if (ConfigLoader.config.logLlmErrorsToChat) logErrorToChat(errorMessage);
                        throw new RuntimeException("Anthropic HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonArray content = json.getAsJsonArray("content");
                    LlmResult result = parseContent(content);
                    PromptLogger.logPrompt(type, "Last used action/output: " + content, ConfigLoader.config.anthropicModel);
                    history.recordAssistantReply(type, result);
                    return result;
                });
    }

    private static JsonArray buildMessages(List<ConversationTurn> turns) {
        JsonArray messages = new JsonArray();
        String lastToolUseId = null;

        for (ConversationTurn turn : turns) {
            switch (turn.role()) {
                case USER -> messages.add(textMessage("user", turn.text()));
                case TOOL -> {
                    // FEEDBACK turns aren't always the result of an actual tool call - server
                    // notices like "server just (re)started" go through the same path with
                    // nothing preceding them. A tool_result needs a matching tool_use id, so
                    // without one this is really just a plain notice - send it as plain text.
                    if (lastToolUseId != null) {
                        JsonObject toolResult = new JsonObject();
                        toolResult.addProperty("type", "tool_result");
                        toolResult.addProperty("tool_use_id", lastToolUseId);
                        toolResult.addProperty("content", turn.text() == null ? "" : turn.text());
                        JsonArray blocks = new JsonArray();
                        blocks.add(toolResult);
                        JsonObject message = new JsonObject();
                        message.addProperty("role", "user");
                        message.add("content", blocks);
                        messages.add(message);
                        lastToolUseId = null; // consumed - a lone extra TOOL turn falls back to plain text too
                    } else {
                        messages.add(textMessage("user", turn.text()));
                    }
                }
                case ASSISTANT -> {
                    LlmToolCall call = turn.toolCall();
                    if (call == null) {
                        messages.add(textMessage("assistant", turn.text()));
                    } else {
                        JsonArray blocks = new JsonArray();
                        if (turn.text() != null && !turn.text().isBlank()) {
                            JsonObject text = new JsonObject();
                            text.addProperty("type", "text");
                            text.addProperty("text", turn.text());
                            blocks.add(text);
                        }
                        Map<String, String> namedArgs = ActionRegistry.toNamedArgs(call.actionName(), call.value(), call.value2(), call.value3());
                        JsonObject input = new JsonObject();
                        namedArgs.forEach(input::addProperty);
                        JsonObject toolUse = new JsonObject();
                        toolUse.addProperty("type", "tool_use");
                        // Anthropic requires an id on every tool_use block; fall back to a synthetic
                        // one on replay if this call somehow doesn't have one (e.g. a very first turn).
                        String id = call.id() != null ? call.id() : "toolu_" + Integer.toHexString(System.identityHashCode(call));
                        toolUse.addProperty("id", id);
                        toolUse.addProperty("name", call.actionName());
                        toolUse.add("input", input);
                        blocks.add(toolUse);

                        JsonObject message = new JsonObject();
                        message.addProperty("role", "assistant");
                        message.add("content", blocks);
                        messages.add(message);
                        lastToolUseId = id;
                    }
                }
            }
        }
        return messages;
    }

    private static JsonObject textMessage(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        return message;
    }

    private static LlmResult parseContent(JsonArray content) {
        StringBuilder text = new StringBuilder();
        for (JsonElement element : content) {
            JsonObject block = element.getAsJsonObject();
            String type = block.get("type").getAsString();
            if (type.equals("tool_use")) {
                String name = block.get("name").getAsString();
                String id = block.get("id").getAsString();
                Map<String, String> namedArgs = ChatMessages.argsToStringMap(block.getAsJsonObject("input"));
                String[] positional = ActionRegistry.toPositionalValues(name, namedArgs);
                String reply = text.toString().isBlank() ? null : text.toString().trim();
                return new LlmResult(reply, new LlmToolCall(id, name, positional[0], positional[1], positional[2]));
            }
            if (type.equals("text")) text.append(block.get("text").getAsString());
        }
        return LlmClient.parseFreeText(text.toString());
    }

}
