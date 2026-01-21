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

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utility class for calculating JSONPath at a given position in JSON text.
 * Provides hover information including path, type, and value.
 */
public class JsonPathCalculator {

    private static final Logger logger = LogManager.getLogger(JsonPathCalculator.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JsonPathCalculator() {
        // Utility class
    }

    /**
     * Result of JSONPath calculation containing path and metadata.
     * @param jsonPath The calculated JSON path
     * @param valueType The type of the value
     * @param value The value content
     * @param key The property key name
     * @param depth The nesting depth
     */
    public record JsonHoverInfo(
            String jsonPath,
            String valueType,
            String value,
            String key,
            int depth
    ) {
        /**
         * Checks if this hover info contains valid path information.
         *
         * @return true if the JSON path is not null and not empty
         */
        public boolean isValid() {
            return jsonPath != null && !jsonPath.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("JSONPath: ").append(jsonPath);
            if (key != null && !key.isEmpty()) {
                sb.append("\nKey: ").append(key);
            }
            sb.append("\nType: ").append(valueType);
            if (value != null && !value.isEmpty()) {
                String displayValue = value.length() > 50 ? value.substring(0, 47) + "..." : value;
                sb.append("\nValue: ").append(displayValue);
            }
            return sb.toString();
        }
    }

    /**
     * Calculates the JSONPath for a given character position in JSON text.
     *
     * @param jsonText The JSON text
     * @param position The character position (0-based)
     * @return JsonHoverInfo containing path and metadata, or null if invalid
     */
    public static JsonHoverInfo calculatePath(String jsonText, int position) {
        if (jsonText == null || position < 0 || position >= jsonText.length()) {
            return null;
        }

        try {
            return parseAndFindPath(jsonText, position);
        } catch (Exception e) {
            logger.trace("Error calculating JSONPath: {}", e.getMessage());
            return null;
        }
    }

    private static JsonHoverInfo parseAndFindPath(String text, int targetPosition) {
        Deque<PathElement> pathStack = new ArrayDeque<>();
        pathStack.push(new PathElement("$", ElementType.ROOT, -1));

        int i = 0;
        int length = text.length();
        String currentKey = null;
        int arrayIndex = -1;
        int lastKeyStart = -1;
        int lastKeyEnd = -1;
        int lastValueStart = -1;
        String lastValueType = "unknown";
        StringBuilder currentValue = new StringBuilder();
        boolean collectingValue = false;

        while (i < length) {
            char c = text.charAt(i);

            // Handle position check
            if (i == targetPosition) {
                // We're at the target position, build the path
                return buildHoverInfo(pathStack, currentKey, lastValueType,
                        currentValue.toString().trim(), arrayIndex, i >= lastKeyStart && i <= lastKeyEnd);
            }

            switch (c) {
                case '"' -> {
                    int stringEnd = findStringEnd(text, i);
                    if (stringEnd < 0) {
                        // Unterminated string - stop parsing
                        return buildHoverInfo(pathStack, currentKey, lastValueType,
                                currentValue.toString().trim(), arrayIndex, false);
                    }

                    // Extract the string content (without quotes)
                    String stringContent = extractString(text, i);

                    // Determine if this is a property key: "..."\s*:
                    int afterString = findNextNonWhitespace(text, stringEnd + 1);
                    boolean isKey = afterString < length && text.charAt(afterString) == ':';

                    if (isKey) {
                        lastKeyStart = i;
                        lastKeyEnd = stringEnd;
                        currentKey = stringContent;

                        if (targetPosition >= lastKeyStart && targetPosition <= lastKeyEnd) {
                            return buildHoverInfo(pathStack, currentKey, "property",
                                    null, arrayIndex, true);
                        }
                    } else {
                        // String value
                        lastValueStart = i;
                        lastValueType = "string";
                        collectingValue = false;
                        currentValue.setLength(0);

                        if (targetPosition >= lastValueStart && targetPosition <= stringEnd) {
                            return buildHoverInfo(pathStack, currentKey, "string",
                                    stringContent, arrayIndex, false);
                        }
                    }

                    // Jump to end quote; loop will i++ afterwards
                    i = stringEnd;
                }
                case ':' -> {
                    // After colon comes the value
                    lastValueStart = findNextNonWhitespace(text, i + 1);
                    if (lastValueStart < length) {
                        char valueStart = text.charAt(lastValueStart);
                        lastValueType = detectValueType(text, lastValueStart);
                        if (valueStart != '"' && valueStart != '{' && valueStart != '[') {
                            currentValue.setLength(0);
                            collectingValue = true;
                        } else {
                            collectingValue = false;
                            currentValue.setLength(0);
                        }
                    }
                }
                case '{' -> {
                    if (currentKey != null) {
                        pathStack.push(new PathElement(currentKey, ElementType.OBJECT, -1));
                        currentKey = null;
                    } else if (!pathStack.isEmpty() && pathStack.peek().type == ElementType.ARRAY) {
                        pathStack.push(new PathElement("", ElementType.OBJECT, arrayIndex));
                    }
                    lastValueType = "object";
                    collectingValue = false;
                }
                case '}' -> {
                    if (!pathStack.isEmpty() && pathStack.peek().type != ElementType.ROOT) {
                        pathStack.pop();
                    }
                    collectingValue = false;
                }
                case '[' -> {
                    if (currentKey != null) {
                        pathStack.push(new PathElement(currentKey, ElementType.ARRAY, -1));
                        currentKey = null;
                    }
                    arrayIndex = 0;
                    lastValueType = "array";
                    collectingValue = false;
                }
                case ']' -> {
                    if (!pathStack.isEmpty() && pathStack.peek().type == ElementType.ARRAY) {
                        pathStack.pop();
                    }
                    arrayIndex = -1;
                    collectingValue = false;
                }
                case ',' -> {
                    if (!pathStack.isEmpty() && pathStack.peek().type == ElementType.ARRAY) {
                        arrayIndex++;
                    }
                    currentKey = null;
                    collectingValue = false;
                    currentValue.setLength(0);
                }
                default -> {
                    if (collectingValue && !Character.isWhitespace(c)) {
                        currentValue.append(c);
                    }
                }
            }
            i++;
        }

        // If we reach here, position was at the end
        return buildHoverInfo(pathStack, currentKey, lastValueType,
                currentValue.toString().trim(), arrayIndex, false);
    }

    private static String detectValueType(String text, int position) {
        if (position >= text.length()) return "unknown";
        char c = text.charAt(position);

        if (c == '"') return "string";
        if (c == '{') return "object";
        if (c == '[') return "array";
        if (c == 't' || c == 'f') return "boolean";
        if (c == 'n') return "null";
        if (Character.isDigit(c) || c == '-') return "number";

        return "unknown";
    }

    private static int findNextNonWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String extractString(String text, int startQuote) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int findStringEnd(String text, int startQuote) {
        boolean escaped = false;
        for (int i = startQuote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static JsonHoverInfo buildHoverInfo(Deque<PathElement> pathStack, String currentKey,
                                                 String valueType, String value, int arrayIndex, boolean onKey) {
        StringBuilder path = new StringBuilder();

        // Build path from stack (forward order, from root to leaf)
        PathElement[] elements = pathStack.toArray(new PathElement[0]);
        for (int i = elements.length - 1; i >= 0; i--) {
            PathElement elem = elements[i];
            if (elem.type == ElementType.ROOT) {
                path.append(elem.name);
            } else if (elem.type == ElementType.ARRAY) {
                // Array elements don't add to path themselves,
                // the index is added when accessing elements
                if (elem.name != null && !elem.name.isEmpty()) {
                    path.append(".").append(elem.name);
                }
            } else if (elem.index >= 0) {
                // This is an object inside an array - add array index notation
                path.append("[").append(elem.index).append("]");
            } else {
                // Regular object property
                path.append(".").append(elem.name);
            }
        }

        // Add current key if present
        if (currentKey != null && !currentKey.isEmpty()) {
            path.append(".").append(currentKey);
        } else if (arrayIndex >= 0 && !pathStack.isEmpty() && pathStack.peek().type == ElementType.ARRAY) {
            path.append("[").append(arrayIndex).append("]");
        }

        String pathStr = path.toString();
        if (pathStr.isEmpty()) {
            pathStr = "$";
        }

        return new JsonHoverInfo(
                pathStr,
                valueType,
                value != null && !value.isEmpty() ? value : null,
                currentKey,
                pathStack.size() - 1
        );
    }

    private enum ElementType {
        ROOT, OBJECT, ARRAY
    }

    private record PathElement(String name, ElementType type, int index) {}
}
