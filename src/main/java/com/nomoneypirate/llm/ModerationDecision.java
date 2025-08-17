package com.nomoneypirate.llm;

public record ModerationDecision(Action action, String playerName, String text) {
    public enum Action { CHAT, WHISPER, WARN, KICK, IGNORE }
}
