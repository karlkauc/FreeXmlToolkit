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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;
import org.fxt.freexmltoolkit.di.ServiceRegistry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;

/**
 * Service for JSON operations including parsing, formatting, validation,
 * and JSONPath queries. Supports JSON, JSONC, and JSON5 formats.
 */
public class JsonService {

    private static final Logger logger = LogManager.getLogger(JsonService.class);

    private final Gson gson;
    private final Gson gsonPretty;

    // Cached JSON Schema registry
    private SchemaRegistry schemaRegistry;

    /**
     * Creates a new JsonService with default Gson configurations.
     */
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
            SchemaRegistry registry = getSchemaRegistry();
            Schema schema = registry.getSchema(schemaJson);

            List<Error> validationErrors = schema.validate(json, InputFormat.JSON);

            for (Error error : validationErrors) {
                errors.add(error.getMessage());
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
        List<String> errors = new ArrayList<>();
        for (SchemaError error : validateAgainstSchemaDetailed(json, schemaFile)) {
            errors.add(error.message());
        }
        return errors;
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
            SchemaRegistry registry = getSchemaRegistry();
            Schema schema = registry.getSchema(SchemaLocation.of(schemaUri.toString()));

            List<Error> validationErrors = schema.validate(json, InputFormat.JSON);

            for (Error error : validationErrors) {
                errors.add(error.getMessage());
            }
        } catch (Exception e) {
            errors.add("Schema validation error: " + e.getMessage());
            logger.error("Schema validation failed", e);
        }

