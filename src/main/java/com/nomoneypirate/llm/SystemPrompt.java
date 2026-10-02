package com.nomoneypirate.llm;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.config.LangConfig;
import com.nomoneypirate.llm.tools.ActionRegistry;

/**
 * Builds the system prompt in one place so every provider (and the token budget) sees exactly
 * the same text: persona + Minecraft help + security rules, optionally followed by the generated
 * action list for models without native tool support.
 */
public final class SystemPrompt {

    private SystemPrompt() {
    }

    /** Persona, Minecraft help and security rules. Used as-is when actions are offered as native tools. */
    public static String base() {
        LangConfig lang = ConfigLoader.lang;
        StringBuilder sb = new StringBuilder();
        append(sb, lang.systemRules);
        append(sb, lang.systemMinecraftHelp);
        append(sb, lang.systemSecurityRules);
        return sb.toString().trim();
    }

    /** {@link #base()} plus the generated action list and few-shot examples (JSON-fallback mode). */
    public static String withFallbackActions() {
        StringBuilder sb = new StringBuilder(base());
        append(sb, ActionRegistry.toFallbackPromptText());
        append(sb, ConfigLoader.lang.actionFewShotExamples);
        return sb.toString().trim();
    }

    /**
     * Rough token cost of everything that is sent besides the conversation itself (system prompt
     * and the action definitions) - whichever transport is bigger, so the history budget never
     * assumes more room than a request really has.
     */
    public static int estimateOverheadTokens() {
        int nativeTools = base().length() + ActionRegistry.toFunctionTools().toString().length();
        int fallback = withFallbackActions().length();
        return Math.max(nativeTools, fallback) / 4;
    }

    private static void append(StringBuilder sb, String block) {
        if (block == null || block.isBlank()) return;
        if (!sb.isEmpty()) sb.append("\n\n");
        sb.append(block.trim());
    }
}
