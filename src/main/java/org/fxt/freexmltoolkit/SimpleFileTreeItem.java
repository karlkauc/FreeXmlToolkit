package org.fxt.freexmltoolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class SimpleFileTreeItem extends TreeItem<File> {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private String pattern;

    /**
     * Calling the constructor of super class in oder to create a new
     * TreeItem<File>.
     *
     * @param f
     *            an object of type File from which a tree should be build or
     *            which children should be gotten.
     */
    public SimpleFileTreeItem(File f) {
        super(f);
    }

    public SimpleFileTreeItem(File f, String pattern) {
        super(f);
        this.pattern = pattern;
    }


    /*
     * (non-Javadoc)
     *
     * @see javafx.scene.control.TreeItem#getChildren()
     */
    @Override
    public ObservableList<TreeItem<File>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;

            /*
             * First getChildren() call, so we actually go off and determine the
             * children of the File contained in this TreeItem.
             */
            super.getChildren().setAll(buildChildren(this));
        }
        return super.getChildren();
    }

    /*
     * (non-Javadoc)
     *
     * @see javafx.scene.control.TreeItem#isLeaf()
     */
    @Override
    public boolean isLeaf() {
        if (isFirstTimeLeaf) {
            isFirstTimeLeaf = false;
            File f = getValue();
            isLeaf = f.isFile();
        }

        return isLeaf;
    }

    /**
     * Returning a collection of type ObservableList containing TreeItems, which
     * represent all children available in handed TreeItem.
     *
     * @param TreeItem
     *            the root node from which children a collection of TreeItem
     *            should be created.
     * @return an ObservableList<TreeItem<File>> containing TreeItems, which
     *         represent all children available in handed TreeItem. If the
     *         handed TreeItem is a leaf, an empty list is returned.
     */
    private ObservableList<TreeItem<File>> buildChildren(TreeItem<File> TreeItem) {
        File f = TreeItem.getValue();
        if (f != null && f.isDirectory()) {
            File[] files;
            if (this.pattern != null) {
                files = f.listFiles((dir1, name) -> new File(dir1.getAbsolutePath() + "/" + name).isDirectory() || name.matches(this.pattern));
            }
            else {
                files = f.listFiles();
            }

            if (files != null) {
                Arrays.sort(files);
            }

            if (files != null) {
                ObservableList<TreeItem<File>> children = FXCollections
                        .observableArrayList();

                for (File childFile : files) {
                    children.add(new SimpleFileTreeItem(childFile, this.pattern));
                }

                return children;
            }
        }

        return FXCollections.emptyObservableList();
    }

    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;
    private boolean isLeaf;
}