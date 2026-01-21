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

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Service for validating JSON documents against JSON Schema.
 * Provides detailed validation results with line numbers when possible.
 */
public class JsonValidationService {

    private static final Logger logger = LogManager.getLogger(JsonValidationService.class);

    private final JsonSchemaProvider schemaProvider;

    /**
     * Creates a new JsonValidationService with a default schema provider.
     */
    public JsonValidationService() {
        this.schemaProvider = new JsonSchemaProvider();
    }

    /**
     * Creates a new JsonValidationService with a custom schema provider.
     *
     * @param schemaProvider the schema provider to use for loading schemas
     */
    public JsonValidationService(JsonSchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
    }

    /**
     * Validates JSON against a schema from a file.
     *
     * @param jsonContent the JSON content to validate
     * @param schemaFile  the schema file
     * @return validation result
     */
    public ValidationResult validate(String jsonContent, File schemaFile) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return new ValidationResult(false, List.of(new ValidationError("JSON content is empty", null, -1)));
        }

        if (schemaFile == null || !schemaFile.exists()) {
            return new ValidationResult(false, List.of(new ValidationError("Schema file not found", null, -1)));
        }

        try {
            JsonSchema schema = schemaProvider.loadSchema(schemaFile);
            return validateWithSchema(jsonContent, schema);
        } catch (IOException e) {
            logger.error("Failed to load schema: {}", schemaFile, e);
            return new ValidationResult(false, List.of(new ValidationError("Failed to load schema: " + e.getMessage(), null, -1)));
        }
    }

    /**
     * Validates JSON against a schema from a string.
     *
     * @param jsonContent the JSON content to validate
     * @param schemaJson  the schema as JSON string
     * @return validation result
     */
    public ValidationResult validate(String jsonContent, String schemaJson) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return new ValidationResult(false, List.of(new ValidationError("JSON content is empty", null, -1)));
        }

        if (schemaJson == null || schemaJson.isBlank()) {
            return new ValidationResult(false, List.of(new ValidationError("Schema is empty", null, -1)));
        }

        try {
            JsonSchema schema = schemaProvider.loadSchemaFromString(schemaJson);
            return validateWithSchema(jsonContent, schema);
        } catch (Exception e) {
            logger.error("Failed to parse schema", e);
            return new ValidationResult(false, List.of(new ValidationError("Failed to parse schema: " + e.getMessage(), null, -1)));
        }
    }

    /**
     * Validates JSON content with a compiled schema.
     */
    private ValidationResult validateWithSchema(String jsonContent, JsonSchema schema) {
        List<ValidationError> errors = new ArrayList<>();

        try {
            Set<ValidationMessage> messages = schema.validate(jsonContent, InputFormat.JSON);

            if (messages.isEmpty()) {
                logger.debug("JSON validated successfully");
                return new ValidationResult(true, Collections.emptyList());
            }

            for (ValidationMessage message : messages) {
                String path = message.getInstanceLocation() != null ?
                        message.getInstanceLocation().toString() : "";
                String errorMessage = message.getMessage();

                // Try to estimate line number from path
                int lineNumber = estimateLineNumber(jsonContent, path);

                errors.add(new ValidationError(errorMessage, path, lineNumber));
            }

            logger.debug("JSON validation found {} errors", errors.size());
            return new ValidationResult(false, errors);

        } catch (Exception e) {
            logger.error("Validation failed", e);
            return new ValidationResult(false, List.of(new ValidationError("Validation error: " + e.getMessage(), null, -1)));
        }
    }

    /**
     * Estimates the line number of an error based on JSON path.
     * This is a heuristic and may not be accurate for complex documents.
     */
    private int estimateLineNumber(String json, String path) {
        if (path == null || path.isEmpty() || path.equals("$")) {
            return 1;
        }

        // Extract the key from the path
        String key = extractKeyFromPath(path);
        if (key == null) {
            return -1;
        }

        // Search for the key in the JSON
        String searchPattern = "\"" + key + "\"";
        int index = json.indexOf(searchPattern);
        if (index == -1) {
            return -1;
        }

        // Count lines up to this point
        int lineNumber = 1;
        for (int i = 0; i < index; i++) {
            if (json.charAt(i) == '\n') {
                lineNumber++;
            }
        }

        return lineNumber;
    }

    /**
     * Extracts the last key or index from a JSON path.
     */
    private String extractKeyFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Handle array index paths like $.items[0]
        int lastBracket = path.lastIndexOf('[');
        if (lastBracket > 0) {
            // Return the key before the bracket
            int lastDot = path.lastIndexOf('.', lastBracket);
            if (lastDot >= 0) {
                return path.substring(lastDot + 1, lastBracket);
            }
        }

        // Handle property paths like $.user.name
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            return path.substring(lastDot + 1);
        }

        // Handle paths without dots
        if (path.startsWith("$")) {
            return path.substring(1);
        }

        return path;
    }

    /**
     * Clears the schema cache.
     */
    public void clearCache() {
        schemaProvider.clearCache();
    }

    // ==================== Result Classes ====================

    /**
     * Result of a validation operation.
     * @param valid Whether the validation was successful
     * @param errors List of validation errors
     */
    public record ValidationResult(boolean valid, List<ValidationError> errors) {

        /**
         * Gets a summary message describing the validation outcome.
         *
         * @return a human-readable summary message
         */
        public String getSummary() {
            if (valid) {
                return "Validation successful";
            }
            return "Validation failed with " + errors.size() + " error(s)";
        }

        /**
         * Gets the validation errors as formatted strings.
         *
         * @return a list of error messages formatted for display
         */
        public List<String> getErrorMessages() {
            return errors.stream()
                    .map(ValidationError::toString)
                    .toList();
        }
    }

    /**
     * A single validation error.
     * @param message The error message
     * @param path The JSON path to the error
     * @param lineNumber The estimated line number
     */
    public record ValidationError(String message, String path, int lineNumber) {

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (lineNumber > 0) {
                sb.append("Line ").append(lineNumber).append(": ");
            }
            sb.append(message);
            if (path != null && !path.isEmpty() && !path.equals("$")) {
                sb.append(" (at ").append(path).append(")");
            }
            return sb.toString();
        }
    }
}
