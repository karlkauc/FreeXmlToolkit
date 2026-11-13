package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * "Filter…" started on a value (a simple table cell or a leaf row) opens with an expression that
 * keeps the members with that value, e.g. {@code Position[Country='AT']}.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewFilterSuggestionTest {

    private static final String XML = """
            <Fund>
              <Positions>
                <Position id="p1"><Country>AT</Country><Name>Bank's bond</Name><Assets><A>1</A></Assets></Position>
                <Position id="p2"><Country>DE</Country><Name>Other</Name><Assets><A>2</A></Assets></Position>
                <Position id="p3"><Country>AT</Country><Name>Third</Name><Assets><A>3</A></Assets></Position>
              </Positions>
              <Info><Currency>EUR</Currency></Info>
            </Fund>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 500));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private RepeatingElementsTable positionTable() {
        return row("Position").getRepeatingTable();
    }

    private void openOnTableCell(int tableRow, String column) {
        fx(() -> {
            view.showTableContextMenu(0, 0, row("Position"), positionTable(), tableRow, column);
            view.contextMenu().hide();
        });
    }

    private String initialText() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.initialFilterText(view.filterTargetRow()));
    }

    private List<String> tableIds() {
        return positionTable().getRows().stream().map(r -> r.getElement().getAttribute("id")).toList();
    }

    @Test
    void aChildElementCellSuggestsItsValue() {
        fx(view::expandAll);
        openOnTableCell(0, "Country");

        assertEquals("Positions", WaitForAsyncUtils.waitForAsyncFx(2000, view::filterTargetRow).getLabel());
        String text = initialText();
        assertEquals("Position[Country='AT']", text);

        // The suggestion is a working filter on the element holding the table
        fx(() -> view.setFilter(view.filterTargetRow(), text));
        assertEquals(List.of("p1", "p3"), tableIds());
    }

    @Test
    void anAttributeCellSuggestsTheAttribute() {
        fx(view::expandAll);
        openOnTableCell(1, "id");
        assertEquals("Position[@id='p2']", initialText());
    }

    @Test
    void apostrophesAreEscaped() {
        fx(view::expandAll);
        openOnTableCell(0, "Name");
        assertEquals("Position[Name='Bank''s bond']", initialText());
    }

    @Test
    void aComplexCellOrTheRowNumberSuggestsNothing() {
        fx(view::expandAll);
        openOnTableCell(0, null);
        assertEquals("", initialText());

        openOnTableCell(0, "Assets");
        FlatRow target = WaitForAsyncUtils.waitForAsyncFx(2000, view::filterTargetRow);
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.suggestedFilterExpression(target)));
    }

    @Test
    void anExistingFilterWinsOverTheSuggestion() {
        fx(view::expandAll);
        fx(() -> view.setFilter(row("Positions"), "Position[Country='DE']"));
        openOnTableCell(0, "Country");
        assertEquals("Position[Country='DE']", initialText());
    }

    @Test
    void aLeafRowFiltersItsParentsSiblings() {
        fx(view::expandAll);
        fx(() -> view.selectRow(row("Currency")));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::canFilterSelection), "Filter… is enabled on a leaf");
        assertEquals("Fund", WaitForAsyncUtils.waitForAsyncFx(2000, view::filterTargetRow).getLabel());
        assertEquals("Info[Currency='EUR']", initialText());
    }
}
