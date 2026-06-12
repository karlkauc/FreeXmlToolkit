package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Graphic view's arrow-key navigation: UP/DOWN walk
 * the visible rows (elements AND their always-visible attribute rows), RIGHT
 * expands a collapsed element so its children become reachable.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewKeyboardTest {

    private static final String XML = """
            <root id="r1">
              <child a="1"><grand g="9">text</grand></child>
            </root>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
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

    private String selectedElementName() {
        var node = view.getSelectedNode();
        return (node instanceof XmlElement element) ? element.getName() : null;
    }

    @Test
    void arrowKeysWalkTheVisibleRows() {
        WaitForAsyncUtils.waitForFxEvents();

        // Visible rows: root · @id · child · @a (attributes show even collapsed).
        press(KeyCode.DOWN); // selects the first row
        assertEquals("root", selectedElementName());

        press(KeyCode.DOWN); // @id row (its model node is the owning element)
        assertEquals("root", selectedElementName());

        press(KeyCode.DOWN); // child element
        assertEquals("child", selectedElementName());

        press(KeyCode.UP);
        press(KeyCode.UP);
        assertEquals("root", selectedElementName());
    }

    @Test
    void rightArrowExpandsACollapsedElement() {
        WaitForAsyncUtils.waitForFxEvents();
        press(KeyCode.DOWN); // root
        press(KeyCode.DOWN); // @id
        press(KeyCode.DOWN); // child (collapsed)
        assertEquals("child", selectedElementName());

        press(KeyCode.RIGHT); // expand child
        press(KeyCode.DOWN);  // @a
        press(KeyCode.DOWN);  // grand - reachable only after the expand
        assertEquals("grand", selectedElementName());

        press(KeyCode.LEFT);  // grand is a leaf row here: jumps to the parent
        assertEquals("child", selectedElementName());
    }
}
