package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.xmledit.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A text change that does not come from the Tree / Graphic view itself (a panel action's text edits, a quick
 * fix, the editor's undo) shows in that view at once: the model is parsed again, and the grid keeps its
 * selection.
 */
@ExtendWith(ApplicationExtension.class)
class EditorTabTextChangeRefreshTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <order>
              <customer>Ann</customer>
              <items>
                <item id="1">
                  <name>Pen</name>
                </item>
                <item id="2">
                  <name>Ink</name>
                </item>
              </items>
            </order>
            """;

    private static final String JSON = """
            {
              "name": "Alice",
              "items": [
                {"id": 1, "n": "a"},
                {"id": 2, "n": "b"}
              ]
            }
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new javafx.scene.Scene(host, 900, 600));
        stage.show();
    }

    private EditorTab open(String text, EditorFileType type, String name, ViewMode mode) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openGeneratedDocument(text, type, name));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> !t.isEmpty()).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(mode);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> (EditorTab) host.lookupAll(".tab-pane").stream()
                .map(n -> ((javafx.scene.control.TabPane) n).getSelectionModel().getSelectedItem())
                .filter(EditorTab.class::isInstance).findFirst().orElseThrow());
    }

    /** Replaces the first {@code from} in the active text by {@code to}, the way panel actions edit it. */
    private void edit(String from, String to) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            String text = host.getActiveText().orElseThrow();
            int at = text.indexOf(from);
            assertTrue(at >= 0, from);
            return host.replaceActiveTextEdits(List.of(new TextEdit(at, at + from.length(), to)));
        });
    }

    /** The element's name, attributes and text, as a compact signature. */
    private static String serialized(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> element.getName() + element.getAttributes()
                + element.getChildElements().stream().map(c -> c.getName() + "=" + c.getTextContent()).toList());
    }

    private String gridText(EditorTab tab) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.xmlGridView.getCanvasView().getAdapter().serialize());
    }

    @Test
    void theXmlGridShowsTextEditsAndKeepsItsSelection() throws Exception {
        EditorTab tab = open(XML, EditorFileType.XML, "order.xml", ViewMode.GRAPHIC);
        var before = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.xmlGridView.getCanvasView());
        assertNotNull(before);
        // select the second item (a row of the items table) in the grid
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var root = tab.xmlEditorContext.getDocument().getRootElement();
            var items = root.getChildElements().get(1);
            var name = items.getChildElements().get(1).getChildElements().getFirst();
            return before.revealProblem(name, null);
        });
        WaitForAsyncUtils.waitForFxEvents();
        var chosen = (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement)
                WaitForAsyncUtils.waitForAsyncFx(2000, before::getSelectedNode);
        assertNotNull(chosen);

        edit("<name>Ink</name>", "<name>Quill</name>");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> gridText(tab).contains("Quill"));
        var after = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.xmlGridView.getCanvasView());
        assertNotSame(before, after, "the grid shows the parsed text");
        var selected = (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement)
                WaitForAsyncUtils.waitForAsyncFx(2000, after::getSelectedNode);
        assertNotNull(selected, "the selection is kept");
        assertNotSame(chosen, selected, "a node of the new model");
        assertEquals(chosen.getName(), selected.getName());
        assertEquals(serialized(chosen).replace("Ink", "Quill"), serialized(selected), "the same place in the document");
        assertEquals(ViewMode.GRAPHIC, tab.viewMode);

        // the editor's undo takes the text back - the grid follows
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> gridText(tab).contains("Ink"));
        assertTrue(host.getActiveText().orElseThrow().contains("<name>Ink</name>"));
    }

    @Test
    void gridEditsDoNotParseTheTextAgain() throws Exception {
        EditorTab tab = open(XML, EditorFileType.XML, "order.xml", ViewMode.GRAPHIC);
        var canvas = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.xmlGridView.getCanvasView());
        var context = tab.xmlEditorContext;
        // an edit of the model written to the text, the way the grid's own edits are
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var customer = context.getDocument().getRootElement().getChildElements().getFirst();
            return tab.editXml(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand(
                    customer, "Bob"));
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> canvas == tab.xmlGridView.getCanvasView()),
                "the grid's own edit keeps its canvas");
        assertTrue(context == tab.xmlEditorContext, "and its model (undo history)");
    }

    @Test
    void theXmlTreeShowsTextEdits() throws Exception {
        EditorTab tab = open(XML, EditorFileType.XML, "order.xml", ViewMode.TREE);
        edit("<customer>Ann</customer>", "<customer>Bea</customer>");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.xmlEditorContext != null && "Bea".equals(tab.xmlEditorContext.getDocument().getRootElement()
                        .getChildElements().getFirst().getTextContent())));
        assertEquals(ViewMode.TREE, tab.viewMode);
    }

    @Test
    void theJsonGridShowsTextEdits() throws Exception {
        EditorTab tab = open(JSON, EditorFileType.JSON, "data.json", ViewMode.GRAPHIC);
        edit("\"Alice\"", "\"Bob\"");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.jsonGridView.getCanvasView().getAdapter().serialize().contains("Bob")));
        assertEquals(ViewMode.GRAPHIC, tab.viewMode);
    }
}
