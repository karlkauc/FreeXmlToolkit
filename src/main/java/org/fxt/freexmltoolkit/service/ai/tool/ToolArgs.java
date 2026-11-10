package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Typed access to the arguments of a tool call. Every problem is a {@link ToolException} that names the
 * argument, so a model can correct its call. An unknown argument is refused rather than ignored: a typo
 * would otherwise silently change what the tool does. A JSON {@code null} counts as absent.
 */
public final class ToolArgs {

    private final JsonObject arguments;

    /**
     * @param arguments the call's arguments ({@code null} = none)
     * @param allowed   every argument name the tool accepts
     * @throws ToolException when an argument is not in {@code allowed}
     */
    public ToolArgs(JsonObject arguments, Set<String> allowed) throws ToolException {
        this.arguments = arguments == null ? new JsonObject() : arguments;
        Set<String> unknown = new TreeSet<>(this.arguments.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new ToolException("unknown argument" + (unknown.size() > 1 ? "s " : " ")
                    + String.join(", ", unknown) + "; allowed: " + String.join(", ", new TreeSet<>(allowed)));
        }
    }

    /** Whether the argument is present and not {@code null}. */
    public boolean has(String name) {
        JsonElement value = arguments.get(name);
        return value != null && !value.isJsonNull();
    }

    public String requiredString(String name) throws ToolException {
        return optionalString(name).orElseThrow(() -> new ToolException("argument '" + name + "' is required"));
    }

    public Optional<String> optionalString(String name) throws ToolException {
        if (!has(name)) {
            return Optional.empty();
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ToolException("argument '" + name + "' must be a string");
        }
        return Optional.of(value.getAsString());
    }

    public int optionalInt(String name, int defaultValue) throws ToolException {
        if (!has(name)) {
            return defaultValue;
        }
        JsonElement value = arguments.get(name);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            if (number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE) {
                return (int) number;
            }
        }
        throw new ToolException("argument '" + name + "' must be an integer");
    }

    /** As {@link #optionalInt}, also checking {@code min <= value <= max}. */
    public int optionalInt(String name, int defaultValue, int min, int max) throws ToolException {
        int value = optionalInt(name, defaultValue);
        if (value < min || value > max) {
            throw new ToolException("argument '" + name + "' must be between " + min + " and " + max);
        }
        return value;
    }

    public Optional<Double> optionalNumber(String name) throws ToolException {
        if (!has(name)) {
            return Optional.empty();
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ToolException("argument '" + name + "' must be a number");
        }
        return Optional.of(value.getAsDouble());
    }

    public boolean optionalBoolean(String name, boolean defaultValue) throws ToolException {
        if (!has(name)) {
            return defaultValue;
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new ToolException("argument '" + name + "' must be true or false");
        }
        return value.getAsBoolean();
    }

    /** An object whose values are strings, in the given order; empty when absent. */
    public Map<String, String> stringMap(String name) throws ToolException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!has(name)) {
            return map;
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonObject()) {
            throw new ToolException("argument '" + name + "' must be an object of strings");
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (!isString(entry.getValue())) {
                throw new ToolException("argument '" + name + "." + entry.getKey() + "' must be a string");
            }
            map.put(entry.getKey(), entry.getValue().getAsString());
        }
        return map;
    }

    /** An array of strings; empty when absent. */
    public List<String> stringList(String name) throws ToolException {
        List<String> list = new ArrayList<>();
        if (!has(name)) {
            return list;
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonArray()) {
            throw new ToolException("argument '" + name + "' must be an array of strings");
        }
        for (JsonElement item : value.getAsJsonArray()) {
            if (!isString(item)) {
                throw new ToolException("argument '" + name + "' must be an array of strings");
            }
            list.add(item.getAsString());
        }
        return list;
    }

    private static boolean isString(JsonElement element) {
        return element != null && element.isJsonPrimitive() && ((JsonPrimitive) element).isString();
    }
}
