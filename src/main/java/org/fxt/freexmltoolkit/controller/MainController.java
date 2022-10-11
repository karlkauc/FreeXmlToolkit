package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
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

public class MainController {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    XsdController xsdController;

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @FXML
    Label version;

    @FXML
    AnchorPane contentPane;

    @FXML
    Button xslt, xml, xsd, xsdValidation, fop, signature, schematron, help, settings, exit;


    @FXML
    public void initialize() {
        version.setText("Version: 0.0.1");

        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> version.setText(new Date().toString()));
        }, 1, 2, TimeUnit.SECONDS);

        exit.setOnAction(e -> System.exit(0));

        loadPageFromPath("/pages/welcome.fxml");
    }

    @FXML
    public void loadPage(ActionEvent ae) {
        Button currentButton = (Button) ae.getSource();
        String pagePath = switch (currentButton.getId()) {
            case "xslt" -> "/pages/tab_xslt.fxml";
            case "xml" -> "/pages/tab_xml.fxml";
            case "xsd" -> "/pages/tab_xsd.fxml";
            case "xsdValidation" -> "/pages/tab_validation.fxml";
            case "fop" -> "/pages/tab_fop.fxml";
            case "signature" -> "/pages/tab_signature.fxml";
            case "schematron" -> null;
            case "help" -> null;
            case "settings" -> "/pages/settings.fxml";
            default -> null;
        };

        if (pagePath != null) {
            loadPageFromPath(pagePath);
        }

        for (Node node : currentButton.getParent().getChildrenUnmodifiable()) {
            node.getStyleClass().remove("active");
        }
        currentButton.getStyleClass().add("active");

    }

    private void loadPageFromPath(String pagePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(pagePath));
            Pane newLoadedPane = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(newLoadedPane);

            // var controller = loader.getController();

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(e.getCause());
            logger.error(e.getStackTrace());
        }
    }


}
