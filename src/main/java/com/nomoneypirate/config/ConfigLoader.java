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
    public static LangConfig lang;

    public static void loadConfig() {
        // Using default fabric config directory
        // Load config file
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator.json");
        try {
            if (!Files.exists(configPath)) {
                config = new ModConfig(); // Defaults
                save(configPath, config);
            } else {
                String json = Files.readString(configPath);
                config = new Gson().fromJson(json, ModConfig.class);
            }
        } catch (IOException e) {
            LOGGER.error("[themoderator] Error loading config file: {}", e.getMessage());
            config = new ModConfig(); // Fallback
        }
    }
    public static void loadLang() {
        // Using default fabric config directory
        // Load lang file
        Path langPath = FabricLoader.getInstance().getConfigDir().resolve(ConfigLoader.config.languageFileName + ".json");
        try {
            if (!Files.exists(langPath)) {
                lang = new LangConfig(); // Defaults
                save(langPath, lang);
            } else {
                String json = Files.readString(langPath);
                lang = new Gson().fromJson(json, LangConfig.class);

            }
        } catch (IOException e) {
            //System.err.println("[themoderator] Error loading config: " + e.getMessage());
            LOGGER.error("[themoderator] Error loading language file: {}", e.getMessage());
            lang = new LangConfig(); // Fallback
        }
    }

    private static void save(Path path, Object cl) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(cl);
        Files.writeString(path, json);
    }
}