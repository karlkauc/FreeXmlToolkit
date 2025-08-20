package org.fxt.freexmltoolkit.controls;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify the logic for formatting child element display in XmlEditor.
 */
class XmlEditorChildElementDisplayTest {

    @Test
    void testElementNameExtractionLogic() {
        // Test the logic that will be used in XmlEditor for extracting element names

        List<String> testXPaths = Arrays.asList(
                "/FundsXML4/ControlData/UniqueDocumentID",
                "/FundsXML4/ControlData/DocumentGenerated",
                "/FundsXML4/ControlData/Version",
                "/FundsXML4/Funds/Fund/Names/OfficialName",
                "/root/element",
                "simpleElement"
        );

        List<String> expectedNames = Arrays.asList(
                "UniqueDocumentID",
                "DocumentGenerated",
                "Version",
                "OfficialName",
                "element",
                "simpleElement"
        );

        for (int i = 0; i < testXPaths.size(); i++) {
            String xpath = testXPaths.get(i);
            String expected = expectedNames.get(i);

            // Simulate the logic used in formatChildElementsForDisplay
            String elementName = extractElementName(xpath);

            assertEquals(expected, elementName,
                    "Failed to extract element name from XPath: " + xpath);
        }
    }

    @Test
    void testElementNameExtractionEdgeCases() {
        // Test edge cases
        assertNull(extractElementName(null));
        assertNull(extractElementName(""));
        assertNull(extractElementName("   "));
        assertEquals("element", extractElementName("/element"));
        assertEquals("element", extractElementName("element/"));
    }

    @Test
    void testFormattedDisplayConcept() {
        // Test the concept of formatted display (without accessing private methods)
        List<String> mockChildXPaths = Arrays.asList(
                "/FundsXML4/ControlData/UniqueDocumentID",
                "/FundsXML4/ControlData/DocumentGenerated",
                "/FundsXML4/Funds/Fund"
        );

        // Simulate what formatChildElementsForDisplay would do
        List<String> formattedElements = mockChildXPaths.stream()
                .map(this::extractElementName)
                .filter(name -> name != null)
                .toList();

        assertEquals(3, formattedElements.size());
        assertTrue(formattedElements.contains("UniqueDocumentID"));
        assertTrue(formattedElements.contains("DocumentGenerated"));
        assertTrue(formattedElements.contains("Fund"));

        // Verify that no full XPaths remain
        for (String formatted : formattedElements) {
            assertFalse(formatted.contains("/FundsXML4/"),
                    "Formatted element should not contain full XPath: " + formatted);
        }
    }

    /**
     * Helper method that simulates the element name extraction logic from XmlEditor.
     */
    private String extractElementName(String xpath) {
        if (xpath == null || xpath.trim().isEmpty()) {
            return null;
        }

        xpath = xpath.trim();

        // Handle trailing slash
        if (xpath.endsWith("/")) {
            xpath = xpath.substring(0, xpath.length() - 1);
        }

        int lastSlashIndex = xpath.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < xpath.length() - 1) {
            return xpath.substring(lastSlashIndex + 1);
        }

        return xpath.isEmpty() ? null : xpath;
    }
}