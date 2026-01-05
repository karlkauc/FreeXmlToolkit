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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for XML editor UI operations.
 *
 * <p>Provides functionality for text formatting, display helpers,
 * and UI-related utilities.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlEditorUIHelper {
    private static final Logger logger = LogManager.getLogger(XmlEditorUIHelper.class);

    // Pre-compiled patterns for optimization
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern NBSP_PATTERN = Pattern.compile("&nbsp;");
    private static final Pattern LT_PATTERN = Pattern.compile("&lt;");
    private static final Pattern GT_PATTERN = Pattern.compile("&gt;");
    private static final Pattern AMP_PATTERN = Pattern.compile("&amp;");

    private XmlEditorUIHelper() {
        // Utility class - no instantiation
    }

    /**
     * Strips HTML tags from text for plain text display.
     * Optimized to use pre-compiled patterns and StringBuilder.
     *
     * @param html the HTML text to strip
     * @return plain text without HTML tags
     */
    public static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // Use pre-compiled patterns instead of calling replaceAll multiple times
        String result = HTML_TAG_PATTERN.matcher(html).replaceAll("");
        result = NBSP_PATTERN.matcher(result).replaceAll(" ");
        result = AMP_PATTERN.matcher(result).replaceAll("&");
        result = LT_PATTERN.matcher(result).replaceAll("<");
        result = GT_PATTERN.matcher(result).replaceAll(">");
        return result.trim();
    }

    /**
     * Truncates text to a maximum length with ellipsis if needed.
     *
     * @param text the text to truncate
     * @param maxLength the maximum length
     * @return truncated text with ellipsis if needed
     */
    public static String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Formats child elements for display, removing container markers.
     *
     * <p>Converts markers like SEQUENCE_container, CHOICE_container to readable format.</p>
     *
     * @param children the list of child element names
     * @param removeContainerMarkers whether to remove container markers (SEQUENCE_, CHOICE_, ALL_)
     * @return formatted child elements
     */
    public static List<String> formatChildElementsForDisplay(List<String> children, boolean removeContainerMarkers) {
        List<String> result = new ArrayList<>();

        for (String child : children) {
            if (removeContainerMarkers) {
                // Remove container markers for display
                if (child.startsWith("SEQUENCE_")) {
                    continue; // Skip container markers
                }
                if (child.startsWith("CHOICE_")) {
                    continue;
                }
                if (child.startsWith("ALL_")) {
                    continue;
                }
            }

            result.add(child);
        }

        return result;
    }

    /**
     * Extracts element name from XPath.
     *
     * @param xpath the XPath expression (e.g., "/root/parent/element")
     * @return the element name (last part of XPath) or null if invalid
     */
    public static String extractElementNameFromXPath(String xpath) {
        if (xpath == null || xpath.isEmpty() || "/".equals(xpath)) {
            return null;
        }

        String[] parts = xpath.split("/");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            if (!lastPart.isEmpty()) {
                return lastPart;
            }
        }

        return null;
    }

    /**
     * Validates if XPath should be processed (not an error message).
     *
     * @param xpath the XPath to validate
     * @return true if XPath appears valid
     */
    public static boolean isValidXPath(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return false;
        }

        // Check if it's an error message
        return !xpath.equals("Invalid XML structure") &&
               !xpath.equals("No XML content") &&
               !xpath.equals("Unable to determine XPath");
    }

    /**
     * Record representing a tag match with position, name, and type.
     */
    public static record TagMatch(int position, String name, TagType type) {
    }

    /**
     * Enum for tag types.
     */
    public enum TagType {
        OPEN, CLOSE, SELF_CLOSING
    }
}
