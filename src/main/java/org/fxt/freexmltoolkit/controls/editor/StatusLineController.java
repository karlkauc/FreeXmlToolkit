package org.fxt.freexmltoolkit.controls.editor;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
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

    // File properties for status line
    private String currentEncoding = "UTF-8";
    private String currentLineSeparator = "LF";
    private int currentIndentationSize;
    private boolean useSpaces = true;

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
        // Set initial spacing and styling for status line
        statusLine.setSpacing(20);
        statusLine.getStyleClass().add("status-line");

        // Add all status components
        statusLine.getChildren().addAll(
                cursorPositionLabel,
                encodingLabel,
                lineSeparatorLabel,
                indentationLabel
        );

        // Apply styling to labels
        cursorPositionLabel.getStyleClass().add("status-label");
        encodingLabel.getStyleClass().add("status-label");
        lineSeparatorLabel.getStyleClass().add("status-label");
        indentationLabel.getStyleClass().add("status-label");

        logger.debug("Status line initialized with cursor position, encoding, line separator, and indentation");
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
                cursorPositionLabel.setText("Line: 1, Column: 1");
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

            cursorPositionLabel.setText("Line: " + line + ", Column: " + column + selectionInfo);

        } catch (Exception e) {
            logger.debug("Error updating cursor position: {}", e.getMessage());
            cursorPositionLabel.setText("Line: ?, Column: ?");
        }
    }

    /**
     * Updates the indentation label to show the current configured indent spaces.
     */
    private void updateIndentationLabel() {
        int indentSpaces = propertiesService.getXmlIndentSpaces();
        currentIndentationSize = indentSpaces;
        String indentType = useSpaces ? "spaces" : "tabs";
        indentationLabel.setText(indentSpaces + " " + indentType);
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
        encodingLabel.setText(this.currentEncoding);
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
        lineSeparatorLabel.setText(this.currentLineSeparator);
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
}