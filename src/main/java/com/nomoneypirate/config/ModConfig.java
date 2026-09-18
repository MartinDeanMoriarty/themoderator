package com.nomoneypirate.config;

import java.util.Set;

public class ModConfig {
    //// === Ollama ===
    public Boolean useOllama = true;
    // Usually no change needed.
    public String ollamaURI = "http://localhost:11434/api/chat";
    // The name of the model used to generate
    public String ollamaModel = "qwen3.5:latest";
    // Model warm up
    public Boolean ollamaWarmup = false;
    // Offer actions to the model as native tools/functions (recommended - works even for models
    // Ollama doesn't officially tag as tool-capable). Set to false for a model that reliably
    // ignores tool definitions; the moderator then falls back to a schema-constrained JSON reply.
    public Boolean ollamaUseNativeTools = true;
    //// === OpenAI ===
    public Boolean useOpenAi = false;
    // Usually no change needed.
    public String OpenAiURI = "https://api.openai.com/v1/chat/completions";
    // OpenAi api-key
    public String openAiApiKey = "?";
    //OpenAi model
    public String openAiModel = "gpt-4.1";
    // Offer actions as native OpenAI tools/functions. Should stay true for any modern model.
    public Boolean openAiUseNativeTools = true;
    //// === Google gemini ===
    public Boolean useGemini = false;
    // Usually no change needed.
    public String geminiURI = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    // Gemini api-key
    public String geminiApiKey = "";
    // Offer actions as native Gemini function declarations. Should stay true for any modern model.
    public Boolean geminiUseNativeTools = true;
    //// === Anthropic (Claude) ===
    public Boolean useAnthropic = false;
    // Usually no change needed.
    public String anthropicURI = "https://api.anthropic.com/v1/messages";
    // Anthropic api-key. Needs its own console.anthropic.com account with billing set up -
    // a claude.ai Pro/Max subscription does not include Messages API access.
    public String anthropicApiKey = "?";
    // Anthropic model. Haiku is fast/cheap and plenty for chat-moderation; bump to a Sonnet/Opus
    // model if you want a sharper moderator and don't mind the extra cost per message.
    public String anthropicModel = "claude-haiku-4-5";
    // Offer actions as native Anthropic tools. Should stay true for any modern model.
    public Boolean anthropicUseNativeTools = true;
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
    public Boolean useWhitelist = false;
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
    public Boolean logLlmErrorsToChat = true;
}
