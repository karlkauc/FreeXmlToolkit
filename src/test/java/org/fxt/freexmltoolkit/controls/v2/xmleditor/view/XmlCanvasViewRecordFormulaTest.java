package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
 * Formulas of a record of an embedded table (the QMV case: a formula on a
 * {@code PK.006…} element that is a row of the outer table) show in the record's opened cell.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewRecordFormulaTest {

    private static final String XML = """
            <Meldung>
              <List VRG="2">
                <Datenpunkt><Code>A</Code><Wert>10.5</Wert></Datenpunkt>
                <Datenpunkt><Code>B</Code><Wert>4.5</Wert></Datenpunkt>
                <?fxt-formula name="Summe_Wert" store="false" expression="sum(Datenpunkt/Wert)"?>
              </List>
              <List VRG="4">
                <Datenpunkt><Code>C</Code><Wert>1</Wert></Datenpunkt>
                <Datenpunkt><Code>D</Code><Wert>2</Wert></Datenpunkt>
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

    /** Expands the outer table and opens the Datenpunkt cell of its first record. */
    private GridBlock openFirstCell() {
        return fx(() -> {
            view.expandAll();
            RepeatingElementsTable outer = listRow().getRepeatingTable();
            RepeatingElementsTable.TableRow record = outer.getRows().getFirst();
            if (!record.isColumnExpanded("Datenpunkt")) {
                record.toggleColumnExpanded("Datenpunkt");
            }
            return record.getCellGrid("Datenpunkt");
        });
    }

    @Test
    void theRecordsFormulaIsShownWithItsResultInTheOpenedCell() {
        GridBlock grid = openFirstCell();
        assertNotNull(grid);
        FlatRow first = grid.rows().getFirst();
        GridModelAdapter.FormulaView formula = fx(() -> view.formulaOf(first));
        assertNotNull(formula, "the formula row comes first in the cell");
        assertEquals("Summe_Wert", formula.name());
        assertEquals("15", formula.result());
    }

    @Test
    void aFormulaAddedFromTheInnerTableIsShownAndSelected() {
        GridBlock grid = openFirstCell();
        FlatRow groupRow = grid.rows().stream().filter(r -> r.getRepeatingTable() != null).findFirst().orElseThrow();
        RepeatingElementsTable inner = groupRow.getRepeatingTable();

        AtomicReference<XmlProcessingInstruction> pi = new AtomicReference<>();
        fx(() -> {
            view.showTableContextMenu(0, 0, groupRow, inner, 0, "Wert");
            XmlGridContextMenu menu = (XmlGridContextMenu) view.contextMenu();
            menu.hide();
            pi.set(menu.insertFormula(menu.formulaTarget(),
                    new GridFormulas.Formula("Anzahl", "count(Datenpunkt)", false)));
            return null;
        });

        XmlElement record = (XmlElement) context.getDocument().getRootElement().getChildElements("List").getFirst();
        assertSame(record, pi.get().getParent(), "the formula goes to the record holding the table");
        assertSame(pi.get(), fx(view::getSelectedNode), "the new formula row in the cell is selected");
        GridBlock reopened = fx(() -> listRow().getRepeatingTable().getRows().getFirst().getCellGrid("Datenpunkt"));
        List<String> names = reopened.rows().stream().filter(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION)
                .map(r -> GridFormulas.parse(r.getValue()).name()).toList();
        assertEquals(List.of("Summe_Wert", "Anzahl"), names, "several named formulas, in document order");
    }
}
