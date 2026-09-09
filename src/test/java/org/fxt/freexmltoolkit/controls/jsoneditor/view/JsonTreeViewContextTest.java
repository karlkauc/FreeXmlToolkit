package org.fxt.freexmltoolkit.controls.jsoneditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The JSON tree bound to a shared {@link JsonEditorContext} rebuilds on model changes while
 * keeping the selected node, and serves the shell search bar as a search target.
 */
@ExtendWith(ApplicationExtension.class)
class JsonTreeViewContextTest {

    private JsonTreeView tree;
    private JsonEditorContext context;

    @Start
    void start(Stage stage) {
        context = new JsonEditorContext();
        context.loadDocumentFromString("{\"a\": {\"b\": 1}, \"name\": \"Alice\"}");
        tree = new JsonTreeView();
        tree.setContext(context);
        stage.setScene(new Scene(tree, 400, 400));
        stage.show();
    }

    private JsonObject root() {
        return (JsonObject) context.getDocument().getRootValue();
    }

    @Test
    void modelChangesRebuildTheTreeKeepingSelection() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectNode(root().getProperty("name"));
            return null;
        });
        assertSame(root().getProperty("name"), tree.getSelectedNode());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            context.executeCommand(new AddPropertyCommand(root(), "z", new JsonPrimitive("new"), -1));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> tree.getSelectedNode() == root().getProperty("name")));
        assertSame(context.getDocument(), tree.getDocument(), "no re-parse: same document instance");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            context.executeCommand(new SetPrimitiveValueCommand((JsonPrimitive) root().getProperty("name"), "Bob"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Bob", ((JsonPrimitive) tree.getSelectedNode()).getValue());
    }

    @Test
    void servesAsSearchTarget() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.findAll("alice")));
        assertSame(root().getProperty("name"), tree.getSelectedNode());
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("b", true)));
        assertSame(root().getProperty("a").getChild(0), tree.getSelectedNode());
        assertEquals(0, WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.findAll("zzz")));
        tree.clearSearch();
    }
}
