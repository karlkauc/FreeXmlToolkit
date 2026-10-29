package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the XMLSpy-style block layout: a container's child rows form a block
 * docked at the container's value column, every block has its own name-column width, an
 * expanded container's name cell spans its child block (own height 0), a collapsed container
 * shows a one-line preview, and an embedded table sits beside its owner's name cell.
 */
@ExtendWith(ApplicationExtension.class)
class GridBlockLayoutTest {

    private static final String XML = """
            <root>
              <a>1</a>
              <averyveryverylongelementname>2</averyveryverylongelementname>
              <b><c>3</c></b>
              <p id="7"><q>4</q></p>
              <items>
                <item><x>1</x></item>
                <item><x>2</x></item>
              </items>
            </root>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
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

    private FlatRow row(String label) {
        return view.visibleRowList().stream()
                .filter(r -> label.equals(r.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no visible row " + label));
    }

    private int index(FlatRow row) {
        return view.visibleRowList().indexOf(row);
    }

    @Test
    void childBlockStartsAtTheParentsValueColumn() {
        fx(view::expandAll);
        FlatRow root = row("root");
        FlatRow a = row("a");
        FlatRow b = row("b");
        FlatRow c = row("c");

        assertEquals(8, view.labelXOf(root), 0.001, "root block starts at the left margin");
        assertEquals(view.valueXOf(root), view.labelXOf(a), 0.001, "children dock at the parent's value column");
        assertEquals(view.labelXOf(a), view.labelXOf(b), 0.001, "siblings share the block");
        assertEquals(view.valueXOf(a), view.valueXOf(b), 0.001, "one name-column width per block");
        assertEquals(view.valueXOf(b), view.labelXOf(c), 0.001, "nested block docks at b's value column");
        assertEquals(view.valueXOf(root) - 8, view.nameColumnWidthValue(), 0.001);
    }

    @Test
    void blockWidthFollowsTheWidestLabelInTheBlock() {
        fx(view::expandAll);
        FlatRow a = row("a");
        FlatRow longRow = row("averyveryverylongelementname");
        double blockWidth = view.valueXOf(a) - view.labelXOf(a);
        double longLabel = new GridMetrics(TextMeasurer.fx(), GridMetrics.DEFAULT_WRAP_WIDTH)
                .width(longRow.getLabel(), GridFont.ROW_BOLD);
        assertTrue(blockWidth >= GridMetrics.EXPAND_BAR_WIDTH + GridMetrics.ICON_AREA_WIDTH + longLabel,
                "block " + blockWidth + " must fit the widest label " + longLabel);
        FlatRow c = row("c");
        double nestedBlockWidth = view.valueXOf(c) - view.labelXOf(c);
        assertTrue(nestedBlockWidth < blockWidth, "a block with short labels is narrower");
        assertEquals(120, nestedBlockWidth, 0.001, "but never narrower than the minimum");
    }

    @Test
    void expandedContainerSpansItsChildrenAndHasNoOwnLine() {
        fx(view::expandAll);
        FlatRow root = row("root");
        FlatRow a = row("a");
        assertEquals(0, view.rowHeightAt(index(root)), 0.001, "an expanded container has no own line");
        assertEquals(view.rowTopAt(index(root)), view.rowTopAt(index(a)), 0.001,
                "the first child starts where the container starts");
        assertNull(view.valueBlockOf(root), "no preview while expanded");
    }

    @Test
    void collapsedContainerShowsAOneLinePreview() {
        fx(view::expandAll);
        FlatRow b = row("b");
        fx(() -> {
            FlatRow.toggleExpand(b, view.visibleRowList());
            view.rebuild();
        });
        FlatRow collapsed = row("b");
        TextBlock preview = view.valueBlockOf(collapsed);
        assertNotNull(preview, "collapsed container shows the preview");
        assertEquals(1, preview.lineCount(), "the preview never wraps");
        assertEquals("<b><c>3</c></b>", preview.lines().get(0));
        assertEquals(GridMetrics.ROW_HEIGHT, view.rowHeightAt(index(collapsed)), 0.001);
        assertTrue(view.visibleRowList().stream().noneMatch(r -> "c".equals(r.getLabel())), "c is hidden");
    }

    @Test
    void attributesStayVisibleUnderACollapsedElementNextToItsPreview() {
        fx(view::expandAll);
        FlatRow p = row("p");
        fx(() -> {
            FlatRow.toggleExpand(p, view.visibleRowList());
            view.rebuild();
        });
        FlatRow collapsed = row("p");
        FlatRow id = row("id");
        assertEquals(FlatRow.RowType.ATTRIBUTE, id.getType());
        assertNotNull(view.valueBlockOf(collapsed), "preview on the element's own line");
        assertEquals(view.valueXOf(collapsed), view.labelXOf(id), 0.001, "the attribute row docks below it");
        assertEquals(view.rowTopAt(index(collapsed)) + GridMetrics.ROW_HEIGHT, view.rowTopAt(index(id)), 0.001);
    }

    @Test
    void embeddedTableSitsBesideTheOwnersNameCell() {
        fx(view::expandAll);
        FlatRow owner = row("item");
        assertTrue(owner.hasRepeatingTable());
        RepeatingElementsTable table = owner.getRepeatingTable();
        int idx = index(owner);
        assertEquals(view.valueXOf(owner), table.getX(), 0.001, "table starts at the value column");
        assertEquals(view.rowTopAt(idx), table.getY(), 0.001, "table starts at the owner's top");
        assertEquals(table.getHeight(), view.rowHeightAt(idx), 0.001, "the owner row is as tall as its table");
        assertEquals(RepeatingElementsTable.ROW_HEIGHT * 3, table.getHeight(), 0.001, "header + 2 rows, no title bar");
    }

    @Test
    void pointsLeftOfAChildBlockHitTheAncestorsNameCell() {
        fx(view::expandAll);
        FlatRow root = row("root");
        FlatRow a = row("a");
        double y = view.rowTopAt(index(a)) + GridMetrics.ROW_HEIGHT / 2;
        assertSame(a, view.rowAtForTest(view.labelXOf(a) + 5, y));
        assertSame(root, view.rowAtForTest(view.labelXOf(a) - 5, y),
                "left of the child block lies the root's spanning name cell");
        assertSame(a, view.rowAtForTest(view.valueXOf(a) + 50, y), "the value cell belongs to the row");
    }
}
