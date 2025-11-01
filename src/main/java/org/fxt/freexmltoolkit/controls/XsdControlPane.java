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
import org.fxt.freexmltoolkit.controls.commands.*;
import org.fxt.freexmltoolkit.controls.dialogs.AssertionEditorDialog;
import org.fxt.freexmltoolkit.controls.dialogs.XsdHelpDialog;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Integrated XSD Control Panel that combines Properties and Validation functionality
 * This panel replaces the tab-based approach with a unified interface.
 */
public class XsdControlPane extends ScrollPane {

    private static final Logger logger = LogManager.getLogger(XsdControlPane.class);

    // Current node and DOM manipulator
    private XsdNodeInfo currentNode;
    private XsdDomManipulator domManipulator;
    private XsdUndoManager undoManager;

    // Callback for property changes
    private java.util.function.Consumer<String> onPropertyChangedCallback;
    private Runnable changeCallback;

    // Main container
    private VBox mainContainer;

    // Properties Section
    private VBox propertiesSection;
    private TextField nameField;
    private TextField typeField;
    private TextField minOccursField;
    private TextField maxOccursField;
    private TextArea documentationField;
    private TextArea annotationField;
    private TextArea exampleField;
    private ListView<String> enumerationListView;
    private TextField newEnumerationField;

    // Validation Section
    private VBox validationSection;
    private Label validationTitleLabel;

    // Pattern validation
    private TextArea patternField;
    private TextField testValueField;
    private Label patternResultLabel;
    private ComboBox<String> patternLibraryComboBox;

    // Enumeration validation removed - using properties section ListView only

    // Range validation
    private TextField minInclusiveField;
    private TextField maxInclusiveField;
    private TextField minExclusiveField;
    private TextField maxExclusiveField;

    // Length validation
    private TextField lengthField;
    private TextField minLengthField;
    private TextField maxLengthField;

    // Decimal validation
    private TextField totalDigitsField;
    private TextField fractionDigitsField;

    // Whitespace validation
    private ComboBox<WhitespaceAction> whitespaceComboBox;

    // Custom facets validation
    private TableView<CustomFacet> customFacetsTable;
    private ObservableList<CustomFacet> customFacetsData;

    // XSD 1.1 Assertions Section
    private VBox assertionsSection;
    private ListView<AssertionItem> assertionsListView;
    private ObservableList<AssertionItem> assertionsData;
    private VBox assertionsUI;  // Container for actual assertions list and controls

    public XsdControlPane() {
        initializePane();
        createContent();
        setupEventHandlers();
        logger.debug("XsdControlPane initialized");
    }

