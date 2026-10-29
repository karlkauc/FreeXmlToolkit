package org.fxt.freexmltoolkit.controls.v2.editor;

import java.util.Collection;
import java.util.Objects;

import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEventBus;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.core.ModeDetector;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.IntelliSenseEngine;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.ContextMenuManagerV2;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.EventHandlerManager;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.StatusLineManagerV2;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.SyntaxHighlightManagerV2;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.ValidationManagerV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;

/**
 * XML Code Editor V2 - Completely redesigned architecture.
 *
 * <p>This is a clean-room implementation with:</p>
 * <ul>
 *   <li>Dependency Injection for all components</li>
 *   <li>Manager-based architecture for separation of concerns</li>
 *   <li>Event-driven communication via EditorEventBus</li>
 *   <li>Context-sensitive IntelliSense with provider registry</li>
 *   <li>Testable design with no tight coupling</li>
 * </ul>
 */
public class XmlCodeEditorV2 extends VBox implements org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget {

    private static final Logger logger = LogManager.getLogger(XmlCodeEditorV2.class);

    // Core components
    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final EditorContext editorContext;
    private final EditorEventBus eventBus;

    // Managers
    private final SyntaxHighlightManagerV2 syntaxManager;
    /** Merge point for syntax + validation-error + search-match styling. */
    private final org.fxt.freexmltoolkit.controls.v2.editor.managers.CombinedStyleManager combinedStyles;
    private final FoldingManagerV2 foldingManager;
    private final StatusLineManagerV2 statusLineManager;
    private final EventHandlerManager eventHandlerManager;
    private final ContextMenuManagerV2 contextMenuManager;

    // IntelliSense
    private final IntelliSenseEngine intelliSenseEngine;

    // Folding control - can be disabled for single-line XML files
    private boolean foldingEnabled = true;

    // Optional extra gutter element prepended to the line-number/fold strip.
    // Used by the XSLT debugger to render breakpoint dots and execution markers.
    private java.util.function.IntFunction<javafx.scene.Node> extraGutterFactory;

    // Font size control
    private static final int DEFAULT_FONT_SIZE = 11;
    private static final int MIN_FONT_SIZE = 6;
    private static final int MAX_FONT_SIZE = 72;
    private int fontSize = DEFAULT_FONT_SIZE;

    /**
     * Creates a new XmlCodeEditorV2.
     *
     * @param schemaProvider the XSD schema provider
     */
    public XmlCodeEditorV2(XmlSchemaProvider schemaProvider) {
        super();
        Objects.requireNonNull(schemaProvider, "SchemaProvider cannot be null");

        logger.info("Creating XmlCodeEditorV2...");

        // Create core components
        this.codeArea = new PlainTextCodeArea();
        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.eventBus = new EditorEventBus();
        this.editorContext = new EditorContext(codeArea, eventBus, schemaProvider);

        // Create managers
        this.syntaxManager = new SyntaxHighlightManagerV2(codeArea);
        // One merge point for syntax + validation-error + search-match styling,
        // so the layers stop overwriting each other.
        this.combinedStyles = new org.fxt.freexmltoolkit.controls.v2.editor.managers
                .CombinedStyleManager(codeArea);
        this.syntaxManager.setStyleManager(combinedStyles);
        ValidationManagerV2 validationManager = new ValidationManagerV2(editorContext);
        validationManager.setStyleManager(combinedStyles);
        this.foldingManager = new FoldingManagerV2(codeArea);
        this.statusLineManager = new StatusLineManagerV2(editorContext);
        this.eventHandlerManager = new EventHandlerManager(editorContext);
        this.contextMenuManager = new ContextMenuManagerV2(editorContext, foldingManager);
        this.contextMenuManager.initialize();

        // Create IntelliSense engine
        this.intelliSenseEngine = new IntelliSenseEngine(editorContext);
        this.intelliSenseEngine.initialize();
        this.editorContext.setIntelliSenseEngine(intelliSenseEngine);
        this.eventHandlerManager.setIntelliSenseEngine(intelliSenseEngine);

        // Set font size callbacks
        this.eventHandlerManager.setFontSizeCallbacks(
                this::increaseFontSize,
                this::decreaseFontSize,
                this::resetFontSize
        );

        // Initialize UI
        initializeUI();

        // Setup event handlers
        eventHandlerManager.setupHandlers();

        // Setup text change listeners
        setupTextChangeListeners();

        logger.info("XmlCodeEditorV2 created successfully");
    }

