package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Panel for viewing and editing XSD facets (restrictions).
 * Displays facets in a table with add/edit/delete functionality.
 *
 * @since 2.0
 */
public class FacetsPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(FacetsPanel.class);

    private final XsdEditorContext editorContext;
    private XsdRestriction currentRestriction;
    private XsdElement currentElement; // For showing referenced type facets
    private boolean isInheritedView; // True if showing facets from referenced type

    private TableView<XsdFacet> facetsTable;
    private Button addButton;
    private Button editButton;
    private Button deleteButton;
    private Label infoLabel; // Shows info about inherited facets

    /**
     * Creates a new facets panel.
     *
     * @param editorContext the editor context
     */
    public FacetsPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setPadding(new Insets(10));
        setSpacing(10);

        // Title
        Label titleLabel = new Label("Facets (Restrictions)");
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Info label for inherited facets
        infoLabel = new Label();
        infoLabel.setStyle("-fx-background-color: #d1ecf1; -fx-text-fill: #0c5460; " +
                          "-fx-padding: 5; -fx-border-color: #bee5eb; -fx-border-radius: 3; " +
                          "-fx-background-radius: 3;");
        infoLabel.setWrapText(true);
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);

        // Table
        facetsTable = new TableView<>();
        facetsTable.setPlaceholder(new Label("No facets defined"));
        facetsTable.setPrefHeight(200);

        // Type column
        TableColumn<XsdFacet, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cellData -> {
            XsdFacetType type = cellData.getValue().getFacetType();
            return new javafx.beans.property.SimpleStringProperty(
                    type != null ? type.getXmlName() : "");
        });
        typeColumn.setPrefWidth(120);

        // Value column with custom cell factory to highlight fixed/inherited values
        TableColumn<XsdFacet, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueColumn.setCellFactory(col -> new TableCell<XsdFacet, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    setText(value);

                    // Get the facet from this row
                    XsdFacet facet = getTableView().getItems().get(getIndex());

                    // If showing inherited facets (from referenced type)
                    if (isInheritedView) {
                        setStyle("-fx-background-color: #e7f3ff; -fx-text-fill: #004085; " +
                                "-fx-font-style: italic;");
                        Tooltip tooltip = new Tooltip(
                            "Inherited from type '" + (currentElement != null ? currentElement.getType() : "?") +
                            "' (read-only)"
                        );
                        setTooltip(tooltip);
                    } else {
                        // Check if this facet is fixed for the base type
                        String baseType = currentRestriction != null ? currentRestriction.getBase() : null;
                        if (baseType != null && facet != null &&
                            XsdDatatypeFacets.isFacetFixed(baseType, facet.getFacetType())) {
                            setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
                            Tooltip tooltip = new Tooltip(
                                "Fixed value for " + baseType + " (defined by XSD specification)"
                            );
                            setTooltip(tooltip);
                        } else {
                            setStyle("");
                            setTooltip(null);
                        }
                    }
                }
            }
        });
        valueColumn.setPrefWidth(200);

        // Fixed column
        TableColumn<XsdFacet, Boolean> fixedColumn = new TableColumn<>("Fixed");
        fixedColumn.setCellValueFactory(new PropertyValueFactory<>("fixed"));
        fixedColumn.setPrefWidth(60);

        facetsTable.getColumns().addAll(typeColumn, valueColumn, fixedColumn);

        // Buttons
        addButton = new Button("Add");
        addButton.setOnAction(e -> handleAdd());

        editButton = new Button("Edit");
        editButton.setOnAction(e -> handleEdit());
        editButton.setDisable(true);

        deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> handleDelete());
        deleteButton.setDisable(true);

        // Enable/disable edit/delete based on selection
        facetsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
        });

        HBox buttonBox = new HBox(10, addButton, editButton, deleteButton);

        VBox.setVgrow(facetsTable, Priority.ALWAYS);
        getChildren().addAll(titleLabel, infoLabel, facetsTable, buttonBox);

        // Initially disabled until a restriction is set
        setDisable(true);
    }

    /**
     * Sets the restriction to display/edit.
     *
     * @param restriction the restriction, or null to clear
     */
    public void setRestriction(XsdRestriction restriction) {
        this.currentRestriction = restriction;
        this.currentElement = null;
        this.isInheritedView = false;

        // Hide info label
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);

        if (restriction == null) {
            facetsTable.setItems(FXCollections.observableArrayList());
            setDisable(true);
            logger.debug("Cleared facets panel");
        } else {
            ObservableList<XsdFacet> facets = FXCollections.observableArrayList(restriction.getFacets());
            facetsTable.setItems(facets);
            setDisable(!editorContext.isEditMode());

            // Enable add/edit/delete for direct restrictions
            addButton.setDisable(false);

            logger.debug("Loaded {} facets for restriction (base: {})", facets.size(), restriction.getBase());
        }
    }

    /**
     * Sets an element to display facets from its referenced type (read-only).
     *
     * @param element the element, or null to clear
     */
    public void setElement(XsdElement element) {
        this.currentElement = element;
        this.currentRestriction = null;
        this.isInheritedView = false;

        if (element == null || element.getType() == null) {
            facetsTable.setItems(FXCollections.observableArrayList());
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("Cleared facets panel (no element or type)");
            return;
        }

        // Try to resolve the referenced type
        String typeName = element.getType();
        XsdSimpleType simpleType = findSimpleType(typeName);

        if (simpleType == null) {
            // Not a simple type or not found
            facetsTable.setItems(FXCollections.observableArrayList());
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("Type '{}' not found or not a simple type", typeName);
            return;
        }

        // Extract facets from the simple type's restriction
        List<XsdFacet> inheritedFacets = extractFacetsFromSimpleType(simpleType);

        if (inheritedFacets.isEmpty()) {
            facetsTable.setItems(FXCollections.observableArrayList());
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("No facets found in type '{}'", typeName);
            return;
        }

        // Display facets as read-only
        this.isInheritedView = true;
        ObservableList<XsdFacet> facets = FXCollections.observableArrayList(inheritedFacets);
        facetsTable.setItems(facets);
        setDisable(false); // Enable panel to show facets

        // Show info label
        infoLabel.setText("ℹ️ Showing facets from referenced type '" + typeName + "' (read-only)");
        infoLabel.setVisible(true);
        infoLabel.setManaged(true);

        // Disable all editing buttons
        addButton.setDisable(true);
        editButton.setDisable(true);
        deleteButton.setDisable(true);

        logger.debug("Loaded {} inherited facets from type '{}' for element '{}'",
                    inheritedFacets.size(), typeName, element.getName());
    }

    /**
     * Refreshes the facets from the current restriction.
     */
    public void refresh() {
        if (currentRestriction != null) {
            ObservableList<XsdFacet> facets = FXCollections.observableArrayList(currentRestriction.getFacets());
            facetsTable.setItems(facets);
            logger.debug("Refreshed facets panel with {} facets", facets.size());
        }
    }

    /**
     * Handles adding a new facet.
     */
    private void handleAdd() {
        // Can't add facets in inherited view
        if (isInheritedView || currentRestriction == null) {
            return;
        }

        // Create dialog for adding facet
        Dialog<FacetInput> dialog = createFacetDialog(null);
        dialog.showAndWait().ifPresent(input -> {
            AddFacetCommand command = new AddFacetCommand(
                    currentRestriction,
                    input.type,
                    input.value,
                    input.fixed
            );
            editorContext.getCommandManager().executeCommand(command);
            refresh();
            logger.info("Added {} facet", input.type.getXmlName());
        });
    }

    /**
     * Handles editing the selected facet.
     */
    private void handleEdit() {
        XsdFacet selected = facetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Can't edit facets in inherited view
        if (isInheritedView) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Cannot Edit");
            alert.setHeaderText("Inherited Facet");
            alert.setContentText("This facet is inherited from the referenced type '" +
                               (currentElement != null ? currentElement.getType() : "?") +
                               "' and cannot be edited here.\n\n" +
                               "To modify this facet, edit the SimpleType definition.");
            alert.showAndWait();
            return;
        }

        // Create dialog for editing facet
        Dialog<FacetInput> dialog = createFacetDialog(selected);
        dialog.showAndWait().ifPresent(input -> {
            EditFacetCommand command = new EditFacetCommand(
                    selected,
                    input.value,
                    input.fixed
            );
            editorContext.getCommandManager().executeCommand(command);
            refresh();
            logger.info("Edited {} facet", selected.getFacetType().getXmlName());
        });
    }

    /**
     * Handles deleting the selected facet.
     */
    private void handleDelete() {
        XsdFacet selected = facetsTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentRestriction == null) {
            return;
        }

        // Can't delete facets in inherited view
        if (isInheritedView) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Cannot Delete");
            alert.setHeaderText("Inherited Facet");
            alert.setContentText("This facet is inherited from the referenced type '" +
                               (currentElement != null ? currentElement.getType() : "?") +
                               "' and cannot be deleted here.\n\n" +
                               "To remove this facet, edit the SimpleType definition.");
            alert.showAndWait();
            return;
        }

        // Confirm deletion
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Facet");
        confirm.setHeaderText("Delete " + selected.getFacetType().getXmlName() + " facet?");
        confirm.setContentText("Value: " + selected.getValue());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                DeleteFacetCommand command = new DeleteFacetCommand(currentRestriction, selected);
                editorContext.getCommandManager().executeCommand(command);
                refresh();
                logger.info("Deleted {} facet", selected.getFacetType().getXmlName());
            }
        });
    }

    /**
     * Creates a dialog for adding/editing a facet.
     *
     * @param existingFacet the existing facet to edit, or null to add new
     * @return the dialog
     */
    private Dialog<FacetInput> createFacetDialog(XsdFacet existingFacet) {
        Dialog<FacetInput> dialog = new Dialog<>();
        dialog.setTitle(existingFacet == null ? "Add Facet" : "Edit Facet");

        // Get base type from restriction to filter applicable facets
        String baseType = currentRestriction != null ? currentRestriction.getBase() : null;
        Set<XsdFacetType> applicableFacets = baseType != null
            ? XsdDatatypeFacets.getApplicableFacets(baseType)
            : Set.of(XsdFacetType.values());

        // Update header with base type info
        if (baseType != null && existingFacet == null) {
            dialog.setHeaderText("Add facet for type: " + baseType);
        } else if (baseType != null) {
            dialog.setHeaderText("Edit facet for type: " + baseType);
        } else {
            dialog.setHeaderText(existingFacet == null ? "Add a new facet" : "Edit facet");
        }

        // Buttons
        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        // Content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        // Type ComboBox - filtered by applicable facets
        ComboBox<XsdFacetType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(applicableFacets);

        // Custom cell factory to show XML names and tooltips
        typeCombo.setCellFactory(lv -> new ListCell<XsdFacetType>() {
            @Override
            protected void updateItem(XsdFacetType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.getXmlName());

                    // Create tooltip with facet description
                    String tooltipText = getFacetDescription(item, baseType);
                    if (tooltipText != null) {
                        Tooltip tooltip = new Tooltip(tooltipText);
                        tooltip.setWrapText(true);
                        tooltip.setMaxWidth(300);
                        setTooltip(tooltip);
                    }
                }
            }
        });

        // Set button cell to show XML name
        typeCombo.setButtonCell(new ListCell<XsdFacetType>() {
            @Override
            protected void updateItem(XsdFacetType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getXmlName());
                }
            }
        });

        if (existingFacet != null) {
            typeCombo.setValue(existingFacet.getFacetType());
            typeCombo.setDisable(true); // Can't change type when editing
        }

        // Value field
        TextField valueField = new TextField();
        Label valueLabel = new Label("Value:");

        // Check if this facet has a fixed value for the base type
        boolean hasFixedValue = false;
        String fixedValue = null;
        if (existingFacet != null && baseType != null) {
            hasFixedValue = XsdDatatypeFacets.isFacetFixed(baseType, existingFacet.getFacetType());
            if (hasFixedValue) {
                fixedValue = XsdDatatypeFacets.getFixedFacetValue(baseType, existingFacet.getFacetType());
            }
        }

        if (existingFacet != null) {
            valueField.setText(existingFacet.getValue());

            // Make read-only if fixed
            if (hasFixedValue) {
                valueField.setEditable(false);
                valueField.setStyle("-fx-background-color: #f0f0f0;");
                valueLabel.setText("Value (fixed):");

                // Add tooltip explaining why it's fixed
                Tooltip fixedTooltip = new Tooltip(
                    "This facet has a fixed value (" + fixedValue + ") for type " + baseType +
                    " according to XSD specification and cannot be changed."
                );
                fixedTooltip.setWrapText(true);
                fixedTooltip.setMaxWidth(300);
                valueField.setTooltip(fixedTooltip);
            }
        }

        // Add listener to update value field when type changes (for add dialog)
        if (existingFacet == null) {
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && baseType != null) {
                    boolean isFixed = XsdDatatypeFacets.isFacetFixed(baseType, newVal);
                    if (isFixed) {
                        String value = XsdDatatypeFacets.getFixedFacetValue(baseType, newVal);
                        valueField.setText(value != null ? value : "");
                        valueField.setEditable(false);
                        valueField.setStyle("-fx-background-color: #f0f0f0;");
                        valueLabel.setText("Value (fixed):");

                        Tooltip fixedTooltip = new Tooltip(
                            "This facet has a fixed value for type " + baseType +
                            " according to XSD specification."
                        );
                        fixedTooltip.setWrapText(true);
                        fixedTooltip.setMaxWidth(300);
                        valueField.setTooltip(fixedTooltip);
                    } else {
                        valueField.setText("");
                        valueField.setEditable(true);
                        valueField.setStyle("");
                        valueLabel.setText("Value:");
                        valueField.setTooltip(null);
                    }
                }
            });
        }

        CheckBox fixedCheck = new CheckBox("Fixed (cannot be changed in derived types)");
        if (existingFacet != null) {
            fixedCheck.setSelected(existingFacet.isFixed());
        }

        grid.add(new Label("Type:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(valueLabel, 0, 1);
        grid.add(valueField, 1, 1);
        grid.add(fixedCheck, 0, 2, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert result
        dialog.setResultConverter(buttonType -> {
            if (buttonType == okButton) {
                XsdFacetType type = typeCombo.getValue();
                String value = valueField.getText().trim();
                boolean fixed = fixedCheck.isSelected();

                if (type != null && !value.isEmpty()) {
                    return new FacetInput(type, value, fixed);
                }
            }
            return null;
        });

        return dialog;
    }

    /**
     * Gets a description for a facet type.
     *
     * @param facetType the facet type
     * @param baseType the base type (for context)
     * @return description text
     */
    private String getFacetDescription(XsdFacetType facetType, String baseType) {
        String description = switch (facetType) {
            case LENGTH -> "Exact length of the value (for strings, binary types)";
            case MIN_LENGTH -> "Minimum length of the value";
            case MAX_LENGTH -> "Maximum length of the value";
            case PATTERN -> "Regular expression pattern that the value must match";
            case ENUMERATION -> "One of the allowed values (enumeration)";
            case WHITE_SPACE -> "Whitespace handling: preserve, replace, or collapse";
            case MAX_INCLUSIVE -> "Maximum value (inclusive)";
            case MAX_EXCLUSIVE -> "Maximum value (exclusive)";
            case MIN_INCLUSIVE -> "Minimum value (inclusive)";
            case MIN_EXCLUSIVE -> "Minimum value (exclusive)";
            case TOTAL_DIGITS -> "Maximum total number of digits";
            case FRACTION_DIGITS -> "Maximum number of decimal places";
            case ASSERTION -> "XPath 2.0 assertion (XSD 1.1)";
            case EXPLICIT_TIMEZONE -> "Timezone requirement: required, prohibited, or optional (XSD 1.1)";
        };

        // Add fixed value info if applicable
        if (baseType != null && XsdDatatypeFacets.isFacetFixed(baseType, facetType)) {
            String fixedValue = XsdDatatypeFacets.getFixedFacetValue(baseType, facetType);
            description += "\n\nFIXED for " + baseType + ": " + fixedValue;
        }

        return description;
    }

    /**
     * Finds a SimpleType by name in the schema.
     *
     * @param typeName the type name (e.g., "ISINType" or "xs:string")
     * @return the SimpleType, or null if not found
     */
    private XsdSimpleType findSimpleType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }

        // Remove namespace prefix if present
        String localName = typeName.contains(":") ? typeName.substring(typeName.indexOf(":") + 1) : typeName;

        // Don't try to resolve built-in XSD types
        if (typeName.startsWith("xs:") || isBuiltInType(localName)) {
            return null;
        }

        // Search in schema's children
        XsdSchema schema = editorContext.getSchema();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                if (localName.equals(simpleType.getName())) {
                    return simpleType;
                }
            }
        }

        return null;
    }

    /**
     * Checks if a type is a built-in XSD type.
     *
     * @param typeName the type name
     * @return true if built-in type
     */
    private boolean isBuiltInType(String typeName) {
        return Set.of("string", "normalizedString", "token", "language", "Name", "NCName",
                     "ID", "IDREF", "ENTITY", "NMTOKEN", "decimal", "integer", "long", "int",
                     "short", "byte", "float", "double", "boolean", "dateTime", "date", "time",
                     "duration", "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION",
                     "positiveInteger", "negativeInteger", "nonPositiveInteger", "nonNegativeInteger",
                     "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                     "dateTimeStamp", "yearMonthDuration", "dayTimeDuration",
                     "gYear", "gYearMonth", "gMonth", "gMonthDay", "gDay").contains(typeName);
    }

    /**
     * Extracts all facets from a SimpleType.
     *
     * @param simpleType the simple type
     * @return list of facets
     */
    private List<XsdFacet> extractFacetsFromSimpleType(XsdSimpleType simpleType) {
        List<XsdFacet> facets = new ArrayList<>();

        if (simpleType == null) {
            return facets;
        }

        // Look for restriction in children
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                // Get all facets from restriction
                facets.addAll(restriction.getFacets());
            }
        }

        return facets;
    }

    /**
     * Helper class for facet dialog input.
     */
    private static class FacetInput {
        final XsdFacetType type;
        final String value;
        final boolean fixed;

        FacetInput(XsdFacetType type, String value, boolean fixed) {
            this.type = type;
            this.value = value;
            this.fixed = fixed;
        }
    }
}
