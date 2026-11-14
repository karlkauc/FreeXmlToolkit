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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Builds a sample instance of a JSON Schema: {@code const}, {@code default}, {@code examples}
 * or the first {@code enum} value where the schema names one, otherwise a value of the
 * declared type that respects {@code format}, {@code minimum} / {@code maximum},
 * {@code minLength} and {@code minItems}. Recursive schemas stop at {@link Options#maxDepth()}.
 * Of {@code anyOf} / {@code oneOf} alternatives only the members every branch shares are
 * filled in "required only" mode; with all properties the result may need a manual choice.
 */
public final class JsonSchemaSampleGenerator {

    /**
     * Options.
     *
     * @param requiredOnly only the required properties (else every declared one)
     * @param maxDepth     nesting limit for recursive schemas
     * @param arrayItems   items per array (at least {@code minItems})
     */
    public record Options(boolean requiredOnly, int maxDepth, int arrayItems) {

        /** Required properties only, depth 8, one item per array. */
        public static Options defaults() {
            return new Options(true, 8, 1);
        }
    }

    private final JsonSchemaResolver resolver;
    private final Options options;

    private JsonSchemaSampleGenerator(JsonSchemaResolver resolver, Options options) {
        this.resolver = resolver;
        this.options = options;
    }

    /**
     * @param resolver the schema
     * @param options  the options
     * @return the sample instance
     */
    public static JsonElement generate(JsonSchemaResolver resolver, Options options) {
        return new JsonSchemaSampleGenerator(resolver, options != null ? options : Options.defaults())
                .value(new ArrayList<>(), 0);
    }

    /**
     * A sample of the value at a place in an instance (e.g. a missing required property).
     *
     * @param resolver the schema
     * @param path     the instance path of the place
     * @param options  the options
     * @return the sample value
     */
    public static JsonElement generateAt(JsonSchemaResolver resolver, List<Object> path, Options options) {
        return new JsonSchemaSampleGenerator(resolver, options != null ? options : Options.defaults())
                .value(new ArrayList<>(path), 0);
    }

    private JsonElement value(List<Object> path, int depth) {
        SchemaView view = resolver.at(path, null);
        if (view.constValue() != null) {
            return view.constValue().deepCopy();
        }
        if (view.defaultValue() != null) {
            return view.defaultValue().deepCopy();
        }
        if (!view.enumValues().isEmpty()) {
            return view.enumValues().get(0).deepCopy();
        }
        if (!view.examples().isEmpty()) {
            return view.examples().get(0).deepCopy();
        }
        String type = type(view);
        return switch (type) {
            case "object" -> object(view, path, depth);
            case "array" -> array(view, path, depth);
            case "integer" -> new JsonPrimitive(number(view, true));
            case "number" -> new JsonPrimitive(number(view, false));
            case "boolean" -> new JsonPrimitive(false);
            case "null" -> JsonNull.INSTANCE;
            default -> new JsonPrimitive(string(view));
        };
    }

    private static String type(SchemaView view) {
        for (String preferred : new String[] {"object", "array", "string", "integer", "number", "boolean", "null"}) {
            if (view.types().contains(preferred)) {
                return preferred;
            }
        }
        return !view.properties().isEmpty() || view.closed() ? "object" : "string";
    }

    private JsonElement object(SchemaView view, List<Object> path, int depth) {
        JsonObject object = new JsonObject();
        if (depth >= options.maxDepth()) {
            return object;
        }
        for (SchemaView.PropertyInfo property : view.properties().values()) {
            if (options.requiredOnly() && !property.required()) {
                continue;
            }
            path.add(property.name());
            object.add(property.name(), value(path, depth + 1));
            path.removeLast();
        }
        // required members the schema names but does not describe
        for (String required : view.required()) {
            if (!object.has(required)) {
                object.add(required, new JsonPrimitive(""));
            }
        }
        return object;
    }

    private JsonElement array(SchemaView view, List<Object> path, int depth) {
        JsonArray array = new JsonArray();
        int minItems = intConstraint(view, "minItems", 0);
        int count = Math.max(minItems, options.requiredOnly() ? minItems : options.arrayItems());
        if (depth >= options.maxDepth()) {
            return array;
        }
        for (int i = 0; i < count; i++) {
            path.add(i);
            JsonElement item = value(path, depth + 1);
            path.removeLast();
            // uniqueItems: numbers / strings are made distinct by their position
            if (Boolean.parseBoolean(view.constraints().getOrDefault("uniqueItems", "false")) && array.contains(item)) {
                item = item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()
                        ? new JsonPrimitive(item.getAsBigDecimal().add(BigDecimal.valueOf(i)))
                        : item.isJsonPrimitive() ? new JsonPrimitive(item.getAsString() + (i + 1)) : item;
            }
            array.add(item);
        }
        return array;
    }

    private static Number number(SchemaView view, boolean integer) {
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal minimum = decimal(view, "minimum");
        BigDecimal exclusiveMinimum = decimal(view, "exclusiveMinimum");
        BigDecimal maximum = decimal(view, "maximum");
        if (minimum != null) {
            value = minimum;
        } else if (exclusiveMinimum != null) {
            value = exclusiveMinimum.add(integer ? BigDecimal.ONE : new BigDecimal("0.5"));
        } else if (maximum != null && maximum.compareTo(BigDecimal.ZERO) < 0) {
            value = maximum;
        }
        BigDecimal multipleOf = decimal(view, "multipleOf");
        if (multipleOf != null && multipleOf.signum() > 0) {
            value = value.divide(multipleOf, 0, java.math.RoundingMode.CEILING).multiply(multipleOf);
        }
        if (integer) {
            value = value.setScale(0, java.math.RoundingMode.CEILING);
            return value.longValueExact();
        }
        return value.stripTrailingZeros().scale() <= 0 ? (Number) value.longValue() : value;
    }

    private static String string(SchemaView view) {
        String format = view.format();
        String sample = format == null ? "string" : switch (format) {
            case "date-time" -> "2026-01-01T12:00:00Z";
            case "date" -> "2026-01-01";
            case "time" -> "12:00:00Z";
            case "duration" -> "P1D";
            case "email", "idn-email" -> "user@example.com";
            case "hostname", "idn-hostname" -> "example.com";
            case "ipv4" -> "192.0.2.1";
            case "ipv6" -> "2001:db8::1";
            case "uri", "iri", "uri-reference", "iri-reference", "url" -> "https://example.com/";
            case "uuid" -> "123e4567-e89b-12d3-a456-426614174000";
            case "regex" -> ".*";
            case "json-pointer" -> "/";
            default -> "string";
        };
        int minLength = intConstraint(view, "minLength", 0);
        int maxLength = intConstraint(view, "maxLength", Integer.MAX_VALUE);
        String fromPattern = RegexSample.of(view.constraints().get("pattern"));
        if (fromPattern != null && fromPattern.length() >= minLength && fromPattern.length() <= maxLength) {
            return fromPattern;
        }
        StringBuilder value = new StringBuilder(sample);
        while (value.length() < minLength) {
            value.append('x');
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value.toString();
    }

    private static int intConstraint(SchemaView view, String keyword, int fallback) {
        try {
            String value = view.constraints().get(keyword);
            return value == null ? fallback : new BigDecimal(value).intValue();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BigDecimal decimal(SchemaView view, String keyword) {
        try {
            String value = view.constraints().get(keyword);
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null; // draft-4 boolean exclusiveMinimum
        }
    }
}
