package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The mouse path: a click on the expand button of a deferred card, in the real view. */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewLazyExpandClickTest {

    private static final Path FUNDS_XML =
            Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");

    private XsdGraphView graph;
    private XsdEditorContext context;

    @Start
    void start(Stage stage) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(
                Files.readString(FUNDS_XML), FUNDS_XML, FUNDS_XML.toAbsolutePath().getParent());
        context = new XsdEditorContext(schema);
        graph = new XsdGraphView(context);
        stage.setScene(new Scene(graph, 1200, 800));
        stage.show();
    }

    private static <T> T onFx(Callable<T> call) throws Exception {
        T result = WaitForAsyncUtils.asyncFx(call).get();
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private static List<VisualNode> cards(VisualNode root) {
        List<VisualNode> cards = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            cards.add(node);
            node.getChildren().forEach(pending::push);
        }
        return cards;
    }

    private VisualNode card(String label) {
        return cards(graph.getRootNode()).stream()
                .filter(n -> label.equals(n.getLabel())).findFirst().orElseThrow();
    }

    private void click(double x, double y) throws Exception {
        onFx(() -> {
            Point2D scene = graph.scenePointOf(x, y);
            Node canvas = graph.lookupAll("*").stream().filter(Canvas.class::isInstance).findFirst().orElseThrow();
            canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, scene.getX(), scene.getY(),
                    scene.getX(), scene.getY(), MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, true, null));
            return null;
        });
    }

    /** Waits for a repaint that placed the card's button, then clicks it. */
    private void clickExpandButton(VisualNode card) throws Exception {
        double[] button = null;
        for (int attempt = 0; attempt < 50 && button == null; attempt++) {
            button = onFx(() -> findButton(card));
            if (button == null) {
                WaitForAsyncUtils.sleep(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
        assertNotNull(button, "the button was painted");
        click(button[0], button[1]);
    }

    private static double[] findButton(VisualNode card) {
        for (double y = card.getY(); y < card.getY() + card.getHeight(); y++) {
            for (double x = card.getX(); x < card.getX() + card.getWidth(); x++) {
                if (card.expandButtonContainsPoint(x, y)) {
                    return new double[] {x, y};
                }
            }
        }
        return null;
    }

    @Test
    void clickLoadsTheCardAndKeepsItOpenAcrossAnEdit() throws Exception {
        VisualNode card = onFx(() -> card("AssetMasterData"));
        assertTrue(card.hasUnloadedChildren(), "the card is past the node budget");
        onFx(() -> {
            graph.selectModelNode(card.getModelNode());
            return null;
        });

        clickExpandButton(card);

        assertTrue(card.isExpanded());
        assertFalse(card.getChildren().isEmpty());
        VisualNode loaded = onFx(() -> cards(card).stream()
                .filter(n -> n != card && n.getModelNode() instanceof XsdElement).findFirst().orElseThrow());
        assertTrue(onFx(() -> {
            graph.selectModelNode(loaded.getModelNode());
            return graph.getSelectionModel().getPrimarySelection() == loaded;
        }), "the loaded card is known to the view");

        String renamed = loaded.getLabel() + "X";
        onFx(() -> context.getCommandManager()
                .executeCommand(new RenameNodeCommand(loaded.getModelNode(), renamed)));
        WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS, () -> {
            VisualNode after = onFx(() -> card("AssetMasterData"));
            return after.isExpanded() && onFx(() -> cards(after).stream()
                    .anyMatch(n -> renamed.equals(n.getLabel())));
        });
    }
}
