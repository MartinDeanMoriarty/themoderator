package com.nomoneypirate.actions;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.locations.Location;
import com.nomoneypirate.locations.LocationManager;
import com.nomoneypirate.profiles.PlayerManager;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
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
import java.util.stream.Collectors;

public class ModDecisions {

    /**
     * Sends {@code arg} to the LLM and applies whatever action it decides on next - the future
     * this hangs off completes on an arbitrary HTTP-client thread, so every continuation of the
     * feedback loop needs to hop back onto the server thread before touching any game state, and
     * needs an error handler so a failure deep in a chain doesn't just vanish silently.
     */
    public static void moderateAndApply(MinecraftServer server, LlmClient.ModerationType type, String arg) {
        LlmClient.moderateAsync(type, arg)
                .thenAccept(dec -> server.execute(() -> applyDecision(server, dec)))
                .exceptionally(ex -> {
                    if (ConfigLoader.config.modLogging) LOGGER.error("Moderation chain failed: {}", ex.getMessage());
                    return null;
                });
    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {

        switch (decision.action()) {

            case IGNORE, SELFFEEDBACK, PLAYERLIST, WHOIS, PLAYERMEM, SERVERRULES, SERVERINFO, CHANGEWEATHER,
                 CHANGETIME, LISTLOCATIONS, GETLOCATION, SETLOCATION, REMLOCATION -> {

                switch (decision.action()) {

                    // Set actionMode
                    case IGNORE -> ModEvents.actionMode = false;

                    case SELFFEEDBACK -> {
                        // Feedback
                        String feedback = decision.value();
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case PLAYERLIST -> {
                        Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                        String list = players.stream()
                                .map(p -> p.getName().getString())
                                .collect(Collectors.joining(", "));
                        // Feedback
                        String feedback = ConfigLoader.lang.payersOnlineFeedback.formatted(list);
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case WHOIS -> {
                        String feedback;
                        String playerName = decision.value();
                        if (playerName != null) {
                            if (PlayerManager.isKnown(playerName)) {
                                feedback = ConfigLoader.lang.feedback_64.formatted(playerName, PlayerManager.getProfile(playerName));
                            } else {
                                // First contact
                                PlayerManager.addPlayer(playerName);
                                feedback = ConfigLoader.lang.feedback_63.formatted(playerName);
                            }
                        } else {
                            feedback = ConfigLoader.lang.feedback_07;
                        }
                        // Feedback
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }
                    case PLAYERMEM -> {
                        String feedback;
                        String playerName = decision.value();
                        if (playerName != null) {
                            if (PlayerManager.isKnown(playerName)) {
                                PlayerManager.addTag(playerName, decision.value2());
                                feedback = ConfigLoader.lang.feedback_65.formatted(playerName, decision.value2());
                            } else {
                                // First contact
                                PlayerManager.addPlayer(playerName);
                                feedback = ConfigLoader.lang.feedback_63.formatted(decision.value2(), playerName);
                                PlayerManager.addTag(playerName, decision.value2());
                            }
                        } else {
                            feedback = ConfigLoader.lang.feedback_07;
                        }
                        // Feedback
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case SERVERRULES -> {
                        // Feedback
                        String feedback = ConfigLoader.lang.serverRules;
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case SERVERINFO -> {
                        // Feedback
                        String feedback = ConfigLoader.lang.serverInfo;
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case CHANGEWEATHER -> {
                        String feedback;
                        ServerWorld world = server.getOverworld();
                        if (world != null) {
                            feedback = ModActions.changeWeather(world, decision.value());
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case CHANGETIME -> {
                        String feedback;
                        ServerWorld world = server.getOverworld();
                        if (world != null) {
                            feedback = ModActions.changeTime(world, decision.value());
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
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
                            if (ConfigLoader.config.modLogging)
                                LOGGER.error("Error listing locations: {}", e.getMessage());
                            feedback = ConfigLoader.lang.exceptionFeedback; // error
                        }

                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
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
                            if (ConfigLoader.config.modLogging)
                                LOGGER.error("Error getting location '{}': {}", locationName, e.getMessage());
                            feedback = ConfigLoader.lang.exceptionFeedback;
                        }

                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case SETLOCATION -> {
                        String locationName = decision.value();
                        String feedback;
                        Position pos = parsePosition(decision.value3(), "SETLOCATION");
                        double posX = pos.x();
                        double posZ = pos.z();
                        if (pos.valid()) {
                            try {
                                if (!locationName.isEmpty()) {
                                    LocationManager.setLocation(locationName, decision.value2(), (int) posX, (int) posZ);
                                    feedback = ConfigLoader.lang.feedback_54.formatted(locationName); // Saved
                                } else {
                                    feedback = ConfigLoader.lang.feedback_59.formatted(locationName); // Error
                                }
                            } catch (Exception e) {
                                if (ConfigLoader.config.modLogging)
                                    LOGGER.error("Error setting location '{}': {}", locationName, e.getMessage());
                                feedback = ConfigLoader.lang.feedback_60.formatted(locationName); // Error
                            }
                        } else {
                            feedback = ConfigLoader.lang.feedback_61;
                        }

                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case REMLOCATION -> {
                        String locationName = decision.value();
                        String feedback;

                        try {
                            boolean removed = LocationManager.remLocation(locationName);
                            if (removed) {
                                feedback = ConfigLoader.lang.feedback_55.formatted(locationName); // Successfully deleted
                            } else {
                                feedback = ConfigLoader.lang.feedback_56.formatted(locationName); // Not found or not deleted
                            }
                        } catch (Exception e) {
                            if (ConfigLoader.config.modLogging)
                                LOGGER.error("Error removing location '{}': {}", locationName, e.getMessage());
                            feedback = ConfigLoader.lang.feedback_57.formatted(locationName); // Delete error
                        }

                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }
                }
            }

            case TELEPORT, TPTOLOCATION, WHEREIS, WARN, KICK, BAN, PARDON, DAMAGEPLAYER, CLEARINVENTORY, KILLPLAYER, GIVEPLAYER -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String feedback = ConfigLoader.lang.feedback_07.formatted(decision.value2());
                    moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    return;
                }

                ServerWorld world = player.getEntityWorld();
                String playerName = decision.value();

                switch (decision.action()) {
                    case TELEPORT -> {
                        String feedback;
                        if (world != null) {
                            // Parse position
                            Position pos = parsePosition(decision.value2(), "TELEPORTPOSITION");
                            double posX = pos.x();
                            double posZ = pos.z();

                            if (pos.valid()) {
                                feedback = ModActions.teleportPositionPlayer(world, playerName, posX, posZ);
                            } else {
                                feedback = ConfigLoader.lang.feedback_61;
                            }
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case TPTOLOCATION -> {
                        String locationName = decision.value();
                        String feedback;
                        if (world != null) {
                            try {
                                Location loc = LocationManager.getLocation(locationName);
                                if (loc == null) {
                                    feedback = ConfigLoader.lang.feedback_56.formatted(locationName); // No Location
                                } else {
                                    double posX = loc.x;
                                    double posZ = loc.z;
                                    feedback = ModActions.teleportPositionPlayer(world, playerName, posX, posZ);
                                }
                            } catch (Exception e) {
                                if (ConfigLoader.config.modLogging)
                                    LOGGER.error("Error in location '{}': {}", locationName, e.getMessage());
                                feedback = ConfigLoader.lang.exceptionFeedback;
                            }
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case WHEREIS -> {
                        // Feedback
                        String feedback = ModActions.whereIs(server, playerName);
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                    }

                    case WARN -> {
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.RED, false, true, false);
                        server.getPlayerManager().broadcast(message, false);
                        // Feedback
                        String feedback = ConfigLoader.lang.feedback_08.formatted(playerName, decision.value2());
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case KICK -> {
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.RED, true, false, true);
                        player.networkHandler.disconnect(message);
                        // Feedback
                        String feedback = ConfigLoader.lang.feedback_09.formatted(playerName, decision.value2());
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case DAMAGEPLAYER -> {
                        String feedback;
                        if (world != null) {
                            // Parse number
                            Number num = parseNumber(decision.value2(), "DAMAGEPLAYER");
                            int amount = num.number();
                            if (num.valid()) {
                                feedback = ModActions.damagePlayer(world, playerName, amount);
                            } else {
                                feedback = ConfigLoader.lang.feedback_62;
                            }
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case CLEARINVENTORY -> {
                        String feedback;
                        if (world != null) {
                            feedback = ModActions.clearInventory(world, playerName);
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case KILLPLAYER -> {
                        String feedback;
                        if (world != null) {
                            feedback = ModActions.killPlayer(world, playerName);
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case GIVEPLAYER -> {
                        String feedback;
                        if (world != null) {
                            // Parse number
                            Number num = parseNumber(decision.value3(), "DAMAGEPLAYER");
                            int amount = num.number();
                            if (num.valid()) {
                                feedback = ModActions.givePlayer(world, playerName, decision.value2(), amount);
                            } else {
                                feedback = ConfigLoader.lang.feedback_62;
                            }
                            // Feedback
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        }
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = ConfigLoader.lang.feedback_10;
                            moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                            return;
                        }
                        // Build Text
                        Text message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), Formatting.BLUE, Formatting.DARK_RED, true, false, true);
                        player.networkHandler.disconnect(message);

                        if (ConfigLoader.config.useWhitelist) {
                            // Remove Player of the whitelist
                            PlayerConfigEntry profile = new PlayerConfigEntry(player.getGameProfile());
                            WhitelistEntry entry = server.getPlayerManager().getWhitelist().get(profile);
                            if (entry != null) {
                                server.getPlayerManager().getWhitelist().remove(profile);
                                server.getPlayerManager().reloadWhitelist();
                            }
                        }
                        if (ConfigLoader.config.useBanlist) {
                            BannedPlayerEntry entry = getBannedPlayerEntry(decision, player);
                            server.getPlayerManager().getUserBanList().add(entry);
                            try {
                                server.getPlayerManager().getUserBanList().save();
                            } catch (IOException e) {
                                if (ConfigLoader.config.modLogging) LOGGER.info(String.valueOf(e));
                                throw new RuntimeException(e);
                            }
                        }
                        String feedback = ConfigLoader.lang.feedback_11.formatted(playerName, decision.value2());
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }

                    case PARDON -> {
                        if (ConfigLoader.config.useWhitelist) {
                            // Put Player on whitelist
                            PlayerConfigEntry profile = new PlayerConfigEntry(player.getGameProfile());
                            WhitelistEntry entry = server.getPlayerManager().getWhitelist().get(profile);
                            if (entry == null) {
                                WhitelistEntry new_entry = new WhitelistEntry(profile);
                                server.getPlayerManager().getWhitelist().add(new_entry);
                                server.getPlayerManager().reloadWhitelist();
                            }
                        }
                        if (ConfigLoader.config.useBanlist) {
                            BannedPlayerEntry entry = getBannedPlayerEntry(decision, player);
                            server.getPlayerManager().getUserBanList().remove(entry);
                            try {
                                server.getPlayerManager().getUserBanList().save();
                            } catch (IOException e) {
                                if (ConfigLoader.config.modLogging) LOGGER.info(String.valueOf(e));
                                throw new RuntimeException(e);
                            }
                        }
                        String feedback = ConfigLoader.lang.feedback_12.formatted(playerName);
                        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(feedback));
                        if (ConfigLoader.config.modLogging) LOGGER.info(feedback);
                    }
                }
            }

        }

    }

    private static @NotNull BannedPlayerEntry getBannedPlayerEntry(ModerationDecision decision, ServerPlayerEntity player) {
        PlayerConfigEntry profile = new PlayerConfigEntry(player.getGameProfile());
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

    public record Position(double x, double z, boolean valid) {
    }

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

    public record Number(int number, boolean valid) {
    }

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
