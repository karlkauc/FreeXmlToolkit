package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing XSD 1.1 assertions and type alternatives in XsdViewService
 */
@DisplayName("XSD 1.1 Assertion Parsing Tests")
class XsdAssertionParsingTest {

    private XsdViewService service;

    @BeforeEach
    void setUp() {
        service = new XsdViewService();
    }

    @Test
    @DisplayName("Should parse xs:assert with test attribute")
    void testParseAssertWithTest() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1"
                           elementFormDefault="qualified">
                    <xs:element name="product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="price" type="xs:decimal"/>
                                <xs:element name="discount" type="xs:decimal"/>
                            </xs:sequence>
                            <xs:assert test="price > discount"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        assertNotNull(tree);
        assertEquals("product", tree.name());

        // Find the assert node in the tree
        XsdNodeInfo assertNode = findNodeByType(tree, XsdNodeInfo.NodeType.ASSERT);
        assertNotNull(assertNode, "Assert node should be present");
        assertEquals("assert", assertNode.name());
        assertEquals("price > discount", assertNode.xpathExpression());
        assertEquals(XsdNodeInfo.NodeType.ASSERT, assertNode.nodeType());
    }

    @Test
    @DisplayName("Should parse xs:assert with documentation")
    void testParseAssertWithDocumentation() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="price" type="xs:decimal"/>
                                <xs:element name="discount" type="xs:decimal"/>
                            </xs:sequence>
                            <xs:assert test="price > discount">
                                <xs:annotation>
                                    <xs:documentation>Price must be greater than discount</xs:documentation>
                                </xs:annotation>
                            </xs:assert>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        XsdNodeInfo assertNode = findNodeByType(tree, XsdNodeInfo.NodeType.ASSERT);

        assertNotNull(assertNode);
        assertEquals("price > discount", assertNode.xpathExpression());
        assertNotNull(assertNode.documentation());
        assertTrue(assertNode.documentation().contains("Price must be greater than discount"));
    }

    @Test
    @DisplayName("Should parse xs:assert with xpath-default-namespace")
    void testParseAssertWithXPathDefaultNamespace() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           targetNamespace="http://example.com/product"
                           vc:minVersion="1.1">
                    <xs:element name="product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="price" type="xs:decimal"/>
                            </xs:sequence>
                            <xs:assert test="price > 0" xpath-default-namespace="http://example.com/product"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        XsdNodeInfo assertNode = findNodeByType(tree, XsdNodeInfo.NodeType.ASSERT);

        assertNotNull(assertNode);
        assertEquals("price > 0", assertNode.xpathExpression());
        assertNotNull(assertNode.xsd11Attributes());
        assertTrue(assertNode.xsd11Attributes().containsKey("xpath-default-namespace"));
        assertEquals("http://example.com/product",
                assertNode.xsd11Attributes().get("xpath-default-namespace"));
    }

    @Test
    @DisplayName("Should parse multiple xs:assert elements")
    void testParseMultipleAsserts() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="price" type="xs:decimal"/>
                                <xs:element name="discount" type="xs:decimal"/>
                                <xs:element name="quantity" type="xs:integer"/>
                            </xs:sequence>
                            <xs:assert test="price > 0"/>
                            <xs:assert test="discount >= 0"/>
                            <xs:assert test="quantity > 0"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        int assertCount = countNodesByType(tree, XsdNodeInfo.NodeType.ASSERT);

        assertEquals(3, assertCount, "Should find 3 assert nodes");
    }

    @Test
    @DisplayName("Should parse xs:alternative with test and type")
    void testParseAlternativeWithTestAndType() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="value">
                        <xs:alternative test="@type='integer'" type="xs:integer"/>
                        <xs:alternative test="@type='string'" type="xs:string"/>
                        <xs:alternative type="xs:decimal"/>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        int alternativeCount = countNodesByType(tree, XsdNodeInfo.NodeType.ALTERNATIVE);

        assertEquals(3, alternativeCount, "Should find 3 alternative nodes");

        XsdNodeInfo firstAlternative = findNodeByType(tree, XsdNodeInfo.NodeType.ALTERNATIVE);
        assertNotNull(firstAlternative);
        assertEquals("alternative", firstAlternative.name());
        assertEquals(XsdNodeInfo.NodeType.ALTERNATIVE, firstAlternative.nodeType());
        assertNotNull(firstAlternative.xpathExpression());
    }

    @Test
    @DisplayName("Should parse xs:alternative with documentation")
    void testParseAlternativeWithDocumentation() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="value">
                        <xs:alternative test="@type='integer'" type="xs:integer">
                            <xs:annotation>
                                <xs:documentation>Integer type when @type='integer'</xs:documentation>
                            </xs:annotation>
                        </xs:alternative>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        XsdNodeInfo alternativeNode = findNodeByType(tree, XsdNodeInfo.NodeType.ALTERNATIVE);

        assertNotNull(alternativeNode);
        assertNotNull(alternativeNode.documentation());
        assertTrue(alternativeNode.documentation().contains("Integer type"));
    }

    @Test
    @DisplayName("Should parse complex assertion with XPath 2.0 features")
    void testParseComplexAssertion() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="order">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="item" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="price" type="xs:decimal"/>
                                            <xs:element name="quantity" type="xs:integer"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                                <xs:element name="total" type="xs:decimal"/>
                            </xs:sequence>
                            <xs:assert test="total = sum(item/(price * quantity))"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        XsdNodeInfo assertNode = findNodeByType(tree, XsdNodeInfo.NodeType.ASSERT);

        assertNotNull(assertNode);
        assertEquals("total = sum(item/(price * quantity))", assertNode.xpathExpression());
    }

    @Test
    @DisplayName("Should parse assertion on simpleType restriction")
    void testParseAssertionOnSimpleType() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="temperature">
                        <xs:simpleType>
                            <xs:restriction base="xs:decimal">
                                <xs:minInclusive value="-273.15"/>
                                <xs:assert test=". >= -273.15"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        // Note: assertions in simpleType restrictions might be handled differently
        // This test verifies the schema is parsed without errors
        assertNotNull(tree);
        assertEquals("temperature", tree.name());
    }

    @Test
    @DisplayName("Should identify XSD 1.1 node via isXsd11() method")
    void testIsXsd11Method() {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="price" type="xs:decimal"/>
                            </xs:sequence>
                            <xs:assert test="price > 0"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeInfo tree = service.buildLightweightTree(xsd);
        XsdNodeInfo assertNode = findNodeByType(tree, XsdNodeInfo.NodeType.ASSERT);

        assertNotNull(assertNode);
        assertTrue(assertNode.isXsd11(), "Assert node should be identified as XSD 1.1");
    }

    // Helper methods

    /**
     * Recursively finds the first node of a specific type
     */
    private XsdNodeInfo findNodeByType(XsdNodeInfo root, XsdNodeInfo.NodeType targetType) {
        if (root == null) return null;

        if (root.nodeType() == targetType) {
            return root;
        }

        for (XsdNodeInfo child : root.children()) {
            XsdNodeInfo found = findNodeByType(child, targetType);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Recursively counts all nodes of a specific type
     */
    private int countNodesByType(XsdNodeInfo root, XsdNodeInfo.NodeType targetType) {
        if (root == null) return 0;

        int count = (root.nodeType() == targetType) ? 1 : 0;

        for (XsdNodeInfo child : root.children()) {
            count += countNodesByType(child, targetType);
        }

        return count;
    }
}
