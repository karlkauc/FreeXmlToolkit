package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.IntelliSenseEngine;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * Manages all event handlers for the editor.
 * Centralizes event handling logic.
 */
public class EventHandlerManager {

    private static final Logger logger = LogManager.getLogger(EventHandlerManager.class);

    private final EditorContext editorContext;
    private final CodeArea codeArea;
    private IntelliSenseEngine intelliSenseEngine;
    private javafx.scene.control.Tooltip hoverTooltip;
    private boolean ctrlPressed = false;

    // Font size callbacks
    private Runnable onIncreaseFontSize;
    private Runnable onDecreaseFontSize;
    private Runnable onResetFontSize;

    public EventHandlerManager(EditorContext editorContext) {
        this.editorContext = editorContext;
        this.codeArea = editorContext.getCodeArea();
        logger.info("EventHandlerManager created");
    }

    /**
     * Sets the IntelliSense engine.
     */
    public void setIntelliSenseEngine(IntelliSenseEngine engine) {
        this.intelliSenseEngine = engine;
    }

    /**
     * Sets the font size callbacks for Ctrl+Scroll and keyboard shortcuts.
     *
     * @param onIncrease callback for increasing font size
     * @param onDecrease callback for decreasing font size
     * @param onReset    callback for resetting font size
     */
    public void setFontSizeCallbacks(Runnable onIncrease, Runnable onDecrease, Runnable onReset) {
        this.onIncreaseFontSize = onIncrease;
        this.onDecreaseFontSize = onDecrease;
        this.onResetFontSize = onReset;
        logger.debug("Font size callbacks set");
    }

    /**
     * Sets up all event handlers.
     */
    public void setupHandlers() {
        setupKeyboardHandlers();
        setupMouseHandlers();
        setupScrollHandler();
        setupTextChangeHandlers();
        logger.debug("All event handlers setup");
    }

