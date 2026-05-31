package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link SampleXmlRunner} (no UI): generates a sample XML instance from an
 * XSD, reusing {@code XsdDocumentationService.generateSampleXml}.
 */
class SampleXmlRunnerTest {

    private static final File XSD = new File("src/test/resources/purchageOrder.xsd");

    @Test
    void generatesSampleXmlForTheRootElement() {
        String xml = SampleXmlRunner.generate(XSD, false, 2);

        assertFalse(xml.startsWith("ERROR:"), xml);
        assertTrue(xml.contains("<?xml"), "must be an XML document: " + xml);
        assertTrue(xml.contains("PurchaseOrder"), "must contain the root element: " + xml);
    }

    @Test
    void missingFileReturnsError() {
        assertTrue(SampleXmlRunner.generate(new File("/no/such.xsd"), false, 1).startsWith("ERROR:"));
    }
}
