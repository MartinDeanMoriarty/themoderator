package com.nomoneypirate.actions;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.config.ModConfig;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.locations.Location;
import com.nomoneypirate.locations.LocationManager;
import com.nomoneypirate.profiles.PlayerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.nomoneypirate.llm.ModerationDecision.Action;

public class ModDecisions {

    // Actions that hurt a player - never allowed against operators while protectOperators is on
    private static final Set<Action> OPERATOR_PROTECTED = EnumSet.of(
            Action.KICK, Action.BAN, Action.KILLPLAYER, Action.DAMAGEPLAYER, Action.CLEARINVENTORY);

    private static final int MAX_LOCATION_NAME = 32;
    private static final int MAX_LOCATIONS = 100;
    private static final int MAX_NOTE_LENGTH = 100;

    // How many feedback rounds the current chain has used (action -> feedback -> next action ...)
    private static final AtomicInteger chainDepth = new AtomicInteger();

    /** Marks the start of a new chain - for requests that begin with a notice instead of a player message. */
    public static void startChain() {
        chainDepth.set(0);
    }

    /** The chain is over: the moderator is available for the next message again. */
    public static void endChain() {
        chainDepth.set(0);
        ModEvents.setBusy(false);
    }

    /**
     * Sends {@code arg} to the LLM and applies whatever action it decides on next - the future
     * this hangs off completes on an arbitrary HTTP-client thread, so every continuation of the
     * feedback loop needs to hop back onto the server thread before touching any game state, and
     * needs an error handler so a failure deep in a chain doesn't just vanish silently.
     * Chains are limited to {@code maxActionChain} feedback rounds so a confused model can't loop forever.
     */
    public static void moderateAndApply(MinecraftServer server, LlmClient.ModerationType type, String arg) {
        if (type == LlmClient.ModerationType.FEEDBACK) {
            int limit = Math.max(1, ConfigLoader.config.maxActionChain);
            if (chainDepth.incrementAndGet() > limit) {
                LOGGER.warn("Action chain stopped after {} feedback rounds (maxActionChain).", limit);
                endChain();
                return;
            }
        } else {
            chainDepth.set(0);
        }
        LlmClient.moderateAsync(type, arg)
                .thenAccept(dec -> server.execute(() -> {
                    try {
                        applyDecision(server, dec);
                    } catch (RuntimeException e) {
                        LOGGER.error("Applying the decision {} failed", dec.action(), e);
                        endChain();
                    }
                }))
                .exceptionally(ex -> {
                    reportLlmError(ex, type);
                    return null;
                });
    }

