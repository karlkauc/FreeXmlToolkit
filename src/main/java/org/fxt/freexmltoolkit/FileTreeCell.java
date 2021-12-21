package org.fxt.freexmltoolkit;

import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

import java.io.File;
import java.util.Locale;

public class FileTreeCell extends TreeCell<File> {
    @Override
    protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);
        if (file == null || empty) {
            setGraphic(null);
        } else {
            HBox hBox = new HBox();
            hBox.setSpacing(3);

            Text t = new Text();
            if (file.isDirectory()) {
                ImageView imgVw = new ImageView();
                var i = new Image(getClass().getResourceAsStream("/img/icons8-opened_folder.png"));
                imgVw.setImage(i);
                imgVw.setFitHeight(16);
                imgVw.setFitWidth(16);

                hBox.getChildren().add(imgVw);
                t.setText(file.getName());
                hBox.getChildren().add(t);
            } else {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    ImageView imgVw = new ImageView();
                    var i = new Image(getClass().getResourceAsStream("/img/icons8-xml-64.png"));
                    imgVw.setImage(i);
                    imgVw.setFitHeight(16);
                    imgVw.setFitWidth(16);

                    hBox.getChildren().add(imgVw);

                }
                t.setText(file.getName() + ":" + file.length());
                hBox.getChildren().add(t);
            }
            setGraphic(hBox);
        }
    }

}
