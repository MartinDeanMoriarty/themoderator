package com.nomoneypirate.config;

import java.util.Set;

public class ModConfig {
    //// === Ollama ===
    public Boolean useOllama = true;
    // Usually no change needed.
    public String ollamaURI = "http://localhost:11434/api/generate";
    // The name of the model used to generate
    public String ollamaModel = "mistral-nemo";
    // Model warm up
    public Boolean ollamaWarmup = true;
    //// === OpenAI ===
    public Boolean useOpenAi = false;
    // Usually no change needed.
    public String OpenAiURI = "https://api.openai.com/v1/chat/completions";
    // OpenAi api-key
    public String openAiApiKey = "?";
    //OpenAi model
    public String openAiModel = "gpt-4.1";
    //// === Google gemini ===
    public Boolean useGemini = false;
    // Usually no change needed.
    public String geminiApiUri = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
    // Gemini api-key
    public String geminiApiKey = "?";
    //// The "context size" aka "token limit" (attention span) of the moderator
    public Integer tokenLimit = 4096;
    // How much timeout in seconds
    public Integer connectionTimeout = 30;
    public Integer responseTimeout = 30;
    // The name of the language file
    public String languageFileName = "themoderator_de";
    //// Activation keywords
    public Set<String> activationKeywords = Set.of("moderator", "mod", "admin");
    // Request cooldown in seconds
    public Integer requestCooldown = 4;
    // Is moderator allowed to use BAN?
    public Boolean allowBanCommand = true;
    // Server with a whitelist should use this!
    public Boolean useWhitelist = true;
    // Server without a whitelist should use this!
    // But it is possible to use both.
    public Boolean useBanlist = false;
    //// === Moderation schedule ===
    public Boolean scheduledModeration = false;
    // Moderation schedule interval in minutes
    public Integer scheduleModerationInterval = 120;
    // Scheduled server provider restart announcement
    public Boolean scheduledServerRestart = false;
    // Automatic server provider restart at hour 0-23
    public Integer autoRestartHour = 4;
    // Announce minutes before restart
    public Integer serverRestartPrewarn = 5;
    //// === Logging ===
    // Moderation schedule logging
    public Boolean scheduleLogging = false;
    public String scheduleLogFilename = "themoderator_schedule";
    // Mod Logging
    public  Boolean modLogging = true;
    // LLM Logging
    public Boolean llmLogging = true;
    public String llmLogFilename = "themoderator_llm";
}