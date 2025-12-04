package org.fxt.freexmltoolkit.controls.v2.editor.dialogs;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageLocation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

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
    private final TableView<TypeUsageLocation> tableView;
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
        this.tableView = new TableView<>();

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
            dialogPane.getStylesheets().add(
                    getClass().getResource("/css/dialog-theme.css").toExternalForm()
            );
        } catch (Exception e) {
            logger.warn("Could not load dialog theme CSS", e);
        }

        // Content
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Header
        HBox headerBox = createHeader();
        content.getChildren().add(headerBox);

        // TableView or "No usages" message
        if (usages.isEmpty()) {
            content.getChildren().add(createNoUsagesMessage());
        } else {
            configureTableView();
            VBox.setVgrow(tableView, Priority.ALWAYS);
            content.getChildren().add(tableView);
            content.getChildren().add(createHelpText());
        }

        dialogPane.setContent(content);

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

    private HBox createHeader() {
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(BootstrapIcons.SEARCH);
        icon.setIconSize(20);
        icon.setIconColor(Color.DODGERBLUE);

        Label headerLabel = new Label();
        if (usages.isEmpty()) {
            headerLabel.setText("No usages found for type '" + typeName + "'");
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #28a745;");
        } else {
            headerLabel.setText(usages.size() + " usage" + (usages.size() == 1 ? "" : "s") + " of type '" + typeName + "'");
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        }

        headerBox.getChildren().addAll(icon, headerLabel);
        return headerBox;
    }

    private VBox createNoUsagesMessage() {
        VBox messageBox = new VBox(10);
        messageBox.setAlignment(Pos.CENTER);
        messageBox.setPadding(new Insets(40));

        FontIcon checkIcon = new FontIcon(BootstrapIcons.CHECK_CIRCLE);
        checkIcon.setIconSize(48);
        checkIcon.setIconColor(Color.web("#28a745"));

        Label messageLabel = new Label("This type is not used anywhere in the schema.\nIt can be safely deleted.");
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-alignment: center;");
        messageLabel.setWrapText(true);

        messageBox.getChildren().addAll(checkIcon, messageLabel);
        return messageBox;
    }

    private Label createHelpText() {
        Label helpLabel = new Label("Double-click a row to navigate to the usage location");
        helpLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        return helpLabel;
    }

    @SuppressWarnings("unchecked")
    private void configureTableView() {
        // Node Name column
        TableColumn<TypeUsageLocation, String> nodeNameCol = new TableColumn<>("Node");
        nodeNameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNodeName()));
        nodeNameCol.setPrefWidth(150);

        // Node Type column
        TableColumn<TypeUsageLocation, String> nodeTypeCol = new TableColumn<>("Type");
        nodeTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNodeTypeName()));
        nodeTypeCol.setPrefWidth(100);

        // Reference Type column
        TableColumn<TypeUsageLocation, String> refTypeCol = new TableColumn<>("Reference");
        refTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().referenceType().getDisplayName()));
        refTypeCol.setPrefWidth(120);

        // Path column
        TableColumn<TypeUsageLocation, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPath()));
        pathCol.setPrefWidth(200);

        // File column
        TableColumn<TypeUsageLocation, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSourceFileName()));
        fileCol.setPrefWidth(100);

        tableView.getColumns().addAll(nodeNameCol, nodeTypeCol, refTypeCol, pathCol, fileCol);
        tableView.setItems(FXCollections.observableArrayList(usages));

        // Selection mode
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Double-click handler
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TypeUsageLocation selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null && onNavigateToNode != null) {
                    onNavigateToNode.accept(selected.node());
                    close();
                }
            }
        });

        // Context menu
        ContextMenu contextMenu = new ContextMenu();

        MenuItem navigateItem = new MenuItem("Navigate to Node");
        navigateItem.setGraphic(new FontIcon(BootstrapIcons.ARROW_RIGHT_CIRCLE));
        navigateItem.setOnAction(e -> {
            TypeUsageLocation selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && onNavigateToNode != null) {
                onNavigateToNode.accept(selected.node());
                close();
            }
        });

        MenuItem copyPathItem = new MenuItem("Copy Path");
        copyPathItem.setGraphic(new FontIcon(BootstrapIcons.CLIPBOARD));
        copyPathItem.setOnAction(e -> {
            TypeUsageLocation selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(selected.getPath());
                clipboard.setContent(content);
            }
        });

        contextMenu.getItems().addAll(navigateItem, new SeparatorMenuItem(), copyPathItem);
        tableView.setContextMenu(contextMenu);

        // Placeholder for empty table
        tableView.setPlaceholder(new Label("No usages found"));
    }
}
