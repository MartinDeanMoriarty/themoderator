package com.nomoneypirate.llm;

import com.nomoneypirate.config.ConfigLoader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The running conversation, kept as structured {@link ConversationTurn}s instead of one
 * flat string - each provider maps this list directly onto its native chat-message array
 * (system/user/assistant/tool) rather than smashing everything into a single prompt blob.
 * <p>
 * Thread-safe: the server thread records incoming turns while HTTP-client threads record the
 * model's replies, so every access goes through this object's lock.
 */
public class ConversationHistory {

    // Room kept free for the model's answer (thinking models spend a lot of tokens before they answer)
    static final int ANSWER_RESERVE_TOKENS = 1024;

    private final int maxTokens;
    private final Deque<ConversationTurn> turns = new ArrayDeque<>();

    public ConversationHistory(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public synchronized void add(ConversationTurn turn) {
        turns.addLast(turn);
        trim();
    }

    public synchronized List<ConversationTurn> turns() {
        return normalize(new ArrayList<>(turns));
    }

    /**
     * Records an incoming turn and returns the full turn list to send to the model.
     * MODERATION/FEEDBACK become part of the rolling history; SUMMARY is a one-shot
     * analysis request: sent on its own (without the chat history) and never persisted, so
     * periodic summaries don't clutter the ongoing player-interaction context.
     */
    public synchronized List<ConversationTurn> record(LlmClient.ModerationType type, String arg) {
        ConversationTurn incoming = new ConversationTurn(type.role(), arg, null);
        if (type.isPersistent()) {
            add(incoming);
            return turns();
        }
        // A summary is analysed on its own: the rolling chat history would only eat into the room it needs
        return normalize(new ArrayList<>(List.of(incoming)));
    }

    /** Records the model's reply, if this turn type is meant to persist (see {@link #record}). */
    public synchronized void recordAssistantReply(LlmClient.ModerationType type, LlmResult result) {
        if (type.isPersistent()) {
            add(ConversationTurn.assistant(result.replyText(), result.toolCall()));
        }
    }

    /**
     * Makes sure every tool call is followed by a tool result. A chain often ends right after the
     * model's call (IGNORE, or an action whose feedback never came), and OpenAI, Anthropic and
     * Gemini reject a conversation where a call is directly followed by the next user message.
     * The synthetic result is only added to the outgoing list, never stored.
     */
    private static List<ConversationTurn> normalize(List<ConversationTurn> list) {
        List<ConversationTurn> out = new ArrayList<>(list.size() + 2);
        for (int i = 0; i < list.size(); i++) {
            ConversationTurn turn = list.get(i);
            out.add(turn);
            boolean unanswered = turn.role() == ConversationTurn.Role.ASSISTANT && turn.toolCall() != null
                    && !(i + 1 < list.size() && list.get(i + 1).role() == ConversationTurn.Role.TOOL);
            if (unanswered) out.add(ConversationTurn.toolResult(ConfigLoader.lang.actionAcknowledged));
        }
        return out;
    }

    private void trim() {
        // Whatever is sent besides the conversation (system prompt + action definitions) eats budget too.
        int budget = maxTokens - SystemPrompt.estimateOverheadTokens() - ANSWER_RESERVE_TOKENS;
        int total = estimateTotal();
        // Always keep the newest turn, even if it alone is over budget - a request needs something to answer.
        while (total > budget && turns.size() > 1) {
            total -= estimateTokens(turns.removeFirst());
        }
    }

    private int estimateTotal() {
        int total = 0;
        for (ConversationTurn turn : turns) total += estimateTokens(turn);
        return total;
    }

    private static int estimateTokens(ConversationTurn turn) {
        int tokens = estimateTokens(turn.text());
        LlmToolCall call = turn.toolCall();
        if (call != null) {
            tokens += estimateTokens(call.value()) + estimateTokens(call.value2()) + estimateTokens(call.value3());
        }
        return tokens;
    }

    // Simple token estimation: 1 token ≈ ¾ word ≈ 4 chars
    private static int estimateTokens(String s) {
        return s == null ? 0 : s.length() / 4;
    }
}
