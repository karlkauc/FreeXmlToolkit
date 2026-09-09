package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    // For undo - stores original indices of elements within parent's children
    private List<Integer> originalIndices;
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

        // Store original indices for undo
        originalIndices = new ArrayList<>();
        List<XmlNode> parentChildren = parentElement.getChildren();
        for (XmlElement element : elementsToSort) {
            int index = parentChildren.indexOf(element);
            originalIndices.add(index);
        }

        // Create sorted list
        List<XmlElement> sortedElements = new ArrayList<>(elementsToSort);
        Comparator<XmlElement> comparator = createComparator();
        sortedElements.sort(comparator);

        // Reorder elements within parent
        reorderElements(sortedElements);

        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed || originalIndices == null) {
            return false;
        }

        // Restore original order by reinserting elements at their original indices
        // First, remove all elements we sorted
        for (XmlElement element : elementsToSort) {
            parentElement.removeChild(element);
        }

        // Re-add in original order at original positions
        // Sort by original index to insert in correct order
        List<Map.Entry<XmlElement, Integer>> elementIndexPairs = new ArrayList<>();
        for (int i = 0; i < elementsToSort.size(); i++) {
            elementIndexPairs.add(new AbstractMap.SimpleEntry<>(elementsToSort.get(i), originalIndices.get(i)));
        }
        elementIndexPairs.sort(Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<XmlElement, Integer> pair : elementIndexPairs) {
            int targetIndex = Math.min(pair.getValue(), parentElement.getChildCount());
            parentElement.addChild(targetIndex, pair.getKey());
        }

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
     * Reorders the elements within the parent to match the sorted order.
     */
    private void reorderElements(List<XmlElement> sortedElements) {
        // Get current indices of elements to sort
        List<Integer> currentIndices = new ArrayList<>();
        List<XmlNode> parentChildren = parentElement.getChildren();
        for (XmlElement element : elementsToSort) {
            currentIndices.add(parentChildren.indexOf(element));
        }

        // Sort indices to get the positions we'll fill
        List<Integer> sortedIndices = new ArrayList<>(currentIndices);
        Collections.sort(sortedIndices);

        // Remove all elements to sort
        for (XmlElement element : elementsToSort) {
            parentElement.removeChild(element);
        }

        // Re-add in sorted order at the sorted positions
        for (int i = 0; i < sortedElements.size(); i++) {
            int targetIndex = Math.min(sortedIndices.get(i), parentElement.getChildCount());
            parentElement.addChild(targetIndex, sortedElements.get(i));
        }
    }

    /**
     * Creates a comparator over elements that compares their cell text in this column
     * using the shared {@link GridColumnSort} rules (numeric / date / string, empties last).
     */
    private Comparator<XmlElement> createComparator() {
        return Comparator.comparing(this::getColumnValue, GridColumnSort.comparator(dataType, ascending));
    }

    /**
     * Gets the value for a column from an element.
     */
    private String getColumnValue(XmlElement element) {
        // Find the row for this element
        for (RepeatingElementsTable.TableRow row : table.getRows()) {
            if (row.getElement() == element) {
                return row.getValue(columnName);
            }
        }
        return "";
    }
}
