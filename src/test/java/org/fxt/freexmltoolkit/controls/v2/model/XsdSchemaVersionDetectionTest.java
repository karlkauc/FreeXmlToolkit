package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for XSD version detection in XsdSchema.
 *
 * @since 2.0
 */
class XsdSchemaVersionDetectionTest {

    @Test
    void testSimpleSchemaIsXsd10() {
        // Create a simple XSD 1.0 schema
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        XsdElement element = new XsdElement("TestElement");
        element.setType("xs:string");
        schema.addChild(element);

        assertEquals("1.0", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithComplexTypeIsXsd10() {
        XsdSchema schema = new XsdSchema();

        XsdComplexType complexType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("Name");
        name.setType("xs:string");
        sequence.addChild(name);

        XsdElement age = new XsdElement("Age");
        age.setType("xs:int");
        sequence.addChild(age);

        complexType.addChild(sequence);
        schema.addChild(complexType);

        assertEquals("1.0", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithAssertIsXsd11() {
        XsdSchema schema = new XsdSchema();

        XsdComplexType complexType = new XsdComplexType("PersonType");

        // Add an xs:assert (XSD 1.1 feature)
        XsdAssert assertion = new XsdAssert();
        assertion.setTest("@age >= 0");
        complexType.addChild(assertion);

        schema.addChild(complexType);

        assertEquals("1.1", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithOverrideIsXsd11() {
        XsdSchema schema = new XsdSchema();

        // Add an xs:override (XSD 1.1 feature)
        XsdOverride override = new XsdOverride();
        override.setSchemaLocation("other.xsd");
        schema.addChild(override);

        assertEquals("1.1", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithOpenContentIsXsd11() {
        XsdSchema schema = new XsdSchema();

        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        // Add xs:openContent (XSD 1.1 feature)
        XsdOpenContent openContent = new XsdOpenContent();
        complexType.addChild(openContent);

        schema.addChild(complexType);

        assertEquals("1.1", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithAlternativeIsXsd11() {
        XsdSchema schema = new XsdSchema();

        XsdElement element = new XsdElement("Value");

        // Add xs:alternative (XSD 1.1 feature)
        XsdAlternative alternative = new XsdAlternative();
        alternative.setTest("@type = 'string'");
        alternative.setTypeAttribute("xs:string");
        element.addChild(alternative);

        schema.addChild(element);

        assertEquals("1.1", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithAssertionFacetIsXsd11() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType simpleType = new XsdSimpleType("PositiveInt");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:int");

        // Add assertion facet (XSD 1.1 feature)
        XsdFacet assertionFacet = new XsdFacet(XsdFacetType.ASSERTION, ". >= 0");
        restriction.addChild(assertionFacet);

        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        assertEquals("1.1", schema.detectXsdVersion());
    }

    @Test
    void testSchemaWithPatternFacetIsXsd10() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType simpleType = new XsdSimpleType("PhoneNumber");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        // Pattern facet is XSD 1.0
        XsdFacet patternFacet = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{3}-[0-9]{4}");
        restriction.addChild(patternFacet);

        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        assertEquals("1.0", schema.detectXsdVersion());
    }

    @Test
    void testNestedXsd11FeatureDetection() {
        // Test that XSD 1.1 features are detected even when deeply nested
        XsdSchema schema = new XsdSchema();

        XsdComplexType outerType = new XsdComplexType("OuterType");
        XsdSequence sequence = new XsdSequence();

        XsdElement nestedElement = new XsdElement("Nested");
        XsdComplexType innerType = new XsdComplexType("");
        XsdSequence innerSequence = new XsdSequence();

        XsdElement deepElement = new XsdElement("Deep");

        // Add an alternative deep in the tree
        XsdAlternative alternative = new XsdAlternative();
        alternative.setTest("@flag = 'true'");
        deepElement.addChild(alternative);

        innerSequence.addChild(deepElement);
        innerType.addChild(innerSequence);
        nestedElement.addChild(innerType);
        sequence.addChild(nestedElement);
        outerType.addChild(sequence);
        schema.addChild(outerType);

        assertEquals("1.1", schema.detectXsdVersion());
    }
}
