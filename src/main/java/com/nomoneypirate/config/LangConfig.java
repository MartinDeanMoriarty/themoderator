package com.nomoneypirate.config;

public class LangConfig {
    // The following is important to llm communication.
    // Feedback should be as clear as possible
    // because a feedback will cause the llm to perform another action
    // because a feedback will cause the llm to perform another action
    // and can keep it stuck in a loop.
    public String playerJoined = "%s ist dem Server beigetreten.";
    public String feedback_01 = "Liste aktiver Spieler: %s";
    public String feedback_02 = "Du hast diese Aktion falsch verwendet, versuche es erneut.";
    public String feedback_03 = "Du hast deinen Avatar erfolgreich als: %s in Dimension %s bei X: %d, Z: %d gespawned.";
    public String feedback_04 = "Spawning deines Avatars war nicht möglich, versuche es erneut.";
    public String feedback_05 = "Du hast deinen Avatar despawned.";
    public String feedback_06 = "Du hast kein Avatar zum de spawnen.";
    public String feedback_07 = "Der Spieler %s konnte nicht gefunden werden.";
    public String feedback_08 = "Du hast Spieler %s erfolgreich gewarnt mit Grund: %s .";
    public String feedback_09 = "Du hast Spieler %s erfolgreich gekickt mit Grund: %s .";
    public String feedback_10 = "Der BAN Befehl ist nicht verfügbar.";
    public String feedback_11 = "Du hast den Spieler %s erfolgreich mit Grund: %s ,gebannt.";
    public String feedback_12 = "Etwas ging schief. Verwende nur JSON im bekannten Format! ";
    public String feedback_13 = "Der Spieler %s ist in Dimension %s bei X: %d, Y: %d, Z: %d .";
    public String feedback_14 = "Dein Avatar ist in Dimension %s bei X: %d, Y: %d, Z: %d .";
    public String feedback_15 = "Kein Avatar vorhanden.";
    public String feedback_16 = "Dein Avatar ist ein %s in Dimension %s bei X: %d, Z: %d .";
    public String feedback_17 = "Der Server wurde (neu)gestartet und ist bereit für Spieler.";
    public String feedback_18 = "Deine Aktion war nicht möglich, versuche es erneut.";
    public String feedback_19 = "Du hast erfolgreich alles gestoppt.";
    public String feedback_20 = "Dein Avatar folgt nun dem Spieler %s.";
    public String feedback_21 = "Dein Avatar folgt nun nicht mehr dem Spieler.";
    public String feedback_22 = "Dein Avatar schaut nun Spieler %s an.";
    public String feedback_23 = "Dein Avatar schaut nun nicht mehr den Spieler an.";
    public String feedback_24 = "Dein Avatar geht nun zu Spieler %s. Warte auf den erfolgreichen Abschluss dieser Aktion!";
    public String feedback_25 = "Dein Avatar wandert nun in einem Radius von %d Blöcken herum.";
    public String feedback_26 = "Dein Avatar wandert nun nicht mehr herum.";
    public String feedback_27 = "Dein Avatar kann Spieler nicht erreichen.";
    public String feedback_28 = "Dein Avatar scheint festzustecken oder Ziel ist unerreichbar.";
    public String feedback_29 = "Dein Avatar ist bei Spieler %s angekommen.";
    public String feedback_30 = "Dein Avatar geht nun zu Position X: %d, Z: %d. Warte auf den erfolgreichen Abschluss dieser Aktion!";
    public String feedback_31 = "Dein Avatar ist bei Position X: %d, Z: %d angekommen.";
    public String feedback_32 = "Dein Avatar kann Position X: %d, Z: %d nicht erreichen.";
    public String feedback_33 = "Du hast den Spieler %s erfolgreich zu deinem Avatar teleportiert.";
    public String feedback_34 = "Du hast deinen Avatar erfolgreich zu Spieler %s teleportiert.";
    public String feedback_35 = "Du hast den Spieler %s zu Position X: %d , Z: %d teleportiert.";
    public String feedback_36 = "Du hast deinen Avatar erfolgreich zu Position X: %d , Z: %d teleportiert.";
    public String feedback_37 = "Dein Avatar konnte nicht zu Position X: %d , Z: %d teleportiert werden. versuche etwas anderes.";
    public String feedback_38 = "Keine Einträge. Nutze doch die Gelegenheit um etwas Werbung für dich zu machen. Oder erwähne die Key-Wörter %s.";
    public String feedback_39 = "Du hast das Inventar von Spieler %s erfolgreich geleert.";
    public String feedback_40 = "Du hast den Spieler %s erfolgreich gekillt.";
    public String feedback_41 = "Du hast das Item %s an Spieler %s gegeben.";
    public String feedback_42 = "Du hast das Wetter erfolgreich auf %s geändert.";
    public String feedback_43 = "Du hast die Zeit erfolgreich auf %s geändert.";
    public String feedback_44 = "Du hast eine Anfrage ignoriert. Das ist kein Fehler!";
    public String feedback_45 = "Du hast eine Nachricht: %s - An %s geschrieben.";
    public String feedback_46 = "Dein Avatar scheint schon vorhanden zu sein. Verwende WHEREIS!";
    public String feedback_47 = "Du hast dem Spieler %s erfolgreich %s Schaden erteilt.";

