/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileExplorerTreeItem extends TreeItem<Path> {

    private static final Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);
    private boolean isLeaf;
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;

    // Feld für das Caching der Anzahl der Unterverzeichnisse.
    // -1 bedeutet "noch nicht berechnet".
    private long subdirectoryCount = -1;

    private final List<String> allowedExtensions;

    public FileExplorerTreeItem(Path value) {
        this(value, null);
    }

    public FileExplorerTreeItem(Path value, List<String> allowedExtensions) {
        super(value);
        this.allowedExtensions = allowedExtensions;
    }

    /**
     * Expands the tree view down to the specified target path and returns the corresponding TreeItem.
     * This method ensures that all parent directories of the target path are expanded.
     *
     * @param path The full path of the item to find and reveal.
     * @return The TreeItem for the specified path, or null if not found.
     */
    public TreeItem<Path> expandAndFind(Path path) {
        // 1. Check if the target path is a descendant of the current item's path. If not, we can stop.
        if (!path.startsWith(getValue())) {
            return null;
        }

        // 2. If this is the exact item, we found it.
        if (getValue().equals(path)) {
            return this;
        }

        // 3. If it's a directory, we need to expand it and search its children.
        if (Files.isDirectory(getValue())) {
            // Ensure children are loaded for the lazy-loading mechanism by calling getChildren().
            getChildren();

            // Recursively search in the children.
            for (TreeItem<Path> child : super.getChildren()) {
                if (child instanceof FileExplorerTreeItem) {
                    TreeItem<Path> foundItem = ((FileExplorerTreeItem) child).expandAndFind(path);
                    if (foundItem != null) {
                        // If a descendant was found, expand the current node and return the found item.
                        this.setExpanded(true);
                        return foundItem;
                    }
                }
            }
        }

        // 4. Not found in any child branch or it's a leaf that doesn't match.
        return null;
    }


    public long getSubdirectoryCount() {
        if (this.subdirectoryCount == -1) { // Nur beim ersten Mal berechnen
            Path path = getValue();
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    // Zähle nur die Elemente im Stream, die Verzeichnisse sind.
                    this.subdirectoryCount = stream.filter(Files::isDirectory).count();
                } catch (IOException e) {
                    logger.info("Could not count subdirectories in: {}", path, e);
                    this.subdirectoryCount = 0; // Im Fehlerfall ist die Anzahl 0.
                }
            } else {
                this.subdirectoryCount = 0; // Dateien haben 0 Unterverzeichnisse.
            }
        }
        return this.subdirectoryCount;
    }

    /**
     * Refreshes the children of this tree item by reloading the directory contents.
     * This is useful when files have been added, removed, or modified in the directory.
     * Only expanded nodes are refreshed to avoid unnecessary file system access.
     */
    public void refresh() {
        Path path = getValue();
        if (path == null || !Files.isDirectory(path)) {
            return;
        }

        // Only refresh if children have been loaded before
        if (!isFirstTimeChildren) {
            // Reset the subdirectory count cache
            this.subdirectoryCount = -1;

            // Get the current set of child paths for comparison
            var currentChildPaths = new java.util.HashSet<Path>();
            for (TreeItem<Path> child : super.getChildren()) {
                currentChildPaths.add(child.getValue());
            }

            // Build new children list
            var newChildren = buildChildren(this);
            var newChildPaths = new java.util.HashSet<Path>();
            for (TreeItem<Path> child : newChildren) {
                newChildPaths.add(child.getValue());
            }

            // Only update if there are actual changes
            if (!currentChildPaths.equals(newChildPaths)) {
                logger.debug("Directory contents changed, refreshing: {}", path);
                super.getChildren().setAll(newChildren);
            }

            // Recursively refresh expanded children
            for (TreeItem<Path> child : super.getChildren()) {
                if (child instanceof FileExplorerTreeItem fileExplorerChild && child.isExpanded()) {
                    fileExplorerChild.refresh();
                }
            }
        }
    }

    /**
     * Forces a full refresh of this tree item and all its children,
     * regardless of their expanded state. Use with caution as this can be slow.
     */
    public void forceRefresh() {
        Path path = getValue();
        if (path == null || !Files.isDirectory(path)) {
            return;
        }

        // Reset state flags
        this.isFirstTimeChildren = false;
        this.subdirectoryCount = -1;
        this.isFirstTimeLeaf = true;

        // Rebuild children
        super.getChildren().setAll(buildChildren(this));

        // Recursively force refresh all children
        for (TreeItem<Path> child : super.getChildren()) {
            if (child instanceof FileExplorerTreeItem fileExplorerChild) {
                if (Files.isDirectory(child.getValue())) {
                    fileExplorerChild.forceRefresh();
                }
            }
        }
    }

    @Override
    public ObservableList<TreeItem<Path>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            super.getChildren().setAll(buildChildren(this));
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        if (isFirstTimeLeaf) {
            isFirstTimeLeaf = false;
            Path path = getValue();

            if (Files.isRegularFile(path)) {
                isLeaf = true;
            } else if (Files.isDirectory(path)) {
                // Ein Verzeichnis ist ein "Blatt", wenn es KEINE Kinder enthält, die angezeigt würden.
                // Ein Kind wird angezeigt, wenn es ein Verzeichnis ist ODER eine Datei, die dem Filter entspricht.
                try (var stream = Files.list(path)) {
                    isLeaf = stream.noneMatch(p -> {
                        if (Files.isDirectory(p)) {
                            return true; // Hat ein Unterverzeichnis, ist also kein Blatt.
                        }
                        // Es ist eine Datei, also gegen den Filter prüfen.
                        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
                            return true; // Kein Filter, also wird die Datei angezeigt -> kein Blatt.
                        }
                        String extension = FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase();
                        return allowedExtensions.contains(extension); // Datei entspricht Filter -> kein Blatt.
                    });
                } catch (IOException e) {
                    logger.info("Could not check directory content for: {}", path, e);
                    isLeaf = true; // Im Fehlerfall als Blatt behandeln.
                }
            } else {
                // Sollte nicht vorkommen, aber eine sichere Voreinstellung.
                isLeaf = true;
            }
        }
        return isLeaf;
    }

    private ObservableList<TreeItem<Path>> buildChildren(TreeItem<Path> treeItem) {
        Path path = treeItem.getValue();
        if (path != null && Files.isDirectory(path)) {
            try {
                var children = javafx.collections.FXCollections.<TreeItem<Path>>observableArrayList();
                try (var stream = Files.list(path)) {
                    stream
                            // Wir filtern den Stream, bevor wir die Elemente verarbeiten.
                            .filter(p -> {
                                // Verzeichnisse werden immer angezeigt.
                                if (Files.isDirectory(p)) {
                                    return true;
                                }
                                // Wenn kein Filter gesetzt ist, werden alle Dateien angezeigt.
                                if (allowedExtensions == null || allowedExtensions.isEmpty()) {
                                    return true;
                                }
                                // Andernfalls prüfe, ob die Dateiendung in der Liste der erlaubten Endungen ist.
                                String extension = FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase();
                                return allowedExtensions.contains(extension);
                            })
                            .sorted((p1, p2) -> {
                                if (Files.isDirectory(p1) && !Files.isDirectory(p2)) return -1;
                                if (!Files.isDirectory(p1) && Files.isDirectory(p2)) return 1;
                                return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                            })
                            // KORREKTUR: Wir übergeben die Filterliste an die Kind-Elemente weiter.
                            .forEach(p -> children.add(new FileExplorerTreeItem(p, this.allowedExtensions)));
                }
                return children;
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return javafx.collections.FXCollections.emptyObservableList();
    }
}
