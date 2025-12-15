package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a nested grid node for hierarchical XML visualization.
 *
 * <p>Each node renders as its own mini-grid with:</p>
 * <ul>
 *   <li>Header with element name and expand/collapse button</li>
 *   <li>Attribute rows (name | value)</li>
 *   <li>Text content row (if present)</li>
 *   <li>Child grids nested inside</li>
 * </ul>
 *
 * <p>Layout is calculated bottom-up (children first) for sizes,
 * then top-down for positions.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class NestedGridNode {

    // ==================== Layout Constants ====================

    public static final double HEADER_HEIGHT = 28;
    public static final double ROW_HEIGHT = 24;
    public static final double CHILDREN_HEADER_HEIGHT = 20;
    public static final double INDENT = 20;
    public static final double CHILD_SPACING = 8;
    public static final double COMPACT_CHILD_SPACING = 4;  // Reduced spacing for compact leaf nodes
    public static final double GRID_PADDING = 8;
    public static final double MIN_GRID_WIDTH = 200;
    public static final double ATTR_NAME_WIDTH = 120;
    public static final int MAX_DEPTH = 30;

    // ==================== Model ====================

    private final XmlNode modelNode;
    private final NestedGridNode parent;
    private final List<NestedGridNode> children = new ArrayList<>();
    private final List<RepeatingElementsTable> repeatingTables = new ArrayList<>();
    private final int depth;

    // ==================== Grid Data ====================

    private final List<AttributeCell> attributeCells = new ArrayList<>();
    private String textContent;
    private String elementName;
    private NodeType nodeType;
    private boolean isLeafWithText = false;  // Element has only text content, no child elements

    // ==================== Layout ====================

    private double x;
    private double y;
    private double width;
    private double height;

    // ==================== State ====================

    private boolean expanded = true;
    private boolean selected = false;
    private boolean hovered = false;
    private boolean headerHovered = false;
    private boolean skipOwnHeader = false;  // If true, don't render own header (for inline children display)

    // ==================== Property Change Support ====================

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private PropertyChangeListener modelListener;

    // ==================== Node Type Enum ====================

    public enum NodeType {
        ELEMENT,
        TEXT,
        COMMENT,
        CDATA,
        PROCESSING_INSTRUCTION,
        DOCUMENT
    }

    // ==================== Attribute Cell ====================

    public static class AttributeCell {
        private final String name;
        private String value;

        public AttributeCell(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // ==================== Constructor ====================

    public NestedGridNode(XmlNode modelNode, NestedGridNode parent, int depth) {
        this.modelNode = modelNode;
        this.parent = parent;
        this.depth = depth;

        updateFromModel();
        setupModelListener();
    }

    // ==================== Model Synchronization ====================

    private void updateFromModel() {
        attributeCells.clear();
        textContent = null;
        isLeafWithText = false;

        if (modelNode instanceof XmlElement) {
            XmlElement element = (XmlElement) modelNode;
            this.nodeType = NodeType.ELEMENT;
            this.elementName = element.getName();

            // Extract attributes
            for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
                attributeCells.add(new AttributeCell(attr.getKey(), attr.getValue()));
            }

            // Extract text content and check if leaf element
            this.textContent = extractTextContent(element);
            this.isLeafWithText = (textContent != null && !textContent.isEmpty());

        } else if (modelNode instanceof XmlText) {
            this.nodeType = NodeType.TEXT;
            this.elementName = "#text";
            this.textContent = ((XmlText) modelNode).getText();

        } else if (modelNode instanceof XmlComment) {
            this.nodeType = NodeType.COMMENT;
            this.elementName = "<!-- -->";
            this.textContent = ((XmlComment) modelNode).getText();

        } else if (modelNode instanceof XmlCData) {
            this.nodeType = NodeType.CDATA;
            this.elementName = "<![CDATA[]]>";
            this.textContent = ((XmlCData) modelNode).getText();

        } else if (modelNode instanceof XmlProcessingInstruction) {
            XmlProcessingInstruction pi = (XmlProcessingInstruction) modelNode;
            this.nodeType = NodeType.PROCESSING_INSTRUCTION;
            this.elementName = "<?" + pi.getTarget() + "?>";
            this.textContent = pi.getData();

        } else if (modelNode instanceof XmlDocument) {
            this.nodeType = NodeType.DOCUMENT;
            this.elementName = "Document";

        } else {
            this.nodeType = NodeType.ELEMENT;
            this.elementName = modelNode.getClass().getSimpleName();
        }
    }

    private String extractTextContent(XmlElement element) {
        // First check if this element has any element children
        boolean hasElementChildren = false;
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement) {
                hasElementChildren = true;
                break;
            }
        }

        // If it has element children, it's not a leaf - return null
        if (hasElementChildren) {
            return null;
        }

        // Extract text content from text nodes
        StringBuilder text = new StringBuilder();
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlText) {
                String t = ((XmlText) child).getText().trim();
                if (!t.isEmpty()) {
                    if (text.length() > 0) text.append(" ");
                    text.append(t);
                }
            }
        }

        return text.length() > 0 ? text.toString() : null;
    }

    private void setupModelListener() {
        modelListener = evt -> {
            updateFromModel();
            pcs.firePropertyChange("modelUpdated", null, this);
        };
        modelNode.addPropertyChangeListener(modelListener);
    }

    // ==================== Tree Building ====================

    /**
     * Builds a nested grid tree from an XML document.
     */
    public static NestedGridNode buildTree(XmlDocument document) {
        NestedGridNode root = new NestedGridNode(document, null, 0);
        buildChildren(root, document.getChildren(), 1);
        return root;
    }

    /**
     * Builds a NestedGridNode from an XmlElement with all its children.
     * Used for creating child grids in expanded table cells.
     */
    public static NestedGridNode buildFromElement(XmlElement element, int depth) {
        NestedGridNode node = new NestedGridNode(element, null, depth);
        buildChildren(node, element.getChildren(), depth + 1);
        node.setExpanded(true);  // Start expanded
        return node;
    }

    /**
     * Builds a container NestedGridNode that only shows the children of an element,
     * without showing the element itself. Used in table cells where the column header
     * already shows the element name.
     *
     * @param element The element whose children should be displayed
     * @param depth   The depth level for rendering
     * @return A container node with only the children (no header for the element itself)
     */
    public static NestedGridNode buildChildrenOnly(XmlElement element, int depth) {
        // Create a "virtual" container node that just holds children
        NestedGridNode container = new NestedGridNode(element, null, depth);
        container.setSkipOwnHeader(true);  // Flag to skip rendering own header
        buildChildren(container, element.getChildren(), depth);
        container.setExpanded(true);
        return container;
    }

    private static void buildChildren(NestedGridNode parent, List<XmlNode> children, int depth) {
        if (depth > MAX_DEPTH) return;

        // If parent is a leaf element with only text content, don't add text children
        // The text is already displayed in the parent's header
        if (parent.isLeafWithText()) {
            return; // No children to add - text is shown inline
        }

        // First, identify repeating elements (same name appearing 2+ times)
        Map<String, List<XmlElement>> elementsByName = new LinkedHashMap<>();

        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                XmlElement element = (XmlElement) child;
                elementsByName.computeIfAbsent(element.getName(), k -> new ArrayList<>()).add(element);
            }
        }

        // Create tables for repeating elements (2+ with same name)
        Set<String> groupedElementNames = new HashSet<>();
        for (Map.Entry<String, List<XmlElement>> entry : elementsByName.entrySet()) {
            if (entry.getValue().size() >= 2) {
                RepeatingElementsTable table = new RepeatingElementsTable(
                    entry.getKey(), entry.getValue(), parent, depth);
                parent.repeatingTables.add(table);
                groupedElementNames.add(entry.getKey());
            }
        }

        // Check if there are any element children (not just text)
        boolean hasElementChildren = !elementsByName.isEmpty();

        // Add non-grouped elements as individual nodes
        for (XmlNode child : children) {
            // Skip whitespace-only text nodes
            if (child instanceof XmlText) {
                String text = ((XmlText) child).getText().trim();
                if (text.isEmpty()) continue;
                // Skip text nodes if parent has element children (mixed content)
                // Text will be shown as #text row in parent, not as separate child
                if (hasElementChildren) continue;
            }

            // Skip elements that are in a repeating table
            if (child instanceof XmlElement) {
                XmlElement element = (XmlElement) child;
                if (groupedElementNames.contains(element.getName())) {
                    continue; // Already in a table
                }
            }

            NestedGridNode node = new NestedGridNode(child, parent, depth);
            parent.children.add(node);

            // Recursively add children for elements
            if (child instanceof XmlElement) {
                XmlElement element = (XmlElement) child;
                buildChildren(node, element.getChildren(), depth + 1);
            }
        }
    }

    // ==================== Layout Calculation ====================

    /**
     * Calculates the height of this grid including children and tables.
     * Must be called bottom-up (children first).
     */
    public double calculateHeight() {
        // If skipOwnHeader is true, only calculate children height (no own header/content)
        if (skipOwnHeader) {
            double childrenHeight = 0;
            for (RepeatingElementsTable table : repeatingTables) {
                table.calculateHeight();
                childrenHeight += table.getHeight() + CHILD_SPACING;
            }
            for (NestedGridNode child : children) {
                boolean isCompactLeaf = child.isLeafWithText && child.attributeCells.isEmpty();
                childrenHeight += child.getHeight() + (isCompactLeaf ? COMPACT_CHILD_SPACING : CHILD_SPACING);
            }
            this.height = Math.max(childrenHeight, CHILD_SPACING);  // Minimum spacing
            return this.height;
        }

        // Leaf elements with only text: compact height (just header, minimal padding)
        if (isLeafWithText && attributeCells.isEmpty()) {
            this.height = HEADER_HEIGHT;
            return this.height;
        }

        // Content height
        double contentHeight = HEADER_HEIGHT;

        // Attribute rows
        contentHeight += attributeCells.size() * ROW_HEIGHT;

        // Text content row - only if NOT a leaf with text (leaf text is shown in header)
        if (hasTextContent() && !isLeafWithText) {
            contentHeight += ROW_HEIGHT;
        }

        // Children (including tables)
        double childrenHeight = 0;
        boolean hasChildrenContent = !children.isEmpty() || !repeatingTables.isEmpty();

        if (expanded && hasChildrenContent) {
            childrenHeight = CHILDREN_HEADER_HEIGHT;

            // Add height for repeating element tables
            for (RepeatingElementsTable table : repeatingTables) {
                table.calculateHeight();
                childrenHeight += table.getHeight() + CHILD_SPACING;
            }

            // Add height for individual children
            for (NestedGridNode child : children) {
                boolean isCompactLeaf = child.isLeafWithText && child.attributeCells.isEmpty();
                childrenHeight += child.getHeight() + (isCompactLeaf ? COMPACT_CHILD_SPACING : CHILD_SPACING);
            }
        }

        this.height = contentHeight + childrenHeight + GRID_PADDING;
        return this.height;
    }

    /**
     * Calculates the width of this grid.
     */
    public double calculateWidth(double availableWidth) {
        // Minimum width
        double w = Math.max(MIN_GRID_WIDTH, availableWidth - depth * INDENT);

        // Check children width
        if (expanded) {
            // Check repeating tables width
            for (RepeatingElementsTable table : repeatingTables) {
                double tableWidth = table.calculateWidth(w - INDENT) + INDENT;
                w = Math.max(w, tableWidth);
            }

            // Check individual children width
            for (NestedGridNode child : children) {
                double childWidth = child.calculateWidth(w - INDENT) + INDENT;
                w = Math.max(w, childWidth);
            }
        }

        this.width = w;
        return this.width;
    }

    /**
     * Returns the Y position where the content ends (before children).
     */
    public double getContentEndY() {
        // If skipOwnHeader is true, there is no own content - return y position
        if (skipOwnHeader) {
            return y;
        }
        double endY = y + HEADER_HEIGHT;
        endY += attributeCells.size() * ROW_HEIGHT;
        if (textContent != null && !textContent.isEmpty()) {
            endY += ROW_HEIGHT;
        }
        return endY;
    }

    /**
     * Returns the Y position where children start.
     */
    public double getChildrenStartY() {
        // If skipOwnHeader is true, children start directly at y position
        if (skipOwnHeader) {
            return y;
        }
        return getContentEndY() + CHILDREN_HEADER_HEIGHT;
    }

    // ==================== Hit Testing ====================

    /**
     * Tests if a point is inside this grid's header.
     */
    public boolean isHeaderHit(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + HEADER_HEIGHT;
    }

    /**
     * Tests if a point is inside this grid.
     */
    public boolean containsPoint(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + height;
    }

    /**
     * Finds the attribute cell at the given point.
     * @return index of attribute, or -1 if not found
     */
    public int getAttributeIndexAt(double px, double py) {
        if (px < x || px > x + width) return -1;

        double rowY = y + HEADER_HEIGHT;
        for (int i = 0; i < attributeCells.size(); i++) {
            if (py >= rowY && py < rowY + ROW_HEIGHT) {
                return i;
            }
            rowY += ROW_HEIGHT;
        }
        return -1;
    }

    /**
     * Tests if point is on text content row.
     */
    public boolean isTextContentHit(double px, double py) {
        if (textContent == null || textContent.isEmpty()) return false;

        double textY = y + HEADER_HEIGHT + attributeCells.size() * ROW_HEIGHT;
        return px >= x && px <= x + width &&
               py >= textY && py < textY + ROW_HEIGHT;
    }

    // ==================== Visibility ====================

    /**
     * Tests if this grid is visible in the viewport.
     */
    public boolean isVisible(double viewportTop, double viewportBottom) {
        double nodeTop = y;
        double nodeBottom = y + height;
        return nodeBottom >= viewportTop && nodeTop <= viewportBottom;
    }

    // ==================== Getters and Setters ====================

    public XmlNode getModelNode() { return modelNode; }
    public NestedGridNode getParent() { return parent; }
    public List<NestedGridNode> getChildren() { return children; }
    public List<RepeatingElementsTable> getRepeatingTables() { return repeatingTables; }
    public boolean hasChildren() { return !children.isEmpty() || !repeatingTables.isEmpty(); }
    public boolean hasIndividualChildren() { return !children.isEmpty(); }
    public boolean hasRepeatingTables() { return !repeatingTables.isEmpty(); }
    public int getDepth() { return depth; }

    public String getElementName() { return elementName; }
    public NodeType getNodeType() { return nodeType; }
    public List<AttributeCell> getAttributeCells() { return attributeCells; }
    public String getTextContent() { return textContent; }
    public boolean hasTextContent() { return textContent != null && !textContent.isEmpty(); }
    public boolean isLeafWithText() { return isLeafWithText; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) {
        boolean old = this.expanded;
        this.expanded = expanded;
        pcs.firePropertyChange("expanded", old, expanded);
    }

    /**
     * Check if this node has content that can be expanded/collapsed.
     * A node is expandable if it has children or repeating element tables.
     */
    public boolean hasExpandableContent() {
        return !children.isEmpty() || !repeatingTables.isEmpty();
    }
    public void toggleExpanded() { setExpanded(!expanded); }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) {
        boolean old = this.selected;
        this.selected = selected;
        pcs.firePropertyChange("selected", old, selected);
    }

    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) {
        boolean old = this.hovered;
        this.hovered = hovered;
        pcs.firePropertyChange("hovered", old, hovered);
    }

    public boolean isHeaderHovered() { return headerHovered; }
    public void setHeaderHovered(boolean headerHovered) {
        this.headerHovered = headerHovered;
    }

    public boolean isSkipOwnHeader() { return skipOwnHeader; }
    public void setSkipOwnHeader(boolean skipOwnHeader) {
        this.skipOwnHeader = skipOwnHeader;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Disposes this node and all children.
     */
    public void dispose() {
        if (modelListener != null) {
            modelNode.removePropertyChangeListener(modelListener);
        }
        for (NestedGridNode child : children) {
            child.dispose();
        }
    }

    // ==================== Utility ====================

    /**
     * Expands all nodes recursively.
     */
    public void expandAll() {
        setExpanded(true);
        for (NestedGridNode child : children) {
            child.expandAll();
        }
    }

    /**
     * Collapses all nodes recursively.
     */
    public void collapseAll() {
        setExpanded(false);
        for (NestedGridNode child : children) {
            child.collapseAll();
        }
    }

    /**
     * Finds a node by model reference.
     */
    public NestedGridNode findByModel(XmlNode model) {
        if (this.modelNode == model) return this;

        for (NestedGridNode child : children) {
            NestedGridNode found = child.findByModel(model);
            if (found != null) return found;
        }
        return null;
    }
}
