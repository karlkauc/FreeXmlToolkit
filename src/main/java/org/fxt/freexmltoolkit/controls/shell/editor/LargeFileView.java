package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableListBase;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.largefile.LargeFileIndex;
import org.fxt.freexmltoolkit.service.largefile.LargeFilePatcher;
import org.fxt.freexmltoolkit.service.largefile.LargeFileValidation;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Cursor;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlWindow;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Large File Viewer: a view of a text or XML file of any size. The file is indexed once in the
 * background ({@link LargeFileIndex}); the visible rows are read from disk as they scroll into view,
 * so a file of several GB needs only a few MB of memory.
 *
 * <p>Tabs: Text (go to line, find, copy), Problems (streaming validation), Structure (element tree
 * and records, {@link LargeFileStructurePane}), Graphic (one window of the file in the grid, editable
 * and written back into the file, {@link LargeFileWindowPane}) and Statistics (element paths,
 * {@link LargeFileStatisticsPane}). A change of the file on disk is noticed and offers a reload.</p>
 */
public class LargeFileView extends VBox {

    private static final Logger logger = LogManager.getLogger(LargeFileView.class);
    static final double ROW_HEIGHT = 18;
    static final int MAX_HITS = 10_000;
    static final int MAX_COPY_ROWS = 100_000;
    /** Children per page in the tree and the records table. */
    static final int PAGE = LargeFileStructurePane.PAGE;
    /** Elements larger than this are not opened as a new document. */
    static final int MAX_EXTRACT_BYTES = 50 * 1024 * 1024;
    /** Stop after this many problems. */
    static final int MAX_PROBLEMS = 1000;
    private static final Pattern TOKENS = Pattern.compile(
            "(<!--.*?(?:-->|$))|(</?[\\w:.-]+|/?>|<\\?[\\w:.-]*|\\?>)|([\\w:.-]+)(?=\\s*=)|(\"[^\"]*\"?|'[^']*'?)");

    /** A piece of work run off the FX thread; polls {@code cancelled}, reports byte offsets to {@code progress}. */
    @FunctionalInterface
    interface Job<T> {
        T run(BooleanSupplier cancelled, LongConsumer progress) throws Exception;
    }

    /**
     * Which part of the file the Graphic tab shows.
     *
     * @param parent    the element whose children the window holds, or {@code null} for the root window
     * @param ancestors the elements from the root down to the parent's parent
     * @param subject   the root (root window) or the parent
     * @param start     the cursor of the first child, or {@code null} for the first child
     * @param maxCount  at most this many children (fewer when they do not fit {@link LargeXmlWindow#MAX_WINDOW_BYTES})
     */
    record WindowSpec(Node parent, List<Node> ancestors, Node subject, Cursor start, int maxCount) {
    }

    /** A window that is shown: its spec, the window, the children it holds, where the next one starts. */
    private record ShownWindow(WindowSpec spec, LargeXmlWindow window, List<Node> children, Cursor next) {
    }

    /** What to do with edits that are not written yet. */
    enum PendingChoice { APPLY, DISCARD, CANCEL }

    /** Test hook: answers the "unapplied changes" question instead of a dialog. */
    static java.util.function.Function<String, PendingChoice> pendingAnswer;
    /** Test hook: the schema chosen in "Against a Schema…" instead of a file chooser. */
    static java.util.function.Supplier<java.io.File> schemaChoice;

    @FXML private Label info;
    @FXML private TextField lineField;
    @FXML private TextField findField;
    @FXML private CheckBox regexCheck;
    @FXML private CheckBox caseCheck;
    @FXML private Button findButton;
    @FXML private HBox progressBox;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private HBox changedBar;
    @FXML private Label changedLabel;
    @FXML private SplitPane split;
    @FXML private StackPane rowsSlot;
    @FXML private ListView<LargeFileIndex.Hit> results;
    @FXML private Label status;
    @FXML private javafx.scene.control.MenuButton validateButton;
    @FXML private MenuItem validateDeclared;
    @FXML private javafx.scene.control.Tab problemsTab;
    @FXML private Label problemsLabel;
    @FXML private javafx.scene.control.TableView<LargeFileValidation.Problem> problems;
    @FXML private javafx.scene.control.TableColumn<LargeFileValidation.Problem, String> severityColumn;
    @FXML private javafx.scene.control.TableColumn<LargeFileValidation.Problem, String> problemLineColumn;
    @FXML private javafx.scene.control.TableColumn<LargeFileValidation.Problem, String> problemColumnColumn;
    @FXML private javafx.scene.control.TableColumn<LargeFileValidation.Problem, String> messageColumn;
    @FXML private javafx.scene.control.TabPane views;
    @FXML private javafx.scene.control.Tab textTab;
    @FXML private javafx.scene.control.Tab structureTab;
    @FXML private javafx.scene.control.Tab graphicTab;
    @FXML private javafx.scene.control.Tab statisticsTab;

