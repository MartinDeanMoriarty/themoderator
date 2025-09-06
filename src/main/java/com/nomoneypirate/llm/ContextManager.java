package com.nomoneypirate.llm;

import com.nomoneypirate.config.ConfigLoader;

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
        // Let's add newest which is last!
        context.addLast(message);
        // Trim context with token limit
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
        // Let's first calculate the system prompt tokens
        Map<String, Deque<String>> ruleMap = new HashMap<>();
        String ruleString = ConfigLoader.lang.systemRules;
        Deque<String> ruleDeque = new ArrayDeque<>(Arrays.asList(ruleString.split(",")));
        ruleMap.put("Rules", ruleDeque);
        Deque<String> rules = ruleMap.get("Rules");
        int estTokensSystemRules = rules.stream().mapToInt(r -> r.length() / 4).sum();
        // Calculate prompt tokens
        int estimatedTokens = context.stream().mapToInt(s -> s.length() / 4).sum();
        // Calculate final estimation
        int finalEstimation = estTokensSystemRules + estimatedTokens;
        while (finalEstimation > maxTokens && !context.isEmpty()) {
            // Cut off the oldest which is first!
            String removed = context.removeFirst();
            estimatedTokens -= removed.length() / 4;
        }
    }
}