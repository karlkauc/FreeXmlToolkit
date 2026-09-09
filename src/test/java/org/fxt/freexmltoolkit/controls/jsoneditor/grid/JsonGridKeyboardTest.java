package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.BooleanToggle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the JSON grid's arrow-key navigation (mirror of the XML grid's
 * keyboard test): UP/DOWN walk the visible rows, RIGHT expands a collapsed object, LEFT
 * jumps to the parent; the shell search target finds values inside collapsed nodes.
 */
@ExtendWith(ApplicationExtension.class)
class JsonGridKeyboardTest {

    private static final String JSON = """
            {"name": "Alice", "address": {"city": "Vienna", "zip": "1010"}, "ok": true}
            """;

    private JsonCanvasView view;
    private JsonEditorContext context;

    @Start
    void start(Stage stage) {
        context = new JsonEditorContext();
        context.loadDocumentFromString(JSON);
        view = new JsonCanvasView(context);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private void press(KeyCode code) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                    false, false, false, false));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String selectedLabel() {
        JsonNode node = view.getSelectedNode();
        return node == null ? null : JsonGridAdapter.labelOf(node);
    }

    @Test
    void arrowKeysWalkTheVisibleRows() {
        WaitForAsyncUtils.waitForFxEvents();
        // Visible rows: $ (expanded) · name · address (collapsed) · ok
        press(KeyCode.DOWN);
        assertEquals("$", selectedLabel());
        press(KeyCode.DOWN);
        assertEquals("name", selectedLabel());
        press(KeyCode.DOWN);
        assertEquals("address", selectedLabel());
        press(KeyCode.DOWN);
        assertEquals("ok", selectedLabel(), "children of the collapsed address are skipped");
        press(KeyCode.UP);
        press(KeyCode.UP);
        assertEquals("name", selectedLabel());
    }

    @Test
    void rightArrowExpandsACollapsedObject() {
        WaitForAsyncUtils.waitForFxEvents();
        press(KeyCode.DOWN); // $
        press(KeyCode.DOWN); // name
        press(KeyCode.DOWN); // address
        assertEquals("address", selectedLabel());

        press(KeyCode.RIGHT); // expand
        press(KeyCode.DOWN);
        assertEquals("city", selectedLabel(), "reachable only after the expand");
        press(KeyCode.LEFT);  // leaf: jump to parent
        assertEquals("address", selectedLabel());
    }

    @Test
    void searchRevealsValuesInsideCollapsedNodes() {
        WaitForAsyncUtils.waitForFxEvents();
        int matches = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.findAll("vienna"));
        assertEquals(1, matches);
        assertEquals("city", selectedLabel(), "the match inside the collapsed object is revealed and selected");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.find("1010", true)));
        assertEquals("zip", selectedLabel());
    }

    @Test
    void booleanValuesEditThroughTheToggleWidget() {
        WaitForAsyncUtils.waitForFxEvents();
        FlatRow okRow = JsonGridAdapter.flatten(context.getDocument()).stream()
                .filter(r -> "ok".equals(r.getLabel())).findFirst().orElseThrow();
        var spec = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getAdapter().editSpec(okRow, false));
        assertInstanceOf(BooleanToggle.class, spec.widget());
        assertEquals("true", spec.currentValue());
        assertEquals("true", spec.widget().getValue());
    }
}
