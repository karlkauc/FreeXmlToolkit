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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A lazy-loading TreeItem implementation for file system browsing.
 * This class provides efficient navigation of file system directories
 * by loading child items on demand rather than eagerly.
 *
 * <p>Features include:</p>
 * <ul>
 *   <li>Lazy loading of directory contents</li>
 *   <li>Optional file extension filtering</li>
 *   <li>Caching of subdirectory counts</li>
 *   <li>Refresh and force refresh capabilities</li>
 *   <li>Expand-and-find functionality for path navigation</li>
 * </ul>
 *
 * <p>Directories are always shown before files, and both are sorted
 * alphabetically (case-insensitive).</p>
 *
 * @see javafx.scene.control.TreeItem
 */
public class FileExplorerTreeItem extends TreeItem<Path> {

    private static final Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);
    private boolean isLeaf;
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;

    // Cached number of subdirectories.
    // -1 means "not calculated yet".
    private long subdirectoryCount = -1;

    private final List<String> allowedExtensions;
    /** Whether this path is a directory, as known from the parent's listing ({@code null} = ask the disk). */
    private final Boolean knownDirectory;
    /** List directories on a worker thread (the shell's workspace tree); children inherit it. */
    private final boolean asyncListing;

    /**
     * Creates a new FileExplorerTreeItem with the specified path.
     * No file extension filtering is applied.
     *
     * @param value the file system path represented by this tree item
     */
    public FileExplorerTreeItem(Path value) {
        this(value, null);
    }

    /**
     * Creates a new FileExplorerTreeItem with the specified path and extension filter.
     *
     * @param value             the file system path represented by this tree item
     * @param allowedExtensions list of allowed file extensions (without dot, e.g., "xml", "xsd"),
     *                          or null/empty to allow all files
     */
    public FileExplorerTreeItem(Path value, List<String> allowedExtensions) {
        this(value, allowedExtensions, null, false);
    }

    private FileExplorerTreeItem(Path value, List<String> allowedExtensions, Boolean knownDirectory,
                                 boolean asyncListing) {
        super(value);
        this.allowedExtensions = allowedExtensions;
        this.knownDirectory = knownDirectory;
        this.asyncListing = asyncListing;
    }

    /**
     * A root whose directories are listed on a worker thread when first expanded, with the children
     * filled in on the FX thread. Listing (and stat-ing every entry, several times while sorting) on the FX
     * thread froze the UI for seconds on large or network folders. {@link #isLeaf()} then assumes a
     * directory has children until its listing shows otherwise.
     */
    public static FileExplorerTreeItem withBackgroundListing(Path value, List<String> allowedExtensions) {
        return new FileExplorerTreeItem(value, allowedExtensions, null, true);
    }

    private boolean isDirectory() {
        return knownDirectory != null ? knownDirectory : Files.isDirectory(getValue());
    }

    /**
     * Loads the children now, on the calling thread, if they were not listed yet: for code that needs
     * them immediately (revealing a path, tests), not for the user expanding a folder.
     */
    public ObservableList<TreeItem<Path>> loadChildrenNow() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            super.getChildren().setAll(buildChildren(this));
        }
        return super.getChildren();
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
        if (isDirectory()) {
            // Ensure children are loaded (now: the caller needs them; only the folders on the path).
            loadChildrenNow();

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


    /**
     * Returns the count of subdirectories in this directory.
     *
     * @return the number of subdirectories
     */
    public long getSubdirectoryCount() {
        if (this.subdirectoryCount == -1) { // Calculate only on first access
            Path path = getValue();
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    // Count only the stream entries that are directories.
                    this.subdirectoryCount = stream.filter(Files::isDirectory).count();
                } catch (IOException e) {
                    logger.info("Could not count subdirectories in: {}", path, e);
                    this.subdirectoryCount = 0; // On error, report zero subdirectories.
                }
            } else {
                this.subdirectoryCount = 0; // Files have no subdirectories.
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
            if (child instanceof FileExplorerTreeItem fileExplorerChild && Files.isDirectory(child.getValue())) {
                fileExplorerChild.forceRefresh();
            }
        }
    }

    @Override
    public ObservableList<TreeItem<Path>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            if (asyncListing) {
                listInBackground();
            } else {
                super.getChildren().setAll(buildChildren(this));
            }
        }
        return super.getChildren();
    }

    /** Lists the directory on a worker and fills in the children on the FX thread. */
    private void listInBackground() {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            List<Entry> entries = list(getValue(), allowedExtensions);
            javafx.application.Platform.runLater(() -> {
                isLeaf = entries.isEmpty();
                isFirstTimeLeaf = false;
                super.getChildren().setAll(toItems(entries));
            });
        });
    }

    @Override
    public boolean isLeaf() {
        if (isFirstTimeLeaf) {
            isFirstTimeLeaf = false;
            Path path = getValue();

            if (asyncListing) {
                // No disk access here (this runs for every row the tree shows): a directory is assumed
                // to have children until its listing arrives (listInBackground corrects it).
                isLeaf = !isDirectory();
            } else if (Files.isRegularFile(path)) {
                isLeaf = true;
            } else if (Files.isDirectory(path)) {
                // A directory is a "leaf" if it contains NO children that would be displayed.
                // A child is displayed if it is a directory OR a file matching the filter.
                try (var stream = Files.list(path)) {
                    isLeaf = stream.noneMatch(p -> {
                        if (Files.isDirectory(p)) {
                            return true; // Has a subdirectory, so it is not a leaf.
                        }
                        // It is a file, so check it against the filter.
                        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
                            return true; // No filter, so the file is displayed -> not a leaf.
                        }
                        String extension = FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase();
                        return allowedExtensions.contains(extension); // File matches the filter -> not a leaf.
                    });
                } catch (IOException e) {
                    logger.info("Could not check directory content for: {}", path, e);
                    isLeaf = true; // Treat as a leaf on error.
                }
            } else {
                // Should not happen, but a safe default.
                isLeaf = true;
            }
        }
        return isLeaf;
    }

    private ObservableList<TreeItem<Path>> buildChildren(TreeItem<Path> treeItem) {
        Path path = treeItem.getValue();
        if (path != null && Files.isDirectory(path)) {
            return toItems(list(path, allowedExtensions));
        }
        return javafx.collections.FXCollections.emptyObservableList();
    }

    /** A directory entry with its kind, read once while listing. */
    private record Entry(Path path, boolean directory) {
    }

    /**
     * The displayed entries of {@code directory}: subdirectories first, then the files the filter allows,
     * each group by name. Every entry's attributes are read once; the former comparator asked the disk
     * whether an entry is a directory on every comparison.
     */
    private static List<Entry> list(Path directory, List<String> allowedExtensions) {
        List<Entry> entries = new java.util.ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.forEach(p -> {
                boolean dir;
                try {
                    dir = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class).isDirectory();
                } catch (IOException e) {
                    dir = false;
                }
                // Directories are always displayed; files only if no filter is set or they match it.
                if (dir || allowedExtensions == null || allowedExtensions.isEmpty()
                        || allowedExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase())) {
                    entries.add(new Entry(p, dir));
                }
            });
        } catch (IOException | java.io.UncheckedIOException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
        entries.sort((a, b) -> {
            if (a.directory() != b.directory()) {
                return a.directory() ? -1 : 1;
            }
            return a.path().getFileName().toString().compareToIgnoreCase(b.path().getFileName().toString());
        });
        return entries;
    }

    private ObservableList<TreeItem<Path>> toItems(List<Entry> entries) {
        var children = javafx.collections.FXCollections.<TreeItem<Path>>observableArrayList();
        for (Entry entry : entries) {
            // Pass the filter list (and the kind just read) on to the child items.
            children.add(new FileExplorerTreeItem(entry.path(), allowedExtensions, entry.directory(), asyncListing));
        }
        return children;
    }

    /** @return whether this item is a directory (known from the parent's listing when possible) */
    public boolean isDirectoryItem() {
        return isDirectory();
    }
}
