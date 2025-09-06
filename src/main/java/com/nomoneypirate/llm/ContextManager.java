package com.nomoneypirate.llm;

import java.util.*;

public class ContextManager {
    private final int maxTokens;
    private final Map<String, Deque<String>> llmContexts = new HashMap<>();

    public ContextManager(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void addMessage(String type, String message) {
        llmContexts.putIfAbsent(type, new ArrayDeque<>());
        Deque<String> context = llmContexts.get(type);
        context.addLast(message);
        trimContext(context);
    }

    public String buildPrompt(String type) {
        Deque<String> context = llmContexts.getOrDefault(type, new ArrayDeque<>());
        StringBuilder sb = new StringBuilder().append("\n\n");

        for (String msg : context) {
            sb.append(msg).append("\n");
        }

        return sb.toString().trim();
    }

    public void clearContext(String type) {
        llmContexts.remove(type);
    }

    private void trimContext(Deque<String> context) {
        // Simple token estimation: 1 token ≈ ¾ word ≈ 4 chars
        int estimatedTokens = context.stream().mapToInt(s -> s.length() / 4).sum();
        while (estimatedTokens > maxTokens && !context.isEmpty()) {
            String removed = context.removeFirst();
            estimatedTokens -= removed.length() / 4;
        }
    }
}