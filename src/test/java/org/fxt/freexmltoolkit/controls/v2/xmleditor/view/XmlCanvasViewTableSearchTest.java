package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** The grid search also finds values inside embedded tables and selects the matching cell. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewTableSearchTest {

    private static final String XML = """
            <root>
              <items>
                <item><a>alpha</a><b>one</b></item>
                <item><a>beta</a><b>zeta</b></item>
                <item><a>zeta</a><b>three</b></item>
              </items>
            </root>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> c) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, c);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    @Test
    void tableCellsAreSearchedAndTheHitCellIsSelected() {
        int count = fx(() -> view.findAll("zeta"));
        assertEquals(2, count, "both table cells containing zeta");
        assertEquals("zeta\n", fx(view::selectedCellsAsTsv), "the first hit (row 2, column b) is selected");
        assertTrue(view.visibleRowList().stream().anyMatch(r -> "item".equals(r.getLabel()) && r.isExpanded()),
                "the collapsed group was expanded to show the hit");

        fx(() -> view.find("zeta", true));
        assertEquals("zeta\n", fx(view::selectedCellsAsTsv), "second hit: row 3, column a");
        fx(() -> view.find("zeta", true));
        assertEquals("zeta\n", fx(view::selectedCellsAsTsv), "wraps around");
    }

    @Test
    void pureCellSearchHelperReportsCoordinates() {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        List<FlatRow> rows = adapter.flatten();
        adapter.attachTables(rows, () -> { });
        List<XmlCanvasSearch.Hit> hits = XmlCanvasSearch.findHits(rows, "ZETA");
        assertEquals(2, hits.size());
        assertEquals(1, hits.get(0).tableRow());
        assertEquals(1, hits.get(0).tableColumn());
        assertEquals(2, hits.get(1).tableRow());
        assertEquals(0, hits.get(1).tableColumn());
        assertTrue(XmlCanvasSearch.findHits(rows, "items").stream().noneMatch(XmlCanvasSearch.Hit::isCell));
    }
}