    /**
     * Initializes the UI layout.
     */
    private void initializeUI() {
        // Load CSS stylesheets
        loadStylesheets();

        // Set up code area styling
        codeArea.setStyle("-fx-font-size: 11pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");

        // Add style class to scrollPane so CSS can target its scrollbars
        scrollPane.getStyleClass().add("xml-editor-scroll-pane");

        // Setup combined paragraph graphics (fold indicators + line numbers)
        codeArea.setParagraphGraphicFactory(createCombinedParagraphGraphicFactory());

        // Highlight the caret's line (Figma current-line tint). Deferred to the next pulse: the caret
        // moves in the middle of an edit (undo/redo, replace), and re-styling a paragraph from inside
        // that change re-entered the viewport and threw an NPE in VirtualizedScrollPane.setVPosition.
        codeArea.currentParagraphProperty().addListener((obs, oldP, newP) -> scheduleCurrentLineStyle());
        updateCurrentLineStyle(codeArea.getCurrentParagraph());

        ignoreFlowlessHitRace(codeArea);

        // Layout: scrollpane (with the debugger's inline-value layer on top) + status line
        inlineValues = new InlineValueLayer(codeArea);
        javafx.scene.layout.StackPane editorStack = new javafx.scene.layout.StackPane(scrollPane, inlineValues);
        VBox.setVgrow(editorStack, Priority.ALWAYS);
        getChildren().addAll(editorStack, statusLineManager.getStatusLine());

        logger.debug("UI initialized");
    }

    /**
     * Shows or hides the built-in status line. The Unified Shell hosts its own global status bar
     * (Ln/Col · type · schema · encoding · chars), so it hides this per-editor strip to avoid a
     * duplicated status row in Text view.
     *
     * @param visible {@code true} to show the status line, {@code false} to hide and unmanage it
     */
    public void setStatusLineVisible(boolean visible) {
        javafx.scene.Node statusLine = statusLineManager.getStatusLine();
        statusLine.setVisible(visible);
        statusLine.setManaged(visible);
    }

    /** Paragraph index currently carrying the {@code current-line} style. */
    private int currentLineIndex = -1;
    /** A current-line update is queued for the next pulse (several caret moves collapse into one). */
    private boolean currentLineStyleQueued;

    private void scheduleCurrentLineStyle() {
        if (currentLineStyleQueued) {
            return;
        }
        currentLineStyleQueued = true;
        Platform.runLater(() -> {
            currentLineStyleQueued = false;
            updateCurrentLineStyle(codeArea.getCurrentParagraph());
        });
    }

    /**
     * Drops a mouse event that RichTextFX cannot hit-test. Flowless throws
     * {@code NoSuchElementException} from {@code CellListManager.getPresentCell} when a press or drag
     * lands while the viewport has no laid-out cell at that spot (seen right after large edits); the
     * click is lost either way, so it is ignored and a layout is requested instead of an uncaught error
     * per mouse event. Any other exception propagates unchanged.
     */
    static void ignoreFlowlessHitRace(CodeArea area) {
        javafx.event.EventDispatcher original = area.getEventDispatcher();
        area.setEventDispatcher((event, tail) -> {
            try {
                return original.dispatchEvent(event, tail);
            } catch (java.util.NoSuchElementException e) {
                if (!(event instanceof javafx.scene.input.MouseEvent) || !isFlowlessHitRace(e)) {
                    throw e;
                }
                logger.debug("Ignored {} that hit no laid-out cell", event.getEventType());
                area.requestLayout();
                return null;
            }
        });
    }

