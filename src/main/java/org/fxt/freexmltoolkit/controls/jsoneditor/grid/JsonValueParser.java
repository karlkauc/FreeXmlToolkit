/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;

/**
 * Converts between the raw text of a grid edit field and the Java value stored
 * in a {@link JsonPrimitive}.
 *
 * <p>Pure utility, no UI dependencies. Numbers are parsed into
 * {@link BigInteger} (no fraction/exponent) or {@link BigDecimal} so that the
 * text the user typed survives serialization unchanged.</p>
 *
 * @since 2.0
 */
public final class JsonValueParser {

    private JsonValueParser() {
        // Utility class
    }

    /**
     * Parses edit-field text into the Java value for a primitive of the given type.
     *
     * <ul>
     *   <li>{@code NUMBER}: {@link BigInteger} when the trimmed text contains no
     *       {@code .}, {@code e} or {@code E}, otherwise {@link BigDecimal}</li>
     *   <li>{@code BOOLEAN}: only {@code true} / {@code false} (case-insensitive)</li>
     *   <li>{@code NULL}: the text {@code null} (or no text) maps to {@code null},
     *       anything else is kept as a String</li>
     *   <li>{@code STRING}: the text as is ({@code null} becomes the empty string)</li>
     * </ul>
     *
     * @param text        the raw text (may be null)
     * @param currentType the primitive type the text is entered for
     * @return the parsed value (may be {@code null} for the NULL type)
     * @throws IllegalArgumentException if the text cannot be parsed for the type,
     *                                  or the type is not a primitive type
     */
    public static Object parse(String text, JsonNodeType currentType) {
        if (currentType == null) {
            throw new IllegalArgumentException("Type must not be null");
        }
        return switch (currentType) {
            case STRING -> text == null ? "" : text;
            case NUMBER -> parseNumber(text);
            case BOOLEAN -> parseBoolean(text);
            case NULL -> (text == null || "null".equals(text.trim())) ? null : text;
            default -> throw new IllegalArgumentException("Not a primitive type: " + currentType);
        };
    }

    /**
     * Parses a JSON number literal.
     *
     * @param text the raw text
     * @return {@link BigInteger} for integral literals, {@link BigDecimal} otherwise
     * @throws IllegalArgumentException if the text is not a number
     */
    public static Number parseNumber(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Not a number: '" + text + "'");
        }
        String trimmed = text.trim();
        try {
            if (trimmed.indexOf('.') < 0 && trimmed.indexOf('e') < 0 && trimmed.indexOf('E') < 0) {
                return new BigInteger(trimmed);
            }
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number: '" + text + "'", e);
        }
    }

    private static Boolean parseBoolean(String text) {
        if (text != null) {
            String trimmed = text.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
        }
        throw new IllegalArgumentException("Not a boolean: '" + text + "'");
    }

    /**
     * Returns the raw text to show in an edit field for the given primitive.
     *
     * @param primitive the primitive (may be null)
     * @return {@code null} for a JSON null (or a null primitive), otherwise the
     *         unquoted text form of the value
     */
    public static String display(JsonPrimitive primitive) {
        if (primitive == null || primitive.isNull()) {
            return null;
        }
        return primitive.getAsString();
    }
}
