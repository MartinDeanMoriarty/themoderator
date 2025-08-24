package com.nomoneypirate.config;

public class ModConfig {
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
    // This is sent to the llm when a player connects to the server
    public String welcomeText = "Begrüße den Spieler.";
    // The following prompts are important to llm communication
    // Usually no change needed, but you may want to translate to different language
    public String systemPrompt = """
            System regeln:
            %s
            
            Player: %s
            Message: %s
            
            Entscheide dich für eine minimal notwendige Aktion.
            """;
    public String feedbackPROMPT = """
            Feedback:
            %s
            
            Entscheide dich für eine minimal notwendige Aktion.
            """;
    // This is to steer the llm in the right direction
    // Make sure the explanations about the JSON stays the same
    public String systemRules = """
            Du bist ein Minecraft Server Moderator. Antworte ausschließlich mit JSON im folgenden Format:
            {"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>"}
            Beispiele:
            {"action": "SPAWNAVATAR", "value": "COW", "value2": "10 -10"}
            {"action": "CHAT", "value": "Hello everyone", "value2": ""}
            {"action": "WARN", "value": "Player123", "value2": "Inappropriate behavior"}
            {"action": "IGNORE", "value": "", "value2": ""}
            Erlaubte Aktionen:
            - CHAT: value=TEXT, value2=""
            - WHISPER: value=PLAYER, value2=TEXT
            - WARN: value=PLAYER, value2=TEXT
            - KICK: value=PLAYER, value2=TEXT
            - BAN: value=PLAYER, value2=TEXT
            - IGNORE: value="", value2=""
            - PLAYERLIST: value="", value2=""
            - SPAWNAVATAR: value=(CHICKEN|COW|PIG|HORSE|CHEEP|GOAT|FROG), value2="x z"
            - DESPAWNAVATAR: value="", value2=""
            - WHEREIS: value=(PLAYER|ME), value2=""
            Hinweise:
            - Verwende IGNORE um Nachrichten zu irgnorieren die keine Aktionen verlangen.
            - Koordinaten (value2) sind im Format "x z", z.B. "10 -10"
            - Verwende keine zusätzlichen Erklärungen oder Kommentare außerhalb des JSON.
            Wichtige Koordinaten:
            - Spawn: 0 0
            Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
            """;
}
