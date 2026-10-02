package com.nomoneypirate.config;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    public static ModConfig config;
    public static LangConfig lang;

    public static void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator/config.json");
        config = load(configPath, ModConfig.class, new ModConfig(), "config file");
        if (config.modLogging) LOGGER.info("Config Initialized.");
    }

    public static void loadLang() {
        if (config == null) config = new ModConfig(); // In case config does not exist
        Path langPath = FabricLoader.getInstance().getConfigDir().resolve("themoderator/"+config.languageFileName + ".json");
        lang = load(langPath, LangConfig.class, new LangConfig(), "language file");
        if (config.modLogging) LOGGER.info("Language Initialized.");
    }

    /**
     * Loads a JSON file into {@code clazz}. Keys missing from the file keep their default value, so a new
     * option in an update just works. A broken file (unreadable, invalid JSON, empty) must never stop the game
     * from starting: the defaults are used and the file is left alone, so the owner can fix it instead of losing it.
     */
    private static <T> T load(Path path, Class<T> clazz, T defaultInstance, String what) {
        try {
            if (!Files.exists(path)) {
                save(path, defaultInstance);
                return defaultInstance;
            }
            String json = Files.readString(path);
            T loaded = new Gson().fromJson(json, clazz);
            if (loaded == null) {
                LOGGER.error("The {} {} is empty - using the defaults for now. (The file was not touched.)", what, path);
                return defaultInstance;
            }
            addMissingKeys(path, json, loaded, what);
            return loaded;
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Could not load the {} {}: {} - using the defaults for now. (The file was not touched.)", what, path, e.getMessage());
            return defaultInstance;
        }
    }

    /**
     * After an update the file on disk lacks the new options (they only exist as defaults in memory), so nobody
     * would ever find them. Write the loaded settings back - existing values are exactly what was loaded, only the
     * missing keys are added.
     */
    private static void addMissingKeys(Path path, String originalJson, Object loaded, String what) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            JsonObject before = JsonParser.parseString(originalJson).getAsJsonObject();
            JsonObject after = gson.toJsonTree(loaded).getAsJsonObject();
            if (before.keySet().containsAll(after.keySet())) return;
            Files.writeString(path, gson.toJson(after));
            LOGGER.info("Added new options to the {} {}.", what, path.getFileName());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not add new options to the {} {}: {}", what, path.getFileName(), e.getMessage());
        }
    }

    private static void save(Path path, Object cl) throws IOException {
        Files.createDirectories(path.getParent()); // Make sure path exist
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(cl);
        Files.writeString(path, json);
    }
}
