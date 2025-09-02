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
 * Auto-completion functionality for XSD (XML Schema Definition) documents.
 * Provides IntelliSense-like features for XSD elements, attributes, and data types.
 */
public class XsdAutoComplete {

    private static final Logger logger = LogManager.getLogger(XsdAutoComplete.class);

    // XSD element suggestions
    private static final String[] XSD_ELEMENTS = {
            "schema", "element", "complexType", "simpleType", "attribute", "group", "attributeGroup",
            "sequence", "choice", "all", "restriction", "extension", "union", "list",
            "enumeration", "pattern", "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
            "length", "minLength", "maxLength", "whiteSpace", "fractionDigits", "totalDigits",
            "import", "include", "redefine", "notation", "annotation", "documentation", "appinfo"
    };

    // XSD attribute suggestions
    private static final String[] XSD_ATTRIBUTES = {
            "name", "type", "ref", "minOccurs", "maxOccurs", "use", "default", "fixed",
            "form", "nillable", "abstract", "block", "final", "substitutionGroup",
            "targetNamespace", "xmlns", "elementFormDefault", "attributeFormDefault",
            "blockDefault", "finalDefault", "version", "id", "base", "memberTypes",
            "itemType", "value", "namespace", "schemaLocation", "xpath", "source"
    };

    // XSD built-in data types
    private static final String[] XSD_DATA_TYPES = {
            "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time",
            "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary",
            "anyURI", "QName", "NOTATION", "normalizedString", "token", "language", "NMTOKEN",
            "NMTOKENS", "Name", "NCName", "ID", "IDREF", "IDREFS", "ENTITY", "ENTITIES",
            "integer", "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte",
            "nonNegativeInteger", "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
            "positiveInteger"
    };

    // Auto-completion popup and components
    private final CodeArea codeArea;
    private final Popup autoCompletePopup;
    private final ListView<CompletionItem> suggestionsList;

    // State management
    private boolean isActive = false;
    private String currentPrefix = "";
    private int completionStart = -1;

    /**
     * Constructor - attaches auto-completion to the specified CodeArea
     */
    public XsdAutoComplete(CodeArea codeArea) {
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

        logger.debug("XsdAutoComplete initialized for CodeArea");
    }

    /**
     * Set up event handlers for auto-completion functionality
     */
    private void setupEventHandlers() {
        // Handle key presses in code area
        codeArea.setOnKeyPressed(event -> {
            if (isActive && autoCompletePopup.isShowing()) {
                switch (event.getCode()) {
                    case ESCAPE -> hideAutoComplete();
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

        // Handle text changes for triggering auto-completion
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() > oldText.length()) {
                // Text was added
                char lastChar = newText.charAt(newText.length() - 1);
                handleTextInput(lastChar);
            } else if (isActive) {
                // Text was removed - update suggestions
                updateSuggestions();
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
        int caretPos = codeArea.getCaretPosition();

        // Trigger auto-completion on specific characters
        switch (inputChar) {
            case '<' -> triggerElementCompletion(caretPos);
            case ' ' -> {
                if (isInsideElement(caretPos)) {
                    triggerAttributeCompletion(caretPos);
                }
            }
            case '\"', '\'' -> {
                if (isInsideAttributeValue(caretPos)) {
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

        // Add XSD elements with namespace prefix
        for (String element : XSD_ELEMENTS) {
            suggestions.add(new CompletionItem("xs:" + element, CompletionType.XSD_ELEMENT,
                    "XSD element: " + element));
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

        // Add XSD attributes
        for (String attribute : XSD_ATTRIBUTES) {
            if (attribute.startsWith(currentPrefix)) {
                suggestions.add(new CompletionItem(attribute + "=\"\"", CompletionType.XSD_ATTRIBUTE,
                        "XSD attribute: " + attribute));
            }
        }

        if (!suggestions.isEmpty()) {
            showSuggestions(suggestions);
        }
    }

    /**
     * Trigger value completion (for type attributes, etc.)
     */
    private void triggerValueCompletion(int caretPos) {
        String attributeName = getAttributeNameBeforeValue(caretPos);

        if ("type".equals(attributeName)) {
            currentPrefix = "";
            completionStart = caretPos;

            List<CompletionItem> suggestions = new ArrayList<>();

            // Add XSD data types with namespace prefix
            for (String dataType : XSD_DATA_TYPES) {
                suggestions.add(new CompletionItem("xs:" + dataType, CompletionType.XSD_DATA_TYPE,
                        "XSD data type: " + dataType));
            }

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

        // Add all XSD elements
        for (String element : XSD_ELEMENTS) {
            allSuggestions.add(new CompletionItem("xs:" + element, CompletionType.XSD_ELEMENT,
                    "XSD element"));
        }

        // Add all XSD attributes
        for (String attribute : XSD_ATTRIBUTES) {
            allSuggestions.add(new CompletionItem(attribute, CompletionType.XSD_ATTRIBUTE,
                    "XSD attribute"));
        }

        // Add all XSD data types
        for (String dataType : XSD_DATA_TYPES) {
            allSuggestions.add(new CompletionItem("xs:" + dataType, CompletionType.XSD_DATA_TYPE,
                    "XSD data type"));
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
            logger.debug("XSD auto-completion popup shown with {} suggestions", suggestions.size());
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

            // Replace current prefix with completion
            codeArea.replaceText(completionStart, caretPos, item.text());

            // Position cursor appropriately
            int newCaretPos = completionStart + item.text().length();

            // For attributes with quotes, position cursor between quotes
            if (item.type() == CompletionType.XSD_ATTRIBUTE && item.text().contains("=\"\"")) {
                newCaretPos -= 1; // Position before closing quote
            }

            codeArea.moveTo(newCaretPos);

            logger.debug("Inserted XSD completion: {}", item.text());
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
        XSD_ELEMENT,
        XSD_ATTRIBUTE,
        XSD_DATA_TYPE
    }

    // ========== Public API ==========

    /**
     * Enable or disable auto-completion
     */
    public void setEnabled(boolean enabled) {
        if (!enabled && isActive) {
            hideAutoComplete();
        }
        logger.debug("XSD auto-completion enabled: {}", enabled);
    }

    /**
     * Manually trigger auto-completion
     */
    public void triggerAutoComplete() {
        int caretPos = codeArea.getCaretPosition();
        handleTextInput('\0'); // Trigger with null character
    }

    /**
     * Check if auto-completion is currently active
     */
    public boolean isActive() {
        return isActive;
    }
}