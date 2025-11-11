package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdRedefine.
 *
 * @since 2.0
 */
class XsdRedefineTest {

    private XsdRedefine xsdRedefine;

    @BeforeEach
    void setUp() {
        xsdRedefine = new XsdRedefine("common-types.xsd");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdRedefine redef = new XsdRedefine();
        assertEquals("redefine", redef.getName());
        assertNull(redef.getSchemaLocation());
    }

    @Test
    @DisplayName("constructor with schemaLocation should set location")
    void testConstructorWithSchemaLocation() {
        XsdRedefine redef = new XsdRedefine("types.xsd");
        assertEquals("redefine", redef.getName());
        assertEquals("types.xsd", redef.getSchemaLocation());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return REDEFINE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.REDEFINE, xsdRedefine.getNodeType());
    }

    // ========== SchemaLocation Property Tests ==========

    @Test
    @DisplayName("getSchemaLocation() should return schema location")
    void testGetSchemaLocation() {
        assertEquals("common-types.xsd", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should set schema location")
    void testSetSchemaLocation() {
        xsdRedefine.setSchemaLocation("other-types.xsd");
        assertEquals("other-types.xsd", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should fire PropertyChangeEvent")
    void testSetSchemaLocationFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("common-types.xsd", evt.getOldValue());
            assertEquals("new-types.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        xsdRedefine.addPropertyChangeListener(listener);
        xsdRedefine.setSchemaLocation("new-types.xsd");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setSchemaLocation() should fire event with correct old and new values")
    void testSetSchemaLocationMultipleTimes() {
        xsdRedefine.setSchemaLocation("first.xsd");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("first.xsd", evt.getOldValue());
            assertEquals("second.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        xsdRedefine.addPropertyChangeListener(listener);
        xsdRedefine.setSchemaLocation("second.xsd");

        assertTrue(eventFired.get());
        assertEquals("second.xsd", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept null")
    void testSetSchemaLocationNull() {
        xsdRedefine.setSchemaLocation(null);
        assertNull(xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept absolute URLs")
    void testSetSchemaLocationAbsoluteURL() {
        xsdRedefine.setSchemaLocation("http://example.com/schemas/types.xsd");
        assertEquals("http://example.com/schemas/types.xsd", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept relative paths")
    void testSetSchemaLocationRelativePath() {
        xsdRedefine.setSchemaLocation("../common/types.xsd");
        assertEquals("../common/types.xsd", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept empty string")
    void testSetSchemaLocationEmpty() {
        xsdRedefine.setSchemaLocation("");
        assertEquals("", xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept whitespace")
    void testSetSchemaLocationWhitespace() {
        xsdRedefine.setSchemaLocation("   ");
        assertEquals("   ", xsdRedefine.getSchemaLocation());
    }

    // ========== Helper Methods Tests ==========

    @Test
    @DisplayName("getElements() should return empty list when no elements")
    void testGetElementsEmpty() {
        List<XsdElement> elements = xsdRedefine.getElements();
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    @Test
    @DisplayName("getElements() should return all element children")
    void testGetElements() {
        XsdElement elem1 = new XsdElement("Element1");
        XsdElement elem2 = new XsdElement("Element2");
        xsdRedefine.addChild(elem1);
        xsdRedefine.addChild(elem2);

        List<XsdElement> elements = xsdRedefine.getElements();
        assertEquals(2, elements.size());
        assertTrue(elements.contains(elem1));
        assertTrue(elements.contains(elem2));
    }

    @Test
    @DisplayName("getComplexTypes() should return empty list when no complex types")
    void testGetComplexTypesEmpty() {
        List<XsdComplexType> types = xsdRedefine.getComplexTypes();
        assertNotNull(types);
        assertTrue(types.isEmpty());
    }

    @Test
    @DisplayName("getComplexTypes() should return all complex type children")
    void testGetComplexTypes() {
        XsdComplexType type1 = new XsdComplexType("Type1");
        XsdComplexType type2 = new XsdComplexType("Type2");
        xsdRedefine.addChild(type1);
        xsdRedefine.addChild(type2);

        List<XsdComplexType> types = xsdRedefine.getComplexTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(type1));
        assertTrue(types.contains(type2));
    }

    @Test
    @DisplayName("getSimpleTypes() should return empty list when no simple types")
    void testGetSimpleTypesEmpty() {
        List<XsdSimpleType> types = xsdRedefine.getSimpleTypes();
        assertNotNull(types);
        assertTrue(types.isEmpty());
    }

    @Test
    @DisplayName("getSimpleTypes() should return all simple type children")
    void testGetSimpleTypes() {
        XsdSimpleType type1 = new XsdSimpleType("Type1");
        XsdSimpleType type2 = new XsdSimpleType("Type2");
        xsdRedefine.addChild(type1);
        xsdRedefine.addChild(type2);

        List<XsdSimpleType> types = xsdRedefine.getSimpleTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(type1));
        assertTrue(types.contains(type2));
    }

    @Test
    @DisplayName("getGroups() should return empty list when no groups")
    void testGetGroupsEmpty() {
        List<XsdGroup> groups = xsdRedefine.getGroups();
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("getGroups() should return all group children")
    void testGetGroups() {
        XsdGroup group1 = new XsdGroup("Group1");
        XsdGroup group2 = new XsdGroup("Group2");
        xsdRedefine.addChild(group1);
        xsdRedefine.addChild(group2);

        List<XsdGroup> groups = xsdRedefine.getGroups();
        assertEquals(2, groups.size());
        assertTrue(groups.contains(group1));
        assertTrue(groups.contains(group2));
    }

    @Test
    @DisplayName("getAttributeGroups() should return empty list when no attribute groups")
    void testGetAttributeGroupsEmpty() {
        List<XsdAttributeGroup> groups = xsdRedefine.getAttributeGroups();
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("getAttributeGroups() should return all attribute group children")
    void testGetAttributeGroups() {
        XsdAttributeGroup group1 = new XsdAttributeGroup("AttrGroup1");
        XsdAttributeGroup group2 = new XsdAttributeGroup("AttrGroup2");
        xsdRedefine.addChild(group1);
        xsdRedefine.addChild(group2);

        List<XsdAttributeGroup> groups = xsdRedefine.getAttributeGroups();
        assertEquals(2, groups.size());
        assertTrue(groups.contains(group1));
        assertTrue(groups.contains(group2));
    }

    @Test
    @DisplayName("helper methods should filter correctly with mixed children")
    void testHelperMethodsWithMixedChildren() {
        XsdElement element = new XsdElement("Element1");
        XsdComplexType complexType = new XsdComplexType("ComplexType1");
        XsdSimpleType simpleType = new XsdSimpleType("SimpleType1");
        XsdGroup group = new XsdGroup("Group1");
        XsdAttributeGroup attrGroup = new XsdAttributeGroup("AttrGroup1");

        xsdRedefine.addChild(element);
        xsdRedefine.addChild(complexType);
        xsdRedefine.addChild(simpleType);
        xsdRedefine.addChild(group);
        xsdRedefine.addChild(attrGroup);

        assertEquals(1, xsdRedefine.getElements().size());
        assertEquals(1, xsdRedefine.getComplexTypes().size());
        assertEquals(1, xsdRedefine.getSimpleTypes().size());
        assertEquals(1, xsdRedefine.getGroups().size());
        assertEquals(1, xsdRedefine.getAttributeGroups().size());
        assertEquals(5, xsdRedefine.getChildren().size());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        xsdRedefine.setDocumentation("Redefine common types");

        XsdRedefine copy = (XsdRedefine) xsdRedefine.deepCopy("");

        assertNotNull(copy);
        assertNotSame(xsdRedefine, copy);
        assertEquals("common-types.xsd", copy.getSchemaLocation());
        assertEquals("Redefine common types", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdRedefine copy = (XsdRedefine) xsdRedefine.deepCopy("_copy");

        assertEquals("redefine_copy", copy.getName());
        assertEquals("common-types.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdRedefine copy = (XsdRedefine) xsdRedefine.deepCopy("");

        assertNotEquals(xsdRedefine.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdRedefine copy = (XsdRedefine) xsdRedefine.deepCopy("");
        copy.setSchemaLocation("modified.xsd");

        assertEquals("common-types.xsd", xsdRedefine.getSchemaLocation());
        assertEquals("modified.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should copy null schemaLocation")
    void testDeepCopyWithNullSchemaLocation() {
        XsdRedefine redef = new XsdRedefine();
        XsdRedefine copy = (XsdRedefine) redef.deepCopy("");

        assertNull(copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyWithChildren() {
        XsdComplexType type = new XsdComplexType("Type1");
        xsdRedefine.addChild(type);

        XsdRedefine copy = (XsdRedefine) xsdRedefine.deepCopy("");

        assertEquals(1, copy.getChildren().size());
        assertNotSame(type, copy.getChildren().get(0));
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("redefine should be addable as child to schema")
    void testRedefineAsChildOfSchema() {
        XsdSchema schema = new XsdSchema();
        schema.addChild(xsdRedefine);

        assertEquals(schema, xsdRedefine.getParent());
        assertTrue(schema.getChildren().contains(xsdRedefine));
    }

    @Test
    @DisplayName("multiple redefines should be independent")
    void testMultipleRedefinesIndependence() {
        XsdSchema schema = new XsdSchema();
        XsdRedefine redef1 = new XsdRedefine("types.xsd");
        XsdRedefine redef2 = new XsdRedefine("elements.xsd");

        schema.addChild(redef1);
        schema.addChild(redef2);

        assertEquals(2, schema.getChildren().size());
        assertEquals("types.xsd", redef1.getSchemaLocation());
        assertEquals("elements.xsd", redef2.getSchemaLocation());
    }

    @Test
    @DisplayName("redefine can have redefined component children")
    void testRedefineWithComponentChildren() {
        XsdComplexType type = new XsdComplexType("RefinedType");
        xsdRedefine.addChild(type);

        assertEquals(1, xsdRedefine.getChildren().size());
        assertEquals(type, xsdRedefine.getChildren().get(0));
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete redefine with schema location and redefined type")
    void testCompleteRedefine() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/main");

        XsdRedefine redef = new XsdRedefine("common/types.xsd");
        redef.setDocumentation("Redefine common type definitions");

        // Redefine a complex type by extending it
        XsdComplexType refinedType = new XsdComplexType("PersonType");
        redef.addChild(refinedType);

        schema.addChild(redef);

        // Verify structure
        assertEquals(1, schema.getChildren().size());
        XsdRedefine retrievedRedef = (XsdRedefine) schema.getChildren().get(0);
        assertEquals("common/types.xsd", retrievedRedef.getSchemaLocation());
        assertEquals("Redefine common type definitions", retrievedRedef.getDocumentation());
        assertEquals(1, retrievedRedef.getComplexTypes().size());
    }

    @Test
    @DisplayName("redefine for extending and restricting types")
    void testRedefineForTypeRefinement() {
        XsdSchema mainSchema = new XsdSchema();
        mainSchema.setTargetNamespace("http://example.com/ns");

        XsdRedefine redef = new XsdRedefine("base-types.xsd");

        // Redefine a complex type
        XsdComplexType refinedComplexType = new XsdComplexType("BaseComplexType");
        redef.addChild(refinedComplexType);

        // Redefine a simple type
        XsdSimpleType refinedSimpleType = new XsdSimpleType("BaseSimpleType");
        redef.addChild(refinedSimpleType);

        mainSchema.addChild(redef);

        assertEquals(1, mainSchema.getChildren().size());
        assertEquals(1, redef.getComplexTypes().size());
        assertEquals(1, redef.getSimpleTypes().size());
    }

    @Test
    @DisplayName("redefine with relative path")
    void testRedefineWithRelativePath() {
        XsdRedefine redef = new XsdRedefine("../shared/common.xsd");
        assertEquals("../shared/common.xsd", redef.getSchemaLocation());
    }

    @Test
    @DisplayName("redefine with absolute URL")
    void testRedefineWithAbsoluteURL() {
        XsdRedefine redef = new XsdRedefine("http://example.com/schemas/common.xsd");
        assertEquals("http://example.com/schemas/common.xsd", redef.getSchemaLocation());
    }

    @Test
    @DisplayName("redefine with subdirectory path")
    void testRedefineWithSubdirectoryPath() {
        XsdRedefine redef = new XsdRedefine("common/types/base-types.xsd");
        assertEquals("common/types/base-types.xsd", redef.getSchemaLocation());
    }

    // ========== Comparison with Include and Override ==========

    @Test
    @DisplayName("redefine should differ from include")
    void testRedefineDifferentFromInclude() {
        XsdRedefine redefine = new XsdRedefine("types.xsd");
        XsdInclude include = new XsdInclude("types.xsd");

        assertNotEquals(redefine.getNodeType(), include.getNodeType());
        assertEquals(XsdNodeType.REDEFINE, redefine.getNodeType());
        assertEquals(XsdNodeType.INCLUDE, include.getNodeType());
    }

    @Test
    @DisplayName("redefine should differ from override")
    void testRedefineDifferentFromOverride() {
        XsdRedefine redefine = new XsdRedefine("types.xsd");
        XsdOverride override = new XsdOverride("types.xsd");

        assertNotEquals(redefine.getNodeType(), override.getNodeType());
        assertEquals(XsdNodeType.REDEFINE, redefine.getNodeType());
        assertEquals(XsdNodeType.OVERRIDE, override.getNodeType());
    }

    @Test
    @DisplayName("redefine is XSD 1.0, override is XSD 1.1 replacement")
    void testRedefineVsOverrideSemantics() {
        // Redefine: XSD 1.0 feature (deprecated in XSD 1.1)
        XsdRedefine redefine = new XsdRedefine("types.xsd");
        XsdComplexType redefinedType = new XsdComplexType("Type1");
        redefine.addChild(redefinedType);

        // Override: XSD 1.1 replacement for redefine
        XsdOverride override = new XsdOverride("types.xsd");
        XsdComplexType overriddenType = new XsdComplexType("Type1");
        override.addChild(overriddenType);

        // Both can contain redefinitions, but override is the modern approach
        assertEquals(1, redefine.getComplexTypes().size());
        assertEquals(1, override.getComplexTypes().size());
        assertNotEquals(redefine.getNodeType(), override.getNodeType());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        xsdRedefine.addPropertyChangeListener(evt -> listener1Fired.set(true));
        xsdRedefine.addPropertyChangeListener(evt -> listener2Fired.set(true));

        xsdRedefine.setSchemaLocation("new-location.xsd");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    @Test
    @DisplayName("redefine with very long path")
    void testRedefineWithVeryLongPath() {
        String longPath = "very/long/path/to/some/deeply/nested/schema/file.xsd";
        xsdRedefine.setSchemaLocation(longPath);
        assertEquals(longPath, xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("redefine with special characters in path")
    void testRedefineWithSpecialCharacters() {
        String specialPath = "schemas/common-types_v1.2.xsd";
        xsdRedefine.setSchemaLocation(specialPath);
        assertEquals(specialPath, xsdRedefine.getSchemaLocation());
    }

    @Test
    @DisplayName("redefine with multiple component types")
    void testRedefineWithMultipleComponentTypes() {
        XsdElement element = new XsdElement("Element1");
        XsdComplexType complexType = new XsdComplexType("ComplexType1");
        XsdSimpleType simpleType = new XsdSimpleType("SimpleType1");
        XsdGroup group = new XsdGroup("Group1");
        XsdAttributeGroup attrGroup = new XsdAttributeGroup("AttrGroup1");

        xsdRedefine.addChild(element);
        xsdRedefine.addChild(complexType);
        xsdRedefine.addChild(simpleType);
        xsdRedefine.addChild(group);
        xsdRedefine.addChild(attrGroup);

        assertEquals(5, xsdRedefine.getChildren().size());
        assertEquals(1, xsdRedefine.getElements().size());
        assertEquals(1, xsdRedefine.getComplexTypes().size());
        assertEquals(1, xsdRedefine.getSimpleTypes().size());
        assertEquals(1, xsdRedefine.getGroups().size());
        assertEquals(1, xsdRedefine.getAttributeGroups().size());
    }
}
