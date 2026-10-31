package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The grid inside the editor's tab pane keeps the keyboard focus after a click (the tab pane takes
 * the focus on every press it receives), so "+" / "−" open and close a selected column's cells.
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridKeysInEditorHostTest {

    private static final String PERSON = "<Person><Name>%s</Name>"
            + "<Address><Street>Main</Street><Geo><Lat>1</Lat><Lng>2</Lng></Geo></Address></Person>";

    private EditorHost host;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 700));
        stage.show();
    }

    @Test
    void plusAndMinusReachTheGridAfterAHeaderClick(FxRobot robot, @TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("persons.xml");
        Files.writeString(xml, "<root>" + PERSON.formatted("A") + PERSON.formatted("B") + "</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Person")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlCanvasView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XmlCanvasView) host.lookupAll("*")
                .stream().filter(n -> n instanceof XmlCanvasView).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.expandAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        RepeatingElementsTable table = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.visibleRowList().stream()
                .filter(r -> "Person".equals(r.getLabel())).findFirst().orElseThrow().getRepeatingTable());
        Point2D header = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            FlatRow owner = table.getOwnerRow();
            double top = view.rowTopAt(view.visibleRowList().indexOf(owner));
            RepeatingElementsTable.CellRect cell = table.cellRect(0, table.getColumns().indexOf(table.getColumn("Address")));
            return view.canvasNode().localToScreen(cell.x() + 10, top + 8);
        });

        robot.clickOn(header);
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(view.canvasNode(), stage.getScene().getFocusOwner(), "the grid keeps the focus");

        robot.type(KeyCode.ADD);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(table.getRows().get(0).isColumnExpanded("Address"));
        assertTrue(table.getRows().get(1).isColumnExpanded("Address"));

        robot.type(KeyCode.SUBTRACT);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(table.getRows().get(0).isColumnExpanded("Address"));
        assertFalse(table.getRows().get(1).isColumnExpanded("Address"));
    }
}
