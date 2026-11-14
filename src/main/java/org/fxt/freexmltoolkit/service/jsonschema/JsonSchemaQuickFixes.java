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
import java.util.List;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.JsonService.SchemaError;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfEditPlan;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfTextEdit;
import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.fxt.freexmltoolkit.util.JsonPointerLocator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Quick fixes for JSON Schema violations, offered in the same places as Schematron quick fixes
 * (Problems list, gutter light bulb) and applied as formatting-preserving text edits in one
 * undo step:
 * <ul>
 *   <li>{@code required}: add the missing property with a sample value from the schema,</li>
 *   <li>{@code enum} / {@code const}: replace the value with an allowed one,</li>
 *   <li>{@code additionalProperties}: remove the property the schema does not allow.</li>
 * </ul>
 * A fix travels as an {@link SqfFixSuggestion} whose {@code fixKey} starts with
 * {@link #KEY_PREFIX}, {@code svrlLocation} holds the instance pointer and {@code fixId} the
 * fix's argument as JSON.
 */
public final class JsonSchemaQuickFixes {

    /** Prefix of the {@code fixKey} of JSON Schema fixes. */
    public static final String KEY_PREFIX = "json-schema:";
    static final String ADD_REQUIRED = KEY_PREFIX + "add-required";
    static final String REPLACE_VALUE = KEY_PREFIX + "replace-value";
    static final String REMOVE_PROPERTY = KEY_PREFIX + "remove-property";

    /** At most this many "replace with" fixes for one enum. */
    private static final int MAX_VALUE_FIXES = 6;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private JsonSchemaQuickFixes() {
    }

    /** @return whether the fix is a JSON Schema fix (not an SQF one) */
    public static boolean isJsonFix(SqfFixSuggestion fix) {
        return fix != null && fix.fixKey() != null && fix.fixKey().startsWith(KEY_PREFIX);
    }

    /**
     * @param error    a schema violation
     * @param resolver the schema (sample values for added properties), may be {@code null}
     * @return the fixes for it (empty when none applies)
     */
    public static List<SqfFixSuggestion> forError(SchemaError error, JsonSchemaResolver resolver) {
        List<SqfFixSuggestion> fixes = new ArrayList<>();
        String keyword = error.keyword();
        if (keyword == null) {
            return fixes;
        }
        String pointer = error.instancePointer();
        switch (keyword) {
            case "required" -> {
                if (error.property() != null) {
                    List<Object> path = new ArrayList<>(error.instanceSegments());
                    path.add(error.property());
                    JsonElement sample = resolver != null
                            ? JsonSchemaSampleGenerator.generateAt(resolver, path, JsonSchemaSampleGenerator.Options.defaults())
                            : new com.google.gson.JsonPrimitive("");
                    JsonObject argument = new JsonObject();
                    argument.addProperty("key", error.property());
                    argument.add("value", sample);
                    fixes.add(fix(ADD_REQUIRED, GSON.toJson(argument),
                            I18n.t("JsonQuickFix.add-required", error.property()), pointer));
                }
            }
            case "enum", "const" -> {
                List<JsonElement> allowed = new ArrayList<>();
                try {
                    JsonElement value = error.schemaValue() != null ? JsonParser.parseString(error.schemaValue()) : null;
                    if (value != null && "enum".equals(keyword) && value.isJsonArray()) {
                        value.getAsJsonArray().forEach(allowed::add);
                    } else if (value != null && "const".equals(keyword)) {
                        allowed.add(value);
                    }
                } catch (RuntimeException e) {
                    return fixes;
                }
                for (JsonElement value : allowed.subList(0, Math.min(MAX_VALUE_FIXES, allowed.size()))) {
                    String json = GSON.toJson(value);
                    fixes.add(fix(REPLACE_VALUE, json, I18n.t("JsonQuickFix.replace-value", json), pointer));
                }
            }
            case "additionalProperties", "unevaluatedProperties" -> {
                if (error.property() != null) {
                    fixes.add(fix(REMOVE_PROPERTY, GSON.toJson(error.property()),
                            I18n.t("JsonQuickFix.remove-property", error.property()), pointer));
                }
            }
            default -> {
                // no automatic fix
            }
        }
        return fixes;
    }

    private static SqfFixSuggestion fix(String key, String argument, String title, String pointer) {
        return new SqfFixSuggestion(key, argument, title, title, null, pointer, false);
    }

    /**
     * Computes the text edits of a fix.
     *
     * @param text the document text
     * @param fix  the fix
     * @return the edits (sorted descending by start)
     * @throws IllegalStateException when the fix no longer applies to the text
     */
    public static SqfEditPlan computeEdit(String text, SqfFixSuggestion fix) {
        JsonPointerLocator locator = new JsonPointerLocator(text);
        JsonPointerLocator.Location location = locator.locationOf(fix.svrlLocation());
        if (location == null) {
            throw new IllegalStateException("The value the fix refers to is no longer in the document");
        }
        List<SqfTextEdit> edits = switch (fix.fixKey()) {
            case ADD_REQUIRED -> addProperty(text, location, JsonParser.parseString(fix.fixId()).getAsJsonObject());
            case REPLACE_VALUE -> List.of(new SqfTextEdit(location.valueStart(), location.valueEnd(), fix.fixId()));
            case REMOVE_PROPERTY -> removeProperty(text, locator, fix.svrlLocation(),
                    JsonParser.parseString(fix.fixId()).getAsString());
            default -> throw new IllegalStateException("Unknown fix " + fix.fixKey());
        };
        List<SqfTextEdit> sorted = new ArrayList<>(edits);
        sorted.sort((a, b) -> Integer.compare(b.start(), a.start()));
        return new SqfEditPlan(sorted, fix.title());
    }

    /** Adds {@code "key": value} as the last member of the object at the location. */
    private static List<SqfTextEdit> addProperty(String text, JsonPointerLocator.Location object, JsonObject argument) {
        if (text.charAt(object.valueStart()) != '{') {
            throw new IllegalStateException("The value is no object");
        }
        String member = GSON.toJson(argument.get("key").getAsString()) + ": " + GSON.toJson(argument.get("value"));
        int open = object.valueStart();
        int close = object.valueEnd() - 1;
        List<Token> tokens = JsonTokenizer.tokenize(text.substring(0, object.valueEnd()));
        Token firstMember = null;
        Token last = null;
        Token commentAfterLast = null;
        for (Token t : tokens) {
            if (t.start() <= open || t.start() >= close) {
                continue;
            }
            if (t.kind() == Kind.WHITESPACE) {
                continue;
            }
            if (t.kind().isComment()) {
                // a comment on the last member's line stays behind it
                if (last != null && t.kind() == Kind.LINE_COMMENT && text.substring(last.end(), t.start()).indexOf('\n') < 0) {
                    commentAfterLast = t;
                }
                continue;
            }
            if (firstMember == null) {
                firstMember = t;
            }
            last = t;
            commentAfterLast = null;
        }
        boolean multiLine = text.substring(open, close).indexOf('\n') >= 0;
        String objectIndent = indentOf(text, open);
        String memberIndent = firstMember != null ? indentOf(text, firstMember.start()) : objectIndent + "  ";
        String lineBreak = multiLine ? "\n" + memberIndent : " ";
        if (last == null) {
            // empty object
            return List.of(new SqfTextEdit(open + 1, open + 1, multiLine ? "\n" + memberIndent + member : member));
        }
        if (last.kind() == Kind.COMMA) {
            int at = commentAfterLast != null ? commentAfterLast.end() : last.end();
            return List.of(new SqfTextEdit(at, at, lineBreak + member + ","));
        }
        if (commentAfterLast != null) {
            return List.of(new SqfTextEdit(commentAfterLast.end(), commentAfterLast.end(), lineBreak + member),
                    new SqfTextEdit(last.end(), last.end(), ","));
        }
        return List.of(new SqfTextEdit(last.end(), last.end(), "," + lineBreak + member));
    }

    /** Removes a member and its separating comma (and its line, when it stands alone on it). */
    private static List<SqfTextEdit> removeProperty(String text, JsonPointerLocator locator, String objectPointer,
                                                    String key) {
        List<Object> segments = JsonPointerLocator.segments(objectPointer);
        if (segments == null) {
            throw new IllegalStateException("Invalid location");
        }
        segments = new ArrayList<>(segments);
        segments.add(key);
        JsonPointerLocator.Location member = locator.locationOf(segments);
        if (member == null || member.keyStart() < 0) {
            throw new IllegalStateException("The property is no longer in the document");
        }
        List<Token> tokens = JsonTokenizer.tokenize(text);
        Token next = null;
        Token previous = null;
        for (Token t : tokens) {
            if (t.kind().isTrivia()) {
                continue;
            }
            if (t.end() <= member.keyStart()) {
                previous = t;
            } else if (t.start() >= member.valueEnd() && next == null) {
                next = t;
            }
        }
        int start = member.keyStart();
        int end = member.valueEnd();
        if (next != null && next.kind() == Kind.COMMA) {
            end = next.end();
        } else if (previous != null && previous.kind() == Kind.COMMA) {
            start = previous.start();
        }
        // a member alone on its line takes the line with it
        int lineStart = text.lastIndexOf('\n', start - 1) + 1;
        int lineEnd = text.indexOf('\n', end);
        if (text.substring(lineStart, start).isBlank() && lineEnd >= 0 && text.substring(end, lineEnd).isBlank()
                && start == member.keyStart()) {
            start = lineStart;
            end = lineEnd + 1;
        }
        return List.of(new SqfTextEdit(start, end, ""));
    }

    private static String indentOf(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(lineStart, i);
    }
}
