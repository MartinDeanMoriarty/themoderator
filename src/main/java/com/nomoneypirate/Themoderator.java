package com.nomoneypirate;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.server.BannedPlayerEntry;
import com.mojang.authlib.GameProfile;
import com.nomoneypirate.commands.ModCommands;
import com.nomoneypirate.entity.ModAvatar;

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
        ConfigLoader.load();
        // Register mod commands
        ModCommands.registerCommands();

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Get player name
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();

            String welcomeText = ConfigLoader.config.welcomeText;
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

        // Intercept chat messages (server-side)
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
        //System.out.println("Initialized.");
        LOGGER.info("Initialized.");
    }

    // Apply the decision and translate it into an action
    private void applyDecision(MinecraftServer server, ModerationDecision decision) {
        switch (decision.action()) {
            case CHAT -> server.getPlayerManager().broadcast(
                    Text.literal("The Moderator: " + decision.value()),
                    false
            );

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback ans LLM
                String feedback = "Current players: " + list;
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case SPAWNAVATAR -> {
                String feedback;
                ModAvatar.currentMobPosX = 0;
                ModAvatar.currentMobPosZ = 0;

                if (!decision.value2().isEmpty()) {
                    String[] parts = decision.value2().trim().split("\\s+");
                    if (parts.length == 2) {
                        try {
                            ModAvatar.currentMobPosX = Integer.parseInt(parts[0]);
                            ModAvatar.currentMobPosZ = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            feedback = "Incorrect usage: " + decision.value2();
                            // Feedback
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                        }
                    } else {
                        feedback = "Incorrect usage: " + decision.value2();
                        // Feedback
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                    }
                }

                //MinecraftServer server = world.getServer();
                if (ModAvatar.spawnModeratorAvatar(server.getOverworld(), decision.value(),ModAvatar.currentMobPosX,ModAvatar.currentMobPosZ)) {
                    feedback = "Avatar spawned as: "+ decision.value2() +". At:  " + ModAvatar.currentMobPosX + "  " + ModAvatar.currentMobPosZ;
                } else {
                    feedback = "Spawning was not possible.";
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case DESPAWNAVATAR -> {
                String feedback;

                if (ModAvatar.despawnModeratorAvatar(server.getOverworld())) {
                    feedback = "Avatar despawned.";
                } else {
                    feedback = "No Avatar to despawn.";
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case WHEREIS -> {
                String feedback = "";

                if (Objects.equals(decision.value(), "PLAYER")) {
                    feedback = whereIs(server.getOverworld(), decision.value(), null);
                }
                if (Objects.equals(decision.value(), "ME")) {
                    feedback = whereIs(server.getOverworld(), "", ModAvatar.currentMobId);
                }
                // Feedback
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case WHISPER, WARN, KICK, BAN -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String info = "Player '" + decision.value() + "' not found.";
                    LOGGER.info(info);
                    LlmClient.sendFeedbackAsync(info)
                            .thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> player.sendMessage(
                            Text.literal("The Moderator: " + decision.value2()),
                            false
                    );

                    case WARN -> {
                        player.sendMessage(
                                Text.literal(
                                        "The Moderator: "
                                                + decision.value2()
                                ),
                                false
                        );
                        String feedback = "Warned "
                                + decision.value()
                                + " with message: \""
                                + decision.value2()
                                + "\"";
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info(feedback);
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: "
                                                + decision.value2()
                                )
                        );
                        String feedback = "Kicked "
                                + decision.value()
                                + " with reason: \""
                                + decision.value2()
                                + "\"";
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info(feedback);
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = "The BAN command is not available.";
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                            return;
                        }
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: "
                                                + decision.value2()
                                )
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
                        String feedback = "Banned "
                                + decision.value()
                                + " with reason: \""
                                + decision.value2()
                                + "\"";
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

    public static String whereIs(ServerWorld world, String name, UUID currentMobUuid) {
        // Player
        if (name.isEmpty() && currentMobUuid == null) {
            return "No entity specified.";
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(name);
        if (player != null) {
            BlockPos pos = player.getBlockPos();
            return String.format("Player '%s' is at X: %d, Y: %d, Z: %d", name, pos.getX(), pos.getY(), pos.getZ());
        }

        // Moderator mob
        Entity entity = Objects.requireNonNull(world.getServer().getWorld(world.getRegistryKey())).getEntity(currentMobUuid);
        if (entity != null) {
            BlockPos pos = entity.getBlockPos();
            return String.format("Your '%s' is at X: %d, Y: %d, Z: %d", entity.getName().getString(), pos.getX(), pos.getY(), pos.getZ());
        }
        return "Entity not found.";
    }

}