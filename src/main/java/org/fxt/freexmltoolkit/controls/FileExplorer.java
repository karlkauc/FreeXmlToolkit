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

public class FileExplorer extends VBox {

    private final static Logger logger = LogManager.getLogger(FileExplorer.class);
    TreeTableView<Path> fileTreeView;
    Label fileNameLabel = new Label();
    StringProperty stringProperty = new SimpleStringProperty();

    Path selectedFile = null;

    StringProperty displayText = new SimpleStringProperty();
    String displayString = "";

    public String getDisplayString() {
        return displayString;
    }

    public void setDisplayString(String displayString) {
        this.displayString = displayString;
        this.displayText.set(displayString);
    }

    public String getDisplayText() {
        return displayText.get();
    }

    public StringProperty displayTextProperty() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText.set(displayText);
    }

    public Path getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(Path selectedFile) {
        this.selectedFile = selectedFile;
    }

    public FileExplorer() {
        logger.debug("FileExplorer()");
        init();
    }

    private void init() {
        try {
            this.setPadding(new Insets(10, 10, 10, 10));
            this.setSpacing(10);

            fileNameLabel.textProperty().bind(stringProperty);

            fileTreeView = new TreeTableView<>();
            fileTreeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

            TreeTableColumn<Path, String> fileName = new TreeTableColumn<>("File Name");
            TreeTableColumn<Path, String> fileExtension = new TreeTableColumn<>("File Extension");
            TreeTableColumn<Path, String> fileSize = new TreeTableColumn<>("File Size");
            TreeTableColumn<Path, String> fileDate = new TreeTableColumn<>("File Date");

            fileName.setCellValueFactory(p -> {
                if (p.getValue() != null && p.getValue().getValue() != null && p.getValue().getValue().getFileName() != null) {
                    return new SimpleStringProperty(p.getValue().getValue().toFile().getName());
                } else if (p.getValue() != null && p.getValue().getValue() != null) {
                    // Root Element
                    return new SimpleStringProperty(p.getValue().getValue().toString());
                }

                return new SimpleStringProperty("");
            });
            fileExtension.setCellValueFactory(p -> {
                if (p.getValue() != null && p.getValue().getValue() != null && p.getValue().getValue().getFileName() != null) {
                    var file = p.getValue().getValue();
                    logger.debug("Is directory: {}, {}", file.getFileName().toFile().getAbsoluteFile(), Files.isDirectory(file));

                    if (Files.isDirectory(file)) {
                        return new SimpleStringProperty("");
                    }
                    return new SimpleStringProperty(FilenameUtils.getExtension(p.getValue().getValue().getFileName().toString()));
                } else {
                    return new SimpleStringProperty("");
                }
            });
            fileSize.setCellValueFactory(p -> {
                if (p.getValue().getValue().toFile().isFile() && !Files.isDirectory(p.getValue().getValue())) {
                    return new SimpleStringProperty(FileUtils.byteCountToDisplaySize(p.getValue().getValue().toFile().length()));
                }
                return new SimpleStringProperty("");
            });
            fileDate.setCellValueFactory(p -> {
                final Path file = p.getValue().getValue();
                try {
                    final BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                    return new SimpleStringProperty(attr.lastModifiedTime().toString());
                } catch (IOException e) {
                    // logger.error(e.getMessage());
                }

                return new SimpleStringProperty("");
            });

            fileTreeView.getColumns().add(fileName);
            fileTreeView.getColumns().add(fileExtension);
            fileTreeView.getColumns().add(fileSize);
            fileTreeView.getColumns().add(fileDate);

            String hostName = "computer";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {
            }

            FileExplorerTreeItem<Path> root = new FileExplorerTreeItem<>(new File(hostName).toPath());

            for (File file : File.listRoots()) {
                logger.debug("drinnen: {}", file.getName());
                root.getChildren().add(new FileExplorerTreeItem<>(file.toPath()));
                logger.debug("nach treeNode");
            }

            fileTreeView.setShowRoot(true);
            fileTreeView.setRoot(root);

            fileTreeView.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((observable, oldValue, newValue) -> {
                        if (newValue.getValue().toFile().isFile()) {
                            stringProperty.setValue(newValue.getValue().toFile().getName());
                            selectedFile = newValue.getValue();
                        }
                        if (newValue.getValue().toFile().isDirectory()) {
                            if (newValue.isExpanded()) {
                                newValue.getChildren().clear();
                            } else {
                                try (var walk = Files.walk(newValue.getValue(), 1)) {
                                    List<Path> result = walk
                                            .filter(f -> f != newValue.getValue())
                                            .filter(f -> f.toFile().isFile() || f.toFile().isDirectory())
                                            .toList();
                                    for (Path file : result) {
                                        newValue.getChildren().add(new FileExplorerTreeItem<>(file));
                                    }
                                } catch (IOException e) {
                                    // e.printStackTrace();
                                }
                            }
                        }
                        // System.out.println("Selected Text : " + newValue.getValue());
                    });

            HBox header = new HBox();
            header.setSpacing(10);
            var headerLabel = new Label();
            headerLabel.textProperty().bind(displayText);

            header.getChildren().add(headerLabel);
            header.getChildren().add(fileNameLabel);

            this.getChildren().addAll(header, fileTreeView);

            VBox.setVgrow(fileTreeView, Priority.ALWAYS);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
