package com.nomoneypirate.llm.providers;

import com.google.gson.*;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.*;
import com.nomoneypirate.llm.tools.ActionRegistry;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.nomoneypirate.Themoderator.LOGGER;

/**
 * Talks to the Gemini generateContent API using real "user"/"model" turns and native
 * function-declarations instead of stuffing the whole conversation into one text part.
 * <p>
 * Note: implemented against Google's documented function-calling shape (a "function" role
 * content carrying a functionResponse part) but not live-verified in this session - no
 * Gemini API key was available to test against. Please try it against your key and report
 * back if the tool-call round trip needs adjusting.
 */
public class GeminiProvider implements LlmProvider {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();
    static ConversationHistory history = new ConversationHistory(ConfigLoader.config.tokenLimit);

    @Override
    public CompletableFuture<LlmResult> moderateAsync(LlmClient.ModerationType type, String arg) {
        var turns = history.record(type, arg);
        boolean nativeTools = ConfigLoader.config.geminiUseNativeTools;

        JsonObject systemInstruction = new JsonObject();
        systemInstruction.add("parts", singleTextPart(SystemPrompt.base()));

        JsonObject body = new JsonObject();
        body.add("systemInstruction", systemInstruction);
        body.add("contents", buildContents(turns));
        if (nativeTools) {
            JsonObject toolSet = new JsonObject();
            toolSet.add("functionDeclarations", ActionRegistry.toGeminiFunctionDeclarations());
            JsonArray tools = new JsonArray();
            tools.add(toolSet);
            body.add("tools", tools);
        }

        PromptLogger.logPrompt(type, GSON.toJson(body.get("contents")), "Gemini");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigLoader.config.geminiURI + "?key=" + ConfigLoader.config.geminiApiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        LOGGER.warn("Gemini HTTP {}: {}", resp.statusCode(), resp.body());
                        // The error shows up in chat once, via the central handler in ModDecisions.moderateAndApply
                        throw new RuntimeException("Gemini HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonArray parts = json.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts");
                    LlmResult result = parseParts(parts);
                    PromptLogger.logPrompt(type, "Last used action/output: " + parts, "Gemini");
                    history.recordAssistantReply(type, result);
                    return result;
                });
    }

    private static JsonArray buildContents(List<ConversationTurn> turns) {
        JsonArray contents = new JsonArray();
        for (ConversationTurn turn : turns) {
            JsonObject content = new JsonObject();
            switch (turn.role()) {
                case USER -> {
                    content.addProperty("role", "user");
                    content.add("parts", singleTextPart(turn.text()));
                }
                case ASSISTANT -> {
                    content.addProperty("role", "model");
                    LlmToolCall call = turn.toolCall();
                    if (call == null) {
                        content.add("parts", singleTextPart(turn.text()));
                    } else {
                        Map<String, String> namedArgs = ActionRegistry.toNamedArgs(call.actionName(), call.value(), call.value2(), call.value3());
                        JsonObject args = new JsonObject();
                        namedArgs.forEach(args::addProperty);
                        JsonObject functionCall = new JsonObject();
                        functionCall.addProperty("name", call.actionName());
                        functionCall.add("args", args);
                        JsonObject part = new JsonObject();
                        part.add("functionCall", functionCall);
                        JsonArray parts = new JsonArray();
                        parts.add(part);
                        content.add("parts", parts);
                    }
                }
                case TOOL -> {
                    // FEEDBACK turns aren't always the result of an actual function call - server
                    // notices like "server just (re)started" go through the same path with nothing
                    // preceding them. A functionResponse needs a matching preceding functionCall to
                    // name, so without one this is really just a plain notice - send it as "user".
                    String pendingCall = lastAssistantAction(contents);
                    if (pendingCall.isEmpty()) {
                        content.addProperty("role", "user");
                        content.add("parts", singleTextPart(turn.text()));
                    } else {
                        content.addProperty("role", "function");
                        JsonObject response = new JsonObject();
                        response.addProperty("content", turn.text() == null ? "" : turn.text());
                        JsonObject functionResponse = new JsonObject();
                        functionResponse.addProperty("name", pendingCall);
                        functionResponse.add("response", response);
                        JsonObject part = new JsonObject();
                        part.add("functionResponse", functionResponse);
                        JsonArray parts = new JsonArray();
                        parts.add(part);
                        content.add("parts", parts);
                    }
                }
            }
            contents.add(content);
        }
        return contents;
    }

    /**
     * Only looks at the immediately preceding content - a functionResponse must pair with the
     * call it directly answers, not with some earlier unrelated call further back in history.
     */
    private static String lastAssistantAction(JsonArray contentsSoFar) {
        if (contentsSoFar.isEmpty()) return "";
        JsonObject last = contentsSoFar.get(contentsSoFar.size() - 1).getAsJsonObject();
        if (!"model".equals(last.get("role").getAsString())) return "";
        for (JsonElement part : last.getAsJsonArray("parts")) {
            JsonObject p = part.getAsJsonObject();
            if (p.has("functionCall")) return p.getAsJsonObject("functionCall").get("name").getAsString();
        }
        return "";
    }

    private static JsonArray singleTextPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text == null ? "" : text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        return parts;
    }

    private static LlmResult parseParts(JsonArray parts) {
        StringBuilder text = new StringBuilder();
        for (JsonElement element : parts) {
            JsonObject part = element.getAsJsonObject();
            if (part.has("functionCall")) {
                JsonObject functionCall = part.getAsJsonObject("functionCall");
                String name = functionCall.get("name").getAsString();
                Map<String, String> namedArgs = ChatMessages.argsToStringMap(functionCall.getAsJsonObject("args"));
                String[] positional = ActionRegistry.toPositionalValues(name, namedArgs);
                String reply = text.toString().isBlank() ? null : text.toString().trim();
                return new LlmResult(reply, new LlmToolCall(null, name, positional[0], positional[1], positional[2]));
            }
            if (part.has("text")) text.append(part.get("text").getAsString());
        }
        return LlmClient.parseFreeText(text.toString());
    }

}
