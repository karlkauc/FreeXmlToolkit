package org.fxt.freexmltoolkit.controls.shell.editor;

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
                    status.setText("Click a row to select it, Shift+click to extend, Ctrl+C to copy.");
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
            status.setText("Enter a line number between 1 and " + index.lineCount() + ".");
        } catch (IOException e) {
            status.setText("Cannot read the file: " + e.getMessage());
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
            status.setText("Line " + String.format(Locale.ROOT, "%,d", first.line())
                    + (rows > 1 ? " · " + String.format(Locale.ROOT, "%,d", rows) + " rows selected" : "")
                    + " · byte offset " + String.format(Locale.ROOT, "%,d", first.offset()));
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
            status.setText("Copied " + String.format(Locale.ROOT, "%,d", rows.size()) + " rows"
                    + (count < selectionEnd - selectionStart + 1 ? " (at most " + MAX_COPY_ROWS + ")" : "") + ".");
        } catch (IOException e) {
            status.setText("Cannot read the file: " + e.getMessage());
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
            status.setText("Invalid regular expression: " + e.getDescription());
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
                    status.setText("Search cancelled.");
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    hideProgress();
                    findButton.setDisable(false);
                    status.setText("Search failed: " + e.getMessage());
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

    /** Releases the file and stops a running index or search. */
    public void close() {
        cancel.set(true);
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
