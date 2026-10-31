package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
        click(x, y, 1);
    }

    private void click(double x, double y, int count) {
        fx(() -> {
            Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                    MouseButton.PRIMARY, count, false, false, false, false,
                    false, false, false, true, false, true, null));
            return null;
        });
    }

    private void key(KeyCode code) {
        fx(() -> {
            Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
            return null;
        });
    }

    /** The canvas point of a cell of a table inside the Position cell of Waehrung row 0. */
    private double[] nestedPoint(java.util.function.Function<GridBlock, double[]> inGrid) {
        return fx(() -> {
            FlatRow owner = rootOwner("Waehrung");
            RepeatingElementsTable waehrung = owner.getRepeatingTable();
            double top = view.rowTopAt(view.visibleRowList().indexOf(owner));
            RepeatingElementsTable.TableRow row = waehrung.getRows().getFirst();
            RepeatingElementsTable.CellRect cell = waehrung.cellRect(0,
                    waehrung.getColumns().indexOf(waehrung.getColumn("Position")));
            double summary = waehrung.getCellLayout(row, "Position").summaryHeight();
            double[] local = inGrid.apply(row.getCellGrid("Position"));
            return new double[] {cell.x() + RepeatingElementsTable.CELL_PADDING + local[0],
                    top + cell.y() + summary + local[1]};
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

    @Test
    void aDoubleClickEditsACellOfANestedTable() {
        load(BELEG);
        fx(() -> {
            rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().toggleColumnExpanded("Position");
            view.rebuild();
            return null;
        });
        double[] wert = nestedPoint(grid -> {
            FlatRow owner = topRow(grid);
            RepeatingElementsTable positions = owner.getRepeatingTable();
            RepeatingElementsTable.CellRect rect = positions.cellRect(1,
                    positions.getColumns().indexOf(positions.getColumn("Wert")));
            double ownerTop = grid.geometry().y()[grid.geometry().index().get(owner)];
            return new double[] {rect.x() + 6, ownerTop + rect.y() + GridMetrics.ROW_HEIGHT / 2};
        });

        click(wert[0], wert[1], 2);
        javafx.scene.control.TextField field = fx(view::editFieldForTest);
        assertNotNull(field, "the nested cell is edited in place");
        assertEquals("20", fx(field::getText));
        fx(() -> {
            field.setText("999");
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });

        // The model changed, and after the rebuild the cell is open with its table again
        RepeatingElementsTable waehrung = fx(() -> rootOwner("Waehrung").getRepeatingTable());
        GridBlock grid = fx(() -> waehrung.getRows().getFirst().getCellGrid("Position"));
        assertNotNull(grid, "the cell stays open after the edit");
        assertEquals("999", fx(() -> topRow(grid).getRepeatingTable().getRows().get(1).getValue("Wert")));
    }

    @Test
    void rowsInsideACellAreSelectedAndNavigatedByKeyboard() {
        load(BELEG);
        fx(() -> {
            rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().toggleColumnExpanded("Position");
            view.rebuild();
            return null;
        });
        fx(() -> {
            GridBlock grid = rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().getCellGrid("Position");
            view.toggleTableDisplay(topRow(grid)); // the Positions as numbered rows
            return null;
        });
        double[] second = nestedPoint(grid -> {
            FlatRow row = grid.rows().stream().filter(r -> r.getInstanceIndex() == 2).findFirst().orElseThrow();
            int i = grid.geometry().index().get(row);
            return new double[] {row.getLabelX() + 40, grid.geometry().y()[i] + GridMetrics.ROW_HEIGHT / 2};
        });

        click(second[0], second[1]);
        Object selected = fx(view::getSelectedNode);
        assertTrue(selected instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                && "Position".equals(e.getName()) && "2".equals(e.getAttribute("Posnr")), "selected: " + selected);

        key(KeyCode.DOWN); // the next visible row of the cell's grid: the Posnr of <2>
        assertEquals("Posnr", fx(() -> view.multiSelectionForTest().getFirst().getLabel()));
        key(KeyCode.DOWN); // then <3>
        assertEquals(3, fx(() -> view.multiSelectionForTest().getFirst().getInstanceIndex()));

        key(KeyCode.UP);
        key(KeyCode.UP);
        assertEquals(2, fx(() -> view.multiSelectionForTest().getFirst().getInstanceIndex()));
        assertEquals(selected, fx(view::getSelectedNode));
        key(KeyCode.RIGHT); // expands <2>
        GridBlock grid = fx(() -> rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().getCellGrid("Position"));
        assertTrue(grid.rows().stream().anyMatch(r -> "Wert".equals(r.getLabel()) && r.isVisible()));

        key(KeyCode.LEFT); // collapses
        key(KeyCode.LEFT); // top of the cell's grid: back to the Waehrung cell
        Object host = fx(view::getSelectedNode);
        assertTrue(host instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                && "Waehrung".equals(e.getName()), "back at the host cell: " + host);
    }

    @Test
    void findRevealsAHitInsideAnOpenedCell() {
        load(BELEG);
        fx(() -> {
            rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().toggleColumnExpanded("Position");
            view.rebuild();
            GridBlock grid = rootOwner("Waehrung").getRepeatingTable().getRows().getFirst().getCellGrid("Position");
            view.toggleTableDisplay(topRow(grid)); // numbered rows, all collapsed
            return null;
        });

        assertTrue(fx(() -> view.find("30", true)), "the Wert of <3> inside the cell is found");

        FlatRow hit = fx(() -> view.multiSelectionForTest().getFirst());
        assertEquals("Wert", hit.getLabel());
        assertNotNull(hit.getBlock(), "a row of the cell's grid");
        assertTrue(hit.isVisible(), "its collapsed <3> was opened");
        assertEquals(3, hit.getParentRow().getInstanceIndex());
    }

    private static final String ASSETS = "<AssetMasterData>"
            + "<Asset><Name>A</Name><AssetDetails><Bond><Issuer>X</Issuer>"
            + "<Coupon><Type>f</Type><Rate>1</Rate></Coupon></Bond></AssetDetails></Asset>"
            + "<Asset><Name>B</Name><AssetDetails><Bond><Issuer>Y</Issuer>"
            + "<Coupon><Type>v</Type><Rate>2</Rate></Coupon></Bond></AssetDetails></Asset>"
            + "</AssetMasterData>";

    @Test
    void aCellWrappingOneElementShowsItsContentAndNestedArrowsToggleRowsNotTheCell() {
        load(ASSETS);
        FlatRow owner = fx(() -> rootOwner("Asset"));
        RepeatingElementsTable assets = fx(owner::getRepeatingTable);
        double tableTop = fx(() -> view.rowTopAt(view.visibleRowList().indexOf(owner)));
        int col = fx(() -> assets.getColumns().indexOf(assets.getColumn("AssetDetails")));
        RepeatingElementsTable.CellRect cell = fx(() -> assets.cellRect(1, col));

        // Open the cell with its arrow: AssetDetails only wraps Bond (named by the summary),
        // so the cell shows Bond's children directly — no second "Bond" level
        click(cell.x() + 6, tableTop + cell.y() + GridMetrics.ROW_HEIGHT / 2);
        GridBlock grid = fx(() -> assets.getRows().get(1).getCellGrid("AssetDetails"));
        assertNotNull(grid, "the cell opened");
        assertEquals("Issuer", topRow(grid).getLabel());
        assertTrue(grid.rows().stream().noneMatch(r -> "Bond".equals(r.getLabel())));
        FlatRow coupon = grid.rows().stream().filter(r -> "Coupon".equals(r.getLabel())).findFirst().orElseThrow();
        assertFalse(coupon.isExpanded());

        // Coupon's arrow (inside the cell, in the x range of the cell's arrow) toggles Coupon only
        double summary = fx(() -> assets.getCellLayout(assets.getRows().get(1), "AssetDetails").summaryHeight());
        double couponTop = fx(() -> grid.geometry().y()[grid.geometry().index().get(coupon)]);
        double x = cell.x() + RepeatingElementsTable.CELL_PADDING + coupon.getLabelX() + 4;
        double y = tableTop + cell.y() + summary + couponTop + GridMetrics.ROW_HEIGHT / 2;
        click(x, y);
        assertNotNull(fx(() -> assets.getRows().get(1).getCellGrid("AssetDetails")), "the cell stays open");
        assertTrue(fx(coupon::isExpanded), "Coupon opened");
        click(x, y);
        assertFalse(fx(coupon::isExpanded), "and closes again");
    }
}
