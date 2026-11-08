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
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;

/**
 * A {@link Tab} that hosts the side-by-side compare & merge view.
 *
 * <p>Both sides are fully editable {@link CodeArea}s. The diff is recomputed
 * 300 ms after the last keystroke. Per-chunk arrows live in the gutter
 * between the panes, and the toolbar exposes navigation and bulk-apply
 * actions plus per-side save buttons.
 *
 * <p>In XML mode (both sides XML) the panes are compared by structure ({@link XmlStructuralDiff}):
 * a list below them names each difference, and selecting one shows its lines. Merging stays a
 * line operation of the Text mode.
 */
public final class DiffView extends Tab {

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(DiffView.class);

    private static final Duration DEBOUNCE = Duration.millis(300);

    private final CodeArea leftArea = new PlainTextCodeArea();
    private final CodeArea rightArea = new PlainTextCodeArea();
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

    /** Line comparison with merge arrows, or the structural XML comparison. */
    enum Mode { TEXT, XML }

    static final String PREF_MODE = "diff.mode";
    static final String PREF_NORMALIZE = "diff.xml.normalizeWhitespace";
    static final String PREF_IGNORE_COMMENTS = "diff.xml.ignoreComments";
    static final String PREF_PREFIXES = "diff.xml.reportPrefixes";
    static final String PREF_IGNORE_ORDER = "diff.xml.ignoreOrder";
    static final String PREF_KEYS = "diff.xml.keyAttributes";

