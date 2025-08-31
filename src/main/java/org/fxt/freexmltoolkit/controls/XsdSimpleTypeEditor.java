package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Professional SimpleType Editor Dialog for XSD Schema
 * Supports restrictions, patterns, enumerations, and all XSD facets
 */
public class XsdSimpleTypeEditor extends Dialog<SimpleTypeResult> {

    private static final Logger logger = LogManager.getLogger(XsdSimpleTypeEditor.class);

    // UI Components
    private TextField nameField;
    private ComboBox<String> baseTypeCombo;
    private TabPane facetsTabPane;

    // Pattern/Regex Tab
    private TextArea patternArea;
    private Label patternStatusLabel;
    private TextField patternTestField;
    private Label patternTestResult;

    // Enumeration Tab
    private TableView<EnumerationItem> enumerationTable;
    private ObservableList<EnumerationItem> enumerationItems;

    // Length Restrictions Tab
    private TextField lengthField;
    private TextField minLengthField;
    private TextField maxLengthField;

    // Numeric Restrictions Tab
    private TextField minInclusiveField;
    private TextField maxInclusiveField;
    private TextField minExclusiveField;
    private TextField maxExclusiveField;
    private TextField totalDigitsField;
    private TextField fractionDigitsField;

    // String Processing Tab
    private ComboBox<String> whiteSpaceCombo;

    // Documentation
    private TextArea documentationArea;

    // Live Preview
    private TextArea previewArea;
    private ListView<String> testDataList;
    private Label validationResultLabel;

    // Mode
    private final boolean isEditMode;
    private final Element existingSimpleType;
    private final Document xsdDocument;

    /**
     * Default constructor for creating new SimpleType
     */
    public XsdSimpleTypeEditor() {
        this(null, null);
    }

    /**
     * Constructor for creating new SimpleType
     */
    public XsdSimpleTypeEditor(Document xsdDocument) {
        this(xsdDocument, null);
    }

    /**
     * Constructor for editing existing SimpleType
     */
    public XsdSimpleTypeEditor(Document xsdDocument, Element existingSimpleType) {
        this.xsdDocument = xsdDocument;
        this.existingSimpleType = existingSimpleType;
        this.isEditMode = existingSimpleType != null;

        setTitle(isEditMode ? "Edit SimpleType" : "Create SimpleType");
        setHeaderText(isEditMode ?
                "Edit the simple type definition" :
                "Define a new simple type with restrictions");
        setResizable(true);

        initializeDialog();

        if (isEditMode) {
            loadExistingSimpleType();
        }

        setupValidation();
        updatePreview();
    }

    /**
     * Initialize the dialog UI
     */
    private void initializeDialog() {
        // Main layout
        BorderPane mainPane = new BorderPane();
        mainPane.setPrefSize(900, 700);

        // Top section - Name and Base Type
        VBox topSection = createTopSection();
        mainPane.setTop(topSection);

        // Center section - Split between facets and preview
        SplitPane centerSplit = new SplitPane();
        centerSplit.setDividerPositions(0.6);

        // Left side - Facets
        VBox facetsSection = createFacetsSection();

        // Right side - Preview and Testing
        VBox previewSection = createPreviewSection();

        centerSplit.getItems().addAll(facetsSection, previewSection);
        mainPane.setCenter(centerSplit);

        // Bottom section - Documentation
        VBox bottomSection = createDocumentationSection();
        mainPane.setBottom(bottomSection);

        getDialogPane().setContent(mainPane);

        // Buttons
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-type-selector.css").toExternalForm()
        );

