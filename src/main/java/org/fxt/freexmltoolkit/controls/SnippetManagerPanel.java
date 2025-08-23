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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
     * Set the current XML content for snippet execution
     */
    public void setXmlContent(String xmlContent) {
        this.currentXmlContent = xmlContent;
        executeButton.setDisable(selectedSnippet == null || xmlContent == null || xmlContent.trim().isEmpty());
    }

    /**
     * Set callback for execution results
     */
    public void setResultCallback(Consumer<XPathExecutionResult> callback) {
        this.resultCallback = callback;
    }

    /**
     * Execute snippet by name (for context menu integration)
     */
    public void executeSnippetByName(String snippetName) {
        XPathSnippet snippet = snippetRepository.getSnippetByName(snippetName);
        if (snippet != null) {
            snippetListView.getSelectionModel().select(snippet);
            executeSelectedSnippet();
        }
    }

    /**
     * Get context-aware snippet suggestions
     */
    public List<XPathSnippet> getContextSuggestions(String xmlContext) {
        // TODO: Implement smart context-aware suggestions
        return snippetRepository.searchSnippets(xmlContext);
    }

    /**
     * Add custom snippet
     */
    public void addCustomSnippet(XPathSnippet snippet) {
        snippetRepository.saveSnippet(snippet);
        loadSnippets();
    }

    public void shutdown() {
        executorService.shutdown();
    }

    // ========== Custom List Cell ==========

    private static class SnippetListCell extends ListCell<XPathSnippet> {
        private final Label nameLabel = new Label();
        private final Label descriptionLabel = new Label();
        private final Label tagsLabel = new Label();
        private final VBox container = new VBox(2);

        public SnippetListCell() {
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            descriptionLabel.setFont(Font.font("System", 10));
            descriptionLabel.setStyle("-fx-text-fill: gray;");
            tagsLabel.setFont(Font.font("System", 9));
            tagsLabel.setStyle("-fx-text-fill: blue;");

            container.getChildren().addAll(nameLabel, descriptionLabel, tagsLabel);
        }

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