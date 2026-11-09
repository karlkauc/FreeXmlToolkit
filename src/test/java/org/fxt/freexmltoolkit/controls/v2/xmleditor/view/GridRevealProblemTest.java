package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A validation problem is revealed in the grid: its row, its table cell, a cell inside a table cell. */
@ExtendWith(ApplicationExtension.class)
class GridRevealProblemTest {

    private static final String XML = """
            <order id="1">
              <customer>Ada</customer>
              <item><name>A</name><price>1</price></item>
              <item><name>B</name><price>x</price></item>
              <item><name>C</name><price>3</price></item>
              <box><part><no>1</no></part><part><no>2</no></part></box>
              <box><part><no>3</no></part><part><no>bad</no></part></box>
            </order>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) throws Exception {
        context = new XmlEditorContext(
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser().parse(XML));
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> c) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, c);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private XmlElement root() {
        return context.getDocument().getRootElement();
    }

    @Test
    void aPlainElementSelectsItsRow() {
        XmlElement customer = root().getChildElements("customer").getFirst();
        assertTrue(fx(() -> view.revealProblem(customer, null)));
        assertSame(customer, fx(view::getSelectedNode));
        assertNotNull(view.highlightedRow, "the row flashes");
    }

    @Test
    void aTableMemberSelectsItsCell() {
        XmlElement secondPrice = root().getChildElements("item").get(1).getChildElements("price").getFirst();
        assertTrue(fx(() -> view.revealProblem(secondPrice, null)));
        RepeatingElementsTable table = view.highlightedTable;
        assertNotNull(table, "a table cell flashes");
        assertEquals(1, view.highlightedTableRow);
        assertEquals("price", table.getColumn(view.highlightedTableColumn).getName());
        assertTrue(view.tables.inCellRange(table, 1, view.highlightedTableColumn), "the cell is selected");
    }

    @Test
    void anAttributeOfTheRootSelectsItsAttributeRow() {
        assertTrue(fx(() -> view.revealProblem(root(), "id")));
        assertNotNull(view.highlightedRow);
        assertTrue(view.highlightedRow.getType().isAttribute() && "id".equals(view.highlightedRow.getLabel()));
    }

    @Test
    void aCellInsideATableCellIsOpenedOnTheWay() {
        XmlElement bad = root().getChildElements("box").get(1).getChildElements("part").get(1)
                .getChildElements("no").getFirst();
        assertTrue(fx(() -> view.revealProblem(bad, null)));
        RepeatingElementsTable inner = view.highlightedTable;
        assertNotNull(inner, "the inner table's cell flashes");
        assertEquals(1, view.highlightedTableRow, "the second part");
        assertEquals("no", inner.getColumn(view.highlightedTableColumn).getName());
    }
}
