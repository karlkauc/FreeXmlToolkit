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

package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Factory for creating JsonNode trees from JSON text or Gson JsonElement.
 * This class provides static methods and should not be instantiated.
 */
public class JsonNodeFactory {

    private static final Logger logger = LogManager.getLogger(JsonNodeFactory.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JsonNodeFactory() {
        // Utility class
    }

    /**
     * Parses a JSON string into a JsonDocument.
     *
     * @param jsonText the JSON text to parse
     * @return the parsed JsonDocument
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public static JsonDocument parse(String jsonText) throws JsonSyntaxException {
        if (jsonText == null || jsonText.isBlank()) {
            return new JsonDocument();
        }

        // Detect format and strip comments if needed
        String format = JsonSyntaxHighlighter.detectFormat(jsonText);
        String cleanedText = jsonText;

        if ("jsonc".equals(format) || "json5".equals(format)) {
            cleanedText = JsonSyntaxHighlighter.stripComments(jsonText);
            // For JSON5, also handle trailing commas
            if ("json5".equals(format)) {
                cleanedText = cleanedText.replaceAll(",\\s*([}\\]])", "$1");
            }
        }

        JsonElement gsonElement = JsonParser.parseString(cleanedText);
        JsonNode rootNode = fromGsonElement(gsonElement);

        JsonDocument doc = new JsonDocument(rootNode);
        doc.setFormat(format);

        logger.debug("Parsed JSON document with format: {}", format);
        return doc;
    }

    /**
     * Parses a JSON file into a JsonDocument.
     *
     * @param file the file to parse
     * @return the parsed JsonDocument
     * @throws IOException if reading fails
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public static JsonDocument parse(File file) throws IOException, JsonSyntaxException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonDocument doc = parse(content);
        doc.setSourceFile(file);
        return doc;
    }

    /**
     * Converts a Gson JsonElement to our JsonNode model.
     *
     * @param element the Gson element
     * @return the corresponding JsonNode
     */
    public static JsonNode fromGsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonPrimitive.nullValue();
        }

        if (element.isJsonPrimitive()) {
            return fromGsonPrimitive(element.getAsJsonPrimitive());
        }

        if (element.isJsonArray()) {
            return fromGsonArray(element.getAsJsonArray());
        }

        if (element.isJsonObject()) {
            return fromGsonObject(element.getAsJsonObject());
        }

        // Fallback
        return JsonPrimitive.nullValue();
    }

    /**
     * Converts a Gson primitive to JsonPrimitive.
     */
    private static JsonPrimitive fromGsonPrimitive(com.google.gson.JsonPrimitive gsonPrimitive) {
        if (gsonPrimitive.isBoolean()) {
            return new JsonPrimitive(gsonPrimitive.getAsBoolean());
        }

        if (gsonPrimitive.isNumber()) {
            Number number = gsonPrimitive.getAsNumber();

            // Try to use the most appropriate number type
            String numStr = number.toString();
            if (!numStr.contains(".") && !numStr.contains("e") && !numStr.contains("E")) {
                // Integer type
                try {
                    long longVal = number.longValue();
                    if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                        return new JsonPrimitive((int) longVal);
                    }
                    return new JsonPrimitive(longVal);
                } catch (NumberFormatException e) {
                    return new JsonPrimitive(new BigDecimal(numStr));
                }
            }
            // Floating point
            return new JsonPrimitive(number.doubleValue());
        }

        if (gsonPrimitive.isString()) {
            return new JsonPrimitive(gsonPrimitive.getAsString());
        }

        return JsonPrimitive.nullValue();
    }

    /**
     * Converts a Gson array to JsonArray.
     */
    private static JsonArray fromGsonArray(com.google.gson.JsonArray gsonArray) {
        JsonArray array = new JsonArray();

        for (JsonElement element : gsonArray) {
            array.add(fromGsonElement(element));
        }

        return array;
    }

    /**
     * Converts a Gson object to JsonObject.
     */
    private static JsonObject fromGsonObject(com.google.gson.JsonObject gsonObject) {
        JsonObject obj = new JsonObject();

        for (var entry : gsonObject.entrySet()) {
            JsonNode value = fromGsonElement(entry.getValue());
            value.setKey(entry.getKey());
            obj.addChild(value);
        }

        return obj;
    }

    /**
     * Creates an empty JsonObject.
     *
     * @return a new empty JsonObject instance
     */
    public static JsonObject createObject() {
        return new JsonObject();
    }

    /**
     * Creates an empty JsonArray.
     *
     * @return a new empty JsonArray instance
     */
    public static JsonArray createArray() {
        return new JsonArray();
    }

    /**
     * Creates a string primitive.
     *
     * @param value the string value
     * @return a new JsonPrimitive containing the string value
     */
    public static JsonPrimitive createString(String value) {
        return new JsonPrimitive(value);
    }

    /**
     * Creates a number primitive.
     *
     * @param value the number value
     * @return a new JsonPrimitive containing the number value
     */
    public static JsonPrimitive createNumber(Number value) {
        return new JsonPrimitive(value);
    }

    /**
     * Creates a boolean primitive.
     *
     * @param value the boolean value
     * @return a new JsonPrimitive containing the boolean value
     */
    public static JsonPrimitive createBoolean(boolean value) {
        return new JsonPrimitive(value);
    }

    /**
     * Creates a null primitive.
     *
     * @return a new JsonPrimitive representing a JSON null value
     */
    public static JsonPrimitive createNull() {
        return JsonPrimitive.nullValue();
    }

    /**
     * Creates a new empty document with an empty object as root.
     *
     * @return a new JsonDocument with an empty JsonObject as root
     */
    public static JsonDocument createEmptyDocument() {
        JsonDocument doc = new JsonDocument();
        doc.setRootValue(new JsonObject());
        return doc;
    }

    /**
     * Creates a document from simple key-value string pairs.
     * The key-value pairs should be provided as alternating key and value arguments.
     *
     * @param keyValues alternating key and value strings (key1, value1, key2, value2, ...)
     * @return a new JsonDocument containing an object with the specified properties
     */
    public static JsonDocument createSimpleDocument(String... keyValues) {
        JsonObject root = new JsonObject();

        for (int i = 0; i < keyValues.length - 1; i += 2) {
            String key = keyValues[i];
            String value = keyValues[i + 1];
            JsonPrimitive primitive = new JsonPrimitive(value);
            primitive.setKey(key);
            root.addChild(primitive);
        }

        return new JsonDocument(root);
    }
}
