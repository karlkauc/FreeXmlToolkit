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

    @Test
    void attributesAreRowsOfTheirOwnThatReportTheirElement() {
        XmlEditorContext ctx = context("<order id=\"1\" status=\"open\"><item>a</item></order>");
        XmlNode[] reported = {null};
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setOnSelectionChanged(n -> reported[0] = n);
            tree.setModel(ctx);
            return null;
        });
        TreeItem<XmlNode> root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot());
        var first = root.getChildren().get(0).getValue();
        var second = root.getChildren().get(1).getValue();
        assertTrue(first instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlAttribute a
                && "id".equals(a.getName()) && "1".equals(a.getValue()), "attributes come first");
        assertTrue(second instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlAttribute a
                && "status".equals(a.getName()));
        assertSame(root.getValue(), tree.ownerOf(first));
        assertTrue(ctx.getDocument().getRootElement().getChildren().stream()
                .noneMatch(c -> c instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlAttribute),
                "the stand-ins are not part of the model");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectAttribute((XmlElement) root.getValue(), "status");
            return null;
        });
        assertSame(root.getValue(), reported[0], "an attribute row reports its element");
    }
}
