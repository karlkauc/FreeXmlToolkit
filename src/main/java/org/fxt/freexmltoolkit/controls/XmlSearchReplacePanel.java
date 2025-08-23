package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Advanced Search & Replace Panel for XML Editor with professional features.
 * Provides regex support, XML-specific search, scope selection, and preview functionality.
 */
public class XmlSearchReplacePanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlSearchReplacePanel.class);

    // UI Components
    private TextField searchField;
    private TextField replaceField;
    private CheckBox regexCheckBox;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox wholeWordCheckBox;
    private CheckBox xmlAwareCheckBox;
    private ComboBox<String> searchScopeComboBox;
    private ComboBox<String> xmlSearchTypeComboBox;
    private Label resultsLabel;
    private ListView<SearchResult> resultsListView;

    // Action Buttons
    private Button findNextButton;
    private Button findPreviousButton;
    private Button replaceButton;
    private Button replaceAllButton;
    private Button closeButton;

    // Search State
    private CodeArea targetCodeArea;
    private final List<SearchResult> searchResults = new ArrayList<>();
    private int currentResultIndex = -1;
    private boolean isVisible = false;

    // Search Options
    private boolean regexEnabled = false;
    private boolean caseSensitive = false;
    private boolean wholeWordOnly = false;
    private boolean xmlAware = false;
    private SearchScope currentScope = SearchScope.DOCUMENT;
    private XmlSearchType currentXmlSearchType = XmlSearchType.CONTENT;

    /**
         * Search result representation
         */
        public record SearchResult(int startIndex, int endIndex, String matchedText, String context, int lineNumber,
                                   SearchResultType type) {
            public enum SearchResultType {
                TEXT, ELEMENT_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, COMMENT, CDATA
            }

        @Override
            public String toString() {
                return String.format("Line %d: %s (%s)", lineNumber + 1,
                        context.trim().substring(0, Math.min(50, context.trim().length())) + "...",
                        type.toString().toLowerCase());
            }
        }

    /**
     * Search scope enumeration
     */
    public enum SearchScope {
        DOCUMENT("Entire Document"),
        SELECTION("Selection Only"),
        FROM_CURSOR("From Cursor"),
        VISIBLE_AREA("Visible Area");

        private final String displayName;

        SearchScope(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * XML-specific search types
     */
    public enum XmlSearchType {
        CONTENT("Text Content"),
        ELEMENT_NAMES("Element Names"),
        ATTRIBUTE_NAMES("Attribute Names"),
        ATTRIBUTE_VALUES("Attribute Values"),
        COMMENTS("Comments"),
        CDATA("CDATA Sections"),
        ALL("All XML Parts");

        private final String displayName;

        XmlSearchType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public XmlSearchReplacePanel() {
        initializeUI();
        setupEventHandlers();
        setupKeyboardShortcuts();

        // Initially hidden
        setVisible(false);
        setManaged(false);

        logger.info("Advanced XML Search & Replace Panel initialized");
    }

    /**
     * Initialize the user interface
     */
    private void initializeUI() {
        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().addAll("xml-search-replace-panel", "search-panel");

        // Title bar with close button
        HBox titleBar = createTitleBar();

        // Search input section
        VBox searchSection = createSearchSection();

        // Replace input section  
        VBox replaceSection = createReplaceSection();

        // Options section
        HBox optionsSection = createOptionsSection();

        // XML-specific options
        HBox xmlOptionsSection = createXmlOptionsSection();

        // Action buttons
        HBox actionButtons = createActionButtons();

        // Results section
        VBox resultsSection = createResultsSection();

        getChildren().addAll(
                titleBar,
                new Separator(),
                searchSection,
                replaceSection,
                optionsSection,
                xmlOptionsSection,
                actionButtons,
                new Separator(),
                resultsSection
        );
    }

    /**
     * Create title bar with close button
     */
    private HBox createTitleBar() {
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("search-title-bar");

        Label titleLabel = new Label("Advanced Search & Replace");
        titleLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
        titleLabel.getStyleClass().add("search-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        closeButton = new Button();
        closeButton.setGraphic(new FontIcon("bi-x"));
        closeButton.getStyleClass().addAll("search-close-button", "button-icon");
        closeButton.setTooltip(new Tooltip("Close Search Panel (Esc)"));

        titleBar.getChildren().addAll(titleLabel, spacer, closeButton);
        return titleBar;
    }

    /**
     * Create search input section
     */
    private VBox createSearchSection() {
        VBox section = new VBox(5);

        Label searchLabel = new Label("Search for:");
        searchLabel.getStyleClass().add("search-label");

        searchField = new TextField();
        searchField.setPromptText("Enter search term or regex pattern...");
        searchField.getStyleClass().addAll("search-field", "xml-text-field");

        section.getChildren().addAll(searchLabel, searchField);
        return section;
    }

    /**
     * Create replace input section
     */
    private VBox createReplaceSection() {
        VBox section = new VBox(5);

        Label replaceLabel = new Label("Replace with:");
        replaceLabel.getStyleClass().add("search-label");

        replaceField = new TextField();
        replaceField.setPromptText("Enter replacement text...");
        replaceField.getStyleClass().addAll("replace-field", "xml-text-field");

        section.getChildren().addAll(replaceLabel, replaceField);
        return section;
    }

    /**
     * Create options section
     */
    private HBox createOptionsSection() {
        HBox section = new HBox(15);
        section.setAlignment(Pos.CENTER_LEFT);
        section.getStyleClass().add("search-options");

        regexCheckBox = new CheckBox("Regex");
        regexCheckBox.getStyleClass().add("search-checkbox");
        regexCheckBox.setTooltip(new Tooltip("Use regular expressions"));

        caseSensitiveCheckBox = new CheckBox("Match Case");
        caseSensitiveCheckBox.getStyleClass().add("search-checkbox");
        caseSensitiveCheckBox.setTooltip(new Tooltip("Case sensitive search"));

        wholeWordCheckBox = new CheckBox("Whole Word");
        wholeWordCheckBox.getStyleClass().add("search-checkbox");
        wholeWordCheckBox.setTooltip(new Tooltip("Match whole words only"));

        section.getChildren().addAll(regexCheckBox, caseSensitiveCheckBox, wholeWordCheckBox);
        return section;
    }

    /**
     * Create XML-specific options section
     */
    private HBox createXmlOptionsSection() {
        HBox section = new HBox(10);
        section.setAlignment(Pos.CENTER_LEFT);
        section.getStyleClass().add("xml-search-options");

        xmlAwareCheckBox = new CheckBox("XML-Aware Search");
        xmlAwareCheckBox.getStyleClass().add("search-checkbox");
        xmlAwareCheckBox.setTooltip(new Tooltip("Enable XML-specific search features"));

        Label scopeLabel = new Label("Scope:");
        scopeLabel.getStyleClass().add("search-label");

        searchScopeComboBox = new ComboBox<>();
        for (SearchScope scope : SearchScope.values()) {
            searchScopeComboBox.getItems().add(scope.getDisplayName());
        }
        searchScopeComboBox.setValue(SearchScope.DOCUMENT.getDisplayName());
        searchScopeComboBox.getStyleClass().addAll("search-combo", "xml-combo-box");

        Label xmlTypeLabel = new Label("XML Type:");
        xmlTypeLabel.getStyleClass().add("search-label");

        xmlSearchTypeComboBox = new ComboBox<>();
        for (XmlSearchType type : XmlSearchType.values()) {
            xmlSearchTypeComboBox.getItems().add(type.getDisplayName());
        }
        xmlSearchTypeComboBox.setValue(XmlSearchType.CONTENT.getDisplayName());
        xmlSearchTypeComboBox.getStyleClass().addAll("search-combo", "xml-combo-box");
        xmlSearchTypeComboBox.setDisable(true); // Initially disabled

        section.getChildren().addAll(
                xmlAwareCheckBox,
                new Separator(),
                scopeLabel, searchScopeComboBox,
                xmlTypeLabel, xmlSearchTypeComboBox
        );
        return section;
    }

    /**
     * Create action buttons
     */
    private HBox createActionButtons() {
        HBox section = new HBox(8);
        section.setAlignment(Pos.CENTER_LEFT);
        section.getStyleClass().add("search-actions");

        findNextButton = new Button("Find Next");
        findNextButton.setGraphic(new FontIcon("bi-arrow-down"));
        findNextButton.getStyleClass().addAll("search-button", "primary-button");
        findNextButton.setDefaultButton(true);

        findPreviousButton = new Button("Find Previous");
        findPreviousButton.setGraphic(new FontIcon("bi-arrow-up"));
        findPreviousButton.getStyleClass().addAll("search-button", "secondary-button");

        replaceButton = new Button("Replace");
        replaceButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        replaceButton.getStyleClass().addAll("search-button", "secondary-button");
        replaceButton.setDisable(true);

        replaceAllButton = new Button("Replace All");
        replaceAllButton.setGraphic(new FontIcon("bi-arrow-repeat"));
        replaceAllButton.getStyleClass().addAll("search-button", "warning-button");
        replaceAllButton.setDisable(true);

        section.getChildren().addAll(
                findNextButton, findPreviousButton,
                new Separator(),
                replaceButton, replaceAllButton
        );
        return section;
    }

    /**
     * Create results section
     */
    private VBox createResultsSection() {
        VBox section = new VBox(5);
        section.getStyleClass().add("search-results-section");

        resultsLabel = new Label("No results");
        resultsLabel.getStyleClass().add("search-results-label");

        resultsListView = new ListView<>();
        resultsListView.setPrefHeight(100);
        resultsListView.getStyleClass().addAll("search-results-list", "xml-list-view");
        resultsListView.setVisible(false);
        resultsListView.setManaged(false);

        section.getChildren().addAll(resultsLabel, resultsListView);
        return section;
    }

    /**
     * Set up event handlers
     */
    private void setupEventHandlers() {
        // Close button
        closeButton.setOnAction(e -> hide());

        // Search field - trigger search on text change
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty()) {
                performSearch();
            } else {
                clearResults();
            }
        });

        // Option checkboxes
        regexCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            regexEnabled = newVal;
            if (!searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        caseSensitiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            caseSensitive = newVal;
            if (!searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        wholeWordCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            wholeWordOnly = newVal;
            if (!searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        xmlAwareCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            xmlAware = newVal;
            xmlSearchTypeComboBox.setDisable(!newVal);
            if (!searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        // Scope and type combo boxes
        searchScopeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentScope = SearchScope.valueOf(newVal.toUpperCase().replace(" ", "_"));
            if (!searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        xmlSearchTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            for (XmlSearchType type : XmlSearchType.values()) {
                if (type.getDisplayName().equals(newVal)) {
                    currentXmlSearchType = type;
                    break;
                }
            }
            if (xmlAware && !searchField.getText().isEmpty()) {
                performSearch();
            }
        });

        // Action buttons
        findNextButton.setOnAction(e -> findNext());
        findPreviousButton.setOnAction(e -> findPrevious());
        replaceButton.setOnAction(e -> replaceSelected());
        replaceAllButton.setOnAction(e -> replaceAll());

        // Results list selection
        resultsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldResult, newResult) -> {
            if (newResult != null && targetCodeArea != null) {
                jumpToResult(newResult);
            }
        });
    }

    /**
     * Set up keyboard shortcuts
     */
    private void setupKeyboardShortcuts() {
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    findPrevious();
                } else {
                    findNext();
                }
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.R) {
                if (event.isShiftDown()) {
                    replaceAll();
                } else {
                    replaceSelected();
                }
                event.consume();
            }
        });
    }

    // ========== Public API ==========

    /**
     * Show the search panel and focus on search field
     */
    public void show() {
        if (!isVisible) {
            setVisible(true);
            setManaged(true);
            isVisible = true;

            Platform.runLater(() -> {
                searchField.requestFocus();
                if (targetCodeArea != null && targetCodeArea.getSelectedText() != null &&
                        !targetCodeArea.getSelectedText().isEmpty()) {
                    searchField.setText(targetCodeArea.getSelectedText());
                    searchField.selectAll();
                }
            });

            logger.debug("Search panel shown");
        }
    }

    /**
     * Hide the search panel
     */
    public void hide() {
        if (isVisible) {
            setVisible(false);
            setManaged(false);
            isVisible = false;
            clearHighlights();

            if (targetCodeArea != null) {
                targetCodeArea.requestFocus();
            }

            logger.debug("Search panel hidden");
        }
    }

    /**
     * Toggle search panel visibility
     */
    public void toggle() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Set the target code area for searching
     */
    public void setTargetCodeArea(CodeArea codeArea) {
        this.targetCodeArea = codeArea;
        logger.debug("Target code area set for search panel");
    }

    /**
     * Get current search text
     */
    public String getSearchText() {
        return searchField.getText();
    }

    /**
     * Set search text
     */
    public void setSearchText(String text) {
        searchField.setText(text);
    }

    /**
     * Get current replace text
     */
    public String getReplaceText() {
        return replaceField.getText();
    }

    /**
     * Set replace text
     */
    public void setReplaceText(String text) {
        replaceField.setText(text);
    }

    // ========== Search Implementation ==========

    /**
     * Perform search operation
     */
    private void performSearch() {
        if (targetCodeArea == null || searchField.getText().isEmpty()) {
            clearResults();
            return;
        }

        try {
            String searchText = searchField.getText();
            String documentText = targetCodeArea.getText();

            clearResults();

            if (xmlAware) {
                performXmlAwareSearch(searchText, documentText);
            } else {
                performStandardSearch(searchText, documentText);
            }

            updateResultsDisplay();
            highlightResults();

            logger.debug("Search completed: {} results found for '{}'", searchResults.size(), searchText);

        } catch (PatternSyntaxException e) {
            logger.warn("Invalid regex pattern: {}", e.getMessage());
            resultsLabel.setText("Invalid regex pattern");
            resultsLabel.getStyleClass().add("error");
        } catch (Exception e) {
            logger.error("Search error", e);
            resultsLabel.setText("Search error: " + e.getMessage());
            resultsLabel.getStyleClass().add("error");
        }
    }

    /**
     * Perform standard text search
     */
    private void performStandardSearch(String searchText, String documentText) {
        Pattern pattern = createSearchPattern(searchText);
        Matcher matcher = pattern.matcher(documentText);

        while (matcher.find()) {
            int startIndex = matcher.start();
            int endIndex = matcher.end();
            String matchedText = matcher.group();
            int lineNumber = getLineNumber(documentText, startIndex);
            String context = getContextAround(documentText, startIndex, endIndex);

            SearchResult result = new SearchResult(
                    startIndex, endIndex, matchedText, context, lineNumber,
                    SearchResult.SearchResultType.TEXT
            );

            if (isInSearchScope(startIndex)) {
                searchResults.add(result);
            }
        }
    }

    /**
     * Perform XML-aware search
     */
    private void performXmlAwareSearch(String searchText, String documentText) {
        switch (currentXmlSearchType) {
            case CONTENT:
                searchXmlContent(searchText, documentText);
                break;
            case ELEMENT_NAMES:
                searchElementNames(searchText, documentText);
                break;
            case ATTRIBUTE_NAMES:
                searchAttributeNames(searchText, documentText);
                break;
            case ATTRIBUTE_VALUES:
                searchAttributeValues(searchText, documentText);
                break;
            case COMMENTS:
                searchComments(searchText, documentText);
                break;
            case CDATA:
                searchCData(searchText, documentText);
                break;
            case ALL:
                searchAllXmlParts(searchText, documentText);
                break;
        }
    }

    /**
     * Search XML text content (between tags)
     */
    private void searchXmlContent(String searchText, String documentText) {
        Pattern contentPattern = Pattern.compile(">([^<]+)<");
        Matcher contentMatcher = contentPattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (contentMatcher.find()) {
            String content = contentMatcher.group(1);
            Matcher searchMatcher = searchPattern.matcher(content);

            while (searchMatcher.find()) {
                int absoluteStart = contentMatcher.start(1) + searchMatcher.start();
                int absoluteEnd = contentMatcher.start(1) + searchMatcher.end();

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            searchMatcher.group(), SearchResult.SearchResultType.TEXT);
                }
            }
        }
    }

    /**
     * Search XML element names
     */
    private void searchElementNames(String searchText, String documentText) {
        Pattern elementPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9-_]*)");
        Matcher elementMatcher = elementPattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (elementMatcher.find()) {
            String elementName = elementMatcher.group(2);
            Matcher searchMatcher = searchPattern.matcher(elementName);

            if (searchMatcher.find()) {
                int absoluteStart = elementMatcher.start(2);
                int absoluteEnd = elementMatcher.end(2);

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            elementName, SearchResult.SearchResultType.ELEMENT_NAME);
                }
            }
        }
    }

    /**
     * Search XML attribute names
     */
    private void searchAttributeNames(String searchText, String documentText) {
        Pattern attrPattern = Pattern.compile("\\s+([a-zA-Z][a-zA-Z0-9-_]*)\\s*=");
        Matcher attrMatcher = attrPattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (attrMatcher.find()) {
            String attrName = attrMatcher.group(1);
            Matcher searchMatcher = searchPattern.matcher(attrName);

            if (searchMatcher.find()) {
                int absoluteStart = attrMatcher.start(1);
                int absoluteEnd = attrMatcher.end(1);

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            attrName, SearchResult.SearchResultType.ATTRIBUTE_NAME);
                }
            }
        }
    }

    /**
     * Search XML attribute values
     */
    private void searchAttributeValues(String searchText, String documentText) {
        Pattern valuePattern = Pattern.compile("=\\s*[\"']([^\"']*)[\"']");
        Matcher valueMatcher = valuePattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (valueMatcher.find()) {
            String value = valueMatcher.group(1);
            Matcher searchMatcher = searchPattern.matcher(value);

            while (searchMatcher.find()) {
                int absoluteStart = valueMatcher.start(1) + searchMatcher.start();
                int absoluteEnd = valueMatcher.start(1) + searchMatcher.end();

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            searchMatcher.group(), SearchResult.SearchResultType.ATTRIBUTE_VALUE);
                }
            }
        }
    }

    /**
     * Search XML comments
     */
    private void searchComments(String searchText, String documentText) {
        Pattern commentPattern = Pattern.compile("<!--([\\s\\S]*?)-->");
        Matcher commentMatcher = commentPattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (commentMatcher.find()) {
            String comment = commentMatcher.group(1);
            Matcher searchMatcher = searchPattern.matcher(comment);

            while (searchMatcher.find()) {
                int absoluteStart = commentMatcher.start(1) + searchMatcher.start();
                int absoluteEnd = commentMatcher.start(1) + searchMatcher.end();

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            searchMatcher.group(), SearchResult.SearchResultType.COMMENT);
                }
            }
        }
    }

    /**
     * Search CDATA sections
     */
    private void searchCData(String searchText, String documentText) {
        Pattern cdataPattern = Pattern.compile("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>");
        Matcher cdataMatcher = cdataPattern.matcher(documentText);
        Pattern searchPattern = createSearchPattern(searchText);

        while (cdataMatcher.find()) {
            String cdata = cdataMatcher.group(1);
            Matcher searchMatcher = searchPattern.matcher(cdata);

            while (searchMatcher.find()) {
                int absoluteStart = cdataMatcher.start(1) + searchMatcher.start();
                int absoluteEnd = cdataMatcher.start(1) + searchMatcher.end();

                if (isInSearchScope(absoluteStart)) {
                    addSearchResult(documentText, absoluteStart, absoluteEnd,
                            searchMatcher.group(), SearchResult.SearchResultType.CDATA);
                }
            }
        }
    }

    /**
     * Search all XML parts
     */
    private void searchAllXmlParts(String searchText, String documentText) {
        searchXmlContent(searchText, documentText);
        searchElementNames(searchText, documentText);
        searchAttributeNames(searchText, documentText);
        searchAttributeValues(searchText, documentText);
        searchComments(searchText, documentText);
        searchCData(searchText, documentText);

        // Sort results by position
        searchResults.sort((a, b) -> Integer.compare(a.startIndex(), b.startIndex()));
    }

    // ========== Helper Methods ==========

    /**
     * Create search pattern based on options
     */
    private Pattern createSearchPattern(String searchText) {
        int flags = 0;
        if (!caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }

        String patternStr = searchText;
        if (!regexEnabled) {
            patternStr = Pattern.quote(searchText);
        }

        if (wholeWordOnly && !regexEnabled) {
            patternStr = "\\b" + patternStr + "\\b";
        }

        return Pattern.compile(patternStr, flags);
    }

    /**
     * Add search result helper method
     */
    private void addSearchResult(String documentText, int startIndex, int endIndex,
                                 String matchedText, SearchResult.SearchResultType type) {
        int lineNumber = getLineNumber(documentText, startIndex);
        String context = getContextAround(documentText, startIndex, endIndex);

        SearchResult result = new SearchResult(
                startIndex, endIndex, matchedText, context, lineNumber, type
        );

        searchResults.add(result);
    }

    /**
     * Check if position is in current search scope
     */
    private boolean isInSearchScope(int position) {
        if (targetCodeArea == null) return true;

        switch (currentScope) {
            case DOCUMENT:
                return true;
            case SELECTION:
                if (targetCodeArea.getSelection().getLength() > 0) {
                    int selStart = targetCodeArea.getSelection().getStart();
                    int selEnd = targetCodeArea.getSelection().getEnd();
                    return position >= selStart && position <= selEnd;
                }
                return true;
            case FROM_CURSOR:
                return position >= targetCodeArea.getCaretPosition();
            case VISIBLE_AREA:
                // Simplified - would need more complex implementation for actual visible area
                return true;
            default:
                return true;
        }
    }

    /**
     * Get line number for text position
     */
    private int getLineNumber(String text, int position) {
        int lineNumber = 0;
        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    /**
     * Get context around match position
     */
    private String getContextAround(String text, int startIndex, int endIndex) {
        int contextStart = Math.max(0, startIndex - 30);
        int contextEnd = Math.min(text.length(), endIndex + 30);

        String context = text.substring(contextStart, contextEnd);

        // Remove newlines for display
        context = context.replaceAll("\\r?\\n", " ");

        return context;
    }

    /**
     * Clear all search results
     */
    private void clearResults() {
        searchResults.clear();
        currentResultIndex = -1;
        clearHighlights();
        updateResultsDisplay();

        replaceButton.setDisable(true);
        replaceAllButton.setDisable(true);
    }

    /**
     * Update results display
     */
    private void updateResultsDisplay() {
        Platform.runLater(() -> {
            if (searchResults.isEmpty()) {
                resultsLabel.setText("No results");
                resultsLabel.getStyleClass().remove("error");
                resultsListView.setVisible(false);
                resultsListView.setManaged(false);
            } else {
                resultsLabel.setText(String.format("%d result%s found",
                        searchResults.size(), searchResults.size() == 1 ? "" : "s"));
                resultsLabel.getStyleClass().remove("error");

                resultsListView.getItems().clear();
                resultsListView.getItems().addAll(searchResults);
                resultsListView.setVisible(true);
                resultsListView.setManaged(true);

                replaceButton.setDisable(false);
                replaceAllButton.setDisable(false);
            }
        });
    }

    /**
     * Highlight search results in code area
     */
    private void highlightResults() {
        // In a full implementation, this would apply visual highlighting to the code area
        // For now, we log the highlighting action
        logger.debug("Highlighting {} search results", searchResults.size());
    }

    /**
     * Clear highlights from code area
     */
    private void clearHighlights() {
        // In a full implementation, this would remove visual highlighting
        logger.debug("Cleared search result highlights");
    }

    /**
     * Find next result
     */
    private void findNext() {
        if (searchResults.isEmpty()) return;

        currentResultIndex = (currentResultIndex + 1) % searchResults.size();
        jumpToCurrentResult();
    }

    /**
     * Find previous result
     */
    private void findPrevious() {
        if (searchResults.isEmpty()) return;

        currentResultIndex = currentResultIndex <= 0 ?
                searchResults.size() - 1 : currentResultIndex - 1;
        jumpToCurrentResult();
    }

    /**
     * Jump to current search result
     */
    private void jumpToCurrentResult() {
        if (currentResultIndex >= 0 && currentResultIndex < searchResults.size()) {
            SearchResult result = searchResults.get(currentResultIndex);
            jumpToResult(result);

            // Update list selection
            resultsListView.getSelectionModel().select(currentResultIndex);
        }
    }

    /**
     * Jump to specific search result
     */
    private void jumpToResult(SearchResult result) {
        if (targetCodeArea != null) {
            targetCodeArea.moveTo(result.startIndex());
            targetCodeArea.selectRange(result.startIndex(), result.endIndex());
            targetCodeArea.requestFollowCaret();

            logger.debug("Jumped to search result at position {}-{}",
                    result.startIndex(), result.endIndex());
        }
    }

    /**
     * Replace selected/current result
     */
    private void replaceSelected() {
        if (currentResultIndex >= 0 && currentResultIndex < searchResults.size() &&
                targetCodeArea != null && !replaceField.getText().isEmpty()) {

            SearchResult result = searchResults.get(currentResultIndex);
            String replacementText = replaceField.getText();

            // Handle regex replacement groups
            if (regexEnabled) {
                try {
                    Pattern pattern = createSearchPattern(searchField.getText());
                    String originalText = targetCodeArea.getText(result.startIndex(), result.endIndex());
                    replacementText = pattern.matcher(originalText).replaceFirst(replacementText);
                } catch (Exception e) {
                    logger.warn("Regex replacement error", e);
                }
            }

            targetCodeArea.replaceText(result.startIndex(), result.endIndex(), replacementText);

            logger.debug("Replaced text at position {}-{} with '{}'",
                    result.startIndex(), result.endIndex(), replacementText);

            // Refresh search to update results
            performSearch();
        }
    }

    /**
     * Replace all occurrences
     */
    private void replaceAll() {
        if (searchResults.isEmpty() || targetCodeArea == null || replaceField.getText().isEmpty()) {
            return;
        }

        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Replace All");
        confirmDialog.setHeaderText("Replace all occurrences?");
        confirmDialog.setContentText(String.format("This will replace %d occurrence%s of '%s' with '%s'.",
                searchResults.size(), searchResults.size() == 1 ? "" : "s",
                searchField.getText(), replaceField.getText()));

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            int replacements = 0;
            String replacementText = replaceField.getText();

            // Replace from end to beginning to maintain correct positions
            for (int i = searchResults.size() - 1; i >= 0; i--) {
                SearchResult result = searchResults.get(i);

                String finalReplacementText = replacementText;
                if (regexEnabled) {
                    try {
                        Pattern pattern = createSearchPattern(searchField.getText());
                        String originalText = targetCodeArea.getText(result.startIndex(), result.endIndex());
                        finalReplacementText = pattern.matcher(originalText).replaceFirst(replacementText);
                    } catch (Exception e) {
                        logger.warn("Regex replacement error for result {}", i, e);
                        continue;
                    }
                }

                targetCodeArea.replaceText(result.startIndex(), result.endIndex(), finalReplacementText);
                replacements++;
            }

            logger.info("Replaced {} occurrences", replacements);

            // Refresh search
            performSearch();

            // Show success message
            resultsLabel.setText(String.format("Replaced %d occurrence%s",
                    replacements, replacements == 1 ? "" : "s"));
        }
    }

    /**
     * Check if panel is currently visible
     */
    public boolean isSearchVisible() {
        return isVisible;
    }
}