    private static void reportLlmError(Throwable error, LlmClient.ModerationType type) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        LOGGER.warn("LLM request failed ({}): {}", type, cause.toString());
        // Only a player waiting for an answer needs to see this in chat - not a failed background summary
        if (type == LlmClient.ModerationType.MODERATION && ConfigLoader.config.logLlmErrorsToChat) {
            ModEvents.logErrorToChat(formatChatOutput("", ConfigLoader.lang.llmErrorMessage, ChatFormatting.BLUE, ChatFormatting.YELLOW, false, true, false));
        }
        if (type != LlmClient.ModerationType.SUMMARY) endChain();
    }

    /** Test hook: when set, feedback goes here instead of to the LLM (the self test has no model to talk to). */
    public static volatile java.util.function.Consumer<String> testFeedbackSink;

    /** Reports a result back to the LLM so it can react to it (and possibly do the next action). */
    private static void feedback(MinecraftServer server, String text) {
        java.util.function.Consumer<String> sink = testFeedbackSink;
        if (sink != null) {
            sink.accept(text);
            return;
        }
        moderateAndApply(server, LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedbackContext.formatted(text));
    }

    // Apply the decisions and translate them into actions
    public static void applyDecision(MinecraftServer server, ModerationDecision decision) {
        Action action = decision.action();
        ModConfig config = ConfigLoader.config;

        if (action == Action.IGNORE) {
            // Nothing to do - the chain ends here
            endChain();
            return;
        }
        if (action == Action.SELFFEEDBACK) {
            feedback(server, decision.value());
            return;
        }

        // Audit trail - always on, whatever modLogging says: this is what the moderator did to whom
        LOGGER.info("Moderator action: {}", describe(decision));

        if (config.disabledActions != null && config.disabledActions.stream().anyMatch(a -> a.equalsIgnoreCase(action.name()))) {
            LOGGER.info("Moderator action {} refused: disabled in the config.", action);
            feedback(server, ConfigLoader.lang.feedbackActionDisabled.formatted(action.name()));
            return;
        }

        switch (action) {
            case PLAYERLIST -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                feedback(server, ConfigLoader.lang.payersOnlineFeedback.formatted(list));
            }
            case WHOIS -> whois(server, decision);
            case PLAYERMEM -> playerMem(server, decision);
            case SERVERRULES -> feedback(server, ConfigLoader.lang.serverRules);
            case SERVERINFO -> feedback(server, ConfigLoader.lang.serverInfo);
            case CHANGEWEATHER -> feedback(server, ModActions.changeWeather(server, decision.value()));
            case CHANGETIME -> feedback(server, ModActions.changeTime(server, decision.value()));
            case LISTLOCATIONS -> listLocations(server);
            case GETLOCATION -> getLocation(server, decision);
            case SETLOCATION -> setLocation(server, decision);
            case REMLOCATION -> remLocation(server, decision);
            case PARDON -> pardon(server, decision);
            default -> playerAction(server, decision);
        }
    }

    private static void whois(MinecraftServer server, ModerationDecision decision) {
        String name = decision.value();
        ServerPlayer online = server.getPlayerList().getPlayer(name);
        String known = online != null ? online.getName().getString() : name;
        String feedback;
        if (PlayerManager.isKnown(known)) {
            feedback = ConfigLoader.lang.feedback_64.formatted(known, PlayerManager.getProfile(known));
        } else if (online != null) {
            // First contact - only with players that really are on the server, not with whatever name the LLM comes up with
            PlayerManager.addPlayer(known);
            feedback = ConfigLoader.lang.feedback_63.formatted(known);
        } else {
            feedback = ConfigLoader.lang.feedback_07.formatted(name);
        }
        feedback(server, feedback);
    }

    private static void playerMem(MinecraftServer server, ModerationDecision decision) {
        String name = decision.value();
        String note = decision.value2().trim();
        if (note.length() > MAX_NOTE_LENGTH) note = note.substring(0, MAX_NOTE_LENGTH);
        ServerPlayer online = server.getPlayerList().getPlayer(name);
        String known = online != null ? online.getName().getString() : name;
        String feedback;
        if (note.isEmpty()) {
            feedback = ConfigLoader.lang.feedbackInvalidInput.formatted(decision.value2());
        } else if (PlayerManager.isKnown(known)) {
            PlayerManager.addTag(known, note);
            feedback = ConfigLoader.lang.feedback_65.formatted(known, note);
        } else if (online != null) {
            // First contact
            PlayerManager.addPlayer(known);
            PlayerManager.addTag(known, note);
            feedback = ConfigLoader.lang.feedback_63.formatted(known) + " " + ConfigLoader.lang.feedback_65.formatted(known, note);
        } else {
            feedback = ConfigLoader.lang.feedback_07.formatted(name);
        }
        feedback(server, feedback);
    }

    private static void listLocations(MinecraftServer server) {
        String feedback;
        try {
            List<Location> locations = LocationManager.listLocations();

            if (locations.isEmpty()) {
                feedback = ConfigLoader.lang.feedback_58; // No Locations
            } else {
                String locationList = locations.stream()
                        .map(loc -> loc.name + " (" + loc.dim + ": " + loc.x + ", " + loc.z + ")")
                        .collect(Collectors.joining(", "));
                feedback = ConfigLoader.lang.feedback_52.formatted(locationList); // All Locations
            }
        } catch (Exception e) {
            LOGGER.error("Error listing locations", e);
            feedback = ConfigLoader.lang.exceptionFeedback; // error
        }
        feedback(server, feedback);
    }

    private static void getLocation(MinecraftServer server, ModerationDecision decision) {
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
            LOGGER.error("Error getting location '{}'", locationName, e);
            feedback = ConfigLoader.lang.exceptionFeedback;
        }
        feedback(server, feedback);
    }

    private static void setLocation(MinecraftServer server, ModerationDecision decision) {
        String locationName = decision.value().trim();
        String dimension = normalizeDimension(decision.value2());
        Position pos = parsePosition(decision.value3(), "SETLOCATION");
        String feedback;
        if (locationName.isEmpty() || locationName.length() > MAX_LOCATION_NAME) {
            feedback = ConfigLoader.lang.feedback_59.formatted(locationName);
        } else if (dimension == null) {
            feedback = ConfigLoader.lang.feedbackInvalidInput.formatted(decision.value2());
        } else if (!pos.valid()) {
            feedback = ConfigLoader.lang.feedback_61;
        } else if (LocationManager.getLocation(locationName) == null && LocationManager.count() >= MAX_LOCATIONS) {
            feedback = ConfigLoader.lang.feedbackTooManyLocations;
        } else {
            try {
                LocationManager.setLocation(locationName, dimension, (int) pos.x(), (int) pos.z());
                feedback = ConfigLoader.lang.feedback_54.formatted(locationName); // Saved
            } catch (Exception e) {
                LOGGER.error("Error setting location '{}'", locationName, e);
                feedback = ConfigLoader.lang.feedback_60.formatted(locationName); // Error
            }
        }
        feedback(server, feedback);
    }

    private static void remLocation(MinecraftServer server, ModerationDecision decision) {
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
            LOGGER.error("Error removing location '{}'", locationName, e);
            feedback = ConfigLoader.lang.feedback_57.formatted(locationName); // Delete error
        }
        feedback(server, feedback);
    }

    /** PARDON works on the ban list by name - a banned player is offline, so there is no ServerPlayer to look up. */
    private static void pardon(MinecraftServer server, ModerationDecision decision) {
        String name = decision.value();
        UserBanList bans = server.getPlayerList().getBans();
        NameAndId target = null;
        for (UserBanListEntry entry : bans.getEntries()) {
            NameAndId user = entry.getUser();
            if (user != null && user.name().equalsIgnoreCase(name)) {
                target = user;
                break;
            }
        }
        boolean wasBanned = target != null;
        if (target == null) {
            // Not banned (anymore) - but with a whitelist the player may still need to be let back in
            ServerPlayer online = server.getPlayerList().getPlayer(name);
            if (online != null) target = new NameAndId(online.getGameProfile());
        }
        if (target == null || (!wasBanned && !ConfigLoader.config.useWhitelist)) {
            feedback(server, wasBanned || target != null ? ConfigLoader.lang.feedbackNotBanned.formatted(name) : ConfigLoader.lang.feedback_07.formatted(name));
            return;
        }

        if (wasBanned) {
            bans.remove(target);
            try {
                bans.save();
            } catch (IOException e) {
                LOGGER.error("Saving the ban list failed", e);
                feedback(server, ConfigLoader.lang.exceptionFeedback);
                return;
            }
        }
        if (ConfigLoader.config.useWhitelist && server.getPlayerList().getWhiteList().get(target) == null) {
            // Put Player (back) on the whitelist
            server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(target));
            server.getPlayerList().reloadWhiteList();
        }
        feedback(server, ConfigLoader.lang.feedback_12.formatted(target.name()));
    }

    /** Everything that targets one online player. */
    private static void playerAction(MinecraftServer server, ModerationDecision decision) {
        Action action = decision.action();
        ServerPlayer player = server.getPlayerList().getPlayer(decision.value());
        if (player == null) {
            feedback(server, ConfigLoader.lang.feedback_07.formatted(decision.value()));
            return;
        }
        String playerName = player.getName().getString();

        if (ConfigLoader.config.protectOperators && OPERATOR_PROTECTED.contains(action)
                && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
            LOGGER.info("Moderator action {} against operator {} refused (protectOperators).", action, playerName);
            feedback(server, ConfigLoader.lang.feedbackOperatorProtected.formatted(playerName));
            return;
        }

        switch (action) {
            case TELEPORT -> {
                Position pos = parsePosition(decision.value2(), "TELEPORT");
                feedback(server, pos.valid()
                        ? ModActions.teleportPositionPlayer(player, pos.x(), pos.z())
                        : ConfigLoader.lang.feedback_61);
            }
            case TPTOLOCATION -> {
                // value = player, value2 = location
                String locationName = decision.value2();
                String feedback;
                try {
                    Location loc = LocationManager.getLocation(locationName);
                    feedback = loc == null
                            ? ConfigLoader.lang.feedback_56.formatted(locationName) // No Location
                            : ModActions.teleportToLocation(server, player, loc);
                } catch (Exception e) {
                    LOGGER.error("Error teleporting to location '{}'", locationName, e);
                    feedback = ConfigLoader.lang.exceptionFeedback;
                }
                feedback(server, feedback);
            }
            case WHEREIS -> feedback(server, ModActions.whereIs(player));
            case WARN -> {
                Component message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), ChatFormatting.BLUE, ChatFormatting.RED, false, true, false);
                server.getPlayerList().broadcastSystemMessage(message, false);
                feedback(server, ConfigLoader.lang.feedback_08.formatted(playerName, decision.value2()));
            }
            case KICK -> {
                Component message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), ChatFormatting.BLUE, ChatFormatting.RED, true, false, true);
                player.connection.disconnect(message);
                feedback(server, ConfigLoader.lang.feedback_09.formatted(playerName, decision.value2()));
            }
            case DAMAGEPLAYER -> {
                Number num = parseNumber(decision.value2(), "DAMAGEPLAYER");
                feedback(server, num.valid()
                        ? ModActions.damagePlayer(player, num.number())
                        : ConfigLoader.lang.feedback_62);
            }
            case CLEARINVENTORY -> feedback(server, ModActions.clearInventory(player));
            case KILLPLAYER -> feedback(server, ModActions.killPlayer(player));
            case GIVEPLAYER -> {
                Number num = parseNumber(decision.value3(), "GIVEPLAYER");
                feedback(server, num.valid()
                        ? ModActions.givePlayer(player, decision.value2(), num.number())
                        : ConfigLoader.lang.feedback_62);
            }
            case BAN -> ban(server, decision, player);
            default -> {
                LOGGER.warn("No handler for the action {}", action);
                feedback(server, ConfigLoader.lang.feedback_02);
            }
        }
    }

    private static void ban(MinecraftServer server, ModerationDecision decision, ServerPlayer player) {
        String playerName = player.getName().getString();
        if (!ConfigLoader.config.allowBanCommand) {
            feedback(server, ConfigLoader.lang.feedback_10);
            return;
        }
        NameAndId profile = new NameAndId(player.getGameProfile());
        // Ban first, then disconnect - the player must not be able to reconnect in between
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(getBannedPlayerEntry(decision, profile));
        try {
            bans.save();
        } catch (IOException e) {
            LOGGER.error("Saving the ban list failed", e);
            feedback(server, ConfigLoader.lang.exceptionFeedback);
            return;
        }
        if (ConfigLoader.config.useWhitelist && server.getPlayerList().getWhiteList().get(profile) != null) {
            // Remove Player of the whitelist
            server.getPlayerList().getWhiteList().remove(profile);
            server.getPlayerList().reloadWhiteList();
        }
        Component message = formatChatOutput(ConfigLoader.config.moderatorName + ": ", decision.value2(), ChatFormatting.BLUE, ChatFormatting.DARK_RED, true, false, true);
        player.connection.disconnect(message);
        feedback(server, ConfigLoader.lang.feedback_11.formatted(playerName, decision.value2()));
    }

    private static @NotNull UserBanListEntry getBannedPlayerEntry(ModerationDecision decision, NameAndId profile) {
        Date now = new Date();
        String reason = decision.value2();
        String source = "[" + ConfigLoader.config.moderatorName + "]";
        //Date expiry = null; // null = permanent

        return new UserBanListEntry(
                profile,
                now,
                source,
                null,
                reason
        );
    }

    /** One line for the log: the action and what the LLM passed to it, without anything that could fake extra log lines. */
    private static String describe(ModerationDecision decision) {
        String text = decision.action() + "(" + decision.value() + ", " + decision.value2() + ", " + decision.value3() + ")";
        text = text.replaceAll("[\\p{Cntrl}]", " ");
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** OVERWORLD, NETHER or END - however the LLM wrote it. Null if it is none of them. */
    private static String normalizeDimension(String raw) {
        if (raw == null) return null;
        String dim = raw.trim().toUpperCase().replace("MINECRAFT:", "");
        return switch (dim) {
            case "OVERWORLD", "THE_OVERWORLD" -> "OVERWORLD";
            case "NETHER", "THE_NETHER" -> "NETHER";
            case "END", "THE_END" -> "END";
            default -> null;
        };
    }

    public record Position(double x, double z, boolean valid) {
    }

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    /** Reads "X Z" - but also "10,-10", "X: 10 Z: -10" or "(10 / -10)", whatever the LLM comes up with. */
    public static Position parsePosition(String input, String caseString) {
        double posX = 0;
        double posZ = 0;
        boolean valid = false;

        if (input != null && !input.isEmpty()) {
            // Commas just separate the values (coordinates are whole blocks anyway), only "." can be a decimal point
            Matcher m = NUMBER.matcher(input.replace(',', ' '));
            double[] values = new double[3];
            int found = 0;
            while (m.find() && found < 3) {
                try {
                    values[found++] = Double.parseDouble(m.group());
                } catch (NumberFormatException e) {
                    found = 3; // treat as invalid
                }
            }
            if (found == 2) {
                posX = Math.floor(values[0]);
                posZ = Math.floor(values[1]);
                valid = true;
            } else {
                LOGGER.warn("Could not read a position from '{}' (action {})", input, caseString);
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
            Matcher m = NUMBER.matcher(input);
            if (m.find()) {
                try {
                    number = (int) Math.round(Double.parseDouble(m.group()));
                    valid = true;
                } catch (NumberFormatException e) {
                    LOGGER.warn("Could not read a number from '{}' (action {})", input, caseString);
                }
            } else {
                LOGGER.warn("Could not read a number from '{}' (action {})", input, caseString);
            }
        }
        return new Number(number, valid);
    }

    public static Component formatChatOutput(String prefix, String text, ChatFormatting prefixColor, ChatFormatting textColor, boolean bold, boolean italic, boolean underline) {
        // Build Text
        if (prefix.isEmpty()) prefix = "->";
        return Component.empty()
                .append(Component.literal(prefix).withStyle(style -> style.withColor(prefixColor)))
                .append(Component.literal(text).withStyle(style -> style.withColor(textColor).withBold(bold).withItalic(italic).withUnderlined(underline)));
    }

}
