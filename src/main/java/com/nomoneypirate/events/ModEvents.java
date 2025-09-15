package com.nomoneypirate.events;

import static com.nomoneypirate.Themoderator.LOGGER;
import static com.nomoneypirate.entity.ModAvatar.*;
import com.mojang.authlib.GameProfile;
import com.nomoneypirate.actions.ModActions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.llm.ModerationScheduler;
import com.nomoneypirate.locations.Location;
import com.nomoneypirate.locations.LocationManager;
import com.nomoneypirate.profiles.PlayerManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.*;
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

    public static MinecraftServer SERVER;
    private static Boolean actionMode = false;

    public static void registerEvents() {

        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVER = null;
            ModerationScheduler.shutdown();
        });

        // "Update-Loop" and world ready event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Scheduler
            if (ConfigLoader.config.scheduledModeration) {
                tickCounter++;
                if (tickCounter >= INTERVAL_TICKS) {
                    tickCounter = 0;
                    ModerationScheduler.runScheduledTask(server);
                }
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
                        // Avatar (not)found message
                        feedback.append(" Avatar: ").append(currentModeratorAvatar());
                    }
                    // Only send if there is a feedback
                    // AND only send if it is a dedicated server
                     if (!feedback.isEmpty() && server instanceof DedicatedServer) {
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback.toString())).thenAccept(dec -> applyDecision(server, dec));
                    }
                    checked = true;
                }
            }
        });

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!actionMode) {
                // Start action mode. Stopped with STOPCHAIN
                actionMode = true;
                // Get player name
                ServerPlayerEntity player = handler.getPlayer();
                String playerName = player.getName().getString();

                String welcomeText = ConfigLoader.lang.playerJoined.formatted(playerName);
                // Asynchronous to the LLM

                Objects.requireNonNull(LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, ConfigLoader.lang.feedback_47.formatted(welcomeText))).thenAccept(decision -> {
                    // Back to the server thread
                    server.execute(() -> applyDecision(server, decision));
                }).exceptionally(ex -> {
                    // In case of errors: do not block anything, at most log
                    if (ConfigLoader.config.modLogging) LOGGER.error("Welcoming failed: {}", ex.getMessage());
                    return null;
                });
            }
            else {
                // Chat Output: Model is busy
                if (ModEvents.SERVER != null) ModEvents.SERVER.getPlayerManager().broadcast(Text.literal(ConfigLoader.lang.playerFeedback),false);

            }
        });

        // Intercept game messages for moderation scheduler
        ServerMessageEvents.GAME_MESSAGE.register((server, text, params) -> {
            String content = text.getString();
            // Add Game Messages to moderation scheduler
            if (ConfigLoader.config.scheduledModeration) {
                String serverMessage = ConfigLoader.lang.feedback_48.formatted(content);
                ModerationScheduler.addMessage(serverMessage);
            }
        });

        //Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (!actionMode) {
                // Start action mode. Stopped with STOPCHAIN
                actionMode = true;
                MinecraftServer server = sender.getServer();
                if (server == null) return;

                String playerName = sender.getName().getString();
                String content = message.getContent().getString();

                // Add Chat Messages to moderation scheduler
                String chatMessage = ConfigLoader.lang.feedback_47.formatted(ConfigLoader.lang.feedback_51.formatted(playerName, content));
                if (ConfigLoader.config.scheduledModeration) {
                    ModerationScheduler.addMessage(chatMessage);
                }

                // Keyword-Check
                if (ConfigLoader.config.activationKeywords.stream().anyMatch(content.toLowerCase()::contains)) {
                    long now = System.currentTimeMillis();
                    long last = cooldowns.getOrDefault("Chat", 0L);

                    // Cooldown
                    if (now - last < COOLDOWN_MILLIS) {
                        // Chat Output: Model is busy
                       if (SERVER != null) SERVER.getPlayerManager().broadcast(Text.literal(ConfigLoader.lang.playerFeedback),false);
                       return;
                    }
                    cooldowns.put("Chat", now);

                    // Async-Request
                    LlmClient.moderateAsync(LlmClient.ModerationType.MODERATION, chatMessage).thenAccept(decision -> server.execute(() -> applyDecision(server, decision))).exceptionally(ex -> {
                    if (ConfigLoader.config.modLogging) LOGGER.error("LLM error: {}", ex.getMessage());
                    return null;
                });
            }
        }
            else {
            // Chat Output: Model is busy
            if (ModEvents.SERVER != null) ModEvents.SERVER.getPlayerManager().broadcast(Text.literal(ConfigLoader.lang.playerFeedback),false);
        }
        });

        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("4of6 Events Initialized.");
    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {

        switch (decision.action()) {

            case IGNORE -> {
                // Feedback
                String feedback = ConfigLoader.lang.feedback_44; 
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case CHAT -> {
                // Chat Output
                server.getPlayerManager().broadcast(Text.literal("The Moderator: " + decision.value()), false);
                // Feedback
                String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value(), "Chat");
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback
                String feedback = ConfigLoader.lang.feedback_01.formatted(list);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case TELEPORTPOSITION -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    // Parse position
                    Position pos = parsePosition(decision.value2(), "TELEPORTPOSITION");
                    double posX = pos.x();
                    double posZ = pos.z();

                    if (pos.valid()) {
                        if (Objects.equals(decision.value(), "AVATAR")) {
                            feedback = ModActions.teleportPositionAvatar(world, currentAvatarId, posX, posZ);
                        } else {
                            feedback = ModActions.teleportPositionPlayer(world, decision.value(), posX, posZ);
                        }
                    }
                    else {
                        feedback = ConfigLoader.lang.feedback_61;
                    }
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case SPAWNAVATAR -> {
                String feedback;
                currentAvatarPosX = 0;
                currentAvatarPosZ = 0;
                // Parse position
                Position pos = parsePosition(decision.value3(), "SPAWNAVATAR");
                double currentAvatarPosX = pos.x();
                double currentAvatarPosZ = pos.z();
                if (pos.valid()) {
                feedback = spawnModeratorAvatar(server, decision.value(), decision.value2(), currentAvatarPosX, currentAvatarPosZ);
                }
                else {
                    feedback = ConfigLoader.lang.feedback_61;
                }
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case DESPAWNAVATAR -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                // Feedback
                feedback = despawnModeratorAvatar(world);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case WHOIS -> {
                String feedback;
                String playerName = decision.value();
                if (playerName != null) {
                    if (PlayerManager.isKnown(playerName)) {
                        feedback = ConfigLoader.lang.feedback_64.formatted(playerName, PlayerManager.getProfile(playerName).toString());
                    }
                    else {
                        // First contact
                        PlayerManager.addPlayer(playerName);
                        feedback = ConfigLoader.lang.feedback_63.formatted(playerName);
                    }
                }
                else {
                    feedback = ConfigLoader.lang.feedback_02;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case PLAYERMEM -> {
                String feedback;
                String playerName = decision.value();
                if (playerName != null) {
                    if (PlayerManager.isKnown(playerName)) {
                        PlayerManager.addTag(playerName, decision.value2());
                        feedback = ConfigLoader.lang.feedback_65.formatted(decision.value2(), playerName);
                    }
                    else {
                        // First contact
                        PlayerManager.addPlayer(playerName);
                        feedback = ConfigLoader.lang.feedback_65.formatted(decision.value2(), playerName);
                        PlayerManager.addTag(playerName, decision.value2());
                    }
                }
                else {
                    feedback = ConfigLoader.lang.feedback_02;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case FEEDBACK -> {
                // Feedback
                String feedback = decision.value();
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case SERVERRULES -> {
                // Feedback
                String feedback = ConfigLoader.lang.serverRules;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case ACTIONEXAMPLES -> {
                // Feedback
                String feedback = ConfigLoader.lang.actionExamples;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case GOTOPOSITION -> {
                String feedback;
                // Parse position
                Position pos = parsePosition(decision.value(), "GOTOPOSITION");
                double posX = pos.x();
                double posZ = pos.z();
                if (pos.valid()) {
                 feedback = ModActions.startGotoPosition((ServerWorld) currentAvatarWorld, currentAvatarId, posX, posZ);
                }
                else {
                    feedback = ConfigLoader.lang.feedback_61;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case DAMAGEPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.damagePlayer(world, decision.value(), Integer.parseInt(decision.value2()));
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CLEARINVENTORY -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.clearInventory(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case KILLPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.killPlayer(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case GIVEPLAYER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.givePlayer(world, decision.value(), Item.byRawId(Integer.parseInt(decision.value2())), Integer.parseInt(decision.value3()));
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CHANGEWEATHER -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeWeather(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CHANGETIME -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeTime(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case SLEEP -> {
                String feedback;
                ServerWorld world = findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.sleepAtNight(world);
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case MOVEAROUND -> {
                String feedback;
                if (ModActions.startMoveAround((ServerWorld) currentAvatarWorld, currentAvatarId, Double.parseDouble(decision.value()))) {
                    feedback = ConfigLoader.lang.feedback_25.formatted(decision.value());
                } else {
                    feedback = ConfigLoader.lang.feedback_18;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case LISTLOCATIONS -> {
                String feedback;

                try {
                    List<Location> locations = LocationManager.listLocations();

                    if (locations.isEmpty()) {
                        feedback = ConfigLoader.lang.feedback_58; // No Locations
                    } else {
                        String locationList = locations.stream()
                                .map(loc -> loc.name + " (" + loc.x + ", " + loc.z + ")")
                                .collect(Collectors.joining(", "));

                        feedback = ConfigLoader.lang.feedback_52.formatted(locationList); // All Locations
                    }
                } catch (Exception e) {
                    if (ConfigLoader.config.modLogging) LOGGER.error("Error listing locations: {}", e.getMessage());
                    feedback = ConfigLoader.lang.feedback_02; // error
                }

                LlmClient.moderateAsync(
                        LlmClient.ModerationType.FEEDBACK,
                        ConfigLoader.lang.feedback_49.formatted(feedback)
                ).thenAccept(dec -> applyDecision(server, dec));
            }

            case GETLOCATION -> {
                String locationName = decision.value();
                String feedback;

                try {
                    Location loc = LocationManager.getLocation(locationName);

                    if (loc == null) {
                        feedback = ConfigLoader.lang.feedback_56.formatted(locationName); // No Location
                    } else {
                        feedback = ConfigLoader.lang.feedback_53.formatted(loc.name, loc.dim, loc.x, loc.z); // Output
                    }
                } catch (Exception e) {
                    if (ConfigLoader.config.modLogging) LOGGER.error("Error getting location '{}': {}", locationName, e.getMessage());
                    feedback = ConfigLoader.lang.feedback_02;
                }

                LlmClient.moderateAsync(
                        LlmClient.ModerationType.FEEDBACK,
                        ConfigLoader.lang.feedback_49.formatted(feedback)
                ).thenAccept(dec -> applyDecision(server, dec));
            }

            case SETLOCATION -> {
                String feedback;
                Position pos = parsePosition(decision.value3(), "SETLOCATION");
                double posX = pos.x();
                double posZ = pos.z();
                if (pos.valid()) {
                    try {
                        if (!decision.value2().isEmpty()) {
                            LocationManager.setLocation(decision.value(), decision.value2(), (int) posX, (int) posZ);
                            feedback = ConfigLoader.lang.feedback_54.formatted(decision.value2()); // Saved
                        } else {
                            feedback = ConfigLoader.lang.feedback_59.formatted(decision.value2()); // Error
                        }
                    } catch (Exception e) {
                        if (ConfigLoader.config.modLogging) LOGGER.error("Error setting location '{}': {}", decision.value2(), e.getMessage());
                        feedback = ConfigLoader.lang.feedback_60.formatted(decision.value2()); // Error
                    }
                }
                else {
                    feedback = ConfigLoader.lang.feedback_61;
                }

                LlmClient.moderateAsync(
                        LlmClient.ModerationType.FEEDBACK,
                        ConfigLoader.lang.feedback_49.formatted(feedback)
                ).thenAccept(dec -> applyDecision(server, dec));
            }

            case REMLOCATION -> {
                String locationName = decision.value();
                String feedback;

                try {
                    boolean removed = LocationManager.remLocation(locationName);
                    if (removed) {
                        feedback = ConfigLoader.lang.feedback_55.formatted(locationName); // Erfolgreich gelöscht
                    } else {
                        feedback = ConfigLoader.lang.feedback_56.formatted(locationName); // Nicht gefunden oder nicht gelöscht
                    }
                } catch (Exception e) {
                    if (ConfigLoader.config.modLogging) LOGGER.error("Error removing location '{}': {}", locationName, e.getMessage());
                    feedback = ConfigLoader.lang.feedback_57.formatted(locationName); // Fehler beim Löschen
                }

                LlmClient.moderateAsync(
                        LlmClient.ModerationType.FEEDBACK,
                        ConfigLoader.lang.feedback_49.formatted(feedback)
                ).thenAccept(dec -> applyDecision(server, dec));
            }

            case WHISPER, WARN, KICK, BAN, FOLLOWPLAYER, LOOKATPLAYER, GOTOPLAYER -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String feedback = ConfigLoader.lang.feedback_07.formatted(decision.value2());
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> {
                        player.sendMessage(Text.literal("The Moderator: " + decision.value2()),false);
                        String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value2(), decision.value());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case WARN -> {
                        player.sendMessage(
                                Text.literal("The Moderator: " + decision.value2()),false
                        );
                        String feedback = ConfigLoader.lang.feedback_08.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: " + decision.value2())
                        );
                        String feedback = ConfigLoader.lang.feedback_09.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case LOOKATPLAYER -> {
                        String feedback;
                        if (ModActions.startLookingAtPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_22.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case GOTOPLAYER -> {
                        String feedback = ModActions.startGotoPlayer((ServerWorld) currentAvatarWorld, currentAvatarId, decision.value());
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = ConfigLoader.lang.feedback_10;
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }
                }
            }

            case STOPCHAIN -> // Stop action mode
                    actionMode = false;

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

    public record Position(double x, double z, boolean valid) {}

    public static Position parsePosition(String input, String caseString) {
        double posX = 0;
        double posZ = 0;
        boolean valid = false;

        if (input != null && !input.isEmpty()) {
            String[] parts = input.trim().split("\\s+");
            if (parts.length == 2) {
                try {
                    posX = Integer.parseInt(parts[0]);
                    posZ = Integer.parseInt(parts[1]);
                    valid = true;
                } catch (NumberFormatException e) {
                    if (ConfigLoader.config.modLogging)
                        LOGGER.error("NumberFormatException: ModEvent.java -> case {} {}", caseString, e.toString());
                }
            } else {
                if (ConfigLoader.config.modLogging)
                    LOGGER.warn("Parts length problem: ModEvent.java -> case {}", caseString);
            }
        }

        return new Position(posX, posZ, valid);
    }


}