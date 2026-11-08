package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The way a user works with a large schema, on the real {@link EditorHost}: open a file, switch to
 * the Graphic view, click a card open, edit inside it, undo.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphWorkflowTest {

    private static final long TIMEOUT_S = 60;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1200, 800));
        stage.show();
    }

    private static <T> T onFx(Callable<T> call) throws Exception {
        T result = WaitForAsyncUtils.asyncFx(call).get(TIMEOUT_S, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private XsdGraphView openInGraphicView(Path file) throws Exception {
        onFx(() -> {
            host.openFile(file);
            return null;
        });
        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> host.getActiveText().isPresent());
        onFx(() -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        XsdGraphView[] graph = new XsdGraphView[1];
        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> onFx(() -> {
            graph[0] = (XsdGraphView) host.lookupAll("*").stream()
                    .filter(XsdGraphView.class::isInstance).findFirst().orElse(null);
            return graph[0] != null && graph[0].getRootNode() != null;
        }));
        return graph[0];
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

    /** @return whether every card above this one is open, and it can be selected */
    private static boolean isReachable(VisualNode card) {
        for (VisualNode above = card.getParent(); above != null; above = above.getParent()) {
            if (!above.isExpanded()) {
                return false;
            }
        }
        return card.getModelNode() != null;
    }

    private static boolean isRenamable(Object model) {
        return model instanceof XsdAttribute attribute && attribute.getRef() == null
                || model instanceof XsdElement element && element.getRef() == null;
    }

    /** Waits for a repaint that placed the card's button, then clicks it like the mouse would. */
    private void clickExpandButton(XsdGraphView graph, VisualNode card) throws Exception {
        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> onFx(() -> button(card) != null));
        onFx(() -> {
            double[] button = button(card);
            Point2D scene = graph.scenePointOf(button[0], button[1]);
            Node canvas = graph.lookupAll("*").stream().filter(Canvas.class::isInstance).findFirst().orElseThrow();
            canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, scene.getX(), scene.getY(),
                    scene.getX(), scene.getY(), MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, true, null));
            return null;
        });
    }

    private static double[] button(VisualNode card) {
        for (double y = card.getY(); y < card.getY() + card.getHeight(); y++) {
            for (double x = card.getX(); x < card.getX() + card.getWidth(); x++) {
                if (card.expandButtonContainsPoint(x, y)) {
                    return new double[] {x, y};
                }
            }
        }
        return null;
    }

    private static boolean hasCard(XsdGraphView graph, String label) {
        return cards(graph.getRootNode()).stream().anyMatch(card -> label.equals(card.getLabel()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/test/resources/schema/include_files/FundsXML4.xsd",
            "src/test/resources/xsd/real-world/jats-nlm-nih-gov-jats-archivearticle1-4/jats.nlm.nih.gov/archiving/1.4/xsd/JATS-archivearticle1-4.xsd"})
    void aDeferredCardOpensByClickAndStaysOpenAcrossAnUndoneEdit(String file) throws Exception {
        // src/test/resources/xsd/real-world/ is a local corpus (.gitignore); a fresh checkout (CI) has none of it
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(Path.of(file)),
                "real-world schema corpus not present: " + file);
        XsdGraphView graph = openInGraphicView(Path.of(file).toAbsolutePath());
        VisualNode deferred = onFx(() -> {
            List<VisualNode> open = cards(graph.getRootNode());
            return open.stream().filter(VisualNode::hasUnloadedChildren)
                    .filter(XsdGraphWorkflowTest::isReachable)
                    .findFirst().orElseThrow();
        });
        onFx(() -> {
            graph.selectModelNode(deferred.getModelNode());
            return null;
        });

        clickExpandButton(graph, deferred);

        assertTrue(deferred.isExpanded(), "the click opened " + deferred.getLabel());
        assertFalse(deferred.getChildren().isEmpty(), "the click loaded " + deferred.getLabel());

        // An edit rebuilds the tree: the opened card stays open, and undo brings the name back
        XsdNode loaded = onFx(() -> cards(deferred).stream()
                .filter(card -> card != deferred)
                .map(card -> card.getModelNode())
                .filter(XsdGraphWorkflowTest::isRenamable)
                .findFirst().orElseThrow());
        String name = loaded.getName();
        onFx(() -> graph.getEditorContext().getCommandManager()
                .executeCommand(new RenameNodeCommand(loaded, name + "Renamed")));
        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> onFx(() -> hasCard(graph, name + "Renamed")));

        onFx(() -> {
            host.undoActive();
            return null;
        });

        assertEquals(name, loaded.getName());
        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> onFx(() -> hasCard(graph, name)));
    }

    @Test
    void aMissingIncludeIsNamedAboveTheDiagram(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="gone.xsd"/>
                  <xs:element name="root" type="Lost"/>
                </xs:schema>
                """);
        XsdGraphView graph = openInGraphicView(main);

        WaitForAsyncUtils.waitFor(TIMEOUT_S, TimeUnit.SECONDS, () -> onFx(() -> {
            Node bar = graph.lookup("#unresolved-references");
            return bar != null && bar.isVisible();
        }));
        assertTrue(onFx(() -> cards(graph.getRootNode()).stream()
                .anyMatch(card -> "Unresolved type: Lost".equals(card.getLabel()))));
    }
}
