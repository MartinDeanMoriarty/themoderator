package com.nomoneypirate.llm.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nomoneypirate.llm.ConversationTurn;
import com.nomoneypirate.llm.LlmToolCall;
import com.nomoneypirate.llm.tools.ActionRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {role, content} style {@code messages} array shared by OpenAI Chat Completions
 * and Ollama's {@code /api/chat} - both use plain role/content messages plus an assistant
 * {@code tool_calls} array and {role:"tool", ...} result messages. The one real difference
 * between the two is how a tool call's arguments are encoded on the wire (Ollama: a JSON
 * object, OpenAI: a JSON-encoded string) - controlled here via {@code argumentsAsJsonString}.
 */
final class ChatMessages {

    private ChatMessages() {
    }

    static JsonArray build(String systemText, List<ConversationTurn> turns, boolean argumentsAsJsonString) {
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemText));

        String lastToolCallId = null;
        for (ConversationTurn turn : turns) {
            switch (turn.role()) {
                case USER -> messages.add(message("user", turn.text()));
                case TOOL -> {
                    // FEEDBACK turns aren't always the result of an actual tool call - server
                    // notices like "server just (re)started" go through the same path with
                    // nothing preceding them. A "tool" message needs a matching tool_call_id
                    // (OpenAI rejects one without it), so without one this is really just a
                    // plain notice to the model - send it as a "user" message instead.
                    if (lastToolCallId != null) {
                        JsonObject toolMessage = message("tool", turn.text());
                        toolMessage.addProperty("tool_call_id", lastToolCallId);
                        messages.add(toolMessage);
                        lastToolCallId = null; // consumed - a lone extra TOOL turn falls back to "user" too
                    } else {
                        messages.add(message("user", turn.text()));
                    }
                }
                case ASSISTANT -> {
                    LlmToolCall call = turn.toolCall();
                    if (call == null) {
                        messages.add(message("assistant", turn.text()));
                    } else {
                        messages.add(assistantToolCallMessage(turn, argumentsAsJsonString));
                        lastToolCallId = call.id();
                    }
                }
            }
        }
        return messages;
    }

    /** Reads a JSON object of tool-call arguments into a plain string map (values may be any JSON scalar). */
    static Map<String, String> argsToStringMap(JsonObject args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args != null) {
            for (var entry : args.entrySet()) {
                if (!entry.getValue().isJsonNull()) map.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return map;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        return message;
    }

    private static JsonObject assistantToolCallMessage(ConversationTurn turn, boolean argumentsAsJsonString) {
        LlmToolCall call = turn.toolCall();
        Map<String, String> namedArgs = ActionRegistry.toNamedArgs(call.actionName(), call.value(), call.value2(), call.value3());

        JsonObject arguments = new JsonObject();
        namedArgs.forEach(arguments::addProperty);

        JsonObject function = new JsonObject();
        function.addProperty("name", call.actionName());
        function.add("arguments", argumentsAsJsonString ? new JsonPrimitive(arguments.toString()) : arguments);

        JsonObject toolCall = new JsonObject();
        if (call.id() != null) toolCall.addProperty("id", call.id());
        toolCall.addProperty("type", "function");
        toolCall.add("function", function);

        JsonArray toolCalls = new JsonArray();
        toolCalls.add(toolCall);

        JsonObject assistantMessage = new JsonObject();
        assistantMessage.addProperty("role", "assistant");
        assistantMessage.addProperty("content", turn.text() == null ? "" : turn.text());
        assistantMessage.add("tool_calls", toolCalls);
        return assistantMessage;
    }
}
