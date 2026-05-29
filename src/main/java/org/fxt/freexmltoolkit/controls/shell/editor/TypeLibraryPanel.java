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

        javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(4,
                actionButton("Generate XSD from XML", "bi-magic", this::generateXsdFromActive),
                actionButton("Flatten Schema", "bi-layers", this::flattenActive),
                actionButton("Statistics", "bi-bar-chart", this::statisticsActive));

        Label typesLabel = new Label("TYPES");
        typesLabel.getStyleClass().add("fxt-side-panel-title");

        getChildren().addAll(title, actions, typesLabel, list);

        refresh();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refresh());
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> refresh());
    }

    /** Infers an XSD from the active XML document and opens it as a new tab (async). */
    public void generateXsdFromActive() {
        runAsync(SchemaActionRunner::generateXsdFromXml, EditorFileType.XSD, "Generated.xsd");
    }

    /** Flattens the active XSD (resolves includes) and opens the result (async). */
    public void flattenActive() {
        var doc = editorHost.getActiveDocument();
        java.nio.file.Path baseDir = doc.map(OpenDocument::getPath)
                .map(java.nio.file.Path::getParent).orElse(null);
        runAsync(content -> SchemaActionRunner.flatten(content, baseDir),
                EditorFileType.XSD, "Flattened.xsd");
    }

    /** Collects statistics for the active XSD and opens a text report (async). */
    public void statisticsActive() {
        runAsync(SchemaActionRunner::statistics, EditorFileType.OTHER, "Statistics.txt");
    }

    private void runAsync(java.util.function.Function<String, String> action,
                          EditorFileType outputType, String outputName) {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result = action.apply(content);
            javafx.application.Platform.runLater(() -> {
                if (!result.startsWith("ERROR:")) {
                    editorHost.openGeneratedDocument(result, outputType, outputName);
                }
            });
        });
    }

    private javafx.scene.control.Button actionButton(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        javafx.scene.control.Button button = new javafx.scene.control.Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
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
