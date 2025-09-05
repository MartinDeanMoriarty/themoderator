package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.entity.ModAvatar.*;
import com.mojang.authlib.GameProfile;
import com.nomoneypirate.actions.ModActions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.llm.ModerationScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModEvents {

    // For world ready event
    static boolean checked = false;
    // Scheduler interval
    private static int tickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int INTERVAL_MINUTES = ConfigLoader.config.scheduleInterval;
    public static final int INTERVAL_TICKS = TICKS_PER_MINUTE * INTERVAL_MINUTES;
    // Cooldown for chat messages
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = ConfigLoader.config.requestCooldown * 1_000; // * 1000 = milliseconds


    public static void registerEvents() {

        // "Update-Loop" and world ready event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Scheduler
            tickCounter++;
            if (tickCounter >= INTERVAL_TICKS) {
                tickCounter = 0;
                ModerationScheduler.runScheduledTask(server);
            }

            ServerWorld world = findModeratorWorld(server);
            if (checked && currentAvatarId != null) {
                // Pull a chunk loader along with the Avatar if there is an Avatar
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
                        feedback.append(" Avatar: ").append(currentModeratorAvatar());
                    }
                    // Only send if there is a feedback
                     if (!feedback.isEmpty()) {
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback.toString()).thenAccept(dec -> applyDecision(server, dec));
                        //LlmClient.sendFeedbackAsync(feedback.toString()).thenAccept(dec -> applyDecision(server, dec));
                    }
                    checked = true;
                }
            }
        });

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Get player name
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();

            String welcomeText = ConfigLoader.lang.welcomeText.formatted(playerName);
            // Asynchronous to the LLM
            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, welcomeText).thenAccept(decision -> {
            //LlmClient.moderateAsync(playerName, welcomeText).thenAccept(decision -> {
                // Back to the server thread
                server.execute(() -> applyDecision(server, decision));
            }).exceptionally(ex -> {
                // In case of errors: do not block anything, at most log
                if (ConfigLoader.config.modLogging) LOGGER.error("Welcoming failed: {}", ex.getMessage());
                return null;
            });
        });

        // Intercept game messages to collect for the llm
        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.getString();
            // Add to messageBuffer
            String serverMessage = ConfigLoader.lang.feedback_48.formatted(content);
            ModerationScheduler.addMessage(serverMessage);
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            MinecraftServer server = sender.getServer();
            if (server == null) return;

            String playerName = sender.getName().getString();
            String content = message.getContent().getString();
            // Add to messageBuffer
            String chatMessage = ConfigLoader.lang.feedback_47.formatted(playerName, content);
            ModerationScheduler.addMessage(chatMessage);

            // Keyword-Check
            if (ConfigLoader.config.activationKeywords.stream().anyMatch(content.toLowerCase()::contains)) {
                long now = System.currentTimeMillis();
                long last = cooldowns.getOrDefault(playerName, 0L);

                // Cooldown
                if (now - last < COOLDOWN_MILLIS) {
                    if (ConfigLoader.config.modLogging) LOGGER.info("Cooldown! {} – request ignored.", playerName);
                    return;
                }

                // Cooldown
                cooldowns.put(playerName, now);

                // Async-Request
                LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, chatMessage).thenAccept(decision -> {
                //LlmClient.moderateAsync(playerName, content).thenAccept(decision -> {
                    server.execute(() -> applyDecision(server, decision));
                }).exceptionally(ex -> {
                    if (ConfigLoader.config.modLogging) LOGGER.error("LLM error: {}", ex.getMessage());
                    return null;
                });
            }
        });

    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {
        switch (decision.action()) {

            case IGNORE -> {
                // Feedback
                String feedback = ConfigLoader.lang.feedback_44;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                //LlmClient.sendFeedbackAsync(feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case STOPACTION -> {
                String feedback = "";
                switch (decision.value()) {
                    case "ALL" -> {
                        if (ModActions.stopAllGoals((ServerWorld) currentAvatarWorld, currentAvatarId)) {
                            feedback = ConfigLoader.lang.feedback_19;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }
                    case "FOLLOWING" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) currentAvatarWorld, currentAvatarId, "FOLLOWING")) {
                            feedback = ConfigLoader.lang.feedback_21;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }

                    case "LOOKINGAT" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) currentAvatarWorld, currentAvatarId, "LOOKINGAT")) {
                            feedback = ConfigLoader.lang.feedback_23;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }

                    case "MOVINGAROUND" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) currentAvatarWorld, currentAvatarId, "MOVINGAROUND")) {
                            feedback = ConfigLoader.lang.feedback_26;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                //LlmClient.sendFeedbackAsync(feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case CHAT -> {
                server.getPlayerManager().broadcast(Text.literal("The Moderator: " + decision.value()), false);
                String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value(), "Chat");
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback
                String feedback = ConfigLoader.lang.feedback_01.formatted(list);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                //LlmClient.sendFeedbackAsync(feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case TELEPORTPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    if (Objects.equals(decision.value(), "AVATAR")) {
                        feedback = ModActions.teleportAvatar(world, currentAvatarId, decision.value2());
                    }
                    else {
                        feedback = ModActions.teleportPlayer(world, currentAvatarId, decision.value());
                    }
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case TELEPORTPOSITION -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                double posX = 0;
                double posZ = 0;

                if (!decision.value2().isEmpty()) {
                    String[] parts = decision.value2().trim().split("\\s+");
                    if (parts.length == 2) {
                        try {
                            posX = Integer.parseInt(parts[0]);
                            posZ = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            if (ConfigLoader.config.modLogging) LOGGER.warn("NumberFormatException: ModEvent.java -> case TELEPORTPOSITION {}", String.valueOf(e));
                        }
                    } else {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Parts length problem: {}", "ModEvent.java -> case TELEPORTPOSITION");
                    }
                }

                if (world != null) {
                    if (Objects.equals(decision.value(), "AVATAR")) {
                        feedback = ModActions.teleportPositionAvatar(world, currentAvatarId, posX, posZ);
                    }
                    else {
                        feedback = ModActions.teleportPositionPlayer(world, decision.value(), posX, posZ);
                    }
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case SPAWNAVATAR -> {
                String feedback;
                currentAvatarPosX = 0;
                currentAvatarPosZ = 0;
                if (!decision.value3().isEmpty()) {
                    String[] parts = decision.value3().trim().split("\\s+");
                    if (parts.length == 2) {
                        try {
                            currentAvatarPosX = Integer.parseInt(parts[0]);
                            currentAvatarPosZ = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            if (ConfigLoader.config.modLogging) LOGGER.warn("NumberFormatException: ModEvent.java -> case SPAWNAVATAR : {}", String.valueOf(e));
                        }
                    } else {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Parts length problem: {}", "ModEvent.java -> case SPAWNAVATAR");
                   }
                }
                feedback = spawnModeratorAvatar(server, decision.value(), decision.value2(), currentAvatarPosX, currentAvatarPosZ);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));

            }

            case DESPAWNAVATAR -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                // Feedback
                feedback = despawnModeratorAvatar(world);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case WHEREIS -> {
                String feedback;
                if (Objects.equals(decision.value(), "AVATAR")) {
                    feedback = ModActions.whereIs(server, "", currentAvatarId);
                }
                else {
                    feedback = ModActions.whereIs(server, decision.value(), null);
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case FEEDBACK -> {
                String feedback = decision.value();
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case GOTOPOSITION -> {
                String feedback;
                double posX = 0;
                double posZ = 0;

                if (!decision.value().isEmpty()) {
                    String[] parts = decision.value().trim().split("\\s+");
                    if (parts.length == 2) {
                        try {
                            posX = Integer.parseInt(parts[0]);
                            posZ = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            if (ConfigLoader.config.modLogging) LOGGER.warn("NumberFormatException: ModEvent.java -> case GOTOPOSITION {}", String.valueOf(e));
                        }
                    } else {
                        if (ConfigLoader.config.modLogging) LOGGER.info("Parts length problem: {}", "ModEvent.java -> case GOTOPOSITION");
                    }
                }

                feedback = ModActions.startGotoPosition((ServerWorld) currentAvatarWorld, currentAvatarId, posX, posZ);
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
            }

            case DAMAGEPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.damagePlayer(world, decision.value(), Integer.parseInt(decision.value2()));
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CLEARINVENTORY -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.clearInventory(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case KILLPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.killPlayer(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case GIVEPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.givePlayer(world, decision.value(), Item.byRawId(Integer.parseInt(decision.value2())), Integer.parseInt(decision.value3()));
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CHANGEWEATHER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeWeather(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CHANGETIME -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeTime(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case WHISPER, WARN, KICK, BAN, FOLLOWPLAYER, LOOKATPLAYER, GOTOPLAYER, MOVEAROUND -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String feedback = ConfigLoader.lang.feedback_07.formatted(decision.value2());
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> {
                        player.sendMessage(Text.literal("The Moderator: " + decision.value2()),false);
                        String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value2(), decision.value());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case WARN -> {
                        player.sendMessage(
                                Text.literal("The Moderator: " + decision.value2()),false
                        );
                        String feedback = ConfigLoader.lang.feedback_08.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: " + decision.value2())
                        );
                        String feedback = ConfigLoader.lang.feedback_09.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case FOLLOWPLAYER -> {
                        String feedback;
                        if (ModActions.startFollowPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_20.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case LOOKATPLAYER -> {
                        String feedback;
                        if (ModActions.startLookingAtPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_22.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case GOTOPLAYER -> {
                        String feedback = ModActions.startGotoPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value());
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case MOVEAROUND -> {
                        String feedback;
                        if (ModActions.startMoveAround((ServerWorld) currentAvatarWorld, currentAvatarId, Double.parseDouble(decision.value()))) {
                            feedback = ConfigLoader.lang.feedback_25.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = ConfigLoader.lang.feedback_10;
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                            return;
                        }
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: " + decision.value2())
                        );
                        if (ConfigLoader.config.useWhitelist) {
                            // Remove Player of the whitelist
                            WhitelistEntry entry = server.getPlayerManager().getWhitelist().get(player.getGameProfile());
                            if (entry != null) {
                                server.getPlayerManager().getWhitelist().remove(player.getGameProfile());
                                server.getPlayerManager().reloadWhitelist();
                            }
                        }
                        if (ConfigLoader.config.useBanlist) {
                            BannedPlayerEntry entry = getBannedPlayerEntry(decision, player);
                            server.getPlayerManager().getUserBanList().add(entry);
                            try {
                                server.getPlayerManager().getUserBanList().save();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        String feedback = ConfigLoader.lang.feedback_11.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }
                }
            }

            case STOPCHAIN -> {
                // Do nothing
            }
        }
    }

    private static @NotNull BannedPlayerEntry getBannedPlayerEntry(ModerationDecision decision, ServerPlayerEntity player) {
        GameProfile profile = player.getGameProfile();
        Date now = new Date();
        String reason = decision.value2();
        String source = "[The Moderator]";
        //Date expiry = null; // null = permanent

        return new BannedPlayerEntry(
                profile,
                now,
                source,
                null,
                reason
        );
    }

}