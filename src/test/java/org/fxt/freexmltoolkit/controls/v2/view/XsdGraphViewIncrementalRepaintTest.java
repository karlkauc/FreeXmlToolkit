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
 * P4: a paint-only change (selection) must repaint only the affected node regions,
 * not relayout and repaint the whole canvas. We assert via the render diagnostics
 * that selection triggers a regional repaint touching far fewer cards than a full draw.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewIncrementalRepaintTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Library">
                <xs:complexType><xs:sequence>
                  <xs:element name="Book1" type="xs:string"/>
                  <xs:element name="Book2" type="xs:string"/>
                  <xs:element name="Book3" type="xs:string"/>
                  <xs:element name="Book4" type="xs:string"/>
                  <xs:element name="Book5" type="xs:string"/>
                  <xs:element name="Book6" type="xs:string"/>
                  <xs:element name="Book7" type="xs:string"/>
                  <xs:element name="Book8" type="xs:string"/>
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
        graph.setViewportCullingEnabled(false); // render every node deterministically
        stage.setScene(new Scene(graph, 1100, 700));
        stage.show();
    }

    @Test
    void selectionRepaintsOnlyTheAffectedRegion() throws Exception {
        // Expand everything, then take a full-draw baseline.
        int full = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            expandAll(graph.getRootNode());
            graph.repaint(); // force a full redraw baseline
            return graph.getLastRenderedNodeCount();
        });
        assertFalse(graph.wasLastPaintRegional(), "baseline must be a full redraw");
        assertTrue(full >= 8, "a full draw of the expanded schema should render many cards, was " + full);

        // Now change the selection to a different leaf: a paint-only change.
        XsdNode book6 = find(schema, "Book6");
        int partial = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            VisualNode card = locate(graph.getRootNode(), book6);
            graph.getSelectionModel().select(card);
            return graph.getLastRenderedNodeCount();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(graph.wasLastPaintRegional(), "selection must trigger a regional repaint, not a full redraw");
        assertTrue(partial < full,
                "regional repaint must render fewer cards than a full draw (partial=" + partial + ", full=" + full + ")");
        assertTrue(partial <= full / 2,
                "regional repaint should touch only a small neighbourhood (partial=" + partial + ", full=" + full + ")");
    }

    private void expandAll(VisualNode node) {
        if (node == null) {
            return;
        }
        node.setExpanded(true);
        for (VisualNode child : node.getChildren()) {
            expandAll(child);
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
