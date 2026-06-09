package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the XML-instance tree renders the shared {@link XmlNode} model's element structure.
 */
@ExtendWith(ApplicationExtension.class)
class XmlInstanceTreeViewTest {

    private XmlInstanceTreeView tree;

    @Start
    void start(Stage stage) {
        tree = new XmlInstanceTreeView();
        stage.setScene(new Scene(tree, 400, 500));
        stage.show();
    }

    private XmlEditorContext context(String xml) {
        XmlEditorContext ctx = new XmlEditorContext();
        ctx.loadDocumentFromString(xml);
        return ctx;
    }

    @Test
    void rendersElementTree() {
        XmlEditorContext ctx = context("<order id=\"1\"><item>a</item><item>b</item></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setModel(ctx);
            return null;
        });
        TreeItem<XmlNode> root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot());
        assertNotNull(root);
        assertEquals("order", ((XmlElement) root.getValue()).getQualifiedName());
        long elementChildren = root.getChildren().stream()
                .filter(c -> c.getValue() instanceof XmlElement).count();
        assertEquals(2, elementChildren, "two <item> children expected");
    }

    @Test
    void nullModelClearsTheTree() {
        XmlEditorContext ctx = context("<order id=\"1\"><item>a</item></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setModel(ctx);
            return null;
        });
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setModel(null);
            return null;
        });
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot()));
    }
}
