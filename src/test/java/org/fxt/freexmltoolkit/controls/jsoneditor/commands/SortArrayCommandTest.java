package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;
import org.junit.jupiter.api.Test;

class SortArrayCommandTest {

    private static JsonArray array(String json) {
        return (JsonArray) JsonNodeFactory.parse(json).getRootValue();
    }

    private static List<String> column(JsonArray array, String key) {
        return array.getChildren().stream()
                .map(item -> {
                    JsonNode p = ((JsonObject) item).getProperty(key);
                    return p instanceof JsonPrimitive prim && !prim.isNull() ? prim.getAsString() : "";
                }).toList();
    }

    @Test
    void sortsNumericallyAndUndoRestoresOrder() {
        JsonArray items = array("[{\"n\": 10}, {\"n\": 9}, {\"n\": 100}]");
        SortArrayCommand cmd = new SortArrayCommand(items, "n", true, ColumnDataType.NUMERIC);

        assertTrue(cmd.execute());
        assertEquals(List.of("9", "10", "100"), column(items, "n"));
        assertTrue(cmd.undo());
        assertEquals(List.of("10", "9", "100"), column(items, "n"));
        assertTrue(cmd.execute());
        assertEquals(List.of("9", "10", "100"), column(items, "n"));
    }

    @Test
    void sortsStringsCaseInsensitivelyDescending() {
        JsonArray items = array("[{\"s\": \"b\"}, {\"s\": \"C\"}, {\"s\": \"a\"}]");
        assertTrue(new SortArrayCommand(items, "s", false, ColumnDataType.STRING).execute());
        assertEquals(List.of("C", "b", "a"), column(items, "s"));
    }

    @Test
    void sortsDates() {
        JsonArray items = array("[{\"d\": \"2024-03-01\"}, {\"d\": \"2023-12-31\"}, {\"d\": \"2024-01-15\"}]");
        assertTrue(new SortArrayCommand(items, "d", true, ColumnDataType.DATE).execute());
        assertEquals(List.of("2023-12-31", "2024-01-15", "2024-03-01"), column(items, "d"));
    }

    @Test
    void missingValuesSortLast() {
        JsonArray items = array("[{\"n\": 2}, {}, {\"n\": 1}]");
        assertTrue(new SortArrayCommand(items, "n", true, ColumnDataType.NUMERIC).execute());
        assertEquals(List.of("1", "2", ""), column(items, "n"));
    }

    @Test
    void alreadySortedArrayIsNotChanged() {
        JsonArray items = array("[{\"n\": 1}, {\"n\": 2}]");
        assertFalse(new SortArrayCommand(items, "n", true, ColumnDataType.NUMERIC).execute());
        assertFalse(new SortArrayCommand(array("[{\"n\": 1}]"), "n", true, ColumnDataType.NUMERIC).execute());
    }

    @Test
    void itemsKeepTheirParentAfterSorting() {
        JsonArray items = array("[{\"n\": 2}, {\"n\": 1}]");
        new SortArrayCommand(items, "n", true, ColumnDataType.NUMERIC).execute();
        for (JsonNode item : items.getChildren()) {
            assertSame(items, item.getParent());
        }
        assertEquals("Sort by 'n' (ascending)", new SortArrayCommand(items, "n", true, null).getDescription());
    }
}
