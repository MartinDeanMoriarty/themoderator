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
 * Trimming behaviour (drop the oldest turns once a rough token budget is exceeded) is
 * unchanged from before.
 */
public class ConversationHistory {

    private final int maxTokens;
    private final Deque<ConversationTurn> turns = new ArrayDeque<>();

    public ConversationHistory(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void add(ConversationTurn turn) {
        turns.addLast(turn);
        trim();
    }

    public List<ConversationTurn> turns() {
        return List.copyOf(turns);
    }

    /**
     * Records an incoming turn and returns the full turn list to send to the model.
     * MODERATION/FEEDBACK become part of the rolling history; SUMMARY is a one-shot
     * analysis request that is visible for this call only and never persisted, so
     * periodic summaries don't clutter the ongoing player-interaction context.
     */
    public List<ConversationTurn> record(LlmClient.ModerationType type, String arg) {
        ConversationTurn incoming = new ConversationTurn(type.role(), arg, null);
        if (type.isPersistent()) {
            add(incoming);
            return turns();
        }
        List<ConversationTurn> combined = new ArrayList<>(turns());
        combined.add(incoming);
        return combined;
    }

    /** Records the model's reply, if this turn type is meant to persist (see {@link #record}). */
    public void recordAssistantReply(LlmClient.ModerationType type, LlmResult result) {
        if (type.isPersistent()) {
            add(ConversationTurn.assistant(result.replyText(), result.toolCall()));
        }
    }

    private void trim() {
        int budget = maxTokens - estimateTokens(ConfigLoader.lang.systemRules);
        while (estimateTotal() > budget && !turns.isEmpty()) {
            turns.removeFirst();
        }
    }

    private int estimateTotal() {
        int total = 0;
        for (ConversationTurn turn : turns) {
            total += estimateTokens(turn.text());
            LlmToolCall call = turn.toolCall();
            if (call != null) {
                total += estimateTokens(call.value()) + estimateTokens(call.value2()) + estimateTokens(call.value3());
            }
        }
        return total;
    }

    // Simple token estimation: 1 token ≈ ¾ word ≈ 4 chars
    private static int estimateTokens(String s) {
        return s == null ? 0 : s.length() / 4;
    }
}
