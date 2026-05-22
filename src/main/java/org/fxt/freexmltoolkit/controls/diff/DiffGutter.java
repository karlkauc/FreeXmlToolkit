package org.fxt.freexmltoolkit.controls.diff;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

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

    public DiffGutter(CodeArea leftArea, CodeArea rightArea,
                      BiConsumer<DiffChunk, Direction> applyHandler) {
        this.leftArea = leftArea;
        this.rightArea = rightArea;
        this.applyHandler = applyHandler;

        setPrefWidth(GUTTER_WIDTH);
        setMinWidth(GUTTER_WIDTH);
        setMaxWidth(GUTTER_WIDTH);
        getStyleClass().add("diff-gutter");

        ChangeListener<Object> relayout = (obs, o, n) -> relayoutArrows();
        leftArea.estimatedScrollYProperty().addListener(relayout);
        rightArea.estimatedScrollYProperty().addListener(relayout);
        leftArea.totalHeightEstimateProperty().addListener(relayout);
        rightArea.totalHeightEstimateProperty().addListener(relayout);
        heightProperty().addListener(relayout);
        widthProperty().addListener(relayout);
    }

    public void setChunks(List<DiffChunk> chunks) {
        this.chunks = chunks == null ? List.of() : chunks;
        relayoutArrows();
    }

    private void relayoutArrows() {
        getChildren().clear();
        if (chunks.isEmpty()) return;

        for (DiffChunk c : chunks) {
            if (c.isEqual()) continue;

            double leftY  = visibleLineY(leftArea,  c.getLeftStart());
            double rightY = visibleLineY(rightArea, c.getRightStart());

            double anchorY;
            if (Double.isNaN(leftY) && Double.isNaN(rightY)) continue;
            if (Double.isNaN(leftY)) anchorY = rightY;
            else if (Double.isNaN(rightY)) anchorY = leftY;
            else anchorY = (leftY + rightY) / 2.0;

            if (canApplyToRight(c)) addArrow("▶", anchorY, true, c, Direction.LEFT_TO_RIGHT,
                    "Apply this chunk from left to right");
            if (canApplyToLeft(c)) addArrow("◀", anchorY, false, c, Direction.RIGHT_TO_LEFT,
                    "Apply this chunk from right to left");
        }
    }

    private boolean canApplyToRight(DiffChunk c) {
        return c.getType() != DiffChunk.Type.EQUAL;
    }

    private boolean canApplyToLeft(DiffChunk c) {
        return c.getType() != DiffChunk.Type.EQUAL;
    }

    private void addArrow(String label, double anchorY, boolean rightSide,
                          DiffChunk chunk, Direction dir, String tooltip) {
        Button btn = new Button(label);
        btn.getStyleClass().addAll("diff-gutter-arrow");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnAction(e -> applyHandler.accept(chunk, dir));
        btn.setMinSize(ARROW_SIZE, ARROW_SIZE);
        btn.setPrefSize(ARROW_SIZE + 6, ARROW_SIZE + 4);
        btn.setMaxSize(ARROW_SIZE + 6, ARROW_SIZE + 4);

        double x = rightSide ? GUTTER_WIDTH / 2.0 + 1 : GUTTER_WIDTH / 2.0 - (ARROW_SIZE + 6) - 1;
        btn.setLayoutX(x);
        btn.setLayoutY(Math.max(0, anchorY - (ARROW_SIZE + 4) / 2.0));
        getChildren().add(btn);
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
        Optional<Bounds> bounds = area.getParagraphBoundsOnScreen(paragraphIndex);
        if (bounds.isEmpty()) return Double.NaN;
        Bounds local = screenToLocal(bounds.get());
        if (local == null) return Double.NaN;
        return local.getMinY() + local.getHeight() / 2.0;
    }
}
