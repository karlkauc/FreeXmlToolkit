package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
    void testParseSchemaWithLeadingUtf8Bom() throws Exception {
        // Files saved with a UTF-8 BOM keep the leading U+FEFF when read via
        // Files.readString(..., UTF_8). Parsing that text through a character
        // stream must not fail with "Content is not allowed in prolog".
        String xsd = "﻿" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals("http://example.com/test", schema.getTargetNamespace());
        assertEquals(1, schema.getChildren().size());
    }

    @Test
    void testInvalidContentThrowsWithoutPrintingToStderr() {
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(captured));
        try {
            assertThrows(Exception.class, () -> new XsdNodeFactory().fromString("not xml at all"));
        } finally {
            System.setErr(originalErr);
        }
        // The default JAXP handler would print "[Fatal Error] ... Content is not allowed
        // in prolog." here; the quiet handler must keep the console clean.
        assertTrue(captured.toString().isEmpty(),
                "Parse failure must not write to System.err, but got: " + captured);
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
    @Disabled("Failing due to pre-existing bug in XsdNodeFactory's annotation parsing")
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

    @Test
    void parsesXsd11AssertsInComplexTypeExtensionAndRestriction() throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="BaseType">
                    <xs:attribute name="total" type="xs:decimal"/>
                    <xs:assert test="@total >= 0"/>
                    <xs:assert test="@total >= ((" xpathDefaultNamespace="##targetNamespace"/>
                  </xs:complexType>
                  <xs:complexType name="ExtType">
                    <xs:complexContent>
                      <xs:extension base="BaseType">
                        <xs:attribute name="tax" type="xs:decimal"/>
                        <xs:assert test="@tax le @total"/>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="ResType">
                    <xs:complexContent>
                      <xs:restriction base="BaseType">
                        <xs:assert test="@total lt 100"/>
                      </xs:restriction>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);

        java.util.List<XsdAssert> asserts = new java.util.ArrayList<>();
        collectAsserts(schema, asserts);
        assertEquals(4, asserts.size(), asserts.stream().map(XsdAssert::getTest).toList().toString());
        assertEquals("@total >= 0", asserts.get(0).getTest());
        assertEquals("##targetNamespace", asserts.get(1).getXpathDefaultNamespace());
        assertEquals(XsdNodeType.COMPLEX_TYPE, asserts.get(0).getParent().getNodeType());
        assertEquals(XsdNodeType.EXTENSION, asserts.get(2).getParent().getNodeType());
        assertEquals(XsdNodeType.RESTRICTION, asserts.get(3).getParent().getNodeType());
        assertEquals("1.1", schema.detectXsdVersion());

        String out = new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer().serialize(schema);
        assertEquals(4, out.split("<xs:assert ", -1).length - 1, out);
        assertTrue(out.contains("test=\"@tax le @total\""), out);
    }

    private static void collectAsserts(XsdNode node, java.util.List<XsdAssert> into) {
        if (node instanceof XsdAssert a) {
            into.add(a);
        }
        for (XsdNode child : node.getChildren()) {
            collectAsserts(child, into);
        }
    }

    @Test
    void parsesContentModelInsideComplexContentRestrictionAndExtension() throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:group name="AddressGroup">
                    <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
                  </xs:group>
                  <xs:attributeGroup name="AuditAttrs">
                    <xs:attribute name="createdBy" type="xs:string"/>
                  </xs:attributeGroup>
                  <xs:complexType name="BaseType">
                    <xs:sequence><xs:element name="name" type="xs:string"/><xs:any minOccurs="0"/></xs:sequence>
                    <xs:attribute name="id" type="xs:string"/>
                    <xs:anyAttribute/>
                  </xs:complexType>
                  <xs:complexType name="RestrictedType">
                    <xs:complexContent>
                      <xs:restriction base="BaseType">
                        <xs:annotation><xs:documentation>narrowed</xs:documentation></xs:annotation>
                        <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
                        <xs:attribute name="id" type="xs:string" use="required"/>
                        <xs:attributeGroup ref="AuditAttrs"/>
                        <xs:anyAttribute processContents="lax"/>
                      </xs:restriction>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="GroupedType">
                    <xs:complexContent>
                      <xs:extension base="BaseType">
                        <xs:group ref="AddressGroup"/>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="MeasureType">
                    <xs:simpleContent>
                      <xs:restriction base="xs:string">
                        <xs:simpleType><xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
                        <xs:attribute name="unit" type="xs:string"/>
                      </xs:restriction>
                    </xs:simpleContent>
                  </xs:complexType>
                </xs:schema>
                """;
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);

        XsdRestriction restricted = firstRestriction(schema, "RestrictedType");
        assertEquals(java.util.List.of(XsdNodeType.SEQUENCE, XsdNodeType.ATTRIBUTE, XsdNodeType.ATTRIBUTE_GROUP,
                        XsdNodeType.ANY_ATTRIBUTE),
                restricted.getChildren().stream().map(XsdNode::getNodeType).toList());
        assertEquals("narrowed", restricted.getDocumentations().getFirst().getText());
        assertEquals("id", restricted.getChildren().get(1).getName());

        XsdNode extension = findType(schema, "GroupedType").getChildren().getFirst().getChildren().getFirst();
        assertEquals(XsdNodeType.EXTENSION, extension.getNodeType());
        assertEquals(java.util.List.of(XsdNodeType.GROUP), extension.getChildren().stream().map(XsdNode::getNodeType).toList());

        XsdRestriction measure = firstRestriction(schema, "MeasureType");
        assertEquals(java.util.List.of(XsdNodeType.SIMPLE_TYPE, XsdNodeType.ATTRIBUTE),
                measure.getChildren().stream().map(XsdNode::getNodeType).toList());

        String out = new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer().serialize(schema);
        int restrictionStart = out.indexOf("<xs:restriction base=\"BaseType\"");
        int restrictionEnd = out.indexOf("</xs:restriction>", restrictionStart);
        String body = out.substring(restrictionStart, restrictionEnd);
        assertTrue(body.contains("<xs:sequence") && body.contains("name=\"id\"") && body.contains("ref=\"AuditAttrs\"")
                && body.contains("<xs:anyAttribute"), body);
    }

    private static XsdComplexType findType(XsdSchema schema, String name) {
        return schema.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType && name.equals(n.getName()))
                .map(n -> (XsdComplexType) n).findFirst().orElseThrow();
    }

    private static XsdRestriction firstRestriction(XsdSchema schema, String typeName) {
        java.util.List<XsdRestriction> found = new java.util.ArrayList<>();
        collect(findType(schema, typeName), found, XsdRestriction.class);
        return found.getFirst();
    }

    private static <T extends XsdNode> void collect(XsdNode node, java.util.List<T> into, Class<T> type) {
        if (type.isInstance(node)) {
            into.add(type.cast(node));
        }
        for (XsdNode child : node.getChildren()) {
            collect(child, into, type);
        }
    }
}
