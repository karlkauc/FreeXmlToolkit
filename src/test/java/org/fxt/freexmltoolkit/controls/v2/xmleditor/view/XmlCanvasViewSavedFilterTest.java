package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Filters saved in the document come back inactive (a file never opens filtered unnoticed) and
 * are switched on with "Filter Active"; saving, editing and removing follow into the document.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewSavedFilterTest {

    private static final String XML = """
            <Meldung>
              <List VRG="2">
                <?fxt-filter expression="Datenpunkt[starts-with(Code, 'B')]"?>
                <Datenpunkt><Code>A</Code></Datenpunkt>
                <Datenpunkt><Code>B</Code></Datenpunkt>
              </List>
              <List VRG="4">
                <Datenpunkt><Code>C</Code></Datenpunkt>
                <Datenpunkt><Code>D</Code></Datenpunkt>
              </List>
            </Meldung>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
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

    private void run(Runnable r) {
        fx(() -> {
            r.run();
            return null;
        });
    }

    private FlatRow listRow() {
        return view.visibleRowList().stream().filter(r -> "List".equals(r.getLabel())).findFirst().orElseThrow();
    }

    private GridBlock openCell(int record) {
        return fx(() -> {
            view.expandAll();
            RepeatingElementsTable.TableRow row = listRow().getRepeatingTable().getRows().get(record);
            if (!row.isColumnExpanded("Datenpunkt")) {
                row.toggleColumnExpanded("Datenpunkt");
            }
            return row.getCellGrid("Datenpunkt");
        });
    }

    private static List<String> codes(GridBlock grid) {
        RepeatingElementsTable table = grid.rows().stream().filter(r -> r.getRepeatingTable() != null)
                .findFirst().orElseThrow().getRepeatingTable();
        return table.getRows().stream().map(r -> r.getValue("Code")).toList();
    }

    /** Right-click on "N × <Datenpunkt>" of a record (the context menu's cell). */
    private void rightClickCell(int record) {
        openCell(record);
        run(() -> {
            view.showTableContextMenu(0, 0, listRow(), listRow().getRepeatingTable(), record, "Datenpunkt");
            view.contextMenu().hide();
        });
    }

    private XmlElement record(int index) {
        return context.getDocument().getRootElement().getChildElements("List").get(index);
    }

    @Test
    void aSavedFilterOpensInactiveAndIsSwitchedOnFromTheMenu() {
        GridBlock grid = openCell(0);
        FlatRow line = grid.rows().getFirst();
        assertEquals(FlatRow.RowType.FILTER, line.getType(), "the funnel line shows the saved filter");
        assertTrue(line.getValue().endsWith("· saved"));
        assertEquals(List.of("A", "B"), codes(grid), "inactive: nothing is filtered");

        rightClickCell(0);
        assertEquals(Optional.of(false), fx(view::filterActiveStateOfSelection));
        assertEquals(Optional.of(true), fx(view::filterSavedStateOfSelection));
        run(view::toggleFilterActiveOfSelection);
        assertEquals(List.of("B"), codes(openCell(0)), "switched on");
    }

    @Test
    void savingWritesTheFilterIntoTheDocumentAsOneUndoableEdit() {
        rightClickCell(1);
        run(() -> view.setFilter(view.prepareFilterTarget(), "Datenpunkt[starts-with(Code, 'D')]"));
        rightClickCell(1);
        assertEquals(Optional.of(false), fx(view::filterSavedStateOfSelection));
        run(view::toggleFilterSavedOfSelection);

        assertNotNull(GridFilters.of(record(1)), "saved in the record");
        assertEquals("Datenpunkt[starts-with(Code, 'D')]", GridFilters.parse(GridFilters.of(record(1)).getData()));
        assertTrue(context.serializeToString().contains("<?fxt-filter expression=\"Datenpunkt[starts-with(Code, 'D')]\"?>"));
        assertEquals(List.of("D"), codes(openCell(1)), "the filter stays active after saving");

        run(() -> {
            context.undo();
            view.rebuild();
        });
        assertNull(GridFilters.of(record(1)), "undo removes it from the document");
        assertFalse(openCell(1).rows().stream().anyMatch(r -> r.getType() == FlatRow.RowType.FILTER));
    }

    @Test
    void editingAndRemovingFollowIntoTheDocument() {
        rightClickCell(0);
        run(() -> view.setFilter(view.prepareFilterTarget(), "Datenpunkt[starts-with(Code, 'A')]"));
        assertEquals("Datenpunkt[starts-with(Code, 'A')]", GridFilters.parse(GridFilters.of(record(0)).getData()),
                "the saved filter follows the edit");
        assertEquals(List.of("A"), codes(openCell(0)));

        rightClickCell(0);
        run(view::removeFilterOfSelection);
        assertNull(GridFilters.of(record(0)), "Remove Filter removes it from the document");
        assertEquals(List.of("A", "B"), codes(openCell(0)));
    }

    @Test
    void removeAllFiltersAlsoClearsTheDocument() {
        rightClickCell(1);
        run(() -> view.setFilter(view.prepareFilterTarget(), "Datenpunkt[Code = 'C']"));
        rightClickCell(1);
        run(view::toggleFilterSavedOfSelection);
        assertEquals(2, GridFilters.savedFilters(context.getDocument()).size());

        run(view::removeAllFilters);
        assertTrue(GridFilters.savedFilters(context.getDocument()).isEmpty());
        assertFalse(fx(view::hasAnyFilter));
    }

    @Test
    void anInstructionDeletedOutsideTheGridLeavesTheView() {
        run(() -> {
            context.executeCommand(new DeleteNodeCommand(GridFilters.of(record(0))));
            view.rebuild();
        });
        assertFalse(fx(view::hasAnyFilter));
        assertFalse(openCell(0).rows().stream().anyMatch(r -> r.getType() == FlatRow.RowType.FILTER));
    }
}
