package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class FileExplorerTreeItem<E extends Path> extends TreeItem<Path> {
    private final static Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);

    public static Image folderCollapseImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/folder.png")));
    public static Image folderExpandImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/folder-open.png")));
    public static Image fileImage = new Image(Objects.requireNonNull(FileExplorerTreeItem.class.getResourceAsStream("/img/text-x-generic.png")));

    public String fileNameString;

    public String getFileName() {
        return fileNameString;
    }

    public FileExplorerTreeItem(E path) {
        super(path);
        logger.debug("FileExplorerTreeItem created");
        logger.debug("File: {}", path.toString());

        if (Files.isDirectory(path)) {
            logger.debug("is directory: {}", path.toString());
            this.setGraphic(new ImageView(folderCollapseImage));
        } else {
            logger.debug("is file: {}", path.toString());
            this.setGraphic(new ImageView(fileImage));
            fileNameString = path.getFileName().toString();
        }
        this.setValue(path);
    }
}
