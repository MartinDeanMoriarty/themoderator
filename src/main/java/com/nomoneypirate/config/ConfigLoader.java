package com.nomoneypirate.config;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    public static ModConfig config;

    public static void load() {
        // Using default fabric config directory
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator.json");
        try {
            if (!Files.exists(configPath)) {
                config = new ModConfig(); // Defaults
                save(configPath);
            } else {
                String json = Files.readString(configPath);
                config = new Gson().fromJson(json, ModConfig.class);
            }
        } catch (IOException e) {
            //System.err.println("[themoderator] Error loading config: " + e.getMessage());
            LOGGER.error("[themoderator] Error loading config: {}", e.getMessage());
            config = new ModConfig(); // Fallback
        }
    }

    private static void save(Path path) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(config);
        Files.writeString(path, json);
    }
}