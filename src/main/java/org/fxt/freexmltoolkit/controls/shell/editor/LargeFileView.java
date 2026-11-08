package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.i18n.I18n;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.collections.ObservableListBase;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.largefile.LargeFileIndex;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure;

/**
 * Large File Viewer: a read-only view of a text or XML file of any size. The file is indexed once in
 * the background ({@link LargeFileIndex}); the visible rows are read from disk as they scroll into view,
 * so a file of several GB needs only a few MB of memory. Go to line, find (in the background, list of
 * hits), select rows (click, Shift+click) and copy them (Ctrl+C).
 */
public class LargeFileView extends VBox {

    private static final Logger logger = LogManager.getLogger(LargeFileView.class);
    static final double ROW_HEIGHT = 18;
    static final int MAX_HITS = 10_000;
    static final int MAX_COPY_ROWS = 100_000;
    private static final Pattern TOKENS = Pattern.compile(
            "(<!--.*?(?:-->|$))|(</?[\\w:.-]+|/?>|<\\?[\\w:.-]*|\\?>)|([\\w:.-]+)(?=\\s*=)|(\"[^\"]*\"?|'[^']*'?)");

    @FXML private Label info;
    @FXML private TextField lineField;
    @FXML private TextField findField;
    @FXML private CheckBox regexCheck;
    @FXML private CheckBox caseCheck;
    @FXML private Button findButton;
    @FXML private HBox progressBox;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private SplitPane split;
    @FXML private StackPane rowsSlot;
    @FXML private ListView<LargeFileIndex.Hit> results;
    @FXML private Label status;
    @FXML private javafx.scene.control.MenuButton validateButton;
    @FXML private javafx.scene.control.MenuItem validateDeclared;
    @FXML private javafx.scene.control.Tab problemsTab;
    @FXML private Label problemsLabel;
    @FXML private javafx.scene.control.TableView<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem> problems;
    @FXML private javafx.scene.control.TableColumn<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem, String> severityColumn;
    @FXML private javafx.scene.control.TableColumn<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem, String> problemLineColumn;
    @FXML private javafx.scene.control.TableColumn<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem, String> problemColumnColumn;
    @FXML private javafx.scene.control.TableColumn<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem, String> messageColumn;
    @FXML private javafx.scene.control.TabPane views;
    @FXML private javafx.scene.control.Tab textTab;
    @FXML private javafx.scene.control.Tab structureTab;
    @FXML private javafx.scene.control.TreeView<Object> tree;
    @FXML private Label recordsLabel;
    @FXML private Button loadMoreButton;
    @FXML private javafx.scene.control.TableView<LargeXmlStructure.Node> records;

    /** Children per page in the tree and the records table. */
    static final int PAGE = 1000;
    /** Elements larger than this are not opened as a new document. */
    static final int MAX_EXTRACT_BYTES = 50 * 1024 * 1024;

    private LargeXmlStructure structure;
    /** Set when the view closes: stops structure reads. */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Loaded children of an element: the nodes so far, where the next page starts (-1: all), the counters. */
    private final java.util.Map<LargeXmlStructure.Node, Loaded> loaded = new java.util.HashMap<>();
    private LargeXmlStructure.Node recordsParent;
    private java.util.function.BiConsumer<String, String> documentOpener = (name, text) -> { };

    private static final class Loaded {
        final List<LargeXmlStructure.Node> nodes = new ArrayList<>();
        long next;
        java.util.Map<String, Integer> positions;
        boolean loading;
    }

    /** "Load more" entry of the tree. */
    private record More(LargeXmlStructure.Node parent) {
    }

    private final Path path;
    private final ListView<Integer> flow = new ListView<>();
    private final RowNumbers rowNumbers = new RowNumbers();
    private volatile LargeFileIndex index;
    private AtomicBoolean cancel = new AtomicBoolean();
    private Runnable onIndexed = () -> { };

