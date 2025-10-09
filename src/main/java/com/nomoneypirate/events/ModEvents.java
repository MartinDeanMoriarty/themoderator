package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModEvents {

    // Scheduler intervals
    private static int moderationTickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    public static boolean restartAnnounced = false;
    // Cooldown for chat messages
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    public static MinecraftServer SERVER;
    public static Boolean actionMode = false;
    private static boolean startAnnounced = false;

    public static void registerEvents() {
        // Scheduler intervals
        final int INTERVAL_MINUTES = ConfigLoader.config.scheduleSummaryInterval;
        final int MODERATE_INTERVAL_TICKS = TICKS_PER_MINUTE * INTERVAL_MINUTES;
        final LocalTime RESTART_TIME = LocalTime.of(ConfigLoader.config.autoRestartHour, 0); // 04:00 Uhr
        final int ANNOUNCE_BEFORE_MINUTES = ConfigLoader.config.serverRestartPrewarn;
        // Cooldown for chat messages
        final long COOLDOWN_MILLIS = ConfigLoader.config.requestCooldown * 1_000; // * 1000 = milliseconds

        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVER = null;
            ModerationScheduler.shutdown();
        });

        // "Update-Loop" and world ready event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Scheduled Moderation
            moderationTickCounter++;
            if (ConfigLoader.config.scheduledSummary && moderationTickCounter >= MODERATE_INTERVAL_TICKS) {
                moderationTickCounter = 0;
                ModerationScheduler.runScheduledTask(server, "summary");
            }

            // Scheduled restart announcement
            LocalTime now = LocalTime.now();
            // Check if we are in the time window
            if (ConfigLoader.config.scheduledServerRestart && !restartAnnounced && now.isAfter(RESTART_TIME.minusMinutes(ANNOUNCE_BEFORE_MINUTES)) && now.isBefore(RESTART_TIME)) {
                restartAnnounced = true;
                ModerationScheduler.runScheduledTask(server, "restart");
            }

            // Let the llm know when the server (re)started
            // Only send if it is a dedicated server because on singleplayer it collides with the player join message
            if (!startAnnounced && server instanceof DedicatedServer) {
                startAnnounced = true;
                // Server (re)start message.
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(ConfigLoader.lang.feedback_17
                )).thenAccept(dec -> ModDecisions.applyDecision(server, dec));
            }

        });

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!actionMode) {
                // Get player name
                ServerPlayerEntity player = handler.getPlayer();
                String playerName = player.getName().getString();

                String welcomeText = ConfigLoader.lang.playerJoined.formatted(playerName);
                // Async-Request
                LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, ConfigLoader.lang.requestContext.formatted(welcomeText)).thenAccept(decision -> server.execute(() -> ModDecisions.applyDecision(server, decision))).exceptionally(ex -> {
                    if (ConfigLoader.config.modLogging) LOGGER.error("Welcoming failed: {}", ex.getMessage());
                    return null;
                });
            }
        });

        // Intercept game messages for moderation scheduler
        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.getString();
            // Add Game Messages to moderation scheduler
            if (ConfigLoader.config.scheduledSummary) {
                String serverMessage = ConfigLoader.lang.serverMessage.formatted(content);
                ModerationScheduler.addMessage(serverMessage);
            }
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            String playerName = sender.getName().getString();
            String content = message.getContent().getString();
            String chatMessage = ConfigLoader.lang.requestContext.formatted(ConfigLoader.lang.playerMessage.formatted(playerName, content));
            Text busyMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.busyFeedback, Formatting.BLUE, Formatting.YELLOW, false, true, false);

            // Add all Chat Messages to moderation scheduler
            if (ConfigLoader.config.scheduledSummary) {
                ModerationScheduler.addMessage(chatMessage);
            }

            // Keyword-Check
            if (!ConfigLoader.config.useActivationKeywords || (ConfigLoader.config.useActivationKeywords && ConfigLoader.config.activationKeywords.stream().anyMatch(content.toLowerCase()::contains))) {
                if (!actionMode) {
                    // Cooldown
                    long now = System.currentTimeMillis();
                    long last = cooldowns.getOrDefault("Chat", 0L);
                    if (now - last < COOLDOWN_MILLIS) {
                        if (SERVER != null)
                            // Chat Output: Model is busy. Using execute to put the message behind player message
                            server.execute(() -> SERVER.getPlayerManager().broadcast(busyMessage, false));
                        return;
                    }
                    cooldowns.put("Chat", now);

                    // Async-Request
                    LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, chatMessage).thenAccept(decision -> server.execute(() -> ModDecisions.applyDecision(server, decision))).exceptionally(ex -> {
                        if (ConfigLoader.config.modLogging) LOGGER.error("LLM error: {}", ex.getMessage());
                        return null;
                    });
                }
                else {
                    // Chat Output: Model is busy. Using execute to put the message behind player message
                    server.execute(() -> SERVER.getPlayerManager().broadcast(busyMessage, false));
                }
            }

        });

        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("4of6 Events Initialized.");
    }

}