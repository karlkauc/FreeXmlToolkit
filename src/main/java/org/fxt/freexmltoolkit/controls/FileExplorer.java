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

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A custom VBox implementation for displaying a file explorer.
 */
public class FileExplorer extends VBox {

    private static final Logger logger = LogManager.getLogger(FileExplorer.class);
    private final TreeTableView<Path> fileTreeView = new TreeTableView<>();
    private final Label fileNameLabel = new Label();
    private final TextField pathTextField = new TextField();
    private final StringProperty stringProperty = new SimpleStringProperty();
    private final StringProperty displayText = new SimpleStringProperty();
    private Path selectedFile;
    private List<String> allowedFileExtensions;
    private FileExplorerTreeItem root;

    /**
     * Constructs a FileExplorer instance and initializes the UI components.
     */
    public FileExplorer() {
        logger.debug("FileExplorer()");
        init();
    }

    public void setAllowedFileExtensions(List<String> extensions) {
        // Wir speichern die Endungen in Kleinbuchstaben, um den Vergleich robust zu machen.
        if (extensions != null) {
            this.allowedFileExtensions = extensions.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        } else {
            this.allowedFileExtensions = null;
        }

        if (this.root != null) {
            this.root.getChildren().clear();
            for (File file : File.listRoots()) {
                // Erstelle die Laufwerks-Knoten mit der jetzt vorhandenen Filterliste.
                this.root.getChildren().add(new FileExplorerTreeItem(file.toPath(), this.allowedFileExtensions));
            }
        }
    }


    /**
     * Initializes the UI components and sets up the file tree view.
     */
    private void init() {
        setPadding(new Insets(10));
        setSpacing(10);

        fileNameLabel.textProperty().bind(stringProperty);

        fileTreeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Spalte für den Dateinamen mit Icons
        TreeTableColumn<Path, Path> fileNameColumn = new TreeTableColumn<>("File Name");
        fileNameColumn.prefWidthProperty().bind(fileTreeView.widthProperty().multiply(0.80));
        fileNameColumn.setCellValueFactory(p -> new javafx.beans.property.SimpleObjectProperty<>(p.getValue().getValue()));

        fileNameColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);

                getStyleClass().remove("has-subdirectories");
                setText(null);
                setGraphic(null);

                if (empty || item == null) {
                    return;
                }

                FontIcon icon;
                if (Files.isDirectory(item)) {
                    icon = new FontIcon("fa-folder-o");
                    icon.setIconColor(javafx.scene.paint.Color.ORANGE);
                } else {
                    icon = new FontIcon("fa-file-o");
                    icon.setIconColor(javafx.scene.paint.Color.DODGERBLUE);
                }
                icon.setIconSize(16);
                String fileName = item.getFileName() == null ? item.toString() : item.getFileName().toString();

                setText(fileName);
                setGraphic(icon);
                setContentDisplay(ContentDisplay.LEFT);
                setGraphicTextGap(5);

