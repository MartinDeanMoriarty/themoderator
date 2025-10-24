package com.nomoneypirate.config;

import java.util.Set;

public class ModConfig {
    //// === Ollama ===
    public Boolean useOllama = false;
    // Usually no change needed.
    public String ollamaURI = "http://localhost:11434/api/generate";
    // The name of the model used to generate
    public String ollamaModel = "mistral-nemo";
    // Model warm up
    public Boolean ollamaWarmup = false;
    //// === OpenAI ===
    public Boolean useOpenAi = false;
    // Usually no change needed.
    public String OpenAiURI = "https://api.openai.com/v1/chat/completions";
    // OpenAi api-key
    public String openAiApiKey = "?";
    //OpenAi model
    public String openAiModel = "gpt-4.1";
    //// === Google gemini ===
    public Boolean useGemini = true;
    // Usually no change needed.
    public String geminiApiUri = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    // Gemini api-key
    public String geminiApiKey = "";
    //// The "context size" aka "token limit" (attention span) of the moderator
    public Integer tokenLimit = 4096;
    // How much timeout in seconds
    public Integer connectionTimeout = 30;
    public Integer responseTimeout = 30;
    // The name of the language file
    public String languageFileName = "themoderator_de";
    ////  The name of the moderator
    public String moderatorName = "The Moderator";
    //// Activation keywords
    // Can be names of the moderator or used as a blacklist
    public Boolean useActivationKeywords = false;
    public Set<String> activationKeywords = Set.of("moderator", "mod", "admin");
    // Blacklist examples ("arsch", "hurensohn", "schwuchtel")
    // Request cooldown in seconds
    public Integer requestCooldown = 1;
    // Is moderator allowed to use BAN?
    public Boolean allowBanCommand = false;
    // Server with a whitelist should use this!
    public Boolean useWhitelist = true;
    // Server without a whitelist should use this!
    // But it is possible to use both.
    public Boolean useBanlist = false;
    //// === Moderation schedules ===
    // Scheduled summaries
    public Boolean scheduledSummary = false;
    // Summary interval in minutes
    public Integer scheduleSummaryInterval = 30;
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