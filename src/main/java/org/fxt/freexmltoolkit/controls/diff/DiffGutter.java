package org.fxt.freexmltoolkit.controls.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;

import org.fxmisc.richtext.CodeArea;

/**
 * The thin pane between the two diff CodeAreas that renders per-chunk
 * apply-arrows ({@code ◀} / {@code ▶}). Arrows are repositioned whenever the
 * underlying text, scroll position, or chunk list changes.
 */
public final class DiffGutter extends Pane {

    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

    private static final double GUTTER_WIDTH = 36;
    private static final double ARROW_SIZE = 14;

    private final CodeArea leftArea;
    private final CodeArea rightArea;
    private final BiConsumer<DiffChunk, Direction> applyHandler;

    private List<DiffChunk> chunks = List.of();

    /** Reused arrow buttons in pairs: even index applies left to right, odd index right to left. */
    private final List<Button> arrows = new ArrayList<>();
    private final Tooltip toRightTooltip = new Tooltip("Apply this chunk from left to right");
    private final Tooltip toLeftTooltip = new Tooltip("Apply this chunk from right to left");
    private int visibleArrows;
    private boolean relayoutPending;

    /** The chunk and direction an arrow button currently stands for. */
    private record Target(DiffChunk chunk, Direction direction) {}

    public DiffGutter(CodeArea leftArea, CodeArea rightArea,
                      BiConsumer<DiffChunk, Direction> applyHandler) {
        this.leftArea = leftArea;
        this.rightArea = rightArea;
        this.applyHandler = applyHandler;

        setPrefWidth(GUTTER_WIDTH);
        setMinWidth(GUTTER_WIDTH);
        setMaxWidth(GUTTER_WIDTH);
        getStyleClass().add("diff-gutter");

        ChangeListener<Object> relayout = (obs, o, n) -> requestRelayout();
        leftArea.estimatedScrollYProperty().addListener(relayout);
        rightArea.estimatedScrollYProperty().addListener(relayout);
        leftArea.totalHeightEstimateProperty().addListener(relayout);
        rightArea.totalHeightEstimateProperty().addListener(relayout);
        heightProperty().addListener(relayout);
        widthProperty().addListener(relayout);
    }

    public void setChunks(List<DiffChunk> chunks) {
        this.chunks = chunks == null ? List.of() : chunks;
        requestRelayout();
    }

    /**
     * Coalesces the many triggers (both scroll positions, both height estimates, size, chunk
     * list) into one relayout per FX pulse. Scrolling and a recompute fire several of them at once.
     */
    private void requestRelayout() {
        if (relayoutPending) return;
        relayoutPending = true;
        Platform.runLater(() -> {
            relayoutPending = false;
            relayoutArrows();
        });
    }

    /**
     * Positions arrows for the chunks that start inside the visible part of either area.
     * Buttons are pooled and only moved, so the cost depends on the viewport, not on the
     * number of differences.
     */
    private void relayoutArrows() {
        int used = 0;
        if (!chunks.isEmpty()) {
            int[] leftRange = visibleRange(leftArea);
            int[] rightRange = visibleRange(rightArea);

            // Measure first, then touch the scene graph: every bounds lookup forces a layout
            // of the code area, which must not see nodes changed in between.
            List<DiffChunk> shown = new ArrayList<>();
            List<Double> anchors = new ArrayList<>();
            for (DiffChunk c : chunks) {
                if (c.isEqual()) continue;
                boolean leftVisible = contains(leftRange, c.getLeftStart());
                boolean rightVisible = contains(rightRange, c.getRightStart());
                if (!leftVisible && !rightVisible) continue;

                double leftY = leftVisible ? visibleLineY(leftArea, c.getLeftStart()) : Double.NaN;
                double rightY = rightVisible ? visibleLineY(rightArea, c.getRightStart()) : Double.NaN;

                double anchorY;
                if (Double.isNaN(leftY) && Double.isNaN(rightY)) continue;
                if (Double.isNaN(leftY)) anchorY = rightY;
                else if (Double.isNaN(rightY)) anchorY = leftY;
                else anchorY = (leftY + rightY) / 2.0;

                shown.add(c);
                anchors.add(anchorY);
            }

            for (int i = 0; i < shown.size(); i++) {
                placeArrow(used++, anchors.get(i), shown.get(i), Direction.LEFT_TO_RIGHT);
                placeArrow(used++, anchors.get(i), shown.get(i), Direction.RIGHT_TO_LEFT);
            }
        }
        for (int i = used; i < visibleArrows; i++) {
            Button spare = arrows.get(i);
            spare.setVisible(false);
            spare.setUserData(null);
        }
        visibleArrows = used;
    }

