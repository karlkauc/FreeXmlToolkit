package org.fxt.freexmltoolkit.controls;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.stage.FileChooser;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Path;

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

    public void toggleLoadButton() {
        logger.debug("Component Visible: {}", isComponentVisible);

        if (isComponentVisible) {
            this.getChildren().removeAll(loadButton, fileInfo);

            for (Node child : this.getChildren()) {
                logger.debug("Entferne Übergebliebene Komponente: {}", child.getId());
                this.getChildren().remove(child);
            }
            this.getChildren().clear();
            this.setVisible(false);
            this.setPrefWidth(0);
            this.setDisable(true);
        }
        else {
            this.getChildren().addAll(loadButton, fileInfo);
            this.setVisible(true);
            this.setPrefWidth(300);
            this.setDisable(false);
        }
        isComponentVisible = !isComponentVisible;
    }

    public void setButtonText(String buttonText) {
        loadButton.setText(buttonText);
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

        fileInfo.getChildren().clear();
        Text labelFileName = new Text("Filename:");
        Text labelFileNameValue = new Text(file.getName());
        fileInfo.add(labelFileName, 0, 0);
        fileInfo.add(labelFileNameValue, 1, 0);

        labelFileNameValue.setBoundsType(TextBoundsType.VISUAL);
        labelFileName.setBoundsType(TextBoundsType.VISUAL);

        fileInfo.add(new Label("Size:"), 0, 1);
        fileInfo.add(new Label(FileUtils.byteCountToDisplaySize(file.length())), 1, 1);

        // filePath.setText(file.getAbsolutePath());
        return file;
    }

    private void setLayout() {
        this.setStyle("-fx-padding: 7px; ");
        fileInfo.setHgap(10);
        fileInfo.setVgap(10);
        fileInfo.setGridLinesVisible(true);
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public File getFile() {
        return file;
    }
}
