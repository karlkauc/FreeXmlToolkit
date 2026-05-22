package org.fxt.freexmltoolkit.controls.diff;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A {@link Tab} that hosts the side-by-side compare & merge view.
 *
 * <p>Both sides are fully editable {@link CodeArea}s. The diff is recomputed
 * 300 ms after the last keystroke. Per-chunk arrows live in the gutter
 * between the panes, and the toolbar exposes navigation and bulk-apply
 * actions plus per-side save buttons.
 */
public final class DiffView extends Tab {

    private static final Duration DEBOUNCE = Duration.millis(300);

    private final CodeArea leftArea = new CodeArea();
    private final CodeArea rightArea = new CodeArea();
    private final DiffGutter gutter;
    private final Label leftHeaderLabel = new Label();
    private final Label rightHeaderLabel = new Label();

    private final String leftDisplayName;
    private final String rightDisplayName;
    private final Consumer<String> leftSaveHandler;
    private final File rightFile;

    private final boolean[] dirty = {false, false};
    private List<DiffChunk> chunks = List.of();
    private int currentChunkIndex = -1;

    private final AtomicLong recomputeGeneration = new AtomicLong(0);
    private final PauseTransition leftDebounce = new PauseTransition(DEBOUNCE);
    private final PauseTransition rightDebounce = new PauseTransition(DEBOUNCE);
    private boolean suppressDebounce = false;

