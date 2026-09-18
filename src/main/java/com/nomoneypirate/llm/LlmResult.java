package com.nomoneypirate.llm;

/**
 * A provider's normalized response: free chat text and/or one tool call.
 * Replaces parsing a single raw string with a regex - each provider now
 * fills this in directly from its own structured API response.
 */
public record LlmResult(String replyText, LlmToolCall toolCall) {

    public static LlmResult textOnly(String text) {
        return new LlmResult(text, null);
    }

    public boolean hasToolCall() {
        return toolCall != null;
    }

    public boolean hasText() {
        return replyText != null && !replyText.isBlank();
    }
}
