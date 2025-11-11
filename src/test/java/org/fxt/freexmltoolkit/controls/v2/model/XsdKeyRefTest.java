package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdKeyRef.
 *
 * @since 2.0
 */
class XsdKeyRefTest {

    private XsdKeyRef keyRef;

    @BeforeEach
    void setUp() {
        keyRef = new XsdKeyRef("employeeKeyRef");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdKeyRef kr = new XsdKeyRef();
        assertEquals("keyref", kr.getName());
        assertNull(kr.getRefer());
    }

    @Test
    @DisplayName("constructor with name should set name")
    void testConstructorWithName() {
        XsdKeyRef kr = new XsdKeyRef("testKeyRef");
        assertEquals("testKeyRef", kr.getName());
        assertNull(kr.getRefer());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return KEYREF")
    void testGetNodeType() {
        assertEquals(XsdNodeType.KEYREF, keyRef.getNodeType());
    }

    // ========== Refer Property Tests ==========

    @Test
    @DisplayName("refer should be null by default")
    void testReferDefaultValue() {
        assertNull(keyRef.getRefer());
    }

    @Test
    @DisplayName("setRefer() should set refer attribute")
    void testSetRefer() {
        keyRef.setRefer("employeeKey");
        assertEquals("employeeKey", keyRef.getRefer());
    }

    @Test
    @DisplayName("setRefer() should fire PropertyChangeEvent")
    void testSetReferFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("refer", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("personKey", evt.getNewValue());
            eventFired.set(true);
        };

        keyRef.addPropertyChangeListener(listener);
        keyRef.setRefer("personKey");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setRefer() should fire event with correct old and new values")
    void testSetReferMultipleTimes() {
        keyRef.setRefer("firstKey");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("refer", evt.getPropertyName());
            assertEquals("firstKey", evt.getOldValue());
            assertEquals("secondKey", evt.getNewValue());
            eventFired.set(true);
        };

        keyRef.addPropertyChangeListener(listener);
        keyRef.setRefer("secondKey");

        assertTrue(eventFired.get());
        assertEquals("secondKey", keyRef.getRefer());
    }

    @Test
    @DisplayName("setRefer() should accept null")
    void testSetReferNull() {
        keyRef.setRefer("someKey");
        keyRef.setRefer(null);
        assertNull(keyRef.getRefer());
    }

    @Test
    @DisplayName("setRefer() should accept qualified names")
    void testSetReferQualifiedName() {
        keyRef.setRefer("ns:employeeKey");
        assertEquals("ns:employeeKey", keyRef.getRefer());
    }

    // ========== Selector Tests ==========

    @Test
    @DisplayName("getSelector() should return null when no selector")
    void testGetSelectorNull() {
        assertNull(keyRef.getSelector());
    }

    @Test
    @DisplayName("getSelector() should return selector child")
    void testGetSelector() {
        XsdSelector selector = new XsdSelector(".//department");
        keyRef.addChild(selector);

        assertEquals(selector, keyRef.getSelector());
    }

    // ========== Field Tests ==========

    @Test
    @DisplayName("getFields() should return empty list when no fields")
    void testGetFieldsEmpty() {
        assertTrue(keyRef.getFields().isEmpty());
    }

    @Test
    @DisplayName("getFields() should return all field children")
    void testGetFields() {
        XsdField field1 = new XsdField("@deptId");
        XsdField field2 = new XsdField("@managerId");
        keyRef.addChild(field1);
        keyRef.addChild(field2);

        assertEquals(2, keyRef.getFields().size());
        assertTrue(keyRef.getFields().contains(field1));
        assertTrue(keyRef.getFields().contains(field2));
    }

    // ========== Complete KeyRef Constraint Tests ==========

