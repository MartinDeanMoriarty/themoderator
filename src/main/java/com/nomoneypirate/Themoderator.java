package com.nomoneypirate;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.commands.ModCommands;
import com.nomoneypirate.llm.LlmClient;
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
        // Load Locations
        LocationManager.loadLocations();
        // Warmup ollama model
        if (ConfigLoader.config.useOllama) LlmClient.warmupModel();
    }

}