/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
        logger.debug("MY FILE CHOOSER TREE - CONSTRUCTOR");

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
