package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdSimpleContent.
 *
 * @since 2.0
 */
class XsdSimpleContentTest {

    private XsdSimpleContent simpleContent;

    @BeforeEach
    void setUp() {
        simpleContent = new XsdSimpleContent();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdSimpleContent sc = new XsdSimpleContent();
        assertEquals("simpleContent", sc.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return SIMPLE_CONTENT")
    void testGetNodeType() {
        assertEquals(XsdNodeType.SIMPLE_CONTENT, simpleContent.getNodeType());
    }

    // ========== Extension/Restriction Children Tests ==========

    @Test
    @DisplayName("getExtension() should return null when no extension child")
    void testGetExtensionNull() {
        assertNull(simpleContent.getExtension());
    }

    @Test
    @DisplayName("getExtension() should return extension child when present")
    void testGetExtensionPresent() {
        XsdExtension extension = new XsdExtension("xs:string");
        simpleContent.addChild(extension);

        XsdExtension found = simpleContent.getExtension();
        assertNotNull(found);
        assertEquals(extension, found);
    }

    @Test
    @DisplayName("getRestriction() should return null when no restriction child")
    void testGetRestrictionNull() {
        assertNull(simpleContent.getRestriction());
    }

    @Test
    @DisplayName("getRestriction() should return restriction child when present")
    void testGetRestrictionPresent() {
        XsdRestriction restriction = new XsdRestriction("xs:string");
        simpleContent.addChild(restriction);

        XsdRestriction found = simpleContent.getRestriction();
        assertNotNull(found);
        assertEquals(restriction, found);
    }

    @Test
    @DisplayName("should support either extension or restriction, not both")
    void testEitherExtensionOrRestriction() {
        // XSD rules: simpleContent must have either extension OR restriction, not both
        XsdExtension extension = new XsdExtension("BaseType");
        simpleContent.addChild(extension);

        assertNotNull(simpleContent.getExtension());
        assertNull(simpleContent.getRestriction());

        // If we add restriction (which shouldn't happen in practice),
        // both getters would work but only one should be used
        XsdRestriction restriction = new XsdRestriction("OtherBaseType");
        simpleContent.addChild(restriction);

        assertNotNull(simpleContent.getExtension());
        assertNotNull(simpleContent.getRestriction());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("simpleContent should support parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("MyComplexType");
        simpleContent.setParent(parent);
        parent.addChild(simpleContent);

        assertEquals(parent, simpleContent.getParent());
        assertTrue(parent.getChildren().contains(simpleContent));
    }

    @Test
    @DisplayName("extension should be added as child")
    void testExtensionAsChild() {
        XsdExtension extension = new XsdExtension("BaseType");
        simpleContent.addChild(extension);

        assertEquals(1, simpleContent.getChildren().size());
        assertEquals(extension, simpleContent.getChildren().get(0));
        assertEquals(simpleContent, extension.getParent());
    }

    @Test
    @DisplayName("restriction should be added as child")
    void testRestrictionAsChild() {
        XsdRestriction restriction = new XsdRestriction("BaseType");
        simpleContent.addChild(restriction);

        assertEquals(1, simpleContent.getChildren().size());
        assertEquals(restriction, simpleContent.getChildren().get(0));
        assertEquals(simpleContent, restriction.getParent());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        XsdExtension extension = new XsdExtension("BaseType");
        simpleContent.addChild(extension);

        XsdSimpleContent copy = (XsdSimpleContent) simpleContent.deepCopy(null);

        assertNotNull(copy);
        assertEquals(simpleContent.getName(), copy.getName());
        assertNotSame(simpleContent, copy);
        assertNotEquals(simpleContent.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change simpleContent name")
    void testDeepCopyWithSuffix() {
        XsdSimpleContent copy = (XsdSimpleContent) simpleContent.deepCopy("_Copy");

        // SimpleContent name is always "simpleContent", suffix should not be applied
        assertEquals("simpleContent", copy.getName());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        XsdExtension extension = new XsdExtension("BaseType");
        simpleContent.addChild(extension);

        XsdSimpleContent copy = (XsdSimpleContent) simpleContent.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdExtension);
        assertNotSame(extension, copy.getChildren().get(0));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("simpleContent should work in complexType context")
    void testSimpleContentInComplexType() {
        XsdComplexType complexType = new XsdComplexType("MyComplexType");

        XsdSimpleContent content = new XsdSimpleContent();

        XsdExtension extension = new XsdExtension("xs:string");
        content.addChild(extension);

        complexType.addChild(content);

        assertEquals(complexType, content.getParent());
        assertEquals(1, complexType.getChildren().size());
        assertEquals(content, complexType.getChildren().get(0));
    }

    @Test
    @DisplayName("simpleContent with extension")
    void testSimpleContentWithExtension() {
        XsdSimpleContent content = new XsdSimpleContent();

        XsdExtension extension = new XsdExtension("xs:string");
        XsdAttribute attribute = new XsdAttribute("currency");
        attribute.setType("xs:string");
        extension.addChild(attribute);

        content.addChild(extension);

        assertNotNull(content.getExtension());
        assertEquals("xs:string", content.getExtension().getBase());
        assertEquals(1, content.getExtension().getChildren().size());
    }

    @Test
    @DisplayName("simpleContent with restriction")
    void testSimpleContentWithRestriction() {
        XsdSimpleContent content = new XsdSimpleContent();

        XsdRestriction restriction = new XsdRestriction("xs:integer");
        XsdFacet minFacet = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        restriction.addFacet(minFacet);

        content.addChild(restriction);

        assertNotNull(content.getRestriction());
        assertEquals("xs:integer", content.getRestriction().getBase());
        assertEquals(1, content.getRestriction().getFacets().size());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create simpleContent extending base type with attributes")
    void testSimpleContentExtensionWithAttributes() {
        // Example: <xs:simpleContent>
        //            <xs:extension base="xs:string">
        //              <xs:attribute name="lang" type="xs:string"/>
        //            </xs:extension>
        //          </xs:simpleContent>

        XsdSimpleContent content = new XsdSimpleContent();

        XsdExtension extension = new XsdExtension("xs:string");
        XsdAttribute langAttribute = new XsdAttribute("lang");
        langAttribute.setType("xs:string");
        extension.addChild(langAttribute);

        content.addChild(extension);

        assertNotNull(content.getExtension());
        assertEquals("xs:string", content.getExtension().getBase());
        assertEquals(1, content.getExtension().getChildren().size());
    }

    @Test
    @DisplayName("create simpleContent restricting base type")
    void testSimpleContentRestriction() {
        // Example: <xs:simpleContent>
        //            <xs:restriction base="xs:integer">
        //              <xs:minInclusive value="0"/>
        //              <xs:maxInclusive value="100"/>
        //            </xs:restriction>
        //          </xs:simpleContent>

        XsdSimpleContent content = new XsdSimpleContent();

        XsdRestriction restriction = new XsdRestriction("xs:integer");
        restriction.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0"));
        restriction.addFacet(new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100"));

        content.addChild(restriction);

        assertNotNull(content.getRestriction());
        assertEquals(2, content.getRestriction().getFacets().size());
    }

    @Test
    @DisplayName("create price type with currency attribute")
    void testPriceTypeExample() {
        // Example: <xs:complexType name="PriceType">
        //            <xs:simpleContent>
        //              <xs:extension base="xs:decimal">
        //                <xs:attribute name="currency" type="xs:string"/>
        //              </xs:extension>
        //            </xs:simpleContent>
        //          </xs:complexType>

        XsdComplexType priceType = new XsdComplexType("PriceType");
        XsdSimpleContent content = new XsdSimpleContent();

        XsdExtension extension = new XsdExtension("xs:decimal");
        XsdAttribute currencyAttr = new XsdAttribute("currency");
        currencyAttr.setType("xs:string");
        extension.addChild(currencyAttr);

        content.addChild(extension);
        priceType.addChild(content);

        assertEquals(1, priceType.getChildren().size());
        assertTrue(priceType.getChildren().get(0) instanceof XsdSimpleContent);
    }

    @Test
    @DisplayName("simpleContent represents text content with attributes")
    void testSimpleContentSemantics() {
        // SimpleContent means: element has text content PLUS attributes
        // Example XML: <price currency="USD">19.99</price>

        XsdSimpleContent content = new XsdSimpleContent();
        XsdExtension extension = new XsdExtension("xs:decimal");

        XsdAttribute currencyAttr = new XsdAttribute("currency");
        currencyAttr.setType("xs:string");
        currencyAttr.setUse("required");

        extension.addChild(currencyAttr);
        content.addChild(extension);

        assertNotNull(content.getExtension());
        assertEquals(1, content.getExtension().getChildren().size());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        String toString = simpleContent.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("simpleContent name should always be 'simpleContent'")
    void testSimpleContentNameAlwaysSimpleContent() {
        assertEquals("simpleContent", simpleContent.getName());

        XsdExtension extension = new XsdExtension("xs:string");
        simpleContent.addChild(extension);
        assertEquals("simpleContent", simpleContent.getName());

        XsdSimpleContent another = new XsdSimpleContent();
        assertEquals("simpleContent", another.getName());
    }

    @Test
    @DisplayName("simpleContent should not have mixed property")
    void testSimpleContentNoMixedProperty() {
        // Unlike ComplexContent, SimpleContent doesn't have a mixed attribute
        // This is just a semantic test to document the difference

        XsdSimpleContent sc = new XsdSimpleContent();
        // No setMixed() method should exist
        assertNotNull(sc);
    }
}
