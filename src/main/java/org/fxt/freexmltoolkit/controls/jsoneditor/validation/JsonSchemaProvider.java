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

package org.fxt.freexmltoolkit.controls.jsoneditor.validation;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider for JSON Schema objects with caching support.
 * Supports JSON Schema drafts: Draft 4, Draft 6, Draft 7, 2019-09, 2020-12.
 */
public class JsonSchemaProvider {

    private static final Logger logger = LogManager.getLogger(JsonSchemaProvider.class);

    // Schema factories for different versions
    private static final Map<SpecVersion.VersionFlag, JsonSchemaFactory> factories = new ConcurrentHashMap<>();

    // Schema cache (file path -> schema)
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    // Cache entry with timestamp
    private record CachedSchema(JsonSchema schema, long timestamp) {}

    // Cache TTL in milliseconds (5 minutes)
    private static final long CACHE_TTL = 5 * 60 * 1000;

    /**
     * Creates a new JsonSchemaProvider with an empty cache.
     * Schema factories are initialized lazily when needed.
     */
    public JsonSchemaProvider() {
        // Initialize factories lazily
    }

    /**
     * Gets a JsonSchemaFactory for the specified version.
     */
    private JsonSchemaFactory getFactory(SpecVersion.VersionFlag version) {
        return factories.computeIfAbsent(version, JsonSchemaFactory::getInstance);
    }

    /**
     * Gets the default factory (latest version: 2020-12).
     */
    private JsonSchemaFactory getDefaultFactory() {
        return getFactory(SpecVersion.VersionFlag.V202012);
    }

    /**
     * Loads a JSON Schema from a file.
     *
     * @param schemaFile the schema file
     * @return the compiled JsonSchema
     * @throws IOException if reading fails
     */
    public JsonSchema loadSchema(File schemaFile) throws IOException {
        String path = schemaFile.getAbsolutePath();

        // Check cache
        CachedSchema cached = schemaCache.get(path);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL) {
            // Check if file was modified
            if (schemaFile.lastModified() < cached.timestamp) {
                return cached.schema;
            }
        }

        // Load and parse schema
        String content = Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8);
        JsonSchema schema = loadSchemaFromString(content);

        // Cache it
        schemaCache.put(path, new CachedSchema(schema, System.currentTimeMillis()));

        logger.debug("Loaded schema from file: {}", schemaFile.getName());
        return schema;
    }

    /**
     * Loads a JSON Schema from a string.
     *
     * @param schemaJson the schema as JSON string
     * @return the compiled JsonSchema
     */
    public JsonSchema loadSchemaFromString(String schemaJson) {
        // Detect schema version from $schema property
        SpecVersion.VersionFlag version = detectSchemaVersion(schemaJson);
        JsonSchemaFactory factory = getFactory(version);

        return factory.getSchema(schemaJson);
    }

    /**
     * Loads a JSON Schema from a URI.
     *
     * @param schemaUri the schema URI
     * @return the compiled JsonSchema
     */
    public JsonSchema loadSchema(URI schemaUri) {
        return getDefaultFactory().getSchema(schemaUri);
    }

    /**
     * Detects the JSON Schema version from the $schema property.
     */
    private SpecVersion.VersionFlag detectSchemaVersion(String schemaJson) {
        // Look for $schema property
        if (schemaJson.contains("draft-04")) {
            return SpecVersion.VersionFlag.V4;
        }
        if (schemaJson.contains("draft-06")) {
            return SpecVersion.VersionFlag.V6;
        }
        if (schemaJson.contains("draft-07")) {
            return SpecVersion.VersionFlag.V7;
        }
        if (schemaJson.contains("2019-09")) {
            return SpecVersion.VersionFlag.V201909;
        }
        if (schemaJson.contains("2020-12")) {
            return SpecVersion.VersionFlag.V202012;
        }

        // Default to latest
        return SpecVersion.VersionFlag.V202012;
    }

    /**
     * Clears the schema cache.
     */
    public void clearCache() {
        schemaCache.clear();
        logger.debug("Schema cache cleared");
    }

    /**
     * Removes a specific schema from cache.
     *
     * @param schemaFile the schema file to remove from cache
     */
    public void invalidateCache(File schemaFile) {
        schemaCache.remove(schemaFile.getAbsolutePath());
    }

    /**
     * Gets the number of schemas currently in the cache.
     *
     * @return the number of cached schemas
     */
    public int getCacheSize() {
        return schemaCache.size();
    }

    /**
     * Creates common JSON Schema templates.
     */
    public static class Templates {

        /**
         * Private constructor to prevent instantiation of this utility class.
         */
        private Templates() {
            // Utility class
        }

        /**
         * Creates a minimal schema with the specified type.
         *
         * @param type the JSON type (e.g., "string", "number", "object", "array")
         * @return a JSON Schema string with the specified type
         */
        public static String createTypeSchema(String type) {
            return """
                    {
                      "$schema": "https://json-schema.org/draft/2020-12/schema",
                      "type": "%s"
                    }
                    """.formatted(type);
        }

        /**
         * Creates an object schema with required properties.
         *
         * @param properties the property names to include as required
         * @return a JSON Schema string for an object with the specified required properties
         */
        public static String createObjectSchema(String... properties) {
            StringBuilder sb = new StringBuilder();
            sb.append("""
                    {
                      "$schema": "https://json-schema.org/draft/2020-12/schema",
                      "type": "object",
                      "properties": {
                    """);

            for (int i = 0; i < properties.length; i++) {
                sb.append("    \"").append(properties[i]).append("\": {}");
                if (i < properties.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            sb.append("""
                      },
                      "required": [
                    """);

            for (int i = 0; i < properties.length; i++) {
                sb.append("    \"").append(properties[i]).append("\"");
                if (i < properties.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            sb.append("""
                      ]
                    }
                    """);

            return sb.toString();
        }

        /**
         * Creates an array schema with the specified item type.
         *
         * @param itemType the JSON type for array items (e.g., "string", "number")
         * @return a JSON Schema string for an array with the specified item type
         */
        public static String createArraySchema(String itemType) {
            return """
                    {
                      "$schema": "https://json-schema.org/draft/2020-12/schema",
                      "type": "array",
                      "items": {
                        "type": "%s"
                      }
                    }
                    """.formatted(itemType);
        }
    }
}
