package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.TreeItem;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link XsdTreeBuilder}, the UI-free conversion of an XsdNode model
 * into a JavaFX TreeItem tree for the virtualized Tree view (no toolkit needed).
 */
class XsdTreeBuilderTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void buildsTreeRootedAtTheSchema() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        TreeItem<XsdNode> root = XsdTreeBuilder.build(schema);

        assertSame(schema, root.getValue());
        assertFalse(root.getChildren().isEmpty(), "schema must have child nodes");
    }

    @Test
    void treeContainsAllModelElements() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        TreeItem<XsdNode> root = XsdTreeBuilder.build(schema);

        List<String> names = new ArrayList<>();
        collectNames(root, names);
        assertTrue(names.contains("root"), "expected element 'root' in tree");
        assertTrue(names.contains("item"), "expected element 'item' in tree");
    }

    @Test
    void nodesAreExpandedByDefault() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        TreeItem<XsdNode> root = XsdTreeBuilder.build(schema);
        assertTrue(root.isExpanded());
    }

    private void collectNames(TreeItem<XsdNode> item, List<String> out) {
        if (item.getValue() != null && item.getValue().getName() != null) {
            out.add(item.getValue().getName());
        }
        for (TreeItem<XsdNode> child : item.getChildren()) {
            collectNames(child, out);
        }
    }
}
