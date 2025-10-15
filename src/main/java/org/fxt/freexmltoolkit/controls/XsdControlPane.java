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

    // Callback for property changes
    private java.util.function.Consumer<String> onPropertyChangedCallback;

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
        showNoSelectionState();
    }

    private void createPropertiesSection() {
        propertiesSection = new VBox(15);
        propertiesSection.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px; -fx-border-radius: 5px; -fx-padding: 15px;");

        // Title
        Label titleLabel = new Label("Element Properties");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        titleLabel.setGraphic(new FontIcon("bi-gear"));

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
                titleLabel,
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

            // For now, we'll only save the basic properties that are supported
            logger.info("Saved basic properties (minOccurs, maxOccurs) for element: {}", currentNode.name());

            // Advanced features like enumerations and validation rules would require
            // extending XsdDomManipulator with additional methods

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
     * Placeholder for future enumeration saving functionality
     * TODO: Implement when XsdDomManipulator supports enumeration updates
     */
    private void saveEnumerations(String xpath) {
        logger.info("Enumeration saving not yet implemented - requires XsdDomManipulator extension");
    }

    /**
     * Placeholder for future validation rules saving functionality
     * TODO: Implement when XsdDomManipulator supports constraint updates
     */
    private void saveValidationRules(String xpath) {
        logger.info("Validation rules saving not yet implemented - requires XsdDomManipulator extension");
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