package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
    private final ColumnDataType dataType;
    private final RepeatingElementsTable table;

    // For undo - the parent's children in their order before the sort
    private List<XmlNode> originalOrder;
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
        this.dataType = table.detectColumnDataType(columnName);

        // Get parent element (all elements should have the same parent)
        if (!elementsToSort.isEmpty()) {
            XmlNode parent = elementsToSort.get(0).getParent();
            this.parentElement = (parent instanceof XmlElement) ? (XmlElement) parent : null;
        } else {
            this.parentElement = null;
        }
    }

    @Override
    public boolean execute() {
        if (parentElement == null || elementsToSort.isEmpty()) {
            return false;
        }
        originalOrder = new ArrayList<>(parentElement.getChildren());

        // Each cell value is looked up once: a lookup per comparison made sorting large tables
        // quadratic and froze the UI.
        Map<XmlElement, String> values = new IdentityHashMap<>();
        for (RepeatingElementsTable.TableRow row : table.getRows()) {
            if (row.getElement() != null) {
                values.putIfAbsent(row.getElement(), row.getValue(columnName));
            }
        }
        Comparator<String> byValue = GridColumnSort.comparator(dataType, ascending);
        List<XmlElement> sortedElements = new ArrayList<>();
        for (XmlElement element : elementsToSort) {
            if (element.getParent() == parentElement) {
                sortedElements.add(element); // an element moved away meanwhile keeps no slot
            }
        }
        sortedElements.sort(Comparator.comparing(e -> values.getOrDefault(e, ""), byValue));

        parentElement.reorderChildren(withSortedSlots(originalOrder, sortedElements));
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
        Set<XmlNode> sorted = Collections.newSetFromMap(new IdentityHashMap<>());
        sorted.addAll(sortedElements);
        List<XmlNode> result = new ArrayList<>(children.size());
        int next = 0;
        for (XmlNode child : children) {
            result.add(sorted.contains(child) ? sortedElements.get(next++) : child);
        }
        return result;
    }
}
