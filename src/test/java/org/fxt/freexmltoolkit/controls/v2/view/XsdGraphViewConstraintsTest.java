package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.beans.property.BooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The display option in a real view: it reaches open diagrams and leaves their state alone. */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewConstraintsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="library">
                <xs:complexType><xs:sequence>
                  <xs:element name="book" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence></xs:complexType>
                <xs:key name="bookKey"><xs:selector xpath="book"/><xs:field xpath="@isbn"/></xs:key>
              </xs:element>
            </xs:schema>
            """;

    private final BooleanProperty option = XsdDiagramOptions.showConstraintsProperty();
    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        option.set(true);
        graph = new XsdGraphView(new XsdEditorContext(new XsdNodeFactory().fromString(XSD)));
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    @AfterEach
    void restoreOption() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> option.set(true));
    }

    @Test
    void switchingResizesTheCardAndKeepsItsState() {
        VisualNode root = graph.getRootNode();
        assertTrue(root.isExpanded());
        double shown = root.getHeight();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> option.set(false));
        double hidden = root.getHeight();

        assertTrue(hidden < shown, hidden + " < " + shown);
        assertEquals(root, graph.getRootNode(), "the tree is not rebuilt");
        assertTrue(root.isExpanded());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> option.set(true));
        assertEquals(shown, root.getHeight());
    }

    @Test
    void theTooltipAppearsOverTheRowsOnly() {
        VisualNode root = graph.getRootNode();
        double x = root.getX() + 20;

        moveTo(x, root.getY() + 10);
        assertEquals(null, installedTooltip(), "over the header");

        moveTo(x, root.getY() + root.getHeight() - 8);
        Tooltip tooltip = (Tooltip) installedTooltip();
        assertEquals("key bookKey\nselector: book\nfields: @isbn", tooltip.getText());

        moveTo(x, root.getY() + 10);
        assertEquals(null, installedTooltip(), "gone again");
    }

    private Object installedTooltip() {
        return canvas().getProperties().get("javafx.scene.control.Tooltip");
    }

    private Canvas canvas() {
        return (Canvas) graph.lookup("Canvas");
    }

    private void moveTo(double x, double y) {
        Point2D scene = graph.scenePointOf(x, y);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> canvas().fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED,
                scene.getX(), scene.getY(), scene.getX(), scene.getY(), MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null)));
    }
}
