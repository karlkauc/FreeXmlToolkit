package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.largefile.LargeRecordExport;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Cursor;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Step;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlWindow;

/**
 * The Structure tab of the {@link LargeFileView}: the element tree (children listed a page at a time,
 * with "Previous page" / "Load more" entries), the records table of the selected element, Go to XPath
 * / child number, Show in Graphic and the export of all children.
 */
final class LargeFileStructurePane extends VBox {

    /** Children per page in the tree and the records table. */
    static final int PAGE = 1000;

    /** What the pane needs from the viewer. */
    interface Host {
        /** @return the structure of the file, or {@code null} while it is (re)indexed */
        LargeXmlStructure structure();

        /** Runs a job off the FX thread with the progress bar; {@code failed} runs on the FX thread after an error. */
        <T> void background(String label, LargeFileView.Job<T> job, Consumer<T> done, Runnable failed);

        default <T> void background(String label, LargeFileView.Job<T> job, Consumer<T> done) {
            background(label, job, done, () -> { });
        }

        void status(String text);

        void showInText(Node node);

        void showInGraphic(LargeFileView.WindowSpec spec);

        void openDocument(String name, String text);

        javafx.stage.Window window();
    }

    /** Test hook: the export target instead of a file chooser. */
    static java.util.function.Function<LargeRecordExport.Format, File> exportTarget;

    @FXML private TextField pathField;
    @FXML private TreeView<Object> tree;
    @FXML private Label recordsLabel;
    @FXML private TextField childField;
    @FXML private Button loadMoreButton;
    @FXML private Button graphicButton;
    @FXML private MenuButton exportButton;
    @FXML private TableView<Node> records;

    private final Host host;
    /** Loaded children per element (identity: a re-read node is another node). */
    private final Map<Node, Loaded> loaded = new IdentityHashMap<>();
    private final Map<Node, TreeItem<Object>> items = new IdentityHashMap<>();
    private Node recordsParent;
    private boolean rootRequested;

    /** The loaded children of an element: a contiguous run of whole pages. */
    static final class Loaded {
        final List<Node> nodes = new ArrayList<>();
        /** the cursor of every loaded page, in order (the first lists {@code nodes.get(0)}) */
        final List<Cursor> pages = new ArrayList<>();
        long firstIndex;
        /** where the next page starts, or {@code null} when the last child is loaded */
        Cursor next;
        boolean loading;
        LargeXmlStructure.PageIndex pageIndex;

        boolean covers(long index) {
            return index >= firstIndex && index < firstIndex + nodes.size();
        }

        Node at(long index) {
            return covers(index) ? nodes.get((int) (index - firstIndex)) : null;
        }

        /** @return the cursor of the child at {@code index} (it must be loaded) */
        Cursor cursorOf(long index) {
            int page = (int) ((index - firstIndex) / PAGE);
            Cursor start = pages.get(page);
            Map<String, Integer> counters = new HashMap<>(start.positions());
            int from = (int) (start.index() - firstIndex);
            for (int i = from; i < index - firstIndex; i++) {
                counters.merge(nodes.get(i).name(), 1, Integer::sum);
            }
            return new Cursor(nodes.get((int) (index - firstIndex)).start(), counters);
        }
    }

    /** "Load more" entry of the tree. */
    record More(Node parent) {
    }

    /** "Previous page" entry of the tree. */
    record Previous(Node parent) {
    }

