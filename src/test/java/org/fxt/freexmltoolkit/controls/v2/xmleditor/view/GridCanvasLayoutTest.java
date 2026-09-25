package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the content-driven grid layout: long values wrap into taller
 * rows instead of being truncated, the content width covers every value, embedded
 * table cells wrap at the wrap width, and row tops stay consistent with row heights.
 */
@ExtendWith(ApplicationExtension.class)
class GridCanvasLayoutTest {

    private static final String LONG_VALUE = "lorem ipsum dolor sit amet ".repeat(12).trim(); // 323 chars

    private static final String XML = """
            <root>
              <long>%s</long>
              <short>ok</short>
              <items>
                <item><a>short</a><b>%s</b></item>
                <item><a>x</a><b>y</b></item>
              </items>
            </root>
            """.formatted(LONG_VALUE, LONG_VALUE);

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private FlatRow rowLabelled(String label) {
        return view.visibleRowList().stream()
                .filter(r -> label.equals(r.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no visible row " + label));
    }

    @Test
    void longValueWrapsIntoATallerRow() {
        WaitForAsyncUtils.waitForFxEvents();
        List<FlatRow> rows = view.visibleRowList();
        int longIdx = rows.indexOf(rowLabelled("long"));
        int shortIdx = rows.indexOf(rowLabelled("short"));

        TextBlock block = view.valueBlockOf(rows.get(longIdx));
        assertNotNull(block);
        assertTrue(block.lineCount() > 1, "323-char value must wrap, lines=" + block.lineCount());
        // The XML adapter decorates leaf values with quotes; nothing else may be lost.
        assertEquals("\"" + LONG_VALUE + "\"", String.join(" ", block.lines()), "no text may be lost");
        assertTrue(block.width() <= GridMetrics.DEFAULT_WRAP_WIDTH + 0.001);

        assertTrue(view.rowHeightAt(longIdx) > GridMetrics.ROW_HEIGHT, "wrapped row is taller");
        assertEquals(GridMetrics.ROW_HEIGHT, view.rowHeightAt(shortIdx), 0.001, "single-line row keeps 24 px");
    }

    @Test
    void contentWidthCoversTheWidestValue() {
        WaitForAsyncUtils.waitForFxEvents();
        FlatRow longRow = rowLabelled("long");
        TextBlock block = view.valueBlockOf(longRow);
        assertTrue(view.contentWidth() >= view.nameColumnWidthValue() + block.width(),
                "content width " + view.contentWidth() + " must cover name col "
                        + view.nameColumnWidthValue() + " + value " + block.width());
    }

    @Test
    void embeddedTableCellWrapsAtTheWrapWidth() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.expandAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        FlatRow tableRow = rowLabelled("item");
        assertTrue(tableRow.hasRepeatingTable(), "item group must be an embedded table");
        RepeatingElementsTable table = tableRow.getRepeatingTable();
        RepeatingElementsTable.TableColumn b = table.getColumn("b");
        assertTrue(b.getWidth() <= GridMetrics.DEFAULT_WRAP_WIDTH + RepeatingElementsTable.CELL_PADDING * 2 + 0.001,
                "column b must be capped at the wrap width, was " + b.getWidth());
        RepeatingElementsTable.TableRow first = table.getRows().get(0);
        assertTrue(table.getCellLayout(first, "b").summary().lineCount() > 1, "cell value wraps");
        assertTrue(table.calculateRowHeight(first) > RepeatingElementsTable.ROW_HEIGHT, "table row grows");
        assertTrue(table.getMetrics().measurer() instanceof FxTextMeasurer,
                "the canvas must hand its FX measurer to embedded tables");
    }

    @Test
    void rowTopsAreConsistentWithRowHeightsAfterExpandAll() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.expandAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        int n = view.visibleRowList().size();
        assertTrue(n >= 5);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            assertEquals(sum, view.rowTopAt(i), 0.001, "row top of row " + i);
            sum += view.rowHeightAt(i);
        }
        assertEquals(sum, view.contentHeight(), 0.001, "content height is the sum of row heights");
    }
}
