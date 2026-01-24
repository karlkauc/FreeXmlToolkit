package org.fxt.freexmltoolkit.controls;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.XPathExecutionEngine;
import org.fxt.freexmltoolkit.service.XPathExecutionResult;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Revolutionary Snippet Manager Panel - the unique selling point of the XML Editor.
 * Provides one-click XPath/XQuery execution with advanced features like:
 * - Context-aware snippet suggestions
 * - Performance profiling
 * - Smart categorization and search
 * - Parameter substitution
 * - Execution history
 */
public class SnippetManagerPanel extends VBox {

    private final XPathSnippetRepository snippetRepository;
    private final XPathExecutionEngine executionEngine;
    private final ExecutorService executorService;

    // UI Components
    private TextField searchField;
    private ComboBox<XPathSnippet.SnippetCategory> categoryFilter;
    private ComboBox<XPathSnippet.SnippetType> typeFilter;
    private ListView<XPathSnippet> snippetListView;
    private TextArea snippetPreview;
    private TextArea resultDisplay;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button executeButton;
    private Button favoriteButton;
    private VBox parametersPanel;

    // Data
    private final ObservableList<XPathSnippet> allSnippets;
    private final FilteredList<XPathSnippet> filteredSnippets;
    private XPathSnippet selectedSnippet;
    private String currentXmlContent;
    private Consumer<XPathExecutionResult> resultCallback;

