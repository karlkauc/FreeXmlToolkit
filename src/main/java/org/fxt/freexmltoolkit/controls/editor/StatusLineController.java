package org.fxt.freexmltoolkit.controls.editor;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Controls the status line display for the XML code editor.
 * This class manages cursor position, encoding, line separator, and indentation information.
 */
public class StatusLineController {

    private static final Logger logger = LogManager.getLogger(StatusLineController.class);

    private final CodeArea codeArea;
    private final PropertiesService propertiesService;

    // Status line components
    private final HBox statusLine = new HBox();
    private final Label cursorPositionLabel = new Label("Line: 1, Column: 1");
    private final Label encodingLabel = new Label("UTF-8");
    private final Label lineSeparatorLabel = new Label("LF");
    private final Label indentationLabel = new Label();
    private final Label documentInfoLabel = new Label("Ready");
    private final Label xsdParsingStatusLabel = new Label();

    // File properties for status line
    private String currentEncoding = "UTF-8";
    private String currentLineSeparator = "LF";
    private int currentIndentationSize;
    private boolean useSpaces = true;

    // XSD parsing status
    private XsdParsingStatus xsdParsingStatus = XsdParsingStatus.NOT_STARTED;

    /**
     * Enumeration for XSD parsing status states.
     */
    public enum XsdParsingStatus {
        NOT_STARTED("⚫", "No XSD"),
        PARSING("🔄", "Parsing XSD..."),
        COMPLETED("✅", "XSD Ready"),
        ERROR("❌", "XSD Error");

        private final String icon;
        private final String text;

        XsdParsingStatus(String icon, String text) {
            this.icon = icon;
            this.text = text;
        }

        public String getIcon() {
            return icon;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * Constructor for StatusLineController.
     *
     * @param codeArea          The CodeArea to monitor for status updates
     * @param propertiesService Service for accessing application properties
     */
    public StatusLineController(CodeArea codeArea, PropertiesService propertiesService) {
        this.codeArea = codeArea;
        this.propertiesService = propertiesService;
        this.currentIndentationSize = propertiesService.getXmlIndentSpaces();

        initializeStatusLine();
        setupEventHandlers();
        updateIndentationLabel();
    }

    /**
     * Initializes the status line with all components.
     */
    private void initializeStatusLine() {
        // Set modern XMLSpy-inspired styling for status line
        statusLine.getStyleClass().add("status-line");
        statusLine.setAlignment(Pos.CENTER_LEFT);
        statusLine.setPadding(new Insets(4, 8, 4, 8));
        statusLine.setSpacing(0);

        // Apply modern styling with XMLSpy color scheme
        statusLine.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f5f5f5 0%, #e8e8e8 100%);" +
                        "-fx-border-color: #c0c0c0;" +
                        "-fx-border-width: 1 0 0 0;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 1, 0, 0, -1);"
        );

        // Create spacer for pushing right-aligned items
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Add all status components with separators
        statusLine.getChildren().addAll(
                documentInfoLabel,
                createSeparator(),
                xsdParsingStatusLabel,
                createSeparator(),
                cursorPositionLabel,
                createSeparator(),
                encodingLabel,
                createSeparator(),
                lineSeparatorLabel,
                createSeparator(),
                indentationLabel,
                spacer
        );

        // Apply consistent styling to all labels
        setupLabelStyling(documentInfoLabel, "📄");
        setupLabelStyling(xsdParsingStatusLabel, "⚫");
        setupLabelStyling(cursorPositionLabel, "🧭");
        setupLabelStyling(encodingLabel, "💾");
        setupLabelStyling(lineSeparatorLabel, "⏎");
        setupLabelStyling(indentationLabel, "↹");

        // Initialize XSD parsing status
        updateXsdParsingStatus(XsdParsingStatus.NOT_STARTED);

        logger.debug("Modern status line initialized with XMLSpy-inspired styling");
    }

    /**
     * Sets up event handlers for status line updates.
     */
    private void setupEventHandlers() {
        // Update cursor position when caret moves
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            updateCursorPosition(newPos.intValue());
        });

        // Update cursor position when selection changes
        codeArea.selectionProperty().addListener((obs, oldSelection, newSelection) -> {
            updateCursorPosition(codeArea.getCaretPosition());
        });

