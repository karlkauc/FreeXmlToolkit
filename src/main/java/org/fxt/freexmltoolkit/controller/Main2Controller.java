package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main2Controller {

    private final static Logger logger = LogManager.getLogger(Main2Controller.class);

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @FXML
    Label version;

    @FXML
    AnchorPane contentPane;
    StringProperty memoryInfo = new SimpleStringProperty();

    @FXML
    Button xslt, xml, xsd, xsdValidation, fop, signature, schematron, help, settings, exit;

    @FXML
    void initialize() {
        version.setText("Version: 0.0.1");

        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> version.setText(new Date().toString()));
        }, 1, 2, TimeUnit.SECONDS);

/*
        System.out.println(Runtime.getRuntime().freeMemory() +
                " \t \t " + Runtime.getRuntime().totalMemory() +
                " \t \t " + Runtime.getRuntime().maxMemory());
*/

        // oder MemoryMXBean
        exit.setOnAction(e -> System.exit(0));

    }

    @FXML
    public void loadPage(ActionEvent ae) {
        String pagePath = switch (((Button) ae.getSource()).getId()) {
            case "xslt" -> null;
            case "xml" -> "/pages/tab_xml.fxml";
            case "xsd" -> "/pages/tab_xsd.fxml";
            case "xsdValidation" -> "/pages/tab_validation.fxml";
            case "fop" -> "/pages/tab_fop.fxml";
            case "signature" -> "/pages/tab_signature.fxml";
            case "schematron" -> null;
            case "help" -> null;
            case "settings" -> null;
            default -> null;
        };

        if (pagePath != null) {
            try {
                Pane newLoadedPane = FXMLLoader.load(Objects.requireNonNull(getClass().getResource(pagePath)));
                contentPane.getChildren().clear();
                contentPane.getChildren().add(newLoadedPane);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

}
