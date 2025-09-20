package com.nomoneypirate.actions;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.mojang.authlib.GameProfile;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.entity.ModAvatar;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.locations.Location;
import com.nomoneypirate.locations.LocationManager;
import com.nomoneypirate.profiles.PlayerManager;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class ModDecisions {

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {

        switch (decision.action()) {

            case IGNORE -> {
                // Feedback
                String feedback = ConfigLoader.lang.feedback_44;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case STOPACTION -> {
                String feedback = "";
                switch (decision.value()) {
                    case "ALL" -> {
                        if (ModActions.stopAllGoals((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId)) {
                            feedback = ConfigLoader.lang.feedback_19;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }
                    case "FOLLOWING" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, "FOLLOWING")) {
                            feedback = ConfigLoader.lang.feedback_21;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }

                    case "LOOKINGAT" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, "LOOKINGAT")) {
                            feedback = ConfigLoader.lang.feedback_23;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }

                    case "MOVINGAROUND" -> {
                        if (ModActions.stopSpecificGoal((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, "MOVINGAROUND")) {
                            feedback = ConfigLoader.lang.feedback_26;
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                    }
                }
                // Feedback   
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case CHAT -> {
                // Chat Output
                Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value(), Formatting.BLUE, Formatting.WHITE, false, false, false);
                server.getPlayerManager().broadcast(message, false);
                // Feedback
                String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value(), "Chat");
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback
                String feedback = ConfigLoader.lang.feedback_01.formatted(list);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case TELEPORT -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                if (world != null) {
                    if (Objects.equals(decision.value(), "AVATAR")) {
                        feedback = ModActions.teleportAvatar(world, ModAvatar.currentAvatarId, decision.value2());
                    }
                    else {
                        feedback = ModActions.teleportPlayer(world, ModAvatar.currentAvatarId, decision.value());
                    }
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case TPTOPOSITION -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                if (world != null) {
                    // Parse position
                    Position pos = parsePosition(decision.value2(), "TELEPORTPOSITION");
                    double posX = pos.x();
                    double posZ = pos.z();

                    if (pos.valid()) {
                        if (Objects.equals(decision.value(), "AVATAR")) {
                            feedback = ModActions.teleportPositionAvatar(world, ModAvatar.currentAvatarId, posX, posZ);
                        } else {
                            feedback = ModActions.teleportPositionPlayer(world, decision.value(), posX, posZ);
                        }
                    }
                    else {
                        feedback = ConfigLoader.lang.feedback_61;
                    }
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case SPAWNAVATAR -> {
                String feedback;
                // Parse position
                Position pos = parsePosition(decision.value3(), "SPAWNAVATAR");
                if (pos.valid()) {
                    ModAvatar.currentAvatarPosX = (int)pos.x();
                    ModAvatar.currentAvatarPosZ = (int)pos.z();
                    feedback = ModAvatar.spawnModeratorAvatar(server, decision.value(), decision.value2(), ModAvatar.currentAvatarPosX, ModAvatar.currentAvatarPosZ);
                }
                else {
                    feedback = ConfigLoader.lang.feedback_61;
                }
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case DESPAWNAVATAR -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                // Feedback
                feedback = ModAvatar.despawnModeratorAvatar(world);
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case WHEREIS -> {
                String feedback;
                if (Objects.equals(decision.value(), "AVATAR")) {
                    feedback = ModActions.whereIs(server, "", ModAvatar.currentAvatarId);
                }
                else {
                    feedback = ModActions.whereIs(server, decision.value(), null);
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case WHOIS -> {
                String feedback;
                String playerName = decision.value();
                if (playerName != null) {
                    if (PlayerManager.isKnown(playerName)) {
                        feedback = ConfigLoader.lang.feedback_64.formatted(playerName, PlayerManager.getProfile(playerName));
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
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case PLAYERMEM -> {
                String feedback;
                String playerName = decision.value();
                if (playerName != null) {
                    if (PlayerManager.isKnown(playerName)) {
                        PlayerManager.addTag(playerName, decision.value2());
                        feedback = ConfigLoader.lang.feedback_65.formatted(playerName, decision.value2());
                    }
                    else {
                        // First contact
                        PlayerManager.addPlayer(playerName);
                        feedback = ConfigLoader.lang.feedback_63.formatted(decision.value2(), playerName);
                        PlayerManager.addTag(playerName, decision.value2());
                    }
                }
                else {
                    feedback = ConfigLoader.lang.feedback_02;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case FEEDBACK -> {
                // Feedback
                String feedback = decision.value();
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case SERVERRULES -> {
                // Feedback
                String feedback = ConfigLoader.lang.serverRules;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }
            case ACTIONEXAMPLES -> {
                // Feedback
                String feedback = ConfigLoader.lang.actionExamples;
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case GOTOPOSITION -> {
                String feedback;
                // Parse position
                Position pos = parsePosition(decision.value(), "GOTOPOSITION");
                double posX = pos.x();
                double posZ = pos.z();
                if (pos.valid()) {
                    feedback = ModActions.startGotoPosition((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, posX, posZ);
                }
                else {
                    feedback = ConfigLoader.lang.feedback_61;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
            }

            case CHANGEWEATHER -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeWeather(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case CHANGETIME -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.changeTime(world, decision.value());
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case SLEEP -> {
                String feedback;
                ServerWorld world = ModAvatar.findModeratorWorld(server);
                if (world != null) {
                    feedback = ModActions.sleepAtNight(world);
                    // Feedback
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            }

            case MOVEAROUND -> {
                String feedback;

                if (ModActions.startMoveAround((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, Double.parseDouble(decision.value()))) {
                    feedback = ConfigLoader.lang.feedback_25.formatted(decision.value());
                } else {
                    feedback = ConfigLoader.lang.feedback_18;
                }
                // Feedback
                LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
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
                        ConfigLoader.lang.contextFeedback_03.formatted(feedback)
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
                        ConfigLoader.lang.contextFeedback_03.formatted(feedback)
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
                        ConfigLoader.lang.contextFeedback_03.formatted(feedback)
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
                        ConfigLoader.lang.contextFeedback_03.formatted(feedback)
                ).thenAccept(dec -> applyDecision(server, dec));
            }

            case WHISPER, WARN, KICK, BAN, FOLLOWPLAYER, LOOKATPLAYER, GOTOPLAYER, DAMAGEPLAYER, CLEARINVENTORY, KILLPLAYER, GIVEPLAYER -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String feedback = ConfigLoader.lang.feedback_07.formatted(decision.value2());
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> {
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.WHITE, false, true, false);
                        player.sendMessage(message,false);
                        // Feedback
                        String feedback = ConfigLoader.lang.feedback_45.formatted(decision.value2(), decision.value());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case WARN -> {
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.RED, false, true, false);
                        server.getPlayerManager().broadcast(message, false);
                        // Feedback
                        String feedback = ConfigLoader.lang.feedback_08.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case KICK -> {
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.RED, true, false, true);
                        player.networkHandler.disconnect(message);
                        // Feedback
                        String feedback = ConfigLoader.lang.feedback_09.formatted(decision.value(), decision.value2());
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case FOLLOWPLAYER -> {
                        String feedback;
                        if (ModActions.startFollowPlayer((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_20.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case LOOKATPLAYER -> {
                        String feedback;
                        if (ModActions.startLookingAtPlayer((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, decision.value())) {
                            feedback = ConfigLoader.lang.feedback_22.formatted(decision.value());
                        } else {
                            feedback = ConfigLoader.lang.feedback_18;
                        }
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case GOTOPLAYER -> {
                        String feedback = ModActions.startGotoPlayer((ServerWorld) ModAvatar.currentAvatarWorld, ModAvatar.currentAvatarId, decision.value());
                        // Feedback
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                    }

                    case DAMAGEPLAYER -> {
                        String feedback;
                        ServerWorld world = ModAvatar.findModeratorWorld(server);
                        if (world != null) {
                            // Parse number
                            Number num = parseNumber(decision.value2(), "DAMAGEPLAYER");
                            int amount = num.number();
                            if (num.valid()) {
                                feedback = ModActions.damagePlayer(world, decision.value(), amount);
                            }
                            else {
                                feedback = ConfigLoader.lang.feedback_62;
                            }
                            // Feedback
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        }
                    }

                    case CLEARINVENTORY -> {
                        String feedback;
                        ServerWorld world = ModAvatar.findModeratorWorld(server);
                        if (world != null) {
                            feedback = ModActions.clearInventory(world, decision.value());
                            // Feedback
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        }
                    }

                    case KILLPLAYER -> {
                        String feedback;
                        ServerWorld world = ModAvatar.findModeratorWorld(server);
                        if (world != null) {
                            feedback = ModActions.killPlayer(world, decision.value());
                            // Feedback
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        }
                    }

                    case GIVEPLAYER -> {
                        String feedback;
                        ServerWorld world = ModAvatar.findModeratorWorld(server);
                        if (world != null) {
                            // Parse number
                            Number num = parseNumber(decision.value3(), "DAMAGEPLAYER");
                            int amount = num.number();
                            if (num.valid()) {
                                feedback = ModActions.givePlayer(world, decision.value(), decision.value2(), amount);
                            }
                            else {
                                feedback = ConfigLoader.lang.feedback_62;
                            }
                            // Feedback
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        }
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = ConfigLoader.lang.feedback_10;
                            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                            return;
                        }
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.DARK_RED, true, false, true);
                        player.networkHandler.disconnect(message);

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
                        LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }
                }
            }

            case STOPCHAIN -> // Stop action mode
                    ModEvents.actionMode = false;

        }

    }

    private static @NotNull BannedPlayerEntry getBannedPlayerEntry(ModerationDecision decision, ServerPlayerEntity player) {
        GameProfile profile = player.getGameProfile();
        Date now = new Date();
        String reason = decision.value2();
        String source = "[" + ConfigLoader.config.moderatorName + "]";
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
                        LOGGER.error("NumberFormatException: ModDecisions.java -> parsePosition -> case {} {}", caseString, e.toString());
                }
            } else {
                if (ConfigLoader.config.modLogging)
                    LOGGER.warn("Parts length problem: ModDecisions.java -> case {}", caseString);
            }
        }
        return new Position(posX, posZ, valid);
    }

    public record Number(int number, boolean valid) {}

    public static Number parseNumber(String input, String caseString) {
        int number = 0;
        boolean valid = false;

        if (input != null && !input.isEmpty()) {

                try {
                    number = Integer.parseInt(input);
                    valid = true;
                } catch (NumberFormatException e) {
                    if (ConfigLoader.config.modLogging)
                        LOGGER.error("NumberFormatException: ModDecisions.java -> parseNumber -> case {} {}", caseString, e.toString());
                }

        }
        return new Number(number, valid);
    }

    public static Text formatChatOutput(String prefix, String text, Formatting prefixColor, Formatting textColor, boolean bold, boolean italic, boolean underline) {
        // Build Text
        if (prefix.isEmpty()) prefix = "->";
        return Text.empty()
                .append(Text.literal(prefix).styled(style -> style.withColor(prefixColor)))
                .append(Text.literal(text).styled(style -> style.withColor(textColor).withBold(bold).withItalic(italic).withUnderline(underline)));
    }

}
