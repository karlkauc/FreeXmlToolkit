package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdOpenContent.
 *
 * @since 2.0
 */
class XsdOpenContentTest {

    private XsdOpenContent openContent;

    @BeforeEach
    void setUp() {
        openContent = new XsdOpenContent(XsdOpenContent.Mode.INTERLEAVE);
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name and mode")
    void testDefaultConstructor() {
        XsdOpenContent oc = new XsdOpenContent();
        assertEquals("openContent", oc.getName());
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, oc.getMode());
    }

    @Test
    @DisplayName("constructor with mode should set mode")
    void testConstructorWithMode() {
        XsdOpenContent oc = new XsdOpenContent(XsdOpenContent.Mode.SUFFIX);
        assertEquals("openContent", oc.getName());
        assertEquals(XsdOpenContent.Mode.SUFFIX, oc.getMode());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return OPEN_CONTENT")
    void testGetNodeType() {
        assertEquals(XsdNodeType.OPEN_CONTENT, openContent.getNodeType());
    }

    // ========== Mode Property Tests ==========

    @Test
    @DisplayName("getMode() should return mode")
    void testGetMode() {
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, openContent.getMode());
    }

    @Test
    @DisplayName("setMode() should set mode")
    void testSetMode() {
        openContent.setMode(XsdOpenContent.Mode.SUFFIX);
        assertEquals(XsdOpenContent.Mode.SUFFIX, openContent.getMode());
    }

