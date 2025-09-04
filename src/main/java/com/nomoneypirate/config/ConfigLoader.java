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
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator.json");
        config = load(configPath, ModConfig.class, new ModConfig(), "[themoderator] Error loading config file");
    }

    public static void loadLang() {
        Path langPath = FabricLoader.getInstance().getConfigDir().resolve(config.languageFileName + ".json");
        lang = load(langPath, LangConfig.class, new LangConfig(), "[themoderator] Error loading language file");
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
            if (ConfigLoader.config.modLogging) LOGGER.error("{}: {}", errorMessage, e.getMessage());
            return defaultInstance;
        }
    }

    private static void save(Path path, Object cl) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(cl);
        Files.writeString(path, json);
    }
}