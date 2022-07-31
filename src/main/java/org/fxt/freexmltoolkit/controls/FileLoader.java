package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.File;

public class FileLoader extends VBox {
    FileChooser fileChooser = new FileChooser();
    Button loadButton = new Button("LOAD");
    Text filePath = new Text();

    private String loadPattern;

    File file;

    public FileLoader() {
        this.getChildren().add(loadButton);
        this.getChildren().add(filePath);
    }

    public String getLoadPattern() {
        return loadPattern;
    }

    public void setLoadPattern(String loadPattern) {
        this.loadPattern = loadPattern;
    }

    public File getFileAction() {
        if (loadPattern != null && !loadPattern.isEmpty()) {
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF Files", loadPattern));
        }

        file = fileChooser.showOpenDialog(null);
        filePath.setText(file.getAbsolutePath());
        return file;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public File getFile() {
        return file;
    }
}
