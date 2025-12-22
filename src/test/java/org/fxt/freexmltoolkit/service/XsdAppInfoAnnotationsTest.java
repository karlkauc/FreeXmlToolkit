package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XSD appinfo annotations parsing (@since, @see, @deprecated).
 */
class XsdAppInfoAnnotationsTest {

    @Test
    @DisplayName("Should parse @since, @see, and @deprecated annotations from appinfo tags")
    void testAppInfoAnnotationsParsing() throws Exception {
        // Arrange
        File xsdFile = new File("src/test/resources/test-appinfo-annotations.xsd");
        assertTrue(xsdFile.exists(), "Test XSD file should exist");

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());

        // Act
        service.processXsd(true);
        var xsdDocumentationData = service.xsdDocumentationData;

        // Assert - Check TransactionID element
        // Note: XPath may include SEQUENCE container, so find by element name
        XsdExtendedElement transactionIdElement = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "TransactionID".equals(e.getElementName()))
                .findFirst()
                .orElse(null);
        assertNotNull(transactionIdElement, "TransactionID element should be found");

        XsdDocInfo transactionIdDocInfo = transactionIdElement.getXsdDocInfo();
        assertNotNull(transactionIdDocInfo, "TransactionID should have XsdDocInfo");
        assertTrue(transactionIdDocInfo.hasData(), "TransactionID XsdDocInfo should have data");

        // Check @since
        assertEquals("1.0.0", transactionIdDocInfo.getSince(),
                "TransactionID @since should be '1.0.0'");

        // Check @see
        assertFalse(transactionIdDocInfo.getSee().isEmpty(),
                "TransactionID should have @see annotations");
        assertEquals(2, transactionIdDocInfo.getSee().size(),
                "TransactionID should have 2 @see annotations");
        assertTrue(transactionIdDocInfo.getSee().contains("{@link /Transaction/TransactionKind}"),
                "TransactionID should have @see with {@link}");
        assertTrue(transactionIdDocInfo.getSee().contains("Additional reference information"),
                "TransactionID should have @see with text");

        // Check no @deprecated
        assertNull(transactionIdDocInfo.getDeprecated(),
                "TransactionID should not be deprecated");

        // Assert - Check CancellationFlag element
        // Note: XPath may include SEQUENCE container, so find by element name
        XsdExtendedElement cancellationFlagElement = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "CancellationFlag".equals(e.getElementName()))
                .findFirst()
                .orElse(null);
        assertNotNull(cancellationFlagElement, "CancellationFlag element should be found");

        XsdDocInfo cancellationFlagDocInfo = cancellationFlagElement.getXsdDocInfo();
        assertNotNull(cancellationFlagDocInfo, "CancellationFlag should have XsdDocInfo");
        assertTrue(cancellationFlagDocInfo.hasData(), "CancellationFlag XsdDocInfo should have data");

        // Check @since
        assertEquals("1.0.0", cancellationFlagDocInfo.getSince(),
                "CancellationFlag @since should be '1.0.0'");

        // Check @deprecated
        assertNotNull(cancellationFlagDocInfo.getDeprecated(),
                "CancellationFlag should be deprecated");
        assertEquals("Use TransactionStatus instead", cancellationFlagDocInfo.getDeprecated(),
                "CancellationFlag @deprecated message should match");

        // Check no @see
        assertTrue(cancellationFlagDocInfo.getSee().isEmpty(),
                "CancellationFlag should not have @see annotations");
    }

    @Test
    @DisplayName("Should correctly identify elements with XsdDocInfo data")
    void testHasDataMethod() {
        // Test with no data
        XsdDocInfo emptyDocInfo = new XsdDocInfo();
        assertFalse(emptyDocInfo.hasData(), "Empty XsdDocInfo should not have data");

        // Test with only @since
        XsdDocInfo sinceOnlyDocInfo = new XsdDocInfo();
        sinceOnlyDocInfo.setSince("2.0.0");
        assertTrue(sinceOnlyDocInfo.hasData(), "XsdDocInfo with @since should have data");

        // Test with only @see
        XsdDocInfo seeOnlyDocInfo = new XsdDocInfo();
        seeOnlyDocInfo.getSee().add("Some reference");
        assertTrue(seeOnlyDocInfo.hasData(), "XsdDocInfo with @see should have data");

        // Test with only @deprecated
        XsdDocInfo deprecatedOnlyDocInfo = new XsdDocInfo();
        deprecatedOnlyDocInfo.setDeprecated("No longer used");
        assertTrue(deprecatedOnlyDocInfo.hasData(), "XsdDocInfo with @deprecated should have data");

        // Test with all data
        XsdDocInfo fullDocInfo = new XsdDocInfo();
        fullDocInfo.setSince("1.0.0");
        fullDocInfo.getSee().add("Reference 1");
        fullDocInfo.setDeprecated("Deprecated");
        assertTrue(fullDocInfo.hasData(), "XsdDocInfo with all data should have data");
    }
}
