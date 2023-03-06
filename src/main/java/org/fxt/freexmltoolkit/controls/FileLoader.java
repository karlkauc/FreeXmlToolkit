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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileLoader extends VBox {
    private final static Logger logger = LogManager.getLogger(FileLoader.class);

    FileChooser fileChooser = new FileChooser();
    Button loadButton = new Button();
    GridPane fileInfo = new GridPane();
    private String loadPattern, displayText;
    File file;
    Boolean isComponentVisible = true;

    ImageView teaserImage;

    public FileLoader() {
        this.getChildren().add(loadButton);
        this.getChildren().add(fileInfo);

        this.setOnDragOver(event -> {
            event.acceptTransferModes(TransferMode.MOVE);
        });
    }

    public ImageView getTeaserImage() {
        return teaserImage;
    }

    @FXML
    void onDragDropped(DragEvent event) {
        logger.debug("BIN IN DRAG DROPPED");

        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        } else {
            event.consume();
        }
    }

    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();
        File file = db.getFiles().get(0);

        logger.debug("FILE: {}", file.getAbsolutePath());
    }

    @FXML
    public void setTeaserImage(ImageView teaserImage) {
        this.teaserImage = teaserImage;

        this.getChildren().clear();

        this.getChildren().add(this.teaserImage);
        this.getChildren().add(loadButton);
        this.getChildren().add(fileInfo);
    }

    public void setButtonImageView(ImageView imageView) {
        loadButton.setGraphic(imageView);
    }

    public void setLoadPattern(String loadPattern, String displayText) {
        this.loadPattern = loadPattern;
        this.displayText = displayText;
        setLayout();
    }

    public File getFileAction() {
        if (loadPattern != null && !loadPattern.isEmpty()) {
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(displayText, loadPattern));
        }

        Path path = FileSystems.getDefault().getPath(".");
        fileChooser.setInitialDirectory(path.toFile());

        file = fileChooser.showOpenDialog(null);

        if (file != null) {
            fileInfo.getChildren().clear();

            fileInfo.add(new Label("Size:"), 0, 0);
            fileInfo.add(new Label(FileUtils.byteCountToDisplaySize(file.length())), 1, 0);

            logger.debug("File: {}", file.getAbsolutePath());

            try {
                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

                fileInfo.add(new Label("creation Time:"), 0, 1);
                fileInfo.add(new Label(attr.creationTime().toString()), 1, 1);

                fileInfo.add(new Label("lastModifiedTime:"), 0, 2);
                fileInfo.add(new Label(attr.lastModifiedTime().toString()), 1, 2);
            } catch (IOException e) {
                logger.error(e.getMessage());
            }

            return file;
        }

        return null;
    }

    private void setLayout() {
        this.setStyle("-fx-padding: 7px; ");
        fileInfo.setHgap(10);
        fileInfo.setVgap(10);
        fileInfo.setGridLinesVisible(false);
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public File getFile() {
        return file;
    }
}
