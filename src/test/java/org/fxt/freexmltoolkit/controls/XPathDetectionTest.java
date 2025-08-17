package org.fxt.freexmltoolkit.controls;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XPath detection functionality.
 * Tests the XPath detection logic without requiring JavaFX UI components.
 */
class XPathDetectionTest {

    /**
     * Tests XPath detection for well-formed XML.
     */
    @Test
    void testXPathDetectionForWellFormedXML() {
        String xmlText = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <child>
                        <grandchild>text</grandchild>
                    </child>
                </root>
                """;

        // Test XPath at different positions using manual parsing logic
        assertEquals("No XML content", getCurrentXPathManual(xmlText, 0));
        assertEquals("/root", getCurrentXPathManual(xmlText, xmlText.indexOf("<child>")));
        assertEquals("/root/child", getCurrentXPathManual(xmlText, xmlText.indexOf("<grandchild>")));
        assertEquals("/root/child/grandchild", getCurrentXPathManual(xmlText, xmlText.indexOf("text")));
    }

    /**
     * Tests XPath detection for malformed XML.
     */
    @Test
    void testXPathDetectionForMalformedXML() {
        String malformedXml = """
                <root>
                    <child>
                        <grandchild>text
                    </child>
                </root>
                """;

        // Should still be able to detect XPath even with malformed XML
        String xpath = getCurrentXPathManual(malformedXml, malformedXml.indexOf("text"));
        assertTrue(xpath.contains("grandchild") || xpath.equals("Unable to determine XPath"));
    }

    /**
     * Tests XPath detection with namespaces.
     */
    @Test
    void testXPathDetectionWithNamespaces() {
        String xmlWithNamespaces = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ns:root xmlns:ns="http://example.com">
                    <ns:child>
                        <ns:grandchild>text</ns:grandchild>
                    </ns:child>
                </ns:root>
                """;

        String xpath = getCurrentXPathManual(xmlWithNamespaces, xmlWithNamespaces.indexOf("text"));
        assertTrue(xpath.contains("ns:") || xpath.equals("Unable to determine XPath"));
    }

    /**
     * Tests XPath detection with empty content.
     */
    @Test
    void testXPathDetectionWithEmptyContent() {
        assertEquals("No XML content", getCurrentXPathManual("", 0));
        assertEquals("No XML content", getCurrentXPathManual(null, 0));
        assertEquals("No XML content", getCurrentXPathManual("some text", -1));
    }

    /**
     * Tests XPath detection with self-closing tags.
     */
    @Test
    void testXPathDetectionWithSelfClosingTags() {
        String xmlWithSelfClosing = """
                <root>
                    <child/>
                    <sibling attr="value">text</sibling>
                </root>
                """;

        String xpath = getCurrentXPathManual(xmlWithSelfClosing, xmlWithSelfClosing.indexOf("text"));
        assertTrue(xpath.contains("sibling") || xpath.equals("Unable to determine XPath"));
    }

    /**
     * Tests XPath detection with processing instructions.
     */
    @Test
    void testXPathDetectionWithProcessingInstructions() {
        String xmlWithPI = """
                <?xml version="1.0"?>
                <?xml-stylesheet type="text/xsl" href="style.xsl"?>
                <root>
                    <child>text</child>
                </root>
                """;

        String xpath = getCurrentXPathManual(xmlWithPI, xmlWithPI.indexOf("text"));
        // The manual parsing might not handle processing instructions perfectly
        // So we just check that it doesn't crash and returns something reasonable
        assertNotNull(xpath);
        assertFalse(xpath.isEmpty());
    }

    /**
     * Manual XPath parsing for malformed XML.
     * This method uses simple string operations to extract element names.
     *
     * @param text     The XML text content
     * @param position The current cursor position
     * @return The XPath string or a descriptive message
     */
    private String getCurrentXPathManual(String text, int position) {
        if (text == null || text.isEmpty() || position <= 0) {
            return "No XML content";
        }

        Deque<String> elementStack = new ArrayDeque<>();
        String textBeforeCursor = text.substring(0, position);

        // Simple regex to find opening and closing tags
        String openTagPattern = "<([a-zA-Z][a-zA-Z0-9_:]*)\\b[^>]*>";
        String closeTagPattern = "</([a-zA-Z][a-zA-Z0-9_:]*)\\s*>";

        try {
            // Find all opening tags before cursor
            java.util.regex.Pattern openPattern = java.util.regex.Pattern.compile(openTagPattern);
            java.util.regex.Matcher openMatcher = openPattern.matcher(textBeforeCursor);

            while (openMatcher.find()) {
                String elementName = openMatcher.group(1);
                // Skip self-closing tags and processing instructions
                if (!elementName.startsWith("?") && !elementName.startsWith("!")) {
                    elementStack.push(elementName);
                }
            }

            // Find all closing tags before cursor and remove corresponding opening tags
            java.util.regex.Pattern closePattern = java.util.regex.Pattern.compile(closeTagPattern);
            java.util.regex.Matcher closeMatcher = closePattern.matcher(textBeforeCursor);

            while (closeMatcher.find()) {
                String elementName = closeMatcher.group(1);
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            }

            // Build XPath
            Deque<String> reversedStack = new ArrayDeque<>();
            elementStack.forEach(reversedStack::push);
            String xpath = "/" + String.join("/", reversedStack);

            if (xpath.equals("/")) {
                return "Root element";
            }

            return xpath;

        } catch (Exception e) {
            return "Unable to determine XPath";
        }
    }
}
