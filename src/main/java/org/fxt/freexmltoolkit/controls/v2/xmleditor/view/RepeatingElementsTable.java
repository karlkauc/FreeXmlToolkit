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

    // ==================== Column Order Cache ====================
    // Caches the original column order per element name to maintain stability after sorting
    private static final Map<String, List<String>> columnOrderCache = new HashMap<>();

    /**
     * Clears the column order cache. Call this when loading a new document.
     */
    public static void clearColumnOrderCache() {
        columnOrderCache.clear();
    }

    // ==================== Sort State Cache ====================
    // Caches the sort state per element name to maintain indicator after rebuild
    private static final Map<String, SortState> sortStateCache = new HashMap<>();

    /**
     * Stores sort state (column and direction) for persistence across rebuilds.
     */
    private static class SortState {
        final String columnName;
        final boolean ascending;

        SortState(String columnName, boolean ascending) {
            this.columnName = columnName;
            this.ascending = ascending;
        }
    }

    /**
     * Clears the sort state cache. Call this when loading a new document.
     */
    public static void clearSortStateCache() {
        sortStateCache.clear();
    }

    /**
     * Clears all caches. Call this when loading a new document.
     */
    public static void clearAllCaches() {
        columnOrderCache.clear();
        sortStateCache.clear();
    }

    // ==================== Data ====================

    private final String elementName;
    private final List<XmlElement> elements;
    private final List<TableColumn> columns = new ArrayList<>();
    private final List<TableRow> rows = new ArrayList<>();
    private final NestedGridNode parentNode;
    private final int depth;
    private final Runnable onLayoutChangedCallback;

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

    // ==================== Sort State ====================

    private String sortedColumnName = null;  // Column currently sorted by (null = none)
    private boolean sortAscending = true;    // Sort direction (true = ascending, false = descending)

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
        private final Map<String, XmlElement> complexChildren = new LinkedHashMap<>();
        private final Map<String, NestedGridNode> expandedChildGrids = new LinkedHashMap<>();
        private final Set<String> expandedColumns = new HashSet<>();
        private boolean expanded = false;  // Is this row expanded to show details?

        public TableRow(XmlElement element) {
            this.element = element;
        }

        public XmlElement getElement() { return element; }
        public Map<String, String> getValues() { return values; }
        public Map<String, XmlElement> getComplexChildren() { return complexChildren; }
        public Map<String, NestedGridNode> getExpandedChildGrids() { return expandedChildGrids; }

        public String getValue(String columnName) {
            return values.getOrDefault(columnName, "");
        }

        public boolean hasComplexChild(String columnName) {
            return complexChildren.containsKey(columnName);
        }

        public XmlElement getComplexChild(String columnName) {
            return complexChildren.get(columnName);
        }

        public boolean isColumnExpanded(String columnName) {
            return expandedColumns.contains(columnName);
        }

        public void toggleColumnExpanded(String columnName) {
            if (expandedColumns.contains(columnName)) {
                expandedColumns.remove(columnName);
                expandedChildGrids.remove(columnName);
            } else {
                expandedColumns.add(columnName);
            }
        }

        public Set<String> getExpandedColumns() { return expandedColumns; }

        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean expanded) { this.expanded = expanded; }

        public NestedGridNode getOrCreateChildGrid(String columnName, int depth, Runnable onLayoutChangedCallback) {
            if (!expandedChildGrids.containsKey(columnName)) {
                XmlElement childElement = complexChildren.get(columnName);
                if (childElement != null) {
                    // Use buildChildrenOnly to show only the children (not the element itself,
                    // since the column header already shows the element name)
                    NestedGridNode childGrid = NestedGridNode.buildChildrenOnly(childElement, depth + 1, onLayoutChangedCallback);
                    expandedChildGrids.put(columnName, childGrid);
                }
            }
            return expandedChildGrids.get(columnName);
        }
    }

    // ==================== Constructor ====================

    public RepeatingElementsTable(String elementName, List<XmlElement> elements,
                                   NestedGridNode parentNode, int depth, Runnable onLayoutChangedCallback) {
        this.elementName = elementName;
        this.elements = new ArrayList<>(elements);
        this.parentNode = parentNode;
        this.depth = depth;
        this.onLayoutChangedCallback = onLayoutChangedCallback;

        analyzeStructure();
        buildRows();
        calculateColumnWidths();

        // Restore sort state from cache if previously sorted
        restoreSortStateFromCache();
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

        // Check if we have a cached column order for this element type
        List<String> cachedOrder = columnOrderCache.get(elementName);

        if (cachedOrder != null) {
            // Use cached order - add columns in the original order
            for (String colName : cachedOrder) {
                if (colName.startsWith("@") && attributeNames.contains(colName.substring(1))) {
                    columns.add(new TableColumn(colName.substring(1), ColumnType.ATTRIBUTE));
                } else if (colName.equals("#text") && hasDirectText) {
                    columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
                } else if (childElementNames.contains(colName)) {
                    columns.add(new TableColumn(colName, ColumnType.CHILD_ELEMENT));
                }
            }

            // Add any new columns that weren't in the cache (at the end)
            for (String attrName : attributeNames) {
                if (!cachedOrder.contains("@" + attrName)) {
                    columns.add(new TableColumn(attrName, ColumnType.ATTRIBUTE));
                }
            }
            for (String childName : childElementNames) {
                if (!cachedOrder.contains(childName)) {
                    columns.add(new TableColumn(childName, ColumnType.CHILD_ELEMENT));
                }
            }
            if (hasDirectText && !cachedOrder.contains("#text")) {
                columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
            }
        } else {
            // First time - create columns in discovery order and cache the order
            List<String> columnOrder = new ArrayList<>();

            for (String attrName : attributeNames) {
                columns.add(new TableColumn(attrName, ColumnType.ATTRIBUTE));
                columnOrder.add("@" + attrName);
            }

            for (String childName : childElementNames) {
                columns.add(new TableColumn(childName, ColumnType.CHILD_ELEMENT));
                columnOrder.add(childName);
            }

            if (hasDirectText) {
                columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
                columnOrder.add("#text");
            }

            // Cache the column order for future rebuilds
            columnOrderCache.put(elementName, columnOrder);
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
                                // Store complex children for later expansion
                                if (hasElementChildren(childEl)) {
                                    row.getComplexChildren().put(childName, childEl);
                                }
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
     * Checks if an element has child elements (not just text).
     */
    private boolean hasElementChildren(XmlElement element) {
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts text content from an element.
     * For leaf elements (only text), returns the text.
     * For complex elements (with child elements), returns a summary showing child element names.
     */
    private String extractElementText(XmlElement element) {
        StringBuilder text = new StringBuilder();
        List<String> childElementNames = new ArrayList<>();

        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement childElement) {
                childElementNames.add(childElement.getName());
            } else if (child instanceof XmlText) {
                String t = ((XmlText) child).getText().trim();
                if (!t.isEmpty()) {
                    if (text.length() > 0) text.append(" ");
                    text.append(t);
                }
            }
        }

        // If it has element children, show element names
        if (!childElementNames.isEmpty()) {
            // Build summary showing element names (max 3, then "...")
            StringBuilder summary = new StringBuilder();
            int shown = 0;
            for (String name : childElementNames) {
                if (shown > 0) summary.append(", ");
                if (shown >= 3) {
                    summary.append("...");
                    break;
                }
                summary.append("<").append(name).append(">");
                shown++;
            }

            if (text.length() > 0) {
                // Mixed content: show text + child summary
                return text.toString() + " [" + summary + "]";
            } else {
                // Only element children - show as expandable
                return summary.toString();
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

    /**
     * Recalculates column widths considering expanded child grids inside cells.
     * This should be called after a cell is expanded to ensure the column is wide enough
     * to accommodate the nested content.
     */
    public void recalculateColumnWidthsWithExpandedCells() {
        for (TableColumn col : columns) {
            String colName = col.getName();
            double maxWidth = col.getDisplayName().length() * 8 + CELL_PADDING * 2;

            // Check text content width
            for (TableRow row : rows) {
                String value = row.getValue(colName);
                double valueWidth = value.length() * 7 + CELL_PADDING * 2;
                maxWidth = Math.max(maxWidth, valueWidth);

                // Check expanded child grid width
                if (row.isColumnExpanded(colName)) {
                    NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
                    if (childGrid != null) {
                        // Calculate the width the child grid needs
                        // Give it a generous initial width to calculate its natural size
                        double childNaturalWidth = calculateChildGridNaturalWidth(childGrid);
                        // Add padding for the cell
                        double neededWidth = childNaturalWidth + 8;
                        maxWidth = Math.max(maxWidth, neededWidth);
                    }
                }
            }

            // Apply constraints, but allow wider columns for expanded content
            // Use a larger max width for columns with expanded cells
            double maxAllowedWidth = hasExpandedCells(colName) ? 500 : MAX_COLUMN_WIDTH;
            col.setWidth(Math.min(maxAllowedWidth, Math.max(MIN_COLUMN_WIDTH, maxWidth)));
        }
    }

    /**
     * Calculates the natural width a child grid needs to display properly.
     * This recursively ensures all nested tables also recalculate their column widths.
     */
    private double calculateChildGridNaturalWidth(NestedGridNode childGrid) {
        // First, recursively recalculate column widths for any tables in the child grid
        recalculateNestedTableWidths(childGrid);

        // Give it a large available width to calculate its natural size
        double tempWidth = 1000;
        childGrid.calculateWidth(tempWidth);
        return childGrid.getWidth();
    }

    /**
     * Recursively recalculates column widths for all tables in a nested grid.
     */
    private void recalculateNestedTableWidths(NestedGridNode node) {
        if (node == null) return;

        // Recalculate widths for all child tables
        for (RepeatingElementsTable table : node.getRepeatingTables()) {
            table.recalculateColumnWidthsWithExpandedCells();
        }

        // Recursively process child nodes
        for (NestedGridNode child : node.getChildren()) {
            recalculateNestedTableWidths(child);
        }
    }

    /**
     * Checks if a column has any expanded cells.
     */
    private boolean hasExpandedCells(String columnName) {
        for (TableRow row : rows) {
            if (row.isColumnExpanded(columnName)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Layout Calculation ====================

    /**
     * Calculates the height of this table, including any expanded child grids inside cells.
     */
    public double calculateHeight() {
        if (!expanded) {
            this.height = HEADER_HEIGHT + GRID_PADDING;
        } else {
            // Header + column header
            double h = HEADER_HEIGHT + ROW_HEIGHT;

            // Add height for each row (variable height due to expanded cells)
            for (TableRow row : rows) {
                double rowHeight = calculateRowHeight(row);
                h += rowHeight;
            }

            this.height = h + GRID_PADDING;
        }
        return this.height;
    }

    /**
     * Calculate the height of a single row, accounting for expanded child grids in cells.
     * The row height is the maximum of all cell heights.
     */
    private double calculateRowHeight(TableRow row) {
        double maxCellHeight = ROW_HEIGHT;

        for (String colName : row.getExpandedColumns()) {
            NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
            if (childGrid != null) {
                // Cell height = text row + child grid + small padding
                double cellHeight = ROW_HEIGHT + childGrid.getHeight() + 4;
                maxCellHeight = Math.max(maxCellHeight, cellHeight);
            }
        }

        return maxCellHeight;
    }

    /**
     * Gets the Y position of a specific row within this table.
     */
    public double getRowY(int rowIndex) {
        double rowY = y + HEADER_HEIGHT + ROW_HEIGHT;  // After table header + column headers

        for (int i = 0; i < rowIndex && i < rows.size(); i++) {
            // Each row has variable height based on expanded cells
            rowY += calculateRowHeight(rows.get(i));
        }

        return rowY;
    }

    /**
     * Gets the row index at a specific Y position.
     */
    public int getRowIndexAtY(double py) {
        if (py < y + HEADER_HEIGHT + ROW_HEIGHT) {
            return -1;  // In header area
        }

        double currentY = y + HEADER_HEIGHT + ROW_HEIGHT;

        for (int i = 0; i < rows.size(); i++) {
            double rowHeight = calculateRowHeight(rows.get(i));
            double rowEndY = currentY + rowHeight;

            if (py >= currentY && py < rowEndY) {
                return i;
            }
            currentY = rowEndY;
        }

        return -1;
    }

    /**
     * Checks if a point is within an expanded child grid area (inside a cell).
     * Returns the child grid if found, null otherwise.
     */
    public NestedGridNode getChildGridAt(double px, double py) {
        if (!expanded) return null;

        double currentY = y + HEADER_HEIGHT + ROW_HEIGHT;

        for (TableRow row : rows) {
            double rowHeight = calculateRowHeight(row);
            double rowEndY = currentY + rowHeight;

            // Check if py is within this row
            if (py >= currentY && py < rowEndY) {
                // Check if py is in the expanded cell area (below the text row)
                if (py >= currentY + ROW_HEIGHT) {
                    // Find which column the X coordinate is in
                    double colX = x + GRID_PADDING;
                    for (TableColumn col : columns) {
                        String colName = col.getName();
                        double colEndX = colX + col.getWidth();

                        if (px >= colX && px < colEndX && row.isColumnExpanded(colName)) {
                            NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
                            if (childGrid != null) {
                                return childGrid;
                            }
                        }
                        colX = colEndX;
                    }
                }
                return null;  // In the row but not in a child grid
            }

            currentY = rowEndY;
        }

        return null;
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
     * Tests if a point is inside the column headers row (below table header, above data rows).
     * This is the row that shows column names like "@sku", "@qty", "name", etc.
     */
    public boolean isColumnHeaderHit(double px, double py) {
        double columnHeaderY = y + HEADER_HEIGHT;
        return px >= x && px <= x + width &&
               py >= columnHeaderY && py <= columnHeaderY + ROW_HEIGHT;
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

    /**
     * Gets the X position of a column by name.
     */
    public double getColumnX(String columnName) {
        double colX = x + GRID_PADDING;
        for (TableColumn col : columns) {
            if (col.getName().equals(columnName)) {
                return colX;
            }
            colX += col.getWidth();
        }
        return x + GRID_PADDING;
    }

    /**
     * Gets the width of a column by name.
     */
    public double getColumnWidth(String columnName) {
        for (TableColumn col : columns) {
            if (col.getName().equals(columnName)) {
                return col.getWidth();
            }
        }
        return MIN_COLUMN_WIDTH;
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

    // ==================== Sort State Accessors ====================

    /**
     * Returns the name of the column currently sorted by, or null if not sorted.
     */
    public String getSortedColumnName() { return sortedColumnName; }

    /**
     * Returns true if sorting is ascending, false if descending.
     */
    public boolean isSortAscending() { return sortAscending; }

    /**
     * Sets the sort state for this table.
     *
     * @param columnName the column to sort by (null to clear sorting)
     * @param ascending  true for ascending, false for descending
     */
    public void setSortState(String columnName, boolean ascending) {
        String oldColumn = this.sortedColumnName;
        boolean oldAscending = this.sortAscending;
        this.sortedColumnName = columnName;
        this.sortAscending = ascending;

        // Persist to cache for survival across rebuilds
        if (columnName != null) {
            sortStateCache.put(elementName, new SortState(columnName, ascending));
        } else {
            sortStateCache.remove(elementName);
        }

        pcs.firePropertyChange("sortState",
            oldColumn + ":" + oldAscending,
            columnName + ":" + ascending);
    }

    /**
     * Clears the sort state.
     */
    public void clearSortState() {
        setSortState(null, true);
    }

    /**
     * Restores the sort state from the cache if available.
     * Called during table construction.
     */
    private void restoreSortStateFromCache() {
        SortState cached = sortStateCache.get(elementName);
        if (cached != null) {
            this.sortedColumnName = cached.columnName;
            this.sortAscending = cached.ascending;
        }
    }

    /**
     * Checks if the given column is the currently sorted column.
     *
     * @param columnName the column name to check
     * @return true if this column is sorted
     */
    public boolean isSortedBy(String columnName) {
        return columnName != null && columnName.equals(sortedColumnName);
    }

    public XmlElement getSelectedElement() {
        if (selectedRowIndex >= 0 && selectedRowIndex < rows.size()) {
            return rows.get(selectedRowIndex).getElement();
        }
        return null;
    }

    /**
     * Toggles expansion of a complex cell and creates/removes the child grid.
     * @param rowIndex Row index
     * @param columnName Column name
     * @return true if the cell was expanded/collapsed, false if not a complex cell
     */
    public boolean toggleCellExpansion(int rowIndex, String columnName) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return false;

        TableRow row = rows.get(rowIndex);
        if (!row.hasComplexChild(columnName)) return false;

        row.toggleColumnExpanded(columnName);

        // If now expanded, create the child grid
        if (row.isColumnExpanded(columnName)) {
            NestedGridNode childGrid = row.getOrCreateChildGrid(columnName, depth, onLayoutChangedCallback);
            if (childGrid != null) {
                // Position will be set during layout
                childGrid.setExpanded(true);
            }
        }

        // Recalculate column widths to accommodate expanded content
        recalculateColumnWidthsWithExpandedCells();

        pcs.firePropertyChange("cellExpansion", null, columnName);
        return true;
    }

    /**
     * Gets a column by its name.
     */
    public TableColumn getColumn(String name) {
        for (TableColumn col : columns) {
            if (col.getName().equals(name)) {
                return col;
            }
        }
        return null;
    }

    /**
     * Gets a column by its index.
     */
    public TableColumn getColumn(int index) {
        if (index >= 0 && index < columns.size()) {
            return columns.get(index);
        }
        return null;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // ==================== Sorting Support ====================

    /**
     * Data types for smart sorting.
     */
    public enum ColumnDataType {
        STRING,   // Alphabetical sort (case-insensitive)
        NUMERIC,  // Numeric sort
        DATE      // Date sort
    }

    /**
     * Checks if a column is sortable.
     * A column is sortable if none of its rows contain complex children for that column.
     *
     * @param columnName the column name to check
     * @return true if the column can be sorted
     */
    public boolean isColumnSortable(String columnName) {
        TableColumn col = getColumn(columnName);
        if (col == null) return false;

        // Attribute columns are always sortable (they are always simple values)
        if (col.getType() == ColumnType.ATTRIBUTE) {
            return true;
        }

        // TEXT_CONTENT is always sortable
        if (col.getType() == ColumnType.TEXT_CONTENT) {
            return true;
        }

        // For child element columns, check if any row has complex data
        for (TableRow row : rows) {
            if (row.hasComplexChild(columnName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Detects the data type of values in a column for smart sorting.
     * Analyzes all non-empty values to determine if they are numeric, date, or string.
     *
     * @param columnName the column to analyze
     * @return the detected data type
     */
    public ColumnDataType detectColumnDataType(String columnName) {
        List<String> values = rows.stream()
                .map(row -> row.getValue(columnName))
                .filter(v -> v != null && !v.trim().isEmpty())
                .toList();

        if (values.isEmpty()) {
            return ColumnDataType.STRING;
        }

        // Try numeric - check if all values can be parsed as numbers
        boolean allNumeric = values.stream().allMatch(v -> {
            try {
                // Handle common number formats (with commas, spaces, etc.)
                String cleaned = v.replace(",", "").replace(" ", "").trim();
                Double.parseDouble(cleaned);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        });

        if (allNumeric) {
            return ColumnDataType.NUMERIC;
        }

        // Try date - check common date patterns
        boolean allDates = values.stream().allMatch(this::looksLikeDate);
        if (allDates) {
            return ColumnDataType.DATE;
        }

        // Default to string
        return ColumnDataType.STRING;
    }

    /**
     * Checks if a string looks like a date.
     * Supports common formats: ISO 8601, yyyy-MM-dd, dd.MM.yyyy, MM/dd/yyyy
     */
    private boolean looksLikeDate(String value) {
        if (value == null || value.trim().isEmpty()) return false;

        String v = value.trim();

        // ISO 8601: 2024-01-15 or 2024-01-15T10:30:00
        if (v.matches("\\d{4}-\\d{2}-\\d{2}(T.*)?")) return true;

        // European: 15.01.2024
        if (v.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) return true;

        // US format: 01/15/2024
        if (v.matches("\\d{2}/\\d{2}/\\d{4}")) return true;

        // Short formats: 2024-01, 01/2024
        if (v.matches("\\d{4}-\\d{2}")) return true;
        if (v.matches("\\d{2}/\\d{4}")) return true;

        return false;
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
            List<XmlNode> children, NestedGridNode parentNode, int depth, Runnable onLayoutChangedCallback) {

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
                    new RepeatingElementsTable(entry.getKey(), entry.getValue(), parentNode, depth, onLayoutChangedCallback));
            }
        }

        return tables;
    }
}
