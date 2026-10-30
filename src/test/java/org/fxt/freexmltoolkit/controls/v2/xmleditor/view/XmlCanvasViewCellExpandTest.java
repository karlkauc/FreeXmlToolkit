package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Selecting a whole table row (row number) or column (header) and opening / closing its complex
 * cells one level per "+" / "−"; the open cells survive an edit; the header arrow sorts.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewCellExpandTest {

    private static final String PERSON = "<Person><Name>%s</Name>"
            + "<Address><Street>Main</Street><Geo><Lat>1</Lat><Lng>2</Lng></Geo></Address>"
            + "<Phones><Phone>1</Phone></Phones></Person>";
    private static final String XML = "<root>" + PERSON.formatted("C") + PERSON.formatted("A")
            + PERSON.formatted("B") + "</root>";

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1200, 700));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void click(double x, double y) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, true, false, true, null)));
    }

    private void key(KeyCode code, String text) {
        fx(() -> Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code,
                false, false, false, false)));
    }

    private FlatRow owner() {
        return view.visibleRowList().stream().filter(r -> "Person".equals(r.getLabel())).findFirst().orElseThrow();
    }

    private RepeatingElementsTable table() {
        return owner().getRepeatingTable();
    }

    private double tableTop() {
        return view.rowTopAt(view.visibleRowList().indexOf(owner()));
    }

    private int column(String name) {
        return table().getColumns().indexOf(table().getColumn(name));
    }

    /** Clicks the row-number cell of a table row. */
    private void clickRowNumber(int row) {
        RepeatingElementsTable.CellRect cell = table().cellRect(row, 0);
        click(table().drawnColumnX(0) + 4, tableTop() + cell.y() + 6);
    }

    /** Clicks a column header: on its name, or on the sort arrow at its right. */
    private void clickHeader(String name, boolean arrow) {
        RepeatingElementsTable.CellRect cell = table().cellRect(0, column(name));
        double x = arrow ? cell.x() + cell.width() - 6 : cell.x() + 10;
        click(x, tableTop() + RepeatingElementsTable.ROW_HEIGHT / 2);
    }

    private boolean open(int row, String column) {
        return table().getRows().get(row).isColumnExpanded(column);
    }

    private FlatRow geo(int row) {
        return table().getRows().get(row).getExpandedCellRows("Address").stream()
                .filter(r -> "Geo".equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void plusAndMinusStepThroughTheLevelsOfASelectedRow() {
        fx(view::expandAll);
        clickRowNumber(0);
        key(KeyCode.ADD, "+");
        assertTrue(open(0, "Address"));
        assertTrue(open(0, "Phones"));
        assertFalse(open(1, "Address"), "only the selected row");

        key(KeyCode.ADD, "+");
        assertTrue(geo(0).isExpanded(), "the second + opens the next level");

        key(KeyCode.SUBTRACT, "-");
        assertFalse(geo(0).isExpanded(), "− closes the deepest level first");
        assertTrue(open(0, "Address"));
        key(KeyCode.MINUS, "-");
        assertFalse(open(0, "Address"));
        assertFalse(open(0, "Phones"));
    }

    @Test
    void aHeaderClickSelectsTheColumnAndTheArrowSorts() {
        fx(view::expandAll);
        clickHeader("Address", false);
        assertNull(table().getSortedColumnName(), "a click on the name selects, it does not sort");
        key(KeyCode.ADD, "+");
        for (int r = 0; r < 3; r++) {
            assertTrue(open(r, "Address"), "row " + r);
            assertFalse(open(r, "Phones"), "other columns stay closed");
        }

        clickHeader("Name", true);
        assertEquals("Name", table().getSortedColumnName(), "the arrow sorts");
    }

    @Test
    void openCellsSurviveAnEdit() {
        fx(view::expandAll);
        clickRowNumber(1);
        key(KeyCode.ADD, "+");
        key(KeyCode.ADD, "+");
        assertTrue(geo(1).isExpanded());

        fx(() -> {
            context.getDocument().getRootElement().setAttribute("v", "1");
            view.refresh(); // rebuilds every row and table from the model
        });
        assertTrue(open(1, "Address"), "the cell is open again after the rebuild");
        assertTrue(geo(1).isExpanded(), "and so is its sub-row");
        assertFalse(open(0, "Address"));
    }

    @Test
    void withoutATableSelectionPlusExpandsTheSelectedRow() {
        fx(() -> view.collapseAll());
        FlatRow root = view.visibleRowList().get(0);
        fx(() -> view.setSelectedNode(context.getDocument().getRootElement()));
        key(KeyCode.SUBTRACT, "-");
        assertFalse(root.isExpanded());
        key(KeyCode.ADD, "+");
        assertTrue(root.isExpanded());
        assertTrue(List.copyOf(view.visibleRowList()).size() > 1);
    }
}
