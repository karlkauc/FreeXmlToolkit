package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A custom TreeItem implementation for displaying file system paths in a tree view.
 *
 * @param <E> the type of the path, extending Path
 */
public class FileExplorerTreeItem<E extends Path> extends TreeItem<Path> {
    private static final Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);
    private static final int IMAGE_SIZE = 15;

    // Images for folder (collapsed and expanded) and file icons
    private static final Image folderCollapseImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/folder.png")));
    private static final Image folderExpandImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/folder-open.png")));
    private static final Image fileImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/text-x-generic.png")));

    /**
     * Constructs a FileExplorerTreeItem for the given path.
     *
     * @param path the file system path represented by this TreeItem
     */
    public FileExplorerTreeItem(E path) {
        logger.debug("FileExplorerTreeItem created for: {}", path);

        // Set the graphic based on whether the path is a directory or a file
        var imageView = new ImageView(Files.isDirectory(path) ? folderCollapseImage : fileImage);

        imageView.prefHeight(IMAGE_SIZE);
        imageView.setFitHeight(IMAGE_SIZE);
        imageView.maxHeight(IMAGE_SIZE);
        imageView.minHeight(IMAGE_SIZE);

        imageView.prefWidth(IMAGE_SIZE);
        imageView.setFitWidth(IMAGE_SIZE);
        imageView.maxWidth(IMAGE_SIZE);
        imageView.minWidth(IMAGE_SIZE);

        imageView.setStyle("-fx-padding: 0.75em 0em 0.75em 0em ;");

        this.setGraphic(imageView);
        this.setValue(path);
    }
}