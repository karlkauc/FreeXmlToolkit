package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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

        // Full-bleed per the Figma Explorer mockup (28:48): the panel owns the
        // section header (the workspace folder's name) and the folder actions.
        tree.getStyleClass().add("fxt-workspace-tree");
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new PathCell());
        // Allow selecting several files at once (Ctrl/Shift) for batch actions (e.g. batch transform).
        tree.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
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

        getChildren().add(tree);
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

    /** Re-reads the current root folder from disk (no-op without a root). */
    public void refresh() {
        TreeItem<Path> root = tree.getRoot();
        if (root != null) {
            setRootFolder(root.getValue());
        }
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

    /** @return the currently selected entries that are regular files (empty if none). */
    public List<Path> getSelectedFiles() {
        return tree.getSelectionModel().getSelectedItems().stream()
                .filter(java.util.Objects::nonNull)
                .map(TreeItem::getValue)
                .filter(java.util.Objects::nonNull)
                .filter(Files::isRegularFile)
                .toList();
    }

    /** Opens the selected tree item if it is a regular file. */
    public void openSelected() {
        TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null && Files.isRegularFile(selected.getValue())) {
            fileOpener.accept(selected.getValue());
        }
    }

    /**
     * Renders a path: folders show just their name next to the disclosure chevron
     * (no folder icon, per the mockup), files show their file-type icon.
     */
    private static final class PathCell extends TreeCell<Path> {
        private PathCell() {
            // Follow the TreeView width instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

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
            if (Files.isDirectory(item)) {
                setGraphic(null);
                if (!getStyleClass().contains("fxt-tree-folder")) {
                    getStyleClass().add("fxt-tree-folder");
                }
                return;
            }
            getStyleClass().remove("fxt-tree-folder");
            EditorFileType type = EditorFileType.fromFileName(item.toString());
            IconifyIcon icon = new IconifyIcon(type.icon());
            icon.setIconSize(15);
            // Bind (not set) the per-type color so the tree-cell CSS -fx-icon-color can't override it.
            try {
                icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                        javafx.scene.paint.Color.web(type.color())));
            } catch (Exception ignored) {
                // unparsable color: keep the default icon color
            }
            setGraphic(icon);
        }
    }
}
