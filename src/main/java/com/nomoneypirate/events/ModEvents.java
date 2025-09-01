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
import java.util.Objects;
import java.util.stream.Collectors;

public class ModEvents {

    static boolean checked = false;
    private static int tickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int INTERVAL_MINUTES = ConfigLoader.config.scheduleInterval;
    public static final int INTERVAL_TICKS = TICKS_PER_MINUTE * INTERVAL_MINUTES;

    public static void registerEvents() {

        // "Update-Loop" and World-Ready event
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
            // World-Ready event
            // Let's check once for a lingering mob at server start
            // We can also let the llm know when the server (re)started
            else if (!checked) {
                if (world != null) {
                    StringBuilder feedback = new StringBuilder();
                    // feedback.append(ConfigLoader.lang.feedback_17);
                    // Search for lingering mob
                    if (searchModeratorAvatar(world)) {
                        System.out.println("themoderator -Found Avatar.");
                        feedback.append(" Avatar: ").append(currentModeratorAvatar());
                    }
                    // Only send if there is a feedback
                     if (!feedback.isEmpty()) {
                     System.out.println("themoderator -Sending Feedback:" + feedback);
                    LOGGER.info(feedback.toString());
                     LlmClient.sendFeedbackAsync(feedback.toString())
                            .thenAccept(dec -> applyDecision(server, dec));
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

            String welcomeText = ConfigLoader.lang.welcomeText;
            // Asynchronous to the LLM
            LlmClient.moderateAsync(playerName, welcomeText).thenAccept(decision -> {
                // Back to the server thread
                server.execute(() -> applyDecision(server, decision));
            }).exceptionally(ex -> {
                // In case of errors: do not block anything, at most log
                LOGGER.error("Welcoming failed: {}", ex.getMessage());
                return null;
            });
        });

        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.toString();
            // Add to scheduler
            ModerationScheduler.addMessage("Game", content);
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            // Get player name and chat content
            String playerName = sender.getName().getString();
            String content = message.getContent().getString();

            // Add to scheduler
            ModerationScheduler.addMessage("Spieler", playerName + ": " + content);

            // Check for a keyword to trigger a message to the llm
            if (ConfigLoader.config.activationKeywords.stream().anyMatch(content.toLowerCase()::contains)) {
                // Asynchronous to the llm
                LlmClient.moderateAsync(playerName, content).thenAccept(decision -> {
                    // Back to the server thread to apply the decision
                    server.execute(() -> applyDecision(server, decision));
                }).exceptionally(ex -> {
                    // In case of errors: do not block anything, at most log
                    LOGGER.error("LLM error: {}", ex.getMessage());
                    return null;
                });
            }

        });

    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {
        switch (decision.action()) {

            case STOP -> {
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
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case CHAT -> server.getPlayerManager().broadcast(
                    Text.literal("The Moderator: " + decision.value()),false
            );

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback
                String feedback = ConfigLoader.lang.feedback_01.formatted(list);
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
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
                    LlmClient.sendFeedbackAsync(feedback)
                            .thenAccept(dec -> applyDecision(server, dec));
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
                            feedback = ConfigLoader.lang.feedback_02;
                            // Feedback
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                        }
                    } else {
                        feedback = ConfigLoader.lang.feedback_02;
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
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
                    LlmClient.sendFeedbackAsync(feedback)
                            .thenAccept(dec -> applyDecision(server, dec));
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
                            feedback = ConfigLoader.lang.feedback_02;
                            // Feedback
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                        }
                    } else {
                        feedback = ConfigLoader.lang.feedback_02;
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }
                }
                //MinecraftServer server = world.getServer();
                if (spawnModeratorAvatar(server, decision.value(), decision.value2(), currentAvatarPosX, currentAvatarPosZ)) {
                    feedback = ConfigLoader.lang.feedback_03.formatted(decision.value(), decision.value2(), currentAvatarPosX, currentAvatarPosZ);

                } else {
                    feedback = ConfigLoader.lang.feedback_04;
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case DESPAWNAVATAR -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (despawnModeratorAvatar(world)) {
                    feedback = ConfigLoader.lang.feedback_05;
                } else {
                    feedback = ConfigLoader.lang.feedback_06;
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
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
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case FEEDBACK -> {
                String feedback = decision.value();
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
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
                            feedback = ConfigLoader.lang.feedback_02;
                            // Feedback
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                        }
                    } else {
                        feedback = ConfigLoader.lang.feedback_02;
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }
                }

                feedback = ModActions.startGotoPosition((ServerWorld) currentAvatarWorld, currentAvatarId, posX, posZ);
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case WHISPER, WARN, KICK, BAN, FOLLOWPLAYER, LOOKATPLAYER, GOTOPLAYER, MOVEAROUND -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String feedback = ConfigLoader.lang.feedback_07.formatted(decision.value2());
                    LlmClient.sendFeedbackAsync(feedback)
                            .thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> player.sendMessage(
                            Text.literal("The Moderator: " + decision.value2()),false
                    );

                    case WARN -> {
                        player.sendMessage(
                                Text.literal("The Moderator: " + decision.value2()),false
                        );
                        String feedback = ConfigLoader.lang.feedback_08.formatted(decision.value(), decision.value2());
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info(feedback);
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: " + decision.value2())
                        );
                        String feedback = ConfigLoader.lang.feedback_09.formatted(decision.value(), decision.value2());
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info(feedback);
                    }

                    case FOLLOWPLAYER -> {
                        String feedback;
                        if (ModActions.startFollowPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_20.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }

                    case LOOKATPLAYER -> {
                        String feedback;
                        if (ModActions.startLookingAtPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_22.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }

                    case GOTOPLAYER -> {
                        String feedback = ModActions.startGotoPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value());
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }

                    case MOVEAROUND -> {
                        String feedback;
                        if (ModActions.startMoveAround((ServerWorld) currentAvatarWorld, currentAvatarId, Double.parseDouble(decision.value()))) {
                            feedback = ConfigLoader.lang.feedback_25.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = ConfigLoader.lang.feedback_10;
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
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
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info(feedback);
                    }
                }
            }

            case IGNORE -> {
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