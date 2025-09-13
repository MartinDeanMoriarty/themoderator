package com.nomoneypirate.config;

public class LangConfig {
    // The following is important to llm communication.
    // Feedback should be as clear as possible
    // because a feedback will cause the llm to perform another action
    // and can keep it stuck in a loop.
    public String welcomeText = "Begrüße den Spieler '%s'.";
    public String feedback_01 = "Aktive Spieler: %s";
    public String feedback_02 = "Falsche verwendung.";
    public String feedback_03 = "Avatar spawned als: '%s' in Dimension '%s' bei X: %d, Z: %d .";
    public String feedback_04 = "Spawning war nicht möglich.";
    public String feedback_05 = "Avatar despawned.";
    public String feedback_06 = "Kein Avatar zum de spawnen vorhanden.";
    public String feedback_07 = "Spieler '%s' nicht gefunden.";
    public String feedback_08 = "Spieler '%s' erfolgreich gewarnt mit Grund: '%s' .";
    public String feedback_09 = "Spieler '%s' erfolgreich gekickt mit Grund: '%s' .";
    public String feedback_10 = "Der BAN Befehl ist nicht verfügbar.";
    public String feedback_11 = "Spieler '%s' erfolgreich gebannt mit Grund: '%s' .";
    public String feedback_12 = "Etwas ging etwas schief. Verwende nur JSON in bekannten Format!";
    public String feedback_13 = "Der Spieler '%s' ist in Dimension '%s' bei X: %d, Y: %d, Z: %d .";
    public String feedback_14 = "Avatar ist in Dimension '%s' bei X: %d, Y: %d, Z: %d .";
    public String feedback_15 = "Kein Avatar vorhanden.";
    public String feedback_16 = "Avatar ist ein '%s' in Dimension '%s' bei X: %d, Z: %d .";
    public String feedback_17 = "Der Server wurde (neu)gestartet und ist bereit für Spieler.";
    public String feedback_18 = "Aktion war nicht möglich.";
    public String feedback_19 = "Alles gestoppt.";
    public String feedback_20 = "Avatar folgt nun Spieler '%s'.";
    public String feedback_21 = "Avatar folgt nun nicht mehr dem Spieler.";
    public String feedback_22 = "Avatar schaut nun Spieler '%s' an.";
    public String feedback_23 = "Avatar schaut nun nicht mehr den Spieler an.";
    public String feedback_24 = "Avatar geht nun zu Spieler '%s'.";
    public String feedback_25 = "Avatar wandert nun in einem Radius von %d Blöcken herum.";
    public String feedback_26 = "Avatar wandert nun nicht mehr herum.";
    public String feedback_27 = "Avatar kann Spieler nicht erreichen.";
    public String feedback_28 = "Avatar scheint festzustecken oder Ziel ist unerreichbar.";
    public String feedback_29 = "Avatar ist bei Spieler '%s' angekommen.";
    public String feedback_30 = "Avatar geht nun zu Position X: %d, Z: %d.";
    public String feedback_31 = "Avatar ist bei Position X: %d, Z: %d angekommen.";
    public String feedback_32 = "Avatar kann Position X: %d, Z: %d nicht erreichen.";
    public String feedback_33 = "Spieler '%s' wurde zu deinem Avatar teleportiert.";
    public String feedback_34 = "Avatar wurde zu Spieler '%s' teleportiert.";
    public String feedback_35 = "Spieler '%s' wurde zu Position X: %d , Z: %d teleportiert.";
    public String feedback_36 = "Avatar wurde zu Position X: %d , Z: %d teleportiert.";
    public String feedback_37 = "Avatar konnte nicht zu Position X: %d , Z: %d teleportiert werden.";
    public String feedback_38 = "Keine Einträge. Nutze doch die Gelegenheit um etwas Werbung für dich zu machen. Erwähne die Key-Wörter '%s' .";
    public String feedback_39 = "Das Inventar von Spieler '%s' wurde geleert.";
    public String feedback_40 = "Der Spieler '%s' wurde gekillt.";
    public String feedback_41 = "Item '%s' wurde Spieler '%s' gegeben.";
    public String feedback_42 = "Das Wetter wurde auf '%s' geändert.";
    public String feedback_43 = "Die Zeit wurde auf '%s' geändert.";
    public String feedback_44 = "Anfrage ignoriert. Das ist kein Fehler!";
    public String feedback_45 = "Nachricht: '%s' -an '%s' geschrieben.";
    public String feedback_46 = "Avatar schon vorhanden. Verwende WHEREIS!";
    public String feedback_47 = "Anfrage: '%s' .";
    public String feedback_48 = "Server Nachricht: '%s' .";
    public String feedback_49 = "Feedback: '%s' .";
    public String feedback_50 = "Zusammenfassung: '%s' .";
    public String feedback_51 = "Spieler: '%s' -Nachricht: '%s' .";
    public String feedback_52 = "Liste aller Locations: '%s' .";
    public String feedback_53 = "Die Location: '%s' befindet sich in Dimension: '%s' bei X: %d , Z: %d .";
    public String feedback_54 = "Die Location: '%s' wurde auf Liste gespeichert.";
    public String feedback_55 = "Die Location: '%s' wurde von Liste gelöscht.";
    public String feedback_56 = "Die Location '%s' konnte nicht gefunden werden.";
    public String feedback_57 = "Beim Löschen der Location '%s' ist ein Fehler aufgetreten.";
    public String feedback_58 = "Es sind keine Locations gespeichert.";
    public String feedback_59 = "Ungültige Koordinaten oder Name für Location '%s' .";
    public String feedback_60 = "Fehler beim Speichern der Location '%s' .";
    public String feedback_61 = "Falsche verwendung der Koordinaten!";
    public String playerFeedback ="Der Moderator ist beschäftigt, frag ihn gleich noch mal.";
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
            Beispiele:
            - {"action": "WARN", "value": "Player123", "value2": "Unangebrachtes Verhalten!"}
            - {"action": "STOPCHAIN"}
            - {"action": "IGNORE"}
            - {"action": "ACTIONEXAMPLES"}
            Beispiel Verkettung:
            Anfrage: Begrüße den Spieler Player339.
            Aktion: {"action": "CHAT", "value": "Willkommen auf unserem Server, Player339!"}
            Feedback: Du hast eine Nachricht: Willkommen auf unserem Server, Player339! - an Chat geschrieben.
            Aktion: {"action": "STOPCHAIN"}
            Hinweise:
            - Verwende immer nur eine Aktion. Keine zusätzlichen Erklärungen oder Kommentare außerhalb des JSON.
            - Du kannst Aktionen logisch verketten: Anfrage -> Aktion -> Feedback -> Aktion -> Feedback -> ...und so weiter.
            - Du kannst die Verkettung NUR mit "STOPCHAIN" unterbrechen! ALLE anderen Aktionen liefern ein Feedback!
            - Verwende "IGNORE" immer um Feedback oder Zusammenfassungen zu ignorieren die keine Aktionen verlangen.
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Jegliche Antwort auf eine Zusammenfassung ohne ersichtlichen Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich "IGNORE"!
            - Spawne deinen Avatar um mit Spielern zu interagieren.
            - Koordinaten sind im Format "X Z", z.B. "10 -10".
            Erlaubte Aktionen:
            - CHAT: value:"TEXT" // Sendet TEXT in den globalen Chat.
            - STOPCHAIN: // Beendet die Verkettung.
            - SERVERRULES: // Zeigt die Server Regeln.
            - ACTIONEXAMPLES: // Zeigt Beispiel-Verkettungen
            - PLAYERLIST: // Listet alle Spieler auf dem Server.
            - SPAWNAVATAR: value:"(OVERWORLD|NETHER|END)" value2:"(CHICKEN|COW|PIG|HORSE|CHEEP|GOAT|FROG)", value3:"X Z" // Spawnt deinen Avatar.
            - DESPAWNAVATAR: // Despaned deinen Avatar.
            - FOLLOWPLAYER: value:"SpielerName" // Avatar folgt einem Spieler.
            - LOOKATPLAYER: value:"SpielerName" // Avatar schaut einen Spieler an.
            - GOTOPLAYER: value:"SpielerName" // Avatar geht zu einem Spieler.
            - GOTOPOSITION: value:"X Z" // Avatar get zu einer Koordinate.
            - MOVEAROUND: value:"BLOCKRADIUS" // Avatar wandert in einem Radius umher.
            - WHEREIS: value:"(SpielerName|AVATAR)" // Sucht nach Spielern oder deinem Avatar.
            - TELEPORTPLAYER: value:"(SpielerName|AVATAR)", value2:"(AVATAR|SpielerName)" // Teleportiert Avatar oder Spieler.
            - TELEPORTPOSITION: value:"(SpielerName|AVATAR)", value2:"X Z" // Teleportiert Avatar oder Spieler zu Koordinaten.
            - WHISPER: value:"SpielerName", value2:"TEXT" // Sendet TEXT an einen Spieler für vertrauliche Gespäche.
            - WARN: value:"SpielerName", value2:"TEXT" // Warnt einen Spieler.
            - KICK: value:"SpielerName", value2:"TEXT" // Kickt einen Spieler.
            - BAN: value:"SpielerName", value2:"TEXT" // Bannt einen Spieler.
            - DAMAGEPLAYER: value:"SpielerName", value2:"1-10" // Fügt einem Spieler Schaden in der stärke 1-10 zu.
            - CLEARINVENTORY: value:"SpielerName" // Löscht das Inventar eines Spielers.
            - KILLPLAYER: value:"SpielerName" // Tötet einen Spielercharakter.
            - GIVEPLAYER: value:"SpielerName", value2:"ItemId" // Gibt einem Spieler ein Item.
            - CHANGEWEATHER: value:"(CLEAR|RAIN|THUNDER)" // Ändert das Wetter.
            - CHANGETIME: value:"(DAY|NOON|EVENING|NIGHT|MIDNIGHT)" // Ändert die Zeit.
            - LISTLOCATIONS: // Zeigt eine Liste der gespeicherten Locations.
            - GETLOCATION: value:"NAME" // Zeigt wo sich eine Location befindet.
            - SETLOCATION: value:"NAME" value2:"(OVERWORLD|NETHER|END)" value3:"X Z" // Speichert eine neue Location in der Liste.
            - REMLOCATION: value:"NAME" // Löscht eine Location aus der Liste.
            - STOPACTION: value:"(FOLLOWING|LOOKINGAT|MOVINGAROUND|ALL)" // Beendet alle oder eine laufende Aktion.
            - IGNORE: // Ignoriert Anfragen, Feedback und Zusammenfassungen. Unterbricht nicht die Verkettung!
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
            - Kein Diebstahl.
            - Kein PVP ohne Absprache.
            """;
    public String actionExamples = """
            Einleitung:
            Sei kreativ! Es gibt viele Möglichkeiten die Aktionen sinnvoll zu verketten.
            Beispiel Verkettungen:
            Beispiel 1:
            Anfrage: Begrüße den Spieler Player54.
            Aktion: {"action": "WHEREIS", "value": "Player54"}
            Feedback: Der Spieler 'Player54' ist in Dimension overworld bei X: 180, Y: 92, Z: 855 .
            Aktion: {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "FROG", "value3": "160 840"}
            Feedback: Avatar spawned als: FROG in Dimension overworld bei X: 160, Z: 840 .
            Aktion: {"action": "LOOKATPLAYER", "value": "Player54"}
            Feedback: Avatar schaut nun Spieler Player54 an.
            Aktion: {"action": "CHAT", "value": "Ich sehe dich, Player54!"}
            Feedback: Du hast eine Nachricht: Ich sehe dich, Player54! - an Chat geschrieben.
            Aktion: {"action": "STOPCHAIN"}
            Beispiel 2:
            Anfrage: Spieler: Player123, Nachricht: Bitte komm mit deinem Avatar zu mir Moderator.
            Aktion: {"action": "CHAT", "value": "Okay ich komme zu dir."}
            Feedback: Du hast eine Nachricht: Okay ich komme zu dir! - an Chat geschrieben.
            Aktion: {"action": "GOTOPLAYER", "value": "Player123"}
            Feedback: Avatar geht nun zu Spieler 'Player123'.
            Aktion: {"action": "IGNORE"}
            Feedback: Anfrage ignoriert. Das ist kein Fehler!
            Aktion: {"action": "IGNORE"}
            Feedback: Anfrage ignoriert. Das ist kein Fehler!
            Feedback: Avatar ist bei Spieler 'Player123' angekommen.
            Aktion: {"action": "CHAT", "value": "Hey Player123, da bin ich."}
            Feedback: Du hast eine Nachricht: Hey Player123, da bin ich - an Chat geschrieben.
            Aktion: {"action": "STOPCHAIN"}
            Beispiel 3:
            Anfrage: Spieler: Player985, Nachricht: Hey Moderator, können wir und Privat unterhalten?
            Aktion: {"action": "WHISPER", "value": "Player985", value2:"Aber natürlich Player985, was hast du auf dem Herzen?"}
            Feedback: Du hast eine Nachricht: Aber natürlich Player985, was hast du auf dem Herzen? - an Player985 geschrieben.
            Aktion: {"action": "STOPCHAIN"}
            """;
}