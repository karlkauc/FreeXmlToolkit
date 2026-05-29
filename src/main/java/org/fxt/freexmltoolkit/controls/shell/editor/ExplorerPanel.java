package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

import java.io.File;

/**
 * The Explorer activity side panel (UI rebuild Phase 3): create/open files and
 * list the currently open documents. Drives the {@link EditorHost}.
 * <p>
 * The full workspace file tree (reusing {@code FileExplorer}) and recent files
 * arrive in a later increment; this gives working open/new + an "Open Editors"
 * list bound to the host.
 */
public class ExplorerPanel extends VBox {

    private final EditorHost editorHost;

    public ExplorerPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("EXPLORER");
        title.getStyleClass().add("fxt-side-panel-title");

        HBox actions = new HBox(8, iconButton("bi-file-earmark-plus", "New file", this::newFile),
                iconButton("bi-folder2-open", "Open file…", this::openFile));

        Label openEditors = new Label("OPEN EDITORS");
        openEditors.getStyleClass().add("fxt-side-panel-title");

        ListView<OpenDocument> list = new ListView<>(editorHost.getOpenDocuments());
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setCellFactory(lv -> new OpenDocumentCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                editorHost.selectDocument(newV);
            }
        });

        getChildren().addAll(title, actions, openEditors, list);
    }

    private void newFile() {
        editorHost.newDocument(EditorFileType.XML);
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    private Button iconButton(String icon, String tooltip, Runnable action) {
        Button button = new Button();
        button.getStyleClass().add("fxt-tool-button");
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** Renders an open document with its file-type icon and dirty marker. */
    private static final class OpenDocumentCell extends ListCell<OpenDocument> {
        @Override
        protected void updateItem(OpenDocument item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                textProperty().unbind();
                setText(null);
                setGraphic(null);
                return;
            }
            textProperty().unbind();
            IconifyIcon icon = new IconifyIcon(item.getFileType().icon());
            icon.setIconSize(14);
            setGraphic(icon);
            textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                    () -> (item.isDirty() ? "● " : "") + item.getDisplayName(),
                    item.dirtyProperty(), item.displayNameProperty()));
        }
    }
}
