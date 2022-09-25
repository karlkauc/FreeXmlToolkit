package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main2Controller {

    private final static Logger logger = LogManager.getLogger(Main2Controller.class);

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @FXML
    Label version;

    @FXML
    AnchorPane centerPane;

    StringProperty memoryInfo = new SimpleStringProperty();

    @FXML
    Button exit, xml;

    @FXML
    void initialize() {
        version.setText("Version: 0.0.1");

        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> version.setText(new Date().toString()));
        }, 1, 2, TimeUnit.SECONDS);

        System.out.println(Runtime.getRuntime().freeMemory() +
                " \t \t " + Runtime.getRuntime().totalMemory() +
                " \t \t " + Runtime.getRuntime().maxMemory());

        // oder MemoryMXBean
        exit.setOnAction(e -> System.exit(0));

    }

    @FXML
    private void loadXML() {
        try {
            Pane newLoadedPane = FXMLLoader.load(getClass().getResource("/pages/tab_xml.fxml"));
            centerPane.getChildren().add(newLoadedPane);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

    }

}
