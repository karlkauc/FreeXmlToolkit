package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * The Schema activity side panel: lists the active XSD's top-level named types
 * (Type Library). Selecting a type reveals it in the Tree view. Bound to the
 * {@link EditorHost}; refreshes on tab / view-mode changes.
 */
public class TypeLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<XsdNode> types = FXCollections.observableArrayList();

    public TypeLibraryPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SCHEMA");
        title.getStyleClass().add("fxt-side-panel-title");

        ListView<XsdNode> list = new ListView<>(types);
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No named types"));
        list.setCellFactory(lv -> new TypeCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getName() != null) {
                editorHost.revealTypeByName(newV.getName());
            }
        });

        javafx.scene.control.Button generateXsd = new javafx.scene.control.Button("Generate XSD from XML");
        generateXsd.getStyleClass().add("fxt-tool-button");
        IconifyIcon genIcon = new IconifyIcon("bi-magic");
        genIcon.setIconSize(16);
        generateXsd.setGraphic(genIcon);
        generateXsd.setOnAction(e -> generateXsdFromActive());

        Label typesLabel = new Label("TYPES");
        typesLabel.getStyleClass().add("fxt-side-panel-title");

        getChildren().addAll(title, new javafx.scene.layout.HBox(generateXsd), typesLabel, list);

        refresh();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refresh());
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> refresh());
    }

    /** Infers an XSD from the active XML document and opens it as a new tab (async). */
    public void generateXsdFromActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String xsd = SchemaActionRunner.generateXsdFromXml(xml);
            javafx.application.Platform.runLater(() -> {
                if (!xsd.startsWith("ERROR:")) {
                    editorHost.openGeneratedDocument(xsd, EditorFileType.XSD, "Generated.xsd");
                }
            });
        });
    }

    private void refresh() {
        types.setAll(editorHost.getActiveNamedTypes());
    }

    /** Renders a named type with its node-type icon. */
    private static final class TypeCell extends ListCell<XsdNode> {
        @Override
        protected void updateItem(XsdNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName());
            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(item.getNodeType()));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
