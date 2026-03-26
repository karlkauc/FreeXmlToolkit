package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.DisplayName;
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

    // ==================== Flatten Algorithm Tests ====================

    /**
     * Tests that flatten() produces element row at depth 0, followed by attribute rows at depth 1.
     */
    @Test
    @DisplayName("Flatten simple element with attributes")
    void testFlattenElementWithAttributes() {
        XmlDocument document = new XmlDocument();
        XmlElement company = new XmlElement("Company");
        company.setAttribute("xmlns", "http://example.com");
        company.setAttribute("version", "2.0");
        document.addChild(company);

        List<FlatRow> rows = FlatRow.flatten(document);

        // Should have: Company(ELEMENT) + 2 attribute rows = 3 rows
        assertEquals(3, rows.size());

        FlatRow companyRow = rows.get(0);
        assertEquals(FlatRow.RowType.ELEMENT, companyRow.getType());
        assertEquals(0, companyRow.getDepth());
        assertEquals("Company", companyRow.getLabel());
        assertTrue(companyRow.isExpanded());

        FlatRow attr1 = rows.get(1);
        assertEquals(FlatRow.RowType.ATTRIBUTE, attr1.getType());
        assertEquals(1, attr1.getDepth());

        FlatRow attr2 = rows.get(2);
        assertEquals(FlatRow.RowType.ATTRIBUTE, attr2.getType());
        assertEquals(1, attr2.getDepth());
    }

    /**
     * Tests that flatten() assigns correct depths for nested elements.
     * Root &gt; Child &gt; Grandchild("text") must produce depths 0, 1, 2.
     */
    @Test
    @DisplayName("Flatten nested elements produces correct depth")
    void testFlattenNestedElements() {
        XmlDocument document = new XmlDocument();
        XmlElement root = new XmlElement("Root");
        XmlElement child = new XmlElement("Child");
        XmlElement grandchild = new XmlElement("Grandchild");
        grandchild.addChild(new XmlText("text"));
        child.addChild(grandchild);
        root.addChild(child);
        document.addChild(root);

        List<FlatRow> rows = FlatRow.flatten(document);

        // Root(0), Child(1), Grandchild(2) = 3 rows; no attributes
        assertEquals(3, rows.size());

        assertEquals(0, rows.get(0).getDepth());
        assertEquals("Root", rows.get(0).getLabel());

        assertEquals(1, rows.get(1).getDepth());
        assertEquals("Child", rows.get(1).getLabel());

        FlatRow grandchildRow = rows.get(2);
        assertEquals(2, grandchildRow.getDepth());
        assertEquals("Grandchild", grandchildRow.getLabel());
        // Leaf element: value should be the text content
        assertEquals("text", grandchildRow.getValue());
    }

    /**
     * Tests that attribute rows appear before child element rows.
     */
    @Test
    @DisplayName("Flatten produces attributes before child elements")
    void testFlattenOrderAttributesFirst() {
        XmlDocument document = new XmlDocument();
        XmlElement root = new XmlElement("Root");
        root.setAttribute("id", "42");
        XmlElement child = new XmlElement("Child");
        root.addChild(child);
        document.addChild(root);

        List<FlatRow> rows = FlatRow.flatten(document);

        // Root(0), @id(1), Child(1) = 3 rows
        assertEquals(3, rows.size());

        // Find attribute row and child element row indices
        int attrIndex = -1;
        int childElemIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            FlatRow row = rows.get(i);
            if (row.getType() == FlatRow.RowType.ATTRIBUTE && "id".equals(row.getLabel())) {
                attrIndex = i;
            } else if (row.getType() == FlatRow.RowType.ELEMENT && "Child".equals(row.getLabel())) {
                childElemIndex = i;
            }
        }

        assertTrue(attrIndex >= 0, "Attribute row not found");
        assertTrue(childElemIndex >= 0, "Child element row not found");
        assertTrue(attrIndex < childElemIndex, "Attribute row must come before child element row");
    }

    /**
     * Tests that toggling visibility hides and shows descendants correctly.
     */
    @Test
    @DisplayName("Toggling visibility hides descendants")
    void testToggleVisibility() {
        XmlDocument document = new XmlDocument();
        XmlElement root = new XmlElement("Root");
        root.setAttribute("id", "1");
        XmlElement child = new XmlElement("Child");
        child.addChild(new XmlText("hello"));
        root.addChild(child);
        document.addChild(root);

        List<FlatRow> rows = FlatRow.flatten(document);

        // All rows visible initially: Root(expanded), @id, Child
        assertEquals(3, rows.size());
        assertTrue(rows.stream().allMatch(FlatRow::isVisible));

        // Collapse Root -> Root and its direct attributes stay visible, child elements hidden
        FlatRow rootRow = rows.get(0);
        FlatRow.toggleExpand(rootRow, rows);

        assertFalse(rootRow.isExpanded());
        assertTrue(rootRow.isVisible());
        assertTrue(rows.get(1).isVisible(), "@id should stay visible (attribute of collapsed element)");
        assertFalse(rows.get(2).isVisible(), "Child should be hidden");

        // Expand Root again -> all visible again
        FlatRow.toggleExpand(rootRow, rows);

        assertTrue(rootRow.isExpanded());
        assertTrue(rows.stream().allMatch(FlatRow::isVisible));
    }

    // ==================== Repeating Elements Detection Tests ====================

    /**
     * Tests that repeating sibling elements (2+ with the same tag name)
     * are collapsed into a single FlatRow with the group size as childCount.
     */
    @Test
    @DisplayName("Flatten detects repeating elements and creates single row per group")
    void testFlattenRepeatingElements() {
        XmlDocument document = new XmlDocument();
        XmlElement root = new XmlElement("Root");
        root.addChild(createElementWithText("Item", "A"));
        root.addChild(createElementWithText("Item", "B"));
        root.addChild(createElementWithText("Item", "C"));
        root.addChild(createElementWithText("Other", "X"));
        document.addChild(root);

        List<FlatRow> rows = FlatRow.flatten(document);

        // Root(0), Item group row(1), Other(1) = 3 rows
        // The three <Item> elements are collapsed into a single row
        assertEquals(3, rows.size());

        FlatRow rootRow = rows.get(0);
        assertEquals("Root", rootRow.getLabel());

        // Find the Item group row
        FlatRow itemRow = rows.get(1);
        assertEquals(FlatRow.RowType.ELEMENT, itemRow.getType());
        assertEquals("Item", itemRow.getLabel());
        assertEquals(3, itemRow.getChildCount(), "Repeating group should have count = 3");
        assertEquals(1, itemRow.getDepth());

        // Other should still be a normal row
        FlatRow otherRow = rows.get(2);
        assertEquals("Other", otherRow.getLabel());
        assertEquals("X", otherRow.getValue());
    }

    /**
     * Tests that single elements (appearing only once) are NOT treated as repeating.
     */
    @Test
    @DisplayName("Flatten does not collapse single elements")
    void testFlattenSingleElementsNotGrouped() {
        XmlDocument document = new XmlDocument();
        XmlElement root = new XmlElement("Root");
        root.addChild(createElementWithText("A", "1"));
        root.addChild(createElementWithText("B", "2"));
        root.addChild(createElementWithText("C", "3"));
        document.addChild(root);

        List<FlatRow> rows = FlatRow.flatten(document);

        // Root(0), A(1), B(1), C(1) = 4 rows (no grouping because each name appears only once)
        assertEquals(4, rows.size());
        assertEquals("A", rows.get(1).getLabel());
        assertEquals("1", rows.get(1).getValue());
        assertEquals("B", rows.get(2).getLabel());
        assertEquals("C", rows.get(3).getLabel());
    }

    private XmlElement createElementWithText(String name, String text) {
        XmlElement element = new XmlElement(name);
        element.addChild(new XmlText(text));
        return element;
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
