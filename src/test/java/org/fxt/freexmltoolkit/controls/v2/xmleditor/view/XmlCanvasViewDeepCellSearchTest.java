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
 * The grid search finds texts inside closed complex table cells (nested elements, several
 * same-name children, attributes of cell elements) and opens the cells to reveal the hit.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewDeepCellSearchTest {

    private static final String XML = """
            <root>
              <funds>
                <fund id="F1">
                  <name>Alpha</name>
                  <detail><manager>Smith</manager><city>Vienna</city></detail>
                  <amount ccy="CHF">10</amount>
                </fund>
                <fund id="F2">
                  <name>Beta</name>
                  <detail><manager>Jones</manager><city>Graz</city></detail>
                  <amount ccy="EUR">20</amount>
                  <pos><isin>AT0001</isin></pos>
                  <pos><isin>needleISIN</isin></pos>
                </fund>
              </funds>
            </root>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1000, 700));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> c) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, c);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private static List<FlatRow> rootRows() {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        List<FlatRow> rows = adapter.flatten();
        adapter.attachTables(rows, () -> { });
        return rows;
    }

    @Test
    void closedComplexCellsAreSearchedByTheirContent() {
        List<XmlCanvasSearch.Hit> hits = XmlCanvasSearch.findHits(rootRows(), "vienna");
        assertEquals(1, hits.size());
        assertTrue(hits.getFirst().isHidden(), "the text sits inside the closed detail cell");
        assertEquals(0, hits.getFirst().tableRow());

        List<XmlCanvasSearch.Hit> isin = XmlCanvasSearch.findHits(rootRows(), "needleisin");
        assertEquals(1, XmlCanvasSearch.countMatches(isin), "inside a cell of several same-name children");

        List<XmlCanvasSearch.Hit> ccy = XmlCanvasSearch.findHits(rootRows(), "chf");
        assertEquals(1, ccy.size(), "the attribute shown next to a cell value");
        assertTrue(ccy.getFirst().isCell());
    }

    @Test
    void findOpensTheCellAndSelectsTheRowInside() {
        int count = fx(() -> view.findAll("Vienna"));
        assertEquals(1, count);
        FlatRow selected = fx(view::getSelectedRow);
        assertNotNull(selected, "a row inside the opened cell is selected");
        assertNotNull(selected.getBlock(), "the hit lives in the opened cell's grid");
        assertEquals("Vienna", selected.getValue());
    }

    @Test
    void nextWalksThroughHitsInsideSeveralCells() {
        assertEquals(2, fx(() -> view.findAll("manager")));
        assertEquals("Smith", fx(() -> view.getSelectedRow().getValue()));
        assertTrue(fx(() -> view.find("manager", true)));
        assertEquals("Jones", fx(() -> view.getSelectedRow().getValue()), "the second cell was opened");
        assertEquals(2, fx(() -> view.countMatches("manager")), "counting does not move the selection");
        assertEquals("Jones", fx(() -> view.getSelectedRow().getValue()));
        assertTrue(fx(() -> view.find("manager", true)));
        assertEquals("Smith", fx(() -> view.getSelectedRow().getValue()), "wraps around");
    }

    @Test
    void hitInsideAGroupCellIsReached() {
        assertEquals(1, fx(() -> view.findAll("needleISIN")));
        FlatRow selected = fx(view::getSelectedRow);
        boolean cellSelected = fx(() -> view.selectedCellsAsTsv().contains("needleISIN"));
        assertTrue(cellSelected || (selected != null && "needleISIN".equals(selected.getValue())),
                "the hit is selected, as a row or as a cell of the nested table");
    }
}
