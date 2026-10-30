package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.DropPosition;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JSON grid: type change via the type icon and drag and drop of rows and external text. */
class JsonGridAdapterTypeAndDropTest {

    private JsonEditorContext context;
    private JsonGridAdapter adapter;

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        context = new JsonEditorContext();
        context.loadDocumentFromString("{\"name\": \"42\", \"tags\": [\"a\", \"b\"], \"meta\": {\"x\": 1}}");
        adapter = new JsonGridAdapter(context);
    }

    private FlatRow row(String label) {
        List<FlatRow> rows = adapter.flatten();
        return rows.stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private JsonObject root() {
        return (JsonObject) context.getDocument().getRootValue();
    }

    @Test
    void typeTargetsOfferEveryOtherTypeButNotForTheRoot() {
        assertEquals(List.of("Number", "Boolean", "Null", "Object", "Array"), adapter.typeTargets(row("name")));
        assertTrue(adapter.typeTargets(row(JsonGridAdapter.ROOT_LABEL)).isEmpty());
    }

    @Test
    void changeTypeConvertsTheValueAndUndoes() {
        assertTrue(adapter.changeType(row("name"), "Number"));
        JsonPrimitive name = (JsonPrimitive) root().getProperty("name");
        assertEquals(BigInteger.valueOf(42), name.getValue());

        assertTrue(adapter.changeType(row("name"), "Array"));
        assertInstanceOf(JsonArray.class, root().getProperty("name"));
        context.undo();
        context.undo();
        assertEquals("42", ((JsonPrimitive) root().getProperty("name")).getValue());
        assertFalse(adapter.changeType(row("name"), "String"), "unchanged type");
    }

    @Test
    void dragAPropertyIntoAnArrayAndAnItemBeforeAnother() {
        assertTrue(adapter.canDrag(row("name")));
        assertFalse(adapter.canDrag(row(JsonGridAdapter.ROOT_LABEL)));
        assertTrue(adapter.dropRow(row("name"), row("tags"), DropPosition.INTO, false));
        JsonArray tags = (JsonArray) root().getProperty("tags");
        assertEquals(3, tags.size());
        assertEquals("42", ((JsonPrimitive) tags.get(2)).getAsString());
        assertFalse(root().hasProperty("name"));

        // [1] "b" before [0] "a" in the same array
        assertTrue(adapter.dropRow(row("[1]"), row("[0]"), DropPosition.BEFORE, false));
        assertEquals("b", ((JsonPrimitive) tags.get(0)).getAsString());
        context.undo();
        context.undo();
        assertTrue(root().hasProperty("name"), "each move is one undo step");
    }

    @Test
    void copyDropKeepsTheSourceAndUniquifiesTheKey() {
        assertTrue(adapter.dropRow(row("x"), row("name"), DropPosition.AFTER, true));
        assertEquals(1, ((JsonObject) root().getProperty("meta")).getChildCount(), "source stays");
        assertTrue(root().hasProperty("x"));
        assertTrue(adapter.dropRow(row("x"), row("name"), DropPosition.AFTER, true));
        assertTrue(root().hasProperty("x_copy"));
    }

    @Test
    void neverIntoItselfOrItsDescendants() {
        assertFalse(adapter.canDrop(row("meta"), row("x"), DropPosition.AFTER));
        assertFalse(adapter.canDrop(row("meta"), row("meta"), DropPosition.INTO));
        assertFalse(adapter.canDrop(row("name"), row(JsonGridAdapter.ROOT_LABEL), DropPosition.BEFORE),
                "the root has no siblings");
    }

    @Test
    void droppedTextBecomesValuesOrProperties() {
        assertTrue(adapter.dropText(row("tags"), DropPosition.INTO, "{\"k\": true}"));
        assertInstanceOf(JsonObject.class, ((JsonArray) root().getProperty("tags")).get(2));

        assertTrue(adapter.dropText(row("meta"), DropPosition.INTO, "{\"x\": 5, \"y\": 6}"));
        JsonObject meta = (JsonObject) root().getProperty("meta");
        assertEquals(List.of("x", "x_copy", "y"), meta.getChildren().stream().map(n -> n.getKey()).toList(),
                "an object dropped into an object adds its properties");
        context.undo();
        assertEquals(1, meta.getChildCount(), "one undo step");

        assertTrue(adapter.dropText(row("name"), DropPosition.AFTER, "plain words"));
        assertEquals("plain words", ((JsonPrimitive) root().getProperty("value")).getValue());

        assertTrue(adapter.dropText(row("name"), DropPosition.VALUE, "hello"));
        assertEquals("hello", ((JsonPrimitive) root().getProperty("name")).getValue());
    }
}
