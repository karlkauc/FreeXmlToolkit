package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdField.
 *
 * @since 2.0
 */
class XsdFieldTest {

    private XsdField field;

    @BeforeEach
    void setUp() {
        field = new XsdField();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdField f = new XsdField();
        assertEquals("field", f.getName());
        assertNull(f.getXpath());
    }

    @Test
    @DisplayName("constructor with xpath should set XPath expression")
    void testConstructorWithXpath() {
        XsdField f = new XsdField("@id");
        assertEquals("field", f.getName());
        assertEquals("@id", f.getXpath());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return FIELD")
    void testGetNodeType() {
        assertEquals(XsdNodeType.FIELD, field.getNodeType());
    }

    // ========== XPath Tests ==========

    @Test
    @DisplayName("xpath should be null by default")
    void testXpathDefaultValue() {
        assertNull(field.getXpath());
    }

    @Test
    @DisplayName("setXpath() should set XPath expression")
    void testSetXpath() {
        field.setXpath("@employeeId");
        assertEquals("@employeeId", field.getXpath());
    }

    @Test
    @DisplayName("setXpath() should fire PropertyChangeEvent")
    void testSetXpathFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpath", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("@code", evt.getNewValue());
            eventFired.set(true);
        };

        field.addPropertyChangeListener(listener);
        field.setXpath("@code");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setXpath() should fire event with correct old and new values")
    void testSetXpathMultipleTimes() {
        field.setXpath("@first");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpath", evt.getPropertyName());
            assertEquals("@first", evt.getOldValue());
            assertEquals("@second", evt.getNewValue());
            eventFired.set(true);
        };

        field.addPropertyChangeListener(listener);
        field.setXpath("@second");

        assertTrue(eventFired.get());
        assertEquals("@second", field.getXpath());
    }

    @Test
    @DisplayName("setXpath() should accept null")
    void testSetXpathNull() {
        field.setXpath("@test");
        field.setXpath(null);
        assertNull(field.getXpath());
    }

    @Test
    @DisplayName("setXpath() should accept element-based XPath")
    void testSetXpathElement() {
        String elementXPath = "ns:childElement";
        field.setXpath(elementXPath);
        assertEquals(elementXPath, field.getXpath());
    }

    @Test
    @DisplayName("setXpath() should accept complex XPath expressions")
    void testSetXpathComplexExpression() {
        String complexXPath = "@ns:attribute[contains(., 'value')]";
        field.setXpath(complexXPath);
        assertEquals(complexXPath, field.getXpath());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        field.setXpath("@original");
        field.setDocumentation("Field documentation");

        XsdField copy = (XsdField) field.deepCopy("");

        assertNotNull(copy);
        assertNotSame(field, copy);
        assertEquals("field", copy.getName());
        assertEquals("@original", copy.getXpath());
        assertEquals("Field documentation", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        field.setXpath("@test");

        XsdField copy = (XsdField) field.deepCopy("");

        assertNotEquals(field.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        field.setXpath("@original");

        XsdField copy = (XsdField) field.deepCopy("");
        copy.setXpath("@modified");

        assertEquals("@original", field.getXpath());
        assertEquals("@modified", copy.getXpath());
    }

    @Test
    @DisplayName("deepCopy() should not apply suffix to field name")
    void testDeepCopyIgnoresSuffix() {
        field.setXpath("@test");

        XsdField copy = (XsdField) field.deepCopy("_copy");

        assertEquals("field", copy.getName());
        assertNotEquals("field_copy", copy.getName());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("field should have parent property")
    void testParentProperty() {
        // Parent is set through addChild() on the parent node
        assertNull(field.getParent(), "Parent should be null initially");
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("field should handle empty XPath")
    void testEmptyXpath() {
        field.setXpath("");
        assertEquals("", field.getXpath());
    }

    @Test
    @DisplayName("field should handle whitespace XPath")
    void testWhitespaceXpath() {
        field.setXpath("   ");
        assertEquals("   ", field.getXpath());
    }

    @Test
    @DisplayName("field should handle dot notation XPath")
    void testDotNotationXpath() {
        field.setXpath(".");
        assertEquals(".", field.getXpath());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        field.addPropertyChangeListener(evt -> listener1Fired.set(true));
        field.addPropertyChangeListener(evt -> listener2Fired.set(true));

        field.setXpath("@test");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("multiple fields should be independent")
    void testMultipleFieldsIndependence() {
        XsdField field1 = new XsdField("@id");
        XsdField field2 = new XsdField("@code");

        field1.setXpath("@modified");

        assertEquals("@modified", field1.getXpath());
        assertEquals("@code", field2.getXpath());
    }
}
