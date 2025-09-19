package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.entity.ModAvatar.*;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModEvents {

    // For world ready event
    static boolean checked = false;
    // Scheduler intervals
    private static int moderationTickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int INTERVAL_MINUTES = ConfigLoader.config.scheduleModerationInterval;
    public static final int MODERATE_INTERVAL_TICKS = TICKS_PER_MINUTE * INTERVAL_MINUTES;

    private static final LocalTime RESTART_TIME = LocalTime.of(ConfigLoader.config.autoRestartHour, 0); // 04:00 Uhr
    private static final int ANNOUNCE_BEFORE_MINUTES = ConfigLoader.config.serverRestartPrewarn;
    private static boolean restartAnnounced = false;

    // Cooldown for chat messages
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = ConfigLoader.config.requestCooldown * 1_000; // * 1000 = milliseconds

    public static MinecraftServer SERVER;
    public static Boolean actionMode = false;

    public static void registerEvents() {

        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVER = null;
            ModerationScheduler.shutdown();
        });

        // "Update-Loop" and world ready event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Scheduled Moderation
            moderationTickCounter++;
            if (moderationTickCounter >= MODERATE_INTERVAL_TICKS) {
                moderationTickCounter = 0;
                if (ConfigLoader.config.scheduledModeration) ModerationScheduler.runScheduledTask(server, "summary");
            }

            // Scheduled restart announcement
            LocalTime now = LocalTime.now();
            // Check if we are in the time window
            if (!restartAnnounced && now.isAfter(RESTART_TIME.minusMinutes(ANNOUNCE_BEFORE_MINUTES)) && now.isBefore(RESTART_TIME)) {
                if (ConfigLoader.config.scheduledServerRestart) ModerationScheduler.runScheduledTask(server, "restart");
                restartAnnounced = true;
            }

            // Check for the moderator avatar at runtime
            ServerWorld world = findModeratorWorld(server);
            if (checked && currentAvatarId != null) {
                // Pull a chunk loader along with the avatar if there is an avatar
                if (world != null) {
                    Entity moderator = findModeratorEntity(world);
                    if (moderator != null) {
                        updateChunkAnchor(world, moderator);
                    }
                }
            }
            // World ready event.
            // Let's check once for a lingering mob at server start
            // We can also let the llm know when the server (re)started
            else if (!checked) {
                if (world != null) {
                    StringBuilder feedback = new StringBuilder();
                    // Server (re)start message.
                    feedback.append(ConfigLoader.lang.feedback_17);
                    // Search for lingering mob
                    if (searchModeratorAvatar(world)) {
                        // Avatar found message
                        feedback.append(" Avatar: ").append(currentModeratorAvatar());
                    }
                    // Only send if there is a feedback
                    // AND only send if it is a dedicated server because on singleplayer it collides with the player join message
                     if (!feedback.isEmpty() && server instanceof DedicatedServer) {
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback.toString())).thenAccept(dec -> ModDecisions.applyDecision(server, dec));
                    }
                    checked = true;
                }
            }
        });

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!actionMode) {
                // Start action mode. Stopped with STOPCHAIN in ModDecisions
                actionMode = true;
                // Get player name
                ServerPlayerEntity player = handler.getPlayer();
                String playerName = player.getName().getString();

                String welcomeText = ConfigLoader.lang.playerJoined.formatted(playerName);
                // Asynchronous to the LLM
                Objects.requireNonNull(LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, ConfigLoader.lang.contextFeedback_01.formatted(welcomeText))).thenAccept(decision -> {
                    // Back to the server thread
                    server.execute(() -> ModDecisions.applyDecision(server, decision));
                }).exceptionally(ex -> {
                    // In case of errors: do not block anything, at most log
                    if (ConfigLoader.config.modLogging) LOGGER.error("Welcoming failed: {}", ex.getMessage());
                    return null;
                });
            }
        });

        // Intercept game messages for moderation scheduler
        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.getString();
            // Add Game Messages to moderation scheduler
            if (ConfigLoader.config.scheduledModeration) {
                String serverMessage = ConfigLoader.lang.contextFeedback_04.formatted(content);
                ModerationScheduler.addMessage(serverMessage);
            }
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            String playerName = sender.getName().getString();
            String content = message.getContent().getString();
            String chatMessage = ConfigLoader.lang.contextFeedback_01.formatted(ConfigLoader.lang.feedback_51.formatted(playerName, content));
            Text formatted = ModDecisions.formatChatOutput("", ConfigLoader.lang.playerFeedback, Formatting.BLUE, Formatting.YELLOW, false, true, false);

            // Add all Chat Messages to moderation scheduler
            if (ConfigLoader.config.scheduledModeration) {
                ModerationScheduler.addMessage(chatMessage);
            }

            // Keyword-Check
            if (ConfigLoader.config.activationKeywords.stream().anyMatch(content.toLowerCase()::contains)) {
                if (!actionMode) {
                    // Start action mode. Stopped with STOPCHAIN in ModDecisions.java
                    actionMode = true;

                    // Cooldown
                    long now = System.currentTimeMillis();
                    long last = cooldowns.getOrDefault("Chat", 0L);
                    if (now - last < COOLDOWN_MILLIS) {
                        if (SERVER != null)
                            // Chat Output: Model is busy. Use execute to try to put the message behind player message
                            server.execute(() -> SERVER.getPlayerManager().broadcast(formatted, false));
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
                    // Chat Output: Model is busy. Use execute to put the message behind player message
                    server.execute(() -> SERVER.getPlayerManager().broadcast(formatted, false));
                }
            }

        });

        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("4of6 Events Initialized.");
    }

}