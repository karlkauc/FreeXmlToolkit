package org.fxt.freexmltoolkit.controls.v2.editor.intellisense;

import javafx.geometry.Bounds;
import javafx.scene.input.KeyCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.cache.CompletionCache;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.IntelliSenseState;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.CompletionProvider;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XsdCompletionProvider;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.registry.ProviderRegistry;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.triggers.TriggerSystem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui.IntelliSensePopup;

import java.util.List;

/**
 * Main IntelliSense engine coordinating all IntelliSense functionality.
 * This is the orchestrator that brings together context analysis, providers, cache, and triggers.
 */
public class IntelliSenseEngine {

    private static final Logger logger = LogManager.getLogger(IntelliSenseEngine.class);

    private final EditorContext editorContext;
    private final ProviderRegistry providerRegistry;
    private final TriggerSystem triggerSystem;
    private final CompletionCache completionCache;
    private final IntelliSensePopup popup;

    // State
    private IntelliSenseState state = IntelliSenseState.HIDDEN;
    private XmlContext currentContext;

    /**
     * Creates a new IntelliSense engine.
     *
     * @param editorContext the editor context
     */
    public IntelliSenseEngine(EditorContext editorContext) {
        this.editorContext = editorContext;
        this.providerRegistry = new ProviderRegistry();
        this.triggerSystem = new TriggerSystem();
        this.completionCache = new CompletionCache();
        this.popup = new IntelliSensePopup();

        // Set up popup selection handler
        this.popup.setOnItemSelected(this::insertCompletion);
    }

    /**
     * Initializes the IntelliSense engine.
     * Registers providers and triggers.
     */
    public void initialize() {
        logger.info("Initializing IntelliSense engine");

        // Register completion providers
        registerProviders();

        // Setup triggers
        setupTriggers();

        logger.info("IntelliSense engine initialized with {} providers",
                providerRegistry.getProviderCount());
    }

    /**
     * Registers all completion providers.
     */
    private void registerProviders() {
        // XSD-based provider (highest priority)
        CompletionProvider xsdProvider = new XsdCompletionProvider(editorContext.getSchemaProvider());
        providerRegistry.registerProvider(xsdProvider);

        // Schematron provider
        CompletionProvider schematronProvider = new org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.SchematronCompletionProvider();
        providerRegistry.registerProvider(schematronProvider);

        // XSLT provider
        CompletionProvider xsltProvider = new org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XsltCompletionProvider();
        providerRegistry.registerProvider(xsltProvider);

        // XSL-FO provider
        CompletionProvider xslFoProvider = new org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XslFoCompletionProvider();
        providerRegistry.registerProvider(xslFoProvider);

        // Pattern provider (fallback - lowest priority)
        CompletionProvider patternProvider = new org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.PatternCompletionProvider(editorContext);
        providerRegistry.registerProvider(patternProvider);

        logger.debug("Registered {} providers", providerRegistry.getProviderCount());
    }

    /**
     * Sets up IntelliSense triggers.
     */
    private void setupTriggers() {
        // Character triggers
        triggerSystem.addCharTrigger('<', this::showCompletions);
        triggerSystem.addCharTrigger(' ', this::showCompletionsIfInAttributeContext);

        // Key triggers
        triggerSystem.addKeyTrigger(KeyCode.SPACE, true, this::showCompletions); // Ctrl+Space

        logger.debug("Setup IntelliSense triggers");
    }

