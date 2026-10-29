package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Where a drop lands in the grid: upper/lower third of a name cell, its middle, a value cell. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewDropTargetTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<root><a>1</a><box><x>3</x></box></root>");
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void positionsFollowTheVerticalThirdsAndTheValueCell() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.expandAll();
            return null;
        });
        FlatRow a = row("a");
        FlatRow box = row("box");
        double aTop = view.rowTopAt(view.visibleRowList().indexOf(a));
        double nameX = view.labelXOf(a) + 30;

        GridCanvasView.DropTarget before = view.dropTargetAt(nameX, aTop + 2);
        assertSame(a, before.row());
        assertEquals(DropPosition.BEFORE, before.position());
        assertEquals(DropPosition.AFTER, view.dropTargetAt(nameX, aTop + 22).position());
        assertEquals(DropPosition.INTO, view.dropTargetAt(nameX, aTop + 12).position(), "middle of an element");
        assertEquals(DropPosition.VALUE, view.dropTargetAt(view.valueXOf(a) + 20, aTop + 12).position());

        double boxTop = view.rowTopAt(view.visibleRowList().indexOf(box));
        GridCanvasView.DropTarget intoBox = view.dropTargetAt(view.labelXOf(box) + 30, boxTop + 30);
        assertSame(box, intoBox.row(), "the spanning cell of an expanded container");
        assertEquals(DropPosition.INTO, intoBox.position());
    }
}
