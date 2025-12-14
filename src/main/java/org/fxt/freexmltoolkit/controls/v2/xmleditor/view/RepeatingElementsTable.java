package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Represents a table view for repeating XML elements with the same name.
 *
 * <p>When multiple child elements share the same tag name, they are displayed
 * as a table instead of individual nested grids. Columns are derived from:</p>
 * <ul>
 *   <li>Attributes of the elements</li>
 *   <li>First-level child elements (their text content)</li>
 * </ul>
 *
 * <p>Example: Multiple {@code <item>} elements become:</p>
 * <pre>
 * ┌─ items (3x) ─────────────────────────────────────────┐
 * │ @sku     │ @qty  │ name      │ price   │ description │
 * ├──────────┼───────┼───────────┼─────────┼─────────────┤
 * │ ABC-123  │ 5     │ Widget    │ 9.99    │ A widget    │
 * │ DEF-456  │ 2     │ Gadget    │ 19.99   │ A gadget    │
 * │ GHI-789  │ 10    │ Thing     │ 4.99    │ A thing     │
 * └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class RepeatingElementsTable {

    // ==================== Layout Constants ====================

    public static final double HEADER_HEIGHT = 28;
    public static final double ROW_HEIGHT = 24;
    public static final double MIN_COLUMN_WIDTH = 80;
    public static final double MAX_COLUMN_WIDTH = 250;
    public static final double GRID_PADDING = 8;
    public static final double CELL_PADDING = 6;

    // ==================== Data ====================

    private final String elementName;
    private final List<XmlElement> elements;
    private final List<TableColumn> columns = new ArrayList<>();
    private final List<TableRow> rows = new ArrayList<>();
    private final NestedGridNode parentNode;
    private final int depth;

    // ==================== Layout ====================

    private double x;
    private double y;
    private double width;
    private double height;

    // ==================== State ====================

    private boolean expanded = true;
    private boolean selected = false;
    private boolean hovered = false;
    private int hoveredRowIndex = -1;
    private int hoveredColumnIndex = -1;
    private int selectedRowIndex = -1;

    // ==================== Property Change Support ====================

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // ==================== Column Definition ====================

    public static class TableColumn {
        private final String name;
        private final ColumnType type;
        private double width;

        public TableColumn(String name, ColumnType type) {
            this.name = name;
            this.type = type;
            this.width = MIN_COLUMN_WIDTH;
        }

        public String getName() { return name; }
        public ColumnType getType() { return type; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }

        public String getDisplayName() {
            return type == ColumnType.ATTRIBUTE ? "@" + name : name;
        }
    }

    public enum ColumnType {
        ATTRIBUTE,      // From element attribute
        CHILD_ELEMENT,  // From first-level child element text
        TEXT_CONTENT    // Direct text content of element
    }

    // ==================== Row Definition ====================

    public static class TableRow {
        private final XmlElement element;
        private final Map<String, String> values = new LinkedHashMap<>();

        public TableRow(XmlElement element) {
            this.element = element;
        }

        public XmlElement getElement() { return element; }
        public Map<String, String> getValues() { return values; }

        public String getValue(String columnName) {
            return values.getOrDefault(columnName, "");
        }
    }

    // ==================== Constructor ====================

    public RepeatingElementsTable(String elementName, List<XmlElement> elements,
                                   NestedGridNode parentNode, int depth) {
        this.elementName = elementName;
        this.elements = new ArrayList<>(elements);
        this.parentNode = parentNode;
        this.depth = depth;

        analyzeStructure();
        buildRows();
        calculateColumnWidths();
    }

    // ==================== Structure Analysis ====================

    /**
     * Analyzes all elements to determine columns.
     * Columns come from: attributes + first-level child elements
     */
    private void analyzeStructure() {
        Set<String> attributeNames = new LinkedHashSet<>();
        Set<String> childElementNames = new LinkedHashSet<>();
        boolean hasDirectText = false;

        for (XmlElement element : elements) {
            // Collect attributes
            attributeNames.addAll(element.getAttributes().keySet());

            // Collect first-level child element names
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement) {
                    childElementNames.add(((XmlElement) child).getName());
                } else if (child instanceof XmlText) {
                    String text = ((XmlText) child).getText().trim();
                    if (!text.isEmpty()) {
                        hasDirectText = true;
                    }
                }
            }
        }

        // Create columns: attributes first, then child elements, then text
        for (String attrName : attributeNames) {
            columns.add(new TableColumn(attrName, ColumnType.ATTRIBUTE));
        }

        for (String childName : childElementNames) {
            columns.add(new TableColumn(childName, ColumnType.CHILD_ELEMENT));
        }

        if (hasDirectText) {
            columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
        }
    }

    /**
     * Builds table rows from elements.
     */
    private void buildRows() {
        for (XmlElement element : elements) {
            TableRow row = new TableRow(element);

            // Extract attribute values
            for (TableColumn col : columns) {
                if (col.getType() == ColumnType.ATTRIBUTE) {
                    String value = element.getAttributes().get(col.getName());
                    if (value != null) {
                        row.getValues().put(col.getName(), value);
                    }
                }
            }

            // Extract child element text values
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement) {
                    XmlElement childEl = (XmlElement) child;
                    String childName = childEl.getName();

                    // Check if this is a column
                    for (TableColumn col : columns) {
                        if (col.getType() == ColumnType.CHILD_ELEMENT &&
                            col.getName().equals(childName)) {
                            String text = extractElementText(childEl);
                            if (!row.getValues().containsKey(childName)) {
                                row.getValues().put(childName, text);
                            }
                            break;
                        }
                    }
                } else if (child instanceof XmlText) {
                    String text = ((XmlText) child).getText().trim();
                    if (!text.isEmpty()) {
                        row.getValues().put("#text", text);
                    }
                }
            }

            rows.add(row);
        }
    }

    /**
     * Extracts text content from an element (direct text children).
     */
    private String extractElementText(XmlElement element) {
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
        return text.toString();
    }

    /**
     * Calculates optimal column widths based on content.
     */
    private void calculateColumnWidths() {
        for (TableColumn col : columns) {
            double maxWidth = col.getDisplayName().length() * 8 + CELL_PADDING * 2;

            for (TableRow row : rows) {
                String value = row.getValue(col.getName());
                double valueWidth = value.length() * 7 + CELL_PADDING * 2;
                maxWidth = Math.max(maxWidth, valueWidth);
            }

            col.setWidth(Math.min(MAX_COLUMN_WIDTH, Math.max(MIN_COLUMN_WIDTH, maxWidth)));
        }
    }

    // ==================== Layout Calculation ====================

    /**
     * Calculates the height of this table.
     */
    public double calculateHeight() {
        if (!expanded) {
            this.height = HEADER_HEIGHT + GRID_PADDING;
        } else {
            // Header + column header + data rows
            this.height = HEADER_HEIGHT + ROW_HEIGHT + rows.size() * ROW_HEIGHT + GRID_PADDING;
        }
        return this.height;
    }

    /**
     * Calculates the width of this table.
     */
    public double calculateWidth(double availableWidth) {
        double totalColWidth = 0;
        for (TableColumn col : columns) {
            totalColWidth += col.getWidth();
        }

        this.width = Math.max(NestedGridNode.MIN_GRID_WIDTH, totalColWidth + GRID_PADDING * 2);
        return this.width;
    }

    /**
     * Gets the total columns width.
     */
    public double getTotalColumnsWidth() {
        double total = 0;
        for (TableColumn col : columns) {
            total += col.getWidth();
        }
        return total;
    }

    // ==================== Hit Testing ====================

    /**
     * Tests if a point is inside this table's header.
     */
    public boolean isHeaderHit(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + HEADER_HEIGHT;
    }

    /**
     * Tests if a point is inside this table.
     */
    public boolean containsPoint(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + height;
    }

    /**
     * Gets the row index at a given Y coordinate.
     * @return row index (0-based for data rows), or -1 if header/outside
     */
    public int getRowIndexAt(double py) {
        if (!expanded) return -1;

        double dataStartY = y + HEADER_HEIGHT + ROW_HEIGHT; // After main header + column header
        if (py < dataStartY) return -1;

        int rowIndex = (int) ((py - dataStartY) / ROW_HEIGHT);
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rowIndex;
        }
        return -1;
    }

    /**
     * Gets the column index at a given X coordinate.
     */
    public int getColumnIndexAt(double px) {
        double colX = x + GRID_PADDING;
        for (int i = 0; i < columns.size(); i++) {
            double nextX = colX + columns.get(i).getWidth();
            if (px >= colX && px < nextX) {
                return i;
            }
            colX = nextX;
        }
        return -1;
    }

    // ==================== Visibility ====================

    /**
     * Tests if this table is visible in the viewport.
     */
    public boolean isVisible(double viewportTop, double viewportBottom) {
        double nodeTop = y;
        double nodeBottom = y + height;
        return nodeBottom >= viewportTop && nodeTop <= viewportBottom;
    }

    // ==================== Getters and Setters ====================

    public String getElementName() { return elementName; }
    public List<XmlElement> getElements() { return elements; }
    public int getElementCount() { return elements.size(); }
    public List<TableColumn> getColumns() { return columns; }
    public List<TableRow> getRows() { return rows; }
    public NestedGridNode getParentNode() { return parentNode; }
    public int getDepth() { return depth; }

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

    public int getHoveredRowIndex() { return hoveredRowIndex; }
    public void setHoveredRowIndex(int index) { this.hoveredRowIndex = index; }

    public int getHoveredColumnIndex() { return hoveredColumnIndex; }
    public void setHoveredColumnIndex(int index) { this.hoveredColumnIndex = index; }

    public int getSelectedRowIndex() { return selectedRowIndex; }
    public void setSelectedRowIndex(int index) {
        int old = this.selectedRowIndex;
        this.selectedRowIndex = index;
        pcs.firePropertyChange("selectedRowIndex", old, index);
    }

    public XmlElement getSelectedElement() {
        if (selectedRowIndex >= 0 && selectedRowIndex < rows.size()) {
            return rows.get(selectedRowIndex).getElement();
        }
        return null;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // ==================== Static Factory ====================

    /**
     * Groups child elements by name and creates tables for repeating elements.
     *
     * @param children List of child nodes
     * @param parentNode Parent grid node
     * @param depth Current depth
     * @return Map of element name to table (only for elements appearing 2+ times)
     */
    public static Map<String, RepeatingElementsTable> groupRepeatingElements(
            List<XmlNode> children, NestedGridNode parentNode, int depth) {

        // Count elements by name
        Map<String, List<XmlElement>> elementsByName = new LinkedHashMap<>();

        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                XmlElement element = (XmlElement) child;
                String name = element.getName();
                elementsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(element);
            }
        }

        // Create tables for elements appearing 2+ times
        Map<String, RepeatingElementsTable> tables = new LinkedHashMap<>();

        for (Map.Entry<String, List<XmlElement>> entry : elementsByName.entrySet()) {
            if (entry.getValue().size() >= 2) {
                tables.put(entry.getKey(),
                    new RepeatingElementsTable(entry.getKey(), entry.getValue(), parentNode, depth));
            }
        }

        return tables;
    }
}
