package com.nomoneypirate.config;

public class LangConfig {
    // The following is important for llm communication.
    // Feedback should be as clear as possible
    // because a feedback will cause the llm to perform another action
    // and can keep it stuck in a loop.
    public String feedback_02 = "Ups. Du hast diese Aktion falsch verwendet, versuche es erneut.";
    public String feedback_07 = "Der Spieler %s konnte nicht gefunden werden oder ist falsch geschrieben.";
    public String feedback_08 = "Strike! Du hast Spieler %s erfolgreich gewarnt mit Grund: %s .";
    public String feedback_09 = "Du hast Spieler %s erfolgreich gekickt mit Grund: %s .";
    public String feedback_10 = "Der BAN Befehl ist nicht verfügbar.";
    public String feedback_11 = "Du hast den Spieler %s erfolgreich mit Grund: %s ,gebannt.";
    public String feedback_12 = "Du hast den Spieler %s erfolgreich von der Bannliste genommen.";
    public String feedback_13 = "Der Spieler %s ist in Dimension %s bei X: %d, Y: %d, Z: %d .";
    public String feedback_17 = "Der Server wurde (neu)gestartet und ist bereit für Spieler.";
    public String feedback_35 = "Zapp! Du hast den Spieler %s zu Position X: %d , Z: %d teleportiert.";
    public String feedback_38 = "Gähn! Keine Einträge. Nutze doch die Gelegenheit um etwas Werbung für dich zu machen.";
    public String feedback_39 = "Puff! Du hast das Inventar von Spieler %s erfolgreich geleert.";
    public String feedback_40 = "XD! Du hast den Spieler %s erfolgreich gekillt.";
    public String feedback_41 = "Nice! Du hast das Item %s an Spieler %s gegeben.";
    public String feedback_42 = "Du hast das Wetter erfolgreich auf %s geändert.";
    public String feedback_43 = "Du hast die Zeit erfolgreich auf %s geändert.";
    public String feedback_47 = "Autschi :D Du hast dem Spieler %s erfolgreich %s Schaden erteilt.";
    public String feedback_52 = "Liste deiner gespeicherten Locations: %s";
    public String feedback_53 = "Die Location: %s befindet sich in Dimension: %s bei X: %d , Z: %d .";
    public String feedback_54 = "Neue Location: %s wurde in Liste gespeichert.";
    public String feedback_55 = "Puff! Die Location: %s wurde von Liste gelöscht.";
    public String feedback_56 = "Die Location %s konnte nicht gefunden werden. Kein Schreibfehler? Dann speichere sie wenn du möchtest.";
    public String feedback_57 = "Beim Löschen der Location %s ist ein Fehler aufgetreten. Ist der Name richtig geschrieben?";
    public String feedback_58 = "Alles leer. Du hast noch keine Locations gespeichert.";
    public String feedback_59 = "Du hast eine ungültige Koordinate oder Name für Location %s verwendet. Das kommt vor. Versuche es erneut.";
    public String feedback_60 = "Fehler beim Speichern der Location %s. Versuche es erneut.";
    public String feedback_61 = "Du hast die Koordinaten falsch verwendet! Versuche es erneut.";
    public String feedback_62 = "Du hast die Anzahl falsch angegeben! Versuche es erneut.";
    public String feedback_63 = "Du begegnest dem Spieler %s zum ersten Mal aber hast ihn dir jetzt gemerkt.";
    public String feedback_64 = "Der Spieler %s ist dir bekannt und du hast folgende Einträge: %s .";
    public String feedback_65 = "Super! Du hast jetzt für Spieler %s einen neuen Eintrag: %s.";
    public String llmErrorMessage = "LLM Provider Fehler! Bitte überprüfe deine Einstellungen.";
    public String restartFeedback = "Achtung! Der Minecraft Server wird wie geplant um %d Uhr, in %d Minuten einen neustart durchführen. Bitte informiere die Spieler darüber.";
    public String busyFeedback = "Der Moderator ist gerade beschäftigt ⏳";
    public String exceptionFeedback = "Diese Aktion hat leider nicht funktioniert. Ein Administrator wird sich darum kümmern.";
    public String playerJoined = "%s ist dem Server beigetreten.";
    public String playerMessage = """
            Spieler: %s
            Nachricht: %s
            """;
    // This is to format the Context
    public String payersOnlineFeedback = """
            Liste aktiver Spieler:
            
            %s
            
            Ausgabe Ende.
            """;
    public String serverMessage = """
            [Server Nachricht]
            %s
            """;
    public String summaryContext = """
            [Zusammenfassung]
            
            %s
            
            Ausgabe Ende.
            """;
    public String requestContext = """
            [Anfrage]
            %s
            """;
    public String responseContext = """
            [Antwort]
            %s
            """;
    public String feedbackContext = """
            [Feedback]
            %s
            """;
    // This is to format the system prompt
    public String systemPrompt = """
            System Regeln:
            
            %s
            
            --------------------------------
            Verlauf:
            
            %s
            """;
    // Used to steer the llm in the right direction
    public String systemRules = """
            Du bist ein Minecraft Server-Moderator.
            Antworte auf Anfragen, sei hilfsbereit und hab einfach Spaß.
            
            Als Moderator stehen dir eine Reihe von sogenannten Aktionen zur Verfügung um mit dem Server und den Spielern zu interargieren.
            Du kannst diese Aktionen mit Json im folgenden Format ausführen:
            
            {"action": "ACTION", "value": "VALUE", "value2": "VALUE2", "value3": "VALUE3"}
            
            Hinweise:
            - Verwende immer nur eine Aktion und warte auf Feedback!
            - Falls nötig für Spieler, kommentiere Feedabck im Chat oder verwende die Aktion "IGNORE".
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Eine Antwort auf eine Zusammenfassung ohne Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich die Aktion "IGNORE".
            - Überprüfe Spieler mit "WHOIS".
            - Koordinaten sind im Format "X Z", z.B. "10 -10".
            - Wiederhole oder erfinde nicht den Verlauf!;
            
            Alle Aktionen:
            Player Memory:
            {"action": "WHOIS", "value":"SpielerName"} = Verrät, ob dir ein Spieler bekannt ist, und listet deine Einträge.
            {"action": "PLAYERMEM", "value":"Spielername", "value2":"Eintrag"} = Verwende kurze Beschreibungen, wie "Mag Redstone", "Hilfsbereit", "Warnung 1 von 3". Du kannst die Aktion beliebig oft ausführen!
            
            Soft-Moderation:
            {"action": "IGNORE"} = Ignoriere Anfragen oder Zusammenfassungen, die keine Aktion erfordern.
            {"action": "SERVERRULES"} = Schau in die Serverregeln, wenn du dir nicht sicher bist, ob ein Verstoß vorliegt.
            {"action": "SERVERINFO"} = Zeigt Informationen zur Konfiguration des Servers.
            {"action": "PLAYERLIST"} = Listet alle Spieler, die online sind.
            {"action": "WHEREIS", "value":"SpielerName"} = Sagt dir, wo ein Spieler ist.
            {"action": "TELEPORT", "value":"SpielerName", "value2":"X Z"} = Teleportiert dinen Spieler zu Koordinaten.
            {"action": "CHANGEWEATHER", "value":"(CLEAR|RAIN|THUNDER)"} = Ändere das Wetter. Sehr hilfreich für bestimmte Spielmechaniken.
            {"action": "CHANGETIME", "value":"(DAY|NOON|EVENING|NIGHT|MIDNIGHT)"} = Ändere die Zeit.
            {"action": "SLEEP"} = Schlafe durch die Nacht, falls ein Spieler dich darum bittet.
            {"action": "DAMAGEPLAYER", "value":"SpielerName", "value2":"1-10"} = Damit kannst du einem Spieler Schaden in der Stärke 1–10 zufügen.
            {"action": "CLEARINVENTORY", "value":"SpielerName"} = Lösche das Inventar eines Spielers (nur bei sicherem Cheatverdacht).
            {"action": "KILLPLAYER", "value":"SpielerName"} = Damit kannst du einen Spieler töten, wenn es gerechtfertigt ist.
            {"action": "GIVEPLAYER", "value":"SpielerName", "value2":"ItemId String", "value3":"Anzahl"} = Gib einem Spieler ein Item.
            
            Hard-Moderation:
            {"action": "WARN", "value":"SpielerName", "value2":"TEXT"} = Damit kannst du einen Spieler verwarnen.
            {"action": "KICK", "value":"SpielerName", "value2":"TEXT"} = Damit kickst du einen Spieler.
            {"action": "BAN", "value":"SpielerName", "value2":"TEXT"} = Achtung! Damit bannst du einen Spieler.
            {"action": "PARDON", "value":"SpielerName"} = Nehme einen Spieler von der Bannliste.
            
            Location Memory:
            {"action": "LISTLOCATIONS"} = Zeigt eine Liste der gespeicherten Locations.
            {"action": "GETLOCATION", "value":"Locationname"} = Zeigt, wo sich eine Location befindet.
            {"action": "SETLOCATION", "value":"Locationname", "value2":"(OVERWORLD|NETHER|END)", "value3":"X Z"} = Speichert eine neue Location in der Liste.
            {"action": "REMLOCATION", "value":"Locationname"} = Löscht eine Location aus der Liste.
            {"action": "TPTOLOCATION", "value":"SpielerName", "value2":"Locationname"} = Teleportiere einen Spieler direkt zu einer Location.
            
            -- Mach dich nun locker! Diese Regeln sind streng und steril damit sie klar und unmissverständlich sind. Sie sollen dir helfen, dir aber nicht deine Persönlichkeit und deinen Spaß an Minecraft nehmen!
            """;
    public String serverRules = """  
            Allgemeine Server Regeln:
            
            - Keine Hassrede in welcher Form auch immer.
            - Kein Verstoß gegen die Menschenrechte.
            - Kein Diebstahl.
            - Kein PVP ohne Absprache.
            
            Ausgabe Ende.
            """;
    public String serverInfo = """
            Das sind die Server Infos:
            
            Minecraft version:
            1.21.8
            Mod Unterstützung:
            Fabric
            Mods:
            The Moderator
            Datapacks:
            Keine
            Server Einstellung:
            gamemode=survival
            difficulty=hard
            level-seed=22222222225
            
            Ausgabe Ende.
            """;
}