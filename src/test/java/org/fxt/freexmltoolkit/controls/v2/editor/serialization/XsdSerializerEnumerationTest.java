package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for enumeration serialization in XsdSerializer.
 *
 * @since 2.0
 */
class XsdSerializerEnumerationTest {

    @Test
    @DisplayName("should serialize element with enumerations as simpleType restriction")
    void testSerializeEnumerations() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Status");

        // Add enumerations to the element
        element.addEnumeration("active");
        element.addEnumeration("inactive");
        element.addEnumeration("pending");

        schema.addChild(element);

        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema);
        System.out.println("Serialized XSD for Status element:");
        System.out.println(xsd);

        // Verify the serialized XSD contains the enumerations
        assertTrue(xsd.contains("<xs:element name=\"Status\">"), "Should contain element declaration");
        assertTrue(xsd.contains("<xs:simpleType>"), "Should contain simpleType");
        assertTrue(xsd.contains("<xs:restriction"), "Should contain restriction");
        assertTrue(xsd.contains("<xs:enumeration value=\"active\"/>"), "Should contain active enumeration");
        assertTrue(xsd.contains("<xs:enumeration value=\"inactive\"/>"), "Should contain inactive enumeration");
        assertTrue(xsd.contains("<xs:enumeration value=\"pending\"/>"), "Should contain pending enumeration");
    }

    @Test
    @DisplayName("should serialize element with patterns")
    void testSerializePatterns() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("ZipCode");

        // Add patterns to the element
        element.addPattern("[0-9]{5}");
        element.addPattern("[A-Z]{2}[0-9]{3}");

        schema.addChild(element);

        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema);

        // Verify the serialized XSD contains the patterns
        assertTrue(xsd.contains("<xs:element name=\"ZipCode\">"), "Should contain element declaration");
        assertTrue(xsd.contains("<xs:simpleType>"), "Should contain simpleType");
        assertTrue(xsd.contains("<xs:restriction"), "Should contain restriction");
        assertTrue(xsd.contains("<xs:pattern value=\"[0-9]{5}\"/>"), "Should contain first pattern");
        assertTrue(xsd.contains("<xs:pattern value=\"[A-Z]{2}[0-9]{3}\"/>"), "Should contain second pattern");
    }

    @Test
    @DisplayName("should serialize element with both enumerations and patterns")
    void testSerializeMixedConstraints() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("ProductCode");

        // Add enumerations and patterns
        element.addEnumeration("PROD-001");
        element.addEnumeration("PROD-002");
        element.addPattern("PROD-[0-9]{3}");

        schema.addChild(element);

        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema);

        // Verify the serialized XSD contains both enumerations and patterns
        assertTrue(xsd.contains("<xs:element name=\"ProductCode\">"), "Should contain element declaration");
        assertTrue(xsd.contains("<xs:enumeration value=\"PROD-001\"/>"), "Should contain first enumeration");
        assertTrue(xsd.contains("<xs:enumeration value=\"PROD-002\"/>"), "Should contain second enumeration");
        assertTrue(xsd.contains("<xs:pattern value=\"PROD-[0-9]{3}\"/>"), "Should contain pattern");
    }

    @Test
    @DisplayName("should serialize element without constraints normally")
    void testSerializeWithoutConstraints() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("SimpleElement");
        element.setType("xs:string");

        schema.addChild(element);

        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema);

        // Verify the serialized XSD is simple (no simpleType/restriction added)
        assertTrue(xsd.contains("<xs:element name=\"SimpleElement\""), "Should contain element declaration");
        assertFalse(xsd.contains("<xs:enumeration"), "Should not contain enumerations");
        assertFalse(xsd.contains("<xs:pattern"), "Should not contain patterns");
    }

    @Test
    @DisplayName("should handle round-trip serialization (load and save)")
    void testRoundTripSerialization() throws Exception {
        // Create original XSD with enumerations
        XsdSchema schema1 = new XsdSchema();
        XsdElement element1 = new XsdElement("Status");
        element1.addEnumeration("active");
        element1.addEnumeration("inactive");
        schema1.addChild(element1);

        // Serialize to string
        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema1);

        // Parse back from string
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema2 = factory.fromString(xsd);

        // Find the Status element
        XsdElement element2 = (XsdElement) schema2.getChildren().stream()
                .filter(node -> node instanceof XsdElement && "Status".equals(node.getName()))
                .findFirst()
                .orElse(null);

        // Verify enumerations are preserved
        assertNotNull(element2, "Status element should be parsed");
        assertEquals(2, element2.getEnumerations().size(), "Should have 2 enumerations");
        assertTrue(element2.getEnumerations().contains("active"), "Should contain active");
        assertTrue(element2.getEnumerations().contains("inactive"), "Should contain inactive");
    }
}