    LargeFileStructurePane(Host host) {
        this.host = host;
        FxmlViews.load(this, "/pages/views/LargeFileStructurePane.fxml");
        tree.setShowRoot(true);
        tree.setCellFactory(t -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item instanceof More) {
                    setText(I18n.t("LargeFileView.load-more"));
                } else if (item instanceof Previous) {
                    setText(I18n.t("LargeFileView.previous-page"));
                } else if (item instanceof Node node) {
                    setText(label(node));
                } else {
                    setText(String.valueOf(item));
                }
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, item) -> {
            if (item == null) {
                return;
            }
            if (item.getValue() instanceof More more) {
                loadNext(more.parent());
            } else if (item.getValue() instanceof Previous previous) {
                loadPrevious(previous.parent());
            } else if (item.getValue() instanceof Node node) {
                showRecords(node);
            }
        });
        tree.setContextMenu(nodeMenu(this::selectedTreeNode, true));
        records.setContextMenu(nodeMenu(() -> records.getSelectionModel().getSelectedItem(), false));
        records.setRowFactory(t -> {
            TableRow<Node> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    if (e.isAltDown()) {
                        showRecordInGraphic(row.getItem());
                    } else {
                        host.showInText(row.getItem());
                    }
                }
            });
            return row;
        });
        exportButton.setDisable(true);
        childField.setDisable(true);
    }

    // ---- loading ------------------------------------------------------------------------------

    /** Forgets everything read (after the file changed); the root is read again when needed. */
    void reset() {
        loaded.clear();
        items.clear();
        recordsParent = null;
        rootRequested = false;
        tree.setRoot(null);
        records.getItems().clear();
        records.getColumns().clear();
        recordsLabel.setText(I18n.t("LargeFileView.recordsLabel.text"));
        loadMoreButton.setDisable(true);
        exportButton.setDisable(true);
        childField.setDisable(true);
    }

    /** Reads the root if it is not shown yet; then runs {@code after}. */
    void ensureRoot(Runnable after) {
        if (tree.getRoot() != null) {
            after.run();
            return;
        }
        if (rootRequested || host.structure() == null) {
            return;
        }
        rootRequested = true;
        LargeXmlStructure structure = host.structure();
        recordsLabel.setText(I18n.t("LargeFileView.reading-the-structure"));
        host.background(I18n.t("LargeFileView.reading-the-structure"), (cancelled, progress) -> structure.root(), root -> {
            TreeItem<Object> item = treeItem(root);
            tree.setRoot(item);
            item.setExpanded(true);
            tree.getSelectionModel().select(item);
            after.run();
        });
    }

    private TreeItem<Object> treeItem(Node node) {
        TreeItem<Object> item = new TreeItem<>(node) {
            @Override
            public boolean isLeaf() {
                return !node.hasChildren();
            }
        };
        items.put(node, item);
        if (node.hasChildren()) {
            item.getChildren().add(new TreeItem<>(I18n.t("LargeFileView.loading")));
            item.expandedProperty().addListener((obs, was, expanded) -> {
                if (expanded && !loaded.containsKey(node)) {
                    loadNext(node);
                }
            });
        }
        return item;
    }

    private Loaded state(Node parent) {
        return loaded.computeIfAbsent(parent, p -> {
            Loaded l = new Loaded();
            l.next = new Cursor(p.start(), Map.of());
            return l;
        });
    }

    /** Appends the next page of {@code parent}'s children. */
    private void loadNext(Node parent) {
        Loaded state = state(parent);
        LargeXmlStructure structure = host.structure();
        if (state.loading || state.next == null || structure == null) {
            return;
        }
        state.loading = true;
        Cursor from = state.next;
        boolean firstPage = from.offset() == parent.start();
        host.background(I18n.t("LargeFileView.reading-children", parent.name()),
                (cancelled, progress) -> structure.children(parent, firstPage ? null : from, PAGE, cancelled, progress),
                page -> {
                    state.loading = false;
                    Cursor start = firstPage ? new Cursor(page.children().isEmpty() ? parent.start()
                            : page.children().getFirst().start(), Map.of()) : from;
                    if (state.nodes.isEmpty()) {
                        state.firstIndex = start.index();
                    }
                    state.pages.add(start);
                    state.nodes.addAll(page.children());
                    state.next = page.nextCursor();
                    rebuildItems(parent);
                }, () -> state.loading = false);
    }

    /** Prepends the page before the first loaded one. */
    private void loadPrevious(Node parent) {
        Loaded state = loaded.get(parent);
        if (state == null || state.loading || state.firstIndex == 0) {
            return;
        }
        withPageIndex(parent, state, index -> {
            Cursor cursor = index.pageOf(state.firstIndex - 1);
            LargeXmlStructure structure = host.structure();
            state.loading = true;
            host.background(I18n.t("LargeFileView.reading-children", parent.name()),
                    (cancelled, progress) -> structure.children(parent, cursor, PAGE, cancelled, progress), page -> {
                        state.loading = false;
                        state.nodes.addAll(0, page.children());
                        state.pages.addFirst(cursor);
                        state.firstIndex = cursor.index();
                        rebuildItems(parent);
                    }, () -> state.loading = false);
        });
    }

    /** Replaces the loaded children with the page at {@code cursor}; then runs {@code done}. */
    private void loadPageAt(Node parent, Cursor cursor, Runnable done) {
        Loaded state = state(parent);
        LargeXmlStructure structure = host.structure();
        if (structure == null) {
            return;
        }
        state.loading = true;
        host.background(I18n.t("LargeFileView.reading-children", parent.name()),
                (cancelled, progress) -> structure.children(parent, cursor, PAGE, cancelled, progress), page -> {
                    state.loading = false;
                    state.nodes.clear();
                    state.pages.clear();
                    state.nodes.addAll(page.children());
                    state.pages.add(cursor == null ? new Cursor(parent.start(), Map.of()) : cursor);
                    state.firstIndex = cursor == null ? 0 : cursor.index();
                    state.next = page.nextCursor();
                    rebuildItems(parent);
                    done.run();
                }, () -> state.loading = false);
    }

    /** Runs {@code then} with the page index of {@code parent} (counted once in the background). */
    private void withPageIndex(Node parent, Loaded state, Consumer<LargeXmlStructure.PageIndex> then) {
        if (state.pageIndex != null) {
            then.accept(state.pageIndex);
            return;
        }
        LargeXmlStructure structure = host.structure();
        host.background(I18n.t("LargeFileView.counting-children", parent.name()),
                (cancelled, progress) -> structure.pageIndex(parent, PAGE, cancelled, progress), index -> {
                    state.pageIndex = index;
                    if (parent == recordsParent) {
                        fillRecords(parent);
                    }
                    then.accept(index);
                });
    }

    /** Shows the loaded run of {@code parent}'s children in its tree item and, if shown, the table. */
    private void rebuildItems(Node parent) {
        Loaded state = loaded.get(parent);
        TreeItem<Object> parentItem = items.get(parent);
        if (parentItem != null) {
            java.util.Set<Node> kept = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            kept.addAll(state.nodes);
            for (TreeItem<Object> child : parentItem.getChildren()) {
                if (child.getValue() instanceof Node node && !kept.contains(node)) {
                    forget(node);
                }
            }
            List<TreeItem<Object>> children = new ArrayList<>(state.nodes.size() + 2);
            if (state.firstIndex > 0) {
                children.add(new TreeItem<>(new Previous(parent)));
            }
            for (Node child : state.nodes) {
                TreeItem<Object> existing = items.get(child);
                children.add(existing != null ? existing : treeItem(child));
            }
            if (state.next != null) {
                children.add(new TreeItem<>(new More(parent)));
            }
            parentItem.getChildren().setAll(children);
        }
        if (parent == recordsParent) {
            fillRecords(parent);
        }
    }

    /** Drops what was loaded below a node that leaves the tree. */
    private void forget(Node node) {
        items.remove(node);
        Loaded state = loaded.remove(node);
        if (state != null) {
            state.nodes.forEach(this::forget);
        }
    }

    // ---- records ------------------------------------------------------------------------------

    private void showRecords(Node parent) {
        recordsParent = parent;
        childField.setDisable(!parent.hasChildren());
        exportButton.setDisable(!parent.hasChildren());
        if (!parent.hasChildren()) {
            records.getItems().clear();
            records.getColumns().clear();
            recordsLabel.setText(I18n.t("LargeFileView.has-no-child-elements", parent.xpath()));
            loadMoreButton.setDisable(true);
            return;
        }
        if (loaded.containsKey(parent)) {
            fillRecords(parent);
        } else {
            loadNext(parent);
        }
    }

    @FXML
    public void onLoadMore() {
        if (recordsParent != null) {
            loadNext(recordsParent);
        }
    }

    /** The table: one row per child, a column per field (attributes and leaf texts) of the loaded children. */
    private void fillRecords(Node parent) {
        Loaded state = loaded.get(parent);
        if (state == null) {
            return;
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        boolean anyText = false;
        for (Node child : state.nodes) {
            if (keys.size() < 40) {
                keys.addAll(child.fields().keySet());
            }
            anyText |= !child.text().isEmpty();
        }
        records.getColumns().clear();
        records.getColumns().add(column("#", 70, n -> String.format(Locale.ROOT, "%,d", n.index() + 1)));
        records.getColumns().add(column(I18n.t("LargeFileView.column-element"), 120, Node::name));
        for (String key : keys) {
            records.getColumns().add(column(key, 120, n -> n.fields().getOrDefault(key, "")));
        }
        if (anyText) {
            records.getColumns().add(column(I18n.t("LargeFileView.column-text"), 160, Node::text));
        }
        records.getItems().setAll(state.nodes);
        String range = state.nodes.isEmpty() ? "0" : String.format(Locale.ROOT, "%,d–%,d", state.firstIndex + 1,
                state.firstIndex + state.nodes.size());
        String total = state.pageIndex != null ? I18n.t("LargeFileView.of-total", String.format(Locale.ROOT, "%,d",
                state.pageIndex.total())) : state.next != null ? I18n.t("LargeFileView.more-available") : "";
        recordsLabel.setText(I18n.t("LargeFileView.children-range", parent.xpath(), range, total));
        loadMoreButton.setDisable(state.next == null);
    }

    private static TableColumn<Node, String> column(String title, double width, java.util.function.Function<Node, String> value) {
        TableColumn<Node, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        return column;
    }

    // ---- navigation ---------------------------------------------------------------------------

    /** Focuses the path field (Ctrl+L). */
    void focusPath() {
        pathField.requestFocus();
        pathField.selectAll();
    }

    @FXML
    public void onGoToPath() {
        LargeXmlStructure structure = host.structure();
        String path = pathField.getText();
        if (structure == null || path == null || path.isBlank()) {
            return;
        }
        host.background(I18n.t("LargeFileView.finding", path.strip()),
                (cancelled, progress) -> structure.resolve(path, PAGE, cancelled, progress), steps -> reveal(steps, null));
    }

    @FXML
    public void onGoToChild() {
        Node parent = recordsParent;
        Loaded state = parent == null ? null : loaded.get(parent);
        if (state == null) {
            return;
        }
        long number;
        try {
            number = Long.parseLong(childField.getText().strip().replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            host.status(I18n.t("LargeFileView.enter-a-child-number"));
            return;
        }
        long index = number - 1;
        Runnable select = () -> selectChild(parent, index);
        if (state.covers(index)) {
            select.run();
            return;
        }
        withPageIndex(parent, state, pages -> {
            Cursor cursor = pages.pageOf(index);
            if (cursor == null) {
                host.status(I18n.t("LargeFileView.child-number-out-of-range", parent.xpath(),
                        String.format(Locale.ROOT, "%,d", pages.total())));
                return;
            }
            loadPageAt(parent, cursor.index() == 0 ? null : cursor, select);
        });
    }

    private void selectChild(Node parent, long index) {
        Loaded state = loaded.get(parent);
        Node child = state == null ? null : state.at(index);
        if (child == null) {
            return;
        }
        records.getSelectionModel().clearSelection();
        records.getSelectionModel().select(child);
        records.scrollTo(Math.max(0, records.getSelectionModel().getSelectedIndex() - 3));
        records.requestFocus();
    }

    /** Shows the element that contains the byte {@code offset} (Text tab → Show in Structure). */
    void revealOffset(long offset) {
        LargeXmlStructure structure = host.structure();
        if (structure == null) {
            return;
        }
        host.background(I18n.t("LargeFileView.finding-the-element"),
                (cancelled, progress) -> structure.pathAt(offset, PAGE, cancelled, progress), steps -> {
                    if (steps.isEmpty()) {
                        host.status(I18n.t("LargeFileView.outside-the-root"));
                    } else {
                        reveal(steps, null);
                    }
                });
    }

    /**
     * Expands the tree along the steps (loading the page that lists each one) and selects the last.
     *
     * @param done receives the last node (may be {@code null})
     */
    void reveal(List<Step> steps, Consumer<Node> done) {
        ensureRoot(() -> {
            TreeItem<Object> root = tree.getRoot();
            if (root == null || !(root.getValue() instanceof Node rootNode) || steps.isEmpty()
                    || !rootNode.name().equals(steps.getFirst().name())) {
                return;
            }
            Deque<Step> rest = new ArrayDeque<>(steps.subList(1, steps.size()));
            revealNext(rootNode, rest, done);
        });
    }

    private void revealNext(Node current, Deque<Step> rest, Consumer<Node> done) {
        TreeItem<Object> item = items.get(current);
        if (rest.isEmpty() || item == null) {
            if (item != null) {
                expandParents(item);
                tree.getSelectionModel().select(item);
                int row = tree.getRow(item);
                tree.scrollTo(Math.max(0, row - 3));
            }
            if (done != null) {
                done.accept(current);
            }
            return;
        }
        Step step = rest.poll();
        Loaded state = loaded.get(current);
        Runnable next = () -> {
            Loaded now = loaded.get(current);
            Node child = now == null ? null : now.at(step.index());
            if (child == null) {
                host.status(I18n.t("LargeFileView.cannot-show", current.xpath() + "/" + step.name() + "[" + step.position() + "]"));
                return;
            }
            item.setExpanded(true);
            revealNext(child, rest, done);
        };
        if (state != null && state.covers(step.index())) {
            next.run();
        } else {
            Cursor page = step.page() != null && step.page().index() > 0 ? step.page() : null;
            loadPageAt(current, page, next);
        }
    }

    private static void expandParents(TreeItem<Object> item) {
        for (TreeItem<Object> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
    }

    // ---- menus and actions --------------------------------------------------------------------

    private Node selectedTreeNode() {
        var item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof Node node ? node : null;
    }

    private ContextMenu nodeMenu(Supplier<Node> target, boolean inTree) {
        MenuItem show = menuItem(I18n.t("LargeFileView.show-in-text"), "bi-text-left", () -> host.showInText(target.get()));
        MenuItem graphic = menuItem(I18n.t("LargeFileView.show-in-graphic"), "bi-grid-3x3",
                () -> {
                    if (inTree) {
                        showNodeInGraphic(target.get());
                    } else {
                        showRecordInGraphic(target.get());
                    }
                });
        MenuItem open = menuItem(I18n.t("LargeFileView.open-as-new-document"), "bi-box-arrow-up-right",
                () -> extract(target.get()));
        MenuItem copy = menuItem(I18n.t("LargeFileView.copy-xpath"), "bi-clipboard", () -> {
            Node node = target.get();
            if (node != null) {
                ClipboardContent content = new ClipboardContent();
                content.putString(node.xpath());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        ContextMenu menu = new ContextMenu(show, graphic, open, copy);
        menu.setOnShowing(e -> {
            boolean none = target.get() == null;
            show.setDisable(none);
            graphic.setDisable(none);
            open.setDisable(none);
            copy.setDisable(none);
        });
        return menu;
    }

    private static MenuItem menuItem(String text, String icon, Runnable action) {
        MenuItem item = new MenuItem(text, new IconifyIcon(icon));
        item.setOnAction(e -> action.run());
        return item;
    }

    @FXML
    public void onShowInGraphic() {
        Node row = records.getSelectionModel().getSelectedItem();
        if (row != null && records.isFocused()) {
            showRecordInGraphic(row);
        } else if (selectedTreeNode() != null) {
            showNodeInGraphic(selectedTreeNode());
        } else if (row != null) {
            showRecordInGraphic(row);
        }
    }

    /** The current selection as a window, for the Graphic tab when it is opened without one. */
    LargeFileView.WindowSpec selectionSpec() {
        Node node = selectedTreeNode();
        return node == null ? null : specFor(node);
    }

    /** A tree node: the element itself when it fits, else a page of its children. */
    void showNodeInGraphic(Node node) {
        LargeFileView.WindowSpec spec = node == null ? null : specFor(node);
        if (spec != null) {
            host.showInGraphic(spec);
        }
    }

    private LargeFileView.WindowSpec specFor(Node node) {
        TreeItem<Object> item = items.get(node);
        boolean fits = node.end() - node.start() <= LargeXmlWindow.MAX_WINDOW_BYTES;
        Node parent = item != null && item.getParent() != null && item.getParent().getValue() instanceof Node p ? p : null;
        if (fits && parent == null) {
            return new LargeFileView.WindowSpec(null, List.of(), node, null, 1);
        }
        if (fits) {
            Loaded state = loaded.get(parent);
            Cursor cursor = state != null && state.covers(node.index()) ? state.cursorOf(node.index()) : null;
            if (cursor != null) {
                return new LargeFileView.WindowSpec(parent, ancestorsOf(parent), parent, cursor, 1);
            }
        }
        if (!node.hasChildren()) {
            return null;
        }
        Loaded state = loaded.get(node);
        Cursor start = state != null && !state.pages.isEmpty() && state.firstIndex > 0 ? state.pages.getFirst() : null;
        return new LargeFileView.WindowSpec(node, ancestorsOf(node), node, start, PAGE);
    }

    /** A records row: a page of the shown element's children starting at that row. */
    void showRecordInGraphic(Node row) {
        Node parent = recordsParent;
        Loaded state = parent == null ? null : loaded.get(parent);
        if (row == null || state == null || !state.covers(row.index())) {
            return;
        }
        Cursor cursor = row.index() == 0 ? null : state.cursorOf(row.index());
        host.showInGraphic(new LargeFileView.WindowSpec(parent, ancestorsOf(parent), parent, cursor, PAGE));
    }

    /** @return the elements from the root down to the parent of {@code node} */
    List<Node> ancestorsOf(Node node) {
        List<Node> ancestors = new ArrayList<>();
        TreeItem<Object> item = items.get(node);
        for (TreeItem<Object> parent = item == null ? null : item.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getValue() instanceof Node n) {
                ancestors.addFirst(n);
            }
        }
        return ancestors;
    }

    /** Opens the element as a new, unsaved document (up to 50 MB). */
    void extract(Node node) {
        LargeXmlStructure structure = host.structure();
        if (node == null || structure == null) {
            return;
        }
        host.background(I18n.t("LargeFileView.reading", node.name()),
                (cancelled, progress) -> structure.text(node, LargeFileView.MAX_EXTRACT_BYTES),
                text -> host.openDocument(node.name() + "-" + node.position() + ".xml", text));
    }

    @FXML
    public void onExportCsv() {
        export(LargeRecordExport.Format.CSV);
    }

    @FXML
    public void onExportExcel() {
        export(LargeRecordExport.Format.XLSX);
    }

    private void export(LargeRecordExport.Format format) {
        Node parent = recordsParent;
        LargeXmlStructure structure = host.structure();
        if (parent == null || structure == null || !parent.hasChildren()) {
            return;
        }
        File target;
        if (exportTarget != null) {
            target = exportTarget.apply(format);
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("LargeFileView.export-records"));
            String extension = format == LargeRecordExport.Format.CSV ? "csv" : "xlsx";
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    format == LargeRecordExport.Format.CSV ? "CSV" : "Excel", "*." + extension));
            chooser.setInitialFileName(parent.name() + "." + extension);
            target = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, host.window());
        }
        if (target == null) {
            return;
        }
        host.background(I18n.t("LargeFileView.exporting", parent.xpath()),
                (cancelled, progress) -> LargeRecordExport.export(structure, parent, format, target.toPath(), cancelled,
                        progress),
                result -> host.status(I18n.t("LargeFileView.exported", String.format(Locale.ROOT, "%,d", result.rows()),
                        target.getName())));
    }

    static String label(Node node) {
        StringBuilder label = new StringBuilder(node.name());
        String id = node.fields().entrySet().stream().filter(e -> e.getKey().startsWith("@")).findFirst()
                .map(e -> " " + e.getKey().substring(1) + "=\"" + e.getValue() + "\"").orElse("");
        label.append(id);
        if (!node.hasChildren() && !node.text().isEmpty()) {
            label.append(" = ").append(node.text().length() > 60 ? node.text().substring(0, 60) + "…" : node.text());
        }
        return label.toString();
    }

    // ---- test access --------------------------------------------------------------------------

    TreeView<Object> treeForTesting() {
        return tree;
    }

    TableView<Node> recordsTable() {
        return records;
    }

    String recordsLabelText() {
        return recordsLabel.getText();
    }

    void setPathForTesting(String path) {
        pathField.setText(path);
    }

    void setChildForTesting(String number) {
        childField.setText(number);
    }

    Node recordsParent() {
        return recordsParent;
    }

    /** @return the cursor/state for a node (tests) */
    Loaded loadedFor(Node node) {
        return loaded.get(node);
    }

}