    private Mode mode = Mode.TEXT;
    private XmlStructuralDiff.Options xmlOptions = loadXmlOptions();
    private final XmlDiffPanel xmlPanel = new XmlDiffPanel(this::showDifference);
    private final SplitPane verticalSplit = new SplitPane();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton textModeButton = new ToggleButton("Text");
    private final ToggleButton xmlModeButton = new ToggleButton("XML");
    private final MenuButton xmlOptionsButton = new MenuButton("XML Options");
    private Button allLeftButton;
    private Button allRightButton;

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
        this(leftDisplayName, leftInitialText, leftSaveHandler, rightFile, readFileSafely(rightFile), false);
    }

    /**
     * Above this combined size (in characters, ~ bytes for files) two documents are not compared:
     * the line diff plus two highlighted editors of two 170-MB files exhausted a 24-GB heap.
     */
    public static final long MAX_COMPARE_CHARS = 64L * 1024 * 1024;

    /** @return whether two documents of these sizes are too large to compare side by side */
    public static boolean tooLargeToCompare(long leftChars, long rightChars) {
        return leftChars + rightChars > MAX_COMPARE_CHARS;
    }

    /** @return a user-facing explanation for {@link #tooLargeToCompare} */
    public static String tooLargeMessage(long leftChars, long rightChars) {
        return String.format(java.util.Locale.ROOT,
                "Together the two documents have %.0f MB; the side-by-side comparison supports up to %d MB.",
                (leftChars + rightChars) / (1024.0 * 1024.0), MAX_COMPARE_CHARS / (1024 * 1024));
    }

    /**
     * Opens a diff whose right file is read on a worker thread and whose first comparison runs off the
     * FX thread too (reading and diffing two large files froze the UI for seconds).
     *
     * @return completes on the FX thread with the view (its highlights arrive once the diff is computed)
     */
    public static java.util.concurrent.CompletableFuture<DiffView> open(String leftDisplayName, String leftInitialText,
                                                                       Consumer<String> leftSaveHandler, File rightFile) {
        java.util.concurrent.CompletableFuture<DiffView> result = new java.util.concurrent.CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String rightText = readFileSafely(rightFile);
            Platform.runLater(() -> {
                try {
                    result.complete(new DiffView(leftDisplayName, leftInitialText, leftSaveHandler, rightFile,
                            rightText, true));
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        });
        return result;
    }

    /**
     * Opens a diff of two files on disk; both are read on a worker thread and both sides save back
     * to their own file (UTF-8, like the right side of {@link #open(String, String, Consumer, File)}).
     *
     * @return completes on the FX thread with the view
     */
    public static java.util.concurrent.CompletableFuture<DiffView> openFiles(File leftFile, File rightFile) {
        java.util.concurrent.CompletableFuture<DiffView> result = new java.util.concurrent.CompletableFuture<>();
        Consumer<String> leftSave = text -> {
            try {
                Files.writeString(leftFile.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String leftText = readFileSafely(leftFile);
            String rightText = readFileSafely(rightFile);
            Platform.runLater(() -> {
                try {
                    result.complete(new DiffView(leftFile.getName(), leftText, leftSave, rightFile,
                            rightText, true));
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        });
        return result;
    }

    private DiffView(String leftDisplayName, String leftInitialText, Consumer<String> leftSaveHandler,
                     File rightFile, String rightInitialText, boolean computeInBackground) {
        this.leftDisplayName = leftDisplayName;
        this.rightDisplayName = rightFile.getName();
        this.leftSaveHandler = leftSaveHandler;
        this.rightFile = rightFile;

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
        boolean bothXml = XmlStructuralDiff.looksLikeXml(leftArea.getText())
                && XmlStructuralDiff.looksLikeXml(rightArea.getText());
        xmlModeButton.setDisable(!bothXml);
        if (bothXml && Mode.XML.name().equals(preference(PREF_MODE))) {
            applyMode(Mode.XML);
        }
        if (computeInBackground) {
            recomputeInBackground();
        } else {
            recomputeNow();
        }
    }

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildCenter());
        root.getStyleClass().add("diff-view");
        return root;
    }

    private ToolBar buildToolbar() {
        Button saveLeft = makeButton("Save Left", "bi-floppy", DesignTokens.ColorToken.INFO, "Save left side to original file");
        saveLeft.setOnAction(e -> doSaveLeft());

        Button saveRight = makeButton("Save Right", "bi-floppy", DesignTokens.ColorToken.INFO, "Save right side to picked file");
        saveRight.setOnAction(e -> doSaveRight());

        Button prev = makeButton("Prev", "bi-arrow-up", DesignTokens.ColorToken.NEUTRAL, "Jump to previous change (Alt+Up)");
        prev.setOnAction(e -> navigate(-1));

        Button next = makeButton("Next", "bi-arrow-down", DesignTokens.ColorToken.NEUTRAL, "Jump to next change (Alt+Down)");
        next.setOnAction(e -> navigate(1));

        Button allRight = makeButton("All →", "bi-arrow-right", DesignTokens.ColorToken.SUCCESS, "Apply ALL changes left → right");
        allRightButton = allRight;
        allRight.setOnAction(e -> applyAll(DiffGutter.Direction.LEFT_TO_RIGHT));

        Button allLeft = makeButton("All ←", "bi-arrow-left", DesignTokens.ColorToken.SUCCESS, "Apply ALL changes right → left");
        allLeftButton = allLeft;
        allLeft.setOnAction(e -> applyAll(DiffGutter.Direction.RIGHT_TO_LEFT));

        Button recompute = makeButton("Re-compute", "bi-arrow-clockwise", DesignTokens.ColorToken.NEUTRAL, "Recompute diff now");
        recompute.setOnAction(e -> recomputeNow());

        Button close = makeButton("Close", "bi-x-circle", DesignTokens.ColorToken.DANGER, "Close diff tab");
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
                textModeButton, xmlModeButton, buildXmlOptions(),
                new Separator(),
                close
        );
        textModeButton.setId("diff-mode-text");
        xmlModeButton.setId("diff-mode-xml");
        textModeButton.setToggleGroup(modeGroup);
        xmlModeButton.setToggleGroup(modeGroup);
        textModeButton.setSelected(true);
        textModeButton.setTooltip(new Tooltip("Compare line by line; merge with the arrows"));
        xmlModeButton.setTooltip(new Tooltip("Compare as XML: elements, attributes and text, "
                + "regardless of formatting and attribute order (both sides must be XML)"));
        modeGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                modeGroup.selectToggle(old);   // one mode is always on
            } else if ((selected == xmlModeButton) != (mode == Mode.XML)) {
                setMode(selected == xmlModeButton ? Mode.XML : Mode.TEXT);
            }
        });
        tb.getStyleClass().add("xsd-toolbar");
        return tb;
    }

    private static Button makeButton(String text, String iconLiteral, DesignTokens.ColorToken iconColor, String tip) {
        Button b = new Button(text);
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(20);
        org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(icon, iconColor);
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

        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.getItems().add(split);
        return verticalSplit;
    }

    private MenuButton buildXmlOptions() {
        CheckMenuItem normalize = new CheckMenuItem("Ignore whitespace differences");
        CheckMenuItem comments = new CheckMenuItem("Ignore comments");
        CheckMenuItem prefixes = new CheckMenuItem("Report namespace prefix changes");
        CheckMenuItem order = new CheckMenuItem("Ignore element order");
        MenuItem keys = new MenuItem("Key Attributes…");
        normalize.setSelected(xmlOptions.normalizeWhitespace());
        comments.setSelected(xmlOptions.ignoreComments());
        prefixes.setSelected(xmlOptions.reportPrefixChanges());
        order.setSelected(xmlOptions.ignoreElementOrder());
        Runnable changed = () -> setXmlOptions(new XmlStructuralDiff.Options(normalize.isSelected(),
                comments.isSelected(), prefixes.isSelected(), order.isSelected(), xmlOptions.keyAttributes()));
        for (CheckMenuItem item : List.of(normalize, comments, prefixes, order)) {
            item.setOnAction(e -> changed.run());
        }
        keys.setOnAction(e -> askKeyAttributes());
        xmlOptionsButton.getItems().setAll(normalize, comments, prefixes, order, new SeparatorMenuItem(), keys);
        xmlOptionsButton.setId("diff-xml-options");
        IconifyIcon icon = new IconifyIcon("bi-sliders");
        icon.setIconSize(20);
        org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(icon, DesignTokens.ColorToken.NEUTRAL);
        xmlOptionsButton.setGraphic(icon);
        xmlOptionsButton.getStyleClass().add("toolbar-button");
        xmlOptionsButton.setTooltip(new Tooltip("What the XML comparison ignores"));
        xmlOptionsButton.setDisable(true);
        return xmlOptionsButton;
    }

    private void askKeyAttributes() {
        TextInputDialog dialog = new TextInputDialog(String.join(", ", xmlOptions.keyAttributes()));
        dialog.setTitle("Key Attributes");
        dialog.setHeaderText("Attributes that identify an element among siblings of the same name");
        dialog.setContentText("Attributes (comma-separated, e.g. id, key):");
        if (getTabPane() != null && getTabPane().getScene() != null) {
            dialog.initOwner(getTabPane().getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(text -> setXmlOptions(new XmlStructuralDiff.Options(
                xmlOptions.normalizeWhitespace(), xmlOptions.ignoreComments(), xmlOptions.reportPrefixChanges(),
                xmlOptions.ignoreElementOrder(), XmlStructuralDiff.Options.parseKeys(text))));
    }

    /** Uses new XML comparison options (remembered in the settings) and compares again. */
    void setXmlOptions(XmlStructuralDiff.Options options) {
        xmlOptions = options;
        setPreference(PREF_NORMALIZE, String.valueOf(options.normalizeWhitespace()));
        setPreference(PREF_IGNORE_COMMENTS, String.valueOf(options.ignoreComments()));
        setPreference(PREF_PREFIXES, String.valueOf(options.reportPrefixChanges()));
        setPreference(PREF_IGNORE_ORDER, String.valueOf(options.ignoreElementOrder()));
        setPreference(PREF_KEYS, String.join(",", options.keyAttributes()));
        if (mode == Mode.XML) {
            recomputeInBackground();
        }
    }

    /** Switches between line and XML comparison (remembered for the next Compare view) and compares again. */
    void setMode(Mode newMode) {
        if (newMode == Mode.XML && xmlModeButton.isDisable()) {
            return;
        }
        applyMode(newMode);
        setPreference(PREF_MODE, newMode.name());
        recomputeInBackground();
    }

    private void applyMode(Mode newMode) {
        mode = newMode;
        boolean xml = newMode == Mode.XML;
        modeGroup.selectToggle(xml ? xmlModeButton : textModeButton);
        xmlOptionsButton.setDisable(!xml);
        if (allLeftButton != null) {
            allLeftButton.setDisable(xml);
            allRightButton.setDisable(xml);
        }
        if (xml && !verticalSplit.getItems().contains(xmlPanel)) {
            verticalSplit.getItems().add(xmlPanel);
            verticalSplit.setDividerPositions(0.68);
        } else if (!xml) {
            verticalSplit.getItems().remove(xmlPanel);
        }
        chunks = List.of();
        gutter.setChunks(chunks);
        currentChunkIndex = -1;
    }

    private static XmlStructuralDiff.Options loadXmlOptions() {
        XmlStructuralDiff.Options d = XmlStructuralDiff.Options.DEFAULTS;
        return new XmlStructuralDiff.Options(
                flag(PREF_NORMALIZE, d.normalizeWhitespace()), flag(PREF_IGNORE_COMMENTS, d.ignoreComments()),
                flag(PREF_PREFIXES, d.reportPrefixChanges()), flag(PREF_IGNORE_ORDER, d.ignoreElementOrder()),
                XmlStructuralDiff.Options.parseKeys(preference(PREF_KEYS)));
    }

    private static boolean flag(String key, boolean fallback) {
        String value = preference(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String preference(String key) {
        try {
            return org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;   // no settings service (isolated tests)
        }
    }

    private static void setPreference(String key, String value) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).set(key, value);
        } catch (RuntimeException e) {
            logger.debug("Compare setting {} not saved: {}", key, e.getMessage());
        }
    }

    /** XML mode: shows the lines of a difference on both sides. */
    private void showDifference(XmlStructuralDiff.Difference difference) {
        markLine(leftArea, difference.leftLine());
        markLine(rightArea, difference.rightLine());
    }

    private static void markLine(CodeArea area, int line) {
        area.clearStyle(0, area.getLength());
        int paragraph = line - 1;
        if (paragraph < 0 || paragraph >= area.getParagraphs().size()) {
            return;
        }
        area.setStyle(paragraph, List.of("diff-xml-current"));
        moveCaretToLine(area, paragraph);
    }

    private void applyXmlResult(XmlStructuralDiff.Result result) {
        leftArea.clearStyle(0, leftArea.getLength());
        rightArea.clearStyle(0, rightArea.getLength());
        chunks = List.of();
        gutter.setChunks(chunks);
        xmlPanel.setResult(result);
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
            if (updating[0]) {
                return;
            }
            updating[0] = true;
            try {
                rightScroll.setValue(n);
            } finally {
                updating[0] = false;
            }
        });
        rightScroll.addListener((obs, o, n) -> {
            if (updating[0]) {
                return;
            }
            updating[0] = true;
            try {
                leftScroll.setValue(n);
            } finally {
                updating[0] = false;
            }
        });
    }

    private void installDebouncedRecompute() {
        // After typing, the comparison runs off the FX thread (diffing large files took seconds).
        leftDebounce.setOnFinished(e -> recomputeInBackground());
        rightDebounce.setOnFinished(e -> recomputeInBackground());

        // plainTextChanges, not textProperty: a ChangeListener builds the whole text per keystroke
        org.fxt.freexmltoolkit.controls.v2.editor.TextEdits.of(leftArea).subscribe(change -> {
            if (suppressDebounce) return;
            setDirty(0, true);
            leftDebounce.playFromStart();
        });
        org.fxt.freexmltoolkit.controls.v2.editor.TextEdits.of(rightArea).subscribe(change -> {
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
        if (mode == Mode.XML) {
            applyXmlResult(XmlStructuralDiff.compare(left, right, xmlOptions));
            return;
        }
        List<DiffChunk> result = DiffEngine.compute(left, right);
        if (gen != recomputeGeneration.get()) return;
        this.chunks = result;
        leftArea.setStyleSpans(0, DiffHighlighter.computeHighlighting(left, chunks, DiffHighlighter.Side.LEFT));
        rightArea.setStyleSpans(0, DiffHighlighter.computeHighlighting(right, chunks, DiffHighlighter.Side.RIGHT));
        gutter.setChunks(chunks);
        if (currentChunkIndex >= chunks.size()) currentChunkIndex = -1;
    }

    /**
     * {@link #recomputeNow()} with the diff and the highlight spans computed on a worker thread; the
     * result is applied on the FX thread unless a newer recompute (or an edit) superseded it.
     */
    private void recomputeInBackground() {
        long gen = recomputeGeneration.incrementAndGet();
        String left = leftArea.getText();
        String right = rightArea.getText();
        if (mode == Mode.XML) {
            XmlStructuralDiff.Options options = xmlOptions;
            xmlPanel.setComputing();
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                XmlStructuralDiff.Result result;
                try {
                    result = XmlStructuralDiff.compare(left, right, options);
                } catch (OutOfMemoryError | RuntimeException e) {
                    logger.warn("XML comparison failed: {}", e.toString());
                    result = new XmlStructuralDiff.Result(List.of(), "The comparison failed: " + e.getMessage(), null);
                }
                XmlStructuralDiff.Result done = result;
                Platform.runLater(() -> {
                    if (gen == recomputeGeneration.get() && mode == Mode.XML
                            && leftArea.getLength() == left.length() && rightArea.getLength() == right.length()) {
                        applyXmlResult(done);
                    }
                });
            });
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            List<DiffChunk> result;
            org.fxmisc.richtext.model.StyleSpans<java.util.Collection<String>> leftSpans;
            org.fxmisc.richtext.model.StyleSpans<java.util.Collection<String>> rightSpans;
            try {
                result = DiffEngine.compute(left, right);
                leftSpans = DiffHighlighter.computeHighlighting(left, result, DiffHighlighter.Side.LEFT);
                rightSpans = DiffHighlighter.computeHighlighting(right, result, DiffHighlighter.Side.RIGHT);
            } catch (OutOfMemoryError e) {
                // edited beyond what fits: keep the view usable without highlights
                logger.warn("Not enough memory to compare {} + {} characters", left.length(), right.length());
                return;
            }
            Platform.runLater(() -> {
                if (gen != recomputeGeneration.get()
                        || leftArea.getLength() != left.length() || rightArea.getLength() != right.length()) {
                    return; // superseded or edited meanwhile: the next recompute covers it
                }
                this.chunks = result;
                leftArea.setStyleSpans(0, leftSpans);
                rightArea.setStyleSpans(0, rightSpans);
                gutter.setChunks(chunks);
                if (currentChunkIndex >= chunks.size()) currentChunkIndex = -1;
            });
        });
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

    /**
     * Applies every non-equal chunk as one edit followed by one recompute. Applying them
     * one by one would re-diff and re-highlight both sides once per chunk.
     */
    private void applyAll(DiffGutter.Direction direction) {
        if (mode == Mode.XML) {
            return;   // merging is a line operation of the Text mode
        }
        boolean toRight = direction == DiffGutter.Direction.LEFT_TO_RIGHT;
        DiffMerge.Result merged = DiffMerge.applyAll(leftArea.getText(), rightArea.getText(), chunks,
                toRight ? DiffMerge.Direction.LEFT_TO_RIGHT : DiffMerge.Direction.RIGHT_TO_LEFT);
        CodeArea target = toRight ? rightArea : leftArea;
        String oldText = target.getText();
        String newText = toRight ? merged.right() : merged.left();
        if (oldText.equals(newText)) return;

        // Replace only the span that actually differs, so caret and scroll position survive.
        int max = Math.min(oldText.length(), newText.length());
        int prefix = 0;
        while (prefix < max && oldText.charAt(prefix) == newText.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < max - prefix
                && oldText.charAt(oldText.length() - 1 - suffix) == newText.charAt(newText.length() - 1 - suffix)) {
            suffix++;
        }

        suppressDebounce = true;
        try {
            target.replaceText(prefix, oldText.length() - suffix, newText.substring(prefix, newText.length() - suffix));
            setDirty(toRight ? 1 : 0, true);
        } finally {
            suppressDebounce = false;
        }
        recomputeNow();
    }

    private void navigate(int delta) {
        if (mode == Mode.XML) {
            xmlPanel.select(delta);
            return;
        }
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
            showError("Could not save left side", ex.getMessage(), ex);
        }
    }

    private void doSaveRight() {
        try {
            Files.writeString(rightFile.toPath(), rightArea.getText(), StandardCharsets.UTF_8);
            setDirty(1, false);
        } catch (IOException ex) {
            showError("Could not save right side", ex.getMessage(), ex);
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

    private void showError(String title, String message, Throwable cause) {
        // themed, and counted in the error telemetry like every other error dialog
        org.fxt.freexmltoolkit.util.DialogHelper.showError(title, title, message == null ? "" : message, cause);
    }

    /** For tests: the XML differences shown in XML mode. */
    List<XmlStructuralDiff.Difference> getXmlDifferencesForTesting() {
        return xmlPanel.differences();
    }

    /** For tests: the summary line above the XML differences. */
    String getXmlSummaryForTesting() {
        return xmlPanel.summaryText();
    }

    /** For tests: switches the mode and compares synchronously. */
    void setModeForTesting(Mode newMode) {
        applyMode(newMode);
        recomputeNow();
    }

    /** For tests: whether the XML mode can be chosen. */
    boolean isXmlModeAvailableForTesting() {
        return !xmlModeButton.isDisable();
    }

    /** For tests: selects the next or previous difference (the Prev/Next buttons). */
    void navigateForTesting(int delta) {
        navigate(delta);
    }

    /** For tests: the caret line (0-based) of the left and right pane. */
    int[] getCaretParagraphsForTesting() {
        return new int[] {leftArea.getCurrentParagraph(), rightArea.getCurrentParagraph()};
    }

    /** For tests: returns the chunks computed for the current text pair. */
    public List<DiffChunk> getChunksForTesting() {
        return chunks;
    }

    /** For tests: the gutter between the two panes. */
    DiffGutter getGutterForTesting() {
        return gutter;
    }

    /** For tests: runs the "apply all" toolbar action. */
    void applyAllForTesting(DiffGutter.Direction direction) {
        applyAll(direction);
    }

    /** For tests: text of the left and right pane. */
    String[] getTextsForTesting() {
        return new String[] {leftArea.getText(), rightArea.getText()};
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