    /**
     * Shows completions for the current context.
     */
    public void showCompletions() {
        String text = editorContext.getText();
        int caretPos = editorContext.getCaretPosition();

        logger.debug("Showing completions at position {}", caretPos);

        // Analyze context
        currentContext = analyzeContext(text, caretPos);

        // Check if IntelliSense should be disabled
        if (currentContext.shouldDisableIntelliSense()) {
            logger.debug("IntelliSense disabled for context: {}", currentContext.getType());
            return;
        }

        // Try cache first
        List<CompletionItem> items = completionCache.get(
                currentContext.getXPath(),
                currentContext.getType(),
                editorContext.getCurrentMode()
        );

        // If not in cache, get from providers
        if (items == null) {
            items = providerRegistry.getCompletions(currentContext, editorContext.getCurrentMode());

            // Cache the results
            if (!items.isEmpty()) {
                completionCache.put(
                        currentContext.getXPath(),
                        currentContext.getType(),
                        editorContext.getCurrentMode(),
                        items
                );
            }
        }

        if (!items.isEmpty()) {
            logger.info("Showing {} completion items", items.size());

            // Calculate popup position near caret
            CodeArea codeArea = editorContext.getCodeArea();

            // Get owner window
            javafx.stage.Window ownerWindow = codeArea.getScene() != null ? codeArea.getScene().getWindow() : null;
            if (ownerWindow == null) {
                logger.warn("Cannot show popup - CodeArea has no window");
                return;
            }

            // Get caret bounds in screen coordinates
            Bounds caretBounds = codeArea.getCaretBounds().orElse(null);
            if (caretBounds != null) {
                Bounds screenBounds = codeArea.localToScreen(caretBounds);
                if (screenBounds != null) {
                    // Show popup below the caret
                    double x = screenBounds.getMinX();
                    double y = screenBounds.getMaxY() + 2; // 2px offset

                    popup.show(items, ownerWindow, x, y);
                    state = IntelliSenseState.SHOWING_ELEMENTS;
                    logger.debug("Popup shown at ({}, {}) with {} items", x, y, items.size());
                } else {
                    logger.warn("Could not convert caret bounds to screen coordinates");
                }
            } else {
                logger.warn("Could not get caret bounds");
            }
        } else {
            logger.debug("No completions available");
            hideCompletions();
        }
    }

    /**
     * Shows completions only if in attribute context.
     */
    private void showCompletionsIfInAttributeContext() {
        String text = editorContext.getText();
        int caretPos = editorContext.getCaretPosition();

        // Quick check if we're inside a tag
        String textBeforeCaret = text.substring(0, caretPos);
        int lastOpen = textBeforeCaret.lastIndexOf('<');
        int lastClose = textBeforeCaret.lastIndexOf('>');

        if (lastOpen > lastClose) {
            showCompletions();
        }
    }

    /**
     * Hides the IntelliSense popup.
     */
    public void hideCompletions() {
        popup.hide();
        state = IntelliSenseState.HIDDEN;
        logger.debug("Hiding completions");
    }

    /**
     * Inserts the selected completion item into the editor.
     * Handles intelligent insertion based on item type:
     * - Replaces partial text already typed
     * - Adds quotes for attribute values
     * - Positions cursor correctly after insertion
     *
     * @param item the completion item to insert
     */
    private void insertCompletion(CompletionItem item) {
        if (item == null) {
            return;
        }

        logger.info("Inserting completion: {} (type: {})", item.getLabel(), item.getType());

        CodeArea codeArea = editorContext.getCodeArea();
        String text = editorContext.getText();
        int caretPos = editorContext.getCaretPosition();

        // Get the text to insert
        String insertText = item.getInsertText() != null ? item.getInsertText() : item.getLabel();

        // Detect and delete partial text already typed
        int deleteStart = findPartialTextStart(text, caretPos, item.getLabel());
        int deleteLength = caretPos - deleteStart;

        if (deleteLength > 0) {
            logger.debug("Deleting partial text from {} to {} (length: {})", deleteStart, caretPos, deleteLength);
            codeArea.deleteText(deleteStart, caretPos);
            caretPos = deleteStart;
        }

        // Smart insertion based on item type
        InsertionResult result = performSmartInsertion(item, insertText, currentContext);

        // Insert the text
        codeArea.insertText(caretPos, result.textToInsert);

        // Position cursor
        int newCaretPos = caretPos + result.cursorOffset;
        codeArea.moveTo(newCaretPos);

        logger.debug("Inserted '{}' at position {}, cursor moved to {}",
                     result.textToInsert, caretPos, newCaretPos);
    }

