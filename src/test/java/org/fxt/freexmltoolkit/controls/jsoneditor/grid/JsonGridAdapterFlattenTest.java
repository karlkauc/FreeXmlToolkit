package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow.RowType;
import org.junit.jupiter.api.Test;

/**
 * Flatten rules of {@link JsonGridAdapter}: root row, scalar leaves, collapsed containers,
 * {@code [i]} array items, arrays of objects as a single table row.
 */
class JsonGridAdapterFlattenTest {

    private static List<FlatRow> flatten(String json) {
        return JsonGridAdapter.flatten(JsonNodeFactory.parse(json));
    }

    @Test
    void rootObjectIsTheExpandedDollarRow() {
        List<FlatRow> rows = flatten("{\"name\": \"Alice\"}");

        FlatRow root = rows.get(0);
        assertEquals(RowType.JSON_OBJECT, root.getType());
        assertEquals("$", root.getLabel());
        assertEquals(0, root.getDepth());
        assertTrue(root.isExpanded());
        assertEquals(1, root.getChildCount());
        assertTrue(root.isExpandable());
        assertInstanceOf(JsonObject.class, root.getModelNode());
    }

    @Test
    void scalarPropertiesBecomeTypedLeafRows() {
        List<FlatRow> rows = flatten("{\"s\": \"x\", \"n\": 42, \"b\": true, \"z\": null}");

        assertEquals(5, rows.size());
        assertRow(rows.get(1), RowType.JSON_STRING, "s", "x", 1);
        assertRow(rows.get(2), RowType.JSON_NUMBER, "n", "42", 1);
        assertRow(rows.get(3), RowType.JSON_BOOLEAN, "b", "true", 1);
        assertRow(rows.get(4), RowType.JSON_NULL, "z", "null", 1);
        for (FlatRow leaf : rows.subList(1, 5)) {
            assertTrue(leaf.isLeafWithValue());
            assertFalse(leaf.isExpandable());
            assertSame(rows.get(0), leaf.getParentRow());
        }
    }

    @Test
    void nestedContainersStartCollapsedWithChildCount() {
        List<FlatRow> rows = flatten("{\"a\": {\"x\": 1, \"y\": 2}, \"l\": [1, 2, 3]}");

        FlatRow a = rows.get(1);
        assertRow(a, RowType.JSON_OBJECT, "a", null, 1);
        assertFalse(a.isExpanded());
        assertEquals(2, a.getChildCount());
        assertRow(rows.get(2), RowType.JSON_NUMBER, "x", "1", 2);
        assertRow(rows.get(3), RowType.JSON_NUMBER, "y", "2", 2);

        FlatRow l = rows.get(4);
        assertRow(l, RowType.JSON_ARRAY, "l", null, 1);
        assertEquals(3, l.getChildCount());
        assertRow(rows.get(5), RowType.JSON_NUMBER, "[0]", "1", 2);
        assertRow(rows.get(6), RowType.JSON_NUMBER, "[1]", "2", 2);
        assertRow(rows.get(7), RowType.JSON_NUMBER, "[2]", "3", 2);
        assertSame(l, rows.get(5).getParentRow());
    }

    @Test
    void emptyContainersAreNotExpandable() {
        List<FlatRow> rows = flatten("{\"o\": {}, \"a\": []}");

        assertEquals(3, rows.size());
        assertFalse(rows.get(1).isExpandable());
        assertFalse(rows.get(2).isExpandable());
        assertNull(rows.get(1).getValue());
    }

    @Test
    void arrayOfObjectsIsOneRowWithoutItemRows() {
        List<FlatRow> rows = flatten("{\"items\": [{\"id\": 1}, {\"id\": 2}]}");

        assertEquals(2, rows.size());
        FlatRow items = rows.get(1);
        assertRow(items, RowType.JSON_ARRAY, "items", null, 1);
        assertEquals(2, items.getChildCount());
        assertTrue(JsonGridAdapter.isObjectTable((JsonArray) items.getModelNode()));
    }

