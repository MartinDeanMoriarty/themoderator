package com.nomoneypirate.llm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import static com.nomoneypirate.Themoderator.LOGGER;

/** The scheduled parts of the moderator: periodic chat summaries and the restart announcement. */
public class ModerationScheduler {

    private static final Deque<String> messageBuffer = new ArrayDeque<>();
    private static int bufferChars = 0;

    /** Sends everything said since the last summary to the LLM, newest lines first if it doesn't all fit. */
    public static void runSummary(MinecraftServer server) {
        List<String> snapshot;
        synchronized (messageBuffer) {
            if (messageBuffer.isEmpty()) return;
            snapshot = new ArrayList<>(messageBuffer);
            messageBuffer.clear();
            bufferChars = 0;
        }
        // The summary is sent as one message - keep the newest lines that fit into the model's context
        int budget = summaryBudgetChars();
        Deque<String> kept = new ArrayDeque<>();
        int chars = 0;
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            chars += snapshot.get(i).length() + 1;
            if (chars > budget && !kept.isEmpty()) break;
            kept.addFirst(snapshot.get(i));
        }
        String feedback = String.join("\n", kept);
        ModDecisions.moderateAndApply(server, LlmClient.ModerationType.SUMMARY, ConfigLoader.lang.summaryContext.formatted(feedback));
    }

    /**
     * Tells the players about the upcoming restart. The LLM gets to word it, but a restart warning
     * must not depend on the LLM: if the call fails or the model answers without any chat text,
     * a fixed message is broadcast instead.
     */
    public static void announceRestart(MinecraftServer server, int hour, int minutesLeft) {
        String feedback = ConfigLoader.lang.restartFeedback.formatted(hour, minutesLeft);
        ModDecisions.startChain();
        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback))
                .whenComplete((decision, error) -> server.execute(() -> {
                    if (error != null) LOGGER.warn("Restart announcement via LLM failed: {}", error.getMessage());
                    if (error != null || decision == null || !decision.replied()) {
                        Component fixed = ModDecisions.formatChatOutput(ConfigLoader.config.moderatorName + ": ",
                                ConfigLoader.lang.restartAnnouncement.formatted(hour, minutesLeft),
                                ChatFormatting.BLUE, ChatFormatting.YELLOW, true, false, false);
                        server.getPlayerList().broadcastSystemMessage(fixed, false);
                    }
                    if (decision != null) {
                        try {
                            ModDecisions.applyDecision(server, decision);
                        } catch (RuntimeException e) {
                            LOGGER.error("Applying the decision after the restart announcement failed", e);
                            ModDecisions.endChain();
                        }
                    }
                }));
    }

    public static void addMessage(String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
        String entry = "[" + timestamp + "] " + content;
        synchronized (messageBuffer) {
            messageBuffer.addLast(entry);
            bufferChars += entry.length() + 1;
            // A busy server must not grow this without bound between two summaries
            int cap = summaryBudgetChars() * 2;
            while (bufferChars > cap && messageBuffer.size() > 1) {
                bufferChars -= messageBuffer.removeFirst().length() + 1;
            }
        }
    }

    /** Characters of summary text that fit next to the system prompt and the action definitions. */
    private static int summaryBudgetChars() {
        int tokens = ConfigLoader.config.tokenLimit - SystemPrompt.estimateOverheadTokens() - ConversationHistory.ANSWER_RESERVE_TOKENS;
        return Math.max(1000, tokens * 4);
    }

}
