package org.fxt.freexmltoolkit.controls.v2.editor.dialogs;

import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.theme.SemanticStyle;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageLocation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Dialog displaying all locations where a type is used in the schema.
 * Shows a TableView with columns for node name, reference type, path, and file.
 * Supports navigation to usage locations via double-click or context menu.
 *
 * @since 2.0
 */
public class TypeUsageDialog extends Dialog<TypeUsageLocation> {

    private static final Logger logger = LogManager.getLogger(TypeUsageDialog.class);

    private final String typeName;
    private final List<TypeUsageLocation> usages;
    @FXML private VBox content;
    @FXML private Label headerLabel;
    @FXML private VBox noUsagesBox;
    @FXML private IconifyIcon checkIcon;
    @FXML private TableView<TypeUsageLocation> tableView;
    @FXML private TableColumn<TypeUsageLocation, String> nodeNameCol;
    @FXML private TableColumn<TypeUsageLocation, String> nodeTypeCol;
    @FXML private TableColumn<TypeUsageLocation, String> refTypeCol;
    @FXML private TableColumn<TypeUsageLocation, String> pathCol;
    @FXML private TableColumn<TypeUsageLocation, String> fileCol;
    @FXML private Label helpLabel;
    private Consumer<XsdNode> onNavigateToNode;

    /**
     * Creates a new type usage dialog.
     *
     * @param typeName the name of the type being searched
     * @param usages   the list of usage locations found
     */
    public TypeUsageDialog(String typeName, List<TypeUsageLocation> usages) {
        this.typeName = typeName;
        this.usages = usages;

        initializeDialog();
    }

    /**
     * Sets the callback for navigating to a node when selected.
     *
     * @param onNavigateToNode callback that receives the node to navigate to
     */
    public void setOnNavigateToNode(Consumer<XsdNode> onNavigateToNode) {
        this.onNavigateToNode = onNavigateToNode;
    }

    private void initializeDialog() {
        setTitle("Find Usages: " + typeName);
        initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setPrefWidth(700);
        dialogPane.setPrefHeight(450);

        // Load CSS
        try {
            dialogPane.getStylesheets().addAll(
                    getClass().getResource("/css/app-theme.css").toExternalForm(),
                    getClass().getResource("/css/dialog-theme.css").toExternalForm()
            );
        } catch (Exception e) {
            logger.warn("Could not load dialog theme CSS", e);
        }

        // Content: header, then the table with its help line or the "no usages" message
        dialogPane.setContent(FxmlViews.loadContent(this, "/pages/dialogs/TypeUsageDialog.fxml"));
        if (usages.isEmpty()) {
            headerLabel.setText("No usages found for type '" + typeName + "'");
            SemanticStyle.style(headerLabel, DesignTokens.ColorToken.SUCCESS, c -> "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + c + ";");
            SemanticIcon.paint(checkIcon, DesignTokens.ColorToken.SUCCESS);
            content.getChildren().removeAll(tableView, helpLabel);
        } else {
            headerLabel.setText(usages.size() + " usage" + (usages.size() == 1 ? "" : "s") + " of type '" + typeName + "'");
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            content.getChildren().remove(noUsagesBox);
            configureTableView();
        }

        // Buttons
        dialogPane.getButtonTypes().addAll(ButtonType.CLOSE);

        // Result converter - return selected usage on close
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.CLOSE) {
                return tableView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
    }

    private void configureTableView() {
        nodeNameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNodeName()));
        nodeTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNodeTypeName()));
        refTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().referenceType().getDisplayName()));
        pathCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPath()));
        fileCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSourceFileName()));

        tableView.setItems(FXCollections.observableArrayList(usages));
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    /** Double-click on a row navigates to the usage location. */
    @FXML
    public void onTableClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            onNavigate();
        }
    }

    /** Navigates to the selected usage and closes the dialog. */
    @FXML
    public void onNavigate() {
        TypeUsageLocation selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null && onNavigateToNode != null) {
            onNavigateToNode.accept(selected.node());
            close();
        }
    }

    @FXML
    public void onCopyPath() {
        TypeUsageLocation selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selected.getPath());
            clipboard.setContent(content);
        }
    }
}