        // Result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return createResult();
            }
            return null;
        });
    }

    /**
     * Create top section with name and base type
     */
    private VBox createTopSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 0 0 1px 0;");

        // Name field
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("Type Name:");
        nameLabel.setMinWidth(100);
        nameLabel.setStyle("-fx-font-weight: bold;");

        nameField = new TextField();
        nameField.setPromptText("Enter simple type name (e.g., 'EmailType')");
        nameField.setPrefWidth(300);
        nameField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        HBox.setHgrow(nameField, Priority.ALWAYS);

        nameBox.getChildren().addAll(nameLabel, nameField);

        // Base type selection
        HBox baseTypeBox = new HBox(10);
        baseTypeBox.setAlignment(Pos.CENTER_LEFT);

        Label baseTypeLabel = new Label("Base Type:");
        baseTypeLabel.setMinWidth(100);
        baseTypeLabel.setStyle("-fx-font-weight: bold;");

        baseTypeCombo = new ComboBox<>();
        baseTypeCombo.getItems().addAll(
                "xs:string", "xs:boolean", "xs:decimal", "xs:float", "xs:double",
                "xs:duration", "xs:dateTime", "xs:time", "xs:date", "xs:gYearMonth",
                "xs:gYear", "xs:gMonthDay", "xs:gDay", "xs:gMonth", "xs:hexBinary",
                "xs:base64Binary", "xs:anyURI", "xs:QName", "xs:NOTATION",
                "xs:normalizedString", "xs:token", "xs:language", "xs:NMTOKEN",
                "xs:NMTOKENS", "xs:Name", "xs:NCName", "xs:ID", "xs:IDREF",
                "xs:IDREFS", "xs:ENTITY", "xs:ENTITIES", "xs:integer",
                "xs:nonPositiveInteger", "xs:negativeInteger", "xs:long", "xs:int",
                "xs:short", "xs:byte", "xs:nonNegativeInteger", "xs:unsignedLong",
                "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte", "xs:positiveInteger"
        );
        baseTypeCombo.setValue("xs:string");
        baseTypeCombo.setPrefWidth(300);
        baseTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());

        Button typeInfoButton = new Button();
        typeInfoButton.setGraphic(new FontIcon("bi-info-circle"));
        typeInfoButton.setTooltip(new Tooltip("Information about the selected base type"));
        typeInfoButton.setOnAction(e -> showTypeInfo());

        baseTypeBox.getChildren().addAll(baseTypeLabel, baseTypeCombo, typeInfoButton);

        section.getChildren().addAll(nameBox, baseTypeBox);

        return section;
    }

    /**
     * Create facets section with tabs for different restriction types
     */
    private VBox createFacetsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));

        Label sectionLabel = new Label("Restriction Facets");
        sectionLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        facetsTabPane = new TabPane();
        facetsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Pattern Tab
        Tab patternTab = new Tab("Pattern");
        patternTab.setGraphic(new FontIcon("bi-asterisk"));
        patternTab.setContent(createPatternTab());

        // Enumeration Tab
        Tab enumerationTab = new Tab("Enumeration");
        enumerationTab.setGraphic(new FontIcon("bi-list-ul"));
        enumerationTab.setContent(createEnumerationTab());

        // Length Tab
        Tab lengthTab = new Tab("Length");
        lengthTab.setGraphic(new FontIcon("bi-rulers"));
        lengthTab.setContent(createLengthTab());

        // Numeric Tab
        Tab numericTab = new Tab("Numeric");
        numericTab.setGraphic(new FontIcon("bi-hash"));
        numericTab.setContent(createNumericTab());

        // String Tab
        Tab stringTab = new Tab("String");
        stringTab.setGraphic(new FontIcon("bi-fonts"));
        stringTab.setContent(createStringTab());

        facetsTabPane.getTabs().addAll(patternTab, enumerationTab, lengthTab, numericTab, stringTab);

        section.getChildren().addAll(sectionLabel, facetsTabPane);
        VBox.setVgrow(facetsTabPane, Priority.ALWAYS);

        return section;
    }

    /**
     * Create pattern restriction tab
     */
    private Node createPatternTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label infoLabel = new Label("Define a regular expression pattern:");

        patternArea = new TextArea();
        patternArea.setPromptText("Enter regular expression pattern (e.g., [A-Z][a-z]+)");
        patternArea.setPrefRowCount(3);
        patternArea.setWrapText(true);

        patternStatusLabel = new Label();
        patternStatusLabel.setGraphic(new FontIcon("bi-check-circle"));

        Separator separator = new Separator();

        Label testLabel = new Label("Test Pattern:");
        testLabel.setStyle("-fx-font-weight: bold;");

        HBox testBox = new HBox(10);
        testBox.setAlignment(Pos.CENTER_LEFT);

        patternTestField = new TextField();
        patternTestField.setPromptText("Enter test value");
        patternTestField.setPrefWidth(200);
        HBox.setHgrow(patternTestField, Priority.ALWAYS);

        Button testButton = new Button("Test");
        testButton.setGraphic(new FontIcon("bi-play-circle"));
        testButton.setOnAction(e -> testPattern());

        testBox.getChildren().addAll(patternTestField, testButton);

        patternTestResult = new Label();

        // Pattern library
        Label libraryLabel = new Label("Common Patterns:");
        libraryLabel.setStyle("-fx-font-weight: bold;");

        ComboBox<String> patternLibrary = new ComboBox<>();
        patternLibrary.getItems().addAll(
                "Email: [a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                "Phone: \\+?[1-9]\\d{1,14}",
                "URL: https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=]+",
                "IP Address: \\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}",
                "Date (YYYY-MM-DD): \\d{4}-\\d{2}-\\d{2}",
                "Time (HH:MM): \\d{2}:\\d{2}",
                "Postal Code (US): \\d{5}(-\\d{4})?",
                "Hex Color: #[0-9A-Fa-f]{6}",
                "UUID: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
        patternLibrary.setPromptText("Select a common pattern...");
        patternLibrary.setOnAction(e -> {
            String selected = patternLibrary.getValue();
            if (selected != null && selected.contains(": ")) {
                String pattern = selected.substring(selected.indexOf(": ") + 2);
                patternArea.setText(pattern);
            }
        });

        content.getChildren().addAll(
                infoLabel, patternArea, patternStatusLabel,
                separator, testLabel, testBox, patternTestResult,
                new Separator(), libraryLabel, patternLibrary
        );

        // Add listener for pattern validation
        patternArea.textProperty().addListener((obs, old, text) -> validatePattern());

        return content;
    }

    /**
     * Create enumeration restriction tab
     */
    private Node createEnumerationTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label infoLabel = new Label("Define allowed values:");

        // Table for enumeration values
        enumerationTable = new TableView<>();
        enumerationTable.setEditable(true);
        enumerationTable.setPrefHeight(200);

        TableColumn<EnumerationItem, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(event -> {
            event.getRowValue().setValue(event.getNewValue());
            updatePreview();
        });
        valueColumn.setPrefWidth(200);

        TableColumn<EnumerationItem, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
        descriptionColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        descriptionColumn.setOnEditCommit(event -> {
            event.getRowValue().setDescription(event.getNewValue());
        });
        descriptionColumn.setPrefWidth(300);

        enumerationTable.getColumns().addAll(valueColumn, descriptionColumn);

        enumerationItems = FXCollections.observableArrayList();
        enumerationTable.setItems(enumerationItems);

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);

        Button addButton = new Button("Add Value");
        addButton.setGraphic(new FontIcon("bi-plus-circle"));
        addButton.setOnAction(e -> {
            EnumerationItem newItem = new EnumerationItem("", "");
            enumerationItems.add(newItem);
            enumerationTable.getSelectionModel().select(newItem);
            enumerationTable.scrollTo(newItem);
        });

        Button removeButton = new Button("Remove");
        removeButton.setGraphic(new FontIcon("bi-dash-circle"));
        removeButton.setOnAction(e -> {
            EnumerationItem selected = enumerationTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                enumerationItems.remove(selected);
                updatePreview();
            }
        });

        Button sortButton = new Button("Sort");
        sortButton.setGraphic(new FontIcon("bi-sort-alpha-down"));
        sortButton.setOnAction(e -> {
            enumerationItems.sort(Comparator.comparing(EnumerationItem::getValue));
        });

        buttonBox.getChildren().addAll(addButton, removeButton, sortButton);

        content.getChildren().addAll(infoLabel, enumerationTable, buttonBox);

        return content;
    }

    /**
     * Create length restriction tab
     */
    private Node createLengthTab() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        Label infoLabel = new Label("Set length constraints:");
        infoLabel.setStyle("-fx-font-weight: bold;");
        GridPane.setColumnSpan(infoLabel, 2);

        // Length field
        Label lengthLabel = new Label("Exact Length:");
        lengthField = new TextField();
        lengthField.setPromptText("Exact number of characters");
        lengthField.textProperty().addListener((obs, old, text) -> {
            if (!text.isEmpty()) {
                minLengthField.setDisable(true);
                maxLengthField.setDisable(true);
            } else {
                minLengthField.setDisable(false);
                maxLengthField.setDisable(false);
            }
            updatePreview();
        });

        // Min/Max length fields
        Label minLengthLabel = new Label("Minimum Length:");
        minLengthField = new TextField();
        minLengthField.setPromptText("Minimum characters");
        minLengthField.textProperty().addListener((obs, old, text) -> updatePreview());

        Label maxLengthLabel = new Label("Maximum Length:");
        maxLengthField = new TextField();
        maxLengthField.setPromptText("Maximum characters");
        maxLengthField.textProperty().addListener((obs, old, text) -> updatePreview());

        grid.add(infoLabel, 0, 0);
        grid.add(lengthLabel, 0, 1);
        grid.add(lengthField, 1, 1);
        grid.add(new Separator(), 0, 2, 2, 1);
        grid.add(minLengthLabel, 0, 3);
        grid.add(minLengthField, 1, 3);
        grid.add(maxLengthLabel, 0, 4);
        grid.add(maxLengthField, 1, 4);

        // Info text
        Label noteLabel = new Label("Note: Use either exact length OR min/max combination");
        noteLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #6c757d;");
        grid.add(noteLabel, 0, 5, 2, 1);

        return grid;
    }

    /**
     * Create numeric restriction tab
     */
    private Node createNumericTab() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        Label infoLabel = new Label("Set numeric constraints:");
        infoLabel.setStyle("-fx-font-weight: bold;");
        GridPane.setColumnSpan(infoLabel, 2);

        // Inclusive bounds
        Label minIncLabel = new Label("Min Inclusive:");
        minInclusiveField = new TextField();
        minInclusiveField.setPromptText("Minimum value (inclusive)");
        minInclusiveField.textProperty().addListener((obs, old, text) -> updatePreview());

        Label maxIncLabel = new Label("Max Inclusive:");
        maxInclusiveField = new TextField();
        maxInclusiveField.setPromptText("Maximum value (inclusive)");
        maxInclusiveField.textProperty().addListener((obs, old, text) -> updatePreview());

        // Exclusive bounds
        Label minExcLabel = new Label("Min Exclusive:");
        minExclusiveField = new TextField();
        minExclusiveField.setPromptText("Minimum value (exclusive)");
        minExclusiveField.textProperty().addListener((obs, old, text) -> updatePreview());

        Label maxExcLabel = new Label("Max Exclusive:");
        maxExclusiveField = new TextField();
        maxExclusiveField.setPromptText("Maximum value (exclusive)");
        maxExclusiveField.textProperty().addListener((obs, old, text) -> updatePreview());

        // Digits
        Label totalDigitsLabel = new Label("Total Digits:");
        totalDigitsField = new TextField();
        totalDigitsField.setPromptText("Maximum total digits");
        totalDigitsField.textProperty().addListener((obs, old, text) -> updatePreview());

        Label fractionDigitsLabel = new Label("Fraction Digits:");
        fractionDigitsField = new TextField();
        fractionDigitsField.setPromptText("Maximum decimal places");
        fractionDigitsField.textProperty().addListener((obs, old, text) -> updatePreview());

        grid.add(infoLabel, 0, 0);
        grid.add(minIncLabel, 0, 1);
        grid.add(minInclusiveField, 1, 1);
        grid.add(maxIncLabel, 0, 2);
        grid.add(maxInclusiveField, 1, 2);
        grid.add(new Separator(), 0, 3, 2, 1);
        grid.add(minExcLabel, 0, 4);
        grid.add(minExclusiveField, 1, 4);
        grid.add(maxExcLabel, 0, 5);
        grid.add(maxExclusiveField, 1, 5);
        grid.add(new Separator(), 0, 6, 2, 1);
        grid.add(totalDigitsLabel, 0, 7);
        grid.add(totalDigitsField, 1, 7);
        grid.add(fractionDigitsLabel, 0, 8);
        grid.add(fractionDigitsField, 1, 8);

        return grid;
    }

    /**
     * Create string processing tab
     */
    private Node createStringTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label infoLabel = new Label("String processing options:");
        infoLabel.setStyle("-fx-font-weight: bold;");

        // WhiteSpace handling
        HBox whiteSpaceBox = new HBox(10);
        whiteSpaceBox.setAlignment(Pos.CENTER_LEFT);

        Label whiteSpaceLabel = new Label("White Space:");
        whiteSpaceLabel.setMinWidth(100);

        whiteSpaceCombo = new ComboBox<>();
        whiteSpaceCombo.getItems().addAll("preserve", "replace", "collapse");
        whiteSpaceCombo.setValue("preserve");
        whiteSpaceCombo.setPrefWidth(200);

        Label whiteSpaceInfo = new Label();
        whiteSpaceInfo.setGraphic(new FontIcon("bi-info-circle"));
        whiteSpaceInfo.setTooltip(new Tooltip(
                "preserve: All whitespace is preserved\n" +
                        "replace: Tabs, newlines, and carriage returns are replaced with spaces\n" +
                        "collapse: Sequences of whitespace are collapsed to a single space"
        ));

        whiteSpaceBox.getChildren().addAll(whiteSpaceLabel, whiteSpaceCombo, whiteSpaceInfo);

        // Explanation
        TextArea explanationArea = new TextArea();
        explanationArea.setEditable(false);
        explanationArea.setWrapText(true);
        explanationArea.setPrefRowCount(5);
        explanationArea.setStyle("-fx-control-inner-background: #f8f9fa;");

        whiteSpaceCombo.setOnAction(e -> {
            String selected = whiteSpaceCombo.getValue();
            String explanation = switch (selected) {
                case "preserve" -> "All whitespace characters are preserved exactly as entered.\n" +
                        "This includes spaces, tabs, newlines, and carriage returns.";
                case "replace" -> "All occurrences of #x9 (tab), #xA (line feed) and #xD (carriage return) " +
                        "are replaced with #x20 (space).";
                case "collapse" -> "After the processing defined for 'replace', sequences of #x20 (space) " +
                        "are collapsed to a single space, and leading and trailing spaces are removed.";
                default -> "";
            };
            explanationArea.setText(explanation);
            updatePreview();
        });

        whiteSpaceCombo.fireEvent(new javafx.event.ActionEvent());

        content.getChildren().addAll(infoLabel, whiteSpaceBox, explanationArea);

        return content;
    }

    /**
     * Create preview section
     */
    private VBox createPreviewSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));

        Label sectionLabel = new Label("Live Preview & Testing");
        sectionLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        // XSD Preview
        Label previewLabel = new Label("Generated XSD:");
        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(10);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                "-fx-font-size: 11px; -fx-control-inner-background: #f8f9fa;");

        // Test Data
        Label testDataLabel = new Label("Test Values:");
        testDataList = new ListView<>();
        testDataList.setPrefHeight(100);
        testDataList.setEditable(true);

        HBox testButtonBox = new HBox(10);

        Button addTestButton = new Button("Add");
        addTestButton.setGraphic(new FontIcon("bi-plus"));
        addTestButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add Test Value");
            dialog.setHeaderText("Enter a test value:");
            dialog.showAndWait().ifPresent(value -> {
                testDataList.getItems().add(value);
                validateTestData();
            });
        });

        Button removeTestButton = new Button("Remove");
        removeTestButton.setGraphic(new FontIcon("bi-dash"));
        removeTestButton.setOnAction(e -> {
            String selected = testDataList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                testDataList.getItems().remove(selected);
                validateTestData();
            }
        });

        Button validateButton = new Button("Validate All");
        validateButton.setGraphic(new FontIcon("bi-check-all"));
        validateButton.setOnAction(e -> validateTestData());

        testButtonBox.getChildren().addAll(addTestButton, removeTestButton, validateButton);

        // Validation Result
        validationResultLabel = new Label("Add test values to validate");
        validationResultLabel.setStyle("-fx-padding: 5px; -fx-background-color: #e9ecef; " +
                "-fx-background-radius: 3px;");

        section.getChildren().addAll(
                sectionLabel, previewLabel, previewArea,
                new Separator(),
                testDataLabel, testDataList, testButtonBox, validationResultLabel
        );

        VBox.setVgrow(previewArea, Priority.ALWAYS);

        return section;
    }

    /**
     * Create documentation section
     */
    private VBox createDocumentationSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 1px 0 0 0;");

        Label docLabel = new Label("Documentation:");
        docLabel.setStyle("-fx-font-weight: bold;");

        documentationArea = new TextArea();
        documentationArea.setPromptText("Add documentation for this simple type (optional)");
        documentationArea.setPrefRowCount(3);
        documentationArea.setWrapText(true);

        section.getChildren().addAll(docLabel, documentationArea);

        return section;
    }

    /**
     * Test the pattern with the test input
     */
    private void testPattern() {
        String pattern = patternArea.getText();
        String testValue = patternTestField.getText();

        if (pattern.isEmpty() || testValue.isEmpty()) {
            patternTestResult.setText("Enter both pattern and test value");
            patternTestResult.setTextFill(Color.GRAY);
            return;
        }

        try {
            Pattern p = Pattern.compile(pattern);
            boolean matches = p.matcher(testValue).matches();

            if (matches) {
                patternTestResult.setText("✓ Match successful");
                patternTestResult.setTextFill(Color.GREEN);
            } else {
                patternTestResult.setText("✗ No match");
                patternTestResult.setTextFill(Color.RED);
            }
        } catch (PatternSyntaxException e) {
            patternTestResult.setText("✗ Invalid pattern");
            patternTestResult.setTextFill(Color.RED);
        }
    }

    /**
     * Validate the pattern syntax
     */
    private void validatePattern() {
        String pattern = patternArea.getText();

        if (pattern.isEmpty()) {
            patternStatusLabel.setText("");
            return;
        }

        try {
            Pattern.compile(pattern);
            patternStatusLabel.setText("✓ Valid pattern");
            patternStatusLabel.setTextFill(Color.GREEN);
        } catch (PatternSyntaxException e) {
            patternStatusLabel.setText("✗ Invalid: " + e.getMessage());
            patternStatusLabel.setTextFill(Color.RED);
        }

        updatePreview();
    }

    /**
     * Update the XSD preview
     */
    private void updatePreview() {
        StringBuilder xsd = new StringBuilder();
        String typeName = nameField.getText().trim();
        if (typeName.isEmpty()) typeName = "MySimpleType";

        xsd.append("<xs:simpleType name=\"").append(typeName).append("\">\n");

        // Add documentation if present
        String doc = (documentationArea != null) ? documentationArea.getText().trim() : "";
        if (!doc.isEmpty()) {
            xsd.append("  <xs:annotation>\n");
            xsd.append("    <xs:documentation>").append(doc).append("</xs:documentation>\n");
            xsd.append("  </xs:annotation>\n");
        }

        xsd.append("  <xs:restriction base=\"").append(baseTypeCombo.getValue()).append("\">\n");

        // Add pattern if present
        String pattern = patternArea.getText().trim();
        if (!pattern.isEmpty()) {
            xsd.append("    <xs:pattern value=\"").append(escapeXml(pattern)).append("\"/>\n");
        }

        // Add enumerations
        for (EnumerationItem item : enumerationItems) {
            if (!item.getValue().isEmpty()) {
                xsd.append("    <xs:enumeration value=\"").append(escapeXml(item.getValue())).append("\"");
                if (!item.getDescription().isEmpty()) {
                    xsd.append(">\n");
                    xsd.append("      <xs:annotation>\n");
                    xsd.append("        <xs:documentation>").append(escapeXml(item.getDescription())).append("</xs:documentation>\n");
                    xsd.append("      </xs:annotation>\n");
                    xsd.append("    </xs:enumeration>\n");
                } else {
                    xsd.append("/>\n");
                }
            }
        }

        // Add length restrictions
        addFacetIfNotEmpty(xsd, "length", lengthField.getText());
        addFacetIfNotEmpty(xsd, "minLength", minLengthField.getText());
        addFacetIfNotEmpty(xsd, "maxLength", maxLengthField.getText());

        // Add numeric restrictions
        addFacetIfNotEmpty(xsd, "minInclusive", minInclusiveField.getText());
        addFacetIfNotEmpty(xsd, "maxInclusive", maxInclusiveField.getText());
        addFacetIfNotEmpty(xsd, "minExclusive", minExclusiveField.getText());
        addFacetIfNotEmpty(xsd, "maxExclusive", maxExclusiveField.getText());
        addFacetIfNotEmpty(xsd, "totalDigits", totalDigitsField.getText());
        addFacetIfNotEmpty(xsd, "fractionDigits", fractionDigitsField.getText());

        // Add whiteSpace if not default
        if (!"preserve".equals(whiteSpaceCombo.getValue())) {
            xsd.append("    <xs:whiteSpace value=\"").append(whiteSpaceCombo.getValue()).append("\"/>\n");
        }

        xsd.append("  </xs:restriction>\n");
        xsd.append("</xs:simpleType>");

        if (previewArea != null) {
            previewArea.setText(xsd.toString());
        }
    }

    /**
     * Add a facet to the XSD if the value is not empty
     */
    private void addFacetIfNotEmpty(StringBuilder xsd, String facetName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            xsd.append("    <xs:").append(facetName).append(" value=\"")
                    .append(escapeXml(value.trim())).append("\"/>\n");
        }
    }

    /**
     * Escape XML special characters
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Validate test data against the current restrictions
     */
    private void validateTestData() {
        if (testDataList.getItems().isEmpty()) {
            validationResultLabel.setText("No test data to validate");
            validationResultLabel.setStyle("-fx-padding: 5px; -fx-background-color: #e9ecef; " +
                    "-fx-background-radius: 3px;");
            return;
        }

        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        for (String testValue : testDataList.getItems()) {
            if (validateSingleValue(testValue)) {
                passed++;
            } else {
                failed++;
                failures.add(testValue);
            }
        }

        if (failed == 0) {
            validationResultLabel.setText("✓ All " + passed + " test values are valid");
            validationResultLabel.setStyle("-fx-padding: 5px; -fx-background-color: #d4edda; " +
                    "-fx-text-fill: #155724; -fx-background-radius: 3px;");
        } else {
            validationResultLabel.setText("✗ " + failed + " of " + (passed + failed) +
                    " values failed: " + String.join(", ", failures));
            validationResultLabel.setStyle("-fx-padding: 5px; -fx-background-color: #f8d7da; " +
                    "-fx-text-fill: #721c24; -fx-background-radius: 3px;");
        }
    }

    /**
     * Validate a single test value
     */
    private boolean validateSingleValue(String value) {
        // Check pattern
        String pattern = patternArea.getText().trim();
        if (!pattern.isEmpty()) {
            try {
                Pattern p = Pattern.compile(pattern);
                if (!p.matcher(value).matches()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                // Invalid pattern
            }
        }

        // Check enumeration
        if (!enumerationItems.isEmpty()) {
            boolean found = enumerationItems.stream()
                    .anyMatch(item -> item.getValue().equals(value));
            if (!found) {
                return false;
            }
        }

        // Check length
        String lengthStr = lengthField.getText().trim();
        if (!lengthStr.isEmpty()) {
            try {
                int length = Integer.parseInt(lengthStr);
                if (value.length() != length) {
                    return false;
                }
            } catch (NumberFormatException e) {
                // Invalid length
            }
        }

        // Check min/max length
        String minLengthStr = minLengthField.getText().trim();
        if (!minLengthStr.isEmpty()) {
            try {
                int minLength = Integer.parseInt(minLengthStr);
                if (value.length() < minLength) {
                    return false;
                }
            } catch (NumberFormatException e) {
                // Invalid min length
            }
        }

        String maxLengthStr = maxLengthField.getText().trim();
        if (!maxLengthStr.isEmpty()) {
            try {
                int maxLength = Integer.parseInt(maxLengthStr);
                if (value.length() > maxLength) {
                    return false;
                }
            } catch (NumberFormatException e) {
                // Invalid max length
            }
        }

        // For numeric types, check numeric restrictions
        String baseType = baseTypeCombo.getValue();
        if (isNumericType(baseType)) {
            return validateNumericValue(value);
        }

        return true;
    }

    /**
     * Check if the base type is numeric
     */
    private boolean isNumericType(String baseType) {
        return baseType != null && (
                baseType.contains("integer") || baseType.contains("decimal") ||
                        baseType.contains("float") || baseType.contains("double") ||
                        baseType.contains("long") || baseType.contains("int") ||
                        baseType.contains("short") || baseType.contains("byte") ||
                        baseType.contains("Integer") || baseType.contains("Byte")
        );
    }

    /**
     * Validate numeric value against numeric restrictions
     */
    private boolean validateNumericValue(String value) {
        try {
            double numValue = Double.parseDouble(value);

            // Check min inclusive
            String minInc = minInclusiveField.getText().trim();
            if (!minInc.isEmpty()) {
                double min = Double.parseDouble(minInc);
                if (numValue < min) return false;
            }

            // Check max inclusive
            String maxInc = maxInclusiveField.getText().trim();
            if (!maxInc.isEmpty()) {
                double max = Double.parseDouble(maxInc);
                if (numValue > max) return false;
            }

            // Check min exclusive
            String minExc = minExclusiveField.getText().trim();
            if (!minExc.isEmpty()) {
                double min = Double.parseDouble(minExc);
                if (numValue <= min) return false;
            }

            // Check max exclusive
            String maxExc = maxExclusiveField.getText().trim();
            if (!maxExc.isEmpty()) {
                double max = Double.parseDouble(maxExc);
                return !(numValue >= max);
            }

            return true;

        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Setup validation for the dialog
     */
    private void setupValidation() {
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);

        // Disable OK button if name is empty
        okButton.setDisable(true);

        nameField.textProperty().addListener((obs, old, text) -> {
            okButton.setDisable(text == null || text.trim().isEmpty());
        });
    }

    /**
     * Load existing simple type for editing
     */
    private void loadExistingSimpleType() {
        if (existingSimpleType == null) return;

        // Load name
        String name = existingSimpleType.getAttribute("name");
        nameField.setText(name);

        // Find restriction element
        NodeList restrictions = existingSimpleType.getElementsByTagName("xs:restriction");
        if (restrictions.getLength() > 0) {
            Element restriction = (Element) restrictions.item(0);

            // Load base type
            String base = restriction.getAttribute("base");
            if (base != null && !base.isEmpty()) {
                baseTypeCombo.setValue(base);
            }

            // Load facets
            loadFacets(restriction);
        }

        // Load documentation
        loadDocumentation(existingSimpleType);
    }

    /**
     * Load facets from restriction element
     */
    private void loadFacets(Element restriction) {
        NodeList children = restriction.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element facet) {
                String tagName = facet.getTagName();
                String value = facet.getAttribute("value");

                switch (tagName) {
                    case "xs:pattern" -> patternArea.setText(value);
                    case "xs:enumeration" -> {
                        String desc = getDocumentation(facet);
                        enumerationItems.add(new EnumerationItem(value, desc));
                    }
                    case "xs:length" -> lengthField.setText(value);
                    case "xs:minLength" -> minLengthField.setText(value);
                    case "xs:maxLength" -> maxLengthField.setText(value);
                    case "xs:minInclusive" -> minInclusiveField.setText(value);
                    case "xs:maxInclusive" -> maxInclusiveField.setText(value);
                    case "xs:minExclusive" -> minExclusiveField.setText(value);
                    case "xs:maxExclusive" -> maxExclusiveField.setText(value);
                    case "xs:totalDigits" -> totalDigitsField.setText(value);
                    case "xs:fractionDigits" -> fractionDigitsField.setText(value);
                    case "xs:whiteSpace" -> whiteSpaceCombo.setValue(value);
                }
            }
        }
    }

    /**
     * Load documentation from element
     */
    private void loadDocumentation(Element element) {
        String doc = getDocumentation(element);
        if (!doc.isEmpty()) {
            documentationArea.setText(doc);
        }
    }

    /**
     * Get documentation from element
     */
    private String getDocumentation(Element element) {
        NodeList annotations = element.getElementsByTagName("xs:annotation");
        if (annotations.getLength() > 0) {
            NodeList docs = ((Element) annotations.item(0)).getElementsByTagName("xs:documentation");
            if (docs.getLength() > 0) {
                return docs.item(0).getTextContent();
            }
        }
        return "";
    }

    /**
     * Show information about the selected base type
     */
    private void showTypeInfo() {
        String baseType = baseTypeCombo.getValue();
        String info = getTypeInfo(baseType);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Type Information");
        alert.setHeaderText(baseType);
        alert.setContentText(info);
        alert.showAndWait();
    }

    /**
     * Get information about a type
     */
    private String getTypeInfo(String type) {
        return switch (type) {
            case "xs:string" -> "Represents character strings in XML";
            case "xs:boolean" -> "Represents boolean values (true/false, 1/0)";
            case "xs:decimal" -> "Represents arbitrary precision decimal numbers";
            case "xs:integer" -> "Represents integer values";
            case "xs:date" -> "Represents dates (YYYY-MM-DD)";
            case "xs:time" -> "Represents time values (HH:MM:SS)";
            case "xs:dateTime" -> "Represents date and time values";
            case "xs:anyURI" -> "Represents URIs and URLs";
            case "xs:base64Binary" -> "Represents base64-encoded binary data";
            case "xs:hexBinary" -> "Represents hexadecimal-encoded binary data";
            default -> "XSD built-in type";
        };
    }

    /**
     * Create the result object
     */
    private SimpleTypeResult createResult() {
        // Convert patterns from textarea (one per line) to list
        List<String> patterns = Arrays.asList(patternArea.getText().trim().split("\\n"))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // Convert enumeration items to map
        Map<String, String> enumerations = new HashMap<>();
        for (EnumerationItem item : enumerationItems) {
            enumerations.put(item.getValue(), item.getDescription());
        }

        // Helper method to parse integer fields
        int exactLength = parseIntField(lengthField.getText().trim());
        int minLength = parseIntField(minLengthField.getText().trim());
        int maxLength = parseIntField(maxLengthField.getText().trim());
        int totalDigits = parseIntField(totalDigitsField.getText().trim());
        int fractionDigits = parseIntField(fractionDigitsField.getText().trim());

        return new SimpleTypeResult(
                nameField.getText().trim(),
                baseTypeCombo.getValue(),
                patterns,
                enumerations,
                exactLength,
                minLength,
                maxLength,
                minInclusiveField.getText().trim(),
                maxInclusiveField.getText().trim(),
                minExclusiveField.getText().trim(),
                maxExclusiveField.getText().trim(),
                totalDigits,
                fractionDigits,
                whiteSpaceCombo.getValue(),
                documentationArea.getText().trim()
        );
    }

    /**
     * Parse integer field, return 0 if empty or invalid
     */
    private int parseIntField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Data class for enumeration items
     */
    public static class EnumerationItem {
        private final StringProperty value = new SimpleStringProperty();
        private final StringProperty description = new SimpleStringProperty();

        public EnumerationItem(String value, String description) {
            this.value.set(value);
            this.description.set(description);
        }

        public String getValue() {
            return value.get();
        }

        public void setValue(String value) {
            this.value.set(value);
        }

        public StringProperty valueProperty() {
            return value;
        }

        public String getDescription() {
            return description.get();
        }

        public void setDescription(String description) {
            this.description.set(description);
        }

        public StringProperty descriptionProperty() {
            return description;
        }
    }
}

