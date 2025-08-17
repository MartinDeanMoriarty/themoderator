package com.nomoneypirate;

public class ModConfig {
    public String ollamaURI = "http://localhost:11434/api/generate";
    public String model = "mistral-nemo";
    public String systemPrompt =  """
            System regeln:
            %s

            Player: %s
            Message: %s

            Entscheide dich für eine minimal notwendige Aktion.
            """;
    public String systemRules = """
        Du bist ein Minecraft Server Moderator. Antworte NUR mit JSON:
        {"action": "CHAT|WHISPER|WARN|KICK|IGNORE", "playerName": "<playerName>", "text": "<text>"}.
        Offensichtlich kann dieses json auch so verwendet werden:
        {"action": "CHAT", "text": "<Text>"}.
        Oder so:
        {"action": "IGNORE"}.
        
        Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
        """;
    public String welcomePrompt = "Begrüße den Spieler.";
    public String warnMessage = "Warnung!";
    public String kickMessage = "Kicked von The Moderator";
}