    /**
     * @param leftDisplayName  name shown above the left pane (typically the editor's file name).
     * @param leftInitialText  initial content of the left pane (current editor text).
     * @param leftSaveHandler  invoked with the left pane's text when the user saves the left side
     *                         (delegates write + notifies the underlying editor).
     * @param rightFile        file picked by the user for the right side (read-write).
     */
    public DiffView(String leftDisplayName, String leftInitialText, Consumer<String> leftSaveHandler,
                    File rightFile) {
        this.leftDisplayName = leftDisplayName;
        this.rightDisplayName = rightFile.getName();
        this.leftSaveHandler = leftSaveHandler;
        this.rightFile = rightFile;

        String rightInitialText = readFileSafely(rightFile);

        suppressDebounce = true;
        leftArea.replaceText(leftInitialText == null ? "" : leftInitialText);
        rightArea.replaceText(rightInitialText);
        suppressDebounce = false;

        configureCodeArea(leftArea);
        configureCodeArea(rightArea);

        gutter = new DiffGutter(leftArea, rightArea, this::applyChunk);

        setText("Compare: " + leftDisplayName + " ↔ " + rightDisplayName);
        setGraphic(new IconifyIcon("bi-files"));
        setContent(buildLayout());

        installSyncScroll();
        installDebouncedRecompute();
        addEventHandler(Tab.CLOSED_EVENT, this::handleClosed);
        setOnCloseRequest(this::handleCloseRequest);

        updateHeaders();
        recomputeNow();
    }

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildCenter());
        root.getStyleClass().add("diff-view");
        return root;
    }

    private ToolBar buildToolbar() {
        Button saveLeft = makeButton("Save Left", "bi-save", "#17a2b8", "Save left side to original file");
        saveLeft.setOnAction(e -> doSaveLeft());

        Button saveRight = makeButton("Save Right", "bi-save", "#17a2b8", "Save right side to picked file");
        saveRight.setOnAction(e -> doSaveRight());

        Button prev = makeButton("Prev", "bi-arrow-up", "#6c757d", "Jump to previous change (Alt+Up)");
        prev.setOnAction(e -> navigate(-1));

        Button next = makeButton("Next", "bi-arrow-down", "#6c757d", "Jump to next change (Alt+Down)");
        next.setOnAction(e -> navigate(1));

        Button allRight = makeButton("All →", "bi-arrow-right", "#28a745", "Apply ALL changes left → right");
        allRight.setOnAction(e -> applyAll(DiffGutter.Direction.LEFT_TO_RIGHT));

        Button allLeft = makeButton("All ←", "bi-arrow-left", "#28a745", "Apply ALL changes right → left");
        allLeft.setOnAction(e -> applyAll(DiffGutter.Direction.RIGHT_TO_LEFT));

        Button recompute = makeButton("Re-compute", "bi-arrow-clockwise", "#6c757d", "Recompute diff now");
        recompute.setOnAction(e -> recomputeNow());

        Button close = makeButton("Close", "bi-x-circle", "#dc3545", "Close diff tab");
        close.setOnAction(e -> requestClose());

        ToolBar tb = new ToolBar(
                saveLeft, saveRight,
                new Separator(),
                prev, next,
                new Separator(),
                allLeft, allRight,
                new Separator(),
                recompute,
                new Separator(),
                close
        );
        tb.getStyleClass().add("xsd-toolbar");
        return tb;
    }

    private static Button makeButton(String text, String iconLiteral, String iconColor, String tip) {
        Button b = new Button(text);
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(20);
        icon.setIconColor(javafx.scene.paint.Color.web(iconColor));
        b.setGraphic(icon);
        b.getStyleClass().add("toolbar-button");
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    private SplitPane buildCenter() {
        VBox leftBox = wrapWithHeader(leftHeaderLabel, leftArea);
        VBox rightBox = wrapWithHeader(rightHeaderLabel, rightArea);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.5, 0.5);
        split.getItems().addAll(leftBox, gutter, rightBox);
        SplitPane.setResizableWithParent(gutter, false);

        return split;
    }

    private VBox wrapWithHeader(Label headerLabel, CodeArea area) {
        headerLabel.getStyleClass().add("diff-pane-header");
        headerLabel.setMaxWidth(Double.MAX_VALUE);
        headerLabel.setPadding(new Insets(4, 8, 4, 8));

        HBox header = new HBox(headerLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        header.getStyleClass().add("diff-pane-header-bar");

        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(area);
        VBox box = new VBox(header, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return box;
    }

    private void configureCodeArea(CodeArea area) {
        area.setEditable(true);
        area.getStyleClass().add("xml-code-area");
    }

    private void installSyncScroll() {
        var leftScroll = leftArea.estimatedScrollYProperty();
        var rightScroll = rightArea.estimatedScrollYProperty();
        boolean[] updating = {false};

        leftScroll.addListener((obs, o, n) -> {
            if (updating[0]) return;
            updating[0] = true;
            try { rightScroll.setValue(n.doubleValue()); }
            finally { updating[0] = false; }
        });
        rightScroll.addListener((obs, o, n) -> {
            if (updating[0]) return;
            updating[0] = true;
            try { leftScroll.setValue(n.doubleValue()); }
            finally { updating[0] = false; }
        });
    }

    private void installDebouncedRecompute() {
        leftDebounce.setOnFinished(e -> recomputeNow());
        rightDebounce.setOnFinished(e -> recomputeNow());

        leftArea.textProperty().addListener((obs, o, n) -> {
            if (suppressDebounce) return;
            setDirty(0, true);
            leftDebounce.playFromStart();
        });
        rightArea.textProperty().addListener((obs, o, n) -> {
            if (suppressDebounce) return;
            setDirty(1, true);
            rightDebounce.playFromStart();
        });
    }

    /**
     * Recompute the diff synchronously and apply highlights & gutter arrows.
     * For 5k+ line files this still completes in well under a second; we keep
     * a generation counter so a possibly-stale callback would no-op.
     */
    public void recomputeNow() {
        long gen = recomputeGeneration.incrementAndGet();
        String left = leftArea.getText();
        String right = rightArea.getText();
        List<DiffChunk> result = DiffEngine.compute(left, right);
        if (gen != recomputeGeneration.get()) return;
        this.chunks = result;
        leftArea.setStyleSpans(0, DiffHighlighter.computeHighlighting(left, chunks, DiffHighlighter.Side.LEFT));
        rightArea.setStyleSpans(0, DiffHighlighter.computeHighlighting(right, chunks, DiffHighlighter.Side.RIGHT));
        gutter.setChunks(chunks);
        if (currentChunkIndex >= chunks.size()) currentChunkIndex = -1;
    }

    private void applyChunk(DiffChunk chunk, DiffGutter.Direction direction) {
        applyChunkAt(chunks.indexOf(chunk), direction);
    }

    private void applyChunkAt(int index, DiffGutter.Direction direction) {
        if (index < 0 || index >= chunks.size()) return;
        DiffChunk c = chunks.get(index);
        if (c.isEqual()) return;

        String left = leftArea.getText();
        String right = rightArea.getText();
        int[] leftOffsets = DiffHighlighter.computeLineOffsets(left);
        int[] rightOffsets = DiffHighlighter.computeLineOffsets(right);

        suppressDebounce = true;
        try {
            if (direction == DiffGutter.Direction.LEFT_TO_RIGHT) {
                String replacement = sliceLines(left, leftOffsets, c.getLeftStart(), c.getLeftEnd());
                int from = rightOffsets[Math.min(c.getRightStart(), rightOffsets.length - 1)];
                int to   = rightOffsets[Math.min(c.getRightEnd(),   rightOffsets.length - 1)];
                rightArea.replaceText(from, to, replacement);
                setDirty(1, true);
            } else {
                String replacement = sliceLines(right, rightOffsets, c.getRightStart(), c.getRightEnd());
                int from = leftOffsets[Math.min(c.getLeftStart(), leftOffsets.length - 1)];
                int to   = leftOffsets[Math.min(c.getLeftEnd(),   leftOffsets.length - 1)];
                leftArea.replaceText(from, to, replacement);
                setDirty(0, true);
            }
        } finally {
            suppressDebounce = false;
        }
        recomputeNow();
    }

    private void applyAll(DiffGutter.Direction direction) {
        List<Integer> nonEqualIndices = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (!chunks.get(i).isEqual()) nonEqualIndices.add(i);
        }
        for (int i = nonEqualIndices.size() - 1; i >= 0; i--) {
            applyChunkAt(nonEqualIndices.get(i), direction);
        }
    }

    private void navigate(int delta) {
        if (chunks.isEmpty()) return;
        List<Integer> nonEqual = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) if (!chunks.get(i).isEqual()) nonEqual.add(i);
        if (nonEqual.isEmpty()) return;

        int pos = nonEqual.indexOf(currentChunkIndex);
        if (pos < 0) {
            pos = (delta > 0) ? -1 : nonEqual.size();
        }
        int nextPos = pos + delta;
        if (nextPos < 0) nextPos = nonEqual.size() - 1;
        if (nextPos >= nonEqual.size()) nextPos = 0;
        currentChunkIndex = nonEqual.get(nextPos);

        DiffChunk c = chunks.get(currentChunkIndex);
        moveCaretToLine(leftArea, c.getLeftStart());
        moveCaretToLine(rightArea, c.getRightStart());
    }

    private static void moveCaretToLine(CodeArea area, int paragraph) {
        if (paragraph < 0 || paragraph >= area.getParagraphs().size()) return;
        int pos = area.position(paragraph, 0).toOffset();
        area.moveTo(pos);
        area.requestFollowCaret();
    }

    private static String sliceLines(String text, int[] offsets, int startLine, int endLine) {
        if (startLine >= endLine) return "";
        int end = Math.min(text.length(), offsets[Math.min(endLine, offsets.length - 1)]);
        int start = offsets[Math.min(startLine, offsets.length - 1)];
        return text.substring(start, end);
    }

    private void doSaveLeft() {
        try {
            if (leftSaveHandler != null) leftSaveHandler.accept(leftArea.getText());
            setDirty(0, false);
        } catch (Exception ex) {
            showError("Could not save left side", ex.getMessage());
        }
    }

    private void doSaveRight() {
        try {
            Files.writeString(rightFile.toPath(), rightArea.getText(), StandardCharsets.UTF_8);
            setDirty(1, false);
        } catch (IOException ex) {
            showError("Could not save right side", ex.getMessage());
        }
    }

    private void setDirty(int side, boolean d) {
        dirty[side] = d;
        Platform.runLater(this::updateHeaders);
    }

    private void updateHeaders() {
        leftHeaderLabel.setText(leftDisplayName + (dirty[0] ? " *" : ""));
        rightHeaderLabel.setText(rightDisplayName + (dirty[1] ? " *" : ""));
    }

    private void requestClose() {
        Event.fireEvent(this, new Event(Tab.TAB_CLOSE_REQUEST_EVENT));
        if (getTabPane() != null) {
            getTabPane().getTabs().remove(this);
        }
    }

    private void handleCloseRequest(Event ev) {
        if (!dirty[0] && !dirty[1]) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "There are unsaved changes in the diff view.\nClose anyway?",
                ButtonType.YES, ButtonType.CANCEL);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("Discard merged changes?");
        Optional<ButtonType> r = alert.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.YES) {
            ev.consume();
        }
    }

    private void handleClosed(Event ev) {
        recomputeGeneration.incrementAndGet();
    }

    private static String readFileSafely(File f) {
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "(could not read " + f.getAbsolutePath() + ": " + ex.getMessage() + ")";
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "" : message);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    /** For tests: returns the chunks computed for the current text pair. */
    public List<DiffChunk> getChunksForTesting() {
        return chunks;
    }

    /** For tests: replace text and recompute synchronously. */
    public void setTextsForTesting(String left, String right) {
        suppressDebounce = true;
        try {
            leftArea.replaceText(left);
            rightArea.replaceText(right);
        } finally {
            suppressDebounce = false;
        }
        recomputeNow();
    }

}
