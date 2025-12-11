package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdImport.
 *
 * @since 2.0
 */
class XsdImportTest {

    private XsdImport xsdImport;

    @BeforeEach
    void setUp() {
        xsdImport = new XsdImport("http://example.com/types", "types.xsd");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdImport imp = new XsdImport();
        assertEquals("import", imp.getName());
        assertNull(imp.getNamespace());
        assertNull(imp.getSchemaLocation());
    }

    @Test
    @DisplayName("constructor with namespace should set namespace")
    void testConstructorWithNamespace() {
        XsdImport imp = new XsdImport("http://www.w3.org/2001/XMLSchema");
        assertEquals("import", imp.getName());
        assertEquals("http://www.w3.org/2001/XMLSchema", imp.getNamespace());
        assertNull(imp.getSchemaLocation());
    }

    @Test
    @DisplayName("constructor with namespace and schemaLocation should set both")
    void testConstructorWithNamespaceAndSchemaLocation() {
        XsdImport imp = new XsdImport("http://example.com/ns", "schema.xsd");
        assertEquals("import", imp.getName());
        assertEquals("http://example.com/ns", imp.getNamespace());
        assertEquals("schema.xsd", imp.getSchemaLocation());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return IMPORT")
    void testGetNodeType() {
        assertEquals(XsdNodeType.IMPORT, xsdImport.getNodeType());
    }

    // ========== Namespace Property Tests ==========

    @Test
    @DisplayName("getNamespace() should return namespace")
    void testGetNamespace() {
        assertEquals("http://example.com/types", xsdImport.getNamespace());
    }

    @Test
    @DisplayName("setNamespace() should set namespace")
    void testSetNamespace() {
        xsdImport.setNamespace("http://example.com/other");
        assertEquals("http://example.com/other", xsdImport.getNamespace());
    }

    @Test
    @DisplayName("setNamespace() should fire PropertyChangeEvent")
    void testSetNamespaceFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("namespace", evt.getPropertyName());
            assertEquals("http://example.com/types", evt.getOldValue());
            assertEquals("http://example.com/new", evt.getNewValue());
            eventFired.set(true);
        };

        xsdImport.addPropertyChangeListener(listener);
        xsdImport.setNamespace("http://example.com/new");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setNamespace() should fire event with correct old and new values")
    void testSetNamespaceMultipleTimes() {
        xsdImport.setNamespace("http://first.com");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("namespace", evt.getPropertyName());
            assertEquals("http://first.com", evt.getOldValue());
            assertEquals("http://second.com", evt.getNewValue());
            eventFired.set(true);
        };

        xsdImport.addPropertyChangeListener(listener);
        xsdImport.setNamespace("http://second.com");

