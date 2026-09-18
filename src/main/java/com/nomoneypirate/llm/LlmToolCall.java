package com.nomoneypirate.llm;

/**
 * A normalized action call, already resolved onto the same value/value2/value3
 * slots {@link ModerationDecision} uses - regardless of whether it came from a
 * native tool-call (named arguments, mapped via {@code ActionRegistry.toPositionalValues})
 * or the plain-JSON fallback path (which already uses value/value2/value3 directly).
 *
 * @param id optional provider-assigned id (OpenAI needs this to correlate the
 *           following tool-result message; Ollama/Gemini don't require it)
 */
public record LlmToolCall(String id, String actionName, String value, String value2, String value3) {
}