    @Test
    @DisplayName("setMode() should fire PropertyChangeEvent")
    void testSetModeFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("mode", evt.getPropertyName());
            assertEquals(XsdOpenContent.Mode.INTERLEAVE, evt.getOldValue());
            assertEquals(XsdOpenContent.Mode.SUFFIX, evt.getNewValue());
            eventFired.set(true);
        };

        openContent.addPropertyChangeListener(listener);
        openContent.setMode(XsdOpenContent.Mode.SUFFIX);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setMode() should fire event with correct old and new values")
    void testSetModeMultipleTimes() {
        openContent.setMode(XsdOpenContent.Mode.SUFFIX);

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("mode", evt.getPropertyName());
            assertEquals(XsdOpenContent.Mode.SUFFIX, evt.getOldValue());
            assertEquals(XsdOpenContent.Mode.INTERLEAVE, evt.getNewValue());
            eventFired.set(true);
        };

        openContent.addPropertyChangeListener(listener);
        openContent.setMode(XsdOpenContent.Mode.INTERLEAVE);

        assertTrue(eventFired.get());
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, openContent.getMode());
    }

    @Test
    @DisplayName("setMode() should accept null")
    void testSetModeNull() {
        openContent.setMode(null);
        assertNull(openContent.getMode());
    }

    // ========== Mode Enum Tests ==========

    @Test
    @DisplayName("Mode.INTERLEAVE should have correct value")
    void testModeInterleaveValue() {
        assertEquals("interleave", XsdOpenContent.Mode.INTERLEAVE.getValue());
    }

    @Test
    @DisplayName("Mode.SUFFIX should have correct value")
    void testModeSuffixValue() {
        assertEquals("suffix", XsdOpenContent.Mode.SUFFIX.getValue());
    }

    @Test
    @DisplayName("Mode.fromValue() should return correct mode for 'interleave'")
    void testModeFromValueInterleave() {
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, XsdOpenContent.Mode.fromValue("interleave"));
    }

    @Test
    @DisplayName("Mode.fromValue() should return correct mode for 'suffix'")
    void testModeFromValueSuffix() {
        assertEquals(XsdOpenContent.Mode.SUFFIX, XsdOpenContent.Mode.fromValue("suffix"));
    }

    @Test
    @DisplayName("Mode.fromValue() should return null for invalid value")
    void testModeFromValueInvalid() {
        assertNull(XsdOpenContent.Mode.fromValue("invalid"));
    }

    // ========== Wildcard Tests ==========

    @Test
    @DisplayName("getWildcard() should return null when no wildcard child")
    void testGetWildcardNull() {
        assertNull(openContent.getWildcard());
    }

    @Test
    @DisplayName("getWildcard() should return wildcard child")
    void testGetWildcard() {
        XsdNode wildcardNode = new XsdElement("any");
        openContent.addChild(wildcardNode);

        assertEquals(wildcardNode, openContent.getWildcard());
    }

    @Test
    @DisplayName("getWildcard() should return first child if multiple")
    void testGetWildcardMultiple() {
        XsdNode wildcard1 = new XsdElement("any1");
        XsdNode wildcard2 = new XsdElement("any2");
        openContent.addChild(wildcard1);
        openContent.addChild(wildcard2);

        assertEquals(wildcard1, openContent.getWildcard());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        openContent.setDocumentation("Allow additional elements");

        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("");

        assertNotNull(copy);
        assertNotSame(openContent, copy);
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, copy.getMode());
        assertEquals("Allow additional elements", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("_copy");

        assertEquals("openContent_copy", copy.getName());
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, copy.getMode());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("");

        assertNotEquals(openContent.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("");
        copy.setMode(XsdOpenContent.Mode.SUFFIX);

        assertEquals(XsdOpenContent.Mode.INTERLEAVE, openContent.getMode());
        assertEquals(XsdOpenContent.Mode.SUFFIX, copy.getMode());
    }

    @Test
    @DisplayName("deepCopy() should copy wildcard child")
    void testDeepCopyWithWildcard() {
        XsdNode wildcardNode = new XsdElement("any");
        openContent.addChild(wildcardNode);

        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("");

        assertNotNull(copy.getWildcard());
        assertNotSame(wildcardNode, copy.getWildcard());
    }

    @Test
    @DisplayName("deepCopy() should copy null mode")
    void testDeepCopyWithNullMode() {
        openContent.setMode(null);
        XsdOpenContent copy = (XsdOpenContent) openContent.deepCopy("");

        assertNull(copy.getMode());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("openContent should be addable as child to complexType")
    void testOpenContentAsChildOfComplexType() {
        XsdComplexType complexType = new XsdComplexType("ProductType");
        complexType.addChild(openContent);

        assertEquals(complexType, openContent.getParent());
        assertTrue(complexType.getChildren().contains(openContent));
    }

    @Test
    @DisplayName("openContent should accept wildcard child")
    void testOpenContentWithWildcardChild() {
        XsdNode wildcardNode = new XsdElement("any");
        openContent.addChild(wildcardNode);

        assertEquals(openContent, wildcardNode.getParent());
        assertEquals(1, openContent.getChildren().size());
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete openContent with interleave mode")
    void testCompleteOpenContentInterleave() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        XsdOpenContent oc = new XsdOpenContent(XsdOpenContent.Mode.INTERLEAVE);
        oc.setDocumentation("Allow elements from any namespace to be interleaved");

        XsdNode wildcardNode = new XsdElement("any");
        oc.addChild(wildcardNode);
        complexType.addChild(oc);
        schema.addChild(complexType);

        // Verify structure
        assertEquals(1, complexType.getChildren().size());
        XsdOpenContent retrievedOC = (XsdOpenContent) complexType.getChildren().get(0);
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, retrievedOC.getMode());
        assertNotNull(retrievedOC.getWildcard());
    }

    @Test
    @DisplayName("complete openContent with suffix mode")
    void testCompleteOpenContentSuffix() {
        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        XsdOpenContent oc = new XsdOpenContent(XsdOpenContent.Mode.SUFFIX);
        oc.setDocumentation("Allow elements from other namespaces at the end");

        XsdNode wildcardNode = new XsdElement("any");
        oc.addChild(wildcardNode);
        complexType.addChild(oc);

        assertEquals(XsdOpenContent.Mode.SUFFIX, oc.getMode());
        assertNotNull(oc.getWildcard());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        openContent.addPropertyChangeListener(evt -> listener1Fired.set(true));
        openContent.addPropertyChangeListener(evt -> listener2Fired.set(true));

        openContent.setMode(XsdOpenContent.Mode.SUFFIX);

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    @Test
    @DisplayName("openContent with default mode")
    void testDefaultMode() {
        XsdOpenContent oc = new XsdOpenContent();
        assertEquals(XsdOpenContent.Mode.INTERLEAVE, oc.getMode());
    }
}
