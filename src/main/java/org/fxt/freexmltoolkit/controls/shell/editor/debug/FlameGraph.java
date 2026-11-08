package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.service.XsltProfile;
import org.fxt.freexmltoolkit.service.XsltProfile.CallNode;

/**
 * Icicle flame graph of an {@link XsltProfile} call tree: the whole run on top, below each node the
 * templates and functions it called, the width proportional to the total time. Hover shows the
 * numbers; a click zooms into a node (its width becomes the full width), a click on the top bar or
 * {@link #resetZoom()} goes back. The colour says the kind: template rule, named template, function.
 */
public final class FlameGraph extends Region {

    static final double ROW_HEIGHT = 20;
    private static final double MIN_WIDTH = 2;
    private static final Color RULE = Color.web("#8cb4e8");
    private static final Color NAMED = Color.web("#8fd3c7");
    private static final Color FUNCTION = Color.web("#f2b880");
    private static final Color ROOT = Color.web("#c9ced6");
    private static final Color TEXT = Color.web("#1a1d21");
    private static final Color BORDER = Color.web("#ffffff");

    /** A drawn rectangle, for hit tests. */
    record Box(CallNode node, double x, double y, double width) {
    }

    private final Canvas canvas = new Canvas();
    private final Tooltip tooltip = new Tooltip();
    private final XsltProfile profile;
    private final List<Box> boxes = new ArrayList<>();
    private CallNode zoom;
    private Consumer<CallNode> onOpen = n -> { };

    public FlameGraph(XsltProfile profile) {
        this.profile = profile;
        this.zoom = profile.root();
        getStyleClass().add("fxt-flame-graph");
        setId("profile-flame-graph");
        getChildren().add(canvas);
        tooltip.setShowDelay(Duration.millis(150));
        Tooltip.install(canvas, tooltip);
        canvas.setOnMouseMoved(e -> {
            Box box = boxAt(e.getX(), e.getY());
            tooltip.setText(box == null ? "" : describe(box.node()));
        });
        canvas.setOnMouseClicked(e -> {
            Box box = boxAt(e.getX(), e.getY());
            if (box == null) {
                return;
            }
            if (e.getClickCount() == 2 && box.node().component() != null) {
                onOpen.accept(box.node());
            } else if (e.getClickCount() == 1) {
                zoomTo(box.node() == zoom ? profile.root() : box.node());
            }
        });
        widthProperty().addListener((obs, o, n) -> redraw());
    }

    /** Called with a node on double-click (the view opens its source). */
    public void setOnOpen(Consumer<CallNode> onOpen) {
        this.onOpen = onOpen == null ? n -> { } : onOpen;
    }

    /** Shows {@code node} across the full width. */
    public void zoomTo(CallNode node) {
        zoom = node == null ? profile.root() : node;
        redraw();
    }

    public void resetZoom() {
        zoomTo(profile.root());
    }

    CallNode zoomedNode() {
        return zoom;
    }

    List<Box> boxes() {
        return List.copyOf(boxes);
    }

    private String describe(CallNode node) {
        StringBuilder text = new StringBuilder(node.label());
        if (node.component() != null) {
            text.append("\n").append(node.component().kind().label()).append(" · ")
                    .append(node.component().module()).append(':').append(node.component().line());
        }
        text.append("\nTotal ").append(XsltProfile.millis(node.totalNanos())).append(" ms (")
                .append(profile.percent(node.totalNanos())).append(")")
                .append("\nSelf ").append(XsltProfile.millis(node.selfNanos())).append(" ms")
                .append("\nCalls ").append(node.calls());
        if (node.component() != null) {
            text.append("\nClick: zoom · double-click: open the source");
        }
        return text.toString();
    }

    private Box boxAt(double x, double y) {
        for (Box box : boxes) {
            if (x >= box.x() && x <= box.x() + box.width() && y >= box.y() && y <= box.y() + ROW_HEIGHT) {
                return box;
            }
        }
        return null;
    }

    @Override
    protected double computePrefHeight(double width) {
        return (depth(zoom, 0) + 1) * ROW_HEIGHT + 2;
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    private static int depth(CallNode node, int level) {
        int max = level;
        if (level < 400) {
            for (CallNode child : node.children()) {
                max = Math.max(max, depth(child, level + 1));
            }
        }
        return max;
    }

    @Override
    protected void layoutChildren() {
        double width = Math.max(1, getWidth());
        double height = computePrefHeight(width);
        if (canvas.getWidth() != width || canvas.getHeight() != height) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            redraw();
        }
    }

    private void redraw() {
        double width = Math.max(1, getWidth());
        double height = computePrefHeight(width);
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setFont(Font.font(11));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        boxes.clear();
        long total = Math.max(1, zoom.totalNanos());
        draw(g, zoom, 0, 0, width, total);
        requestLayout();
    }

    private void draw(GraphicsContext g, CallNode node, int level, double x, double width, long total) {
        if (width < MIN_WIDTH || level > 400) {
            return;
        }
        double y = level * ROW_HEIGHT;
        g.setFill(color(node));
        g.fillRect(x, y, width, ROW_HEIGHT - 1);
        g.setStroke(BORDER);
        g.strokeRect(x + 0.5, y + 0.5, Math.max(0, width - 1), ROW_HEIGHT - 2);
        if (width > 30) {
            g.setFill(TEXT);
            String label = node.label() + "  " + XsltProfile.millis(node.totalNanos()) + " ms";
            g.save();
            g.beginPath();
            g.rect(x + 1, y, width - 2, ROW_HEIGHT);
            g.clip();
            g.fillText(label, x + 4, y + ROW_HEIGHT / 2);
            g.restore();
        }
        boxes.add(new Box(node, x, y, width));
        double childX = x;
        for (CallNode child : node.children()) {
            double childWidth = width * child.totalNanos() / Math.max(1, node.totalNanos());
            draw(g, child, level + 1, childX, childWidth, total);
            childX += childWidth;
        }
    }

    private static Color color(CallNode node) {
        if (node.component() == null) {
            return ROOT;
        }
        return switch (node.component().kind()) {
            case TEMPLATE_RULE -> RULE;
            case NAMED_TEMPLATE -> NAMED;
            case FUNCTION -> FUNCTION;
        };
    }
}
