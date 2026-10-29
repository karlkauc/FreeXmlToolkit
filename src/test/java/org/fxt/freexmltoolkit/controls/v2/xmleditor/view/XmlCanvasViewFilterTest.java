package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** XMLSpy-style XQuery filters: only matching nodes stay, tables shrink, the filter line toggles, focus. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewFilterTest {

    private static final String XML = """
            <Temperatures xmlns="urn:t">
              <Month name="May"><Min>8</Min></Month>
              <Month name="June"><Min>12</Min></Month>
              <Month name="July"><Min>14</Min></Month>
              <Note>keep me?</Note>
            </Temperatures>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
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

    private boolean visible(String label) {
        return view.visibleRowList().stream().anyMatch(r -> label.equals(r.getLabel()));
    }

    private List<String> tableMonths() {
        return row("Month").getRepeatingTable().getRows().stream().map(r -> r.getValue("name")).toList();
    }

    @Test
    void filterKeepsOnlyMatchingMembersAndShowsAFilterLine() {
        fx(view::expandAll);
        fx(() -> view.setFilter(row("Temperatures"), "Month[number(Min) > 10]"));

        assertEquals(List.of("June", "July"), tableMonths(), "the table shrinks to the matches");
        assertFalse(visible("Note"), "non-matching siblings are hidden");
        FlatRow line = view.visibleRowList().stream().filter(r -> r.getType() == FlatRow.RowType.FILTER)
                .findFirst().orElseThrow();
        assertEquals("Month[number(Min) > 10]", line.getValue());
        assertEquals(3, context.getDocument().getRootElement().getChildElements("Month").size(),
                "a filter never changes the document");

        // Switching the filter off keeps the line but shows everything
        fx(() -> view.getViewState().setFilterActive(view.getAdapter().stableKey(row("Temperatures")), false));
        fx(view::rebuild);
        assertEquals(List.of("May", "June", "July"), tableMonths());
        assertTrue(visible("Note"));

        // An empty expression removes the filter
        fx(() -> view.setFilter(row("Temperatures"), ""));
        assertTrue(view.visibleRowList().stream().noneMatch(r -> r.getType() == FlatRow.RowType.FILTER));
    }

    @Test
    void invalidExpressionsFilterNothingAndAreReported() {
        fx(view::expandAll);
        fx(() -> view.setFilter(row("Temperatures"), "Month[[["));
        assertEquals(List.of("May", "June", "July"), tableMonths());
        assertTrue(visible("Note"));
    }

    @Test
    void filterToFocusShowsOnlyTheSelectionsSubtree() {
        fx(view::expandAll);
        fx(() -> {
            view.setSelectedNode((XmlElement) row("Note").getModelNode());
            view.filterToFocus();
        });
        assertTrue(visible("Note"));
        assertFalse(visible("Month"), "everything else under the root is filtered away");
    }
}
