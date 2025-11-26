package org.fxt.freexmltoolkit.controls.v2.editor.intellisense;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathExpressionAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XPathCompletionProvider;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.triggers.TriggerSystem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui.IntelliSensePopup;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor;
import org.fxmisc.richtext.CodeArea;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * XPath/XQuery IntelliSense engine for CodeArea.
 * Provides autocomplete functionality for XPath expressions.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic triggers on '/', '[', '@', '(', '$'</li>
 *   <li>Manual trigger with Ctrl+Space</li>
 *   <li>Context-aware completions</li>
 *   <li>Smart insertion (parentheses for functions, :: for axes)</li>
 * </ul>
 */
public class XPathIntelliSenseEngine {

    private static final Logger logger = LogManager.getLogger(XPathIntelliSenseEngine.class);

    private final CodeArea codeArea;
    private final TriggerSystem triggerSystem;
    private final IntelliSensePopup popup;
    private final XPathCompletionProvider provider;
    private final XmlDocumentElementExtractor elementExtractor;
    private final boolean isXQueryMode;

    private Supplier<String> xmlContentSupplier;
    private boolean enabled = true;

    // Track last trigger character for context
    private char lastTriggerChar = 0;

    /**
     * Creates a new XPath IntelliSense engine.
     *
     * @param codeArea     the CodeArea to attach to
     * @param isXQueryMode true if XQuery mode (supports FLWOR)
     */
    public XPathIntelliSenseEngine(CodeArea codeArea, boolean isXQueryMode) {
        this.codeArea = codeArea;
        this.isXQueryMode = isXQueryMode;
        this.triggerSystem = new TriggerSystem();
        this.popup = new IntelliSensePopup();
        this.elementExtractor = new XmlDocumentElementExtractor();
        this.provider = new XPathCompletionProvider(elementExtractor, isXQueryMode);

        setupTriggers();
        setupPopupHandler();
        setupKeyHandler();

        logger.info("XPath IntelliSense engine initialized (XQuery mode: {})", isXQueryMode);
    }

    /**
     * Sets up the character and key triggers.
     */
    private void setupTriggers() {
        // Character triggers
        triggerSystem.addCharTrigger('/', () -> {
            lastTriggerChar = '/';
            showCompletionsDelayed();
        });

        triggerSystem.addCharTrigger('[', () -> {
            lastTriggerChar = '[';
            showCompletionsDelayed();
        });

        triggerSystem.addCharTrigger('@', () -> {
            lastTriggerChar = '@';
            showCompletionsDelayed();
        });

        triggerSystem.addCharTrigger('(', () -> {
            lastTriggerChar = '(';
            showCompletionsDelayed();
        });

        triggerSystem.addCharTrigger('$', () -> {
            lastTriggerChar = '$';
            showCompletionsDelayed();
        });

        triggerSystem.addCharTrigger(':', () -> {
            // Only trigger after axis (when previous char is also ':')
            String text = codeArea.getText();
            int pos = codeArea.getCaretPosition();
            if (pos >= 2 && text.charAt(pos - 2) == ':') {
                lastTriggerChar = ':';
                showCompletionsDelayed();
            }
        });

        // Manual trigger: Ctrl+Space
        triggerSystem.addKeyTrigger(KeyCode.SPACE, true, () -> {
            lastTriggerChar = 0;
            showCompletions();
        });
    }

    /**
     * Sets up the popup selection handler.
     */
    private void setupPopupHandler() {
        popup.setOnItemSelected(this::insertCompletion);
    }

    /**
     * Sets up keyboard event handling.
     */
    private void setupKeyHandler() {
        // Listen for text changes to trigger completions
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!enabled || newText == null || oldText == null) {
                return;
            }

