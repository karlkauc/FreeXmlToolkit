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

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// In der Klasse FileExplorerTreeItem.java

public class FileExplorerTreeItem<T> extends TreeItem<T> {

    private static final Logger logger = LogManager.getLogger(FileExplorerTreeItem.class);
    private boolean isLeaf;
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;

    // NEU: Felder für die Prüfung auf Unterverzeichnisse
    private boolean hasSubdirectories;
    private final boolean isFirstTimeCheckForSubdirectories = true;

    // Feld für das Caching der Anzahl der Unterverzeichnisse.
    // -1 bedeutet "noch nicht berechnet".
    private long subdirectoryCount = -1;

    private final List<String> allowedExtensions;


    public FileExplorerTreeItem(T value) {
        this(value, null);
    }

    public FileExplorerTreeItem(T value, List<String> allowedExtensions) {
        super(value);
        this.allowedExtensions = allowedExtensions;
    }


    public long getSubdirectoryCount() {
        if (this.subdirectoryCount == -1) { // Nur beim ersten Mal berechnen
            Path path = (Path) getValue();
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    // Zähle nur die Elemente im Stream, die Verzeichnisse sind.
                    this.subdirectoryCount = stream.filter(Files::isDirectory).count();
                } catch (IOException e) {
                    logger.error("Could not count subdirectories in: " + path, e);
                    this.subdirectoryCount = 0; // Im Fehlerfall ist die Anzahl 0.
                }
            } else {
                this.subdirectoryCount = 0; // Dateien haben 0 Unterverzeichnisse.
            }
        }
        return this.subdirectoryCount;
    }

    // Wir passen hasSubdirectories an, damit es konsistent mit der Zählung ist.
    public boolean hasSubdirectories() {
        return getSubdirectoryCount() > 0;
    }

    @Override
    public ObservableList<TreeItem<T>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            super.getChildren().setAll(buildChildren(this));
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        if (isFirstTimeLeaf) {
            isFirstTimeLeaf = false;
            Path path = (Path) getValue();

            if (Files.isRegularFile(path)) {
                isLeaf = true;
            } else if (Files.isDirectory(path)) {
                // KORREKTUR: Ein Verzeichnis ist ein "Blatt", wenn es KEINE Kinder enthält, die angezeigt würden.
                // Ein Kind wird angezeigt, wenn es ein Verzeichnis ist ODER eine Datei, die dem Filter entspricht.
                try (var stream = Files.list(path)) {
                    isLeaf = stream.noneMatch(p -> {
                        if (Files.isDirectory(p)) {
                            return true; // Hat ein Unterverzeichnis, ist also kein Blatt.
                        }
                        // Es ist eine Datei, also gegen den Filter prüfen.
                        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
                            return true; // Kein Filter, also wird die Datei angezeigt -> kein Blatt.
                        }
                        String extension = FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase();
                        return allowedExtensions.contains(extension); // Datei entspricht Filter -> kein Blatt.
                    });
                } catch (IOException e) {
                    logger.error("Could not check directory content for: " + path, e);
                    isLeaf = true; // Im Fehlerfall als Blatt behandeln.
                }
            } else {
                // Sollte nicht vorkommen, aber eine sichere Voreinstellung.
                isLeaf = true;
            }
        }
        return isLeaf;
    }

    private ObservableList<TreeItem<T>> buildChildren(TreeItem<T> treeItem) {
        Path path = (Path) treeItem.getValue();
        if (path != null && Files.isDirectory(path)) {
            try {
                var children = javafx.collections.FXCollections.<TreeItem<T>>observableArrayList();
                try (var stream = Files.list(path)) {
                    stream
                            // Wir filtern den Stream, bevor wir die Elemente verarbeiten.
                            .filter(p -> {
                                // Verzeichnisse werden immer angezeigt.
                                if (Files.isDirectory(p)) {
                                    return true;
                                }
                                // Wenn kein Filter gesetzt ist, werden alle Dateien angezeigt.
                                if (allowedExtensions == null || allowedExtensions.isEmpty()) {
                                    return true;
                                }
                                // Andernfalls prüfe, ob die Dateiendung in der Liste der erlaubten Endungen ist.
                                String extension = FilenameUtils.getExtension(p.getFileName().toString()).toLowerCase();
                                return allowedExtensions.contains(extension);
                            })
                            .sorted((p1, p2) -> {
                                if (Files.isDirectory(p1) && !Files.isDirectory(p2)) return -1;
                                if (!Files.isDirectory(p1) && Files.isDirectory(p2)) return 1;
                                return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                            })
                            // KORREKTUR: Wir übergeben die Filterliste an die Kind-Elemente weiter.
                            .forEach(p -> children.add(new FileExplorerTreeItem<>((T) p, this.allowedExtensions)));
                }
                return children;
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return javafx.collections.FXCollections.emptyObservableList();
    }
}