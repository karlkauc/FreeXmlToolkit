package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.fxt.freexmltoolkit.service.XPathExecutionResult;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Advanced results display panel for XPath/XQuery execution with performance metrics.
 * Features:
 * - Tabular result display with sorting and filtering
 * - Performance metrics visualization
 * - Export capabilities
 * - Result history
 * - Interactive result exploration
 */
public class SnippetResultsPanel extends VBox {

    // UI Components
    private TabPane resultsTabPane;
    private TableView<ResultDisplayItem> resultsTable;
    private TextArea rawResultsArea;
    private VBox performancePanel;
    private ListView<ExecutionHistoryItem> historyList;
    private Label statusLabel;
    private Button exportButton;
    private Button clearButton;
    private ComboBox<String> viewModeCombo;

    // Data
    private final ObservableList<ResultDisplayItem> currentResults = FXCollections.observableArrayList();
    private final ObservableList<ExecutionHistoryItem> executionHistory = FXCollections.observableArrayList();
    private XPathExecutionResult lastResult;
    private Consumer<String> xmlNavigationCallback;

    public SnippetResultsPanel() {
        initializeUI();
        setupEventHandlers();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Header with controls
        HBox headerControls = createHeaderControls();

        // Main content - tabbed view
        resultsTabPane = createResultsTabs();

        // Status bar
        HBox statusBar = createStatusBar();

        getChildren().addAll(headerControls, resultsTabPane, statusBar);
    }

