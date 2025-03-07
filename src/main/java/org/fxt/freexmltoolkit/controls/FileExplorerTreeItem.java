package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * A custom TreeItem implementation for displaying file system paths in a tree view.
 *
 * @param <E> the type of the path, extending Path
 */
public class FileExplorerTreeItem<E extends Path> extends TreeItem<Path> {
    private static final Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);

    /**
     * Constructs a FileExplorerTreeItem for the given path.
     *
     * @param path the file system path represented by this TreeItem
     */
    public FileExplorerTreeItem(E path) {
        logger.debug("FileExplorerTreeItem created for: {}", path);
        this.setValue(path);
    }
}