/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.controls.v2.common.utilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/**
 * Unified file I/O utility for common file operations.
 *
 * <p>Provides common functionality for saving files, validating writability,
 * and formatting XML content.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class FileIOUtility {
    private static final Logger logger = LogManager.getLogger(FileIOUtility.class);
    private static final Pattern INDENT_PATTERN = Pattern.compile("^( {2})", Pattern.MULTILINE);

    private FileIOUtility() {
        // Utility class - no instantiation
    }

    /**
     * Saves content to a file with UTF-8 encoding.
     *
     * @param filePath the file path
     * @param content the content to save
     * @throws IOException if an I/O error occurs
     */
    public static void saveToFile(String filePath, String content) throws IOException {
        saveToFile(Path.of(filePath), content);
    }

    /**
     * Saves content to a file with UTF-8 encoding.
     *
     * @param filePath the file path
     * @param content the content to save
     * @throws IOException if an I/O error occurs
     */
    public static void saveToFile(Path filePath, String content) throws IOException {
        validateWritable(filePath);
        Files.writeString(filePath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Saved file: {}", filePath);
    }

    /**
     * Saves content to a file with custom encoding.
     *
     * @param filePath the file path
     * @param content the content to save
     * @param encoding the character encoding
     * @throws IOException if an I/O error occurs
     */
    public static void saveToFile(Path filePath, String content, String encoding) throws IOException {
        validateWritable(filePath);
        Files.writeString(filePath, content, encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Saved file: {} (encoding: {})", filePath, encoding);
    }

    /**
     * Validates that a file path is writable.
     *
     * <p>Creates parent directories if they don't exist.</p>
     *
     * @param filePath the file path to validate
     * @throws IOException if the path is not writable or parent creation fails
     */
    public static void validateWritable(Path filePath) throws IOException {
        // Create parent directories if they don't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.debug("Created parent directories: {}", parentDir);
        }

        // Check if we can write to the parent directory
        if (parentDir != null && !Files.isWritable(parentDir)) {
            throw new IOException("Parent directory is not writable: " + parentDir);
        }
    }

    /**
     * Pretty-prints XML content with specified indentation.
     *
     * <p>Adds proper line breaks and indentation to XML content for readability.</p>
     *
     * @param xml the XML content
     * @param indent the indentation level (number of spaces per level)
     * @return the formatted XML string
     */
    public static String prettyPrintXml(String xml, int indent) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }

        // Simple pretty printing - add line breaks after > and before <
        StringBuilder formatted = new StringBuilder();
        int depth = 0;
        boolean inTag = false;

        for (int i = 0; i < xml.length(); i++) {
            char c = xml.charAt(i);

            if (c == '<') {
                if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != '\n' && !Character.isWhitespace(c)) {
                    formatted.append('\n');
                }
                // Check for closing tag
                if (i + 1 < xml.length() && xml.charAt(i + 1) == '/') {
                    depth--;
                }
                appendIndentation(formatted, depth, indent);
                inTag = true;
            } else if (c == '>') {
                formatted.append(c);
                inTag = false;
                // Don't add newline for tags that contain text immediately
                if (i + 1 < xml.length() && xml.charAt(i + 1) != '<') {
                    // Has text content, don't add newline
                } else {
                    formatted.append('\n');
                    // Opening tags increase depth
                    if (i > 0 && xml.charAt(i - 1) != '/' && !xml.startsWith("<?", Math.max(0, i - 5))) {
                        if (!xml.substring(Math.max(0, i - 20), i).contains("/")) {
                            depth++;
                        }
                    }
                }
                continue;
            } else if (!inTag && Character.isWhitespace(c)) {
                continue;  // Skip whitespace outside tags
            }

            formatted.append(c);
        }

        return formatted.toString();
    }

    /**
     * Appends indentation to a StringBuilder.
     *
     * @param sb the StringBuilder
     * @param depth the indentation depth
     * @param spaces the number of spaces per level
     */
    private static void appendIndentation(StringBuilder sb, int depth, int spaces) {
        for (int i = 0; i < depth * spaces; i++) {
            sb.append(' ');
        }
    }

    /**
     * Reads file content as string with UTF-8 encoding.
     *
     * @param filePath the file path
     * @return the file content
     * @throws IOException if an I/O error occurs
     */
    public static String readFromFile(Path filePath) throws IOException {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * Reads file content as string with custom encoding.
     *
     * @param filePath the file path
     * @param encoding the character encoding
     * @return the file content
     * @throws IOException if an I/O error occurs
     */
    public static String readFromFile(Path filePath, String encoding) throws IOException {
        return Files.readString(filePath, encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding));
    }
}
