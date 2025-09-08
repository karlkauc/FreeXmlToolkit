package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.ListView;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Auto-completion functionality for XSLT documents.
 * Provides IntelliSense-like features for XSLT elements, attributes, and XPath functions.
 */
public class XsltAutoComplete {

    private static final Logger logger = LogManager.getLogger(XsltAutoComplete.class);

    // XSLT element suggestions
    private static final String[] XSLT_ELEMENTS = {
            "stylesheet", "transform", "template", "apply-templates", "call-template", "with-param",
            "param", "variable", "value-of", "text", "element", "attribute", "comment", "processing-instruction",
            "copy", "copy-of", "if", "choose", "when", "otherwise", "for-each", "sort", "number",
            "message", "fallback", "include", "import", "output", "strip-space", "preserve-space",
            "key", "decimal-format", "namespace-alias", "attribute-set", "use-attribute-sets"
    };

    // XSLT attribute suggestions
    private static final String[] XSLT_ATTRIBUTES = {
            "match", "select", "name", "mode", "priority", "test", "href", "use-attribute-sets",
            "namespace", "exclude-result-prefixes", "extension-element-prefixes", "version",
            "method", "encoding", "omit-xml-declaration", "standalone", "doctype-public", "doctype-system",
            "cdata-section-elements", "indent", "media-type", "disable-output-escaping",
            "data-type", "order", "case-order", "lang", "elements", "use", "level", "count", "from",
            "value", "format", "letter-value", "ordinal", "grouping-separator", "grouping-size"
    };

    // XPath function suggestions
    private static final String[] XPATH_FUNCTIONS = {
            "count()", "exists()", "normalize-space()", "string-length()", "substring()", "substring-before()", "substring-after()",
            "contains()", "starts-with()", "ends-with()", "matches()", "replace()", "translate()",
            "number()", "string()", "boolean()", "not()", "true()", "false()",
            "position()", "last()", "name()", "local-name()", "namespace-uri()",
            "sum()", "avg()", "min()", "max()", "round()", "ceiling()", "floor()",
            "concat()", "format-number()", "current()", "document()", "element-available()", "function-available()",
            "generate-id()", "key()", "unparsed-entity-uri()", "system-property()"
    };

    // Auto-completion popup and components
    private final CodeArea codeArea;
    private final Popup autoCompletePopup;
    private final ListView<CompletionItem> suggestionsList;

    // State management
    private boolean isActive = false;
    private boolean isEnabled = false;
    private String currentPrefix = "";
    private int completionStart = -1;

    /**
     * Constructor - attaches auto-completion to the specified CodeArea
     */
    public XsltAutoComplete(CodeArea codeArea) {
        this.codeArea = codeArea;

        // Initialize suggestion list
        this.suggestionsList = new ListView<>();
        this.suggestionsList.setPrefHeight(150);
        this.suggestionsList.setPrefWidth(250);
        this.suggestionsList.getStyleClass().add("autocompletion-popup");

        // Initialize popup
        this.autoCompletePopup = new Popup();
        this.autoCompletePopup.getContent().add(suggestionsList);
        this.autoCompletePopup.setAutoHide(true);
        this.autoCompletePopup.setAutoFix(true);

        // Set up event handlers
        setupEventHandlers();

        logger.debug("XsltAutoComplete initialized for CodeArea");
    }

