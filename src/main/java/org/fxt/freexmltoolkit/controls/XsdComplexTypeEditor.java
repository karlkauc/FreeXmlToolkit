package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
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

import java.util.ArrayList;

/**
 * Professional ComplexType Editor Dialog for XSD Schema
 * Supports content models, extensions, restrictions, and all ComplexType features
 */
public class XsdComplexTypeEditor extends Dialog<ComplexTypeResult> {

    private static final Logger logger = LogManager.getLogger(XsdComplexTypeEditor.class);

    // UI Components
    private TextField nameField;
    private ComboBox<String> contentModelCombo;
    private CheckBox mixedContentCheckBox;
    private CheckBox abstractTypeCheckBox;
    private ComboBox<String> derivationCombo;
    private ComboBox<String> baseTypeCombo;
    private TextArea documentationArea;

    // Content Model Components
    private TabPane contentTabPane;
    private VBox elementsContainer;
    private VBox attributesContainer;
    private TextArea previewArea;

    // Data
    private final ObservableList<ElementItem> elements = FXCollections.observableArrayList();
    private final ObservableList<AttributeItem> attributes = FXCollections.observableArrayList();
    private final boolean isEditMode;
    private final Element existingComplexType;
    private final Document xsdDocument;

    // Built-in XSD types for base type selection
    private static final String[] XSD_BUILT_IN_TYPES = {
            "xs:anyType", "xs:string", "xs:normalizedString", "xs:token", "xs:language",
            "xs:NMTOKEN", "xs:NMTOKENS", "xs:Name", "xs:NCName", "xs:ID", "xs:IDREF", "xs:IDREFS",
            "xs:ENTITY", "xs:ENTITIES", "xs:anyURI", "xs:QName", "xs:NOTATION",
            "xs:decimal", "xs:integer", "xs:nonPositiveInteger", "xs:negativeInteger",
            "xs:long", "xs:int", "xs:short", "xs:byte", "xs:nonNegativeInteger",
            "xs:unsignedLong", "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte",
            "xs:positiveInteger", "xs:float", "xs:double", "xs:boolean",
            "xs:duration", "xs:dateTime", "xs:date", "xs:time", "xs:gYearMonth",
            "xs:gYear", "xs:gMonthDay", "xs:gDay", "xs:gMonth", "xs:hexBinary", "xs:base64Binary"
    };

    /**
     * Default constructor for creating new ComplexType
     */
    public XsdComplexTypeEditor() {
        this(null, null);
    }

    /**
     * Constructor for creating new ComplexType with XSD document context
     */
    public XsdComplexTypeEditor(Document xsdDocument) {
        this(xsdDocument, null);
    }

    /**
     * Constructor for editing existing ComplexType
     */
    public XsdComplexTypeEditor(Document xsdDocument, Element existingComplexType) {
        this.xsdDocument = xsdDocument;
        this.existingComplexType = existingComplexType;
        this.isEditMode = existingComplexType != null;

        setTitle(isEditMode ? "Edit ComplexType" : "Create ComplexType");
        setHeaderText(isEditMode ?
                "Edit the complex type definition" :
                "Define a new complex type with content model");
        setResizable(true);

        // Set dialog size
        getDialogPane().setPrefWidth(800);
        getDialogPane().setPrefHeight(700);

        // Create UI
        createUI();

        // Load existing data if in edit mode
        if (isEditMode) {
            loadExistingComplexType();
        }

        // Set result converter
        setResultConverter(this::createResult);

        logger.info("ComplexType editor initialized in {} mode", isEditMode ? "edit" : "create");
    }

    /**
     * Create the main UI
     */
    private void createUI() {
        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(20));

        // Header section with basic properties
        VBox headerSection = createHeaderSection();

        // Content model tabs
        contentTabPane = createContentModelTabs();

        // Preview section
        VBox previewSection = createPreviewSection();

        mainContainer.getChildren().addAll(headerSection, contentTabPane, previewSection);

        // Wrap in scroll pane
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        getDialogPane().setContent(scrollPane);

