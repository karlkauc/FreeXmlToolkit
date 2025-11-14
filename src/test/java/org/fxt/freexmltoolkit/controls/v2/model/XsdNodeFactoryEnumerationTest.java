package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for enumeration extraction in XsdNodeFactory.
 *
 * @since 2.0
 */
class XsdNodeFactoryEnumerationTest {

    private XsdSchema parseXsdFromString(String xsdContent) throws Exception {
        XsdNodeFactory nodeFactory = new XsdNodeFactory();
        return nodeFactory.fromString(xsdContent);
    }

    @Test
    @DisplayName("should extract enumerations from element with simple type restriction")
    void testExtractEnumerationsFromElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Status">
                        <xs:simpleType>
                            <xs:restriction base="xs:string">
                                <xs:enumeration value="active"/>
                                <xs:enumeration value="inactive"/>
                                <xs:enumeration value="pending"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
                """;

        XsdSchema schema = parseXsdFromString(xsd);
        XsdElement statusElement = (XsdElement) schema.getChildren().stream()
                .filter(node -> node instanceof XsdElement && "Status".equals(node.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(statusElement, "Status element should be parsed");
        assertEquals(3, statusElement.getEnumerations().size(), "Should have 3 enumerations");
        assertTrue(statusElement.getEnumerations().contains("active"));
        assertTrue(statusElement.getEnumerations().contains("inactive"));
        assertTrue(statusElement.getEnumerations().contains("pending"));
    }

    @Test
    @DisplayName("should extract patterns from element with simple type restriction")
    void testExtractPatternsFromElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="ZipCode">
                        <xs:simpleType>
                            <xs:restriction base="xs:string">
                                <xs:pattern value="[0-9]{5}"/>
                                <xs:pattern value="[A-Z]{2}[0-9]{3}"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
                """;

        XsdSchema schema = parseXsdFromString(xsd);
        XsdElement zipElement = (XsdElement) schema.getChildren().stream()
                .filter(node -> node instanceof XsdElement && "ZipCode".equals(node.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(zipElement, "ZipCode element should be parsed");
        assertEquals(2, zipElement.getPatterns().size(), "Should have 2 patterns");
        assertTrue(zipElement.getPatterns().contains("[0-9]{5}"));
        assertTrue(zipElement.getPatterns().contains("[A-Z]{2}[0-9]{3}"));
    }

    @Test
    @DisplayName("should extract both enumerations and patterns from element")
    void testExtractMixedConstraints() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="ProductCode">
                        <xs:simpleType>
                            <xs:restriction base="xs:string">
                                <xs:enumeration value="PROD-001"/>
                                <xs:enumeration value="PROD-002"/>
                                <xs:pattern value="PROD-[0-9]{3}"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
                """;

        XsdSchema schema = parseXsdFromString(xsd);
        XsdElement productElement = (XsdElement) schema.getChildren().stream()
                .filter(node -> node instanceof XsdElement && "ProductCode".equals(node.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(productElement, "ProductCode element should be parsed");
        assertEquals(2, productElement.getEnumerations().size(), "Should have 2 enumerations");
        assertEquals(1, productElement.getPatterns().size(), "Should have 1 pattern");
        assertTrue(productElement.getEnumerations().contains("PROD-001"));
        assertTrue(productElement.getEnumerations().contains("PROD-002"));
        assertTrue(productElement.getPatterns().contains("PROD-[0-9]{3}"));
    }

    @Test
    @DisplayName("should handle element without constraints")
    void testElementWithoutConstraints() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="SimpleElement" type="xs:string"/>
                </xs:schema>
                """;

        XsdSchema schema = parseXsdFromString(xsd);
        XsdElement simpleElement = (XsdElement) schema.getChildren().stream()
                .filter(node -> node instanceof XsdElement && "SimpleElement".equals(node.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(simpleElement, "SimpleElement should be parsed");
        assertEquals(0, simpleElement.getEnumerations().size(), "Should have no enumerations");
        assertEquals(0, simpleElement.getPatterns().size(), "Should have no patterns");
        assertEquals(0, simpleElement.getAssertions().size(), "Should have no assertions");
    }
}
