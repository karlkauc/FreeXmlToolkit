package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Core data class for the flat, row-based XML editor layout.
 *
 * <p>In the XMLSpy-style flat layout, every XML item (element, attribute, text node, etc.)
 * is represented as one FlatRow. This replaces the nested grid approach where elements
 * were rendered as nested containers.</p>
 *
 * <p>The flat row list allows efficient rendering via a virtual scroll pane and
 * enables tree-like expand/collapse by toggling the visibility of descendant rows.</p>
 *
 * <p>Example of how an XML document maps to FlatRows:</p>
 * <pre>
 * &lt;book id="123"&gt;           → FlatRow[ELEMENT, depth=0, label="book", childCount=2]
 *   @id="123"               → FlatRow[ATTRIBUTE, depth=1, label="id", value="123"]
 *   &lt;title&gt;XML Guide&lt;/title&gt; → FlatRow[ELEMENT, depth=1, label="title", value="XML Guide"]
 *   &lt;!-- comment --&gt;        → FlatRow[COMMENT, depth=1, value="comment"]
 * &lt;/book&gt;
 * </pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class FlatRow {

    // ==================== RowType Enum ====================

    /**
     * Enumerates the kinds of XML items that can appear as rows.
     */
    public enum RowType {
        /** An XML element (may have children, attributes, text). */
        ELEMENT,
        /** An XML attribute belonging to an element. */
        ATTRIBUTE,
        /** A text node inside an element. */
        TEXT,
        /** An XML comment ({@code <!-- ... -->}). */
        COMMENT,
        /** A CDATA section ({@code <![CDATA[ ... ]]>}). */
        CDATA,
        /** A processing instruction ({@code <?target data?>}). */
        PROCESSING_INSTRUCTION,
        /** The XML document root. */
        DOCUMENT
    }

    // ==================== Core Fields ====================

    /** The type of XML item this row represents. */
    private final RowType type;

    /** The nesting depth of this row (0 = top-level). */
    private final int depth;

    /** The underlying XML model node. */
    private final XmlNode modelNode;

    /** The parent FlatRow in the flattened list (null for root rows). */
    private final FlatRow parentRow;

    /** Display label (element/attribute name, null for text/comment). */
    private final String label;

    /** Display value (attribute value, leaf element text, comment text, etc.). */
    private final String value;

    /** Number of direct children (used to determine expandability). */
    private final int childCount;

    // ==================== State Fields ====================

    /** Whether this row is expanded (children are visible). Default: false. */
    private boolean expanded = false;

    /** Whether this row is visible in the current scroll view. Default: true. */
    private boolean visible = true;

    /** Whether this row is currently selected. Default: false. */
    private boolean selected = false;

    /** Whether the mouse is hovering over this row. Default: false. */
    private boolean hovered = false;

    // ==================== Repeating Table Fields ====================

    /**
     * The repeating elements table for this row, if this element is displayed as a table.
     * Null when not applicable.
     */
    private RepeatingElementsTable repeatingTable = null;

    /**
     * Index of this row's attribute within its parent element's attribute map.
     * -1 when this is not an attribute row (or when index is not yet assigned).
     */
    private int attributeIndex = -1;

    // ==================== Constructor ====================

    /**
     * Constructs a new FlatRow representing one XML item in the flat layout.
     *
     * @param type       the kind of XML item (ELEMENT, ATTRIBUTE, TEXT, etc.)
     * @param depth      nesting depth (0 = root level)
     * @param modelNode  the underlying XML model node
     * @param parentRow  the parent FlatRow, or null for top-level rows
     * @param label      display label (element/attribute name), may be null for TEXT/COMMENT
     * @param value      display value (attribute value or leaf text), may be null
     * @param childCount number of direct children (0 for leaf nodes and non-elements)
     */
    public FlatRow(RowType type, int depth, XmlNode modelNode, FlatRow parentRow,
                   String label, String value, int childCount) {
        this.type = type;
        this.depth = depth;
        this.modelNode = modelNode;
        this.parentRow = parentRow;
        this.label = label;
        this.value = value;
        this.childCount = childCount;
    }

    // ==================== Core Getters ====================

    /**
     * Returns the row type.
     *
     * @return the type of XML item this row represents
     */
    public RowType getType() {
        return type;
    }

    /**
     * Returns the nesting depth.
     *
     * @return the depth (0 = root level)
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Returns the underlying XML model node.
     *
     * @return the model node
     */
    public XmlNode getModelNode() {
        return modelNode;
    }

    /**
     * Returns the parent FlatRow.
     *
     * @return the parent row, or null for root rows
     */
    public FlatRow getParentRow() {
        return parentRow;
    }

    /**
     * Returns the display label (element or attribute name).
     *
     * @return the label, may be null for text/comment rows
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the display value.
     *
     * @return the value (attribute value, leaf text, etc.), may be null
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the number of direct child nodes.
     *
     * @return child count (0 for leaves and non-elements)
     */
    public int getChildCount() {
        return childCount;
    }

    // ==================== State Getters/Setters ====================

    /**
     * Returns whether this row is expanded (its children rows are visible).
     *
     * @return true if expanded
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Sets the expanded state of this row.
     *
     * @param expanded true to expand, false to collapse
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /**
     * Returns whether this row is visible.
     *
     * @return true if visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets the visibility of this row.
     *
     * @param visible true to show, false to hide
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Returns whether this row is selected.
     *
     * @return true if selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Sets the selected state of this row.
     *
     * @param selected true to select, false to deselect
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * Returns whether the mouse is hovering over this row.
     *
     * @return true if hovered
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * Sets the hovered state of this row.
     *
     * @param hovered true if the mouse is over this row
     */
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    // ==================== Repeating Table Getters/Setters ====================

    /**
     * Returns the repeating elements table associated with this row, or null.
     *
     * @return the repeating table, or null if this row is not displayed as a table
     */
    public RepeatingElementsTable getRepeatingTable() {
        return repeatingTable;
    }

    /**
     * Sets the repeating elements table for this row.
     *
     * @param repeatingTable the table, or null to clear
     */
    public void setRepeatingTable(RepeatingElementsTable repeatingTable) {
        this.repeatingTable = repeatingTable;
    }

    /**
     * Returns the attribute index within the parent element's attribute map.
     *
     * @return the index, or -1 if not applicable
     */
    public int getAttributeIndex() {
        return attributeIndex;
    }

    /**
     * Sets the attribute index.
     *
     * @param attributeIndex the index of this attribute in the parent element
     */
    public void setAttributeIndex(int attributeIndex) {
        this.attributeIndex = attributeIndex;
    }

    // ==================== Helper Methods ====================

    /**
     * Returns whether this row can be expanded.
     *
     * <p>A row is expandable if it is an ELEMENT type and has at least one child.</p>
     *
     * @return true if this is an element with children
     */
    public boolean isExpandable() {
        return type == RowType.ELEMENT && childCount > 0;
    }

    /**
     * Returns whether this row is a leaf element that directly contains a text value.
     *
     * <p>A leaf-with-value row is an ELEMENT type with no children but a non-null value.
     * These are typically displayed inline: {@code <title>XML Guide</title>}</p>
     *
     * @return true if this is an element with value and no children
     */
    public boolean isLeafWithValue() {
        return type == RowType.ELEMENT && childCount == 0 && value != null;
    }

    /**
     * Returns whether this row is associated with a repeating elements table.
     *
     * @return true if a repeating table has been assigned to this row
     */
    public boolean hasRepeatingTable() {
        return repeatingTable != null;
    }

    // ==================== Object Methods ====================

    /**
     * Returns a human-readable string representation for debugging.
     *
     * @return debug string showing type, depth, and label
     */
    @Override
    public String toString() {
        return "FlatRow[" + type + ", depth=" + depth + ", label=" + label
                + (value != null ? ", value=" + value : "")
                + ", children=" + childCount
                + (expanded ? ", expanded" : "")
                + (!visible ? ", hidden" : "")
                + "]";
    }
}
