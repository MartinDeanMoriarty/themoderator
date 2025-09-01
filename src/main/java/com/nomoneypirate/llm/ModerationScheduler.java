package com.nomoneypirate.llm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.JsonObject;
import com.nomoneypirate.config.ConfigLoader;
import static com.nomoneypirate.llm.LlmClient.logToFile;
import com.google.gson.*;

public class ModerationScheduler {

    private static final List<String> messageBuffer = new ArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int INTERVAL_MINUTES = ConfigLoader.config.scheduleInterval;
    private static final Gson GSON = new GsonBuilder().create();

    public static void start() {
        scheduler.scheduleAtFixedRate(() -> {
            List<String> snapshot;
            synchronized (messageBuffer) {
                if (messageBuffer.isEmpty()) return;
                snapshot = new ArrayList<>(messageBuffer);
                messageBuffer.clear();
            }
            // Get summary
            String summary = String.join("\n", snapshot);
            // Send only Keywords description if summary is empty so the llm has something to do
            // Get the activation keywords
            String keyWords = ConfigLoader.config.activationKeywords.toString();
            if (summary.isEmpty()) summary = ConfigLoader.lang.feedback_38.formatted(keyWords);
            // Feedback
            String feedback = "Summary: " + summary;
            new ModerationDecision(ModerationDecision.Action.FEEDBACK, feedback,"", "");

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
//            JsonObject body = new JsonObject();
//            body.addProperty("Summary", summary);
//            // Input logging
//            String jsonBody = GSON.toJson(body);
            logToFile("themoderator_schedule" + timestamp + ".json", "Schedule:\n" + feedback);


            // For now, we just clear messages.
            // Therefor the llm is not able to "look back" as good!?
            // And has probably bad "overlapping knowledge"!?
            clearMessages();

            // Asynchronous to the llm
//            LlmClient.moderateAsync("System", summary).thenAccept(decision -> {
//                server.execute(() -> applyDecision(server, decision));
//                // For now, we just clear messages.
//                // Therefor the llm is not able to "look back" as good!?
//                // And has probably bad "overlapping knowledge"!?
//                clearMessages();
//            }).exceptionally(ex -> {
//                LOGGER.error("LLM summary error: {}", ex.getMessage());
//                return null;
//            });

        }, INTERVAL_MINUTES, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public static void addMessage(String source, String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
        String entry = "[" + timestamp + "] " + source + ": " + content;
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