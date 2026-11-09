package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.editor.menu.XsdContextMenuFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The content of a named complex type is drawn in a titled frame, is read-only in
 * place and leads to the type's editor.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewTypeFrameTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="office">
                <xs:complexType><xs:sequence>
                  <xs:element name="Address_EU" type="EUAddress"/>
                  <xs:element name="Phone" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="EUAddress">
                <xs:sequence>
                  <xs:element name="street" type="xs:string"/>
                  <xs:element name="city" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;
    private XsdEditorContext context;
    private XsdGraphView graph;
    private final List<XsdComplexType> opened = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        context = new XsdEditorContext(schema);
        context.setEditMode(true);
        graph = new XsdGraphView(context);
        graph.setOpenComplexTypeEditorCallback(opened::add);
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    @AfterEach
    void resetDialogs() {
        EditRuleDialogs.setBlockedHandler(null);
    }

    @Test
    void frameEnclosesTheTypesContentAndClearsTheNextSibling() throws Exception {
        reveal("street");
        fx(() -> {
            VisualNode address = card("Address_EU");
            TypeFrame frame = address.getTypeFrames().getFirst();
            assertTrue(frame.getWidth() > 0 && frame.getHeight() > 0);
            for (VisualNode child : address.getChildren()) {
                assertInside(frame, child);
                for (VisualNode grandChild : child.getChildren()) {
                    assertInside(frame, grandChild);
                }
            }
            VisualNode street = card("street");
            assertTrue(street.getY() >= frame.getY() + XsdNodeRenderer.FRAME_HEADER - 0.5,
                    "the title row stays free");
            VisualNode phone = card("Phone");
            assertTrue(phone.getY() >= frame.getY() + frame.getHeight(), "Phone lies below the frame");
            assertTrue(card("Address_EU").getX() + card("Address_EU").getWidth() <= frame.getX(),
                    "the element card stays outside its type's frame");
            return null;
        });
    }

    @Test
    void deleteOnANodeInsideTheFrameIsRefused() throws Exception {
        List<EditCheck> blocked = new ArrayList<>();
        EditRuleDialogs.setBlockedHandler((title, check) -> blocked.add(check));
        XsdNode street = find("street");
        reveal("street");

        fx(() -> {
            graph.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE,
                    false, false, false, false));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, blocked.size());
        assertTrue(blocked.getFirst().reason().contains("EUAddress"), blocked.getFirst().reason());
        assertNotNull(street.getParent(), "still in the type");
        assertEquals(2, street.getParent().getChildren().size());
    }

    @Test
    void doubleClickOnTheFrameTitleOpensTheType() throws Exception {
        reveal("street");
        double[] title = fx(() -> {
            TypeFrame frame = card("Address_EU").getTypeFrames().getFirst();
            assertSame(frame, graph.typeFrameTitleAt(frame.getX() + 20, frame.getY() + 8));
            return graph.toCanvasPoint(frame.getX() + 20, frame.getY() + 8);
        });

        fx(() -> {
            canvas().fireEvent(click(title, 2));
            return null;
        });

        assertEquals(1, opened.size());
        assertEquals("EUAddress", opened.getFirst().getName());
    }

    @Test
    void contextMenuOfAFramedNodeLeadsToTheTypeAndChangesNothing() throws Exception {
        reveal("street");
        fx(() -> {
            XsdContextMenuFactory factory = new XsdContextMenuFactory(context);
            factory.setOpenComplexTypeEditorCallback(opened::add);

            ContextMenu menu = factory.createContextMenu(card("street"));
            MenuItem first = menu.getItems().getFirst();
            assertEquals("Edit Type 'EUAddress'", first.getText());
            assertFalse(first.isDisable());
            assertTrue(item(menu, "Delete").isDisable());
            assertTrue(item(menu, "Rename").isDisable());
            assertFalse(item(menu, "Copy Node").isDisable(), "copying changes nothing");
            first.fire();
            assertEquals("EUAddress", opened.getFirst().getName());

            ContextMenu elementMenu = factory.createContextMenu(card("Address_EU"));
            assertFalse(item(elementMenu, "Delete").isDisable(), "the element itself is editable");
            Menu add = (Menu) elementMenu.getItems().stream()
                    .filter(i -> i instanceof Menu m && m.getText().startsWith("Add")).findFirst().orElseThrow();
            assertTrue(add.isDisable(), "new content would go into the type");
            return null;
        });
    }

    @Test
    void regionalRepaintKeepsTheFrame() throws Exception {
        reveal("street");
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS); // let pending full redraws settle
        WaitForAsyncUtils.waitForFxEvents();
        Color sample = fx(() -> {
            // A selection change repaints only the affected regions; sample within the same pulse
            graph.getSelectionModel().select(card("city"));
            assertTrue(graph.wasLastPaintRegional());
            TypeFrame frame = card("Address_EU").getTypeFrames().getFirst();
            VisualNode street = card("street");
            double[] p = graph.toCanvasPoint(frame.getX() + frame.getWidth() - 5, street.getY() + street.getHeight() / 2);
            var image = canvas().snapshot(null, null);
            return image.getPixelReader().getColor((int) p[0], (int) p[1]);
        });

        Color expected = DesignTokens.ColorToken.TYPE_FRAME_BG.color(DesignTokens.currentTheme());
        assertEquals(expected.getRed(), sample.getRed(), 0.03, sample.toString());
        assertEquals(expected.getGreen(), sample.getGreen(), 0.03, sample.toString());
        assertEquals(expected.getBlue(), sample.getBlue(), 0.03, sample.toString());
    }

    // ---------------------------------------------------------------------------------------

    private static void assertInside(TypeFrame frame, VisualNode node) {
        assertTrue(node.getX() >= frame.getX() && node.getX() + node.getWidth() <= frame.getX() + frame.getWidth()
                        && node.getY() >= frame.getY() && node.getY() + node.getHeight() <= frame.getY() + frame.getHeight(),
                node.getLabel() + " inside " + frame);
    }

    private static MenuItem item(ContextMenu menu, String text) {
        return menu.getItems().stream().filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
    }

    private MouseEvent click(double[] p, int clickCount) {
        javafx.geometry.Point2D scene = canvas().localToScene(p[0], p[1]);
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                MouseButton.PRIMARY, clickCount, false, false, false, false,
                false, false, false, false, false, true, null);
    }

    private VisualNode card(String name) {
        VisualNode card = card(graph.getRootNode(), find(name));
        assertNotNull(card, "card of " + name);
        return card;
    }

    private static VisualNode card(VisualNode node, XsdNode model) {
        if (node.getModelObject() == model) {
            return node;
        }
        if (node.isExpanded()) {
            for (VisualNode child : node.getChildren()) {
                VisualNode found = card(child, model);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void reveal(String name) {
        fx(() -> {
            graph.selectModelNode(find(name));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(200, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Canvas canvas() {
        return findNode(graph, Canvas.class);
    }

    private static <T> T findNode(Parent parent, Class<T> type) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof ScrollPane sp && sp.getContent() instanceof Parent content) {
                if (type.isInstance(content)) {
                    return type.cast(content);
                }
                T found = findNode(content, type);
                if (found != null) {
                    return found;
                }
            }
            if (child instanceof Parent p) {
                T found = findNode(p, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private XsdNode find(String name) {
        return findIn(schema, name);
    }

    private static XsdNode findIn(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = findIn(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }
}