    private HBox createHeaderControls() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5));

        Label titleLabel = new Label("Execution Results");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // View mode selection
        viewModeCombo = new ComboBox<>();
        viewModeCombo.getItems().addAll("Table View", "Raw Text", "Tree View");
        viewModeCombo.setValue("Table View");
        viewModeCombo.setPrefWidth(120);

        // Action buttons
        exportButton = new Button("Export");
        exportButton.setTooltip(new Tooltip("Export results to file"));
        exportButton.setDisable(true);

        clearButton = new Button("Clear");
        clearButton.setTooltip(new Tooltip("Clear current results"));
        clearButton.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, spacer, viewModeCombo, exportButton, clearButton);
        return header;
    }

    private TabPane createResultsTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Results Tab
        Tab resultsTab = new Tab("Results");
        resultsTab.setContent(createResultsContent());

        // Performance Tab
        Tab performanceTab = new Tab("Performance");
        performanceTab.setContent(createPerformanceContent());

        // History Tab
        Tab historyTab = new Tab("History");
        historyTab.setContent(createHistoryContent());

        tabPane.getTabs().addAll(resultsTab, performanceTab, historyTab);
        return tabPane;
    }

    private Node createResultsContent() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        // Results table
        resultsTable = createResultsTable();

        // Raw results area (initially hidden)
        rawResultsArea = new TextArea();
        rawResultsArea.setEditable(false);
        rawResultsArea.setVisible(false);
        rawResultsArea.setManaged(false);

        content.getChildren().addAll(resultsTable, rawResultsArea);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        return content;
    }

    private TableView<ResultDisplayItem> createResultsTable() {
        TableView<ResultDisplayItem> table = new TableView<>(currentResults);

        // Position column
        TableColumn<ResultDisplayItem, Integer> positionCol = new TableColumn<>("#");
        positionCol.setCellValueFactory(new PropertyValueFactory<>("position"));
        positionCol.setPrefWidth(50);
        positionCol.setStyle("-fx-alignment: CENTER;");

        // Node Name column
        TableColumn<ResultDisplayItem, String> nodeNameCol = new TableColumn<>("Node");
        nodeNameCol.setCellValueFactory(new PropertyValueFactory<>("nodeName"));
        nodeNameCol.setPrefWidth(150);

        // Type column
        TableColumn<ResultDisplayItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(80);

        // Value column
        TableColumn<ResultDisplayItem, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setPrefWidth(400);

        // Custom cell factory for value column to handle long text
        valueCol.setCellFactory(col -> new TableCell<ResultDisplayItem, String>() {
            private final Tooltip tooltip = new Tooltip();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String displayText = item.length() > 100 ? item.substring(0, 97) + "..." : item;
                    setText(displayText);
                    tooltip.setText(item);
                    setTooltip(tooltip);
                }
            }
        });

        // XPath column (if available)
        TableColumn<ResultDisplayItem, String> xpathCol = new TableColumn<>("XPath");
        xpathCol.setCellValueFactory(new PropertyValueFactory<>("xpath"));
        xpathCol.setPrefWidth(200);

        table.getColumns().addAll(positionCol, nodeNameCol, typeCol, valueCol, xpathCol);

        // Row selection handler
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && xmlNavigationCallback != null && newItem.getXpath() != null) {
                xmlNavigationCallback.accept(newItem.getXpath());
            }
        });

        return table;
    }

    private Node createPerformanceContent() {
        ScrollPane scrollPane = new ScrollPane();
        performancePanel = new VBox(10);
        performancePanel.setPadding(new Insets(10));
        scrollPane.setContent(performancePanel);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private Node createHistoryContent() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        // History controls
        HBox historyControls = new HBox(5);
        historyControls.setAlignment(Pos.CENTER_LEFT);

        Button clearHistoryButton = new Button("Clear History");
        clearHistoryButton.setOnAction(e -> {
            executionHistory.clear();
            updateHistoryTab();
        });

        Button reExecuteButton = new Button("Re-execute");
        reExecuteButton.setDisable(true);

        historyControls.getChildren().addAll(clearHistoryButton, reExecuteButton);

        // History list
        historyList = new ListView<>(executionHistory);
        historyList.setCellFactory(listView -> new HistoryListCell());

        historyList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            reExecuteButton.setDisable(newItem == null);
        });

        content.getChildren().addAll(historyControls, historyList);
        VBox.setVgrow(historyList, Priority.ALWAYS);
        return content;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-border-color: lightgray; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("No results");
        statusLabel.setFont(Font.font("System", 10));

        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    private void setupEventHandlers() {
        // View mode switching
        viewModeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            switchViewMode(newValue);
        });

        // Export button
        exportButton.setOnAction(e -> exportResults());

        // Clear button
        clearButton.setOnAction(e -> clearResults());
    }

    private void switchViewMode(String mode) {
        switch (mode) {
            case "Table View":
                resultsTable.setVisible(true);
                resultsTable.setManaged(true);
                rawResultsArea.setVisible(false);
                rawResultsArea.setManaged(false);
                break;

            case "Raw Text":
                resultsTable.setVisible(false);
                resultsTable.setManaged(false);
                rawResultsArea.setVisible(true);
                rawResultsArea.setManaged(true);
                break;

            case "Tree View":
                // TODO: Implement tree view for hierarchical XML results
                break;
        }
    }

    // ========== Public API ==========

    /**
     * Display execution results
     */
    public void displayResults(XPathExecutionResult result) {
        this.lastResult = result;

        Platform.runLater(() -> {
            // Update results table
            currentResults.clear();
            if (result.isSuccess() && !result.getResultItems().isEmpty()) {
                List<ResultDisplayItem> displayItems = new ArrayList<>();
                for (int i = 0; i < result.getResultItems().size(); i++) {
                    var item = result.getResultItems().get(i);
                    displayItems.add(new ResultDisplayItem(
                            i + 1,
                            item.getNodeName(),
                            item.getType().getDisplayName(),
                            item.getValue(),
                            item.getXpath()
                    ));
                }
                currentResults.addAll(displayItems);
            }

            // Update raw results area
            if (result.isSuccess()) {
                rawResultsArea.setText(result.getAsString());
            } else {
                rawResultsArea.setText("Error: " + result.getErrorMessage());
            }

            // Update performance panel
            updatePerformancePanel(result);

            // Add to history
            addToHistory(result);

            // Update status
            updateStatus(result);

            // Enable controls
            exportButton.setDisable(false);
            clearButton.setDisable(false);
        });
    }

    private void updatePerformancePanel(XPathExecutionResult result) {
        performancePanel.getChildren().clear();

        // Execution Summary
        Label summaryLabel = new Label("Execution Summary");
        summaryLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        performancePanel.getChildren().add(summaryLabel);

        VBox summaryBox = new VBox(5);
        summaryBox.setPadding(new Insets(0, 0, 0, 20));

        summaryBox.getChildren().addAll(
                new Label("Status: " + (result.isSuccess() ? "✓ Success" : "✗ Error")),
                new Label("Results: " + result.getResultCount() + (result.isTruncated() ? " (truncated)" : "")),
                new Label("Total Time: " + result.getExecutionTime() + " ms"),
                new Label("Query Type: " + (result.getQueryType() != null ? result.getQueryType() : "Unknown"))
        );

        if (result.isSuccess() && result.getExecutionTime() > 0) {
            long avgTimePerResult = result.getResultCount() > 0 ?
                    result.getExecutionTime() / result.getResultCount() : 0;
            summaryBox.getChildren().add(new Label("Avg Time per Result: " + avgTimePerResult + " ms"));
        }

        performancePanel.getChildren().add(summaryBox);

        // Detailed Timing
        if (result.getParseTime() > 0 || result.getCompilationTime() > 0 || result.getEvaluationTime() > 0) {
            Label timingLabel = new Label("Detailed Timing");
            timingLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            timingLabel.setPadding(new Insets(10, 0, 5, 0));
            performancePanel.getChildren().add(timingLabel);

            VBox timingBox = new VBox(3);
            timingBox.setPadding(new Insets(0, 0, 0, 20));

            if (result.getParseTime() > 0) {
                timingBox.getChildren().add(new Label("Parse Time: " + result.getParseTime() + " ms"));
            }
            if (result.getCompilationTime() > 0) {
                timingBox.getChildren().add(new Label("Compilation Time: " + result.getCompilationTime() + " ms"));
            }
            if (result.getEvaluationTime() > 0) {
                timingBox.getChildren().add(new Label("Evaluation Time: " + result.getEvaluationTime() + " ms"));
            }

            performancePanel.getChildren().add(timingBox);
        }

        // Memory Usage
        if (result.getMemoryUsage() > 0) {
            Label memoryLabel = new Label("Memory Usage");
            memoryLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            memoryLabel.setPadding(new Insets(10, 0, 5, 0));
            performancePanel.getChildren().add(memoryLabel);

            VBox memoryBox = new VBox(3);
            memoryBox.setPadding(new Insets(0, 0, 0, 20));
            memoryBox.getChildren().add(new Label("Estimated Memory: " + formatBytes(result.getMemoryUsage())));
            performancePanel.getChildren().add(memoryBox);
        }

        // Query Analysis
        if (!result.getUsedFunctions().isEmpty() || !result.getNamespacesUsed().isEmpty()) {
            Label analysisLabel = new Label("Query Analysis");
            analysisLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            analysisLabel.setPadding(new Insets(10, 0, 5, 0));
            performancePanel.getChildren().add(analysisLabel);

            VBox analysisBox = new VBox(3);
            analysisBox.setPadding(new Insets(0, 0, 0, 20));

            analysisBox.getChildren().add(new Label("Advanced Features: " + (result.isUsedAdvancedFeatures() ? "Yes" : "No")));

            if (!result.getUsedFunctions().isEmpty()) {
                analysisBox.getChildren().add(new Label("Functions Used: " + String.join(", ", result.getUsedFunctions())));
            }

            if (!result.getNamespacesUsed().isEmpty()) {
                analysisBox.getChildren().add(new Label("Namespaces: " + String.join(", ", result.getNamespacesUsed())));
            }

            performancePanel.getChildren().add(analysisBox);
        }
    }

    private void addToHistory(XPathExecutionResult result) {
        ExecutionHistoryItem historyItem = new ExecutionHistoryItem(
                result.getExecutedAt() != null ? result.getExecutedAt() : java.time.LocalDateTime.now(),
                result.getQuery(),
                result.isSuccess(),
                result.getResultCount(),
                result.getExecutionTime(),
                result.getErrorMessage()
        );

        executionHistory.add(0, historyItem); // Add to top

        // Limit history size
        if (executionHistory.size() > 100) {
            executionHistory.remove(executionHistory.size() - 1);
        }

        updateHistoryTab();
    }

    private void updateHistoryTab() {
        // Update history tab badge with count
        Tab historyTab = resultsTabPane.getTabs().get(2);
        historyTab.setText("History (" + executionHistory.size() + ")");
    }

    private void updateStatus(XPathExecutionResult result) {
        if (result.isSuccess()) {
            statusLabel.setText(result.getResultCount() + " results in " + result.getExecutionTime() + "ms");
            statusLabel.setStyle("-fx-text-fill: green;");
        } else {
            statusLabel.setText("Error: " + result.getErrorMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void exportResults() {
        if (lastResult == null) return;

        // TODO: Implement export functionality (CSV, XML, JSON)
        System.out.println("Exporting results... (not implemented yet)");
    }

    private void clearResults() {
        currentResults.clear();
        rawResultsArea.clear();
        performancePanel.getChildren().clear();
        statusLabel.setText("No results");
        statusLabel.setStyle("-fx-text-fill: black;");
        exportButton.setDisable(true);
        clearButton.setDisable(true);
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Set callback for XML navigation
     */
    public void setXmlNavigationCallback(Consumer<String> callback) {
        this.xmlNavigationCallback = callback;
    }

    // ========== Data Classes ==========

    public static class ResultDisplayItem {
        private final int position;
        private final String nodeName;
        private final String type;
        private final String value;
        private final String xpath;

        public ResultDisplayItem(int position, String nodeName, String type, String value, String xpath) {
            this.position = position;
            this.nodeName = nodeName != null ? nodeName : "";
            this.type = type != null ? type : "";
            this.value = value != null ? value : "";
            this.xpath = xpath != null ? xpath : "";
        }

        // Getters for PropertyValueFactory
        public int getPosition() {
            return position;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public String getXpath() {
            return xpath;
        }
    }

    public static class ExecutionHistoryItem {
        private final java.time.LocalDateTime timestamp;
        private final String query;
        private final boolean success;
        private final int resultCount;
        private final long executionTime;
        private final String errorMessage;

        public ExecutionHistoryItem(java.time.LocalDateTime timestamp, String query, boolean success,
                                    int resultCount, long executionTime, String errorMessage) {
            this.timestamp = timestamp;
            this.query = query;
            this.success = success;
            this.resultCount = resultCount;
            this.executionTime = executionTime;
            this.errorMessage = errorMessage;
        }

        // Getters
        public java.time.LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getQuery() {
            return query;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getResultCount() {
            return resultCount;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        public String getSummary() {
            if (success) {
                return resultCount + " results in " + executionTime + "ms";
            } else {
                return "Error: " + (errorMessage != null ? errorMessage : "Unknown error");
            }
        }
    }

    // ========== Custom List Cell ==========

    private static class HistoryListCell extends ListCell<ExecutionHistoryItem> {
        private final Label timeLabel = new Label();
        private final Label queryLabel = new Label();
        private final Label summaryLabel = new Label();
        private final VBox container = new VBox(2);

        public HistoryListCell() {
            timeLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
            queryLabel.setFont(Font.font("System", 10));
            queryLabel.setStyle("-fx-text-fill: gray;");
            summaryLabel.setFont(Font.font("System", 9));

            container.getChildren().addAll(timeLabel, queryLabel, summaryLabel);
        }

        @Override
        protected void updateItem(ExecutionHistoryItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            timeLabel.setText(item.getFormattedTimestamp());

            String queryDisplay = item.getQuery();
            if (queryDisplay.length() > 60) {
                queryDisplay = queryDisplay.substring(0, 57) + "...";
            }
            queryLabel.setText(queryDisplay);

            summaryLabel.setText(item.getSummary());
            summaryLabel.setStyle(item.isSuccess() ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

            setGraphic(container);
            setTooltip(new Tooltip(
                    "Time: " + item.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                            "Query: " + item.getQuery() + "\n" +
                            "Result: " + item.getSummary()
            ));
        }
    }
}