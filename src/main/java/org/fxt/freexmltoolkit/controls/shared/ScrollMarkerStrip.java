package org.fxt.freexmltoolkit.controls.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import org.fxmisc.richtext.GenericStyledArea;

/**
 * Marks lines on top of a code area's vertical scroll bar (an overview ruler): each marker sits
 * where its line lies in the whole document, so the user sees where in a long file the marked
 * lines are and jumps there with a click. Meant to be stacked over the editor (a transparent pane
 * the size of the editor; only the markers take mouse events). Hidden while the document fits and
 * no scroll bar shows.
 */
public final class ScrollMarkerStrip extends Pane {

    /** One marker: a 1-based line, a style class for its colour and a tooltip ({@code null} = none). */
    public record Marker(int line, String styleClass, String tooltip) {
    }

    private static final double MARKER_HEIGHT = 3;

    private final GenericStyledArea<?, ?, ?> area;
    private final IntConsumer onClick;
    private List<Marker> markers = List.of();
    private ScrollBar vbar;
    private int lastParagraphs = -1;

    /**
     * @param area    the code area whose scroll bar is marked (its parent is the scroll pane)
     * @param onClick called with the 1-based line of a clicked marker
     */
    public ScrollMarkerStrip(GenericStyledArea<?, ?, ?> area, IntConsumer onClick) {
        this.area = area;
        this.onClick = onClick;
        getStyleClass().add("fxt-scroll-markers");
        setPickOnBounds(false);
        setManaged(true);
        setMinSize(0, 0);
        area.getParagraphs().addListener((ListChangeListener<Object>) c -> {
            int n = area.getParagraphs().size();
            if (n != lastParagraphs && !markers.isEmpty()) {
                requestLayout();
            }
        });
        layoutBoundsProperty().addListener((obs, o, n) -> requestLayout());
    }

    /** Replaces the markers. */
    public void setMarkers(List<Marker> newMarkers) {
        markers = newMarkers == null ? List.of() : List.copyOf(newMarkers);
        List<Node> nodes = new ArrayList<>();
        for (Marker m : markers) {
            Region r = new Region();
            r.getStyleClass().add("fxt-scroll-marker");
            if (m.styleClass() != null) {
                r.getStyleClass().add(m.styleClass());
            }
            r.setCursor(Cursor.HAND);
            r.setManaged(false);
            if (m.tooltip() != null) {
                Tooltip.install(r, new Tooltip(m.tooltip()));
            }
            int line = m.line();
            r.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    onClick.accept(line);
                    e.consume();
                }
            });
            nodes.add(r);
        }
        getChildren().setAll(nodes);
        requestLayout();
    }

    /** @return the markers shown (tests). */
    public List<Marker> getMarkers() {
        return markers;
    }

    private ScrollBar verticalBar() {
        if (vbar != null && vbar.getParent() != null) {
            return vbar;
        }
        Parent scroll = area.getParent();
        if (scroll == null) {
            return null;
        }
        for (Node child : scroll.getChildrenUnmodifiable()) {
            if (child instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                vbar = bar;
                // follow its size and visibility
                bar.boundsInParentProperty().addListener((obs, o, n) -> requestLayout());
                bar.visibleProperty().addListener((obs, o, n) -> requestLayout());
                return bar;
            }
        }
        return null;
    }

    @Override
    protected void layoutChildren() {
        lastParagraphs = area.getParagraphs().size();
        ScrollBar bar = verticalBar();
        boolean show = bar != null && bar.isVisible() && bar.getScene() != null && getScene() != null
                && bar.getHeight() > 0;
        if (!show) {
            getChildren().forEach(n -> n.setVisible(false));
            return;
        }
        Bounds b = sceneToLocal(bar.localToScene(bar.getLayoutBounds()));
        if (b == null) {
            return;
        }
        double width = Math.max(4, b.getWidth() - 4);
        double x = b.getMinX() + (b.getWidth() - width) / 2;
        // the themes give the arrow buttons no room along the track
        double inset = 2;
        double top = b.getMinY() + inset;
        double track = Math.max(1, b.getHeight() - 2 * inset - MARKER_HEIGHT);
        int lines = Math.max(1, lastParagraphs);
        for (int i = 0; i < getChildren().size() && i < markers.size(); i++) {
            Node n = getChildren().get(i);
            double y = top + track * (Math.max(0, markers.get(i).line() - 1) / (double) Math.max(1, lines - 1));
            n.resizeRelocate(Math.round(x), Math.round(y), Math.round(width), MARKER_HEIGHT);
            n.setVisible(true);
        }
    }
}
