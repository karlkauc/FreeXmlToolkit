/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * A custom VBox implementation for displaying a file explorer.
 */
public class FileExplorer extends VBox {

    private static final Logger logger = LogManager.getLogger(FileExplorer.class);
    private final TreeTableView<Path> fileTreeView = new TreeTableView<>();
    private final Label fileNameLabel = new Label();
    private final StringProperty stringProperty = new SimpleStringProperty();
    private final StringProperty displayText = new SimpleStringProperty();
    private Path selectedFile;

    /**
     * Constructs a FileExplorer instance and initializes the UI components.
     */
    public FileExplorer() {
        logger.debug("FileExplorer()");
        init();
    }

    /**
     * Initializes the UI components and sets up the file tree view.
     */
    private void init() {
        setPadding(new Insets(10));
        setSpacing(10);

        fileNameLabel.textProperty().bind(stringProperty);

        fileTreeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        fileTreeView.getColumns().addAll(createColumn("File Name", this::getFileName),
                createColumn("File Extension", this::getFileExtension),
                createColumn("File Size", this::getFileSize),
                createColumn("File Date", this::getFileDate));

        String hostName = getHostName();
        FileExplorerTreeItem<Path> root = new FileExplorerTreeItem<>(new File(hostName).toPath());
        for (File file : File.listRoots()) {
            root.getChildren().add(new FileExplorerTreeItem<>(file.toPath()));
        }

        fileTreeView.setShowRoot(true);
        fileTreeView.setRoot(root);
        fileTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleSelection((FileExplorerTreeItem<Path>) newVal));

        HBox header = new HBox(10, new Label(), fileNameLabel);
        ((Label) header.getChildren().getFirst()).textProperty().bind(displayText);

        getChildren().addAll(header, fileTreeView);
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);
    }

    /**
     * Creates a TreeTableColumn with the specified title and cell value factory.
     *
     * @param title  the title of the column
     * @param mapper the function to map cell data to a StringProperty
     * @return the created TreeTableColumn
     */
    private TreeTableColumn<Path, String> createColumn(String title, java.util.function.Function<TreeTableColumn.CellDataFeatures<Path, String>, StringProperty> mapper) {
        TreeTableColumn<Path, String> column = new TreeTableColumn<>(title);
        column.setCellValueFactory(mapper::apply);
        return column;
    }

    /**
     * Gets the file name from the specified cell data.
     *
     * @param p the cell data
     * @return the file name as a StringProperty
     */
    private StringProperty getFileName(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();

        if (path == null) {
            return new SimpleStringProperty("");
        }
        if (path.getFileName() == null) {
            return new SimpleStringProperty(path.toString());
        }

        return new SimpleStringProperty(path.toFile().getName());
    }

    /**
     * Gets the file extension from the specified cell data.
     *
     * @param p the cell data
     * @return the file extension as a StringProperty
     */
    private StringProperty getFileExtension(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        if (path != null && !Files.isDirectory(path)) {
            return new SimpleStringProperty(FilenameUtils.getExtension(path.toString()));
        }
        return new SimpleStringProperty("");
    }

    /**
     * Gets the file size from the specified cell data.
     *
     * @param p the cell data
     * @return the file size as a StringProperty
     */
    private StringProperty getFileSize(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        if (path != null && path.toFile().isFile()) {
            return new SimpleStringProperty(FileUtils.byteCountToDisplaySize(path.toFile().length()));
        }
        return new SimpleStringProperty("");
    }

    /**
     * Gets the file date from the specified cell data.
     *
     * @param p the cell data
     * @return the file date as a StringProperty
     */
    private StringProperty getFileDate(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return new SimpleStringProperty(attr.lastModifiedTime().toString());
        } catch (IOException e) {
            return new SimpleStringProperty("");
        }
    }

    /**
     * Gets the host name of the local machine.
     *
     * @return the host name
     */
    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "computer";
        }
    }

    /**
     * Handles the selection of a file or directory in the file tree view.
     *
     * @param newVal the selected TreeItem
     */
    private void handleSelection(FileExplorerTreeItem<Path> newVal) {
        if (newVal == null) return;
        Path path = newVal.getValue();
        if (path.toFile().isFile()) {
            stringProperty.setValue(path.toFile().getName());
            selectedFile = path;
        } else if (path.toFile().isDirectory()) {
            if (newVal.isExpanded()) {
                newVal.getChildren().clear();
            } else {
                try (var walk = Files.walk(path, 1)) {
                    List<Path> result = walk.filter(f -> !f.equals(path)).toList();
                    for (Path file : result) {
                        newVal.getChildren().add(new FileExplorerTreeItem<>(file));
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Gets the currently selected file.
     *
     * @return the selected file
     */
    public Path getSelectedFile() {
        return selectedFile;
    }

    /**
     * Sets the selected file.
     *
     * @param selectedFile the file to select
     */
    public void setSelectedFile(Path selectedFile) {
        this.selectedFile = selectedFile;
    }

    /**
     * Gets the display text.
     *
     * @return the display text
     */
    public String getDisplayText() {
        return displayText.get();
    }

    /**
     * Sets the display text.
     *
     * @param displayText the text to display
     */
    public void setDisplayText(String displayText) {
        this.displayText.set(displayText);
    }

    /**
     * Gets the display text property.
     *
     * @return the display text property
     */
    public StringProperty displayTextProperty() {
        return displayText;
    }
}