package com.nomoneypirate.llm;

public record ModerationDecision(Action action, String value, String value2, String value3) {
    public enum Action {
        CHAT,
        WHISPER,
        WARN,
        KICK,
        BAN,
        IGNORE,
        WHEREIS,
        FEEDBACK,
        PLAYERLIST,
        TELEPORTPLAYER,
        TELEPORTPOSITION,
        SPAWNAVATAR,
        DESPAWNAVATAR,
        FOLLOWPLAYER,
        LOOKATPLAYER,
        GOTOPLAYER,
        GOTOPOSITION,
        MOVEAROUND,
        STOP
    }
}
