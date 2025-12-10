package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.*;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityExporter;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View displaying XSD schema quality analysis results.
 * Shows quality score, naming convention distribution, and issues with filtering.
 *
 * @since 2.0
 */
public class QualityChecksView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(QualityChecksView.class);

    private final XsdSchema schema;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "QualityChecksView-Worker");
        t.setDaemon(true);
        return t;
    });

    private QualityResult currentResult;
    private final XsdQualityExporter exporter = new XsdQualityExporter();

    // UI Components - Header
    private Label scoreLabel;
    private Label scoreDescriptionLabel;
    private Circle scoreCircle;

    // UI Components - Filters
    private ComboBox<String> categoryFilter;
    private ComboBox<String> severityFilter;
    private TextField searchField;

    // UI Components - Score Panel
    private VBox namingDistributionBox;

    // UI Components - Issues
    private TableView<QualityIssue> issuesTable;
    private ObservableList<QualityIssue> allIssues;
    private FilteredList<QualityIssue> filteredIssues;
    private TextArea detailsArea;

    // UI Components - Loading
    private ProgressIndicator loadingIndicator;

    /**
     * Creates a new quality checks view.
     *
     * @param schema the XSD schema to analyze
     */
    public QualityChecksView(XsdSchema schema) {
        this.schema = schema;
        initializeUI();
        refresh();
    }

    /**
     * Initializes the UI.
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Top: Toolbar with filters
        VBox topBox = new VBox(5);
        topBox.getChildren().addAll(createToolbar(), createFilterBar());
        setTop(topBox);

        // Main content
        SplitPane mainSplit = new SplitPane();
        mainSplit.setDividerPositions(0.3);

        // Left: Score panel
        VBox scorePanel = createScorePanel();
        scorePanel.setMinWidth(200);
        scorePanel.setMaxWidth(300);

        // Right: Issues split pane
        SplitPane issuesSplit = new SplitPane();
        issuesSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        issuesSplit.setDividerPositions(0.65);

        VBox issuesBox = createIssuesTable();
        VBox detailsBox = createDetailsPanel();

        issuesSplit.getItems().addAll(issuesBox, detailsBox);

        mainSplit.getItems().addAll(scorePanel, issuesSplit);

        // Stack pane for loading overlay
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        StackPane stackPane = new StackPane(mainSplit, loadingIndicator);
        setCenter(stackPane);
    }

    /**
     * Creates the toolbar with refresh and export buttons.
     */
    private ToolBar createToolbar() {
        Label titleLabel = new Label("Quality Analysis");
        titleLabel.setStyle("-fx-font-size: 14pt; -fx-font-weight: bold;");
        FontIcon titleIcon = new FontIcon(BootstrapIcons.AWARD);
        titleIcon.setIconSize(18);
        titleLabel.setGraphic(titleIcon);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        scoreLabel = new Label("--");
        scoreLabel.setStyle("-fx-font-size: 14pt; -fx-font-weight: bold;");

        scoreDescriptionLabel = new Label("");
        scoreDescriptionLabel.setStyle("-fx-text-fill: #666666;");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setGraphic(new FontIcon(BootstrapIcons.ARROW_CLOCKWISE));
        refreshBtn.setOnAction(e -> refresh());

        // Export buttons
        Button exportCsvBtn = new Button("CSV");
        exportCsvBtn.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_SPREADSHEET));
        exportCsvBtn.setOnAction(e -> exportToCsv());

        Button exportJsonBtn = new Button("JSON");
        exportJsonBtn.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_CODE));
        exportJsonBtn.setOnAction(e -> exportToJson());

        Button exportPdfBtn = new Button("PDF");
        exportPdfBtn.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_RICHTEXT));
        exportPdfBtn.setOnAction(e -> exportToPdf());

        Button exportHtmlBtn = new Button("HTML");
        exportHtmlBtn.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_TEXT));
        exportHtmlBtn.setOnAction(e -> exportToHtml());

        Button exportExcelBtn = new Button("Excel");
        exportExcelBtn.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_EXCEL));
        exportExcelBtn.setOnAction(e -> exportToExcel());

        return new ToolBar(titleLabel, spacer, scoreLabel, scoreDescriptionLabel,
                refreshBtn, new Separator(), exportCsvBtn, exportJsonBtn, exportPdfBtn, exportHtmlBtn, exportExcelBtn);
    }

    /**
     * Creates the filter bar.
     */
    private HBox createFilterBar() {
        Label filterLabel = new Label("Filter:");
        filterLabel.setStyle("-fx-font-weight: bold;");

        // Category filter
        categoryFilter = new ComboBox<>();
        categoryFilter.getItems().addAll("All Categories", "Naming Convention", "Best Practice", "Deprecated",
                "Constraint Conflict", "Inconsistent Definition", "Duplicate Definition");
        categoryFilter.setValue("All Categories");
        categoryFilter.setOnAction(e -> applyFilters());

        // Severity filter
        severityFilter = new ComboBox<>();
        severityFilter.getItems().addAll("All Severities", "Error", "Warning", "Info", "Suggestion");
        severityFilter.setValue("All Severities");
        severityFilter.setOnAction(e -> applyFilters());

        // Fulltext search field with icon
        searchField = new TextField();
        searchField.setPromptText("Search in issues...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        FontIcon searchIcon = new FontIcon("bi-search");
        searchIcon.setIconSize(14);
        searchIcon.setIconColor(Color.GRAY);
        Label searchIconLabel = new Label();
        searchIconLabel.setGraphic(searchIcon);

        // Clear search button
        Button clearBtn = new Button();
        FontIcon clearIcon = new FontIcon("bi-x-circle");
        clearIcon.setIconSize(14);
        clearBtn.setGraphic(clearIcon);
        clearBtn.setTooltip(new Tooltip("Clear filters"));
        clearBtn.setOnAction(e -> {
            searchField.clear();
            categoryFilter.setValue("All Categories");
            severityFilter.setValue("All Severities");
        });

        HBox filterBar = new HBox(10, filterLabel, categoryFilter, severityFilter, new Separator(), searchIconLabel, searchField, clearBtn);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(5, 0, 5, 0));

        return filterBar;
    }

    /**
     * Creates the score panel with score display and naming distribution.
     */
    private VBox createScorePanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");

        // Score circle
        StackPane scorePane = createScoreDisplay();

        // Naming distribution section
        Label namingLabel = new Label("Naming Conventions");
        namingLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12pt;");

        namingDistributionBox = new VBox(5);
        namingDistributionBox.setPadding(new Insets(5));

        // Summary section
        Label summaryLabel = new Label("Summary");
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12pt;");

        panel.getChildren().addAll(scorePane, new Separator(), namingLabel, namingDistributionBox, new Separator(), summaryLabel);

        return panel;
    }

    /**
     * Creates the score display circle.
     */
    private StackPane createScoreDisplay() {
        scoreCircle = new Circle(50);
        scoreCircle.setFill(Color.LIGHTGRAY);
        scoreCircle.setStroke(Color.GRAY);
        scoreCircle.setStrokeWidth(3);

        Text scoreText = new Text("--");
        scoreText.setFont(Font.font("System", FontWeight.BOLD, 28));
        scoreText.setFill(Color.WHITE);

        // Bind score text to scoreLabel
        scoreLabel.textProperty().addListener((obs, oldVal, newVal) -> {
            String numericPart = newVal.replaceAll("[^0-9]", "");
            scoreText.setText(numericPart.isEmpty() ? "--" : numericPart);
        });

        StackPane scorePane = new StackPane(scoreCircle, scoreText);
        scorePane.setPadding(new Insets(10));

        return scorePane;
    }

    /**
     * Creates the issues table.
     */
    private VBox createIssuesTable() {
        allIssues = FXCollections.observableArrayList();
        filteredIssues = new FilteredList<>(allIssues, p -> true);

        // Wrap in SortedList to enable column sorting
        SortedList<QualityIssue> sortedIssues = new SortedList<>(filteredIssues);

        issuesTable = new TableView<>(sortedIssues);
        sortedIssues.comparatorProperty().bind(issuesTable.comparatorProperty());

        issuesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        issuesTable.setPlaceholder(new Label("No quality issues found"));

        // Severity column with icon (sortable by severity priority)
        TableColumn<QualityIssue, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().severity().name()));
        severityCol.setComparator((s1, s2) -> {
            int p1 = getSeverityPriority(IssueSeverity.valueOf(s1));
            int p2 = getSeverityPriority(IssueSeverity.valueOf(s2));
            return Integer.compare(p1, p2);
        });
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    IssueSeverity severity = IssueSeverity.valueOf(item);
                    FontIcon icon = getSeverityIcon(severity);
                    setGraphic(icon);
                    setText(getSeverityText(severity));
                }
            }
        });
        severityCol.setPrefWidth(90);

        // Category column with icon
        TableColumn<QualityIssue, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category().name()));
        categoryCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    IssueCategory category = IssueCategory.valueOf(item);
                    FontIcon icon = getCategoryIcon(category);
                    setGraphic(icon);
                    setText(getCategoryText(category));
                }
            }
        });
        categoryCol.setPrefWidth(120);

        // Message column
        TableColumn<QualityIssue, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        messageCol.setPrefWidth(300);

        // Affected count column
        TableColumn<QualityIssue, Integer> affectedCol = new TableColumn<>("Affected");
        affectedCol.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().affectedElements().size()).asObject());
        affectedCol.setPrefWidth(70);

        issuesTable.getColumns().addAll(severityCol, categoryCol, messageCol, affectedCol);

        // Selection listener
        issuesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showDetails(newVal));

        VBox box = new VBox(issuesTable);
        VBox.setVgrow(issuesTable, Priority.ALWAYS);
        return box;
    }

    /**
     * Creates the details panel.
     */
    private VBox createDetailsPanel() {
        Label detailsLabel = new Label("Details");
        detailsLabel.setStyle("-fx-font-weight: bold;");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setStyle("-fx-font-family: monospace;");
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        VBox box = new VBox(5, detailsLabel, detailsArea);
        box.setPadding(new Insets(5));
        return box;
    }

    /**
     * Gets the severity icon.
     */
    private FontIcon getSeverityIcon(IssueSeverity severity) {
        FontIcon icon = new FontIcon();
        icon.setIconSize(14);

        switch (severity) {
            case ERROR -> {
                icon.setIconLiteral("bi-x-circle-fill");
                icon.setIconColor(Color.RED);
            }
            case WARNING -> {
                icon.setIconLiteral("bi-exclamation-triangle-fill");
                icon.setIconColor(Color.ORANGE);
            }
            case INFO -> {
                icon.setIconLiteral("bi-info-circle-fill");
                icon.setIconColor(Color.DODGERBLUE);
            }
            case SUGGESTION -> {
                icon.setIconLiteral("bi-lightbulb-fill");
                icon.setIconColor(Color.GOLD);
            }
        }

        return icon;
    }

    /**
     * Gets severity display text.
     */
    private String getSeverityText(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case INFO -> "Info";
            case SUGGESTION -> "Suggestion";
        };
    }

    /**
     * Gets severity priority for sorting (lower = more important).
     */
    private int getSeverityPriority(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
            case SUGGESTION -> 3;
        };
    }

    /**
     * Gets the category icon.
     */
    private FontIcon getCategoryIcon(IssueCategory category) {
        FontIcon icon = new FontIcon();
        icon.setIconSize(14);

        switch (category) {
            case NAMING_CONVENTION -> {
                icon.setIconLiteral("bi-type");
                icon.setIconColor(Color.PURPLE);
            }
            case BEST_PRACTICE -> {
                icon.setIconLiteral("bi-check2-square");
                icon.setIconColor(Color.GREEN);
            }
            case DEPRECATED -> {
                icon.setIconLiteral("bi-calendar-x");
                icon.setIconColor(Color.GRAY);
            }
            case CONSTRAINT_CONFLICT -> {
                icon.setIconLiteral("bi-exclamation-diamond-fill");
                icon.setIconColor(Color.CRIMSON);
            }
            case INCONSISTENT_DEFINITION -> {
                icon.setIconLiteral("bi-shuffle");
                icon.setIconColor(Color.ORANGE);
            }
            case DUPLICATE_DEFINITION -> {
                icon.setIconLiteral("bi-files");
                icon.setIconColor(Color.DODGERBLUE);
            }
        }

        return icon;
    }

    /**
     * Gets category display text.
     */
    private String getCategoryText(IssueCategory category) {
        return switch (category) {
            case NAMING_CONVENTION -> "Naming";
            case BEST_PRACTICE -> "Best Practice";
            case DEPRECATED -> "Deprecated";
            case CONSTRAINT_CONFLICT -> "Constraint Conflict";
            case INCONSISTENT_DEFINITION -> "Inconsistent Definition";
            case DUPLICATE_DEFINITION -> "Duplicate Definition";
        };
    }

    /**
     * Applies the current filters to the issues list.
     */
    private void applyFilters() {
        String categoryValue = categoryFilter.getValue();
        String severityValue = severityFilter.getValue();
        String searchText = searchField != null ? searchField.getText() : "";
        String searchLower = searchText != null ? searchText.toLowerCase() : "";

        filteredIssues.setPredicate(issue -> {
            boolean categoryMatch = "All Categories".equals(categoryValue) ||
                    getCategoryText(issue.category()).equals(categoryValue.replace(" Convention", ""));

            boolean severityMatch = "All Severities".equals(severityValue) ||
                    getSeverityText(issue.severity()).equals(severityValue);

            // Fulltext search: check message, suggestion, xpath, and affected elements
            boolean textMatch = searchLower.isEmpty() ||
                    (issue.message() != null && issue.message().toLowerCase().contains(searchLower)) ||
                    (issue.suggestion() != null && issue.suggestion().toLowerCase().contains(searchLower)) ||
                    (issue.xpath() != null && issue.xpath().toLowerCase().contains(searchLower)) ||
                    issue.affectedElements().stream().anyMatch(el -> el.toLowerCase().contains(searchLower)) ||
                    getCategoryText(issue.category()).toLowerCase().contains(searchLower) ||
                    getSeverityText(issue.severity()).toLowerCase().contains(searchLower);

            return categoryMatch && severityMatch && textMatch;
        });
    }

    /**
     * Shows details for the selected issue.
     */
    private void showDetails(QualityIssue issue) {
        if (issue == null) {
            detailsArea.clear();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(getCategoryText(issue.category())).append("\n");
        sb.append("Severity: ").append(getSeverityText(issue.severity())).append("\n");

        // Show XPath location if available
        if (issue.xpath() != null && !issue.xpath().isBlank()) {
            sb.append("Location: ").append(issue.xpath()).append("\n");
        }

        sb.append("\n");
        sb.append("Message:\n");
        sb.append("  ").append(issue.message()).append("\n");

        if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
            sb.append("\nSuggestion:\n");
            sb.append("  ").append(issue.suggestion()).append("\n");
        }

        if (!issue.affectedElements().isEmpty()) {
            sb.append("\nAffected Elements (").append(issue.affectedElements().size()).append("):\n");
            for (String element : issue.affectedElements()) {
                sb.append("  - ").append(element).append("\n");
            }
        }

        detailsArea.setText(sb.toString());
    }

    /**
     * Refreshes the quality analysis.
     */
    public void refresh() {
        logger.debug("Refreshing quality checks view");
        loadingIndicator.setVisible(true);

        executor.submit(() -> {
            try {
                XsdQualityChecker checker = new XsdQualityChecker(schema);
                QualityResult result = checker.check();

                Platform.runLater(() -> {
                    currentResult = result;
                    updateUI(result);
                    loadingIndicator.setVisible(false);
                });
            } catch (Exception e) {
                logger.error("Failed to run quality checks", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    scoreLabel.setText("Error");
                    scoreDescriptionLabel.setText(e.getMessage());
                });
            }
        });
    }

    /**
     * Updates the UI with the quality result.
     */
    private void updateUI(QualityResult result) {
        // Update score display
        int score = result.score();
        scoreLabel.setText(score + "/100");
        scoreDescriptionLabel.setText(result.getScoreDescription());

        // Update score circle color
        Color scoreColor = getScoreColor(score);
        scoreCircle.setFill(scoreColor);
        scoreCircle.setStroke(scoreColor.darker());

        // Update naming distribution
        updateNamingDistribution(result);

        // Update issues table
        allIssues.clear();
        allIssues.addAll(result.issues());

        // Clear details
        detailsArea.clear();
    }

    /**
     * Gets the color for a score value.
     */
    private Color getScoreColor(int score) {
        if (score >= 90) return Color.web("#28a745"); // Green - Excellent
        if (score >= 75) return Color.web("#17a2b8"); // Blue - Good
        if (score >= 60) return Color.web("#ffc107"); // Yellow - Fair
        if (score >= 40) return Color.web("#fd7e14"); // Orange - Needs Improvement
        return Color.web("#dc3545"); // Red - Poor
    }

    /**
     * Updates the naming distribution display.
     */
    private void updateNamingDistribution(QualityResult result) {
        namingDistributionBox.getChildren().clear();

        NamingConvention dominant = result.dominantNamingConvention();

        for (NamingConvention convention : NamingConvention.values()) {
            int count = result.namingDistribution().getOrDefault(convention, 0);
            if (count > 0 || convention == dominant) {
                HBox row = createNamingRow(convention, count, convention == dominant);
                namingDistributionBox.getChildren().add(row);
            }
        }

        // Add dominant info
        if (dominant != NamingConvention.UNKNOWN) {
            Label dominantLabel = new Label("Dominant: " + dominant.getDisplayName());
            dominantLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #666666;");
            namingDistributionBox.getChildren().add(dominantLabel);
        }
    }

    /**
     * Creates a row for naming convention display.
     */
    private HBox createNamingRow(NamingConvention convention, int count, boolean isDominant) {
        Label nameLabel = new Label(convention.getDisplayName());
        if (isDominant) {
            nameLabel.setStyle("-fx-font-weight: bold;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.setStyle("-fx-font-family: monospace;");

        HBox row = new HBox(5, nameLabel, spacer, countLabel);
        row.setAlignment(Pos.CENTER_LEFT);

        if (isDominant) {
            row.setStyle("-fx-background-color: #e7f3ff; -fx-padding: 2 5 2 5; -fx-background-radius: 3;");
        }

        return row;
    }

    /**
     * Gets the current quality result.
     *
     * @return the current result, or null if not yet analyzed
     */
    public QualityResult getCurrentResult() {
        return currentResult;
    }

    // ========== Export Methods ==========

    /**
     * Exports quality results to CSV.
     */
    private void exportToCsv() {
        if (currentResult == null) {
            showWarning("No quality results available. Please run the analysis first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Quality Report to CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("quality-report.csv");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                exporter.exportToCsv(currentResult, file.toPath());
                showInfo("Quality report exported to CSV: " + file.getName());
            } catch (Exception e) {
                logger.error("Failed to export CSV", e);
                showError("Failed to export CSV: " + e.getMessage());
            }
        }
    }

    /**
     * Exports quality results to JSON.
     */
    private void exportToJson() {
        if (currentResult == null) {
            showWarning("No quality results available. Please run the analysis first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Quality Report to JSON");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName("quality-report.json");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                exporter.exportToJson(currentResult, file.toPath());
                showInfo("Quality report exported to JSON: " + file.getName());
            } catch (Exception e) {
                logger.error("Failed to export JSON", e);
                showError("Failed to export JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Exports quality results to PDF.
     */
    private void exportToPdf() {
        if (currentResult == null) {
            showWarning("No quality results available. Please run the analysis first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Quality Report to PDF");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("quality-report.pdf");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            // Run PDF generation in background
            executor.submit(() -> {
                try {
                    exporter.exportToPdf(currentResult, file.toPath());
                    Platform.runLater(() ->
                            showInfo("Quality report exported to PDF: " + file.getName()));
                } catch (Exception e) {
                    logger.error("Failed to export PDF", e);
                    Platform.runLater(() ->
                            showError("Failed to export PDF: " + e.getMessage()));
                }
            });
        }
    }

    /**
     * Exports quality results to HTML.
     */
    private void exportToHtml() {
        if (currentResult == null) {
            showWarning("No quality results available. Please run the analysis first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Quality Report to HTML");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("HTML Files", "*.html"));
        fileChooser.setInitialFileName("quality-report.html");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                exporter.exportToHtml(currentResult, file.toPath());
                showInfo("Quality report exported to HTML: " + file.getName());
            } catch (Exception e) {
                logger.error("Failed to export HTML", e);
                showError("Failed to export HTML: " + e.getMessage());
            }
        }
    }

    /**
     * Exports quality results to Excel.
     */
    private void exportToExcel() {
        if (currentResult == null) {
            showWarning("No quality results available. Please run the analysis first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Quality Report to Excel");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("quality-report.xlsx");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            // Run Excel generation in background
            executor.submit(() -> {
                try {
                    exporter.exportToExcel(currentResult, file.toPath());
                    Platform.runLater(() ->
                            showInfo("Quality report exported to Excel: " + file.getName()));
                } catch (Exception e) {
                    logger.error("Failed to export Excel", e);
                    Platform.runLater(() ->
                            showError("Failed to export Excel: " + e.getMessage()));
                }
            });
        }
    }

    // ========== Alert Helpers ==========

    /**
     * Shows an info alert.
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Successful");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a warning alert.
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error alert.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Export Failed");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        executor.shutdown();
    }
}
