package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
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
 * Drag-and-drop of cards on the graphic XSD canvas: the mouse handlers move (or, with the
 * shortcut key, copy) the node through one undoable command, without panning the view or
 * selecting the drop target, and respect the edit rules and shared types.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewDragDropTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="a" type="xs:string"/>
                    <xs:element name="b" type="xs:string"/>
                    <xs:element name="c" type="xs:string"/>
                    <xs:element name="box">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="inner" type="xs:string"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="addr1" type="AddressType"/>
                    <xs:element name="addr2" type="AddressType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="AddressType">
                <xs:sequence>
                  <xs:element name="street" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;
    private XsdEditorContext context;
    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        context = new XsdEditorContext(schema);
        context.setEditMode(true);
        graph = new XsdGraphView(context);
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    @AfterEach
    void resetDialogs() {
        EditRuleDialogs.setConfirmer(null);
        EditRuleDialogs.setBlockedHandler(null);
    }

    @Test
    void dragReordersWithoutPanningOrSelectingTheTarget() throws Exception {
        XsdNode a = find("a");
        reveal("a", "c");
        double[] scrollBefore = fx(() -> new double[]{scrollPane().getHvalue(), scrollPane().getVvalue()});

        drag("a", "c", 0.9, false);

        waitFor(() -> names(a.getParent()).subList(0, 3).equals(List.of("b", "c", "a")));
        double[] scrollAfter = fx(() -> new double[]{scrollPane().getHvalue(), scrollPane().getVvalue()});
        assertArrayEquals(scrollBefore, scrollAfter, 1e-9, "dragging a card must not pan the diagram");
        waitFor(() -> fx(() -> {
            VisualNode primary = context.getSelectionModel().getPrimarySelection();
            return primary != null && primary.getModelObject() == a;
        }));

        fx(() -> context.getCommandManager().undo());
        assertEquals(List.of("a", "b", "c"), names(a.getParent()).subList(0, 3), "one undo step");
    }

    @Test
    void shortcutDragCopiesIntoAnotherElement() throws Exception {
        XsdNode inner = find("inner");
        reveal("a", "box");

        drag("a", "box", 0.5, true);

        waitFor(() -> names(inner.getParent()).equals(List.of("inner", "a")));
        assertNotNull(find("a"), "the original stays");
        assertEquals("a", names(find("root").getChildren().getFirst().getChildren().getFirst()).getFirst());
    }

    @Test
    void droppingIntoASharedTypeAsksFirst() throws Exception {
        XsdNode street = find("street");
        List<String> asked = new ArrayList<>();
        EditRuleDialogs.setConfirmer((title, check) -> {
            asked.add(check.reason());
            return false;
        });
        reveal("a", "addr1");

        drag("a", "addr1", 0.5, false);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, asked.size());
        assertTrue(asked.getFirst().contains("AddressType"), asked.getFirst());
        assertEquals(List.of("street"), names(street.getParent()), "cancelled: nothing moved");

        EditRuleDialogs.setConfirmer((title, check) -> true);
        reveal("a", "addr1");
        drag("a", "addr1", 0.5, false);
        waitFor(() -> names(street.getParent()).equals(List.of("street", "a")));
    }

    @Test
    void refusedDropExplainsWhyAndChangesNothing() throws Exception {
        List<EditCheck> blocked = new ArrayList<>();
        EditRuleDialogs.setBlockedHandler((title, check) -> blocked.add(check));
        XsdNode a = find("a");
        reveal("a", "b");

        drag("a", "b", 0.5, false);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, blocked.size());
        assertTrue(blocked.getFirst().reason().contains("simple type"), blocked.getFirst().reason());
        assertEquals(List.of("a", "b", "c"), names(a.getParent()).subList(0, 3));
    }

    @Test
    void escapeCancelsTheDrag() throws Exception {
        XsdNode a = find("a");
        reveal("a", "c");

        fx(() -> {
            double[] from = center("a", 0.5);
            double[] to = center("c", 0.9);
            canvas().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, from, true, false));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, to, true, false));
            graph.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_RELEASED, to, false, false));
            return null;
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);

        assertEquals(List.of("a", "b", "c"), names(a.getParent()).subList(0, 3));
    }

    // ---------------------------------------------------------------------------------------

    private void drag(String from, String to, double targetShare, boolean shortcut) {
        fx(() -> {
            double[] start = center(from, 0.5);
            double[] end = center(to, targetShare);
            double[] mid = {(start[0] + end[0]) / 2, (start[1] + end[1]) / 2};
            canvas().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, start, true, shortcut));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, mid, true, shortcut));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, end, true, shortcut));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_RELEASED, end, false, shortcut));
            canvas().fireEvent(mouse(MouseEvent.MOUSE_CLICKED, end, false, shortcut));
            return null;
        });
    }

    /** A mouse event at canvas point {@code p}; dispatch recomputes x/y from the scene coordinates. */
    private MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double[] p,
                             boolean primaryDown, boolean shortcut) {
        javafx.geometry.Point2D scene = canvas().localToScene(p[0], p[1]);
        return new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(), MouseButton.PRIMARY, 1,
                false, shortcut, false, shortcut && System.getProperty("os.name", "").startsWith("Mac"),
                primaryDown, false, false, false, false, false, null);
    }

    /** Canvas coordinates of a point inside the card of {@code name}, {@code share} of its height down. */
    private double[] center(String name, double share) {
        VisualNode card = card(graph.getRootNode(), find(name));
        assertNotNull(card, "card of " + name);
        return graph.toCanvasPoint(card.getX() + card.getWidth() / 2, card.getY() + card.getHeight() * share);
    }

    private void reveal(String... names) {
        fx(() -> {
            for (String name : names) {
                graph.selectModelNode(find(name));
            }
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
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

    private Canvas canvas() {
        return findNode(graph, Canvas.class);
    }

    private ScrollPane scrollPane() {
        return findNode(graph, ScrollPane.class);
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

    private static List<String> names(XsdNode parent) {
        return parent.getChildren().stream().map(XsdNode::getName).toList();
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, condition);
    }
}
