package org.fxt.freexmltoolkit.controls.shell;

import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-2 feasibility spike for the new virtualized Tree/Graphic+Grid renderer
 * (decision D2). It does <b>not</b> build the production renderer; it validates,
 * with measurements, the three riskiest assumptions on a large model:
 * <ol>
 *   <li><b>Virtualization</b> — only cells in the viewport are materialized,
 *       independent of total node count.</li>
 *   <li><b>Fast expand</b> — expanding a node with many children stays well
 *       under the budget (≤100&nbsp;ms), because only visible rows re-layout.</li>
 *   <li><b>Embedded grid cells</b> — a single virtualized row can host a
 *       composite control (an embedded {@link TableView}) for repeating
 *       same-type siblings (the XMLSpy-style grid), without breaking
 *       virtualization.</li>
 * </ol>
 * Conclusions are recorded in {@code 2026-05-29-ui-rebuild-plan.md}.
 */
@ExtendWith(ApplicationExtension.class)
class VirtualizedRendererSpikeTest {

    /** A node in the spike model: most are plain; some are "repeating groups". */
    private record SpikeNode(String label, boolean repeatingGroup) {
    }

    private static final int TOP_LEVEL = 50;
    private static final int CHILDREN_PER = 100; // 50 * 100 = 5000 leaf nodes
    private static final double VIEWPORT_HEIGHT = 760;

    private TreeView<SpikeNode> tree;
    private final AtomicInteger cellsCreated = new AtomicInteger();

    @Start
    void start(Stage stage) {
        TreeItem<SpikeNode> root = new TreeItem<>(new SpikeNode("schema", false));
        root.setExpanded(true);
        for (int i = 0; i < TOP_LEVEL; i++) {
            // every 10th top-level node is a repeating group (embedded grid candidate)
            TreeItem<SpikeNode> group = new TreeItem<>(new SpikeNode("element[" + i + "]", i % 10 == 0));
            for (int j = 0; j < CHILDREN_PER; j++) {
                group.getChildren().add(new TreeItem<>(new SpikeNode("field[" + i + "." + j + "]", false)));
            }
            root.getChildren().add(group);
        }

        tree = new TreeView<>(root);
        tree.setFixedCellSize(24);
        tree.setCellFactory(tv -> new SpikeCell(cellsCreated));

        stage.setScene(new Scene(new VBox(tree), 600, VIEWPORT_HEIGHT));
        stage.show();
    }

    @Test
    void onlyViewportCellsAreMaterialized() {
        WaitForAsyncUtils.waitForFxEvents();
        int created = cellsCreated.get();
        int totalRootChildren = TOP_LEVEL; // 5050 nodes exist overall
        // A ~760px viewport at 24px rows shows ~32 rows; cell reuse keeps the
        // count to a small multiple regardless of the 5000+ node model.
        System.out.println("[spike] cells materialized for 5050-node model: " + created);
        assertTrue(created < 120,
                "virtualization failed: " + created + " cells created (expected « total)");
        assertTrue(created < totalRootChildren * 3L,
                "cell count must not scale with model size");
    }

    @Test
    void expandingALargeNodeIsWithinBudget() {
        WaitForAsyncUtils.waitForFxEvents();
        TreeItem<SpikeNode> root = tree.getRoot();
        TreeItem<SpikeNode> bigNode = root.getChildren().get(1); // 100 children

        long elapsedMs = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            long t0 = System.nanoTime();
            bigNode.setExpanded(true);
            tree.requestLayout();
            tree.layout();
            return (System.nanoTime() - t0) / 1_000_000;
        });
        WaitForAsyncUtils.waitForFxEvents();
        System.out.println("[spike] expand 100-child node: " + elapsedMs + " ms");
        assertTrue(elapsedMs < 100, "expand exceeded 100ms budget: " + elapsedMs + "ms");
    }

    @Test
    void aRowCanHostAnEmbeddedGridWithoutBreakingVirtualization() {
        WaitForAsyncUtils.waitForFxEvents();
        // Find a visible repeating-group cell and confirm it rendered an embedded table.
        boolean foundEmbeddedGrid = tree.lookupAll(".tree-cell").stream()
                .filter(n -> n instanceof SpikeCell)
                .map(n -> (SpikeCell) n)
                .anyMatch(c -> c.getGraphic() instanceof TableView);
        assertTrue(foundEmbeddedGrid,
                "expected at least one repeating-group row to host an embedded grid");
        // Virtualization still holds with composite cells present.
        assertTrue(cellsCreated.get() < 120, "embedded grids must not break virtualization");
    }

    /** Cell that renders a label, and an embedded grid for repeating groups. */
    private static final class SpikeCell extends TreeCell<SpikeNode> {
        SpikeCell(AtomicInteger counter) {
            counter.incrementAndGet();
        }

        @Override
        protected void updateItem(SpikeNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.repeatingGroup()) {
                setText(null);
                setGraphic(buildEmbeddedGrid(item));
            } else {
                setText(item.label());
                setGraphic(null);
            }
        }

        private TableView<String> buildEmbeddedGrid(SpikeNode node) {
            TableView<String> grid = new TableView<>();
            grid.setMaxHeight(20);
            grid.setPlaceholder(new javafx.scene.control.Label(node.label() + " (grid)"));
            return grid;
        }
    }
}
