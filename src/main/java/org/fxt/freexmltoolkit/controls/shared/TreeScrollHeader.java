package org.fxt.freexmltoolkit.controls.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Sticky scroll header of a {@link TreeView}: while the tree is scrolled, a bar over the top of
 * its viewport shows the path of parents of the topmost visible row ({@code root › section ›
 * body}); a click on one selects it and scrolls back to it. The tree's counterpart of the
 * grid's scroll header.
 * <p>
 * The bar is an unmanaged child of the tree's skin, laid over the viewport; it only reads the
 * {@link VirtualFlow}'s visible cells, so it costs a parent walk per scroll step. A selected row
 * that ends up under the bar (keyboard navigation, {@code scrollTo}) is scrolled out from under it.
 *
 * @param <T> the tree's item type
 */
public final class TreeScrollHeader<T> {

    private static final String KEY = TreeScrollHeader.class.getName();

    private final TreeView<T> tree;
    private final Function<T, String> label;
    private final Function<T, Node> icon;
    private final HBox bar = new HBox();
    private VirtualFlow<?> flow;
    private List<TreeItem<T>> chain = List.of();
    private boolean dirty;
    private boolean uncoverPending;
    /** Runs after every layout pass of the tree's scene: cells are placed by then. */
    private final Runnable afterLayout = this::afterLayout;

    private TreeScrollHeader(TreeView<T> tree, Function<T, String> label, Function<T, Node> icon) {
        this.tree = tree;
        this.label = label;
        this.icon = icon;
        bar.getStyleClass().add("fxt-tree-scroll-header");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setManaged(false);
        bar.setVisible(false);
        // Clicks between segments must not reach the rows under the bar
        bar.setOnMouseClicked(javafx.event.Event::consume);
        bar.setOnMousePressed(javafx.event.Event::consume);
    }