                TreeTableRow<Path> row = getTableRow();
                if (row != null) {
                    TreeItem<Path> genericTreeItem = row.getTreeItem();
                    if (genericTreeItem != null) {
                        if (Files.isDirectory(item) && genericTreeItem.isExpanded()) {
                            ((FontIcon) getGraphic()).setIconLiteral("fa-folder-open-o");
                        }

                        if (genericTreeItem instanceof FileExplorerTreeItem customTreeItem) {
                            long count = customTreeItem.getSubdirectoryCount();
                            if (count > 0) {
                                getStyleClass().add("has-subdirectories");
                                setText(getText() + " {" + count + "}");
                            }
                        }
                    }
                }
            }
        });

        fileTreeView.getColumns().addAll(List.of(
                fileNameColumn,
                createColumn("File Extension", this::getFileExtension),
                createColumn("File Size", this::getFileSize)
        ));

        String hostName = getHostName();
        this.root = new FileExplorerTreeItem(Paths.get(hostName)) {
            @Override
            public boolean isLeaf() {
                return false;
            }
        };
        this.root.setExpanded(true);

        for (File file : File.listRoots()) {
            this.root.getChildren().add(new FileExplorerTreeItem(file.toPath(), this.allowedFileExtensions));
        }

        fileTreeView.setRoot(this.root);
        fileTreeView.setShowRoot(true);

        fileTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null && newVal.getValue() != null) {
                        Path path = newVal.getValue();
                        this.selectedFile = path;
                        stringProperty.set(path.getFileName() != null ? path.getFileName().toString() : path.toString());
                        // Aktualisiere das Textfeld mit dem vollen Pfad und entferne Fehler-Styling.
                        pathTextField.setText(path.toString());
                        pathTextField.setStyle("");
                    } else {
                        this.selectedFile = null;
                        stringProperty.set("");
                        // Leere das Textfeld und entferne Fehler-Styling.
                        pathTextField.clear();
                        pathTextField.setStyle("");
                    }
                });

        Button homeButton = new Button();
        FontIcon homeIcon = new FontIcon("fa-home");
        homeIcon.setIconSize(16);
        homeButton.setGraphic(homeIcon);
        homeButton.setTooltip(new Tooltip("Go to User Home Directory"));

        homeButton.setOnAction(event -> {
            Path userHome = Paths.get(System.getProperty("user.home"));
            selectPath(userHome);
        });

        Label displayLabel = new Label();
        displayLabel.textProperty().bind(displayText);

        HBox header = new HBox(10, homeButton, displayLabel, fileNameLabel);

        pathTextField.setPromptText("Enter or paste a path and press Enter...");
        pathTextField.setOnAction(event -> handlePathInput());

        getChildren().addAll(header, pathTextField, fileTreeView);
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);

        this.setOnDragOver(this::handleDragOver);
        this.setOnDragDropped(event -> {
            if (event.getDragboard().hasFiles()) {
                File file = event.getDragboard().getFiles().getFirst();
                if (file != null) {
                    selectPath(file.toPath());
                }
                event.setDropCompleted(true);
            }
            event.consume();
        });

        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/file-explorer.css")).toExternalForm());
    }

    /**
     * Handles the input from the path text field.
     */
    private void handlePathInput() {
        Path path = Paths.get(pathTextField.getText());
        if (Files.exists(path)) {
            // Path is valid, remove error styling and navigate.
            pathTextField.setStyle("");
            selectPath(path);
        } else {
            // Path is invalid, show visual feedback.
            pathTextField.setStyle("-fx-border-color: red; -fx-border-width: 1px;");
        }
    }

    /**
     * Selects the given file in the file explorer view.
     * The tree will be expanded to show the file.
     *
     * @param file The file to select.
     */
    public void selectFile(File file) {
        if (file != null) {
            selectPath(file.toPath());
        }
    }

    /**
     * Finds and selects the given path in the tree, expanding nodes as necessary.
     * This now delegates the search and expansion logic to the root TreeItem.
     *
     * @param path The path to select.
     */
    public void selectPath(Path path) {
        if (path == null || this.root == null) {
            return;
        }

        // The root is a dummy node (hostname). We need to start the search from its children (the drive roots).
        for (TreeItem<Path> driveRoot : this.root.getChildren()) {
            if (driveRoot instanceof FileExplorerTreeItem) {
                // Check if the path is on this drive
                if (path.startsWith(driveRoot.getValue())) {
                    TreeItem<Path> targetItem = ((FileExplorerTreeItem) driveRoot).expandAndFind(path);
                    if (targetItem != null) {
                        // Once the item is found, we select it and scroll to it on the FX thread.
                        Platform.runLater(() -> {
                            fileTreeView.getSelectionModel().select(targetItem);
                            int rowIndex = fileTreeView.getRow(targetItem);
                            if (rowIndex >= 0) {
                                fileTreeView.scrollTo(rowIndex);
                            }
                        });
                        return; // Found it, no need to check other drives
                    }
                }
            }
        }
        logger.warn("Could not find path in tree: {}", path);
    }

    private void handleDragOver(javafx.scene.input.DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        } else {
            event.consume();
        }
    }

    private TreeTableColumn<Path, String> createColumn(String title, java.util.function.Function<TreeTableColumn.CellDataFeatures<Path, String>, StringProperty> mapper) {
        TreeTableColumn<Path, String> column = new TreeTableColumn<>(title);
        column.setCellValueFactory(mapper::apply);
        return column;
    }

    private StringProperty getFileExtension(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        if (path != null && !Files.isDirectory(path)) {
            return new SimpleStringProperty(FilenameUtils.getExtension(path.toString()));
        }
        return new SimpleStringProperty("");
    }

    private StringProperty getFileSize(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        if (path != null && path.toFile().isFile()) {
            return new SimpleStringProperty(FileUtils.byteCountToDisplaySize(path.toFile().length()));
        }
        return new SimpleStringProperty("");
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "Computer";
        }
    }

    public Path getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(Path selectedFile) {
        this.selectedFile = selectedFile;
        selectPath(selectedFile);
    }

    public String getDisplayText() {
        return displayText.get();
    }

    public void setDisplayText(String displayText) {
        this.displayText.set(displayText);
    }

    public StringProperty displayTextProperty() {
        return displayText;
    }
}
