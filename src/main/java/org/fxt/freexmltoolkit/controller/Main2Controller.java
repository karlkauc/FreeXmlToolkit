package org.fxt.freexmltoolkit.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main2Controller {

    private final static Logger logger = LogManager.getLogger(Main2Controller.class);

    @FXML
    Label version;

    @FXML
    Button reloadButton;


    StringProperty memoryInfo = new SimpleStringProperty();

    @FXML
    MenuItem exit;

    @FXML
    void initialize() {
        version.setText("Version: 0.0.1");
/*
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> version.setText(new Date().toString()));
        }, 1, 2, TimeUnit.SECONDS);
*/
        System.out.println(Runtime.getRuntime().freeMemory() +
                " \t \t " + Runtime.getRuntime().totalMemory() +
                " \t \t " + Runtime.getRuntime().maxMemory());

        // oder MemoryMXBean
        exit.setOnAction(e -> System.exit(0));

    }

    @FXML
    void reloadCss() {
        try {
            System.out.println("RELOAD");
            reloadButton.getScene().getStylesheets().clear();
            reloadButton.getScene().getStylesheets().add("C:\\Data\\src\\FreeXmlToolkit\\src\\main\\resources\\css\\mainTheme.css");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
