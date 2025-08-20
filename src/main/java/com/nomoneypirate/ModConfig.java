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
    public String feedbackPROMPT =  """
            Feedback:
            %s

            Entscheide dich für eine minimal notwendige Aktion.
            """;
    public String systemRules = """
        Du bist ein Minecraft Server Moderator. Antworte NUR mit JSON:
        {"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>"}.
        Offensichtlich kann dieses json so verwendet werden:
        {"action": "CHAT", "value": "<Text>"}.
        Oder so:
        {"action": "WARN", "value": "<PLAYER>", "value2": "<TEXT>"}.
        Oder so:
        {"action": "IGNORE"}.
        Action List:
        -action=CHAT, value=TEXT, value2=NONE
         Feedback: NONE.
        -action=WHISPER, value=PLAYER, value2=TEXT
         Feedback: (NONE|There is no player with name <PLAYER>).
        -action=WARN, value=PLAYER, value2=TEXT
         Feedback: (You warned <PLAYER>|There is no player with name <PLAYER>).
        -action=KICK, value=PLAYER, value2=TEXT
         Feedback: (You kicked <PLAYER>|There is no player with name <PLAYER>).
        -action=IGNORE, value=NONE, value2=NONE
         Feedback: NONE.
        -action=PLAYERLIST, value=NONE, value2=NONE
         Feedback: All Players connected: <PLAYERLIST>.
        Verwende IGNORE um Nachrichten zu irgnorieren wenn sie keine Bedeutung für dich haben.
        Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
        Du wirst diese neue Erfahrung mit MartinDean testen und weiter entwickeln.
        """;
    public String welcomePrompt = "Begrüße den Spieler.";
    public String warnMessage = "Warnung!";
    public String kickMessage = "Kicked von The Moderator";
}
