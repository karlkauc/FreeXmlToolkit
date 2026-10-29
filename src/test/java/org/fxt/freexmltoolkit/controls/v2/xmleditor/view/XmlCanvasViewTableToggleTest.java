package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
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
 * TestFX verification of the per-group table toggle and the flip: a click on the table glyph
 * shows the repeating group as numbered rows and back, the corner cell flips the table, and
 * both choices survive a rebuild (edit) because they are kept in the view state.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewTableToggleTest {

    private static final String XML = """
            <root>
              <item><a>1</a><b>x</b></item>
              <item><a>2</a><b>y</b></item>
              <item><a>3</a><b>z</b></item>
              <other>o</other>
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
                true, false, false, true, false, true, null)));
    }

    private List<FlatRow> items() {
        return view.visibleRowList().stream().filter(r -> "item".equals(r.getLabel())).toList();
    }

    @Test
    void tableGlyphTogglesBetweenTableAndNumberedRows() {
        fx(view::expandAll);
        List<FlatRow> owners = items();
        assertEquals(1, owners.size(), "one owner row for the group");
        FlatRow owner = owners.get(0);
        assertTrue(owner.hasRepeatingTable());
        assertFalse(Double.isNaN(owner.getTableGlyphX()), "the glyph was drawn");

        int idx = view.visibleRowList().indexOf(owner);
        click(owner.getTableGlyphX() + 3, view.rowTopAt(idx) + GridMetrics.ROW_HEIGHT / 2);

        List<FlatRow> members = items();
        assertEquals(3, members.size(), "standard display: one row per member");
        assertEquals(List.of(1, 2, 3), members.stream().map(FlatRow::getInstanceIndex).toList());
        assertTrue(members.stream().noneMatch(FlatRow::hasRepeatingTable));
        assertEquals("/root/item", members.get(0).getStandardGroupKey());
        assertTrue(members.get(0).showsTableGlyph(), "the first member carries the glyph");
        assertFalse(members.get(1).showsTableGlyph());
        assertFalse(view.getViewState().isTable("/root/item"));

        // Members are real rows: expanding one shows its children
        fx(() -> view.expandFully(members.get(1)));
        assertTrue(view.visibleRowList().stream().anyMatch(r -> "a".equals(r.getLabel()) && "2".equals(r.getValue())));

        // Survives a rebuild
        fx(view::rebuild);
        assertEquals(3, items().size());

        // And back to the table through the first member's glyph
        FlatRow first = items().get(0);
        int firstIdx = view.visibleRowList().indexOf(first);
        click(first.getTableGlyphX() + 3, view.rowTopAt(firstIdx) + GridMetrics.ROW_HEIGHT / 2);
        assertEquals(1, items().size());
        assertTrue(items().get(0).hasRepeatingTable());
    }

    @Test
    void cornerCellFlipsTheTableAndTheFlipSurvivesARebuild() {
        fx(view::expandAll);
        FlatRow owner = items().get(0);
        RepeatingElementsTable table = owner.getRepeatingTable();
        int idx = view.visibleRowList().indexOf(owner);
        double standardHeight = view.rowHeightAt(idx);
        assertFalse(table.isFlipped());

        click(table.getX() + 5, view.rowTopAt(idx) + 5); // the corner cell
        assertTrue(items().get(0).getRepeatingTable().isFlipped());
        assertTrue(view.getViewState().isFlipped("/root/item"));
        assertEquals(RepeatingElementsTable.ROW_HEIGHT * 3, view.rowHeightAt(idx), 0.001,
                "flipped: numbers row + 2 column rows; the owner follows the table height");
        assertTrue(view.rowHeightAt(idx) != standardHeight || standardHeight == RepeatingElementsTable.ROW_HEIGHT * 3);

        fx(view::rebuild);
        assertTrue(items().get(0).getRepeatingTable().isFlipped(), "the flip is restored after a rebuild");

        click(items().get(0).getRepeatingTable().getX() + 5, view.rowTopAt(idx) + 5);
        assertFalse(items().get(0).getRepeatingTable().isFlipped());
        assertNull(view.flipStateOfSelection() == null ? null : Boolean.FALSE, "no selection in a table");
    }
}
