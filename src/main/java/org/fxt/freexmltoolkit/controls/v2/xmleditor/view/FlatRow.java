package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // ==================== Static Flattening Algorithm ====================

    /**
     * Recursively walks the XML document tree and produces a flat list of FlatRows.
     *
     * <p>Processing order per element:</p>
     * <ol>
     *   <li>The element row itself</li>
     *   <li>Attribute rows (one per attribute, in insertion order)</li>
     *   <li>Child rows (recursively)</li>
     * </ol>
     *
     * <p>Document-level processing instructions and comments are included at depth 0.
     * Whitespace-only text nodes are skipped. For mixed content elements (both child
     * elements and text nodes), non-whitespace text nodes appear as separate TEXT rows.</p>
     *
     * @param document the XML document to flatten
     * @return ordered list of FlatRows representing the entire document
     */
    public static List<FlatRow> flatten(XmlDocument document) {
        List<FlatRow> rows = new ArrayList<>();
        for (XmlNode child : document.getChildren()) {
            if (child instanceof XmlElement element) {
                flattenElement(element, 0, null, rows, true);
            } else if (child instanceof XmlComment comment) {
                rows.add(new FlatRow(RowType.COMMENT, 0, comment, null, null, comment.getText(), 0));
            } else if (child instanceof XmlProcessingInstruction pi) {
                rows.add(new FlatRow(RowType.PROCESSING_INSTRUCTION, 0, pi, null, pi.getTarget(), pi.getData(), 0));
            }
            // Whitespace-only text nodes at document level are skipped
        }
        return rows;
    }

    /**
     * Processes one element: creates its FlatRow, adds attribute rows, recurses into children.
     *
     * @param element         the element to process
     * @param depth           nesting depth of this element
     * @param parentRow       parent FlatRow (null for root elements)
     * @param rows            accumulator list
     * @param expandByDefault whether this element should start expanded
     */
    private static void flattenElement(XmlElement element, int depth, FlatRow parentRow,
                                       List<FlatRow> rows, boolean expandByDefault) {
        // Determine the child count for this element row.
        // Child count = number of attributes + number of meaningful child nodes
        // (element children + non-whitespace text/comment/cdata/pi children)
        int childCount = element.getAttributes().size();
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement) {
                childCount++;
            } else if (child instanceof XmlComment) {
                childCount++;
            } else if (child instanceof XmlProcessingInstruction) {
                childCount++;
            } else if (child instanceof XmlCData) {
                childCount++;
            } else if (child instanceof XmlText text) {
                if (!text.isWhitespace()) {
                    childCount++;
                }
            }
        }

        // Compute the text value for leaf elements (no child elements)
        String textValue = extractTextContent(element);

        FlatRow elementRow = new FlatRow(RowType.ELEMENT, depth, element, parentRow,
                element.getName(), textValue, childCount);
        elementRow.setExpanded(expandByDefault);
        rows.add(elementRow);

        // Attribute rows at depth+1
        int attrIndex = 0;
        for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
            FlatRow attrRow = new FlatRow(RowType.ATTRIBUTE, depth + 1, element, elementRow,
                    attr.getKey(), attr.getValue(), 0);
            attrRow.setAttributeIndex(attrIndex++);
            rows.add(attrRow);
        }

        // Child nodes - recurse into elements, emit TEXT/COMMENT/CDATA/PI rows
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement childElement) {
                flattenElement(childElement, depth + 1, elementRow, rows, false);
            } else if (child instanceof XmlComment comment) {
                rows.add(new FlatRow(RowType.COMMENT, depth + 1, comment, elementRow,
                        null, comment.getText(), 0));
            } else if (child instanceof XmlProcessingInstruction pi) {
                rows.add(new FlatRow(RowType.PROCESSING_INSTRUCTION, depth + 1, pi, elementRow,
                        pi.getTarget(), pi.getData(), 0));
            } else if (child instanceof XmlCData cdata) {
                rows.add(new FlatRow(RowType.CDATA, depth + 1, cdata, elementRow,
                        null, cdata.getText(), 0));
            } else if (child instanceof XmlText text) {
                // Mixed content: emit a TEXT row only for non-whitespace text
                // when the element also has child elements
                if (!text.isWhitespace() && element.hasElementChildren()) {
                    rows.add(new FlatRow(RowType.TEXT, depth + 1, text, elementRow,
                            null, text.getText().strip(), 0));
                }
                // Pure-leaf text: already captured via extractTextContent(), no separate row
            }
        }
    }

    /**
     * Returns the text content of a leaf element (concatenates all text/CDATA nodes, strips
     * surrounding whitespace). Returns {@code null} if the element has child elements (not a leaf).
     *
     * @param element the element to inspect
     * @return trimmed text content, or null if this is not a leaf element
     */
    private static String extractTextContent(XmlElement element) {
        if (element.hasElementChildren()) {
            return null; // Not a leaf
        }
        StringBuilder sb = new StringBuilder();
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlText text) {
                sb.append(text.getText());
            } else if (child instanceof XmlCData cdata) {
                sb.append(cdata.getText());
            }
        }
        String content = sb.toString().strip();
        return content.isEmpty() ? null : content;
    }

    /**
     * Toggles the expand/collapse state of an element row.
     *
     * <ul>
     *   <li>Collapsing: all descendant rows are hidden (recursive).</li>
     *   <li>Expanding: direct children are shown; for child element rows that are themselves
     *       expanded, their children are recursively shown too.</li>
     * </ul>
     *
     * @param elementRow the element row to toggle
     * @param allRows    the complete flat row list
     */
    public static void toggleExpand(FlatRow elementRow, List<FlatRow> allRows) {
        if (elementRow.getType() != RowType.ELEMENT) {
            return; // Only elements are expandable
        }

        if (elementRow.isExpanded()) {
            // Collapse: hide ALL descendants
            elementRow.setExpanded(false);
            for (FlatRow row : allRows) {
                if (isDescendantOf(row, elementRow)) {
                    row.setVisible(false);
                }
            }
        } else {
            // Expand: show direct children; recursively show if child is also expanded
            elementRow.setExpanded(true);
            for (FlatRow row : allRows) {
                if (row.getParentRow() == elementRow) {
                    row.setVisible(true);
                    // If this child element is itself expanded, show its subtree too
                    if (row.getType() == RowType.ELEMENT && row.isExpanded()) {
                        showExpandedSubtree(row, allRows);
                    }
                }
            }
        }
    }

    /**
     * Recursively makes visible the children of an already-expanded element row.
     * Called during expand to restore previously-visible subtree state.
     *
     * @param elementRow the expanded element whose children should be shown
     * @param allRows    the complete flat row list
     */
    private static void showExpandedSubtree(FlatRow elementRow, List<FlatRow> allRows) {
        for (FlatRow row : allRows) {
            if (row.getParentRow() == elementRow) {
                row.setVisible(true);
                if (row.getType() == RowType.ELEMENT && row.isExpanded()) {
                    showExpandedSubtree(row, allRows);
                }
            }
        }
    }

    /**
     * Checks whether {@code candidate} is a descendant of {@code ancestor} by walking
     * the {@code parentRow} chain.
     *
     * @param candidate the row to test
     * @param ancestor  the potential ancestor row
     * @return true if candidate is a (direct or indirect) descendant of ancestor
     */
    private static boolean isDescendantOf(FlatRow candidate, FlatRow ancestor) {
        FlatRow current = candidate.getParentRow();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParentRow();
        }
        return false;
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
