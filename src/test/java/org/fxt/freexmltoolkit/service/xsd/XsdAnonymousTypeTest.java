package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that anonymous (inline) complexTypes and simpleTypes
 * do not get a name attribute during parsing and serialization.
 *
 * <p>Bug: XsdModelAdapter was assigning default names ("complexType", "simpleType")
 * to anonymous types, causing them to be serialized with incorrect name attributes.</p>
 */
class XsdAnonymousTypeTest {

    @Test
    void testAnonymousComplexTypePreserved() throws XsdParseException {
        // XSD with anonymous complexType (no name attribute)
        String xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="FundsXML4">
                <xs:annotation>
                  <xs:documentation>Root element</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="ControlData" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        // Parse
        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(xsd, null, XsdParseOptions.defaults());
        XsdSchema schema = parsed.toXsdModel();

        assertNotNull(schema);

        // Get the FundsXML4 element
        List<XsdNode> children = schema.getChildren();
        assertEquals(1, children.size(), "Should have one root element");

        XsdElement element = (XsdElement) children.get(0);
        assertEquals("FundsXML4", element.getName());

        // Check that the inline complexType has NO name
        List<XsdNode> elementChildren = element.getChildren();
        assertTrue(elementChildren.stream().anyMatch(n -> n instanceof XsdComplexType),
            "Element should have an inline complexType");

        XsdComplexType inlineComplexType = (XsdComplexType) elementChildren.stream()
            .filter(n -> n instanceof XsdComplexType)
            .findFirst()
            .orElseThrow();

        assertNull(inlineComplexType.getName(),
            "Anonymous/inline complexType should have null name");

        // Serialize back to XSD
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(schema);

        // Verify the serialized XSD does NOT contain name="complexType"
        assertFalse(serialized.contains("<xs:complexType name=\"complexType\""),
            "Serialized XSD should not have name=\"complexType\" for anonymous type");

        assertTrue(serialized.contains("<xs:complexType>"),
            "Serialized XSD should have anonymous <xs:complexType> without name attribute");

        System.out.println("Serialized XSD:\n" + serialized);
    }

    @Test
    void testAnonymousSimpleTypePreserved() throws XsdParseException {
        // XSD with anonymous simpleType (no name attribute)
        String xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Age">
                <xs:simpleType>
                  <xs:restriction base="xs:integer">
                    <xs:minInclusive value="0"/>
                    <xs:maxInclusive value="150"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:element>
            </xs:schema>
            """;

        // Parse
        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(xsd, null, XsdParseOptions.defaults());
        XsdSchema schema = parsed.toXsdModel();

        // Serialize back to XSD
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(schema);

        // Verify the serialized XSD does NOT contain name="simpleType"
        assertFalse(serialized.contains("<xs:simpleType name=\"simpleType\""),
            "Serialized XSD should not have name=\"simpleType\" for anonymous type");

        assertTrue(serialized.contains("<xs:simpleType>"),
            "Serialized XSD should have anonymous <xs:simpleType> without name attribute");

        System.out.println("Serialized XSD:\n" + serialized);
    }

    @Test
    void testNamedComplexTypePreserved() throws XsdParseException {
        // XSD with NAMED complexType (has name attribute)
        String xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        // Parse
        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(xsd, null, XsdParseOptions.defaults());
        XsdSchema schema = parsed.toXsdModel();

        // Get the complexType
        List<XsdNode> children = schema.getChildren();
        XsdComplexType complexType = (XsdComplexType) children.get(0);

        assertEquals("PersonType", complexType.getName(),
            "Named complexType should preserve its name");

        // Serialize back to XSD
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(schema);

        // Verify the serialized XSD contains name="PersonType"
        assertTrue(serialized.contains("<xs:complexType name=\"PersonType\">"),
            "Serialized XSD should preserve name=\"PersonType\"");

        System.out.println("Serialized XSD:\n" + serialized);
    }
}
