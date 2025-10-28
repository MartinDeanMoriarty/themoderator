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
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator/config.json");
        config = load(configPath, ModConfig.class, new ModConfig(), "Error loading config file");
        if (config.modLogging) LOGGER.info("Config Initialized.");
    }

    public static void loadLang() {
        if (config == null) config = new ModConfig(); // In case config does not exist
        Path langPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator/"+config.languageFileName + ".json");
        lang = load(langPath, LangConfig.class, new LangConfig(), "Error loading language file");
        if (config.modLogging) LOGGER.info("Language Initialized.");
    }

    private static <T> T load(Path path, Class<T> clazz, T defaultInstance, String errorMessage) {
        try {
            if (!Files.exists(path)) {
                save(path, defaultInstance);
                return defaultInstance;
            } else {
                String json = Files.readString(path);
                return new Gson().fromJson(json, clazz);
            }
        } catch (IOException e) {
            if (config.modLogging) LOGGER.error("{}: {}", errorMessage, e.getMessage());
            return defaultInstance;
        }
    }

    private static void save(Path path, Object cl) throws IOException {
        Files.createDirectories(path.getParent()); // Make sure path exist
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(cl);
        Files.writeString(path, json);
    }
}