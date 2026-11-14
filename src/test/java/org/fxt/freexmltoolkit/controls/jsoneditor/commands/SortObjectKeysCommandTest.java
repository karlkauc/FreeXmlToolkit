package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.StreamSupport;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.junit.jupiter.api.Test;

/** Tests {@link SortObjectKeysCommand}. */
class SortObjectKeysCommandTest {

    private static List<String> keys(JsonObject object) {
        return StreamSupport.stream(object.getPropertyKeys().spliterator(), false).toList();
    }

    @Test
    void sortsOneObjectOrRecursivelyWithOneUndo() {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString("{\"b\": {\"z\": 1, \"y\": 2}, \"a\": [{\"d\": 1, \"c\": 2}], \"B\": 0}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        JsonObject inner = (JsonObject) root.getProperty("b");
        JsonObject inArray = (JsonObject) ((JsonArray) root.getProperty("a")).getChild(0);

        assertTrue(context.executeCommand(new SortObjectKeysCommand(root, false)));
        assertEquals(List.of("a", "B", "b"), keys(root));
        assertEquals(List.of("z", "y"), keys(inner), "nested objects stay unsorted");
        assertTrue(context.undo());
        assertEquals(List.of("b", "a", "B"), keys(root));

        assertTrue(context.executeCommand(new SortObjectKeysCommand(root, true)));
        assertEquals(List.of("a", "B", "b"), keys(root));
        assertEquals(List.of("y", "z"), keys(inner));
        assertEquals(List.of("c", "d"), keys(inArray), "objects inside arrays are sorted too");
        assertTrue(context.undo());
        assertEquals(List.of("z", "y"), keys(inner));
        assertEquals(List.of("d", "c"), keys(inArray));
        assertTrue(context.getDocument().serialize(2, 0).contains("\"z\": 1"));
    }

    @Test
    void anAlreadySortedObjectIsNoChange() {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString("{\"a\": 1, \"b\": 2}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        assertFalse(new SortObjectKeysCommand(root, true).execute());
    }
}
