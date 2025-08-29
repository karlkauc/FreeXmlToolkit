/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023-2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced Type Selection Dialog for XSD Editor.
 * Provides hierarchical type browsing, filtering, and type preview.
 */
public class XsdTypeSelector extends Dialog<String> {

    private static final Logger logger = LogManager.getLogger(XsdTypeSelector.class);

    // Built-in XSD Types categorized
    private static final Map<String, List<String>> BUILTIN_TYPE_CATEGORIES = Map.of(
            "String Types", Arrays.asList(
                    "xs:string", "xs:normalizedString", "xs:token", "xs:language", "xs:Name",
                    "xs:NCName", "xs:ID", "xs:IDREF", "xs:IDREFS", "xs:ENTITY", "xs:ENTITIES",
                    "xs:NMTOKEN", "xs:NMTOKENS"
            ),
            "Numeric Types", Arrays.asList(
                    "xs:decimal", "xs:integer", "xs:long", "xs:int", "xs:short", "xs:byte",
                    "xs:positiveInteger", "xs:nonPositiveInteger", "xs:negativeInteger",
                    "xs:nonNegativeInteger", "xs:unsignedLong", "xs:unsignedInt",
                    "xs:unsignedShort", "xs:unsignedByte", "xs:float", "xs:double"
            ),
            "Date/Time Types", Arrays.asList(
                    "xs:dateTime", "xs:date", "xs:time", "xs:duration",
                    "xs:gYearMonth", "xs:gYear", "xs:gMonthDay", "xs:gDay", "xs:gMonth"
            ),
            "Binary Types", Arrays.asList(
                    "xs:base64Binary", "xs:hexBinary"
            ),
            "Other Types", Arrays.asList(
                    "xs:boolean", "xs:anyURI", "xs:QName", "xs:NOTATION"
            )
    );

    // UI Components
    private TreeView<TypeInfo> typeTreeView;
    private TextField searchField;
    private TextArea descriptionArea;
    private TextArea exampleArea;
    private ListView<String> recentTypesListView;
    private TabPane categoryTabPane;

    // Store original tree structure for search reset
    private TreeItem<TypeInfo> originalTreeRoot;

    // Data
    private final Document xsdDocument;
    private final Set<String> customTypes = new HashSet<>();
    private final Set<String> importedTypes = new HashSet<>();
    private final ObservableList<String> recentTypes = FXCollections.observableArrayList();
    private final ObservableList<String> favoriteTypes = FXCollections.observableArrayList();

    // Result
    private final StringProperty selectedType = new SimpleStringProperty();

