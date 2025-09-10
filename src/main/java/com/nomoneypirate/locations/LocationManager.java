package com.nomoneypirate.locations;

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

public class LocationManager {
    private static final Path locationPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("themoderator/locations.json");

    private static final Map<String, Location> locations = new HashMap<>();

    public static void loadLocations() {
        try {
            Files.createDirectories(locationPath.getParent());
            if (!Files.exists(locationPath)) {
                saveLocations(); // Make sure there is a file to save to
                // Log this!
            } else {
                String json = Files.readString(locationPath);
                Type type = new TypeToken<Map<String, Location>>() {}.getType();
                Map<String, Location> loaded = new Gson().fromJson(json, type);
                locations.clear();
                locations.putAll(loaded);
            }
            // Log this!
            if (ConfigLoader.config.modLogging) LOGGER.info("5of5 Locations Initialized.");
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.error("[themoderator] Error loading locations: {}", e.getMessage());
        }
    }

    public static void saveLocations() {
        try {
            Files.createDirectories(locationPath.getParent());
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(locations);
            Files.writeString(locationPath, json);
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.error("[themoderator] Error saving locations: {}", e.getMessage());
        }
    }

    public static List<Location> listLocations() {
        return new ArrayList<>(locations.values());
    }

    public static Location getLocation(String name) {
        return locations.get(name.toLowerCase());
    }

    public static void setLocation(String name, String dim, int x, int z) {
        locations.put(name.toLowerCase(), new Location(name, dim, x, z));
        saveLocations();
    }

    public static boolean remLocation(String name) {
        boolean removed = locations.remove(name.toLowerCase()) != null;
        if (removed) saveLocations();
        return removed;
    }
}