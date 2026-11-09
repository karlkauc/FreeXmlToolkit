package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColumnSort;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;

/**
 * Command to sort elements in a RepeatingElementsTable by a column value.
 * Modifies the XML model by reordering child elements within their parent.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Sorts elements by the specified column value</li>
 *   <li>Supports ascending and descending order</li>
 *   <li>Detects data type (String, Numeric, Date) for smart sorting</li>
 *   <li>Can take an order computed beforehand off the FX thread ({@link #ofOrder}, {@link #sortedRows})</li>
 *   <li>Preserves positions of non-sorted siblings</li>
 *   <li>Can be undone to restore original order</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SortElementsCommand implements XmlCommand {

    private final XmlElement parentElement;
    private final List<XmlElement> elementsToSort;
    private final String columnName;
    private final boolean ascending;
    /**
     * Read by the first {@link #execute()} only, then dropped: the command lives in the undo
     * history, and holding the table kept a whole table of 680,000 rows alive behind every sort.
     */
    private RepeatingElementsTable table;
    /** The sorted order computed beforehand (off the FX thread), or {@code null} to compute it from {@link #table}. */
    private List<XmlElement> presorted;

    // For undo - the parent's children in their order before the sort
    private List<XmlNode> originalOrder;
    // For redo - the parent's children in their sorted order
    private List<XmlNode> sortedOrder;
    private boolean executed = false;

    /**
     * Constructs a command to sort elements by a column.
     *
     * @param table      the table containing the elements
     * @param columnName the column to sort by
     * @param ascending  true for ascending order, false for descending
     */
    public SortElementsCommand(RepeatingElementsTable table, String columnName, boolean ascending) {
        this.table = table;
        this.elementsToSort = new ArrayList<>(table.getElements());
        this.columnName = columnName;
        this.ascending = ascending;

        // Get parent element (all elements should have the same parent)
        if (!elementsToSort.isEmpty()) {
            XmlNode parent = elementsToSort.get(0).getParent();
            this.parentElement = (parent instanceof XmlElement) ? (XmlElement) parent : null;
        } else {
            this.parentElement = null;
        }
    }

    private SortElementsCommand(XmlElement parent, List<XmlElement> sortedElements, String columnName, boolean ascending) {
        this.parentElement = parent;
        this.elementsToSort = sortedElements;
        this.presorted = sortedElements;
        this.columnName = columnName;
        this.ascending = ascending;
    }

    /**
     * A sort whose order was computed beforehand with {@link #sortedRows} (on a worker thread):
     * executing it only puts the parent's children into that order.
     *
     * @param parent         the parent of the repeating elements
     * @param sortedElements the repeating elements in their sorted order
     */
    public static SortElementsCommand ofOrder(XmlElement parent, List<XmlElement> sortedElements,
                                              String columnName, boolean ascending) {
        return new SortElementsCommand(parent, List.copyOf(sortedElements), columnName, ascending);
    }

    /**
     * The rows of a table sorted by a column: the type (number, date, text) is detected from the
     * values, every value is converted once. Reads only the rows' values, so it may run off the FX
     * thread on a copy of the row list.
     *
     * @return the rows in their sorted order (a new list)
     */
    public static List<RepeatingElementsTable.TableRow> sortedRows(List<RepeatingElementsTable.TableRow> rows,
                                                                  String columnName, boolean ascending) {
        List<String> values = new ArrayList<>(rows.size());
        for (RepeatingElementsTable.TableRow row : rows) {
            values.add(row.getValue(columnName));
        }
        ColumnDataType type = RepeatingElementsTable.detectDataType(values);
        List<GridColumnSort.Key<RepeatingElementsTable.TableRow>> keys = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            keys.add(GridColumnSort.key(rows.get(i), values.get(i), type));
        }
        keys.sort(GridColumnSort.keyComparator(ascending));
        List<RepeatingElementsTable.TableRow> sorted = new ArrayList<>(keys.size());
        for (GridColumnSort.Key<RepeatingElementsTable.TableRow> key : keys) {
            sorted.add(key.item());
        }
        return sorted;
    }

    @Override
    public boolean execute() {
        if (parentElement == null || elementsToSort.isEmpty()) {
            return false;
        }
        originalOrder = new ArrayList<>(parentElement.getChildren());
        if (sortedOrder != null) {
            parentElement.reorderChildren(sortedOrder); // redo: the order computed the first time
            executed = true;
            return true;
        }

        if (presorted != null) {
            // an element moved away meanwhile keeps no slot
            List<XmlElement> present = new ArrayList<>(presorted.size());
            for (XmlElement element : presorted) {
                if (element.getParent() == parentElement) {
                    present.add(element);
                }
            }
            sortedOrder = withSortedSlots(originalOrder, present);
            parentElement.reorderChildren(sortedOrder);
            presorted = null;
            executed = true;
            return true;
        }

        List<XmlElement> sortedElements = new ArrayList<>(table.getRows().size());
        for (RepeatingElementsTable.TableRow row : sortedRows(table.getRows(), columnName, ascending)) {
            XmlElement element = row.getElement();
            // an element moved away meanwhile keeps no slot
            if (element != null && element.getParent() == parentElement) {
                sortedElements.add(element);
            }
        }

        sortedOrder = withSortedSlots(originalOrder, sortedElements);
        parentElement.reorderChildren(sortedOrder);
        table = null;
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed || originalOrder == null) {
            return false;
        }
        parentElement.reorderChildren(originalOrder);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        String direction = ascending ? "ascending" : "descending";
        return "Sort by '" + columnName + "' (" + direction + ")";
    }

    @Override
    public String toString() {
        return getDescription();
    }

    /**
     * The new child order: the slots the sorted elements occupy keep their positions and are
     * filled in sorted order; every other sibling (text, comments, other elements) stays put.
     */
    private List<XmlNode> withSortedSlots(List<XmlNode> children, List<XmlElement> sortedElements) {
        Set<XmlNode> sorted = Collections.newSetFromMap(new IdentityHashMap<>(sortedElements.size()));
        sorted.addAll(sortedElements);
        List<XmlNode> result = new ArrayList<>(children.size());
        int next = 0;
        for (XmlNode child : children) {
            result.add(sorted.contains(child) ? sortedElements.get(next++) : child);
        }
        return result;
    }
}
