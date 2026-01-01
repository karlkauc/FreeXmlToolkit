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
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility class for JSON syntax highlighting.
 * Provides static methods for computing syntax highlighting spans for JSON, JSONC, and JSON5.
 * <p>
 * Supports:
 * <ul>
 *   <li>Standard JSON (RFC 8259)</li>
 *   <li>JSONC - JSON with Comments</li>
 *   <li>JSON5 - Extended JSON (unquoted keys, trailing commas, etc.)</li>
 * </ul>
 */
public final class JsonSyntaxHighlighter {

    // JSON Syntax Highlighting Pattern
    // Order matters: comments before strings to avoid false matches in commented strings
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "(?<LINECOMMENT>//[^\\n]*)" +                                          // Line comments (JSONC/JSON5)
            "|(?<BLOCKCOMMENT>/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)" +                // Block comments (JSONC/JSON5)
            "|(?<KEY>\"(?:[^\"\\\\]|\\\\.)*\"\\s*:)" +                             // Object keys (quoted)
            "|(?<UNQUOTEDKEY>[a-zA-Z_$][a-zA-Z0-9_$]*\\s*:)" +                     // Unquoted keys (JSON5)
            "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\")" +                               // String values
            "|(?<SINGLESTRING>'(?:[^'\\\\]|\\\\.)*')" +                            // Single-quoted strings (JSON5)
            "|(?<NUMBER>-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)" +       // Numbers
            "|(?<HEXNUMBER>0[xX][0-9a-fA-F]+)" +                                   // Hex numbers (JSON5)
            "|(?<INFINITY>[+-]?Infinity)" +                                        // Infinity (JSON5)
            "|(?<NAN>NaN)" +                                                       // NaN (JSON5)
            "|(?<BOOLEAN>true|false)" +                                            // Booleans
            "|(?<NULL>null)" +                                                     // Null
            "|(?<BRACKET>[\\[\\]{}])" +                                            // Brackets
            "|(?<COLON>:)" +                                                       // Colon (for syntax)
            "|(?<COMMA>,)"                                                         // Comma
    );

    private JsonSyntaxHighlighter() {
        // Utility class - no instantiation
    }

    /**
     * Computes syntax highlighting for JSON text.
     * Returns StyleSpans with CSS class names for different JSON elements:
     * <ul>
     *   <li>json-key - Object property keys</li>
     *   <li>json-string - String values</li>
     *   <li>json-number - Numeric values (including hex, Infinity, NaN)</li>
     *   <li>json-boolean - true/false</li>
     *   <li>json-null - null value</li>
     *   <li>json-bracket - Brackets and braces</li>
     *   <li>json-comment - Comments (JSONC/JSON5)</li>
     *   <li>json-punctuation - Colons and commas</li>
     * </ul>
     *
     * @param text The JSON text to highlight
     * @return StyleSpans with highlighting information
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            spansBuilder.add(Collections.emptyList(), 0);
            return spansBuilder.create();
        }

        Matcher matcher = JSON_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            // Add unstyled text before this match
            if (matcher.start() > lastEnd) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            }

            String styleClass = getStyleClass(matcher);
            int matchLength = matcher.end() - matcher.start();

            if (styleClass != null) {
                spansBuilder.add(Collections.singleton(styleClass), matchLength);
            } else {
                spansBuilder.add(Collections.emptyList(), matchLength);
            }

            lastEnd = matcher.end();
        }

        // Add remaining unstyled text
        if (lastEnd < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        }

        return spansBuilder.create();
    }

    /**
     * Determines the CSS style class for the current match.
     */
    private static String getStyleClass(Matcher matcher) {
        if (matcher.group("LINECOMMENT") != null || matcher.group("BLOCKCOMMENT") != null) {
            return "json-comment";
        }
        if (matcher.group("KEY") != null || matcher.group("UNQUOTEDKEY") != null) {
            return "json-key";
        }
        if (matcher.group("STRING") != null || matcher.group("SINGLESTRING") != null) {
            return "json-string";
        }
        if (matcher.group("NUMBER") != null || matcher.group("HEXNUMBER") != null ||
            matcher.group("INFINITY") != null || matcher.group("NAN") != null) {
            return "json-number";
        }
        if (matcher.group("BOOLEAN") != null) {
            return "json-boolean";
        }
        if (matcher.group("NULL") != null) {
            return "json-null";
        }
        if (matcher.group("BRACKET") != null) {
            return "json-bracket";
        }
        if (matcher.group("COLON") != null || matcher.group("COMMA") != null) {
            return "json-punctuation";
        }
        return null;
    }

    /**
     * Detects the JSON format variant based on content.
     *
     * @param text The JSON text to analyze
     * @return The detected format: "json", "jsonc", or "json5"
     */
    public static String detectFormat(String text) {
        if (text == null || text.isEmpty()) {
            return "json";
        }

        // Check for JSON5 features
        boolean hasJson5Features =
            text.contains("Infinity") ||
            text.contains("NaN") ||
            Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*\\s*:").matcher(text).find() ||  // Unquoted keys
            Pattern.compile("'[^']*'").matcher(text).find() ||                        // Single quotes
            Pattern.compile(",\\s*[}\\]]").matcher(text).find() ||                    // Trailing commas
            Pattern.compile("0[xX][0-9a-fA-F]+").matcher(text).find();               // Hex numbers

        if (hasJson5Features) {
            return "json5";
        }

        // Check for JSONC features (comments)
        boolean hasComments =
            text.contains("//") ||
            text.contains("/*");

        if (hasComments) {
            return "jsonc";
        }

        return "json";
    }

    /**
     * Strips comments from JSONC/JSON5 text to produce valid JSON.
     * Useful for parsing with standard JSON parsers.
     *
     * @param text The JSONC/JSON5 text
     * @return Text with comments removed
     */
    public static String stripComments(String text) {
        if (text == null) {
            return null;
        }

        // Remove block comments
        text = text.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");

        // Remove line comments (but not in strings)
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean inLineComment = false;
        char stringChar = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    result.append(c);
                }
                continue;
            }

            if (!inString && c == '/' && next == '/') {
                inLineComment = true;
                continue;
            }

            if (!inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
            } else if (inString && c == stringChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = false;
            }

            result.append(c);
        }

        return result.toString();
    }
}
