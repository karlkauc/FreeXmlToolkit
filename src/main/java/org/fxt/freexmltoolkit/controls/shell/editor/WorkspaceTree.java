package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import org.fxt.freexmltoolkit.controls.FileExplorerTreeItem;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Slim workspace file tree for the Explorer activity (UI rebuild Phase 3,
 * increment 4). Reuses the existing lazy-loading {@link FileExplorerTreeItem}
 * (with extension filtering) and opens a file via the supplied consumer on
 * double-click / Enter.
 */
public class WorkspaceTree extends VBox {

    private static final List<String> ALLOWED =
            List.of("xml", "xsd", "xsl", "xslt", "sch", "schematron", "json");

    private final TreeView<Path> tree = new TreeView<>();
    private final Consumer<Path> fileOpener;

    public WorkspaceTree(Consumer<Path> fileOpener) {
        this.fileOpener = fileOpener;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("WORKSPACE");
        title.getStyleClass().add("fxt-side-panel-title");

        Button openFolder = new Button();
        openFolder.getStyleClass().add("fxt-tool-button");
        IconifyIcon icon = new IconifyIcon("bi-folder2-open");
        icon.setIconSize(16);
        openFolder.setGraphic(icon);
        openFolder.setTooltip(new javafx.scene.control.Tooltip("Open folder…"));
        openFolder.setOnAction(e -> chooseFolder());

        HBox header = new HBox(8, title, openFolder);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        tree.getStyleClass().add("fxt-workspace-tree");
        tree.setShowRoot(true);
        tree.setCellFactory(tv -> new PathCell());
        VBox.setVgrow(tree, Priority.ALWAYS);

        tree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelected();
            }
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                openSelected();
            }
        });

        getChildren().addAll(header, tree);
    }

    /** Sets the workspace root folder and expands it. */
    public void setRootFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        FileExplorerTreeItem root = new FileExplorerTreeItem(folder, ALLOWED);
        root.setExpanded(true);
        tree.setRoot(root);
    }

    /** @return the current workspace root, or {@code null}. */
    public Path getRootFolder() {
        TreeItem<Path> root = tree.getRoot();
        return root != null ? root.getValue() : null;
    }

    /** Reveals and selects the given path in the tree, if present under the root. */
    public void selectPath(Path path) {
        if (tree.getRoot() instanceof FileExplorerTreeItem root) {
            TreeItem<Path> item = root.expandAndFind(path);
            if (item != null) {
                tree.getSelectionModel().select(item);
            }
        }
    }

    /** @return the file/folder names directly under the root (loads them lazily). */
    public List<String> listTopLevelNames() {
        TreeItem<Path> root = tree.getRoot();
        if (root == null) {
            return List.of();
        }
        return root.getChildren().stream()
                .map(TreeItem::getValue)
                .map(Path::getFileName)
                .filter(java.util.Objects::nonNull)
                .map(Path::toString)
                .toList();
    }

    /** Opens the selected tree item if it is a regular file. */
    public void openSelected() {
        TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null && Files.isRegularFile(selected.getValue())) {
            fileOpener.accept(selected.getValue());
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Folder");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            setRootFolder(dir.toPath());
        }
    }

    /** Renders a path with a folder/file-type icon. */
    private static final class PathCell extends TreeCell<Path> {
        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Path name = item.getFileName();
            setText(name != null ? name.toString() : item.toString());
            String iconLiteral = Files.isDirectory(item)
                    ? "bi-folder"
                    : EditorFileType.fromFileName(item.toString()).icon();
            IconifyIcon icon = new IconifyIcon(iconLiteral);
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
