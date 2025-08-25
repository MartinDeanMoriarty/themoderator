package com.nomoneypirate.config;

public class ModConfig {
    // The name of the language file
    public String languageFileName = "themoderator_de.json";
    // Is llm allowed to use BAN?
    public Boolean allowBanCommand = true;
    // Server with a whitelist should use this!
    public Boolean useWhitelist = true;
    // Server without a whitelist should use this!
    // But it is possible to use both.
    public Boolean useBanlist = false;
    // Usually no change needed.
    public String ollamaURI = "http://localhost:11434/api/generate";
    // How much timeout in seconds
    public Integer connectionTimeout = 30;
    public Integer responseTimeout = 30;
    // The name of the model used to generate
    public String model = "mistral-nemo";
    // LLM Logging
    public Boolean llmLogging = true;
    public String logFilename = "themoderator_llm.log";

}
