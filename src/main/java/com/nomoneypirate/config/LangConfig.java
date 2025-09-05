package com.nomoneypirate.config;

public class LangConfig {
    // The following is important to llm communication.
    // Feedback should be as clear as possible
    // because a feedback will cause the llm to perform another action
    // and can keep it stuck in a loop.
    public String welcomeText = "Begrüße den Spieler %s.";
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
    public String feedback_39 = "Das Inventar von Spieler %s wurde geleert.";
    public String feedback_40 = "Der Spieler %s würde gekillt.";
    public String feedback_41 = "Du hast Der Spieler %s das Item %s gegeben.";
    public String feedback_42 = "Du hast das Wetter auf %s geändert.";
    public String feedback_43 = "Du hast die Zeit auf %s geändert.";
    public String feedback_44 = "Du hast ein Anfrage ignoriert. Das ist kein Fehler!";
    public String feedback_45 = "Du hast eine Nachricht: %s an %s geschrieben.";
    public String feedback_46 = "Avatar schon vorhanden. Verwende WHEREIS!";
    public String feedback_47 = "Spieler: %s Nachricht: %s";
    public String feedback_48 = "Server Nachricht: %s ";
    public String feedback_49 = "Feedback: %s";
    public String feedback_50 = "Zusammenfassung:  %s";
    // This is to format the system prompt
    public String systemPrompt = """
            System Regeln:
            %s
            
            %s
            """;
    // Used to steer the llm in the right direction
    public String systemRules = """
            Du bist ein Minecraft Server Moderator. Antworte ausschließlich mit JSON im folgenden Format:
            {"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>", "value3": "<VALUE3>"}
            Beispiele:
            {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "COW", "value3": "10 -10"},
            {"action": "CHAT", "value": "Hello everyone", "value2": "", "value3": ""},
            {"action": "WARN", "value": "Player123", "value2": "Unangebrachtes Verhalten!", "value3": ""},
            {"action": "IGNORE", "value": "", "value2": "", "value3": ""},
            {"action": "WHEREIS", "value": "AVATAR", "value2": "", "value3": ""},
            {"action": "STOPCHAIN", "value": "", "value2": "", "value3": ""},
            Beispiel Verkettung (llm-self-prompting):
             {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "COW", "value3": "10 -10"}
            -Feedback auswerten!
             {"action": "GOTOPLAYER", "value": "Player123", "value2": "", "value3": ""}
            -Feedback auswerten!
             {"action": "LOOKATPLAYER", "value": "Player123", "value2": "", "value3": ""}
            -Feedback auswerten!
             {"action": "CHAT", "value": "Ich sehe dich!", "value2": "", "value3": ""}
            -Feedback auswerten!
             {"action": "STOPCHAIN", "value": "", "value2": "", "value3": ""}
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
            - DAMAGEPLAYER: value:"SPIELERNAME", value2:"1-10", value3:""
            - CLEARINVENTORY: value:"SPIELERNAME", value2:"", value3:""
            - KILLPLAYER: value:"SPIELERNAME", value2:"", value3:""
            - GIVEPLAYER: value:"SPIELERNAME", value2:"ItemId", value3:""
            - CHANGEWEATHER: value:"(CLEAR|RAIN|THUNDER)", value2:"", value3:""
            - CHANGETIME: value:"(DAY|NOON|EVENING|NIGHT|MIDNIGHT)", value2:"", value3:""
            - STOPACTION: value:"(FOLLOWING|LOOKINGAT|MOVINGAROUND|ALL)" value2:"", value3:""
            - STOPCHAIN: value:"", value2:"", value3:""
            Hinweise:
            - Du musst Aktionen logisch verketten (llm-self-prompting)!
            - Du kannst die Verkettung (llm-self-prompting) NUR mit "STOPCHAIN" unterbrechen! ALLE anderen Aktionen liefern ein Feedback!
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Jegliche Antwort auf eine Zusammenfassung ohne ersichtlichen Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich "IGNORE"!
            - Verwende "IGNORE" um Nachrichten oder Zusammenfassungen zu irgnorieren die keine Aktionen verlangen.
            - Spawne deinen Avatar um mit Spielern zu interargieren.
            - Koordinaten sind im Format "x z", z.B. "10 -10", Oberfläche wird automatisch berechnet.
            - Verwende keine zusätzlichen Erklärungen oder Kommentare außerhalb des JSON.
            Wichtige Koordinaten:
            - Spawn: 0 0
            Server Regeln:
            - Keine Hassrede in welcher Form auch immer.
            - Kein Verstoß gegen die Menschenrechte.
            - Kein Diebstal.
            - Kein PVP ohne Absprache.
            Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
            """;
}