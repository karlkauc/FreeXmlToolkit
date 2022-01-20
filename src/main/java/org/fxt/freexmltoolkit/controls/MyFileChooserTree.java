package org.fxt.freexmltoolkit.controls;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MyFileChooserTree extends ListView<TreeView<File>> {
    private ListView<TreeView<File>> content;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    ObservableList<TreeView<File>> list;

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    public MyFileChooserTree() {
        logger.debug("BIN IM CONSTRUCTOR");
        list = FXCollections.emptyObservableList();
        content = new ListView<>();
        // Bindings.bindContent(content.getItems(), list);
    }

    public void setNewItem(String startPath) {
        logger.debug("set new Item: {}", startPath);

        TreeView<File> treeViewXml = new TreeView<>();
        var root1 = new SimpleFileTreeItem(new File(startPath), XML_PATTERN);
        treeViewXml.setRoot(root1);
        treeViewXml.setShowRoot(true);
        // list.add(treeViewXml);
        content.getItems().add(treeViewXml);
    }


    public ListView getContent() {
        return content;
    }

}
