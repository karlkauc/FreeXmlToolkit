package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MyFileChooserTree extends TreeView<File> {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    TreeItem<File> rootItem = new TreeItem<>();

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    public MyFileChooserTree() {
        logger.debug("CONSTRUCTOR");

        rootItem.setExpanded(false);
        this.setRoot(rootItem);
        this.setShowRoot(false);
    }

    public void setNewItem(String startPath) {
        logger.debug("set new Item: {}", startPath);

        var fileTreeItem = new SimpleFileTreeItem(new File(startPath), XML_PATTERN);
        this.setCellFactory(param -> new FileTreeCell());
        rootItem.getChildren().add(fileTreeItem);
    }
}
