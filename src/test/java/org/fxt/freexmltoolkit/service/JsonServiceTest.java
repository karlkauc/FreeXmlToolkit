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

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonService")
class JsonServiceTest {

    private JsonService jsonService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        jsonService = new JsonService();
    }

    @Nested
    @DisplayName("parseJson")
    class ParseJsonTests {

        @Test
        @DisplayName("Parses valid JSON object")
        void parsesValidJsonObject() {
            String json = "{\"name\": \"test\", \"count\": 42}";
            JsonElement element = jsonService.parseJson(json);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
            assertEquals("test", element.getAsJsonObject().get("name").getAsString());
            assertEquals(42, element.getAsJsonObject().get("count").getAsInt());
        }

        @Test
        @DisplayName("Parses valid JSON array")
        void parsesValidJsonArray() {
            String json = "[1, 2, 3, \"text\"]";
            JsonElement element = jsonService.parseJson(json);
            assertNotNull(element);
            assertTrue(element.isJsonArray());
            assertEquals(4, element.getAsJsonArray().size());
        }

        @Test
        @DisplayName("Returns JsonNull for null input")
        void returnsNullForNullInput() {
            JsonElement element = jsonService.parseJson(null);
            assertTrue(element.isJsonNull());
        }

        @Test
        @DisplayName("Returns JsonNull for blank input")
        void returnsNullForBlankInput() {
            JsonElement element = jsonService.parseJson("   ");
            assertTrue(element.isJsonNull());
        }

        @Test
        @DisplayName("Throws exception for invalid JSON")
        void throwsExceptionForInvalidJson() {
            assertThrows(JsonSyntaxException.class, () ->
                jsonService.parseJson("{invalid json}"));
        }
    }

    @Nested
    @DisplayName("parseJsonc")
    class ParseJsoncTests {

        @Test
        @DisplayName("Parses JSONC with line comments")
        void parsesJsoncWithLineComments() {
            String jsonc = "{\n  // This is a comment\n  \"name\": \"test\"\n}";
            JsonElement element = jsonService.parseJsonc(jsonc);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
            assertEquals("test", element.getAsJsonObject().get("name").getAsString());
        }

        @Test
        @DisplayName("Parses JSONC with block comments")
        void parsesJsoncWithBlockComments() {
            String jsonc = "{\n  /* Block comment */\n  \"name\": \"test\"\n}";
            JsonElement element = jsonService.parseJsonc(jsonc);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Returns JsonNull for null input")
        void returnsNullForNullInput() {
            JsonElement element = jsonService.parseJsonc(null);
            assertTrue(element.isJsonNull());
        }
    }

    @Nested
    @DisplayName("parseJson5")
    class ParseJson5Tests {

        @Test
        @DisplayName("Parses JSON5 with trailing commas")
        void parsesJson5WithTrailingCommas() {
            String json5 = "{\"name\": \"test\",}";
            JsonElement element = jsonService.parseJson5(json5);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Parses JSON5 with comments")
        void parsesJson5WithComments() {
            String json5 = "{\n  // comment\n  \"name\": \"test\"\n}";
            JsonElement element = jsonService.parseJson5(json5);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Returns JsonNull for null input")
        void returnsNullForNullInput() {
            JsonElement element = jsonService.parseJson5(null);
            assertTrue(element.isJsonNull());
        }
    }

    @Nested
    @DisplayName("parseAuto")
    class ParseAutoTests {

        @Test
        @DisplayName("Auto-parses standard JSON")
        void autoParsesStandardJson() {
            String json = "{\"name\": \"test\"}";
            JsonElement element = jsonService.parseAuto(json);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Auto-parses JSONC")
        void autoParsesJsonc() {
            String jsonc = "{\n  // comment\n  \"name\": \"test\"\n}";
            JsonElement element = jsonService.parseAuto(jsonc);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Auto-parses JSON5")
        void autoParsesJson5() {
            String json5 = "{\"name\": \"test\",}";
            JsonElement element = jsonService.parseAuto(json5);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }
    }

    @Nested
    @DisplayName("formatJson")
    class FormatJsonTests {

        @Test
        @DisplayName("Formats compact JSON")
        void formatsCompactJson() {
            String json = "{\"name\":\"test\",\"count\":42}";
            String formatted = jsonService.formatJson(json);
            assertNotNull(formatted);
            assertTrue(formatted.contains("\n"));
        }

        @Test
        @DisplayName("Returns null for null input")
        void returnsNullForNullInput() {
            assertNull(jsonService.formatJson(null));
        }

        @Test
        @DisplayName("Returns blank for blank input")
        void returnsBlankForBlankInput() {
            assertEquals("   ", jsonService.formatJson("   "));
        }

        @Test
        @DisplayName("Formats with custom indent")
        void formatsWithCustomIndent() {
            String json = "{\"name\":\"test\"}";
            String formatted = jsonService.formatJson(json, 4);
            assertNotNull(formatted);
            assertTrue(formatted.contains("\n"));
        }
    }

    @Nested
    @DisplayName("compactJson")
    class CompactJsonTests {

        @Test
        @DisplayName("Compacts formatted JSON")
        void compactsFormattedJson() {
            String json = "{\n  \"name\": \"test\",\n  \"count\": 42\n}";
            String compacted = jsonService.compactJson(json);
            assertNotNull(compacted);
            assertFalse(compacted.contains("\n"));
        }

        @Test
        @DisplayName("Returns null for null input")
        void returnsNullForNullInput() {
            assertNull(jsonService.compactJson(null));
        }
    }

    @Nested
    @DisplayName("isValidJson")
    class IsValidJsonTests {

        @Test
        @DisplayName("Returns true for valid JSON")
        void returnsTrueForValidJson() {
            assertTrue(jsonService.isValidJson("{\"name\": \"test\"}"));
        }

        @Test
        @DisplayName("Returns true for valid JSON array")
        void returnsTrueForValidJsonArray() {
            assertTrue(jsonService.isValidJson("[1, 2, 3]"));
        }

        @Test
        @DisplayName("Returns false for invalid JSON")
        void returnsFalseForInvalidJson() {
            assertFalse(jsonService.isValidJson("{invalid}"));
        }

        @Test
        @DisplayName("Returns false for null")
        void returnsFalseForNull() {
            assertFalse(jsonService.isValidJson(null));
        }

        @Test
        @DisplayName("Returns false for blank")
        void returnsFalseForBlank() {
            assertFalse(jsonService.isValidJson("   "));
        }
    }

    @Nested
    @DisplayName("validateJson")
    class ValidateJsonTests {

        @Test
        @DisplayName("Returns null for valid JSON")
        void returnsNullForValidJson() {
            assertNull(jsonService.validateJson("{\"name\": \"test\"}"));
        }

        @Test
        @DisplayName("Returns error for invalid JSON")
        void returnsErrorForInvalidJson() {
            String error = jsonService.validateJson("{invalid}");
            assertNotNull(error);
            assertTrue(error.contains("syntax error"));
        }

        @Test
        @DisplayName("Returns error for empty content")
        void returnsErrorForEmptyContent() {
            String error = jsonService.validateJson("");
            assertEquals("JSON content is empty", error);
        }
    }

    @Nested
    @DisplayName("validateAgainstSchema")
    class ValidateAgainstSchemaTests {

        @Test
        @DisplayName("Returns empty list for valid JSON matching schema")
        void returnsEmptyListForValidJson() {
            String json = "{\"name\": \"test\"}";
            String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """;
            List<String> errors = jsonService.validateAgainstSchema(json, schema);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Returns errors for invalid JSON")
        void returnsErrorsForInvalidJson() {
            String json = "{\"name\": 123}";
            String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """;
            List<String> errors = jsonService.validateAgainstSchema(json, schema);
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Returns error for empty JSON")
        void returnsErrorForEmptyJson() {
            List<String> errors = jsonService.validateAgainstSchema("", "{}");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("empty"));
        }

        @Test
        @DisplayName("Returns error for empty schema")
        void returnsErrorForEmptySchema() {
            List<String> errors = jsonService.validateAgainstSchema("{}", "");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("empty"));
        }
    }

    @Nested
    @DisplayName("executeJsonPath")
    class ExecuteJsonPathTests {

        @Test
        @DisplayName("Executes simple JSONPath")
        void executesSimpleJsonPath() {
            String json = "{\"name\": \"test\", \"items\": [1, 2, 3]}";
            Object result = jsonService.executeJsonPath(json, "$.name");
            assertNotNull(result);
        }

        @Test
        @DisplayName("Executes array JSONPath")
        void executesArrayJsonPath() {
            String json = "{\"items\": [1, 2, 3, 4, 5]}";
            Object result = jsonService.executeJsonPath(json, "$.items[*]");
            assertNotNull(result);
        }

        @Test
        @DisplayName("Returns formatted string result")
        void returnsFormattedStringResult() {
            String json = "{\"name\": \"test\"}";
            String result = jsonService.executeJsonPathAsString(json, "$.name");
            assertNotNull(result);
        }

        @Test
        @DisplayName("Returns error message for invalid path")
        void returnsErrorMessageForInvalidPath() {
            String json = "{\"name\": \"test\"}";
            String result = jsonService.executeJsonPathAsString(json, "$.invalid");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("isValidJsonPath")
    class IsValidJsonPathTests {

        @Test
        @DisplayName("Returns true for valid JSONPath")
        void returnsTrueForValidPath() {
            assertTrue(jsonService.isValidJsonPath("$.name"));
            assertTrue(jsonService.isValidJsonPath("$.items[*]"));
            assertTrue(jsonService.isValidJsonPath("$..name"));
        }

        @Test
        @DisplayName("Returns false for invalid JSONPath")
        void returnsFalseForInvalidPath() {
            assertFalse(jsonService.isValidJsonPath("[invalid"));
        }
    }

    @Nested
    @DisplayName("File Operations")
    class FileOperationsTests {

        @Test
        @DisplayName("Reads JSON file")
        void readsJsonFile() throws IOException {
            Path file = tempDir.resolve("test.json");
            Files.writeString(file, "{\"name\": \"test\"}");

            JsonElement element = jsonService.readFile(file);
            assertNotNull(element);
            assertTrue(element.isJsonObject());
        }

        @Test
        @DisplayName("Writes JSON file")
        void writesJsonFile() throws IOException {
            Path file = tempDir.resolve("output.json");
            JsonElement element = jsonService.parseJson("{\"name\": \"test\"}");

            jsonService.writeFile(element, file);

            assertTrue(Files.exists(file));
            String content = Files.readString(file);
            assertTrue(content.contains("\"name\""));
        }

        @Test
        @DisplayName("Writes formatted JSON string to file")
        void writesFormattedJsonString() throws IOException {
            Path file = tempDir.resolve("formatted.json");
            String json = "{\"name\":\"test\"}";

            jsonService.writeFile(json, file, true);

            assertTrue(Files.exists(file));
            String content = Files.readString(file);
            assertTrue(content.contains("\n")); // Formatted output has newlines
        }
    }

    @Nested
    @DisplayName("detectFormat")
    class DetectFormatTests {

        @Test
        @DisplayName("Detects standard JSON")
        void detectsStandardJson() {
            assertEquals("json", jsonService.detectFormat("{\"name\": \"test\"}"));
        }

        @Test
        @DisplayName("Detects JSONC")
        void detectsJsonc() {
            assertEquals("jsonc", jsonService.detectFormat("{\n// comment\n\"name\": \"test\"\n}"));
        }

        @Test
        @DisplayName("Detects JSON5")
        void detectsJson5() {
            assertEquals("json5", jsonService.detectFormat("{name: \"test\"}"));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {

        @Test
        @DisplayName("Gets JSON type for object")
        void getsTypeForObject() {
            JsonElement element = jsonService.parseJson("{\"name\": \"test\"}");
            assertEquals("object", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Gets JSON type for array")
        void getsTypeForArray() {
            JsonElement element = jsonService.parseJson("[1, 2, 3]");
            assertEquals("array", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Gets JSON type for string")
        void getsTypeForString() {
            JsonElement element = jsonService.parseJson("\"text\"");
            assertEquals("string", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Gets JSON type for number")
        void getsTypeForNumber() {
            JsonElement element = jsonService.parseJson("42");
            assertEquals("number", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Gets JSON type for boolean")
        void getsTypeForBoolean() {
            JsonElement element = jsonService.parseJson("true");
            assertEquals("boolean", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Gets JSON type for null")
        void getsTypeForNull() {
            JsonElement element = jsonService.parseJson("null");
            assertEquals("null", jsonService.getJsonType(element));
        }

        @Test
        @DisplayName("Counts children for object")
        void countsChildrenForObject() {
            JsonElement element = jsonService.parseJson("{\"a\": 1, \"b\": 2, \"c\": 3}");
            assertEquals(3, jsonService.countChildren(element));
        }

        @Test
        @DisplayName("Counts children for array")
        void countsChildrenForArray() {
            JsonElement element = jsonService.parseJson("[1, 2, 3, 4, 5]");
            assertEquals(5, jsonService.countChildren(element));
        }

        @Test
        @DisplayName("Converts to native object")
        void convertsToNativeObject() {
            JsonElement element = jsonService.parseJson("{\"name\": \"test\", \"count\": 42}");
            Object result = jsonService.toNativeObject(element);
            assertNotNull(result);
            assertTrue(result instanceof java.util.Map);
        }
    }
}
