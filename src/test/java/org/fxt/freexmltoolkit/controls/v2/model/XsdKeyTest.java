package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdKey.
 *
 * @since 2.0
 */
class XsdKeyTest {

    private XsdKey key;

    @BeforeEach
    void setUp() {
        key = new XsdKey("employeeKey");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdKey k = new XsdKey();
        assertEquals("key", k.getName());
    }

    @Test
    @DisplayName("constructor with name should set name")
    void testConstructorWithName() {
        XsdKey k = new XsdKey("testKey");
        assertEquals("testKey", k.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return KEY")
    void testGetNodeType() {
        assertEquals(XsdNodeType.KEY, key.getNodeType());
    }

    // ========== Selector Tests ==========

    @Test
    @DisplayName("getSelector() should return null when no selector")
    void testGetSelectorNull() {
        assertNull(key.getSelector());
    }

    @Test
    @DisplayName("getSelector() should return selector child")
    void testGetSelector() {
        XsdSelector selector = new XsdSelector(".//employee");
        key.addChild(selector);

        assertEquals(selector, key.getSelector());
    }

    @Test
    @DisplayName("getSelector() should return first selector if multiple")
    void testGetSelectorMultiple() {
        XsdSelector selector1 = new XsdSelector(".//employee");
        XsdSelector selector2 = new XsdSelector(".//person");
        key.addChild(selector1);
        key.addChild(selector2);

        assertEquals(selector1, key.getSelector());
    }

    // ========== Field Tests ==========

    @Test
    @DisplayName("getFields() should return empty list when no fields")
    void testGetFieldsEmpty() {
        assertTrue(key.getFields().isEmpty());
    }

    @Test
    @DisplayName("getFields() should return all field children")
    void testGetFields() {
        XsdField field1 = new XsdField("@id");
        XsdField field2 = new XsdField("@code");
        key.addChild(field1);
        key.addChild(field2);

        assertEquals(2, key.getFields().size());
        assertTrue(key.getFields().contains(field1));
        assertTrue(key.getFields().contains(field2));
    }

    @Test
    @DisplayName("getFields() should only return field children, not selector")
    void testGetFieldsOnlyFields() {
        XsdSelector selector = new XsdSelector(".//employee");
        XsdField field1 = new XsdField("@id");
        XsdField field2 = new XsdField("@code");

        key.addChild(selector);
        key.addChild(field1);
        key.addChild(field2);

        assertEquals(2, key.getFields().size());
        assertFalse(key.getFields().contains(selector));
    }

    // ========== Complete Identity Constraint Tests ==========

    @Test
    @DisplayName("complete key constraint with selector and fields")
    void testCompleteKeyConstraint() {
        XsdSelector selector = new XsdSelector(".//employee");
        XsdField field1 = new XsdField("@id");
        XsdField field2 = new XsdField("@ssn");

        key.addChild(selector);
        key.addChild(field1);
        key.addChild(field2);

        assertEquals("employeeKey", key.getName());
        assertEquals(selector, key.getSelector());
        assertEquals(2, key.getFields().size());
        assertEquals(".//employee", selector.getXpath());
        assertEquals("@id", field1.getXpath());
        assertEquals("@ssn", field2.getXpath());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        XsdSelector selector = new XsdSelector(".//employee");
        XsdField field = new XsdField("@id");
        key.addChild(selector);
        key.addChild(field);
        key.setDocumentation("Employee key constraint");

        XsdKey copy = (XsdKey) key.deepCopy("");

        assertNotNull(copy);
        assertNotSame(key, copy);
        assertEquals("employeeKey", copy.getName());
        assertEquals("Employee key constraint", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdKey copy = (XsdKey) key.deepCopy("_copy");

        assertEquals("employeeKey_copy", copy.getName());
    }

    @Test
    @DisplayName("deepCopy() should copy selector and fields")
    void testDeepCopyCopiesChildren() {
        XsdSelector selector = new XsdSelector(".//employee");
        XsdField field = new XsdField("@id");
        key.addChild(selector);
        key.addChild(field);

        XsdKey copy = (XsdKey) key.deepCopy("");

        assertNotNull(copy.getSelector());
        assertNotSame(selector, copy.getSelector());
        assertEquals(".//employee", copy.getSelector().getXpath());

        assertEquals(1, copy.getFields().size());
        assertNotSame(field, copy.getFields().get(0));
        assertEquals("@id", copy.getFields().get(0).getXpath());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdKey copy = (XsdKey) key.deepCopy("");

        assertNotEquals(key.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdSelector selector = new XsdSelector(".//employee");
        key.addChild(selector);

        XsdKey copy = (XsdKey) key.deepCopy("");
        XsdSelector copiedSelector = copy.getSelector();
        copiedSelector.setXpath(".//modified");

        assertEquals(".//employee", key.getSelector().getXpath());
        assertEquals(".//modified", copy.getSelector().getXpath());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("key should accept children")
    void testAddChild() {
        XsdSelector selector = new XsdSelector();
        key.addChild(selector);

        assertEquals(1, key.getChildren().size());
        assertEquals(key, selector.getParent());
    }

    @Test
    @DisplayName("key should be addable as child to element")
    void testKeyAsChild() {
        XsdElement element = new XsdElement("Employee");
        element.addChild(key);

        assertEquals(element, key.getParent());
        assertTrue(element.getChildren().contains(key));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("key with null name")
    void testNullName() {
        XsdKey k = new XsdKey(null);
        assertNull(k.getName());
    }

    @Test
    @DisplayName("key with empty name")
    void testEmptyName() {
        XsdKey k = new XsdKey("");
        assertEquals("", k.getName());
    }

    @Test
    @DisplayName("multiple keys on same element should be independent")
    void testMultipleKeysIndependence() {
        XsdElement element = new XsdElement("Employee");
        XsdKey key1 = new XsdKey("key1");
        XsdKey key2 = new XsdKey("key2");

        element.addChild(key1);
        element.addChild(key2);

        XsdField field1 = new XsdField("@id");
        key1.addChild(field1);

        assertEquals(1, key1.getFields().size());
        assertEquals(0, key2.getFields().size());
    }
}
