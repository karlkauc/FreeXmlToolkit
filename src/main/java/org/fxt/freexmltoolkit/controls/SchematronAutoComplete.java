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
 * Auto-completion functionality for Schematron documents.
 * Provides IntelliSense-like features for Schematron elements, attributes, and XPath functions.
 */
public class SchematronAutoComplete {

    private static final Logger logger = LogManager.getLogger(SchematronAutoComplete.class);

    // Schematron element suggestions
    private static final String[] SCHEMATRON_ELEMENTS = {
            "schema", "pattern", "rule", "assert", "report", "let", "param",
            "title", "p", "emph", "dir", "span", "ns", "diagnostics", "diagnostic",
            "name", "value-of", "extends", "include", "phase", "active"
    };

    // Schematron attribute suggestions
    private static final String[] SCHEMATRON_ATTRIBUTES = {
            "context", "test", "flag", "id", "role", "subject", "fpi", "icon",
            "see", "space", "queryBinding", "schemaVersion", "defaultPhase",
            "prefix", "uri", "select", "ref", "documents", "is-a", "abstract"
    };

    // XPath function suggestions
    private static final String[] XPATH_FUNCTIONS = {
            "count()", "exists()", "normalize-space()", "string-length()", "substring()",
            "contains()", "starts-with()", "ends-with()", "matches()", "replace()",
            "number()", "string()", "boolean()", "not()", "true()", "false()",
            "position()", "last()", "name()", "local-name()", "namespace-uri()",
            "sum()", "avg()", "min()", "max()", "round()", "ceiling()", "floor()"
    };

