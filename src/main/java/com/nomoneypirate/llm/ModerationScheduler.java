package com.nomoneypirate.llm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.nomoneypirate.config.ConfigLoader;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;

import static com.nomoneypirate.events.ModEvents.applyDecision;

public class ModerationScheduler {

    private static final List<String> messageBuffer = new ArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void runScheduledTask(MinecraftServer server) {
        // create snapshot and clear list and clear messageBuffer
        List<String> snapshot;
        synchronized (messageBuffer) {
            if (messageBuffer.isEmpty()) return;
            snapshot = new ArrayList<>(messageBuffer);
            messageBuffer.clear();
        }
        // Snapshot to summary
        String summary = String.join("\n", snapshot);
        // If summary is empty, don't let the task go to waste, use it to do something.
        // So, let's tell players about the activation keywords
        String keyWords = ConfigLoader.config.activationKeywords.toString();
        if (summary.isEmpty()) summary = ConfigLoader.lang.feedback_38.formatted(keyWords);

        // Send summary to llm
        LlmClient.moderateAsync(LlmClient.ModerationType.SUMMARY, summary).thenAccept(dec -> applyDecision(server, dec));
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