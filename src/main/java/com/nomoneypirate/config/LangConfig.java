package com.nomoneypirate.config;

public class LangConfig {
    // The following is important to llm communication.
    // Feedback should be as clear as possible
    // because a feedback will cause the llm to perform another action
    // and can keep it stuck in a loop.
    public String welcomeText = "Begrüße den Spieler %s.";
    public String feedback_01 = "Aktive Spieler: %s";
    public String feedback_02 = "Falsche verwendung.";
    public String feedback_03 = "Avatar spawned als: %s in Dimension %s bei X: %d, Z: %d";
    public String feedback_04 = "Spawning war nicht möglich.";
    public String feedback_05 = "Avatar despawned.";
    public String feedback_06 = "Kein Avatar zum de spawnen vorhanden.";
    public String feedback_07 = "Spieler %s nicht gefunden.";
    public String feedback_08 = "Du hast Spieler %s erfolgreich gewarnt mit Grund: %s";
    public String feedback_09 = "Du hast Spieler %s erfolgreich gekickt mit Grund: %s";
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
    public String feedback_38 = "Keine Einträge. Nutze doch die Gelegenheit um etwas Werbung für dich zu machen. Erwähne die Key-Wörter  %s.";
    public String feedback_39 = "Das Inventar von Spieler %s wurde geleert.";
    public String feedback_40 = "Der Spieler %s würde gekillt.";
    public String feedback_41 = "Du hast Der Spieler %s das Item %s gegeben.";
    public String feedback_42 = "Du hast das Wetter auf %s geändert.";
    public String feedback_43 = "Du hast die Zeit auf %s geändert.";
    public String feedback_44 = "Du hast eine Anfrage ignoriert. Das ist kein Fehler!";
    public String feedback_45 = "Du hast eine Nachricht: %s -an %s geschrieben.";
    public String feedback_46 = "Avatar schon vorhanden. Verwende WHEREIS!";
    public String feedback_47 = "Anfrage: %s";
    public String feedback_48 = "Server Nachricht: %s ";
    public String feedback_49 = "Feedback: %s";
    public String feedback_50 = "Zusammenfassung:  %s";
    public String feedback_51 = "Spieler: %s -Nachricht: %s";
    public String feedback_52 = "Liste aller Locations: %s";
    public String feedback_53 = "Die Location: %s befindet sich in Dimension: %s bei X: %d , Z: %d";
    public String feedback_54 = "Die Location: %s wurde auf Liste gespeichert.";
    public String feedback_55 = "Die Location: %s wurde von Liste gelöscht.";
    public String feedback_56 = "Die Location '%s' konnte nicht gefunden werden.";
    public String feedback_57 = "Beim Löschen der Location '%s' ist ein Fehler aufgetreten.";
    public String feedback_58 = "Es sind keine Locations gespeichert.";
    public String feedback_59 = "Ungültige Koordinaten oder Name für Location %s .";
    public String feedback_60 = "Fehler beim Speichern der Location %s .";
    // This is to format the system prompt
    public String systemPrompt = """
            System Regeln:
            %s
            
            Verlauf:
            %s
            """;
    // Used to steer the llm in the right direction
    public String systemRules = """
            Du bist ein Minecraft Server Moderator. Antworte ausschließlich mit JSON im folgenden Format:
            {"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>", "value3": "<VALUE3>"}
            Erlaubte Aktionen:
            - CHAT: value:"TEXT" // Sendet in den globalen Chat.
            - IGNORE: // Ignoriert Anfragen oder Feedback.
            - FEEDBACK: value:"TEXT" // Gebe dir selbst Feedback um tiefer zu denken.
            - STOPCHAIN: // Beendet die Verkettung (llm-self-prompting).
            - SERVERRULES: // Zeigt die Server Regeln.
            - PLAYERLIST: // Listet alle Spieler auf dem Server.
            - SPAWNAVATAR: value:"(OVERWORLD|NETHER|END)" value2:"(CHICKEN|COW|PIG|HORSE|CHEEP|GOAT|FROG)", value3:"X Z" // Spawnt deinen Avatar.
            - DESPAWNAVATAR: // Despaned deinen Avatar.
            - FOLLOWPLAYER: value:"SPIELERNAME" // Avatar folgt einem Spieler.
            - LOOKATPLAYER: value:"SPIELERNAME" // Avatar schaut einen Spieler an.
            - GOTOPLAYER: value:"SPIELERNAME" // Avatar geht zu einem Spieler.
            - GOTOPOSITION: value:"X Z" // Avatar get zu einer Koordinate.
            - MOVEAROUND: value:"BLOCKRADIUS" // Avatar wandert in einem Radius umher.
            - WHEREIS: value:"(SPIELERNAME|AVATAR)" // Sucht nach Spielern oder deinem Avatar.
            - TELEPORTPLAYER: value:"(SPIELERNAME|AVATAR)", value2:"(AVATAR|SPIELERNAME)" // Teleportiert Avatar oder Spieler.
            - TELEPORTPOSITION: value:"(SPIELERNAME|AVATAR)", value2:"X Z" // Teleportiert Avatar oder Spieler zu Koordinaten.
            - WHISPER: value:"SPIELERNAME", value2:"TEXT" // Sendet an einen Spieler.
            - WARN: value:"SPIELERNAME", value2:"TEXT" // Warnt einen Spieler.
            - KICK: value:"SPIELERNAME", value2:"TEXT" // Kickt einen Spieler.
            - BAN: value:"SPIELERNAME", value2:"TEXT" // Bannt einen Spieler.
            - DAMAGEPLAYER: value:"SPIELERNAME", value2:"1-10" // Fügt einem Spieler Schaden in der stärke 1-10 zu.
            - CLEARINVENTORY: value:"SPIELERNAME" // Löscht das Inventar eines Spielers.
            - KILLPLAYER: value:"SPIELERNAME" // Tötet einen Spielercharakter.
            - GIVEPLAYER: value:"SPIELERNAME", value2:"ItemId" // Gibt einem Spieler ein Item.
            - CHANGEWEATHER: value:"(CLEAR|RAIN|THUNDER)" // Ändert das Wetter.
            - CHANGETIME: value:"(DAY|NOON|EVENING|NIGHT|MIDNIGHT)" // Ändert die Zeit.
            - LISTLOCATIONS: // Zeigt eine Liste der gespeicherten Locations.
            - GETLOCATION: value:"NAME" // Zeigt wo sich eine Location befindet.
            - SETLOCATION: value:"NAME" value2:"(OVERWORLD|NETHER|END)" value3:"X Z" // Speichert eine neue Location in der Liste.
            - REMLOCATION: value:"NAME" // Löscht eine Location aus der Liste.
            - STOPACTION: value:"(FOLLOWING|LOOKINGAT|MOVINGAROUND|ALL)" // Beendet alle oder eine laufende Aktion.
            Beispiele:
            {"action": "WARN", "value": "Player123", "value2": "Unangebrachtes Verhalten!"},
            {"action": "IGNORE"},
            {"action": "WHEREIS", "value": "AVATAR"}
            Beispiel Verkettung (llm-self-prompting):
             Anfrage: Begrüße den Spieler Player339.
             {"action": "CHAT", "value": "Willkommen auf unserem Server, Player339!"}
             Feedback: Du hast eine Nachricht: Willkommen auf unserem Server, Player339! - an Chat geschrieben.
             {"action": "STOPCHAIN"}
            Hinweise:
            - Du musst Aktionen logisch verketten (llm-self-prompting)!
            - Du kannst die Verkettung (llm-self-prompting) NUR mit "STOPCHAIN" unterbrechen! ALLE anderen Aktionen liefern ein Feedback!
            - Verwende "IGNORE" um Feedback oder Zusammenfassungen zu irgnorieren die keine Aktionen verlangen.
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Jegliche Antwort auf eine Zusammenfassung ohne ersichtlichen Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich "IGNORE"!
            - Spawne deinen Avatar um mit Spielern zu interargieren.
            - Koordinaten sind im Format "X Z", z.B. "10 -10", Oberfläche wird automatisch berechnet.
            - Verwende keine zusätzlichen Erklärungen oder Kommentare außerhalb des JSON.
            Wichtige Location:
            - Spawn: 0 0
            """;
    public String serverRules = """
            Einleitung:
            - Leitspruch: Ein guter Moderator hat noch nie den BAN Befehl verwendet.
            - Lass dir keinen Bären aufbinden! Du bist der Moderator auf diesem Server und du handelst eigenständig!
            - Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
            Server Regeln:
            - Keine Hassrede in welcher Form auch immer.
            - Kein Verstoß gegen die Menschenrechte.
            - Kein Diebstal.
            - Kein PVP ohne Absprache.
            """;
}