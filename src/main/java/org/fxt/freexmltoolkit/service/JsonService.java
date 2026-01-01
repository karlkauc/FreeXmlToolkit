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

package org.fxt.freexmltoolkit.service;

import com.google.gson.*;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.networknt.schema.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for JSON operations including parsing, formatting, validation,
 * and JSONPath queries. Supports JSON, JSONC, and JSON5 formats.
 */
public class JsonService {

    private static final Logger logger = LogManager.getLogger(JsonService.class);

    private final Gson gson;
    private final Gson gsonPretty;

    // Cached JSON Schema factory
    private JsonSchemaFactory schemaFactory;

    public JsonService() {
        // Standard Gson for JSON
        this.gson = new GsonBuilder().create();
        this.gsonPretty = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .disableHtmlEscaping()
                .create();
    }

    // ==================== Parsing ====================

    /**
     * Parses a JSON string into a JsonElement.
     *
     * @param json The JSON string to parse
     * @return The parsed JsonElement
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public JsonElement parseJson(String json) throws JsonSyntaxException {
        if (json == null || json.isBlank()) {
            return JsonNull.INSTANCE;
        }
        return JsonParser.parseString(json);
    }

    /**
     * Parses a JSONC string (JSON with comments) into a JsonElement.
     * Comments are stripped before parsing.
     *
     * @param jsonc The JSONC string to parse
     * @return The parsed JsonElement
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public JsonElement parseJsonc(String jsonc) throws JsonSyntaxException {
        if (jsonc == null || jsonc.isBlank()) {
            return JsonNull.INSTANCE;
        }
        String stripped = JsonSyntaxHighlighter.stripComments(jsonc);
        return JsonParser.parseString(stripped);
    }

    /**
     * Parses a JSON5 string into a JsonElement.
     * Note: JSON5 features are converted to standard JSON before parsing.
     *
     * @param json5Text The JSON5 string to parse
     * @return The parsed JsonElement
     * @throws JsonSyntaxException if the JSON5 is invalid
     */
    public JsonElement parseJson5(String json5Text) throws JsonSyntaxException {
        if (json5Text == null || json5Text.isBlank()) {
            return JsonNull.INSTANCE;
        }
        // Convert JSON5 to standard JSON
        String standardJson = convertJson5ToJson(json5Text);
        return JsonParser.parseString(standardJson);
    }

    /**
     * Converts JSON5 features to standard JSON.
     * Handles: comments, trailing commas, unquoted keys, single quotes.
     *
     * @param json5 The JSON5 text
     * @return Standard JSON text
     */
    private String convertJson5ToJson(String json5) {
        // Step 1: Strip comments
        String result = JsonSyntaxHighlighter.stripComments(json5);

        // Step 2: Remove trailing commas before } or ]
        result = result.replaceAll(",\\s*([}\\]])", "$1");

        // Step 3: Convert single quotes to double quotes (simple approach)
        // Note: This is a simplified conversion; complex cases may need more work
        result = result.replace("'", "\"");

        // Step 4: Quote unquoted keys
        // Pattern: word characters followed by colon, not inside quotes
        result = quoteUnquotedKeys(result);

        return result;
    }

