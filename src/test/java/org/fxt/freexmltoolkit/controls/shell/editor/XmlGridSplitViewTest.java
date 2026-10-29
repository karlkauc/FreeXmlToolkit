package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The grid's split view: two canvases over one document; an edit in one shows in the other. */
@ExtendWith(ApplicationExtension.class)
class XmlGridSplitViewTest {

    private XmlGridView grid;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        context = new XmlEditorContext();
        context.loadDocumentFromString("<root><a>1</a><b>2</b></root>");
        grid = new XmlGridView();
        grid.setContext(context);
        stage.setScene(new Scene(grid, 900, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void splitShowsTwoCanvasesThatFollowEachOther() {
        fx(() -> grid.setSplit(Orientation.VERTICAL));
        GridCanvasView<?> second = grid.getSecondaryCanvas();
        assertNotNull(second);
        assertNotSame(grid.getCanvasView(), second);
        SplitPane pane = (SplitPane) grid.lookup("#grid-split-pane");
        assertNotNull(pane);
        assertEquals(Orientation.VERTICAL, pane.getOrientation());

        // An edit through the first canvas appears in the second
        var adapter = grid.getCanvasView().getAdapter();
        fx(() -> {
            var row = adapter.flatten().stream().filter(r -> "a".equals(r.getLabel())).findFirst().orElseThrow();
            adapter.commitRowEdit(row, false, "changed");
            grid.getCanvasView().refresh();
        });
        XmlElement a = context.getDocument().getRootElement().getChildElement("a");
        assertEquals("changed", a.getTextContent());
        assertTrue(second.getAdapter().flatten().stream()
                .anyMatch(r -> "a".equals(r.getLabel()) && "changed".equals(r.getValue())));

        fx(() -> grid.setSplit(Orientation.HORIZONTAL));
        assertEquals(Orientation.HORIZONTAL, ((SplitPane) grid.lookup("#grid-split-pane")).getOrientation());
        fx(() -> grid.setSplit(null));
        assertNull(grid.getSecondaryCanvas());
        assertNull(grid.lookup("#grid-split-pane"));
    }
}
