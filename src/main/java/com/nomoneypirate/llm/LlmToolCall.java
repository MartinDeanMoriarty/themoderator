package com.nomoneypirate.llm;

/**
 * A normalized action call, already resolved onto the same value/value2/value3
 * slots {@link ModerationDecision} uses - regardless of whether it came from a
 * native tool-call (named arguments, mapped via {@code ActionRegistry.toPositionalValues})
 * or the plain-JSON fallback path (which already uses value/value2/value3 directly).
 *
 * @param id optional provider-assigned id (OpenAI needs this to correlate the
 *           following tool-result message; Ollama/Gemini don't require it)
 * @param thoughtSignature opaque token Gemini 3 models attach to a function call. The API
 *           rejects the next request unless it is sent back unchanged with that call.
 */
public record LlmToolCall(String id, String actionName, String value, String value2, String value3, String thoughtSignature) {

    public LlmToolCall(String id, String actionName, String value, String value2, String value3) {
        this(id, actionName, value, value2, value3, null);
    }
}
