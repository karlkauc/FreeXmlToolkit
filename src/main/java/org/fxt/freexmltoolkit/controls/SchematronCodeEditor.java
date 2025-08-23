package org.fxt.freexmltoolkit.controls;

import javafx.concurrent.Task;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced code editor specifically designed for Schematron documents.
 * Provides syntax highlighting, auto-completion, and validation features.
 */
public class SchematronCodeEditor extends StackPane {

    private static final Logger logger = LogManager.getLogger(SchematronCodeEditor.class);

    private final CodeArea codeArea;
    private final ExecutorService executor;

    // Validation and highlighting
    private boolean syntaxHighlightingEnabled = true;
    private boolean validationEnabled = true;

    // Performance optimization settings
    private static final int LARGE_FILE_THRESHOLD = 50000; // 50KB threshold
    private static final int SYNTAX_HIGHLIGHTING_CHUNK_SIZE = 10000; // Process in 10KB chunks
    private boolean isLargeFile = false;
    private long lastHighlightingTime = 0;

    public SchematronCodeEditor() {
        // Initialize executor for background tasks
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SchematronCodeEditor-Worker");
            return t;
        });

        // Initialize CodeArea
        codeArea = new CodeArea();
        setupCodeArea();

        // Add to layout
        this.getChildren().add(codeArea);

        logger.info("SchematronCodeEditor initialized successfully");
    }

    /**
     * Set up the code area with Schematron-specific features
     */
    private void setupCodeArea() {
        // Add line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        // Set up syntax highlighting
        setupSyntaxHighlighting();

        // Set up auto-indentation
        setupAutoIndentation();

        // Apply CSS styling
        codeArea.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());
        codeArea.getStyleClass().add("code-area");

        // Set font
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 12px;");

        logger.debug("CodeArea setup completed");
    }

    /**
     * Set up syntax highlighting with live updates and performance optimization
     */
    private void setupSyntaxHighlighting() {
        if (!syntaxHighlightingEnabled) {
            return;
        }

        // Apply syntax highlighting on text changes with adaptive debouncing
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(isLargeFile ? 1000 : 250)) // Longer delay for large files
                .retainLatestUntilLater(executor)
                .supplyTask(() -> computeHighlightingAsync(codeArea.getText()))
                .awaitLatest(codeArea.multiPlainChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) {
                        return java.util.Optional.of(t.get());
                    } else {
                        logger.warn("Syntax highlighting failed", t.getFailure());
                        return java.util.Optional.empty();
                    }
                })
                .subscribe(highlighting -> {
                    if (highlighting != null) {
                        codeArea.setStyleSpans(0, highlighting);
                        lastHighlightingTime = System.currentTimeMillis();
                    }
                });

        logger.debug("Syntax highlighting enabled with performance optimization");
    }

    /**
     * Compute syntax highlighting asynchronously with performance optimization
     */
    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync(String text) {
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                long startTime = System.currentTimeMillis();

                // Check if file is large and optimize processing
                if (text.length() > LARGE_FILE_THRESHOLD) {
                    isLargeFile = true;
                    logger.debug("Processing large file ({} chars) with chunked highlighting", text.length());
                    return computeChunkedHighlighting(text);
                } else {
                    isLargeFile = false;
                    StyleSpans<Collection<String>> result = SchematronSyntaxHighlighter.computeHighlighting(text);
                    long endTime = System.currentTimeMillis();
                    logger.debug("Syntax highlighting completed in {}ms for {} chars",
                            endTime - startTime, text.length());
                    return result;
                }
            }
        };
        executor.execute(task);
        return task;
    }

    /**
     * Compute syntax highlighting in chunks for large files
     */
    private StyleSpans<Collection<String>> computeChunkedHighlighting(String text) {
        if (text.length() <= SYNTAX_HIGHLIGHTING_CHUNK_SIZE) {
            return SchematronSyntaxHighlighter.computeHighlighting(text);
        }

        // For very large files, only highlight visible portion + buffer
        int visibleStart = Math.max(0, (int) codeArea.getEstimatedScrollY() - 1000);
        int visibleEnd = Math.min(text.length(), visibleStart + SYNTAX_HIGHLIGHTING_CHUNK_SIZE);

        // Adjust to word boundaries to avoid breaking XML tags
        visibleStart = findSafeBreakpoint(text, visibleStart, false);
        visibleEnd = findSafeBreakpoint(text, visibleEnd, true);

        String visibleText = text.substring(visibleStart, visibleEnd);
        StyleSpans<Collection<String>> highlighting = SchematronSyntaxHighlighter.computeHighlighting(visibleText);

        // Create a full highlighting span with the visible portion highlighted
        if (visibleStart > 0 || visibleEnd < text.length()) {
            // For now, return basic highlighting for the entire document
            // In a full implementation, this would merge highlighted and unhighlighted spans
            return SchematronSyntaxHighlighter.computeHighlighting(text);
        }

        return highlighting;
    }

    /**
     * Find safe breakpoint for chunked processing (avoid breaking XML tags)
     */
    private int findSafeBreakpoint(String text, int position, boolean forward) {
        if (position <= 0) return 0;
        if (position >= text.length()) return text.length();

        int searchRange = Math.min(100, forward ? text.length() - position : position);

        if (forward) {
            // Search forward for end of tag or whitespace
            for (int i = 0; i < searchRange; i++) {
                char c = text.charAt(position + i);
                if (c == '>' || Character.isWhitespace(c)) {
                    return position + i + 1;
                }
            }
        } else {
            // Search backward for start of tag or whitespace
            for (int i = 0; i < searchRange; i++) {
                char c = text.charAt(position - i);
                if (c == '<' || Character.isWhitespace(c)) {
                    return position - i;
                }
            }
        }

        return position; // Fallback to original position
    }

    /**
     * Set up auto-indentation for XML/Schematron
     */
    private void setupAutoIndentation() {
        codeArea.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> handleEnterKey();
                case TAB -> handleTabKey();
            }
        });
    }

    /**
     * Handle Enter key for auto-indentation
     */
    private void handleEnterKey() {
        int caretPosition = codeArea.getCaretPosition();
        int paragraph = codeArea.getCurrentParagraph();

        // Get current line text
        String currentLine = codeArea.getParagraph(paragraph).getText();

        // Calculate indentation
        String indentation = getIndentation(currentLine);

        // Check if we need extra indentation (after opening tag)
        if (currentLine.trim().endsWith(">") && !currentLine.trim().endsWith("/>") && !currentLine.trim().startsWith("</")) {
            indentation += "    "; // Add 4 spaces for nested elements
        }

        // Insert new line with indentation
        codeArea.insertText(caretPosition, "\n" + indentation);
    }

    /**
     * Handle Tab key for indentation
     */
    private void handleTabKey() {
        int caretPosition = codeArea.getCaretPosition();
        codeArea.insertText(caretPosition, "    "); // 4 spaces
    }

    /**
     * Extract indentation from a line
     */
    private String getIndentation(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    /**
     * Validate the Schematron content
     */
    public void validateContent() {
        if (!validationEnabled) {
            return;
        }

        String text = codeArea.getText();
        if (text.trim().isEmpty()) {
            return;
        }

        // Run validation asynchronously
        Task<SchematronSyntaxHighlighter.SchematronValidationResult> validationTask =
                new Task<SchematronSyntaxHighlighter.SchematronValidationResult>() {
                    @Override
                    protected SchematronSyntaxHighlighter.SchematronValidationResult call() throws Exception {
                        return SchematronSyntaxHighlighter.validateStructure(text);
                    }
                };

        validationTask.setOnSucceeded(e -> {
            SchematronSyntaxHighlighter.SchematronValidationResult result = validationTask.getValue();
            handleValidationResult(result);
        });

        validationTask.setOnFailed(e -> {
            logger.error("Validation failed", validationTask.getException());
        });

        executor.execute(validationTask);
    }

    /**
     * Handle validation results
     */
    private void handleValidationResult(SchematronSyntaxHighlighter.SchematronValidationResult result) {
        // Clear previous error highlighting
        // TODO: Implement error highlighting in future enhancement

        // Log validation results
        if (result.hasErrors()) {
            logger.debug("Validation errors found: {}", result.getErrors());
        }
        if (result.hasWarnings()) {
            logger.debug("Validation warnings found: {}", result.getWarnings());
        }

        // TODO: Show validation results in UI (status bar, error markers, etc.)
    }

    /**
     * Insert text at current cursor position
     */
    public void insertTextAtCursor(String text) {
        int caretPosition = codeArea.getCaretPosition();
        codeArea.insertText(caretPosition, text);

        // Move cursor to end of inserted text
        codeArea.moveTo(caretPosition + text.length());
    }

    /**
     * Insert template with proper indentation
     */
    public void insertTemplate(String template) {
        int caretPosition = codeArea.getCaretPosition();
        int paragraph = codeArea.getCurrentParagraph();

        // Get current line indentation
        String currentLine = codeArea.getParagraph(paragraph).getText();
        String indentation = getIndentation(currentLine);

        // Apply indentation to template
        String indentedTemplate = applyIndentation(template, indentation);

        codeArea.insertText(caretPosition, indentedTemplate);

        // Move cursor appropriately
        codeArea.moveTo(caretPosition + indentedTemplate.length());
    }

    /**
     * Apply indentation to multi-line template
     */
    private String applyIndentation(String template, String indentation) {
        String[] lines = template.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                result.append(lines[i]); // First line doesn't need extra indentation
            } else {
                result.append("\n").append(indentation).append(lines[i]);
            }
        }

        return result.toString();
    }

    /**
     * Format the entire document
     */
    public void formatDocument() {
        // TODO: Implement XML/Schematron formatting
        // This could use the existing XML formatting service
        logger.debug("Document formatting requested (not yet implemented)");
    }

    /**
     * Go to specific line
     */
    public void goToLine(int lineNumber) {
        if (lineNumber >= 1 && lineNumber <= codeArea.getParagraphs().size()) {
            codeArea.moveTo(lineNumber - 1, 0);
            codeArea.requestFocus();

            // Optionally select the entire line
            codeArea.selectLine();
        }
    }

    /**
     * Find and highlight text
     */
    public void findText(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return;
        }

        String content = codeArea.getText();
        int index = content.indexOf(searchText, codeArea.getCaretPosition());

        if (index != -1) {
            codeArea.selectRange(index, index + searchText.length());
            codeArea.requestFocus();
        }
    }

    // ========== Getters and Setters ==========

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }

        // Check if this is a large file
        if (text.length() > LARGE_FILE_THRESHOLD) {
            isLargeFile = true;
            logger.info("Loading large Schematron file ({} chars) - performance mode enabled", text.length());

            // Disable syntax highlighting temporarily for very large files
            boolean originalHighlightingState = syntaxHighlightingEnabled;
            if (text.length() > LARGE_FILE_THRESHOLD * 5) { // 250KB+
                syntaxHighlightingEnabled = false;
                logger.info("Syntax highlighting temporarily disabled for very large file");
            }

            // Set text in chunks to prevent UI freezing
            setTextChunked(text);

            // Re-enable highlighting if it was disabled
            if (!syntaxHighlightingEnabled && originalHighlightingState) {
                syntaxHighlightingEnabled = originalHighlightingState;
                setupSyntaxHighlighting();
            }
        } else {
            isLargeFile = false;
            codeArea.clear();
            codeArea.insertText(0, text);
        }
    }

    /**
     * Set text in chunks for large files to prevent UI blocking
     */
    private void setTextChunked(String text) {
        // For very large files, we set the text directly but optimize highlighting
        codeArea.clear();
        codeArea.insertText(0, text);

        // Schedule highlighting for later
        if (syntaxHighlightingEnabled) {
            javafx.application.Platform.runLater(() -> {
                // Delay highlighting to allow UI to settle
                java.util.Timer timer = new java.util.Timer(true);
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        javafx.application.Platform.runLater(() -> {
                            setupSyntaxHighlighting();
                        });
                    }
                }, 500); // 500ms delay
            });
        }
    }

    public void clear() {
        codeArea.clear();
    }

    public boolean isSyntaxHighlightingEnabled() {
        return syntaxHighlightingEnabled;
    }

    public void setSyntaxHighlightingEnabled(boolean enabled) {
        this.syntaxHighlightingEnabled = enabled;
        if (enabled) {
            setupSyntaxHighlighting();
        }
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean enabled) {
        this.validationEnabled = enabled;
    }

    /**
     * Clean up resources
     */
    public void dispose() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        logger.debug("SchematronCodeEditor disposed");
    }
}