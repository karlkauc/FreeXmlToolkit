package org.fxt.freexmltoolkit.controls.theme;

import java.util.List;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/**
 * Applies the density ({@link Typography.Density}) to the vertical spacing that layouts set in FXML
 * or code: top / bottom {@code padding}, a {@code VBox}'s {@code spacing}, {@code vgap}. Horizontal
 * room stays (a wider row in a narrow side panel would cut its texts). Stylesheet values are left alone —
 * the typography sheet scales those — so only values whose {@link StyleOrigin} is {@code USER} are
 * touched. The original value is kept on the node, so another density or Normal starts from it
 * and nothing compounds.
 *
 * <p>Nodes added later are covered by a listener on the children lists of the visited parents.
 * Control skins are not entered (their spacing is CSS), except the content of the containers
 * that hold application layout (scroll, titled, tab and split panes); canvases and RichTextFX
 * areas are skipped. At Normal density nothing is installed.
 */
public final class DensityScaler {

    private static final String ORIGINAL = "fxt.density.original";
    private static final String WATCHED = "fxt.density.watched";

    /** The factor in force (1 = Normal). */
    private static volatile double factor = 1.0;

    private static final ListChangeListener<Node> CHILDREN = change -> {
        while (change.next()) {
            for (Node added : change.getAddedSubList()) {
                visit(added);
            }
        }
    };

    private DensityScaler() {
    }

    /** @return the factor in force */
    public static double factor() {
        return factor;
    }

    /**
     * Sets the factor in force; the windows are then (re)scaled through {@link #apply}.
     *
     * @param newFactor the density factor (1 = Normal)
     */
    public static void setFactor(double newFactor) {
        factor = newFactor;
    }

    /**
     * Scales a node tree to the factor in force (and keeps scaling what is added to it).
     *
     * @param root the tree (a window's root, a popup's content)
     */
    public static void apply(Node root) {
        if (root != null && (factor != 1.0 || root.getProperties().containsKey(WATCHED))) {
            visit(root);
        }
    }

    private static void visit(Node node) {
        if (node instanceof Canvas || isRichText(node) || node.getStyleClass().contains("fxt-activity-bar")) {
            return;
        }
        if (node instanceof Region region) {
            scaleInsets(region, region.paddingProperty());
        }
        switch (node) {
            case VBox box -> scale(box, "spacing", box.spacingProperty());
            case GridPane grid -> scale(grid, "vgap", grid.vgapProperty());
            case FlowPane flow -> scale(flow, "vgap", flow.vgapProperty());
            case TilePane tile -> scale(tile, "vgap", tile.vgapProperty());
            default -> { }
        }
        switch (node) {
            case ScrollPane scroll -> {
                if (watch(scroll)) {
                    scroll.contentProperty().addListener((obs, old, content) -> visitIfActive(content));
                }
                visitIfActive(scroll.getContent());
            }
            case TitledPane titled -> {
                if (watch(titled)) {
                    titled.contentProperty().addListener((obs, old, content) -> visitIfActive(content));
                }
                visitIfActive(titled.getContent());
            }
            case TabPane tabs -> {
                if (watch(tabs)) {
                    tabs.getTabs().addListener((ListChangeListener<Tab>) change -> {
                        while (change.next()) {
                            for (Tab tab : change.getAddedSubList()) {
                                visitTab(tab);
                            }
                        }
                    });
                }
                for (Tab tab : tabs.getTabs()) {
                    visitTab(tab);
                }
            }
            case SplitPane split -> {
                if (watch(split)) {
                    split.getItems().addListener(CHILDREN);
                }
                for (Node item : List.copyOf(split.getItems())) {
                    visit(item);
                }
            }
            case Control _ -> { } // a skin: CSS spacing
            case Parent parent -> {
                List<Node> children = parent.getChildrenUnmodifiable();
                if (watch(parent)) {
                    parent.getChildrenUnmodifiable().addListener(CHILDREN);
                }
                for (Node child : List.copyOf(children)) {
                    visit(child);
                }
            }
            default -> { }
        }
    }

    /** Marks a node as watched; @return true the first time (the caller installs its listener) */
    private static boolean watch(Node node) {
        return node.getProperties().putIfAbsent(WATCHED, Boolean.TRUE) == null;
    }

    private static boolean watch(Tab tab) {
        return tab.getProperties().putIfAbsent(WATCHED, Boolean.TRUE) == null;
    }

    private static void visitIfActive(Node node) {
        if (node != null) {
            visit(node);
        }
    }

    private static void visitTab(Tab tab) {
        if (watch(tab)) {
            tab.contentProperty().addListener((obs, old, content) -> visitIfActive(content));
        }
        visitIfActive(tab.getContent());
    }

    /** RichTextFX areas: their paragraph boxes come and go while scrolling. */
    private static boolean isRichText(Node node) {
        return node.getClass().getName().startsWith("org.fxmisc.");
    }

    @SuppressWarnings("unchecked")
    private static void scale(Node node, String name, DoubleProperty property) {
        String key = ORIGINAL + "." + name;
        Object original = node.getProperties().get(key);
        if (original == null) {
            if (!isUserValue((StyleableProperty<Number>) (StyleableProperty<?>) property) || property.get() == 0) {
                return;
            }
            original = property.get();
            node.getProperties().put(key, original);
        }
        property.set(Math.round((Double) original * factor * 2) / 2.0);
    }

    @SuppressWarnings("unchecked")
    private static void scaleInsets(Region region, ObjectProperty<Insets> property) {
        String key = ORIGINAL + ".padding";
        Object original = region.getProperties().get(key);
        if (original == null) {
            Insets insets = property.get();
            if (!isUserValue((StyleableProperty<Insets>) property) || insets == null || insets.equals(Insets.EMPTY)) {
                return;
            }
            original = insets;
            region.getProperties().put(key, original);
        }
        Insets o = (Insets) original;
        property.set(new Insets(half(o.getTop()), o.getRight(), half(o.getBottom()), o.getLeft()));
    }

    private static double half(double value) {
        return Math.round(value * factor * 2) / 2.0;
    }

    /** @return true when the value was set in FXML or code, not by a stylesheet */
    private static boolean isUserValue(StyleableProperty<?> property) {
        return property.getStyleOrigin() == StyleOrigin.USER;
    }
}
