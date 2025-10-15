package org.fxt.freexmltoolkit.controls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for XsdSimpleTypeEditor pre-population functionality
 * Note: These tests are primarily for validation of DOM structure
 * since JavaFX components require a display to initialize properly
 */
class XsdSimpleTypeEditorPrePopulationTest {

    private Document testDocument;
    private Element simpleTypeElement;

    private static final String TEST_XSD_WITH_SIMPLETYPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test">
            
                <xs:simpleType name="EmailType">
                    <xs:annotation>
                        <xs:documentation>Email address type with validation</xs:documentation>
                    </xs:annotation>
                    <xs:restriction base="xs:string">
                        <xs:pattern value="[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"/>
                        <xs:minLength value="5"/>
                        <xs:maxLength value="100"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:simpleType name="StatusType">
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="ACTIVE">
                            <xs:annotation>
                                <xs:documentation>Active status</xs:documentation>
                            </xs:annotation>
                        </xs:enumeration>
                        <xs:enumeration value="INACTIVE"/>
                        <xs:enumeration value="PENDING"/>
                    </xs:restriction>
                </xs:simpleType>
            
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        testDocument = builder.parse(new InputSource(new StringReader(TEST_XSD_WITH_SIMPLETYPE)));

        // Find the EmailType simpleType element for testing
        simpleTypeElement = (Element) testDocument.getElementsByTagName("xs:simpleType").item(0);
        assertNotNull(simpleTypeElement, "EmailType simpleType should exist");
        assertEquals("EmailType", simpleTypeElement.getAttribute("name"));
    }

    @Test
    void testSimpleTypeElementStructureForPatternBased() {
        // Verify EmailType SimpleType structure for pre-population
        assertEquals("EmailType", simpleTypeElement.getAttribute("name"));

        // Check documentation
        NodeList annotations = simpleTypeElement.getElementsByTagName("xs:annotation");
        assertEquals(1, annotations.getLength(), "Should have documentation annotation");

        NodeList docs = ((Element) annotations.item(0)).getElementsByTagName("xs:documentation");
        assertEquals(1, docs.getLength(), "Should have documentation element");
        assertEquals("Email address type with validation", docs.item(0).getTextContent().trim());

        // Check restriction structure
        NodeList restrictions = simpleTypeElement.getElementsByTagName("xs:restriction");
        assertEquals(1, restrictions.getLength(), "Should have one restriction");

        Element restriction = (Element) restrictions.item(0);
        assertEquals("xs:string", restriction.getAttribute("base"), "Should be based on xs:string");

        // Check pattern facet
        NodeList patterns = restriction.getElementsByTagName("xs:pattern");
        assertEquals(1, patterns.getLength(), "Should have one pattern");
        assertEquals("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                ((Element) patterns.item(0)).getAttribute("value"));

        // Check length facets
        NodeList minLengths = restriction.getElementsByTagName("xs:minLength");
        assertEquals(1, minLengths.getLength(), "Should have minLength");
        assertEquals("5", ((Element) minLengths.item(0)).getAttribute("value"));

        NodeList maxLengths = restriction.getElementsByTagName("xs:maxLength");
        assertEquals(1, maxLengths.getLength(), "Should have maxLength");
        assertEquals("100", ((Element) maxLengths.item(0)).getAttribute("value"));
    }

    @Test
    void testSimpleTypeElementStructureForEnumerationBased() throws Exception {
        // Find the StatusType simpleType element (has enumerations)
        Element statusTypeElement = (Element) testDocument.getElementsByTagName("xs:simpleType").item(1);
        assertNotNull(statusTypeElement, "StatusType simpleType should exist");
        assertEquals("StatusType", statusTypeElement.getAttribute("name"));

        // Check restriction structure
        NodeList restrictions = statusTypeElement.getElementsByTagName("xs:restriction");
        assertEquals(1, restrictions.getLength(), "Should have one restriction");

        Element restriction = (Element) restrictions.item(0);
        assertEquals("xs:string", restriction.getAttribute("base"), "Should be based on xs:string");

        // Check enumeration facets
        NodeList enumerations = restriction.getElementsByTagName("xs:enumeration");
        assertEquals(3, enumerations.getLength(), "Should have three enumerations");

        // Verify enumeration values
        assertEquals("ACTIVE", ((Element) enumerations.item(0)).getAttribute("value"));
        assertEquals("INACTIVE", ((Element) enumerations.item(1)).getAttribute("value"));
        assertEquals("PENDING", ((Element) enumerations.item(2)).getAttribute("value"));

        // Check that ACTIVE enumeration has documentation
        Element activeEnum = (Element) enumerations.item(0);
        NodeList activeAnnotations = activeEnum.getElementsByTagName("xs:annotation");
        assertEquals(1, activeAnnotations.getLength(), "ACTIVE should have documentation");

        NodeList activeDocs = ((Element) activeAnnotations.item(0)).getElementsByTagName("xs:documentation");
        assertEquals(1, activeDocs.getLength(), "ACTIVE should have documentation element");
        assertEquals("Active status", activeDocs.item(0).getTextContent().trim());
    }

    @Test
    void testDocumentStructureForPrePopulation() {
        // Verify that document contains expected SimpleType elements
        NodeList simpleTypes = testDocument.getElementsByTagName("xs:simpleType");
        assertEquals(2, simpleTypes.getLength(), "Should have two SimpleType elements");

        // Verify schema namespace setup
        Element schema = testDocument.getDocumentElement();
        assertEquals("xs:schema", schema.getTagName());
        assertEquals("http://www.w3.org/2001/XMLSchema", schema.getNamespaceURI());
    }

    @Test
    void testPrePopulationDataAvailability() {
        // This test validates that all the data needed for pre-population is available
        // The actual pre-population happens in XsdSimpleTypeEditor.loadExistingSimpleType()

        // Test that we can find SimpleType by name
        NodeList simpleTypes = testDocument.getElementsByTagName("xs:simpleType");
        Element emailType = null;
        Element statusType = null;

        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element st = (Element) simpleTypes.item(i);
            String name = st.getAttribute("name");
            if ("EmailType".equals(name)) {
                emailType = st;
            } else if ("StatusType".equals(name)) {
                statusType = st;
            }
        }

        assertNotNull(emailType, "EmailType should be found");
        assertNotNull(statusType, "StatusType should be found");

        // Verify that restriction elements exist and can be parsed
        assertNotNull(emailType.getElementsByTagName("xs:restriction").item(0));
        assertNotNull(statusType.getElementsByTagName("xs:restriction").item(0));
    }
}