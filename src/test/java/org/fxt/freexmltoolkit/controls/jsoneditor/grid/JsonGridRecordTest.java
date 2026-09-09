package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.junit.jupiter.api.Test;

class JsonGridRecordTest {

    private static JsonObject object(String json) {
        return (JsonObject) JsonNodeFactory.parse(json).getRootValue();
    }

    @Test
    void columnsFollowPropertyOrderWithCellTexts() {
        JsonGridRecord record = new JsonGridRecord(object("{\"id\": 7, \"name\": \"x\", \"ok\": false, \"n\": null}"));

        assertEquals(List.of("id", "name", "ok", "n"), record.columnKeys());
        assertEquals("7", record.values().get("id"));
        assertEquals("x", record.values().get("name"));
        assertEquals("false", record.values().get("ok"));
        assertEquals("null", record.values().get("n"));
        assertEquals(RepeatingElementsTable.ColumnType.CHILD_ELEMENT, record.columnType("id"));
        assertEquals("id", record.columnName("id"));
        assertTrue(record.attributeSuffixes().isEmpty());
        assertTrue(record.complexChildren().isEmpty());
    }

    @Test
    void nestedContainersAreComplexCellsWithSummaries() {
        JsonGridRecord record = new JsonGridRecord(object("{\"o\": {\"a\": 1, \"b\": 2}, \"l\": [1, 2, 3]}"));

        assertEquals("{2}", record.values().get("o"));
        assertEquals("[3]", record.values().get("l"));
        assertEquals(2, record.complexChildren().size());

        List<FlatRow> rows = record.flattenComplexChild("o");
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).getLabel());
        assertTrue(record.flattenComplexChild("missing").isEmpty());
    }

    @Test
    void tableMergesDifferentKeySetsAcrossRecords() {
        JsonObject first = object("{\"a\": 1, \"c\": 3}");
        JsonObject second = object("{\"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4}");
        RepeatingElementsTable.clearAllCaches();

        RepeatingElementsTable table = RepeatingElementsTable.ofRecords("rows",
                List.of(new JsonGridRecord(first), new JsonGridRecord(second)), 0, null);

        assertEquals(List.of("a", "b", "c", "d"),
                table.getColumns().stream().map(RepeatingElementsTable.TableColumn::getName).toList());
        assertSame(first, table.getRows().get(0).getNode());
        assertNull(table.getRows().get(0).getElement(), "JSON rows carry no XmlElement");
        assertTrue(table.isColumnSortable("a"));
    }
}
