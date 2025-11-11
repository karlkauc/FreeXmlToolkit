package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdInclude.
 *
 * @since 2.0
 */
class XsdIncludeTest {

    private XsdInclude xsdInclude;

    @BeforeEach
    void setUp() {
        xsdInclude = new XsdInclude("common-types.xsd");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdInclude inc = new XsdInclude();
        assertEquals("include", inc.getName());
        assertNull(inc.getSchemaLocation());
    }

    @Test
    @DisplayName("constructor with schemaLocation should set location")
    void testConstructorWithSchemaLocation() {
        XsdInclude inc = new XsdInclude("types.xsd");
        assertEquals("include", inc.getName());
        assertEquals("types.xsd", inc.getSchemaLocation());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return INCLUDE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.INCLUDE, xsdInclude.getNodeType());
    }

    // ========== SchemaLocation Property Tests ==========

    @Test
    @DisplayName("getSchemaLocation() should return schema location")
    void testGetSchemaLocation() {
        assertEquals("common-types.xsd", xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should set schema location")
    void testSetSchemaLocation() {
        xsdInclude.setSchemaLocation("other-types.xsd");
        assertEquals("other-types.xsd", xsdInclude.getSchemaLocation());
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

        xsdInclude.addPropertyChangeListener(listener);
        xsdInclude.setSchemaLocation("new-types.xsd");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setSchemaLocation() should fire event with correct old and new values")
    void testSetSchemaLocationMultipleTimes() {
        xsdInclude.setSchemaLocation("first.xsd");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("first.xsd", evt.getOldValue());
            assertEquals("second.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        xsdInclude.addPropertyChangeListener(listener);
        xsdInclude.setSchemaLocation("second.xsd");

        assertTrue(eventFired.get());
        assertEquals("second.xsd", xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept null")
    void testSetSchemaLocationNull() {
        xsdInclude.setSchemaLocation(null);
        assertNull(xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept absolute URLs")
    void testSetSchemaLocationAbsoluteURL() {
        xsdInclude.setSchemaLocation("http://example.com/schemas/types.xsd");
        assertEquals("http://example.com/schemas/types.xsd", xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept relative paths")
    void testSetSchemaLocationRelativePath() {
        xsdInclude.setSchemaLocation("../common/types.xsd");
        assertEquals("../common/types.xsd", xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept empty string")
    void testSetSchemaLocationEmpty() {
        xsdInclude.setSchemaLocation("");
        assertEquals("", xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept whitespace")
    void testSetSchemaLocationWhitespace() {
        xsdInclude.setSchemaLocation("   ");
        assertEquals("   ", xsdInclude.getSchemaLocation());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        xsdInclude.setDocumentation("Include common types");

        XsdInclude copy = (XsdInclude) xsdInclude.deepCopy("");

        assertNotNull(copy);
        assertNotSame(xsdInclude, copy);
        assertEquals("common-types.xsd", copy.getSchemaLocation());
        assertEquals("Include common types", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdInclude copy = (XsdInclude) xsdInclude.deepCopy("_copy");

        assertEquals("include_copy", copy.getName());
        assertEquals("common-types.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdInclude copy = (XsdInclude) xsdInclude.deepCopy("");

        assertNotEquals(xsdInclude.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdInclude copy = (XsdInclude) xsdInclude.deepCopy("");
        copy.setSchemaLocation("modified.xsd");

        assertEquals("common-types.xsd", xsdInclude.getSchemaLocation());
        assertEquals("modified.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should copy null schemaLocation")
    void testDeepCopyWithNullSchemaLocation() {
        XsdInclude inc = new XsdInclude();
        XsdInclude copy = (XsdInclude) inc.deepCopy("");

        assertNull(copy.getSchemaLocation());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("include should be addable as child to schema")
    void testIncludeAsChildOfSchema() {
        XsdSchema schema = new XsdSchema();
        schema.addChild(xsdInclude);

        assertEquals(schema, xsdInclude.getParent());
        assertTrue(schema.getChildren().contains(xsdInclude));
    }

    @Test
    @DisplayName("multiple includes should be independent")
    void testMultipleIncludesIndependence() {
        XsdSchema schema = new XsdSchema();
        XsdInclude include1 = new XsdInclude("types.xsd");
        XsdInclude include2 = new XsdInclude("elements.xsd");

        schema.addChild(include1);
        schema.addChild(include2);

        assertEquals(2, schema.getChildren().size());
        assertEquals("types.xsd", include1.getSchemaLocation());
        assertEquals("elements.xsd", include2.getSchemaLocation());
    }

    @Test
    @DisplayName("include can have child nodes")
    void testIncludeWithChildren() {
        XsdNode childNode = new XsdElement("child");
        xsdInclude.addChild(childNode);

        assertEquals(1, xsdInclude.getChildren().size());
        assertEquals(childNode, xsdInclude.getChildren().get(0));
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete include with schema location")
    void testCompleteInclude() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/main");

        XsdInclude inc = new XsdInclude("common/types.xsd");
        inc.setDocumentation("Include common type definitions from same namespace");

        schema.addChild(inc);

        // Verify structure
        assertEquals(1, schema.getChildren().size());
        XsdInclude retrievedInc = (XsdInclude) schema.getChildren().get(0);
        assertEquals("common/types.xsd", retrievedInc.getSchemaLocation());
        assertEquals("Include common type definitions from same namespace", retrievedInc.getDocumentation());
    }

    @Test
    @DisplayName("include for modular schema organization")
    void testIncludeForModularSchema() {
        XsdSchema mainSchema = new XsdSchema();
        mainSchema.setTargetNamespace("http://example.com/ns");

        // Include different modules
        XsdInclude typesInclude = new XsdInclude("types.xsd");
        XsdInclude elementsInclude = new XsdInclude("elements.xsd");
        XsdInclude groupsInclude = new XsdInclude("groups.xsd");

        mainSchema.addChild(typesInclude);
        mainSchema.addChild(elementsInclude);
        mainSchema.addChild(groupsInclude);

        assertEquals(3, mainSchema.getChildren().size());
    }

    @Test
    @DisplayName("include with relative path")
    void testIncludeWithRelativePath() {
        XsdInclude inc = new XsdInclude("../shared/common.xsd");
        assertEquals("../shared/common.xsd", inc.getSchemaLocation());
    }

    @Test
    @DisplayName("include with absolute URL")
    void testIncludeWithAbsoluteURL() {
        XsdInclude inc = new XsdInclude("http://example.com/schemas/common.xsd");
        assertEquals("http://example.com/schemas/common.xsd", inc.getSchemaLocation());
    }

    @Test
    @DisplayName("include with subdirectory path")
    void testIncludeWithSubdirectoryPath() {
        XsdInclude inc = new XsdInclude("common/types/base-types.xsd");
        assertEquals("common/types/base-types.xsd", inc.getSchemaLocation());
    }

    // ========== Comparison with Import ==========

    @Test
    @DisplayName("include should differ from import")
    void testIncludeDifferentFromImport() {
        XsdInclude include = new XsdInclude("types.xsd");
        XsdImport xsdImport = new XsdImport("http://example.com/types", "types.xsd");

        assertNotEquals(include.getNodeType(), xsdImport.getNodeType());
        assertEquals(XsdNodeType.INCLUDE, include.getNodeType());
        assertEquals(XsdNodeType.IMPORT, xsdImport.getNodeType());
    }

    @Test
    @DisplayName("include uses same namespace, import uses different namespace")
    void testIncludeVsImportSemantics() {
        // Include: same target namespace
        XsdInclude include = new XsdInclude("same-namespace.xsd");
        assertNull(include.getChildren().stream()
                .filter(c -> c.getName().equals("namespace"))
                .findFirst()
                .orElse(null));

        // This test documents the semantic difference:
        // Include is for same namespace, Import is for different namespace
        assertNotNull(include.getSchemaLocation());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        xsdInclude.addPropertyChangeListener(evt -> listener1Fired.set(true));
        xsdInclude.addPropertyChangeListener(evt -> listener2Fired.set(true));

        xsdInclude.setSchemaLocation("new-location.xsd");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    @Test
    @DisplayName("include with very long path")
    void testIncludeWithVeryLongPath() {
        String longPath = "very/long/path/to/some/deeply/nested/schema/file.xsd";
        xsdInclude.setSchemaLocation(longPath);
        assertEquals(longPath, xsdInclude.getSchemaLocation());
    }

    @Test
    @DisplayName("include with special characters in path")
    void testIncludeWithSpecialCharacters() {
        String specialPath = "schemas/common-types_v1.2.xsd";
        xsdInclude.setSchemaLocation(specialPath);
        assertEquals(specialPath, xsdInclude.getSchemaLocation());
    }
}
