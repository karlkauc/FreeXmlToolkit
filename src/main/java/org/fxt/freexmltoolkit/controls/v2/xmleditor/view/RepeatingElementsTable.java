package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

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
     * The height of a single-line data row in pixels (alias of {@link GridMetrics#ROW_HEIGHT}).
     * This also applies to the column header row. Rows with wrapped text are taller.
     */
    public static final double ROW_HEIGHT = GridMetrics.ROW_HEIGHT;

    /**
     * The minimum width of a table column in pixels.
     * Columns will not be narrower than this value even if content is shorter.
     */
    public static final double MIN_COLUMN_WIDTH = 80;

    /**
     * The padding around the grid content in pixels.
     * Applied to all sides of the table.
     */
    public static final double GRID_PADDING = 8;

    /**
     * The padding inside each cell in pixels (alias of {@link GridMetrics#CELL_PADDING}).
     * Applied horizontally to cell content.
     */
    public static final double CELL_PADDING = GridMetrics.CELL_PADDING;

    /**
     * The minimum width of a table in pixels.
     */
    private static final double MIN_TABLE_WIDTH = 200;

    private static final double[] NO_TOPS = new double[0];

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

    // ==================== Column Width Override Cache ====================
    // User-dragged column widths per element name, so they survive the table
    // rebuild that follows every edit/sort/undo (same idea as the sort-state cache).
    private static final Map<String, Map<String, Double>> columnWidthOverrideCache = new HashMap<>();

    /**
     * Clears all caches. Call this when loading a new document.
     */
    public static void clearAllCaches() {
        columnOrderCache.clear();
        sortStateCache.clear();
        columnWidthOverrideCache.clear();
    }

    // ==================== Data ====================

    private final String elementName;
    private final List<GridRecord> records;
    private final List<TableColumn> columns = new ArrayList<>();
    private final List<TableRow> rows = new ArrayList<>();
    private final int depth;
    private final Runnable onLayoutChangedCallback;

    // ==================== Layout ====================

    private double x;
    private double y;
    private double width;
    private double height;

    /** Measurer + wrap width; the estimating default keeps the class toolkit-free. */
    private GridMetrics metrics = GridMetrics.estimated();
    /** False whenever column widths, row heights or cell layouts must be recomputed. */
    private boolean layoutValid = false;
    /** Row top offsets relative to the first data row; {@code rowTops[rows.size()]} = total. */
    private double[] rowTops = new double[]{0};

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
         * The current (effective) width of this column in pixels.
         */
        private double width;

        /** The width the content would like (unwrapped), for auto-fit and diagnostics. */
        private double contentWidth;

        /** A user-dragged width, or {@code null} for automatic sizing. */
        private Double userWidth;

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

        /** @return the unwrapped content width (values, header, expanded sub-rows) */
        public double getContentWidth() {
            return contentWidth;
        }

        /** @return the user-dragged width, or {@code null} when sized automatically */
        public Double getUserWidth() {
            return userWidth;
        }

        /** @return whether a user-dragged width overrides automatic sizing */
        public boolean hasUserWidth() {
            return userWidth != null;
        }

        /**
         * Returns the name of this column.
         *
         * @return the column name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the type of this column.
         *
         * @return the column type
         */
        public ColumnType getType() {
            return type;
        }

        /**
         * Returns the current width of this column in pixels.
         *
         * @return the column width
         */
        public double getWidth() {
            return width;
        }

        /**
         * Sets the width of this column.
         *
         * @param width the new width in pixels
         */
        public void setWidth(double width) {
            this.width = width;
        }

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
         * The model-agnostic record this row was built from.
         */
        private final GridRecord record;

        /**
         * Map of column names to their string values.
         */
        private final Map<String, String> values = new LinkedHashMap<>();

        /**
         * Map of column names to complex child nodes (nodes with nested structure).
         */
        private final Map<String, Object> complexChildren = new LinkedHashMap<>();

        /**
         * Display-only attribute summaries per column (e.g. {@code ccy=EUR} for
         * {@code <Amount ccy="EUR">…</Amount>}). Kept SEPARATE from {@link #values}
         * so cell editing still round-trips the bare text value.
         */
        private final Map<String, String> attributeSuffixes = new LinkedHashMap<>();

        /**
         * Whether this row is expanded to show additional details.
         */
        private boolean expanded = false;

        /** Per-column measured layout, filled by the owning table's layout pass. */
        private final Map<String, CellLayout> cellLayouts = new HashMap<>();

        /** The row height from the last layout pass; {@code -1} = needs layout. */
        private double layoutHeight = -1;

        /** The table this row belongs to (set by the table while building rows). */
        private RepeatingElementsTable owner;

        /** @return the measured layout of the given column's cell (after the table laid out) */
        public CellLayout getCellLayout(String columnName) {
            return cellLayouts.get(columnName);
        }

        /** @return the row height from the last layout pass, or -1 if not laid out */
        public double getLayoutHeight() {
            return layoutHeight;
        }

        /** Marks this row's cell layouts stale; the owning table re-lays out on next query. */
        public void invalidateLayout() {
            layoutHeight = -1;
            if (owner != null) {
                owner.invalidateLayout();
            }
        }

        /**
         * Constructs a new TableRow for the specified XML element.
         *
         * @param element the XML element this row represents
         */
        public TableRow(XmlElement element) {
            this(new XmlGridRecord(element));
        }

        /**
         * Constructs a new TableRow for the specified record.
         *
         * @param record the record this row represents
         */
        public TableRow(GridRecord record) {
            this.record = record;
        }

        /**
         * Returns the record this row was built from.
         *
         * @return the underlying record
         */
        public GridRecord getRecord() {
            return record;
        }

        /**
         * Returns the model node this row represents (XmlElement or JsonObject).
         *
         * @return the underlying model node
         */
        public Object getNode() {
            return record.node();
        }

        /**
         * Returns the XML element that this row represents.
         *
         * @return the underlying XML element, or {@code null} for non-XML records
         */
        public XmlElement getElement() {
            return record.node() instanceof XmlElement el ? el : null;
        }

        /**
         * Returns the map of column names to their string values.
         *
         * @return the values map
         */
        public Map<String, String> getValues() {
            return values;
        }

        /**
         * Returns the map of column names to complex child nodes.
         *
         * @return the complex children map
         */
        public Map<String, Object> getComplexChildren() {
            return complexChildren;
        }

        /**
         * @return the display-only attribute summary for a column (e.g. {@code ccy=EUR}),
         * or {@code null} when the cell's element has no attributes
         */
        public String getAttributeSuffix(String columnName) {
            return attributeSuffixes.get(columnName);
        }

        /** @return the mutable attribute-summary map (populated while building rows). */
        public Map<String, String> getAttributeSuffixes() {
            return attributeSuffixes;
        }

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
         * Returns the complex child node for the specified column.
         *
         * @param columnName the column name
         * @return the complex child node, or null if not found
         */
        public Object getComplexChild(String columnName) {
            return complexChildren.get(columnName);
        }

        /**
         * Checks if this row is expanded to show additional details.
         *
         * @return true if the row is expanded
         */
        public boolean isExpanded() {
            return expanded;
        }

        /**
         * Sets the expansion state of this row.
         *
         * @param expanded true to expand, false to collapse
         */
        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

        // ==================== Cell Expansion State ====================

        /**
         * Tracks which columns have their complex cells expanded inline.
         */
        private final Set<String> expandedColumns = new HashSet<>();

        /**
         * Cache of flattened child rows for expanded complex cells.
         */
        private final Map<String, List<FlatRow>> expandedCellRows = new LinkedHashMap<>();

        /**
         * Checks if a column's complex cell is expanded for this row.
         *
         * @param columnName the column name to check
         * @return true if the column is expanded
         */
        public boolean isColumnExpanded(String columnName) {
            return expandedColumns.contains(columnName);
        }

        /**
         * Toggles the expansion state of a complex cell in this row.
         * Lazily creates FlatRows for the complex child on first expansion.
         *
         * @param columnName the column name to toggle
         */
        public void toggleColumnExpanded(String columnName) {
            if (expandedColumns.contains(columnName)) {
                expandedColumns.remove(columnName);
                expandedCellRows.remove(columnName);
            } else {
                expandedColumns.add(columnName);
                if (!expandedCellRows.containsKey(columnName)) {
                    if (complexChildren.containsKey(columnName)) {
                        expandedCellRows.put(columnName, record.flattenComplexChild(columnName));
                    }
                }
            }
            invalidateLayout();
        }

        /**
         * Returns the flattened child rows for an expanded complex cell.
         *
         * @param columnName the column name
         * @return the list of FlatRows, or empty list if not expanded
         */
        public List<FlatRow> getExpandedCellRows(String columnName) {
            return expandedCellRows.getOrDefault(columnName, List.of());
        }

        /**
         * Returns the set of currently expanded column names.
         *
         * @return the set of expanded column names
         */
        public Set<String> getExpandedColumns() {
            return expandedColumns;
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
     * @param depth                   the nesting depth of this table
     * @param onLayoutChangedCallback callback to invoke when the layout changes
     */
    public RepeatingElementsTable(String elementName, List<XmlElement> elements,
                                   int depth, Runnable onLayoutChangedCallback) {
        this(elementName, toRecords(elements), depth, onLayoutChangedCallback, true);
    }

    /**
     * Creates a table over model-agnostic records (used by the JSON grid for arrays of
     * objects; the XML grid goes through the {@code List<XmlElement>} constructor).
     *
     * @param name                    the common name of the repeating group (tag name / property key)
     * @param records                 one record per repeating node, in document order
     * @param depth                   nesting depth of the owning row
     * @param onLayoutChangedCallback invoked when the table's layout changes (may be {@code null})
     * @return the new table
     */
    public static RepeatingElementsTable ofRecords(String name, List<? extends GridRecord> records,
                                                   int depth, Runnable onLayoutChangedCallback) {
        return new RepeatingElementsTable(name, new ArrayList<>(records), depth, onLayoutChangedCallback, true);
    }

    private static List<GridRecord> toRecords(List<XmlElement> elements) {
        List<GridRecord> records = new ArrayList<>(elements.size());
        for (XmlElement element : elements) {
            records.add(new XmlGridRecord(element));
        }
        return records;
    }

    private RepeatingElementsTable(String elementName, List<GridRecord> records,
                                   int depth, Runnable onLayoutChangedCallback, boolean fromRecords) {
        this.elementName = elementName;
        this.records = records;
        this.depth = depth;
        this.onLayoutChangedCallback = onLayoutChangedCallback;

        analyzeStructure();
        buildRows();

        // Restore sort state and user column widths from the caches, then lay out.
        restoreSortStateFromCache();
        restoreColumnWidthsFromCache();
        ensureLayout();
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
        // Per-record column keys (document order) for merging, plus the union of keys
        // actually present so stale cached columns are dropped.
        List<List<String>> perElementOrders = new ArrayList<>();
        Set<String> presentKeys = new LinkedHashSet<>();
        Map<String, GridRecord> keyOwner = new HashMap<>();
        for (GridRecord record : records) {
            List<String> keys = record.columnKeys();
            perElementOrders.add(new ArrayList<>(keys));
            for (String key : keys) {
                if (presentKeys.add(key)) {
                    keyOwner.put(key, record);
                }
            }
        }

        // Check if we have a cached column order for this element type
        List<String> cachedOrder = columnOrderCache.get(elementName);

        // Merge all element orders into a unified order respecting document order
        List<String> mergedOrder = mergeColumnOrders(perElementOrders);

        List<String> finalOrder;
        if (cachedOrder != null) {
            // Use cached order for known columns, but insert new columns at correct positions
            finalOrder = mergeCachedWithNew(cachedOrder, mergedOrder);
        } else {
            // First time - use merged order
            finalOrder = mergedOrder;
        }

        for (String key : finalOrder) {
            GridRecord owner = keyOwner.get(key);
            if (owner != null) {
                columns.add(new TableColumn(owner.columnName(key), owner.columnType(key)));
            }
        }

        // Cache the column order for future rebuilds
        columnOrderCache.put(elementName, finalOrder);
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
     * Builds table rows from the records: cell values, display-only attribute suffixes and
     * complex (expandable) children are copied for every column the record provides.
     */
    private void buildRows() {
        for (GridRecord record : records) {
            TableRow row = new TableRow(record);
            row.owner = this;
            for (TableColumn col : columns) {
                String name = col.getName();
                String value = record.values().get(name);
                if (value != null) {
                    row.getValues().put(name, value);
                }
                String suffix = record.attributeSuffixes().get(name);
                if (suffix != null) {
                    row.getAttributeSuffixes().put(name, suffix);
                }
                Object complex = record.complexChildren().get(name);
                if (complex != null) {
                    row.getComplexChildren().put(name, complex);
                }
            }
            rows.add(row);
        }
    }

    /**
     * Calculates the width of the "name portion" of a sub-row inside an expanded
     * complex cell: indentation, expand bar, icon area, label, the "(n)" child-count
     * suffix for expandable rows, and the label/value gap — everything left of the
     * value column, excluding {@link #CELL_PADDING}.
     *
     * <p>This is the single source of truth shared by the table's layout pass and the
     * canvas' cell-tree renderer. The invariant both sides rely on: <em>column width
     * &ge; CELL_PADDING * 2 + the maximum of this value over all visible sub-rows</em>,
     * so node names are never truncated.</p>
     *
     * @param row the sub-row
     * @param m   the metrics to measure with
     * @return the name-portion width in unscaled pixels
     */
    static double subRowNameColumnWidth(FlatRow row, GridMetrics m) {
        double width = row.getDepth() * GridMetrics.INDENT + GridMetrics.ICON_AREA_WIDTH;
        if (row.isExpandable()) {
            width += GridMetrics.EXPAND_BAR_WIDTH;
        }
        if (row.getLabel() != null) {
            width += m.width(row.getLabel(), GridFont.ROW);
        }
        if (row.isExpandable()) {
            String suffix = "(" + row.getChildCount() + ")";
            width += GridMetrics.CHILD_COUNT_GAP + m.width(suffix, GridFont.SMALL);
        }
        return width + GridMetrics.SUB_ROW_LABEL_VALUE_GAP;
    }

    // ==================== Layout ====================

    /**
     * The measured layout of one table cell.
     *
     * @param summary          the cell's (wrapped) summary text
     * @param suffixOnNewLine  whether the attribute suffix did not fit after the last summary line
     * @param suffixX          x offset of the suffix relative to the cell text start (when inline)
     * @param suffix           the laid-out attribute suffix (one line when inline, wrapped when on
     *                         its own lines), or {@code null} when the cell has none
     * @param summaryHeight    height of the summary block including the suffix lines, if any
     * @param nameColumnWidth  width of the sub-row name column for an expanded cell (0 otherwise)
     * @param visibleSubRows   the visible sub-rows of an expanded cell, in draw order
     * @param subRowBlocks     the wrapped value block of each visible sub-row
     * @param subRowTops       top offset of each visible sub-row relative to the sub-row area
     * @param height           total cell height (summary + sub-rows)
     */
    public record CellLayout(TextBlock summary, boolean suffixOnNewLine, double suffixX, TextBlock suffix,
                             double summaryHeight, double nameColumnWidth,
                             List<FlatRow> visibleSubRows, List<TextBlock> subRowBlocks,
                             double[] subRowTops, double height) {

        /**
         * @param yInSubRows y relative to the top of the sub-row area (below the summary)
         * @return the index into {@link #visibleSubRows()} at that y, or -1
         */
        public int subRowIndexAt(double yInSubRows) {
            if (yInSubRows < 0 || subRowBlocks.isEmpty()) {
                return -1;
            }
            int lo = 0;
            int hi = subRowBlocks.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                double top = subRowTops[mid];
                double bottom = top + subRowBlocks.get(mid).height();
                if (yInSubRows < top) {
                    hi = mid - 1;
                } else if (yInSubRows >= bottom) {
                    lo = mid + 1;
                } else {
                    return mid;
                }
            }
            return -1;
        }

        /** @return the height of the given visible sub-row */
        public double subRowHeight(int index) {
            return subRowBlocks.get(index).height();
        }
    }

    /**
     * Replaces the metrics (measurer + wrap width) and re-lays out on next query.
     *
     * @param metrics the metrics to use
     */
    public void setMetrics(GridMetrics metrics) {
        if (metrics != null && metrics != this.metrics) {
            this.metrics = metrics;
            invalidateLayout();
        }
    }

    /** @return the metrics this table lays out with */
    public GridMetrics getMetrics() {
        return metrics;
    }

    /** Marks column widths, row heights and cell layouts stale. */
    public void invalidateLayout() {
        layoutValid = false;
    }

    /**
     * Runs the layout pass if the table is stale. Rows invalidate the table through their
     * back-reference, so this is an O(1) flag check (it runs once per cell per render).
     */
    private void ensureLayout() {
        if (!layoutValid) {
            layout();
        }
    }

    /**
     * The layout pass: measured column widths (content-driven, capped at the wrap
     * width for values, user overrides win), then per-cell wrapped text blocks and
     * row heights, then the table's width/height.
     */
    private void layout() {
        GridMetrics m = metrics;
        double pad2 = CELL_PADDING * 2;
        double maxValueColumn = m.wrapWidth() + pad2;

        // -- Column pass --
        for (TableColumn col : columns) {
            String name = col.getName();
            String header = col.getDisplayName() + (isSortedBy(name) ? " \u25B2" : "");
            double headerWidth = m.width(header, GridFont.ROW_BOLD) + pad2;
            double contentWidth = 0;
            double maxNameCol = 0;

            for (TableRow row : rows) {
                double w = m.width(row.getValue(name), GridFont.ROW);
                if (row.hasComplexChild(name)) {
                    w += GridMetrics.COMPLEX_ARROW_OFFSET;
                }
                String suffix = row.getAttributeSuffix(name);
                if (suffix != null && !suffix.isEmpty()) {
                    w += GridMetrics.SUFFIX_GAP + m.width(suffix, GridFont.ROW);
                }
                contentWidth = Math.max(contentWidth, w + pad2);

                if (row.isColumnExpanded(name)) {
                    double maxSubValue = 0;
                    for (FlatRow sub : row.getExpandedCellRows(name)) {
                        if (!sub.isVisible()) {
                            continue;
                        }
                        maxNameCol = Math.max(maxNameCol, subRowNameColumnWidth(sub, m));
                        if (sub.getValue() != null) {
                            maxSubValue = Math.max(maxSubValue, m.width(sub.getValue(), GridFont.ROW));
                        }
                    }
                    contentWidth = Math.max(contentWidth, pad2 + maxNameCol + maxSubValue);
                }
            }

            col.contentWidth = contentWidth;
            // Values are capped at the wrap width (they wrap); header and sub-row names never are.
            double natural = Math.max(
                    Math.max(MIN_COLUMN_WIDTH, headerWidth),
                    Math.max(Math.min(contentWidth, maxValueColumn), pad2 + maxNameCol));
            col.setWidth(col.userWidth != null ? Math.max(col.userWidth, MIN_COLUMN_WIDTH) : natural);
        }

        // -- Row pass --
        rowTops = new double[rows.size() + 1];
        double top = 0;
        for (int r = 0; r < rows.size(); r++) {
            TableRow row = rows.get(r);
            row.cellLayouts.clear();
            double rowHeight = ROW_HEIGHT;
            for (TableColumn col : columns) {
                CellLayout cell = layoutCell(row, col, m);
                row.cellLayouts.put(col.getName(), cell);
                rowHeight = Math.max(rowHeight, cell.height());
            }
            row.layoutHeight = rowHeight;
            rowTops[r] = top;
            top += rowHeight;
        }
        rowTops[rows.size()] = top;

        double totalColWidth = 0;
        for (TableColumn col : columns) {
            totalColWidth += col.getWidth();
        }
        this.width = Math.max(MIN_TABLE_WIDTH, totalColWidth + GRID_PADDING * 2);
        this.height = expanded
                ? HEADER_HEIGHT + ROW_HEIGHT + top + GRID_PADDING
                : HEADER_HEIGHT + GRID_PADDING;
        layoutValid = true;
    }

    private CellLayout layoutCell(TableRow row, TableColumn col, GridMetrics m) {
        String name = col.getName();
        boolean complex = row.hasComplexChild(name);
        double textAvail = Math.max(GridMetrics.MIN_TEXT_WIDTH,
                col.getWidth() - CELL_PADDING * 2 - (complex ? GridMetrics.COMPLEX_ARROW_OFFSET : 0));
        TextBlock summary = TextBlock.of(m, row.getValue(name), GridFont.ROW, textAvail);

        boolean suffixOnNewLine = false;
        double suffixX = 0;
        TextBlock suffixBlock = null;
        double summaryHeight = summary.height();
        String suffix = row.getAttributeSuffix(name);
        if (suffix != null && !suffix.isEmpty()) {
            double lastLineWidth = m.width(summary.lastLine(), GridFont.ROW);
            double suffixWidth = m.width(suffix, GridFont.ROW);
            if (lastLineWidth + GridMetrics.SUFFIX_GAP + suffixWidth <= textAvail) {
                suffixX = lastLineWidth + GridMetrics.SUFFIX_GAP;
                suffixBlock = new TextBlock(List.of(suffix), suffixWidth, GridMetrics.ROW_HEIGHT);
            } else {
                // Own line(s) below the value, wrapped like the value itself.
                suffixOnNewLine = true;
                suffixBlock = TextBlock.of(m, suffix, GridFont.ROW, textAvail);
                summaryHeight += suffixBlock.lineCount() * GridMetrics.LINE_HEIGHT;
            }
        }

        List<FlatRow> visibleSubRows = List.of();
        List<TextBlock> subRowBlocks = List.of();
        double[] subRowTops = NO_TOPS;
        double nameColumnWidth = 0;
        double subRowsHeight = 0;
        if (row.isColumnExpanded(name)) {
            visibleSubRows = new ArrayList<>();
            for (FlatRow sub : row.getExpandedCellRows(name)) {
                if (sub.isVisible()) {
                    visibleSubRows.add(sub);
                    nameColumnWidth = Math.max(nameColumnWidth, subRowNameColumnWidth(sub, m));
                }
            }
            double valueAvail = Math.max(GridMetrics.MIN_TEXT_WIDTH,
                    col.getWidth() - CELL_PADDING * 2 - nameColumnWidth);
            subRowBlocks = new ArrayList<>(visibleSubRows.size());
            subRowTops = new double[visibleSubRows.size()];
            for (int i = 0; i < visibleSubRows.size(); i++) {
                subRowTops[i] = subRowsHeight;
                TextBlock block = TextBlock.of(m, visibleSubRows.get(i).getValue(), GridFont.ROW, valueAvail);
                subRowBlocks.add(block);
                subRowsHeight += block.height();
            }
        }
        return new CellLayout(summary, suffixOnNewLine, suffixX, suffixBlock, summaryHeight, nameColumnWidth,
                visibleSubRows, subRowBlocks, subRowTops, summaryHeight + subRowsHeight);
    }

    /**
     * @param row        a row of this table
     * @param columnName a column name
     * @return the measured layout of that cell (lays out first if stale)
     */
    public CellLayout getCellLayout(TableRow row, String columnName) {
        ensureLayout();
        return row.getCellLayout(columnName);
    }

    /**
     * Sets or clears a user-dragged column width. The value is floored at
     * {@link #MIN_COLUMN_WIDTH}, remembered per element name across rebuilds and
     * triggers a re-layout (text re-wraps to the new width).
     *
     * @param columnName the column
     * @param width      the new width, or {@code null} to return to automatic sizing
     */
    public void setColumnUserWidth(String columnName, Double width) {
        TableColumn col = getColumn(columnName);
        if (col == null) {
            return;
        }
        col.userWidth = width == null ? null : Math.max(width, MIN_COLUMN_WIDTH);
        Map<String, Double> overrides = columnWidthOverrideCache.computeIfAbsent(elementName, k -> new HashMap<>());
        if (col.userWidth == null) {
            overrides.remove(columnName);
            if (overrides.isEmpty()) {
                columnWidthOverrideCache.remove(elementName);
            }
        } else {
            overrides.put(columnName, col.userWidth);
        }
        invalidateLayout();
        ensureLayout();
    }

    private void restoreColumnWidthsFromCache() {
        Map<String, Double> overrides = columnWidthOverrideCache.get(elementName);
        if (overrides == null) {
            return;
        }
        for (TableColumn col : columns) {
            Double w = overrides.get(col.getName());
            if (w != null) {
                col.userWidth = w;
            }
        }
    }

    /**
     * Re-runs the layout pass (column widths, row heights, cell text).
     */
    public void recalculateColumnWidths() {
        invalidateLayout();
        ensureLayout();
    }

    /**
     * @return the height of this table (lays out first if stale): header + padding when
     * collapsed, header + column header + all rows + padding when expanded
     */
    public double calculateHeight() {
        ensureLayout();
        return height;
    }

    /**
     * @param row a row of this table
     * @return the row's height (summary line plus wrapped lines and expanded sub-rows)
     */
    public double calculateRowHeight(TableRow row) {
        ensureLayout();
        return row.layoutHeight;
    }

    /**
     * @param rowIndex a row index
     * @return the row's top offset relative to the first data row
     */
    public double getRowTop(int rowIndex) {
        ensureLayout();
        return rowTops[Math.max(0, Math.min(rowIndex, rows.size()))];
    }

    /**
     * Gets the absolute Y position of a row's top edge (after the table header and
     * the column header row).
     *
     * @param rowIndex the zero-based index of the row
     * @return the Y coordinate of the row's top edge in pixels
     */
    public double getRowY(int rowIndex) {
        return y + HEADER_HEIGHT + ROW_HEIGHT + getRowTop(rowIndex);
    }

    /**
     * Gets the row index at an absolute Y position (ignores the expanded flag).
     *
     * @param py the Y coordinate to test
     * @return the zero-based row index, or -1 if in the header area or outside
     */
    public int getRowIndexAtY(double py) {
        return getRowIndexAtDataOffset(py - (y + HEADER_HEIGHT + ROW_HEIGHT));
    }

    /**
     * @param rel y relative to the top of the first data row
     * @return the zero-based row index at that offset, or -1 if outside the rows
     */
    public int getRowIndexAtDataOffset(double rel) {
        ensureLayout();
        if (rel < 0 || rel >= rowTops[rows.size()]) {
            return -1;
        }
        int lo = 0;
        int hi = rows.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (rel < rowTops[mid]) {
                hi = mid - 1;
            } else if (rel >= rowTops[mid + 1]) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    /**
     * @param availableWidth unused (tables are never shrunk to fit; they scroll)
     * @return the table width (lays out first if stale)
     */
    public double calculateWidth(double availableWidth) {
        ensureLayout();
        return width;
    }

    /**
     * Finds the column whose right edge is within {@code tolerance} of {@code px}
     * (the drag handle for resizing).
     *
     * @param px        absolute x coordinate
     * @param tolerance hit tolerance in pixels
     * @return the column index whose right edge was hit, or -1
     */
    public int getColumnSeparatorAt(double px, double tolerance) {
        ensureLayout();
        double edge = x + GRID_PADDING;
        for (int i = 0; i < columns.size(); i++) {
            edge += columns.get(i).getWidth();
            if (Math.abs(px - edge) <= tolerance) {
                return i;
            }
        }
        return -1;
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
     * Gets the row index at an absolute Y coordinate, accounting for variable row heights.
     *
     * @param py the Y coordinate to test
     * @return the zero-based row index, or -1 if collapsed, in the header area or outside
     */
    public int getRowIndexAt(double py) {
        return expanded ? getRowIndexAtY(py) : -1;
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
    public String getElementName() {
        return elementName;
    }

    /**
     * Returns the list of XML elements displayed in this table.
     *
     * @return the list of elements
     */
    public List<XmlElement> getElements() {
        List<XmlElement> elements = new ArrayList<>(records.size());
        for (GridRecord record : records) {
            if (record.node() instanceof XmlElement el) {
                elements.add(el);
            }
        }
        return elements;
    }

    /**
     * Returns the records displayed in this table (one per repeating node).
     *
     * @return the records, in row order
     */
    public List<GridRecord> getRecords() {
        return records;
    }

    /**
     * Returns the model nodes displayed in this table (XmlElement / JsonObject).
     *
     * @return the nodes, in row order
     */
    public List<Object> getNodes() {
        List<Object> nodes = new ArrayList<>(records.size());
        for (GridRecord record : records) {
            nodes.add(record.node());
        }
        return nodes;
    }

    /**
     * Returns the number of elements in this table.
     *
     * @return the element count
     */
    public int getElementCount() {
        return records.size();
    }

    /**
     * Returns the list of columns in this table.
     *
     * @return the list of table columns
     */
    public List<TableColumn> getColumns() {
        return columns;
    }

    /**
     * Returns the list of rows in this table.
     *
     * @return the list of table rows
     */
    public List<TableRow> getRows() {
        return rows;
    }

    /**
     * Returns the nesting depth of this table.
     *
     * @return the depth level
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Returns the X coordinate of this table.
     *
     * @return the X position in pixels
     */
    public double getX() {
        return x;
    }

    /**
     * Sets the X coordinate of this table.
     *
     * @param x the new X position in pixels
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Returns the Y coordinate of this table.
     *
     * @return the Y position in pixels
     */
    public double getY() {
        return y;
    }

    /**
     * Sets the Y coordinate of this table.
     *
     * @param y the new Y position in pixels
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Returns the width of this table.
     *
     * @return the width in pixels
     */
    public double getWidth() {
        return width;
    }

    /**
     * Sets the width of this table.
     *
     * @param width the new width in pixels
     */
    public void setWidth(double width) {
        this.width = width;
    }

    /**
     * Returns the height of this table.
     *
     * @return the height in pixels
     */
    public double getHeight() {
        return height;
    }

    /**
     * Sets the height of this table.
     *
     * @param height the new height in pixels
     */
    public void setHeight(double height) {
        this.height = height;
    }

    /**
     * Checks if this table is expanded to show all rows.
     *
     * @return true if expanded, false if collapsed
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Sets the expansion state of this table.
     * Fires a property change event for "expanded".
     *
     * @param expanded true to expand, false to collapse
     */
    public void setExpanded(boolean expanded) {
        boolean old = this.expanded;
        this.expanded = expanded;
        invalidateLayout();
        pcs.firePropertyChange("expanded", old, expanded);
    }

    /**
     * Toggles the expansion state of this table.
     */
    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    /**
     * Checks if this table is currently selected.
     *
     * @return true if selected
     */
    public boolean isSelected() {
        return selected;
    }

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
    public boolean isHovered() {
        return hovered;
    }

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
    public int getHoveredRowIndex() {
        return hoveredRowIndex;
    }

    /**
     * Sets the index of the hovered row.
     *
     * @param index the row index, or -1 if no row is hovered
     */
    public void setHoveredRowIndex(int index) {
        this.hoveredRowIndex = index;
    }

    /**
     * Returns the index of the currently hovered column.
     *
     * @return the hovered column index, or -1 if no column is hovered
     */
    public int getHoveredColumnIndex() {
        return hoveredColumnIndex;
    }

    /**
     * Sets the index of the hovered column.
     *
     * @param index the column index, or -1 if no column is hovered
     */
    public void setHoveredColumnIndex(int index) {
        this.hoveredColumnIndex = index;
    }

    /**
     * Returns the index of the currently selected row.
     *
     * @return the selected row index, or -1 if no row is selected
     */
    public int getSelectedRowIndex() {
        return selectedRowIndex;
    }

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
    public String getSortedColumnName() {
        return sortedColumnName;
    }

    /**
     * Returns true if sorting is ascending, false if descending.
     *
     * @return true for ascending sort order, false for descending
     */
    public boolean isSortAscending() {
        return sortAscending;
    }

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
        invalidateLayout(); // the sort marker widens the header

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
        TableRow row = getSelectedRow();
        return row != null ? row.getElement() : null;
    }

    /**
     * Returns the currently selected table row.
     *
     * @return the selected row, or null if no row is selected
     */
    public TableRow getSelectedRow() {
        if (selectedRowIndex >= 0 && selectedRowIndex < rows.size()) {
            return rows.get(selectedRowIndex);
        }
        return null;
    }

    /**
     * Returns the model node of the currently selected row.
     *
     * @return the selected node, or null if no row is selected
     */
    public Object getSelectedNode() {
        TableRow row = getSelectedRow();
        return row != null ? row.getNode() : null;
    }

    /**
     * Toggles expansion of a complex cell.
     *
     * <p>When a complex cell is expanded, its child elements are displayed as
     * sub-rows within the cell, increasing the row height accordingly.</p>
     *
     * @param rowIndex   the zero-based index of the row
     * @param columnName the name of the column containing the complex cell
     * @return true if the cell was successfully toggled, false if the cell
     *         does not contain complex content or the row index is invalid
     */
    public boolean toggleCellExpansion(int rowIndex, String columnName) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return false;
        }

        TableRow row = rows.get(rowIndex);
        if (!row.hasComplexChild(columnName)) {
            return false;
        }

        row.toggleColumnExpanded(columnName);
        pcs.firePropertyChange("cellExpansion", null, columnName);

        // Notify layout change so the view can recalculate
        if (onLayoutChangedCallback != null) {
            onLayoutChangedCallback.run();
        }

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
        if (col == null) {
            return false;
        }

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
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String v = value.trim();

        // Accept common date formats: ISO 8601 (2024-01-15[T...]), European (15.01.2024),
        // US (01/15/2024), and short forms (2024-01, 01/2024).
        return v.matches("\\d{4}-\\d{2}-\\d{2}(T.*)?")
                || v.matches("\\d{2}\\.\\d{2}\\.\\d{4}")
                || v.matches("\\d{2}/\\d{2}/\\d{4}")
                || v.matches("\\d{4}-\\d{2}")
                || v.matches("\\d{2}/\\d{4}");
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
     * @param depth                   the current nesting depth
     * @param onLayoutChangedCallback callback to invoke when layout changes
     * @return a map of element names to tables, containing only elements that appear 2 or more times
     */
    public static Map<String, RepeatingElementsTable> groupRepeatingElements(
            List<XmlNode> children, int depth, Runnable onLayoutChangedCallback) {

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
                    new RepeatingElementsTable(entry.getKey(), entry.getValue(), depth, onLayoutChangedCallback));
            }
        }

        return tables;
    }
}
