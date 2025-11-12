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
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;

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

    private TableView<XsdFacet> facetsTable;
    private Button addButton;
    private Button editButton;
    private Button deleteButton;

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

        // Value column
        TableColumn<XsdFacet, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
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
        getChildren().addAll(titleLabel, facetsTable, buttonBox);

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

        if (restriction == null) {
            facetsTable.setItems(FXCollections.observableArrayList());
            setDisable(true);
            logger.debug("Cleared facets panel");
        } else {
            ObservableList<XsdFacet> facets = FXCollections.observableArrayList(restriction.getFacets());
            facetsTable.setItems(facets);
            setDisable(!editorContext.isEditMode());
            logger.debug("Loaded {} facets for restriction (base: {})", facets.size(), restriction.getBase());
        }
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
        if (currentRestriction == null) {
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
        dialog.setHeaderText(existingFacet == null ? "Add a new facet" : "Edit facet");

        // Buttons
        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        // Content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ComboBox<XsdFacetType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(XsdFacetType.values());
        if (existingFacet != null) {
            typeCombo.setValue(existingFacet.getFacetType());
            typeCombo.setDisable(true); // Can't change type when editing
        }

        TextField valueField = new TextField();
        if (existingFacet != null) {
            valueField.setText(existingFacet.getValue());
        }

        CheckBox fixedCheck = new CheckBox("Fixed (cannot be changed in derived types)");
        if (existingFacet != null) {
            fixedCheck.setSelected(existingFacet.isFixed());
        }

        grid.add(new Label("Type:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Value:"), 0, 1);
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
