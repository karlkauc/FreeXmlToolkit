package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;

/**
 * Status line manager for XmlCodeEditorV2.
 * Clean-room V2 implementation with live updates.
 *
 * <p>Displays:</p>
 * <ul>
 *   <li>Line and column position</li>
 *   <li>Total character count</li>
 *   <li>File encoding</li>
 *   <li>XSD validation status</li>
 *   <li>Dirty state indicator</li>
 * </ul>
 */
public class StatusLineManagerV2 {

    private static final Logger logger = LogManager.getLogger(StatusLineManagerV2.class);

    /**
     * Enumeration for XSD status states.
     * Represents the different states an XSD schema can be in during the editor lifecycle.
     */
    public enum XsdStatus {
        /**
         * No XSD schema is associated with the current document.
         */
        NONE("No XSD", "gray", "⚫"),

        /**
         * XSD schema is currently being loaded.
         */
        LOADING("Loading XSD...", "#e67e00", "⏳"),

        /**
         * XSD schema has been successfully loaded and is available for validation.
         */
        LOADED("XSD: ✓", "green", "✓"),

        /**
         * XSD schema loading failed or the schema contains errors.
         */
        ERROR("XSD: ✗", "red", "✗");

        private final String text;
        private final String color;
        private final String icon;

        XsdStatus(String text, String color, String icon) {
            this.text = text;
            this.color = color;
            this.icon = icon;
        }

        /**
         * Returns the display text for this status.
         *
         * @return the human-readable status text
         */
        public String getText() { return text; }

        /**
         * Returns the color associated with this status.
         *
         * @return the CSS color value for this status
         */
        public String getColor() { return color; }

        /**
         * Returns the icon character for this status.
         *
         * @return the Unicode icon character representing this status
         */
        public String getIcon() { return icon; }
    }

    private final EditorContext editorContext;
    private final CodeArea codeArea;
    private final HBox statusLine;

    // Status line components
    private final Label lineColumnLabel;
    private final Label charCountLabel;
    private final Label encodingLabel;
    private final Label xsdStatusLabel;
    private final Label dirtyLabel;

    // Current XSD status (for external control)
    private XsdStatus currentXsdStatus = XsdStatus.NONE;

    /**
     * Creates a new StatusLineManagerV2.
     *
     * @param editorContext the editor context
     */
    public StatusLineManagerV2(EditorContext editorContext) {
        this.editorContext = editorContext;
        this.codeArea = editorContext.getCodeArea();

        // Create labels
        this.lineColumnLabel = new Label("Ln 1, Col 1");
        this.charCountLabel = new Label("0 chars");
        this.encodingLabel = new Label("UTF-8");
        this.xsdStatusLabel = new Label("No XSD");
        this.dirtyLabel = new Label("");

        // Create status line
        this.statusLine = createStatusLine();

        // Setup listeners
        setupListeners();

        logger.info("StatusLineManagerV2 created");
    }

    /**
     * Creates the status line UI.
     *
     * @return the status line HBox
     */
    private HBox createStatusLine() {
        HBox statusLine = new HBox(10);
        statusLine.setPadding(new Insets(2, 5, 2, 5));
        statusLine.setAlignment(Pos.CENTER_LEFT);
        statusLine.getStyleClass().add("status-line");

        // Style labels
        lineColumnLabel.getStyleClass().add("status-label");
        charCountLabel.getStyleClass().add("status-label");
        encodingLabel.getStyleClass().add("status-label");
        xsdStatusLabel.getStyleClass().add("status-label");
        dirtyLabel.getStyleClass().add("status-label");

        // Spacer to push XSD status to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLine.getChildren().addAll(
                lineColumnLabel,
                createSeparator(),
                charCountLabel,
                createSeparator(),
                encodingLabel,
                spacer,
                xsdStatusLabel,
                dirtyLabel
        );

