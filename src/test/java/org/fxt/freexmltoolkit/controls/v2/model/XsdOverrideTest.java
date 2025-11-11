package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdOverride.
 *
 * @since 2.0
 */
class XsdOverrideTest {

    private XsdOverride override;

    @BeforeEach
    void setUp() {
        override = new XsdOverride("base-schema.xsd");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdOverride ovr = new XsdOverride();
        assertEquals("override", ovr.getName());
        assertNull(ovr.getSchemaLocation());
    }

    @Test
    @DisplayName("constructor with schemaLocation should set location")
    void testConstructorWithSchemaLocation() {
        XsdOverride ovr = new XsdOverride("http://example.com/schema.xsd");
        assertEquals("override", ovr.getName());
        assertEquals("http://example.com/schema.xsd", ovr.getSchemaLocation());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return OVERRIDE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.OVERRIDE, override.getNodeType());
    }

    // ========== SchemaLocation Property Tests ==========

    @Test
    @DisplayName("getSchemaLocation() should return schema location")
    void testGetSchemaLocation() {
        assertEquals("base-schema.xsd", override.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should set schema location")
    void testSetSchemaLocation() {
        override.setSchemaLocation("modified-schema.xsd");
        assertEquals("modified-schema.xsd", override.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should fire PropertyChangeEvent")
    void testSetSchemaLocationFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("base-schema.xsd", evt.getOldValue());
            assertEquals("new-schema.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        override.addPropertyChangeListener(listener);
        override.setSchemaLocation("new-schema.xsd");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setSchemaLocation() should fire event with correct old and new values")
    void testSetSchemaLocationMultipleTimes() {
        override.setSchemaLocation("first-schema.xsd");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("first-schema.xsd", evt.getOldValue());
            assertEquals("second-schema.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        override.addPropertyChangeListener(listener);
        override.setSchemaLocation("second-schema.xsd");

        assertTrue(eventFired.get());
        assertEquals("second-schema.xsd", override.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept null")
    void testSetSchemaLocationNull() {
        override.setSchemaLocation(null);
        assertNull(override.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept absolute URLs")
    void testSetSchemaLocationAbsoluteURL() {
        override.setSchemaLocation("http://example.com/schemas/base.xsd");
        assertEquals("http://example.com/schemas/base.xsd", override.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept relative paths")
    void testSetSchemaLocationRelativePath() {
        override.setSchemaLocation("../common/types.xsd");
        assertEquals("../common/types.xsd", override.getSchemaLocation());
    }

    // ========== Component Tests ==========

    @Test
    @DisplayName("getComponents() should return empty list when no components")
    void testGetComponentsEmpty() {
        assertTrue(override.getComponents().isEmpty());
    }

    @Test
    @DisplayName("getComponents() should return all children")
    void testGetComponents() {
        XsdComplexType ct = new XsdComplexType("ProductType");
        XsdElement el = new XsdElement("Product");
        override.addChild(ct);
        override.addChild(el);

        assertEquals(2, override.getComponents().size());
        assertTrue(override.getComponents().contains(ct));
        assertTrue(override.getComponents().contains(el));
    }

    // ========== Element Tests ==========

    @Test
    @DisplayName("getElements() should return empty list when no elements")
    void testGetElementsEmpty() {
        assertTrue(override.getElements().isEmpty());
    }

    @Test
    @DisplayName("getElements() should return all element children")
    void testGetElements() {
        XsdElement el1 = new XsdElement("Element1");
        XsdElement el2 = new XsdElement("Element2");
        override.addChild(el1);
        override.addChild(el2);

        assertEquals(2, override.getElements().size());
        assertTrue(override.getElements().contains(el1));
        assertTrue(override.getElements().contains(el2));
    }

    @Test
    @DisplayName("getElements() should only return elements, not other types")
    void testGetElementsOnlyElements() {
        XsdElement element = new XsdElement("Element1");
        XsdComplexType complexType = new XsdComplexType("Type1");
        override.addChild(element);
        override.addChild(complexType);

        assertEquals(1, override.getElements().size());
        assertFalse(override.getElements().contains(complexType));
    }

    // ========== ComplexType Tests ==========

    @Test
    @DisplayName("getComplexTypes() should return empty list when no complexTypes")
    void testGetComplexTypesEmpty() {
        assertTrue(override.getComplexTypes().isEmpty());
    }

    @Test
    @DisplayName("getComplexTypes() should return all complexType children")
    void testGetComplexTypes() {
        XsdComplexType ct1 = new XsdComplexType("Type1");
        XsdComplexType ct2 = new XsdComplexType("Type2");
        override.addChild(ct1);
        override.addChild(ct2);

        assertEquals(2, override.getComplexTypes().size());
        assertTrue(override.getComplexTypes().contains(ct1));
        assertTrue(override.getComplexTypes().contains(ct2));
    }

    // ========== SimpleType Tests ==========

    @Test
    @DisplayName("getSimpleTypes() should return empty list when no simpleTypes")
    void testGetSimpleTypesEmpty() {
        assertTrue(override.getSimpleTypes().isEmpty());
    }

    @Test
    @DisplayName("getSimpleTypes() should return all simpleType children")
    void testGetSimpleTypes() {
        XsdSimpleType st1 = new XsdSimpleType("SimpleType1");
        XsdSimpleType st2 = new XsdSimpleType("SimpleType2");
        override.addChild(st1);
        override.addChild(st2);

        assertEquals(2, override.getSimpleTypes().size());
        assertTrue(override.getSimpleTypes().contains(st1));
        assertTrue(override.getSimpleTypes().contains(st2));
    }

    // ========== Group Tests ==========

    @Test
    @DisplayName("getGroups() should return empty list when no groups")
    void testGetGroupsEmpty() {
        assertTrue(override.getGroups().isEmpty());
    }

    @Test
    @DisplayName("getGroups() should return all group children")
    void testGetGroups() {
        XsdGroup g1 = new XsdGroup("Group1");
        XsdGroup g2 = new XsdGroup("Group2");
        override.addChild(g1);
        override.addChild(g2);

        assertEquals(2, override.getGroups().size());
        assertTrue(override.getGroups().contains(g1));
        assertTrue(override.getGroups().contains(g2));
    }

    // ========== AttributeGroup Tests ==========

    @Test
    @DisplayName("getAttributeGroups() should return empty list when no attributeGroups")
    void testGetAttributeGroupsEmpty() {
        assertTrue(override.getAttributeGroups().isEmpty());
    }

    @Test
    @DisplayName("getAttributeGroups() should return all attributeGroup children")
    void testGetAttributeGroups() {
        XsdAttributeGroup ag1 = new XsdAttributeGroup("AttrGroup1");
        XsdAttributeGroup ag2 = new XsdAttributeGroup("AttrGroup2");
        override.addChild(ag1);
        override.addChild(ag2);

        assertEquals(2, override.getAttributeGroups().size());
        assertTrue(override.getAttributeGroups().contains(ag1));
        assertTrue(override.getAttributeGroups().contains(ag2));
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        override.setDocumentation("Override base schema components");

        XsdOverride copy = (XsdOverride) override.deepCopy("");

        assertNotNull(copy);
        assertNotSame(override, copy);
        assertEquals("base-schema.xsd", copy.getSchemaLocation());
        assertEquals("Override base schema components", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdOverride copy = (XsdOverride) override.deepCopy("_copy");

        assertEquals("override_copy", copy.getName());
        assertEquals("base-schema.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdOverride copy = (XsdOverride) override.deepCopy("");

        assertNotEquals(override.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdOverride copy = (XsdOverride) override.deepCopy("");
        copy.setSchemaLocation("modified.xsd");

        assertEquals("base-schema.xsd", override.getSchemaLocation());
        assertEquals("modified.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should copy components")
    void testDeepCopyWithComponents() {
        XsdComplexType ct = new XsdComplexType("ProductType");
        override.addChild(ct);

        XsdOverride copy = (XsdOverride) override.deepCopy("");

        assertEquals(1, copy.getComplexTypes().size());
        assertNotSame(ct, copy.getComplexTypes().get(0));
    }

    @Test
    @DisplayName("deepCopy() should copy null schemaLocation")
    void testDeepCopyWithNullSchemaLocation() {
        override.setSchemaLocation(null);
        XsdOverride copy = (XsdOverride) override.deepCopy("");

        assertNull(copy.getSchemaLocation());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("override should be addable as child to schema")
    void testOverrideAsChildOfSchema() {
        XsdSchema schema = new XsdSchema();
        schema.addChild(override);

        assertEquals(schema, override.getParent());
        assertTrue(schema.getChildren().contains(override));
    }

    @Test
    @DisplayName("override should accept multiple component types as children")
    void testOverrideWithMultipleComponentTypes() {
        XsdElement element = new XsdElement("Product");
        XsdComplexType complexType = new XsdComplexType("ProductType");
        XsdSimpleType simpleType = new XsdSimpleType("SKUType");
        XsdGroup group = new XsdGroup("ProductGroup");
        XsdAttributeGroup attrGroup = new XsdAttributeGroup("ProductAttributes");

        override.addChild(element);
        override.addChild(complexType);
        override.addChild(simpleType);
        override.addChild(group);
        override.addChild(attrGroup);

        assertEquals(5, override.getComponents().size());
        assertEquals(1, override.getElements().size());
        assertEquals(1, override.getComplexTypes().size());
        assertEquals(1, override.getSimpleTypes().size());
        assertEquals(1, override.getGroups().size());
        assertEquals(1, override.getAttributeGroups().size());
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete override with type redefinition")
    void testCompleteOverrideWithTypeRedefinition() {
        XsdSchema schema = new XsdSchema();

        XsdOverride ovr = new XsdOverride("base-types.xsd");
        ovr.setDocumentation("Override ProductType to add extended attributes");

        // Override ProductType definition
        XsdComplexType productType = new XsdComplexType("ProductType");
        XsdSequence sequence = new XsdSequence();
        productType.addChild(sequence);
        ovr.addChild(productType);

        schema.addChild(ovr);

        // Verify structure
        assertEquals(1, schema.getChildren().size());
        XsdOverride retrievedOvr = (XsdOverride) schema.getChildren().get(0);
        assertEquals("base-types.xsd", retrievedOvr.getSchemaLocation());
        assertEquals(1, retrievedOvr.getComplexTypes().size());
        assertEquals("ProductType", retrievedOvr.getComplexTypes().get(0).getName());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("override with empty schemaLocation")
    void testEmptySchemaLocation() {
        override.setSchemaLocation("");
        assertEquals("", override.getSchemaLocation());
    }

    @Test
    @DisplayName("override with whitespace schemaLocation")
    void testWhitespaceSchemaLocation() {
        override.setSchemaLocation("   ");
        assertEquals("   ", override.getSchemaLocation());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        override.addPropertyChangeListener(evt -> listener1Fired.set(true));
        override.addPropertyChangeListener(evt -> listener2Fired.set(true));

        override.setSchemaLocation("new-location.xsd");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }
}