    /**
     * Finds the start position of partial text already typed.
     * Looks backwards from caret to find matching prefix.
     *
     * @param text      the full text
     * @param caretPos  the caret position
     * @param label     the completion label to match
     * @return the start position of partial text
     */
    private int findPartialTextStart(String text, int caretPos, String label) {
        if (caretPos == 0 || label == null || label.isEmpty()) {
            return caretPos;
        }

        // Look backwards to find word boundary
        int start = caretPos;
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_' && ch != ':') {
                break;
            }
            start--;
        }

        // Check if the typed text is a prefix of the label
        String typedText = text.substring(start, caretPos);
        if (typedText.isEmpty()) {
            return caretPos;
        }

        // Case-insensitive prefix match
        if (label.toLowerCase().startsWith(typedText.toLowerCase())) {
            logger.debug("Found partial text: '{}' matching label '{}'", typedText, label);
            return start;
        }

        return caretPos;
    }

    /**
     * Performs smart insertion based on completion item type.
     *
     * @param item        the completion item
     * @param insertText  the base text to insert
     * @param context     the current XML context
     * @return insertion result with text and cursor offset
     */
    private InsertionResult performSmartInsertion(CompletionItem item, String insertText, XmlContext context) {
        return switch (item.getType()) {
            case ELEMENT -> handleElementInsertion(insertText);
            case ATTRIBUTE -> handleAttributeInsertion(insertText);
            case VALUE -> handleValueInsertion(insertText, context);
            case SNIPPET -> new InsertionResult(insertText, insertText.length());
            // XPath/XQuery types - handled by XPathIntelliSenseEngine, pass through here
            case XPATH_FUNCTION, XPATH_AXIS, XPATH_OPERATOR, XPATH_VARIABLE,
                 XQUERY_KEYWORD, XPATH_NODE_TEST, TYPE -> new InsertionResult(insertText, insertText.length());
        };
    }

    /**
     * Handles element insertion with closing tag.
     */
    private InsertionResult handleElementInsertion(String elementName) {
        // Check if insertText already contains tags
        if (elementName.startsWith("<") && elementName.contains(">")) {
            // Already formatted, find cursor position
            int cursorPos = elementName.indexOf('>') + 1;
            return new InsertionResult(elementName, cursorPos);
        }

        // Simple element name - add tags
        String text = elementName + "></" + elementName + ">";
        int cursorPos = elementName.length() + 1; // Position after '>'
        return new InsertionResult(text, cursorPos);
    }

    /**
     * Handles attribute insertion with quotes.
     */
    private InsertionResult handleAttributeInsertion(String attributeName) {
        // Check if already formatted with quotes
        if (attributeName.contains("=\"\"")) {
            int cursorPos = attributeName.indexOf("=\"\"") + 2; // Inside quotes
            return new InsertionResult(attributeName, cursorPos);
        }

        // Add quotes
        String text = attributeName + "=\"\"";
        int cursorPos = attributeName.length() + 2; // Inside quotes
        return new InsertionResult(text, cursorPos);
    }

    /**
     * Handles value insertion for attributes or text content.
     */
    private InsertionResult handleValueInsertion(String value, XmlContext context) {
        if (context != null && context.getType() == org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType.ATTRIBUTE_VALUE) {
            // Already inside quotes, just insert value
            return new InsertionResult(value, value.length());
        }

        // For text content, just insert
        return new InsertionResult(value, value.length());
    }

    /**
     * Result of smart insertion operation.
     */
    private static class InsertionResult {
        final String textToInsert;
        final int cursorOffset;

        InsertionResult(String textToInsert, int cursorOffset) {
            this.textToInsert = textToInsert;
            this.cursorOffset = cursorOffset;
        }
    }

    /**
     * Analyzes the XML context at a specific position.
     *
     * @param text     the XML text
     * @param position the position
     * @return the XML context
     */
    public XmlContext analyzeContext(String text, int position) {
        return ContextAnalyzer.analyze(text, position);
    }

    /**
     * Gets the trigger system for external event handling.
     *
     * @return the trigger system
     */
    public TriggerSystem getTriggerSystem() {
        return triggerSystem;
    }

    /**
     * Gets the current IntelliSense state.
     *
     * @return the current state
     */
    public IntelliSenseState getState() {
        return state;
    }

    /**
     * Invalidates the completion cache.
     * Call this when the XSD schema changes.
     */
    public void invalidateCache() {
        completionCache.invalidateAll();
        logger.debug("IntelliSense cache invalidated");
    }

    /**
     * Invalidates cache for schema changes.
     */
    public void invalidateCacheForSchema() {
        completionCache.invalidateForSchema();
        logger.debug("IntelliSense cache invalidated for schema change");
    }
}