        // Add buttons
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style the dialog
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/atlantafx-base.css").toExternalForm()
        );

        // Enable/disable OK button based on validation
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(nameField.textProperty().isEmpty());

        // Add live preview updates
        setupLivePreview();
    }

    /**
     * Create header section with basic ComplexType properties
     */
    private VBox createHeaderSection() {
        VBox headerSection = new VBox(10);
        headerSection.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 15; -fx-background-radius: 5;");

        // Title
        Label headerTitle = new Label("ComplexType Definition");
        headerTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        headerTitle.setTextFill(Color.web("#495057"));

        // Name field
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label("Name:");
        nameLabel.setPrefWidth(120);
        nameField = new TextField();
        nameField.setPromptText("Enter ComplexType name");
        nameField.setPrefWidth(200);
        nameBox.getChildren().addAll(nameLabel, nameField);

        // Content Model selection
        HBox contentModelBox = new HBox(10);
        contentModelBox.setAlignment(Pos.CENTER_LEFT);
        Label contentModelLabel = new Label("Content Model:");
        contentModelLabel.setPrefWidth(120);
        contentModelCombo = new ComboBox<>();
        contentModelCombo.getItems().addAll("sequence", "choice", "all", "empty", "simple");
        contentModelCombo.setValue("sequence");
        contentModelCombo.setPrefWidth(150);
        contentModelBox.getChildren().addAll(contentModelLabel, contentModelCombo);

        // Derivation type
        HBox derivationBox = new HBox(10);
        derivationBox.setAlignment(Pos.CENTER_LEFT);
        Label derivationLabel = new Label("Derivation:");
        derivationLabel.setPrefWidth(120);
        derivationCombo = new ComboBox<>();
        derivationCombo.getItems().addAll("none", "extension", "restriction");
        derivationCombo.setValue("none");
        derivationCombo.setPrefWidth(150);
        derivationBox.getChildren().addAll(derivationLabel, derivationCombo);

        // Base Type selection (enabled when derivation is not "none")
        HBox baseTypeBox = new HBox(10);
        baseTypeBox.setAlignment(Pos.CENTER_LEFT);
        Label baseTypeLabel = new Label("Base Type:");
        baseTypeLabel.setPrefWidth(120);
        baseTypeCombo = new ComboBox<>();
        baseTypeCombo.getItems().addAll(XSD_BUILT_IN_TYPES);
        baseTypeCombo.setValue("xs:anyType");
        baseTypeCombo.setPrefWidth(200);
        baseTypeCombo.setDisable(true);
        baseTypeBox.getChildren().addAll(baseTypeLabel, baseTypeCombo);

        // Enable/disable base type based on derivation
        derivationCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            baseTypeCombo.setDisable("none".equals(newVal));
        });

        // Properties checkboxes
        HBox propertiesBox = new HBox(20);
        propertiesBox.setAlignment(Pos.CENTER_LEFT);

        mixedContentCheckBox = new CheckBox("Mixed Content");
        mixedContentCheckBox.setTooltip(new Tooltip("Allow text content mixed with elements"));

        abstractTypeCheckBox = new CheckBox("Abstract Type");
        abstractTypeCheckBox.setTooltip(new Tooltip("Type cannot be used directly, only as base for other types"));

        propertiesBox.getChildren().addAll(mixedContentCheckBox, abstractTypeCheckBox);

        headerSection.getChildren().addAll(
                headerTitle, nameBox, contentModelBox, derivationBox, baseTypeBox, propertiesBox
        );

        return headerSection;
    }

    /**
     * Create content model tabs
     */
    private TabPane createContentModelTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefHeight(350);

        // Elements tab
        Tab elementsTab = new Tab("Elements");
        elementsTab.setGraphic(new FontIcon("bi-box"));
        elementsTab.setContent(createElementsTab());

        // Attributes tab  
        Tab attributesTab = new Tab("Attributes");
        attributesTab.setGraphic(new FontIcon("bi-at"));
        attributesTab.setContent(createAttributesTab());

        // Documentation tab
        Tab documentationTab = new Tab("Documentation");
        documentationTab.setGraphic(new FontIcon("bi-file-text"));
        documentationTab.setContent(createDocumentationTab());

        tabPane.getTabs().addAll(elementsTab, attributesTab, documentationTab);

        return tabPane;
    }

    /**
     * Create elements management tab
     */
    private Node createElementsTab() {
        VBox elementsVBox = new VBox(10);
        elementsVBox.setPadding(new Insets(15));

        // Title and add button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Elements");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Button addElementButton = new Button("Add Element");
        addElementButton.setGraphic(new FontIcon("bi-plus-circle"));
        addElementButton.setOnAction(e -> showAddElementDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerBox.getChildren().addAll(title, spacer, addElementButton);

        // Elements table
        TableView<ElementItem> elementsTable = createElementsTable();

        elementsVBox.getChildren().addAll(headerBox, elementsTable);

        return elementsVBox;
    }

    /**
     * Create elements table
     */
    private TableView<ElementItem> createElementsTable() {
        TableView<ElementItem> table = new TableView<>(elements);
        table.setEditable(true);
        table.setPrefHeight(250);

        // Name column
        TableColumn<ElementItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setPrefWidth(150);

        // Type column
        TableColumn<ElementItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setPrefWidth(150);

        // Min Occurs column
        TableColumn<ElementItem, String> minOccursCol = new TableColumn<>("Min");
        minOccursCol.setCellValueFactory(data -> data.getValue().minOccursProperty());
        minOccursCol.setCellFactory(TextFieldTableCell.forTableColumn());
        minOccursCol.setPrefWidth(60);

        // Max Occurs column
        TableColumn<ElementItem, String> maxOccursCol = new TableColumn<>("Max");
        maxOccursCol.setCellValueFactory(data -> data.getValue().maxOccursProperty());
        maxOccursCol.setCellFactory(TextFieldTableCell.forTableColumn());
        maxOccursCol.setPrefWidth(60);

        // Actions column
        TableColumn<ElementItem, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(param -> new TableCell<ElementItem, Void>() {
            private final Button deleteButton = new Button();

            {
                deleteButton.setGraphic(new FontIcon("bi-trash"));
                deleteButton.getStyleClass().add("danger");
                deleteButton.setOnAction(e -> {
                    ElementItem item = getTableView().getItems().get(getIndex());
                    elements.remove(item);
                    updatePreview();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });
        actionsCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, typeCol, minOccursCol, maxOccursCol, actionsCol);

        return table;
    }

    /**
     * Create attributes management tab
     */
    private Node createAttributesTab() {
        VBox attributesVBox = new VBox(10);
        attributesVBox.setPadding(new Insets(15));

        // Title and add button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Attributes");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Button addAttributeButton = new Button("Add Attribute");
        addAttributeButton.setGraphic(new FontIcon("bi-plus-circle"));
        addAttributeButton.setOnAction(e -> showAddAttributeDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerBox.getChildren().addAll(title, spacer, addAttributeButton);

        // Attributes table
        TableView<AttributeItem> attributesTable = createAttributesTable();

        attributesVBox.getChildren().addAll(headerBox, attributesTable);

        return attributesVBox;
    }

    /**
     * Create attributes table
     */
    private TableView<AttributeItem> createAttributesTable() {
        TableView<AttributeItem> table = new TableView<>(attributes);
        table.setEditable(true);
        table.setPrefHeight(250);

        // Name column
        TableColumn<AttributeItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setPrefWidth(150);

        // Type column
        TableColumn<AttributeItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setPrefWidth(150);

        // Use column
        TableColumn<AttributeItem, String> useCol = new TableColumn<>("Use");
        useCol.setCellValueFactory(data -> data.getValue().useProperty());
        useCol.setCellFactory(param -> new ComboBoxTableCell<>("optional", "required", "prohibited"));
        useCol.setPrefWidth(100);

        // Actions column
        TableColumn<AttributeItem, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(param -> new TableCell<AttributeItem, Void>() {
            private final Button deleteButton = new Button();

            {
                deleteButton.setGraphic(new FontIcon("bi-trash"));
                deleteButton.getStyleClass().add("danger");
                deleteButton.setOnAction(e -> {
                    AttributeItem item = getTableView().getItems().get(getIndex());
                    attributes.remove(item);
                    updatePreview();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });
        actionsCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, typeCol, useCol, actionsCol);

        return table;
    }

    /**
     * Create documentation tab
     */
    private Node createDocumentationTab() {
        VBox docVBox = new VBox(10);
        docVBox.setPadding(new Insets(15));

        Label title = new Label("Documentation");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        documentationArea = new TextArea();
        documentationArea.setPromptText("Enter documentation for this ComplexType...");
        documentationArea.setPrefRowCount(12);
        documentationArea.setWrapText(true);

        docVBox.getChildren().addAll(title, documentationArea);

        return docVBox;
    }

    /**
     * Create preview section
     */
    private VBox createPreviewSection() {
        VBox previewSection = new VBox(10);
        previewSection.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 15; -fx-background-radius: 5;");

        Label previewTitle = new Label("XSD Preview");
        previewTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        previewTitle.setGraphic(new FontIcon("bi-eye"));

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(8);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");

        previewSection.getChildren().addAll(previewTitle, previewArea);

        return previewSection;
    }

    /**
     * Setup live preview updates
     */
    private void setupLivePreview() {
        // Update preview when any field changes
        nameField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        contentModelCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        derivationCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        baseTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        mixedContentCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        abstractTypeCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        documentationArea.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());

        // Initial preview
        updatePreview();
    }

    /**
     * Update XSD preview
     */
    private void updatePreview() {
        StringBuilder preview = new StringBuilder();

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "UnnamedComplexType";
        }

        preview.append("<xs:complexType name=\"").append(name).append("\"");

        if (abstractTypeCheckBox.isSelected()) {
            preview.append(" abstract=\"true\"");
        }

        if (mixedContentCheckBox.isSelected()) {
            preview.append(" mixed=\"true\"");
        }

        preview.append(">\n");

        // Add documentation if present
        String documentation = documentationArea.getText();
        if (documentation != null && !documentation.trim().isEmpty()) {
            preview.append("  <xs:annotation>\n");
            preview.append("    <xs:documentation>").append(documentation.trim()).append("</xs:documentation>\n");
            preview.append("  </xs:annotation>\n");
        }

        // Add derivation
        String derivation = derivationCombo.getValue();
        if (!"none".equals(derivation)) {
            preview.append("  <xs:complexContent>\n");
            preview.append("    <xs:").append(derivation).append(" base=\"").append(baseTypeCombo.getValue()).append("\">\n");

            addContentModelToPreview(preview, "      ");

            preview.append("    </xs:").append(derivation).append(">\n");
            preview.append("  </xs:complexContent>\n");
        } else {
            addContentModelToPreview(preview, "  ");
        }

        preview.append("</xs:complexType>");

        previewArea.setText(preview.toString());
    }

    /**
     * Add content model to preview
     */
    private void addContentModelToPreview(StringBuilder preview, String indent) {
        String contentModel = contentModelCombo.getValue();

        if ("empty".equals(contentModel)) {
            // No content
            return;
        }

        if (!elements.isEmpty() && !"simple".equals(contentModel)) {
            preview.append(indent).append("<xs:").append(contentModel).append(">\n");

            for (ElementItem element : elements) {
                preview.append(indent).append("  <xs:element name=\"").append(element.getName())
                        .append("\" type=\"").append(element.getType()).append("\"");

                if (!element.getMinOccurs().equals("1")) {
                    preview.append(" minOccurs=\"").append(element.getMinOccurs()).append("\"");
                }
                if (!element.getMaxOccurs().equals("1")) {
                    preview.append(" maxOccurs=\"").append(element.getMaxOccurs()).append("\"");
                }

                preview.append("/>\n");
            }

            preview.append(indent).append("</xs:").append(contentModel).append(">\n");
        }

        // Add attributes
        for (AttributeItem attribute : attributes) {
            preview.append(indent).append("<xs:attribute name=\"").append(attribute.getName())
                    .append("\" type=\"").append(attribute.getType()).append("\"");

            if (!"optional".equals(attribute.getUse())) {
                preview.append(" use=\"").append(attribute.getUse()).append("\"");
            }

            preview.append("/>\n");
        }
    }

    /**
     * Show add element dialog
     */
    private void showAddElementDialog() {
        Dialog<ElementItem> dialog = new Dialog<>();
        dialog.setTitle("Add Element");
        dialog.setHeaderText("Define a new element for this ComplexType");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Element name");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(XSD_BUILT_IN_TYPES);
        typeCombo.setValue("xs:string");
        typeCombo.setEditable(true);

        TextField minOccursField = new TextField("1");
        TextField maxOccursField = new TextField("1");

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);
        grid.add(new Label("Min Occurs:"), 0, 2);
        grid.add(minOccursField, 1, 2);
        grid.add(new Label("Max Occurs:"), 0, 3);
        grid.add(maxOccursField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(nameField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ElementItem(nameField.getText(), typeCombo.getValue(),
                        minOccursField.getText(), maxOccursField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(element -> {
            elements.add(element);
            updatePreview();
        });
    }

    /**
     * Show add attribute dialog
     */
    private void showAddAttributeDialog() {
        Dialog<AttributeItem> dialog = new Dialog<>();
        dialog.setTitle("Add Attribute");
        dialog.setHeaderText("Define a new attribute for this ComplexType");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Attribute name");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(XSD_BUILT_IN_TYPES);
        typeCombo.setValue("xs:string");
        typeCombo.setEditable(true);

        ComboBox<String> useCombo = new ComboBox<>();
        useCombo.getItems().addAll("optional", "required", "prohibited");
        useCombo.setValue("optional");

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);
        grid.add(new Label("Use:"), 0, 2);
        grid.add(useCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(nameField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new AttributeItem(nameField.getText(), typeCombo.getValue(), useCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(attribute -> {
            attributes.add(attribute);
            updatePreview();
        });
    }

    /**
     * Load existing ComplexType data for editing
     */
    private void loadExistingComplexType() {
        if (existingComplexType == null) return;

        // Load basic properties
        nameField.setText(existingComplexType.getAttribute("name"));

        if ("true".equals(existingComplexType.getAttribute("mixed"))) {
            mixedContentCheckBox.setSelected(true);
        }

        if ("true".equals(existingComplexType.getAttribute("abstract"))) {
            abstractTypeCheckBox.setSelected(true);
        }

        // TODO: Load content model, elements, attributes from existing ComplexType
        // This would require parsing the existing XSD structure

        updatePreview();
    }

    /**
     * Create result object
     */
    private ComplexTypeResult createResult(ButtonType buttonType) {
        if (buttonType != ButtonType.OK) {
            return null;
        }

        return new ComplexTypeResult(
                nameField.getText().trim(),
                contentModelCombo.getValue(),
                derivationCombo.getValue(),
                baseTypeCombo.getValue(),
                mixedContentCheckBox.isSelected(),
                abstractTypeCheckBox.isSelected(),
                new ArrayList<>(elements),
                new ArrayList<>(attributes),
                documentationArea.getText()
        );
    }

    /**
     * Data class for element items
     */
    public static class ElementItem {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty type = new SimpleStringProperty();
        private final StringProperty minOccurs = new SimpleStringProperty();
        private final StringProperty maxOccurs = new SimpleStringProperty();

        public ElementItem(String name, String type, String minOccurs, String maxOccurs) {
            setName(name);
            setType(type);
            setMinOccurs(minOccurs);
            setMaxOccurs(maxOccurs);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public String getType() {
            return type.get();
        }

        public void setType(String type) {
            this.type.set(type);
        }

        public StringProperty typeProperty() {
            return type;
        }

        public String getMinOccurs() {
            return minOccurs.get();
        }

        public void setMinOccurs(String minOccurs) {
            this.minOccurs.set(minOccurs);
        }

        public StringProperty minOccursProperty() {
            return minOccurs;
        }

        public String getMaxOccurs() {
            return maxOccurs.get();
        }

        public void setMaxOccurs(String maxOccurs) {
            this.maxOccurs.set(maxOccurs);
        }

        public StringProperty maxOccursProperty() {
            return maxOccurs;
        }
    }

    /**
     * Data class for attribute items
     */
    public static class AttributeItem {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty type = new SimpleStringProperty();
        private final StringProperty use = new SimpleStringProperty();

        public AttributeItem(String name, String type, String use) {
            setName(name);
            setType(type);
            setUse(use);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public String getType() {
            return type.get();
        }

        public void setType(String type) {
            this.type.set(type);
        }

        public StringProperty typeProperty() {
            return type;
        }

        public String getUse() {
            return use.get();
        }

        public void setUse(String use) {
            this.use.set(use);
        }

        public StringProperty useProperty() {
            return use;
        }
    }
}