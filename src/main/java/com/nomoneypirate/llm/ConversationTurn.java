package com.nomoneypirate.llm;

/**
 * One turn in the running conversation, tagged with a real chat role instead of
 * a hand-written text label like "[Anfrage]". Providers map this list directly
 * onto their native chat-message array (system/user/assistant/tool).
 */
public record ConversationTurn(Role role, String text, LlmToolCall toolCall) {

    public enum Role {USER, ASSISTANT, TOOL}

    public static ConversationTurn user(String text) {
        return new ConversationTurn(Role.USER, text, null);
    }

    public static ConversationTurn toolResult(String text) {
        return new ConversationTurn(Role.TOOL, text, null);
    }

    public static ConversationTurn assistant(String text, LlmToolCall toolCall) {
        return new ConversationTurn(Role.ASSISTANT, text, toolCall);
    }
}
