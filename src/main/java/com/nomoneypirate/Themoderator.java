package com.nomoneypirate;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.commands.ModCommands;
import com.nomoneypirate.entity.ModAvatar;
import com.nomoneypirate.actions.ModActions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.server.BannedPlayerEntry;
import com.mojang.authlib.GameProfile;

import static com.nomoneypirate.entity.ModAvatar.currentModeratorAvatar;
import static com.nomoneypirate.entity.ModAvatar.searchModeratorAvatar;

public class Themoderator implements ModInitializer {
	public static final String MOD_ID = "themoderator";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        // Load configuration file
        ConfigLoader.loadConfig();
        // Load language file
        ConfigLoader.loadLang();
        // Register mod commands
        ModCommands.registerCommands();

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

//        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
//            // Check for a lingering Mob, which may be still there after a server restart
//            // Search all dimensions
//            for (ServerWorld world : server.getWorlds()) {
//                if (searchModeratorAvatar(world)) {
//                    // An old Mob was found, lets reuse it and send a feedback to the llm
//                    String feedback = currentModeratorAvatar();
//                    LlmClient.sendFeedbackAsync(feedback)
//                            .thenAccept(dec -> applyDecision(server, dec));
//                    break; // Just stop search when found one
//                }
//            }
//        });

//        ServerTickEvents.END_SERVER_TICK.register(server -> {
//            // Server and worlds are ready, so send a feedback to the llm
//            String feedback = ConfigLoader.lang.feedback_17;
//            LlmClient.sendFeedbackAsync(feedback)
//                    .thenAccept(dec -> applyDecision(server, dec));
//
//            // Pull a chunk loader along with the Avatar if there is an Avatar
//            ServerWorld world = ModAvatar.findModeratorWorld(server);
//            if (world != null) {
//                Entity moderator = ModAvatar.findModeratorEntity(world);
//                if (moderator != null) {
//                    ModAvatar.updateChunkAnchor(world, moderator);
//                }
//            }
//        });

        System.out.println(MOD_ID + "Initialized.");
        LOGGER.info("Initialized.");
    }

    // Apply the decision and translate it into an action
    public void applyDecision(MinecraftServer server, ModerationDecision decision) {
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
                if (ModActions.startFollowPlayer((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, decision.value())) {
                    feedback = ConfigLoader.lang.feedback_20.formatted(decision.value());
                } else {
                    feedback = ConfigLoader.lang.feedback_18;
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case STOP -> {
                String feedback;
                if (ModActions.stopAllGoals((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId)) {
                    feedback = ConfigLoader.lang.feedback_19;
                } else {
                    feedback = ConfigLoader.lang.feedback_18;
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case SPAWNAVATAR -> {
                String feedback;
                ModAvatar.currentAvatarPosX = 0;
                ModAvatar.currentAvatarPosZ = 0;

                if (!decision.value2().isEmpty()) {
                    String[] parts = decision.value2().trim().split("\\s+");
                    if (parts.length == 2) {
                        try {
                            ModAvatar.currentAvatarPosX = Integer.parseInt(parts[0]);
                            ModAvatar.currentAvatarPosZ = Integer.parseInt(parts[1]);
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
                if (ModAvatar.spawnModeratorAvatar(server.getOverworld(), decision.value(), ModAvatar.currentAvatarPosX, ModAvatar.currentAvatarPosZ)) {
                    feedback = ConfigLoader.lang.feedback_03.formatted(decision.value2(), ModAvatar.currentAvatarPosX, ModAvatar.currentAvatarPosZ);
                } else {
                    feedback = ConfigLoader.lang.feedback_04;
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case DESPAWNAVATAR -> {
                String feedback;

                if (ModAvatar.despawnModeratorAvatar(server.getOverworld())) {
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
                    feedback = ModActions.whereIs(server.getOverworld(), "", ModAvatar.currentAvatarId);
                }
                else {
                    feedback = ModActions.whereIs(server.getOverworld(), decision.value(), null);
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
                            GameProfile profile = player.getGameProfile();
                            Date now = new Date();
                            String reason = decision.value2();
                            String source = "[The Moderator]";
                            Date expiry = null; // null = permanent

                            BannedPlayerEntry entry = new BannedPlayerEntry(
                                    profile,
                                    now,
                                    source,
                                    expiry,
                                    reason
                            );
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

}