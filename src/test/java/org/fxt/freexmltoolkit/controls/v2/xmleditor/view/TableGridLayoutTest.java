package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The table's drawn grid (XMLSpy table display): drawn column 0 numbers the rows and holds the
 * corner cell, drawn row 0 is the column-header row, and every drawn cell maps back to its
 * logical row and column. No title bar, no outer padding.
 */
class TableGridLayoutTest {

    private RepeatingElementsTable table;

    private static XmlElement item(String a, String b) {
        XmlElement el = new XmlElement("item");
        el.setAttribute("id", a);
        XmlElement child = new XmlElement("b");
        child.addChild(new XmlText(b));
        el.addChild(child);
        return el;
    }

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        table = new RepeatingElementsTable("item", List.of(item("1", "x"), item("2", "y"), item("3", "z")), 0, () -> { });
        table.setX(100);
        table.setY(50);
    }

    @Test
    void drawnGridHasRowNumberColumnAndHeaderRow() {
        assertEquals(3, table.drawnColumnCount(), "row numbers + 2 columns");
        assertEquals(4, table.drawnRowCount(), "header + 3 rows");
        assertEquals(RepeatingElementsTable.CellKind.CORNER, table.cellAt(0, 0).kind());
        assertEquals(RepeatingElementsTable.CellKind.COLUMN_HEADER, table.cellAt(0, 1).kind());
        assertEquals(0, table.cellAt(0, 1).columnIndex());
        assertEquals(RepeatingElementsTable.CellKind.ROW_NUMBER, table.cellAt(2, 0).kind());
        assertEquals(1, table.cellAt(2, 0).rowIndex());
        RepeatingElementsTable.DrawnCell data = table.cellAt(3, 2);
        assertEquals(RepeatingElementsTable.CellKind.DATA, data.kind());
        assertEquals(2, data.rowIndex());
        assertEquals(1, data.columnIndex());
    }

    @Test
    void drawnGeometryStartsAtTheTableOriginWithoutPadding() {
        double numWidth = table.getRowNumberColumnWidth();
        assertTrue(numWidth >= RepeatingElementsTable.MIN_ROW_NUMBER_WIDTH);
        assertEquals(100, table.drawnColumnX(0), 0.001, "row-number column at the table's left edge");
        assertEquals(100 + numWidth, table.drawnColumnX(1), 0.001);
        assertEquals(table.getColumnX("id"), table.drawnColumnX(1), 0.001);
        assertEquals(100 + numWidth + table.getColumn("id").getWidth(), table.drawnColumnX(2), 0.001);
        assertEquals(0, table.drawnRowTop(0), 0.001, "the header row is the first drawn row");
        assertEquals(RepeatingElementsTable.ROW_HEIGHT, table.drawnRowTop(1), 0.001);
        assertEquals(RepeatingElementsTable.ROW_HEIGHT * 4, table.getHeight(), 0.001, "header + 3 single-line rows");
        assertEquals(numWidth + table.getTotalColumnsWidth(), table.getWidth(), 0.001);
    }

    @Test
    void hitTestingMapsPointsToDrawnCells() {
        double numWidth = table.getRowNumberColumnWidth();
        assertEquals(0, table.drawnColumnAt(100 + numWidth / 2));
        assertEquals(1, table.drawnColumnAt(100 + numWidth + 1));
        assertEquals(-1, table.drawnColumnAt(99));
        assertEquals(-1, table.drawnColumnAt(100 + table.getWidth() + 1));
        assertEquals(0, table.drawnRowAt(5));
        assertEquals(1, table.drawnRowAt(RepeatingElementsTable.ROW_HEIGHT + 1));
        assertEquals(3, table.drawnRowAt(RepeatingElementsTable.ROW_HEIGHT * 3 + 1));
        assertEquals(-1, table.drawnRowAt(RepeatingElementsTable.ROW_HEIGHT * 4 + 1));
        assertEquals(-1, table.drawnRowAt(-1));
        assertSame(table.getRows().get(1), table.rowOfDrawn(2, 1));
        assertNull(table.rowOfDrawn(0, 1), "header cells show no data row");
    }

    @Test
    void columnSeparatorsSkipTheRowNumberColumn() {
        double firstEdge = 100 + table.getRowNumberColumnWidth() + table.getColumn("id").getWidth();
        assertEquals(0, table.getColumnSeparatorAt(firstEdge, 4));
        assertEquals(-1, table.getColumnSeparatorAt(100 + table.getRowNumberColumnWidth(), 4),
                "the row-number column has no resize handle");
    }
}
