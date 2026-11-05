package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

/**
 * Filters inside opened cells (the QMV case): "Filter…" from the inner table of a table record
 * filters that record, and its opened cell then lists only the matching members.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewCellFilterTest {

    private static final String XML = """
            <Meldung>
              <List VRG="2">
                <Datenpunkt><Code>A</Code><Wert>1</Wert></Datenpunkt>
                <Datenpunkt><Code>B</Code><Wert>20</Wert></Datenpunkt>
                <Datenpunkt><Code>C</Code><Wert>30</Wert></Datenpunkt>
                <?fxt-formula name="Summe" store="false" expression="sum(Datenpunkt/Wert)"?>
              </List>
              <List VRG="4">
                <Datenpunkt><Code>D</Code><Wert>1</Wert></Datenpunkt>
                <Datenpunkt><Code>E</Code><Wert>40</Wert></Datenpunkt>
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

    private FlatRow listRow() {
        return view.visibleRowList().stream().filter(r -> "List".equals(r.getLabel())).findFirst().orElseThrow();
    }

    /** Opens the Datenpunkt cell of the given record of the outer table. */
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

    private static FlatRow groupRow(GridBlock grid) {
        return grid.rows().stream().filter(r -> r.getRepeatingTable() != null).findFirst().orElseThrow();
    }

    private XmlElement firstRecord() {
        return context.getDocument().getRootElement().getChildElements("List").getFirst();
    }

    @Test
    void filterIsAvailableFromTheInnerTableAndTargetsTheRecord() {
        GridBlock grid = openCell(0);
        FlatRow group = groupRow(grid);
        fx(() -> {
            view.showTableContextMenu(0, 0, group, group.getRepeatingTable(), 0, "Wert");
            view.contextMenu().hide();
            return null;
        });
        assertTrue(fx(view::canFilterSelection), "Filter… is enabled in the inner table");
        assertSame(firstRecord(), fx(() -> view.filterTargetRow().getModelNode()), "it filters the record");

        fx(() -> view.selectRowMatching(r -> r == group));
        assertTrue(fx(view::canFilterSelection), "also on the group row inside the cell");
        assertSame(firstRecord(), fx(() -> view.filterTargetRow().getModelNode()));

        fx(() -> view.selectRowMatching(r -> "List".equals(r.getLabel())));
        assertTrue(fx(view::canFilterSelection), "the outer group row filters the element holding it");
        assertSame(context.getDocument().getRootElement(), fx(() -> view.filterTargetRow().getModelNode()));
    }

    @Test
    void theOpenedCellListsOnlyMatchingMembersAndKeepsItsFormula() {
        GridBlock grid = openCell(0);
        FlatRow group = groupRow(grid);
        fx(() -> {
            view.showTableContextMenu(0, 0, group, group.getRepeatingTable(), 0, "Wert");
            view.contextMenu().hide();
            view.setFilter(view.filterTargetRow(), "Datenpunkt[Wert > 10]");
            return null;
        });

        GridBlock filtered = openCell(0);
        assertEquals(FlatRow.RowType.FILTER, filtered.rows().getFirst().getType(), "the filter line comes first");
        assertEquals(2, groupRow(filtered).getRepeatingTable().getRows().size(), "B and C remain");
        FlatRow formula = filtered.rows().stream()
                .filter(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION).findFirst().orElse(null);
        assertNotNull(formula, "the record's formula stays");
        assertEquals("51", fx(() -> view.formulaOf(formula).result()), "and sums the unfiltered data");

        GridBlock other = openCell(1);
        assertFalse(other.rows().stream().anyMatch(r -> r.getType() == FlatRow.RowType.FILTER),
                "the other record is not filtered");
        assertEquals(2, groupRow(other).getRepeatingTable().getRows().size());

        fx(() -> {
            view.setFilter(openCell(0).rows().getFirst().getParentRow(), "");
            return null;
        });
        List<FlatRow> cleared = openCell(0).rows();
        assertFalse(cleared.stream().anyMatch(r -> r.getType() == FlatRow.RowType.FILTER), "an empty filter removes it");
        assertEquals(3, groupRow(openCell(0)).getRepeatingTable().getRows().size());
    }

    @Test
    void aFilterOnTheRootAlsoReducesTheOpenedCells() {
        openCell(0);
        fx(() -> {
            FlatRow root = view.visibleRowList().stream().filter(r -> "Meldung".equals(r.getLabel())).findFirst().orElseThrow();
            view.setFilter(root, "*/Datenpunkt[starts-with(Code, 'C') or starts-with(Code, 'E')]");
            return null;
        });
        assertEquals(1, groupRow(openCell(0)).getRepeatingTable().getRows().size(), "record 1: only C");
        assertEquals(1, groupRow(openCell(1)).getRepeatingTable().getRows().size(), "record 2: only E");
    }

    @Test
    void startsWithOnTheRecordKeepsTheMatchingMembers() {
        GridBlock grid = openCell(0);
        FlatRow group = groupRow(grid);
        fx(() -> {
            view.showTableContextMenu(0, 0, group, group.getRepeatingTable(), 0, "Code");
            view.contextMenu().hide();
            view.setFilter(view.filterTargetRow(), "Datenpunkt[starts-with(Code, 'B')]");
            return null;
        });
        RepeatingElementsTable table = groupRow(openCell(0)).getRepeatingTable();
        assertEquals(1, table.getRows().size());
        assertEquals("B", table.getRows().getFirst().getValue("Code"));
    }
}
