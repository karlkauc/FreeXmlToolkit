package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * The editor empty-state landing shown when no document is open (e.g. on boot
 * into the Unified Shell). Offers quick "new document" actions, an "open file"
 * action and a clickable list of recent files — the in-editor parity for the
 * welcome dashboard that booting into the shell replaces.
 */
public class EditorWelcomePane extends VBox {

    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();

    /**
     * @param onNew        invoked with the chosen file type for a "new document" action
     * @param onOpen       invoked for the "open file" action (shows a file chooser)
     * @param onOpenRecent invoked with a recent file when the user selects it
     */
    public EditorWelcomePane(Consumer<EditorFileType> onNew, Runnable onOpen, Consumer<File> onOpenRecent) {
        getStyleClass().add("fxt-editor-empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(20);
        setPadding(new Insets(48));

        Label title = new Label("FreeXmlToolkit");
        title.getStyleClass().add("fxt-welcome-title");
        Label subtitle = new Label("Open a file or create a new document to get started.");
        subtitle.getStyleClass().add("fxt-welcome-subtitle");

        FlowPane actions = new FlowPane(12, 12);
        actions.setAlignment(Pos.CENTER);
        actions.getChildren().addAll(
                newButton("New XML", EditorFileType.XML, onNew),
                newButton("New XSD", EditorFileType.XSD, onNew),
                newButton("New JSON", EditorFileType.JSON, onNew),
                openButton(onOpen));

        Label recentLabel = new Label("RECENT");
        recentLabel.getStyleClass().add("fxt-side-panel-title");
        VBox.setMargin(recentLabel, new Insets(16, 0, 0, 0));

        ListView<File> recentList = new ListView<>(recentFiles);
        recentList.getStyleClass().add("fxt-welcome-recent");
        recentList.setMaxWidth(520);
        recentList.setPrefHeight(180);
        recentList.setCellFactory(lv -> new RecentCell());
        recentList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                onOpenRecent.accept(newV);
                recentList.getSelectionModel().clearSelection();
            }
        });

        getChildren().addAll(title, subtitle, actions, recentLabel, recentList);
    }

    /** Replaces the recent-files list (most-recent first). */
    public void setRecentFiles(List<File> files) {
        recentFiles.setAll(files);
    }

    private static Button newButton(String text, EditorFileType type, Consumer<EditorFileType> onNew) {
        Button button = new Button(text, new IconifyIcon(type.icon()));
        button.getStyleClass().addAll("fxt-welcome-action");
        button.setOnAction(e -> onNew.accept(type));
        return button;
    }

    private static Button openButton(Runnable onOpen) {
        Button button = new Button("Open File…", new IconifyIcon("bi-folder2-open"));
        button.getStyleClass().addAll("fxt-welcome-action");
        button.setOnAction(e -> onOpen.run());
        return button;
    }

    /** Renders a recent file with its file-type icon, name and parent path. */
    private static final class RecentCell extends ListCell<File> {
        private final Label name = new Label();
        private final Label path = new Label();
        private final IconifyIcon icon = new IconifyIcon();
        private final javafx.scene.layout.HBox row;

        RecentCell() {
            name.getStyleClass().add("fxt-recent-name");
            path.getStyleClass().add("fxt-recent-path");
            icon.setIconSize(16);
            row = new javafx.scene.layout.HBox(10, icon, new VBox(2, name, path));
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                icon.setIconLiteral(EditorFileType.fromFileName(item.getName()).icon());
                name.setText(item.getName());
                path.setText(item.getParent() == null ? "" : item.getParent());
                setGraphic(row);
            }
        }
    }
}