    @Test
    void attachTablesBuildsTheEmbeddedTableForArraysOfObjects() {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString("{\"items\": [{\"id\": 1, \"n\": \"a\"}, {\"id\": 2, \"n\": \"b\", \"extra\": true}]}");
        JsonGridAdapter adapter = new JsonGridAdapter(context);

        List<FlatRow> rows = adapter.flatten();
        adapter.attachTables(rows, () -> { });

        FlatRow items = rows.get(1);
        assertTrue(items.hasRepeatingTable());
        var table = items.getRepeatingTable();
        assertEquals(2, table.getRows().size());
        assertEquals(List.of("id", "n", "extra"),
                table.getColumns().stream().map(c -> c.getName()).toList());
        assertEquals("b", table.getRows().get(1).getValue("n"));
        assertEquals("", table.getRows().get(0).getValue("extra"));
        assertEquals("items", table.getElementName());
        assertEquals(2, table.getElementCount());
    }

    @Test
    void mixedArraysAreExpandedAsItemRows() {
        List<FlatRow> rows = flatten("[{\"id\": 1}, 2]");

        assertEquals(RowType.JSON_ARRAY, rows.get(0).getType());
        assertEquals("$", rows.get(0).getLabel());
        assertRow(rows.get(1), RowType.JSON_OBJECT, "[0]", null, 1);
        assertRow(rows.get(2), RowType.JSON_NUMBER, "id", "1", 2);
        assertRow(rows.get(3), RowType.JSON_NUMBER, "[1]", "2", 1);
    }

    @Test
    void singleObjectArrayIsNotATable() {
        List<FlatRow> rows = flatten("{\"items\": [{\"id\": 1}]}");
        assertEquals(4, rows.size(), "$ · items · [0] · id");
        assertEquals("[0]", rows.get(2).getLabel());
        assertFalse(JsonGridAdapter.isObjectTable((JsonArray) rows.get(1).getModelNode()));
    }

    @Test
    void flattenValueUsesAVirtualAnchorLikeTheXmlGrid() {
        JsonObject object = (JsonObject) JsonNodeFactory.parse("{\"a\": 1, \"b\": {\"c\": 2}}").getRootValue();

        List<FlatRow> rows = JsonGridAdapter.flattenValue(object);

        assertEquals(3, rows.size());
        assertRow(rows.get(0), RowType.JSON_NUMBER, "a", "1", 0);
        assertRow(rows.get(1), RowType.JSON_OBJECT, "b", null, 0);
        assertFalse(rows.get(1).isExpanded());
        assertRow(rows.get(2), RowType.JSON_NUMBER, "c", "2", 1);
        FlatRow anchor = rows.get(0).getParentRow();
        assertNotNull(anchor);
        assertEquals(-1, anchor.getDepth());
        assertSame(anchor, rows.get(1).getParentRow());
        FlatRow.applyVisibility(rows);
        assertTrue(rows.get(0).isVisible());
        assertFalse(rows.get(2).isVisible(), "children of a collapsed sub-object stay hidden");
    }

    @Test
    void emptyDocumentFlattensToNoRows() {
        assertTrue(JsonGridAdapter.flatten(new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument()).isEmpty());
        assertTrue(JsonGridAdapter.flatten(null).isEmpty());
        assertFalse(new JsonGridAdapter(new JsonEditorContext()).hasDocument());
    }

    @Test
    void decorateLeafValueQuotesStringsOnly() {
        JsonGridAdapter adapter = new JsonGridAdapter(new JsonEditorContext());
        List<FlatRow> rows = flatten("{\"s\": \"x\", \"n\": 42}");
        assertEquals("\"x\"", adapter.decorateLeafValue(rows.get(1)));
        assertEquals("42", adapter.decorateLeafValue(rows.get(2)));
    }

    private static void assertRow(FlatRow row, RowType type, String label, String value, int depth) {
        assertEquals(type, row.getType(), "type of " + label);
        assertEquals(label, row.getLabel());
        assertEquals(value, row.getValue(), "value of " + label);
        assertEquals(depth, row.getDepth(), "depth of " + label);
    }
}
