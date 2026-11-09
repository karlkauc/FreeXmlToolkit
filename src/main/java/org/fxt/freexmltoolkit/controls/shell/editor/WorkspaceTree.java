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
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Slim workspace file tree for the Explorer activity.
 * Reuses the existing lazy-loading {@link FileExplorerTreeItem}
 * (with extension filtering) and opens a file via the supplied consumer on
 * double-click / Enter.
 */
public class WorkspaceTree extends VBox {

    /** Extensions (without dot) shown in the tree — every type the editor host can open, and project files. */
    private static final List<String> ALLOWED = java.util.stream.Stream.concat(
                    EditorFileType.openableExtensions().stream().map(ext -> ext.substring(1)),
                    java.util.stream.Stream.of(org.fxt.freexmltoolkit.service.project.FxtProject.EXTENSION))
            .toList();

    private final TreeView<Path> tree = new TreeView<>();
    private final Consumer<Path> fileOpener;
    /** Shown instead of the (empty) tree while no folder is open: says what to do next. */
    private final VBox emptyState = new VBox(8);
    private Runnable onOpenFolder;
    /** The folders shown (several = a project with several folders, each a top-level node). */
    private List<Path> rootFolders = List.of();
    /** A file's note (what a project assigns to it) for its tooltip, or {@code null}. */
    private java.util.function.Function<Path, String> fileNote = path -> null;

    public WorkspaceTree(Consumer<Path> fileOpener) {
        this.fileOpener = fileOpener;

        // Full-bleed per the Figma Explorer mockup (28:48): the panel owns the
        // section header (the workspace folder's name) and the folder actions.
        tree.getStyleClass().add("fxt-workspace-tree");
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new PathCell(this));
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
                I18n.t("WorkspaceTree.no-folder-open-open-a"));
        hint.setWrapText(true);
        hint.getStyleClass().add("fxt-placeholder-text");
        javafx.scene.control.Button open = PanelActionList.inlineRow(I18n.t("WorkspaceTree.open-folder"), "bi-folder2-open", () -> {
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

    /** Runs {@code listener} whenever the tree selection changes (e.g. to update enable states). */
    public void addSelectionListener(Runnable listener) {
        tree.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<TreeItem<Path>>) c -> listener.run());
    }

    /** @return whether any tree item is selected (no file-system access, cheap for enable states) */
    public boolean hasSelection() {
        return !tree.getSelectionModel().getSelectedItems().isEmpty();
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
        setRootFolders(List.of(folder));
    }

    /**
     * Shows several folders: one folder is the tree's (hidden) root as before; several become
     * top-level nodes below a hidden root. Missing folders are left out.
     */
    public void setRootFolders(List<Path> folders) {
        List<Path> existing = folders.stream().filter(java.util.Objects::nonNull).filter(Files::isDirectory)
                .distinct().toList();
        if (existing.isEmpty()) {
            return;
        }
        rootFolders = existing;
        if (existing.size() == 1) {
            // Folders are listed off the FX thread as they are expanded (large and network folders froze the UI)
            FileExplorerTreeItem root = FileExplorerTreeItem.withBackgroundListing(existing.get(0), ALLOWED);
            root.setExpanded(true);
            tree.setRoot(root);
        } else {
            TreeItem<Path> root = new TreeItem<>(existing.get(0));
            for (Path folder : existing) {
                FileExplorerTreeItem item = FileExplorerTreeItem.withBackgroundListing(folder, ALLOWED);
                item.setExpanded(true);
                root.getChildren().add(item);
            }
            root.setExpanded(true);
            tree.setRoot(root);
        }
        showEmptyState(false);
    }

    /** Sets what a file's tooltip says (e.g. what the open project assigns to it); {@code null} = nothing. */
    public void setFileNote(java.util.function.Function<Path, String> note) {
        this.fileNote = note == null ? path -> null : note;
        tree.refresh();
    }

    /** Re-reads the current root folders from disk (no-op without a root). */
    public void refresh() {
        if (!rootFolders.isEmpty()) {
            setRootFolders(rootFolders);
        }
    }

    /** @return the current workspace root (the first folder), or {@code null}. */
    public Path getRootFolder() {
        return rootFolders.isEmpty() ? null : rootFolders.get(0);
    }

    /** @return the folders shown at the top level */
    public List<Path> getRootFolders() {
        return rootFolders;
    }

    /** Reveals and selects the given path in the tree, if present under one of the roots. */
    public void selectPath(Path path) {
        TreeItem<Path> item = null;
        if (rootFolders.size() > 1 && tree.getRoot() != null) {
            for (TreeItem<Path> child : tree.getRoot().getChildren()) {
                if (child instanceof FileExplorerTreeItem folder
                        && path.toAbsolutePath().normalize().startsWith(folder.getValue().toAbsolutePath().normalize())) {
                    item = folder.expandAndFind(path);
                    if (item != null) {
                        break;
                    }
                }
            }
        } else if (tree.getRoot() instanceof FileExplorerTreeItem root) {
            item = root.expandAndFind(path);
        }
        if (item != null) {
            tree.getSelectionModel().select(item);
            int row = tree.getRow(item);
            if (row >= 0) {
                tree.scrollTo(row);
            }
        }
    }

    /** @return the file/folder names directly under the root (loads them lazily); with several folders, their names. */
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
        private final WorkspaceTree owner;

        private PathCell(WorkspaceTree owner) {
            this.owner = owner;
            // Follow the TreeView width instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            Path name = item.getFileName();
            setText(name != null ? name.toString() : item.toString());
            // the kind is known from the folder listing: no disk access per rendered (or scrolled) row
            boolean directory = getTreeItem() instanceof FileExplorerTreeItem entry
                    ? entry.isDirectoryItem() : Files.isDirectory(item);
            if (directory) {
                setTooltip(null);
                setGraphic(null);
                if (!getStyleClass().contains("fxt-tree-folder")) {
                    getStyleClass().add("fxt-tree-folder");
                }
                return;
            }
            getStyleClass().remove("fxt-tree-folder");
            String note = owner.fileNote.apply(item);
            setTooltip(note == null || note.isBlank() ? null : new javafx.scene.control.Tooltip(note));
            if (org.fxt.freexmltoolkit.service.project.FxtProject.isProjectFile(item)) {
                IconifyIcon project = new IconifyIcon("bi-briefcase");
                project.setIconSize(15);
                setGraphic(project);
                return;
            }
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
