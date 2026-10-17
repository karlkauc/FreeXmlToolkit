package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator;
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
 * {@link XsdGraphView#revealModelPath(List)}: a named type used twice has its child cards twice in
 * the diagram (sharing one model node); revealing an instance path selects the occurrence under the
 * right parent, expands every ancestor and loads lazy subtrees along the way.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewRevealPathTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="StreetType">
                <xs:sequence><xs:element name="number" type="xs:int"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="AddressType">
                <xs:sequence><xs:element name="street" type="StreetType"/></xs:sequence>
              </xs:complexType>
              <xs:element name="person">
                <xs:complexType><xs:sequence>
                  <xs:element name="home" type="AddressType"/>
                  <xs:element name="work" type="AddressType"/>
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
    void selectsTheOccurrenceUnderTheRightParent() throws Exception {
        List<XsdNode> path = XsdInstanceLocator.locateElement(schema, List.of("person", "work", "street", "number"))
                .orElseThrow().particlePath();
        assertEquals(4, path.size());

        boolean exact = WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.revealModelPath(path));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(exact, "every step of the path has a card");

        VisualNode selected = graph.getSelectionModel().getPrimarySelection();
        assertNotNull(selected);
        assertSame(path.get(3), selected.getModelNode(), "the number card is selected");
        assertEquals("street", nearestElement(selected).getModelNode().getName());
        assertEquals("work", nearestElement(nearestElement(selected)).getModelNode().getName(),
                "the occurrence under work, not the first one under home");
        for (VisualNode a = selected.getParent(); a != null; a = a.getParent()) {
            assertTrue(a.isExpanded(), "every ancestor is expanded so the card is visible");
        }
    }

    @Test
    void fallsBackToTheDeepestCardReached() throws Exception {
        List<XsdNode> path = XsdInstanceLocator.locateElement(schema, List.of("person", "home")).orElseThrow()
                .particlePath();
        XsdNode unrelated = schema.getChildren().get(0); // StreetType: never a card under home
        boolean exact = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> graph.revealModelPath(List.of(path.get(0), path.get(1), unrelated)));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(exact);
        assertSame(path.get(1), graph.getSelectionModel().getPrimarySelection().getModelNode());
    }

    /** @return the closest ancestor card backed by an element */
    private static VisualNode nearestElement(VisualNode card) {
        VisualNode n = card.getParent();
        while (n != null && n.getType() != XsdNodeRenderer.NodeWrapperType.ELEMENT) {
            n = n.getParent();
        }
        return n;
    }
}
