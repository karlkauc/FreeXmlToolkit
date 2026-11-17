package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A table inside an opened cell keeps its header pinned too, stacked below the pinned header of
 * the table around it; a click there reaches the inner table's header.
 */
@ExtendWith(ApplicationExtension.class)
class StickyNestedTableHeaderTest {

    private static String waehrung(String iso, int positions) {
        StringBuilder sb = new StringBuilder("<Waehrung Isocode=\"" + iso + "\">");
        for (int i = 1; i <= positions; i++) {
            sb.append("<Position Posnr=\"%d\"><Wert>%d</Wert></Position>".formatted(i, i * 10));
        }
        return sb.append("</Waehrung>").toString();
    }

    private static final String BELEG = "<Beleg><Land Isocode=\"AT\">" + waehrung("CZK", 150) + waehrung("EUR", 3)
            + "</Land></Beleg>";

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(BELEG);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1200, 600));
        stage.show();
        view.expandAll();
        outerOwner().getRepeatingTable().getRows().getFirst().toggleColumnExpanded("Position");
        view.rebuild();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private FlatRow outerOwner() {
        return view.visibleRowList().stream()
                .filter(r -> "Waehrung".equals(r.getLabel()) && r.hasRepeatingTable()).findFirst().orElseThrow();
    }

    @Test
    void innerHeaderSticksBelowTheOuterOne() {
        double[] geo = fx(() -> {
            FlatRow owner = outerOwner();
            RepeatingElementsTable outer = owner.getRepeatingTable();
            double outerTop = view.rowTopAt(view.visibleRowList().indexOf(owner));
            RepeatingElementsTable.TableRow row = outer.getRows().getFirst();
            RepeatingElementsTable.CellRect rect = outer.cellRect(0, outer.getColumns().indexOf(outer.getColumn("Position")));
            double gx = rect.x() + RepeatingElementsTable.CELL_PADDING;
            double gy = outerTop + rect.y() + outer.getCellLayout(row, "Position").summaryHeight();
            GridBlock cell = row.getCellGrid("Position");
            FlatRow innerOwner = cell.rows().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow();
            double innerTop = cell.geometry().y()[cell.geometry().index().get(innerOwner)];
            return new double[] {gx, gy + innerTop, outer.drawnRowHeight(0)};
        });
        RepeatingElementsTable outer = fx(() -> outerOwner().getRepeatingTable());
        RepeatingElementsTable inner = fx(() -> outer.getRows().getFirst().getCellGrid("Position").rows().stream()
                .filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow().getRepeatingTable());

        // Scroll into the middle of the inner table
        fx(() -> {
            view.scrollToForTest(geo[1] + inner.drawnRowTop(70));
            view.render();
            return null;
        });
        double sticky = fx(() -> view.scrollHeaderRows().isEmpty() ? 0 : GridCanvasView.SCROLL_HEADER_HEIGHT);
        assertEquals(sticky, view.pinnedHeaders.get(outer), 0.001, "outer header pinned below the scroll header");
        assertNotNull(view.pinnedHeaders.get(inner), "inner header pinned too");

        // A click just below the outer pinned header hits the inner table's column header
        double x = geo[0] + inner.drawnColumnX(1) + 8;
        double y = sticky + geo[2] + inner.drawnRowHeight(0) / 2;
        fx(() -> {
            Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, true, null));
            return null;
        });
        assertTrue(view.tables.isWholeColumnSelected(inner, 0), "click on the pinned inner header selects its column");
    }
}
