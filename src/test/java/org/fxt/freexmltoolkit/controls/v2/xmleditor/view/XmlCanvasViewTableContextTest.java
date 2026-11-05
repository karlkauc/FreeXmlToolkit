package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Formulas and filters started from inside an embedded table (right-click on a table row or
 * cell) act on the element that holds the table, and a new formula row is shown and selected.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewTableContextTest {

    private static final String XML = """
            <Year value="2025">
              <Month><Name>Jan</Name><Min>1</Min></Month>
              <Month><Name>Feb</Name><Min>3</Min></Month>
              <Month><Name>Mar</Name><Min>12</Min></Month>
              <Note>n</Note>
            </Year>
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

    private RepeatingElementsTable monthTable() {
        return row("Month").getRepeatingTable();
    }

    private List<String> tableMonths() {
        return monthTable().getRows().stream()
                .map(r -> r.getElement().getChildElements("Name").getFirst().getTextContent()).toList();
    }

    /** Opens the context menu as a right-click on a table cell does (then hides it). */
    private XmlGridContextMenu openOnTableCell(int tableRow, String column) {
        AtomicReference<XmlGridContextMenu> menu = new AtomicReference<>();
        fx(() -> {
            view.showTableContextMenu(0, 0, row("Month"), monthTable(), tableRow, column);
            menu.set((XmlGridContextMenu) view.contextMenu());
            menu.get().hide();
        });
        return menu.get();
    }

    private XmlElement root() {
        return context.getDocument().getRootElement();
    }

    @Test
    void formulaFromATableCellGoesToTheTablesParentAndIsShown() {
        fx(view::expandAll);
        XmlGridContextMenu menu = openOnTableCell(1, "Min");

        AtomicReference<XmlProcessingInstruction> pi = new AtomicReference<>();
        fx(() -> pi.set(menu.insertFormula(menu.formulaTarget(),
                new GridFormulas.Formula("avgMin", "avg(Month/Min)", false))));

        assertNotNull(pi.get());
        assertSame(root(), pi.get().getParent(), "the formula belongs to the element holding the table");
        FlatRow formulaRow = view.visibleRowList().stream().filter(r -> r.getModelNode() == pi.get())
                .findFirst().orElse(null);
        assertNotNull(formulaRow, "the formula row is visible in the grid");
        assertNotNull(view.formulaOf(formulaRow));
        assertSame(pi.get(), view.getSelectedNode(), "the new formula is selected");
    }

    @Test
    void formulaOnALeafElementExpandsItAndIsShown() {
        fx(view::expandAll);
        XmlElement note = (XmlElement) row("Note").getModelNode();
        AtomicReference<XmlProcessingInstruction> pi = new AtomicReference<>();
        fx(() -> {
            view.setSelectedNode(note);
            XmlGridContextMenu menu = (XmlGridContextMenu) view.contextMenu();
            menu.show(view, 0, 0, note);
            menu.hide();
            pi.set(menu.insertFormula(menu.formulaTarget(), new GridFormulas.Formula("len", "string-length(.)", false)));
        });

        assertSame(note, pi.get().getParent());
        assertTrue(view.visibleRowList().stream().anyMatch(r -> r.getModelNode() == pi.get()),
                "the leaf element is expanded so its new formula shows");
        assertSame(pi.get(), view.getSelectedNode());
    }

    @Test
    void filterFromATableTargetsTheTablesParent() {
        fx(view::expandAll);
        openOnTableCell(0, "Name");

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::canFilterSelection), "Filter… is enabled in a table");
        FlatRow target = WaitForAsyncUtils.waitForAsyncFx(2000, view::filterTargetRow);
        assertEquals("Year", target.getLabel());

        fx(() -> view.setFilter(target, "Month[number(Min) > 2]"));
        assertEquals(List.of("Feb", "Mar"), tableMonths());
    }

    @Test
    void filterToFocusOnATableRowKeepsOnlyThatRow() {
        fx(view::expandAll);
        openOnTableCell(1, "Name");

        fx(view::filterToFocus);

        assertEquals(List.of("Feb"), tableMonths(), "only the focused member stays in the table");
        assertFalse(visible("Note"), "everything else under the root is filtered away");
        assertEquals(3, root().getChildElements("Month").size(), "a filter never changes the document");
    }
}
