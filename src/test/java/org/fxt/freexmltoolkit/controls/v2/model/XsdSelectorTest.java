package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdSelector.
 *
 * @since 2.0
 */
class XsdSelectorTest {

    private XsdSelector selector;

    @BeforeEach
    void setUp() {
        selector = new XsdSelector();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdSelector s = new XsdSelector();
        assertEquals("selector", s.getName());
        assertNull(s.getXpath());
    }

    @Test
    @DisplayName("constructor with xpath should set XPath expression")
    void testConstructorWithXpath() {
        XsdSelector s = new XsdSelector(".//person");
        assertEquals("selector", s.getName());
        assertEquals(".//person", s.getXpath());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return SELECTOR")
    void testGetNodeType() {
        assertEquals(XsdNodeType.SELECTOR, selector.getNodeType());
    }

    // ========== XPath Tests ==========

    @Test
    @DisplayName("xpath should be null by default")
    void testXpathDefaultValue() {
        assertNull(selector.getXpath());
    }

    @Test
    @DisplayName("setXpath() should set XPath expression")
    void testSetXpath() {
        selector.setXpath(".//employee");
        assertEquals(".//employee", selector.getXpath());
    }

    @Test
    @DisplayName("setXpath() should fire PropertyChangeEvent")
    void testSetXpathFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpath", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals(".//customer", evt.getNewValue());
            eventFired.set(true);
        };

        selector.addPropertyChangeListener(listener);
        selector.setXpath(".//customer");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setXpath() should fire event with correct old and new values")
    void testSetXpathMultipleTimes() {
        selector.setXpath(".//first");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpath", evt.getPropertyName());
            assertEquals(".//first", evt.getOldValue());
            assertEquals(".//second", evt.getNewValue());
            eventFired.set(true);
        };

        selector.addPropertyChangeListener(listener);
        selector.setXpath(".//second");

        assertTrue(eventFired.get());
        assertEquals(".//second", selector.getXpath());
    }

    @Test
    @DisplayName("setXpath() should accept null")
    void testSetXpathNull() {
        selector.setXpath(".//test");
        selector.setXpath(null);
        assertNull(selector.getXpath());
    }

    @Test
    @DisplayName("setXpath() should accept complex XPath expressions")
    void testSetXpathComplexExpression() {
        String complexXPath = "./ns:element[@id='test']/ns:child[position()=1]";
        selector.setXpath(complexXPath);
        assertEquals(complexXPath, selector.getXpath());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        selector.setXpath(".//original");
        selector.setDocumentation("Test documentation");

        XsdSelector copy = (XsdSelector) selector.deepCopy("");

        assertNotNull(copy);
        assertNotSame(selector, copy);
        assertEquals("selector", copy.getName());
        assertEquals(".//original", copy.getXpath());
        assertEquals("Test documentation", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        selector.setXpath(".//test");

        XsdSelector copy = (XsdSelector) selector.deepCopy("");

        assertNotEquals(selector.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        selector.setXpath(".//original");

        XsdSelector copy = (XsdSelector) selector.deepCopy("");
        copy.setXpath(".//modified");

        assertEquals(".//original", selector.getXpath());
        assertEquals(".//modified", copy.getXpath());
    }

    @Test
    @DisplayName("deepCopy() should not apply suffix to selector name")
    void testDeepCopyIgnoresSuffix() {
        selector.setXpath(".//test");

        XsdSelector copy = (XsdSelector) selector.deepCopy("_copy");

        assertEquals("selector", copy.getName());
        assertNotEquals("selector_copy", copy.getName());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("selector should have parent property")
    void testParentProperty() {
        // Parent is set through addChild() on the parent node
        assertNull(selector.getParent(), "Parent should be null initially");
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("selector should handle empty XPath")
    void testEmptyXpath() {
        selector.setXpath("");
        assertEquals("", selector.getXpath());
    }

    @Test
    @DisplayName("selector should handle whitespace XPath")
    void testWhitespaceXpath() {
        selector.setXpath("   ");
        assertEquals("   ", selector.getXpath());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        selector.addPropertyChangeListener(evt -> listener1Fired.set(true));
        selector.addPropertyChangeListener(evt -> listener2Fired.set(true));

        selector.setXpath(".//test");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }
}
