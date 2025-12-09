package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.Severity;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.XPathValidationIssue;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View for XPath validation results.
 * Shows validation issues grouped by severity.
 *
 * @since 2.0
 */
public class XPathValidationView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(XPathValidationView.class);

    private final XsdSchema schema;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XPathValidationView-Worker");
        t.setDaemon(true);
        return t;
    });

    private XsdXPathValidator validator;
    private ValidationResult currentResult;
    private Document sampleXml;

    // UI Components
    private Label totalLabel;
    private Label validLabel;
    private Label errorsLabel;
    private Label warningsLabel;
    private ProgressBar validationProgress;
    private TableView<XPathValidationIssue> issuesTable;
    private ObservableList<XPathValidationIssue> tableData;
    private TextArea detailsArea;
    private ProgressIndicator loadingIndicator;
    private CheckBox showInfoCheckBox;

    /**
     * Creates a new XPath validation view.
     *
     * @param schema the XSD schema to validate
     */
    public XPathValidationView(XsdSchema schema) {
        this.schema = schema;
        this.validator = new XsdXPathValidator(schema);
        initializeUI();
        refresh();
    }

    /**
     * Initializes the UI.
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Top: Summary and controls
        VBox topBox = createTopBox();
        setTop(topBox);

        // Center: SplitPane with issues table and details
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.65);
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Issues table
        VBox tableBox = createTableBox();

        // Details area
        VBox detailsBox = createDetailsBox();

        splitPane.getItems().addAll(tableBox, detailsBox);

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        StackPane stackPane = new StackPane(splitPane, loadingIndicator);
        setCenter(stackPane);
    }

    /**
     * Creates the top box with summary.
     */
    private VBox createTopBox() {
        // Title row
        Label titleLabel = new Label("XPath Validation");
        titleLabel.setStyle("-fx-font-size: 14pt; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        showInfoCheckBox = new CheckBox("Show Info");
        showInfoCheckBox.setSelected(false);
        showInfoCheckBox.setOnAction(e -> filterTable());

        Button validateBtn = new Button("Validate All");
        validateBtn.setGraphic(new FontIcon(BootstrapIcons.PLAY_FILL));
        validateBtn.setOnAction(e -> refresh());

        HBox titleRow = new HBox(10, titleLabel, spacer, showInfoCheckBox, validateBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // Summary row
        HBox summaryRow = createSummaryRow();

        VBox box = new VBox(10, titleRow, summaryRow);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    /**
     * Creates the summary row.
     */
    private HBox createSummaryRow() {
        // Total
        FontIcon totalIcon = new FontIcon(BootstrapIcons.LIST_OL);
        totalIcon.setIconSize(16);
        totalLabel = new Label("Total: 0");
        HBox totalBox = createSummaryItem(totalIcon, totalLabel, "#333333");

        // Valid
        FontIcon validIcon = new FontIcon(BootstrapIcons.CHECK_CIRCLE_FILL);
        validIcon.setIconSize(16);
        validIcon.setIconColor(Color.GREEN);
        validLabel = new Label("Valid: 0");
        HBox validBox = createSummaryItem(validIcon, validLabel, "#28a745");

        // Errors
        FontIcon errorIcon = new FontIcon(BootstrapIcons.X_CIRCLE_FILL);
        errorIcon.setIconSize(16);
        errorIcon.setIconColor(Color.RED);
        errorsLabel = new Label("Errors: 0");
        HBox errorsBox = createSummaryItem(errorIcon, errorsLabel, "#dc3545");

        // Warnings
        FontIcon warningIcon = new FontIcon(BootstrapIcons.EXCLAMATION_TRIANGLE_FILL);
        warningIcon.setIconSize(16);
        warningIcon.setIconColor(Color.ORANGE);
        warningsLabel = new Label("Warnings: 0");
        HBox warningsBox = createSummaryItem(warningIcon, warningsLabel, "#ffc107");

        // Progress bar
        validationProgress = new ProgressBar(0);
        validationProgress.setPrefWidth(200);
        validationProgress.setStyle("-fx-accent: #28a745;");

        HBox row = new HBox(20, totalBox, validBox, errorsBox, warningsBox, validationProgress);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");

        return row;
    }

    /**
     * Creates a summary item.
     */
    private HBox createSummaryItem(FontIcon icon, Label label, String color) {
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
        HBox box = new HBox(5, icon, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Creates the table box.
     */
    private VBox createTableBox() {
        tableData = FXCollections.observableArrayList();
        issuesTable = new TableView<>(tableData);
        issuesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Severity column with icon
        TableColumn<XPathValidationIssue, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().severity().name()));
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Severity severity = Severity.valueOf(item);
                    FontIcon icon = getSeverityIcon(severity);
                    setGraphic(icon);
                    setText(severity.name());
                }
            }
        });
        severityCol.setPrefWidth(80);

        // Source column
        TableColumn<XPathValidationIssue, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSourceDescription()));
        sourceCol.setPrefWidth(100);

        // Constraint column
        TableColumn<XPathValidationIssue, String> constraintCol = new TableColumn<>("Constraint");
        constraintCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().constraintName()));
        constraintCol.setPrefWidth(120);

        // XPath column
        TableColumn<XPathValidationIssue, String> xpathCol = new TableColumn<>("XPath");
        xpathCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().xpath()));
        xpathCol.setPrefWidth(200);

        // Message column
        TableColumn<XPathValidationIssue, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        messageCol.setPrefWidth(250);

        issuesTable.getColumns().addAll(severityCol, sourceCol, constraintCol, xpathCol, messageCol);

        // Selection listener
        issuesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showDetails(newVal));

        VBox box = new VBox(issuesTable);
        VBox.setVgrow(issuesTable, Priority.ALWAYS);
        return box;
    }

    /**
     * Creates the details box.
     */
    private VBox createDetailsBox() {
        Label detailsLabel = new Label("Issue Details");
        detailsLabel.setStyle("-fx-font-weight: bold;");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setStyle("-fx-font-family: monospace;");
        detailsArea.setPrefRowCount(6);
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        VBox box = new VBox(5, detailsLabel, detailsArea);
        box.setPadding(new Insets(5));
        return box;
    }

    /**
     * Gets severity icon.
     */
    private FontIcon getSeverityIcon(Severity severity) {
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
        }

        return icon;
    }

    /**
     * Shows details for the selected issue.
     */
    private void showDetails(XPathValidationIssue issue) {
        if (issue == null) {
            detailsArea.clear();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Severity: ").append(issue.severity()).append("\n");
        sb.append("Source: ").append(issue.getSourceDescription()).append("\n");
        sb.append("Constraint: ").append(issue.constraintName()).append("\n");
        sb.append("\nXPath Expression:\n");
        sb.append("  ").append(issue.xpath()).append("\n");
        sb.append("\nMessage:\n");
        sb.append("  ").append(issue.message()).append("\n");

        if (issue.matchCount() >= 0) {
            sb.append("\nSample XML Test:\n");
            sb.append("  Matches: ").append(issue.matchCount()).append("\n");
        }

        detailsArea.setText(sb.toString());
    }

    /**
     * Filters the table based on checkbox.
     */
    private void filterTable() {
        if (currentResult == null) return;

        tableData.clear();

        if (showInfoCheckBox.isSelected()) {
            tableData.addAll(currentResult.issues());
        } else {
            // Hide INFO level issues
            tableData.addAll(currentResult.issues().stream()
                    .filter(i -> i.severity() != Severity.INFO)
                    .toList());
        }
    }

    /**
     * Sets the sample XML for testing.
     *
     * @param sampleXml the sample XML document
     */
    public void setSampleXml(Document sampleXml) {
        this.sampleXml = sampleXml;
        if (validator != null) {
            validator.setSampleXml(sampleXml);
        }
    }

    /**
     * Refreshes the validation.
     */
    public void refresh() {
        logger.debug("Refreshing XPath validation");
        loadingIndicator.setVisible(true);

        // Re-create validator with current sample XML
        validator = new XsdXPathValidator(schema);
        if (sampleXml != null) {
            validator.setSampleXml(sampleXml);
        }

        executor.submit(() -> {
            try {
                ValidationResult result = validator.validateAll();

                Platform.runLater(() -> {
                    currentResult = result;
                    updateUI(result);
                    loadingIndicator.setVisible(false);
                });
            } catch (Exception e) {
                logger.error("XPath validation failed", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    totalLabel.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Updates the UI with validation result.
     */
    private void updateUI(ValidationResult result) {
        // Update summary
        totalLabel.setText("Total: " + result.totalXPaths());
        validLabel.setText("Valid: " + result.validCount());
        errorsLabel.setText("Errors: " + result.errorCount());
        warningsLabel.setText("Warnings: " + result.warningCount());

        // Update progress bar
        double validRatio = result.totalXPaths() > 0 ?
                (double) result.validCount() / result.totalXPaths() : 1.0;
        validationProgress.setProgress(validRatio);

        // Color progress bar based on result
        if (result.errorCount() > 0) {
            validationProgress.setStyle("-fx-accent: #dc3545;"); // Red
        } else if (result.warningCount() > 0) {
            validationProgress.setStyle("-fx-accent: #ffc107;"); // Yellow
        } else {
            validationProgress.setStyle("-fx-accent: #28a745;"); // Green
        }

        // Update table
        filterTable();

        // Clear details
        detailsArea.clear();
    }

    /**
     * Gets the current result.
     */
    public ValidationResult getCurrentResult() {
        return currentResult;
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        executor.shutdown();
    }
}
