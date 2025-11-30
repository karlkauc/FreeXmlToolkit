package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdSimpleType.
 *
 * @since 2.0
 */
class XsdSimpleTypeTest {

    private XsdSimpleType simpleType;

    @BeforeEach
    void setUp() {
        simpleType = new XsdSimpleType("TestSimpleType");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should create anonymous type with null name")
    void testDefaultConstructor() {
        XsdSimpleType type = new XsdSimpleType();
        // Anonymous simpleTypes don't have a name attribute
        assertNull(type.getName());
    }

    @Test
    @DisplayName("constructor with name should set name")
    void testConstructorWithName() {
        XsdSimpleType type = new XsdSimpleType("MySimpleType");
        assertEquals("MySimpleType", type.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return SIMPLE_TYPE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.SIMPLE_TYPE, simpleType.getNodeType());
    }

    // ========== Base Type Tests ==========

    @Test
    @DisplayName("base should be null by default")
    void testBaseDefaultValue() {
        assertNull(simpleType.getBase());
    }

    @Test
    @DisplayName("setBase() should set base type")
    void testSetBase() {
        simpleType.setBase("xs:string");
        assertEquals("xs:string", simpleType.getBase());
    }

    @Test
    @DisplayName("setBase() should fire PropertyChangeEvent")
    void testSetBaseFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("base", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("xs:integer", evt.getNewValue());
            eventFired.set(true);
        };

        simpleType.addPropertyChangeListener(listener);
        simpleType.setBase("xs:integer");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setBase() should allow null value")
    void testSetBaseAllowsNull() {
        simpleType.setBase("xs:string");
        simpleType.setBase(null);
        assertNull(simpleType.getBase());
    }

    // ========== Final Flag Tests ==========

    @Test
    @DisplayName("isFinal() should be false by default")
    void testIsFinalDefaultValue() {
        assertFalse(simpleType.isFinal());
    }

    @Test
    @DisplayName("setFinal() should set final flag")
    void testSetFinal() {
        simpleType.setFinal(true);
        assertTrue(simpleType.isFinal());
    }

    @Test
    @DisplayName("setFinal() should fire PropertyChangeEvent")
    void testSetFinalFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("final", evt.getPropertyName());
            assertEquals(false, evt.getOldValue());
            assertEquals(true, evt.getNewValue());
            eventFired.set(true);
        };

        simpleType.addPropertyChangeListener(listener);
        simpleType.setFinal(true);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("simpleType should support children (e.g., restriction, list, union)")
    void testCanHaveChildren() {
        XsdSchema schema = new XsdSchema();
        simpleType.setParent(schema);
        schema.addChild(simpleType);

        assertEquals(schema, simpleType.getParent());
        assertTrue(schema.getChildren().contains(simpleType));
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        simpleType.setBase("xs:string");
        simpleType.setFinal(true);

        XsdSimpleType copy = (XsdSimpleType) simpleType.deepCopy(null);

        assertNotNull(copy);
        assertEquals(simpleType.getName(), copy.getName());
        assertEquals(simpleType.getBase(), copy.getBase());
        assertEquals(simpleType.isFinal(), copy.isFinal());
        assertNotSame(simpleType, copy);
        assertNotEquals(simpleType.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should append suffix to name")
    void testDeepCopyWithSuffix() {
        simpleType.setBase("xs:decimal");

        XsdSimpleType copy = (XsdSimpleType) simpleType.deepCopy("_Copy");

        assertEquals("TestSimpleType_Copy", copy.getName());
        assertEquals(simpleType.getBase(), copy.getBase());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        // Add a child element (using a simple XsdElement for testing)
        XsdElement childElement = new XsdElement("TestChild");
        childElement.setType("xs:string");
        simpleType.addChild(childElement);

        XsdSimpleType copy = (XsdSimpleType) simpleType.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdElement);
        XsdElement copiedElement = (XsdElement) copy.getChildren().get(0);
        assertEquals("TestChild", copiedElement.getName());
        assertEquals("xs:string", copiedElement.getType());
        // Child should be a different instance
        assertNotSame(childElement, copiedElement);
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("simpleType should work in schema context")
    void testSimpleTypeInSchema() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        XsdSimpleType zipCodeType = new XsdSimpleType("ZipCodeType");
        zipCodeType.setBase("xs:string");
        zipCodeType.setParent(schema);
        schema.addChild(zipCodeType);

        assertEquals(1, schema.getChildren().size());
        assertEquals(zipCodeType, schema.getChildren().get(0));
        assertEquals(schema, zipCodeType.getParent());
    }

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> eventCount[0]++;

        simpleType.addPropertyChangeListener(listener);
        simpleType.setBase("xs:string");
        simpleType.setFinal(true);
        simpleType.setBase("xs:integer");

        assertEquals(3, eventCount[0]);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("setBase() with different value should fire event")
    void testSetBaseDifferentValue() {
        simpleType.setBase("xs:string");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("base", evt.getPropertyName());
            assertEquals("xs:string", evt.getOldValue());
            assertEquals("xs:integer", evt.getNewValue());
            eventFired.set(true);
        };

        simpleType.addPropertyChangeListener(listener);
        simpleType.setBase("xs:integer");

        assertTrue(eventFired.get(), "Event should fire when value changes");
    }

    @Test
    @DisplayName("simpleType should support setting name")
    void testSetName() {
        simpleType.setName("NewSimpleType");
        assertEquals("NewSimpleType", simpleType.getName());
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        String toString = simpleType.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("TestSimpleType") || toString.contains("XsdSimpleType"));
    }
}
