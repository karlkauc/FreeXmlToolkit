/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.jsonschema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.service.JsonService;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCaretContext.Kind2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Completion proposals for JSON text from its JSON Schema: the properties an object may
 * still get (required ones first) at a key position, the allowed values ({@code const},
 * {@code enum}, {@code default}, {@code examples}, booleans, {@code null}) at a value
 * position. UI-free; the editor shows them.
 */
public final class JsonCompletionProvider {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    /** The kind of a proposal. */
    public enum ProposalKind { PROPERTY, VALUE }

    /**
     * A proposal.
     *
     * @param label        the text shown in the list
     * @param insertText   the text inserted in place of {@link #replaceStart}..{@link #replaceEnd}
     * @param caretOffset  where the caret goes, relative to the start of the inserted text
     * @param replaceStart start of the replaced range
     * @param replaceEnd   end of the replaced range
     * @param kind         property or value
     * @param description  the schema description, or {@code null}
     * @param typeText     the allowed types ({@code string | null}), or {@code null}
     * @param required     whether the property is required
     * @param deprecated   whether it is marked deprecated
     */
    public record Proposal(String label, String insertText, int caretOffset, int replaceStart, int replaceEnd,
                           ProposalKind kind, String description, String typeText, boolean required,
                           boolean deprecated) {
    }

    private JsonCompletionProvider() {
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
        JsonCaretContext.Result context = JsonCaretContext.analyze(text, offset);
        if (context.kind() == Kind2.NONE) {
            return List.of();
        }
        JsonElement instance = parseQuietly(text);
        return context.kind() == Kind2.KEY
                ? keys(context, resolver.at(context.path(), instance), text)
                : values(context, resolver.at(context.path(), instance));
    }

    private static JsonElement parseQuietly(String text) {
        try {
            return new JsonService().parseAuto(text);
        } catch (RuntimeException e) {
            return null; // incomplete while typing: the schema is walked without an instance
        }
    }

    private static List<Proposal> keys(JsonCaretContext.Result context, SchemaView view, String text) {
        List<Proposal> required = new ArrayList<>();
        List<Proposal> optional = new ArrayList<>();
        String typed = context.partial().toLowerCase(Locale.ROOT);
        // is the key followed by a colon already (completing an existing key's name)?
        boolean colonFollows = followsColon(text, context.replaceEnd());
        for (SchemaView.PropertyInfo property : view.properties().values()) {
            String name = property.name();
            if (context.existingKeys().contains(name) || !name.toLowerCase(Locale.ROOT).contains(typed)) {
                continue;
            }
            String quotedName = GSON.toJson(name);
            String insert;
            int caret;
            if (colonFollows) {
                insert = quotedName;
                caret = insert.length();
            } else {
                String skeleton = skeleton(property.schema());
                insert = quotedName + ": " + skeleton;
                caret = quotedName.length() + 2 + caretInSkeleton(skeleton);
            }
            Proposal proposal = new Proposal(name, insert, caret, context.replaceStart(), context.replaceEnd(),
                    ProposalKind.PROPERTY, property.schema().description(), property.schema().typeText(),
                    property.required(), property.schema().deprecated());
            (property.required() ? required : optional).add(proposal);
        }
        List<Proposal> out = new ArrayList<>(required);
        out.addAll(optional);
        // names starting with the typed text first
        out.sort((a, b) -> Boolean.compare(!a.label().toLowerCase(Locale.ROOT).startsWith(typed),
                !b.label().toLowerCase(Locale.ROOT).startsWith(typed)));
        return out;
    }

    private static List<Proposal> values(JsonCaretContext.Result context, SchemaView view) {
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
                values.add(com.google.gson.JsonNull.INSTANCE);
            }
        }
        String typed = context.partial().toLowerCase(Locale.ROOT);
        List<Proposal> out = new ArrayList<>();
        for (JsonElement value : values) {
            String json = GSON.toJson(value);
            String plain = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : json;
            if (!plain.toLowerCase(Locale.ROOT).contains(typed)) {
                continue;
            }
            String description = value.equals(view.defaultValue()) ? "default" : null;
            out.add(new Proposal(plain, json, json.length(), context.replaceStart(), context.replaceEnd(),
                    ProposalKind.VALUE, description, view.typeText(), false, false));
        }
        return out;
    }

    /** @return what follows a new key: the value's skeleton by its type */
    static String skeleton(SchemaView schema) {
        if (schema.constValue() != null) {
            return GSON.toJson(schema.constValue());
        }
        if (schema.enumValues().size() == 1) {
            return GSON.toJson(schema.enumValues().get(0));
        }
        Set<String> types = schema.types();
        if (types.size() == 1) {
            return switch (types.iterator().next()) {
                case "object" -> "{}";
                case "array" -> "[]";
                case "string" -> "\"\"";
                default -> "";
            };
        }
        return "";
    }

    /** @return the caret position inside a skeleton: between the brackets or quotes, else after it */
    private static int caretInSkeleton(String skeleton) {
        return switch (skeleton) {
            case "{}", "[]", "\"\"" -> 1;
            default -> skeleton.length();
        };
    }

    private static boolean followsColon(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }
}
