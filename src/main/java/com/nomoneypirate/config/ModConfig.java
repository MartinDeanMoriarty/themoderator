package com.nomoneypirate.config;

import java.util.Set;

public class ModConfig {
    // Usually no change needed.
    public String ollamaURI = "http://localhost:11434/api/generate";
    // The name of the model used to generate
    public String model = "mistral-nemo";
    // The "attention span" of the moderator
    public Integer tokenLimit = 4000;
    // How much timeout in seconds
    public Integer connectionTimeout = 30;
    public Integer responseTimeout = 30;
    // The name of the language file
    public String languageFileName = "themoderator_de";
    // Activation keywords
    public Set<String> activationKeywords = Set.of("moderator", "mod", "admin");
    // Request cooldown in seconds
    public Integer requestCooldown = 1;
    // Is llm allowed to use BAN?
    public Boolean allowBanCommand = true;
    // Server with a whitelist should use this!
    public Boolean useWhitelist = true;
    // Server without a whitelist should use this!
    // But it is possible to use both.
    public Boolean useBanlist = false;
    // Moderation schedule interval in minutes
    public Integer scheduleInterval = 30;
    // Mod Logging
    public  Boolean modLogging = true;
    // LLM Logging
    public Boolean llmLogging = true;
    public String llmLogFilename = "themoderator_llm";
    public Boolean scheduleLogging = true;
    public String scheduleLogFilename = "themoderator_schedule";
}
