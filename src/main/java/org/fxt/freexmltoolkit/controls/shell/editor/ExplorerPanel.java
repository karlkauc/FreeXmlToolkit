package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The Explorer activity side panel (UI rebuild Phase 3, increment 4): a workspace
 * file tree, the list of open documents, and recent files. Drives the
 * {@link EditorHost} and reuses {@link PropertiesService} for recent files.
 */
public class ExplorerPanel extends VBox {

    private final EditorHost editorHost;
    private final PropertiesService propertiesService = resolvePropertiesService();
    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();
    private final ListView<File> recentList = new ListView<>(recentFiles);

    public ExplorerPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("EXPLORER");
        title.getStyleClass().add("fxt-side-panel-title");

        HBox actions = new HBox(8,
                iconButton("bi-file-earmark-plus", "New file", this::newFile),
                iconButton("bi-folder2-open", "Open file…", this::openFile));

        WorkspaceTree workspace = new WorkspaceTree(editorHost::openFile);
        VBox.setVgrow(workspace, Priority.ALWAYS);

        Label openEditors = new Label("OPEN EDITORS");
        openEditors.getStyleClass().add("fxt-side-panel-title");
        ListView<OpenDocument> openList = new ListView<>(editorHost.getOpenDocuments());
        openList.getStyleClass().add("fxt-open-editors");
        openList.setPrefHeight(120);
        openList.setCellFactory(lv -> new OpenDocumentCell());
        openList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                editorHost.selectDocument(newV);
            }
        });

        Label recentLabel = new Label("RECENT");
        recentLabel.getStyleClass().add("fxt-side-panel-title");
        recentList.getStyleClass().add("fxt-open-editors");
        recentList.setPrefHeight(100);
        recentList.setCellFactory(lv -> new RecentFileCell());
        recentList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.isFile()) {
                editorHost.openFile(newV.toPath());
            }
        });

        getChildren().addAll(title, actions, workspace, openEditors, openList, recentLabel, recentList);

        // Track recent files as documents open.
        refreshRecent();
        editorHost.getOpenDocuments().addListener((javafx.collections.ListChangeListener<OpenDocument>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (OpenDocument doc : c.getAddedSubList()) {
                        rememberRecent(doc);
                    }
                }
            }
        });
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

    private void rememberRecent(OpenDocument doc) {
        if (propertiesService == null || doc.getPath() == null) {
            return;
        }
        propertiesService.addLastOpenFile(doc.getPath().toFile());
        refreshRecent();
    }

    private void refreshRecent() {
        if (propertiesService != null) {
            recentList.getSelectionModel().clearSelection();
            recentFiles.setAll(propertiesService.getLastOpenFiles());
        }
    }

    private static PropertiesService resolvePropertiesService() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            return null; // not available (e.g. in isolated tests)
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

    /** Renders a recent file with its file-type icon and name. */
    private static final class RecentFileCell extends ListCell<File> {
        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName());
            IconifyIcon icon = new IconifyIcon(EditorFileType.fromFileName(item.getName()).icon());
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
