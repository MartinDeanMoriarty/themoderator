package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.config.ModConfig;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModEvents {

    // Scheduler intervals
    private static int moderationTickCounter = 0;
    private static int secondTickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    public static boolean restartAnnounced = false;
    // Cooldown for chat messages
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    public static volatile MinecraftServer SERVER;
    // True while the moderator works on a request (the LLM call and the chain of actions that follows).
    // Use setBusy()/isBusy() - isBusy() also expires the flag, so one lost callback can never block the moderator for good.
    public static volatile boolean actionMode = false;
    private static volatile long busySince = 0L;
    private static boolean startAnnounced = false;

    public static void setBusy(boolean busy) {
        actionMode = busy;
        if (busy) busySince = System.currentTimeMillis();
    }

    public static boolean isBusy() {
        if (!actionMode) return false;
        ModConfig cfg = ConfigLoader.config;
        long limitMillis = (long) cfg.responseTimeout * 1000L * (cfg.maxActionChain + 1);
        if (System.currentTimeMillis() - busySince > limitMillis) {
            LOGGER.warn("The moderator was busy for longer than {}s without finishing - resetting the busy state.", limitMillis / 1000);
            actionMode = false;
            return false;
        }
        return true;
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVER = null;
            actionMode = false;
        });

        // "Update-Loop" and world ready event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Read the config on every tick so /moderatorreload takes effect without a restart
            ModConfig cfg = ConfigLoader.config;

            // Scheduled Moderation
            if (cfg.scheduledSummary) {
                moderationTickCounter++;
                if (moderationTickCounter >= TICKS_PER_MINUTE * Math.max(1, cfg.scheduleSummaryInterval)) {
                    moderationTickCounter = 0;
                    ModerationScheduler.runSummary(server);
                }
            } else {
                moderationTickCounter = 0;
            }

            // Scheduled restart announcement - checking once a second is plenty
            if (++secondTickCounter >= 20) {
                secondTickCounter = 0;
                checkRestartAnnouncement(server, cfg);
            }

            // Let the llm know when the server (re)started
            // Only send if it is a dedicated server because on singleplayer it collides with the player join message
            if (!startAnnounced && server instanceof DedicatedServer) {
                startAnnounced = true;
                // Server (re)start message - not part of any action chain
                ModDecisions.startChain();
                ModDecisions.moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(ConfigLoader.lang.feedback_17));
            }

        });

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!isBusy()) {
                // Get player name
                ServerPlayer player = handler.getPlayer();
                String playerName = player.getName().getString();

                String welcomeText = ConfigLoader.lang.playerJoined.formatted(playerName);
                // Async-Request
                ModDecisions.moderateAndApply(server, LlmClient.ModerationType.MODERATION, ConfigLoader.lang.requestContext.formatted(welcomeText));
            }
        });

        // Intercept game messages for moderation scheduler
        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.getString();
            // Add Game Messages to moderation scheduler - but not the moderator's own output
            if (ConfigLoader.config.scheduledSummary && !isOwnMessage(content)) {
                String serverMessage = ConfigLoader.lang.serverMessage.formatted(content);
                ModerationScheduler.addMessage(serverMessage);
            }
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {

            MinecraftServer server = sender.level().getServer();
            if (server == null) return;
            ModConfig cfg = ConfigLoader.config;

            String playerName = sender.getName().getString();
            String content = message.signedContent();
            String chatMessage = ConfigLoader.lang.requestContext.formatted(ConfigLoader.lang.playerMessage.formatted(playerName, content));
            Component busyMessage = ModDecisions.formatChatOutput("", ConfigLoader.lang.busyFeedback, ChatFormatting.BLUE, ChatFormatting.YELLOW, false, true, false);

            // Add all Chat Messages to moderation scheduler
            if (cfg.scheduledSummary) {
                ModerationScheduler.addMessage(chatMessage);
            }

            // Keyword-Check
            String lowerContent = content.toLowerCase();
            if (!cfg.useActivationKeywords || cfg.activationKeywords.stream().anyMatch(k -> lowerContent.contains(k.toLowerCase()))) {
                if (!isBusy()) {
                    // Cooldown
                    long now = System.currentTimeMillis();
                    long last = cooldowns.getOrDefault("Chat", 0L);
                    if (now - last < cfg.requestCooldown * 1_000L) {
                        // Chat Output: Model is busy. Using execute to put the message behind player message
                        server.execute(() -> server.getPlayerList().broadcastSystemMessage(busyMessage, false));
                        return;
                    }
                    cooldowns.put("Chat", now);

                    // Async-Request
                    ModDecisions.moderateAndApply(server, LlmClient.ModerationType.MODERATION, chatMessage);
                }
                else {
                    // Chat Output: Model is busy. Using execute to put the message behind player message
                    server.execute(() -> server.getPlayerList().broadcastSystemMessage(busyMessage, false));
                }
            }

        });

        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("Events Initialized.");
    }

    /**
     * Announces a restart once, when the next restart is at most {@code serverRestartPrewarn} minutes away.
     * Works from "time until the next occurrence of the restart hour", so a restart at hour 0 (midnight)
     * is handled like any other, and the flag re-arms itself once the window has passed.
     */
    private static void checkRestartAnnouncement(MinecraftServer server, ModConfig cfg) {
        if (!cfg.scheduledServerRestart) {
            restartAnnounced = false;
            return;
        }
        int hour = Math.floorMod(cfg.autoRestartHour, 24);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime restart = now.toLocalDate().atTime(LocalTime.of(hour, 0));
        if (!restart.isAfter(now)) restart = restart.plusDays(1);
        long secondsLeft = Duration.between(now, restart).getSeconds();

        if (secondsLeft <= Math.max(1, cfg.serverRestartPrewarn) * 60L) {
            if (!restartAnnounced) {
                restartAnnounced = true;
                ModerationScheduler.announceRestart(server, hour, (int) Math.max(1, (secondsLeft + 59) / 60));
            }
        } else {
            restartAnnounced = false;
        }
    }

    /** True for lines the moderator itself put into the chat (its replies and its error/busy notices). */
    private static boolean isOwnMessage(String content) {
        return content.startsWith(ConfigLoader.config.moderatorName + ":") || content.startsWith("->");
    }

    /** Safe to call from any thread - the player list belongs to the server thread. */
    public static void logErrorToChat(Component message) {
        MinecraftServer server = SERVER;
        if (server != null) server.execute(() -> server.getPlayerList().broadcastSystemMessage(message, false));
    }

}