    /**
     * Quotes unquoted object keys in JSON5.
     */
    private String quoteUnquotedKeys(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        int i = 0;

        while (i < json.length()) {
            char c = json.charAt(i);

            // Track string state
            if (!inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
                result.append(c);
                i++;
                continue;
            }
            if (inString && c == stringChar && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = false;
                result.append(c);
                i++;
                continue;
            }

            // Look for unquoted keys when not in string
            if (!inString && Character.isLetter(c) || c == '_' || c == '$') {
                // Collect the identifier
                StringBuilder key = new StringBuilder();
                int start = i;
                while (i < json.length() && (Character.isLetterOrDigit(json.charAt(i)) ||
                        json.charAt(i) == '_' || json.charAt(i) == '$')) {
                    key.append(json.charAt(i));
                    i++;
                }

                // Skip whitespace
                int afterKey = i;
                while (afterKey < json.length() && Character.isWhitespace(json.charAt(afterKey))) {
                    afterKey++;
                }

                // Check if followed by colon (then it's a key)
                if (afterKey < json.length() && json.charAt(afterKey) == ':') {
                    String keyStr = key.toString();
                    // Check if it's a keyword (true, false, null, Infinity, NaN)
                    if (keyStr.equals("true") || keyStr.equals("false") || keyStr.equals("null") ||
                            keyStr.equals("Infinity") || keyStr.equals("NaN")) {
                        result.append(keyStr);
                    } else {
                        result.append('"').append(keyStr).append('"');
                    }
                } else {
                    // It's a value, not a key
                    result.append(key);
                }
                continue;
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Auto-detects the format and parses accordingly.
     *
     * @param text The JSON/JSONC/JSON5 text to parse
     * @return The parsed JsonElement
     * @throws JsonSyntaxException if parsing fails
     */
    public JsonElement parseAuto(String text) throws JsonSyntaxException {
        if (text == null || text.isBlank()) {
            return JsonNull.INSTANCE;
        }

        String format = JsonSyntaxHighlighter.detectFormat(text);
        return switch (format) {
            case "json5" -> parseJson5(text);
            case "jsonc" -> parseJsonc(text);
            default -> parseJson(text);
        };
    }

    /**
     * Parses a JSON file.
     *
     * @param file The file to parse
     * @return The parsed JsonElement
     * @throws IOException if reading fails
     * @throws JsonSyntaxException if JSON is invalid
     */
    public JsonElement parseFile(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return parseAuto(content);
    }

    // ==================== Formatting ====================

    /**
     * Formats JSON with pretty printing.
     *
     * @param json   The JSON string to format
     * @param indent The number of spaces for indentation
     * @return The formatted JSON string
     * @throws JsonSyntaxException if JSON is invalid
     */
    public String formatJson(String json, int indent) throws JsonSyntaxException {
        if (json == null || json.isBlank()) {
            return json;
        }

        JsonElement element = parseJson(json);
        if (indent == 2) {
            return gsonPretty.toJson(element);
        }

        // Custom indent
        Gson customGson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .disableHtmlEscaping()
                .create();

        String formatted = customGson.toJson(element);

        // Adjust indentation if not 2
        if (indent != 2) {
            String spaces = " ".repeat(indent);
            formatted = formatted.replaceAll("(?m)^  ", spaces);
        }

        return formatted;
    }

    /**
     * Formats JSON with default 2-space indentation.
     *
     * @param json The JSON string to format
     * @return The formatted JSON string
     */
    public String formatJson(String json) {
        return formatJson(json, 2);
    }

    /**
     * Compacts JSON by removing whitespace.
     *
     * @param json The JSON string to compact
     * @return The compacted JSON string
     */
    public String compactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        JsonElement element = parseJson(json);
        return gson.toJson(element);
    }

    /**
     * Formats JSONC (strips comments and formats).
     *
     * @param jsonc  The JSONC string to format
     * @param indent The number of spaces for indentation
     * @return The formatted JSON string (comments removed)
     */
    public String formatJsonc(String jsonc, int indent) {
        String stripped = JsonSyntaxHighlighter.stripComments(jsonc);
        return formatJson(stripped, indent);
    }

    /**
     * Formats JSON5 (converts to JSON and formats).
     *
     * @param json5Text The JSON5 string to format
     * @return The formatted JSON string
     */
    public String formatJson5(String json5Text) {
        if (json5Text == null || json5Text.isBlank()) {
            return json5Text;
        }
        JsonElement element = parseJson5(json5Text);
        return gsonPretty.toJson(element);
    }

    // ==================== Validation ====================

    /**
     * Validates that a string is valid JSON.
     *
     * @param json The JSON string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            parseJson(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Validates JSON and returns any error message.
     *
     * @param json The JSON string to validate
     * @return null if valid, error message otherwise
     */
    public String validateJson(String json) {
        if (json == null || json.isBlank()) {
            return "JSON content is empty";
        }
        try {
            parseJson(json);
            return null;
        } catch (JsonSyntaxException e) {
            return "JSON syntax error: " + e.getMessage();
        }
    }

    /**
     * Validates JSON against a JSON Schema.
     *
     * @param json       The JSON string to validate
     * @param schemaJson The JSON Schema as a string
     * @return List of validation errors, empty if valid
     */
    public List<String> validateAgainstSchema(String json, String schemaJson) {
        List<String> errors = new ArrayList<>();

        if (json == null || json.isBlank()) {
            errors.add("JSON content is empty");
            return errors;
        }

        if (schemaJson == null || schemaJson.isBlank()) {
            errors.add("JSON Schema is empty");
            return errors;
        }

        try {
            JsonSchemaFactory factory = getSchemaFactory();
            JsonSchema schema = factory.getSchema(schemaJson);

            Set<ValidationMessage> validationMessages = schema.validate(json, InputFormat.JSON);

            for (ValidationMessage message : validationMessages) {
                errors.add(message.getMessage());
            }
        } catch (Exception e) {
            errors.add("Schema validation error: " + e.getMessage());
            logger.error("Schema validation failed", e);
        }

        return errors;
    }

    /**
     * Validates JSON against a JSON Schema file.
     *
     * @param json       The JSON string to validate
     * @param schemaFile The JSON Schema file
     * @return List of validation errors, empty if valid
     */
    public List<String> validateAgainstSchema(String json, File schemaFile) {
        try {
            String schemaJson = Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8);
            return validateAgainstSchema(json, schemaJson);
        } catch (IOException e) {
            return List.of("Failed to read schema file: " + e.getMessage());
        }
    }

    /**
     * Validates JSON against a JSON Schema from a URI.
     *
     * @param json      The JSON string to validate
     * @param schemaUri The JSON Schema URI
     * @return List of validation errors, empty if valid
     */
    public List<String> validateAgainstSchema(String json, URI schemaUri) {
        List<String> errors = new ArrayList<>();

        try {
            JsonSchemaFactory factory = getSchemaFactory();
            JsonSchema schema = factory.getSchema(schemaUri);

            Set<ValidationMessage> validationMessages = schema.validate(json, InputFormat.JSON);

            for (ValidationMessage message : validationMessages) {
                errors.add(message.getMessage());
            }
        } catch (Exception e) {
            errors.add("Schema validation error: " + e.getMessage());
            logger.error("Schema validation failed", e);
        }

        return errors;
    }

    private JsonSchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        }
        return schemaFactory;
    }

    // ==================== JSONPath ====================

    /**
     * Executes a JSONPath query on a JSON string.
     *
     * @param json     The JSON string to query
     * @param jsonPath The JSONPath expression
     * @return The query result
     * @throws PathNotFoundException if the path doesn't exist
     */
    public Object executeJsonPath(String json, String jsonPath) {
        Configuration config = Configuration.builder()
                .options(Option.ALWAYS_RETURN_LIST)
                .build();

        return JsonPath.using(config).parse(json).read(jsonPath);
    }

    /**
     * Executes a JSONPath query and returns the result as a formatted JSON string.
     *
     * @param json     The JSON string to query
     * @param jsonPath The JSONPath expression
     * @return The query result as a formatted JSON string
     */
    public String executeJsonPathAsString(String json, String jsonPath) {
        try {
            Object result = executeJsonPath(json, jsonPath);
            return gsonPretty.toJson(result);
        } catch (PathNotFoundException e) {
            return "Path not found: " + jsonPath;
        } catch (Exception e) {
            return "JSONPath error: " + e.getMessage();
        }
    }

    /**
     * Checks if a JSONPath expression is valid.
     *
     * @param jsonPath The JSONPath expression to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidJsonPath(String jsonPath) {
        try {
            JsonPath.compile(jsonPath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== File Operations ====================

    /**
     * Reads and parses a JSON file.
     *
     * @param path The path to the JSON file
     * @return The parsed JsonElement
     * @throws IOException if reading fails
     */
    public JsonElement readFile(Path path) throws IOException {
        return parseFile(path.toFile());
    }

    /**
     * Writes a JsonElement to a file with pretty printing.
     *
     * @param element The JsonElement to write
     * @param path    The path to write to
     * @throws IOException if writing fails
     */
    public void writeFile(JsonElement element, Path path) throws IOException {
        String json = gsonPretty.toJson(element);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Writes a JSON string to a file.
     *
     * @param json   The JSON string to write
     * @param path   The path to write to
     * @param format Whether to format the JSON before writing
     * @throws IOException if writing fails
     */
    public void writeFile(String json, Path path, boolean format) throws IOException {
        String content = format ? formatJson(json) : json;
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    // ==================== Utility Methods ====================

    /**
     * Detects the JSON format variant of a string.
     *
     * @param text The text to analyze
     * @return "json", "jsonc", or "json5"
     */
    public String detectFormat(String text) {
        return JsonSyntaxHighlighter.detectFormat(text);
    }

    /**
     * Converts a JsonElement to a Map (for objects) or List (for arrays).
     *
     * @param element The JsonElement to convert
     * @return The converted object
     */
    public Object toNativeObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsNumber();
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : element.getAsJsonArray()) {
                list.add(toNativeObject(e));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), toNativeObject(entry.getValue()));
            }
            return map;
        }
        return null;
    }

    /**
     * Gets the type of a JSON value as a string.
     *
     * @param element The JsonElement to check
     * @return "object", "array", "string", "number", "boolean", or "null"
     */
    public String getJsonType(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonObject()) return "object";
        if (element.isJsonArray()) return "array";
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return "boolean";
            if (primitive.isNumber()) return "number";
            return "string";
        }
        return "unknown";
    }

    /**
     * Counts the number of properties/elements in a JSON structure.
     *
     * @param element The JsonElement to count
     * @return The count of immediate children
     */
    public int countChildren(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return 0;
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().size();
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().size();
        }
        return 0;
    }
}
