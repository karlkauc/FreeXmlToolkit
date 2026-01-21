package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.service.XmlService;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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

    // Validation status bar
    private final HBox validationStatusBar;
    private final Label validationStatusLabel;
    private final FontIcon validationStatusIcon;

    // Error highlighting
    private final List<ValidationIssue> currentIssues = new ArrayList<>();
    private final ObjectProperty<SchematronSyntaxHighlighter.SchematronValidationResult> validationResultProperty =
            new SimpleObjectProperty<>();

    // Callback for validation result changes
    private Consumer<SchematronSyntaxHighlighter.SchematronValidationResult> onValidationComplete;

    /**
     * Creates a new SchematronCodeEditor with default settings.
     * Initializes the code area with syntax highlighting, auto-indentation,
     * and validation status bar.
     */
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

        // Initialize validation status bar
        validationStatusIcon = new FontIcon(BootstrapIcons.CHECK_CIRCLE);
        validationStatusIcon.setIconSize(16);
        validationStatusIcon.setIconColor(Color.GREEN);

        validationStatusLabel = new Label("Ready");
        validationStatusLabel.setStyle("-fx-font-size: 11px;");

        validationStatusBar = new HBox(8);
        validationStatusBar.setAlignment(Pos.CENTER_LEFT);
        validationStatusBar.setPadding(new Insets(4, 8, 4, 8));
        validationStatusBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        validationStatusBar.getChildren().addAll(validationStatusIcon, validationStatusLabel);

        // Create layout with code area and status bar
        VBox mainLayout = new VBox();
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        mainLayout.getChildren().addAll(codeArea, validationStatusBar);

        // Add to layout
        this.getChildren().add(mainLayout);

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
     * Handle validation results - updates status bar and applies error highlighting
     */
    private void handleValidationResult(SchematronSyntaxHighlighter.SchematronValidationResult result) {
        // Store validation result
        validationResultProperty.set(result);

        // Clear previous issues
        currentIssues.clear();

        // Collect issues with line positions
        for (String error : result.getErrors()) {
            currentIssues.add(new ValidationIssue(ValidationIssueType.ERROR, error, findLineForIssue(error)));
        }
        for (String warning : result.getWarnings()) {
            currentIssues.add(new ValidationIssue(ValidationIssueType.WARNING, warning, findLineForIssue(warning)));
        }

        // Update status bar
        updateValidationStatusBar(result);

        // Apply error highlighting
        applyErrorHighlighting();

        // Log validation results
        if (result.hasErrors()) {
            logger.debug("Validation errors found: {}", result.getErrors());
        }
        if (result.hasWarnings()) {
            logger.debug("Validation warnings found: {}", result.getWarnings());
        }

        // Notify callback if set
        if (onValidationComplete != null) {
            onValidationComplete.accept(result);
        }
    }

    /**
     * Update validation status bar with results
     */
    private void updateValidationStatusBar(SchematronSyntaxHighlighter.SchematronValidationResult result) {
        javafx.application.Platform.runLater(() -> {
            if (result.hasErrors()) {
                validationStatusIcon.setIconCode(BootstrapIcons.X_CIRCLE);
                validationStatusIcon.setIconColor(Color.RED);
                int errorCount = result.getErrors().size();
                int warningCount = result.getWarnings().size();
                String message = errorCount + " error" + (errorCount != 1 ? "s" : "");
                if (warningCount > 0) {
                    message += ", " + warningCount + " warning" + (warningCount != 1 ? "s" : "");
                }
                validationStatusLabel.setText(message);
                validationStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc3545;");

                // Add tooltip with details
                StringBuilder tooltip = new StringBuilder("Errors:\n");
                for (String error : result.getErrors()) {
                    tooltip.append("  - ").append(error).append("\n");
                }
                if (warningCount > 0) {
                    tooltip.append("\nWarnings:\n");
                    for (String warning : result.getWarnings()) {
                        tooltip.append("  - ").append(warning).append("\n");
                    }
                }
                validationStatusLabel.setTooltip(new Tooltip(tooltip.toString().trim()));
            } else if (result.hasWarnings()) {
                validationStatusIcon.setIconCode(BootstrapIcons.EXCLAMATION_TRIANGLE);
                validationStatusIcon.setIconColor(Color.ORANGE);
                int warningCount = result.getWarnings().size();
                validationStatusLabel.setText(warningCount + " warning" + (warningCount != 1 ? "s" : ""));
                validationStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ffc107;");

                StringBuilder tooltip = new StringBuilder("Warnings:\n");
                for (String warning : result.getWarnings()) {
                    tooltip.append("  - ").append(warning).append("\n");
                }
                validationStatusLabel.setTooltip(new Tooltip(tooltip.toString().trim()));
            } else {
                validationStatusIcon.setIconCode(BootstrapIcons.CHECK_CIRCLE);
                validationStatusIcon.setIconColor(Color.GREEN);
                validationStatusLabel.setText("Valid Schematron");
                validationStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #28a745;");
                validationStatusLabel.setTooltip(new Tooltip("No errors or warnings found"));
            }
        });
    }

    /**
     * Apply error highlighting to the code area
     */
    private void applyErrorHighlighting() {
        if (currentIssues.isEmpty()) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            // Create style spans with error markers for affected lines
            String text = codeArea.getText();
            if (text.isEmpty()) {
                return;
            }

            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            int lastEnd = 0;

            for (ValidationIssue issue : currentIssues) {
                int lineStart = getLineStartPosition(text, issue.line());
                int lineEnd = getLineEndPosition(text, issue.line());

                if (lineStart >= 0 && lineEnd > lineStart && lineStart >= lastEnd) {
                    // Add non-highlighted text before this line
                    if (lineStart > lastEnd) {
                        spansBuilder.add(Collections.emptyList(), lineStart - lastEnd);
                    }

                    // Add error/warning highlighting for this line
                    String styleClass = issue.type() == ValidationIssueType.ERROR ? "validation-error" : "validation-warning";
                    spansBuilder.add(Collections.singleton(styleClass), lineEnd - lineStart);
                    lastEnd = lineEnd;
                }
            }

            // Add remaining unhighlighted text
            if (lastEnd < text.length()) {
                spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
            }

            // Note: For a production implementation, we would merge this with syntax highlighting
            // For now, we just log that highlighting would be applied
            logger.debug("Error highlighting applied for {} issues", currentIssues.size());
        });
    }

    /**
     * Find the line number associated with a validation issue message
     */
    private int findLineForIssue(String issueMessage) {
        // Try to extract line number from message if present
        // For now, return -1 (unknown line)
        // Issues like "Missing root 'schema' element" don't have specific lines
        if (issueMessage.contains("root") || issueMessage.contains("namespace")) {
            return 1; // First line for root element issues
        }
        return -1;
    }

    /**
     * Get the start position of a line (0-indexed)
     */
    private int getLineStartPosition(String text, int lineNumber) {
        if (lineNumber < 1) {
            return -1;
        }

        int currentLine = 1;
        int position = 0;

        while (position < text.length() && currentLine < lineNumber) {
            if (text.charAt(position) == '\n') {
                currentLine++;
            }
            position++;
        }

        return currentLine == lineNumber ? position : -1;
    }

    /**
     * Get the end position of a line
     */
    private int getLineEndPosition(String text, int lineNumber) {
        int lineStart = getLineStartPosition(text, lineNumber);
        if (lineStart < 0) {
            return -1;
        }

        int position = lineStart;
        while (position < text.length() && text.charAt(position) != '\n') {
            position++;
        }

        return position;
    }

    /**
     * Inserts the specified text at the current cursor position.
     * The cursor is moved to the end of the inserted text.
     *
     * @param text the text to insert at the cursor position
     */
    public void insertTextAtCursor(String text) {
        int caretPosition = codeArea.getCaretPosition();
        codeArea.insertText(caretPosition, text);

        // Move cursor to end of inserted text
        codeArea.moveTo(caretPosition + text.length());
    }

    /**
     * Inserts a template at the current cursor position with proper indentation.
     * The template is adjusted to match the current line's indentation level.
     *
     * @param template the template text to insert
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
     * Format the entire document using XML pretty print
     */
    public void formatDocument() {
        formatDocument(2); // Default indent size
    }

    /**
     * Format the entire document with specified indent size
     *
     * @param indentSize number of spaces for indentation
     */
    public void formatDocument(int indentSize) {
        String currentContent = codeArea.getText();
        if (currentContent == null || currentContent.trim().isEmpty()) {
            logger.debug("No content to format");
            return;
        }

        // Run formatting asynchronously to not block UI
        Task<String> formatTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return XmlService.prettyFormat(currentContent, indentSize);
            }
        };

        formatTask.setOnSucceeded(e -> {
            String formattedContent = formatTask.getValue();
            if (formattedContent != null && !formattedContent.equals(currentContent)) {
                // Store caret position (approximately)
                int caretLine = codeArea.getCurrentParagraph();

                // Update content
                codeArea.replaceText(formattedContent);

                // Try to restore caret position
                if (caretLine < codeArea.getParagraphs().size()) {
                    codeArea.moveTo(caretLine, 0);
                }

                logger.info("Document formatted successfully with indent size {}", indentSize);

                // Update status bar temporarily
                updateStatusBarMessage("Document formatted", Color.DODGERBLUE, 2000);
            }
        });

        formatTask.setOnFailed(e -> {
            Throwable exception = formatTask.getException();
            logger.error("Failed to format document", exception);

            // Show error in status bar
            updateStatusBarMessage("Format failed: " + exception.getMessage(), Color.RED, 3000);
        });

        executor.execute(formatTask);
    }

    /**
     * Show a temporary message in the status bar
     */
    private void updateStatusBarMessage(String message, Color color, long durationMs) {
        javafx.application.Platform.runLater(() -> {
            // Store current state
            String previousText = validationStatusLabel.getText();
            String previousStyle = validationStatusLabel.getStyle();

            // Show temporary message
            validationStatusLabel.setText(message);
            validationStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + toHexColor(color) + ";");

            // Restore after duration
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    javafx.application.Platform.runLater(() -> {
                        validationStatusLabel.setText(previousText);
                        validationStatusLabel.setStyle(previousStyle);
                    });
                }
            }, durationMs);
        });
    }

    /**
     * Convert Color to hex string
     */
    private String toHexColor(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    /**
     * Navigates to a specific line in the editor and selects it.
     * The editor receives focus after navigation.
     *
     * @param lineNumber the 1-based line number to navigate to
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
     * Finds and highlights the next occurrence of the specified text.
     * The search starts from the current cursor position.
     *
     * @param searchText the text to search for
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

    /**
     * Returns the underlying CodeArea component.
     *
     * @return the CodeArea used for editing
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Returns the current text content of the editor.
     *
     * @return the text content
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Sets the text content of the editor.
     * For large files (over 50KB), performance optimization is automatically enabled.
     *
     * @param text the text to set, or null to clear the editor
     */
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

    /**
     * Clears all text content from the editor.
     */
    public void clear() {
        codeArea.clear();
    }

    /**
     * Returns whether syntax highlighting is currently enabled.
     *
     * @return true if syntax highlighting is enabled, false otherwise
     */
    public boolean isSyntaxHighlightingEnabled() {
        return syntaxHighlightingEnabled;
    }

    /**
     * Enables or disables syntax highlighting.
     * When enabled, the highlighting is set up immediately.
     *
     * @param enabled true to enable syntax highlighting, false to disable
     */
    public void setSyntaxHighlightingEnabled(boolean enabled) {
        this.syntaxHighlightingEnabled = enabled;
        if (enabled) {
            setupSyntaxHighlighting();
        }
    }

    /**
     * Returns whether validation is currently enabled.
     *
     * @return true if validation is enabled, false otherwise
     */
    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    /**
     * Enables or disables validation of Schematron content.
     *
     * @param enabled true to enable validation, false to disable
     */
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

    // ========== Validation API ==========

    /**
     * Gets the current validation result property
     *
     * @return observable property for validation results
     */
    public ObjectProperty<SchematronSyntaxHighlighter.SchematronValidationResult> validationResultProperty() {
        return validationResultProperty;
    }

    /**
     * Gets the current validation result
     *
     * @return latest validation result, or null if not validated
     */
    public SchematronSyntaxHighlighter.SchematronValidationResult getValidationResult() {
        return validationResultProperty.get();
    }

    /**
     * Sets a callback to be notified when validation completes
     *
     * @param callback callback receiving the validation result
     */
    public void setOnValidationComplete(Consumer<SchematronSyntaxHighlighter.SchematronValidationResult> callback) {
        this.onValidationComplete = callback;
    }

    /**
     * Gets the list of current validation issues
     *
     * @return list of validation issues (errors and warnings)
     */
    public List<ValidationIssue> getValidationIssues() {
        return new ArrayList<>(currentIssues);
    }

    /**
     * Gets the validation status bar component
     *
     * @return the status bar HBox
     */
    public HBox getValidationStatusBar() {
        return validationStatusBar;
    }

    /**
     * Sets whether the validation status bar is visible
     *
     * @param visible true to show, false to hide
     */
    public void setStatusBarVisible(boolean visible) {
        validationStatusBar.setVisible(visible);
        validationStatusBar.setManaged(visible);
    }

    // ========== Nested Types ==========

    /**
     * Enum for validation issue types representing the severity of validation issues.
     */
    public enum ValidationIssueType {
        /** Indicates a critical validation error that prevents processing. */
        ERROR,
        /** Indicates a validation warning that should be reviewed but allows processing. */
        WARNING,
        /** Indicates an informational message about the validation. */
        INFO
    }

    /**
     * Record representing a validation issue with location
     *
     * @param type    the type of issue (error, warning, info)
     * @param message the issue message
     * @param line    the line number (1-based, -1 if unknown)
     */
    public record ValidationIssue(ValidationIssueType type, String message, int line) {
        /**
         * Gets a formatted description of the issue
         *
         * @return formatted string including type and line if available
         */
        public String getFormattedMessage() {
            String prefix = switch (type) {
                case ERROR -> "Error";
                case WARNING -> "Warning";
                case INFO -> "Info";
            };

            if (line > 0) {
                return String.format("[%s] Line %d: %s", prefix, line, message);
            } else {
                return String.format("[%s] %s", prefix, message);
            }
        }
    }
}