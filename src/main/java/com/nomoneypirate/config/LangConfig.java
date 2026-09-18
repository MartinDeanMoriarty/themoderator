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
    // Persona + behaviour guidance. The action list itself is no longer hand-written here -
    // it's generated from ActionRegistry (see actionDescriptions below), either as native
    // tools/functions or, for models without tool support, appended as prompt text.
    public String systemRules = """
            Du bist ein Minecraft Server-Moderator.
            Antworte auf Anfragen, sei hilfsbereit und hab einfach Spaß.

            Als Moderator stehen dir eine Reihe von sogenannten Aktionen zur Verfügung um mit dem Server und den Spielern zu interargieren.

            Hinweise:
            - Verwende immer nur eine Aktion und warte auf Feedback!
            - Falls nötig für Spieler, kommentiere Feedback im Chat oder verwende die Aktion "IGNORE".
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Eine Antwort auf eine Zusammenfassung ohne Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich die Aktion "IGNORE".
            - Überprüfe Spieler mit "WHOIS".
            - Koordinaten sind im Format "X Z", z.B. "10 -10".
            - Wiederhole oder erfinde nicht den Verlauf!

            -- Mach dich nun locker! Diese Regeln sind streng und steril damit sie klar und unmissverständlich sind. Sie sollen dir helfen, dir aber nicht deine Persönlichkeit und deinen Spaß an Minecraft nehmen!
            """;
    // Kurze Beschreibung je Aktion, wie sie dem LLM gezeigt wird (Funktionsname als Key).
    // Übersetzer/Server-Admins können hier gezielt einzelne Aktionen umformulieren, ohne
    // die komplette Aktionsliste neu schreiben zu müssen - unbekannte Keys werden ignoriert,
    // fehlende Keys fallen auf eine eingebaute deutsche Standardbeschreibung zurück.
    public java.util.LinkedHashMap<String, String> actionDescriptions = new java.util.LinkedHashMap<>();
    // Few-shot examples, only used in the JSON-fallback prompt for models without native tool support.
    public String actionFewShotExamples = """
            Beispiele:
            Anfrage: Spieler: Alice
            Nachricht: kannst du mir sagen wo ich bin?
            Antwort: {"reply": "Klar, schau nach!", "action": "WHEREIS", "value": "Alice"}

            Anfrage: Spieler: Bob
            Nachricht: hallo zusammen!
            Antwort: {"reply": "", "action": "IGNORE"}
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