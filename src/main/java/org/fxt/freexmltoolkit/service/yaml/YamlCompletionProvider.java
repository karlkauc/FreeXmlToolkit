package org.fxt.freexmltoolkit.service.yaml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.ProposalKind;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;
import org.fxt.freexmltoolkit.service.jsonschema.SchemaView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

/**
 * Completion proposals for YAML text from its JSON Schema, like
 * {@link org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider} for JSON: the keys a
 * mapping may still get (required ones first) and the allowed values, written as YAML (block
 * style for new mappings and sequences, quotes only where a plain scalar would read back as
 * something else). UI-free.
 */
public final class YamlCompletionProvider {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    /** Plain scalars that read back as a string in the YAML 1.2 core schema and need no quotes. */
    private static final Pattern SAFE_PLAIN = Pattern.compile("[A-Za-z_/.][A-Za-z0-9_./@+-]*( [A-Za-z0-9_./@+-]+)*");
    private static final Pattern NOT_A_STRING = Pattern.compile(
            "~|null|Null|NULL|true|True|TRUE|false|False|FALSE|[-+]?[0-9]+|0o[0-7]+|0x[0-9a-fA-F]+"
                    + "|[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?|[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)");

    private YamlCompletionProvider() {
    }

    /**
     * @param text     the document text
     * @param offset   the caret offset
     * @param resolver the bound schema
     * @return the proposals matching what is typed, in display order (empty when none)
     */
    public static List<Proposal> complete(String text, int offset, JsonSchemaResolver resolver) {
        if (resolver == null) {
            return List.of();
        }
        YamlCaretContext.Result context = YamlCaretContext.analyze(text, offset);
        if (context.kind() == YamlCaretContext.Kind.NONE) {
            return List.of();
        }
        JsonElement instance = YamlDocument.parse(text).json();
        SchemaView view = resolver.at(context.path(), instance);
        return switch (context.kind()) {
            case KEY -> keys(context, view);
            case VALUE -> values(context, view, needsSpace(text, context.replaceStart()));
            case ITEM -> {
                // a mapping item starts with a key, any other item is a value
                if (!view.properties().isEmpty()) {
                    yield keys(context, view);
                }
                yield values(context, view, false);
            }
            case NONE -> List.of();
        };
    }

    /** Right after {@code key:} (completion opened by typing the colon) the value needs a space first. */
    private static boolean needsSpace(String text, int replaceStart) {
        return replaceStart > 0 && text.charAt(replaceStart - 1) == ':';
    }

    private static List<Proposal> keys(YamlCaretContext.Result context, SchemaView view) {
        List<Proposal> required = new ArrayList<>();
        List<Proposal> optional = new ArrayList<>();
        String typed = context.partial().toLowerCase(Locale.ROOT);
        for (SchemaView.PropertyInfo property : view.properties().values()) {
            String name = property.name();
            if (context.existingKeys().contains(name) || !name.toLowerCase(Locale.ROOT).contains(typed)) {
                continue;
            }
            String key = scalar(new JsonPrimitive(name));
            String insert = context.colonFollows() ? key : key + ":" + afterKey(property.schema(), context.column());
            Proposal proposal = new Proposal(name, insert, insert.length(), context.replaceStart(),
                    context.replaceEnd(), ProposalKind.PROPERTY, property.schema().description(),
                    property.schema().typeText(), property.required(), property.schema().deprecated());
            (property.required() ? required : optional).add(proposal);
        }
        List<Proposal> out = new ArrayList<>(required);
        out.addAll(optional);
        out.sort((a, b) -> Boolean.compare(!a.label().toLowerCase(Locale.ROOT).startsWith(typed),
                !b.label().toLowerCase(Locale.ROOT).startsWith(typed)));
        return out;
    }

    /** What follows {@code key:} for a new key: the start of its value by the value's type. */
    static String afterKey(SchemaView schema, int column) {
        if (schema.constValue() != null) {
            return " " + scalar(schema.constValue());
        }
        if (schema.enumValues().size() == 1) {
            return " " + scalar(schema.enumValues().get(0));
        }
        Set<String> types = schema.types();
        if (types.size() == 1) {
            return switch (types.iterator().next()) {
                case "object" -> "\n" + " ".repeat(column + 2);
                case "array" -> "\n" + " ".repeat(column) + "- ";
                default -> " ";
            };
        }
        return " ";
    }

    private static List<Proposal> values(YamlCaretContext.Result context, SchemaView view, boolean space) {
        Set<JsonElement> values = new LinkedHashSet<>(view.allowedValues());
        if (view.constValue() == null) {
            if (view.defaultValue() != null) {
                values.add(view.defaultValue());
            }
            values.addAll(view.examples());
            if (view.types().contains("boolean")) {
                values.add(new JsonPrimitive(true));
                values.add(new JsonPrimitive(false));
            }
            if (view.types().contains("null")) {
                values.add(JsonNull.INSTANCE);
            }
        }
        String typed = context.partial().toLowerCase(Locale.ROOT);
        List<Proposal> out = new ArrayList<>();
        for (JsonElement value : values) {
            String yaml = scalar(value);
            String label = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : yaml;
            if (!label.toLowerCase(Locale.ROOT).contains(typed)) {
                continue;
            }
            String description = value.equals(view.defaultValue()) ? "default" : null;
            String insert = space ? " " + yaml : yaml;
            out.add(new Proposal(label, insert, insert.length(), context.replaceStart(), context.replaceEnd(),
                    ProposalKind.VALUE, description, view.typeText(), false, false));
        }
        return out;
    }

    /**
     * A value written as one YAML token: strings plain when they read back as the same string,
     * else double-quoted (JSON string syntax is valid YAML); numbers, booleans and null as they
     * are; objects and arrays in flow style.
     *
     * @param value the value
     * @return its YAML text
     */
    public static String scalar(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (SAFE_PLAIN.matcher(text).matches() && !NOT_A_STRING.matcher(text).matches()) {
                return text;
            }
        }
        return GSON.toJson(value);
    }
}
