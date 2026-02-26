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

    /**
     * Height of the header row displaying the element name in pixels.
     */
    public static final double HEADER_HEIGHT = 28;

    /**
     * Height of each attribute or text content row in pixels.
     */
    public static final double ROW_HEIGHT = 24;

    /**
     * Height of the children section header in pixels (currently 0 to save screen space).
     */
    public static final double CHILDREN_HEADER_HEIGHT = 0;

    /**
     * Horizontal indentation for nested child elements in pixels.
     */
    public static final double INDENT = 20;

    /**
     * Vertical spacing between child elements in pixels.
     */
    public static final double CHILD_SPACING = 8;

    /**
     * Reduced vertical spacing for compact leaf nodes (elements with only text) in pixels.
     */
    public static final double COMPACT_CHILD_SPACING = 4;

    /**
     * Padding inside the grid in pixels.
     */
    public static final double GRID_PADDING = 8;

    /**
     * Minimum width of a grid node in pixels.
     */
    public static final double MIN_GRID_WIDTH = 200;

    /**
     * Default width for attribute name column in pixels.
     */
    public static final double ATTR_NAME_WIDTH = 120;

    /**
     * Maximum depth for nested elements to prevent infinite recursion.
     */
    public static final int MAX_DEPTH = 30;

    /**
     * Maximum number of children to load initially for performance optimization.
     */
    public static final int MAX_INITIAL_CHILDREN = 50;

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
    private double calculatedNameColumnWidth;  // Dynamic width for attribute/child names
    private double calculatedValueColumnWidth; // Dynamic width for attribute/child values

    // ==================== State ====================

    private boolean expanded = false;  // Default collapsed - only root expands explicitly
    private boolean selected = false;
    private boolean hovered = false;
    private boolean headerHovered = false;
    private boolean skipOwnHeader = false;  // If true, don't render own header (for inline children display)
    private boolean hasMoreChildren = false; // Indicates if there are more children in the model not yet loaded
    private int nextChildIndexToLoad = 0; // The index of the next child to load from the model's children list

    // ==================== Property Change Support ====================

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private PropertyChangeListener modelListener;
    private final Runnable onLayoutChangedCallback;

    // ==================== Node Type Enum ====================

    /**
     * Represents the type of XML node for rendering purposes.
     *
     * <p>Each type determines how the node is visually displayed
     * and what properties are available.</p>
     */
    public enum NodeType {
        /**
         * Standard XML element with optional attributes and children.
         */
        ELEMENT,

        /**
         * Text content node containing character data.
         */
        TEXT,

        /**
         * XML comment node.
         */
        COMMENT,

        /**
         * CDATA section containing unparsed character data.
         */
        CDATA,

        /**
         * Processing instruction node with target and data.
         */
        PROCESSING_INSTRUCTION,

        /**
         * Document root node containing the entire XML structure.
         */
        DOCUMENT
    }

    // ==================== Attribute Cell ====================

    /**
     * Represents an attribute cell containing a name-value pair.
     *
     * <p>Used to store and display XML element attributes in the grid layout.</p>
     */
    public static class AttributeCell {
        private final String name;
        private String value;

        /**
         * Creates a new attribute cell with the specified name and value.
         *
         * @param name the attribute name (immutable after creation)
         * @param value the attribute value (can be modified)
         */
        public AttributeCell(String name, String value) {
            this.name = name;
            this.value = value;
        }

        /**
         * Returns the attribute name.
         *
         * @return the attribute name
         */
        public String getName() { return name; }

        /**
         * Returns the attribute value.
         *
         * @return the attribute value
         */
        public String getValue() { return value; }

        /**
         * Sets the attribute value.
         *
         * @param value the new attribute value
         */
        public void setValue(String value) { this.value = value; }
    }

    // ==================== Constructor ====================

    /**
     * Creates a new NestedGridNode representing an XML node in the visual tree.
     *
     * @param modelNode the underlying XML node this visual node represents
     * @param parent the parent NestedGridNode, or null if this is the root node
     * @param depth the nesting depth level (0 for root, increments for each level)
     * @param onLayoutChangedCallback callback to invoke when layout changes occur, may be null
     */
    public NestedGridNode(XmlNode modelNode, NestedGridNode parent, int depth, Runnable onLayoutChangedCallback) {
        this.modelNode = modelNode;
        this.parent = parent;
        this.depth = depth;
        this.onLayoutChangedCallback = onLayoutChangedCallback;

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
     * The tree starts with the root element, not with the Document node.
     * Only the root node is expanded; all children are collapsed by default.
     *
     * @param document the XML document to build the tree from
     * @param onLayoutChangedCallback callback to run when layout changes
     * @return the root NestedGridNode of the built tree
     */
    public static NestedGridNode buildTree(XmlDocument document, Runnable onLayoutChangedCallback) {
        // Find the root element (skip processing instructions, comments, etc.)
        XmlElement rootElement = null;
        for (XmlNode child : document.getChildren()) {
            if (child instanceof XmlElement) {
                rootElement = (XmlElement) child;
                break;
            }
        }

        // If no root element found, fall back to document node
        if (rootElement == null) {
            NestedGridNode root = new NestedGridNode(document, null, 0, onLayoutChangedCallback);
            buildChildren(root, document.getChildren(), 1, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);
            root.expanded = true;  // Only root is expanded
            return root;
        }

        // Build tree starting from root element
        NestedGridNode root = new NestedGridNode(rootElement, null, 0, onLayoutChangedCallback);
        buildChildren(root, rootElement.getChildren(), 1, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);

        // Only root is expanded - all children start collapsed (default)
        root.expanded = true;

        return root;
    }

    /**
     * Builds a NestedGridNode from an XmlElement with all its children.
     * Used for creating child grids in expanded table cells.
     *
     * @param element the XML element to build from
     * @param depth the depth level for rendering
     * @param onLayoutChangedCallback callback to run when layout changes
     * @return a new NestedGridNode representing the element
     */
    public static NestedGridNode buildFromElement(XmlElement element, int depth, Runnable onLayoutChangedCallback) {
        NestedGridNode node = new NestedGridNode(element, null, depth, onLayoutChangedCallback);
        buildChildren(node, element.getChildren(), depth + 1, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);
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
     * @param onLayoutChangedCallback callback to run when layout changes
     * @return A container node with only the children (no header for the element itself)
     */
    public static NestedGridNode buildChildrenOnly(XmlElement element, int depth, Runnable onLayoutChangedCallback) {
        // Create a "virtual" container node that just holds children
        NestedGridNode container = new NestedGridNode(element, null, depth, onLayoutChangedCallback);
        container.setSkipOwnHeader(true);  // Flag to skip rendering own header
        buildChildren(container, element.getChildren(), depth, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);
        container.setExpanded(true);
        return container;
    }

    /**
     * Helper method to build children with default load parameters.
     *
     * @param parent the parent node to add children to
     * @param modelChildren the list of XML nodes to process
     * @param depth the current depth level
     * @param onLayoutChangedCallback callback to run when layout changes
     */
    private static void buildChildren(NestedGridNode parent, List<XmlNode> modelChildren, int depth, Runnable onLayoutChangedCallback) {
        buildChildren(parent, modelChildren, depth, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);
    }

    /**
     * Builds child nodes from a list of XML nodes with pagination support.
     *
     * <p>This method handles repeating element detection, text node filtering,
     * and lazy loading of children for performance.</p>
     *
     * @param parent the parent node to add children to
     * @param modelChildren the list of XML nodes to process
     * @param depth the current depth level
     * @param loadOffset the starting index for loading (for pagination)
     * @param loadLimit the maximum number of children to load in this batch
     * @param onLayoutChangedCallback callback to run when layout changes
     */
    private static void buildChildren(NestedGridNode parent, List<XmlNode> modelChildren, int depth, int loadOffset, int loadLimit, Runnable onLayoutChangedCallback) {
        if (depth > MAX_DEPTH) return;

        // If this is the initial load, clear existing children and repeating tables.
        // For subsequent loads (loadMoreChildren), children are appended.
        if (loadOffset == 0) {
            parent.children.clear();
            parent.repeatingTables.clear(); // Clear tables too
            parent.hasMoreChildren = false;
            parent.nextChildIndexToLoad = 0;
        }

        // If parent is a leaf element with only text content, don't add text children
        // The text is already displayed in the parent's header
        if (parent.isLeafWithText()) {
            return; // No children to add - text is shown inline
        }

        // Only create tables on initial load, or if it's a new batch that includes the first repeating elements
        if (loadOffset == 0) {
            parent.repeatingTables.addAll(
                RepeatingElementsTable.groupRepeatingElements(modelChildren, parent, depth, onLayoutChangedCallback).values()
            );
        }

        // Collect names of elements that are part of repeating tables
        Set<String> groupedElementNames = parent.repeatingTables.stream()
            .map(RepeatingElementsTable::getElementName)
            .collect(Collectors.toSet());

        // Check if any element children exist in the full list
        boolean hasElementChildrenInModel = modelChildren.stream().anyMatch(c -> c instanceof XmlElement);

        int childrenAddedInThisCall = 0;
        int currentModelIndex = loadOffset;

        for (int i = loadOffset; i < modelChildren.size(); i++) {
            if (childrenAddedInThisCall >= loadLimit) {
                parent.hasMoreChildren = true;
                parent.nextChildIndexToLoad = i;
                break;
            }

            XmlNode child = modelChildren.get(i);

            // Skip whitespace-only text nodes
            if (child instanceof XmlText) {
                String text = ((XmlText) child).getText().trim();
                if (text.isEmpty()) continue;
                // Skip text nodes if parent has element children (mixed content)
                // Text will be shown as #text row in parent, not as separate child
                if (hasElementChildrenInModel) continue;
            }

            // Skip elements that are in a repeating table (already handled by RepeatingElementsTable)
            if (child instanceof XmlElement element) {
                if (groupedElementNames.contains(element.getName())) {
                    continue; // Already in a table
                }
            }

            // Create and add NestedGridNode
            NestedGridNode node = new NestedGridNode(child, parent, depth, onLayoutChangedCallback);
            parent.children.add(node);
            childrenAddedInThisCall++;

            // Recursively build children for this newly added node (always initial load for its children)
            if (child instanceof XmlElement element) {
                buildChildren(node, element.getChildren(), depth + 1, 0, MAX_INITIAL_CHILDREN, onLayoutChangedCallback);
            }

            currentModelIndex = i + 1;
        }

        if (currentModelIndex < modelChildren.size()) {
            parent.hasMoreChildren = true;
            parent.nextChildIndexToLoad = currentModelIndex;
        } else {
            parent.hasMoreChildren = false;
            parent.nextChildIndexToLoad = modelChildren.size();
        }
    }

    // ==================== Layout Calculation ====================

    /**
     * Calculates the height of this grid including children and tables.
     * Must be called bottom-up (children first).
     *
     * @return the calculated height in pixels
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
     * Calculates the width of this grid based on content.
     * Width is not constrained by viewport to allow horizontal scrolling.
     *
     * @param minWidth minimum width hint (not a hard constraint)
     * @return the calculated width in pixels
     */
    public double calculateWidth(double minWidth) {
        // First calculate column widths based on content
        calculateColumnWidths();

        double contentWidth;

        // For leaf nodes with text (displayed as "elementName = text"), calculate inline width
        if (isLeafWithText && attributeCells.isEmpty()) {
            // Format: [icon] elementName = "textContent"
            double iconWidth = 20;  // Expand icon space
            double nameWidth = estimateTextWidth(elementName);
            double equalsWidth = 20;  // " = "
            double textWidth = estimateTextWidth(textContent) + 14;  // +14 for quotes
            contentWidth = iconWidth + nameWidth + equalsWidth + textWidth + GRID_PADDING * 2;
        } else {
            // Standard width calculation for nodes with attributes or children
            contentWidth = calculatedNameColumnWidth + calculatedValueColumnWidth + GRID_PADDING * 3;
        }

        double w = Math.max(MIN_GRID_WIDTH, contentWidth);

        // Check children width - they may be wider
        if (expanded) {
            // Check repeating tables width
            for (RepeatingElementsTable table : repeatingTables) {
                double tableWidth = table.calculateWidth(w) + INDENT;
                w = Math.max(w, tableWidth);
            }

            // Check individual children width
            for (NestedGridNode child : children) {
                double childWidth = child.calculateWidth(w) + INDENT;
                w = Math.max(w, childWidth);
            }
        }

        this.width = w;
        return this.width;
    }

    /**
     * Calculates optimal column widths for attribute names and values.
     * This ensures all attribute names align at the longest name, and all values align properly.
     */
    private void calculateColumnWidths() {
        double maxNameWidth = 0;
        double maxValueWidth = 0;

        // Check all attribute names and values
        for (AttributeCell cell : attributeCells) {
            // Attribute name with "@" prefix
            String attrName = "@" + cell.getName();
            double nameWidth = estimateTextWidth(attrName);
            maxNameWidth = Math.max(maxNameWidth, nameWidth);

            // Attribute value
            double valueWidth = estimateTextWidth(cell.getValue());
            maxValueWidth = Math.max(maxValueWidth, valueWidth);
        }

        // Check text content row if present
        if (textContent != null && !textContent.isEmpty()) {
            double labelWidth = estimateTextWidth("#text");
            maxNameWidth = Math.max(maxNameWidth, labelWidth);

            double textWidth = estimateTextWidth(textContent);
            maxValueWidth = Math.max(maxValueWidth, textWidth);
        }

        // Add padding to calculated widths
        calculatedNameColumnWidth = maxNameWidth + GRID_PADDING * 2;
        calculatedValueColumnWidth = maxValueWidth + GRID_PADDING * 2;

        // Ensure minimum widths
        calculatedNameColumnWidth = Math.max(80, calculatedNameColumnWidth);
        calculatedValueColumnWidth = Math.max(100, calculatedValueColumnWidth);
    }

    /**
     * Estimates the width of text in pixels.
     * Uses 7px per character as approximation (matches font size ~12px).
     */
    private double estimateTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() * 7.0;
    }

    /**
     * Returns the Y position where the content ends (before children).
     *
     * @return the Y coordinate where content ends
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
     *
     * @return the Y coordinate where children start
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
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is inside the header area
     */
    public boolean isHeaderHit(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + HEADER_HEIGHT;
    }

    /**
     * Tests if a point is inside this grid.
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is inside this grid's bounds
     */
    public boolean containsPoint(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + height;
    }

    /**
     * Finds the attribute cell at the given point.
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
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
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is on the text content row
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
     *
     * @param viewportTop the top Y coordinate of the viewport
     * @param viewportBottom the bottom Y coordinate of the viewport
     * @return true if any part of this grid is visible in the viewport
     */
    public boolean isVisible(double viewportTop, double viewportBottom) {
        double nodeTop = y;
        double nodeBottom = y + height;
        return nodeBottom >= viewportTop && nodeTop <= viewportBottom;
    }

    // ==================== Getters and Setters ====================

    /**
     * Returns the underlying XML node this visual node represents.
     *
     * @return the model XML node
     */
    public XmlNode getModelNode() { return modelNode; }

    /**
     * Returns the parent NestedGridNode in the visual hierarchy.
     *
     * @return the parent node, or null if this is the root
     */
    public NestedGridNode getParent() { return parent; }

    /**
     * Returns the list of child NestedGridNodes.
     *
     * @return the list of children (may be empty but never null)
     */
    public List<NestedGridNode> getChildren() { return children; }

    /**
     * Returns the list of repeating element tables for this node.
     *
     * @return the list of repeating tables (may be empty but never null)
     */
    public List<RepeatingElementsTable> getRepeatingTables() { return repeatingTables; }

    /**
     * Checks if this node has any children (individual children, repeating tables, or more to load).
     *
     * @return true if the node has any children or children pending to load
     */
    public boolean hasChildren() { return !children.isEmpty() || !repeatingTables.isEmpty() || hasMoreChildren; }

    /**
     * Checks if this node has individual (non-table) children.
     *
     * @return true if the node has individual children
     */
    public boolean hasIndividualChildren() { return !children.isEmpty(); }

    /**
     * Checks if this node has any repeating element tables.
     *
     * @return true if the node has repeating element tables
     */
    public boolean hasRepeatingTables() { return !repeatingTables.isEmpty(); }

    /**
     * Returns the depth level of this node in the visual hierarchy.
     *
     * @return the depth (0 for root, increments for each nested level)
     */
    public int getDepth() { return depth; }

    /**
     * Returns the element name for display purposes.
     *
     * @return the element name or special name for non-element nodes
     */
    public String getElementName() { return elementName; }

    /**
     * Returns the type of this XML node.
     *
     * @return the node type (ELEMENT, TEXT, COMMENT, etc.)
     */
    public NodeType getNodeType() { return nodeType; }

    /**
     * Returns the list of attribute cells for this element.
     *
     * @return the list of attribute cells (may be empty but never null)
     */
    public List<AttributeCell> getAttributeCells() { return attributeCells; }

    /**
     * Returns the text content of this node.
     *
     * @return the text content, or null if none
     */
    public String getTextContent() { return textContent; }

    /**
     * Checks if this node has non-empty text content.
     *
     * @return true if text content exists and is not empty
     */
    public boolean hasTextContent() { return textContent != null && !textContent.isEmpty(); }

    /**
     * Checks if this is a leaf element with only text content (no child elements).
     *
     * @return true if this element has only text content
     */
    public boolean isLeafWithText() { return isLeafWithText; }

    /**
     * Returns the X coordinate of this node's position.
     *
     * @return the X coordinate in pixels
     */
    public double getX() { return x; }

    /**
     * Sets the X coordinate of this node's position.
     *
     * @param x the new X coordinate in pixels
     */
    public void setX(double x) { this.x = x; }

    /**
     * Returns the Y coordinate of this node's position.
     *
     * @return the Y coordinate in pixels
     */
    public double getY() { return y; }

    /**
     * Sets the Y coordinate of this node's position.
     *
     * @param y the new Y coordinate in pixels
     */
    public void setY(double y) { this.y = y; }

    /**
     * Returns the calculated width of this node.
     *
     * @return the width in pixels
     */
    public double getWidth() { return width; }

    /**
     * Sets the width of this node.
     *
     * @param width the new width in pixels
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Returns the calculated height of this node.
     *
     * @return the height in pixels
     */
    public double getHeight() { return height; }

    /**
     * Sets the height of this node.
     *
     * @param height the new height in pixels
     */
    public void setHeight(double height) { this.height = height; }

    /**
     * Returns the calculated width for the attribute name column.
     *
     * @return the name column width in pixels
     */
    public double getCalculatedNameColumnWidth() { return calculatedNameColumnWidth; }

    /**
     * Returns the calculated width for the attribute value column.
     *
     * @return the value column width in pixels
     */
    public double getCalculatedValueColumnWidth() { return calculatedValueColumnWidth; }

    /**
     * Checks if this node is expanded to show its children.
     *
     * @return true if the node is expanded
     */
    public boolean isExpanded() { return expanded; }

    /**
     * Sets the expanded state of this node.
     *
     * @param expanded true to expand and show children, false to collapse
     */
    public void setExpanded(boolean expanded) {
        boolean old = this.expanded;
        this.expanded = expanded;
        pcs.firePropertyChange("expanded", old, expanded);
    }

    /**
     * Checks if this node has content that can be expanded or collapsed.
     * A node is expandable if it has children or repeating element tables.
     *
     * @return true if the node has expandable content
     */
    public boolean hasExpandableContent() {
        return !children.isEmpty() || !repeatingTables.isEmpty();
    }

    /**
     * Toggles the expanded state of this node.
     */
    public void toggleExpanded() { setExpanded(!expanded); }

    /**
     * Checks if this node is currently selected.
     *
     * @return true if the node is selected
     */
    public boolean isSelected() { return selected; }

    /**
     * Sets the selection state of this node.
     *
     * @param selected true to select, false to deselect
     */
    public void setSelected(boolean selected) {
        boolean old = this.selected;
        this.selected = selected;
        pcs.firePropertyChange("selected", old, selected);
    }

    /**
     * Checks if this node is currently hovered by the mouse.
     *
     * @return true if the node is hovered
     */
    public boolean isHovered() { return hovered; }

    /**
     * Sets the hover state of this node.
     *
     * @param hovered true if the mouse is over this node
     */
    public void setHovered(boolean hovered) {
        boolean old = this.hovered;
        this.hovered = hovered;
        pcs.firePropertyChange("hovered", old, hovered);
    }

    /**
     * Checks if the header of this node is currently hovered.
     *
     * @return true if the header is hovered
     */
    public boolean isHeaderHovered() { return headerHovered; }

    /**
     * Sets the header hover state of this node.
     *
     * @param headerHovered true if the mouse is over the header
     */
    public void setHeaderHovered(boolean headerHovered) {
        this.headerHovered = headerHovered;
    }

    /**
     * Checks if this node should skip rendering its own header.
     *
     * @return true if the header should be skipped
     */
    public boolean isSkipOwnHeader() { return skipOwnHeader; }

    /**
     * Sets whether this node should skip rendering its own header.
     *
     * @param skipOwnHeader true to skip header rendering
     */
    public void setSkipOwnHeader(boolean skipOwnHeader) {
        this.skipOwnHeader = skipOwnHeader;
    }

    /**
     * Checks if there are more children in the model that have not been loaded yet.
     *
     * @return true if more children are available to load
     */
    public boolean hasMoreChildren() { return hasMoreChildren; }

    /**
     * Sets whether this node has more children to load.
     *
     * @param hasMoreChildren true if more children are available
     */
    public void setHasMoreChildren(boolean hasMoreChildren) {
        this.hasMoreChildren = hasMoreChildren;
    }

    /**
     * Returns the index of the next child to load from the model.
     *
     * @return the index of the next child to load
     */
    public int getNextChildIndexToLoad() { return nextChildIndexToLoad; }

    /**
     * Sets the index of the next child to load from the model.
     *
     * @param nextChildIndexToLoad the index of the next child to load
     */
    public void setNextChildIndexToLoad(int nextChildIndexToLoad) {
        this.nextChildIndexToLoad = nextChildIndexToLoad;
    }

    /**
     * Adds a property change listener to this node.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener from this node.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Loads additional children into this node.
     * Starts from `nextChildIndexToLoad` and adds up to `MAX_INITIAL_CHILDREN` more.
     */
    public void loadMoreChildren() {
        if (!hasMoreChildren) {
            return;
        }

        // Get the model children from the modelNode
        List<XmlNode> modelChildren = Collections.emptyList();
        if (modelNode instanceof XmlElement element) {
            modelChildren = element.getChildren();
        } else if (modelNode instanceof XmlDocument document) {
            modelChildren = document.getChildren();
        }

        int currentChildrenCount = children.size();
        int childrenToAdd = Math.min(MAX_INITIAL_CHILDREN, modelChildren.size() - nextChildIndexToLoad);

        // Build more children
        buildChildren(this, modelChildren, this.depth + 1, nextChildIndexToLoad, childrenToAdd, onLayoutChangedCallback);

        // Update state
        nextChildIndexToLoad += childrenToAdd;
        if (nextChildIndexToLoad >= modelChildren.size()) {
            hasMoreChildren = false;
        }

        // Notify UI to re-layout and re-render
        if (onLayoutChangedCallback != null) {
            onLayoutChangedCallback.run();
        }

        pcs.firePropertyChange("childrenLoaded", currentChildrenCount, children.size());
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
     * Finds a NestedGridNode in this tree that corresponds to the given model node.
     *
     * @param model the XML node to search for
     * @return the matching NestedGridNode, or null if not found
     */
    public NestedGridNode findByModel(XmlNode model) {
        if (this.modelNode == model) return this;

        for (NestedGridNode child : children) {
            NestedGridNode found = child.findByModel(model);
            if (found != null) return found;
        }
        return null;
    }

    // ==================== Expand State Persistence ====================

    public static class TreeState {
        public final Map<XmlNode, Boolean> nodeExpanded = new IdentityHashMap<>();
        public final Map<XmlNode, Map<String, Boolean>> tableExpanded = new IdentityHashMap<>();
        public final Map<XmlNode, Set<String>> cellExpanded = new IdentityHashMap<>();
    }

    /**
     * Collects the expand state of all nodes and tables in this tree.
     * Uses the XmlNode reference as key since the model objects persist across rebuilds.
     *
     * @return TreeState object with all expanded states
     */
    public TreeState collectExpandState() {
        TreeState state = new TreeState();
        collectExpandStateRecursive(state);
        return state;
    }

    private void collectExpandStateRecursive(TreeState state) {
        state.nodeExpanded.put(modelNode, expanded);

        for (NestedGridNode child : children) {
            child.collectExpandStateRecursive(state);
        }

        // Also collect state from repeating tables
        for (RepeatingElementsTable table : repeatingTables) {
            // Store table expanded state using its parent node and element name
            state.tableExpanded.computeIfAbsent(modelNode, k -> new HashMap<>())
                 .put(table.getElementName(), table.isExpanded());

            // Collect state from nested grids inside table cells
            for (RepeatingElementsTable.TableRow row : table.getRows()) {
                if (!row.getExpandedColumns().isEmpty()) {
                    state.cellExpanded.put(row.getElement(), new HashSet<>(row.getExpandedColumns()));
                }
                for (NestedGridNode childGrid : row.getExpandedChildGrids().values()) {
                    childGrid.collectExpandStateRecursive(state);
                }
            }
        }
    }

    /**
     * Restores the expand state of all nodes and tables in this tree.
     *
     * @param state TreeState collected previously
     */
    public void restoreExpandState(TreeState state) {
        if (state == null) {
            return;
        }
        restoreExpandStateRecursive(state);
    }

    private void restoreExpandStateRecursive(TreeState state) {
        Boolean wasExpanded = state.nodeExpanded.get(modelNode);
        if (wasExpanded != null) {
            this.expanded = wasExpanded;
        }

        for (NestedGridNode child : children) {
            child.restoreExpandStateRecursive(state);
        }

        for (RepeatingElementsTable table : repeatingTables) {
            Map<String, Boolean> tablesForNode = state.tableExpanded.get(modelNode);
            if (tablesForNode != null) {
                Boolean tableExpanded = tablesForNode.get(table.getElementName());
                if (tableExpanded != null) {
                    table.setExpanded(tableExpanded);
                }
            }

            boolean needsRecalculation = false;
            for (RepeatingElementsTable.TableRow row : table.getRows()) {
                Set<String> expandedCols = state.cellExpanded.get(row.getElement());
                if (expandedCols != null) {
                    for (String col : expandedCols) {
                        if (!row.isColumnExpanded(col)) {
                            row.toggleColumnExpanded(col);
                            NestedGridNode childGrid = row.getOrCreateChildGrid(col, this.depth, onLayoutChangedCallback);
                            childGrid.setExpanded(true);
                            childGrid.restoreExpandStateRecursive(state);
                            needsRecalculation = true;
                        }
                    }
                }
            }
            if (needsRecalculation) {
                table.recalculateColumnWidthsWithExpandedCells();
            }
        }
    }
}
