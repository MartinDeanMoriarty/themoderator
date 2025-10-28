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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.*;
import java.net.http.*;
import java.net.http.*;
import com.google.gson.*;

public class PlayerManager {
    private static final Path playerPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("themoderator/playerManager.json");

    private static final Map<String, PlayerProfile> players = new HashMap<>();

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
                players.putAll(loaded);
            }
            // Log this!
            if (ConfigLoader.config.modLogging) LOGGER.info("Player Manager Initialized.");
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.error("Error loading Player Manager: {}", e.getMessage());
        }
    }
    public static void savePlayers() {
        try {
            Files.createDirectories(playerPath.getParent());
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(players);
            Files.writeString(playerPath, json);
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.error("Error saving player: {}", e.getMessage());
        }
    }

    public static boolean isKnown(String name) {
        return players.containsKey(name);
    }

    public static void addPlayer(String name) {
        String location = "";
        PlayerProfile profile = players.getOrDefault(name,
                new PlayerProfile(name, location,new ArrayList<>()));
        players.put(name, profile);
        savePlayers();
    }

    public static void addLocation(String name, String location) {
        PlayerProfile profile = players.get(name);
        if (profile != null) {
            profile.locations = location;
            savePlayers();
        }
    }

    public static PlayerProfile getProfile(String name) {
        return players.get(name);
    }

    public static List<PlayerProfile> listProfiles() {
        return new ArrayList<>(players.values());
    }

    public static void addTag(String name, String tag) {
        PlayerProfile profile = players.get(name);
        if (profile != null && !profile.tags.contains(tag)) {
            profile.tags.add(tag);
            savePlayers();
        }
    }
}