        return errors;
    }

    private SchemaRegistry getSchemaRegistry() {
        if (schemaRegistry == null) {
            schemaRegistry = newSchemaRegistry();
        }
        return schemaRegistry;
    }

    /**
     * 2020-12 is only the fallback dialect — a schema with an explicit {@code $schema}
     * selects its own (draft-07 / 2019-09 / 2020-12 meta-schemas ship on the classpath).
     * {@code fetchRemoteResources()} is required even for local {@code file:} schemas
     * (the default loader only serves the classpath) and also lets relative and remote
     * {@code $ref}s resolve — mirroring how XSD imports are fetched.
     */
    private static SchemaRegistry newSchemaRegistry() {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources()));
    }

    // ==================== JSON Schema binding ====================

    /**
     * One JSON Schema violation: display message, instance location (both as walkable
     * segments and as an RFC 6901 pointer string), and the failing keyword.
     *
     * @param message          human-readable error message
     * @param instanceSegments instance location as segments (String = object key, Integer = array index)
     * @param instancePointer  instance location as an RFC 6901 JSON pointer ("" = root)
     * @param keyword          the failing schema keyword (e.g. "type", "required"), may be null
     */
    public record SchemaError(String message, List<Object> instanceSegments,
                              String instancePointer, String keyword) {
    }

    /** json-schema.org meta-schema ids declare the document's own dialect, not a validation binding. */
    private static final Pattern META_SCHEMA_ID = Pattern.compile("^https?://json-schema\\.org/");

    private static final String SCHEMA_CACHE_DIR = FileUtils.getUserDirectory().getAbsolutePath()
            + File.separator + ".freeXmlToolkit" + File.separator + "cache";

    /**
     * Sniffs a top-level {@code "$schema"} string member from a JSON instance document,
     * without filtering out json-schema.org meta-schema ids. Used by the Schema Library
     * lookup, which maps the raw id (a meta-schema id can legitimately be registered as a
     * library entry's key) rather than a validation-binding location.
     *
     * @param json the JSON instance document text
     * @return the raw declared {@code $schema} value, or empty if none is declared
     */
    public Optional<String> getRawSchemaIdFromJsonContent(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement schema = root.getAsJsonObject().get("$schema");
            if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String location = schema.getAsString().trim();
            return location.isEmpty() ? Optional.empty() : Optional.of(location);
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Sniffs a top-level {@code "$schema"} string member from a JSON instance document.
     * Returns empty for json-schema.org meta-schema ids — those declare the document's
     * own dialect (the document IS a schema), not a validation binding.
     *
     * @param json the JSON instance document text
     * @return the declared schema location, or empty if none is declared
     */
    public Optional<String> getSchemaLocationFromJsonContent(String json) {
        return getRawSchemaIdFromJsonContent(json).filter(l -> !META_SCHEMA_ID.matcher(l).find());
    }

    /**
     * Resolves a {@code $schema} location to a local file: absolute path, path relative
     * to {@code baseDir}, {@code file:} URI, or http(s) URL (downloaded once into the
     * shared schema cache, keyed by the MD5 of the lowercased URL).
     *
     * @param location the declared schema location
     * @param baseDir  directory to resolve relative paths against, may be null
     * @return the resolved local file, or null when unresolvable
     */
    public File resolveJsonSchemaLocation(String location, File baseDir) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String temp = location.trim();
        try {
            if (temp.startsWith("file:")) {
                File file = new File(new URI(temp));
                return file.exists() ? file : null;
            }
            if (temp.startsWith("http://") || temp.startsWith("https://")) {
                return downloadSchemaToCache(temp);
            }
            File file = new File(temp);
            if (!file.isAbsolute() && baseDir != null) {
                file = new File(baseDir, temp);
            }
            return file.exists() ? file : null;
        } catch (Exception e) {
            logger.warn("Could not resolve JSON Schema location '{}': {}", temp, e.getMessage());
            return null;
        }
    }

    private File downloadSchemaToCache(String url) {
        try {
            String md5Hex = DigestUtils.md5Hex(url.toLowerCase()).toUpperCase();
            Path cacheDir = Path.of(SCHEMA_CACHE_DIR, md5Hex);
            String fileName = FilenameUtils.getName(URI.create(url).getPath());
            if (fileName == null || fileName.isBlank()) {
                fileName = "schema.json";
            }
            Path cached = cacheDir.resolve(fileName);
            if (Files.exists(cached) && Files.size(cached) > 1) {
                return cached.toFile();
            }
            String content = ServiceRegistry.get(ConnectionService.class).getTextContentFromURL(URI.create(url));
            // Pre-save gate: keep HTML error pages and other garbage out of the cache.
            if (!isValidJson(content)) {
                logger.warn("Downloaded JSON Schema from '{}' is not valid JSON - not cached", url);
                return null;
            }
            Files.createDirectories(cacheDir);
            Files.writeString(cached, content, StandardCharsets.UTF_8);
            return cached.toFile();
        } catch (Exception e) {
            logger.warn("Could not download JSON Schema '{}': {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Structured variant of {@link #validateAgainstSchema(String, File)}: each violation
     * carries its instance location so callers can map it to a line in the source text.
     *
     * @param json       the JSON string to validate
     * @param schemaFile the JSON Schema file
     * @return list of violations, empty if valid
     */
    public List<SchemaError> validateAgainstSchemaDetailed(String json, File schemaFile) {
        List<SchemaError> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            errors.add(new SchemaError("JSON content is empty", List.of(), "", null));
            return errors;
        }
        if (schemaFile == null || !schemaFile.exists()) {
            errors.add(new SchemaError("JSON Schema file not found: " + schemaFile, List.of(), "", null));
            return errors;
        }
        try {
            // Fresh registry per call: registries cache compiled schemas by location,
            // so a shared one would keep serving stale results after schema edits.
            SchemaRegistry registry = newSchemaRegistry();
            // Loading via the file's URI keeps the base URI so relative $refs resolve.
            Schema schema = registry.getSchema(SchemaLocation.of(schemaFile.toURI().toString()));
            for (Error error : schema.validate(json, InputFormat.JSON)) {
                errors.add(toSchemaError(error));
            }
        } catch (Exception e) {
            errors.add(new SchemaError("Schema validation error: " + e.getMessage(), List.of(), "", null));
            logger.error("Schema validation failed", e);
        }
        return errors;
    }

    private static SchemaError toSchemaError(Error error) {
        List<Object> segments = new ArrayList<>();
        NodePath location = error.getInstanceLocation();
        if (location != null) {
            for (int i = 0; i < location.getNameCount(); i++) {
                segments.add(location.getElement(i));
            }
        }
        StringBuilder pointer = new StringBuilder();
        for (Object segment : segments) {
            pointer.append('/').append(segment.toString().replace("~", "~0").replace("/", "~1"));
        }
        return new SchemaError(error.getMessage(), List.copyOf(segments), pointer.toString(), error.getKeyword());
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
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
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
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonObject()) {
            return "object";
        }
        if (element.isJsonArray()) {
            return "array";
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return "boolean";
            }
            if (primitive.isNumber()) {
                return "number";
            }
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
