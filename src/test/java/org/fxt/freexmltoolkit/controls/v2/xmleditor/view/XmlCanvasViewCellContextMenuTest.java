package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A right-click on a table cell that is an element of its own (Identifiers of an Asset row) makes
 * that element the target of the node actions; row actions stay in the Table submenu.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewCellContextMenuTest {

    private static final String ASSET = "<Asset id=\"%s\"><Name>%s</Name>"
            + "<Identifiers><ISIN>X%s</ISIN><WKN>W%s</WKN></Identifiers></Asset>";
    private static final String XML = "<AssetMasterData>" + ASSET.formatted(1, "A", 1, 1)
            + ASSET.formatted(2, "B", 2, 2) + ASSET.formatted(3, "C", 3, 3) + "</AssetMasterData>";

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
        view.expandAll();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private RepeatingElementsTable table() {
        return view.visibleRowList().stream().filter(r -> "Asset".equals(r.getLabel())).findFirst()
                .orElseThrow().getRepeatingTable();
    }

    /** Right-clicks the cell of a table row and column; returns the shown menu. */
    private ContextMenu rightClick(int row, String column) {
        return fx(() -> {
            RepeatingElementsTable t = table();
            double top = view.rowTopAt(view.visibleRowList().indexOf(t.getOwnerRow()));
            RepeatingElementsTable.CellRect cell = t.cellRect(row, t.getColumns().indexOf(t.getColumn(column)));
            double x = cell.x() + cell.width() / 2;
            double y = top + cell.y() + 8;
            Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                    MouseButton.SECONDARY, 1, false, false, false, false, false, false, true, true, false, true, null));
            return Window.getWindows().stream().filter(w -> w instanceof ContextMenu cm && cm.isShowing())
                    .map(w -> (ContextMenu) w).findFirst().orElseThrow();
        });
    }

    private XmlElement asset(int index) {
        return context.getDocument().getRootElement().getChildElements("Asset").get(index);
    }

    @Test
    void anElementCellIsTheTargetOfTheNodeActions() {
        ContextMenu menu = rightClick(2, "Identifiers");
        assertSame(asset(2).getChildElement("Identifiers"), context.getSelectionModel().getSelectedNode(),
                "Add Child Element & co. act on Identifiers, not on the Asset row");
        fx(() -> {
            menu.hide();
            return null;
        });

        ContextMenu nameMenu = rightClick(1, "Name");
        assertSame(asset(1).getChildElement("Name"), context.getSelectionModel().getSelectedNode());
        fx(() -> {
            nameMenu.hide();
            return null;
        });
    }

    @Test
    void deleteRowInTheTableSubmenuDeletesTheWholeRow() {
        ContextMenu menu = rightClick(1, "Identifiers");
        MenuItem deleteRow = fx(() -> menu.getItems().stream().filter(i -> i instanceof Menu m && "Table".equals(m.getText()))
                .flatMap(i -> ((Menu) i).getItems().stream()).filter(i -> "Delete Row".equals(i.getText()))
                .findFirst().orElseThrow());
        assertFalse(deleteRow.isDisable());
        fx(() -> {
            menu.hide();
            deleteRow.fire();
            return null;
        });
        assertEquals(2, context.getDocument().getRootElement().getChildElements("Asset").size());
        assertEquals("3", asset(1).getAttribute("id"), "the second Asset is gone");
        fx(() -> {
            context.undo();
            return null;
        });
        assertEquals(3, context.getDocument().getRootElement().getChildElements("Asset").size(), "one undo step");
    }

    @Test
    void attributeCellsKeepTheRowAsTarget() {
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        RepeatingElementsTable.TableRow row = fx(() -> table().getRows().get(0));
        assertNull(adapter.cellNode(row, "id"), "an attribute is no element cell");
        assertNull(adapter.cellNode(row, "Missing"));
        assertSame(asset(0).getChildElement("Identifiers"), adapter.cellNode(row, "Identifiers"));
    }
}
