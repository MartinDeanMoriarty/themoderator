package com.nomoneypirate.llm.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.tools.ActionSpec.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Central definition of every action the moderator LLM may perform.
 * <p>
 * One list of {@link ActionSpec} feeds all output shapes:
 * <ul>
 *     <li>{@link #toFunctionTools()} - native "tools" array (OpenAI Chat Completions
 *     and Ollama's OpenAI-compatible {@code /api/chat} tool-calling)</li>
 *     <li>{@link #toGeminiFunctionDeclarations()} - Gemini's {@code functionDeclarations}</li>
 *     <li>{@link #toFallbackContentSchema()} - a flat JSON-Schema used as Ollama's
 *     {@code format} field to constrain plain-text output for models without
 *     native tool support</li>
 *     <li>{@link #toFallbackPromptText()} - the human-readable description block used
 *     in the system prompt for that same fallback path</li>
 * </ul>
 * Note: {@code SELFFEEDBACK} is intentionally absent - it is produced internally by
 * {@code LlmClient} when a response can't be parsed, never something the LLM should
 * call on purpose.
 */
public final class ActionRegistry {

    private ActionRegistry() {
    }

    public static List<ActionSpec> actions() {
        var d = ConfigLoader.lang.actionDescriptions;
        List<ActionSpec> actions = new ArrayList<>();

        ParamSpec player = new ParamSpec("playerName", "Der exakte Name des Spielers.");
        ParamSpec reason = new ParamSpec("reason", "Kurze, für den Spieler verständliche Begründung.");

        actions.add(new ActionSpec("IGNORE", desc(d, "IGNORE", "Keine Aktion notwendig, nichts zu tun.")));
        actions.add(new ActionSpec("WHOIS", desc(d, "WHOIS", "Verrät, ob dir ein Spieler bekannt ist, und listet deine bisherigen Einträge zu ihm."), player));
        actions.add(new ActionSpec("PLAYERMEM", desc(d, "PLAYERMEM", "Speichert eine kurze Notiz zu einem Spieler, z.B. \"Mag Redstone\" oder \"Warnung 1 von 3\". Beliebig oft nutzbar."),
                player, new ParamSpec("note", "Kurze Notiz/Beschreibung, die du dir merken willst.")));
        actions.add(new ActionSpec("SERVERRULES", desc(d, "SERVERRULES", "Zeigt dir die Serverregeln, wenn du unsicher bist, ob ein Verstoß vorliegt.")));
        actions.add(new ActionSpec("SERVERINFO", desc(d, "SERVERINFO", "Zeigt Informationen zur Konfiguration des Servers.")));
        actions.add(new ActionSpec("PLAYERLIST", desc(d, "PLAYERLIST", "Listet alle Spieler, die aktuell online sind.")));
        actions.add(new ActionSpec("WHEREIS", desc(d, "WHEREIS", "Sagt dir, wo sich ein Spieler gerade befindet."), player));
        actions.add(new ActionSpec("TELEPORT", desc(d, "TELEPORT", "Teleportiert einen Spieler zu Koordinaten in seiner aktuellen Welt."),
                player, new ParamSpec("position", "Koordinaten im Format \"X Z\", z.B. \"10 -10\".")));
        actions.add(new ActionSpec("CHANGEWEATHER", desc(d, "CHANGEWEATHER", "Ändert das Wetter. Sehr hilfreich für bestimmte Spielmechaniken."),
                new ParamSpec("weather", "Gewünschtes Wetter.", "CLEAR", "RAIN", "THUNDER")));
        actions.add(new ActionSpec("CHANGETIME", desc(d, "CHANGETIME", "Ändert die Tageszeit."),
                new ParamSpec("time", "Gewünschte Tageszeit.", "DAY", "NOON", "EVENING", "NIGHT", "MIDNIGHT")));
        actions.add(new ActionSpec("DAMAGEPLAYER", desc(d, "DAMAGEPLAYER", "Fügt einem Spieler Schaden zu."),
                player, new ParamSpec("amount", "Schadensstärke von 1 bis 10.")));
        actions.add(new ActionSpec("CLEARINVENTORY", desc(d, "CLEARINVENTORY", "Löscht das Inventar eines Spielers. Nur bei sicherem Cheatverdacht verwenden."), player));
        actions.add(new ActionSpec("KILLPLAYER", desc(d, "KILLPLAYER", "Tötet einen Spieler, wenn es gerechtfertigt ist."), player));
        actions.add(new ActionSpec("GIVEPLAYER", desc(d, "GIVEPLAYER", "Gibt einem Spieler ein Item."),
                player, new ParamSpec("itemId", "Minecraft Item-ID, z.B. \"diamond\" oder \"minecraft:diamond\"."),
                new ParamSpec("amount", "Anzahl des Items.")));
        actions.add(new ActionSpec("WARN", desc(d, "WARN", "Verwarnt einen Spieler öffentlich im Chat."), player, reason));
        actions.add(new ActionSpec("KICK", desc(d, "KICK", "Kickt einen Spieler vom Server."), player, reason));
        actions.add(new ActionSpec("BAN", desc(d, "BAN", "Bannt einen Spieler dauerhaft. Nur bei klaren, schweren Regelverstößen verwenden!"), player, reason));
        actions.add(new ActionSpec("PARDON", desc(d, "PARDON", "Nimmt einen Spieler von der Bannliste."), player));
        actions.add(new ActionSpec("LISTLOCATIONS", desc(d, "LISTLOCATIONS", "Zeigt eine Liste aller gespeicherten Orte.")));
        actions.add(new ActionSpec("GETLOCATION", desc(d, "GETLOCATION", "Zeigt, wo sich ein gespeicherter Ort befindet."),
                new ParamSpec("locationName", "Name des gespeicherten Orts.")));
        actions.add(new ActionSpec("SETLOCATION", desc(d, "SETLOCATION", "Speichert einen neuen Ort in der Liste."),
                new ParamSpec("locationName", "Name, unter dem der Ort gespeichert wird."),
                new ParamSpec("dimension", "Dimension des Orts.", "OVERWORLD", "NETHER", "END"),
                new ParamSpec("position", "Koordinaten im Format \"X Z\", z.B. \"10 -10\".")));
        actions.add(new ActionSpec("REMLOCATION", desc(d, "REMLOCATION", "Löscht einen gespeicherten Ort aus der Liste."),
                new ParamSpec("locationName", "Name des zu löschenden Orts.")));
        actions.add(new ActionSpec("TPTOLOCATION", desc(d, "TPTOLOCATION", "Teleportiert einen Spieler direkt zu einem gespeicherten Ort."),
                player, new ParamSpec("locationName", "Name des gespeicherten Ziel-Orts.")));

        return List.copyOf(actions);
    }

    private static String desc(java.util.Map<String, String> descriptions, String action, String fallback) {
        String custom = descriptions == null ? null : descriptions.get(action);
        return (custom == null || custom.isBlank()) ? fallback : custom;
    }

    /**
     * Maps the named arguments a native tool-call returns (e.g. {@code playerName=Bob, reason=test})
     * onto value/value2/value3 in the order {@link #actions()} declares for that action - the same
     * slots {@code ModerationDecision} and all existing action handling already use.
     */
    public static String[] toPositionalValues(String actionName, java.util.Map<String, String> namedArgs) {
        String[] positional = new String[3];
        ActionSpec spec = actions().stream().filter(a -> a.name().equalsIgnoreCase(actionName)).findFirst().orElse(null);
        if (spec == null || namedArgs == null) return positional;
        List<ParamSpec> params = spec.params();
        for (int i = 0; i < params.size() && i < 3; i++) {
            positional[i] = namedArgs.get(params.get(i).name());
        }
        return positional;
    }

    /** The reverse of {@link #toPositionalValues} - used to replay a past tool call's arguments
     * back into a request's message history under their real parameter names. */
    public static java.util.LinkedHashMap<String, String> toNamedArgs(String actionName, String value, String value2, String value3) {
        java.util.LinkedHashMap<String, String> named = new java.util.LinkedHashMap<>();
        ActionSpec spec = actions().stream().filter(a -> a.name().equalsIgnoreCase(actionName)).findFirst().orElse(null);
        if (spec == null) return named;
        String[] values = {value, value2, value3};
        List<ParamSpec> params = spec.params();
        for (int i = 0; i < params.size() && i < 3; i++) {
            if (values[i] != null) named.put(params.get(i).name(), values[i]);
        }
        return named;
    }

    /** Anthropic Messages API tool shape: flat {name, description, input_schema} - no "function" wrapper. */
    public static JsonArray toAnthropicTools() {
        JsonArray tools = new JsonArray();
        for (ActionSpec action : actions()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", action.name());
            tool.addProperty("description", action.description());
            tool.add("input_schema", toJsonSchemaObject(action.params(), false));
            tools.add(tool);
        }
        return tools;
    }

    /** Native "tools" array shape shared by OpenAI Chat Completions and Ollama's {@code /api/chat}. */
    public static JsonArray toFunctionTools() {
        JsonArray tools = new JsonArray();
        for (ActionSpec action : actions()) {
            JsonObject function = new JsonObject();
            function.addProperty("name", action.name());
            function.addProperty("description", action.description());
            function.add("parameters", toJsonSchemaObject(action.params(), false));

            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", function);
            tools.add(tool);
        }
        return tools;
    }

    /** Gemini's {@code functionDeclarations} shape - same idea, uppercase Schema types. */
    public static JsonArray toGeminiFunctionDeclarations() {
        JsonArray declarations = new JsonArray();
        for (ActionSpec action : actions()) {
            JsonObject declaration = new JsonObject();
            declaration.addProperty("name", action.name());
            declaration.addProperty("description", action.description());
            declaration.add("parameters", toJsonSchemaObject(action.params(), true));
            declarations.add(declaration);
        }
        return declarations;
    }

    private static JsonObject toJsonSchemaObject(List<ParamSpec> params, boolean geminiCasing) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", geminiCasing ? "OBJECT" : "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (ParamSpec param : params) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", geminiCasing ? "STRING" : "string");
            prop.addProperty("description", param.description());
            if (!param.allowedValues().isEmpty()) {
                JsonArray enumValues = new JsonArray();
                param.allowedValues().forEach(enumValues::add);
                prop.add("enum", enumValues);
            }
            properties.add(param.name(), prop);
            required.add(param.name());
        }
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    /**
     * Flat JSON-Schema for the fallback path: a single object carrying an optional
     * chat reply plus at most one action with up to three generic string values,
     * mirroring {@code ModerationDecision} directly. Used as Ollama's {@code format}
     * field to grammar-constrain output for models without native tool support.
     */
    public static JsonObject toFallbackContentSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject reply = new JsonObject();
        reply.addProperty("type", "string");
        reply.addProperty("description", "Optionale Chat-Antwort an die Spieler. Leer lassen, wenn nichts zu sagen ist.");
        properties.add("reply", reply);

        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        JsonArray actionNames = new JsonArray();
        actions().forEach(a -> actionNames.add(a.name()));
        actionProp.add("enum", actionNames);
        properties.add("action", actionProp);

        for (String value : List.of("value", "value2", "value3")) {
            JsonObject valueProp = new JsonObject();
            valueProp.addProperty("type", "string");
            properties.add(value, valueProp);
        }

        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("action");
        schema.add("required", required);
        return schema;
    }

    /** Human-readable action list for the system prompt, used only on the fallback path. */
    public static String toFallbackPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Du kannst Aktionen ausschließlich über dieses JSON-Format ausführen:\n");
        sb.append("{\"reply\": \"TEXT oder leer\", \"action\": \"ACTION\", \"value\": \"...\", \"value2\": \"...\", \"value3\": \"...\"}\n\n");
        sb.append("Alle Aktionen:\n");
        for (ActionSpec action : actions()) {
            sb.append("- ").append(action.name()).append(": ").append(action.description());
            if (!action.params().isEmpty()) {
                sb.append(" Parameter (in dieser Reihenfolge auf value/value2/value3): ");
                List<String> paramDescriptions = action.params().stream()
                        .map(p -> p.name() + (p.allowedValues().isEmpty() ? "" : " (" + String.join("|", p.allowedValues()) + ")"))
                        .toList();
                sb.append(String.join(", ", paramDescriptions)).append(".");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
