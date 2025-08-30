package org.fxt.freexmltoolkit.controls;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validation Rules Panel - displays validation constraints for selected XSD nodes
 * This panel contains the same content as the Validation Rules popup but embedded in a tab.
 */
public class XsdValidationPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdValidationPanel.class);

    // UI Components
    private TabPane tabPane;
    private XsdNodeInfo currentNode;

    // Pattern Tab
    private TextArea patternField;
    private TextField testValueField;
    private Label patternResultLabel;
    private ComboBox<String> patternLibraryComboBox;

    // Enumeration Tab
    private TableView<EnumerationValue> enumerationTable;
    private ObservableList<EnumerationValue> enumerationData;

    // Range Tab
    private TextField minInclusiveField;
    private TextField maxInclusiveField;
    private TextField minExclusiveField;
    private TextField maxExclusiveField;

    // Length Tab
    private TextField lengthField;
    private TextField minLengthField;
    private TextField maxLengthField;

    // Decimal Tab
    private TextField totalDigitsField;
    private TextField fractionDigitsField;

    // Whitespace Tab
    private ComboBox<WhitespaceAction> whitespaceComboBox;

    // Custom Facets Tab
    private TableView<CustomFacet> customFacetsTable;
    private ObservableList<CustomFacet> customFacetsData;

    public XsdValidationPanel() {
        initializePanel();
        createContent();
        setupEventHandlers();

        logger.debug("XsdValidationPanel initialized");
    }

    private void initializePanel() {
        setPadding(new Insets(10));
        setSpacing(10);
        setStyle("-fx-background-color: #fafafa;");
    }

    private void createContent() {
        // Title section
        VBox titleSection = new VBox(5);
        Label titleLabel = new Label("Validation Rules");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        titleLabel.setGraphic(new FontIcon("bi-shield-check"));

        Label subtitleLabel = new Label("No node selected");
        subtitleLabel.setId("validation-subtitle");
        subtitleLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");

        titleSection.getChildren().addAll(titleLabel, subtitleLabel);

        // Validation preview panel
        VBox previewPanel = createValidationPreviewPanel();

        // Tab pane
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Initially empty - tabs will be created based on selected node
        createDefaultMessage();

        getChildren().addAll(titleSection, previewPanel, tabPane);
        VBox.setVgrow(tabPane, javafx.scene.layout.Priority.ALWAYS);
    }

    private void createDefaultMessage() {
        Tab defaultTab = new Tab("Validation");
        defaultTab.setGraphic(new FontIcon("bi-info-circle"));

        VBox content = new VBox(20);
        content.setPadding(new Insets(40));
        content.setAlignment(javafx.geometry.Pos.CENTER);

        Label messageLabel = new Label("Select a node in the diagram to view its validation rules");
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");
        messageLabel.setGraphic(new FontIcon("bi-cursor"));

        content.getChildren().add(messageLabel);
        defaultTab.setContent(content);

        tabPane.getTabs().add(defaultTab);
    }

    /**
     * Updates the validation panel for the selected node
     */
    public void updateForNode(XsdNodeInfo node) {
        this.currentNode = node;

        if (node == null) {
            showNoSelectionState();
            return;
        }

        // Update subtitle
        Label subtitleLabel = (Label) lookup("#validation-subtitle");
        if (subtitleLabel != null) {
            String dataType = determineDataType(node);
            String typeInfo = (dataType != null && !dataType.equals("unknown")) ? " (" + dataType + ")" : "";
            subtitleLabel.setText("Element: " + node.name() + typeInfo);
        }

        // Clear existing tabs and create new ones based on node type
        tabPane.getTabs().clear();
        createTabsForDataType(node);
        loadCurrentConstraints(node);

        logger.debug("Updated validation panel for node: {}", node.name());
    }

    private void showNoSelectionState() {
        this.currentNode = null;

        Label subtitleLabel = (Label) lookup("#validation-subtitle");
        if (subtitleLabel != null) {
            subtitleLabel.setText("No node selected");
        }

        tabPane.getTabs().clear();
        createDefaultMessage();
    }

    /**
     * Creates tabs based on the data type of the target node
     */
    private void createTabsForDataType(XsdNodeInfo node) {
        String dataType = determineDataType(node);
        logger.debug("Creating tabs for data type: {} (node: {})", dataType, node.name());

        // Always show these tabs for all data types
        createPatternTab();      // RegEx patterns work for all string-based types
        createEnumerationTab();  // Enumerations work for all types
        createCustomFacetsTab(); // Custom facets are always possible

        // Type-specific tabs
        if (isStringType(dataType)) {
            createLengthTab();
            createWhitespaceTab();
        }

        if (isNumericType(dataType)) {
            createRangeTab();
        }

        if (isDecimalType(dataType)) {
            createDecimalTab();
        }

        // If no specific type is determined, show all tabs (fallback)
        if (dataType == null || dataType.isEmpty() || "unknown".equals(dataType)) {
            logger.debug("Unknown data type, showing all tabs as fallback");
            if (!hasTab("Range")) createRangeTab();
            if (!hasTab("Length")) createLengthTab();
            if (!hasTab("Decimal")) createDecimalTab();
            if (!hasTab("Whitespace")) createWhitespaceTab();
        }
    }

    /**
     * Determines the data type of the target node
     */
    private String determineDataType(XsdNodeInfo node) {
        String nodeType = node.type();

        if (nodeType == null || nodeType.isEmpty()) {
            return "unknown";
        }

        // Normalize XSD type names
        if (nodeType.startsWith("xs:") || nodeType.startsWith("xsd:")) {
            return nodeType.substring(nodeType.indexOf(':') + 1).toLowerCase();
        }

        return nodeType.toLowerCase();
    }

    /**
     * Checks if the given data type is a string-based type
     */
    private boolean isStringType(String dataType) {
        if (dataType == null) return false;
        return dataType.equals("string") ||
                dataType.equals("normalizedstring") ||
                dataType.equals("token") ||
                dataType.equals("name") ||
                dataType.equals("ncname") ||
                dataType.equals("id") ||
                dataType.equals("idref") ||
                dataType.equals("idrefs") ||
                dataType.equals("entity") ||
                dataType.equals("entities") ||
                dataType.equals("nmtoken") ||
                dataType.equals("nmtokens") ||
                dataType.equals("anyuri") ||
                dataType.equals("language");
    }

    /**
     * Checks if the given data type is numeric
     */
    private boolean isNumericType(String dataType) {
        if (dataType == null) return false;
        return dataType.equals("integer") ||
                dataType.equals("int") ||
                dataType.equals("long") ||
                dataType.equals("short") ||
                dataType.equals("byte") ||
                dataType.equals("positiveinteger") ||
                dataType.equals("negativeinteger") ||
                dataType.equals("nonnegativeinteger") ||
                dataType.equals("nonpositiveinteger") ||
                dataType.equals("unsignedlong") ||
                dataType.equals("unsignedint") ||
                dataType.equals("unsignedshort") ||
                dataType.equals("unsignedbyte") ||
                dataType.equals("float") ||
                dataType.equals("double") ||
                dataType.equals("decimal");
    }

    /**
     * Checks if the given data type is decimal-based (supports precision constraints)
     */
    private boolean isDecimalType(String dataType) {
        if (dataType == null) return false;
        return dataType.equals("decimal") ||
                dataType.equals("float") ||
                dataType.equals("double");
    }

    /**
     * Checks if a tab with the given text already exists
     */
    private boolean hasTab(String tabText) {
        return tabPane.getTabs().stream()
                .anyMatch(tab -> tab.getText().equals(tabText));
    }

    private VBox createValidationPreviewPanel() {
        VBox previewPanel = new VBox(5);
        previewPanel.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 4px; -fx-padding: 10px;");

        Label titleLabel = new Label("Validation Preview");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setGraphic(new FontIcon("bi-shield-check"));

        HBox testPanel = new HBox(10);
        testPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label testLabel = new Label("Test Value:");
        TextField globalTestField = new TextField();
        globalTestField.setPromptText("Enter test value to validate against all rules...");
        globalTestField.setPrefWidth(300);

        Button testButton = new Button("Validate");
        testButton.setGraphic(new FontIcon("bi-play-circle"));
        testButton.getStyleClass().add("primary");

        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-weight: bold;");

        testPanel.getChildren().addAll(testLabel, globalTestField, testButton, resultLabel);

        testButton.setOnAction(e -> {
            String testValue = globalTestField.getText();
            validateGlobalTest(testValue, resultLabel);
        });

        previewPanel.getChildren().addAll(titleLabel, testPanel);
        return previewPanel;
    }

    private void createPatternTab() {
        Tab patternTab = new Tab("Pattern (RegEx)");
        patternTab.setGraphic(new FontIcon("bi-code-slash"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Pattern Library Section
        VBox librarySection = new VBox(10);
        Label libraryLabel = new Label("Pattern Library:");
        libraryLabel.setStyle("-fx-font-weight: bold;");

        patternLibraryComboBox = new ComboBox<>();
        patternLibraryComboBox.getItems().addAll(
                "Email Address: ^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$",
                "Phone Number: ^\\+?[1-9]\\d{1,14}$",
                "URL: ^https?://[\\w\\.-]+\\.[a-zA-Z]{2,}(/.*)?$",
                "ISBN: ^(?:ISBN(?:-13)?:?\\s)?(?=.{17}$)97[89]\\d{10}$",
                "Credit Card: ^\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}$",
                "IP Address: ^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
                "Date (YYYY-MM-DD): ^\\d{4}-\\d{2}-\\d{2}$",
                "Time (HH:MM:SS): ^\\d{2}:\\d{2}:\\d{2}$"
        );
        patternLibraryComboBox.setPromptText("Select from pattern library...");
        patternLibraryComboBox.setPrefWidth(500);

        Button usePatternButton = new Button("Use Selected Pattern");
        usePatternButton.setGraphic(new FontIcon("bi-arrow-down"));

        HBox libraryControls = new HBox(10);
        libraryControls.getChildren().addAll(patternLibraryComboBox, usePatternButton);

        librarySection.getChildren().addAll(libraryLabel, libraryControls);

        // Pattern Editor Section
        VBox editorSection = new VBox(10);
        Label editorLabel = new Label("Regular Expression Pattern:");
        editorLabel.setStyle("-fx-font-weight: bold;");

        patternField = new TextArea();
        patternField.setPrefRowCount(4);
        patternField.setPromptText("Enter regular expression pattern...");
        patternField.getStyleClass().add("code-editor");

        // Pattern Test Section
        VBox testSection = new VBox(10);
        Label testLabel = new Label("Test Pattern:");
        testLabel.setStyle("-fx-font-weight: bold;");

        HBox testControls = new HBox(10);
        testValueField = new TextField();
        testValueField.setPromptText("Enter test value...");
        testValueField.setPrefWidth(300);

        Button testPatternButton = new Button("Test");
        testPatternButton.setGraphic(new FontIcon("bi-play-circle"));
        testPatternButton.getStyleClass().add("primary");

        patternResultLabel = new Label();
        patternResultLabel.setStyle("-fx-font-weight: bold;");

        testControls.getChildren().addAll(testValueField, testPatternButton, patternResultLabel);

        editorSection.getChildren().addAll(editorLabel, patternField);
        testSection.getChildren().addAll(testLabel, testControls);

        content.getChildren().addAll(librarySection, new Separator(), editorSection, testSection);

        // Event handlers
        usePatternButton.setOnAction(e -> {
            String selected = patternLibraryComboBox.getValue();
            if (selected != null) {
                String pattern = selected.substring(selected.indexOf(": ") + 2);
                patternField.setText(pattern);
            }
        });

        testPatternButton.setOnAction(e -> testPattern());
        patternField.textProperty().addListener((obs, oldVal, newVal) -> testPattern());
        testValueField.textProperty().addListener((obs, oldVal, newVal) -> testPattern());

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        patternTab.setContent(scrollPane);
        tabPane.getTabs().add(patternTab);
    }

    private void createEnumerationTab() {
        Tab enumTab = new Tab("Enumeration");
        enumTab.setGraphic(new FontIcon("bi-list-ul"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Enumeration Values:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Table for enumeration values
        enumerationTable = new TableView<>();
        enumerationData = FXCollections.observableArrayList();
        enumerationTable.setItems(enumerationData);
        enumerationTable.setEditable(true);

        TableColumn<EnumerationValue, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setPrefWidth(400);
        valueCol.setOnEditCommit(event -> {
            EnumerationValue enumValue = event.getRowValue();
            enumValue.setValue(event.getNewValue());
            enumerationTable.refresh();
        });

        enumerationTable.getColumns().add(valueCol);
        enumerationTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Controls
        HBox controls = new HBox(10);
        Button addButton = new Button("Add Value");
        addButton.setGraphic(new FontIcon("bi-plus-circle"));
        addButton.getStyleClass().add("success");

        Button removeButton = new Button("Remove Selected");
        removeButton.setGraphic(new FontIcon("bi-trash"));
        removeButton.getStyleClass().add("danger");

        Button clearButton = new Button("Clear All");
        clearButton.setGraphic(new FontIcon("bi-x-circle"));

        controls.getChildren().addAll(addButton, removeButton, clearButton);

        content.getChildren().addAll(titleLabel, enumerationTable, controls);

        // Event handlers
        addButton.setOnAction(e -> {
            enumerationData.add(new EnumerationValue(""));
            enumerationTable.getSelectionModel().selectLast();
            enumerationTable.edit(enumerationData.size() - 1, valueCol);
        });

        removeButton.setOnAction(e -> {
            EnumerationValue selected = enumerationTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                enumerationData.remove(selected);
            }
        });

        clearButton.setOnAction(e -> {
            if (confirmClearAction("enumeration values")) {
                enumerationData.clear();
            }
        });

        enumTab.setContent(content);
        tabPane.getTabs().add(enumTab);
    }

    private void createRangeTab() {
        Tab rangeTab = new Tab("Range");
        rangeTab.setGraphic(new FontIcon("bi-sliders"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Range Constraints:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        // Min Inclusive
        Label minIncLabel = new Label("Minimum (Inclusive):");
        minInclusiveField = new TextField();
        minInclusiveField.setPromptText("Enter minimum inclusive value...");

        // Max Inclusive
        Label maxIncLabel = new Label("Maximum (Inclusive):");
        maxInclusiveField = new TextField();
        maxInclusiveField.setPromptText("Enter maximum inclusive value...");

        // Min Exclusive
        Label minExcLabel = new Label("Minimum (Exclusive):");
        minExclusiveField = new TextField();
        minExclusiveField.setPromptText("Enter minimum exclusive value...");

        // Max Exclusive
        Label maxExcLabel = new Label("Maximum (Exclusive):");
        maxExclusiveField = new TextField();
        maxExclusiveField.setPromptText("Enter maximum exclusive value...");

        grid.add(minIncLabel, 0, 0);
        grid.add(minInclusiveField, 1, 0);
        grid.add(maxIncLabel, 0, 1);
        grid.add(maxInclusiveField, 1, 1);
        grid.add(minExcLabel, 0, 2);
        grid.add(minExclusiveField, 1, 2);
        grid.add(maxExcLabel, 0, 3);
        grid.add(maxExclusiveField, 1, 3);

        // Info panel
        VBox infoPanel = createInfoPanel("Range constraints define the valid numeric range for values. " +
                "Inclusive constraints include the boundary values, while exclusive constraints exclude them.");

        content.getChildren().addAll(titleLabel, grid, infoPanel);

        rangeTab.setContent(content);
        tabPane.getTabs().add(rangeTab);
    }

    private void createLengthTab() {
        Tab lengthTab = new Tab("Length");
        lengthTab.setGraphic(new FontIcon("bi-type"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Length Constraints:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        // Exact Length
        Label lengthLabel = new Label("Exact Length:");
        lengthField = new TextField();
        lengthField.setPromptText("Enter exact length...");

        // Min Length
        Label minLengthLabel = new Label("Minimum Length:");
        minLengthField = new TextField();
        minLengthField.setPromptText("Enter minimum length...");

        // Max Length
        Label maxLengthLabel = new Label("Maximum Length:");
        maxLengthField = new TextField();
        maxLengthField.setPromptText("Enter maximum length...");

        grid.add(lengthLabel, 0, 0);
        grid.add(lengthField, 1, 0);
        grid.add(minLengthLabel, 0, 1);
        grid.add(minLengthField, 1, 1);
        grid.add(maxLengthLabel, 0, 2);
        grid.add(maxLengthField, 1, 2);

        // Info panel
        VBox infoPanel = createInfoPanel("Length constraints define the valid character count for string values. " +
                "Use exact length for fixed-size fields, or min/max for variable-length fields.");

        content.getChildren().addAll(titleLabel, grid, infoPanel);

        lengthTab.setContent(content);
        tabPane.getTabs().add(lengthTab);
    }

    private void createDecimalTab() {
        Tab decimalTab = new Tab("Decimal");
        decimalTab.setGraphic(new FontIcon("bi-calculator"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Decimal Constraints:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        // Total Digits
        Label totalDigitsLabel = new Label("Total Digits:");
        totalDigitsField = new TextField();
        totalDigitsField.setPromptText("Enter total number of digits...");

        // Fraction Digits
        Label fractionDigitsLabel = new Label("Fraction Digits:");
        fractionDigitsField = new TextField();
        fractionDigitsField.setPromptText("Enter number of fraction digits...");

        grid.add(totalDigitsLabel, 0, 0);
        grid.add(totalDigitsField, 1, 0);
        grid.add(fractionDigitsLabel, 0, 1);
        grid.add(fractionDigitsField, 1, 1);

        // Example panel
        VBox examplePanel = createInfoPanel("Examples:\n" +
                "• totalDigits=5, fractionDigits=2 allows: 123.45, -99.99, 0.01\n" +
                "• totalDigits=3 allows: 123, -99, 0 (integers)\n" +
                "• fractionDigits=2 allows: unlimited.XX decimal places");

        content.getChildren().addAll(titleLabel, grid, examplePanel);

        decimalTab.setContent(content);
        tabPane.getTabs().add(decimalTab);
    }

    private void createWhitespaceTab() {
        Tab wsTab = new Tab("Whitespace");
        wsTab.setGraphic(new FontIcon("bi-text-paragraph"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Whitespace Handling:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        whitespaceComboBox = new ComboBox<>();
        whitespaceComboBox.getItems().addAll(WhitespaceAction.values());
        whitespaceComboBox.setValue(WhitespaceAction.PRESERVE);
        whitespaceComboBox.setPrefWidth(300);

        // Description panel
        VBox descPanel = new VBox(10);
        Label descLabel = new Label("Whitespace Actions:");
        descLabel.setStyle("-fx-font-weight: bold;");

        TextArea descArea = new TextArea();
        descArea.setEditable(false);
        descArea.setPrefRowCount(8);
        descArea.setText(
                "PRESERVE: Keep all whitespace characters as-is\n" +
                        "• Input: '  hello\\n  world  '\n" +
                        "• Output: '  hello\\n  world  '\n\n" +

                        "REPLACE: Replace tabs and newlines with spaces\n" +
                        "• Input: '  hello\\n\\tworld  '\n" +
                        "• Output: '  hello  world  '\n\n" +

                        "COLLAPSE: Replace tabs/newlines with spaces and collapse multiple spaces\n" +
                        "• Input: '  hello\\n\\tworld  '\n" +
                        "• Output: ' hello world '"
        );

        descPanel.getChildren().addAll(descLabel, descArea);

        content.getChildren().addAll(titleLabel, whitespaceComboBox, descPanel);

        wsTab.setContent(content);
        tabPane.getTabs().add(wsTab);
    }

    private void createCustomFacetsTab() {
        Tab customTab = new Tab("Custom Facets");
        customTab.setGraphic(new FontIcon("bi-gear"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label titleLabel = new Label("Custom Validation Facets:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Table for custom facets
        customFacetsTable = new TableView<>();
        customFacetsData = FXCollections.observableArrayList();
        customFacetsTable.setItems(customFacetsData);
        customFacetsTable.setEditable(true);

        TableColumn<CustomFacet, String> nameCol = new TableColumn<>("Facet Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setPrefWidth(150);
        nameCol.setOnEditCommit(event -> {
            CustomFacet facet = event.getRowValue();
            facet.setName(event.getNewValue());
            customFacetsTable.refresh();
        });

        TableColumn<CustomFacet, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setPrefWidth(200);
        valueCol.setOnEditCommit(event -> {
            CustomFacet facet = event.getRowValue();
            facet.setValue(event.getNewValue());
            customFacetsTable.refresh();
        });

        TableColumn<CustomFacet, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setPrefWidth(250);
        descCol.setOnEditCommit(event -> {
            CustomFacet facet = event.getRowValue();
            facet.setDescription(event.getNewValue());
            customFacetsTable.refresh();
        });

        customFacetsTable.getColumns().addAll(nameCol, valueCol, descCol);

        // Controls
        HBox controls = new HBox(10);
        Button addButton = new Button("Add Facet");
        addButton.setGraphic(new FontIcon("bi-plus-circle"));
        addButton.getStyleClass().add("success");

        Button removeButton = new Button("Remove Selected");
        removeButton.setGraphic(new FontIcon("bi-trash"));
        removeButton.getStyleClass().add("danger");

        controls.getChildren().addAll(addButton, removeButton);

        content.getChildren().addAll(titleLabel, customFacetsTable, controls);

        // Event handlers
        addButton.setOnAction(e -> {
            customFacetsData.add(new CustomFacet("", "", ""));
            customFacetsTable.getSelectionModel().selectLast();
            customFacetsTable.edit(customFacetsData.size() - 1, nameCol);
        });

        removeButton.setOnAction(e -> {
            CustomFacet selected = customFacetsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                customFacetsData.remove(selected);
            }
        });

        customTab.setContent(content);
        tabPane.getTabs().add(customTab);
    }

    private VBox createInfoPanel(String text) {
        VBox infoPanel = new VBox(5);
        infoPanel.setStyle("-fx-background-color: #e8f4f8; -fx-border-color: #bee5eb; " +
                "-fx-border-width: 1px; -fx-border-radius: 4px; -fx-padding: 10px;");

        Label infoLabel = new Label("Information");
        infoLabel.setStyle("-fx-font-weight: bold;");
        infoLabel.setGraphic(new FontIcon("bi-info-circle"));

        TextArea infoText = new TextArea(text);
        infoText.setEditable(false);
        infoText.setWrapText(true);
        infoText.setPrefRowCount(3);
        infoText.setStyle("-fx-background-color: transparent;");

        infoPanel.getChildren().addAll(infoLabel, infoText);
        return infoPanel;
    }

    private void testPattern() {
        if (patternField == null || testValueField == null || patternResultLabel == null) {
            return;
        }

        String pattern = patternField.getText().trim();
        String testValue = testValueField.getText();

        if (pattern.isEmpty()) {
            patternResultLabel.setText("");
            patternResultLabel.setStyle("");
            return;
        }

        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            boolean matches = compiledPattern.matcher(testValue).matches();

            patternResultLabel.setText(matches ? "✓ Match" : "✗ No Match");
            patternResultLabel.setStyle(matches ? "-fx-text-fill: #28a745;" : "-fx-text-fill: #dc3545;");

        } catch (PatternSyntaxException e) {
            patternResultLabel.setText("✗ Invalid Pattern: " + e.getDescription());
            patternResultLabel.setStyle("-fx-text-fill: #dc3545;");
        }
    }

    private void validateGlobalTest(String testValue, Label resultLabel) {
        List<String> validationErrors = new ArrayList<>();
        List<String> validationSuccess = new ArrayList<>();

        // Test pattern
        if (patternField != null) {
            String pattern = patternField.getText().trim();
            if (!pattern.isEmpty()) {
                try {
                    boolean matches = Pattern.compile(pattern).matcher(testValue).matches();
                    if (matches) {
                        validationSuccess.add("Pattern");
                    } else {
                        validationErrors.add("Pattern mismatch");
                    }
                } catch (PatternSyntaxException e) {
                    validationErrors.add("Invalid pattern");
                }
            }
        }

        // Test enumeration
        if (enumerationData != null && !enumerationData.isEmpty()) {
            boolean found = enumerationData.stream().anyMatch(e -> e.getValue().equals(testValue));
            if (found) {
                validationSuccess.add("Enumeration");
            } else {
                validationErrors.add("Value not in enumeration list");
            }
        }

        // Test length constraints
        if (lengthField != null) {
            int length = testValue.length();
            if (!lengthField.getText().trim().isEmpty()) {
                try {
                    int exactLength = Integer.parseInt(lengthField.getText().trim());
                    if (length == exactLength) {
                        validationSuccess.add("Exact length");
                    } else {
                        validationErrors.add("Length must be exactly " + exactLength);
                    }
                } catch (NumberFormatException e) {
                    validationErrors.add("Invalid length constraint");
                }
            }
        }

        // Update result
        if (validationErrors.isEmpty() && !validationSuccess.isEmpty()) {
            resultLabel.setText("✓ Valid (" + String.join(", ", validationSuccess) + ")");
            resultLabel.setStyle("-fx-text-fill: #28a745;");
        } else if (!validationErrors.isEmpty()) {
            resultLabel.setText("✗ Invalid: " + String.join("; ", validationErrors));
            resultLabel.setStyle("-fx-text-fill: #dc3545;");
        } else {
            resultLabel.setText("No constraints defined");
            resultLabel.setStyle("-fx-text-fill: #6c757d;");
        }
    }

    private boolean confirmClearAction(String itemType) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear " + itemType);
        alert.setHeaderText("Are you sure you want to clear all " + itemType + "?");
        alert.setContentText("This action cannot be undone.");

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void loadCurrentConstraints(XsdNodeInfo node) {
        logger.debug("Loading current constraints for node: {}", node.name());

        try {
            // Clear existing data
            if (enumerationData != null) {
                enumerationData.clear();
            }

            // TODO: Load existing enumeration values from XSD DOM
            // TODO: Load other constraint types (pattern, range, etc.)

        } catch (Exception e) {
            logger.error("Error loading current constraints for node: " + node.name(), e);
        }
    }

    private void setupEventHandlers() {
        logger.debug("Event handlers configured for XsdValidationPanel");
    }

    // Supporting classes (reused from XsdValidationRulesEditor)
    public static class EnumerationValue {
        private String value;

        public EnumerationValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class CustomFacet {
        private String name;
        private String value;
        private String description;

        public CustomFacet(String name, String value, String description) {
            this.name = name;
            this.value = value;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public enum WhitespaceAction {
        PRESERVE("preserve"),
        REPLACE("replace"),
        COLLAPSE("collapse");

        private final String value;

        WhitespaceAction(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name() + " (" + value + ")";
        }
    }
}