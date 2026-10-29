package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the table cell selection and its TSV exchange: a click selects a cell,
 * Shift+click extends the range, the range copies as TSV, and a pasted block lands at the
 * top-left selected cell as one undo step.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewTableClipboardTest {

    private static final String XML = """
            <root>
              <item><a>1</a><b>x</b></item>
              <item><a>2</a><b>y</b></item>
              <item><a>3</a><b>z</b></item>
            </root>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;
    private String roundTripped;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        view.setOnDocumentModified(xml -> roundTripped = xml);
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void click(double x, double y, boolean shift) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, shift, false, false, false,
                true, false, false, true, false, true, null)));
    }

    private FlatRow owner() {
        return view.visibleRowList().stream().filter(r -> "item".equals(r.getLabel())).findFirst().orElseThrow();
    }

    private double tableTop() {
        return view.rowTopAt(view.visibleRowList().indexOf(owner()));
    }

    private void clickCell(int row, int col, boolean shift) {
        RepeatingElementsTable table = owner().getRepeatingTable();
        RepeatingElementsTable.CellRect rect = table.cellRect(row, col);
        click(rect.x() + rect.width() / 2, tableTop() + rect.y() + rect.height() / 2, shift);
    }

    @Test
    void clickAndShiftClickSelectARangeThatCopiesAsTsv() {
        fx(view::expandAll);
        assertNull(view.selectedCellsAsTsv(), "no cell selected yet");
        clickCell(0, 1, false);
        assertEquals("x\n", view.selectedCellsAsTsv());
        clickCell(1, 0, true);
        assertEquals("1\tx\n2\ty\n", view.selectedCellsAsTsv(), "Shift+click extends the range");
    }

    @Test
    void pastedBlockLandsAtTheTopLeftSelectedCellAsOneUndoStep() {
        fx(view::expandAll);
        clickCell(1, 0, false);
        fx(() -> assertTrue(view.pasteTsvIntoSelection("20\tY\n30\tZ\n40\tW\n")));

        XmlElement root = context.getDocument().getRootElement();
        assertEquals(4, root.getChildElements("item").size(), "the extra pasted row is appended");
        assertEquals("20", root.getChildElements("item").get(1).getChildElement("a").getTextContent());
        assertEquals("Z", root.getChildElements("item").get(2).getChildElement("b").getTextContent());
        assertEquals("W", root.getChildElements("item").get(3).getChildElement("b").getTextContent());
        assertTrue(roundTripped != null && roundTripped.contains("<a>40</a>"), "the edit is round-tripped");

        fx(() -> {
            context.undo();
            view.rebuild();
        });
        assertEquals(3, root.getChildElements("item").size());
        assertEquals("2", root.getChildElements("item").get(1).getChildElement("a").getTextContent());
    }
}
