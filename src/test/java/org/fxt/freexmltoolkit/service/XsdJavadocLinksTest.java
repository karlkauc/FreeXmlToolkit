package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parseJavadocLinks method in XsdDocumentationHtmlService.
 */
class XsdJavadocLinksTest {

    @Test
    @DisplayName("Should parse {@link} tags with XPaths and element names")
    void testParseJavadocLinks() throws Exception {
        // Arrange
        File xsdFile = new File("src/test/resources/test-appinfo-annotations.xsd");
        assertTrue(xsdFile.exists(), "Test XSD file should exist");

        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFile.getAbsolutePath());
        docService.processXsd(true);

        XsdDocumentationHtmlService htmlService = new XsdDocumentationHtmlService();
        htmlService.setDocumentationData(docService.xsdDocumentationData);
        htmlService.setXsdDocumentationService(docService);

        // Act & Assert - Test XPath link
        String xpathLink = "{@link /Transaction/TransactionKind}";
        String parsedXPathLink = htmlService.parseJavadocLinks(xpathLink);

        assertNotNull(parsedXPathLink, "Parsed XPath link should not be null");
        assertTrue(parsedXPathLink.contains("<a href="), "Should contain an anchor tag");
        assertTrue(parsedXPathLink.contains("TransactionKind"), "Should contain element name");
        assertTrue(parsedXPathLink.contains("<code>"), "Should contain code tag");

        // Act & Assert - Test element name link
        String nameLink = "{@link TransactionStatus}";
        String parsedNameLink = htmlService.parseJavadocLinks(nameLink);

        assertNotNull(parsedNameLink, "Parsed name link should not be null");
        assertTrue(parsedNameLink.contains("<a href=") || parsedNameLink.contains("<code>"),
                "Should contain anchor or code tag");
        assertTrue(parsedNameLink.contains("TransactionStatus"), "Should contain element name");

        // Act & Assert - Test plain text with link
        String textWithLink = "Use TransactionStatus instead. See {@link /Transaction/TransactionStatus}";
        String parsedText = htmlService.parseJavadocLinks(textWithLink);

        assertNotNull(parsedText, "Parsed text should not be null");
        assertTrue(parsedText.contains("Use TransactionStatus instead"), "Should preserve plain text");
        assertTrue(parsedText.contains("<a href=") || parsedText.contains("<code>"),
                "Should contain link or code tag");

        // Act & Assert - Test non-existent link
        String nonExistentLink = "{@link /NonExistent/Element}";
        String parsedNonExistent = htmlService.parseJavadocLinks(nonExistentLink);

        assertNotNull(parsedNonExistent, "Parsed non-existent link should not be null");
        assertTrue(parsedNonExistent.contains("<code>"), "Should contain code tag for non-existent links");
        assertFalse(parsedNonExistent.contains("<a href="), "Should not contain anchor for non-existent links");
    }

    @Test
    @DisplayName("Should handle null and empty content gracefully")
    void testParseJavadocLinksEdgeCases() throws Exception {
        // Arrange
        File xsdFile = new File("src/test/resources/test-appinfo-annotations.xsd");
        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFile.getAbsolutePath());
        docService.processXsd(true);

        XsdDocumentationHtmlService htmlService = new XsdDocumentationHtmlService();
        htmlService.setDocumentationData(docService.xsdDocumentationData);
        htmlService.setXsdDocumentationService(docService);

        // Act & Assert
        assertEquals("", htmlService.parseJavadocLinks(null), "Null content should return empty string");
        assertEquals("", htmlService.parseJavadocLinks(""), "Empty content should return empty string");
        assertEquals("Plain text", htmlService.parseJavadocLinks("Plain text"),
                "Plain text without links should be unchanged");
    }
}
