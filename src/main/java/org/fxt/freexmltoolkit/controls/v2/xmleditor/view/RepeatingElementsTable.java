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

    /**
     * The height of the table header row in pixels.
     * This header displays the element name and count (e.g., "items (3x)").
     */
    public static final double HEADER_HEIGHT = 28;

    /**
     * The height of each data row in pixels.
     * This also applies to the column header row.
     */
    public static final double ROW_HEIGHT = 24;

    /**
     * The minimum width of a table column in pixels.
     * Columns will not be narrower than this value even if content is shorter.
     */
    public static final double MIN_COLUMN_WIDTH = 80;

    /**
     * The maximum width of a table column in pixels.
     * Columns will not be wider than this value unless they contain expanded cells.
     */
    public static final double MAX_COLUMN_WIDTH = 250;

    /**
     * The padding around the grid content in pixels.
     * Applied to all sides of the table.
     */
    public static final double GRID_PADDING = 8;

    /**
     * The padding inside each cell in pixels.
     * Applied horizontally to cell content.
     */
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
     *
     * <p>This inner class holds the sorting configuration for a table, including
     * which column is sorted and the sort direction. It is used to restore
     * the sort state when the table is rebuilt (e.g., after editing).</p>
     */
    private static class SortState {
        /**
         * The name of the column that is sorted.
         */
        final String columnName;

        /**
         * True if sorting is ascending, false if descending.
         */
        final boolean ascending;

        /**
         * Constructs a new SortState with the specified column and direction.
         *
         * @param columnName the name of the sorted column
         * @param ascending  true for ascending sort, false for descending
         */
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

    /**
     * Represents a column in the repeating elements table.
     *
     * <p>Each column corresponds to either an attribute or a first-level child element
     * of the repeating XML elements. The column stores its name, type, and current width.</p>
     */
    public static class TableColumn {
        /**
         * The name of the column (attribute name or child element name).
         */
        private final String name;

        /**
         * The type of this column (attribute, child element, or text content).
         */
        private final ColumnType type;

        /**
         * The current width of this column in pixels.
         */
        private double width;

        /**
         * Constructs a new TableColumn with the specified name and type.
         *
         * @param name the column name (attribute name or element name)
         * @param type the type of data this column represents
         */
        public TableColumn(String name, ColumnType type) {
            this.name = name;
            this.type = type;
            this.width = MIN_COLUMN_WIDTH;
        }

        /**
         * Returns the name of this column.
         *
         * @return the column name
         */
        public String getName() { return name; }

        /**
         * Returns the type of this column.
         *
         * @return the column type
         */
        public ColumnType getType() { return type; }

        /**
         * Returns the current width of this column in pixels.
         *
         * @return the column width
         */
        public double getWidth() { return width; }

        /**
         * Sets the width of this column.
         *
         * @param width the new width in pixels
         */
        public void setWidth(double width) { this.width = width; }

        /**
         * Returns the display name for this column header.
         * Attribute columns are prefixed with "@" to distinguish them from elements.
         *
         * @return the display name (e.g., "@id" for attributes, "name" for elements)
         */
        public String getDisplayName() {
            return type == ColumnType.ATTRIBUTE ? "@" + name : name;
        }
    }

    /**
     * Enumeration of column types in the repeating elements table.
     *
     * <p>Columns can represent different types of XML data:</p>
     * <ul>
     *   <li>{@link #ATTRIBUTE} - Data from an element's attribute</li>
     *   <li>{@link #CHILD_ELEMENT} - Text content from a first-level child element</li>
     *   <li>{@link #TEXT_CONTENT} - Direct text content of the element</li>
     * </ul>
     */
    public enum ColumnType {
        /**
         * Column data comes from an XML attribute.
         */
        ATTRIBUTE,

        /**
         * Column data comes from the text content of a first-level child element.
         */
        CHILD_ELEMENT,

        /**
         * Column data comes from the direct text content of the element.
         */
        TEXT_CONTENT
    }

    // ==================== Row Definition ====================

    /**
     * Represents a single row in the repeating elements table.
     *
     * <p>Each row corresponds to one XML element from the repeating group.
     * The row stores the column values extracted from the element's attributes
     * and child elements, as well as any expanded child grids for complex content.</p>
     */
    public static class TableRow {
        /**
         * The XML element that this row represents.
         */
        private final XmlElement element;

        /**
         * Map of column names to their string values.
         */
        private final Map<String, String> values = new LinkedHashMap<>();

        /**
         * Map of column names to complex child elements (elements with nested structure).
         */
        private final Map<String, XmlElement> complexChildren = new LinkedHashMap<>();

        /**
         * Map of column names to expanded child grid nodes.
         */
        private final Map<String, NestedGridNode> expandedChildGrids = new LinkedHashMap<>();

        /**
         * Set of column names that are currently expanded to show nested content.
         */
        private final Set<String> expandedColumns = new HashSet<>();

        /**
         * Whether this row is expanded to show additional details.
         */
        private boolean expanded = false;

        /**
         * Constructs a new TableRow for the specified XML element.
         *
         * @param element the XML element this row represents
         */
        public TableRow(XmlElement element) {
            this.element = element;
        }

        /**
         * Returns the XML element that this row represents.
         *
         * @return the underlying XML element
         */
        public XmlElement getElement() { return element; }

        /**
         * Returns the map of column names to their string values.
         *
         * @return the values map
         */
        public Map<String, String> getValues() { return values; }

        /**
         * Returns the map of column names to complex child elements.
         *
         * @return the complex children map
         */
        public Map<String, XmlElement> getComplexChildren() { return complexChildren; }

        /**
         * Returns the map of column names to expanded child grid nodes.
         *
         * @return the expanded child grids map
         */
        public Map<String, NestedGridNode> getExpandedChildGrids() { return expandedChildGrids; }

        /**
         * Returns the value for a specific column.
         *
         * @param columnName the name of the column
         * @return the value for the column, or empty string if not found
         */
        public String getValue(String columnName) {
            return values.getOrDefault(columnName, "");
        }

        /**
         * Checks if this row has a complex child element for the specified column.
         *
         * @param columnName the column name to check
         * @return true if the column contains a complex child element
         */
        public boolean hasComplexChild(String columnName) {
            return complexChildren.containsKey(columnName);
        }

        /**
         * Returns the complex child element for the specified column.
         *
         * @param columnName the column name
         * @return the complex child element, or null if not found
         */
        public XmlElement getComplexChild(String columnName) {
            return complexChildren.get(columnName);
        }

        /**
         * Checks if the specified column is currently expanded.
         *
         * @param columnName the column name to check
         * @return true if the column is expanded to show nested content
         */
        public boolean isColumnExpanded(String columnName) {
            return expandedColumns.contains(columnName);
        }

        /**
         * Toggles the expansion state of a column.
         * If expanded, collapses it and removes the child grid.
         * If collapsed, marks it as expanded.
         *
         * @param columnName the column name to toggle
         */
        public void toggleColumnExpanded(String columnName) {
            if (expandedColumns.contains(columnName)) {
                expandedColumns.remove(columnName);
                expandedChildGrids.remove(columnName);
            } else {
                expandedColumns.add(columnName);
            }
        }

        /**
         * Returns the set of column names that are currently expanded.
         *
         * @return the set of expanded column names
         */
        public Set<String> getExpandedColumns() { return expandedColumns; }

        /**
         * Checks if this row is expanded to show additional details.
         *
         * @return true if the row is expanded
         */
        public boolean isExpanded() { return expanded; }

        /**
         * Sets the expansion state of this row.
         *
         * @param expanded true to expand, false to collapse
         */
        public void setExpanded(boolean expanded) { this.expanded = expanded; }

        /**
         * Gets or creates a child grid for the specified column.
         *
         * <p>If a child grid does not exist for the column, one is created using
         * {@link NestedGridNode#buildChildrenOnly} to show only the children
         * (not the element itself, since the column header already shows the element name).</p>
         *
         * @param columnName              the column name
         * @param depth                   the current nesting depth
         * @param onLayoutChangedCallback callback to invoke when layout changes
         * @return the child grid for the column, or null if no complex child exists
         */
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

    /**
     * Constructs a new RepeatingElementsTable for a group of repeating XML elements.
     *
     * <p>The constructor analyzes the structure of all elements to determine columns,
     * builds table rows from the elements, calculates optimal column widths, and
     * restores any previously saved sort state from the cache.</p>
     *
     * @param elementName             the common name of all elements in this table
     * @param elements                the list of XML elements to display in the table
     * @param parentNode              the parent NestedGridNode containing this table
     * @param depth                   the nesting depth of this table
     * @param onLayoutChangedCallback callback to invoke when the layout changes
     */
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
     * Columns come from: attributes + first-level child elements.
     *
     * <p>Column order is determined by merging the document order from all elements.
     * When different elements have different children, the unified column order
     * respects the relative position of each column within each element.</p>
     *
     * <p>Example: If Element1 has [A, C, D] and Element2 has [A, B, C, D, E],
     * the merged order will be [A, B, C, D, E] because B appears before C in Element2.</p>
     */
    private void analyzeStructure() {
        // Collect all unique column names for validation
        Set<String> allAttributeNames = new LinkedHashSet<>();
        Set<String> allChildElementNames = new LinkedHashSet<>();
        boolean hasDirectText = false;

        // Also build per-element column order lists for merging
        List<List<String>> perElementOrders = new ArrayList<>();

        for (XmlElement element : elements) {
            List<String> elementColumnOrder = new ArrayList<>();

            // Collect attributes (in document order)
            for (String attrName : element.getAttributes().keySet()) {
                allAttributeNames.add(attrName);
                elementColumnOrder.add("@" + attrName);
            }

            // Collect first-level child element names (in document order)
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement) {
                    String childName = ((XmlElement) child).getName();
                    allChildElementNames.add(childName);
                    // Only add first occurrence of each child name per element
                    if (!elementColumnOrder.contains(childName)) {
                        elementColumnOrder.add(childName);
                    }
                } else if (child instanceof XmlText) {
                    String text = ((XmlText) child).getText().trim();
                    if (!text.isEmpty()) {
                        hasDirectText = true;
                        // Add text marker if not already present
                        if (!elementColumnOrder.contains("#text")) {
                            elementColumnOrder.add("#text");
                        }
                    }
                }
            }

            perElementOrders.add(elementColumnOrder);
        }

        // Check if we have a cached column order for this element type
        List<String> cachedOrder = columnOrderCache.get(elementName);

        // Merge all element orders into a unified order respecting document order
        List<String> mergedOrder = mergeColumnOrders(perElementOrders);

        if (cachedOrder != null) {
            // Use cached order for known columns, but insert new columns at correct positions
            List<String> finalOrder = mergeCachedWithNew(cachedOrder, mergedOrder);

            for (String colName : finalOrder) {
                if (colName.startsWith("@") && allAttributeNames.contains(colName.substring(1))) {
                    columns.add(new TableColumn(colName.substring(1), ColumnType.ATTRIBUTE));
                } else if (colName.equals("#text") && hasDirectText) {
                    columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
                } else if (allChildElementNames.contains(colName)) {
                    columns.add(new TableColumn(colName, ColumnType.CHILD_ELEMENT));
                }
            }

            // Update cache with the new merged order
            columnOrderCache.put(elementName, finalOrder);
        } else {
            // First time - use merged order and cache it
            for (String colName : mergedOrder) {
                if (colName.startsWith("@") && allAttributeNames.contains(colName.substring(1))) {
                    columns.add(new TableColumn(colName.substring(1), ColumnType.ATTRIBUTE));
                } else if (colName.equals("#text") && hasDirectText) {
                    columns.add(new TableColumn("#text", ColumnType.TEXT_CONTENT));
                } else if (allChildElementNames.contains(colName)) {
                    columns.add(new TableColumn(colName, ColumnType.CHILD_ELEMENT));
                }
            }

            // Cache the column order for future rebuilds
            columnOrderCache.put(elementName, mergedOrder);
        }
    }

    /**
     * Merges column orders from multiple elements into a single unified order.
     *
     * <p>The algorithm respects the relative order of columns within each element.
     * When a new column is encountered, it is inserted at a position that maintains
     * its relative order to columns that are already in the merged list.</p>
     *
     * @param perElementOrders list of column orders, one per element
     * @return unified column order respecting document order from all elements
     */
    private List<String> mergeColumnOrders(List<List<String>> perElementOrders) {
        List<String> merged = new ArrayList<>();

        for (List<String> elementOrder : perElementOrders) {
            int insertPosition = 0;

            for (String colName : elementOrder) {
                int existingIndex = merged.indexOf(colName);

                if (existingIndex >= 0) {
                    // Column already exists - update insert position to after it
                    insertPosition = existingIndex + 1;
                } else {
                    // New column - find the best position to insert it
                    // Look for the next column in this element's order that's already in merged
                    int bestPosition = findBestInsertPosition(merged, elementOrder, colName, insertPosition);
                    merged.add(bestPosition, colName);
                    insertPosition = bestPosition + 1;
                }
            }
        }

        return merged;
    }

    /**
     * Finds the best position to insert a new column in the merged list.
     *
     * <p>This looks at the columns that come after the new column in the element's
     * order and finds where they are in the merged list. The new column should be
     * inserted before the first of these columns.</p>
     *
     * @param merged the current merged column list
     * @param elementOrder the column order from the current element
     * @param colName the column to insert
     * @param minPosition the minimum position (must be >= this)
     * @return the best position to insert the column
     */
    private int findBestInsertPosition(List<String> merged, List<String> elementOrder,
                                        String colName, int minPosition) {
        int colIndex = elementOrder.indexOf(colName);

        // Look for subsequent columns in the element order that already exist in merged
        for (int i = colIndex + 1; i < elementOrder.size(); i++) {
            String laterCol = elementOrder.get(i);
            int laterIndex = merged.indexOf(laterCol);
            if (laterIndex >= 0 && laterIndex >= minPosition) {
                // Found a later column - insert before it
                return laterIndex;
            }
        }

        // No later columns found - append at end (but at least at minPosition)
        return Math.max(minPosition, merged.size());
    }

    /**
     * Merges a cached column order with a newly computed order.
     *
     * <p>This preserves the cached order for existing columns (important for
     * maintaining sort stability) while inserting new columns at their
     * correct positions based on document order.</p>
     *
     * @param cachedOrder the previously cached column order
     * @param newOrder the newly computed order from current elements
     * @return merged order with new columns inserted at correct positions
     */
    private List<String> mergeCachedWithNew(List<String> cachedOrder, List<String> newOrder) {
        List<String> result = new ArrayList<>(cachedOrder);

        for (String colName : newOrder) {
            if (!result.contains(colName)) {
                // New column - find correct position based on newOrder
                int insertPos = findInsertPositionFromNewOrder(result, newOrder, colName);
                result.add(insertPos, colName);
            }
        }

        return result;
    }

    /**
     * Finds where to insert a new column based on its position in the new order.
     *
     * @param result the current result list
     * @param newOrder the new order to use as reference
     * @param colName the column to insert
     * @return position to insert at
     */
    private int findInsertPositionFromNewOrder(List<String> result, List<String> newOrder, String colName) {
        int colIndex = newOrder.indexOf(colName);

        // Look for the first column after colName (in newOrder) that exists in result
        for (int i = colIndex + 1; i < newOrder.size(); i++) {
            String laterCol = newOrder.get(i);
            int laterIndex = result.indexOf(laterCol);
            if (laterIndex >= 0) {
                return laterIndex;
            }
        }

        // Look for the last column before colName (in newOrder) that exists in result
        for (int i = colIndex - 1; i >= 0; i--) {
            String earlierCol = newOrder.get(i);
            int earlierIndex = result.indexOf(earlierCol);
            if (earlierIndex >= 0) {
                return earlierIndex + 1;
            }
        }

        // No reference points - append at end
        return result.size();
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
     *
     * <p>This method should be called after a cell is expanded to ensure the column
     * is wide enough to accommodate the nested content. It iterates through all columns
     * and rows, checking both text content width and expanded child grid width.</p>
     *
     * <p>Columns with expanded cells are allowed a larger maximum width (500px) compared
     * to regular columns ({@link #MAX_COLUMN_WIDTH}).</p>
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
     *
     * <p>When collapsed, returns only the header height plus padding.
     * When expanded, includes the column header row and all data rows with
     * their variable heights (accounting for expanded cells).</p>
     *
     * @return the calculated height in pixels
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
     *
     * <p>The Y position accounts for variable row heights caused by expanded cells.
     * It starts after the table header and column header rows.</p>
     *
     * @param rowIndex the zero-based index of the row
     * @return the Y coordinate of the row's top edge in pixels
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
     *
     * <p>This method accounts for variable row heights caused by expanded cells.
     * Returns -1 if the Y position is in the header area or outside the table.</p>
     *
     * @param py the Y coordinate to test
     * @return the zero-based row index, or -1 if in header area or outside
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
     *
     * <p>This method is used for hit testing to determine if mouse events
     * should be delegated to a nested child grid. It checks if the point
     * is within the expanded cell area (below the text row) of any row.</p>
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return the child grid at the specified point, or null if not in a child grid
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
     * Calculates the width of this table based on column widths.
     *
     * <p>The table width is the sum of all column widths plus padding on both sides.
     * The minimum width is enforced from {@link NestedGridNode#MIN_GRID_WIDTH}.</p>
     *
     * @param availableWidth the available width (currently unused, for future expansion)
     * @return the calculated width in pixels
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
     * Gets the total width of all columns combined.
     *
     * @return the sum of all column widths in pixels
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
     * Tests if a point is inside this table's header area.
     *
     * <p>The header is the top row that displays the element name and count.</p>
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is within the header bounds
     */
    public boolean isHeaderHit(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + HEADER_HEIGHT;
    }

    /**
     * Tests if a point is inside the column headers row.
     *
     * <p>The column header row is positioned below the table header and above the data rows.
     * It displays column names like "@sku", "@qty", "name", etc.</p>
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is within the column header row bounds
     */
    public boolean isColumnHeaderHit(double px, double py) {
        double columnHeaderY = y + HEADER_HEIGHT;
        return px >= x && px <= x + width &&
               py >= columnHeaderY && py <= columnHeaderY + ROW_HEIGHT;
    }

    /**
     * Tests if a point is inside this table's bounds.
     *
     * @param px the X coordinate to test
     * @param py the Y coordinate to test
     * @return true if the point is within the table's bounding rectangle
     */
    public boolean containsPoint(double px, double py) {
        return px >= x && px <= x + width &&
               py >= y && py <= y + height;
    }

    /**
     * Gets the row index at a given Y coordinate using fixed row height calculation.
     *
     * <p>Note: This method uses a simplified calculation assuming fixed row heights.
     * For accurate results with expanded cells, use {@link #getRowIndexAtY(double)}.</p>
     *
     * @param py the Y coordinate to test
     * @return the zero-based row index, or -1 if in header area or outside
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
     *
     * @param px the X coordinate to test
     * @return the zero-based column index, or -1 if outside column bounds
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
     * Gets the X position of a column by its name.
     *
     * @param columnName the name of the column to find
     * @return the X coordinate of the column's left edge, or the default position if not found
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
     * Gets the width of a column by its name.
     *
     * @param columnName the name of the column to find
     * @return the width of the column in pixels, or {@link #MIN_COLUMN_WIDTH} if not found
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
     * Tests if this table is visible within the given viewport bounds.
     *
     * <p>A table is considered visible if any part of it overlaps with the viewport.</p>
     *
     * @param viewportTop    the Y coordinate of the viewport's top edge
     * @param viewportBottom the Y coordinate of the viewport's bottom edge
     * @return true if the table is at least partially visible
     */
    public boolean isVisible(double viewportTop, double viewportBottom) {
        double nodeTop = y;
        double nodeBottom = y + height;
        return nodeBottom >= viewportTop && nodeTop <= viewportBottom;
    }

    // ==================== Getters and Setters ====================

    /**
     * Returns the common name of all elements in this table.
     *
     * @return the element name
     */
    public String getElementName() { return elementName; }

    /**
     * Returns the list of XML elements displayed in this table.
     *
     * @return the list of elements
     */
    public List<XmlElement> getElements() { return elements; }

    /**
     * Returns the number of elements in this table.
     *
     * @return the element count
     */
    public int getElementCount() { return elements.size(); }

    /**
     * Returns the list of columns in this table.
     *
     * @return the list of table columns
     */
    public List<TableColumn> getColumns() { return columns; }

    /**
     * Returns the list of rows in this table.
     *
     * @return the list of table rows
     */
    public List<TableRow> getRows() { return rows; }

    /**
     * Returns the parent NestedGridNode containing this table.
     *
     * @return the parent node
     */
    public NestedGridNode getParentNode() { return parentNode; }

    /**
     * Returns the nesting depth of this table.
     *
     * @return the depth level
     */
    public int getDepth() { return depth; }

    /**
     * Returns the X coordinate of this table.
     *
     * @return the X position in pixels
     */
    public double getX() { return x; }

    /**
     * Sets the X coordinate of this table.
     *
     * @param x the new X position in pixels
     */
    public void setX(double x) { this.x = x; }

    /**
     * Returns the Y coordinate of this table.
     *
     * @return the Y position in pixels
     */
    public double getY() { return y; }

    /**
     * Sets the Y coordinate of this table.
     *
     * @param y the new Y position in pixels
     */
    public void setY(double y) { this.y = y; }

    /**
     * Returns the width of this table.
     *
     * @return the width in pixels
     */
    public double getWidth() { return width; }

    /**
     * Sets the width of this table.
     *
     * @param width the new width in pixels
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Returns the height of this table.
     *
     * @return the height in pixels
     */
    public double getHeight() { return height; }

    /**
     * Sets the height of this table.
     *
     * @param height the new height in pixels
     */
    public void setHeight(double height) { this.height = height; }

    /**
     * Checks if this table is expanded to show all rows.
     *
     * @return true if expanded, false if collapsed
     */
    public boolean isExpanded() { return expanded; }

    /**
     * Sets the expansion state of this table.
     * Fires a property change event for "expanded".
     *
     * @param expanded true to expand, false to collapse
     */
    public void setExpanded(boolean expanded) {
        boolean old = this.expanded;
        this.expanded = expanded;
        pcs.firePropertyChange("expanded", old, expanded);
    }

    /**
     * Toggles the expansion state of this table.
     */
    public void toggleExpanded() { setExpanded(!expanded); }

    /**
     * Checks if this table is currently selected.
     *
     * @return true if selected
     */
    public boolean isSelected() { return selected; }

    /**
     * Sets the selection state of this table.
     * Fires a property change event for "selected".
     *
     * @param selected true to select, false to deselect
     */
    public void setSelected(boolean selected) {
        boolean old = this.selected;
        this.selected = selected;
        pcs.firePropertyChange("selected", old, selected);
    }

    /**
     * Checks if this table is currently hovered by the mouse.
     *
     * @return true if hovered
     */
    public boolean isHovered() { return hovered; }

    /**
     * Sets the hover state of this table.
     * Fires a property change event for "hovered".
     *
     * @param hovered true if mouse is over the table
     */
    public void setHovered(boolean hovered) {
        boolean old = this.hovered;
        this.hovered = hovered;
        pcs.firePropertyChange("hovered", old, hovered);
    }

    /**
     * Returns the index of the currently hovered row.
     *
     * @return the hovered row index, or -1 if no row is hovered
     */
    public int getHoveredRowIndex() { return hoveredRowIndex; }

    /**
     * Sets the index of the hovered row.
     *
     * @param index the row index, or -1 if no row is hovered
     */
    public void setHoveredRowIndex(int index) { this.hoveredRowIndex = index; }

    /**
     * Returns the index of the currently hovered column.
     *
     * @return the hovered column index, or -1 if no column is hovered
     */
    public int getHoveredColumnIndex() { return hoveredColumnIndex; }

    /**
     * Sets the index of the hovered column.
     *
     * @param index the column index, or -1 if no column is hovered
     */
    public void setHoveredColumnIndex(int index) { this.hoveredColumnIndex = index; }

    /**
     * Returns the index of the currently selected row.
     *
     * @return the selected row index, or -1 if no row is selected
     */
    public int getSelectedRowIndex() { return selectedRowIndex; }

    /**
     * Sets the index of the selected row.
     * Fires a property change event for "selectedRowIndex".
     *
     * @param index the row index to select, or -1 to deselect
     */
    public void setSelectedRowIndex(int index) {
        int old = this.selectedRowIndex;
        this.selectedRowIndex = index;
        pcs.firePropertyChange("selectedRowIndex", old, index);
    }

    // ==================== Sort State Accessors ====================

    /**
     * Returns the name of the column currently sorted by, or null if not sorted.
     *
     * @return the sorted column name, or null if the table is not sorted
     */
    public String getSortedColumnName() { return sortedColumnName; }

    /**
     * Returns true if sorting is ascending, false if descending.
     *
     * @return true for ascending sort order, false for descending
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

    /**
     * Returns the XML element of the currently selected row.
     *
     * @return the selected XML element, or null if no row is selected
     */
    public XmlElement getSelectedElement() {
        if (selectedRowIndex >= 0 && selectedRowIndex < rows.size()) {
            return rows.get(selectedRowIndex).getElement();
        }
        return null;
    }

    /**
     * Toggles expansion of a complex cell and creates or removes the child grid.
     *
     * <p>When a cell containing complex content is expanded, a child grid is created
     * to display the nested XML structure. When collapsed, the child grid is removed.</p>
     *
     * <p>After toggling, column widths are recalculated to accommodate the expanded content.</p>
     *
     * @param rowIndex   the zero-based index of the row
     * @param columnName the name of the column containing the complex cell
     * @return true if the cell was successfully expanded or collapsed, false if the cell
     *         does not contain complex content or the row index is invalid
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
     *
     * @param name the name of the column to find
     * @return the TableColumn with the specified name, or null if not found
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
     *
     * @param index the zero-based index of the column
     * @return the TableColumn at the specified index, or null if index is out of bounds
     */
    public TableColumn getColumn(int index) {
        if (index >= 0 && index < columns.size()) {
            return columns.get(index);
        }
        return null;
    }

    /**
     * Adds a property change listener to this table.
     *
     * <p>Listeners will be notified of changes to properties such as "expanded",
     * "selected", "hovered", "selectedRowIndex", "sortState", and "cellExpansion".</p>
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener from this table.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // ==================== Sorting Support ====================

    /**
     * Data types used for smart column sorting.
     *
     * <p>The data type determines how values are compared during sorting:</p>
     * <ul>
     *   <li>{@link #STRING} - Alphabetical comparison, case-insensitive</li>
     *   <li>{@link #NUMERIC} - Numeric comparison after parsing</li>
     *   <li>{@link #DATE} - Chronological comparison</li>
     * </ul>
     */
    public enum ColumnDataType {
        /**
         * String data type for alphabetical sorting (case-insensitive).
         */
        STRING,

        /**
         * Numeric data type for numerical sorting.
         */
        NUMERIC,

        /**
         * Date data type for chronological sorting.
         */
        DATE
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
     * <p>This factory method analyzes a list of child nodes, groups XML elements
     * by their tag name, and creates a RepeatingElementsTable for each group
     * that contains 2 or more elements with the same name.</p>
     *
     * <p>Elements that appear only once are not included in the returned map
     * and should be displayed as individual nested grids instead.</p>
     *
     * @param children                the list of child nodes to analyze
     * @param parentNode              the parent grid node that will contain the tables
     * @param depth                   the current nesting depth
     * @param onLayoutChangedCallback callback to invoke when layout changes
     * @return a map of element names to tables, containing only elements that appear 2 or more times
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
