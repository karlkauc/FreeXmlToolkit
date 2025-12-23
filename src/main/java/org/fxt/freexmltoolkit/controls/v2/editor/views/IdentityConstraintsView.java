package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.AnalysisResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.IdentityConstraintInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.ValidationStatus;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View displaying identity constraints (Key, KeyRef, Unique) and assertions.
 *
 * @since 2.0
 */
public class IdentityConstraintsView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(IdentityConstraintsView.class);

    private final XsdSchema schema;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IdentityConstraintsView-Worker");
        t.setDaemon(true);
        return t;
    });

    private AnalysisResult currentResult;

    // UI Components
    private Label summaryLabel;
    private TableView<IdentityConstraintInfo> constraintsTable;
    private ObservableList<IdentityConstraintInfo> tableData;
    private TextArea detailsArea;
    private ProgressIndicator loadingIndicator;

    /**
     * Creates a new identity constraints view.
     *
     * @param schema the XSD schema to analyze
     */
    public IdentityConstraintsView(XsdSchema schema) {
        this.schema = schema;
        initializeUI();
        refresh();
    }

    /**
     * Initializes the UI.
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Toolbar
        ToolBar toolbar = createToolbar();
        setTop(toolbar);

        // Main content - SplitPane with table and details
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.7);

        // Left: Table
        VBox tableBox = createTableBox();

        // Right: Details
        VBox detailsBox = createDetailsBox();

        splitPane.getItems().addAll(tableBox, detailsBox);

        // Stack pane for loading overlay
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        StackPane stackPane = new StackPane(splitPane, loadingIndicator);
        setCenter(stackPane);
    }

    /**
     * Creates the toolbar.
     */
    private ToolBar createToolbar() {
        Label titleLabel = new Label("Identity Constraints");
        titleLabel.setStyle("-fx-font-size: 14pt; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        summaryLabel = new Label("Loading...");
        summaryLabel.setStyle("-fx-text-fill: #666666;");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setGraphic(new FontIcon(BootstrapIcons.ARROW_CLOCKWISE));
        refreshBtn.setOnAction(e -> refresh());

        return new ToolBar(titleLabel, spacer, summaryLabel, refreshBtn);
    }

    /**
     * Creates the table box.
     */
    private VBox createTableBox() {
        // Create table
        tableData = FXCollections.observableArrayList();
        constraintsTable = new TableView<>(tableData);
        constraintsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Type column with icon
        TableColumn<IdentityConstraintInfo, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTypeDisplayName()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    FontIcon icon = getIconForType(item);
                    setGraphic(icon);
                }
            }
        });
        typeCol.setPrefWidth(100);

        // Name column
        TableColumn<IdentityConstraintInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        nameCol.setPrefWidth(150);

        // Parent column
        TableColumn<IdentityConstraintInfo, String> parentCol = new TableColumn<>("Parent Element");
        parentCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().parentElementName()));
        parentCol.setPrefWidth(120);

        // Selector column
        TableColumn<IdentityConstraintInfo, String> selectorCol = new TableColumn<>("Selector XPath");
        selectorCol.setCellValueFactory(data -> {
            String xpath = data.getValue().selectorXPath();
            String test = data.getValue().testExpression();
            return new SimpleStringProperty(xpath != null ? xpath : (test != null ? test : "-"));
        });
        selectorCol.setPrefWidth(200);

        // Status column with icon
        TableColumn<IdentityConstraintInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    ValidationStatus status = ValidationStatus.valueOf(item);
                    FontIcon icon = getStatusIcon(status);
                    setGraphic(icon);
                    setText(getStatusText(status));
                }
            }
        });
        statusCol.setPrefWidth(100);

        // Source file column
        TableColumn<IdentityConstraintInfo, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSourceFileName()));
        sourceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    // Show full path in tooltip
                    IdentityConstraintInfo info = getTableView().getItems().get(getIndex());
                    if (info.sourceFile() != null) {
                        setTooltip(new Tooltip(info.sourceFile().toString()));
                    }
                }
            }
        });
        sourceCol.setPrefWidth(100);

        constraintsTable.getColumns().addAll(typeCol, nameCol, parentCol, selectorCol, statusCol, sourceCol);

        // Selection listener
        constraintsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showDetails(newVal));

        VBox box = new VBox(constraintsTable);
        VBox.setVgrow(constraintsTable, Priority.ALWAYS);
        return box;
    }

    /**
     * Creates the details box.
     */
    private VBox createDetailsBox() {
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
     * Gets the icon for a constraint type.
     */
    private FontIcon getIconForType(String type) {
        FontIcon icon = new FontIcon();
        icon.setIconSize(14);

        switch (type) {
            case "Key" -> {
                icon.setIconLiteral("bi-key");
                icon.setIconColor(Color.DARKBLUE);
            }
            case "KeyRef" -> {
                icon.setIconLiteral("bi-link-45deg");
                icon.setIconColor(Color.DARKGREEN);
            }
            case "Unique" -> {
                icon.setIconLiteral("bi-hash");
                icon.setIconColor(Color.DARKORANGE);
            }
            case "Assert" -> {
                icon.setIconLiteral("bi-check-square");
                icon.setIconColor(Color.PURPLE);
            }
            default -> {
                icon.setIconLiteral("bi-question-circle");
                icon.setIconColor(Color.GRAY);
            }
        }

        return icon;
    }

    /**
     * Gets the status icon.
     */
    private FontIcon getStatusIcon(ValidationStatus status) {
        FontIcon icon = new FontIcon();
        icon.setIconSize(14);

        switch (status) {
            case VALID -> {
                icon.setIconLiteral("bi-check-circle-fill");
                icon.setIconColor(Color.GREEN);
            }
            case WARNING -> {
                icon.setIconLiteral("bi-exclamation-triangle-fill");
                icon.setIconColor(Color.ORANGE);
            }
            case ERROR -> {
                icon.setIconLiteral("bi-x-circle-fill");
                icon.setIconColor(Color.RED);
            }
        }

        return icon;
    }

    /**
     * Gets status text.
     */
    private String getStatusText(ValidationStatus status) {
        return switch (status) {
            case VALID -> "Valid";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }

    /**
     * Shows details for the selected constraint.
     */
    private void showDetails(IdentityConstraintInfo info) {
        if (info == null) {
            detailsArea.clear();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(info.getTypeDisplayName()).append("\n");
        sb.append("Name: ").append(info.name()).append("\n");
        sb.append("Parent: ").append(info.parentElementName()).append("\n");
        sb.append("\n");

        if (info.isAssert()) {
            sb.append("Test Expression:\n");
            sb.append("  ").append(info.testExpression()).append("\n");
        } else {
            sb.append("Selector XPath:\n");
            sb.append("  ").append(info.selectorXPath() != null ? info.selectorXPath() : "(none)").append("\n");
            sb.append("\n");

            sb.append("Field XPaths:\n");
            if (info.fieldXPaths().isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (String field : info.fieldXPaths()) {
                    sb.append("  - ").append(field).append("\n");
                }
            }

            if (info.isKeyRef() && info.referTo() != null) {
                sb.append("\nRefers to: ").append(info.referTo()).append("\n");
            }
        }

        sb.append("\nStatus: ").append(info.status()).append("\n");
        sb.append("Message: ").append(info.statusMessage()).append("\n");

        detailsArea.setText(sb.toString());
    }

    /**
     * Refreshes the view.
     */
    public void refresh() {
        logger.debug("Refreshing identity constraints view");
        loadingIndicator.setVisible(true);

        executor.submit(() -> {
            try {
                XsdIdentityConstraintAnalyzer analyzer = new XsdIdentityConstraintAnalyzer(schema);
                AnalysisResult result = analyzer.analyze();

                Platform.runLater(() -> {
                    currentResult = result;
                    updateUI(result);
                    loadingIndicator.setVisible(false);
                });
            } catch (Exception e) {
                logger.error("Failed to analyze identity constraints", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    summaryLabel.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Updates the UI with the analysis result.
     */
    private void updateUI(AnalysisResult result) {
        // Update summary
        summaryLabel.setText(String.format("Total: %d | Errors: %d | Warnings: %d",
                result.totalCount(), result.errorCount(), result.warningCount()));

        // Update table
        tableData.clear();
        tableData.addAll(result.getAllConstraints());

        // Clear details
        detailsArea.clear();
    }

    /**
     * Gets the current result.
     */
    public AnalysisResult getCurrentResult() {
        return currentResult;
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        executor.shutdown();
    }
}