    private void initializePane() {
        setFitToWidth(true);
        setStyle("-fx-background-color: #fafafa;");

        mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(15));
        setContent(mainContainer);
    }

    private void createContent() {
        createPropertiesSection();
        createValidationSection();
        createAssertionsSection();
        showNoSelectionState();
    }

    private void createPropertiesSection() {
        propertiesSection = new VBox(15);
        propertiesSection.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 5px; -fx-padding: 15px;");

        // Title with Help button
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label("Element Properties");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        titleLabel.setGraphic(new FontIcon("bi-gear"));

        // Add spacer to push help button to the right
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // Help button
        Button helpButton = new Button("Help");
        helpButton.setGraphic(new FontIcon("bi-question-circle"));
        helpButton.setStyle("-fx-background-color: #0d6efd; -fx-text-fill: white; -fx-font-weight: bold;");
        helpButton.setOnAction(e -> showHelp());

        titleBox.getChildren().addAll(titleLabel, spacer, helpButton);

        // Properties grid
        GridPane propertiesGrid = new GridPane();
        propertiesGrid.setHgap(10);
        propertiesGrid.setVgap(10);

        // Name field
        Label nameLabel = new Label("Name:");
        nameField = new TextField();
        nameField.setEditable(false);
        nameField.setStyle("-fx-background-color: #f8f9fa;");

        // Type field
        Label typeLabel = new Label("Type:");
        typeField = new TextField();
        typeField.setEditable(false);
        typeField.setStyle("-fx-background-color: #f8f9fa;");

        // MinOccurs field
        Label minOccursLabel = new Label("Min Occurs:");
        minOccursField = new TextField();
        minOccursField.setEditable(false);
        minOccursField.setStyle("-fx-background-color: #f8f9fa;");

        // MaxOccurs field
        Label maxOccursLabel = new Label("Max Occurs:");
        maxOccursField = new TextField();
        maxOccursField.setEditable(false);
        maxOccursField.setStyle("-fx-background-color: #f8f9fa;");

        propertiesGrid.add(nameLabel, 0, 0);
        propertiesGrid.add(nameField, 1, 0);
        propertiesGrid.add(typeLabel, 0, 1);
        propertiesGrid.add(typeField, 1, 1);
        propertiesGrid.add(minOccursLabel, 0, 2);
        propertiesGrid.add(minOccursField, 1, 2);
        propertiesGrid.add(maxOccursLabel, 0, 3);
        propertiesGrid.add(maxOccursField, 1, 3);

        // Documentation section
        Label docLabel = new Label("Documentation:");
        docLabel.setStyle("-fx-font-weight: bold;");
        documentationField = new TextArea();
        documentationField.setPrefRowCount(3);
        documentationField.setEditable(false);
        documentationField.setStyle("-fx-background-color: #f8f9fa;");

        // Annotation section
        Label annoLabel = new Label("Annotations:");
        annoLabel.setStyle("-fx-font-weight: bold;");
        annotationField = new TextArea();
        annotationField.setPrefRowCount(2);
        annotationField.setEditable(false);
        annotationField.setStyle("-fx-background-color: #f8f9fa;");

        // Enumeration section
        Label enumLabel = new Label("Enumerations:");
        enumLabel.setStyle("-fx-font-weight: bold;");
        enumLabel.setGraphic(new FontIcon("bi-list-ul"));

        enumerationListView = new ListView<>();
        enumerationListView.setPrefHeight(100);
        enumerationListView.setStyle("-fx-background-color: #f8f9fa;");

        HBox enumControls = new HBox(5);
        newEnumerationField = new TextField();
        newEnumerationField.setPromptText("Add new enumeration value...");
        Button addEnumButton = new Button("Add");
        addEnumButton.setGraphic(new FontIcon("bi-plus"));
        addEnumButton.setOnAction(e -> addEnumerationValue());
        Button removeEnumButton = new Button("Remove");
        removeEnumButton.setGraphic(new FontIcon("bi-trash"));
        removeEnumButton.setOnAction(e -> removeSelectedEnumeration());
        enumControls.getChildren().addAll(newEnumerationField, addEnumButton, removeEnumButton);

        // Example section
        Label exampleLabel = new Label("Example:");
        exampleLabel.setStyle("-fx-font-weight: bold;");
        exampleField = new TextArea();
        exampleField.setPrefRowCount(3);
        exampleField.setEditable(false);
        exampleField.setStyle("-fx-background-color: #f8f9fa;");

        // Save button section
        HBox saveButtonContainer = new HBox();
        saveButtonContainer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        saveButtonContainer.setPadding(new Insets(10, 0, 0, 0));

        Button saveButton = new Button("Save Changes");
        saveButton.setGraphic(new FontIcon("bi-check-circle"));
        saveButton.getStyleClass().addAll("btn", "btn-success");
        saveButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        saveButton.setOnAction(e -> saveChanges());

        saveButtonContainer.getChildren().add(saveButton);

        propertiesSection.getChildren().addAll(
                titleBox,
                propertiesGrid,
                new Separator(),
                docLabel, documentationField,
                annoLabel, annotationField,
                enumLabel, enumerationListView, enumControls,
                exampleLabel, exampleField,
                saveButtonContainer
        );

        mainContainer.getChildren().add(propertiesSection);
    }

    private void createValidationSection() {
        validationSection = new VBox(15);
        validationSection.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 5px; -fx-padding: 15px;");

        // Title
        validationTitleLabel = new Label("Validation Rules");
        validationTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        validationTitleLabel.setGraphic(new FontIcon("bi-shield-check"));

        // Global validation test
        VBox testSection = createGlobalTestSection();

        // Pattern validation
        VBox patternSection = createPatternSection();

        // Enumeration validation removed - handled in properties section

        // Range validation
        VBox rangeSection = createRangeSection();

        // Length validation  
        VBox lengthSection = createLengthSection();

        // Decimal validation
        VBox decimalSection = createDecimalSection();

        // Whitespace validation
        VBox whitespaceSection = createWhitespaceSection();

        // Custom facets
        VBox customFacetsSection = createCustomFacetsSection();

        validationSection.getChildren().addAll(
                validationTitleLabel,
                testSection,
                new Separator(),
                patternSection,
                new Separator(),
                rangeSection,
                new Separator(),
                lengthSection,
                new Separator(),
                decimalSection,
                new Separator(),
                whitespaceSection,
                new Separator(),
                customFacetsSection
        );

        mainContainer.getChildren().add(validationSection);
    }

    private void createAssertionsSection() {
        assertionsSection = new VBox(15);
        assertionsSection.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 5px; -fx-padding: 15px;");

        // Title
        Label titleLabel = new Label("XSD 1.1 Assertions");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        FontIcon assertIcon = new FontIcon("bi-check-circle");
        assertIcon.setIconColor(javafx.scene.paint.Color.web("#1976d2"));
        titleLabel.setGraphic(assertIcon);

        // Info label
        Label infoLabel = new Label("Define XPath 2.0 expressions to validate element content");
        infoLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        // Create assertions UI (for both complexTypes and simpleTypes)
        assertionsUI = createAssertionsUI();

        // Initially hide the section (will be shown only for compatible nodes)
        assertionsSection.setVisible(false);
        assertionsSection.setManaged(false);

        assertionsSection.getChildren().addAll(
                titleLabel,
                infoLabel,
                new Separator(),
                assertionsUI
        );

        mainContainer.getChildren().add(assertionsSection);
    }

    private VBox createAssertionsUI() {
        VBox container = new VBox(10);

        // Assertions list
        assertionsData = FXCollections.observableArrayList();
        assertionsListView = new ListView<>(assertionsData);
        assertionsListView.setPrefHeight(150);
        assertionsListView.setPlaceholder(new Label("No assertions defined"));

        // Custom cell factory for better display
        assertionsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AssertionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cellContent = new VBox(3);

                    // Test expression
                    Label testLabel = new Label(item.testExpression);
                    testLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold;");

                    // Documentation (if present)
                    if (item.documentation != null && !item.documentation.isEmpty()) {
                        Label docLabel = new Label(item.documentation);
                        docLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 10px;");
                        docLabel.setWrapText(true);
                        cellContent.getChildren().addAll(testLabel, docLabel);
                    } else {
                        cellContent.getChildren().add(testLabel);
                    }

                    setGraphic(cellContent);
                }
            }
        });

        // Controls
        HBox controls = new HBox(10);
        controls.setPadding(new Insets(5, 0, 0, 0));

        Button addButton = new Button("Add Assertion");
        addButton.setGraphic(new FontIcon("bi-plus"));
        addButton.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; -fx-font-weight: bold;");
        addButton.setOnAction(e -> showAddAssertionDialog());

        Button editButton = new Button("Edit");
        editButton.setGraphic(new FontIcon("bi-pencil"));
        editButton.setOnAction(e -> showEditAssertionDialog());
        editButton.disableProperty().bind(assertionsListView.getSelectionModel().selectedItemProperty().isNull());

        Button deleteButton = new Button("Delete");
        deleteButton.setGraphic(new FontIcon("bi-trash"));
        deleteButton.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;");
        deleteButton.setOnAction(e -> deleteSelectedAssertion());
        deleteButton.disableProperty().bind(assertionsListView.getSelectionModel().selectedItemProperty().isNull());

        controls.getChildren().addAll(addButton, editButton, deleteButton);

        container.getChildren().addAll(assertionsListView, controls);
        return container;
    }

    private VBox createGlobalTestSection() {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 4px; -fx-padding: 10px;");

        Label titleLabel = new Label("Validation Test");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-play-circle"));

        HBox testPanel = new HBox(10);
        testPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label testLabel = new Label("Test Value:");
        TextField globalTestField = new TextField();
        globalTestField.setPromptText("Enter test value...");
        globalTestField.setPrefWidth(300);

        Button testButton = new Button("Validate");
        testButton.setGraphic(new FontIcon("bi-check-circle"));
        testButton.getStyleClass().add("primary");

        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-weight: bold;");

        testPanel.getChildren().addAll(testLabel, globalTestField, testButton, resultLabel);

        testButton.setOnAction(e -> {
            String testValue = globalTestField.getText();
            validateGlobalTest(testValue, resultLabel);
        });

        section.getChildren().addAll(titleLabel, testPanel);
        return section;
    }

    private VBox createPatternSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Pattern (Regular Expression)");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-code-slash"));

        // Pattern library
        patternLibraryComboBox = new ComboBox<>();
        patternLibraryComboBox.getItems().addAll(
                "Email: ^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$",
                "Phone: ^\\+?[1-9]\\d{1,14}$",
                "URL: ^https?://[\\w\\.-]+\\.[a-zA-Z]{2,}(/.*)?$",
                "Date (YYYY-MM-DD): ^\\d{4}-\\d{2}-\\d{2}$"
        );
        patternLibraryComboBox.setPromptText("Select pattern...");

        Button usePatternButton = new Button("Use");
        usePatternButton.setGraphic(new FontIcon("bi-arrow-down"));
        usePatternButton.setOnAction(e -> {
            String selected = patternLibraryComboBox.getValue();
            if (selected != null) {
                String pattern = selected.substring(selected.indexOf(": ") + 2);
                patternField.setText(pattern);
            }
        });

        HBox patternLibrary = new HBox(10);
        patternLibrary.getChildren().addAll(patternLibraryComboBox, usePatternButton);

        // Pattern editor
        patternField = new TextArea();
        patternField.setPrefRowCount(2);
        patternField.setPromptText("Enter regular expression...");

        // Pattern test
        HBox testControls = new HBox(10);
        testValueField = new TextField();
        testValueField.setPromptText("Test value...");
        testValueField.setPrefWidth(200);

        Button testPatternButton = new Button("Test");
        testPatternButton.setGraphic(new FontIcon("bi-play"));
        testPatternButton.setOnAction(e -> testPattern());

        patternResultLabel = new Label();
        patternResultLabel.setStyle("-fx-font-weight: bold;");

        testControls.getChildren().addAll(testValueField, testPatternButton, patternResultLabel);

        // Auto-test on text change
        patternField.textProperty().addListener((obs, oldVal, newVal) -> testPattern());
        testValueField.textProperty().addListener((obs, oldVal, newVal) -> testPattern());

        section.getChildren().addAll(titleLabel, patternLibrary, patternField, testControls);
        return section;
    }


    private VBox createRangeSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Range Constraints");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-sliders"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        Label minIncLabel = new Label("Min (Inclusive):");
        minInclusiveField = new TextField();
        minInclusiveField.setPromptText("Minimum value...");

        Label maxIncLabel = new Label("Max (Inclusive):");
        maxInclusiveField = new TextField();
        maxInclusiveField.setPromptText("Maximum value...");

        Label minExcLabel = new Label("Min (Exclusive):");
        minExclusiveField = new TextField();
        minExclusiveField.setPromptText("Minimum exclusive...");

        Label maxExcLabel = new Label("Max (Exclusive):");
        maxExclusiveField = new TextField();
        maxExclusiveField.setPromptText("Maximum exclusive...");

        grid.add(minIncLabel, 0, 0);
        grid.add(minInclusiveField, 1, 0);
        grid.add(maxIncLabel, 0, 1);
        grid.add(maxInclusiveField, 1, 1);
        grid.add(minExcLabel, 0, 2);
        grid.add(minExclusiveField, 1, 2);
        grid.add(maxExcLabel, 0, 3);
        grid.add(maxExclusiveField, 1, 3);

        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    private VBox createLengthSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Length Constraints");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-type"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        Label lengthLabel = new Label("Exact Length:");
        lengthField = new TextField();
        lengthField.setPromptText("Exact length...");

        Label minLengthLabel = new Label("Min Length:");
        minLengthField = new TextField();
        minLengthField.setPromptText("Minimum length...");

        Label maxLengthLabel = new Label("Max Length:");
        maxLengthField = new TextField();
        maxLengthField.setPromptText("Maximum length...");

        grid.add(lengthLabel, 0, 0);
        grid.add(lengthField, 1, 0);
        grid.add(minLengthLabel, 0, 1);
        grid.add(minLengthField, 1, 1);
        grid.add(maxLengthLabel, 0, 2);
        grid.add(maxLengthField, 1, 2);

        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    private VBox createDecimalSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Decimal Constraints");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-calculator"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        Label totalDigitsLabel = new Label("Total Digits:");
        totalDigitsField = new TextField();
        totalDigitsField.setPromptText("Total digits...");

        Label fractionDigitsLabel = new Label("Fraction Digits:");
        fractionDigitsField = new TextField();
        fractionDigitsField.setPromptText("Fraction digits...");

        grid.add(totalDigitsLabel, 0, 0);
        grid.add(totalDigitsField, 1, 0);
        grid.add(fractionDigitsLabel, 0, 1);
        grid.add(fractionDigitsField, 1, 1);

        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    private VBox createWhitespaceSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Whitespace Handling");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-text-paragraph"));

        whitespaceComboBox = new ComboBox<>();
        whitespaceComboBox.getItems().addAll(WhitespaceAction.values());
        whitespaceComboBox.setValue(WhitespaceAction.PRESERVE);
        whitespaceComboBox.setPrefWidth(200);

        section.getChildren().addAll(titleLabel, whitespaceComboBox);
        return section;
    }

    private VBox createCustomFacetsSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Custom Facets");
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setGraphic(new FontIcon("bi-gear"));

        // Table
        customFacetsTable = new TableView<>();
        customFacetsData = FXCollections.observableArrayList();
        customFacetsTable.setItems(customFacetsData);
        customFacetsTable.setEditable(true);
        customFacetsTable.setPrefHeight(120);

        TableColumn<CustomFacet, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setPrefWidth(120);

        TableColumn<CustomFacet, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setPrefWidth(150);

        TableColumn<CustomFacet, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setPrefWidth(200);

        customFacetsTable.getColumns().addAll(nameCol, valueCol, descCol);

        // Controls
        HBox controls = new HBox(10);
        Button addButton = new Button("Add");
        addButton.setGraphic(new FontIcon("bi-plus"));
        addButton.setOnAction(e -> {
            customFacetsData.add(new CustomFacet("", "", ""));
            customFacetsTable.getSelectionModel().selectLast();
            customFacetsTable.edit(customFacetsData.size() - 1, nameCol);
        });

        Button removeButton = new Button("Remove");
        removeButton.setGraphic(new FontIcon("bi-trash"));
        removeButton.setOnAction(e -> {
            CustomFacet selected = customFacetsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                customFacetsData.remove(selected);
            }
        });

        controls.getChildren().addAll(addButton, removeButton);

        section.getChildren().addAll(titleLabel, customFacetsTable, controls);
        return section;
    }

    public void updateForNode(XsdNodeInfo node, XsdDomManipulator domManipulator) {
        this.currentNode = node;
        this.domManipulator = domManipulator;

        if (node == null) {
            showNoSelectionState();
            return;
        }

        // Update properties section
        updatePropertiesSection(node);

        // Update validation section
        updateValidationSection(node);

        // Update assertions section
        updateAssertionsSection(node);

        logger.debug("Updated XsdControlPane for node: {}", node.name());
    }

    private void updatePropertiesSection(XsdNodeInfo node) {
        // Update basic properties
        nameField.setText(node.name() != null ? node.name() : "");
        typeField.setText(node.type() != null ? node.type() : "");
        minOccursField.setText(node.minOccurs() != null ? node.minOccurs() : "");
        maxOccursField.setText(node.maxOccurs() != null ? node.maxOccurs() : "");

        // Update documentation
        String documentation = (node.documentation() != null) ? node.documentation() : "";
        documentationField.setText(documentation);

        // Update annotations (using documentation field as XsdNodeInfo doesn't have annotation)
        String annotations = (node.documentation() != null) ? node.documentation() : "";
        annotationField.setText(annotations);

        // Load enumerations
        loadEnumerations();

        // Update example
        String example = generateExample(node);
        exampleField.setText(example);
    }

    private void updateValidationSection(XsdNodeInfo node) {
        String dataType = determineDataType(node);
        String typeInfo = (dataType != null && !dataType.equals("unknown")) ? " (" + dataType + ")" : "";
        validationTitleLabel.setText("Validation Rules - " + node.name() + typeInfo);

        // Show/hide sections based on data type
        updateValidationSectionVisibility(dataType);

        // Load current constraints
        loadCurrentConstraints(node);
    }

    private void updateValidationSectionVisibility(String dataType) {
        // For now, show all sections - could be optimized to show only relevant ones
        // based on data type like in the original XsdValidationPanel
    }

    private void updateAssertionsSection(XsdNodeInfo node) {
        // Assertions are applicable to:
        // 1. Global complexType nodes
        // 2. Global simpleType nodes
        // 3. Element nodes with inline complexType
        // 4. Element nodes with inline simpleType
        // 5. Element nodes within a complexType (assertions will be added to parent complexType)

        boolean isCompatibleNode = false;

        if (node.nodeType() == XsdNodeInfo.NodeType.COMPLEX_TYPE) {
            // Global complexType: always compatible
            isCompatibleNode = true;
        } else if (node.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE) {
            // Global simpleType: always compatible
            isCompatibleNode = true;
        } else if (node.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
            // Element: compatible if it has inline type OR is within a complexType
            if (hasInlineComplexType(node) || hasInlineSimpleType(node)) {
                isCompatibleNode = true;
            } else {
                // Check if element is within a complexType (not inline, but parent)
                isCompatibleNode = isElementWithinComplexType(node);
            }
        }

        if (!isCompatibleNode) {
            // Hide section for incompatible nodes
            assertionsSection.setVisible(false);
            assertionsSection.setManaged(false);
            if (assertionsData != null) {
                assertionsData.clear();
            }
            return;
        }

        // Show section for compatible nodes (both complexTypes and simpleTypes)
        assertionsSection.setVisible(true);
        assertionsSection.setManaged(true);
        assertionsUI.setVisible(true);
        assertionsUI.setManaged(true);

        // Load assertions from the node (or parent complexType if applicable)
        loadAssertions(node);
    }

    /**
     * Creates a XsdNodeInfo for the parent complexType of the given element node
     */
    private XsdNodeInfo getParentComplexTypeNodeInfo(XsdNodeInfo node) {
        if (node == null || domManipulator == null) {
            return null;
        }

        try {
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                return null;
            }

            Element parentComplexType = findParentComplexType(element);
            if (parentComplexType == null) {
                return null;
            }

            // Get the name of the parent complexType
            String complexTypeName = parentComplexType.getAttribute("name");
            if (complexTypeName == null || complexTypeName.isEmpty()) {
                complexTypeName = "anonymous";
            }

            // Build xpath for the parent complexType
            // XsdDomManipulator expects simple paths like "/MenuItemType", not XPath predicates
            String complexTypeXPath = "/" + complexTypeName;

            logger.debug("Created parent complexType XPath: {} for element: {}", complexTypeXPath, node.name());

            // Create XsdNodeInfo for the parent complexType
            return new XsdNodeInfo(
                    complexTypeName,
                    null,
                    complexTypeXPath,
                    null,
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    null,
                    null,
                    XsdNodeInfo.NodeType.COMPLEX_TYPE
            );

        } catch (Exception e) {
            logger.error("Error getting parent complexType node info for: " + node.name(), e);
            return null;
        }
    }

    /**
     * Finds the parent complexType element in the DOM tree
     */
    private Element findParentComplexType(Element element) {
        if (element == null) {
            return null;
        }

        org.w3c.dom.Node current = element.getParentNode();
        while (current != null) {
            if (current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element currentElement = (Element) current;
                if ("complexType".equals(currentElement.getLocalName()) &&
                        "http://www.w3.org/2001/XMLSchema".equals(currentElement.getNamespaceURI())) {
                    return currentElement;
                }
            }
            current = current.getParentNode();
        }
        return null;
    }

    /**
     * Checks if an element is within a complexType (i.e., has a complexType ancestor in the DOM)
     */
    private boolean isElementWithinComplexType(XsdNodeInfo node) {
        if (node == null || domManipulator == null) {
            return false;
        }

        try {
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                return false;
            }

            // Walk up the DOM tree to find a complexType ancestor
            Element parentComplexType = findParentComplexType(element);
            if (parentComplexType != null) {
                logger.debug("Element {} is within complexType: {}", node.name(), parentComplexType.getAttribute("name"));
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking if element is within complexType: " + node.name(), e);
            return false;
        }
    }

    /**
     * Checks if the given node has an inline complexType (not a type reference)
     */
    private boolean hasInlineComplexType(XsdNodeInfo node) {
        if (node == null || domManipulator == null) {
            return false;
        }

        try {
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                return false;
            }

            // Check for inline complexType only
            NodeList complexTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
            return complexTypes.getLength() > 0;

        } catch (Exception e) {
            logger.error("Error checking if node has inline complexType: " + node.name(), e);
            return false;
        }
    }

    /**
     * Checks if the given node has an inline simpleType (not a type reference)
     */
    private boolean hasInlineSimpleType(XsdNodeInfo node) {
        if (node == null || domManipulator == null) {
            return false;
        }

        try {
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                return false;
            }

            // Check for inline simpleType only
            NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
            return simpleTypes.getLength() > 0;

        } catch (Exception e) {
            logger.error("Error checking if node has inline simpleType: " + node.name(), e);
            return false;
        }
    }

    /**
     * Checks if the given node is a simpleType node or has an inline simpleType.
     * This determines whether to use simpleType assertion commands.
     */
    private boolean isSimpleTypeForAssertions(XsdNodeInfo node) {
        if (node == null) {
            return false;
        }

        // Global simpleType nodes
        if (node.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE) {
            logger.debug("Node is a SIMPLE_TYPE: {}", node.name());
            return true;
        }

        // Elements with inline simpleType
        if (node.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
            boolean hasInline = hasInlineSimpleType(node);
            logger.debug("Element {} has inline simpleType: {}", node.name(), hasInline);

            if (hasInline) {
                return true;
            }

            // Check if element has a type reference to a simpleType
            boolean hasSimpleTypeRef = hasSimpleTypeReference(node);
            logger.debug("Element {} has simpleType reference: {}", node.name(), hasSimpleTypeRef);
            return hasSimpleTypeRef;
        }

        return false;
    }

    /**
     * Checks if an element has a type attribute that references a simpleType
     * (either built-in or custom).
     */
    private boolean hasSimpleTypeReference(XsdNodeInfo node) {
        if (node == null || domManipulator == null) {
            return false;
        }

        try {
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                return false;
            }

            String typeAttr = element.getAttribute("type");
            if (typeAttr == null || typeAttr.isEmpty()) {
                return false;
            }

            // Check if it's a built-in XSD simple type
            if (typeAttr.startsWith("xs:") || typeAttr.startsWith("xsd:")) {
                String typeName = typeAttr.substring(typeAttr.indexOf(':') + 1);
                // List of XSD built-in simple types
                return isBuiltInSimpleType(typeName);
            }

            // Check if it references a custom simpleType in the schema
            String localName = typeAttr;
            if (typeAttr.contains(":")) {
                localName = typeAttr.substring(typeAttr.indexOf(":") + 1);
            }

            // Try to find the referenced type
            Element referencedType = findTypeDefinition(localName);
            if (referencedType != null) {
                return "simpleType".equals(referencedType.getLocalName());
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking if node has simpleType reference: " + node.name(), e);
            return false;
        }
    }

    /**
     * Checks if a type name is a built-in XSD simple type.
     */
    private boolean isBuiltInSimpleType(String typeName) {
        return switch (typeName) {
            case "string", "boolean", "decimal", "float", "double",
                 "duration", "dateTime", "time", "date", "gYearMonth", "gYear",
                 "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary",
                 "anyURI", "QName", "NOTATION",
                 "normalizedString", "token", "language", "NMTOKEN", "NMTOKENS",
                 "Name", "NCName", "ID", "IDREF", "IDREFS", "ENTITY", "ENTITIES",
                 "integer", "nonPositiveInteger", "negativeInteger", "long",
                 "int", "short", "byte", "nonNegativeInteger", "unsignedLong",
                 "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger" -> true;
            default -> false;
        };
    }

    private void loadAssertions(XsdNodeInfo node) {
        if (assertionsData == null || domManipulator == null || node == null) {
            return;
        }

        assertionsData.clear();

        try {
            // Find the DOM element for this node
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                logger.debug("Element not found for xpath: {}", node.xpath());
                return;
            }

            // For elements within a complexType (but not inline), load assertions from parent
            Element targetElement = element;
            if (node.nodeType() == XsdNodeInfo.NodeType.ELEMENT &&
                    !hasInlineComplexType(node) &&
                    !hasInlineSimpleType(node) &&
                    isElementWithinComplexType(node)) {
                // Find parent complexType
                targetElement = findParentComplexType(element);
                if (targetElement != null) {
                    logger.debug("Loading assertions from parent complexType for element: {}", node.name());
                } else {
                    targetElement = element;  // Fallback to original element
                }
            }

            // Get assertions from the DOM
            List<Element> assertionElements = findAssertionElements(targetElement);

            // Convert DOM assertions to AssertionItem objects
            for (Element assertElement : assertionElements) {
                String testExpr = assertElement.getAttribute("test");
                String namespace = assertElement.getAttribute("xpath-default-namespace");

                // Extract documentation from xs:annotation/xs:documentation
                String doc = extractDocumentation(assertElement);

                // Create a lightweight XsdNodeInfo for the assertion (needed for edit/delete)
                // We'll use the node's xpath + "/assert" as a simple identifier
                XsdNodeInfo assertionNodeInfo = createAssertionNodeInfo(node, assertElement, testExpr);

                AssertionItem item = new AssertionItem(
                        assertionNodeInfo,
                        testExpr,
                        namespace.isEmpty() ? null : namespace,
                        doc
                );
                assertionsData.add(item);
            }

            logger.debug("Loaded {} assertions for node: {}", assertionsData.size(), node.name());

        } catch (Exception e) {
            logger.error("Error loading assertions for node: " + node.name(), e);
        }
    }

    /**
     * Finds all xs:assert elements within a given element.
     * Handles both complexType assertions (direct children) and simpleType assertions (within restriction).
     */
    private List<Element> findAssertionElements(Element element) {
        List<Element> assertions = new ArrayList<>();
        String localName = element.getLocalName();

        if ("simpleType".equals(localName)) {
            // For global simpleType: assertions are inside xs:restriction
            NodeList restrictions = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
            for (int i = 0; i < restrictions.getLength(); i++) {
                Element restriction = (Element) restrictions.item(i);
                addDirectChildAssertions(restriction, assertions);
            }
        } else if ("complexType".equals(localName)) {
            // For global complexType: assertions are direct children
            addDirectChildAssertions(element, assertions);
        } else if ("element".equals(localName)) {
            // For element nodes, check for inline simpleType or complexType
            // First, check for inline simpleType with assertions in restriction
            NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
            if (simpleTypes.getLength() > 0) {
                Element simpleType = (Element) simpleTypes.item(0);
                NodeList restrictions = simpleType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
                for (int i = 0; i < restrictions.getLength(); i++) {
                    Element restriction = (Element) restrictions.item(i);
                    addDirectChildAssertions(restriction, assertions);
                }
            }

            // Also check for inline complexType with direct assertions
            NodeList complexTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
            if (complexTypes.getLength() > 0) {
                Element complexType = (Element) complexTypes.item(0);
                addDirectChildAssertions(complexType, assertions);
            }
        }

        return assertions;
    }

    /**
     * Adds all xs:assert elements that are direct children of the given parent element.
     */
    private void addDirectChildAssertions(Element parent, List<Element> assertions) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("assert".equals(childElement.getLocalName()) &&
                        "http://www.w3.org/2001/XMLSchema".equals(childElement.getNamespaceURI())) {
                    assertions.add(childElement);
                }
            }
        }
    }

    /**
     * Extracts documentation text from an xs:assert element's xs:annotation/xs:documentation.
     */
    private String extractDocumentation(Element assertElement) {
        NodeList annotations = assertElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            NodeList docs = annotation.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "documentation");
            if (docs.getLength() > 0) {
                return docs.item(0).getTextContent();
            }
        }
        return null;
    }

    /**
     * Creates a lightweight XsdNodeInfo for an assertion element.
     * This is needed for edit/delete operations.
     */
    private XsdNodeInfo createAssertionNodeInfo(XsdNodeInfo parentNode, Element assertElement, String testExpr) {
        // Build xpath for this assertion
        String assertXPath = parentNode.xpath() + "/assert";

        // Create a minimal XsdNodeInfo for the assertion using the correct constructor signature
        return new XsdNodeInfo(
                "assert",                                   // name
                null,                                       // type
                assertXPath,                                // xpath
                null,                                       // documentation (we handle it separately)
                java.util.Collections.emptyList(),          // children
                java.util.Collections.emptyList(),          // exampleValues
                null,                                       // minOccurs
                null,                                       // maxOccurs
                XsdNodeInfo.NodeType.ASSERT,                // nodeType
                testExpr,                                   // xpathExpression (the test attribute)
                java.util.Collections.emptyMap()            // xsd11Attributes
        );
    }

    private void showAddAssertionDialog() {
        if (currentNode == null || domManipulator == null) {
            showAlert("No Element Selected", "Please select an element to add an assertion.", "warning");
            return;
        }

        try {
            // Determine the target node for the assertion
            XsdNodeInfo targetNode = currentNode;
            String elementContext = currentNode.name() != null ? currentNode.name() : "";

            // Check if current node is within a complexType (but not inline)
            if (currentNode.nodeType() == XsdNodeInfo.NodeType.ELEMENT &&
                    !hasInlineComplexType(currentNode) &&
                    !hasInlineSimpleType(currentNode) &&
                    isElementWithinComplexType(currentNode)) {
                // Create XsdNodeInfo for parent complexType
                targetNode = getParentComplexTypeNodeInfo(currentNode);
                if (targetNode != null) {
                    elementContext = "Parent complexType (" + targetNode.name() + ")";
                    logger.debug("Adding assertion to parent complexType: {}", targetNode.name());
                } else {
                    // Fallback to current node if parent not found
                    targetNode = currentNode;
                }
            }

            // Check if target node is a simpleType or element with inline simpleType
            boolean isSimpleType = isSimpleTypeForAssertions(targetNode);

            if (elementContext.isEmpty()) {
                elementContext = targetNode.name() != null ? targetNode.name() :
                        (isSimpleType ? "simpleType" : "complexType");
            }

            AssertionEditorDialog dialog = new AssertionEditorDialog(elementContext, isSimpleType);

            XsdNodeInfo finalTargetNode = targetNode;  // For lambda capture
            dialog.showAndWait().ifPresent(result -> {
                XsdCommand command;

                if (isSimpleType) {
                    // Use AddSimpleTypeAssertionCommand for simpleTypes and elements with inline simpleTypes
                    command = new AddSimpleTypeAssertionCommand(
                            domManipulator,
                            finalTargetNode,
                            result.testExpression(),
                            result.xpathDefaultNamespace(),
                            result.documentation()
                    );
                } else {
                    // Use AddAssertionCommand for complexTypes
                    command = new AddAssertionCommand(
                            domManipulator,
                            finalTargetNode,
                            result.testExpression(),
                            result.xpathDefaultNamespace(),
                            result.documentation()
                    );
                }

                if (undoManager != null && undoManager.executeCommand(command)) {
                    logger.info("Assertion added successfully");
                    // Reload assertions to update the UI immediately
                    loadAssertions(currentNode);
                    // Notify that changes were made (triggers save)
                    if (changeCallback != null) {
                        changeCallback.run();
                    }
                } else {
                    showAlert("Failed to add assertion", "Could not add the assertion", "error");
                }
            });

        } catch (Exception e) {
            logger.error("Error showing add assertion dialog", e);
            showAlert("Error", "Failed to open assertion dialog: " + e.getMessage(), "error");
        }
    }

    private void showEditAssertionDialog() {
        AssertionItem selected = assertionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        try {
            // Check if current node is a simpleType or element with inline simpleType
            boolean isSimpleType = isSimpleTypeForAssertions(currentNode);

            String elementContext = currentNode.name() != null ? currentNode.name() :
                    (isSimpleType ? "simpleType" : "complexType");
            AssertionEditorDialog dialog = new AssertionEditorDialog(
                    elementContext,
                    isSimpleType,
                    selected.testExpression,
                    selected.xpathDefaultNamespace,
                    selected.documentation
            );

            dialog.showAndWait().ifPresent(result -> {
                EditAssertionCommand command = new EditAssertionCommand(
                        domManipulator,
                        selected.assertionNode,
                        result.testExpression(),
                        result.xpathDefaultNamespace(),
                        result.documentation()
                );

                if (undoManager != null && undoManager.executeCommand(command)) {
                    logger.info("Assertion edited successfully");
                    // Reload assertions to update the UI immediately
                    loadAssertions(currentNode);
                    // Notify that changes were made (triggers save)
                    if (changeCallback != null) {
                        changeCallback.run();
                    }
                } else {
                    showAlert("Failed to edit assertion", "Could not edit the assertion", "error");
                }
            });

        } catch (Exception e) {
            logger.error("Error showing edit assertion dialog", e);
            showAlert("Error", "Failed to open assertion dialog: " + e.getMessage(), "error");
        }
    }

    private void deleteSelectedAssertion() {
        AssertionItem selected = assertionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        try {
            // Show confirmation dialog
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete Assertion");
            confirmation.setHeaderText("Are you sure you want to delete this assertion?");
            confirmation.setContentText(selected.testExpression);

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    // Check if current node is a simpleType or element with inline simpleType
                    boolean isSimpleType = isSimpleTypeForAssertions(currentNode);

                    XsdCommand command;

                    if (isSimpleType) {
                        // Use DeleteSimpleTypeAssertionCommand for simpleTypes and elements with inline simpleTypes
                        command = new DeleteSimpleTypeAssertionCommand(
                                domManipulator,
                                selected.assertionNode
                        );
                    } else {
                        // Use DeleteAssertionCommand for complexTypes
                        command = new DeleteAssertionCommand(
                                domManipulator,
                                selected.assertionNode
                        );
                    }

                    if (undoManager != null && undoManager.executeCommand(command)) {
                        logger.info("Assertion deleted successfully");
                        // Reload assertions to update the UI immediately
                        loadAssertions(currentNode);
                        // Notify that changes were made (triggers save)
                        if (changeCallback != null) {
                            changeCallback.run();
                        }
                    } else {
                        showAlert("Failed to delete assertion", "Could not delete the assertion", "error");
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Error deleting assertion", e);
            showAlert("Error", "Failed to delete assertion: " + e.getMessage(), "error");
        }
    }

    private void showNoSelectionState() {
        this.currentNode = null;
        this.domManipulator = null;

        clearFields();
        validationTitleLabel.setText("Validation Rules - No node selected");
    }

    private void clearFields() {
        // Clear properties fields
        nameField.clear();
        typeField.clear();
        minOccursField.clear();
        maxOccursField.clear();
        documentationField.clear();
        annotationField.clear();
        exampleField.clear();
        enumerationListView.getItems().clear();
        newEnumerationField.clear();

        // Clear validation fields
        if (patternField != null) patternField.clear();
        if (testValueField != null) testValueField.clear();
        if (patternResultLabel != null) patternResultLabel.setText("");
        if (patternLibraryComboBox != null) patternLibraryComboBox.setValue(null);

        // Clear enumeration list view only

        if (minInclusiveField != null) minInclusiveField.clear();
        if (maxInclusiveField != null) maxInclusiveField.clear();
        if (minExclusiveField != null) minExclusiveField.clear();
        if (maxExclusiveField != null) maxExclusiveField.clear();

        if (lengthField != null) lengthField.clear();
        if (minLengthField != null) minLengthField.clear();
        if (maxLengthField != null) maxLengthField.clear();

        if (totalDigitsField != null) totalDigitsField.clear();
        if (fractionDigitsField != null) fractionDigitsField.clear();

        if (whitespaceComboBox != null) whitespaceComboBox.setValue(WhitespaceAction.PRESERVE);

        if (customFacetsData != null) customFacetsData.clear();

        // Clear assertions
        if (assertionsData != null) assertionsData.clear();
    }

    private void loadEnumerations() {
        if (currentNode == null || domManipulator == null) {
            enumerationListView.getItems().clear();
            return;
        }

        try {
            Element element = domManipulator.findElementByXPath(currentNode.xpath());
            if (element != null) {
                List<String> enumerations = extractEnumerationsFromElement(element);
                enumerationListView.getItems().clear();
                enumerationListView.getItems().addAll(enumerations);
            }
        } catch (Exception e) {
            logger.error("Error loading enumerations for node: " + currentNode.name(), e);
        }
    }

    private List<String> extractEnumerationsFromElement(Element element) {
        List<String> enumerations = new ArrayList<>();

        // 1. Check for direct restriction within the element
        NodeList restrictionElements = element.getElementsByTagName("xs:restriction");
        for (int i = 0; i < restrictionElements.getLength(); i++) {
            Element restriction = (Element) restrictionElements.item(i);
            enumerations.addAll(extractEnumerationsFromRestriction(restriction));
        }

        // 2. Check for simpleType definitions
        NodeList simpleTypeElements = element.getElementsByTagName("xs:simpleType");
        for (int i = 0; i < simpleTypeElements.getLength(); i++) {
            Element simpleType = (Element) simpleTypeElements.item(i);
            NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
            for (int j = 0; j < restrictions.getLength(); j++) {
                Element restriction = (Element) restrictions.item(j);
                enumerations.addAll(extractEnumerationsFromRestriction(restriction));
            }
        }

        // 3. Check for type references
        String typeAttr = element.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty() && domManipulator != null) {
            enumerations.addAll(extractEnumerationsFromTypeReference(typeAttr));
        }

        return enumerations;
    }

    private List<String> extractEnumerationsFromRestriction(Element restriction) {
        List<String> enumerations = new ArrayList<>();
        NodeList enumerationElements = restriction.getElementsByTagName("xs:enumeration");
        for (int i = 0; i < enumerationElements.getLength(); i++) {
            Element enumElement = (Element) enumerationElements.item(i);
            String value = enumElement.getAttribute("value");
            if (value != null && !value.isEmpty()) {
                enumerations.add(value);
            }
        }
        return enumerations;
    }

    private List<String> extractEnumerationsFromTypeReference(String typeRef) {
        List<String> enumerations = new ArrayList<>();
        try {
            String xpath = "//xs:simpleType[@name='" + typeRef.replace("tns:", "").replace("xs:", "") + "']";
            Element simpleType = domManipulator.findElementByXPath(xpath);
            if (simpleType != null) {
                NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
                for (int i = 0; i < restrictions.getLength(); i++) {
                    Element restriction = (Element) restrictions.item(i);
                    enumerations.addAll(extractEnumerationsFromRestriction(restriction));
                }
            }
        } catch (Exception e) {
            logger.debug("Could not resolve type reference: " + typeRef, e);
        }
        return enumerations;
    }

    private void addEnumerationValue() {
        String value = newEnumerationField.getText().trim();
        if (!value.isEmpty() && !enumerationListView.getItems().contains(value)) {
            enumerationListView.getItems().add(value);
            newEnumerationField.clear();
        }
    }

    private void removeSelectedEnumeration() {
        String selectedItem = enumerationListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            enumerationListView.getItems().remove(selectedItem);
        }
    }

    private String generateExample(XsdNodeInfo node) {
        if (node == null) return "";

        StringBuilder example = new StringBuilder();
        example.append("<").append(node.name()).append(">");

        // Add sample content based on type
        if (node.type() != null) {
            if (node.type().contains("string")) {
                example.append("Sample text");
            } else if (node.type().contains("int") || node.type().contains("decimal")) {
                example.append("123");
            } else if (node.type().contains("boolean")) {
                example.append("true");
            } else if (node.type().contains("date")) {
                example.append("2024-01-01");
            } else {
                example.append("...");
            }
        } else {
            example.append("...");
        }

        example.append("</").append(node.name()).append(">");

        return example.toString();
    }

    private String determineDataType(XsdNodeInfo node) {
        String nodeType = node.type();
        if (nodeType == null || nodeType.isEmpty()) {
            return "unknown";
        }

        if (nodeType.startsWith("xs:") || nodeType.startsWith("xsd:")) {
            return nodeType.substring(nodeType.indexOf(':') + 1).toLowerCase();
        }

        return nodeType.toLowerCase();
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
            patternResultLabel.setText("✗ Invalid Pattern");
            patternResultLabel.setStyle("-fx-text-fill: #dc3545;");
        }
    }

    private void validateGlobalTest(String testValue, Label resultLabel) {
        List<String> errors = new ArrayList<>();
        List<String> success = new ArrayList<>();

        // Test pattern
        if (patternField != null && !patternField.getText().trim().isEmpty()) {
            try {
                boolean matches = Pattern.compile(patternField.getText().trim()).matcher(testValue).matches();
                if (matches) {
                    success.add("Pattern");
                } else {
                    errors.add("Pattern mismatch");
                }
            } catch (PatternSyntaxException e) {
                errors.add("Invalid pattern");
            }
        }

        // Test enumeration
        if (enumerationListView != null && !enumerationListView.getItems().isEmpty()) {
            boolean found = enumerationListView.getItems().contains(testValue);
            if (found) {
                success.add("Enumeration");
            } else {
                errors.add("Not in enumeration");
            }
        }

        // Test length
        if (lengthField != null && !lengthField.getText().trim().isEmpty()) {
            try {
                int expectedLength = Integer.parseInt(lengthField.getText().trim());
                if (testValue.length() == expectedLength) {
                    success.add("Length");
                } else {
                    errors.add("Length mismatch");
                }
            } catch (NumberFormatException e) {
                errors.add("Invalid length constraint");
            }
        }

        // Update result
        if (errors.isEmpty() && !success.isEmpty()) {
            resultLabel.setText("✓ Valid (" + String.join(", ", success) + ")");
            resultLabel.setStyle("-fx-text-fill: #28a745;");
        } else if (!errors.isEmpty()) {
            resultLabel.setText("✗ Invalid: " + String.join("; ", errors));
            resultLabel.setStyle("-fx-text-fill: #dc3545;");
        } else {
            resultLabel.setText("No constraints");
            resultLabel.setStyle("-fx-text-fill: #6c757d;");
        }
    }

    private void loadCurrentConstraints(XsdNodeInfo node) {
        logger.debug("Loading constraints for node: {}", node.name());

        if (domManipulator == null || node == null || node.xpath() == null) {
            logger.debug("Cannot load constraints: missing dom manipulator, node, or xpath");
            clearAllConstraintFields();
            return;
        }

        try {
            // Find the element in the XSD DOM
            Element element = domManipulator.findElementByXPath(node.xpath());
            if (element == null) {
                logger.debug("Element not found for xpath: {}", node.xpath());
                clearAllConstraintFields();
                return;
            }

            // Clear all fields first
            clearAllConstraintFields();

            // Load constraints based on element type
            if (hasInlineSimpleType(element)) {
                loadConstraintsFromInlineSimpleType(element);
            } else if (hasTypeReference(element)) {
                loadConstraintsFromTypeReference(element);
            }

            // Load documentation/annotation
            loadAnnotation(element);

            logger.debug("Successfully loaded constraints for node: {}", node.name());

        } catch (Exception e) {
            logger.error("Error loading constraints for node: " + node.name(), e);
            clearAllConstraintFields();
        }
    }

    /**
     * Clear all constraint fields
     */
    private void clearAllConstraintFields() {
        // Clear pattern fields
        if (patternField != null) patternField.clear();
        if (testValueField != null) testValueField.clear();
        if (patternResultLabel != null) patternResultLabel.setText("");

        // Clear range fields
        if (minInclusiveField != null) minInclusiveField.clear();
        if (maxInclusiveField != null) maxInclusiveField.clear();
        if (minExclusiveField != null) minExclusiveField.clear();
        if (maxExclusiveField != null) maxExclusiveField.clear();

        // Clear length fields
        if (lengthField != null) lengthField.clear();
        if (minLengthField != null) minLengthField.clear();
        if (maxLengthField != null) maxLengthField.clear();

        // Clear decimal fields
        if (totalDigitsField != null) totalDigitsField.clear();
        if (fractionDigitsField != null) fractionDigitsField.clear();

        // Clear whitespace field
        if (whitespaceComboBox != null) whitespaceComboBox.setValue(null);

        // Clear enumeration list
        if (enumerationListView != null) enumerationListView.getItems().clear();

        // Clear annotation fields
        if (annotationField != null) annotationField.clear();

        // Clear custom facets
        if (customFacetsData != null) customFacetsData.clear();
    }

    /**
     * Check if element has inline simpleType definition
     */
    private boolean hasInlineSimpleType(Element element) {
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        return simpleTypes.getLength() > 0;
    }

    /**
     * Check if element has type reference (e.g., type="xs:string")
     */
    private boolean hasTypeReference(Element element) {
        return element.hasAttribute("type") && !element.getAttribute("type").isEmpty();
    }

    /**
     * Load constraints from inline simpleType
     */
    private void loadConstraintsFromInlineSimpleType(Element element) {
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        if (simpleTypes.getLength() > 0) {
            Element simpleType = (Element) simpleTypes.item(0);
            loadConstraintsFromSimpleTypeElement(simpleType);
        }
    }

    /**
     * Load constraints from type reference (looks up global types)
     */
    private void loadConstraintsFromTypeReference(Element element) {
        String typeName = element.getAttribute("type");
        if (typeName == null || typeName.isEmpty()) {
            return;
        }

        // Skip built-in XSD types for now - they don't have user-defined constraints
        if (typeName.startsWith("xs:")) {
            logger.debug("Skipping built-in type: {}", typeName);
            return;
        }

        // Try to find the referenced simpleType or complexType
        Element referencedType = findTypeDefinition(typeName);
        if (referencedType != null) {
            if ("xs:simpleType".equals(referencedType.getTagName())) {
                loadConstraintsFromSimpleTypeElement(referencedType);
            }
            // For complexType, we could load its content model constraints in the future
        }
    }

    /**
     * Find a type definition by name in the schema
     */
    private Element findTypeDefinition(String typeName) {
        if (domManipulator == null || domManipulator.getDocument() == null) {
            return null;
        }

        // Remove namespace prefix if present (e.g., "tns:MyType" -> "MyType")
        String localName = typeName;
        if (typeName.contains(":")) {
            localName = typeName.substring(typeName.indexOf(":") + 1);
        }

        // Search for simpleType with matching name
        NodeList simpleTypes = domManipulator.getDocument().getElementsByTagName("xs:simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (localName.equals(simpleType.getAttribute("name"))) {
                return simpleType;
            }
        }

        // Search for complexType with matching name
        NodeList complexTypes = domManipulator.getDocument().getElementsByTagName("xs:complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (localName.equals(complexType.getAttribute("name"))) {
                return complexType;
            }
        }

        return null;
    }

    /**
     * Load constraints from a simpleType element
     */
    private void loadConstraintsFromSimpleTypeElement(Element simpleType) {
        // Find restriction element
        NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
        if (restrictions.getLength() > 0) {
            Element restriction = (Element) restrictions.item(0);
            loadFacetsFromRestriction(restriction);
        }
    }

    /**
     * Load all facets from a restriction element
     */
    private void loadFacetsFromRestriction(Element restriction) {
        NodeList children = restriction.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element facet) {
                String tagName = facet.getTagName();
                String value = facet.getAttribute("value");

                switch (tagName) {
                    case "xs:pattern" -> loadPatternFacet(value);
                    case "xs:enumeration" -> loadEnumerationFacet(facet);
                    case "xs:length" -> loadLengthFacet(value);
                    case "xs:minLength" -> loadMinLengthFacet(value);
                    case "xs:maxLength" -> loadMaxLengthFacet(value);
                    case "xs:minInclusive" -> loadMinInclusiveFacet(value);
                    case "xs:maxInclusive" -> loadMaxInclusiveFacet(value);
                    case "xs:minExclusive" -> loadMinExclusiveFacet(value);
                    case "xs:maxExclusive" -> loadMaxExclusiveFacet(value);
                    case "xs:totalDigits" -> loadTotalDigitsFacet(value);
                    case "xs:fractionDigits" -> loadFractionDigitsFacet(value);
                    case "xs:whiteSpace" -> loadWhiteSpaceFacet(value);
                    default -> loadCustomFacet(tagName, value);
                }
            }
        }
    }

    private void loadPatternFacet(String value) {
        if (patternField != null && value != null) {
            String currentText = patternField.getText();
            if (currentText.isEmpty()) {
                patternField.setText(value);
            } else {
                patternField.setText(currentText + "\n" + value);
            }
        }
    }

    private void loadEnumerationFacet(Element enumElement) {
        if (enumerationListView != null) {
            String value = enumElement.getAttribute("value");
            if (value != null && !value.isEmpty()) {
                enumerationListView.getItems().add(value);
            }
        }
    }

    private void loadLengthFacet(String value) {
        if (lengthField != null && value != null) {
            lengthField.setText(value);
        }
    }

    private void loadMinLengthFacet(String value) {
        if (minLengthField != null && value != null) {
            minLengthField.setText(value);
        }
    }

    private void loadMaxLengthFacet(String value) {
        if (maxLengthField != null && value != null) {
            maxLengthField.setText(value);
        }
    }

    private void loadMinInclusiveFacet(String value) {
        if (minInclusiveField != null && value != null) {
            minInclusiveField.setText(value);
        }
    }

    private void loadMaxInclusiveFacet(String value) {
        if (maxInclusiveField != null && value != null) {
            maxInclusiveField.setText(value);
        }
    }

    private void loadMinExclusiveFacet(String value) {
        if (minExclusiveField != null && value != null) {
            minExclusiveField.setText(value);
        }
    }

    private void loadMaxExclusiveFacet(String value) {
        if (maxExclusiveField != null && value != null) {
            maxExclusiveField.setText(value);
        }
    }

    private void loadTotalDigitsFacet(String value) {
        if (totalDigitsField != null && value != null) {
            totalDigitsField.setText(value);
        }
    }

    private void loadFractionDigitsFacet(String value) {
        if (fractionDigitsField != null && value != null) {
            fractionDigitsField.setText(value);
        }
    }

    private void loadWhiteSpaceFacet(String value) {
        if (whitespaceComboBox != null && value != null) {
            try {
                WhitespaceAction action = WhitespaceAction.valueOf(value.toUpperCase());
                whitespaceComboBox.setValue(action);
            } catch (IllegalArgumentException e) {
                logger.debug("Unknown whitespace action: {}", value);
            }
        }
    }

    private void loadCustomFacet(String facetName, String value) {
        if (customFacetsData != null && facetName != null && value != null) {
            // Remove "xs:" prefix if present
            String simpleName = facetName.startsWith("xs:") ? facetName.substring(3) : facetName;
            customFacetsData.add(new CustomFacet(simpleName, value, ""));
        }
    }

    /**
     * Load annotation/documentation for the element
     */
    private void loadAnnotation(Element element) {
        if (annotationField == null) {
            return;
        }

        NodeList annotations = element.getElementsByTagName("xs:annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            NodeList docs = annotation.getElementsByTagName("xs:documentation");
            if (docs.getLength() > 0) {
                String documentation = docs.item(0).getTextContent();
                if (documentation != null && !documentation.trim().isEmpty()) {
                    annotationField.setText(documentation.trim());
                }
            }
        }
    }

    private void setupEventHandlers() {
        // Additional event handlers can be added here
        logger.debug("Event handlers configured for XsdControlPane");
    }

    /**
     * Saves all changes made in the control pane back to the XSD DOM
     */
    private void saveChanges() {
        if (currentNode == null || domManipulator == null) {
            showAlert("No Element Selected", "Please select an element before saving changes.", "warning");
            return;
        }

        try {
            String xpath = currentNode.xpath();
            logger.debug("Saving changes for element: {} at xpath: {}", currentNode.name(), xpath);

            // Save basic properties (these are usually read-only, but keeping for completeness)
            // Note: name and type changes are typically handled differently and may require more complex logic

            // Save minOccurs if changed
            String minOccurs = minOccursField.getText().trim();
            if (!minOccurs.isEmpty()) {
                domManipulator.updateElementProperties(xpath, "minOccurs", minOccurs);
            }

            // Save maxOccurs if changed  
            String maxOccurs = maxOccursField.getText().trim();
            if (!maxOccurs.isEmpty()) {
                domManipulator.updateElementProperties(xpath, "maxOccurs", maxOccurs);
            }

            // Save documentation (using updateElementProperties for basic properties only)
            // Note: Documentation saving would require specialized DOM manipulation

            // Save enumeration values
            saveEnumerations(xpath);

            // Save validation rules (when implemented)
            saveValidationRules(xpath);

            logger.info("Saved properties, enumerations, and validation rules for element: {}", currentNode.name());

            // Notify that changes were saved
            showAlert("Changes Saved", "All changes have been successfully saved to the XSD.", "success");

            // Trigger callback to notify parent components of changes
            notifyChanges("Element properties and validation rules updated");

            logger.info("Successfully saved changes for element: {}", currentNode.name());

        } catch (Exception e) {
            logger.error("Error saving changes for element: " + currentNode.name(), e);
            showAlert("Save Error", "Failed to save changes: " + e.getMessage(), "error");
        }
    }

    /**
     * Saves enumeration values for the current element
     */
    private void saveEnumerations(String xpath) {
        if (domManipulator == null) {
            logger.warn("Cannot save enumerations: domManipulator is null");
            showAlert("Save Error", "DOM manipulator not available", "error");
            return;
        }

        try {
            // Get current enumeration values from the ListView
            List<String> enumerations = new ArrayList<>(enumerationListView.getItems());

            // Update enumerations using XsdDomManipulator
            boolean success = domManipulator.updateElementEnumerations(xpath, enumerations);

            if (success) {
                logger.info("Successfully saved {} enumeration values for element at {}", enumerations.size(), xpath);
                showAlert("Success", "Enumeration values saved successfully", "success");

                // Notify change callback if available
                if (changeCallback != null) {
                    changeCallback.run();
                }
            } else {
                logger.error("Failed to save enumeration values for element at {}", xpath);
                showAlert("Save Error", "Failed to save enumeration values", "error");
            }
        } catch (Exception e) {
            logger.error("Error saving enumeration values for element at " + xpath, e);
            showAlert("Save Error", "Error saving enumeration values: " + e.getMessage(), "error");
        }
    }

    /**
     * Saves validation rules/constraints for the current element
     */
    private void saveValidationRules(String xpath) {
        if (domManipulator == null) {
            logger.warn("Cannot save validation rules: domManipulator is null");
            showAlert("Save Error", "DOM manipulator not available", "error");
            return;
        }

        try {
            // Collect all constraint values from UI fields
            java.util.Map<String, String> constraints = new java.util.HashMap<>();

            // Pattern constraint
            if (patternField != null && !patternField.getText().trim().isEmpty()) {
                constraints.put("pattern", patternField.getText().trim());
            }

            // Length constraints
            if (lengthField != null && !lengthField.getText().trim().isEmpty()) {
                constraints.put("length", lengthField.getText().trim());
            }
            if (minLengthField != null && !minLengthField.getText().trim().isEmpty()) {
                constraints.put("minLength", minLengthField.getText().trim());
            }
            if (maxLengthField != null && !maxLengthField.getText().trim().isEmpty()) {
                constraints.put("maxLength", maxLengthField.getText().trim());
            }

            // Range constraints
            if (minInclusiveField != null && !minInclusiveField.getText().trim().isEmpty()) {
                constraints.put("minInclusive", minInclusiveField.getText().trim());
            }
            if (maxInclusiveField != null && !maxInclusiveField.getText().trim().isEmpty()) {
                constraints.put("maxInclusive", maxInclusiveField.getText().trim());
            }
            if (minExclusiveField != null && !minExclusiveField.getText().trim().isEmpty()) {
                constraints.put("minExclusive", minExclusiveField.getText().trim());
            }
            if (maxExclusiveField != null && !maxExclusiveField.getText().trim().isEmpty()) {
                constraints.put("maxExclusive", maxExclusiveField.getText().trim());
            }

            // Decimal constraints
            if (totalDigitsField != null && !totalDigitsField.getText().trim().isEmpty()) {
                constraints.put("totalDigits", totalDigitsField.getText().trim());
            }
            if (fractionDigitsField != null && !fractionDigitsField.getText().trim().isEmpty()) {
                constraints.put("fractionDigits", fractionDigitsField.getText().trim());
            }

            // Whitespace constraint
            if (whitespaceComboBox != null && whitespaceComboBox.getValue() != null) {
                WhitespaceAction action = whitespaceComboBox.getValue();
                if (action != WhitespaceAction.PRESERVE) { // Only add if not default
                    constraints.put("whiteSpace", action.toString().toLowerCase());
                }
            }

            // Update constraints using XsdDomManipulator
            boolean success = domManipulator.updateElementConstraints(xpath, constraints);

            if (success) {
                logger.info("Successfully saved {} validation rules for element at {}", constraints.size(), xpath);
                showAlert("Success", "Validation rules saved successfully", "success");

                // Notify change callback if available
                if (changeCallback != null) {
                    changeCallback.run();
                }
            } else {
                logger.error("Failed to save validation rules for element at {}", xpath);
                showAlert("Save Error", "Failed to save validation rules", "error");
            }
        } catch (Exception e) {
            logger.error("Error saving validation rules for element at " + xpath, e);
            showAlert("Save Error", "Error saving validation rules: " + e.getMessage(), "error");
        }
    }

    /**
     * Shows an alert dialog to the user
     */
    private void showAlert(String title, String message, String type) {
        Alert.AlertType alertType;
        switch (type.toLowerCase()) {
            case "success" -> alertType = Alert.AlertType.INFORMATION;
            case "warning" -> alertType = Alert.AlertType.WARNING;
            case "error" -> alertType = Alert.AlertType.ERROR;
            default -> alertType = Alert.AlertType.INFORMATION;
        }

        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Sets the callback for property changes
     */
    public void setOnPropertyChanged(java.util.function.Consumer<String> callback) {
        this.onPropertyChangedCallback = callback;
    }

    /**
     * Notifies parent components of changes (similar to the original XsdPropertyPanel callback)
     */
    private void notifyChanges(String message) {
        logger.info("Changes notification: {}", message);

        if (onPropertyChangedCallback != null) {
            onPropertyChangedCallback.accept(message);
        }
    }

    /**
     * Shows the XSD Panel help dialog
     */
    private void showHelp() {
        try {
            XsdHelpDialog helpDialog = new XsdHelpDialog();
            helpDialog.show();
            logger.debug("XSD Help dialog displayed");
        } catch (Exception e) {
            logger.error("Error showing help dialog", e);
            showAlert("Help Error", "Failed to open help dialog: " + e.getMessage(), "error");
        }
    }

    // Supporting classes from XsdValidationPanel
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

    /**
     * Sets the change callback to be notified when properties are modified
     *
     * @param changeCallback The callback to run when changes occur
     */
    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    /**
     * Gets the current XSD content as a string from the DOM manipulator.
     * This can be used to save the current state to a file.
     *
     * @return The XSD content as a string, or null if no DOM manipulator is set
     */
    public String getCurrentXsdContent() {
        if (domManipulator == null) {
            return null;
        }
        try {
            return domManipulator.getXsdAsString();
        } catch (Exception e) {
            logger.error("Error getting XSD content from DOM manipulator", e);
            return null;
        }
    }

    /**
     * Sets the undo manager for assertion operations
     *
     * @param undoManager The undo manager to use for commands
     */
    public void setUndoManager(XsdUndoManager undoManager) {
        this.undoManager = undoManager;
    }

    /**
         * Helper class to represent an assertion item in the ListView
         */
        private record AssertionItem(XsdNodeInfo assertionNode, String testExpression, String xpathDefaultNamespace,
                                     String documentation) {
            private AssertionItem(XsdNodeInfo assertionNode, String testExpression,
                                  String xpathDefaultNamespace, String documentation) {
                this.assertionNode = assertionNode;
                this.testExpression = testExpression != null ? testExpression : "";
                this.xpathDefaultNamespace = xpathDefaultNamespace;
                this.documentation = documentation;
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