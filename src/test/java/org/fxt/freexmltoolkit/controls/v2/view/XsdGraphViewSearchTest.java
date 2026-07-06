package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the {@code XmlSearchTarget} implementation of
 * {@link XsdGraphView}: find/next/previous cycles the match list and reveals
 * each match (expanding collapsed ancestors) via {@code selectModelNode}.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewSearchTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Company">
                <xs:complexType><xs:sequence>
                  <xs:element name="Address">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Street" type="xs:string"/>
                      <xs:element name="StreetNumber" type="xs:int"/>
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

    private String selectedName() {
        VisualNode selected = graph.getSelectionModel().getPrimarySelection();
        return selected != null && selected.getModelNode() != null
                ? selected.getModelNode().getName() : null;
    }

    @Test
    void findSelectsAndCyclesForward() {
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("street", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Street", selectedName());

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("street", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("StreetNumber", selectedName());

        // Wrap-around back to the first match.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("street", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Street", selectedName());
    }

    @Test
    void findBackwardWrapsToLastMatch() {
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("street", false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("StreetNumber", selectedName());
    }

    @Test
    void findRevealsMatchInCollapsedSubtree() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            collapse(graph.getRootNode());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("Street", true)));
        WaitForAsyncUtils.waitForFxEvents();

        VisualNode selected = graph.getSelectionModel().getPrimarySelection();
        assertNotNull(selected, "match must be selected");
        assertEquals("Street", selected.getModelNode().getName());
        for (VisualNode a = selected.getParent(); a != null; a = a.getParent()) {
            assertTrue(a.isExpanded(), "every ancestor must be expanded so the match is visible");
        }
    }

    @Test
    void findAllCountsAndSelectsFirst() {
        int count = WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.findAll("street"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, count);
        assertEquals("Street", selectedName());
    }

    @Test
    void noMatchReturnsFalseAndZero() {
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.find("zzz-nothing", true)));
        assertEquals(0, (int) WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.findAll("zzz-nothing")));
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
}
