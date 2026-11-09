package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A press anywhere in the window closes the diagram's context menu. */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewContextMenuClickAwayTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        graph = new XsdGraphView(new XsdEditorContext(new XsdNodeFactory().fromString(XSD)));
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, MouseButton button, double x, double y) {
        return new MouseEvent(type, x, y, x, y, button, 1, false, false, false, false,
                button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY,
                true, false, true, null);
    }

    @Test
    void pressElsewhereHidesTheContextMenu() {
        fx(() -> Event.fireEvent(graph.canvasForTest(), mouse(MouseEvent.MOUSE_CLICKED, MouseButton.SECONDARY, 600, 500)));
        assertNotNull(graph.openContextMenuForTest(), "right-click shows the menu");

        fx(() -> Event.fireEvent(graph.canvasForTest(), mouse(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, 10, 10)));
        assertNull(graph.openContextMenuForTest(), "a press elsewhere closes it");
    }

    @Test
    void aSecondRightClickReplacesTheMenu() {
        fx(() -> Event.fireEvent(graph.canvasForTest(), mouse(MouseEvent.MOUSE_CLICKED, MouseButton.SECONDARY, 600, 500)));
        var first = graph.openContextMenuForTest();
        assertNotNull(first);
        fx(() -> Event.fireEvent(graph.canvasForTest(), mouse(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY, 650, 520)));
        fx(() -> Event.fireEvent(graph.canvasForTest(), mouse(MouseEvent.MOUSE_CLICKED, MouseButton.SECONDARY, 650, 520)));
        assertNotNull(graph.openContextMenuForTest(), "the new menu is open");
        org.junit.jupiter.api.Assertions.assertFalse(first.isShowing(), "the old menu is gone");
    }
}
