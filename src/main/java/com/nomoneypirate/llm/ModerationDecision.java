package com.nomoneypirate.llm;

/**
 * What the moderator decided to do. {@code replied} says whether the model also wrote chat text
 * (already broadcast by the time this exists) - callers like the restart announcement use it to
 * know if they need a fallback message.
 */
public record ModerationDecision(Action action, String value, String value2, String value3, boolean replied) {

    public ModerationDecision(Action action, String value, String value2, String value3) {
        this(action, value, value2, value3, false);
    }

    public enum Action {
        WARN,
        KICK,
        BAN,
        PARDON,
        IGNORE,
        WHEREIS,
        WHOIS,
        SELFFEEDBACK,
        PLAYERMEM,
        SERVERRULES,
        SERVERINFO,
        PLAYERLIST,
        TELEPORT,
        DAMAGEPLAYER,
        CLEARINVENTORY,
        KILLPLAYER,
        GIVEPLAYER,
        CHANGEWEATHER,
        CHANGETIME,
        LISTLOCATIONS,
        GETLOCATION,
        SETLOCATION,
        REMLOCATION,
        TPTOLOCATION
    }
}
