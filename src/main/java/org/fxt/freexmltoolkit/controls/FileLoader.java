package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class FileLoader extends VBox {
    FileChooser fileChooser = new FileChooser();
    Button loadButton = new Button("LOAD");
    GridPane fileInfo = new GridPane();
    private String loadPattern, displayText;

    File file;

    public FileLoader() {
        this.getChildren().add(loadButton);
        this.getChildren().add(fileInfo);
    }

    public void setButtonText(String buttonText) {
        loadButton.setText(buttonText);
    }

    public void setLoadPattern(String loadPattern, String displayText) {
        this.loadPattern = loadPattern;
        this.displayText = displayText;
    }

    public File getFileAction() {
        if (loadPattern != null && !loadPattern.isEmpty()) {
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(displayText, loadPattern));
        }

        Path path = FileSystems.getDefault().getPath(".");
        fileChooser.setInitialDirectory(path.toFile());

        file = fileChooser.showOpenDialog(null);

        fileInfo.getChildren().clear();
        fileInfo.add(new Label("Filename:"),0,0);
        fileInfo.add(new Label(file.getName()),1,0);

        fileInfo.add(new Label("Size:"),0,1);
        fileInfo.add(new Label(FileUtils.byteCountToDisplaySize(file.length())),1,1);


        // filePath.setText(file.getAbsolutePath());
        return file;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public File getFile() {
        return file;
    }
}
