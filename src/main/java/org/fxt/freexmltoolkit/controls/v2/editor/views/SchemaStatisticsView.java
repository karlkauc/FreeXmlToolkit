package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsCollector;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsExporter;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View displaying comprehensive statistics about an XSD schema.
 * Auto-updates when the schema changes.
 *
 * @since 2.0
 */
public class SchemaStatisticsView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(SchemaStatisticsView.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final XsdSchema schema;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SchemaStatisticsView-Worker");
        t.setDaemon(true);
        return t;
    });

    private XsdStatistics currentStatistics;
    private final XsdStatisticsExporter exporter = new XsdStatisticsExporter();

    // UI Components
    private Label lastUpdatedLabel;
    private VBox contentBox;
    private ProgressIndicator loadingIndicator;

    // Schema Info Labels
    private Label xsdVersionLabel;
    private Label targetNamespaceLabel;
    private Label elementFormDefaultLabel;
    private Label attributeFormDefaultLabel;
    private Label namespaceCountLabel;
    private Label fileCountLabel;

    // Node Count Labels
    private Label totalNodesLabel;
    private Label elementsLabel;
    private Label attributesLabel;
    private Label complexTypesLabel;
    private Label simpleTypesLabel;
    private Label groupsLabel;
    private Label sequencesLabel;
    private Label choicesLabel;

    // Documentation Labels
    private Label docCoverageLabel;
    private Label withDocsLabel;
    private Label withAppInfoLabel;
    private Label deprecatedLabel;
    private Label sinceLabel;
    private Label languagesLabel;

    // Cardinality Labels
    private Label optionalLabel;
    private Label requiredLabel;
    private Label unboundedLabel;

    // Type Usage
    private VBox typeUsageBox;

    // PropertyChangeListener for auto-update
    private final PropertyChangeListener schemaChangeListener = this::onSchemaChanged;

    /**
     * Creates a new schema statistics view.
     *
     * @param schema the schema to display statistics for
     */
    public SchemaStatisticsView(XsdSchema schema) {
        this.schema = schema;
        initializeUI();
        registerSchemaListener();
        refreshStatistics();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Toolbar
        ToolBar toolbar = createToolbar();
        setTop(toolbar);

        // Main content with ScrollPane
        contentBox = new VBox(15);
        contentBox.setPadding(new Insets(10));

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        // Schema Info Section
        TitledPane schemaInfoPane = createSchemaInfoSection();

        // Node Counts Section
        TitledPane nodeCountsPane = createNodeCountsSection();

        // Documentation Section
        TitledPane docsPane = createDocumentationSection();

        // Type Usage Section
        TitledPane typeUsagePane = createTypeUsageSection();

        // Cardinality Section
        TitledPane cardinalityPane = createCardinalitySection();

        contentBox.getChildren().addAll(
                schemaInfoPane,
                nodeCountsPane,
                docsPane,
                typeUsagePane,
                cardinalityPane
        );

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        // Stack pane for loading overlay
        StackPane stackPane = new StackPane(scrollPane, loadingIndicator);
        setCenter(stackPane);

        // Status bar
        HBox statusBar = createStatusBar();
        setBottom(statusBar);
    }

    /**
     * Creates the toolbar with export and refresh buttons.
     */
    private ToolBar createToolbar() {
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setGraphic(new FontIcon(BootstrapIcons.ARROW_CLOCKWISE));
        refreshBtn.setOnAction(e -> refreshStatistics());

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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label titleLabel = new Label("Schema Statistics");
        titleLabel.setStyle("-fx-font-size: 14pt; -fx-font-weight: bold;");

        return new ToolBar(titleLabel, spacer, refreshBtn, new Separator(),
                exportCsvBtn, exportJsonBtn, exportPdfBtn, exportHtmlBtn, exportExcelBtn);
    }

    /**
     * Creates the schema information section.
     */
    private TitledPane createSchemaInfoSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        int row = 0;
        xsdVersionLabel = addStatRow(grid, "XSD Version:", row++);
        targetNamespaceLabel = addStatRow(grid, "Target Namespace:", row++);
        elementFormDefaultLabel = addStatRow(grid, "Element Form Default:", row++);
        attributeFormDefaultLabel = addStatRow(grid, "Attribute Form Default:", row++);
        namespaceCountLabel = addStatRow(grid, "Namespaces:", row++);
        fileCountLabel = addStatRow(grid, "Schema Files:", row++);

        TitledPane pane = new TitledPane("Schema Information", grid);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setGraphic(new FontIcon(BootstrapIcons.FILE_EARMARK_CODE));
        return pane;
    }

    /**
     * Creates the node counts section.
     */
    private TitledPane createNodeCountsSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        int row = 0;
        totalNodesLabel = addStatRow(grid, "Total Nodes:", row++);
        elementsLabel = addStatRow(grid, "Elements:", row++);
        attributesLabel = addStatRow(grid, "Attributes:", row++);
        complexTypesLabel = addStatRow(grid, "Complex Types:", row++);
        simpleTypesLabel = addStatRow(grid, "Simple Types:", row++);
        groupsLabel = addStatRow(grid, "Groups:", row++);
        sequencesLabel = addStatRow(grid, "Sequences:", row++);
        choicesLabel = addStatRow(grid, "Choices:", row++);

        TitledPane pane = new TitledPane("Node Counts", grid);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setGraphic(new FontIcon(BootstrapIcons.DIAGRAM_3));
        return pane;
    }

    /**
     * Creates the documentation section.
     */
    private TitledPane createDocumentationSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        int row = 0;
        docCoverageLabel = addStatRow(grid, "Documentation Coverage:", row++);
        withDocsLabel = addStatRow(grid, "Nodes with Documentation:", row++);
        withAppInfoLabel = addStatRow(grid, "Nodes with AppInfo:", row++);
        deprecatedLabel = addStatRow(grid, "@deprecated tags:", row++);
        sinceLabel = addStatRow(grid, "@since tags:", row++);
        languagesLabel = addStatRow(grid, "Languages:", row++);

        TitledPane pane = new TitledPane("Documentation Statistics", grid);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setGraphic(new FontIcon(BootstrapIcons.FILE_TEXT));
        return pane;
    }

    /**
     * Creates the type usage section.
     */
    private TitledPane createTypeUsageSection() {
        typeUsageBox = new VBox(5);
        typeUsageBox.setPadding(new Insets(10));

        TitledPane pane = new TitledPane("Top Used Types", typeUsageBox);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setGraphic(new FontIcon(BootstrapIcons.BAR_CHART));
        return pane;
    }

    /**
     * Creates the cardinality section.
     */
    private TitledPane createCardinalitySection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        int row = 0;
        optionalLabel = addStatRow(grid, "Optional Elements (minOccurs=0):", row++);
        requiredLabel = addStatRow(grid, "Required Elements (minOccurs≥1):", row++);
        unboundedLabel = addStatRow(grid, "Unbounded Elements:", row++);

        TitledPane pane = new TitledPane("Cardinality Statistics", grid);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setGraphic(new FontIcon(BootstrapIcons.HASH));
        return pane;
    }

    /**
     * Creates the status bar.
     */
    private HBox createStatusBar() {
        lastUpdatedLabel = new Label("Last updated: -");
        lastUpdatedLabel.setStyle("-fx-font-size: 10pt; -fx-text-fill: #666666;");

        HBox statusBar = new HBox(lastUpdatedLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_RIGHT);
        statusBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        return statusBar;
    }

    /**
     * Adds a statistic row to the grid.
     */
    private Label addStatRow(GridPane grid, String labelText, int row) {
        Label nameLabel = new Label(labelText);
        nameLabel.setStyle("-fx-font-weight: normal;");

        Label valueLabel = new Label("-");
        valueLabel.setStyle("-fx-font-weight: bold;");

        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);

        return valueLabel;
    }

    /**
     * Registers the schema change listener for auto-update.
     */
    private void registerSchemaListener() {
        schema.addPropertyChangeListener(schemaChangeListener);
    }

    /**
     * Called when the schema changes.
     */
    private void onSchemaChanged(PropertyChangeEvent evt) {
        // Debounce - only refresh if not already loading
        if (!loadingIndicator.isVisible()) {
            Platform.runLater(this::refreshStatistics);
        }
    }

    /**
     * Refreshes the statistics display.
     */
    public void refreshStatistics() {
        logger.debug("Refreshing schema statistics...");
        loadingIndicator.setVisible(true);

        executor.submit(() -> {
            try {
                XsdStatisticsCollector collector = new XsdStatisticsCollector(schema);
                XsdStatistics stats = collector.collect();

                Platform.runLater(() -> {
                    currentStatistics = stats;
                    updateUI(stats);
                    loadingIndicator.setVisible(false);
                });
            } catch (Exception e) {
                logger.error("Failed to collect statistics", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    showError("Failed to collect statistics: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Updates the UI with the collected statistics.
     */
    private void updateUI(XsdStatistics stats) {
        // Schema Info
        xsdVersionLabel.setText(stats.xsdVersion());
        targetNamespaceLabel.setText(stats.targetNamespace().isEmpty() ? "(none)" : stats.targetNamespace());
        elementFormDefaultLabel.setText(stats.elementFormDefault());
        attributeFormDefaultLabel.setText(stats.attributeFormDefault());
        namespaceCountLabel.setText(String.valueOf(stats.namespaceCount()));
        fileCountLabel.setText(String.valueOf(stats.fileCount()));

        // Node Counts
        totalNodesLabel.setText(String.valueOf(stats.totalNodeCount()));
        elementsLabel.setText(String.valueOf(stats.getElementCount()));
        attributesLabel.setText(String.valueOf(stats.getAttributeCount()));
        complexTypesLabel.setText(String.valueOf(stats.getComplexTypeCount()));
        simpleTypesLabel.setText(String.valueOf(stats.getSimpleTypeCount()));
        groupsLabel.setText(String.valueOf(stats.getGroupCount()));
        sequencesLabel.setText(String.valueOf(stats.getNodeCount(XsdNodeType.SEQUENCE)));
        choicesLabel.setText(String.valueOf(stats.getNodeCount(XsdNodeType.CHOICE)));

        // Documentation
        docCoverageLabel.setText(String.format("%.1f%%", stats.documentationCoveragePercent()));
        withDocsLabel.setText(String.valueOf(stats.nodesWithDocumentation()));
        withAppInfoLabel.setText(String.valueOf(stats.nodesWithAppInfo()));

        Map<String, Integer> appInfoTags = stats.appInfoTagCounts();
        deprecatedLabel.setText(String.valueOf(appInfoTags.getOrDefault("@deprecated", 0)));
        sinceLabel.setText(String.valueOf(appInfoTags.getOrDefault("@since", 0)));

        if (stats.documentationLanguages().isEmpty()) {
            languagesLabel.setText("(none)");
        } else {
            languagesLabel.setText(String.join(", ", stats.documentationLanguages()));
        }

        // Type Usage
        typeUsageBox.getChildren().clear();
        if (stats.topUsedTypes().isEmpty()) {
            typeUsageBox.getChildren().add(new Label("No user-defined types found"));
        } else {
            for (XsdStatistics.TypeUsageEntry entry : stats.topUsedTypes()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(entry.typeName());
                nameLabel.setMinWidth(200);
                nameLabel.setStyle("-fx-font-weight: bold;");

                ProgressBar bar = new ProgressBar();
                int maxUsage = stats.topUsedTypes().getFirst().usageCount();
                bar.setProgress(maxUsage > 0 ? (double) entry.usageCount() / maxUsage : 0);
                bar.setPrefWidth(150);

                Label countLabel = new Label(entry.usageCount() + " usages");
                countLabel.setStyle("-fx-text-fill: #666666;");

                row.getChildren().addAll(nameLabel, bar, countLabel);
                typeUsageBox.getChildren().add(row);
            }

            // Show unused types count
            if (!stats.unusedTypes().isEmpty()) {
                Label unusedLabel = new Label(stats.unusedTypes().size() + " unused types");
                unusedLabel.setStyle("-fx-text-fill: #cc6600; -fx-font-style: italic;");
                typeUsageBox.getChildren().add(unusedLabel);
            }
        }

        // Cardinality
        optionalLabel.setText(String.valueOf(stats.optionalElements()));
        requiredLabel.setText(String.valueOf(stats.requiredElements()));
        unboundedLabel.setText(String.valueOf(stats.unboundedElements()));

        // Status
        lastUpdatedLabel.setText("Last updated: " + stats.collectedAt().format(DATE_FORMATTER));
    }

    /**
     * Exports statistics to CSV.
     */
    private void exportToCsv() {
        if (currentStatistics == null) {
            showError("No statistics available. Please refresh first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Statistics to CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("xsd-statistics.csv");

        Window window = getScene() != null ? getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            try {
                exporter.exportToCsv(currentStatistics, file.toPath());
                showInfo("Statistics exported to CSV: " + file.getName());
            } catch (IOException e) {
                logger.error("Failed to export CSV", e);
                showError("Failed to export CSV: " + e.getMessage());
            }
        }
    }

    /**
     * Exports statistics to JSON.
     */
    private void exportToJson() {
        if (currentStatistics == null) {
            showError("No statistics available. Please refresh first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Statistics to JSON");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName("xsd-statistics.json");

        Window window = getScene() != null ? getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            try {
                exporter.exportToJson(currentStatistics, file.toPath());
                showInfo("Statistics exported to JSON: " + file.getName());
            } catch (IOException e) {
                logger.error("Failed to export JSON", e);
                showError("Failed to export JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Exports statistics to PDF.
     */
    private void exportToPdf() {
        if (currentStatistics == null) {
            showError("No statistics available. Please refresh first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Statistics to PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("xsd-statistics.pdf");

        Window window = getScene() != null ? getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            loadingIndicator.setVisible(true);
            executor.submit(() -> {
                try {
                    exporter.exportToPdf(currentStatistics, file.toPath());
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        showInfo("Statistics exported to PDF: " + file.getName());
                    });
                } catch (IOException e) {
                    logger.error("Failed to export PDF", e);
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        showError("Failed to export PDF: " + e.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Exports statistics to HTML.
     */
    private void exportToHtml() {
        if (currentStatistics == null) {
            showError("No statistics available. Please refresh first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Statistics to HTML");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html"));
        fileChooser.setInitialFileName("xsd-statistics.html");

        Window window = getScene() != null ? getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            try {
                exporter.exportToHtml(currentStatistics, file.toPath());
                showInfo("Statistics exported to HTML: " + file.getName());
            } catch (IOException e) {
                logger.error("Failed to export HTML", e);
                showError("Failed to export HTML: " + e.getMessage());
            }
        }
    }

    /**
     * Exports statistics to Excel.
     */
    private void exportToExcel() {
        if (currentStatistics == null) {
            showError("No statistics available. Please refresh first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Statistics to Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("xsd-statistics.xlsx");

        Window window = getScene() != null ? getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            loadingIndicator.setVisible(true);
            executor.submit(() -> {
                try {
                    exporter.exportToExcel(currentStatistics, file.toPath());
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        showInfo("Statistics exported to Excel: " + file.getName());
                    });
                } catch (IOException e) {
                    logger.error("Failed to export Excel", e);
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        showError("Failed to export Excel: " + e.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Shows an error message.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an info message.
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Gets the current statistics.
     */
    public XsdStatistics getCurrentStatistics() {
        return currentStatistics;
    }

    /**
     * Cleanup resources when the view is disposed.
     */
    public void dispose() {
        schema.removePropertyChangeListener(schemaChangeListener);
        executor.shutdown();
    }
}
