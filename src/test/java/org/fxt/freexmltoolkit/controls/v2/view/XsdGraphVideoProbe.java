package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

/**
 * Screen positions in an XSD diagram for the tutorial videos ({@code screenshots/XsdGraphicDocVideo}):
 * the diagram is one canvas, so its cards, expand buttons and type frames have no nodes of their
 * own. Every method runs on the FX thread and reads the last layout.
 */
public final class XsdGraphVideoProbe {

    private XsdGraphVideoProbe() {
    }

    /**
     * @param labels card labels from the root down; each one is looked for among the visible
     *               descendants of the previous card (compositor cards in between are skipped)
     * @return the card, or null when it is not showing
     */
    public static VisualNode card(XsdGraphView view, String... labels) {
        VisualNode node = view.rootNodeForTest();
        if (node == null) {
            return null;
        }
        int from = labels.length > 0 && labels[0].equals(node.getLabel()) ? 1 : 0;
        for (int i = from; i < labels.length && node != null; i++) {
            node = find(node, labels[i]);
        }
        return node;
    }

    private static VisualNode find(VisualNode parent, String label) {
        if (!parent.isExpanded()) {
            return null;
        }
        for (VisualNode child : parent.getChildren()) {
            if (label.equals(child.getLabel())) {
                return child;
            }
        }
        for (VisualNode child : parent.getChildren()) {
            if (child.getLabel() == null || child.getLabel().isEmpty() || isCompositor(child)) {
                VisualNode found = find(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isCompositor(VisualNode node) {
        Object model = node.getModelObject();
        return model instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence
                || model instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice
                || model instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll;
    }

    /** @return the first compositor card (sequence, choice, all) below {@code parent}, or null */
    public static VisualNode compositor(VisualNode parent) {
        if (parent == null || !parent.isExpanded()) {
            return null;
        }
        for (VisualNode child : parent.getChildren()) {
            if (isCompositor(child)) {
                return child;
            }
        }
        return null;
    }

    /** @return the card on screen */
    public static Bounds bounds(XsdGraphView view, VisualNode node) {
        view.repaint(); // a card keeps its old place until the next layout
        return screen(view, node.getX(), node.getY(), node.getWidth(), node.getHeight());
    }

    /** @return the card's expand button on screen (the renderer's geometry) */
    public static Bounds expandButton(XsdGraphView view, VisualNode node) {
        view.repaint(); // a card keeps its old place until the next layout
        double size = 18;
        double x = XsdNodeRenderer.expandButtonX(node.getX(), node.getWidth());
        double y = node.getY() + (XsdNodeRenderer.CARD_HEIGHT - size) / 2;
        return screen(view, x, y, size, size);
    }

    /** @return the title strip of the type frame named {@code typeName} on screen, or null */
    public static Bounds frameTitle(XsdGraphView view, String typeName) {
        view.repaint();
        TypeFrame frame = frame(view.rootNodeForTest(), typeName);
        if (frame == null) {
            return null;
        }
        return screen(view, frame.getX(), frame.getY(), Math.min(frame.getWidth(), 160), 18);
    }

    /** @return the whole type frame named {@code typeName} on screen, or null */
    public static Bounds frame(XsdGraphView view, String typeName) {
        view.repaint();
        TypeFrame frame = frame(view.rootNodeForTest(), typeName);
        return frame == null ? null : screen(view, frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight());
    }

    private static TypeFrame frame(VisualNode node, String typeName) {
        if (node == null || !node.isExpanded()) {
            return null;
        }
        for (TypeFrame frame : node.getTypeFrames()) {
            if (typeName.equals(frame.typeName())) {
                return frame;
            }
        }
        for (VisualNode child : node.getChildren()) {
            TypeFrame found = frame(child, typeName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Bounds screen(XsdGraphView view, double x, double y, double w, double h) {
        var canvas = view.canvasForTest();
        Point2D a = canvas.localToScreen(canvas.sceneToLocal(view.scenePointOf(x, y)));
        Point2D b = canvas.localToScreen(canvas.sceneToLocal(view.scenePointOf(x + w, y + h)));
        return new BoundingBox(a.getX(), a.getY(), b.getX() - a.getX(), b.getY() - a.getY());
    }
}
