package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import org.w3c.dom.Node;

/**
 * Verifies the XML-instance tree renders an XML document's element/text structure.
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

    @Test
    void rendersElementTree() {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tree.setXml("<order id=\"1\"><item>a</item><item>b</item></order>"));
        assertTrue(ok);
        TreeItem<Node> root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot());
        assertNotNull(root);
        assertEquals("order", root.getValue().getNodeName());
        // two <item> element children (whitespace text nodes ignored)
        long elementChildren = root.getChildren().stream()
                .filter(c -> c.getValue().getNodeType() == Node.ELEMENT_NODE).count();
        assertEquals(2, elementChildren, "two <item> children expected");
    }

    @Test
    void invalidXmlClearsTheTree() {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setXml("<not-closed>"));
        assertFalse(ok);
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot()));
    }
}
