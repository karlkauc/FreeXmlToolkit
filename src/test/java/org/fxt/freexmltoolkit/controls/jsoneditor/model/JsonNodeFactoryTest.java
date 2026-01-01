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

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonNodeFactory")
class JsonNodeFactoryTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("parse(String)")
    class ParseStringTests {

        @Test
        @DisplayName("Parses empty string to empty document")
        void parsesEmptyString() {
            JsonDocument doc = JsonNodeFactory.parse("");
            assertNotNull(doc);
        }

        @Test
        @DisplayName("Parses null to empty document")
        void parsesNull() {
            JsonDocument doc = JsonNodeFactory.parse((String) null);
            assertNotNull(doc);
        }

        @Test
        @DisplayName("Parses simple JSON object")
        void parsesSimpleObject() {
            String json = "{\"name\": \"test\", \"count\": 42}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc);
            assertNotNull(doc.getRootValue());
            assertEquals(JsonNodeType.OBJECT, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Parses JSON array")
        void parsesArray() {
            String json = "[1, 2, 3, \"text\", true]";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc);
            assertNotNull(doc.getRootValue());
            assertEquals(JsonNodeType.ARRAY, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Parses nested JSON")
        void parsesNestedJson() {
            String json = "{\"person\": {\"name\": \"John\", \"address\": {\"city\": \"NYC\"}}}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc);
            assertEquals(JsonNodeType.OBJECT, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Throws exception for invalid JSON")
        void throwsForInvalidJson() {
            assertThrows(JsonSyntaxException.class, () ->
                JsonNodeFactory.parse("{invalid json}"));
        }
    }

    @Nested
    @DisplayName("JSONC Parsing")
    class JsoncParsingTests {

        @Test
        @DisplayName("Parses JSONC with line comments")
        void parsesLineComments() {
            String jsonc = "{\n  // This is a comment\n  \"name\": \"test\"\n}";
            JsonDocument doc = JsonNodeFactory.parse(jsonc);

            assertNotNull(doc);
            assertEquals("jsonc", doc.getFormat());
        }

        @Test
        @DisplayName("Parses JSONC with block comments")
        void parsesBlockComments() {
            String jsonc = "{\n  /* Block comment */\n  \"name\": \"test\"\n}";
            JsonDocument doc = JsonNodeFactory.parse(jsonc);

            assertNotNull(doc);
            assertEquals("jsonc", doc.getFormat());
        }

        @Test
        @DisplayName("Parses JSONC with multiple comments")
        void parsesMultipleComments() {
            String jsonc = """
                {
                  // First comment
                  "name": "test",
                  /* Second comment */
                  "value": 42
                }
                """;
            JsonDocument doc = JsonNodeFactory.parse(jsonc);

            assertNotNull(doc);
            assertEquals("jsonc", doc.getFormat());
        }
    }

    @Nested
    @DisplayName("JSON5 Parsing")
    class Json5ParsingTests {

        @Test
        @DisplayName("Parses JSON5 with trailing commas")
        void parsesTrailingCommas() {
            String json5 = "{\"name\": \"test\",}";
            JsonDocument doc = JsonNodeFactory.parse(json5);

            assertNotNull(doc);
            assertEquals("json5", doc.getFormat());
        }

        @Test
        @DisplayName("Parses JSON5 array with trailing commas")
        void parsesArrayTrailingCommas() {
            String json5 = "[1, 2, 3,]";
            JsonDocument doc = JsonNodeFactory.parse(json5);

            assertNotNull(doc);
            assertEquals("json5", doc.getFormat());
        }
    }

    @Nested
    @DisplayName("parse(File)")
    class ParseFileTests {

        @Test
        @DisplayName("Parses JSON file")
        void parsesJsonFile() throws IOException {
            Path file = tempDir.resolve("test.json");
            Files.writeString(file, "{\"name\": \"test\"}");

            JsonDocument doc = JsonNodeFactory.parse(file.toFile());

            assertNotNull(doc);
            assertEquals(file.toFile(), doc.getSourceFile());
        }

        @Test
        @DisplayName("Parses JSONC file")
        void parsesJsoncFile() throws IOException {
            Path file = tempDir.resolve("test.jsonc");
            Files.writeString(file, "{\n  // comment\n  \"name\": \"test\"\n}");

            JsonDocument doc = JsonNodeFactory.parse(file.toFile());

            assertNotNull(doc);
            assertEquals("jsonc", doc.getFormat());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("Creates empty object")
        void createsEmptyObject() {
            JsonObject obj = JsonNodeFactory.createObject();
            assertNotNull(obj);
            assertEquals(JsonNodeType.OBJECT, obj.getNodeType());
        }

        @Test
        @DisplayName("Creates empty array")
        void createsEmptyArray() {
            JsonArray arr = JsonNodeFactory.createArray();
            assertNotNull(arr);
            assertEquals(JsonNodeType.ARRAY, arr.getNodeType());
        }

        @Test
        @DisplayName("Creates string primitive")
        void createsString() {
            JsonPrimitive str = JsonNodeFactory.createString("test");
            assertNotNull(str);
            assertEquals("test", str.getAsString());
        }

        @Test
        @DisplayName("Creates number primitive")
        void createsNumber() {
            JsonPrimitive num = JsonNodeFactory.createNumber(42);
            assertNotNull(num);
            assertEquals(42, num.getAsInt());
        }

        @Test
        @DisplayName("Creates boolean primitive")
        void createsBoolean() {
            JsonPrimitive bool = JsonNodeFactory.createBoolean(true);
            assertNotNull(bool);
            assertTrue(bool.getAsBoolean());
        }

        @Test
        @DisplayName("Creates null primitive")
        void createsNull() {
            JsonPrimitive nullVal = JsonNodeFactory.createNull();
            assertNotNull(nullVal);
            assertTrue(nullVal.isNull());
        }

        @Test
        @DisplayName("Creates empty document")
        void createsEmptyDocument() {
            JsonDocument doc = JsonNodeFactory.createEmptyDocument();
            assertNotNull(doc);
            assertNotNull(doc.getRootValue());
            assertEquals(JsonNodeType.OBJECT, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Creates simple document from key-value pairs")
        void createsSimpleDocument() {
            JsonDocument doc = JsonNodeFactory.createSimpleDocument(
                "name", "test",
                "version", "1.0"
            );
            assertNotNull(doc);
            assertNotNull(doc.getRootValue());
        }
    }

    @Nested
    @DisplayName("Primitive Type Handling")
    class PrimitiveTypeTests {

        @Test
        @DisplayName("Parses integer numbers correctly")
        void parsesIntegers() {
            String json = "{\"value\": 42}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            JsonObject root = (JsonObject) doc.getRootValue();
            JsonNode value = root.getChildren().get(0);

            assertEquals(JsonNodeType.NUMBER, value.getNodeType());
        }

        @Test
        @DisplayName("Parses floating point numbers correctly")
        void parsesFloats() {
            String json = "{\"value\": 3.14159}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }

        @Test
        @DisplayName("Parses boolean values correctly")
        void parsesBooleans() {
            String json = "{\"active\": true, \"deleted\": false}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }

        @Test
        @DisplayName("Parses null values correctly")
        void parsesNulls() {
            String json = "{\"value\": null}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }

        @Test
        @DisplayName("Parses string values correctly")
        void parsesStrings() {
            String json = "{\"text\": \"Hello, World!\"}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }
    }

    @Nested
    @DisplayName("Complex JSON Structures")
    class ComplexStructureTests {

        @Test
        @DisplayName("Parses deeply nested objects")
        void parsesDeeplyNested() {
            String json = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"deep\"}}}}}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }

        @Test
        @DisplayName("Parses array of objects")
        void parsesArrayOfObjects() {
            String json = "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertEquals(JsonNodeType.ARRAY, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Parses mixed content")
        void parsesMixedContent() {
            String json = """
                {
                  "string": "text",
                  "number": 42,
                  "boolean": true,
                  "null": null,
                  "array": [1, 2, 3],
                  "object": {"nested": "value"}
                }
                """;
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertNotNull(doc.getRootValue());
        }

        @Test
        @DisplayName("Parses empty object")
        void parsesEmptyObject() {
            String json = "{}";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertEquals(JsonNodeType.OBJECT, doc.getRootValue().getNodeType());
        }

        @Test
        @DisplayName("Parses empty array")
        void parsesEmptyArray() {
            String json = "[]";
            JsonDocument doc = JsonNodeFactory.parse(json);

            assertEquals(JsonNodeType.ARRAY, doc.getRootValue().getNodeType());
        }
    }
}