    private static boolean isFlowlessHitRace(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            if (frame.getClassName().startsWith("org.fxmisc.flowless.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the {@code current-line} paragraph style to the caret's line so the
     * stylesheet can tint it (Figma current-line highlight). Cheap: it only
     * re-styles the previous and new paragraphs.
     */
    private void updateCurrentLineStyle(int newIndex) {
        int oldIndex = currentLineIndex;
        currentLineIndex = newIndex;
        if (oldIndex != newIndex) {
            restyleParagraph(oldIndex);
        }
        restyleParagraph(newIndex);
    }

    /** The XSLT debugger's inline variable values (shown while paused). */
    private InlineValueLayer inlineValues;

    /**
     * Shows the debugger's values after the end of their declaration lines (1-based line →
     * text); an empty map or {@code null} removes them. Editing the text removes them too.
     */
    public void setDebugInlineValues(java.util.Map<Integer, String> lineValues) {
        inlineValues.setValues(lineValues);
    }

    /** @return the inline values currently shown (1-based line → text). */
    public java.util.Map<Integer, String> getDebugInlineValues() {
        return inlineValues.getValues();
    }

    /** The paragraph (0-based) tinted as the debugger's current execution line, or -1. */
    private int debugLineIndex = -1;
    /** 0-based line of the debugger's context node when this document is the input, or -1. */
    private int debugInputLineIndex = -1;

    /**
     * Marks {@code line1Based} as the line the XSLT debugger is paused at (paragraph style
     * {@code debug-current-line}); {@code -1} or {@link #clearDebugCurrentLine()} clears it.
     * Composed with the caret's {@code current-line} style, so neither clobbers the other.
     */
    public void setDebugCurrentLine(int line1Based) {
        int oldIndex = debugLineIndex;
        debugLineIndex = line1Based > 0 ? line1Based - 1 : -1;
        if (oldIndex != debugLineIndex) {
            restyleParagraph(oldIndex);
        }
        restyleParagraph(debugLineIndex);
    }

    /**
     * Marks the line of the debugger's current context node in an input document (a second,
     * blue tint — distinct from the stylesheet's execution line); {@code <= 0} clears it.
     */
    public void setDebugInputLine(int line1Based) {
        int oldIndex = debugInputLineIndex;
        debugInputLineIndex = line1Based > 0 ? line1Based - 1 : -1;
        if (oldIndex != debugInputLineIndex) {
            restyleParagraph(oldIndex);
        }
        restyleParagraph(debugInputLineIndex);
    }

    /** @return the 1-based input-node line, or 0 (tests). */
    public int getDebugInputLine() {
        return debugInputLineIndex + 1;
    }

    /** Removes the debugger's execution-line tint. */
    public void clearDebugCurrentLine() {
        setDebugCurrentLine(-1);
    }

    /** Re-applies the composed paragraph styles (caret line, debug line) to one paragraph. */
    private void restyleParagraph(int index) {
        if (index < 0 || index >= codeArea.getParagraphs().size()) {
            return;
        }
        java.util.List<String> styles = new java.util.ArrayList<>(2);
        if (index == currentLineIndex) {
            styles.add("current-line");
        }
        if (index == debugLineIndex) {
            styles.add("debug-current-line");
        }
        if (index == debugInputLineIndex) {
            styles.add("debug-input-line");
        }
        codeArea.setParagraphStyle(index, styles);
    }

    /**
     * Creates combined paragraph graphics factory for line numbers and fold indicators.
     *
     * @return the paragraph graphics factory
     */
    private java.util.function.IntFunction<javafx.scene.Node> createCombinedParagraphGraphicFactory() {
        return lineIndex -> {
            javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(3);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // Opaque gutter background so horizontally scrolled text never shows through
            // behind the line numbers. Set inline (highest precedence) to guarantee it paints
            // regardless of stylesheet cascade; keep the .lineno class for semantic styling.
            // The colour is the theme-aware surface-2 token and is re-applied on a theme switch
            // (SemanticStyle keeps a weak registration), so the gutter is dark in dark mode.
            hbox.getStyleClass().add("lineno");
            org.fxt.freexmltoolkit.controls.theme.SemanticStyle.style(hbox,
                    org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BG_SURFACE_2,
                    hex -> "-fx-background-color: " + hex + ";");

            // Optional extra gutter (e.g. breakpoint markers) — first slot
            if (extraGutterFactory != null) {
                javafx.scene.Node extra = extraGutterFactory.apply(lineIndex);
                if (extra != null) {
                    hbox.getChildren().add(extra);
                }
            }

            // Get fold indicator from folding manager
            javafx.scene.Node foldIndicator = foldingManager.createFoldIndicator(lineIndex);
            if (foldIndicator != null) {
                logger.debug("Adding fold indicator for line {}", lineIndex);
                hbox.getChildren().add(foldIndicator);
            } else {
                // Add spacer to maintain alignment
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                spacer.setPrefSize(12, 12);
                hbox.getChildren().add(spacer);
            }

            // Add line number label — the caret's line is indigo + bold (Figma). The color is
            // resolved per evaluation so a theme switch is picked up on the next re-render.
            javafx.scene.control.Label lineNumber = new javafx.scene.control.Label(String.format("%4d", lineIndex + 1));
            lineNumber.styleProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                    () -> codeArea.getCurrentParagraph() == lineIndex
                            ? "-fx-font-family: 'JetBrains Mono', monospace; -fx-text-fill: "
                            + (isDarkTheme() ? "#4c9bf5" : "#1373d9") + "; -fx-font-weight: bold;"
                            : "-fx-font-family: 'JetBrains Mono', monospace; -fx-text-fill: #8a93a0;",
                    codeArea.currentParagraphProperty()));
            hbox.getChildren().add(lineNumber);

            return hbox;
        };
    }

    /**
     * Installs (or removes, when {@code factory} is {@code null}) an extra
     * gutter component prepended to the existing line-number/fold strip. Used
     * by the XSLT debugger to draw breakpoint markers and execution arrows.
     */
    public void setExtraGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        this.extraGutterFactory = factory;
        // Force RichTextFX to re-render all paragraph graphics
        codeArea.setParagraphGraphicFactory(createCombinedParagraphGraphicFactory());
    }

