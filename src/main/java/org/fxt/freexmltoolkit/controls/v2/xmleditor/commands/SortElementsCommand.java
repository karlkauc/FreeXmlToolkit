package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

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

    // Date formatters for parsing
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2024-01-15
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),           // 15.01.2024
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),           // 01/15/2024
            DateTimeFormatter.ofPattern("yyyy-MM"),              // 2024-01
            DateTimeFormatter.ofPattern("dd/MM/yyyy")            // 15/01/2024
    );

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
     * Creates a comparator based on the detected data type.
     */
    private Comparator<XmlElement> createComparator() {
        Comparator<XmlElement> comparator = (e1, e2) -> {
            String v1 = getColumnValue(e1);
            String v2 = getColumnValue(e2);

            // Handle nulls/empties - always sort to end
            if (v1 == null || v1.trim().isEmpty()) return 1;
            if (v2 == null || v2.trim().isEmpty()) return -1;

            return switch (dataType) {
                case NUMERIC -> compareNumeric(v1, v2);
                case DATE -> compareDates(v1, v2);
                default -> v1.compareToIgnoreCase(v2);
            };
        };

        return ascending ? comparator : comparator.reversed();
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

    /**
     * Compares two numeric string values.
     */
    private int compareNumeric(String v1, String v2) {
        try {
            double d1 = Double.parseDouble(v1.replace(",", "").replace(" ", "").trim());
            double d2 = Double.parseDouble(v2.replace(",", "").replace(" ", "").trim());
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fallback to string comparison
            return v1.compareToIgnoreCase(v2);
        }
    }

    /**
     * Compares two date string values.
     */
    private int compareDates(String v1, String v2) {
        LocalDate date1 = parseDate(v1);
        LocalDate date2 = parseDate(v2);

        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return 1;
        if (date2 == null) return -1;

        return date1.compareTo(date2);
    }

    /**
     * Attempts to parse a date string using multiple formats.
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        String v = value.trim();

        // Handle datetime by extracting date part
        if (v.contains("T")) {
            v = v.substring(0, v.indexOf("T"));
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        return null;
    }
}