    public XsdTypeSelector(Document xsdDocument) {
        this.xsdDocument = xsdDocument;

        setTitle("Select Type");
        setHeaderText("Choose a type for your element or attribute");
        setResizable(true);

        // Initialize dialog
        initializeDialog();
        extractCustomTypes();
        populateTypeTree();
        setupEventHandlers();

        // Set dialog result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return selectedType.get();
            }
            return null;
        });

        logger.debug("XsdTypeSelector initialized with {} custom types", customTypes.size());
    }

    /**
     * Initialize the dialog UI components.
     */
    private void initializeDialog() {
        // Create main layout
        BorderPane mainPane = new BorderPane();
        mainPane.setPrefSize(800, 600);

        // Top: Search and filters
        VBox topPane = createTopPane();
        mainPane.setTop(topPane);

        // Center: Split pane with type tree and details
        SplitPane centerPane = createCenterPane();
        mainPane.setCenter(centerPane);

        // Bottom: Recent types and favorites
        VBox bottomPane = createBottomPane();
        mainPane.setBottom(bottomPane);

        getDialogPane().setContent(mainPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Disable OK button initially
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        // Enable OK button when type is selected
        selectedType.addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });
    }

    /**
     * Create top pane with search and filters.
     */
    private VBox createTopPane() {
        VBox topPane = new VBox(10);
        topPane.setPadding(new Insets(15));
        topPane.setStyle("-fx-background-color: #f8f9fa;");

        // Title
        Label titleLabel = new Label("Type Selection");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Search box
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Search:");
        searchField = new TextField();
        searchField.setPromptText("Type name, description, or example...");
        searchField.setPrefWidth(300);

        Button clearButton = new Button();
        clearButton.setGraphic(new FontIcon("bi-x"));
        clearButton.setOnAction(e -> searchField.clear());

        searchBox.getChildren().addAll(searchLabel, searchField, clearButton);

        // Filter options
        HBox filterBox = new HBox(15);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        CheckBox showBuiltinCheck = new CheckBox("Built-in Types");
        showBuiltinCheck.setSelected(true);
        CheckBox showCustomCheck = new CheckBox("Custom Types");
        showCustomCheck.setSelected(true);
        CheckBox showImportedCheck = new CheckBox("Imported Types");
        showImportedCheck.setSelected(true);

        filterBox.getChildren().addAll(
                new Label("Show:"), showBuiltinCheck, showCustomCheck, showImportedCheck
        );

        topPane.getChildren().addAll(titleLabel, searchBox, filterBox);
        return topPane;
    }

    /**
     * Create center pane with type tree and details.
     */
    private SplitPane createCenterPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.4);

        // Left: Type tree
        VBox leftPane = createTypeTreePane();

        // Right: Type details  
        VBox rightPane = createTypeDetailsPane();

        splitPane.getItems().addAll(leftPane, rightPane);
        return splitPane;
    }

    /**
     * Create type tree pane.
     */
    private VBox createTypeTreePane() {
        VBox treePane = new VBox(10);
        treePane.setPadding(new Insets(15));

        Label treeLabel = new Label("Available Types");
        treeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Create type tree
        TreeItem<TypeInfo> rootItem = new TreeItem<>(new TypeInfo("Types", "", TypeCategory.ROOT));
        rootItem.setExpanded(true);

        typeTreeView = new TreeView<>(rootItem);
        typeTreeView.setShowRoot(false);
        typeTreeView.setCellFactory(tv -> new TypeTreeCell());
        VBox.setVgrow(typeTreeView, Priority.ALWAYS);

        treePane.getChildren().addAll(treeLabel, typeTreeView);
        return treePane;
    }

    /**
     * Create type details pane.
     */
    private VBox createTypeDetailsPane() {
        VBox detailsPane = new VBox(10);
        detailsPane.setPadding(new Insets(15));

        Label detailsLabel = new Label("Type Details");
        detailsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Create tab pane for different info
        categoryTabPane = new TabPane();

        // Description tab
        Tab descriptionTab = new Tab("Description");
        descriptionTab.setClosable(false);
        descriptionArea = new TextArea();
        descriptionArea.setEditable(false);
        descriptionArea.setPrefRowCount(6);
        descriptionArea.setWrapText(true);
        descriptionTab.setContent(new ScrollPane(descriptionArea));

        // Example tab
        Tab exampleTab = new Tab("Examples");
        exampleTab.setClosable(false);
        exampleArea = new TextArea();
        exampleArea.setEditable(false);
        exampleArea.setPrefRowCount(6);
        exampleArea.setWrapText(true);
        exampleArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace;");
        exampleTab.setContent(new ScrollPane(exampleArea));

        categoryTabPane.getTabs().addAll(descriptionTab, exampleTab);
        VBox.setVgrow(categoryTabPane, Priority.ALWAYS);

        detailsPane.getChildren().addAll(detailsLabel, categoryTabPane);
        return detailsPane;
    }

    /**
     * Create bottom pane with recent and favorite types.
     */
    private VBox createBottomPane() {
        VBox bottomPane = new VBox(10);
        bottomPane.setPadding(new Insets(15));
        bottomPane.setStyle("-fx-background-color: #f8f9fa;");

        // Recent types
        HBox recentBox = new HBox(10);
        recentBox.setAlignment(Pos.CENTER_LEFT);

        Label recentLabel = new Label("Recent:");
        recentTypesListView = new ListView<>(recentTypes);
        recentTypesListView.setPrefHeight(60);
        recentTypesListView.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        recentTypesListView.setCellFactory(lv -> new RecentTypeCell());
        HBox.setHgrow(recentTypesListView, Priority.ALWAYS);

        recentBox.getChildren().addAll(recentLabel, recentTypesListView);

        bottomPane.getChildren().add(recentBox);
        return bottomPane;
    }

    /**
     * Extract custom types from XSD document.
     */
    private void extractCustomTypes() {
        if (xsdDocument == null) return;

        // Extract complex types
        NodeList complexTypes = xsdDocument.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element element = (Element) complexTypes.item(i);
            String name = element.getAttribute("name");
            if (!name.isEmpty()) {
                customTypes.add(name);
            }
        }

        // Extract simple types
        NodeList simpleTypes = xsdDocument.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element element = (Element) simpleTypes.item(i);
            String name = element.getAttribute("name");
            if (!name.isEmpty()) {
                customTypes.add(name);
            }
        }

        logger.debug("Extracted {} custom types: {}", customTypes.size(), customTypes);
    }

    /**
     * Populate the type tree with all available types.
     */
    private void populateTypeTree() {
        TreeItem<TypeInfo> root = typeTreeView.getRoot();

        // Built-in types
        TreeItem<TypeInfo> builtinRoot = new TreeItem<>(new TypeInfo("Built-in Types", "Standard XSD Types", TypeCategory.BUILTIN_CATEGORY));
        builtinRoot.setExpanded(true);

        for (Map.Entry<String, List<String>> category : BUILTIN_TYPE_CATEGORIES.entrySet()) {
            TreeItem<TypeInfo> categoryItem = new TreeItem<>(new TypeInfo(category.getKey(), "", TypeCategory.BUILTIN_CATEGORY));
            categoryItem.setExpanded(false);

            for (String type : category.getValue()) {
                TypeInfo typeInfo = new TypeInfo(type, getTypeDescription(type), TypeCategory.BUILTIN);
                TreeItem<TypeInfo> typeItem = new TreeItem<>(typeInfo);
                categoryItem.getChildren().add(typeItem);
            }

            builtinRoot.getChildren().add(categoryItem);
        }

        // Custom types
        if (!customTypes.isEmpty()) {
            TreeItem<TypeInfo> customRoot = new TreeItem<>(new TypeInfo("Custom Types", "Types defined in this schema", TypeCategory.CUSTOM_CATEGORY));
            customRoot.setExpanded(true);

            for (String type : customTypes.stream().sorted().collect(Collectors.toList())) {
                TypeInfo typeInfo = new TypeInfo(type, "Custom type defined in this schema", TypeCategory.CUSTOM);
                TreeItem<TypeInfo> typeItem = new TreeItem<>(typeInfo);
                customRoot.getChildren().add(typeItem);
            }

            root.getChildren().add(customRoot);
        }

        // Imported types (placeholder)
        if (!importedTypes.isEmpty()) {
            TreeItem<TypeInfo> importedRoot = new TreeItem<>(new TypeInfo("Imported Types", "Types from imported schemas", TypeCategory.IMPORTED_CATEGORY));
            importedRoot.setExpanded(false);

            for (String type : importedTypes.stream().sorted().collect(Collectors.toList())) {
                TypeInfo typeInfo = new TypeInfo(type, "Imported type from external schema", TypeCategory.IMPORTED);
                TreeItem<TypeInfo> typeItem = new TreeItem<>(typeInfo);
                importedRoot.getChildren().add(typeItem);
            }

            root.getChildren().add(importedRoot);
        }

        root.getChildren().add(builtinRoot);

        // Initialize recent types with commonly used types
        recentTypes.addAll(Arrays.asList("xs:string", "xs:int", "xs:boolean", "xs:dateTime"));

        // Store the original tree structure for search reset
        originalTreeRoot = cloneTreeItem(root);
    }

    /**
     * Setup event handlers for UI components.
     */
    private void setupEventHandlers() {
        // Tree selection
        typeTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue().category.isSelectableType()) {
                selectedType.set(newVal.getValue().name);
                updateTypeDetails(newVal.getValue());
            } else {
                selectedType.set(null);
                clearTypeDetails();
            }
        });

        // Search functionality
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterTypes(newVal);
        });

        // Recent types selection
        recentTypesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedType.set(newVal);
                selectTypeInTree(newVal);
            }
        });

        // Double-click to select
        typeTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<TypeInfo> item = typeTreeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue().category.isSelectableType()) {
                    // Trigger OK button
                    Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
                    okButton.fire();
                }
            }
        });
    }

    /**
     * Update type details pane.
     */
    private void updateTypeDetails(TypeInfo typeInfo) {
        descriptionArea.setText(typeInfo.description + "\n\n" + getExtendedTypeDescription(typeInfo.name));
        exampleArea.setText(getTypeExamples(typeInfo.name));
    }

    /**
     * Clear type details pane.
     */
    private void clearTypeDetails() {
        descriptionArea.clear();
        exampleArea.clear();
    }

    /**
     * Filter types based on search text.
     */
    private void filterTypes(String searchText) {
        logger.debug("Filtering types with: {}", searchText);

        TreeItem<TypeInfo> root = typeTreeView.getRoot();
        if (root == null) return;

        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all types when search is empty - restore original tree
            if (originalTreeRoot != null) {
                typeTreeView.setRoot(cloneTreeItem(originalTreeRoot));
            }
            return;
        }

        String searchLower = searchText.toLowerCase().trim();

        // Create filtered tree
        TreeItem<TypeInfo> filteredRoot = new TreeItem<>(root.getValue());
        buildFilteredTree(root, filteredRoot, searchLower);

        // Replace tree content
        typeTreeView.setRoot(filteredRoot);

        // Expand all categories that have matches
        expandAllCategories(filteredRoot);
    }

    /**
     * Build filtered tree recursively
     */
    private void buildFilteredTree(TreeItem<TypeInfo> source, TreeItem<TypeInfo> target, String searchLower) {
        for (TreeItem<TypeInfo> child : source.getChildren()) {
            TypeInfo childInfo = child.getValue();

            // Check if this item matches
            boolean matches = false;
            if (childInfo.category.isSelectableType()) {
                String nameLower = childInfo.name.toLowerCase();
                String descLower = childInfo.description.toLowerCase();
                String examplesLower = getTypeExamples(childInfo.name).toLowerCase();

                // Priority 1: Exact name match or name contains search term
                if (nameLower.contains(searchLower)) {
                    matches = true;
                }
                // Priority 2: Description contains search term as whole word
                else if (containsWholeWord(descLower, searchLower)) {
                    matches = true;
                }
                // Priority 3: Examples contain search term
                else if (examplesLower.contains(searchLower)) {
                    matches = true;
                }
            }

            // Check if any children match
            TreeItem<TypeInfo> filteredChild = new TreeItem<>(childInfo);
            buildFilteredTree(child, filteredChild, searchLower);
            boolean hasMatchingChildren = !filteredChild.getChildren().isEmpty();

            // Add this item if it matches or has matching children
            if (matches || hasMatchingChildren) {
                target.getChildren().add(filteredChild);
            }
        }
    }

    /**
     * Expand all category nodes
     */
    private void expandAllCategories(TreeItem<TypeInfo> item) {
        if (!item.getValue().category.isSelectableType()) {
            item.setExpanded(true);
        }

        for (TreeItem<TypeInfo> child : item.getChildren()) {
            expandAllCategories(child);
        }
    }

    /**
     * Clone a tree item and all its children
     */
    private TreeItem<TypeInfo> cloneTreeItem(TreeItem<TypeInfo> source) {
        TreeItem<TypeInfo> clone = new TreeItem<>(source.getValue());
        clone.setExpanded(source.isExpanded());

        for (TreeItem<TypeInfo> child : source.getChildren()) {
            clone.getChildren().add(cloneTreeItem(child));
        }

        return clone;
    }

    /**
     * Check if text contains search term as a whole word
     */
    private boolean containsWholeWord(String text, String searchTerm) {
        if (text == null || searchTerm == null) {
            return false;
        }

        // Use word boundary regex to match whole words only
        String pattern = "\\b" + java.util.regex.Pattern.quote(searchTerm) + "\\b";
        return java.util.regex.Pattern.compile(pattern).matcher(text).find();
    }

    /**
     * Select type in tree.
     */
    private void selectTypeInTree(String typeName) {
        // Find and select the type in the tree
        TreeItem<TypeInfo> root = typeTreeView.getRoot();
        TreeItem<TypeInfo> typeItem = findTypeInTree(root, typeName);
        if (typeItem != null) {
            typeTreeView.getSelectionModel().select(typeItem);
            typeTreeView.scrollTo(typeTreeView.getSelectionModel().getSelectedIndex());
        }
    }

    /**
     * Find type in tree recursively.
     */
    private TreeItem<TypeInfo> findTypeInTree(TreeItem<TypeInfo> item, String typeName) {
        if (item.getValue().name.equals(typeName)) {
            return item;
        }

        for (TreeItem<TypeInfo> child : item.getChildren()) {
            TreeItem<TypeInfo> result = findTypeInTree(child, typeName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Get type description.
     */
    private String getTypeDescription(String type) {
        return switch (type) {
            case "xs:string" -> "A sequence of characters";
            case "xs:int" -> "32-bit signed integer (-2147483648 to 2147483647)";
            case "xs:boolean" -> "True or false value";
            case "xs:dateTime" -> "Date and time in ISO 8601 format";
            case "xs:decimal" -> "Decimal number with arbitrary precision";
            case "xs:double" -> "64-bit floating point number";
            case "xs:float" -> "32-bit floating point number";
            case "xs:long" -> "64-bit signed integer";
            case "xs:short" -> "16-bit signed integer";
            case "xs:byte" -> "8-bit signed integer";
            case "xs:date" -> "Date in YYYY-MM-DD format";
            case "xs:time" -> "Time in HH:MM:SS format";
            case "xs:anyURI" -> "Uniform Resource Identifier";
            case "xs:base64Binary" -> "Base64-encoded binary data";
            case "xs:hexBinary" -> "Hex-encoded binary data";
            default -> "XSD data type";
        };
    }

    /**
     * Get extended type description.
     */
    private String getExtendedTypeDescription(String type) {
        return switch (type) {
            case "xs:string" ->
                    "The string type can contain any Unicode characters. No restrictions on length or content unless specified by facets.";
            case "xs:int" ->
                    "Derived from xs:long. Commonly used for integer values in applications. Range: -2,147,483,648 to 2,147,483,647.";
            case "xs:boolean" -> "Can contain values: true, false, 1 (true), 0 (false). Case sensitive.";
            case "xs:dateTime" -> "Format: YYYY-MM-DDTHH:MM:SS with optional timezone. Example: 2023-12-25T14:30:00Z";
            default -> "Standard XSD built-in type with specific validation rules and formatting requirements.";
        };
    }

    /**
     * Get type examples.
     */
    private String getTypeExamples(String type) {
        return switch (type) {
            case "xs:string" -> """
                    Examples:
                    "Hello World"
                    "User Name"
                    "Product-123"
                    ""
                    "Multi-line
                    text content"
                    """;
            case "xs:int" -> """
                    Examples:
                    42
                    -123
                    0
                    2147483647
                    -2147483648
                    """;
            case "xs:boolean" -> """
                    Examples:
                    true
                    false
                    1
                    0
                    """;
            case "xs:dateTime" -> """
                    Examples:
                    2023-12-25T14:30:00
                    2023-12-25T14:30:00Z
                    2023-12-25T14:30:00+01:00
                    2023-01-01T00:00:00.000Z
                    """;
            case "xs:decimal" -> """
                    Examples:
                    123.45
                    0.001
                    -999.999
                    1000000.00
                    0
                    """;
            case "xs:date" -> """
                    Examples:
                    2023-12-25
                    2023-01-01
                    2023-02-29
                    """;
            case "xs:time" -> """
                    Examples:
                    14:30:00
                    09:15:30
                    23:59:59
                    00:00:00
                    """;
            default -> "No examples available for this type.";
        };
    }

    // Inner Classes

    /**
         * Type information holder.
         */
        public record TypeInfo(String name, String description, TypeCategory category) {

        @Override
            public String toString() {
                return name;
            }
        }

    /**
     * Type categories for organization.
     */
    public enum TypeCategory {
        ROOT(false),
        BUILTIN_CATEGORY(false),
        CUSTOM_CATEGORY(false),
        IMPORTED_CATEGORY(false),
        BUILTIN(true),
        CUSTOM(true),
        IMPORTED(true);

        private final boolean selectableType;

        TypeCategory(boolean selectableType) {
            this.selectableType = selectableType;
        }

        public boolean isSelectableType() {
            return selectableType;
        }
    }

    /**
     * Custom tree cell for type display.
     */
    private static class TypeTreeCell extends TreeCell<TypeInfo> {
        @Override
        protected void updateItem(TypeInfo item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.name);

                // Set icon based on category
                FontIcon icon = switch (item.category) {
                    case BUILTIN -> new FontIcon("bi-box");
                    case CUSTOM -> new FontIcon("bi-gear");
                    case IMPORTED -> new FontIcon("bi-download");
                    case BUILTIN_CATEGORY, CUSTOM_CATEGORY, IMPORTED_CATEGORY -> new FontIcon("bi-folder");
                    default -> new FontIcon("bi-question-circle");
                };

                // Set icon color
                icon.getStyleClass().add(switch (item.category) {
                    case BUILTIN -> "builtin-type-icon";
                    case CUSTOM -> "custom-type-icon";
                    case IMPORTED -> "imported-type-icon";
                    default -> "category-icon";
                });

                setGraphic(icon);
            }
        }
    }

    /**
     * Custom cell for recent types list.
     */
    private static class RecentTypeCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);
                setStyle("-fx-border-color: #ddd; -fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; -fx-padding: 2 8 2 8;");
            }
        }
    }
}