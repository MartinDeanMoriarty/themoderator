package com.nomoneypirate.config;

public class LangConfig {
    public String welcomeText = "Begrüße den Spieler.";
    public String feedback_01 = "Aktive Spieler: %s";
    public String feedback_02 = "Falsche verwendung: %s";
    public String feedback_03 = "Avatar gespawned als: %s  bei: %s";
    public String feedback_04 = "Spawning war nicht möglich.";
    public String feedback_05 = "Avatar despawned.";
    public String feedback_06 = "Kein Avatar zum despawnen vorhanden.";
    public String feedback_07 = "Spieler %s nicht gefunden.";
    public String feedback_08 = "Spieler %s erfolgreich gewarnt mit Grund: %s";
    public String feedback_09 = "Spieler %s erfolgreich gekickt mit Grund: %s";
    public String feedback_10 = "Der BAN Befehl ist nicht verfügbar.";
    public String feedback_11 = "Spieler %s erfolgreich gebannt mit Grund: %s";
    public String feedback_12 = "Etwas ging schief. Verwende nur JSON!";
    public String feedback_13 = "Spieler '%s' ist bei X: %d, Y: %d, Z: %d";
    public String feedback_14 = "Dein Avatar ist bei X: %d, Y: %d, Z: %d";
    // The following prompts are important to llm communication
    // Usually no change needed, but you may want to translate to different language
    public String systemPrompt = """
            System Regeln:
            %s
            
            Spieler: %s
            Nachricht: %s
            
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