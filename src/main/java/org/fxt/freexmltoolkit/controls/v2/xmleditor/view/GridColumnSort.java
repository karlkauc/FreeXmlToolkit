package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;

/**
 * Pure comparison logic for sorting a {@link RepeatingElementsTable} column, shared by the
 * XML {@code SortElementsCommand} and the JSON {@code SortArrayCommand}.
 *
 * <p>Values are compared according to the detected {@link ColumnDataType}: numerically
 * (thousands separators and spaces stripped), by date (ISO, European and US formats, a
 * trailing {@code T...} time part ignored) or case-insensitively as strings. Empty and
 * {@code null} values always sort to the end, regardless of direction.</p>
 */
public final class GridColumnSort {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private GridColumnSort() {
    }

    /**
     * Returns a comparator over cell texts for the given data type and direction.
     * Reversing the comparator for descending order also reverses the empties-to-end
     * rule, matching the historical behaviour of the XML grid.
     */
    public static Comparator<String> comparator(ColumnDataType dataType, boolean ascending) {
        Comparator<String> comparator = (v1, v2) -> compareValues(v1, v2, dataType);
        return ascending ? comparator : comparator.reversed();
    }

    /** Compares two cell texts in ascending order for the given data type. */
    public static int compareValues(String v1, String v2, ColumnDataType dataType) {
        // Handle nulls/empties - always sort to end
        if (v1 == null || v1.trim().isEmpty()) {
            return 1;
        }
        if (v2 == null || v2.trim().isEmpty()) {
            return -1;
        }
        return switch (dataType) {
            case NUMERIC -> compareNumeric(v1, v2);
            case DATE -> compareDates(v1, v2);
            default -> v1.compareToIgnoreCase(v2);
        };
    }

    private static int compareNumeric(String v1, String v2) {
        try {
            double d1 = Double.parseDouble(v1.replace(",", "").replace(" ", "").trim());
            double d2 = Double.parseDouble(v2.replace(",", "").replace(" ", "").trim());
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fallback to string comparison
            return v1.compareToIgnoreCase(v2);
        }
    }

    private static int compareDates(String v1, String v2) {
        LocalDate date1 = parseDate(v1);
        LocalDate date2 = parseDate(v2);
        if (date1 == null && date2 == null) {
            return 0;
        }
        if (date1 == null) {
            return 1;
        }
        if (date2 == null) {
            return -1;
        }
        return date1.compareTo(date2);
    }

    /** Parses a date in one of the supported formats; the time part of a datetime is ignored. */
    static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
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
