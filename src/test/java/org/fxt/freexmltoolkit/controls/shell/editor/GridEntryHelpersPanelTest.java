package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModelTestSupport;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The entry helpers beside the XML grid: shown by the header toggle, following the selection. */
@ExtendWith(ApplicationExtension.class)
class GridEntryHelpersPanelTest {

    @TempDir
    Path dir;

    private XmlGridView grid;
    private XmlEditorContext context;
    private final java.util.List<String> written = new java.util.ArrayList<>();

    @Start
    void start(Stage stage) {
        context = new XmlEditorContext();
        context.loadDocumentFromString("<Company><Name>ACME</Name><Person id=\"p1\"><Role>Manager</Role></Person></Company>");
        grid = new XmlGridView();
        grid.setOnModified(written::add);
        grid.setContext(context);
        stage.setScene(new Scene(grid, 1000, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(Entry::name).toList();
    }

    private static void doubleClick(javafx.scene.Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    @Test
    void theToggleShowsThePanelThatFollowsTheSelectionAndInserts() throws Exception {
        assertNull(grid.lookup("#grid-entry-helpers"), "off by default");
        fx(() -> grid.setSidePanelShown(true));
        assertNotNull(grid.lookup("#grid-side-split"));
        GridEntryHelpersPanel panel = grid.getEntryHelpers();
        assertTrue(panel.isVisible());
        assertTrue(panel.childrenList().getItems().isEmpty(), "no schema yet");

        // The schema arrives later (bound off-thread in the shell)
        var schema = EntryHelperModelTestSupport.schema(dir);
        fx(() -> context.setSchemaProvider(schema));
        fx(() -> grid.getCanvasView().setSelectedNode(context.getDocument().getRootElement()));
        assertEquals(List.of("Name", "Phone", "Email", "Person"), names(panel.childrenList().getItems()));

        // Double-click on Phone appends it to Company (one command, text written back)
        fx(() -> {
            panel.childrenList().getSelectionModel().select(1);
            doubleClick(panel.childrenList());
        });
        XmlElement company = context.getDocument().getRootElement();
        assertNotNull(company.getChildElement("Phone"));
        assertFalse(written.isEmpty(), "the grid wrote the document back");

        // An attribute row: its enumeration in Values
        XmlElement person = company.getChildElement("Person");
        fx(() -> person.setAttribute("level", "junior"));
        fx(() -> grid.getCanvasView().rebuild());
        fx(() -> grid.getCanvasView().selectRowMatching(
                r -> r.getType() == FlatRow.RowType.ATTRIBUTE && "level".equals(r.getLabel())));
        assertEquals(List.of("junior", "senior"), names(panel.valuesList().getItems()));
        fx(() -> {
            panel.valuesList().getSelectionModel().select(1);
            doubleClick(panel.valuesList());
        });
        assertEquals("senior", person.getAttribute("level"));

        fx(() -> grid.setSidePanelShown(false));
        assertNull(grid.lookup("#grid-side-split"));
        assertFalse(grid.isSidePanelShown());
    }
}
