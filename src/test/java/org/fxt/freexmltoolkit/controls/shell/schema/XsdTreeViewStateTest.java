package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the XSD Tree view preserves the user's expand/collapse state across a
 * re-render (e.g. after a structured edit), per matrix #50.
 */
@ExtendWith(ApplicationExtension.class)
class XsdTreeViewStateTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="a" type="xs:string"/>
                  <xs:element name="b" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdTreeView tree;

    @Start
    void start(Stage stage) {
        tree = new XsdTreeView();
        stage.setScene(new Scene(tree, 500, 600));
        stage.show();
    }

    @Test
    void preservesCollapsedStateAcrossReRender() throws Exception {
        XsdSchema schema = (XsdSchema) new XsdNodeFactory().fromString(XSD);

        // Render, then collapse the first non-leaf below the root.
        String collapsedId = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setSchema(schema);
            TreeItem<XsdNode> target = firstCollapsibleDescendant(tree.getRoot());
            assertNotNull(target, "test fixture must have a collapsible node");
            target.setExpanded(false);
            return target.getValue().getId();
        });

        // Re-render the same model (as a structured edit would).
        Boolean stillCollapsed = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setSchema(schema);
            TreeItem<XsdNode> again = findById(tree.getRoot(), collapsedId);
            assertNotNull(again, "node must still exist after re-render");
            return again.isExpanded();
        });

        assertFalse(stillCollapsed, "collapsed node must remain collapsed after re-render (matrix #50)");
    }

    private TreeItem<XsdNode> firstCollapsibleDescendant(TreeItem<XsdNode> item) {
        if (item == null) {
            return null;
        }
        for (TreeItem<XsdNode> child : item.getChildren()) {
            if (!child.isLeaf()) {
                return child;
            }
            TreeItem<XsdNode> deeper = firstCollapsibleDescendant(child);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private TreeItem<XsdNode> findById(TreeItem<XsdNode> item, String id) {
        if (item == null) {
            return null;
        }
        if (item.getValue() != null && id.equals(item.getValue().getId())) {
            return item;
        }
        for (TreeItem<XsdNode> child : item.getChildren()) {
            TreeItem<XsdNode> found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
