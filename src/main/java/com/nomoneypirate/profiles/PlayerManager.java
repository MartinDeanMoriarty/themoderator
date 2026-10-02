package com.nomoneypirate.profiles;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.google.gson.reflect.TypeToken;
import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.*;

public class PlayerManager {
    private static final Path playerPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("themoderator/playerManager.json");

    // The feedback loop runs entirely on the server thread now, but this is still shared,
    // mutable state read from command/event handlers too - a plain HashMap isn't safe for that.
    private static final Map<String, PlayerProfile> players = new ConcurrentHashMap<>();

    public static void loadPlayers() {
        try {
            Files.createDirectories(playerPath.getParent());
            if (!Files.exists(playerPath)) {
                savePlayers(); // Make sure there is a file to save to
                // Log this!
            } else {
                String json = Files.readString(playerPath);
                Type type = new TypeToken<Map<String, PlayerProfile>>() {}.getType();
                Map<String, PlayerProfile> loaded = new Gson().fromJson(json, type);
                players.clear();
                if (loaded != null) players.putAll(loaded); // an empty file deserializes to null
            }
            // Log this!
            if (ConfigLoader.config.modLogging) LOGGER.info("Player Manager Initialized.");
        } catch (IOException e) {
            LOGGER.error("Error loading Player Manager: {}", e.getMessage());
        }
    }
    public static void savePlayers() {
        try {
            Files.createDirectories(playerPath.getParent());
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(players);
            Files.writeString(playerPath, json);
        } catch (IOException e) {
            LOGGER.error("Error saving player: {}", e.getMessage());
        }
    }

    /** The LLM spells names however it likes - map "bob" onto the stored "Bob" instead of creating a second profile. */
    private static String resolveKey(String name) {
        if (name == null) return "";
        if (players.containsKey(name)) return name;
        for (String key : players.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return name;
    }

    public static boolean isKnown(String name) {
        return name != null && players.containsKey(resolveKey(name));
    }

    public static void addPlayer(String name) {
        // computeIfAbsent instead of getOrDefault+put - the two would race under concurrent access.
        players.computeIfAbsent(resolveKey(name), n -> new PlayerProfile(n, "", new ArrayList<>()));
        savePlayers();
    }

    public static void addLocation(String name, String location) {
        PlayerProfile profile = players.get(resolveKey(name));
        if (profile != null) {
            synchronized (players) {
                profile.locations = location;
            }
            savePlayers();
        }
    }

    public static PlayerProfile getProfile(String name) {
        return players.get(resolveKey(name));
    }

    public static List<PlayerProfile> listProfiles() {
        return new ArrayList<>(players.values());
    }

    // Notes are written by the LLM - keep a profile from growing without bound
    private static final int MAX_TAGS = 30;

    public static void addTag(String name, String tag) {
        PlayerProfile profile = players.get(resolveKey(name));
        if (profile == null) return;
        // profile.tags is a plain ArrayList, not thread-safe on its own - guard the
        // check-then-add against a concurrent addTag on the same profile.
        boolean added = false;
        synchronized (players) {
            if (profile.tags == null) profile.tags = new ArrayList<>();
            if (!profile.tags.contains(tag)) {
                profile.tags.add(tag);
                while (profile.tags.size() > MAX_TAGS) profile.tags.remove(0);
                added = true;
            }
        }
        if (added) savePlayers();
    }
}