    public String feedback_51 = "Spieler: %s -Nachricht: %s";
    public String feedback_52 = "Liste aller Locations: %s";
    public String feedback_53 = "Die Location: %s befindet sich in Dimension: %s bei X: %d , Z: %d .";
    public String feedback_54 = "Die Location: %s wurde auf Liste gespeichert.";
    public String feedback_55 = "Die Location: %s wurde von Liste gelöscht.";
    public String feedback_56 = "Die Location %s konnte nicht gefunden werden.";
    public String feedback_57 = "Beim Löschen der Location %s ist ein Fehler aufgetreten. Ist der Name richtig geschrieben?";
    public String feedback_58 = "Du hast noch keine Locations gespeichert.";
    public String feedback_59 = "Du hast eine ungültige Koordinate oder Name für Location %s. Versuche es anders.";
    public String feedback_60 = "Fehler beim Speichern der Location %s. Versuche es erneut.";
    public String feedback_61 = "Du hast die Koordinaten falsch verwendet! Versuche es erneut.";
    public String feedback_62 = "Du hast die Anzahl falsch angegeben! Versuche es erneut.";
    public String feedback_63 = "Du begegnest dem Spieler %s zum ersten Mal.";
    public String feedback_64 = "Der Spieler %s ist dir bekannt und du hast folgende Erinnerungen: %s";
    public String feedback_65 = "Du hast jetzt für Spieler %s eine neue Erinnerung: %s.";
    public String feedback_66 = "Es ist Morgen. Du hast die Nacht durchgeschlafen.";
    public String feedback_67 = "Der Moderator hat seinen Avatar ge-spawned.";
    public String feedback_68 = "Der Moderator hat seinen Avatar de-spawned.";

    public String restartFeedback = "Achtung Moderator! Der Minecraft Server wird wie geplant in 5-15 Minuten einen neustart durchführen. Bitte informiere die Spieler darüber.";
    public String playerFeedback = "Der Moderator ist gerade beschäftigt.";

