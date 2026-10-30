package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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
 * Every node shows like a root node, at any depth: an opened table cell holds a full grid whose
 * repeating groups are tables again (or numbered rows), whose cells open grids again — and all of it
 * survives a rebuild.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewNestedGridTest {

    private static final String POSITION = "<Position Posnr=\"%d\"><Wert>%d</Wert></Position>";

    private static String waehrung(String iso, int positions) {
        StringBuilder sb = new StringBuilder("<Waehrung Isocode=\"" + iso + "\">");
        for (int i = 1; i <= positions; i++) {
            sb.append(POSITION.formatted(i, i * 10));
        }
        return sb.append("</Waehrung>").toString();
    }

    private static final String BELEG = "<Beleg><Land Isocode=\"AT\">" + waehrung("CZK", 4) + waehrung("EUR", 10)
            + "</Land></Beleg>";

    private static final String ORDERS = "<Order>"
            + "<Item no=\"1\"><Part p=\"a\"><Serial>1</Serial><Serial>2</Serial><Serial>3</Serial></Part>"
            + "<Part p=\"b\"><Serial>4</Serial><Serial>5</Serial><Serial>6</Serial></Part></Item>"
            + "<Item no=\"2\"><Part p=\"c\"><Serial>7</Serial><Serial>8</Serial><Serial>9</Serial></Part>"
            + "<Part p=\"d\"><Serial>10</Serial><Serial>11</Serial><Serial>12</Serial></Part></Item>"
            + "</Order>";

    private Stage stage;
    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    private void load(String xml) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            RepeatingElementsTable.clearAllCaches();
            XmlEditorContext context = new XmlEditorContext();
            context.loadDocumentFromString(xml);
            view = new XmlCanvasView(context);
            stage.setScene(new Scene(view, 1200, 700));
            stage.show();
            view.expandAll();
            return null;
        });
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private FlatRow rootOwner(String label) {
        return view.visibleRowList().stream()
                .filter(r -> label.equals(r.getLabel()) && r.hasRepeatingTable()).findFirst().orElseThrow();
    }

    private static FlatRow topRow(GridBlock grid) {
        return grid.rows().getFirst();
    }

    /** Opens a cell like a click would (the table, then the canvas re-lays out). */
    private GridBlock open(RepeatingElementsTable table, int row, String column) {
        return fx(() -> {
            RepeatingElementsTable.TableRow tableRow = table.getRows().get(row);
            if (!tableRow.isColumnExpanded(column)) {
                tableRow.toggleColumnExpanded(column);
            }
            view.render();
            return tableRow.getCellGrid(column);
        });
    }

    @Test
    void aGroupInsideACellIsATableOfAllItsMembers() {
        load(BELEG);
        RepeatingElementsTable waehrung = fx(() -> rootOwner("Waehrung").getRepeatingTable());

        GridBlock czk = open(waehrung, 0, "Position");
        GridBlock eur = open(waehrung, 1, "Position");

        FlatRow czkOwner = topRow(czk);
        assertTrue(czkOwner.hasRepeatingTable(), "the four Positions are a table");
        assertTrue(czkOwner.isExpanded());
        assertEquals(4, fx(() -> czkOwner.getRepeatingTable().getRows().size()));
        assertEquals(10, fx(() -> topRow(eur).getRepeatingTable().getRows().size()));
        assertSame(czkOwner, fx(() -> czkOwner.getRepeatingTable().getOwnerRow()));
        assertEquals(List.of("@Posnr", "Wert"), fx(() -> czkOwner.getRepeatingTable().getColumns().stream()
                .map(c -> c.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE ? "@" + c.getName() : c.getName())
                .toList()));

        // The cell is as tall as its grid; the outer table grows with it
        double before = fx(() -> waehrung.calculateHeight());
        assertTrue(before > 10 * GridMetrics.ROW_HEIGHT, "the nested 10-row table makes the outer table tall: " + before);
    }

    @Test
    void theNestedGroupSwitchesToNumberedRowsAndBack() {
        load(BELEG);
        RepeatingElementsTable waehrung = fx(() -> rootOwner("Waehrung").getRepeatingTable());
        GridBlock czk = open(waehrung, 0, "Position");

        fx(() -> {
            view.toggleTableDisplay(topRow(czk));
            return null;
        });

        // The rebuild re-opened the cell; the group now shows as <1>…<4>, each expandable
        RepeatingElementsTable rebuilt = fx(() -> rootOwner("Waehrung").getRepeatingTable());
        GridBlock rows = fx(() -> rebuilt.getRows().getFirst().getCellGrid("Position"));
        assertNotNull(rows, "the opened cell survives the rebuild");
        List<FlatRow> members = rows.rows().stream().filter(r -> r.getDepth() == 0).toList();
        assertEquals(List.of(1, 2, 3, 4), members.stream().map(FlatRow::getInstanceIndex).toList());

        // Expand <2> like a click, rebuild, and it stays expanded
        fx(() -> {
            FlatRow.toggleExpand(members.get(1), rows.rows());
            rows.invalidate();
            view.rebuild();
            return null;
        });
        GridBlock again = fx(() -> rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().getCellGrid("Position"));
        FlatRow second = again.rows().stream().filter(r -> r.getInstanceIndex() == 2).findFirst().orElseThrow();
        assertTrue(second.isExpanded(), "the expand state of a row inside a cell survives the rebuild");
        assertTrue(again.rows().stream().anyMatch(r -> "Wert".equals(r.getLabel()) && r.isVisible()));

        fx(() -> {
            view.toggleTableDisplay(second);
            return null;
        });
        GridBlock table = fx(() -> rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().getCellGrid("Position"));
        assertTrue(topRow(table).hasRepeatingTable(), "back to a table");
    }

    @Test
    void tablesInCellsInTablesInCellsKeepTheirStateAtEveryLevel() {
        load(ORDERS);
        RepeatingElementsTable items = fx(() -> rootOwner("Item").getRepeatingTable());
        assertEquals(2, fx(() -> items.getRows().size()));

        // Level 1: the Parts of item 1 are a table
        GridBlock parts = open(items, 0, "Part");
        RepeatingElementsTable partTable = fx(() -> topRow(parts).getRepeatingTable());
        assertEquals(2, fx(() -> partTable.getRows().size()));

        // Level 2: the Serials of part a are a table inside it
        GridBlock serials = open(partTable, 0, "Serial");
        RepeatingElementsTable serialTable = fx(() -> topRow(serials).getRepeatingTable());
        assertEquals(List.of("1", "2", "3"), fx(() -> serialTable.getRows().stream()
                .map(r -> r.getValue(XmlGridRecord.TEXT_KEY)).toList()));

        // Flip the innermost table, rebuild: every level comes back as it was
        fx(() -> {
            view.flipTable(topRow(serials));
            view.rebuild();
            return null;
        });
        RepeatingElementsTable items2 = fx(() -> rootOwner("Item").getRepeatingTable());
        GridBlock parts2 = fx(() -> items2.getRows().getFirst().getCellGrid("Part"));
        assertNotNull(parts2, "level 1 cell still open");
        RepeatingElementsTable partTable2 = fx(() -> topRow(parts2).getRepeatingTable());
        GridBlock serials2 = fx(() -> partTable2.getRows().getFirst().getCellGrid("Serial"));
        assertNotNull(serials2, "level 2 cell still open");
        assertTrue(fx(() -> topRow(serials2).getRepeatingTable().isFlipped()), "the inner flip is remembered");

        // Closing and reopening the level 1 cell keeps what is inside
        fx(() -> {
            items2.getRows().getFirst().toggleColumnExpanded("Part");
            items2.getRows().getFirst().toggleColumnExpanded("Part");
            return null;
        });
        GridBlock reopened = fx(() -> items2.getRows().getFirst().getCellGrid("Part"));
        assertNotNull(fx(() -> topRow(reopened).getRepeatingTable().getRows().getFirst().getCellGrid("Serial")));

        // Heights add up from the inside out
        double inner = fx(() -> topRow(serials2).getRepeatingTable().calculateHeight());
        double outer = fx(() -> items2.calculateHeight());
        assertTrue(outer > inner, "outer " + outer + " > inner " + inner);
        fx(() -> {
            view.render(); // draws all levels without failing
            return null;
        });
    }

    private void click(double x, double y) {
        fx(() -> {
            Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    false, false, false, true, false, true, null));
            return null;
        });
    }

    @Test
    void clicksReachTheCellArrowsOfNestedTables() {
        load(ORDERS);
        FlatRow itemOwner = fx(() -> rootOwner("Item"));
        RepeatingElementsTable items = fx(itemOwner::getRepeatingTable);
        double itemsTop = fx(() -> view.rowTopAt(view.visibleRowList().indexOf(itemOwner)));
        int partCol = fx(() -> items.getColumns().indexOf(items.getColumn("Part")));
        RepeatingElementsTable.CellRect partCell = fx(() -> items.cellRect(0, partCol));

        // Click the arrow of the outer cell: the Parts open as a table
        click(partCell.x() + 6, itemsTop + partCell.y() + GridMetrics.ROW_HEIGHT / 2);
        GridBlock parts = fx(() -> items.getRows().getFirst().getCellGrid("Part"));
        assertNotNull(parts, "the outer cell opened");
        FlatRow partOwner = topRow(parts);
        RepeatingElementsTable partTable = fx(partOwner::getRepeatingTable);

        // Click the arrow of the inner table's Serial cell, through the cell's grid
        double summary = fx(() -> items.getCellLayout(items.getRows().getFirst(), "Part").summaryHeight());
        double gridLeft = partCell.x() + RepeatingElementsTable.CELL_PADDING;
        double gridTop = itemsTop + partCell.y() + summary;
        int serialCol = fx(() -> partTable.getColumns().indexOf(partTable.getColumn("Serial")));
        RepeatingElementsTable.CellRect serialCell = fx(() -> partTable.cellRect(1, serialCol));
        double ownerTop = fx(() -> parts.geometry().y()[parts.geometry().index().get(partOwner)]);
        click(gridLeft + serialCell.x() + 6, gridTop + ownerTop + serialCell.y() + GridMetrics.ROW_HEIGHT / 2);

        GridBlock serials = fx(() -> partTable.getRows().get(1).getCellGrid("Serial"));
        assertNotNull(serials, "the inner cell opened by a click");
        assertEquals(3, fx(() -> topRow(serials).getRepeatingTable().getRows().size()));
    }
}
