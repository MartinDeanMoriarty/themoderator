package com.nomoneypirate.llm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import net.minecraft.server.MinecraftServer;

public class ModerationScheduler {

    private static final List<String> messageBuffer = new ArrayList<>();
    public static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void runScheduledTask(MinecraftServer server, String type) {
        // Feedback
        String feedback;
        switch (type) {
            case "summary" -> {
                // create snapshot and clear list and clear messageBuffer
                List<String> snapshot;
                synchronized (messageBuffer) {
                    if (messageBuffer.isEmpty()) return;
                    snapshot = new ArrayList<>(messageBuffer);
                    messageBuffer.clear();
                }
                // Snapshot to summary
                feedback = String.join("\n", snapshot);
                // If summary is empty, don't let the task go to waste, use it to do something.
                // So, let the llm tell players about the activation keywords or what ever
                String keyWords = ConfigLoader.config.activationKeywords.toString();
                if (feedback.isEmpty()) feedback = ConfigLoader.lang.feedback_38.formatted(keyWords);
                // Send summary to llm
                LlmClient.moderateAsync(LlmClient.ModerationType.SUMMARY, ConfigLoader.lang.contextFeedback_05.formatted(feedback)).thenAccept(dec -> ModDecisions.applyDecision(server, dec));
            }

            case "restart" -> {
                // Feedback
                int nextHour = LocalDateTime.now().getHour() + 1;
                int nowMinutes = LocalDateTime.now().getMinute();
                if (nextHour == ConfigLoader.config.autoRestartHour) {
                    if (nowMinutes >= 45) {
                        feedback = ConfigLoader.lang.restartFeedback.formatted(ConfigLoader.config.autoRestartHour, ConfigLoader.config.serverRestartPrewarn);
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec ->  ModDecisions.applyDecision(server, dec));
                    }
                }
            }

        }

    }

    public static void addMessage(String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
        String entry = "[" + timestamp + "] " + content;
        synchronized (messageBuffer) {
            messageBuffer.add(entry);
        }
    }

    public static void clearMessages() {
        synchronized (messageBuffer) {
            messageBuffer.clear();
        }
    }

    public static void shutdown() {
        scheduler.shutdownNow();
    }

}