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
    // Context window in tokens sent to Ollama ("num_ctx"). 0 = use tokenLimit (below). Ollama's own default
    // is often just 4096, and it silently cuts off the start of a prompt that doesn't fit - the system prompt.
    public Integer ollamaNumCtx = 0;
    // Thinking models (e.g. qwen3.5, gemma4) reason before every answer. "off" = 2-3x faster replies, but in tests
    // the answers got worse and some models (gemma4) then write actions as chat text instead of calling them.
    // "on" = force thinking, "auto" = whatever the model does by default (recommended).
    public String ollamaThink = "auto";
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
    // The model is part of this URL. "gemini-flash-latest" always points to Google's current Flash model;
    // pin a specific one (e.g. .../models/gemini-3.8-flash:generateContent) if you want it to never change.
    // Google retires old models for new API keys (gemini-2.5-flash already is - HTTP 404 "no longer available
    // to new users"), so if you get that error, change the model name here.
    // The free tier allows only about 5 requests per minute per model - a busy chat will hit HTTP 429.
    public String geminiURI = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";
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
    //// The "context size" aka "token limit" (attention span) of the moderator.
    // The system prompt and the action definitions already take ~2.6k of it, plus room for the answer
    // (thinking models need a lot) - so 8192 is a sensible minimum for a conversation with some memory.
    public Integer tokenLimit = 8192;
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
    // BAN always writes the vanilla ban list (so /pardon and the vanilla banlist commands work).
    // Servers with a whitelist can additionally let BAN remove the player from it (PARDON adds them back).
    public Boolean useWhitelist = false;
    // Kept so old config files stay valid - BAN uses the ban list regardless of this setting now.
    public Boolean useBanlist = false;
    //// === Safety ===
    // The moderator may never kick/ban/kill/damage/clear the inventory of operators. A prompt alone can't
    // stop someone from talking the LLM into it, this check happens in code.
    public Boolean protectOperators = true;
    // Actions the LLM is not allowed to use at all, e.g. ["WHEREIS", "GIVEPLAYER"]. The LLM gets told the
    // action is disabled. Names as in the action list (KICK, BAN, GIVEPLAYER, TELEPORT, ...).
    public Set<String> disabledActions = new java.util.HashSet<>();
    // How many actions the LLM may chain after one player message (action -> feedback -> next action ...)
    // before the chain is cut off, so a confused model can't loop forever.
    public Integer maxActionChain = 6;
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
    // Mod logging: the mod's own informational lines ("Config Initialized", warm-up, ...) in the normal
    // Minecraft log (console + logs/latest.log, tagged "themoderator"). Errors, warnings and the audit line
    // for every executed moderation action are ALWAYS logged, regardless of this setting.
    public  Boolean modLogging = true;
    // LLM Logging: writes the messages sent to the LLM into logs/<llmLogFilename>.log (separate file)
    public Boolean llmLogging = true;
    public String llmLogFilename = "themoderator_llm";
    public Boolean logLlmErrorsToChat = true;
}
