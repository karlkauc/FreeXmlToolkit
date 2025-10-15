package org.fxt.freexmltoolkit.controls;

import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdControlPane constraint loading functionality
 */
class XsdControlPaneConstraintsTest {

    private XsdDomManipulator domManipulator;

    private static final String TEST_XSD_WITH_CONSTRAINTS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test">
            
                <!-- Element with inline simpleType and pattern -->
                <xs:element name="Email">
                    <xs:annotation>
                        <xs:documentation>Email address element</xs:documentation>
                    </xs:annotation>
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:pattern value="[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"/>
                            <xs:minLength value="5"/>
                            <xs:maxLength value="100"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            
                <!-- Element with enumeration -->
                <xs:element name="Status">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:enumeration value="ACTIVE"/>
                            <xs:enumeration value="INACTIVE"/>
                            <xs:enumeration value="PENDING"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            
                <!-- Element with numeric constraints -->
                <xs:element name="Age">
                    <xs:simpleType>
                        <xs:restriction base="xs:int">
                            <xs:minInclusive value="0"/>
                            <xs:maxInclusive value="120"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
            
                <!-- Named simpleType with constraints -->
                <xs:simpleType name="PriceType">
                    <xs:restriction base="xs:decimal">
                        <xs:minExclusive value="0"/>
                        <xs:totalDigits value="10"/>
                        <xs:fractionDigits value="2"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <!-- Element referencing named type -->
                <xs:element name="Price" type="tns:PriceType"/>
            
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        domManipulator = new XsdDomManipulator();
        domManipulator.loadXsd(TEST_XSD_WITH_CONSTRAINTS);
    }

    @Test
    void testPatternConstraintLoading() throws Exception {
        // Given - Email element with pattern constraint
        Element emailElement = domManipulator.findElementByXPath("/Email");
        assertNotNull(emailElement, "Email element should exist");

        // Verify pattern constraint exists in DOM
        Element simpleType = (Element) emailElement.getElementsByTagName("xs:simpleType").item(0);
        assertNotNull(simpleType, "SimpleType should exist");

        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);
        assertNotNull(restriction, "Restriction should exist");

        Element pattern = (Element) restriction.getElementsByTagName("xs:pattern").item(0);
        assertNotNull(pattern, "Pattern should exist");
        assertEquals("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                pattern.getAttribute("value"));
    }

    @Test
    void testEnumerationConstraintLoading() throws Exception {
        // Given - Status element with enumeration constraints
        Element statusElement = domManipulator.findElementByXPath("/Status");
        assertNotNull(statusElement, "Status element should exist");

        // Verify enumeration constraints exist in DOM
        Element simpleType = (Element) statusElement.getElementsByTagName("xs:simpleType").item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);

        var enumerations = restriction.getElementsByTagName("xs:enumeration");
        assertEquals(3, enumerations.getLength(), "Should have 3 enumerations");

        assertEquals("ACTIVE", ((Element) enumerations.item(0)).getAttribute("value"));
        assertEquals("INACTIVE", ((Element) enumerations.item(1)).getAttribute("value"));
        assertEquals("PENDING", ((Element) enumerations.item(2)).getAttribute("value"));
    }

    @Test
    void testNumericConstraintLoading() throws Exception {
        // Given - Age element with numeric constraints  
        Element ageElement = domManipulator.findElementByXPath("/Age");
        assertNotNull(ageElement, "Age element should exist");

        // Verify numeric constraints exist in DOM
        Element simpleType = (Element) ageElement.getElementsByTagName("xs:simpleType").item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);

        Element minInclusive = (Element) restriction.getElementsByTagName("xs:minInclusive").item(0);
        assertNotNull(minInclusive, "MinInclusive should exist");
        assertEquals("0", minInclusive.getAttribute("value"));

        Element maxInclusive = (Element) restriction.getElementsByTagName("xs:maxInclusive").item(0);
        assertNotNull(maxInclusive, "MaxInclusive should exist");
        assertEquals("120", maxInclusive.getAttribute("value"));
    }

    @Test
    void testTypeReferenceConstraintLoading() throws Exception {
        // Given - Price element referencing PriceType
        Element priceElement = domManipulator.findElementByXPath("/Price");
        assertNotNull(priceElement, "Price element should exist");
        assertEquals("tns:PriceType", priceElement.getAttribute("type"));

        // Verify the referenced type exists
        Element priceTypeElement = findTypeByName("PriceType");
        assertNotNull(priceTypeElement, "PriceType should exist");

        Element restriction = (Element) priceTypeElement.getElementsByTagName("xs:restriction").item(0);
        assertNotNull(restriction, "Restriction should exist");

        // Verify constraints in referenced type
        Element minExclusive = (Element) restriction.getElementsByTagName("xs:minExclusive").item(0);
        assertNotNull(minExclusive, "MinExclusive should exist");
        assertEquals("0", minExclusive.getAttribute("value"));

        Element totalDigits = (Element) restriction.getElementsByTagName("xs:totalDigits").item(0);
        assertNotNull(totalDigits, "TotalDigits should exist");
        assertEquals("10", totalDigits.getAttribute("value"));

        Element fractionDigits = (Element) restriction.getElementsByTagName("xs:fractionDigits").item(0);
        assertNotNull(fractionDigits, "FractionDigits should exist");
        assertEquals("2", fractionDigits.getAttribute("value"));
    }

    @Test
    void testDocumentationLoading() throws Exception {
        // Given - Email element with documentation
        Element emailElement = domManipulator.findElementByXPath("/Email");
        assertNotNull(emailElement, "Email element should exist");

        // Verify documentation exists
        var annotations = emailElement.getElementsByTagName("xs:annotation");
        assertEquals(1, annotations.getLength(), "Should have annotation");

        var docs = ((Element) annotations.item(0)).getElementsByTagName("xs:documentation");
        assertEquals(1, docs.getLength(), "Should have documentation");
        assertEquals("Email address element", docs.item(0).getTextContent().trim());
    }

    @Test
    void testLengthConstraintLoading() throws Exception {
        // Given - Email element with length constraints
        Element emailElement = domManipulator.findElementByXPath("/Email");
        Element simpleType = (Element) emailElement.getElementsByTagName("xs:simpleType").item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);

        // Verify length constraints
        Element minLength = (Element) restriction.getElementsByTagName("xs:minLength").item(0);
        assertNotNull(minLength, "MinLength should exist");
        assertEquals("5", minLength.getAttribute("value"));

        Element maxLength = (Element) restriction.getElementsByTagName("xs:maxLength").item(0);
        assertNotNull(maxLength, "MaxLength should exist");
        assertEquals("100", maxLength.getAttribute("value"));
    }

    @Test
    void testDomManipulatorConstraintAccess() {
        // Verify that we can access the DOM structure through XsdDomManipulator
        assertNotNull(domManipulator.getDocument(), "Document should be available");

        var simpleTypes = domManipulator.getDocument().getElementsByTagName("xs:simpleType");
        assertTrue(simpleTypes.getLength() > 0, "Should find simpleTypes");

        var elements = domManipulator.getDocument().getElementsByTagName("xs:element");
        assertTrue(elements.getLength() > 0, "Should find elements");
    }

    /**
     * Helper method to find a type definition by name
     */
    private Element findTypeByName(String typeName) {
        var simpleTypes = domManipulator.getDocument().getElementsByTagName("xs:simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (typeName.equals(simpleType.getAttribute("name"))) {
                return simpleType;
            }
        }
        return null;
    }
}