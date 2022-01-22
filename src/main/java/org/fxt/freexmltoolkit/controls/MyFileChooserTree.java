package org.fxt.freexmltoolkit.controls;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MyFileChooserTree extends ListView<TreeView<File>> {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    public MyFileChooserTree() {
        logger.debug("BIN IM CONSTRUCTOR");
        // this.setPrefHeight(this.getParent().getScene().getHeight());
    }

    public void setNewItem(String startPath) {
        logger.debug("set new Item: {}", startPath);

        TreeView<File> treeViewXml = new TreeView<>();
        var root1 = new SimpleFileTreeItem(new File(startPath), XML_PATTERN);
        treeViewXml.setRoot(root1);
        treeViewXml.setShowRoot(true);

        treeViewXml.setCellFactory(param -> new FileTreeCell());
        this.getItems().add(treeViewXml);

        // this.setPrefHeight(treeViewXml.getHeight());
        widthProperty().addListener(this::heightChanged);
    }

    private void widthChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        double width = getWidth() + this.getInsets().getLeft() + this.getInsets().getRight();
        this.setPrefWidth(Math.max(this.getPrefWidth(), width));
    }

    private void heightChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        double height = getHeight() + this.getInsets().getTop() + this.getInsets().getBottom();
        this.setPrefHeight(Math.max(this.getPrefHeight(), height));
    }
}