        return statusLine;
    }

    /**
     * Creates a vertical separator.
     *
     * @return separator label
     */
    private Label createSeparator() {
        Label separator = new Label("|");
        separator.getStyleClass().add("status-separator");
        return separator;
    }

    /**
     * Sets up listeners for live updates.
     */
    private void setupListeners() {
        // Listen to caret position changes
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            updateLineColumn();
        });

        // Listen to text changes for character count
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            updateCharCount();
        });

        // Listen to dirty state changes
        editorContext.addPropertyChangeListener("dirty", evt -> {
            Platform.runLater(this::updateDirtyIndicator);
        });

        // Listen to mode changes
        editorContext.addPropertyChangeListener("currentMode", evt -> {
            Platform.runLater(this::updateXsdStatus);
        });

        // Listen to XSD file changes
        editorContext.addPropertyChangeListener("xsdLoaded", evt -> {
            Platform.runLater(this::updateXsdStatus);
        });

        // Initial update
        updateLineColumn();
        updateCharCount();
        updateDirtyIndicator();
        updateXsdStatus();
    }

    /**
     * Updates the line/column display.
     */
    private void updateLineColumn() {
        int caretPos = codeArea.getCaretPosition();
        int line = codeArea.getCurrentParagraph() + 1; // 1-based
        int column = codeArea.getCaretColumn() + 1; // 1-based

        Platform.runLater(() -> {
            lineColumnLabel.setText(String.format("Ln %d, Col %d", line, column));
        });
    }

    /**
     * Updates the character count display.
     */
    private void updateCharCount() {
        int charCount = codeArea.getText().length();

        Platform.runLater(() -> {
            charCountLabel.setText(String.format("%,d chars", charCount));
        });
    }

    /**
     * Updates the dirty indicator.
     */
    private void updateDirtyIndicator() {
        boolean isDirty = editorContext.isDirty();
        dirtyLabel.setText(isDirty ? "●" : "");
        dirtyLabel.setStyle(isDirty ? "-fx-text-fill: orange;" : "");
    }

    /**
     * Updates the XSD status indicator based on current state.
     * If externally set status is LOADING, keeps that status.
     * Otherwise, checks the schema provider.
     */
    private void updateXsdStatus() {
        // If loading status was set externally, keep it
        if (currentXsdStatus == XsdStatus.LOADING) {
            applyXsdStatus(XsdStatus.LOADING);
            return;
        }

        // Auto-detect from schema provider
        boolean hasSchema = editorContext.getSchemaProvider() != null &&
                            editorContext.getSchemaProvider().hasSchema();

        if (hasSchema) {
            currentXsdStatus = XsdStatus.LOADED;
        } else if (currentXsdStatus != XsdStatus.ERROR) {
            currentXsdStatus = XsdStatus.NONE;
        }

        applyXsdStatus(currentXsdStatus);
    }

    /**
     * Applies the given XSD status to the UI.
     *
     * @param status the status to display
     */
    private void applyXsdStatus(XsdStatus status) {
        xsdStatusLabel.setText(status.getText());
        xsdStatusLabel.setStyle("-fx-text-fill: " + status.getColor() + ";");
        logger.debug("XSD status updated to: {}", status);
    }

    /**
     * Sets the XSD status to LOADING.
     * Call this when starting to load an XSD schema.
     */
    public void setXsdLoading() {
        currentXsdStatus = XsdStatus.LOADING;
        Platform.runLater(() -> applyXsdStatus(XsdStatus.LOADING));
    }

    /**
     * Sets the XSD status to LOADED.
     * Call this when XSD schema has been successfully loaded.
     */
    public void setXsdLoaded() {
        currentXsdStatus = XsdStatus.LOADED;
        Platform.runLater(() -> applyXsdStatus(XsdStatus.LOADED));
    }

    /**
     * Sets the XSD status to ERROR.
     * Call this when XSD schema loading failed.
     */
    public void setXsdError() {
        currentXsdStatus = XsdStatus.ERROR;
        Platform.runLater(() -> applyXsdStatus(XsdStatus.ERROR));
    }

    /**
     * Sets the XSD status to NONE.
     * Call this when no XSD is associated.
     */
    public void setXsdNone() {
        currentXsdStatus = XsdStatus.NONE;
        Platform.runLater(() -> applyXsdStatus(XsdStatus.NONE));
    }

    /**
     * Gets the current XSD status.
     *
     * @return the current XSD status
     */
    public XsdStatus getXsdStatus() {
        return currentXsdStatus;
    }

    /**
     * Sets the encoding display.
     *
     * @param encoding the encoding name
     */
    public void setEncoding(String encoding) {
        Platform.runLater(() -> {
            encodingLabel.setText(encoding != null ? encoding : "UTF-8");
        });
    }

    /**
     * Gets the status line UI component.
     *
     * @return the status line HBox
     */
    public HBox getStatusLine() {
        return statusLine;
    }

    /**
     * Refreshes the cursor position display.
     */
    public void refreshCursorPosition() {
        updateLineColumn();
    }

    /**
     * Refreshes all status line components.
     */
    public void refresh() {
        updateLineColumn();
        updateCharCount();
        updateDirtyIndicator();
        updateXsdStatus();
    }
}
