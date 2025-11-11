package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdUnique.
 *
 * @since 2.0
 */
class XsdUniqueTest {

    private XsdUnique unique;

    @BeforeEach
    void setUp() {
        unique = new XsdUnique("emailUnique");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdUnique u = new XsdUnique();
        assertEquals("unique", u.getName());
    }

    @Test
    @DisplayName("constructor with name should set name")
    void testConstructorWithName() {
        XsdUnique u = new XsdUnique("testUnique");
        assertEquals("testUnique", u.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return UNIQUE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.UNIQUE, unique.getNodeType());
    }

    // ========== Selector Tests ==========

    @Test
    @DisplayName("getSelector() should return null when no selector")
    void testGetSelectorNull() {
        assertNull(unique.getSelector());
    }

    @Test
    @DisplayName("getSelector() should return selector child")
    void testGetSelector() {
        XsdSelector selector = new XsdSelector(".//user");
        unique.addChild(selector);

        assertEquals(selector, unique.getSelector());
    }

    @Test
    @DisplayName("getSelector() should return first selector if multiple")
    void testGetSelectorMultiple() {
        XsdSelector selector1 = new XsdSelector(".//user");
        XsdSelector selector2 = new XsdSelector(".//person");
        unique.addChild(selector1);
        unique.addChild(selector2);

        assertEquals(selector1, unique.getSelector());
    }

    // ========== Field Tests ==========

    @Test
    @DisplayName("getFields() should return empty list when no fields")
    void testGetFieldsEmpty() {
        assertTrue(unique.getFields().isEmpty());
    }

    @Test
    @DisplayName("getFields() should return all field children")
    void testGetFields() {
        XsdField field1 = new XsdField("@email");
        XsdField field2 = new XsdField("@username");
        unique.addChild(field1);
        unique.addChild(field2);

        assertEquals(2, unique.getFields().size());
        assertTrue(unique.getFields().contains(field1));
        assertTrue(unique.getFields().contains(field2));
    }

    @Test
    @DisplayName("getFields() should only return field children, not selector")
    void testGetFieldsOnlyFields() {
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field1 = new XsdField("@email");
        XsdField field2 = new XsdField("@username");

        unique.addChild(selector);
        unique.addChild(field1);
        unique.addChild(field2);

        assertEquals(2, unique.getFields().size());
        assertFalse(unique.getFields().contains(selector));
    }

    // ========== Complete Unique Constraint Tests ==========

    @Test
    @DisplayName("complete unique constraint with selector and fields")
    void testCompleteUniqueConstraint() {
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field = new XsdField("@email");

        unique.addChild(selector);
        unique.addChild(field);

        assertEquals("emailUnique", unique.getName());
        assertEquals(selector, unique.getSelector());
        assertEquals(1, unique.getFields().size());
        assertEquals(".//user", selector.getXpath());
        assertEquals("@email", field.getXpath());
    }

