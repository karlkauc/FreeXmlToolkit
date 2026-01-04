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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for XPath analysis and XML element extraction.
 *
 * <p>Provides functionality to parse XML content, extract element information,
 * and build XPath expressions from text positions.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XPathAnalyzer {
    private static final Pattern ELEMENT_TAG_PATTERN = Pattern.compile("<\\s*([a-zA-Z_:][a-zA-Z0-9_:.-]*)");
    private static final Pattern OPENING_TAG_PATTERN = Pattern.compile("<\\s*([a-zA-Z_:][a-zA-Z0-9_:.-]*)(?:\\s|>)");
    private static final Pattern CLOSING_TAG_PATTERN = Pattern.compile("</\\s*([a-zA-Z_:][a-zA-Z0-9_:.-]*)\\s*>");
    private static final Pattern SELF_CLOSING_TAG_PATTERN = Pattern.compile("<\\s*([a-zA-Z_:][a-zA-Z0-9_:.-]*)(?:\\s[^>]*)?>\\s*<");

    private XPathAnalyzer() {
        // Utility class - no instantiation
    }

    /**
     * Builds a deque of element names representing the path from root to the current position.
     *
     * @param xmlContent the XML content
     * @param caretPosition the cursor position in the text
     * @return deque of element names from root to current position
     */
    public static Deque<String> buildElementStackToPosition(String xmlContent, int caretPosition) {
        Deque<String> elementStack = new LinkedList<>();
        int pos = 0;

        while (pos < caretPosition && pos < xmlContent.length()) {
            int nextOpen = xmlContent.indexOf('<', pos);
            if (nextOpen == -1 || nextOpen >= caretPosition) {
                break;
            }

            int nextClose = xmlContent.indexOf('>', nextOpen);
            if (nextClose == -1) {
                break;
            }

            String tagContent = xmlContent.substring(nextOpen, nextClose + 1);

            if (tagContent.startsWith("</")) {
                // Closing tag
                Matcher closingMatcher = CLOSING_TAG_PATTERN.matcher(tagContent);
                if (closingMatcher.find()) {
                    String elementName = closingMatcher.group(1);
                    if (!elementStack.isEmpty() && elementStack.peekLast().equals(elementName)) {
                        elementStack.removeLast();
                    }
                }
            } else if (tagContent.endsWith("/>")) {
                // Self-closing tag - don't add to stack
            } else if (!tagContent.startsWith("<?") && !tagContent.startsWith("<!")) {
                // Opening tag
                Matcher openingMatcher = OPENING_TAG_PATTERN.matcher(tagContent);
                if (openingMatcher.find()) {
                    String elementName = openingMatcher.group(1);
                    elementStack.addLast(elementName);
                }
            }

            pos = nextClose + 1;
        }

        return elementStack;
    }

    /**
     * Extracts the root element name from XML content.
     *
     * @param xmlContent the XML content
     * @return the root element name, or null if not found
     */
    public static String extractRootElementName(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            return null;
        }

        // Skip XML declaration and comments
        int startPos = 0;
        while (startPos < xmlContent.length()) {
            int tagStart = xmlContent.indexOf('<', startPos);
            if (tagStart == -1) {
                return null;
            }

            int tagEnd = xmlContent.indexOf('>', tagStart);
            if (tagEnd == -1) {
                return null;
            }

            String tag = xmlContent.substring(tagStart, tagEnd + 1);

            // Skip declarations and comments
            if (tag.startsWith("<?") || tag.startsWith("<!")) {
                startPos = tagEnd + 1;
                continue;
            }

            // Found the first element tag
            if (!tag.startsWith("</")) {
                Matcher matcher = ELEMENT_TAG_PATTERN.matcher(tag);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            return null;
        }

        return null;
    }

    /**
     * Builds XPath string from an element stack.
     *
     * @param elementStack the stack of elements
     * @return XPath expression
     */
    public static String buildXPathFromStack(Deque<String> elementStack) {
        if (elementStack.isEmpty()) {
            return "/";
        }

        StringBuilder xpath = new StringBuilder("/");
        boolean first = true;
        for (String element : elementStack) {
            if (!first) {
                xpath.append("/");
            }
            xpath.append(element);
            first = false;
        }

        return xpath.toString();
    }

    /**
     * Extracts element names from XML attributes at a specific position.
     *
     * @param xmlContent the XML content
     * @param position the cursor position
     * @return element information record or null
     */
    public static ElementInfo extractElementInfoAtPosition(String xmlContent, int position) {
        if (position > xmlContent.length()) {
            return null;
        }

        // Find the nearest opening tag before the position
        int tagStart = xmlContent.lastIndexOf('<', position);
        if (tagStart == -1) {
            return null;
        }

        int tagEnd = xmlContent.indexOf('>', tagStart);
        if (tagEnd == -1 || tagEnd < position) {
            return null;
        }

        String tagContent = xmlContent.substring(tagStart, tagEnd + 1).trim();

        if (tagContent.startsWith("<?") || tagContent.startsWith("<!") || tagContent.startsWith("</")) {
            return null;
        }

        Matcher matcher = ELEMENT_TAG_PATTERN.matcher(tagContent);
        if (matcher.find()) {
            String elementName = matcher.group(1);
            // Type would need to be determined from XSD - return empty for now
            return new ElementInfo(elementName, "");
        }

        return null;
    }

    /**
     * Record representing element information.
     */
    public record ElementInfo(String name, String type) {
    }
}
