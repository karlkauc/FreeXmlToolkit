package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for XSD parsing and serialization.
 * Tests ensure that: Parse(XSD) → Serialize → Parse → Serialize produces identical results.
 *
 * @since 2.0
 */
class XsdRoundTripTest {

    /**
     * Helper method to normalize XML strings for comparison.
     * Removes extra whitespace and normalizes structure.
     */
    private String normalizeXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        doc.normalize();

        // Simple normalization: remove extra whitespace
        return xml.replaceAll(">\\s+<", "><")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    @Test
    void testRoundTripSimpleSchema() throws Exception {
        String originalXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test"
                           elementFormDefault="qualified">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        // Parse original
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema1 = factory.fromString(originalXsd);

        // Serialize
        XsdSerializer serializer = new XsdSerializer();
        String serialized1 = serializer.serialize(schema1);

        // Parse again
        XsdSchema schema2 = factory.fromString(serialized1);

        // Serialize again
        String serialized2 = serializer.serialize(schema2);

        // Compare
        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        // Verify basic properties are preserved
        assertEquals(schema1.getTargetNamespace(), schema2.getTargetNamespace());
        assertEquals(schema1.getElementFormDefault(), schema2.getElementFormDefault());
        assertEquals(schema1.getChildren().size(), schema2.getChildren().size());

        System.out.println("✅ Round-trip test passed for simple schema");
    }

    @Test
    void testRoundTripComplexTypeWithSequence() throws Exception {
        String originalXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="person">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                                <xs:element name="age" type="xs:int" minOccurs="0"/>
                            </xs:sequence>
                            <xs:attribute name="id" type="xs:int" use="required"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSerializer serializer = new XsdSerializer();

        // First round-trip
        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        // Second round-trip
        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        // Compare
        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        System.out.println("✅ Round-trip test passed for complexType with sequence and attribute");
    }

    @Test
    void testRoundTripSimpleTypeWithRestriction() throws Exception {
        String originalXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="ZipCode">
                        <xs:restriction base="xs:string">
                            <xs:pattern value="\\\\d{5}"/>
                            <xs:minLength value="5"/>
                            <xs:maxLength value="5"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        // Verify restriction and facets are preserved
        XsdSimpleType simpleType1 = (XsdSimpleType) schema1.getChildren().get(0);
        XsdSimpleType simpleType2 = (XsdSimpleType) schema2.getChildren().get(0);

        assertEquals(simpleType1.getName(), simpleType2.getName());

        XsdRestriction restriction1 = (XsdRestriction) simpleType1.getChildren().stream()
                .filter(n -> n instanceof XsdRestriction)
                .findFirst()
                .orElse(null);

        XsdRestriction restriction2 = (XsdRestriction) simpleType2.getChildren().stream()
                .filter(n -> n instanceof XsdRestriction)
                .findFirst()
                .orElse(null);

        assertNotNull(restriction1);
        assertNotNull(restriction2);
        assertEquals(restriction1.getFacets().size(), restriction2.getFacets().size());

        System.out.println("✅ Round-trip test passed for simpleType with restriction and facets");
    }

    @Test
    void testRoundTripWithChoice() throws Exception {
        String originalXsd = """
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
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        System.out.println("✅ Round-trip test passed for choice compositor");
    }

    @Test
    void testRoundTripWithAnnotation() throws Exception {
        String originalXsd = """
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
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        // Verify annotations are preserved
        XsdElement element1 = (XsdElement) schema1.getChildren().get(0);
        XsdElement element2 = (XsdElement) schema2.getChildren().get(0);

        assertEquals(element1.getDocumentation(), element2.getDocumentation());
        assertEquals(element1.getAppinfoAsString(), element2.getAppinfoAsString());

        System.out.println("✅ Round-trip test passed for annotations");
    }

    @Test
    void testRoundTripWithIdentityConstraints() throws Exception {
        String originalXsd = """
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
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        System.out.println("✅ Round-trip test passed for identity constraints (key)");
    }

    @Test
    void testRoundTripWithComplexContent() throws Exception {
        String originalXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="Address">
                        <xs:sequence>
                            <xs:element name="street" type="xs:string"/>
                            <xs:element name="city" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                    <xs:complexType name="USAddress">
                        <xs:complexContent>
                            <xs:extension base="Address">
                                <xs:sequence>
                                    <xs:element name="state" type="xs:string"/>
                                    <xs:element name="zip" type="xs:string"/>
                                </xs:sequence>
                            </xs:extension>
                        </xs:complexContent>
                    </xs:complexType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        System.out.println("✅ Round-trip test passed for complexContent with extension");
    }

    @Test
    void testRoundTripWithListAndUnion() throws Exception {
        String originalXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="StateList">
                        <xs:list itemType="xs:string"/>
                    </xs:simpleType>
                    <xs:simpleType name="StringOrInt">
                        <xs:union memberTypes="xs:string xs:int"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSerializer serializer = new XsdSerializer();

        XsdSchema schema1 = factory.fromString(originalXsd);
        String serialized1 = serializer.serialize(schema1);

        XsdSchema schema2 = factory.fromString(serialized1);
        String serialized2 = serializer.serialize(schema2);

        assertEquals(normalizeXml(serialized1), normalizeXml(serialized2),
                    "Round-trip serialization should produce identical results");

        System.out.println("✅ Round-trip test passed for list and union types");
    }
}
