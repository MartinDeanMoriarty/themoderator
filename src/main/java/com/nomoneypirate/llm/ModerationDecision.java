package com.nomoneypirate.llm;

public record ModerationDecision(Action action, String value, String value2, String value3) {
    public enum Action {
        WARN,
        KICK,
        BAN,
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
        REMLOCATION
    }
}