            // Check if a character was typed (not deleted)
            if (newText.length() > oldText.length()) {
                char lastChar = newText.charAt(newText.length() - 1);
                Platform.runLater(() -> triggerSystem.handleCharTyped(lastChar));
            } else {
                // Text deleted - hide popup
                popup.hide();
            }
        });

        // Handle key events for popup navigation
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (popup.isShowing()) {
                switch (event.getCode()) {
                    case DOWN -> {
                        popup.selectNext();
                        event.consume();
                    }
                    case UP -> {
                        popup.selectPrevious();
                        event.consume();
                    }
                    case ENTER, TAB -> {
                        CompletionItem selected = popup.getSelectedItem();
                        if (selected != null) {
                            insertCompletion(selected);
                            event.consume();
                        }
                    }
                    case ESCAPE -> {
                        popup.hide();
                        event.consume();
                    }
                }
            } else {
                // Check for manual trigger
                if (triggerSystem.handleKeyPressed(event)) {
                    event.consume();
                }
            }
        });
    }

    /**
     * Shows completions with a small delay to allow the character to be inserted.
     */
    private void showCompletionsDelayed() {
        Platform.runLater(this::showCompletions);
    }

    /**
     * Shows completions based on current context.
     */
    public void showCompletions() {
        if (!enabled) {
            return;
        }

        // Refresh element extractor from XML content
        refreshElementExtractor();

        // Analyze context
        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();

        XPathEditorContext context = XPathExpressionAnalyzer.analyze(text, caretPos, isXQueryMode);

        // Get completions
        List<CompletionItem> items = provider.getCompletions(context);

        if (items.isEmpty()) {
            popup.hide();
            return;
        }

        // Show popup near caret
        showPopupAtCaret(items);
    }

    /**
     * Refreshes the element extractor with current XML content.
     */
    private void refreshElementExtractor() {
        if (xmlContentSupplier != null) {
            String xmlContent = xmlContentSupplier.get();
            if (xmlContent != null && !xmlContent.isBlank()) {
                elementExtractor.extractFromXml(xmlContent);
            }
        }
    }

    /**
     * Shows the popup near the caret position.
     */
    private void showPopupAtCaret(List<CompletionItem> items) {
        try {
            Optional<Bounds> caretBoundsOpt = codeArea.getCaretBounds();

            if (caretBoundsOpt.isPresent()) {
                Bounds caretBounds = caretBoundsOpt.get();
                double x = caretBounds.getMinX();
                double y = caretBounds.getMaxY() + 5; // Below the caret

                Window window = codeArea.getScene().getWindow();
                popup.show(items, window, x, y);
            } else {
                // Fallback: show at scene position
                Bounds sceneBounds = codeArea.localToScreen(codeArea.getBoundsInLocal());
                if (sceneBounds != null) {
                    Window window = codeArea.getScene().getWindow();
                    popup.show(items, window, sceneBounds.getMinX() + 50, sceneBounds.getMinY() + 30);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to show popup at caret: {}", e.getMessage());
        }
    }

    /**
     * Inserts the selected completion.
     */
    private void insertCompletion(CompletionItem item) {
        if (item == null) {
            return;
        }

        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();

        // Analyze to find token start
        XPathEditorContext context = XPathExpressionAnalyzer.analyze(text, caretPos, isXQueryMode);
        int tokenStart = context.getTokenStartPosition();
        int tokenLength = context.getCurrentTokenLength();

        // Determine what to insert
        String insertText = getSmartInsertText(item);

        // Calculate replacement range
        int replaceStart = tokenStart;
        int replaceEnd = tokenStart + tokenLength;

        // Perform replacement
        codeArea.replaceText(replaceStart, replaceEnd, insertText);

        // Move caret to appropriate position
        int newCaretPos = replaceStart + insertText.length();

        // For functions, place cursor inside parentheses
        if (item.getType() == CompletionItemType.XPATH_FUNCTION && insertText.endsWith("()")) {
            newCaretPos = replaceStart + insertText.length() - 1;
        }

        codeArea.moveTo(newCaretPos);
        popup.hide();

        logger.debug("Inserted completion: '{}' at position {}", insertText, replaceStart);
    }

    /**
     * Gets the smart insert text based on completion type.
     */
    private String getSmartInsertText(CompletionItem item) {
        String insertText = item.getInsertText();
        CompletionItemType type = item.getType();

        if (type == null) {
            return insertText;
        }

        switch (type) {
            case XPATH_FUNCTION -> {
                // Ensure functions have ()
                if (!insertText.endsWith("()") && !insertText.endsWith("(")) {
                    return insertText + "()";
                }
            }
            case XPATH_AXIS -> {
                // Ensure axes have ::
                if (!insertText.endsWith("::")) {
                    return insertText + "::";
                }
            }
            case XQUERY_KEYWORD -> {
                // Ensure keywords have trailing space
                if (!insertText.endsWith(" ")) {
                    return insertText + " ";
                }
            }
            default -> {
                // Use insert text as-is
            }
        }

        return insertText;
    }

    /**
     * Sets the XML content supplier for element extraction.
     */
    public void setXmlContentSupplier(Supplier<String> supplier) {
        this.xmlContentSupplier = supplier;
    }

    /**
     * Enables or disables the IntelliSense engine.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            popup.hide();
        }
    }

    /**
     * Returns whether the engine is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the trigger system for external configuration.
     */
    public TriggerSystem getTriggerSystem() {
        return triggerSystem;
    }

    /**
     * Gets the element extractor for manual updates.
     */
    public XmlDocumentElementExtractor getElementExtractor() {
        return elementExtractor;
    }

    /**
     * Hides the completion popup.
     */
    public void hidePopup() {
        popup.hide();
    }

    /**
     * Returns whether the popup is currently showing.
     */
    public boolean isPopupShowing() {
        return popup.isShowing();
    }

    /**
     * Disposes of resources.
     */
    public void dispose() {
        popup.hide();
        triggerSystem.clearTriggers();
        elementExtractor.clear();
        logger.debug("XPath IntelliSense engine disposed");
    }
}
