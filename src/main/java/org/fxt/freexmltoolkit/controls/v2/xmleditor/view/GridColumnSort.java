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

    /**
     * A cell text converted once for sorting (number or date parsed up front), carrying the item
     * it sorts. Sorting 680,000 keys parsed each value about 40 times when comparing texts.
     *
     * @param item   the sorted item (row element)
     * @param text   the cell text
     * @param empty  whether the text is empty (sorts to the end)
     * @param number the parsed number, for {@code NUMERIC}
     * @param numeric whether {@code number} was parsed (else numbers compare as text)
     * @param date   the parsed date ({@code null}: none), for {@code DATE}
     * @param type   the column's data type
     */
    public record Key<T>(T item, String text, boolean empty, double number, boolean numeric, LocalDate date,
                         ColumnDataType type) {
    }

    /** @return the sort key of a cell text for the given data type */
    public static <T> Key<T> key(T item, String text, ColumnDataType dataType) {
        boolean empty = text == null || text.trim().isEmpty();
        double number = 0;
        boolean numeric = false;
        LocalDate date = null;
        if (!empty && dataType == ColumnDataType.NUMERIC) {
            try {
                number = Double.parseDouble(text.replace(",", "").replace(" ", "").trim());
                numeric = true;
            } catch (NumberFormatException e) {
                // compares as text
            }
        } else if (!empty && dataType == ColumnDataType.DATE) {
            date = parseDate(text);
        }
        return new Key<>(item, text, empty, number, numeric, date, dataType);
    }

    /**
     * Orders keys exactly like {@link #comparator} orders their texts (empties to the end for
     * ascending, to the front for descending; unparsable numbers compare as text).
     */
    public static <T> Comparator<Key<T>> keyComparator(boolean ascending) {
        Comparator<Key<T>> comparator = GridColumnSort::compareKeys;
        return ascending ? comparator : comparator.reversed();
    }

    private static int compareKeys(Key<?> k1, Key<?> k2) {
        if (k1.empty()) {
            return 1;
        }
        if (k2.empty()) {
            return -1;
        }
        return switch (k1.type()) {
            case NUMERIC -> !k1.numeric() || !k2.numeric()
                    ? k1.text().compareToIgnoreCase(k2.text())
                    : Double.compare(k1.number(), k2.number());
            case DATE -> compareDates(k1.date(), k2.date());
            default -> k1.text().compareToIgnoreCase(k2.text());
        };
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
        return compareDates(parseDate(v1), parseDate(v2));
    }

    private static int compareDates(LocalDate date1, LocalDate date2) {
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
