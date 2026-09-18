package com.nomoneypirate.llm.tools;

import java.util.List;

/**
 * Describes one moderator action the LLM may invoke: its name, a human-readable
 * description and its parameters, in the exact order they map onto
 * {@link com.nomoneypirate.llm.ModerationDecision}'s value/value2/value3 slots.
 * <p>
 * This is the single source of truth the action list is generated from - both
 * for native tool/function-calling (OpenAI, Gemini, tool-capable Ollama models)
 * and for the plain-JSON fallback prompt used with models that don't support
 * native tools. Previously these two had to be kept in sync by hand.
 */
public record ActionSpec(String name, String description, List<ParamSpec> params) {

    public ActionSpec(String name, String description) {
        this(name, description, List.of());
    }

    public ActionSpec(String name, String description, ParamSpec... params) {
        this(name, description, List.of(params));
    }

    /** One parameter of an action. Every value in this mod is a plain string. */
    public record ParamSpec(String name, String description, List<String> allowedValues) {

        public ParamSpec(String name, String description) {
            this(name, description, List.of());
        }

        public ParamSpec(String name, String description, String... allowedValues) {
            this(name, description, List.of(allowedValues));
        }
    }
}