    @Test
    @DisplayName("complete keyref constraint with refer, selector and fields")
    void testCompleteKeyRefConstraint() {
        XsdSelector selector = new XsdSelector(".//department");
        XsdField field = new XsdField("@employeeId");

        keyRef.setRefer("employeeKey");
        keyRef.addChild(selector);
        keyRef.addChild(field);

        assertEquals("employeeKeyRef", keyRef.getName());
        assertEquals("employeeKey", keyRef.getRefer());
        assertEquals(selector, keyRef.getSelector());
        assertEquals(1, keyRef.getFields().size());
        assertEquals(".//department", selector.getXpath());
        assertEquals("@employeeId", field.getXpath());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        XsdSelector selector = new XsdSelector(".//department");
        XsdField field = new XsdField("@employeeId");
        keyRef.setRefer("employeeKey");
        keyRef.addChild(selector);
        keyRef.addChild(field);
        keyRef.setDocumentation("Department-Employee reference");

        XsdKeyRef copy = (XsdKeyRef) keyRef.deepCopy("");

        assertNotNull(copy);
        assertNotSame(keyRef, copy);
        assertEquals("employeeKeyRef", copy.getName());
        assertEquals("employeeKey", copy.getRefer());
        assertEquals("Department-Employee reference", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        keyRef.setRefer("employeeKey");

        XsdKeyRef copy = (XsdKeyRef) keyRef.deepCopy("_copy");

        assertEquals("employeeKeyRef_copy", copy.getName());
        assertEquals("employeeKey", copy.getRefer());
    }

    @Test
    @DisplayName("deepCopy() should copy selector and fields")
    void testDeepCopyCopiesChildren() {
        XsdSelector selector = new XsdSelector(".//department");
        XsdField field = new XsdField("@employeeId");
        keyRef.setRefer("employeeKey");
        keyRef.addChild(selector);
        keyRef.addChild(field);

        XsdKeyRef copy = (XsdKeyRef) keyRef.deepCopy("");

        assertNotNull(copy.getSelector());
        assertNotSame(selector, copy.getSelector());
        assertEquals(".//department", copy.getSelector().getXpath());

        assertEquals(1, copy.getFields().size());
        assertNotSame(field, copy.getFields().get(0));
        assertEquals("@employeeId", copy.getFields().get(0).getXpath());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdKeyRef copy = (XsdKeyRef) keyRef.deepCopy("");

        assertNotEquals(keyRef.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        keyRef.setRefer("originalKey");

        XsdKeyRef copy = (XsdKeyRef) keyRef.deepCopy("");
        copy.setRefer("modifiedKey");

        assertEquals("originalKey", keyRef.getRefer());
        assertEquals("modifiedKey", copy.getRefer());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("keyref should reference existing key")
    void testKeyRefReferencesKey() {
        XsdElement employeeElement = new XsdElement("Employee");
        XsdKey key = new XsdKey("employeeKey");
        XsdSelector keySelector = new XsdSelector(".");
        XsdField keyField = new XsdField("@id");
        key.addChild(keySelector);
        key.addChild(keyField);
        employeeElement.addChild(key);

        XsdElement departmentElement = new XsdElement("Department");
        XsdKeyRef keyRefConstraint = new XsdKeyRef("employeeRef");
        keyRefConstraint.setRefer("employeeKey");
        XsdSelector refSelector = new XsdSelector(".//employee");
        XsdField refField = new XsdField("@employeeId");
        keyRefConstraint.addChild(refSelector);
        keyRefConstraint.addChild(refField);
        departmentElement.addChild(keyRefConstraint);

        // Verify structure
        assertEquals("employeeKey", key.getName());
        assertEquals("employeeRef", keyRefConstraint.getName());
        assertEquals("employeeKey", keyRefConstraint.getRefer());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("keyref with empty refer")
    void testEmptyRefer() {
        keyRef.setRefer("");
        assertEquals("", keyRef.getRefer());
    }

    @Test
    @DisplayName("keyref with whitespace refer")
    void testWhitespaceRefer() {
        keyRef.setRefer("   ");
        assertEquals("   ", keyRef.getRefer());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        keyRef.addPropertyChangeListener(evt -> listener1Fired.set(true));
        keyRef.addPropertyChangeListener(evt -> listener2Fired.set(true));

        keyRef.setRefer("testKey");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }
}