        assertTrue(eventFired.get());
        assertEquals("http://second.com", xsdImport.getNamespace());
    }

    @Test
    @DisplayName("setNamespace() should accept null")
    void testSetNamespaceNull() {
        xsdImport.setNamespace(null);
        assertNull(xsdImport.getNamespace());
    }

    @Test
    @DisplayName("setNamespace() should accept empty string")
    void testSetNamespaceEmpty() {
        xsdImport.setNamespace("");
        assertEquals("", xsdImport.getNamespace());
    }

    // ========== SchemaLocation Property Tests ==========

    @Test
    @DisplayName("getSchemaLocation() should return schema location")
    void testGetSchemaLocation() {
        assertEquals("types.xsd", xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should set schema location")
    void testSetSchemaLocation() {
        xsdImport.setSchemaLocation("other.xsd");
        assertEquals("other.xsd", xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should fire PropertyChangeEvent")
    void testSetSchemaLocationFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("types.xsd", evt.getOldValue());
            assertEquals("new-types.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        xsdImport.addPropertyChangeListener(listener);
        xsdImport.setSchemaLocation("new-types.xsd");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setSchemaLocation() should fire event with correct old and new values")
    void testSetSchemaLocationMultipleTimes() {
        xsdImport.setSchemaLocation("first.xsd");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("schemaLocation", evt.getPropertyName());
            assertEquals("first.xsd", evt.getOldValue());
            assertEquals("second.xsd", evt.getNewValue());
            eventFired.set(true);
        };

        xsdImport.addPropertyChangeListener(listener);
        xsdImport.setSchemaLocation("second.xsd");

        assertTrue(eventFired.get());
        assertEquals("second.xsd", xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept null")
    void testSetSchemaLocationNull() {
        xsdImport.setSchemaLocation(null);
        assertNull(xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept absolute URLs")
    void testSetSchemaLocationAbsoluteURL() {
        xsdImport.setSchemaLocation("http://example.com/schemas/types.xsd");
        assertEquals("http://example.com/schemas/types.xsd", xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("setSchemaLocation() should accept relative paths")
    void testSetSchemaLocationRelativePath() {
        xsdImport.setSchemaLocation("../common/types.xsd");
        assertEquals("../common/types.xsd", xsdImport.getSchemaLocation());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        xsdImport.setDocumentation("Import common types");

        XsdImport copy = (XsdImport) xsdImport.deepCopy("");

        assertNotNull(copy);
        assertNotSame(xsdImport, copy);
        assertEquals("http://example.com/types", copy.getNamespace());
        assertEquals("types.xsd", copy.getSchemaLocation());
        assertEquals("Import common types", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdImport copy = (XsdImport) xsdImport.deepCopy("_copy");

        assertEquals("import_copy", copy.getName());
        assertEquals("http://example.com/types", copy.getNamespace());
        assertEquals("types.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdImport copy = (XsdImport) xsdImport.deepCopy("");

        assertNotEquals(xsdImport.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdImport copy = (XsdImport) xsdImport.deepCopy("");
        copy.setNamespace("http://modified.com");
        copy.setSchemaLocation("modified.xsd");

        assertEquals("http://example.com/types", xsdImport.getNamespace());
        assertEquals("types.xsd", xsdImport.getSchemaLocation());
        assertEquals("http://modified.com", copy.getNamespace());
        assertEquals("modified.xsd", copy.getSchemaLocation());
    }

    @Test
    @DisplayName("deepCopy() should copy null properties")
    void testDeepCopyWithNullProperties() {
        XsdImport imp = new XsdImport();
        XsdImport copy = (XsdImport) imp.deepCopy("");

        assertNull(copy.getNamespace());
        assertNull(copy.getSchemaLocation());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("import should be addable as child to schema")
    void testImportAsChildOfSchema() {
        XsdSchema schema = new XsdSchema();
        schema.addChild(xsdImport);

        assertEquals(schema, xsdImport.getParent());
        assertTrue(schema.getChildren().contains(xsdImport));
    }

    @Test
    @DisplayName("multiple imports should be independent")
    void testMultipleImportsIndependence() {
        XsdSchema schema = new XsdSchema();
        XsdImport import1 = new XsdImport("http://first.com", "first.xsd");
        XsdImport import2 = new XsdImport("http://second.com", "second.xsd");

        schema.addChild(import1);
        schema.addChild(import2);

        assertEquals(2, schema.getChildren().size());
        assertEquals("http://first.com", import1.getNamespace());
        assertEquals("http://second.com", import2.getNamespace());
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("import XML Schema namespace")
    void testImportXMLSchema() {
        XsdImport imp = new XsdImport("http://www.w3.org/2001/XMLSchema");
        assertEquals("http://www.w3.org/2001/XMLSchema", imp.getNamespace());
        assertNull(imp.getSchemaLocation());
    }

    @Test
    @DisplayName("complete import with namespace and location")
    void testCompleteImport() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/main");

        XsdImport imp = new XsdImport("http://example.com/common", "common/types.xsd");
        imp.setDocumentation("Import common type definitions");

        schema.addChild(imp);

        // Verify structure
        assertEquals(1, schema.getChildren().size());
        XsdImport retrievedImp = (XsdImport) schema.getChildren().get(0);
        assertEquals("http://example.com/common", retrievedImp.getNamespace());
        assertEquals("common/types.xsd", retrievedImp.getSchemaLocation());
        assertEquals("Import common type definitions", retrievedImp.getDocumentation());
    }

    @Test
    @DisplayName("import without schemaLocation")
    void testImportWithoutSchemaLocation() {
        XsdImport imp = new XsdImport("http://example.com/types");
        assertEquals("http://example.com/types", imp.getNamespace());
        assertNull(imp.getSchemaLocation());
    }

    @Test
    @DisplayName("import with schemaLocation hint")
    void testImportWithSchemaLocationHint() {
        XsdImport imp = new XsdImport("http://example.com/types", "http://example.com/schemas/types.xsd");
        assertEquals("http://example.com/types", imp.getNamespace());
        assertEquals("http://example.com/schemas/types.xsd", imp.getSchemaLocation());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("import with empty namespace")
    void testEmptyNamespace() {
        xsdImport.setNamespace("");
        assertEquals("", xsdImport.getNamespace());
    }

    @Test
    @DisplayName("import with whitespace namespace")
    void testWhitespaceNamespace() {
        xsdImport.setNamespace("   ");
        assertEquals("   ", xsdImport.getNamespace());
    }

    @Test
    @DisplayName("import with empty schemaLocation")
    void testEmptySchemaLocation() {
        xsdImport.setSchemaLocation("");
        assertEquals("", xsdImport.getSchemaLocation());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        xsdImport.addPropertyChangeListener(evt -> listener1Fired.set(true));
        xsdImport.addPropertyChangeListener(evt -> listener2Fired.set(true));

        xsdImport.setNamespace("http://new.com");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    @Test
    @DisplayName("import can have child nodes")
    void testImportWithChildren() {
        XsdNode childNode = new XsdElement("child");
        xsdImport.addChild(childNode);

        assertEquals(1, xsdImport.getChildren().size());
        assertEquals(childNode, xsdImport.getChildren().get(0));
    }

    // ========== Multi-File Support (Resolution Tracking) Tests ==========

    @Nested
    @DisplayName("Multi-File Support Tests")
    class MultiFileSupportTests {

        @Test
        @DisplayName("new import should not be resolved")
        void testNewImportNotResolved() {
            XsdImport imp = new XsdImport("http://example.com", "test.xsd");
            assertFalse(imp.isResolved());
            assertNull(imp.getImportedSchema());
            assertNull(imp.getResolvedPath());
            assertNull(imp.getResolutionError());
        }

        @Test
        @DisplayName("setImportedSchema should mark as resolved")
        void testSetImportedSchema() {
            XsdSchema importedSchema = new XsdSchema();
            importedSchema.setTargetNamespace("http://example.com");

            xsdImport.setImportedSchema(importedSchema);

            assertTrue(xsdImport.isResolved());
            assertEquals(importedSchema, xsdImport.getImportedSchema());
        }

        @Test
        @DisplayName("setImportedSchema(null) should mark as not resolved")
        void testSetImportedSchemaNull() {
            XsdSchema importedSchema = new XsdSchema();
            xsdImport.setImportedSchema(importedSchema);
            assertTrue(xsdImport.isResolved());

            xsdImport.setImportedSchema(null);
            assertFalse(xsdImport.isResolved());
            assertNull(xsdImport.getImportedSchema());
        }

        @Test
        @DisplayName("setImportedSchema should fire PropertyChangeEvent")
        void testSetImportedSchemaFiresEvent() {
            AtomicBoolean importedSchemaChanged = new AtomicBoolean(false);
            AtomicBoolean resolvedChanged = new AtomicBoolean(false);

            xsdImport.addPropertyChangeListener(evt -> {
                if ("importedSchema".equals(evt.getPropertyName())) {
                    importedSchemaChanged.set(true);
                } else if ("resolved".equals(evt.getPropertyName())) {
                    resolvedChanged.set(true);
                }
            });

            xsdImport.setImportedSchema(new XsdSchema());

            assertTrue(importedSchemaChanged.get());
            assertTrue(resolvedChanged.get());
        }

        @Test
        @DisplayName("setResolvedPath should set path")
        void testSetResolvedPath() {
            Path path = Path.of("/test/schema.xsd");
            xsdImport.setResolvedPath(path);

            assertEquals(path, xsdImport.getResolvedPath());
        }

        @Test
        @DisplayName("setResolvedPath should fire PropertyChangeEvent")
        void testSetResolvedPathFiresEvent() {
            AtomicBoolean eventFired = new AtomicBoolean(false);

            xsdImport.addPropertyChangeListener(evt -> {
                if ("resolvedPath".equals(evt.getPropertyName())) {
                    eventFired.set(true);
                }
            });

            xsdImport.setResolvedPath(Path.of("/test/schema.xsd"));
            assertTrue(eventFired.get());
        }

        @Test
        @DisplayName("setResolutionError should set error message")
        void testSetResolutionError() {
            xsdImport.setResolutionError("File not found");
            assertEquals("File not found", xsdImport.getResolutionError());
        }

        @Test
        @DisplayName("setResolutionError should fire PropertyChangeEvent")
        void testSetResolutionErrorFiresEvent() {
            AtomicBoolean eventFired = new AtomicBoolean(false);

            xsdImport.addPropertyChangeListener(evt -> {
                if ("resolutionError".equals(evt.getPropertyName())) {
                    eventFired.set(true);
                }
            });

            xsdImport.setResolutionError("Error message");
            assertTrue(eventFired.get());
        }

        @Test
        @DisplayName("markResolutionFailed should set error and clear schema")
        void testMarkResolutionFailed() {
            // First set a successful import
            xsdImport.setImportedSchema(new XsdSchema());
            assertTrue(xsdImport.isResolved());

            // Then mark as failed
            xsdImport.markResolutionFailed("Connection timeout");

            assertFalse(xsdImport.isResolved());
            assertNull(xsdImport.getImportedSchema());
            assertEquals("Connection timeout", xsdImport.getResolutionError());
        }

        @Test
        @DisplayName("getImportFileName should extract filename from schemaLocation")
        void testGetImportFileNameWithPath() {
            xsdImport.setSchemaLocation("path/to/types.xsd");
            assertEquals("types.xsd", xsdImport.getImportFileName());
        }

        @Test
        @DisplayName("getImportFileName should handle URL")
        void testGetImportFileNameWithUrl() {
            xsdImport.setSchemaLocation("http://example.com/schemas/types.xsd");
            assertEquals("types.xsd", xsdImport.getImportFileName());
        }

        @Test
        @DisplayName("getImportFileName should return schemaLocation if no path separator")
        void testGetImportFileNameSimple() {
            xsdImport.setSchemaLocation("types.xsd");
            assertEquals("types.xsd", xsdImport.getImportFileName());
        }

        @Test
        @DisplayName("getImportFileName should return 'unknown' if schemaLocation is null")
        void testGetImportFileNameNull() {
            xsdImport.setSchemaLocation(null);
            assertEquals("unknown", xsdImport.getImportFileName());
        }

        @Test
        @DisplayName("getImportFileName should return 'unknown' if schemaLocation is empty")
        void testGetImportFileNameEmpty() {
            xsdImport.setSchemaLocation("");
            assertEquals("unknown", xsdImport.getImportFileName());
        }

        @Test
        @DisplayName("deepCopy should copy multi-file support properties")
        void testDeepCopyMultiFileProperties() {
            XsdSchema importedSchema = new XsdSchema();
            Path resolvedPath = Path.of("/test/imported.xsd");

            xsdImport.setImportedSchema(importedSchema);
            xsdImport.setResolvedPath(resolvedPath);
            xsdImport.setResolutionError("previous error");

            XsdImport copy = (XsdImport) xsdImport.deepCopy("");

            assertTrue(copy.isResolved());
            assertEquals(importedSchema, copy.getImportedSchema());
            assertEquals(resolvedPath, copy.getResolvedPath());
            assertEquals("previous error", copy.getResolutionError());
        }
    }
}
