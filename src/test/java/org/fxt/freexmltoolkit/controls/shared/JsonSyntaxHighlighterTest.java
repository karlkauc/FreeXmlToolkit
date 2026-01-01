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

package org.fxt.freexmltoolkit.controls.shared;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonSyntaxHighlighter")
class JsonSyntaxHighlighterTest {

    @Nested
    @DisplayName("computeHighlighting")
    class ComputeHighlightingTests {

        @Test
        @DisplayName("Returns empty spans for null input")
        void returnsEmptyForNull() {
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(null);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Returns empty spans for empty string")
        void returnsEmptyForEmptyString() {
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting("");
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights simple JSON object")
        void highlightsSimpleJsonObject() {
            String json = "{\"name\": \"value\"}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
            assertTrue(spans.length() > 0);
        }

        @Test
        @DisplayName("Highlights JSON with numbers")
        void highlightsJsonWithNumbers() {
            String json = "{\"count\": 42, \"price\": 19.99}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights JSON with booleans")
        void highlightsJsonWithBooleans() {
            String json = "{\"active\": true, \"deleted\": false}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights JSON with null")
        void highlightsJsonWithNull() {
            String json = "{\"value\": null}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights JSON array")
        void highlightsJsonArray() {
            String json = "[1, 2, 3, \"text\", true]";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }
    }

    @Nested
    @DisplayName("JSONC Support")
    class JsoncSupportTests {

        @Test
        @DisplayName("Highlights line comments")
        void highlightsLineComments() {
            String jsonc = "{\n  // This is a comment\n  \"key\": \"value\"\n}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(jsonc);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights block comments")
        void highlightsBlockComments() {
            String jsonc = "{\n  /* Block comment */\n  \"key\": \"value\"\n}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(jsonc);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights multi-line block comments")
        void highlightsMultiLineBlockComments() {
            String jsonc = "{\n  /*\n   * Multi-line\n   * comment\n   */\n  \"key\": \"value\"\n}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(jsonc);
            assertNotNull(spans);
        }
    }

    @Nested
    @DisplayName("JSON5 Support")
    class Json5SupportTests {

        @Test
        @DisplayName("Highlights unquoted keys")
        void highlightsUnquotedKeys() {
            String json5 = "{name: \"value\", count: 42}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json5);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights single-quoted strings")
        void highlightsSingleQuotedStrings() {
            String json5 = "{'name': 'value'}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json5);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights hex numbers")
        void highlightsHexNumbers() {
            String json5 = "{\"value\": 0xFF, \"other\": 0xDEADBEEF}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json5);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights Infinity")
        void highlightsInfinity() {
            String json5 = "{\"max\": Infinity, \"min\": -Infinity}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json5);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Highlights NaN")
        void highlightsNaN() {
            String json5 = "{\"invalid\": NaN}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json5);
            assertNotNull(spans);
        }
    }

    @Nested
    @DisplayName("detectFormat")
    class DetectFormatTests {

        @Test
        @DisplayName("Detects standard JSON")
        void detectsStandardJson() {
            String json = "{\"name\": \"value\", \"count\": 42}";
            assertEquals("json", JsonSyntaxHighlighter.detectFormat(json));
        }

        @Test
        @DisplayName("Returns json for null input")
        void returnsJsonForNull() {
            assertEquals("json", JsonSyntaxHighlighter.detectFormat(null));
        }

        @Test
        @DisplayName("Returns json for empty input")
        void returnsJsonForEmpty() {
            assertEquals("json", JsonSyntaxHighlighter.detectFormat(""));
        }

        @Test
        @DisplayName("Detects JSONC with line comments")
        void detectsJsoncWithLineComments() {
            String jsonc = "{\n  // comment\n  \"key\": \"value\"\n}";
            assertEquals("jsonc", JsonSyntaxHighlighter.detectFormat(jsonc));
        }

        @Test
        @DisplayName("Detects JSONC with block comments")
        void detectsJsoncWithBlockComments() {
            String jsonc = "{\n  /* comment */\n  \"key\": \"value\"\n}";
            assertEquals("jsonc", JsonSyntaxHighlighter.detectFormat(jsonc));
        }

        @Test
        @DisplayName("Detects JSON5 with unquoted keys")
        void detectsJson5WithUnquotedKeys() {
            String json5 = "{name: \"value\"}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }

        @Test
        @DisplayName("Detects JSON5 with single quotes")
        void detectsJson5WithSingleQuotes() {
            String json5 = "{'name': 'value'}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }

        @Test
        @DisplayName("Detects JSON5 with trailing commas")
        void detectsJson5WithTrailingCommas() {
            String json5 = "{\"name\": \"value\",}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }

        @Test
        @DisplayName("Detects JSON5 with Infinity")
        void detectsJson5WithInfinity() {
            String json5 = "{\"max\": Infinity}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }

        @Test
        @DisplayName("Detects JSON5 with NaN")
        void detectsJson5WithNaN() {
            String json5 = "{\"value\": NaN}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }

        @Test
        @DisplayName("Detects JSON5 with hex numbers")
        void detectsJson5WithHexNumbers() {
            String json5 = "{\"color\": 0xFF0000}";
            assertEquals("json5", JsonSyntaxHighlighter.detectFormat(json5));
        }
    }

    @Nested
    @DisplayName("stripComments")
    class StripCommentsTests {

        @Test
        @DisplayName("Returns null for null input")
        void returnsNullForNull() {
            assertNull(JsonSyntaxHighlighter.stripComments(null));
        }

        @Test
        @DisplayName("Returns unchanged for JSON without comments")
        void returnsUnchangedForJsonWithoutComments() {
            String json = "{\"name\": \"value\"}";
            assertEquals(json, JsonSyntaxHighlighter.stripComments(json));
        }

        @Test
        @DisplayName("Strips line comments")
        void stripsLineComments() {
            String jsonc = "{\"name\": \"value\"} // comment";
            String result = JsonSyntaxHighlighter.stripComments(jsonc);
            assertFalse(result.contains("//"));
            assertTrue(result.contains("\"name\""));
        }

        @Test
        @DisplayName("Strips block comments")
        void stripsBlockComments() {
            String jsonc = "{/* comment */\"name\": \"value\"}";
            String result = JsonSyntaxHighlighter.stripComments(jsonc);
            assertFalse(result.contains("/*"));
            assertTrue(result.contains("\"name\""));
        }

        @Test
        @DisplayName("Preserves strings containing comment-like patterns")
        void preservesStringsWithCommentPatterns() {
            String json = "{\"url\": \"http://example.com\"}";
            String result = JsonSyntaxHighlighter.stripComments(json);
            assertTrue(result.contains("http://example.com"));
        }

        @Test
        @DisplayName("Handles multiple line comments")
        void handlesMultipleLineComments() {
            String jsonc = "{\n// comment 1\n\"a\": 1,\n// comment 2\n\"b\": 2\n}";
            String result = JsonSyntaxHighlighter.stripComments(jsonc);
            assertFalse(result.contains("comment 1"));
            assertFalse(result.contains("comment 2"));
            assertTrue(result.contains("\"a\""));
            assertTrue(result.contains("\"b\""));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles deeply nested JSON")
        void handlesDeeplyNestedJson() {
            String json = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"value\"}}}}}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Handles escaped characters in strings")
        void handlesEscapedCharacters() {
            String json = "{\"text\": \"Line1\\nLine2\\tTabbed\"}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @Test
        @DisplayName("Handles unicode in strings")
        void handlesUnicode() {
            String json = "{\"emoji\": \"Hello \\u263A\", \"text\": \"日本語\"}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "1e10", "1E10", "1.5e-10", "1.5E+10", "-1e10"
        })
        @DisplayName("Handles scientific notation numbers")
        void handlesScientificNotation(String number) {
            String json = "{\"value\": " + number + "}";
            StyleSpans<Collection<String>> spans = JsonSyntaxHighlighter.computeHighlighting(json);
            assertNotNull(spans);
        }
    }
}
