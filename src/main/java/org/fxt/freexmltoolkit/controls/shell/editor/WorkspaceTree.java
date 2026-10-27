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
 * Slim workspace file tree for the Explorer activity.
 * Reuses the existing lazy-loading {@link FileExplorerTreeItem}
 * (with extension filtering) and opens a file via the supplied consumer on
 * double-click / Enter.
 */
public class WorkspaceTree extends VBox {

    /** Extensions (without dot) shown in the tree — every type the editor host can open. */
    private static final List<String> ALLOWED = EditorFileType.openableExtensions().stream()
            .map(ext -> ext.substring(1))
            .toList();

    private final TreeView<Path> tree = new TreeView<>();
    private final Consumer<Path> fileOpener;
    /** Shown instead of the (empty) tree while no folder is open: says what to do next. */
    private final VBox emptyState = new VBox(8);
    private Runnable onOpenFolder;

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

        javafx.scene.control.Label hint = new javafx.scene.control.Label(
                "No folder open. Open a folder to browse its XML, XSD and stylesheet files here.");
        hint.setWrapText(true);
        hint.getStyleClass().add("fxt-placeholder-text");
        javafx.scene.control.Button open = PanelActionList.inlineRow("Open folder…", "bi-folder2-open", () -> {
            if (onOpenFolder != null) {
                onOpenFolder.run();
            }
        });
        open.setId("explorer-workspace-open-folder");
        emptyState.getChildren().addAll(hint, open);
        emptyState.getStyleClass().add("fxt-workspace-empty");

        getChildren().addAll(emptyState, tree);
        showEmptyState(true);
    }

    /** Sets the context menu offered on the files of the tree (e.g. transform / validate the selection). */
    public void setFileContextMenu(javafx.scene.control.ContextMenu menu) {
        tree.setContextMenu(menu);
    }

    /** Sets the action of the empty state's "Open folder…" button. */
    public void setOnOpenFolder(Runnable action) {
        this.onOpenFolder = action;
    }

    /** @return whether the "no folder open" hint is currently shown (for tests) */
    boolean isEmptyStateShown() {
        return emptyState.isVisible();
    }

    private void showEmptyState(boolean empty) {
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        tree.setVisible(!empty);
        tree.setManaged(!empty);
    }

    /** Sets the workspace root folder and expands it. */
    public void setRootFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        // Folders are listed off the FX thread as they are expanded (large and network folders froze the UI)
        FileExplorerTreeItem root = FileExplorerTreeItem.withBackgroundListing(folder, ALLOWED);
        root.setExpanded(true);
        tree.setRoot(root);
        showEmptyState(false);
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
                int row = tree.getRow(item);
                if (row >= 0) {
                    tree.scrollTo(row);
                }
            }
        }
    }

    /** @return the file/folder names directly under the root (loads them lazily). */
    public List<String> listTopLevelNames() {
        TreeItem<Path> root = tree.getRoot();
        if (root == null) {
            return List.of();
        }
        var children = root instanceof FileExplorerTreeItem item ? item.loadChildrenNow() : root.getChildren();
        return children.stream()
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
            // the kind is known from the folder listing: no disk access per rendered (or scrolled) row
            boolean directory = getTreeItem() instanceof FileExplorerTreeItem entry
                    ? entry.isDirectoryItem() : Files.isDirectory(item);
            if (directory) {
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