    /**
     * Forces a refresh of paragraph graphics. Call after a breakpoint set
     * change to re-render the gutter without modifying text.
     */
    public void refreshGutter() {
        codeArea.setParagraphGraphicFactory(createCombinedParagraphGraphicFactory());
    }

    /**
     * Loads CSS stylesheets.
     */
    /** @return {@code true} if the persisted UI theme is dark (same key ThemeManager writes). */
    private static boolean isDarkTheme() {
        try {
            return "dark".equals(org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get("ui.theme"));
        } catch (Throwable t) {
            return false;
        }
    }

    private void loadStylesheets() {
        try {
            // NOTE: fxt-theme.css is deliberately NOT attached here. It reaches the editor
            // via the shell's parent stylesheets; a node-level copy would out-rank the
            // dark-theme.css overrides and lock the editor into the light palette.

            // Load XML highlighting CSS
            String xmlCssPath = "/scss/xml-highlighting.css";
            var xmlCssResource = getClass().getResource(xmlCssPath);
            if (xmlCssResource != null) {
                codeArea.getStylesheets().add(xmlCssResource.toExternalForm());
            }

            logger.debug("Stylesheets loaded");
        } catch (Exception e) {
            logger.error("Error loading stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets up text change listeners.
     */
    private void setupTextChangeListeners() {
        // plainTextChanges, not a textProperty ChangeListener: the latter materializes the whole
        // document as a String on every keystroke (twice, with the old value), which cost tens of ms per
        // key on multi-MB files. The highlighter reads the text itself, once, after its debounce.
        codeArea.plainTextChanges().subscribe(change -> {
            if (change.getInserted().equals(change.getRemoved())) {
                return;
            }
            textRevision++;
            editorContext.setDirty(true);
            syntaxManager.requestHighlighting();
        });

        // Caret position listener for status line
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            statusLineManager.refreshCursorPosition();

            // Publish caret moved event
            eventBus.publish(new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.CaretMovedEvent(
                    oldPos.intValue(), newPos.intValue()));
        });

        logger.debug("Text change listeners setup");
    }

    // ==================== Public API ====================

    /**
     * Sets the text content.
     *
     * @param text the text to set
     */
    public void setText(String text) {
        codeArea.replaceText(text);
        afterSetText(text);
    }

    /** From this length on, {@link #prepareText} builds the document off the FX thread. */
    public static final int PREPARE_THRESHOLD_CHARS = 2 * 1024 * 1024;

    /**
     * A document built off the FX thread for {@link #setText(PreparedText)}.
     *
     * @param text     the text
     * @param document the RichTextFX document of {@code text}
     */
    public record PreparedText(String text, StyledDocument<Collection<String>, String, Collection<String>> document) {
    }

    /**
     * Builds the RichTextFX document of a large text: splitting it into paragraphs and building
     * the paragraph tree took seconds on the FX thread for files of tens of MB (telemetry:
     * {@code FingerTree.mkTree} and the line-split regex under {@link #setText(String)}).
     * Safe to call on any thread: it reads only the area's immutable initial styles and
     * segment operations.
     *
     * @return the prepared document, or null when the text is short enough for {@link #setText(String)}
     */
    public PreparedText prepareText(String text) {
        if (text == null || text.length() < PREPARE_THRESHOLD_CHARS) {
            return null;
        }
        return new PreparedText(text, ReadOnlyStyledDocument.fromString(text,
                codeArea.getInitialParagraphStyle(), codeArea.getInitialTextStyle(), codeArea.getSegOps()));
    }

    /** Same as {@link #setText(String)} with a document built by {@link #prepareText}. */
    public void setText(PreparedText prepared) {
        codeArea.replace(0, codeArea.getLength(), prepared.document());
        afterSetText(prepared.text());
    }

    private void afterSetText(String text) {
        // Move cursor to the beginning and scroll to top
        codeArea.moveTo(0);
        codeArea.requestFollowCaret();

        // Auto-detect editor mode based on content
        if (text != null && !text.isEmpty()) {
            EditorMode detectedMode = ModeDetector.detectMode(text, editorContext.hasSchema());
            editorContext.setCurrentMode(detectedMode);
            logger.info("Auto-detected editor mode: {}", detectedMode);

            Platform.runLater(() -> {
                syntaxManager.applySyntaxHighlighting(text);

                // Only update folding if enabled (disabled for single-line XML files to prevent freezing).
                // The regions are computed off the FX thread; applying them refreshes the gutter once.
                if (foldingEnabled) {
                    foldingManager.updateFoldingRegionsAsync(text);
                } else {
                    logger.info("Folding disabled - skipping folding region calculation");
                }
            });
        }
    }

    /**
     * Replaces only the {@code [start, oldEnd)} character region with
     * {@code replacement} instead of the whole document, then refreshes syntax
     * highlighting and folding. Unlike {@link #setText(String)} it does not move the
     * caret, so a caret outside the changed region is preserved (P6 round-trip).
     *
     * @param start       region start offset
     * @param oldEnd      region end offset (exclusive)
     * @param replacement the replacement text for the region
     */
    public void replaceTextRegion(int start, int oldEnd, String replacement) {
        int caret = codeArea.getCaretPosition();
        codeArea.replaceText(start, oldEnd, replacement);

        // replaceText() puts the caret at the end of the insertion; restore the user's
        // caret, shifting it only if the change happened before it.
        int restored;
        if (caret <= start) {
            restored = caret;
        } else if (caret >= oldEnd) {
            restored = caret + (replacement.length() - (oldEnd - start));
        } else {
            restored = start + replacement.length();
        }
        codeArea.moveTo(Math.max(0, Math.min(restored, codeArea.getLength())));

        // Highlighting follows from the text change itself (plainTextChanges → requestHighlighting).
        // Folding regions are recomputed off the FX thread; applying them refreshes the gutter once.
        if (foldingEnabled && codeArea.getLength() > 0) {
            foldingManager.updateFoldingRegionsAsync(codeArea.getText());
        }
    }

    /** Edits seen since creation; compare two values instead of comparing whole document strings. */
    private long textRevision;

    /** @return a counter that changes with every text edit (FX thread) */
    public long getTextRevision() {
        return textRevision;
    }

    /**
     * Gets the current text content.
     *
     * @return the text
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Gets the code area component.
     *
     * @return the code area
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Highlights the given search-match ranges (start offset → length) on top of
     * the syntax highlighting. Pass an empty map or use
     * {@link #clearSearchMatches()} to remove the overlay.
     */
    public void setSearchMatchRanges(java.util.Map<Integer, Integer> ranges) {
        combinedStyles.setMatchRanges(ranges);
    }

    /** Removes the search-match highlight overlay. */
    public void clearSearchMatches() {
        combinedStyles.clearMatches();
    }

    /**
     * Gets the editor context.
     *
     * @return the editor context
     */
    public EditorContext getEditorContext() {
        return editorContext;
    }

    /**
     * Gets the event bus.
     *
     * @return the event bus
     */
    public EditorEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the IntelliSense engine.
     *
     * @return the IntelliSense engine
     */
    public IntelliSenseEngine getIntelliSenseEngine() {
        return intelliSenseEngine;
    }

    /**
     * Sets the editor mode.
     *
     * @param mode the editor mode
     */
    public void setEditorMode(EditorMode mode) {
        editorContext.setCurrentMode(mode);
    }

    /**
     * Gets the current editor mode.
     *
     * @return the editor mode
     */
    public EditorMode getEditorMode() {
        return editorContext.getCurrentMode();
    }

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return editorContext.isDirty();
    }

