package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.MoveTableColumnCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Moving a table column by dragging its header: every member follows, undo restores, no sort. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewColumnMoveTest {

    private static final String XML = """
            <root>
              <item x="1" y="2"><a>1</a><b>2</b><c>3</c></item>
              <item x="3" y="4"><a>4</a><b>5</b><c>6</c></item>
            </root>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1000, 500));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void mouse(EventType<MouseEvent> type, double x, double y, boolean still) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(type, x, y, x, y,
                MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED,
                false, false, true, false, still, null)));
    }

    private FlatRow owner() {
        return view.visibleRowList().stream().filter(r -> "item".equals(r.getLabel())).findFirst().orElseThrow();
    }

    private List<String> childNames(int member) {
        return context.getDocument().getRootElement().getChildElements("item").get(member)
                .getChildElements().stream().map(XmlElement::getName).toList();
    }

    @Test
    void draggingAHeaderMovesTheColumnInEveryMember() {
        fx(view::expandAll);
        RepeatingElementsTable table = owner().getRepeatingTable();
        int idx = view.visibleRowList().indexOf(owner());
        double y = view.rowTopAt(idx) + RepeatingElementsTable.ROW_HEIGHT / 2;
        RepeatingElementsTable.CellRect c = table.cellRect(0, table.getColumns().indexOf(table.getColumn("c")));
        RepeatingElementsTable.CellRect a = table.cellRect(0, table.getColumns().indexOf(table.getColumn("a")));
        double fromX = c.x() + c.width() / 2;
        double toX = a.x() + 5; // left half of "a": insert before it

        mouse(MouseEvent.MOUSE_PRESSED, fromX, y, true);
        mouse(MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, y, false);
        mouse(MouseEvent.MOUSE_DRAGGED, toX, y, false);
        mouse(MouseEvent.MOUSE_RELEASED, toX, y, false);
        mouse(MouseEvent.MOUSE_CLICKED, toX, y, false);

        assertEquals(List.of("c", "a", "b"), childNames(0));
        assertEquals(List.of("c", "a", "b"), childNames(1));
        RepeatingElementsTable rebuilt = owner().getRepeatingTable();
        assertEquals(List.of("@x", "@y", "c", "a", "b"), rebuilt.columnKeys(), "the table shows the new order");
        assertNull(rebuilt.getSortedColumnName(), "a header drag never sorts");

        fx(() -> {
            context.undo();
            view.rebuild();
        });
        assertEquals(List.of("a", "b", "c"), childNames(0));
        assertEquals(List.of("@x", "@y", "a", "b", "c"), owner().getRepeatingTable().columnKeys());
    }

    @Test
    void attributesMoveAmongAttributesOnly() {
        RepeatingElementsTable table = new RepeatingElementsTable("item",
                context.getDocument().getRootElement().getChildElements("item"), 1, () -> { });
        MoveTableColumnCommand mixed = new MoveTableColumnCommand(table, 0, 3, true);
        assertFalse(mixed.isApplicable(), "an attribute cannot move between child elements");

        MoveTableColumnCommand attrs = new MoveTableColumnCommand(table, 1, 0, true);
        assertTrue(context.executeCommand(attrs));
        XmlElement first = context.getDocument().getRootElement().getChildElements("item").get(0);
        assertEquals(List.of("y", "x"), List.copyOf(first.getAttributes().keySet()));
        context.undo();
        assertEquals(List.of("x", "y"), List.copyOf(first.getAttributes().keySet()));
    }
}
