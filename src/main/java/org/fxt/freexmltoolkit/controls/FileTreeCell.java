package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FileTreeCell extends TreeCell<File> {
    static final Map<String, String> FILE_ICONS = Map.of(
            "xml", "/img/icons8-xml-64.png",
            "xslt", "/img/icons8-transform-64.png"
    );

    @Override
    protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);
        if (file == null || empty) {
            setGraphic(null);
        } else {
            HBox hBox = new HBox();
            hBox.setSpacing(3);

            Text displayText = new Text();
            if (file.isDirectory()) {
                ImageView imgVw = new ImageView();
                var i = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/icons8-opened_folder.png")));
                imgVw.setImage(i);
                imgVw.setFitHeight(16);
                imgVw.setFitWidth(16);

                hBox.getChildren().add(imgVw);
                displayText.setText(file.getName());
                hBox.getChildren().add(displayText);
            } else {
                var ext = FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ROOT);
                if (FILE_ICONS.containsKey(ext)) {
                    ImageView imageView = new ImageView();
                    var image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(FILE_ICONS.get(ext))));
                    imageView.setImage(image);
                    imageView.setFitHeight(16);
                    imageView.setFitWidth(16);

                    hBox.getChildren().add(imageView);
                }
                displayText.setText(file.getName() + " (" + FileUtils.byteCountToDisplaySize(file.length()) + ")");
                hBox.getChildren().add(displayText);
            }
            setGraphic(hBox);
        }
    }

}