    private void placeArrow(int index, double anchorY, DiffChunk chunk, Direction dir) {
        if (index == arrows.size()) {
            arrows.add(createArrow(index % 2 == 0));
        }
        Button btn = arrows.get(index);
        btn.setUserData(new Target(chunk, dir));
        btn.setLayoutY(Math.max(0, anchorY - (ARROW_SIZE + 4) / 2.0));
        btn.setVisible(true);
    }

    private Button createArrow(boolean rightSide) {
        Button btn = new Button(rightSide ? "▶" : "◀");
        btn.getStyleClass().addAll("diff-gutter-arrow");
        btn.setTooltip(rightSide ? toRightTooltip : toLeftTooltip);
        btn.setOnAction(e -> {
            if (btn.getUserData() instanceof Target t) applyHandler.accept(t.chunk(), t.direction());
        });
        btn.setMinSize(ARROW_SIZE, ARROW_SIZE);
        btn.setPrefSize(ARROW_SIZE + 6, ARROW_SIZE + 4);
        btn.setMaxSize(ARROW_SIZE + 6, ARROW_SIZE + 4);
        btn.setLayoutX(rightSide ? GUTTER_WIDTH / 2.0 + 1 : GUTTER_WIDTH / 2.0 - (ARROW_SIZE + 6) - 1);
        getChildren().add(btn);
        return btn;
    }

    /**
     * Returns the first and last paragraph index currently shown by {@code area}, or
     * {@code null} when nothing is shown (not laid out yet, hidden tab, headless tests).
     */
    private static int[] visibleRange(CodeArea area) {
        try {
            return new int[] {area.firstVisibleParToAllParIndex(), area.lastVisibleParToAllParIndex()};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean contains(int[] range, int paragraphIndex) {
        return range != null && paragraphIndex >= range[0] && paragraphIndex <= range[1];
    }

    /** For tests: number of arrow buttons currently shown. */
    int getVisibleArrowCountForTesting() {
        return visibleArrows;
    }

    /**
     * Returns the Y coordinate in this gutter's local coordinate system that
     * corresponds to the start of {@code paragraphIndex} in {@code area}, or
     * {@link Double#NaN} when that paragraph is not visible.
     */
    private double visibleLineY(CodeArea area, int paragraphIndex) {
        if (paragraphIndex < 0 || paragraphIndex >= area.getParagraphs().size()) {
            return Double.NaN;
        }
        Optional<Bounds> bounds;
        try {
            bounds = area.getParagraphBoundsOnScreen(paragraphIndex);
        } catch (RuntimeException e) {
            // RichTextFX computes on-screen bounds here and throws (NPE: "nodeScreen is null")
            // when the area is not laid out on a real screen yet — e.g. during early layout,
            // when the diff tab is not the visible one, or under headless (Monocle) tests.
            // There is nothing to anchor an arrow to in that case, so treat it as "not visible".
            return Double.NaN;
        }
        if (bounds.isEmpty()) return Double.NaN;
        Bounds local = screenToLocal(bounds.get());
        if (local == null) return Double.NaN;
        return local.getMinY() + local.getHeight() / 2.0;
    }
}
