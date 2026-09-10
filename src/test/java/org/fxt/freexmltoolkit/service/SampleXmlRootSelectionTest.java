package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers choosing the root global element for sample XML generation, in both the plain
 * ({@link XsdDocumentationService#generateSampleXml(String, boolean, int)}) and the realistic
 * ({@link ProfiledXmlGeneratorService#generateRealistic(GenerationProfile, org.fxt.freexmltoolkit.domain.XsdDocumentationData, String, String)})
 * generator.
 */
class SampleXmlRootSelectionTest {

    /** Three global elements, no choices/enumerations/patterns, so the output structure is deterministic. */
    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns="urn:test:roots" targetNamespace="urn:test:roots" elementFormDefault="qualified">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="id" type="xs:int"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="customer">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="name" type="xs:string"/>
                    <xs:element name="age" type="xs:int" minOccurs="0"/>
                  </xs:sequence>
                  <xs:attribute name="code" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>
              <xs:element name="note" type="xs:string"/>
            </xs:schema>
            """;

    private Path xsd;

    @BeforeEach
    void writeSchema(@TempDir Path dir) throws Exception {
        xsd = dir.resolve("roots.xsd");
        Files.writeString(xsd, XSD);
    }

    private XsdDocumentationService processedService() {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service;
    }

    @Test
    void rootElementNamesAreInDocumentOrder() {
        assertEquals(List.of("order", "customer", "note"), processedService().getRootElementNames());
    }

    @Test
    void namedFirstRootMatchesDefaultGeneration() {
        XsdDocumentationService service = processedService();
        assertEquals(service.generateSampleXml(false, 2), service.generateSampleXml("order", false, 2));
    }

    @Test
    void namedSecondRootIsGeneratedAndValid() throws Exception {
        XsdDocumentationService service = processedService();

        String required = service.generateSampleXml("customer", true, 2);
        assertTrue(required.contains("<customer "), required);
        assertTrue(required.contains("code=\""), required);
        assertTrue(required.contains("<name>"), required);
        assertTrue(!required.contains("<age>"), "optional element must be omitted in mandatory-only mode");
        assertValid(required);

        String optional = service.generateSampleXml("customer", false, 2);
        assertTrue(optional.contains("<age>"), optional);
        assertValid(optional);

        assertValid(service.generateSampleXml("note", false, 2));
    }

    @Test
    void unknownRootIsRejected() {
        XsdDocumentationService service = processedService();
        assertThrows(IllegalArgumentException.class, () -> service.generateSampleXml("missing", false, 2));
    }

    @Test
    void switchingRootsDoesNotReprocessTheSchema() {
        XsdDocumentationService service = processedService();
        String first = service.generateSampleXml("customer", true, 2);
        int mapSize = service.xsdDocumentationData.getExtendedXsdElementMap().size();

        service.generateSampleXml("order", true, 2);
        String again = service.generateSampleXml("customer", true, 2);

        assertEquals(mapSize, service.xsdDocumentationData.getExtendedXsdElementMap().size());
        assertEquals(first, again);
    }

    @Test
    void realisticGeneratorHonoursTheNamedRoot() throws Exception {
        XsdDocumentationService service = processedService();
        service.processXsd(Boolean.TRUE);
        GenerationProfile profile = new GenerationProfile("Realistic");
        profile.setMandatoryOnly(false);
        profile.setMaxOccurrences(2);
        ProfiledXmlGeneratorService generator = new ProfiledXmlGeneratorService();

        String defaultRoot = generator.generateRealistic(profile, service.xsdDocumentationData, xsd.toString());
        assertTrue(defaultRoot.contains("<order "), defaultRoot);

        String customer = generator.generateRealistic(profile, service.xsdDocumentationData, xsd.toString(), "customer");
        assertTrue(customer.contains("<customer "), customer);
        assertValid(customer);

        assertThrows(IllegalArgumentException.class,
                () -> generator.generateRealistic(profile, service.xsdDocumentationData, xsd.toString(), "missing"));
    }

    private void assertValid(String xml) throws Exception {
        Schema schema = new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile());
        schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
