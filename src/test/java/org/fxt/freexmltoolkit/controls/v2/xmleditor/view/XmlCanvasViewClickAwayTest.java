package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A press outside the grid's inline editor commits the edit, and any press in the window
 * closes the grid's context menu.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewClickAwayTest {

    private static final String XML = """
            <root>
              <child>value</child>
              <other>text</other>
            </root>
            """;

    private XmlEditorContext context;
    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, int clicks) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, clicks,
                false, false, false, false, true, false, false, true, false, true, null);
    }

    /** A press and click on a canvas point, as the scene would deliver them. */
    private void pressAndClick(Node target, double x, double y) {
        fx(() -> Event.fireEvent(target, mouse(MouseEvent.MOUSE_PRESSED, x, y, 1)));
        fx(() -> Event.fireEvent(target, mouse(MouseEvent.MOUSE_CLICKED, x, y, 1)));
    }

    private int rowIndex(String label) {
        return view.visibleRowList().indexOf(view.visibleRowList().stream()
                .filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow());
    }

    private double rowY(int idx) {
        return view.rowTopAt(idx) + GridMetrics.ROW_HEIGHT / 2;
    }

    private TextField openEditorOn(String label) {
        WaitForAsyncUtils.waitForFxEvents();
        int idx = rowIndex(label);
        double x = view.valueXOf(view.visibleRowList().get(idx)) + 10;
        fx(() -> Event.fireEvent(view.canvasNode(), mouse(MouseEvent.MOUSE_CLICKED, x, rowY(idx), 2)));
        TextField field = view.editFieldForTest();
        assertNotNull(field, "double-click opens the inline editor");
        return field;
    }

    private String textOf(String elementName) {
        XmlElement root = context.getDocument().getRootElement();
        return root.getChildElements().stream().filter(e -> elementName.equals(e.getName()))
                .findFirst().orElseThrow().getTextContent();
    }

    @Test
    void pressOnAnotherRowCommitsTheEditAndSelectsThatRow() {
        TextField field = openEditorOn("child");
        fx(() -> field.setText("changed"));

        int otherIdx = rowIndex("other");
        double x = view.labelXOf(view.visibleRowList().get(otherIdx)) + 10;
        pressAndClick(view.canvasNode(), x, rowY(otherIdx));

        assertNull(view.editFieldForTest(), "the editor closes");
        assertEquals("changed", textOf("child"), "the value was committed");
        assertTrue(view.getSelectedNode() instanceof XmlElement e && "other".equals(e.getName()),
                "the press still selects the clicked row");
    }

    @Test
    void pressInsideTheFieldKeepsEditing() {
        TextField field = openEditorOn("child");
        fx(() -> field.setText("typing"));
        fx(() -> Event.fireEvent(field, mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 1)));

        assertNotNull(view.editFieldForTest(), "a press in the field must not end the edit");
        assertEquals("value", textOf("child"));
    }

    @Test
    void pressInTheWindowHidesTheContextMenu() {
        WaitForAsyncUtils.waitForFxEvents();
        int idx = rowIndex("child");
        double x = view.labelXOf(view.visibleRowList().get(idx)) + 10;
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, rowY(idx), x, rowY(idx),
                MouseButton.SECONDARY, 1, false, false, false, false, false, false, true, true, false, true, null)));
        XmlGridContextMenu menu = (XmlGridContextMenu) view.contextMenu();
        assertTrue(menu.isShowing(), "right-click shows the menu");

        fx(() -> Event.fireEvent(view.canvasNode(), mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 1)));
        assertFalse(menu.isShowing(), "a press elsewhere closes it");
    }
}
