package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdDomManipulator XSD 1.1 assertion functionality
 */
class XsdDomManipulatorAssertTest {

    private XsdDomManipulator domManipulator;

    private static final String TEST_XSD_WITH_ASSERT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1"
                       elementFormDefault="qualified">
            
                <xs:element name="document">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="header">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="id" type="xs:string"/>
                                    </xs:sequence>
                                    <xs:assert test="count(id) = 1"/>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        domManipulator = new XsdDomManipulator();
        domManipulator.loadXsd(TEST_XSD_WITH_ASSERT);
    }

    @Test
    void testFindAssertElement() {
        // Given - XPath to the complexType containing the assert
        String complexTypeXPath = "/document/header";

        // When - find the complexType element
        Element complexTypeElement = domManipulator.findElementByXPath(complexTypeXPath);

        // Then - verify the element was found
        assertNotNull(complexTypeElement, "ComplexType element should be found");

        // When - try to find the assert element
        String assertXPath = "/document/header/assert";
        Node assertNode = domManipulator.findNodeByPath(assertXPath);

        // Then - verify the assert was found
        assertNotNull(assertNode, "Assert element should be found");
        assertEquals(Node.ELEMENT_NODE, assertNode.getNodeType(), "Should be an element node");

        Element assertElement = (Element) assertNode;
        assertEquals("assert", assertElement.getLocalName(), "Should be an assert element");
        assertEquals("count(id) = 1", assertElement.getAttribute("test"),
                "Assert should have correct test expression");
    }

    @Test
    void testIsXsd11() {
        // When
        String version = domManipulator.getXsdVersion();

        // Then
        assertEquals("1.1", version, "Schema version should be 1.1");
        assertTrue(domManipulator.isXsd11(), "Schema should be identified as XSD 1.1");
    }
}