    /**
     * Creates a new SnippetManagerPanel with default configuration.
     * Initializes the snippet repository, execution engine, and UI components.
     */
    public SnippetManagerPanel() {
        this.snippetRepository = XPathSnippetRepository.getInstance();
        this.executionEngine = new XPathExecutionEngine();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SnippetExecution");
            t.setDaemon(true);
            return t;
        });

        // Initialize data
        this.allSnippets = FXCollections.observableArrayList(snippetRepository.getAllSnippets());
        this.filteredSnippets = new FilteredList<>(allSnippets);

        initializeUI();
        setupEventHandlers();
        loadSnippets();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(10));
        setMaxWidth(400); // Side panel width

        // Header
        Label headerLabel = new Label("XPath/XQuery Snippets");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Search and Filter Section
        VBox filterSection = createFilterSection();

        // Snippet List
        VBox listSection = createSnippetListSection();

        // Preview and Execution Section
        VBox executionSection = createExecutionSection();

        // Status Bar
        HBox statusBar = createStatusBar();

        getChildren().addAll(
                headerLabel,
                new Separator(),
                filterSection,
                listSection,
                executionSection,
                statusBar
        );
    }

    private VBox createFilterSection() {
        VBox section = new VBox(5);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search snippets...");
        searchField.setPrefColumnCount(20);

        // Filter controls
        HBox filterControls = new HBox(5);

        categoryFilter = new ComboBox<>();
        categoryFilter.setPromptText("Category");
        categoryFilter.getItems().add(null); // "All" option
        categoryFilter.getItems().addAll(XPathSnippet.SnippetCategory.values());
        categoryFilter.setPrefWidth(120);

        typeFilter = new ComboBox<>();
        typeFilter.setPromptText("Type");
        typeFilter.getItems().add(null); // "All" option
        typeFilter.getItems().addAll(XPathSnippet.SnippetType.values());
        typeFilter.setPrefWidth(80);

        filterControls.getChildren().addAll(categoryFilter, typeFilter);

        section.getChildren().addAll(searchField, filterControls);
        return section;
    }

    private VBox createSnippetListSection() {
        VBox section = new VBox(5);

        Label listLabel = new Label("Available Snippets");
        listLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        snippetListView = new ListView<>(filteredSnippets);
        snippetListView.setPrefHeight(200);
        snippetListView.setCellFactory(listView -> new SnippetListCell());

        section.getChildren().addAll(listLabel, snippetListView);
        return section;
    }

    private VBox createExecutionSection() {
        VBox section = new VBox(5);

        // Preview section
        Label previewLabel = new Label("Snippet Preview");
        previewLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        snippetPreview = new TextArea();
        snippetPreview.setPrefRowCount(3);
        snippetPreview.setEditable(false);
        snippetPreview.setWrapText(true);

        // Parameters panel (dynamic)
        parametersPanel = new VBox(3);

        // Execution controls
        HBox executionControls = new HBox(5);
        executionControls.setAlignment(Pos.CENTER_LEFT);

        executeButton = new Button("Execute");
        executeButton.setDefaultButton(true);
        executeButton.setDisable(true);

        favoriteButton = new Button("★");
        favoriteButton.setTooltip(new Tooltip("Add to favorites"));
        favoriteButton.setDisable(true);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(16, 16);
        progressIndicator.setVisible(false);

        executionControls.getChildren().addAll(executeButton, favoriteButton, progressIndicator);

        // Results display
        Label resultLabel = new Label("Execution Results");
        resultLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        resultDisplay = new TextArea();
        resultDisplay.setPrefRowCount(6);
        resultDisplay.setEditable(false);
        resultDisplay.setWrapText(true);

        section.getChildren().addAll(
                previewLabel, snippetPreview,
                parametersPanel,
                executionControls,
                resultLabel, resultDisplay
        );

        return section;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready");
        statusLabel.setFont(Font.font("System", 10));

        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    private void setupEventHandlers() {
        // Search functionality
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        // Filter functionality
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        // Snippet selection
        snippetListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSnippet, newSnippet) -> onSnippetSelected(newSnippet)
        );

        // Double-click execution
        snippetListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedSnippet != null) {
                executeSelectedSnippet();
            }
        });

        // Execution button
        executeButton.setOnAction(e -> executeSelectedSnippet());

        // Favorite button
        favoriteButton.setOnAction(e -> toggleFavorite());
    }

    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        XPathSnippet.SnippetCategory selectedCategory = categoryFilter.getValue();
        XPathSnippet.SnippetType selectedType = typeFilter.getValue();

        filteredSnippets.setPredicate(snippet -> {
            // Search filter
            if (!searchText.isEmpty()) {
                boolean matchesSearch = snippet.getName().toLowerCase().contains(searchText) ||
                        snippet.getDescription().toLowerCase().contains(searchText) ||
                        snippet.getQuery().toLowerCase().contains(searchText) ||
                        snippet.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(searchText));

                if (!matchesSearch) return false;
            }

            // Category filter
            if (selectedCategory != null && snippet.getCategory() != selectedCategory) {
                return false;
            }

            // Type filter
            return selectedType == null || snippet.getType() == selectedType;
        });

        updateStatusLabel();
    }

    private void onSnippetSelected(XPathSnippet snippet) {
        selectedSnippet = snippet;

        if (snippet == null) {
            snippetPreview.clear();
            resultDisplay.clear();
            parametersPanel.getChildren().clear();
            executeButton.setDisable(true);
            favoriteButton.setDisable(true);
            return;
        }

        // Update preview
        snippetPreview.setText(snippet.getQuery());

        // Build parameters panel
        buildParametersPanel(snippet);

        // Enable controls
        executeButton.setDisable(currentXmlContent == null || currentXmlContent.trim().isEmpty());
        favoriteButton.setDisable(false);

        // Update favorite button state
        updateFavoriteButton(snippet);
    }

    private void buildParametersPanel(XPathSnippet snippet) {
        parametersPanel.getChildren().clear();

        if (snippet.getParameters().isEmpty()) {
            return;
        }

        Label paramLabel = new Label("Parameters:");
        paramLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        parametersPanel.getChildren().add(paramLabel);

        for (var parameter : snippet.getParameters()) {
            HBox paramBox = new HBox(5);
            paramBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(parameter.getFormattedName() + ":");
            nameLabel.setPrefWidth(80);
            nameLabel.setTooltip(new Tooltip(parameter.getTooltipText()));

            TextField valueField = new TextField(parameter.getDefaultValue());
            valueField.setPromptText(parameter.getExample() != null ? parameter.getExample() : "Enter " + parameter.getName());
            valueField.setPrefColumnCount(15);
            valueField.setUserData(parameter); // Store parameter reference

            paramBox.getChildren().addAll(nameLabel, valueField);
            parametersPanel.getChildren().add(paramBox);
        }
    }

    private void executeSelectedSnippet() {
        if (selectedSnippet == null || currentXmlContent == null) {
            showStatus("No snippet selected or XML content available", true);
            return;
        }

        // Collect parameter values
        Map<String, String> parameterValues = new HashMap<>();
        for (var node : parametersPanel.getChildren()) {
            if (node instanceof HBox hbox) {
                for (var child : hbox.getChildren()) {
                    if (child instanceof TextField textField && textField.getUserData() != null) {
                        var parameter = (org.fxt.freexmltoolkit.domain.SnippetParameter) textField.getUserData();
                        String value = textField.getText();
                        if (value != null && !value.trim().isEmpty()) {
                            parameterValues.put(parameter.getName(), value);
                        }
                    }
                }
            }
        }

        // Validate parameters
        for (var parameter : selectedSnippet.getParameters()) {
            String value = parameterValues.get(parameter.getName());
            var validationResult = parameter.validateValue(value);
            if (!validationResult.isValid()) {
                showStatus("Parameter validation failed: " + validationResult.getErrorMessage(), true);
                return;
            }
        }

        // Execute snippet asynchronously
        showStatus("Executing snippet: " + selectedSnippet.getName(), false);
        progressIndicator.setVisible(true);
        executeButton.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                return executionEngine.executeSnippet(selectedSnippet, currentXmlContent, parameterValues);
            } catch (Exception e) {
                return XPathExecutionResult.error("Execution failed: " + e.getMessage());
            }
        }, executorService).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                displayExecutionResult(result);
                progressIndicator.setVisible(false);
                executeButton.setDisable(false);

                if (resultCallback != null) {
                    resultCallback.accept(result);
                }
            });
        }, Platform::runLater);
    }

    private void displayExecutionResult(XPathExecutionResult result) {
        if (result.isSuccess()) {
            StringBuilder output = new StringBuilder();
            output.append("✓ ").append(result.getResultSummary()).append("\n\n");

            if (result.getResultCount() > 0) {
                output.append("Results:\n");
                output.append("--------\n");

                for (int i = 0; i < Math.min(result.getResultCount(), 100); i++) {
                    var item = result.getResultItems().get(i);
                    output.append("[").append(i + 1).append("] ");
                    if (item.getNodeName() != null && !item.getNodeName().isEmpty()) {
                        output.append(item.getNodeName()).append(": ");
                    }
                    output.append(item.getValue()).append("\n");
                }

                if (result.getResultCount() > 100) {
                    output.append("... and ").append(result.getResultCount() - 100).append(" more items\n");
                }
            }

            output.append("\n").append(result.getExecutionStatistics());
            resultDisplay.setText(output.toString());
            showStatus("Execution completed successfully - " + result.getResultSummary(), false);
        } else {
            resultDisplay.setText("✗ Error: " + result.getErrorMessage());
            showStatus("Execution failed: " + result.getErrorMessage(), true);
        }
    }

    private void toggleFavorite() {
        if (selectedSnippet == null) return;

        boolean isFavorite = selectedSnippet.isFavorite();
        selectedSnippet.setFavorite(!isFavorite);

        // Save to repository
        snippetRepository.updateSnippet(selectedSnippet);

        updateFavoriteButton(selectedSnippet);
        showStatus(selectedSnippet.isFavorite() ? "Added to favorites" : "Removed from favorites", false);
    }

    private void updateFavoriteButton(XPathSnippet snippet) {
        if (snippet.isFavorite()) {
            favoriteButton.setText("★");
            favoriteButton.setTooltip(new Tooltip("Remove from favorites"));
            favoriteButton.setStyle("-fx-text-fill: gold;");
        } else {
            favoriteButton.setText("☆");
            favoriteButton.setTooltip(new Tooltip("Add to favorites"));
            favoriteButton.setStyle("-fx-text-fill: gray;");
        }
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: black;");

            // Auto-clear status after 5 seconds
            if (!isError) {
                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
                    if (statusLabel.getText().equals(message)) {
                        statusLabel.setText("Ready");
                        statusLabel.setStyle("-fx-text-fill: black;");
                    }
                }));
                timeline.play();
            }
        });
    }

    private void updateStatusLabel() {
        int total = allSnippets.size();
        int filtered = filteredSnippets.size();

        if (total == filtered) {
            statusLabel.setText(total + " snippets available");
        } else {
            statusLabel.setText(filtered + " of " + total + " snippets shown");
        }
    }

    private void loadSnippets() {
        List<XPathSnippet> snippets = snippetRepository.getAllSnippets();
        Platform.runLater(() -> {
            allSnippets.setAll(snippets);
            updateStatusLabel();
        });
    }

    // ========== Public API ==========

    /**
     * Sets the current XML content for snippet execution.
     * This content is used as the input document when executing XPath/XQuery snippets.
     *
     * @param xmlContent the XML content to use for snippet execution, may be null or empty
     */
    public void setXmlContent(String xmlContent) {
        this.currentXmlContent = xmlContent;
        executeButton.setDisable(selectedSnippet == null || xmlContent == null || xmlContent.trim().isEmpty());
    }

    /**
     * Sets the callback to be invoked when a snippet execution completes.
     * The callback receives the execution result containing either the query results or error information.
     *
     * @param callback the consumer to receive execution results, or null to disable callbacks
     */
    public void setResultCallback(Consumer<XPathExecutionResult> callback) {
        this.resultCallback = callback;
    }

    /**
     * Executes a snippet by its name.
     * This method is useful for context menu integration where snippets can be executed
     * directly by their name without requiring manual selection.
     *
     * @param snippetName the name of the snippet to execute
     */
    public void executeSnippetByName(String snippetName) {
        XPathSnippet snippet = snippetRepository.getSnippetByName(snippetName);
        if (snippet != null) {
            snippetListView.getSelectionModel().select(snippet);
            executeSelectedSnippet();
        }
    }

    /**
     * Returns context-aware snippet suggestions based on the provided XML context.
     * The suggestions are determined by analyzing the XML content and returning
     * snippets that are most relevant to the current editing context.
     *
     * @param xmlContext the XML context to analyze for snippet suggestions
     * @return a list of snippets that are relevant to the given context
     */
    public List<XPathSnippet> getContextSuggestions(String xmlContext) {
        if (xmlContext == null || xmlContext.trim().isEmpty()) {
            // Return popular and favorite snippets when no context
            List<XPathSnippet> suggestions = new ArrayList<>();
            suggestions.addAll(snippetRepository.getFavoriteSnippets());
            suggestions.addAll(snippetRepository.getMostPopularSnippets(5));
            return suggestions.stream().distinct().limit(10).collect(Collectors.toList());
        }

        // Analyze the XML context
        XmlContextAnalysis analysis = analyzeXmlContext(xmlContext);

        // Score and rank snippets based on context
        Map<XPathSnippet, Integer> scoredSnippets = new HashMap<>();

        for (XPathSnippet snippet : snippetRepository.getAllSnippets()) {
            int score = calculateContextScore(snippet, analysis);
            if (score > 0) {
                scoredSnippets.put(snippet, score);
            }
        }

        // Sort by score (descending) and return top suggestions
        return scoredSnippets.entrySet().stream()
                .sorted((a, b) -> {
                    int scoreCompare = Integer.compare(b.getValue(), a.getValue());
                    if (scoreCompare != 0) return scoreCompare;
                    // Tie-breaker: favorites first, then by usage
                    if (a.getKey().isFavorite() != b.getKey().isFavorite()) {
                        return a.getKey().isFavorite() ? -1 : 1;
                    }
                    return Long.compare(b.getKey().getExecutionCount(), a.getKey().getExecutionCount());
                })
                .map(Map.Entry::getKey)
                .limit(15)
                .collect(Collectors.toList());
    }

    /**
     * Analyzes the XML context to extract relevant information for snippet matching.
     */
    private XmlContextAnalysis analyzeXmlContext(String xmlContext) {
        XmlContextAnalysis analysis = new XmlContextAnalysis();

        // Extract element names
        java.util.regex.Pattern elementPattern = java.util.regex.Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:-]*)(?:\\s|>|/)");
        java.util.regex.Matcher elementMatcher = elementPattern.matcher(xmlContext);
        while (elementMatcher.find()) {
            String elementName = elementMatcher.group(1);
            if (!elementName.startsWith("/")) {
                analysis.elementNames.add(elementName.toLowerCase());
            }
        }

        // Extract attribute names
        java.util.regex.Pattern attrPattern = java.util.regex.Pattern.compile("\\s([a-zA-Z][a-zA-Z0-9_:-]*)\\s*=");
        java.util.regex.Matcher attrMatcher = attrPattern.matcher(xmlContext);
        while (attrMatcher.find()) {
            analysis.attributeNames.add(attrMatcher.group(1).toLowerCase());
        }

        // Extract namespace prefixes
        java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile("xmlns:([a-zA-Z][a-zA-Z0-9_-]*)\\s*=");
        java.util.regex.Matcher nsMatcher = nsPattern.matcher(xmlContext);
        while (nsMatcher.find()) {
            analysis.namespaces.add(nsMatcher.group(1).toLowerCase());
        }

        // Detect context type
        analysis.hasAttributes = !analysis.attributeNames.isEmpty();
        analysis.hasNamespaces = !analysis.namespaces.isEmpty();
        analysis.inComment = xmlContext.contains("<!--") && !xmlContext.contains("-->");
        analysis.inCdata = xmlContext.contains("<![CDATA[") && !xmlContext.contains("]]>");
        analysis.isNested = countOccurrences(xmlContext, '<') > 2;
        analysis.hasTextContent = xmlContext.matches(".*>[^<]+<.*");

        // Detect specific patterns
        analysis.hasNumericContent = xmlContext.matches(".*>\\s*\\d+(\\.\\d+)?\\s*<.*");
        analysis.hasDateContent = xmlContext.matches(".*>\\s*\\d{4}-\\d{2}-\\d{2}.*<.*");

        return analysis;
    }

    /**
     * Calculates a relevance score for a snippet based on the XML context analysis.
     */
    private int calculateContextScore(XPathSnippet snippet, XmlContextAnalysis analysis) {
        int score = 0;

        // Base score for all snippets
        score += 1;

        // Bonus for favorites
        if (snippet.isFavorite()) {
            score += 5;
        }

        // Bonus for popular snippets
        if (snippet.getExecutionCount() > 10) {
            score += 3;
        } else if (snippet.getExecutionCount() > 0) {
            score += 1;
        }

        // Category-based scoring
        switch (snippet.getCategory()) {
            case NAVIGATION:
                if (analysis.isNested) score += 4;
                if (analysis.elementNames.size() > 3) score += 2;
                break;
            case EXTRACTION:
                if (analysis.hasTextContent) score += 4;
                if (analysis.hasAttributes) score += 3;
                break;
            case FILTERING:
                if (analysis.hasAttributes) score += 4;
                if (analysis.elementNames.size() > 2) score += 2;
                break;
            case TRANSFORMATION:
                if (analysis.hasNumericContent) score += 3;
                if (analysis.hasDateContent) score += 2;
                break;
            case VALIDATION:
                if (analysis.hasAttributes) score += 2;
                break;
            case ANALYSIS:
                if (analysis.hasNamespaces) score += 4;
                if (analysis.elementNames.size() > 5) score += 3;
                break;
            case UTILITY:
                score += 1; // Always somewhat relevant
                break;
            default:
                break;
        }

        // Tag-based scoring - match tags with detected elements/attributes
        for (String tag : snippet.getTags()) {
            String tagLower = tag.toLowerCase();
            if (analysis.elementNames.contains(tagLower)) {
                score += 5;
            }
            if (analysis.attributeNames.contains(tagLower)) {
                score += 4;
            }
            if (analysis.namespaces.contains(tagLower)) {
                score += 3;
            }
            // Common tag matches
            if (tagLower.equals("attribute") && analysis.hasAttributes) {
                score += 2;
            }
            if (tagLower.equals("namespace") && analysis.hasNamespaces) {
                score += 2;
            }
            if (tagLower.equals("text") && analysis.hasTextContent) {
                score += 2;
            }
        }

        // Query-based scoring - check if snippet query mentions detected elements
        String queryLower = snippet.getQuery().toLowerCase();
        for (String element : analysis.elementNames) {
            if (queryLower.contains(element)) {
                score += 3;
            }
        }
        for (String attr : analysis.attributeNames) {
            if (queryLower.contains("@" + attr)) {
                score += 3;
            }
        }

        return score;
    }

    /**
     * Counts occurrences of a character in a string.
     */
    private int countOccurrences(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * Internal class to hold XML context analysis results.
     */
    private static class XmlContextAnalysis {
        Set<String> elementNames = new HashSet<>();
        Set<String> attributeNames = new HashSet<>();
        Set<String> namespaces = new HashSet<>();
        boolean hasAttributes = false;
        boolean hasNamespaces = false;
        boolean inComment = false;
        boolean inCdata = false;
        boolean isNested = false;
        boolean hasTextContent = false;
        boolean hasNumericContent = false;
        boolean hasDateContent = false;
    }

    /**
     * Adds a custom snippet to the repository.
     * The snippet is persisted and becomes immediately available in the snippet list.
     *
     * @param snippet the custom snippet to add to the repository
     */
    public void addCustomSnippet(XPathSnippet snippet) {
        snippetRepository.saveSnippet(snippet);
        loadSnippets();
    }

    /**
     * Shuts down the panel and releases all resources.
     * This method should be called when the panel is no longer needed
     * to properly terminate the background executor service.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    // ========== Custom List Cell ==========

    /**
     * Custom list cell for displaying XPath snippets in the snippet list view.
     * Displays the snippet name, description, and tags with appropriate styling.
     */
    private static class SnippetListCell extends ListCell<XPathSnippet> {
        private final Label nameLabel = new Label();
        private final Label descriptionLabel = new Label();
        private final Label tagsLabel = new Label();
        private final VBox container = new VBox(2);

        /**
         * Creates a new snippet list cell with styled labels for displaying snippet information.
         */
        public SnippetListCell() {
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            descriptionLabel.setFont(Font.font("System", 10));
            descriptionLabel.setStyle("-fx-text-fill: gray;");
            tagsLabel.setFont(Font.font("System", 9));
            tagsLabel.setStyle("-fx-text-fill: blue;");

            container.getChildren().addAll(nameLabel, descriptionLabel, tagsLabel);
        }

        /**
         * Updates the cell content when the item changes.
         * Displays the snippet name (with favorite indicator), description, and tags.
         *
         * @param snippet the XPath snippet to display, or null if the cell is empty
         * @param empty true if this cell does not contain any content
         */
        @Override
        protected void updateItem(XPathSnippet snippet, boolean empty) {
            super.updateItem(snippet, empty);

            if (empty || snippet == null) {
                setGraphic(null);
                return;
            }

            nameLabel.setText((snippet.isFavorite() ? "★ " : "") + snippet.getName());
            descriptionLabel.setText(snippet.getDescription());

            if (!snippet.getTags().isEmpty()) {
                tagsLabel.setText("Tags: " + String.join(", ", snippet.getTags()));
                tagsLabel.setVisible(true);
            } else {
                tagsLabel.setVisible(false);
            }

            setGraphic(container);
            setTooltip(new Tooltip(
                    snippet.getDescription() + "\n\n" +
                            "Type: " + snippet.getType().getDisplayName() + "\n" +
                            "Category: " + snippet.getCategory().getDisplayName() + "\n" +
                            "Query: " + snippet.getQuery()
            ));
        }
    }
}