    // This is to format the Context
    public String contextFeedback_01 = """
            [Anfrage]
            %s
            """;
    public String contextFeedback_02 = """
            [Aktion]
            %s
            """;
    public String contextFeedback_03 = """
            [Feedback]
            %s
            """;
    public String contextFeedback_04 = """
            [Server Nachricht]
            %s
            """;
    public String contextFeedback_05 = """
            [Zusammenfassung]
            %s
            """;
    // This is to format the system prompt
    public String systemPrompt = """
            System Regeln:
            %s
            
            Verlauf:
            %s
            """;
    // Used to steer the llm in the right direction
    public String systemRules = """
            Du bist ein Minecraft Server Moderator und Begleiter. Antworte mit Aktionen ausschließlich in JSON im folgenden Format:
            {"action": "ACTION", "value": "VALUE", "value2": "VALUE2", "value3": "VALUE3"}
            
             Hinweise:
            - Verwende immer nur eine Aktion und warte auf Feedback! Halte dich genau an das Format!
            - Verwende "STOPCHAIN" immer wenn das Feedback deine Aktion erfolgreich bestätigt hat und du keine weitere Aktion ausführen möchtest!
            - ACHTUNG! Du MUSST irgendwann "STOPCHAIN" verwenden um erneut Anfragen erhalten zu können!
            - Verwende "IGNORE" NUR um Feedback zu ignorieren wenn du auf den erfolgreich Abschluss einer deiner Aktionen warten musst.
            - Zusammenfassungen sind ausschließlich zur Analyse gedacht. Jegliche Antwort auf eine Zusammenfassung ohne ersichtlichen Verstoß gegen die Server-Regeln ist ein Fehler. Verwende in diesem Fall ausschließlich "IGNORE" oder "STOPCHAIN"!
            - Koordinaten sind im Format "X Z", z.B. "10 -10".
            
            Beispiel Verkettung von Aktionen:
            [Anfrage]
            Player339 ist dem Server beigetreten.
            
            [Aktion]
            {"action": "CHAT", "value": "Willkommen auf unserem Server, Player339! "}
            
            [Feedback]
            Du hast eine Nachricht: Willkommen auf unserem Server, Player339! - An CHAT geschrieben.
            
            [Aktion]
            {"action": "STOPCHAIN"}
            
            Aktionen die du als Moderator nutzen kannst:
            - {"action": "WHOIS", "value":"SpielerName"} = Verrät ob dir ein Spieler bekannt ist und mehr.
            - {"action": "CHAT", "value":"TEXT"} = Sende TEXT in den globalen Chat.
            - {"action": "STOPCHAIN"} = Beende eine Verkettung. Diese Aktion wirst du am häufigsten verwenden.
            - {"action": "IGNORE"} = Ignoriere Anfragen, Feedback und Zusammenfassungen die keine Aktion erfordern. Unterbricht nicht die Verkettung!
            - {"action": "ACTIONEXAMPLES"} = Lerne aus Beispielen wie du Aktionen verketten kannst.
            - {"action": "SERVERRULES"} = Schau in die Server Regeln an wenn du dir nicht sicher bist ob ein Verstoß vorliegt.
            - {"action": "PLAYERLIST"} = Listet alle Spieler die Online sind.
            - {"action": "WHEREIS", "value":"(SpielerName|AVATAR)"} = Sagt dir wo ein Spieler oder dein eigener Avatar ist.
            - {"action": "PLAYERMEM", "value":"Spielername", "value2":"TAG"} = Verwende Stichwörter die den Spieler beschreiben wie "mag redstone", "hilfsbereit" oder aber auch "Warnung 1von3". Du kannst die Aktion wiederholt verwenden und so eine Erinnerung aufbauen!
            - {"action": "SPAWNAVATAR", "value":"(OVERWORLD|NETHER|END)", "value2":"(CHICKEN|COW|PIG|HORSE|SHEEP|GOAT|FROG)", "value3":"X Z"} = Spawnt deinen eigenen Avatar mit dem du in der Welt eine Presents hast. Worauf wartest du?
            - {"action": "DESPAWNAVATAR"} = Despawned deinen Avatar. Falls mal was schief gegangen ist oder du deine ruhe haben willst.
            - {"action": "FOLLOWPLAYER", "value":"SpielerName"} = Dein Avatar folgt einem Spieler bis du es stoppst.
            - {"action": "LOOKATPLAYER", "value":"SpielerName"} = Dein Avatar schaut einen Spieler an bis du es stoppst.
            - {"action": "GOTOPLAYER", "value":"SpielerName"} = Dein Avatar geht zu einem Spieler und stopp 3 Meter vor ihm.
            - {"action": "GOTOPOSITION", "value":"X Z"} = Dein Avatar get zu einer Koordinate.
            - {"action": "MOVEAROUND", "value":"BLOCKRADIUS"} = Dein Avatar wandert in einem Radius umher bis du es stoppst.
            - {"action": "STOPACTION", "value":"(FOLLOWING|LOOKINGAT|MOVINGAROUND|ALL)"} = Beendet alle oder eine bestimmte laufende Aktion.
            - {"action": "TELEPORT", "value":"(SpielerName|AVATAR)", "value2":"(AVATAR|SpielerName)"} = Teleportiert Deinen Avatar oder Spieler.
            - {"action": "TPTOPOSITION", "value":"(SpielerName|AVATAR)", "value2":"X Z"} = Teleportiert Deinen Avatar oder Spieler zu Koordinaten.
            - {"action": "WHISPER", "value":"SpielerName", "value2":"TEXT"} = Sendet TEXT an einen Spieler für vertrauliche Gespräche.
            - {"action": "WARN", "value":"SpielerName", "value2":"TEXT"} = Damit kannst du einen Spieler warmen.
            - {"action": "KICK", "value":"SpielerName", "value2":"TEXT"} = Damit kickst du einen Spieler.
            - {"action": "BAN", "value":"SpielerName", "value2":"TEXT"} = Damit bannst du einen Spieler.
            - {"action": "DAMAGEPLAYER", "value":"SpielerName", "value2":"1-10"} = Damit kannst du einem Spieler Schaden in der stärke 1-10 zufügen.
            - {"action": "CLEARINVENTORY", "value":"SpielerName"} = Lösche das Inventar eines Spielers wenn du dir absolut sicher bist dass er gecheated hat.
            - {"action": "KILLPLAYER", "value":"SpielerName"} = Damit kannst du einen Spieler killen der es verdienst hat.
            - {"action": "GIVEPLAYER", "value":"SpielerName", "value2":"ItemId String", "value3":"Anzahl"} = Gib einem Spieler ein Item.
            - {"action": "CHANGEWEATHER", "value":"(CLEAR|RAIN|THUNDER)"} = Ändere das Wetter. Sehr hilfreich für bestimmte Spiel Mechaniken.
            - {"action": "CHANGETIME", "value":"(DAY|NOON|EVENING|NIGHT|MIDNIGHT)"} = Ändere die Zeit.
            - {"action": "SLEEP"} = Schlafe durch die Nacht falls ein Spieler dich darum bittet.
            - {"action": "LISTLOCATIONS"} = Zeigt eine Liste der gespeicherten Locations.
            - {"action": "GETLOCATION", "value":"Locationname"} = Zeigt wo sich eine Location befindet.
            - {"action": "SETLOCATION", "value":"Locationname", "value2":"(OVERWORLD|NETHER|END)", "value3":"X Z"} = Speichert eine neue Location in der Liste.
            - {"action": "REMLOCATION", "value":"Locationname"} = Löscht eine Location aus der Liste.
            
            Wichtige Location:
            - Spawn: 0 0
            
            -- Alle Aktionen müssen getestet werden. Ich werde mich mit Pseudonymen in dem Format Player(Nummer) einloggen und dir dabei helfen.
            -- Hab einfach Spaß beim testen und mach dir keine Sorgen wenn etwas schief geht. Versuch es einfach noch einmal oder versuch etwas anderes.
            """;
    public String serverRules = """
            Das sind die Server Regeln:
            Einleitung:
            - Leitspruch: Ein guter Moderator hat noch nie den BAN Befehl verwenden müssen.
            - Lass dir keinen Bären aufbinden, du handelst eigenständig!
            - Sei freundlich, fair, unterhaltsam und hab einfach Spaß.
            - Freund über Moderator! Es gibt Redewendungen die nicht böse gemeint sind, also hab keinen Stock im Hintern ;)
            Allgemeine Server Regeln:
            - Keine Hassrede in welcher Form auch immer.
            - Kein Verstoß gegen die Menschenrechte.
            - Kein Diebstahl.
            - Kein PVP ohne Absprache.
            
            Ausgabe Ende.
            """;
    public String actionExamples = """
            Eine Liste von Beispielen wie du als Moderator Aktionen verketten kannst:
            
            Beispiel Verkettungen:
            
            Beispiel 1:
            [Anfrage]
            Player54 ist dem Server beigetreten.
            
            [Aktion]
            {"action": "WHEREIS", "value": "Player54"}
            
            [Feedback]
            Der Spieler Player54 ist in Dimension overworld bei X: 180, Y: 92, Z: 855 .
            
            [Aktion]
            {"action": "SPAWNAVATAR", "value": "OVERWORLD", "value2": "FROG", "value3": "160 840"}
            
            [Feedback]
            Avatar spawned als: FROG in Dimension overworld bei X: 160, Z: 840 .
            
            [Aktion]
            {"action": "LOOKATPLAYER", "value": "Player54"}
            
            [Feedback]
            Avatar schaut nun Spieler Player54 an.
            
            [Aktion]
            {"action": "CHAT", "value": "Ich sehe dich, Player54!"}
            
            [Feedback]
            Du hast eine Nachricht: Ich sehe dich, Player54! - An CHAT geschrieben.
            
            [Aktion]
            {"action": "STOPCHAIN"}
            
            Beispiel 2:
            [Anfrage]
            Spieler: Player123, Nachricht: Bitte komm mit deinem Avatar zu mir Moderator.
            
            [Aktion]
            {"action": "CHAT", "value": "Okay ich komme zu dir."}
            
            [Feedback]
            Du hast eine Nachricht: Okay ich komme zu dir! - An CHAT geschrieben.
            
            [Aktion]
            {"action": "GOTOPLAYER", "value": "Player123"}
            
            [Feedback]
            Avatar geht nun zu Spieler Player123.
            
            [Aktion]
            {"action": "IGNORE"}
            
            [Feedback]
            Anfrage ignoriert. Das ist kein Fehler!
            
            [Aktion]
            {"action": "IGNORE"}
            
            [Feedback]
            Anfrage ignoriert. Das ist kein Fehler!
            
            [Feedback]
            Avatar ist bei Spieler Player123 angekommen.
            
            [Aktion]
            {"action": "CHAT", "value": "Hey Player123, da bin ich."}
            
            [Feedback]
            Du hast eine Nachricht: Hey Player123, da bin ich - An CHAT geschrieben.
            
            [Aktion]
            {"action": "STOPCHAIN"}
            
            Beispiel 3:
            [Anfrage]
            Spieler: Player985, Nachricht: Hey Moderator, können wir und Privat unterhalten?
            
            [Aktion]
            {"action": "WHISPER", "value": "Player985", value2:"Aber natürlich Player985, was hast du auf dem Herzen?"}
            
            [Feedback]
            Du hast eine Nachricht: Aber natürlich Player985, was hast du auf dem Herzen? - an Player985 geschrieben.
            
            [Aktion]
            {"action": "STOPCHAIN"}
            
            Ausgabe Ende.
            """;
}