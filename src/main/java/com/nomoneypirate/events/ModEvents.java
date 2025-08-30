package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.entity.ModAvatar.*;

import com.mojang.authlib.GameProfile;
import com.nomoneypirate.actions.ModActions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
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

    public static void registerEvents() {
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

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            // Get player name and chat content
            String playerName = sender.getName().getString();
            String content = message.getContent().getString();

            // Asynchronous to the LLM
            LlmClient.moderateAsync(playerName, content).thenAccept(decision -> {
                // Back to the server thread to apply the decision
                server.execute(() -> applyDecision(server, decision));
            }).exceptionally(ex -> {
                // In case of errors: do not block anything, at most log
                LOGGER.error("LLM error: {}", ex.getMessage());
                return null;
            });
        });


        ServerTickEvents.END_SERVER_TICK.register(server -> {

            ServerWorld world = findModeratorWorld(server);
            if (checked && currentAvatarId != null) {
                // Pull a chunk loader along with the Avatar if there is an Avatar
                if (world != null) {
                    Entity moderator = findModeratorEntity(world);
                    if (moderator != null) {
                        updateChunkAnchor(world, moderator);
                    }
                }
                // Let's check once for a lingering mob at server start
            } else {
                if (world != null) {
                    // If an old Mob was found, lets reuse it
                    if (searchModeratorAvatar(world)) {
                        String feedback = currentModeratorAvatar();
                        LOGGER.info(feedback);
                        //LlmClient.sendFeedbackAsync(feedback)
                        //        .thenAccept(dec -> applyDecision(server, dec));

                    }
                }
                checked = true;
            }
        });

    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {
        switch (decision.action()) {

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

            case FOLLOW -> {
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
                    case "FOLLOW" -> {
                        if (ModActions.stopAllGoals((ServerWorld) currentAvatarWorld, currentAvatarId)) {
                            feedback = ConfigLoader.lang.feedback_21;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
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
                if (Objects.equals(decision.value(), "ME")) {
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

            case WHISPER, WARN, KICK, BAN -> {
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