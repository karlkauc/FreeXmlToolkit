package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdNodeFactory.
 *
 * @since 2.0
 */
class XsdNodeFactoryTest {

    @Test
    void testParseSimpleSchema() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test"
                           elementFormDefault="qualified">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals("http://example.com/test", schema.getTargetNamespace());
        assertEquals("qualified", schema.getElementFormDefault());
        assertEquals(1, schema.getChildren().size());

        XsdNode firstChild = schema.getChildren().get(0);
        assertInstanceOf(XsdElement.class, firstChild);
        XsdElement element = (XsdElement) firstChild;
        assertEquals("root", element.getName());
        assertEquals("xs:string", element.getType());
    }

    @Test
    void testParseElementWithComplexType() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="person">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                                <xs:element name="age" type="xs:int"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals(1, schema.getChildren().size());

        XsdElement element = (XsdElement) schema.getChildren().get(0);
        assertEquals("person", element.getName());

        // Check for complexType child
        XsdNode complexTypeNode = element.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType)
                .findFirst()
                .orElse(null);

        assertNotNull(complexTypeNode);
        XsdComplexType complexType = (XsdComplexType) complexTypeNode;

        // Check for sequence child within complexType
        XsdNode sequenceNode = complexType.getChildren().stream()
                .filter(n -> n instanceof XsdSequence)
                .findFirst()
                .orElse(null);

        assertNotNull(sequenceNode);
        XsdSequence sequence = (XsdSequence) sequenceNode;
        assertEquals(2, sequence.getChildren().size());

        // Check first element in sequence
        XsdElement nameElement = (XsdElement) sequence.getChildren().get(0);
        assertEquals("name", nameElement.getName());
        assertEquals("xs:string", nameElement.getType());

        // Check second element in sequence
        XsdElement ageElement = (XsdElement) sequence.getChildren().get(1);
        assertEquals("age", ageElement.getName());
        assertEquals("xs:int", ageElement.getType());
    }

    @Test
    void testParseElementWithOccurrenceConstraints() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="items">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="item" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement itemsElement = (XsdElement) schema.getChildren().get(0);

        // Get complexType from element
        XsdComplexType complexType = (XsdComplexType) itemsElement.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType)
                .findFirst()
                .orElse(null);

        assertNotNull(complexType);

        // Get sequence from complexType
        XsdSequence sequence = (XsdSequence) complexType.getChildren().stream()
                .filter(n -> n instanceof XsdSequence)
                .findFirst()
                .orElse(null);

        assertNotNull(sequence);
        XsdElement itemElement = (XsdElement) sequence.getChildren().get(0);

        assertEquals("item", itemElement.getName());
        assertEquals(0, itemElement.getMinOccurs());
        assertEquals(XsdNode.UNBOUNDED, itemElement.getMaxOccurs());
    }

    @Test
    void testParseSimpleTypeWithRestriction() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="ZipCode">
                        <xs:restriction base="xs:string">
                            <xs:pattern value="\\d{5}"/>
                            <xs:minLength value="5"/>
                            <xs:maxLength value="5"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdSimpleType simpleType = (XsdSimpleType) schema.getChildren().get(0);
        assertEquals("ZipCode", simpleType.getName());

        XsdRestriction restriction = (XsdRestriction) simpleType.getChildren().stream()
                .filter(n -> n instanceof XsdRestriction)
                .findFirst()
                .orElse(null);

        assertNotNull(restriction);
        assertEquals("xs:string", restriction.getBase());

        // Check facets
        assertTrue(restriction.hasFacet(XsdFacetType.PATTERN));
        assertTrue(restriction.hasFacet(XsdFacetType.MIN_LENGTH));
        assertTrue(restriction.hasFacet(XsdFacetType.MAX_LENGTH));

        XsdFacet patternFacet = restriction.getFacetByType(XsdFacetType.PATTERN);
        assertEquals("\\d{5}", patternFacet.getValue());
    }

    @Test
    void testParseComplexTypeWithAttribute() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="Product">
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:int" use="required"/>
                    </xs:complexType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdComplexType complexType = (XsdComplexType) schema.getChildren().get(0);
        assertEquals("Product", complexType.getName());

        // Check for attribute child
        XsdAttribute attribute = (XsdAttribute) complexType.getChildren().stream()
                .filter(n -> n instanceof XsdAttribute)
                .findFirst()
                .orElse(null);

        assertNotNull(attribute);
        assertEquals("id", attribute.getName());
        assertEquals("xs:int", attribute.getType());
        assertEquals("required", attribute.getUse());
    }

    @Test
    void testParseElementWithIdentityConstraints() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="catalog">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="product" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="id" type="xs:string"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                        <xs:key name="productKey">
                            <xs:selector xpath="product"/>
                            <xs:field xpath="id"/>
                        </xs:key>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement catalogElement = (XsdElement) schema.getChildren().get(0);
        assertEquals("catalog", catalogElement.getName());

        // Check for key constraint
        XsdKey key = (XsdKey) catalogElement.getChildren().stream()
                .filter(n -> n instanceof XsdKey)
                .findFirst()
                .orElse(null);

        assertNotNull(key);
        assertEquals("productKey", key.getName());

        // Check selector
        XsdSelector selector = key.getSelector();
        assertNotNull(selector);
        assertEquals("product", selector.getXpath());

        // Check field
        assertEquals(1, key.getFields().size());
        XsdField field = key.getFields().get(0);
        assertEquals("id", field.getXpath());
    }

    @Test
    void testParseChoice() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="payment">
                        <xs:complexType>
                            <xs:choice>
                                <xs:element name="cash" type="xs:decimal"/>
                                <xs:element name="card" type="xs:string"/>
                            </xs:choice>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement paymentElement = (XsdElement) schema.getChildren().get(0);

        // Get complexType from element
        XsdComplexType complexType = (XsdComplexType) paymentElement.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType)
                .findFirst()
                .orElse(null);

        assertNotNull(complexType);

        // Get choice from complexType
        XsdChoice choice = (XsdChoice) complexType.getChildren().stream()
                .filter(n -> n instanceof XsdChoice)
                .findFirst()
                .orElse(null);

        assertNotNull(choice);
        assertEquals(2, choice.getChildren().size());

        XsdElement cashElement = (XsdElement) choice.getChildren().get(0);
        assertEquals("cash", cashElement.getName());

        XsdElement cardElement = (XsdElement) choice.getChildren().get(1);
        assertEquals("card", cardElement.getName());
    }

    @Test
    void testParseAnnotation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:string">
                        <xs:annotation>
                            <xs:documentation>This is a test element</xs:documentation>
                            <xs:appinfo>Some application info</xs:appinfo>
                        </xs:annotation>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement element = (XsdElement) schema.getChildren().get(0);
        assertEquals("This is a test element", element.getDocumentation());
        assertEquals("Some application info", element.getAppinfoAsString());
    }
}
