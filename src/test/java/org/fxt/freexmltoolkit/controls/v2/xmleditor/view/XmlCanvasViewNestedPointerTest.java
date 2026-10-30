package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
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

/** The pointer reaches rows and tables inside opened cells: hover, cursor, separators, drag and drop. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewNestedPointerTest {

    private static String waehrung(String iso, int positions) {
        StringBuilder sb = new StringBuilder("<Waehrung Isocode=\"" + iso + "\">");
        for (int i = 1; i <= positions; i++) {
            sb.append("<Position Posnr=\"%d\"><Wert>%d</Wert></Position>".formatted(i, i * 10));
        }
        return sb.append("</Waehrung>").toString();
    }

    static final String BELEG = "<Beleg><Land Isocode=\"AT\">" + waehrung("CZK", 4) + waehrung("EUR", 3)
            + "</Land></Beleg>";

    Stage stage;
    XmlCanvasView view;
    XmlEditorContext context;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    void load(boolean positionsAsRows) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            RepeatingElementsTable.clearAllCaches();
            context = new XmlEditorContext();
            context.loadDocumentFromString(BELEG);
            view = new XmlCanvasView(context);
            stage.setScene(new Scene(view, 1200, 700));
            stage.show();
            view.expandAll();
            rootOwner().getRepeatingTable().getRows().getFirst().toggleColumnExpanded("Position");
            view.rebuild();
            if (positionsAsRows) {
                view.toggleTableDisplay(cell().rows().getFirst());
            }
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    FlatRow rootOwner() {
        return view.visibleRowList().stream()
                .filter(r -> "Waehrung".equals(r.getLabel()) && r.hasRepeatingTable()).findFirst().orElseThrow();
    }

    /** The grid of the opened Position cell of the CZK row. */
    GridBlock cell() {
        return rootOwner().getRepeatingTable().getRows().getFirst().getCellGrid("Position");
    }

    /** @return the canvas point of a point given in the cell grid's coordinates */
    double[] inCell(double x, double y) {
        return fx(() -> {
            FlatRow owner = rootOwner();
            RepeatingElementsTable waehrung = owner.getRepeatingTable();
            double top = view.rowTopAt(view.visibleRowList().indexOf(owner));
            RepeatingElementsTable.TableRow row = waehrung.getRows().getFirst();
            RepeatingElementsTable.CellRect rect = waehrung.cellRect(0,
                    waehrung.getColumns().indexOf(waehrung.getColumn("Position")));
            double summary = waehrung.getCellLayout(row, "Position").summaryHeight();
            return new double[] {rect.x() + RepeatingElementsTable.CELL_PADDING + x, top + rect.y() + summary + y};
        });
    }

    /** @return the cell grid's row with the given instance number */
    FlatRow member(int n) {
        return fx(() -> cell().rows().stream().filter(r -> r.getInstanceIndex() == n).findFirst().orElseThrow());
    }

    double topOf(FlatRow row) {
        return fx(() -> cell().geometry().y()[cell().geometry().index().get(row)]);
    }

    void mouse(EventType<MouseEvent> type, double x, double y) {
        fx(() -> {
            Event.fireEvent(view.canvasNode(), new MouseEvent(type, x, y, x, y, MouseButton.NONE, 0,
                    false, false, false, false, false, false, false, true, false, true, null));
            return null;
        });
    }

    /** A primary-button mouse event (press / drag / release / click). */
    void button(EventType<MouseEvent> type, double x, double y, int clicks) {
        fx(() -> {
            Event.fireEvent(view.canvasNode(), new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, clicks,
                    false, false, false, false, type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED,
                    false, false, true, false, true, null));
            return null;
        });
    }

    void drag(double fromX, double y, double toX) {
        button(MouseEvent.MOUSE_PRESSED, fromX, y, 1);
        button(MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, y, 1);
        button(MouseEvent.MOUSE_DRAGGED, toX, y, 1);
        button(MouseEvent.MOUSE_RELEASED, toX, y, 1);
        button(MouseEvent.MOUSE_CLICKED, toX, y, 1); // JavaFX follows a release with a click (swallowed after a drag)
        fx(() -> {
            view.render();
            return null;
        });
    }

    double nameCellWidth(FlatRow row) {
        return fx(() -> row.getValueX() - row.getLabelX());
    }

    @Test
    void hoveringARowInsideACellHighlightsThatRowNotTheHostTableRow() {
        load(true);
        FlatRow second = member(2);
        double[] p = inCell(second.getLabelX() + 40, topOf(second) + GridMetrics.ROW_HEIGHT / 2);

        mouse(MouseEvent.MOUSE_MOVED, p[0], p[1]);

        assertTrue(fx(second::isHovered), "the nested row is hovered");
        RepeatingElementsTable outer = fx(() -> rootOwner().getRepeatingTable());
        assertEquals(-1, fx(outer::getHoveredRowIndex), "the host table row is not");
        assertFalse(fx(outer::isHovered));

        // Over its expand arrow: the hand cursor
        double[] arrow = inCell(second.getLabelX() + 4, topOf(second) + GridMetrics.ROW_HEIGHT / 2);
        mouse(MouseEvent.MOUSE_MOVED, arrow[0], arrow[1]);
        assertEquals(Cursor.HAND, fx(() -> view.canvasNode().getCursor()));

        mouse(MouseEvent.MOUSE_EXITED, -1, -1);
        assertFalse(fx(second::isHovered), "leaving the canvas clears the hover");
    }

    @Test
    void hoveringANestedTableHighlightsItsRow() {
        load(false);
        FlatRow owner = fx(() -> cell().rows().getFirst());
        RepeatingElementsTable positions = fx(owner::getRepeatingTable);
        RepeatingElementsTable.CellRect wert = fx(() -> positions.cellRect(2,
                positions.getColumns().indexOf(positions.getColumn("Wert"))));
        double[] p = inCell(wert.x() + 20, topOf(owner) + wert.y() + GridMetrics.ROW_HEIGHT / 2);

        mouse(MouseEvent.MOUSE_MOVED, p[0], p[1]);

        assertEquals(2, fx(positions::getHoveredRowIndex));
        assertEquals(-1, fx(() -> rootOwner().getRepeatingTable().getHoveredRowIndex()));
        fx(() -> {
            view.render();
            return null;
        });
    }

    @Test
    void theNameColumnOfABlockInsideACellIsDraggedAndRemembered() {
        load(true);
        FlatRow second = member(2);
        double before = nameCellWidth(second);
        double rootBefore = fx(() -> nameCellWidth(rootOwner()));
        double[] separator = inCell(second.getValueX(), topOf(second) + GridMetrics.ROW_HEIGHT / 2);

        mouse(MouseEvent.MOUSE_MOVED, separator[0], separator[1]);
        assertEquals(Cursor.H_RESIZE, fx(() -> view.canvasNode().getCursor()), "the separator is found in the cell");
        drag(separator[0], separator[1], separator[0] + 60);

        assertEquals(before + 60, nameCellWidth(member(2)), 1.0, "the cell's top block is wider");
        assertEquals(rootBefore, fx(() -> nameCellWidth(rootOwner())), 0.01, "the root block is not");

        fx(() -> {
            view.rebuild();
            return null;
        });
        assertEquals(before + 60, nameCellWidth(member(2)), 1.0, "the width survives a rebuild");

        // A double-click on the separator returns to the automatic width
        double[] moved = inCell(member(2).getValueX(), topOf(member(2)) + GridMetrics.ROW_HEIGHT / 2);
        button(MouseEvent.MOUSE_CLICKED, moved[0], moved[1], 2);
        fx(() -> {
            view.render();
            return null;
        });
        assertEquals(before, nameCellWidth(member(2)), 1.0);
    }

    @Test
    void columnsOfANestedTableAreResizedAndOptimalWidthsResetsThem() {
        load(false);
        FlatRow owner = fx(() -> cell().rows().getFirst());
        RepeatingElementsTable positions = fx(owner::getRepeatingTable);
        RepeatingElementsTable.TableColumn wert = fx(() -> positions.getColumn("Wert"));
        double before = fx(wert::getWidth);
        RepeatingElementsTable.CellRect header = fx(() -> positions.cellRect(0,
                positions.getColumns().indexOf(wert)));
        int dc = fx(() -> positions.drawnColumnAt(header.x() + 5));
        double edge = fx(() -> positions.drawnColumnX(dc) + positions.drawnColumnWidth(dc));
        double[] handle = inCell(edge, topOf(owner) + GridMetrics.ROW_HEIGHT / 2);

        drag(handle[0], handle[1], handle[0] + 50);
        assertEquals(before + 50, fx(wert::getWidth), 1.0, "the nested table's column is wider");

        fx(() -> {
            view.optimalWidths();
            return null;
        });
        assertEquals(before, fx(() -> cell().rows().getFirst().getRepeatingTable().getColumn("Wert").getWidth()), 1.0);
    }

    @Test
    void rowsInsideACellAreDragSourcesAndDropTargets() {
        load(true);
        FlatRow second = member(2);
        FlatRow fourth = member(4);
        double[] name = inCell(second.getLabelX() + 40, topOf(second) + GridMetrics.ROW_HEIGHT / 2);
        assertSame(second, fx(() -> view.dragCandidateAt(name[0], name[1])));

        double top4 = topOf(fourth);
        double[] upper = inCell(fourth.getLabelX() + 40, top4 + 2);
        double[] lower = inCell(fourth.getLabelX() + 40, top4 + GridMetrics.ROW_HEIGHT - 2);
        GridCanvasView.DropTarget before = fx(() -> view.dropTargetAt(upper[0], upper[1]));
        GridCanvasView.DropTarget after = fx(() -> view.dropTargetAt(lower[0], lower[1]));
        assertSame(fourth, before.row());
        assertEquals(DropPosition.BEFORE, before.position());
        assertEquals(DropPosition.AFTER, after.position());

        // The drop itself: move <2> after <4>; the cell stays open after the rebuild
        assertTrue(fx(() -> view.getAdapter().dropRow(second, fourth, DropPosition.AFTER, false)));
        fx(() -> {
            view.rebuild();
            return null;
        });
        var czk = context.getDocument().getRootElement().getChildElement("Land").getChildElements("Waehrung").getFirst();
        assertEquals(java.util.List.of("1", "3", "4", "2"), czk.getChildElements("Position").stream()
                .map(e -> e.getAttribute("Posnr")).toList());
        assertNotNull(fx(this::cell), "the cell is still open");
    }
}