    // Common XML elements
    private static final String[] XML_ELEMENTS = {
            "xml", "xmlns", "version", "encoding", "standalone"
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
    public SchematronAutoComplete(CodeArea codeArea) {
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

        logger.debug("SchematronAutoComplete initialized for CodeArea");
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
                        navigateSuggestions(-1);
                    }
                    case DOWN -> {
                        event.consume();
                        navigateSuggestions(1);
                    }
                }
            }
        });

        // Handle typed characters for triggering auto-completion
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
            if (!isEnabled) {
                return; // Don't handle if disabled
            }
            
            String character = event.getCharacter();
            if (character != null && !character.isEmpty()) {
                char inputChar = character.charAt(0);
                logger.debug("Schematron auto-completion: KEY_TYPED event with character '{}'", inputChar);
                handleTextInput(inputChar);
            }
        });

        // Handle caret position changes
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (isActive && !isCaretInCompletionRange()) {
                hideAutoComplete();
            }
        });

        // Handle suggestion selection
        suggestionsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCurrentSuggestion();
            }
        });

        // Handle keyboard on the suggestions list (Enter/Tab accept, Esc closes)
        suggestionsList.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case ENTER, TAB -> {
                    selectCurrentSuggestion();
                    event.consume();
                }
                case ESCAPE -> {
                    hideAutoComplete();
                    event.consume();
                }
                default -> {
                }
            }
        });

        // Handle focus loss
        codeArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && isActive) {
                hideAutoComplete();
            }
        });
    }

    /**
     * Handle text input and determine if auto-completion should be triggered
     */
    private void handleTextInput(char inputChar) {
        if (!isEnabled) {
            return; // Don't handle input if auto-completion is disabled
        }
        
        int caretPos = codeArea.getCaretPosition();

        // Trigger auto-completion on specific characters
        switch (inputChar) {
            case '<' -> {
                logger.debug("Schematron auto-completion: Detected '<' character, triggering element completion");
                triggerElementCompletion(caretPos);
            }
            case ' ' -> {
                if (isInsideElement(caretPos)) {
                    logger.debug("Schematron auto-completion: Detected space inside element, triggering attribute completion");
                    triggerAttributeCompletion(caretPos);
                }
            }
            case '"', '\'' -> {
                if (isInsideAttributeValue(caretPos)) {
                    logger.debug("Schematron auto-completion: Detected quote inside attribute value, triggering value completion");
                    triggerValueCompletion(caretPos);
                }
            }
            default -> {
                if (isActive && Character.isLetter(inputChar)) {
                    updateSuggestions();
                } else if (isActive && !Character.isLetterOrDigit(inputChar) && inputChar != '-' && inputChar != ':') {
                    hideAutoComplete();
                }
            }
        }
    }

    /**
     * Trigger element name completion
     */
    private void triggerElementCompletion(int caretPos) {
        currentPrefix = "";
        completionStart = caretPos;

        List<CompletionItem> suggestions = new ArrayList<>();

        // Add Schematron elements
        for (String element : SCHEMATRON_ELEMENTS) {
            suggestions.add(new CompletionItem(element, CompletionType.SCHEMATRON_ELEMENT,
                    "Schematron element: " + element));
        }

        // Add common XML elements
        for (String element : XML_ELEMENTS) {
            suggestions.add(new CompletionItem(element, CompletionType.XML_ELEMENT,
                    "XML element: " + element));
        }

        showSuggestions(suggestions);
    }

    /**
     * Trigger attribute name completion
     */
    private void triggerAttributeCompletion(int caretPos) {
        String currentWord = getCurrentWord(caretPos);
        currentPrefix = currentWord;
        completionStart = caretPos - currentWord.length();

        List<CompletionItem> suggestions = new ArrayList<>();

        // Add Schematron attributes
        for (String attribute : SCHEMATRON_ATTRIBUTES) {
            if (attribute.startsWith(currentPrefix)) {
                suggestions.add(new CompletionItem(attribute + "=\"\"", CompletionType.SCHEMATRON_ATTRIBUTE,
                        "Schematron attribute: " + attribute));
            }
        }

        if (!suggestions.isEmpty()) {
            showSuggestions(suggestions);
        }
    }

    /**
     * Trigger value completion (for test, context attributes etc.)
     */
    private void triggerValueCompletion(int caretPos) {
        String attributeName = getAttributeNameBeforeValue(caretPos);

        if ("test".equals(attributeName) || "context".equals(attributeName) || "select".equals(attributeName)) {
            currentPrefix = "";
            completionStart = caretPos;

            List<CompletionItem> suggestions = new ArrayList<>();

            // Add XPath functions
            for (String function : XPATH_FUNCTIONS) {
                suggestions.add(new CompletionItem(function, CompletionType.XPATH_FUNCTION,
                        "XPath function: " + function.substring(0, function.length() - 2)));
            }

            // Add common XPath expressions
            suggestions.add(new CompletionItem(".", CompletionType.XPATH_EXPRESSION, "Current node"));
            suggestions.add(new CompletionItem("..", CompletionType.XPATH_EXPRESSION, "Parent node"));
            suggestions.add(new CompletionItem("*", CompletionType.XPATH_EXPRESSION, "Any element"));
            suggestions.add(new CompletionItem("@*", CompletionType.XPATH_EXPRESSION, "Any attribute"));
            suggestions.add(new CompletionItem("text()", CompletionType.XPATH_EXPRESSION, "Text content"));

            showSuggestions(suggestions);
        }
    }

    /**
     * Update suggestions based on current prefix
     */
    private void updateSuggestions() {
        if (!isActive) return;

        int caretPos = codeArea.getCaretPosition();
        String newPrefix = getCurrentPrefix(caretPos);

        if (!newPrefix.equals(currentPrefix)) {
            currentPrefix = newPrefix;

            // Filter existing suggestions
            List<CompletionItem> allSuggestions = getAllSuggestions();
            List<CompletionItem> filteredSuggestions = allSuggestions.stream()
                    .filter(item -> item.text().toLowerCase().startsWith(currentPrefix.toLowerCase()))
                    .collect(Collectors.toList());

            if (filteredSuggestions.isEmpty()) {
                hideAutoComplete();
            } else {
                suggestionsList.getItems().setAll(filteredSuggestions);
                if (!suggestionsList.getSelectionModel().isEmpty()) {
                    suggestionsList.getSelectionModel().selectFirst();
                }
            }
        }
    }

    /**
     * Get all available suggestions
     */
    private List<CompletionItem> getAllSuggestions() {
        List<CompletionItem> allSuggestions = new ArrayList<>();

        // Add all Schematron elements
        for (String element : SCHEMATRON_ELEMENTS) {
            allSuggestions.add(new CompletionItem(element, CompletionType.SCHEMATRON_ELEMENT,
                    "Schematron element"));
        }

        // Add all Schematron attributes  
        for (String attribute : SCHEMATRON_ATTRIBUTES) {
            allSuggestions.add(new CompletionItem(attribute, CompletionType.SCHEMATRON_ATTRIBUTE,
                    "Schematron attribute"));
        }

        // Add all XPath functions
        for (String function : XPATH_FUNCTIONS) {
            allSuggestions.add(new CompletionItem(function, CompletionType.XPATH_FUNCTION,
                    "XPath function"));
        }

        return allSuggestions;
    }

    /**
     * Show the auto-completion popup with suggestions
     */
    private void showSuggestions(List<CompletionItem> suggestions) {
        if (suggestions.isEmpty()) {
            hideAutoComplete();
            return;
        }

        suggestionsList.getItems().setAll(suggestions);
        suggestionsList.getSelectionModel().selectFirst();

        // Calculate popup position
        var bounds = codeArea.getCaretBounds();
        if (bounds.isPresent()) {
            var caretBounds = bounds.get();
            var screenBounds = codeArea.localToScreen(caretBounds);

            autoCompletePopup.show(codeArea,
                    screenBounds.getMinX(),
                    screenBounds.getMaxY() + 5);

            isActive = true;
            logger.debug("Auto-completion popup shown with {} suggestions", suggestions.size());

            // Focus the list so Enter works immediately
            suggestionsList.requestFocus();
        }
    }

    /**
     * Hide the auto-completion popup
     */
    private void hideAutoComplete() {
        if (autoCompletePopup.isShowing()) {
            autoCompletePopup.hide();
        }
        isActive = false;
        currentPrefix = "";
        completionStart = -1;
    }

    /**
     * Navigate through suggestions
     */
    private void navigateSuggestions(int direction) {
        var selectionModel = suggestionsList.getSelectionModel();
        int currentIndex = selectionModel.getSelectedIndex();
        int newIndex = currentIndex + direction;

        if (newIndex >= 0 && newIndex < suggestionsList.getItems().size()) {
            selectionModel.select(newIndex);
            suggestionsList.scrollTo(newIndex);
        }
    }

    /**
     * Select and insert the current suggestion
     */
    private void selectCurrentSuggestion() {
        CompletionItem selectedItem = suggestionsList.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            insertCompletion(selectedItem);
            hideAutoComplete();
        }
    }

    /**
     * Insert the selected completion into the code area
     */
    private void insertCompletion(CompletionItem item) {
        if (completionStart >= 0) {
            int caretPos = codeArea.getCaretPosition();

            // For element completions, insert full tag with closing pair and place caret between ><
            if (item.type() == CompletionType.SCHEMATRON_ELEMENT || item.type() == CompletionType.XML_ELEMENT) {
                String elementName = item.text();
                // Detect if the user already typed '<' (common when triggering by typing '<')
                boolean hasOpeningBracket = completionStart > 0 &&
                        codeArea.getText().charAt(completionStart - 1) == '<';

                String insertion = hasOpeningBracket
                        ? elementName + "></" + elementName + ">"
                        : "<" + elementName + "></" + elementName + ">";

                // Replace current prefix range with our constructed insertion
                codeArea.replaceText(completionStart, caretPos, insertion);

                // Caret between >< of opening/closing tag
                int caretOffsetWithinInsertion = (hasOpeningBracket ? elementName.length() + 1 : elementName.length() + 2);
                codeArea.moveTo(completionStart + caretOffsetWithinInsertion);

                logger.debug("Inserted schematron element snippet: {}", insertion);
            } else {
                // Replace current prefix with completion text
                codeArea.replaceText(completionStart, caretPos, item.text());

                // Position cursor appropriately
                int newCaretPos = completionStart + item.text().length();

                // For attributes with quotes, position cursor between quotes
                if (item.type() == CompletionType.SCHEMATRON_ATTRIBUTE && item.text().contains("=\"\"")) {
                    newCaretPos -= 1; // Position before closing quote
                }

                codeArea.moveTo(newCaretPos);
            }

            logger.debug("Inserted completion: {}", item.text());
        }
    }

    // ========== Helper Methods ==========

    /**
     * Get the current word at caret position
     */
    private String getCurrentWord(int caretPos) {
        String text = codeArea.getText();
        int start = caretPos;

        // Find start of word
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }

        return text.substring(start, caretPos);
    }

    /**
     * Get the current prefix for filtering
     */
    private String getCurrentPrefix(int caretPos) {
        if (completionStart >= 0 && caretPos >= completionStart) {
            return codeArea.getText().substring(completionStart, caretPos);
        }
        return "";
    }

    /**
     * Check if caret is inside an XML element (for attribute completion)
     */
    private boolean isInsideElement(int caretPos) {
        String text = codeArea.getText();
        int lastOpenBracket = text.lastIndexOf('<', caretPos - 1);
        int lastCloseBracket = text.lastIndexOf('>', caretPos - 1);

        return lastOpenBracket > lastCloseBracket && lastOpenBracket != -1;
    }

    /**
     * Check if caret is inside an attribute value
     */
    private boolean isInsideAttributeValue(int caretPos) {
        String text = codeArea.getText();
        if (caretPos == 0) return false;

        // Look backwards for quote characters
        int quoteCount = 0;
        for (int i = caretPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                quoteCount++;
            } else if (c == '<' || c == '>') {
                break;
            }
        }

        return quoteCount % 2 == 1; // Odd number means we're inside quotes
    }

    /**
     * Get the attribute name that precedes the current value context
     */
    private String getAttributeNameBeforeValue(int caretPos) {
        String text = codeArea.getText();

        // Look backwards to find attribute="
        for (int i = caretPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '=') {
                // Found equals, now find attribute name
                int nameEnd = i - 1;
                while (nameEnd >= 0 && Character.isWhitespace(text.charAt(nameEnd))) {
                    nameEnd--;
                }

                int nameStart = nameEnd;
                while (nameStart >= 0 && (Character.isLetterOrDigit(text.charAt(nameStart)) ||
                        text.charAt(nameStart) == '-' || text.charAt(nameStart) == ':')) {
                    nameStart--;
                }

                if (nameStart < nameEnd) {
                    return text.substring(nameStart + 1, nameEnd + 1);
                }
                break;
            } else if (c == '<' || c == '>') {
                break;
            }
        }

        return "";
    }

    /**
     * Check if caret is within the completion range
     */
    private boolean isCaretInCompletionRange() {
        if (completionStart < 0) return false;

        int caretPos = codeArea.getCaretPosition();
        return caretPos >= completionStart;
    }

    // ========== Inner Classes ==========

    /**
         * Represents a completion item with text, type, and description
         */
        public record CompletionItem(String text, CompletionType type, String description) {

        @Override
            public String toString() {
                return text + " - " + description;
            }
        }

    /**
     * Types of completion items
     */
    public enum CompletionType {
        SCHEMATRON_ELEMENT,
        SCHEMATRON_ATTRIBUTE,
        XML_ELEMENT,
        XML_ATTRIBUTE,
        XPATH_FUNCTION,
        XPATH_EXPRESSION
    }

    // ========== Public API ==========

    /**
     * Enable or disable auto-completion
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled && isActive) {
            hideAutoComplete();
        }
        logger.debug("Schematron auto-completion enabled: {}", enabled);
    }

    /**
     * Check if auto-completion is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Manually trigger auto-completion
     */
    public void triggerAutoComplete() {
        if (!isEnabled) {
            logger.debug("Schematron auto-completion: triggerAutoComplete called but auto-completion is disabled");
            return;
        }
        
        logger.debug("Schematron auto-completion: Manual trigger requested");
        int caretPos = codeArea.getCaretPosition();
        
        // Determine context and trigger appropriate completion
        String text = codeArea.getText();
        if (caretPos > 0 && text.charAt(caretPos - 1) == '<') {
            triggerElementCompletion(caretPos);
        } else if (isInsideElement(caretPos)) {
            triggerAttributeCompletion(caretPos);
        } else {
            triggerElementCompletion(caretPos);
        }
    }

    /**
     * Check if auto-completion is currently active
     */
    public boolean isActive() {
        return isActive;
    }
}