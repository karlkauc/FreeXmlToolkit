package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * All cards of a sibling group share the width of the widest one (Figma 07.6b). The layout used
 * to preset that width and then overwrite it with each card's own width.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewUniformWidthTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="ControlData">
                <xs:complexType><xs:sequence>
                  <xs:element name="Id" type="xs:string"/>
                  <xs:element name="DocumentGeneratedWithAVeryLongName" type="xs:dateTime"/>
                  <xs:element name="Date" type="xs:date" minOccurs="0"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        graph = new XsdGraphView(new XsdEditorContext(new XsdNodeFactory().fromString(XSD)));
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    @Test
    void siblingsShareTheWidestWidth() {
        VisualNode sequence = findSequence(graph.getRootNode());
        assertNotNull(sequence, "an expanded sequence");
        List<VisualNode> cards = sequence.getChildren();
        assertEquals(3, cards.size());

        double width = cards.get(1).getWidth();
        for (VisualNode card : cards) {
            assertEquals(width, card.getWidth(), card.getLabel());
            assertEquals(cards.get(0).getX(), card.getX(), "one column");
        }
        assertTrue(width > 160, "the long name widens the group: " + width);
    }

    @Test
    void theCompositorKeepsItsSize() {
        VisualNode sequence = findSequence(graph.getRootNode());
        assertNotNull(sequence);
        assertEquals(30, sequence.getWidth());
        assertEquals(30, sequence.getHeight());
    }

    private static VisualNode findSequence(VisualNode node) {
        if (node == null) {
            return null;
        }
        if (node.getType() == NodeWrapperType.SEQUENCE && node.isExpanded()) {
            return node;
        }
        for (VisualNode child : node.getChildren()) {
            VisualNode found = findSequence(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
