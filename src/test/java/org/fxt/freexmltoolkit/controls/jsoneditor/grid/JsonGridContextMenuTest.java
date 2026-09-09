package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Dialog-free paths of the JSON grid context menu: keyboard copy/paste, duplicate and
 * move go through the command stack; helpers for keys and paths.
 */
@ExtendWith(ApplicationExtension.class)
class JsonGridContextMenuTest {

    private JsonEditorContext context;
    private JsonGridContextMenu menu;
    private int refreshes;

    @Start
    void start(Stage stage) {
        context = new JsonEditorContext();
        context.loadDocumentFromString("{\"name\": \"Alice\", \"items\": [1, 2, 3]}");
        menu = new JsonGridContextMenu(context, () -> refreshes++);
        stage.setScene(new Scene(new Pane(), 200, 200));
        stage.show();
    }

    private JsonObject root() {
        return (JsonObject) context.getDocument().getRootValue();
    }

    private JsonArray items() {
        return (JsonArray) root().getProperty("items");
    }

    private void key(JsonNode node, KeyCode code, boolean ctrl, boolean shift, boolean alt) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            menu.handleKeyPress(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, false), node);
            return null;
        });
    }

    @Test
    void copyAndPasteAsSiblingDuplicatesAnArrayItem() {
        JsonNode second = items().get(1);
        key(second, KeyCode.C, true, false, false);
        assertTrue(menu.hasClipboard());
        key(second, KeyCode.V, true, false, false);

        assertEquals(4, items().size());
        assertEquals("2", ((JsonPrimitive) items().get(2)).getAsString(), "pasted right after the source item");
        assertNotSame(second, items().get(2), "copy pastes a deep copy");
        assertEquals(1, refreshes);
        assertTrue(context.canUndo());
    }

    @Test
    void cutAndPasteAsChildMovesTheItem() {
        JsonNode first = items().get(0);
        key(first, KeyCode.X, true, false, false);
        key(items(), KeyCode.V, true, true, false); // Ctrl+Shift+V = paste as child (append)

        assertEquals(3, items().size());
        assertSame(first, items().get(2), "cut moves the very node to the end");
        assertFalse(menu.hasClipboard());
    }

    @Test
    void duplicateKeepsPositionAndUniquifiesKeys() {
        key(root().getProperty("name"), KeyCode.D, true, false, false);
        assertNotNull(root().getProperty("name_copy"));
        assertEquals(1, root().indexOf(root().getProperty("name_copy")));

        key(items().get(0), KeyCode.D, true, false, false);
        assertEquals(4, items().size());
        assertEquals("1", ((JsonPrimitive) items().get(1)).getAsString());
    }

    @Test
    void altArrowsMoveWithinTheParent() {
        JsonNode first = items().get(0);
        key(first, KeyCode.DOWN, false, false, true);
        assertSame(first, items().get(1));
        key(first, KeyCode.UP, false, false, true);
        assertSame(first, items().get(0));
        key(first, KeyCode.UP, false, false, true); // already first: no-op
        assertSame(first, items().get(0));
    }

    @Test
    void helpersBuildUniqueKeysAndPathSegments() {
        assertEquals("fresh", JsonGridContextMenu.uniqueKey(root(), "fresh"));
        assertEquals("name_copy", JsonGridContextMenu.uniqueKey(root(), "name"));
        assertEquals(".plain", JsonGridContextMenu.pathSegment("plain"));
        assertEquals("['a b']", JsonGridContextMenu.pathSegment("a b"));
        assertEquals("Number", JsonGridContextMenu.typeLabel(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType.NUMBER));
        assertInstanceOf(JsonObject.class, JsonGridContextMenu.newNode(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType.OBJECT));
        assertTrue(((JsonPrimitive) JsonGridContextMenu.newNode(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType.NULL)).isNull());
    }
}
