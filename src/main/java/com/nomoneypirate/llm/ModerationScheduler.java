package com.nomoneypirate.llm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.nomoneypirate.config.ConfigLoader;

import static com.nomoneypirate.events.ModEvents.applyDecision;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;

public class ModerationScheduler {

    private static final List<String> messageBuffer = new ArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void runScheduledTask(MinecraftServer server) {
        System.out.println("themoderator - Schedule started.");
        List<String> snapshot;
        synchronized (messageBuffer) {
            if (messageBuffer.isEmpty()) return;
            snapshot = new ArrayList<>(messageBuffer);
            messageBuffer.clear();
        }

        String summary = String.join("\n", snapshot);
        String keyWords = ConfigLoader.config.activationKeywords.toString();
        if (summary.isEmpty()) summary = ConfigLoader.lang.feedback_38.formatted(keyWords);

        // Send summary to llm
        LlmClient.sendSummaryAsync(summary)
                .thenAccept(dec -> applyDecision(server, dec));

        // For now, we just clear messages.
        // Therefor the llm is not able to "look back" as good!?
        // And has probably bad "overlapping knowledge"!?
        // clearMessages();
        System.out.println("themoderator - Schedule Task done.");

    }

    public static void addMessage(String source, String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
        String entry = "[" + timestamp + "] " + source + ": " + content;
        synchronized (messageBuffer) {
            messageBuffer.add(entry);
        }
        System.out.println("themoderator - Schedule line added.");
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