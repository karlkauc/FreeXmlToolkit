/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    XmlController xmlController;

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public final ExecutorService service = Executors.newCachedThreadPool();

    @FXML
    Label version;

    @FXML
    AnchorPane contentPane;

    @FXML
    Button xslt, xml, xsd, xsdValidation, fop, signature, help, settings, exit;

    @FXML
    MenuItem menuItemExit;

    @FXML
    Menu lastOpenFilesMenu;

    @FXML
    Label menuText1, menuText2;

    @FXML
    VBox leftMenu;

    List<File> lastOpenFiles = new LinkedList<>();

    Boolean showMenu = true;

    @FXML
    public void initialize() {
        version.setText("Version: 0.0.1");

        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> version.setText(new Date().toString()));
        }, 1, 2, TimeUnit.SECONDS);

        exit.setOnAction(e -> {
            prepareShutdown();
            System.exit(0);
        });
        menuItemExit.setOnAction(e -> {
            prepareShutdown();
            System.exit(0);
        });
        loadLastOpenFiles();

        loadPageFromPath("/pages/welcome.fxml");
    }

    private void prepareShutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.info("Forcing scheduler shutdown");
            scheduler.shutdownNow();
        }

        service.shutdown();
        try {
            if (!service.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.info("Forcing service shutdown");
            service.shutdownNow();
        }
    }

    private void loadLastOpenFiles() {
        lastOpenFiles.clear();
        lastOpenFiles = propertiesService.getLastOpenFiles();
        logger.debug("Last open Files: {}", lastOpenFiles.toString());

        for (File f : lastOpenFiles) {
            MenuItem m = new MenuItem(f.getName());
            m.setOnAction(event -> {
                logger.debug("File {} selected.", f.getAbsoluteFile().getName());
                if (xmlController != null) {
                    xmlController.displayFileContent(f);
                }
            });
            lastOpenFilesMenu.getItems().add(m);
        }
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
            case "help" -> "/pages/tab_help.fxml";
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

            try {
                Class<?> aClass = loader.getController().getClass();

                if (aClass.equals(XmlController.class)) {
                    ((XmlController) loader.getController()).setParentController(this);
                } else if (aClass.equals(XsdValidationController.class)) {
                    ((XsdValidationController) loader.getController()).setParentController(this);
                } else if (aClass.equals(SettingsController.class)) {
                    ((SettingsController) loader.getController()).setParentController(this);
                } else if (aClass.equals(WelcomeController.class)) {
                    ((WelcomeController) loader.getController()).setParentController(this);
                } else if (aClass.equals(XsdController.class)) {
                    logger.debug("set XSD Controller");
                    ((XsdController) loader.getController()).setParentController(this);
                } else if (aClass.equals(XsltController.class)) {
                    logger.debug("set XSLT Controller");
                    ((XsltController) loader.getController()).setParentController(this);
                }

                var controller = loader.getController();
                logger.debug("Controller Class: {}", controller.getClass());

            } catch (Exception e) {
                logger.error("Error in Controller setting.");
                logger.error(e.getStackTrace());
                logger.error(e.getMessage());
            }

            contentPane.getChildren().clear();
            contentPane.getChildren().add(newLoadedPane);

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(e.getCause());
            logger.error(e.getStackTrace());
        }
    }

    @FXML
    private void toggleMenuBar() {
        logger.debug("Show Menu: {}", showMenu);

        if (showMenu) {
            leftMenu.setMaxWidth(50);
            leftMenu.setMinWidth(50);

            menuText1.setText(">>");
            menuText2.setText("");

            xml.setStyle("-fx-text-fill: transparent;");
            xsd.setStyle("-fx-text-fill: transparent;");
            xsdValidation.setStyle("-fx-text-fill: transparent;");
            xslt.setStyle("-fx-text-fill: transparent;");
            fop.setStyle("-fx-text-fill: transparent;");
            help.setStyle("-fx-text-fill: transparent;");
            settings.setStyle("-fx-text-fill: transparent;");
            exit.setStyle("-fx-text-fill: transparent;");
        } else {
            leftMenu.setMinWidth(200);
            leftMenu.setMaxWidth(200);

            menuText1.setText("FundsXML Toolkit");
            menuText2.setText("Enterprise Edition");

            xml.setStyle("-fx-text-fill: normal;");
            xsd.setStyle("-fx-text-fill: normal;");
            xsdValidation.setStyle("-fx-text-fill: normal;");
            xslt.setStyle("-fx-text-fill: normal;");
            fop.setStyle("-fx-text-fill: normal;");
            help.setStyle("-fx-text-fill: normal;");
            settings.setStyle("-fx-text-fill: normal;");
            exit.setStyle("-fx-text-fill: normal;");
        }

        showMenu = !showMenu;
    }


}
