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

    @Test
    void realisticModeHonorsEnumerations(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="order">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="status">
                          <xs:simpleType>
                            <xs:restriction base="xs:string">
                              <xs:enumeration value="OPEN"/>
                              <xs:enumeration value="CLOSED"/>
                            </xs:restriction>
                          </xs:simpleType>
                        </xs:element>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
        java.nio.file.Path file = tmp.resolve("order.xsd");
        java.nio.file.Files.writeString(file, xsd);

        String xml = SampleXmlRunner.generate(file.toFile(), false, 1, true);
        assertFalse(xml.startsWith("ERROR:"), xml);
        assertTrue(xml.contains("OPEN") || xml.contains("CLOSED"),
                "realistic mode must emit a valid enumeration value for <status>: " + xml);
    }
}
