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
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private final StringProperty stringProperty = new SimpleStringProperty();
    private final StringProperty displayText = new SimpleStringProperty();
    private Path selectedFile;
    private List<String> allowedFileExtensions;
    private FileExplorerTreeItem<Path> root;
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
        // HINWEIS: Um einen bereits geladenen Baum zu aktualisieren, müsste man ihn hier neu aufbauen.
        // Für Ihren Anwendungsfall, bei dem der Filter beim Initialisieren gesetzt wird, ist das aber nicht nötig.

        if (this.root != null) {
            this.root.getChildren().clear();
            for (File file : File.listRoots()) {
                // Erstelle die Laufwerks-Knoten mit der jetzt vorhandenen Filterliste.
                this.root.getChildren().add(new FileExplorerTreeItem<>(file.toPath(), this.allowedFileExtensions));
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

                // 1. Zustand der Zelle vollständig zurücksetzen
                getStyleClass().remove("has-subdirectories");
                setText(null);
                setGraphic(null);

                // 2. Leere Zellen behandeln
                if (empty || item == null) {
                    return;
                }

                // 3. Basis-Rendering (wird immer ausgeführt)
                FontIcon icon;
                if (Files.isDirectory(item)) {
                    icon = new FontIcon("fa-folder-o"); // Standard: geschlossener Ordner
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

                // 4. Ansicht verbessern, wenn TreeItem-Informationen verfügbar sind
                TreeTableRow<Path> row = getTableRow();
                if (row != null) {
                    TreeItem<Path> genericTreeItem = row.getTreeItem();
                    if (genericTreeItem != null) {

                        // Ordner-Icon bei aufgeklapptem Zustand ändern
                        if (Files.isDirectory(item) && genericTreeItem.isExpanded()) {
                            ((FontIcon) getGraphic()).setIconLiteral("fa-folder-open-o");
                        }

                        // CSS-Klasse und Text für Unterverzeichnisse anpassen
                        if (genericTreeItem instanceof FileExplorerTreeItem<Path> customTreeItem) {
                            // Hole die Anzahl der Unterverzeichnisse
                            long count = customTreeItem.getSubdirectoryCount();

                            // Füge die CSS-Klasse für das '+' Symbol hinzu und passe den Text an
                            if (count > 0) {
                                getStyleClass().add("has-subdirectories");
                                // Hänge die Anzahl an den bestehenden Text an
                                setText(getText() + " {" + count + "}");
                            }
                        }
                    }
                }
            }
        });

        // Füge die neue Spalte und die alten Spalten hinzu
        fileTreeView.getColumns().addAll(fileNameColumn,
                createColumn("File Extension", this::getFileExtension),
                createColumn("File Size", this::getFileSize));

        // ,
        //                createColumn("File Date", this::getFileDate)

        // Logischer Aufbau des Root-Knotens
        String hostName = getHostName();
        this.root = new FileExplorerTreeItem<>(Paths.get(hostName)) {
            @Override
            public boolean isLeaf() {
                return false;
            }
        };
        this.root.setExpanded(true);

        for (File file : File.listRoots()) {
            this.root.getChildren().add(new FileExplorerTreeItem<>(file.toPath(), this.allowedFileExtensions));
        }

        fileTreeView.setRoot(this.root);
        fileTreeView.setShowRoot(true);

        // Vereinfachter Selection-Listener, der den Baum nicht mehr manipuliert
        fileTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null && newVal.getValue() != null) {
                        Path path = newVal.getValue();
                        this.selectedFile = path;
                        stringProperty.set(path.getFileName() != null ? path.getFileName().toString() : path.toString());
                    } else {
                        this.selectedFile = null;
                        stringProperty.set("");
                    }
                });

        Button homeButton = new Button();
        FontIcon homeIcon = new FontIcon("fa-home"); // Ein passendes Icon
        homeIcon.setIconSize(16);
        homeButton.setGraphic(homeIcon);
        homeButton.setTooltip(new Tooltip("Go to User Home Directory")); // Guter Stil für Icon-Buttons

        // Die Aktion, die beim Klick ausgeführt wird
        homeButton.setOnAction(event -> {
            // Hole den Pfad zum Benutzerverzeichnis vom System
            Path userHome = Paths.get(System.getProperty("user.home"));
            // Nutze deine vorhandene, mächtige Methode, um dorthin zu springen
            selectPath(userHome);
        });

        // Header neu aufbauen, um den Home-Button zu integrieren.
        // Wir erstellen das Label für den displayText explizit, um den Code lesbarer zu machen.
        Label displayLabel = new Label();
        displayLabel.textProperty().bind(displayText);

        HBox header = new HBox(10, homeButton, displayLabel, fileNameLabel);

        getChildren().addAll(header, fileTreeView);
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);

        // Saubere Drag-and-Drop-Implementierung
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
     * Finds and selects the given path in the tree, expanding nodes as necessary.
     *
     * @param path The path to select.
     */
    public void selectPath(Path path) {
        if (path == null || fileTreeView.getRoot() == null) {
            return;
        }
        TreeItem<Path> item = findItemByPath(fileTreeView.getRoot(), path);
        if (item != null) {
            fileTreeView.getSelectionModel().select(item);
            Platform.runLater(() -> {
                int rowIndex = fileTreeView.getRow(item);
                if (rowIndex >= 0) {
                    fileTreeView.scrollTo(rowIndex);
                }
            });
        }
    }

    private TreeItem<Path> findItemByPath(TreeItem<Path> root, Path path) {
        Path rootOfPath = path.getRoot();
        if (rootOfPath == null) return null;

        for (TreeItem<Path> fsRootItem : root.getChildren()) {
            if (fsRootItem.getValue().equals(rootOfPath)) {
                return findRecursive(fsRootItem, path);
            }
        }
        return null;
    }

    private TreeItem<Path> findRecursive(TreeItem<Path> currentItem, Path targetPath) {
        try {
            if (currentItem.getValue() != null && Files.isSameFile(currentItem.getValue(), targetPath)) {
                return currentItem;
            }
        } catch (IOException e) {
            if (currentItem.getValue() != null && currentItem.getValue().equals(targetPath)) {
                return currentItem;
            }
        }

        if (targetPath.startsWith(currentItem.getValue())) {
            currentItem.setExpanded(true);
            for (TreeItem<Path> child : currentItem.getChildren()) {
                TreeItem<Path> found = findRecursive(child, targetPath);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private StringProperty getFileDate(TreeTableColumn.CellDataFeatures<Path, String> p) {
        Path path = p.getValue().getValue();
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            Date lastModified = new Date(attr.lastModifiedTime().toMillis());
            return new SimpleStringProperty(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastModified));
        } catch (IOException e) {
            return new SimpleStringProperty("");
        }
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