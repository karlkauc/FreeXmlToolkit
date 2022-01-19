package org.fxt.freexmltoolkit.controls;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MyFileChooserTree extends HBox {
    private HBox content;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    ObservableList<TreeView<File>> list = FXCollections.emptyObservableList();

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    public void MyFileChooserTree() {
        logger.debug("BIN IM CONSTRUCTOR");
        content = new HBox();
        Bindings.bindContent(content.getChildren(), list);
    }

    public void setNewItem(String startPath) {
        TreeView<File> treeViewXml = new TreeView<>();
        var root1 = new SimpleFileTreeItem(new File(startPath), XML_PATTERN);
        treeViewXml.setRoot(root1);
        treeViewXml.setShowRoot(true);
        // list.add(treeViewXml);
    }


    public HBox getContent() {
        return content;
    }

}