    /**
     * Set up event handlers for auto-completion functionality
     */
    private void setupEventHandlers() {
        // Handle key presses in code area for popup navigation
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (isActive && autoCompletePopup.isShowing()) {
                switch (event.getCode()) {
                    case ESCAPE -> {
                        hideAutoComplete();
                        event.consume();
                    }
                    case ENTER -> {
                        event.consume();
                        selectCurrentSuggestion();
                    }
                    case UP -> {
                        event.consume();
                        navigateUp();
                    }
                    case DOWN -> {
                        event.consume();
                        navigateDown();
                    }
                    case TAB -> {
                        event.consume();
                        selectCurrentSuggestion();
                    }
                    default -> {
                        // Let other keys pass through
                    }
                }
            }
        });

        // Handle character typed events for auto-completion triggers
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
            if (isEnabled) {
                String character = event.getCharacter();
                if ("<".equals(character)) {
                    showElementCompletions();
                } else if (" ".equals(character) && isInsideXsltElement()) {
                    showAttributeCompletions();
                } else if (character.matches("[a-zA-Z0-9:_-]")) {
                    updateCompletions();
                }
            }
        });

        // Handle suggestion selection
        suggestionsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCurrentSuggestion();
            }
        });
    }

    /**
     * Shows element completions for XSLT elements
     */
    private void showElementCompletions() {
        if (!isEnabled) return;

        int caretPosition = codeArea.getCaretPosition();
        String text = codeArea.getText();

        // Check if we're in XSLT context
        if (!isInXsltContext(text, caretPosition)) {
            return;
        }

        List<CompletionItem> completions = new ArrayList<>();
        for (String element : XSLT_ELEMENTS) {
            completions.add(new CompletionItem(element, CompletionItemType.ELEMENT, "xsl:" + element));
        }

        showCompletions(completions, caretPosition);
    }

    /**
     * Shows attribute completions for XSLT attributes
     */
    private void showAttributeCompletions() {
        if (!isEnabled) return;

        int caretPosition = codeArea.getCaretPosition();
        List<CompletionItem> completions = new ArrayList<>();

        for (String attribute : XSLT_ATTRIBUTES) {
            completions.add(new CompletionItem(attribute, CompletionItemType.ATTRIBUTE, attribute + "=\"\""));
        }

        showCompletions(completions, caretPosition);
    }

    /**
     * Updates completions based on current prefix
     */
    private void updateCompletions() {
        if (!isActive || !autoCompletePopup.isShowing()) return;

        // Get current prefix
        int caretPosition = codeArea.getCaretPosition();
        String text = codeArea.getText();
        String prefix = extractPrefix(text, caretPosition);

        // Filter suggestions based on prefix
        List<CompletionItem> filteredSuggestions = getAllCompletions().stream()
                .filter(item -> item.displayText().toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());

        suggestionsList.getItems().setAll(filteredSuggestions);
        if (!filteredSuggestions.isEmpty()) {
            suggestionsList.getSelectionModel().selectFirst();
        }
    }

    /**
     * Shows the completion popup with the given suggestions
     */
    private void showCompletions(List<CompletionItem> completions, int position) {
        if (completions.isEmpty()) return;

        suggestionsList.getItems().setAll(completions);
        suggestionsList.getSelectionModel().selectFirst();

        // Position popup
        var bounds = codeArea.getCaretBounds();
        if (bounds.isPresent()) {
            autoCompletePopup.show(codeArea, bounds.get().getMaxX(), bounds.get().getMaxY());
            isActive = true;
            completionStart = position;
        }
    }

    /**
     * Gets all available completions
     */
    private List<CompletionItem> getAllCompletions() {
        List<CompletionItem> all = new ArrayList<>();

        // Add elements
        for (String element : XSLT_ELEMENTS) {
            all.add(new CompletionItem(element, CompletionItemType.ELEMENT, "xsl:" + element));
        }

        // Add attributes
        for (String attribute : XSLT_ATTRIBUTES) {
            all.add(new CompletionItem(attribute, CompletionItemType.ATTRIBUTE, attribute + "=\"\""));
        }

        // Add functions
        for (String function : XPATH_FUNCTIONS) {
            all.add(new CompletionItem(function, CompletionItemType.FUNCTION, function));
        }

        return all;
    }

    /**
     * Checks if we're inside an XSLT element
     */
    private boolean isInsideXsltElement() {
        int caretPosition = codeArea.getCaretPosition();
        String text = codeArea.getText();

        // Simple check - look for < before cursor and > after cursor
        int lastOpenBracket = text.lastIndexOf('<', caretPosition - 1);
        int lastCloseBracket = text.lastIndexOf('>', caretPosition - 1);

        return lastOpenBracket > lastCloseBracket;
    }

    /**
     * Checks if we're in XSLT context
     */
    private boolean isInXsltContext(String text, int position) {
        // Check if we have XSLT namespace declaration
        return text.contains("http://www.w3.org/1999/XSL/Transform") ||
                text.contains("xsl:stylesheet") ||
                text.contains("xsl:transform");
    }

    /**
     * Extracts the current prefix being typed
     */
    private String extractPrefix(String text, int position) {
        if (position == 0) return "";

        int start = position - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }

        return text.substring(start + 1, position);
    }

    /**
     * Selects the current suggestion and inserts it
     */
    private void selectCurrentSuggestion() {
        CompletionItem selected = suggestionsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            insertCompletion(selected);
            hideAutoComplete();
        }
    }

    /**
     * Inserts the selected completion
     */
    private void insertCompletion(CompletionItem item) {
        int caretPosition = codeArea.getCaretPosition();
        String insertText = item.insertText();

        // Remove current prefix if any
        String text = codeArea.getText();
        String prefix = extractPrefix(text, caretPosition);

        if (!prefix.isEmpty()) {
            codeArea.replaceText(caretPosition - prefix.length(), caretPosition, insertText);
        } else {
            codeArea.insertText(caretPosition, insertText);
        }

        logger.debug("Inserted XSLT completion: {}", insertText);
    }

    /**
     * Navigates up in the suggestions list
     */
    private void navigateUp() {
        int currentIndex = suggestionsList.getSelectionModel().getSelectedIndex();
        if (currentIndex > 0) {
            suggestionsList.getSelectionModel().select(currentIndex - 1);
        }
    }

    /**
     * Navigates down in the suggestions list
     */
    private void navigateDown() {
        int currentIndex = suggestionsList.getSelectionModel().getSelectedIndex();
        if (currentIndex < suggestionsList.getItems().size() - 1) {
            suggestionsList.getSelectionModel().select(currentIndex + 1);
        }
    }

    /**
     * Hides the auto-completion popup
     */
    private void hideAutoComplete() {
        if (autoCompletePopup.isShowing()) {
            autoCompletePopup.hide();
        }
        isActive = false;
        completionStart = -1;
        currentPrefix = "";
    }

    /**
     * Enables or disables auto-completion
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            hideAutoComplete();
        }
        logger.debug("XSLT auto-completion enabled: {}", enabled);
    }

    /**
     * Returns whether auto-completion is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Returns whether auto-completion is currently active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
         * Completion item representation
         */
        private record CompletionItem(String displayText, CompletionItemType type, String insertText) {

        @Override
            public String toString() {
                return displayText;
            }
        }

    /**
     * Completion item types
     */
    private enum CompletionItemType {
        ELEMENT, ATTRIBUTE, FUNCTION
    }
}