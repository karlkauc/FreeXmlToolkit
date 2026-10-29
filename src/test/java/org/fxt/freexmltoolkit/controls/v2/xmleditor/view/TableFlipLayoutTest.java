package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Flipped table display (XMLSpy "Flip Rows/Columns"): the drawn grid is transposed — one
 * drawn column per record (numbered in the header row), one drawn row per logical column
 * (named in the first column) — while every data cell still maps to its logical row/column.
 */
class TableFlipLayoutTest {

    private RepeatingElementsTable table;

    private static XmlElement item(String id, String name, String note) {
        XmlElement el = new XmlElement("item");
        el.setAttribute("id", id);
        XmlElement n = new XmlElement("name");
        n.addChild(new XmlText(name));
        el.addChild(n);
        if (note != null) {
            XmlElement x = new XmlElement("note");
            x.addChild(new XmlText(note));
            el.addChild(x);
        }
        return el;
    }

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        table = new RepeatingElementsTable("item", List.of(
                item("1", "alpha", "a rather long note that wraps onto several lines when the column is narrow"),
                item("2", "beta", null)), 0, () -> { });
        table.setX(10);
        table.setY(0);
    }

    @Test
    void flippedGridIsTransposed() {
        assertFalse(table.isFlipped());
        assertEquals(4, table.drawnColumnCount(), "standard: numbers + 3 columns");
        assertEquals(3, table.drawnRowCount(), "standard: header + 2 rows");

        table.setFlipped(true);
        assertTrue(table.isFlipped());
        assertEquals(3, table.drawnColumnCount(), "flipped: headers + 2 records");
        assertEquals(4, table.drawnRowCount(), "flipped: numbers row + 3 columns");

        assertEquals(RepeatingElementsTable.CellKind.CORNER, table.cellAt(0, 0).kind());
        RepeatingElementsTable.DrawnCell number = table.cellAt(0, 2);
        assertEquals(RepeatingElementsTable.CellKind.ROW_NUMBER, number.kind());
        assertEquals(1, number.rowIndex(), "record 2 is drawn column 2");
        RepeatingElementsTable.DrawnCell header = table.cellAt(2, 0);
        assertEquals(RepeatingElementsTable.CellKind.COLUMN_HEADER, header.kind());
        assertEquals(1, header.columnIndex(), "column 'name' is drawn row 2");
        RepeatingElementsTable.DrawnCell data = table.cellAt(3, 1);
        assertEquals(RepeatingElementsTable.CellKind.DATA, data.kind());
        assertEquals(0, data.rowIndex());
        assertEquals(2, data.columnIndex());
    }

    @Test
    void cellRectsAgreeWithTheDrawnGeometryInBothOrientations() {
        RepeatingElementsTable.CellRect standard = table.cellRect(1, 2);
        assertEquals(table.drawnColumnX(3), standard.x(), 0.001);
        assertEquals(table.drawnRowTop(2), standard.y(), 0.001);

        table.setFlipped(true);
        RepeatingElementsTable.CellRect flipped = table.cellRect(1, 2);
        assertEquals(table.drawnColumnX(2), flipped.x(), 0.001, "record 2 = drawn column 2");
        assertEquals(table.drawnRowTop(3), flipped.y(), 0.001, "column 'note' = drawn row 3");
        assertEquals(table.drawnColumnWidth(2), flipped.width(), 0.001);
        assertEquals(table.drawnRowHeight(3), flipped.height(), 0.001);

        // The whole table is covered: last column edge = width, last row edge = height
        int lastCol = table.drawnColumnCount() - 1;
        int lastRow = table.drawnRowCount() - 1;
        assertEquals(table.getX() + table.getWidth(),
                table.drawnColumnX(lastCol) + table.drawnColumnWidth(lastCol), 0.001);
        assertEquals(table.getHeight(), table.drawnRowTop(lastRow) + table.drawnRowHeight(lastRow), 0.001);
    }

    @Test
    void flippedRowsGrowWithTheTallestCellAndHitTestingFollows() {
        table.setFlipped(true);
        RepeatingElementsTable.TableRow first = table.getRows().get(0);
        RepeatingElementsTable.CellLayout note = table.getCellLayout(first, "note");
        assertTrue(note.summary().lineCount() > 1, "the long note wraps at the record's width");
        assertEquals(note.height(), table.drawnRowHeight(3), 0.001, "the 'note' row is as tall as that cell");
        assertEquals(RepeatingElementsTable.ROW_HEIGHT, table.drawnRowHeight(2), 0.001, "short cells stay one line");

        double recordX = table.drawnColumnX(1) + 1;
        assertEquals(1, table.drawnColumnAt(recordX));
        assertEquals(3, table.drawnRowAt(table.drawnRowTop(3) + 1));
        assertEquals(-1, table.getColumnSeparatorAt(table.drawnColumnX(2), 4), "no resize handles when flipped");

        table.setFlipped(false);
        assertEquals(4, table.drawnColumnCount(), "flipping back restores the standard grid");
    }
}
