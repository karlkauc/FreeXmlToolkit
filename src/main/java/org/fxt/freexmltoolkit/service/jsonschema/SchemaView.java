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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;

/**
 * What a JSON Schema says about one place in an instance, merged over {@code $ref},
 * {@code allOf} and (as alternatives) {@code anyOf} / {@code oneOf} / {@code if-then-else}.
 * A best-effort summary for editing help, not a validator.
 *
 * @param types                the allowed JSON types ({@code object}, {@code array}, {@code string},
 *                             {@code number}, {@code integer}, {@code boolean}, {@code null}); empty = any
 * @param title                the title, or {@code null}
 * @param description          the description, or {@code null}
 * @param defaultValue         the default, or {@code null}
 * @param examples             example values
 * @param enumValues           the allowed values ({@code enum}), empty when not restricted
 * @param constValue           the only allowed value ({@code const}), or {@code null}
 * @param format               the {@code format}, or {@code null}
 * @param deprecated           whether the place is marked deprecated
 * @param properties           the declared properties of an object, in schema order
 * @param patternProperties    the {@code patternProperties} regular expressions
 * @param closed               whether no other properties are allowed ({@code additionalProperties: false})
 * @param required             the required property names
 * @param constraints          further restrictions as keyword → value text (minimum, maxLength, pattern, …)
 * @param known                whether the schema says anything about the place at all
 */
public record SchemaView(Set<String> types, String title, String description, JsonElement defaultValue,
                         List<JsonElement> examples, List<JsonElement> enumValues, JsonElement constValue,
                         String format, boolean deprecated, Map<String, PropertyInfo> properties,
                         List<String> patternProperties, boolean closed, Set<String> required,
                         Map<String, String> constraints, boolean known) {

    /** Nothing known (no schema, or the place is outside what the schema describes). */
    public static final SchemaView UNKNOWN = new SchemaView(Set.of(), null, null, null, List.of(), List.of(), null,
            null, false, Map.of(), List.of(), false, Set.of(), Map.of(), false);

    /**
     * A declared property of an object.
     *
     * @param name     the property name
     * @param required whether the object requires it
     * @param schema   what the schema says about its value (without its own properties)
     */
    public record PropertyInfo(String name, boolean required, SchemaView schema) {
    }

    /** @return the allowed values to offer: the {@code const}, else the {@code enum} values */
    public List<JsonElement> allowedValues() {
        return constValue != null ? List.of(constValue) : enumValues;
    }

    /** @return the types as text ({@code "string | null"}), or {@code null} when not restricted */
    public String typeText() {
        return types.isEmpty() ? null : String.join(" | ", types);
    }
}