        // Update document info when text changes
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            updateDocumentInfo(newText);
        });

        logger.debug("Status line event handlers set up");
    }

    /**
     * Updates the cursor position display.
     *
     * @param caretPosition The current caret position
     */
    private void updateCursorPosition(int caretPosition) {
        try {
            if (codeArea.getText() == null || codeArea.getText().isEmpty()) {
                cursorPositionLabel.setText("🧭 Ln 1, Col 1");
                return;
            }

            // Convert caret position to line and column
            var position = codeArea.offsetToPosition(caretPosition, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward);
            int line = position.getMajor() + 1; // Convert to 1-based
            int column = position.getMinor() + 1; // Convert to 1-based

            // Check if there's a selection
            String selectionInfo = "";
            if (codeArea.getSelection().getLength() > 0) {
                int selectionLength = codeArea.getSelection().getLength();
                selectionInfo = " (" + selectionLength + " selected)";
            }

            cursorPositionLabel.setText("🧭 Ln " + line + ", Col " + column + selectionInfo);

        } catch (Exception e) {
            logger.debug("Error updating cursor position: {}", e.getMessage());
            cursorPositionLabel.setText("🧭 Ln ?, Col ?");
        }
    }

    /**
     * Updates the indentation label to show the current configured indent spaces.
     */
    private void updateIndentationLabel() {
        String indentType = useSpaces ? "spaces" : "tabs";
        indentationLabel.setText("↹ " + currentIndentationSize + " " + indentType);
    }

    /**
     * Refreshes the indentation display in the status line.
     * Call this method when the indent settings have been changed.
     */
    public void refreshIndentationDisplay() {
        updateIndentationLabel();
        logger.debug("Indentation display refreshed");
    }

    /**
     * Updates the encoding display.
     *
     * @param encoding The current file encoding
     */
    public void setEncoding(String encoding) {
        this.currentEncoding = encoding != null ? encoding : "UTF-8";
        encodingLabel.setText("💾 " + this.currentEncoding);
        logger.debug("Encoding updated to: {}", this.currentEncoding);
    }

    /**
     * Gets the current encoding.
     *
     * @return The current encoding
     */
    public String getEncoding() {
        return currentEncoding;
    }

    /**
     * Updates the line separator display.
     *
     * @param lineSeparator The line separator type (LF, CRLF, CR)
     */
    public void setLineSeparator(String lineSeparator) {
        this.currentLineSeparator = lineSeparator != null ? lineSeparator : "LF";
        lineSeparatorLabel.setText("⏎ " + this.currentLineSeparator);
        logger.debug("Line separator updated to: {}", this.currentLineSeparator);
    }

    /**
     * Gets the current line separator.
     *
     * @return The current line separator
     */
    public String getLineSeparator() {
        return currentLineSeparator;
    }

    /**
     * Sets whether to use spaces or tabs for indentation.
     *
     * @param useSpaces true to use spaces, false to use tabs
     */
    public void setUseSpaces(boolean useSpaces) {
        this.useSpaces = useSpaces;
        updateIndentationLabel();
        logger.debug("Indentation type changed to: {}", useSpaces ? "spaces" : "tabs");
    }

    /**
     * Gets whether spaces are used for indentation.
     *
     * @return true if spaces are used, false if tabs are used
     */
    public boolean isUseSpaces() {
        return useSpaces;
    }

    /**
     * Sets the indentation size.
     *
     * @param indentationSize The number of spaces/tabs for indentation
     */
    public void setIndentationSize(int indentationSize) {
        this.currentIndentationSize = Math.max(1, indentationSize);
        updateIndentationLabel();
        logger.debug("Indentation size changed to: {}", this.currentIndentationSize);
    }

    /**
     * Gets the current indentation size.
     *
     * @return The indentation size
     */
    public int getIndentationSize() {
        return currentIndentationSize;
    }

    /**
     * Gets the status line HBox component.
     *
     * @return The status line component
     */
    public HBox getStatusLine() {
        return statusLine;
    }

    /**
     * Updates all status line information based on the current file properties.
     *
     * @param encoding      The file encoding
     * @param lineSeparator The line separator type
     * @param indentSize    The indentation size
     * @param useSpaces     Whether to use spaces for indentation
     */
    public void updateAllStatus(String encoding, String lineSeparator, int indentSize, boolean useSpaces) {
        setEncoding(encoding);
        setLineSeparator(lineSeparator);
        setIndentationSize(indentSize);
        setUseSpaces(useSpaces);

        // Manually update cursor position
        updateCursorPosition(codeArea.getCaretPosition());

        logger.debug("All status information updated");
    }

    /**
     * Resets the status line to default values.
     */
    public void resetToDefaults() {
        setEncoding("UTF-8");
        setLineSeparator("LF");
        setIndentationSize(propertiesService.getXmlIndentSpaces());
        setUseSpaces(true);
        updateCursorPosition(0);
        logger.debug("Status line reset to defaults");
    }

    /**
     * Shows or hides the status line.
     *
     * @param visible true to show, false to hide
     */
    public void setVisible(boolean visible) {
        statusLine.setVisible(visible);
        statusLine.setManaged(visible);
        logger.debug("Status line visibility set to: {}", visible);
    }

    /**
     * Checks if the status line is currently visible.
     *
     * @return true if visible, false if hidden
     */
    public boolean isVisible() {
        return statusLine.isVisible();
    }

    /**
     * Sets custom text for the cursor position label.
     * This can be used to show custom status information.
     *
     * @param text The text to display
     */
    public void setCursorPositionText(String text) {
        cursorPositionLabel.setText(text);
    }

    /**
     * Forces an update of the cursor position display.
     */
    public void refreshCursorPosition() {
        updateCursorPosition(codeArea.getCaretPosition());
    }

    /**
     * Creates a modern separator for status line components.
     */
    private Separator createSeparator() {
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        separator.setPrefHeight(16);
        separator.setStyle(
                "-fx-background-color: #c0c0c0;" +
                        "-fx-border-color: #c0c0c0;" +
                        "-fx-padding: 2 4 2 4;"
        );
        return separator;
    }

    /**
     * Sets up modern XMLSpy-inspired styling for status labels.
     */
    private void setupLabelStyling(Label label, String icon) {
        label.getStyleClass().add("status-label");
        label.setAlignment(Pos.CENTER_LEFT);
        label.setPadding(new Insets(2, 8, 2, 8));

        // Apply XMLSpy-inspired styling with icons and fixed border to prevent size changes
        label.setStyle(
                "-fx-text-fill: #333333;" +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                        "-fx-font-size: 11px;" +
                        "-fx-background-color: transparent;" +
                        "-fx-border-radius: 3px;" +
                        "-fx-background-radius: 3px;" +
                        "-fx-border-color: transparent;" +
                        "-fx-border-width: 1px;"
        );

        // Add hover effect with consistent sizing
        label.setOnMouseEntered(e -> {
            label.setStyle(
                    "-fx-text-fill: #333333;" +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                            "-fx-font-size: 11px;" +
                            "-fx-background-color: rgba(74, 144, 226, 0.1);" +
                            "-fx-border-radius: 3px;" +
                            "-fx-background-radius: 3px;" +
                            "-fx-border-color: #4a90e2;" +
                            "-fx-border-width: 1px;"
            );
        });

        label.setOnMouseExited(e -> {
            label.setStyle(
                    "-fx-text-fill: #333333;" +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                            "-fx-font-size: 11px;" +
                            "-fx-background-color: transparent;" +
                            "-fx-border-radius: 3px;" +
                            "-fx-background-radius: 3px;" +
                            "-fx-border-color: transparent;" +
                            "-fx-border-width: 1px;"
            );
        });

        // Set initial text with icon
        if (label == documentInfoLabel) {
            label.setText(icon + " Ready");
        } else if (label == xsdParsingStatusLabel) {
            label.setText(icon + " No XSD");
        }
    }

    /**
     * Updates document information display.
     */
    private void updateDocumentInfo(String text) {
        if (text == null || text.isEmpty()) {
            documentInfoLabel.setText("📄 Empty Document");
        } else {
            int lineCount = text.split("\n").length;
            int charCount = text.length();
            documentInfoLabel.setText(String.format("📄 %d lines, %d chars", lineCount, charCount));
        }
    }

    /**
     * Sets a custom status message.
     */
    public void setStatusMessage(String message) {
        documentInfoLabel.setText("📄 " + message);
    }

    /**
     * Updates the XSD parsing status display.
     * This method ensures UI updates happen on the JavaFX Application Thread.
     *
     * @param status The current XSD parsing status
     */
    public void updateXsdParsingStatus(XsdParsingStatus status) {
        this.xsdParsingStatus = status;

        // Ensure UI updates happen on JavaFX Application Thread
        Platform.runLater(() -> {
            xsdParsingStatusLabel.setText(status.getIcon() + " " + status.getText());

            // Apply special styling for parsing state (animated effect)
            if (status == XsdParsingStatus.PARSING) {
                // Add a subtle animation or styling for parsing state
                xsdParsingStatusLabel.getStyleClass().add("parsing-status");
                logger.debug("XSD parsing status updated to: PARSING");
            } else {
                xsdParsingStatusLabel.getStyleClass().remove("parsing-status");
                logger.debug("XSD parsing status updated to: {}", status);
            }
        });
    }

    /**
     * Gets the current XSD parsing status.
     *
     * @return The current XSD parsing status
     */
    public XsdParsingStatus getXsdParsingStatus() {
        return xsdParsingStatus;
    }

    /**
     * Sets XSD parsing status to PARSING state.
     */
    public void setXsdParsingStarted() {
        updateXsdParsingStatus(XsdParsingStatus.PARSING);
    }

    /**
     * Sets XSD parsing status to COMPLETED state.
     */
    public void setXsdParsingCompleted() {
        updateXsdParsingStatus(XsdParsingStatus.COMPLETED);
    }

    /**
     * Sets XSD parsing status to ERROR state.
     */
    public void setXsdParsingError() {
        updateXsdParsingStatus(XsdParsingStatus.ERROR);
    }

    /**
     * Sets XSD parsing status to NOT_STARTED state.
     */
    public void setXsdParsingNotStarted() {
        updateXsdParsingStatus(XsdParsingStatus.NOT_STARTED);
    }
}