    /**
     * Gives a tree the sticky scroll header.
     *
     * @param tree  the tree
     * @param label the text of an item in the path (a short name)
     * @param icon  the item's icon, or {@code null} for none (may return {@code null})
     * @param <T>   the item type
     * @return the header
     */
    public static <T> TreeScrollHeader<T> install(TreeView<T> tree, Function<T, String> label, Function<T, Node> icon) {
        TreeScrollHeader<T> header = new TreeScrollHeader<>(tree, label, icon);
        tree.getProperties().put(KEY, header);
        tree.skinProperty().addListener((obs, old, skin) -> header.attach(skin));
        header.attach(tree.getSkin());
        tree.expandedItemCountProperty().addListener((obs, o, n) -> header.queueUpdate());
        tree.rootProperty().addListener((obs, o, n) -> header.queueUpdate());
        tree.showRootProperty().addListener((obs, o, n) -> header.queueUpdate());
        tree.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> header.queueUncover());
        tree.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null) {
                old.removePostLayoutPulseListener(header.afterLayout);
            }
            if (scene != null) {
                scene.addPostLayoutPulseListener(header.afterLayout);
                header.queueUpdate();
            }
        });
        if (tree.getScene() != null) {
            tree.getScene().addPostLayoutPulseListener(header.afterLayout);
        }
        return header;
    }

    /**
     * @param tree a tree
     * @return the header installed on it, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> TreeScrollHeader<T> of(TreeView<T> tree) {
        return (TreeScrollHeader<T>) tree.getProperties().get(KEY);
    }

    /** @return the items the header shows, outermost first (empty while it is hidden) */
    public List<TreeItem<T>> chain() {
        return chain;
    }

    /** @return the bar node (for tests) */
    public HBox bar() {
        return bar;
    }

    private void attach(Skin<?> skin) {
        if (!(skin instanceof SkinBase<?> base)) {
            return;
        }
        // The skin builds its flow in the constructor; the bar goes over it (last child)
        base.getChildren().remove(bar);
        base.getChildren().add(bar);
        flow = base.getChildren().stream().filter(VirtualFlow.class::isInstance)
                .map(n -> (VirtualFlow<?>) n).findFirst().orElse(null);
        if (flow != null) {
            flow.positionProperty().addListener((obs, o, n) -> queueUpdate());
            flow.layoutBoundsProperty().addListener((obs, o, n) -> queueUpdate());
        }
        queueUpdate();
    }

    /** Recomputes the path after the next layout pass (cells placed). */
    private void queueUpdate() {
        dirty = true;
        Platform.requestNextPulse();
    }

    /** Scrolls the selected row out from under the bar after the next layout pass. */
    private void queueUncover() {
        uncoverPending = true;
        queueUpdate();
    }

    private void afterLayout() {
        if (!dirty) {
            return;
        }
        dirty = false;
        update();
        if (uncoverPending) {
            uncoverPending = false;
            uncoverSelection();
        }
    }

    /** Recomputes the shown path and places the bar. */
    private void update() {
        List<TreeItem<T>> next = computeChain();
        if (!next.equals(chain)) {
            chain = next;
            rebuildSegments();
        }
        bar.setVisible(!chain.isEmpty());
        place();
    }

    /** Recomputes the path at once (tests and callers that changed the tree without a scroll). */
    public void refresh() {
        update();
    }

    private double barHeight() {
        if (bar.getScene() != null) {
            bar.applyCss();
        }
        return Math.max(bar.prefHeight(-1), 1);
    }

    /** @return the parents of the row just below the bar's bottom edge, outermost first */
    private List<TreeItem<T>> computeChain() {
        if (flow == null || flow.getPosition() <= 0 || tree.getRoot() == null) {
            return List.of();
        }
        IndexedCell<?> first = flow.getFirstVisibleCell();
        IndexedCell<?> last = flow.getLastVisibleCell();
        if (first == null || last == null) {
            return List.of();
        }
        double edge = barHeight();
        int target = -1;
        for (int i = first.getIndex(); i <= last.getIndex(); i++) {
            IndexedCell<?> cell = flow.getVisibleCell(i);
            if (cell != null && cell.getLayoutY() + cell.getHeight() > edge) {
                target = i;
                break;
            }
        }
        TreeItem<T> item = target >= 0 ? tree.getTreeItem(target) : null;
        if (item == null) {
            return List.of();
        }
        List<TreeItem<T>> path = new ArrayList<>();
        for (TreeItem<T> p = item.getParent(); p != null; p = p.getParent()) {
            if (p != tree.getRoot() || tree.isShowRoot()) {
                path.addFirst(p);
            }
        }
        return List.copyOf(path);
    }

    private void rebuildSegments() {
        List<Node> nodes = new ArrayList<>();
        for (TreeItem<T> item : chain) {
            if (!nodes.isEmpty()) {
                Label separator = new Label("›");
                separator.getStyleClass().add("fxt-tree-scroll-header-separator");
                nodes.add(separator);
            }
            T value = item.getValue();
            Label segment = new Label(value == null ? "" : label.apply(value));
            segment.getStyleClass().add("fxt-tree-scroll-header-segment");
            Node graphic = value == null || icon == null ? null : icon.apply(value);
            if (graphic != null) {
                segment.setGraphic(graphic);
            }
            segment.setOnMouseClicked(e -> {
                e.consume();
                reveal(item);
            });
            nodes.add(segment);
        }
        bar.getChildren().setAll(nodes);
    }

    /** Selects an item of the path and scrolls so its row sits just below the bar. */
    private void reveal(TreeItem<T> item) {
        int row = tree.getRow(item);
        if (row < 0) {
            return;
        }
        tree.getSelectionModel().clearAndSelect(row);
        tree.scrollTo(row);
        tree.requestFocus();
        queueUncover();
    }

    /** Places the bar over the top of the viewport (the scroll bar stays free). */
    private void place() {
        if (flow == null) {
            return;
        }
        Region clip = (Region) flow.lookup(".clipped-container");
        double width = clip != null && clip.getWidth() > 0 ? clip.getWidth() : flow.getWidth();
        double height = barHeight();
        bar.resizeRelocate(flow.getLayoutX(), flow.getLayoutY(), width, height);
    }

    /** Scrolls a selected row out from under the bar. */
    private void uncoverSelection() {
        update();
        if (flow == null || !bar.isVisible()) {
            return;
        }
        int selected = tree.getSelectionModel().getSelectedIndex();
        IndexedCell<?> cell = selected >= 0 ? flow.getVisibleCell(selected) : null;
        double edge = barHeight();
        if (cell != null && cell.getLayoutY() < edge) {
            flow.scrollPixels(cell.getLayoutY() - edge);
            update();
        }
    }
}