    private final Path path;
    private final ListView<Integer> flow = new ListView<>();
    private final RowNumbers rowNumbers = new RowNumbers();
    private final LargeFileStructurePane structurePane;
    private final LargeFileWindowPane windowPane;
    private final LargeFileStatisticsPane statisticsPane;
    /** Set when the view closes: stops jobs. */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** The cancel flags of the running jobs. */
    private final Set<AtomicBoolean> running = ConcurrentHashMap.newKeySet();
    private volatile AtomicBoolean shownJob;
    private volatile LargeFileIndex index;
    private volatile LargeXmlStructure structure;
    private volatile boolean applying;
    private volatile DiskStamp stamp;
    private final Timeline changeCheck;
    private boolean changeCheckRunning;
    private ShownWindow shown;
    /** Set once a window was asked for (the Graphic tab then does not pick one itself). */
    private boolean windowRequested;
    /** The windows shown before the current one (Previous). */
    private final Deque<WindowSpec> history = new ArrayDeque<>();
    private Runnable onIndexed = () -> { };
    private java.util.function.BiConsumer<String, String> documentOpener = (name, text) -> { };
    private Runnable closer = () -> { };

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
        flow.setContextMenu(textMenu());
        rowsSlot.getChildren().add(flow);
        results.setCellFactory(list -> new HitCell());
        results.getSelectionModel().selectedItemProperty().addListener((obs, o, hit) -> {
            if (hit != null) {
                showRow(hit.row());
            }
        });
        results.setContextMenu(hitMenu());
        structurePane = new LargeFileStructurePane(new StructureHost());
        structureTab.setContent(structurePane);
        windowPane = new LargeFileWindowPane(new WindowHost());
        graphicTab.setContent(windowPane);
        statisticsPane = new LargeFileStatisticsPane(new StatisticsHost());
        statisticsTab.setContent(statisticsPane);
        structureTab.setOnSelectionChanged(e -> {
            if (structureTab.isSelected()) {
                structurePane.ensureRoot(() -> { });
            }
        });
        graphicTab.setOnSelectionChanged(e -> {
            if (graphicTab.isSelected() && shown == null && !windowRequested && structure != null) {
                WindowSpec spec = structurePane.selectionSpec();
                if (spec != null) {
                    loadWindow(spec, false);
                } else {
                    rootWindow();
                }
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        setUpProblems();
        setXmlTabsDisabled(true);
        setDisableControls(true);
        info.setText(I18n.t("LargeFileView.indexing-info", path.getFileName()));
        changeCheck = new Timeline(new KeyFrame(Duration.seconds(2), e -> checkForChanges()));
        changeCheck.setCycleCount(Timeline.INDEFINITE);
        index(null);
    }

    /** @return the file shown */
    public Path path() {
        return path;
    }

    /** Called on the FX thread once the file is indexed (tests). */
    void setOnIndexed(Runnable action) {
        onIndexed = action == null ? () -> { } : action;
    }

    /** Opens an extracted element as a new document (name, text). */
    public void setDocumentOpener(java.util.function.BiConsumer<String, String> opener) {
        documentOpener = opener == null ? (name, text) -> { } : opener;
    }

    /** Closes the viewer's tab (after "Apply" in the close prompt). */
    public void setCloser(Runnable closer) {
        this.closer = closer == null ? () -> { } : closer;
    }

    // ---- background jobs ----------------------------------------------------------------------

    /** Runs a job with the progress bar (relative to the file size); {@code done} and {@code failed} on the FX thread. */
    <T> void background(String label, Job<T> job, Consumer<T> done, Runnable failed) {
        LargeFileIndex current = index; // NOPMD - the view owns the index and closes it on dispose
        background(label, current == null ? path.toFile().length() : current.size(), job, done, failed);
    }

    private <T> void background(String label, long total, Job<T> job, Consumer<T> done, Runnable failed) {
        AtomicBoolean cancelled = new AtomicBoolean();
        running.add(cancelled);
        shownJob = cancelled;
        showProgress(label, total <= 0 ? -1 : 0);
        LongConsumer progress = read -> Platform.runLater(() -> {
            if (shownJob == cancelled) {
                progressBar.setProgress(total <= 0 ? -1 : Math.min(1, (double) read / total));
            }
        });
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                T result = job.run(() -> cancelled.get() || closed.get(), progress);
                Platform.runLater(() -> {
                    finished(cancelled);
                    if (!closed.get()) {
                        done.accept(result);
                    }
                });
            } catch (CancellationException e) {
                Platform.runLater(() -> {
                    finished(cancelled);
                    status.setText(I18n.t("LargeFileView.cancelled", label));
                    failed.run();
                });
            } catch (Exception | StackOverflowError e) {
                logger.debug("{} failed: {}", label, e.getMessage(), e);
                Platform.runLater(() -> {
                    finished(cancelled);
                    status.setText(I18n.t("LargeFileView.failed", label, e.getMessage()));
                    failed.run();
                });
            }
        });
    }

    private void finished(AtomicBoolean job) {
        running.remove(job);
        if (running.isEmpty()) {
            hideProgress();
        }
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
        running.forEach(job -> job.set(true));
    }

    // ---- index --------------------------------------------------------------------------------

    /** (Re)indexes the file; {@code after} runs on the FX thread once it is shown (may be {@code null}). */
    private void index(Runnable after) {
        long t0 = System.nanoTime();
        background(I18n.t("LargeFileView.indexing", path.getFileName()), path.toFile().length(),
                (cancelled, progress) -> {
                    DiskStamp before = DiskStamp.of(path);
                    return Map.entry(before, LargeFileIndex.build(path, progress, cancelled));
                }, built -> {
                    installIndex(built.getValue(), built.getKey());
                    logger.debug("Indexed {} in {} ms", path, (System.nanoTime() - t0) / 1_000_000);
                    if (after != null) {
                        after.run();
                    }
                    onIndexed.run();
                }, () -> info.setText(I18n.t("LargeFileView.cannot-be-shown", path.getFileName(), status.getText())));
    }

    private void installIndex(LargeFileIndex built, DiskStamp diskStamp) {
        index = built;
        structure = looksLikeXml(built) ? new LargeXmlStructure(built) : null;
        stamp = diskStamp;
        changedBar.setVisible(false);
        changedBar.setManaged(false);
        setDisableControls(false);
        setXmlTabsDisabled(structure == null);
        flow.getSelectionModel().clearSelection();
        rowNumbers.setSize((int) Math.min(Integer.MAX_VALUE - 8, built.rowCount()));
        info.setText(I18n.t("LargeFileView.info", path.getFileName(), LargeFileGuard.formatSize(built.size()),
                String.format(Locale.ROOT, "%,d", built.lineCount()), built.charset().name()));
        status.setText(I18n.t("LargeFileView.click-a-row-to-select"));
        if (!changeCheckRunning) {
            changeCheckRunning = true;
            changeCheck.play();
        }
        if (structureTab.isSelected()) {
            structurePane.ensureRoot(() -> { });
        }
    }

    /** Releases the index and forgets everything read from it (before the file is rewritten or reloaded). */
    private LargeFileIndex releaseIndex() {
        LargeFileIndex old = index; // NOPMD - the view owns the index and closes it on dispose
        index = null;
        structure = null;
        flow.getSelectionModel().clearSelection();
        rowNumbers.setSize(0);
        results.getItems().clear();
        structurePane.reset();
        statisticsPane.reset();
        setDisableControls(true);
        return old;
    }

    private static void closeQuietly(LargeFileIndex index) {
        if (index != null) {
            try {
                index.close();
            } catch (IOException e) {
                logger.debug("Closing {}: {}", index.path(), e.getMessage());
            }
        }
    }

    private void setDisableControls(boolean disabled) {
        validateButton.setDisable(disabled);
        lineField.setDisable(disabled);
        findField.setDisable(disabled);
        findButton.setDisable(disabled);
    }

    private void setXmlTabsDisabled(boolean disabled) {
        structureTab.setDisable(disabled);
        graphicTab.setDisable(disabled);
        statisticsTab.setDisable(disabled);
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

    // ---- changes on disk ----------------------------------------------------------------------

    /** Every 2 s while the window has the focus: compares the file's stamp off the FX thread. */
    private void checkForChanges() {
        DiskStamp known = stamp;
        if (known == null || applying || closed.get() || getScene() == null || getScene().getWindow() == null
                || !getScene().getWindow().isFocused() || changedBar.isVisible()) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            DiskStamp now = DiskStamp.of(path);
            if (!now.equals(known)) {
                Platform.runLater(() -> {
                    if (stamp == known && !applying) {
                        showChanged(now);
                    }
                });
            }
        });
    }

    private void showChanged(DiskStamp now) {
        changedLabel.setText(now.exists() ? I18n.t("LargeFileView.changed-on-disk", path.getFileName())
                : I18n.t("LargeFileView.deleted-on-disk", path.getFileName()));
        changedBar.setVisible(true);
        changedBar.setManaged(true);
    }

    @FXML
    public void onReload() {
        if (!settlePending(I18n.t("LargeFileView.reload-discards"), false, this::reload)) {
            return;
        }
        reload();
    }

    private void reload() {
        onCancel();
        changedBar.setVisible(false);
        changedBar.setManaged(false);
        shown = null;
        windowRequested = false;
        history.clear();
        windowPane.clear(I18n.t("LargeFileWindowPane.messageLabel.text"));
        closeQuietly(releaseIndex());
        index(null);
    }

    // ---- keys and text ------------------------------------------------------------------------

    private void onKey(KeyEvent e) {
        if (e.isShortcutDown() && e.getCode() == KeyCode.G) {
            views.getSelectionModel().select(textTab);
            lineField.requestFocus();
            lineField.selectAll();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
            views.getSelectionModel().select(textTab);
            findField.requestFocus();
            findField.selectAll();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.L && !structureTab.isDisabled()) {
            views.getSelectionModel().select(structureTab);
            structurePane.focusPath();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.C && !(e.getTarget() instanceof TextField)
                && textTab.isSelected()) {
            copySelection();
            e.consume();
        } else if (e.getCode() == KeyCode.F3) {
            nextHit(e.isShiftDown() ? -1 : 1);
            e.consume();
        }
    }

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
            status.setText(I18n.t("LargeFileView.line-byte-offset", String.format(Locale.ROOT, "%,d", first.line()),
                    rows > 1 ? " · " + I18n.t("LargeFileView.rows-selected", String.format(Locale.ROOT, "%,d", rows)) : "",
                    String.format(Locale.ROOT, "%,d", first.offset())));
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
            status.setText(I18n.t("LargeFileView.copied-rows", String.format(Locale.ROOT, "%,d", rows.size()),
                    count < selectionEnd - selectionStart + 1 ? I18n.t("LargeFileView.atMost", MAX_COPY_ROWS) : ""));
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    private ContextMenu textMenu() {
        MenuItem copy = new MenuItem(I18n.t("LargeFileView.copy"), new IconifyIcon("bi-clipboard"));
        copy.setOnAction(e -> copySelection());
        MenuItem structureItem = new MenuItem(I18n.t("LargeFileView.show-in-structure"), new IconifyIcon("bi-diagram-3"));
        structureItem.setOnAction(e -> showSelectionInStructure());
        ContextMenu menu = new ContextMenu(copy, structureItem);
        menu.setOnShowing(e -> {
            boolean none = flow.getSelectionModel().getSelectedIndices().isEmpty();
            copy.setDisable(none);
            structureItem.setDisable(none || structure == null);
        });
        return menu;
    }

    private ContextMenu hitMenu() {
        MenuItem structureItem = new MenuItem(I18n.t("LargeFileView.show-in-structure"), new IconifyIcon("bi-diagram-3"));
        structureItem.setOnAction(e -> showHitInStructure(results.getSelectionModel().getSelectedItem()));
        ContextMenu menu = new ContextMenu(structureItem);
        menu.setOnShowing(e -> structureItem.setDisable(results.getSelectionModel().getSelectedItem() == null
                || structure == null));
        return menu;
    }

    /** Shows the element at the first selected row's first tag in the Structure tab. */
    void showSelectionInStructure() {
        long[] range = selectedRange();
        LargeFileIndex current = index; // NOPMD - the view owns the index and closes it on dispose
        if (current == null || range[0] < 0) {
            return;
        }
        try {
            LargeFileIndex.Row row = current.row(range[0]);
            String text = row.text();
            int at = 0;
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
            revealOffset(row.offset() + text.substring(0, at).getBytes(current.charset()).length);
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    /** Shows the element that contains a search hit in the Structure tab. */
    void showHitInStructure(LargeFileIndex.Hit hit) {
        LargeFileIndex current = index; // NOPMD - the view owns the index and closes it on dispose
        if (hit == null || current == null) {
            return;
        }
        try {
            LargeFileIndex.Row row = current.row(hit.row());
            revealOffset(row.offset() + row.text().substring(0, Math.min(hit.start(), row.text().length()))
                    .getBytes(current.charset()).length);
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    private void revealOffset(long offset) {
        if (structure == null) {
            return;
        }
        views.getSelectionModel().select(structureTab);
        structurePane.revealOffset(offset);
    }

    // ---- find ---------------------------------------------------------------------------------

    @FXML
    public void onFind() {
        LargeFileIndex searched = index; // NOPMD - the view owns the index and closes it on dispose
        if (searched == null || findField.getText().isEmpty()) {
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
        findButton.setDisable(true);
        background(I18n.t("LargeFileView.searching"),
                (cancelled, progress) -> searched.search(pattern, MAX_HITS, progress, cancelled),
                this::showHits, () -> findButton.setDisable(false));
    }

    private void showHits(List<LargeFileIndex.Hit> hits) {
        findButton.setDisable(false);
        views.getSelectionModel().select(textTab);
        results.getItems().setAll(hits);
        if (!split.getItems().contains(results)) {
            results.setVisible(true);
            results.setManaged(true);
            split.getItems().add(results);
            split.setDividerPositions(0.72);
        }
        status.setText(hits.isEmpty() ? I18n.t("LargeFileView.no-matches")
                : hits.size() >= MAX_HITS ? I18n.t("LargeFileView.matches-capped", String.format(Locale.ROOT, "%,d", hits.size()), MAX_HITS)
                : I18n.t("LargeFileView.matches", String.format(Locale.ROOT, "%,d", hits.size())));
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

    // ---- structure ----------------------------------------------------------------------------

    /** Switches to the Text tab at the element's first line. */
    void showInText(Node node) {
        LargeFileIndex current = index; // NOPMD - the view owns the index and closes it on dispose
        if (node == null || current == null) {
            return;
        }
        try {
            long row = current.rowOfOffset(node.start());
            views.getSelectionModel().select(textTab);
            showRow(row);
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-read-the-file", e.getMessage()));
        }
    }

    /** Shows the first element with a path (names from the root, no positions) in the Structure tab. */
    void showPath(String namePath) {
        LargeXmlStructure current = structure;
        if (current == null) {
            return;
        }
        background(I18n.t("LargeFileView.finding", namePath),
                (cancelled, progress) -> current.firstOffsetOf(namePath, cancelled, progress), offset -> {
                    if (offset >= 0) {
                        revealOffset(offset + 1);
                    }
                }, () -> { });
    }

    private final class StructureHost implements LargeFileStructurePane.Host {
        @Override
        public LargeXmlStructure structure() {
            return structure;
        }

        @Override
        public <T> void background(String label, Job<T> job, Consumer<T> done, Runnable failed) {
            LargeFileView.this.background(label, job, done, failed);
        }

        @Override
        public void status(String text) {
            status.setText(text);
        }

        @Override
        public void showInText(Node node) {
            LargeFileView.this.showInText(node);
        }

        @Override
        public void showInGraphic(WindowSpec spec) {
            LargeFileView.this.showInGraphic(spec);
        }

        @Override
        public void openDocument(String name, String text) {
            documentOpener.accept(name, text);
        }

        @Override
        public javafx.stage.Window window() {
            return getScene() != null ? getScene().getWindow() : null;
        }
    }

    private final class StatisticsHost implements LargeFileStatisticsPane.Host {
        @Override
        public LargeXmlStructure structure() {
            return structure;
        }

        @Override
        public <T> void background(String label, Job<T> job, Consumer<T> done, Runnable failed) {
            LargeFileView.this.background(label, job, done, failed);
        }

        @Override
        public void showPath(String namePath) {
            LargeFileView.this.showPath(namePath);
        }
    }

    // ---- graphic: a window of the file in the grid ---------------------------------------------

    private final class WindowHost implements LargeFileWindowPane.Host {
        @Override
        public void applyWindow() {
            LargeFileView.this.applyWindow(null);
        }

        @Override
        public void discardWindow() {
            if (shown != null) {
                loadWindow(shown.spec(), false);
            }
        }

        @Override
        public void previousWindow() {
            LargeFileView.this.previousWindow();
        }

        @Override
        public void nextWindow() {
            LargeFileView.this.nextWindow();
        }
    }

    /** Shows a window in the Graphic tab (asks first when the current one has edits that are not written). */
    void showInGraphic(WindowSpec spec) {
        if (!settlePending(I18n.t("LargeFileView.window-has-changes"), true, () -> showInGraphic(spec))) {
            return;
        }
        history.clear();
        loadWindow(spec, false);
        views.getSelectionModel().select(graphicTab);
    }

    /** The root as a window when it fits, else the first page of its children. */
    private void rootWindow() {
        LargeXmlStructure current = structure;
        if (current == null) {
            return;
        }
        background(I18n.t("LargeFileView.reading-the-structure"), (cancelled, progress) -> current.root(), root -> {
            boolean fits = root.end() - root.start() <= LargeXmlWindow.MAX_WINDOW_BYTES;
            loadWindow(fits ? new WindowSpec(null, List.of(), root, null, 1)
                    : new WindowSpec(root, List.of(), root, null, PAGE), false);
        }, () -> { });
    }

    /**
     * Reads a window and shows it.
     *
     * @param remember whether the current window goes onto the history (Next)
     */
    private void loadWindow(WindowSpec spec, boolean remember) {
        LargeXmlStructure current = structure;
        if (current == null) {
            return;
        }
        windowRequested = true;
        windowPane.showMessage(I18n.t("LargeFileView.loading-window"));
        WindowSpec previous = shown == null ? null : shown.spec();
        background(I18n.t("LargeFileView.loading-window"), (cancelled, progress) -> {
            ShownWindow window = readWindow(current, spec, cancelled, progress);
            XmlDocument document = new XmlParser().parse(window.window().text());
            return Map.entry(window, document);
        }, loaded -> {
            if (remember && previous != null) {
                history.push(previous);
            }
            ShownWindow window = loaded.getKey();
            shown = window;
            XmlEditorContext context = new XmlEditorContext(loaded.getValue());
            windowPane.show(window.window(), context, label(window),
                    !history.isEmpty() || window.window().firstIndex() > 0, window.next() != null);
        }, () -> {
            windowRequested = shown != null;
            windowPane.showMessage(status.getText());
        });
    }

    /** Reads the children of a spec that fit into a window. */
    private static ShownWindow readWindow(LargeXmlStructure structure, WindowSpec spec, BooleanSupplier cancelled,
                                          LongConsumer progress) throws IOException {
        if (spec.parent() == null) {
            return new ShownWindow(spec, LargeXmlWindow.ofRoot(structure, spec.subject()), List.of(), null);
        }
        LargeXmlStructure.Page page = structure.children(spec.parent(), spec.start(), spec.maxCount(), cancelled, progress);
        List<Node> fit = LargeXmlWindow.fitting(page.children(), LargeXmlWindow.MAX_WINDOW_BYTES);
        if (fit.isEmpty()) {
            throw new IOException(I18n.t("LargeFileView.has-no-child-elements", spec.parent().xpath()));
        }
        if (fit.getLast().end() - fit.getFirst().start() > LargeXmlWindow.MAX_WINDOW_BYTES) {
            throw new IOException(I18n.t("LargeFileView.child-too-large", fit.getFirst().xpath(),
                    LargeFileGuard.formatSize(LargeXmlWindow.MAX_WINDOW_BYTES)));
        }
        LargeXmlWindow window = LargeXmlWindow.ofChildren(structure, spec.parent(), spec.ancestors(), fit);
        Cursor next;
        if (fit.size() < page.children().size()) {
            Map<String, Integer> counters = new HashMap<>(spec.start() == null ? Map.of() : spec.start().positions());
            fit.forEach(child -> counters.merge(child.name(), 1, Integer::sum));
            next = new Cursor(page.children().get(fit.size()).start(), counters);
        } else {
            next = spec.maxCount() == 1 ? null : page.nextCursor();
        }
        return new ShownWindow(spec, window, List.copyOf(fit), next);
    }

    private static String label(ShownWindow window) {
        if (window.spec().parent() == null) {
            return I18n.t("LargeFileView.window-root", window.spec().subject().xpath());
        }
        if (window.children().size() == 1 && window.spec().maxCount() == 1) {
            return window.children().getFirst().xpath();
        }
        return I18n.t("LargeFileView.window-children", window.spec().parent().xpath(),
                String.format(Locale.ROOT, "%,d", window.window().firstIndex() + 1),
                String.format(Locale.ROOT, "%,d", window.window().firstIndex() + window.window().count()));
    }

    private void nextWindow() {
        ShownWindow current = shown;
        if (current == null || current.next() == null) {
            return;
        }
        if (!settlePending(I18n.t("LargeFileView.window-has-changes"), true, this::nextWindow)) {
            return;
        }
        WindowSpec spec = current.spec();
        loadWindow(new WindowSpec(spec.parent(), spec.ancestors(), spec.subject(), current.next(), spec.maxCount()), true);
    }

    private void previousWindow() {
        ShownWindow current = shown;
        if (current == null) {
            return;
        }
        if (!settlePending(I18n.t("LargeFileView.window-has-changes"), true, this::previousWindow)) {
            return;
        }
        if (!history.isEmpty()) {
            loadWindow(history.pop(), false);
            return;
        }
        WindowSpec spec = current.spec();
        long first = current.window().firstIndex();
        LargeXmlStructure currentStructure = structure;
        if (first <= 0 || spec.parent() == null || currentStructure == null) {
            return;
        }
        background(I18n.t("LargeFileView.counting-children", spec.parent().name()),
                (cancelled, progress) -> currentStructure.pageIndex(spec.parent(), PAGE, cancelled, progress), pages -> {
                    Cursor cursor = pages.pageOf(Math.max(0, first - Math.max(1, Math.min(PAGE, spec.maxCount()))));
                    loadWindow(new WindowSpec(spec.parent(), spec.ancestors(), spec.subject(),
                            cursor == null || cursor.index() == 0 ? null : cursor, spec.maxCount()), false);
                }, () -> { });
    }

    /**
     * Settles edits that are not written yet before something replaces the window.
     *
     * @param afterApply what to do once they are written (APPLY); the caller does not go on itself then
     * @return whether the caller may go on now (no edits, or discarded)
     */
    private boolean settlePending(String question, boolean offerApply, Runnable afterApply) {
        if (!windowPane.isDirty()) {
            return true;
        }
        PendingChoice choice = askPending(question, offerApply);
        if (choice == PendingChoice.APPLY) {
            applyWindow(afterApply);
            return false;
        }
        if (choice == PendingChoice.DISCARD) {
            windowPane.clear(I18n.t("LargeFileView.loading-window"));
            shown = null;
            return true;
        }
        return false;
    }

    private PendingChoice askPending(String question, boolean offerApply) {
        if (pendingAnswer != null) {
            return pendingAnswer.apply(question);
        }
        ButtonType apply = new ButtonType(I18n.t("LargeFileWindowPane.applyButton.text"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(I18n.t("LargeFileView.discard-changes"), ButtonBar.ButtonData.NO);
        Alert alert = DialogHelper.createStyledAlert(Alert.AlertType.CONFIRMATION, I18n.t("LargeFileView.unapplied-title"),
                question, I18n.t("LargeFileView.unapplied-content"));
        alert.getButtonTypes().setAll(offerApply ? List.of(apply, discard, ButtonType.CANCEL) : List.of(discard, ButtonType.CANCEL));
        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
        return answer == apply ? PendingChoice.APPLY : answer == discard ? PendingChoice.DISCARD : PendingChoice.CANCEL;
    }

    /**
     * Writes the edited window into the file (one streaming pass, see {@link LargeFilePatcher}), indexes
     * the file again and shows the same window.
     *
     * @param after runs once the file is written and the window shown again (may be {@code null})
     */
    void applyWindow(Runnable after) {
        ShownWindow current = shown;
        LargeXmlWindow window = windowPane.window();
        String edited = windowPane.editedText();
        if (current == null || window == null || edited == null || applying) {
            return;
        }
        LargeFilePatcher.Patch patch;
        try {
            patch = window.patchFor(edited);
        } catch (IOException e) {
            status.setText(I18n.t("LargeFileView.cannot-apply", e.getMessage()));
            DialogHelper.showWarning(I18n.t("LargeFileView.apply-title"), I18n.t("LargeFileView.cannot-apply-header"),
                    e.getMessage());
            return;
        }
        applying = true;
        windowPane.setBusy(true);
        changedBar.setVisible(false);
        changedBar.setManaged(false);
        onCancel();
        LargeFileIndex old = releaseIndex(); // NOPMD - closed by the write job below
        long t0 = System.nanoTime();
        background(I18n.t("LargeFileView.writing", path.getFileName()), window.fileSize(), (cancelled, progress) -> {
            closeQuietly(old);
            LargeFilePatcher.Result result = LargeFilePatcher.apply(path, window.fileSize(), window.fileModified(),
                    List.of(patch), false, progress, cancelled);
            DiskStamp written = DiskStamp.of(path);
            return Map.entry(result, Map.entry(written, LargeFileIndex.build(path, progress, () -> closed.get())));
        }, done -> {
            applying = false;
            windowPane.setBusy(false);
            installIndex(done.getValue().getValue(), done.getValue().getKey());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            status.setText(I18n.t("LargeFileView.written", path.getFileName(),
                    LargeFileGuard.formatSize(done.getKey().size()), String.format(Locale.ROOT, "%,d", ms)));
            WindowSpec spec = current.spec();
            loadWindow(spec, false);
            if (after != null) {
                after.run();
            }
        }, () -> {
            applying = false;
            windowPane.setBusy(false);
            String reason = status.getText();
            index(() -> status.setText(reason));
        });
    }

    /**
     * Asks before the tab closes when the window has edits that are not written yet.
     *
     * @return whether the tab may close now
     */
    public boolean confirmClose() {
        if (!windowPane.isDirty()) {
            return true;
        }
        PendingChoice choice = askPending(I18n.t("LargeFileView.close-has-changes"), true);
        if (choice == PendingChoice.APPLY) {
            applyWindow(closer);
            return false;
        }
        return choice == PendingChoice.DISCARD;
    }

    /** @return whether the Graphic tab holds edits that are not written into the file yet */
    public boolean hasPendingChanges() {
        return windowPane.isDirty();
    }

    /**
     * Asks whether edits that are not written yet may be dropped (application exit, where writing
     * cannot wait).
     *
     * @return whether they may be dropped
     */
    public boolean confirmDiscard() {
        return !windowPane.isDirty()
                || askPending(I18n.t("LargeFileView.exit-has-changes"), false) == PendingChoice.DISCARD;
    }

    // ---- validation ---------------------------------------------------------------------------

    private void setUpProblems() {
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(severityColumn, LargeFileValidation.Problem::severity);
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(problemLineColumn,
                p -> p.line() > 0 ? String.format(Locale.ROOT, "%,d", p.line()) : "");
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(problemColumnColumn,
                p -> p.column() > 0 ? Integer.toString(p.column()) : "");
        org.fxt.freexmltoolkit.controls.shell.FxmlColumns.text(messageColumn, LargeFileValidation.Problem::message);
        problems.setRowFactory(t -> {
            javafx.scene.control.TableRow<LargeFileValidation.Problem> row = new javafx.scene.control.TableRow<>();
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
                java.io.File declared = LargeFileValidation.declaredSchema(path).orElse(null);
                validateDeclared.setDisable(declared == null);
                validateDeclared.setText(declared == null ? I18n.t("LargeFileView.validate-declared-none")
                        : I18n.t("LargeFileView.validate-against", declared.getName()));
            }
        });
    }

    @FXML
    public void onValidateDeclared() {
        validate(LargeFileValidation.declaredSchema(path).orElse(null), true);
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
        validateButton.setDisable(true);
        long t0 = System.nanoTime();
        background(schema == null ? I18n.t("LargeFileView.checking-well-formedness")
                        : I18n.t("LargeFileView.validating-against", schema.getName()),
                (cancelled, progress) -> LargeFileValidation.validate(path, schema, MAX_PROBLEMS, progress, cancelled),
                result -> showProblems(result, schema, (System.nanoTime() - t0) / 1_000_000),
                () -> validateButton.setDisable(index == null));
    }

    private void showProblems(LargeFileValidation.Result result, java.io.File schema, long ms) {
        validateButton.setDisable(false);
        problemsTab.setDisable(false);
        problems.getItems().setAll(result.problems());
        String against = schema == null ? I18n.t("LargeFileView.well-formedness") : schema.getName();
        String text;
        if (result.schemaError() != null) {
            text = I18n.t("LargeFileView.schema-unusable", against, result.schemaError());
        } else if (result.valid()) {
            text = schema == null ? I18n.t("LargeFileView.well-formed") : I18n.t("LargeFileView.valid-against", schema.getName());
        } else {
            text = against + ": " + (result.truncated()
                    ? I18n.t("LargeFileView.problems-capped", String.format(Locale.ROOT, "%,d", result.problems().size()), MAX_PROBLEMS)
                    : I18n.t("LargeFileView.problem-count", String.format(Locale.ROOT, "%,d", result.problems().size())))
                    + (result.fatal() ? I18n.t("LargeFileView.not-well-formed-suffix") : "") + ".";
        }
        problemsLabel.setText(text + " (" + String.format(Locale.ROOT, "%,d", ms) + " ms)");
        status.setText(text);
        views.getSelectionModel().select(problemsTab);
    }

    /** Releases the file and stops running jobs. */
    public void close() {
        closed.set(true);
        onCancel();
        changeCheck.stop();
        closeQuietly(index);
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
            LargeFileIndex current = index; // NOPMD - the view owns the index and closes it on dispose
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
                text.getChildren().setAll(new Text(I18n.t("LargeFileView.cannot-read-row", e.getMessage())));
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

    // ---- test access --------------------------------------------------------------------------

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

    List<LargeFileValidation.Problem> problemsForTesting() {
        return List.copyOf(problems.getItems());
    }

    String problemsLabelForTesting() {
        return problemsLabel.getText();
    }

    void selectStructureForTesting() {
        views.getSelectionModel().select(structureTab);
    }

    void selectGraphicForTesting() {
        views.getSelectionModel().select(graphicTab);
    }

    void selectStatisticsForTesting() {
        views.getSelectionModel().select(statisticsTab);
    }

    javafx.scene.control.TreeView<Object> treeForTesting() {
        return structurePane.treeForTesting();
    }

    List<Node> recordsForTesting() {
        return List.copyOf(structurePane.recordsTable().getItems());
    }

    List<String> recordColumnsForTesting() {
        return structurePane.recordsTable().getColumns().stream().map(javafx.scene.control.TableColumn::getText).toList();
    }

    String recordsLabelForTesting() {
        return structurePane.recordsLabelText();
    }

    LargeFileStructurePane structurePaneForTesting() {
        return structurePane;
    }

    LargeFileWindowPane windowPaneForTesting() {
        return windowPane;
    }

    LargeFileStatisticsPane statisticsPaneForTesting() {
        return statisticsPane;
    }

    boolean changedBarShownForTesting() {
        return changedBar.isVisible();
    }

    boolean isBusyForTesting() {
        return !running.isEmpty() || applying;
    }

    void checkForChangesForTesting() {
        DiskStamp known = stamp;
        if (known != null && !DiskStamp.of(path).equals(known)) {
            showChanged(DiskStamp.of(path));
        }
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
