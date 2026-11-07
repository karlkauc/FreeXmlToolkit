package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pre-converted sort keys order cells exactly like the text comparator. */
class GridColumnSortKeyTest {

    private static final List<String> VALUES = Arrays.asList(
            "10", "2", "", "1,000", " 3 ", null, "abc", "-4.5", "2024-01-15", "15.01.2023",
            "2023-12-31T10:00", "x", "7", "  ", "NaN", "2", "B", "a");

    @ParameterizedTest
    @EnumSource(ColumnDataType.class)
    void keysSortLikeTheTextComparator(ColumnDataType type) {
        for (boolean ascending : new boolean[] {true, false}) {
            List<String> byText = new ArrayList<>(VALUES);
            byText.sort(GridColumnSort.comparator(type, ascending));

            List<GridColumnSort.Key<String>> keys = new ArrayList<>();
            for (String value : VALUES) {
                keys.add(GridColumnSort.key(value, value, type));
            }
            keys.sort(GridColumnSort.keyComparator(ascending));
            List<String> byKey = keys.stream().map(GridColumnSort.Key::item).toList();

            assertEquals(byText, byKey, type + (ascending ? " ascending" : " descending"));
        }
    }
}
