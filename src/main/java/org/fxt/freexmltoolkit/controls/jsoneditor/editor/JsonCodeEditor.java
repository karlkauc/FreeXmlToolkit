/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.EditorFont;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * JSON Code Editor component using RichTextFX.
 * Provides syntax highlighting for JSON, JSONC, and JSON5.
 */
public class JsonCodeEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(JsonCodeEditor.class);

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final Label statusLabel;

    // State
    private boolean dirty = false;
    private String documentPath;
    private String detectedFormat = "json";

    // Font size control
    /** A zoom of this editor alone while "remember zoom" is off; null = the editor font's size. */
    private Integer localFontSize;
    private final javafx.beans.value.ChangeListener<EditorFont.Settings> editorFontListener =
            (obs, old, value) -> updateFontSize();

    // Hover tooltip
    private Popup hoverPopup;
    private Label hoverLabel;
    private long lastHoverUpdate = 0;
    private static final long HOVER_DELAY_MS = 300;

    // Drag & Drop callback
    private Consumer<File> onFileDropped;

    // Context menu
    private JsonContextMenuManager contextMenuManager;

    /** Merge point for syntax + validation-error + search-match styling (like the XML editor). */
    private final org.fxt.freexmltoolkit.controls.v2.editor.managers.CombinedStyleManager combinedStyles;
    /** The problems shown in the text, by 0-based paragraph (gutter badge + tooltip). */
    private java.util.Map<Integer, List<String>> problemsByLine = java.util.Map.of();
    /** The marked problem ranges as {start, end, message index into problemMessages}. */
    private List<ProblemRange> problemRanges = List.of();
    /** Folds objects, arrays and block comments (bracket pairs, see {@link JsonFoldScanner}). */
    private final org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2 foldingManager;

    /** The bound JSON Schema (completion, hover), or null. Set from worker threads. */
    private volatile org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver schema;
    /** Schema-driven completion (Ctrl+Space, after a quote at a key or a colon). */
    private final JsonIntelliSenseEngine intelliSense;

    /** An extra gutter column contributed by the shell (e.g. quick-fix light bulbs), or null. */
    private IntFunction<javafx.scene.Node> extraGutterFactory;

    /**
     * A marked problem range.
     *
     * @param start   offset of its first character
     * @param end     offset after its last character
     * @param line    0-based paragraph of {@code start}
     * @param message the problem message
     */
    public record ProblemRange(int start, int end, int line, String message) {
    }

    /**
     * Creates a new JSON code editor.
     */
    public JsonCodeEditor() {
        super();

        logger.info("Creating JsonCodeEditor...");

        // Create code area
        this.codeArea = new PlainTextCodeArea();
        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.combinedStyles = new org.fxt.freexmltoolkit.controls.v2.editor.managers.CombinedStyleManager(codeArea);
        this.foldingManager = new org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2(
                codeArea, JsonFoldScanner::scan);
        this.intelliSense = new JsonIntelliSenseEngine(codeArea, () -> schema);

        // Create status bar
        this.statusLabel = new Label(I18n.t("JsonCodeEditor.ready"));

        // Initialize UI
        initializeUI();

        // Setup event handlers
        setupEventHandlers();

        // Setup hover tooltip
        setupHoverTooltip();

        // Setup drag & drop
        setupDragAndDrop();

        // Setup context menu
        setupContextMenu();

        logger.info("JsonCodeEditor created successfully");
    }

    /**
     * Sets up the context menu for the editor.
     */
    private void setupContextMenu() {
        contextMenuManager = new JsonContextMenuManager(codeArea);
        contextMenuManager.initializeContextMenu();
        contextMenuManager.setFoldingActions(foldingManager::foldAll, foldingManager::unfoldAll);
        logger.debug("Context menu setup");
    }

    /**
     * Sets the context menu action handler.
     *
     * @param actions The action handler
     */
    public void setContextMenuActions(JsonContextMenuManager.JsonContextActions actions) {
        if (contextMenuManager != null) {
            contextMenuManager.setContextActions(actions);
        }
    }

    /**
     * Initializes the UI layout.
     */
    private void initializeUI() {
        // Load stylesheets
        loadStylesheets();

        // Set up code area styling: the user's editor font, followed live
        updateFontSize();
        EditorFont.settingsProperty().addListener(new javafx.beans.value.WeakChangeListener<>(editorFontListener));
        codeArea.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isShortcutDown() && event.getDeltaY() != 0) {
                if (event.getDeltaY() > 0) {
                    increaseFontSize();
                } else {
                    decreaseFontSize();
                }
                event.consume();
            }
        });

        // Setup line numbers
        codeArea.setParagraphGraphicFactory(createLineNumberFactory());

        // Status bar styling (theme-aware: fxt-theme.css light, dark-theme.css override)
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("json-editor-statusbar");
        statusBar.getChildren().add(statusLabel);

        // Layout
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(scrollPane, statusBar);

        logger.debug("UI initialized");
    }

    /**
     * Creates line number factory.
     */
    private IntFunction<javafx.scene.Node> createLineNumberFactory() {
        return lineIndex -> {
            HBox hbox = new HBox(3);
            hbox.setAlignment(Pos.CENTER_LEFT);

            if (extraGutterFactory != null) {
                javafx.scene.Node extra = extraGutterFactory.apply(lineIndex);
                if (extra != null) {
                    hbox.getChildren().add(extra);
                }
            }

            javafx.scene.Node foldIndicator = foldingManager.createFoldIndicator(lineIndex);
            if (foldIndicator != null) {
                hbox.getChildren().add(foldIndicator);
            } else {
                Region foldSpacer = new Region();
                foldSpacer.setPrefSize(org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2.FOLD_TOGGLE_SIZE,
                        org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2.FOLD_TOGGLE_SIZE);
                hbox.getChildren().add(foldSpacer);
            }

            // Problem badge (or a spacer of the same size for alignment)
            List<String> problems = problemsByLine.get(lineIndex);
            if (problems != null && !problems.isEmpty()) {
                var icon = new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-exclamation-circle-fill");
                icon.setIconSize(12);
                icon.getStyleClass().add("json-gutter-problem");
                Label badge = new Label();
                badge.setGraphic(icon);
                badge.setMinSize(12, 12);
                badge.setPrefSize(12, 12);
                badge.getStyleClass().add("json-gutter-problem-badge");
                javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(String.join("\n", problems));
                tooltip.setShowDelay(javafx.util.Duration.millis(200));
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(560);
                badge.setTooltip(tooltip);
                badge.setAccessibleText(String.join("; ", problems));
                hbox.getChildren().add(badge);
            } else {
                Region spacer = new Region();
                spacer.setPrefSize(12, 12);
                spacer.setMinSize(12, 12);
                hbox.getChildren().add(spacer);
            }

            // Add line number
            Label lineNumber = new Label(String.format("%4d", lineIndex + 1));
            lineNumber.setStyle("-fx-font-family: monospace; -fx-text-fill: gray;");
            hbox.getChildren().add(lineNumber);

            return hbox;
        };
    }

    /**
     * Shows validation problems in the text: each range is underlined, its line gets a badge with
     * the messages as tooltip, and hovering it shows the message. The markers are dropped on the
     * next edit (their offsets would be stale) until the next validation sets new ones.
     *
     * @param ranges the problems; empty clears them
     */
    public void setProblems(List<ProblemRange> ranges) {
        List<ProblemRange> list = ranges == null ? List.of() : List.copyOf(ranges);
        if (list.isEmpty() && problemRanges.isEmpty()) {
            return;
        }
        problemRanges = list;
        java.util.Map<Integer, List<String>> byLine = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> overlay = new java.util.HashMap<>();
        for (ProblemRange range : list) {
            byLine.computeIfAbsent(range.line(), l -> new java.util.ArrayList<>()).add(range.message());
            overlay.merge(range.start(), range.end() - range.start(), Math::max);
        }
        problemsByLine = byLine;
        if (overlay.isEmpty()) {
            combinedStyles.clearErrors();
        } else {
            combinedStyles.setErrorRanges(overlay);
        }
        refreshGutter();
    }

    /** Above this size the text is not scanned for folding regions (a copy per edit pause). */
    private static final int FOLDING_LIMIT = 16 * 1024 * 1024;

    /** Recomputes the foldable regions off the FX thread (skipped for very large documents). */
    private void updateFolding() {
        if (codeArea.getLength() <= FOLDING_LIMIT) {
            foldingManager.updateFoldingRegionsAsync(codeArea.getText());
        }
    }

    /**
     * Binds a JSON Schema for completion and hover help ({@code null} unbinds). Any thread.
     *
     * @param resolver the schema
     */
    public void setSchema(org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver resolver) {
        this.schema = resolver;
    }

    /** @return the bound schema, or {@code null} */
    public org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver getSchema() {
        return schema;
    }

    /** @return the completion engine */
    public JsonIntelliSenseEngine getIntelliSense() {
        return intelliSense;
    }

    /**
     * What the bound schema says about the key or value at an offset, as hover text lines.
     *
     * @param offset an offset in the text
     * @return the lines (empty without a schema or when it says nothing)
     */
    public List<String> schemaInfoAt(int offset) {
        var resolver = schema;
        // the hover reads the whole text on every mouse pause: only for documents up to 1 MB
        if (resolver == null || codeArea.getLength() > 1024 * 1024) {
            return List.of();
        }
        List<Object> path = org.fxt.freexmltoolkit.service.jsonschema.JsonCaretContext.pathAt(codeArea.getText(), offset);
        if (path == null) {
            return List.of();
        }
        var view = resolver.at(path, null);
        if (!view.known()) {
            return List.of();
        }
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder head = new StringBuilder("Schema:");
        if (view.title() != null) {
            head.append(' ').append(view.title());
        }
        if (view.typeText() != null) {
            head.append(" (").append(view.typeText()).append(')');
        }
        if (!path.isEmpty() && path.get(path.size() - 1) instanceof String key) {
            var parent = resolver.at(path.subList(0, path.size() - 1), null);
            if (parent.required().contains(key)) {
                head.append(" - required");
            }
        }
        if (view.deprecated()) {
            head.append(" - deprecated");
        }
        lines.add(head.toString());
        if (view.description() != null) {
            lines.add(view.description().length() > 300 ? view.description().substring(0, 297) + "..." : view.description());
        }
        if (!view.allowedValues().isEmpty()) {
            List<String> values = view.allowedValues().stream().limit(8).map(Object::toString).toList();
            lines.add("Allowed: " + String.join(", ", values) + (view.allowedValues().size() > 8 ? ", ..." : ""));
        }
        if (view.format() != null) {
            lines.add("Format: " + view.format());
        }
        if (!view.constraints().isEmpty()) {
            List<String> parts = new java.util.ArrayList<>();
            view.constraints().forEach((k, v) -> parts.add(k + " " + v));
            lines.add(String.join(", ", parts));
        }
        return lines;
    }

    /** @return the folding support (fold all / unfold all, foldable regions) */
    public org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2 getFoldingManager() {
        return foldingManager;
    }

    /** @return the context menu manager (the shell installs its actions) */
    public JsonContextMenuManager getContextMenuManager() {
        return contextMenuManager;
    }

    /** @return the problems currently marked in the text */
    public List<ProblemRange> getProblems() {
        return problemRanges;
    }

    /** @return the messages of the problems covering {@code offset} (empty when none) */
    public List<String> problemsAt(int offset) {
        List<String> messages = new java.util.ArrayList<>();
        for (ProblemRange range : problemRanges) {
            if (offset >= range.start() && offset < range.end()) {
                messages.add(range.message());
            }
        }
        return messages;
    }

    /**
     * Sets an extra gutter column (left of the problem badge), e.g. quick-fix light bulbs.
     *
     * @param factory a node per 0-based paragraph ({@code null} entries allowed); {@code null} removes it
     */
    public void setExtraGutterFactory(IntFunction<javafx.scene.Node> factory) {
        this.extraGutterFactory = factory;
        refreshGutter();
    }

    /** Rebuilds the gutter cells (after the problems or the extra column changed). */
    public void refreshGutter() {
        codeArea.setParagraphGraphicFactory(createLineNumberFactory());
    }

    /**
     * Highlights search matches (start offset → length) on top of the syntax highlighting.
     *
     * @param ranges the matches; empty clears them
     */
    public void setSearchMatchRanges(java.util.Map<Integer, Integer> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            combinedStyles.clearMatches();
        } else {
            combinedStyles.setMatchRanges(ranges);
        }
    }

    /**
     * Loads CSS stylesheets.
     */
    private void loadStylesheets() {
        try {
            // NOTE: fxt-theme.css is deliberately NOT attached here. It reaches the editor
            // via the shell's parent stylesheets; a node-level copy would out-rank the
            // dark-theme.css overrides and lock the editor into the light palette.

            // Load JSON highlighting CSS
            var jsonCssResource = getClass().getResource("/css/json-editor.css");
            if (jsonCssResource != null) {
                codeArea.getStylesheets().add(jsonCssResource.toExternalForm());
            }

            logger.debug("Stylesheets loaded");
        } catch (Exception e) {
            logger.error("Error loading stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets up event handlers.
     */
    private void setupEventHandlers() {
        // Problem markers point at offsets of the validated text: an edit makes them stale
        codeArea.plainTextChanges().subscribe(change -> {
            if (!problemRanges.isEmpty()) {
                setProblems(List.of());
            }
        });

        // Text change listener with debounce for syntax highlighting
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> {
                    requestHighlighting();
                    updateFolding();
                    dirty = true;
                });

        // In viewport mode, re-highlight the newly visible region when the user scrolls.
        codeArea.estimatedScrollYProperty().addListener((obs, oldV, newV) -> {
            if (codeArea.getLength() > VIEWPORT_THRESHOLD) {
                viewportDebounce.playFromStart();
            }
        });
        viewportDebounce.setOnFinished(e -> highlightVisibleRegion());

        // Caret position listener for status line
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            updateStatusBar();
        });

        // Keyboard shortcuts
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case PLUS, EQUALS -> increaseFontSize();
                    case MINUS -> decreaseFontSize();
                    case DIGIT0 -> resetFontSize();
                    default -> { }
                }
            }
        });

        logger.debug("Event handlers setup");
    }

    /**
     * Sets up the hover tooltip for showing JSONPath information.
     */
    private void setupHoverTooltip() {
        // Create popup and label
        hoverPopup = new Popup();
        hoverLabel = new Label();
        hoverLabel.setStyle(
                "-fx-background-color: #2d2d2d; " +
                "-fx-text-fill: #e0e0e0; " +
                "-fx-padding: 8 12; " +
                "-fx-font-family: 'JetBrains Mono'; " +
                org.fxt.freexmltoolkit.controls.theme.Typography.fontSize(11) + " " +
                "-fx-background-radius: 4; " +
                "-fx-border-radius: 4; " +
                "-fx-border-color: #555; " +
                "-fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 2, 2);"
        );
        hoverPopup.getContent().add(hoverLabel);
        hoverPopup.setAutoHide(true);

        // Mouse move handler
        codeArea.setOnMouseMoved(event -> {
            long now = System.currentTimeMillis();
            if (now - lastHoverUpdate < HOVER_DELAY_MS) {
                return;
            }
            lastHoverUpdate = now;

            try {
                // Get character position under mouse
                var hitInfo = codeArea.hit(event.getX(), event.getY());
                int charIndex = hitInfo.getCharacterIndex().orElse(-1);

                if (charIndex >= 0 && charIndex < codeArea.getLength()) {
                    String text = codeArea.getText();
                    JsonPathCalculator.JsonHoverInfo hoverInfo = JsonPathCalculator.calculatePath(text, charIndex);
                    List<String> problems = problemsAt(charIndex);

                    if (!problems.isEmpty() || (hoverInfo != null && hoverInfo.isValid())) {
                        // Build tooltip text: the problems first, then where the caret is
                        StringBuilder tooltipText = new StringBuilder();
                        for (String problem : problems) {
                            tooltipText.append("\u26A0 ").append(problem).append('\n');
                        }
                        List<String> schemaLines = schemaInfoAt(charIndex);
                        if (hoverInfo == null || !hoverInfo.isValid()) {
                            schemaLines.forEach(line -> tooltipText.append(line).append('\n'));
                            hoverLabel.setText(tooltipText.toString().strip());
                            hoverPopup.show(codeArea, event.getScreenX() + 15, event.getScreenY() + 15);
                            return;
                        }
                        tooltipText.append("Path: ").append(hoverInfo.jsonPath());
                        tooltipText.append("\nType: ").append(hoverInfo.valueType());
                        if (hoverInfo.key() != null) {
                            tooltipText.append("\nKey: \"").append(hoverInfo.key()).append("\"");
                        }
                        if (hoverInfo.value() != null && !hoverInfo.value().isEmpty()) {
                            String displayValue = hoverInfo.value();
                            if (displayValue.length() > 50) {
                                displayValue = displayValue.substring(0, 47) + "...";
                            }
                            tooltipText.append("\nValue: ").append(displayValue);
                        }
                        for (String line : schemaLines) {
                            tooltipText.append('\n').append(line);
                        }

                        hoverLabel.setText(tooltipText.toString());

                        // Position and show popup
                        Bounds screenBounds = codeArea.localToScreen(codeArea.getBoundsInLocal());
                        if (screenBounds != null) {
                            double x = event.getScreenX() + 15;
                            double y = event.getScreenY() + 15;
                            hoverPopup.show(codeArea, x, y);
                        }
                    } else {
                        hoverPopup.hide();
                    }
                } else {
                    hoverPopup.hide();
                }
            } catch (Exception e) {
                logger.trace("Hover tooltip error: {}", e.getMessage());
                hoverPopup.hide();
            }
        });

        // Hide on mouse exit
        codeArea.setOnMouseExited(event -> hoverPopup.hide());

        logger.debug("Hover tooltip setup");
    }

    /**
     * Sets up drag and drop support for JSON files.
     */
    private void setupDragAndDrop() {
        // Accept drag over
        codeArea.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty() && isJsonFile(files.get(0))) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        // Handle drop
        codeArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    if (isJsonFile(file)) {
                        if (onFileDropped != null) {
                            // Use callback if set
                            onFileDropped.accept(file);
                            success = true;
                        } else {
                            // Load file directly
                            try {
                                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                                setText(content);
                                documentPath = file.getAbsolutePath();
                                success = true;
                                logger.info("File dropped and loaded: {}", file.getName());
                            } catch (Exception e) {
                                logger.error("Error loading dropped file: {}", e.getMessage(), e);
                            }
                        }
                    }
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        // Visual feedback on drag enter/exit
        codeArea.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                codeArea.setStyle(codeArea.getStyle() + "-fx-border-color: #28a745; -fx-border-width: 2;");
            }
        });

        codeArea.setOnDragExited(event -> {
            updateFontSize(); // Reset style
        });

        logger.debug("Drag and drop setup");
    }

    /**
     * Checks if a file is a JSON file.
     */
    private boolean isJsonFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".jsonc") || name.endsWith(".json5");
    }

    /**
     * Sets a callback for when a file is dropped onto the editor.
     *
     * @param callback the callback to invoke when a file is dropped, receives the dropped file
     */
    public void setOnFileDropped(Consumer<File> callback) {
        this.onFileDropped = callback;
    }

    /** Above this size only the visible paragraphs are highlighted (like the XML editor). */
    private static final int VIEWPORT_THRESHOLD = 2 * 1024 * 1024;
    private static final int VIEWPORT_BUFFER_PARAGRAPHS = 80;
    private final javafx.animation.PauseTransition viewportDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
    /** Bumped per highlight request (FX thread); a result for an older request is dropped. */
    private long highlightRequest;

    /** Highlights spans off the FX thread; one daemon thread shared by all JSON editors. */
    private static final java.util.concurrent.ExecutorService HIGHLIGHTER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "JsonHighlighter");
                t.setDaemon(true);
                return t;
            });

    /**
     * Highlights the current text without blocking the FX thread: the spans (and the format) are
     * computed on a background thread and applied if the text did not change meanwhile. Above
     * {@link #VIEWPORT_THRESHOLD} only the visible paragraphs are styled: applying spans to a multi-MB
     * document took hundreds of ms per typing pause. FX thread.
     */
    private void requestHighlighting() {
        if (codeArea.getLength() == 0) {
            return;
        }
        if (codeArea.getLength() > VIEWPORT_THRESHOLD) {
            highlightVisibleRegion();
            return;
        }
        long request = ++highlightRequest;
        String text = codeArea.getText();
        HIGHLIGHTER.execute(() -> {
            StyleSpans<Collection<String>> highlighting;
            String format;
            try {
                highlighting = computeHighlighting(text);
                format = JsonSyntaxHighlighter.detectFormat(text);
            } catch (RuntimeException e) {
                logger.error("Error computing syntax highlighting: {}", e.getMessage(), e);
                return;
            }
            Platform.runLater(() -> {
                if (request != highlightRequest || codeArea.getLength() != text.length()) {
                    return; // edited meanwhile: a newer request follows
                }
                try {
                    combinedStyles.setSyntaxStyles(highlighting);
                    detectedFormat = format;
                    updateStatusBar();
                } catch (Exception e) {
                    logger.debug("Error applying syntax highlighting: {}", e.getMessage());
                }
            });
        });
    }

    /** Highlights only the visible paragraphs (plus a buffer); viewport mode for very large documents. */
    private void highlightVisibleRegion() {
        try {
            int paragraphs = codeArea.getParagraphs().size();
            if (paragraphs == 0) {
                return;
            }
            int first = 0;
            int last = Math.min(paragraphs - 1, VIEWPORT_BUFFER_PARAGRAPHS * 2);
            try {
                first = Math.max(0, codeArea.firstVisibleParToAllParIndex() - VIEWPORT_BUFFER_PARAGRAPHS);
                last = Math.min(paragraphs - 1, codeArea.lastVisibleParToAllParIndex() + VIEWPORT_BUFFER_PARAGRAPHS);
            } catch (Exception notLaidOut) {
                // viewport not laid out yet: highlight the top chunk
            }
            int start = codeArea.getAbsolutePosition(first, 0);
            int end = codeArea.getAbsolutePosition(last, codeArea.getParagraphLength(last));
            if (end > start) {
                codeArea.setStyleSpans(start, computeHighlighting(codeArea.getText(start, end)));
            }
        } catch (Exception e) {
            logger.debug("Viewport highlighting failed: {}", e.getMessage());
        }
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        // Minified JSON: lines with thousands of spans freeze the editor's line layout.
        return org.fxt.freexmltoolkit.controls.shared.DenseLineGuard
                .flattenDenseLines(text, JsonSyntaxHighlighter.computeHighlighting(text));
    }

    /**
     * Updates the status bar.
     */
    private void updateStatusBar() {
        int line = codeArea.getCurrentParagraph() + 1;
        int column = codeArea.getCaretColumn() + 1;
        int totalLines = codeArea.getParagraphs().size();
        int totalChars = codeArea.getLength();

        String status = String.format("Line %d, Col %d | Lines: %d | Chars: %d | Format: %s",
                line, column, totalLines, totalChars, detectedFormat.toUpperCase());

        statusLabel.setText(status);
    }

    // ==================== Public API ====================

    /**
     * Sets the text content of the editor.
     *
     * @param text the text content to set, or null to clear the editor
     */
    public void setText(String text) {
        codeArea.replaceText(text != null ? text : "");
        codeArea.moveTo(0);
        codeArea.requestFollowCaret();
        if (text != null && !text.isEmpty()) {
            Platform.runLater(() -> {
                requestHighlighting();
                updateFolding();
                updateStatusBar();
            });
        }
        dirty = false;
    }

    /**
     * Gets the current text content of the editor.
     *
     * @return the current text content
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Gets the underlying CodeArea component for advanced operations.
     *
     * @return the CodeArea component used by this editor
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return true if there are unsaved changes, false otherwise
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag indicating whether there are unsaved changes.
     *
     * @param dirty true to mark as having unsaved changes, false otherwise
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Sets the file path associated with this document.
     *
     * @param path the absolute file path, or null if not associated with a file
     */
    public void setDocumentPath(String path) {
        this.documentPath = path;
    }

    /**
     * Gets the file path associated with this document.
     *
     * @return the absolute file path, or null if not associated with a file
     */
    public String getDocumentPath() {
        return documentPath;
    }

    /**
     * Gets the detected JSON format based on the content analysis.
     *
     * @return the detected format: "json", "jsonc", or "json5"
     */
    public String getDetectedFormat() {
        return detectedFormat;
    }

    /**
     * Refreshes syntax highlighting.
     */
    public void refreshHighlighting() {
        if (codeArea.getLength() > 0) {
            Platform.runLater(this::requestHighlighting);
        }
    }

    /**
     * Performs undo operation.
     */
    public void undo() {
        codeArea.undo();
    }

    /**
     * Performs redo operation.
     */
    public void redo() {
        codeArea.redo();
    }

    /**
     * Checks if an undo operation is available.
     *
     * @return true if undo is available, false otherwise
     */
    public boolean canUndo() {
        return codeArea.isUndoAvailable();
    }

    /**
     * Checks if a redo operation is available.
     *
     * @return true if redo is available, false otherwise
     */
    public boolean canRedo() {
        return codeArea.isRedoAvailable();
    }

    /**
     * Copies selected text to clipboard.
     */
    public void copy() {
        codeArea.copy();
    }

    /**
     * Cuts selected text to clipboard.
     */
    public void cut() {
        codeArea.cut();
    }

    /**
     * Pastes text from clipboard.
     */
    public void paste() {
        codeArea.paste();
    }

    /**
     * Selects all text.
     */
    public void selectAll() {
        codeArea.selectAll();
    }

    /**
     * Gets the currently selected text in the editor.
     *
     * @return the selected text, or an empty string if nothing is selected
     */
    public String getSelectedText() {
        return codeArea.getSelectedText();
    }

    /**
     * Replaces the currently selected text with the specified text.
     *
     * @param text the text to replace the selection with
     */
    public void replaceSelection(String text) {
        codeArea.replaceSelection(text);
    }

    /**
     * Increases font size.
     */
    public void increaseFontSize() {
        setFontSize(getFontSize() + 1);
    }

    /**
     * Decreases font size.
     */
    public void decreaseFontSize() {
        setFontSize(getFontSize() - 1);
    }

    /**
     * Resets the zoom (built-in default with "remember zoom", otherwise the settings' size).
     */
    public void resetFontSize() {
        localFontSize = null;
        if (!EditorFont.zoomTo(EditorFont.DEFAULT_SIZE)) {
            updateFontSize();
        }
    }

    /**
     * Zooms to a size: every editor with "remember zoom" on, otherwise this editor only.
     *
     * @param size the size in points
     */
    public void setFontSize(int size) {
        int clamped = Math.clamp(size, EditorFont.MIN_SIZE, EditorFont.MAX_SIZE);
        if (EditorFont.zoomTo(clamped)) {
            localFontSize = null;
        } else {
            localFontSize = clamped;
            updateFontSize();
        }
    }

    /** @return the current font size in points */
    public int getFontSize() {
        return localFontSize != null ? localFontSize : EditorFont.current().size();
    }

    private void updateFontSize() {
        EditorFont.Settings font = EditorFont.current();
        int size = getFontSize();
        codeArea.setStyle(EditorFont.inlineStyle(font, size, "pt"));
        EditorFont.applyLineSpacing(codeArea, EditorFont.ptToPx(size), font.lineSpacing());
    }

    /**
     * Requests focus for the code area.
     */
    public void focusEditor() {
        Platform.runLater(codeArea::requestFocus);
    }

    /**
     * Moves the caret to the specified character position in the document.
     *
     * @param position the character position to move to (0-based index)
     */
    public void moveTo(int position) {
        if (position >= 0 && position <= codeArea.getText().length()) {
            Platform.runLater(() -> {
                codeArea.moveTo(position);
                codeArea.requestFollowCaret();
            });
        }
    }

    /**
     * Gets the current caret position in the document.
     *
     * @return the current caret position (0-based character index)
     */
    public int getCaretPosition() {
        return codeArea.getCaretPosition();
    }

    /**
     * Gets the text property for observing content changes.
     *
     * @return the observable text property that can be used for binding or listeners
     */
    public javafx.beans.value.ObservableValue<String> textProperty() {
        return codeArea.textProperty();
    }

    /**
     * Requests focus.
     */
    @Override
    public void requestFocus() {
        Platform.runLater(codeArea::requestFocus);
    }
}
