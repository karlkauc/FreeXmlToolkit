package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Inline-edit mapping of {@link JsonGridAdapter}: values keep their type, rejected input
 * keeps the editor open (returns {@code false}), keys are renamed through the command stack.
 */
class JsonGridAdapterEditTest {

    private JsonEditorContext context;
    private JsonGridAdapter adapter;
    private List<FlatRow> rows;

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        context = new JsonEditorContext();
        context.loadDocumentFromString(
                "{\"name\": \"Alice\", \"age\": 30, \"ok\": true, \"items\": [{\"id\": 1}, {\"id\": 2, \"n\": \"b\"}]}");
        adapter = new JsonGridAdapter(context);
        rows = adapter.flatten();
        adapter.attachTables(rows, () -> { });
    }

    private FlatRow row(String label) {
        return rows.stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private JsonObject root() {
        return (JsonObject) context.getDocument().getRootValue();
    }

    @Test
    void stringEditExecutesSetPrimitiveValueCommand() {
        assertTrue(adapter.commitRowEdit(row("name"), false, "Bob"));
        assertEquals("Bob", ((JsonPrimitive) root().getProperty("name")).getValue());
        assertTrue(context.canUndo());
    }

    @Test
    void numberEditKeepsNumberType() {
        assertTrue(adapter.commitRowEdit(row("age"), false, "31"));
        JsonPrimitive age = (JsonPrimitive) root().getProperty("age");
        assertTrue(age.isNumber());
        assertEquals(BigInteger.valueOf(31), age.getValue());
    }

    @Test
    void invalidNumberIsRejectedWithoutCommand() {
        assertFalse(adapter.commitRowEdit(row("age"), false, "abc"));
        assertEquals("30", ((JsonPrimitive) root().getProperty("age")).getAsString());
        assertFalse(context.canUndo());
    }

    @Test
    void unchangedValueExecutesNoCommand() {
        assertTrue(adapter.commitRowEdit(row("age"), false, "30"));
        assertTrue(adapter.commitRowEdit(row("name"), false, "Alice"));
        assertFalse(context.canUndo());
    }

    @Test
    void booleanEditParsesTheToggleText() {
        // The BooleanToggle widget itself needs the JavaFX toolkit; see JsonGridKeyboardTest.
        assertTrue(adapter.commitRowEdit(row("ok"), false, "false"));
        assertEquals(Boolean.FALSE, ((JsonPrimitive) root().getProperty("ok")).getValue());
        assertFalse(adapter.commitRowEdit(row("ok"), false, "maybe"));
    }

    @Test
    void keyEditExecutesRenameKeyCommand() {
        assertTrue(adapter.canEditName(row("name")));
        assertFalse(adapter.canEditName(row("$")), "the root has no key");
        assertTrue(adapter.commitRowEdit(row("name"), true, "fullName"));
        assertNotNull(root().getProperty("fullName"));
        assertNull(root().getProperty("name"));
    }

    @Test
    void duplicateKeyIsRejected() {
        assertFalse(adapter.commitRowEdit(row("name"), true, "age"));
        assertNotNull(root().getProperty("name"));
        assertFalse(context.canUndo());
    }

    @Test
    void cellEditOnMissingPropertyAddsIt() {
        RepeatingElementsTable table = row("items").getRepeatingTable();
        assertNotNull(table);
        assertTrue(adapter.commitCellEdit(table, 0, "n", "a"));
        JsonObject first = (JsonObject) root().getProperty("items").getChild(0);
        assertEquals("a", ((JsonPrimitive) first.getProperty("n")).getValue());
    }

    @Test
    void cellEditOnExistingPropertyKeepsType() {
        RepeatingElementsTable table = row("items").getRepeatingTable();
        assertTrue(adapter.commitCellEdit(table, 1, "id", "20"));
        JsonObject second = (JsonObject) root().getProperty("items").getChild(1);
        assertEquals(BigInteger.valueOf(20), ((JsonPrimitive) second.getProperty("id")).getValue());
        assertFalse(adapter.commitCellEdit(table, 1, "id", "x"));
    }

    @Test
    void sortTableReordersTheArrayAndSerializesIt() {
        RepeatingElementsTable table = row("items").getRepeatingTable();
        adapter.sortTable(table, "id", false);
        assertEquals("2", ((JsonPrimitive) ((JsonObject) root().getProperty("items").getChild(0)).getProperty("id")).getAsString());
        assertTrue(table.isSortedBy("id"));
        assertFalse(table.isSortAscending());
        assertTrue(adapter.serialize().indexOf("\"id\": 2") < adapter.serialize().indexOf("\"id\": 1"));
        assertTrue(context.undo());
        assertEquals("1", ((JsonPrimitive) ((JsonObject) root().getProperty("items").getChild(0)).getProperty("id")).getAsString());
    }

    @Test
    void valueEditsAreOnlyOfferedForScalars() {
        assertTrue(adapter.canEditValue(row("name")));
        assertFalse(adapter.canEditValue(row("items")));
        assertFalse(adapter.canEditValue(row("$")));
    }
}