    public LargeFileView(Path path) {
        this.path = path;
        FxmlViews.load(this, "/pages/views/LargeFileView.fxml");
        getChildren().remove(results);
        flow.setFixedCellSize(ROW_HEIGHT);
        flow.setItems(rowNumbers);
        flow.setCellFactory(f -> new RowCell());
        flow.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        flow.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> selectionChanged());
        flow.setId("large-file-flow");
        flow.getStyleClass().add("fxt-large-file-rows");
        rowsSlot.getChildren().add(flow);
        results.setCellFactory(list -> new HitCell());
        results.getSelectionModel().selectedItemProperty().addListener((obs, o, hit) -> {
            if (hit != null) {
                showRow(hit.row());
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        setUpStructure();
        setUpProblems();
        setDisableControls(true);
        info.setText(path.getFileName() + " · indexing…");
        index();
    }

    /** @return the file shown */
    public Path path() {
        return path;
    }

    /** Called on the FX thread once the file is indexed (tests). */
    void setOnIndexed(Runnable action) {
        onIndexed = action == null ? () -> { } : action;
    }

    private void index() {
        AtomicBoolean cancelled = new AtomicBoolean();
        cancel = cancelled;
        long size = path.toFile().length();
        showProgress("Indexing " + path.getFileName() + "…", 0);
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                LargeFileIndex built = LargeFileIndex.build(path,
                        read -> Platform.runLater(() -> progressBar.setProgress(size <= 0 ? -1 : (double) read / size)),
                        cancelled::get);
                Platform.runLater(() -> {
                    index = built;
                    hideProgress();
                    setDisableControls(false);
                    rowNumbers.setSize((int) Math.min(Integer.MAX_VALUE - 8, built.rowCount()));
                    info.setText(path.getFileName() + " · " + LargeFileGuard.formatSize(built.size()) + " · "
                            + String.format(Locale.ROOT, "%,d", built.lineCount()) + " lines · "
                            + built.charset().name() + " · read-only");
                    status.setText(I18n.t("LargeFileView.click-a-row-to-select"));
                    structureTab.setDisable(!looksLikeXml(built));
                    onIndexed.run();
                });
            } catch (CancellationException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    info.setText(path.getFileName() + " · indexing cancelled");
                });
            } catch (IOException | RuntimeException e) {
                logger.warn("Cannot index {}", path, e);
                Platform.runLater(() -> {
                    hideProgress();
                    info.setText(path.getFileName() + " · cannot be shown: " + e.getMessage());
                });
            }
        });
    }

    private void setDisableControls(boolean disabled) {
        validateButton.setDisable(disabled);
        lineField.setDisable(disabled);
        findField.setDisable(disabled);
        findButton.setDisable(disabled);
    }

    private void showProgress(String text, double progress) {
        progressLabel.setText(text);
        progressBar.setProgress(progress);
        progressBox.setVisible(true);
        progressBox.setManaged(true);
    }

    private void hideProgress() {
        progressBox.setVisible(false);
        progressBox.setManaged(false);
    }

    @FXML
    public void onCancel() {
        cancel.set(true);
    }

    private void onKey(KeyEvent e) {
        if (e.isShortcutDown() && e.getCode() == KeyCode.G) {
            lineField.requestFocus();
            lineField.selectAll();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
            findField.requestFocus();
            findField.selectAll();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.C && !(e.getTarget() instanceof TextField)) {
            copySelection();
            e.consume();
        } else if (e.getCode() == KeyCode.F3) {
            nextHit(e.isShiftDown() ? -1 : 1);
            e.consume();
        }
    }

    // ---- navigation ----------------------------------------------------------------------------

    @FXML
    public void onGoToLine() {
        if (index == null) {
            return;
        }
        try {
            long line = Long.parseLong(lineField.getText().strip().replace(",", "").replace(".", ""));
            showRow(index.rowOfLine(line));
        } catch (NumberFormatException e) {
            status.setText(I18n.t("LargeFileView.enter-a-line-number-between", index.lineCount()));
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    /** Scrolls to a row and selects it. */
    public void showRow(long row) {
        if (index == null) {
            return;
        }
        int target = (int) Math.max(0, Math.min(row, rowNumbers.size() - 1));
        flow.getSelectionModel().clearAndSelect(target);
        flow.scrollTo(Math.max(0, target - 3));
        flow.getFocusModel().focus(target);
    }

    /** Shows a 1-based line (the view of an editor's "go to line"). */
    public void showLine(long line) throws IOException {
        if (index != null) {
            showRow(index.rowOfLine(line));
        }
    }

    /** @return the first and last selected row, or -1 */
    private long[] selectedRange() {
        var indices = flow.getSelectionModel().getSelectedIndices();
        if (indices.isEmpty()) {
            return new long[] {-1, -1};
        }
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (Integer i : indices) {
            min = Math.min(min, i);
            max = Math.max(max, i);
        }
        return new long[] {min, max};
    }

    private void selectionChanged() {
        long[] range = selectedRange();
        if (index == null || range[0] < 0) {
            return;
        }
        try {
            LargeFileIndex.Row first = index.row(range[0]);
            long rows = range[1] - range[0] + 1;
            status.setText(I18n.t("LargeFileView.line-byte-offset", String.format(Locale.ROOT, "%,d", first.line()), (rows > 1 ? " · " + String.format(Locale.ROOT, "%,d", rows) + " rows selected" : ""), String.format(Locale.ROOT, "%,d", first.offset())));
        } catch (IOException e) {
            status.setText("");
        }
    }

    private void copySelection() {
        long[] range = selectedRange();
        long selectionStart = range[0];
        long selectionEnd = range[1];
        if (index == null || selectionStart < 0) {
            return;
        }
        long count = Math.min(MAX_COPY_ROWS, selectionEnd - selectionStart + 1);
        try {
            List<LargeFileIndex.Row> rows = index.rows(selectionStart, (int) count);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0 && !rows.get(i).continuation()) {
                    text.append('\n');
                }
                text.append(rows.get(i).text());
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(text.toString());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText(I18n.t("LargeFileView.copied-rows", String.format(Locale.ROOT, "%,d", rows.size()), (count < selectionEnd - selectionStart + 1 ? I18n.t("LargeFileView.atMost", MAX_COPY_ROWS) : "")));
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    // ---- find ---------------------------------------------------------------------------------

    @FXML
    public void onFind() {
        if (index == null || findField.getText().isEmpty()) {
            return;
        }
        Pattern pattern;
        try {
            int flags = caseCheck.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            pattern = regexCheck.isSelected() ? Pattern.compile(findField.getText(), flags)
                    : Pattern.compile(Pattern.quote(findField.getText()), flags);
        } catch (PatternSyntaxException e) {
            status.setText(I18n.t("LargeFileView.invalid-regular-expression", e.getDescription()));
            return;
        }
        cancel.set(true);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancel = cancelled;
        LargeFileIndex searched = index;
        long size = searched.size();
        showProgress("Searching…", 0);
        findButton.setDisable(true);
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                List<LargeFileIndex.Hit> hits = searched.search(pattern, MAX_HITS,
                        read -> Platform.runLater(() -> progressBar.setProgress(size <= 0 ? -1 : (double) read / size)),
                        cancelled::get);
                Platform.runLater(() -> showHits(hits));
            } catch (CancellationException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    findButton.setDisable(false);
                    status.setText(I18n.t("LargeFileView.search-cancelled"));
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    findButton.setDisable(false);
                    status.setText(I18n.t("LargeFileView.search-failed", e.getMessage()));
                });
            }
        });
    }

    private void showHits(List<LargeFileIndex.Hit> hits) {
        hideProgress();
        findButton.setDisable(false);
        results.getItems().setAll(hits);
        if (!split.getItems().contains(results)) {
            results.setVisible(true);
            results.setManaged(true);
            split.getItems().add(results);
            split.setDividerPositions(0.72);
        }
        status.setText(hits.isEmpty() ? "No matches." : String.format(Locale.ROOT, "%,d", hits.size())
                + (hits.size() >= MAX_HITS ? "+ matches (the first " + MAX_HITS + " are listed)" : " matches")
                + ". F3: next, Shift+F3: previous.");
        if (!hits.isEmpty()) {
            results.getSelectionModel().select(0);
        }
    }

    private void nextHit(int delta) {
        int size = results.getItems().size();
        if (size == 0) {
            return;
        }
        int current = results.getSelectionModel().getSelectedIndex();
        int next = current < 0 ? 0 : Math.floorMod(current + delta, size);
        results.getSelectionModel().select(next);
        results.scrollTo(next);
    }

    // ---- structure ------------------------------------------------------------------------------

    /** Opens an extracted element as a new document (name, text). */
    public void setDocumentOpener(java.util.function.BiConsumer<String, String> opener) {
        documentOpener = opener == null ? (name, text) -> { } : opener;
    }

    private static boolean looksLikeXml(LargeFileIndex index) {
        try {
            for (LargeFileIndex.Row row : index.rows(0, 20)) {
                String text = row.text().strip();
                if (!text.isEmpty()) {
                    return text.startsWith("<");
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private void setUpStructure() {
        structureTab.setDisable(true);
        structureTab.setOnSelectionChanged(e -> {
            if (structureTab.isSelected() && structure == null && index != null) {
                loadRoot();
            }
        });
        tree.setShowRoot(true);
        tree.setCellFactory(t -> new javafx.scene.control.TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof More) {
                    setText(I18n.t("LargeFileView.load-more"));
                    setGraphic(null);
                } else if (item instanceof LargeXmlStructure.Node node) {
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
                loadPage(more.parent(), item.getParent());
            } else if (item.getValue() instanceof LargeXmlStructure.Node node) {
                showRecords(node);
            }
        });
        javafx.scene.control.ContextMenu treeMenu = nodeMenu(() -> selectedTreeNode());
        tree.setContextMenu(treeMenu);
        records.setContextMenu(nodeMenu(() -> records.getSelectionModel().getSelectedItem()));
        records.setRowFactory(t -> {
            javafx.scene.control.TableRow<LargeXmlStructure.Node> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showInText(row.getItem());
                }
            });
            return row;
        });
    }

    private LargeXmlStructure.Node selectedTreeNode() {
        var item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof LargeXmlStructure.Node node ? node : null;
    }

    private javafx.scene.control.ContextMenu nodeMenu(java.util.function.Supplier<LargeXmlStructure.Node> target) {
        javafx.scene.control.MenuItem show = menuItem(I18n.t("LargeFileView.show-in-text"), "bi-text-left", () -> showInText(target.get()));
        javafx.scene.control.MenuItem open = menuItem(I18n.t("LargeFileView.open-as-new-document"), "bi-box-arrow-up-right",
                () -> extract(target.get()));
        javafx.scene.control.MenuItem copy = menuItem(I18n.t("LargeFileView.copy-xpath"), "bi-clipboard", () -> {
            LargeXmlStructure.Node node = target.get();
            if (node != null) {
                ClipboardContent content = new ClipboardContent();
                content.putString(node.xpath());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(show, open, copy);
        menu.setOnShowing(e -> {
            boolean none = target.get() == null;
            show.setDisable(none);
            open.setDisable(none);
            copy.setDisable(none);
        });
        return menu;
    }

    private static javafx.scene.control.MenuItem menuItem(String text, String icon, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text,
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon(icon));
        item.setOnAction(e -> action.run());
        return item;
    }

    static String label(LargeXmlStructure.Node node) {
        StringBuilder label = new StringBuilder(node.name());
        String id = node.fields().entrySet().stream().filter(e -> e.getKey().startsWith("@")).findFirst()
                .map(e -> " " + e.getKey().substring(1) + "=\"" + e.getValue() + "\"").orElse("");
        label.append(id);
        if (!node.hasChildren() && !node.text().isEmpty()) {
            label.append(" = ").append(node.text().length() > 60 ? node.text().substring(0, 60) + "…" : node.text());
        }
        return label.toString();
    }

    private void loadRoot() {
        LargeFileIndex current = index;
        recordsLabel.setText(I18n.t("LargeFileView.reading-the-structure"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                LargeXmlStructure s = new LargeXmlStructure(current);
                LargeXmlStructure.Node root = s.root();
                Platform.runLater(() -> {
                    structure = s;
                    javafx.scene.control.TreeItem<Object> item = treeItem(root);
                    tree.setRoot(item);
                    item.setExpanded(true);
                    tree.getSelectionModel().select(item);
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> recordsLabel.setText(I18n.t("LargeFileView.no-xml-structure", e.getMessage())));
            }
        });
    }

    /** A tree item that lists its children when it is expanded the first time. */
    private javafx.scene.control.TreeItem<Object> treeItem(LargeXmlStructure.Node node) {
        javafx.scene.control.TreeItem<Object> item = new javafx.scene.control.TreeItem<>(node) {
            @Override
            public boolean isLeaf() {
                return !node.hasChildren();
            }
        };
        if (node.hasChildren()) {
            item.getChildren().add(new javafx.scene.control.TreeItem<>("Loading…"));
            item.expandedProperty().addListener((obs, was, expanded) -> {
                if (expanded && !loaded.containsKey(node)) {
                    loadPage(node, item);
                }
            });
        }
        return item;
    }

    /** Loads the next page of {@code parent}'s children into its tree item and, if shown, the table. */
    private void loadPage(LargeXmlStructure.Node parent, javafx.scene.control.TreeItem<Object> parentItem) {
        Loaded state = loaded.computeIfAbsent(parent, p -> {
            Loaded l = new Loaded();
            l.next = p.start();
            return l;
        });
        if (state.loading || state.next < 0 || structure == null) {
            return;
        }
        state.loading = true;
        LargeXmlStructure s = structure;
        long from = state.next;
        var positions = state.positions;
        showProgress("Reading children of " + parent.name() + "…", -1);
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                LargeXmlStructure.Page page = s.children(parent, from, positions, PAGE, closed::get, null);
                Platform.runLater(() -> {
                    hideProgress();
                    state.loading = false;
                    state.nodes.addAll(page.children());
                    state.next = page.next();
                    state.positions = page.positions();
                    if (parentItem != null) {
                        parentItem.getChildren().removeIf(c -> !(c.getValue() instanceof LargeXmlStructure.Node));
                        for (LargeXmlStructure.Node child : page.children()) {
                            parentItem.getChildren().add(treeItem(child));
                        }
                        if (state.next >= 0) {
                            parentItem.getChildren().add(new javafx.scene.control.TreeItem<>(new More(parent)));
                        }
                    }
                    if (parent.equals(recordsParent)) {
                        fillRecords(parent);
                    }
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    state.loading = false;
                    recordsLabel.setText(I18n.t("LargeFileView.cannot-read-the-children", e.getMessage()));
                });
            }
        });
    }

    private javafx.scene.control.TreeItem<Object> itemOf(LargeXmlStructure.Node node) {
        java.util.Deque<javafx.scene.control.TreeItem<Object>> pending = new java.util.ArrayDeque<>();
        if (tree.getRoot() != null) {
            pending.add(tree.getRoot());
        }
        while (!pending.isEmpty()) {
            var item = pending.poll();
            if (node.equals(item.getValue())) {
                return item;
            }
            if (item.isExpanded()) {
                pending.addAll(item.getChildren());
            }
        }
        return null;
    }

    private void showRecords(LargeXmlStructure.Node parent) {
        recordsParent = parent;
        if (!parent.hasChildren()) {
            records.getItems().clear();
            records.getColumns().clear();
            recordsLabel.setText(parent.xpath() + " has no child elements.");
            loadMoreButton.setDisable(true);
            return;
        }
        Loaded state = loaded.get(parent);
        if (state == null) {
            loadPage(parent, itemOf(parent));
        } else {
            fillRecords(parent);
        }
    }

    @FXML
    public void onLoadMore() {
        if (recordsParent != null) {
            loadPage(recordsParent, itemOf(recordsParent));
        }
    }

    /** The table: one row per child, a column per field (attributes and leaf texts) of the loaded children. */
    private void fillRecords(LargeXmlStructure.Node parent) {
        Loaded state = loaded.get(parent);
        if (state == null) {
            return;
        }
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        boolean anyText = false;
        for (LargeXmlStructure.Node child : state.nodes) {
            if (keys.size() < 40) {
                keys.addAll(child.fields().keySet());
            }
            anyText |= !child.text().isEmpty();
        }
        records.getColumns().clear();
        records.getColumns().add(column("#", 60, n -> Integer.toString(n.position())));
        records.getColumns().add(column("Element", 120, LargeXmlStructure.Node::name));
        for (String key : keys) {
            records.getColumns().add(column(key, 120, n -> n.fields().getOrDefault(key, "")));
        }
        if (anyText) {
            records.getColumns().add(column("Text", 160, LargeXmlStructure.Node::text));
        }
        records.getItems().setAll(state.nodes);
        recordsLabel.setText(I18n.t("LargeFileView.children-of", parent.xpath(), String.format(Locale.ROOT, "%,d", state.nodes.size()), (state.next >= 0 ? " loaded, more available" : "")));
        loadMoreButton.setDisable(state.next < 0);
    }

    private static javafx.scene.control.TableColumn<LargeXmlStructure.Node, String> column(String title, double width,
            java.util.function.Function<LargeXmlStructure.Node, String> value) {
        javafx.scene.control.TableColumn<LargeXmlStructure.Node, String> column = new javafx.scene.control.TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(value.apply(c.getValue())));
        return column;
    }

    /** Switches to the Text tab at the element's first line. */
    void showInText(LargeXmlStructure.Node node) {
        if (node == null || index == null) {
            return;
        }
        try {
            long row = index.rowOfOffset(node.start());
            views.getSelectionModel().select(textTab);
            showRow(row);
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    /** Opens the element as a new, unsaved document (up to 50 MB). */
    void extract(LargeXmlStructure.Node node) {
        if (node == null || structure == null) {
            return;
        }
        LargeXmlStructure s = structure;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String text = s.text(node, MAX_EXTRACT_BYTES);
                Platform.runLater(() -> documentOpener.accept(node.name() + "-" + node.position() + ".xml", text));
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> status.setText(I18n.t("LargeFileView.cannot-open-the-element", e.getMessage())));
            }
        });
    }

    // ---- validation ----------------------------------------------------------------------------

    /** Stop after this many problems. */
    static final int MAX_PROBLEMS = 1000;
    /** Test hook: the schema chosen in "Against a Schema…" instead of a file chooser. */
    static java.util.function.Supplier<java.io.File> schemaChoice;

    private void setUpProblems() {
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(severityColumn, p -> p.severity());
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(problemLineColumn,
                p -> p.line() > 0 ? String.format(Locale.ROOT, "%,d", p.line()) : "");
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(problemColumnColumn,
                p -> p.column() > 0 ? Integer.toString(p.column()) : "");
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(messageColumn, p -> p.message());
        problems.setRowFactory(t -> {
            javafx.scene.control.TableRow<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem> row =
                    new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && row.getItem().line() > 0) {
                    try {
                        views.getSelectionModel().select(textTab);
                        showLine(row.getItem().line());
                    } catch (IOException ex) {
                        status.setText(I18n.t("LargeFileView.cannot-read-the-file", ex.getMessage()));
                    }
                }
            });
            return row;
        });
        validateButton.setOnShowing(e -> {
            if (validateDeclared.getUserData() == null) {
                validateDeclared.setUserData(Boolean.TRUE);
                java.io.File declared = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.declaredSchema(path)
                        .orElse(null);
                validateDeclared.setDisable(declared == null);
                validateDeclared.setText(declared == null ? "Against the Declared Schema (none)"
                        : "Against " + declared.getName());
            }
        });
    }

    @FXML
    public void onValidateDeclared() {
        validate(org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.declaredSchema(path).orElse(null), true);
    }

    @FXML
    public void onValidateSchema() {
        java.io.File schema;
        if (schemaChoice != null) {
            schema = schemaChoice.get();
        } else {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle(I18n.t("LargeFileView.validate-against-a-schema"));
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XML Schema", "*.xsd"));
            schema = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser,
                    getScene() != null ? getScene().getWindow() : null);
        }
        if (schema != null) {
            validate(schema, true);
        }
    }

    @FXML
    public void onValidateWellFormed() {
        validate(null, false);
    }

    /** Validates the file in the background (streaming) and lists the problems. */
    void validate(java.io.File schema, boolean schemaWanted) {
        if (index == null) {
            return;
        }
        if (schemaWanted && schema == null) {
            status.setText(I18n.t("LargeFileView.the-document-declares-no-schema"));
            return;
        }
        cancel.set(true);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancel = cancelled;
        long size = index.size();
        validateButton.setDisable(true);
        showProgress(schema == null ? "Checking well-formedness…" : "Validating against " + schema.getName() + "…", 0);
        long t0 = System.nanoTime();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                var result = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.validate(path, schema, MAX_PROBLEMS,
                        read -> Platform.runLater(() -> progressBar.setProgress(size <= 0 ? -1 : (double) read / size)),
                        cancelled::get);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Platform.runLater(() -> showProblems(result, schema, ms));
            } catch (CancellationException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    validateButton.setDisable(false);
                    status.setText(I18n.t("LargeFileView.validation-cancelled"));
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    validateButton.setDisable(false);
                    status.setText(I18n.t("LargeFileView.validation-failed", e.getMessage()));
                });
            }
        });
    }

    private void showProblems(org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Result result,
                              java.io.File schema, long ms) {
        hideProgress();
        validateButton.setDisable(false);
        problemsTab.setDisable(false);
        problems.getItems().setAll(result.problems());
        String against = schema == null ? "Well-formedness" : schema.getName();
        String text;
        if (result.schemaError() != null) {
            text = against + ": the schema cannot be used. " + result.schemaError();
        } else if (result.valid()) {
            text = schema == null ? "The document is well-formed." : "The document is valid against " + schema.getName() + ".";
        } else {
            text = against + ": " + String.format(Locale.ROOT, "%,d", result.problems().size())
                    + (result.truncated() ? "+ problems (stopped after " + MAX_PROBLEMS + ")" : " problem(s)")
                    + (result.fatal() ? "; not well-formed, checked up to the first fatal error" : "") + ".";
        }
        problemsLabel.setText(text + " (" + String.format(Locale.ROOT, "%,d", ms) + " ms)");
        status.setText(text);
        views.getSelectionModel().select(problemsTab);
    }

    /** Releases the file and stops a running index or search. */
    public void close() {
        cancel.set(true);
        closed.set(true);
        LargeFileIndex current = index;
        if (current != null) {
            try {
                current.close();
            } catch (IOException e) {
                logger.debug("Closing {}: {}", path, e.getMessage());
            }
        }
    }

    // ---- cells --------------------------------------------------------------------------------

    /** The row numbers 0..n-1 as a list that stores nothing (n can be tens of millions). */
    static final class RowNumbers extends ObservableListBase<Integer> {
        private int size;

        void setSize(int newSize) {
            int old = size;
            beginChange();
            size = newSize;
            if (old > 0) {
                nextRemove(0, java.util.Collections.nCopies(old, 0));
            }
            if (newSize > 0) {
                nextAdd(0, newSize);
            }
            endChange();
        }

        @Override
        public Integer get(int i) {
            if (i < 0 || i >= size) {
                throw new IndexOutOfBoundsException(i);
            }
            return i;
        }

        @Override
        public int size() {
            return size;
        }
    }

    /** One display row: line number (a mark for a continuation) and the coloured text. */
    final class RowCell extends ListCell<Integer> {
        private final Label number = new Label();
        private final TextFlow text = new TextFlow();
        private final HBox box = new HBox(number, text);

        RowCell() {
            getStyleClass().add("fxt-large-file-row");
            number.getStyleClass().add("fxt-large-file-line-number");
            number.setMinWidth(80);
            number.setPadding(new Insets(0, 10, 0, 4));
            setText(null);
        }

        @Override
        protected void updateItem(Integer row, boolean empty) {
            super.updateItem(row, empty);
            text.getChildren().clear();
            LargeFileIndex current = index;
            if (empty || row == null || current == null) {
                setGraphic(null);
                return;
            }
            try {
                LargeFileIndex.Row r = current.row(row);
                number.setText(r.continuation() ? "↪" : String.format(Locale.ROOT, "%,d", r.line()));
                text.getChildren().setAll(highlight(r.text()));
            } catch (IOException e) {
                number.setText("");
                text.getChildren().setAll(new Text("(cannot read: " + e.getMessage() + ")"));
            }
            setGraphic(box);
        }
    }

    /** Light XML colouring of one row (a row may start inside a tag or comment; it is a hint). */
    static List<Text> highlight(String row) {
        List<Text> parts = new ArrayList<>();
        Matcher matcher = TOKENS.matcher(row);
        int at = 0;
        while (matcher.find()) {
            if (matcher.start() > at) {
                parts.add(text(row.substring(at, matcher.start()), "fxt-lf-text"));
            }
            String style = matcher.group(1) != null ? "fxt-lf-comment"
                    : matcher.group(2) != null ? "fxt-lf-tag"
                    : matcher.group(3) != null ? "fxt-lf-attr" : "fxt-lf-value";
            parts.add(text(matcher.group(), style));
            at = matcher.end();
        }
        if (at < row.length()) {
            parts.add(text(row.substring(at), "fxt-lf-text"));
        }
        return parts;
    }

    private static Text text(String value, String style) {
        Text text = new Text(value);
        text.getStyleClass().addAll("fxt-lf", style);
        return text;
    }

    /** A search hit: line number and the row text around the match. */
    private static final class HitCell extends ListCell<LargeFileIndex.Hit> {
        @Override
        protected void updateItem(LargeFileIndex.Hit hit, boolean empty) {
            super.updateItem(hit, empty);
            if (empty || hit == null) {
                setText(null);
                return;
            }
            String text = hit.text().strip();
            setText(String.format(Locale.ROOT, "%,d: ", hit.line()) + (text.length() > 200 ? text.substring(0, 200) + "…" : text));
        }
    }

    // test access
    LargeFileIndex indexForTesting() {
        return index;
    }

    long[] selectionForTesting() {
        return selectedRange();
    }

    List<LargeFileIndex.Hit> hitsForTesting() {
        return List.copyOf(results.getItems());
    }

    void copySelectionForTesting() {
        copySelection();
    }

    String statusForTesting() {
        return status.getText();
    }

    String infoForTesting() {
        return info.getText();
    }

    List<org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Problem> problemsForTesting() {
        return List.copyOf(problems.getItems());
    }

    String problemsLabelForTesting() {
        return problemsLabel.getText();
    }

    void selectStructureForTesting() {
        views.getSelectionModel().select(structureTab);
    }

    javafx.scene.control.TreeView<Object> treeForTesting() {
        return tree;
    }

    List<LargeXmlStructure.Node> recordsForTesting() {
        return List.copyOf(records.getItems());
    }

    List<String> recordColumnsForTesting() {
        return records.getColumns().stream().map(javafx.scene.control.TableColumn::getText).toList();
    }

    String recordsLabelForTesting() {
        return recordsLabel.getText();
    }

    void setFindForTesting(String text, boolean regex) {
        findField.setText(text);
        regexCheck.setSelected(regex);
    }

    void setLineForTesting(String text) {
        lineField.setText(text);
    }

    String flowStateForTesting() {
        return "flow " + flow.getWidth() + "x" + flow.getHeight() + " cells=" + rowNumbers.size()
                + " slot " + rowsSlot.getWidth() + "x" + rowsSlot.getHeight() + " view " + getWidth() + "x" + getHeight()
                + " scene=" + (getScene() != null);
    }

    int firstVisibleRowForTesting() {
        return flow.lookupAll(".fxt-large-file-row").stream()
                .filter(n -> n instanceof RowCell c && !c.isEmpty() && c.isVisible() && c.getIndex() >= 0)
                .mapToInt(n -> ((RowCell) n).getIndex()).min().orElse(-1);
    }
}
