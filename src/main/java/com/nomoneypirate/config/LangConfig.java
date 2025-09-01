package com.nomoneypirate.config;

public class LangConfig {
    // The following is important to llm communication.
    // Feedback should be as clear as possible
    // because a feedback can cause the llm to perform another action
    // or keep it stuck in a loop.
    public String welcomeText = "Begrüße den Spieler.";
    public String feedback_01 = "Aktive Spieler: %s";
    public String feedback_02 = "Falsche verwendung.";
    public String feedback_03 = "Avatar gespawned als: %s in Dimension %s bei X: %d, Z: %d";
    public String feedback_04 = "Spawning war nicht möglich.";
    public String feedback_05 = "Avatar despawned.";
    public String feedback_06 = "Kein Avatar zum despawnen vorhanden.";
    public String feedback_07 = "Spieler %s nicht gefunden.";
    public String feedback_08 = "Spieler %s erfolgreich gewarnt mit Grund: %s";
    public String feedback_09 = "Spieler %s erfolgreich gekickt mit Grund: %s";
    public String feedback_10 = "Der BAN Befehl ist nicht verfügbar.";
    public String feedback_11 = "Spieler %s erfolgreich gebannt mit Grund: %s";
    public String feedback_12 = "Etwas ging schief. Verwende nur JSON!";
    public String feedback_13 = "Spieler '%s' ist in Dimension %s bei X: %d, Y: %d, Z: %d";
    public String feedback_14 = "Dein Avatar ist in Dimension %s bei X: %d, Y: %d, Z: %d";
    public String feedback_15 = "Kein Avatar vorhanden";
    public String feedback_16 = "Dein Avatar ist ein %s in Dimension %s bei X: %d, Z: %d";
    public String feedback_17 = "Der Server wurde (neu)gestartet und ist bereit für Spieler.";
    public String feedback_18 = "Aktion war nicht möglich.";
    public String feedback_19 = "Alles gestoppt.";
    public String feedback_20 = "Dein Avatar folgt nun Spieler %s.";
    public String feedback_21 = "Dein Avatar folgt nun nicht mehr dem Spieler.";
    public String feedback_22 = "Dein Avatar schaut nun Spieler %s an.";
    public String feedback_23 = "Dein Avatar schaut nun nicht mehr den Spieler an.";
    public String feedback_24 = "Dein Avatar geht nun zu Spieler %s.";
    public String feedback_25 = "Dein Avatar wandert nun in einem Radius von %d Blöcken herum.";
    public String feedback_26 = "Dein Avatar wandert nun nicht mehr herum.";
    public String feedback_27 = "Dein Avatar kann Spieler nicht erreichen.";
    public String feedback_28 = "Dein Avatar scheint festzustecken oder Ziel ist unerreichbar.";
    public String feedback_29 = "Dein Avatar ist bei Spieler %s angekommen.";
    public String feedback_30 = "Dein Avatar geht nun zu Position X: %d, Z: %d.";
    public String feedback_31 = "Dein Avatar ist bei Position X: %d, Z: %d angekommen.";
    public String feedback_32 = "Dein Avatar kann Position X: %d, Z: %d nicht erreichen.";
    public String feedback_33 = "Spieler %s wurde zu deinem Avatar teleportiert.";
    public String feedback_34 = "Avatar wurde zu Spieler %s teleportiert.";
    public String feedback_35 = "Spieler %s wurde zu Position X: %d , Z: %d teleportiert.";
    public String feedback_36 = "Dein Avatar wurde zu Position X: %d , Z: %d teleportiert.";
    public String feedback_37 = "Dein Avatar konnte nicht zu Position X: %d , Z: %d teleportiert werden.";
    public String feedback_38 = "Keine Einträge. Nutze doch die Gelegenheit um etwas Werbung für dich zu machen. Erwähne die Keywörter  %s.";
    // This is to format the prompts
    public String systemPrompt = """
            System Regeln:
            %s
            
            Spieler: %s
            Nachricht: %s
            """;
    public String feedbackPROMPT = """
            System Regeln:
            %s
            
            Feedback: %s
            """;
    // This is what some call "system prompt". Used to steer the llm in the right direction
    public String systemRules = """
            Du bist ein Minecraft Server Moderator. Reagiere auf dierekte Anfragen und analysiere regelmäßig eine Zusammenfassung (Summary). Antworte ausschließlich mit JSON im folgenden Format:
            {"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>", "value3": "<VALUE3>"}
            Beispiele:
            {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "COW", "value3": "10 -10"}
            {"action": "CHAT", "value": "Hello everyone", "value2": "", "value3": ""}
            {"action": "WARN", "value": "Player123", "value2": "Unangebrachtes Verhalten!", "value3": ""}
            {"action": "IGNORE", "value": "", "value2": "", "value3": ""}
            {"action": "WHEREIS", "value": "AVATAR", "value2": "", "value3": ""}
            Beispiel Verkettung:
             {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "COW", "value3": "10 -10"}
            -Feedback abwarten!
             {"action": "GOTOPLAYER", "value": "Player123", "value2": "", "value3": ""}
            -Feedback abwarten!
             {"action": "LOOKATPLAYER", "value": "Player123", "value2": "", "value3": ""}
            -Feedback abwarten!
             {"action": "CHAT", "value": "Ich sehe dich!", "value2": "", "value3": ""}
            Erlaubte Aktionen:
            - CHAT: value:"TEXT", value2:"", value3:""
            - WHISPER: value:"SPIELERNAME", value2:"TEXT", value3:""
            - WARN: value:"SPIELERNAME", value2:"TEXT", value3:""
            - KICK: value:"SPIELERNAME", value2:"TEXT", value3:""
            - BAN: value:"SPIELERNAME", value2:"TEXT", value3:""
            - IGNORE: value:"", value2:"", value3:""
            - PLAYERLIST: value:"", value2:"", value3:""
            - WHEREIS: value:"(SPIELERNAME|AVATAR)", value2:"", value3:""
            - TELEPORTPLAYER: value:"(SPIELERNAME|AVATAR)", value2:"(AVATAR|SPIELERNAME)", value3:""
            - TELEPORTPOSITION: value:"(SPIELERNAME|AVATAR)", value2:"x z", value3:""
            - FEEDBACK: value:"TEXT", value2:"", value3:""
            - SPAWNAVATAR: value:"(OVERWORLD|NETHER|END)" value2:"(CHICKEN|COW|PIG|HORSE|CHEEP|GOAT|FROG)", value3:"x z"
            - DESPAWNAVATAR: value:"", value2:"", value3:""
            - FOLLOWPLAYER: value:"SPIELERNAME", value2:"", value3:""
            - LOOKATPLAYER: value:"SPIELERNAME", value2:"", value3:""
            - GOTOPLAYER: value:"SPIELERNAME", value2:"", value3:""
            - GOTOPOSITION: value:"x z", value2:"", value3:""
            - MOVEAROUND: value:"BLOCKRADIUS", value2:"", value3:""
            - STOP: value:"(FOLLOWING|LOOKINGAT|MOVINGAROUND|ALL)" value2:"", value3:""
            Hinweise:
            - Spawne deinen eigenen Avatar (In Aktionen AVATAR) und verwende ihn um mit der Welt oder Spielern zu interargieren.
            - Du kannst Aktionen logisch verketten in dem du auf Feedback mit einer Aktion reagierst oder selbst die Aktion FEEDBACK verwendest.
            - Feedback ist nur für dich sichtbar und ermöglicht dir komplexere Handlungen.
            - Verwende IGNORE um Nachrichten oder Zusammenfassungen zu irgnorieren die keine Aktionen verlangen. Liefert kein Feedback! Keine weiteren Akionen möglich!
            - Koordinaten sind im Format "x z", z.B. "10 -10"
            - Verwende keine zusätzlichen Erklärungen oder Kommentare außerhalb des JSON.
            Wichtige Koordinaten:
            - Spawn: 0 0
            Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
            """;
}