package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.Test;

/**
 * Tests for FlatRow data class.
 *
 * <p>FlatRow is the core data class for the flat, row-based XML editor layout.
 * Each XML item (element, attribute, text node, etc.) is represented as one FlatRow.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
class FlatRowTest {

    // ==================== Element Row Tests ====================

    /**
     * Tests that an element row stores type, depth, and label correctly.
     */
    @Test
    void testElementRow() {
        XmlElement element = new XmlElement("book");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 2, element, null, "book", null, 3);

        assertEquals(FlatRow.RowType.ELEMENT, row.getType());
        assertEquals(2, row.getDepth());
        assertEquals(element, row.getModelNode());
        assertNull(row.getParentRow());
        assertEquals("book", row.getLabel());
        assertNull(row.getValue());
        assertEquals(3, row.getChildCount());
    }

    /**
     * Tests that an element row with a value (leaf element) stores correctly.
     */
    @Test
    void testLeafElementRow() {
        XmlElement element = new XmlElement("title");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 3, element, null, "title", "XML Guide", 0);

        assertEquals(FlatRow.RowType.ELEMENT, row.getType());
        assertEquals(3, row.getDepth());
        assertEquals("title", row.getLabel());
        assertEquals("XML Guide", row.getValue());
        assertEquals(0, row.getChildCount());
    }

    // ==================== Attribute Row Tests ====================

    /**
     * Tests that an attribute row stores name and value correctly.
     */
    @Test
    void testAttributeRow() {
        XmlElement element = new XmlElement("book");
        FlatRow parentRow = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "book", null, 0);
        FlatRow attrRow = new FlatRow(FlatRow.RowType.ATTRIBUTE, 2, element, parentRow, "id", "123", 0);

        assertEquals(FlatRow.RowType.ATTRIBUTE, attrRow.getType());
        assertEquals(2, attrRow.getDepth());
        assertEquals(element, attrRow.getModelNode());
        assertEquals(parentRow, attrRow.getParentRow());
        assertEquals("id", attrRow.getLabel());
        assertEquals("123", attrRow.getValue());
        assertEquals(0, attrRow.getChildCount());
    }

    /**
     * Tests that attribute index is -1 by default and can be set.
     */
    @Test
    void testAttributeIndex() {
        XmlElement element = new XmlElement("book");
        FlatRow attrRow = new FlatRow(FlatRow.RowType.ATTRIBUTE, 2, element, null, "id", "123", 0);

        assertEquals(-1, attrRow.getAttributeIndex());
        attrRow.setAttributeIndex(0);
        assertEquals(0, attrRow.getAttributeIndex());
        attrRow.setAttributeIndex(5);
        assertEquals(5, attrRow.getAttributeIndex());
    }

    // ==================== Expand/Collapse Tests ====================

    /**
     * Tests that expand/collapse toggles state correctly.
     */
    @Test
    void testExpandCollapse() {
        XmlElement element = new XmlElement("section");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "section", null, 5);

        // Default is not expanded
        assertFalse(row.isExpanded());

        // Expand it
        row.setExpanded(true);
        assertTrue(row.isExpanded());

        // Collapse it
        row.setExpanded(false);
        assertFalse(row.isExpanded());
    }

    // ==================== Visibility Tests ====================

    /**
     * Tests that visibility can be toggled.
     */
    @Test
    void testVisibility() {
        XmlElement element = new XmlElement("item");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "item", null, 0);

        // Default is visible
        assertTrue(row.isVisible());

        // Hide it
        row.setVisible(false);
        assertFalse(row.isVisible());

        // Show it again
        row.setVisible(true);
        assertTrue(row.isVisible());
    }

    // ==================== Helper Method Tests ====================

    /**
     * Tests isExpandable() returns true only for element rows with children.
     */
    @Test
    void testIsExpandable() {
        XmlElement element = new XmlElement("parent");

        // Element with children: expandable
        FlatRow elementWithChildren = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "parent", null, 3);
        assertTrue(elementWithChildren.isExpandable());

        // Element without children: not expandable
        FlatRow leafElement = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "leaf", null, 0);
        assertFalse(leafElement.isExpandable());

        // Attribute row: not expandable (even if childCount > 0)
        FlatRow attributeRow = new FlatRow(FlatRow.RowType.ATTRIBUTE, 1, element, null, "id", "val", 3);
        assertFalse(attributeRow.isExpandable());

        // Text row: not expandable
        XmlText textNode = new XmlText("some text");
        FlatRow textRow = new FlatRow(FlatRow.RowType.TEXT, 1, textNode, null, null, "some text", 0);
        assertFalse(textRow.isExpandable());
    }

    /**
     * Tests isLeafWithValue() returns true only for leaf elements with a value.
     */
    @Test
    void testIsLeafWithValue() {
        XmlElement element = new XmlElement("title");

        // Element with value, no children: leaf with value
        FlatRow leafWithValue = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "title", "XML Guide", 0);
        assertTrue(leafWithValue.isLeafWithValue());

        // Element with children: not a leaf with value
        FlatRow parentElement = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "book", null, 2);
        assertFalse(parentElement.isLeafWithValue());

        // Element with no value: not a leaf with value
        FlatRow leafNoValue = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "empty", null, 0);
        assertFalse(leafNoValue.isLeafWithValue());

        // Attribute row (not ELEMENT): not a leaf with value
        FlatRow attrRow = new FlatRow(FlatRow.RowType.ATTRIBUTE, 1, element, null, "id", "123", 0);
        assertFalse(attrRow.isLeafWithValue());
    }

    /**
     * Tests hasRepeatingTable() returns true only when a repeating table is set.
     */
    @Test
    void testHasRepeatingTable() {
        XmlElement element = new XmlElement("items");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "items", null, 3);

        // No repeating table by default
        assertFalse(row.hasRepeatingTable());
        assertNull(row.getRepeatingTable());

        // Set a mocked repeating table
        RepeatingElementsTable table = mock(RepeatingElementsTable.class);
        row.setRepeatingTable(table);
        assertTrue(row.hasRepeatingTable());
        assertEquals(table, row.getRepeatingTable());

        // Remove it
        row.setRepeatingTable(null);
        assertFalse(row.hasRepeatingTable());
    }

    // ==================== Selection and Hover State Tests ====================

    /**
     * Tests that selected state can be toggled.
     */
    @Test
    void testSelectedState() {
        XmlElement element = new XmlElement("item");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "item", null, 0);

        // Default is not selected
        assertFalse(row.isSelected());

        row.setSelected(true);
        assertTrue(row.isSelected());

        row.setSelected(false);
        assertFalse(row.isSelected());
    }

    /**
     * Tests that hovered state can be toggled.
     */
    @Test
    void testHoveredState() {
        XmlElement element = new XmlElement("item");
        FlatRow row = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "item", null, 0);

        // Default is not hovered
        assertFalse(row.isHovered());

        row.setHovered(true);
        assertTrue(row.isHovered());

        row.setHovered(false);
        assertFalse(row.isHovered());
    }

    // ==================== RowType Enum Tests ====================

    /**
     * Tests that all expected RowType enum values exist.
     */
    @Test
    void testRowTypeValues() {
        FlatRow.RowType[] types = FlatRow.RowType.values();
        assertNotNull(types);
        assertEquals(7, types.length);

        // Verify all expected types are present
        assertNotNull(FlatRow.RowType.ELEMENT);
        assertNotNull(FlatRow.RowType.ATTRIBUTE);
        assertNotNull(FlatRow.RowType.TEXT);
        assertNotNull(FlatRow.RowType.COMMENT);
        assertNotNull(FlatRow.RowType.CDATA);
        assertNotNull(FlatRow.RowType.PROCESSING_INSTRUCTION);
        assertNotNull(FlatRow.RowType.DOCUMENT);
    }
}