    /**
     * Sets up keyboard event handlers.
     */
    private void setupKeyboardHandlers() {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Font size keyboard shortcuts (Ctrl+Plus, Ctrl+Minus, Ctrl+0)
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case PLUS, ADD, EQUALS -> {
                        if (onIncreaseFontSize != null) {
                            onIncreaseFontSize.run();
                            event.consume();
                            return;
                        }
                    }
                    case MINUS, SUBTRACT -> {
                        if (onDecreaseFontSize != null) {
                            onDecreaseFontSize.run();
                            event.consume();
                            return;
                        }
                    }
                    case NUMPAD0, DIGIT0 -> {
                        if (onResetFontSize != null) {
                            onResetFontSize.run();
                            event.consume();
                            return;
                        }
                    }
                    default -> {
                        // Continue with IntelliSense handling
                    }
                }
            }

            // IntelliSense handling
            if (intelliSenseEngine != null) {
                boolean consumed = intelliSenseEngine.getTriggerSystem().handleKeyPressed(event);
                if (consumed) {
                    event.consume();
                }
            }
        });

        codeArea.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();
            if (character != null && character.length() == 1) {
                if (intelliSenseEngine != null) {
                    intelliSenseEngine.getTriggerSystem().handleCharTyped(character.charAt(0));
                }
            }
        });

        logger.debug("Keyboard handlers setup");
    }

    /**
     * Sets up scroll event handler for Ctrl+Scroll font size control.
     */
    private void setupScrollHandler() {
        codeArea.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    // Scroll up = increase font size
                    if (onIncreaseFontSize != null) {
                        onIncreaseFontSize.run();
                    }
                } else if (event.getDeltaY() < 0) {
                    // Scroll down = decrease font size
                    if (onDecreaseFontSize != null) {
                        onDecreaseFontSize.run();
                    }
                }
                event.consume();
            }
        });
        logger.debug("Scroll handler setup (Ctrl+Scroll for font size)");
    }

    /**
     * Sets up mouse event handlers.
     * Implements Ctrl+Click for go-to-definition and hover tooltips.
     */
    private void setupMouseHandlers() {
        // Mouse click handler (Ctrl+Click for go-to-definition)
        codeArea.setOnMouseClicked(this::handleMouseClick);

        // Mouse move handler (hover tooltips)
        codeArea.setOnMouseMoved(this::handleMouseMove);

        // Track Ctrl key state for cursor changes
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && !ctrlPressed) {
                ctrlPressed = true;
                updateCursorStyle();
            }
        });

        codeArea.setOnKeyReleased(event -> {
            if (!event.isControlDown() && ctrlPressed) {
                ctrlPressed = false;
                updateCursorStyle();
            }
        });

        logger.debug("Mouse handlers setup (Ctrl+Click, Hover)");
    }

    /**
     * Handles mouse click events.
     * Implements Ctrl+Click for go-to-definition.
     */
    private void handleMouseClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (event.isControlDown()) {
            logger.debug("Ctrl+Click detected at ({}, {})", event.getX(), event.getY());
            handleGoToDefinition(event);
        }
    }

    /**
     * Handles mouse move events for hover tooltips.
     */
    private void handleMouseMove(MouseEvent event) {
        // Only show tooltips when not in Ctrl mode
        if (ctrlPressed) {
            removeTooltip();
            return;
        }

        // Get character position at mouse cursor
        int charPos = getCharacterPosition(event);
        if (charPos < 0) {
            removeTooltip();
            return;
        }

        // Analyze context at position
        String text = editorContext.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        XmlContext context = ContextAnalyzer.analyze(text, charPos);
        String tooltipText = buildTooltipText(context);

        if (tooltipText != null && !tooltipText.isEmpty()) {
            showTooltip(tooltipText);
        } else {
            removeTooltip();
        }
    }

    /**
     * Shows tooltip with given text.
     */
    private void showTooltip(String text) {
        if (hoverTooltip == null) {
            hoverTooltip = new javafx.scene.control.Tooltip(text);
            javafx.scene.control.Tooltip.install(codeArea, hoverTooltip);
        } else {
            hoverTooltip.setText(text);
        }
    }

    /**
     * Removes the tooltip.
     */
    private void removeTooltip() {
        if (hoverTooltip != null) {
            javafx.scene.control.Tooltip.uninstall(codeArea, hoverTooltip);
            hoverTooltip = null;
        }
    }

    /**
     * Handles go-to-definition functionality.
     */
    private void handleGoToDefinition(MouseEvent event) {
        int charPos = getCharacterPosition(event);
        if (charPos < 0) {
            return;
        }

        String text = editorContext.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        // Analyze context
        XmlContext context = ContextAnalyzer.analyze(text, charPos);

        // Get element information from XSD
        if (editorContext.hasSchema()) {
            String xpath = context.getXPath();
            var xsdData = editorContext.getSchemaProvider().getXsdDocumentationData();

            if (xsdData != null) {
                XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(xpath);

                if (elementInfo != null) {
                    showElementInfo(elementInfo);
                } else {
                    logger.debug("No XSD definition found for XPath: {}", xpath);
                }
            }
        } else {
            logger.debug("No schema loaded, go-to-definition not available");
        }
    }

    /**
     * Gets character position from mouse event.
     */
    private int getCharacterPosition(MouseEvent event) {
        try {
            Point2D point = new Point2D(event.getX(), event.getY());
            var hitInfo = codeArea.hit(point.getX(), point.getY());
            return hitInfo.getInsertionIndex();
        } catch (Exception e) {
            logger.debug("Error getting character position: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Builds tooltip text from XML context.
     */
    private String buildTooltipText(XmlContext context) {
        if (context == null) {
            return null;
        }

        StringBuilder tooltip = new StringBuilder();

        // Add context type
        tooltip.append("Context: ").append(context.getType()).append("\n");

        // Add current element
        if (context.getCurrentElement() != null) {
            tooltip.append("Element: ").append(context.getCurrentElement()).append("\n");
        }

        // Add current attribute
        if (context.getCurrentAttribute() != null) {
            tooltip.append("Attribute: ").append(context.getCurrentAttribute()).append("\n");
        }

        // Add XPath
        if (context.getXPath() != null) {
            tooltip.append("XPath: ").append(context.getXPath());
        }

        // Add XSD type information if available
        if (editorContext.hasSchema()) {
            String xpath = context.getXPath();
            var xsdData = editorContext.getSchemaProvider().getXsdDocumentationData();

            if (xsdData != null) {
                XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(xpath);
                if (elementInfo != null && elementInfo.getElementType() != null) {
                    tooltip.append("\nType: ").append(elementInfo.getElementType());
                    if (elementInfo.isMandatory()) {
                        tooltip.append(" (required)");
                    }
                }
            }
        }

        return tooltip.length() > 0 ? tooltip.toString() : null;
    }

    /**
     * Shows element information dialog.
     */
    private void showElementInfo(XsdExtendedElement elementInfo) {
        StringBuilder info = new StringBuilder();

        if (elementInfo.getElementType() != null) {
            info.append("Type: ").append(elementInfo.getElementType()).append("\n");
        }

        info.append("Mandatory: ").append(elementInfo.isMandatory() ? "Yes" : "No").append("\n");

        if (elementInfo.getChildren() != null && !elementInfo.getChildren().isEmpty()) {
            info.append("\nChild Elements (").append(elementInfo.getChildren().size()).append("):\n");
            for (String child : elementInfo.getChildren()) {
                info.append("  - ").append(child).append("\n");
            }
        }

        logger.debug("Element Info displayed for: {}", elementInfo.getElementName());

        // Show in proper dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Element Information");
        alert.setHeaderText("Element: " + elementInfo.getElementName());
        alert.setContentText(info.toString());
        alert.showAndWait();
    }

    /**
     * Updates cursor style based on Ctrl key state.
     */
    private void updateCursorStyle() {
        if (ctrlPressed) {
            codeArea.setCursor(Cursor.HAND);
        } else {
            codeArea.setCursor(Cursor.TEXT);
        }
    }

    /**
     * Sets up text change handlers.
     *
     * <p>Note: Text change listeners for syntax highlighting and validation are
     * already implemented in {@link org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2#setupTextChangeListeners()}.
     * This method is currently a placeholder for any additional event handler setup
     * that might be needed in the future.</p>
     */
    private void setupTextChangeHandlers() {
        // Text change handling is already implemented in XmlCodeEditorV2
        // No additional setup needed here at this time
        logger.debug("Text change handlers managed by XmlCodeEditorV2");
    }
}
