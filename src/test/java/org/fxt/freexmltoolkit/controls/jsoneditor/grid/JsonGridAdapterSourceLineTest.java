package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.junit.jupiter.api.Test;

/** Source lines of grid rows (validation markers): {@link JsonGridAdapter#sourceLine}. */
class JsonGridAdapterSourceLineTest {

    private static final String TEXT = """
            {
              "name": "x",
              "nested": {
                "deep": 1
              },
              "list": [
                {"a": 1},
                {"a": 2}
              ]
            }
            """;

    private static FlatRow row(List<FlatRow> rows, String label) {
        return rows.stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void rowsKnowTheirLineInTheSourceText() {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString(TEXT);
        JsonGridAdapter adapter = new JsonGridAdapter(context);
        List<FlatRow> rows = adapter.flatten();
        assertEquals(0, adapter.sourceLine(row(rows, "name")), "no source text yet");

        adapter.setSourceText(TEXT);
        assertEquals(1, adapter.sourceLine(rows.get(0)));
        assertEquals(2, adapter.sourceLine(row(rows, "name")));
        assertEquals(3, adapter.sourceLine(row(rows, "nested")));
        FlatRow list = row(rows, "list");
        assertArrayEquals(new int[] {7, 8}, adapter.memberSourceLines(list));
    }

    @Test
    void anEditDropsTheSourceLines() {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString(TEXT);
        JsonGridAdapter adapter = new JsonGridAdapter(context);
        adapter.setSourceText(TEXT);
        List<FlatRow> rows = adapter.flatten();
        FlatRow name = row(rows, "name");
        assertEquals(2, adapter.sourceLine(name));

        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        assertTrue(adapter.commitRowEdit(name, false, "y"));
        assertEquals("y", ((JsonPrimitive) root.getProperty("name")).getValue());
        assertEquals(0, adapter.sourceLine(name), "markers must not point at stale lines after an edit");
    }
}