    @Test
    @DisplayName("unique constraint with composite key")
    void testUniqueWithCompositeKey() {
        XsdSelector selector = new XsdSelector(".//person");
        XsdField field1 = new XsdField("@firstName");
        XsdField field2 = new XsdField("@lastName");
        XsdField field3 = new XsdField("@birthDate");

        unique.addChild(selector);
        unique.addChild(field1);
        unique.addChild(field2);
        unique.addChild(field3);

        assertEquals(3, unique.getFields().size());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field = new XsdField("@email");
        unique.addChild(selector);
        unique.addChild(field);
        unique.setDocumentation("Email must be unique");

        XsdUnique copy = (XsdUnique) unique.deepCopy("");

        assertNotNull(copy);
        assertNotSame(unique, copy);
        assertEquals("emailUnique", copy.getName());
        assertEquals("Email must be unique", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdUnique copy = (XsdUnique) unique.deepCopy("_copy");

        assertEquals("emailUnique_copy", copy.getName());
    }

    @Test
    @DisplayName("deepCopy() should copy selector and fields")
    void testDeepCopyCopiesChildren() {
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field = new XsdField("@email");
        unique.addChild(selector);
        unique.addChild(field);

        XsdUnique copy = (XsdUnique) unique.deepCopy("");

        assertNotNull(copy.getSelector());
        assertNotSame(selector, copy.getSelector());
        assertEquals(".//user", copy.getSelector().getXpath());

        assertEquals(1, copy.getFields().size());
        assertNotSame(field, copy.getFields().get(0));
        assertEquals("@email", copy.getFields().get(0).getXpath());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdUnique copy = (XsdUnique) unique.deepCopy("");

        assertNotEquals(unique.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdSelector selector = new XsdSelector(".//user");
        unique.addChild(selector);

        XsdUnique copy = (XsdUnique) unique.deepCopy("");
        XsdSelector copiedSelector = copy.getSelector();
        copiedSelector.setXpath(".//modified");

        assertEquals(".//user", unique.getSelector().getXpath());
        assertEquals(".//modified", copy.getSelector().getXpath());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("unique should accept children")
    void testAddChild() {
        XsdSelector selector = new XsdSelector();
        unique.addChild(selector);

        assertEquals(1, unique.getChildren().size());
        assertEquals(unique, selector.getParent());
    }

    @Test
    @DisplayName("unique should be addable as child to element")
    void testUniqueAsChild() {
        XsdElement element = new XsdElement("User");
        element.addChild(unique);

        assertEquals(element, unique.getParent());
        assertTrue(element.getChildren().contains(unique));
    }

    // ========== Comparison with Key Tests ==========

    @Test
    @DisplayName("unique and key should have different node types")
    void testUniqueDifferentFromKey() {
        XsdKey key = new XsdKey("testKey");

        assertNotEquals(unique.getNodeType(), key.getNodeType());
        assertEquals(XsdNodeType.UNIQUE, unique.getNodeType());
        assertEquals(XsdNodeType.KEY, key.getNodeType());
    }

    @Test
    @DisplayName("unique constraint allows null values unlike key")
    void testUniqueAllowsNulls() {
        // This is a conceptual test - unique allows null values in fields
        // while key does not. The model doesn't enforce this, but documents it.
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field = new XsdField("@optionalId");
        unique.addChild(selector);
        unique.addChild(field);

        // Structure should be valid even for optional fields
        assertNotNull(unique.getSelector());
        assertEquals(1, unique.getFields().size());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("unique with null name")
    void testNullName() {
        XsdUnique u = new XsdUnique(null);
        assertNull(u.getName());
    }

    @Test
    @DisplayName("unique with empty name")
    void testEmptyName() {
        XsdUnique u = new XsdUnique("");
        assertEquals("", u.getName());
    }

    @Test
    @DisplayName("multiple unique constraints on same element should be independent")
    void testMultipleUniquesIndependence() {
        XsdElement element = new XsdElement("User");
        XsdUnique unique1 = new XsdUnique("emailUnique");
        XsdUnique unique2 = new XsdUnique("usernameUnique");

        element.addChild(unique1);
        element.addChild(unique2);

        XsdField field1 = new XsdField("@email");
        unique1.addChild(field1);

        assertEquals(1, unique1.getFields().size());
        assertEquals(0, unique2.getFields().size());
    }

    @Test
    @DisplayName("unique constraint with single field")
    void testUniqueWithSingleField() {
        XsdSelector selector = new XsdSelector(".//product");
        XsdField field = new XsdField("@sku");

        unique.addChild(selector);
        unique.addChild(field);

        assertEquals(1, unique.getFields().size());
    }

    @Test
    @DisplayName("unique constraint with multiple fields")
    void testUniqueWithMultipleFields() {
        XsdSelector selector = new XsdSelector(".//booking");
        XsdField field1 = new XsdField("@roomNumber");
        XsdField field2 = new XsdField("@date");
        XsdField field3 = new XsdField("@timeSlot");

        unique.addChild(selector);
        unique.addChild(field1);
        unique.addChild(field2);
        unique.addChild(field3);

        assertEquals(3, unique.getFields().size());
    }
}
