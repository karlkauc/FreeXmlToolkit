package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Label;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MyFileChooserTree extends VBox {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    TreeView<File> treeViewXml;

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    public void setTest() {
        logger.debug("TEST");
        this.getChildren().add(new Label("TEST"));
    }

    public void setNewItem(String startPath) {
        logger.debug("set new Item: {}", startPath);

        treeViewXml = new TreeView<>();
        var fileTreeItem = new SimpleFileTreeItem(new File(startPath), XML_PATTERN);
        treeViewXml.setRoot(fileTreeItem);
        treeViewXml.setShowRoot(true);

        this.getChildren().add(treeViewXml);
    }
}
