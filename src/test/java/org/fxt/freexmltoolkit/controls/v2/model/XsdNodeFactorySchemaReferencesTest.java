package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdNodeFactory parsing of schema references:
 * import, include, redefine, and override.
 *
 * @since 2.0
 */
class XsdNodeFactorySchemaReferencesTest {

    @Test
    void testParseImportWithNamespaceAndSchemaLocation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/other"
                               schemaLocation="other.xsd"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema, "Schema should be parsed");
        assertEquals(1, schema.getChildren().size(), "Schema should have 1 child");

        XsdNode child = schema.getChildren().get(0);
        assertInstanceOf(XsdImport.class, child, "Child should be XsdImport");

        XsdImport xsdImport = (XsdImport) child;
        assertEquals("http://example.com/other", xsdImport.getNamespace(),
                    "Import namespace should match");
        assertEquals("other.xsd", xsdImport.getSchemaLocation(),
                    "Import schemaLocation should match");

        System.out.println("✅ testParseImportWithNamespaceAndSchemaLocation passed");
    }

    @Test
    void testParseImportWithOnlyNamespace() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:import namespace="http://www.w3.org/XML/1998/namespace"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals(1, schema.getChildren().size());

        XsdImport xsdImport = (XsdImport) schema.getChildren().get(0);
        assertEquals("http://www.w3.org/XML/1998/namespace", xsdImport.getNamespace());
        assertNull(xsdImport.getSchemaLocation(), "SchemaLocation should be null when not specified");

        System.out.println("✅ testParseImportWithOnlyNamespace passed");
    }

    @Test
    void testParseImportWithAnnotation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:import namespace="http://example.com/other" schemaLocation="other.xsd">
                        <xs:annotation>
                            <xs:documentation>Import from other schema</xs:documentation>
                        </xs:annotation>
                    </xs:import>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdImport xsdImport = (XsdImport) schema.getChildren().get(0);
        assertEquals("http://example.com/other", xsdImport.getNamespace());
        assertEquals("other.xsd", xsdImport.getSchemaLocation());
        assertEquals("Import from other schema", xsdImport.getDocumentation());

        System.out.println("✅ testParseImportWithAnnotation passed");
    }

    @Test
    void testParseIncludeWithSchemaLocation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test">
                    <xs:include schemaLocation="common.xsd"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals(1, schema.getChildren().size());

        XsdNode child = schema.getChildren().get(0);
        assertInstanceOf(XsdInclude.class, child, "Child should be XsdInclude");

        XsdInclude xsdInclude = (XsdInclude) child;
        assertEquals("common.xsd", xsdInclude.getSchemaLocation());

        System.out.println("✅ testParseIncludeWithSchemaLocation passed");
    }

    @Test
    void testParseIncludeWithAnnotation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd">
                        <xs:annotation>
                            <xs:documentation>Common type definitions</xs:documentation>
                        </xs:annotation>
                    </xs:include>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdInclude xsdInclude = (XsdInclude) schema.getChildren().get(0);
        assertEquals("types.xsd", xsdInclude.getSchemaLocation());
        assertEquals("Common type definitions", xsdInclude.getDocumentation());

        System.out.println("✅ testParseIncludeWithAnnotation passed");
    }

    @Test
    void testParseRedefineWithSimpleType() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:redefine schemaLocation="base.xsd">
                        <xs:simpleType name="StringType">
                            <xs:restriction base="xs:string">
                                <xs:maxLength value="100"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:redefine>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals(1, schema.getChildren().size());

        XsdNode child = schema.getChildren().get(0);
        assertInstanceOf(XsdRedefine.class, child, "Child should be XsdRedefine");

        XsdRedefine xsdRedefine = (XsdRedefine) child;
        assertEquals("base.xsd", xsdRedefine.getSchemaLocation());
        assertEquals(1, xsdRedefine.getChildren().size(), "Redefine should have 1 child (simpleType)");

        XsdNode simpleType = xsdRedefine.getChildren().get(0);
        assertInstanceOf(XsdSimpleType.class, simpleType, "Child should be XsdSimpleType");
        assertEquals("StringType", ((XsdSimpleType) simpleType).getName());

        System.out.println("✅ testParseRedefineWithSimpleType passed");
    }

    @Test
    void testParseRedefineWithComplexType() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:redefine schemaLocation="base.xsd">
                        <xs:complexType name="PersonType">
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:redefine>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdRedefine xsdRedefine = (XsdRedefine) schema.getChildren().get(0);
        assertEquals("base.xsd", xsdRedefine.getSchemaLocation());
        assertEquals(1, xsdRedefine.getChildren().size());

        XsdComplexType complexType = (XsdComplexType) xsdRedefine.getChildren().get(0);
        assertEquals("PersonType", complexType.getName());

        System.out.println("✅ testParseRedefineWithComplexType passed");
    }

    @Test
    void testParseRedefineWithGroup() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:redefine schemaLocation="base.xsd">
                        <xs:group name="CommonElements">
                            <xs:sequence>
                                <xs:element name="id" type="xs:string"/>
                            </xs:sequence>
                        </xs:group>
                    </xs:redefine>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdRedefine xsdRedefine = (XsdRedefine) schema.getChildren().get(0);
        assertEquals(1, xsdRedefine.getChildren().size());

        XsdGroup group = (XsdGroup) xsdRedefine.getChildren().get(0);
        assertEquals("CommonElements", group.getName());

        System.out.println("✅ testParseRedefineWithGroup passed");
    }

    @Test
    void testParseRedefineWithAttributeGroup() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:redefine schemaLocation="base.xsd">
                        <xs:attributeGroup name="CommonAttributes">
                            <xs:attribute name="id" type="xs:string" use="required"/>
                        </xs:attributeGroup>
                    </xs:redefine>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdRedefine xsdRedefine = (XsdRedefine) schema.getChildren().get(0);
        assertEquals(1, xsdRedefine.getChildren().size());

        XsdAttributeGroup attributeGroup = (XsdAttributeGroup) xsdRedefine.getChildren().get(0);
        assertEquals("CommonAttributes", attributeGroup.getName());

        System.out.println("✅ testParseRedefineWithAttributeGroup passed");
    }

    @Test
    void testParseRedefineWithAnnotation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:redefine schemaLocation="base.xsd">
                        <xs:annotation>
                            <xs:documentation>Redefining base types</xs:documentation>
                        </xs:annotation>
                        <xs:simpleType name="StringType">
                            <xs:restriction base="xs:string"/>
                        </xs:simpleType>
                    </xs:redefine>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdRedefine xsdRedefine = (XsdRedefine) schema.getChildren().get(0);
        assertEquals("Redefining base types", xsdRedefine.getDocumentation());
        assertEquals(1, xsdRedefine.getChildren().size(), "Should have 1 simpleType child");

        System.out.println("✅ testParseRedefineWithAnnotation passed");
    }

    @Test
    void testParseOverrideWithSchemaLocation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test">
                    <xs:override schemaLocation="base.xsd">
                        <xs:simpleType name="StringType">
                            <xs:restriction base="xs:string">
                                <xs:maxLength value="200"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:override>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertNotNull(schema);
        assertEquals(1, schema.getChildren().size());

        XsdNode child = schema.getChildren().get(0);
        assertInstanceOf(XsdOverride.class, child, "Child should be XsdOverride");

        XsdOverride xsdOverride = (XsdOverride) child;
        assertEquals("base.xsd", xsdOverride.getSchemaLocation());
        assertEquals(1, xsdOverride.getChildren().size());

        XsdSimpleType simpleType = (XsdSimpleType) xsdOverride.getChildren().get(0);
        assertEquals("StringType", simpleType.getName());

        System.out.println("✅ testParseOverrideWithSchemaLocation passed");
    }

    @Test
    void testParseOverrideWithComplexType() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:override schemaLocation="base.xsd">
                        <xs:complexType name="PersonType">
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                                <xs:element name="email" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:override>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdOverride xsdOverride = (XsdOverride) schema.getChildren().get(0);
        assertEquals(1, xsdOverride.getChildren().size());

        XsdComplexType complexType = (XsdComplexType) xsdOverride.getChildren().get(0);
        assertEquals("PersonType", complexType.getName());

        System.out.println("✅ testParseOverrideWithComplexType passed");
    }

    @Test
    void testParseOverrideWithElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:override schemaLocation="base.xsd">
                        <xs:element name="root" type="xs:string"/>
                    </xs:override>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdOverride xsdOverride = (XsdOverride) schema.getChildren().get(0);
        assertEquals(1, xsdOverride.getChildren().size());

        XsdElement element = (XsdElement) xsdOverride.getChildren().get(0);
        assertEquals("root", element.getName());
        assertEquals("xs:string", element.getType());

        System.out.println("✅ testParseOverrideWithElement passed");
    }

    @Test
    void testParseOverrideWithAnnotation() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:override schemaLocation="base.xsd">
                        <xs:annotation>
                            <xs:documentation>XSD 1.1 override mechanism</xs:documentation>
                        </xs:annotation>
                        <xs:simpleType name="StringType">
                            <xs:restriction base="xs:string"/>
                        </xs:simpleType>
                    </xs:override>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdOverride xsdOverride = (XsdOverride) schema.getChildren().get(0);
        assertEquals("XSD 1.1 override mechanism", xsdOverride.getDocumentation());
        assertEquals(1, xsdOverride.getChildren().size());

        System.out.println("✅ testParseOverrideWithAnnotation passed");
    }

    @Test
    void testParseMultipleImports() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:import namespace="http://example.com/one" schemaLocation="one.xsd"/>
                    <xs:import namespace="http://example.com/two" schemaLocation="two.xsd"/>
                    <xs:import namespace="http://example.com/three" schemaLocation="three.xsd"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertEquals(3, schema.getChildren().size(), "Schema should have 3 imports");

        XsdImport import1 = (XsdImport) schema.getChildren().get(0);
        XsdImport import2 = (XsdImport) schema.getChildren().get(1);
        XsdImport import3 = (XsdImport) schema.getChildren().get(2);

        assertEquals("http://example.com/one", import1.getNamespace());
        assertEquals("http://example.com/two", import2.getNamespace());
        assertEquals("http://example.com/three", import3.getNamespace());

        System.out.println("✅ testParseMultipleImports passed");
    }

    @Test
    void testParseMixedSchemaReferences() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test">
                    <xs:import namespace="http://example.com/other" schemaLocation="other.xsd"/>
                    <xs:include schemaLocation="common.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertEquals(3, schema.getChildren().size(), "Schema should have 3 children");

        assertInstanceOf(XsdImport.class, schema.getChildren().get(0));
        assertInstanceOf(XsdInclude.class, schema.getChildren().get(1));
        assertInstanceOf(XsdElement.class, schema.getChildren().get(2));

        System.out.println("✅ testParseMixedSchemaReferences passed");
    }
}
