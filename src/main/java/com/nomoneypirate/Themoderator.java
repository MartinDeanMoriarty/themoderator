package com.nomoneypirate;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.commands.ModCommands;
import com.nomoneypirate.llm.providers.OllamaProvider;
import com.nomoneypirate.locations.LocationManager;
import com.nomoneypirate.profiles.PlayerManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Themoderator implements ModInitializer {

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "themoderator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        // Load configuration file
        ConfigLoader.loadConfig();
        // Load language file
        ConfigLoader.loadLang();
        // Register mod commands
        ModCommands.registerCommands();
        // Register mod events
        ModEvents.registerEvents();
        // Load player memory
        PlayerManager.loadPlayers();
        // Load Locations memory
        LocationManager.loadLocations();
        // Development only: run the in-server self test (class is not part of the release jar)
        String selfTest = System.getenv("THEMODERATOR_SELFTEST");
        if (selfTest != null) {
            try {
                // THEMODERATOR_SELFTEST=chat plays a short live chat instead of the action checks
                String testClass = selfTest.equals("chat") ? "LiveChat" : "SelfTest";
                Class.forName("com.nomoneypirate.dev." + testClass).getMethod("register").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("THEMODERATOR_SELFTEST is set, but the self test is not available: {}", e.toString());
            }
        }
        // Warmup ollama model
        if (ConfigLoader.config.useOllama && ConfigLoader.config.ollamaWarmup) OllamaProvider.warmupModel();
    }

}