    /**
     * Sets the dirty flag.
     *
     * @param dirty the dirty state
     */
    public void setDirty(boolean dirty) {
        editorContext.setDirty(dirty);
    }

    /**
     * Sets the document URI.
     *
     * @param uri the document URI
     */
    public void setDocumentUri(String uri) {
        editorContext.setDocumentUri(uri);
    }

    /**
     * Gets the document URI.
     *
     * @return the document URI
     */
    public String getDocumentUri() {
        return editorContext.getDocumentUri();
    }

    /**
     * Refreshes syntax highlighting.
     */
    public void refreshHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            Platform.runLater(() -> syntaxManager.applySyntaxHighlighting(currentText));
        }
    }

    /**
     * Folds all foldable regions in the editor.
     */
    public void foldAll() {
        foldingManager.foldAll();
    }

    /**
     * Unfolds all folded regions in the editor.
     */
    public void unfoldAll() {
        foldingManager.unfoldAll();
    }

    /**
     * Gets the folding manager.
     *
     * @return the folding manager
     */
    public FoldingManagerV2 getFoldingManager() {
        return foldingManager;
    }

    /**
     * Invalidates IntelliSense cache.
     * Call this when XSD schema changes.
     */
    public void invalidateIntelliSenseCache() {
        intelliSenseEngine.invalidateCacheForSchema();
        logger.debug("IntelliSense cache invalidated");
    }

    /**
     * Refreshes the status line display.
     * Call this when XSD or other status information changes.
     */
    public void refreshStatusLine() {
        statusLineManager.refresh();
    }

    // ==================== XSD Status Control ====================

    /**
     * Sets the XSD status to LOADING.
     * Call this when starting to load an XSD schema.
     */
    public void setXsdLoading() {
        statusLineManager.setXsdLoading();
    }

    /**
     * Sets the XSD status to LOADED.
     * Call this when XSD schema has been successfully loaded.
     * Also invalidates the IntelliSense cache so completions are recalculated.
     */
    public void setXsdLoaded() {
        statusLineManager.setXsdLoaded();
        // Invalidate IntelliSense cache so completions are recalculated with the new schema
        if (intelliSenseEngine != null) {
            intelliSenseEngine.invalidateCacheForSchema();
            logger.debug("IntelliSense cache invalidated after XSD load");
        }
    }

    /**
     * Sets the XSD status to ERROR.
     * Call this when XSD schema loading failed.
     */
    public void setXsdError() {
        statusLineManager.setXsdError();
    }

    /**
     * Sets the XSD status to NONE.
     * Call this when no XSD is associated.
     */
    public void setXsdNone() {
        statusLineManager.setXsdNone();
    }

    /**
     * Gets the context menu manager.
     *
     * @return the context menu manager
     */
    public ContextMenuManagerV2 getContextMenuManager() {
        return contextMenuManager;
    }

    /**
     * Sets whether code folding is enabled.
     * Disabling folding is useful for single-line XML files to prevent performance issues.
     *
     * @param enabled true to enable folding, false to disable
     */
    public void setFoldingEnabled(boolean enabled) {
        this.foldingEnabled = enabled;
        logger.info("Folding enabled: {}", enabled);
    }

    /**
     * Checks if code folding is enabled.
     *
     * @return true if folding is enabled
     */
    public boolean isFoldingEnabled() {
        return foldingEnabled;
    }

    // ==================== Font Size Control ====================

    /**
     * Increases the font size by 1 point.
     */
    public void increaseFontSize() {
        if (fontSize < MAX_FONT_SIZE) {
            setFontSize(fontSize + 1);
        }
    }

    /**
     * Decreases the font size by 1 point.
     */
    public void decreaseFontSize() {
        if (fontSize > MIN_FONT_SIZE) {
            setFontSize(fontSize - 1);
        }
    }

    /**
     * Resets the font size to the default value.
     */
    public void resetFontSize() {
        setFontSize(DEFAULT_FONT_SIZE);
    }

    /**
     * Sets the font size.
     *
     * @param size the font size in points
     */
    public void setFontSize(int size) {
        this.fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        codeArea.setStyle("-fx-font-size: " + fontSize + "pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");
        logger.debug("Font size changed to: {}pt", fontSize);
    }

    /**
     * Gets the current font size.
     *
     * @return the font size in points
     */
    public int getFontSize() {
        return fontSize;
    }

    @Override
    public String toString() {
        return "XmlCodeEditorV2{" +
                "mode=" + editorContext.getCurrentMode() +
                ", dirty=" + editorContext.isDirty() +
                ", hasSchema=" + editorContext.hasSchema() +
                '}';
    }

    // ==================== Search & Replace ====================

    private String lastSearchText = "";

    /**
     * Case-insensitive {@code indexOf} on the text itself. Lower-casing the whole document per Find Next
     * cost tens of ms on large files, and its offsets drift wherever lower-casing changes the length
     * (e.g. U+0130), which then selected the wrong range.
     */
    static int indexOfIgnoreCase(String text, String needle, int from) {
        int last = text.length() - needle.length();
        for (int i = Math.max(0, from); i <= last; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /** Case-insensitive {@code lastIndexOf}: the last match starting at or before {@code from}. */
    static int lastIndexOfIgnoreCase(String text, String needle, int from) {
        for (int i = Math.min(from, text.length() - needle.length()); i >= 0; i--) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds text in the editor.
     *
     * @param searchText the text to find
     * @param forward    true to search forward, false to search backward
     * @return true if text was found
     */
    @Override
    public boolean find(String searchText, boolean forward) {
        if (searchText == null || searchText.isEmpty()) {
            return false;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return false;
        }

        // Reset search position if search text changed
        if (!searchText.equals(lastSearchText)) {
            lastSearchText = searchText;
        }

        int currentPos = codeArea.getCaretPosition();
        int foundPos;

        if (forward) {
            // Search forward from current position
            int startPos = currentPos;
            foundPos = indexOfIgnoreCase(content, searchText, startPos);
            // Wrap around if not found
            if (foundPos < 0 && startPos > 0) {
                foundPos = indexOfIgnoreCase(content, searchText, 0);
            }
        } else {
            // Search backward from current position
            int endPos = currentPos > 0 ? currentPos - 1 : content.length();
            foundPos = lastIndexOfIgnoreCase(content, searchText, endPos);
            // Wrap around if not found
            if (foundPos < 0) {
                foundPos = lastIndexOfIgnoreCase(content, searchText, content.length());
            }
        }

        if (foundPos >= 0) {
            codeArea.selectRange(foundPos, foundPos + searchText.length());
            codeArea.requestFollowCaret();
            return true;
        }

        return false;
    }

    /**
     * Counts all occurrences of the search text and selects the first one.
     *
     * @param searchText the text to find
     * @return the number of occurrences found
     */
    @Override
    public int findAll(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return 0;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int count = 0;
        int pos = 0;

        while ((pos = indexOfIgnoreCase(content, searchText, pos)) >= 0) {
            count++;
            pos += searchText.length();
        }

        // Select first occurrence
        if (count > 0) {
            find(searchText, true);
        }

        return count;
    }

    /**
     * Replaces the current selection with new text.
     *
     * @param findText    the text to find
     * @param replaceText the replacement text
     * @return true if replacement was made
     */
    public boolean replace(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) {
            return false;
        }

        String selected = codeArea.getSelectedText();
        if (selected != null && selected.equalsIgnoreCase(findText)) {
            codeArea.replaceSelection(replaceText != null ? replaceText : "");
            return true;
        } else {
            // Find next occurrence first
            if (find(findText, true)) {
                codeArea.replaceSelection(replaceText != null ? replaceText : "");
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces all occurrences of text.
     *
     * @param findText    the text to find
     * @param replaceText the replacement text
     * @return the number of replacements made
     */
    public int replaceAll(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) {
            return 0;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return 0;
        }

        // Count occurrences first
        int count = 0;
        int pos = 0;
        while ((pos = indexOfIgnoreCase(content, findText, pos)) >= 0) {
            count++;
            pos += findText.length();
        }

        if (count > 0) {
            // Perform case-insensitive replace
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            pos = 0;
            while ((pos = indexOfIgnoreCase(content, findText, pos)) >= 0) {
                result.append(content, lastEnd, pos);
                result.append(replaceText != null ? replaceText : "");
                lastEnd = pos + findText.length();
                pos = lastEnd;
            }
            result.append(content.substring(lastEnd));

            int oldCaret = codeArea.getCaretPosition();
            codeArea.replaceText(result.toString());
            int newLen = codeArea.getLength();
            codeArea.moveTo(Math.min(oldCaret, newLen));
        }

        return count;
    }

    /**
     * Clears the search state.
     */
    @Override
    public void clearSearch() {
        lastSearchText = "";
    }
}
