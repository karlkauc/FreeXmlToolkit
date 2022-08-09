package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileLoader extends VBox {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    FileChooser fileChooser = new FileChooser();
    Button loadButton = new Button();
    GridPane fileInfo = new GridPane();
    private String loadPattern, displayText;
    File file;
    Boolean isComponentVisible = true;

    public FileLoader() {
        this.getChildren().add(loadButton);
        this.getChildren().add(fileInfo);
    }

    public void setImageView(ImageView imageView) {
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

            try {
                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

                fileInfo.add(new Label("creation Time:"), 0, 1);
                fileInfo.add(new Label(attr.creationTime().toString()), 1, 1);

                fileInfo.add(new Label("lastModifiedTime:"), 0, 2);
                fileInfo.add(new Label(attr.lastModifiedTime().toString()), 1, 2);
            } catch (IOException e) {
                throw new RuntimeException(e);
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
