package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * P3: {@link XsdGraphView#selectModelNode(XsdNode)} must reveal a node that lives
 * inside a collapsed subtree — expanding its ancestors and selecting it — not just
 * silently select a hidden card.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewRevealTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Company">
                <xs:complexType><xs:sequence>
                  <xs:element name="Address">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Street" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdGraphView graph;
    private XsdSchema schema;

    @Start
    void start(Stage stage) throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        graph = new XsdGraphView(schema);
        stage.setScene(new Scene(graph, 900, 600));
        stage.show();
    }

    @Test
    void revealExpandsAncestorsAndSelectsTheDeepNode() throws Exception {
        XsdNode street = find(schema, "Street");
        assertNotNull(street, "test fixture must contain the deep node");

        // Collapse the whole tree so the deep node is hidden.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            collapse(graph.getRootNode());
            return null;
        });
        VisualNode before = locate(graph.getRootNode(), street);
        assertNotNull(before, "the deep node's card exists (FULL build mode)");
        assertFalse(before.getParent().isExpanded(), "precondition: the parent is collapsed");

        // Reveal it.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            graph.selectModelNode(street);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        VisualNode selected = graph.getSelectionModel().getPrimarySelection();
        assertNotNull(selected, "the deep node must be selected after reveal");
        assertSame(street, selected.getModelNode(), "the selected card must be the deep node");
        for (VisualNode a = selected.getParent(); a != null; a = a.getParent()) {
            assertTrue(a.isExpanded(), "every ancestor must be expanded so the node is visible");
        }
    }

    private void collapse(VisualNode node) {
        if (node == null) {
            return;
        }
        node.setExpanded(false);
        for (VisualNode child : node.getChildren()) {
            collapse(child);
        }
    }

    private VisualNode locate(VisualNode node, XsdNode model) {
        if (node == null) {
            return null;
        }
        if (node.getModelNode() == model) {
            return node;
        }
        for (VisualNode child : node.getChildren()) {
            VisualNode found = locate(child, model);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
