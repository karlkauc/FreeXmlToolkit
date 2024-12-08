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
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.apache.commons.io.FileUtils;
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

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    public final ExecutorService service = Executors.newCachedThreadPool();
    final Runtime runtime = Runtime.getRuntime();

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

    @FXML
    ImageView logoImageView;
    List<File> lastOpenFiles = new LinkedList<>();

    Boolean showMenu = true;

    FXMLLoader loader;

    @FXML
    public void initialize() {

        scheduler.scheduleAtFixedRate(() -> {
            String date = new Date().toString();

            long allocated = runtime.totalMemory();
            long used = allocated - runtime.freeMemory();
            long max = runtime.maxMemory();
            long available = max - used;
            var size = String.format("Max: %s Allocated: %s Used: %s Available: %s",
                    FileUtils.byteCountToDisplaySize(max),
                    FileUtils.byteCountToDisplaySize(allocated),
                    FileUtils.byteCountToDisplaySize(used),
                    FileUtils.byteCountToDisplaySize(available));

            var percent = Math.round((float) used / available * 100) + "%";

            Platform.runLater(() -> version.setText(date + " " + size + " " + percent));
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
            loader = new FXMLLoader(getClass().getResource(pagePath));
            Pane newLoadedPane = loader.load();

            System.gc();

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

            logoImageView.setFitHeight(15);
            logoImageView.setFitWidth(75);
            logoImageView.setPreserveRatio(true);

            setButtonSmall(xml);
            setButtonSmall(xsd);
            setButtonSmall(xsdValidation);
            setButtonSmall(xslt);
            setButtonSmall(fop);
            setButtonSmall(help);
            setButtonSmall(settings);
            setButtonSmall(exit);
        } else {
            leftMenu.setMinWidth(200);
            leftMenu.setMaxWidth(200);

            menuText1.setText("FundsXML Toolkit");
            menuText2.setText("Enterprise Edition");

            logoImageView.setFitHeight(75);
            logoImageView.setFitWidth(100);
            logoImageView.setPreserveRatio(true);

            setButtonLarge(xml);
            setButtonLarge(xsd);
            setButtonLarge(xsdValidation);
            setButtonLarge(xslt);
            setButtonLarge(fop);
            setButtonLarge(help);
            setButtonLarge(settings);
            setButtonLarge(exit);
        }

        showMenu = !showMenu;
    }

    private void setButtonSmall(Button buttonSmall) {
        buttonSmall.getStyleClass().remove("menu_button");
        buttonSmall.getStyleClass().add("menu_button_collapsed");
    }

    private void setButtonLarge(Button buttonLarge) {
        buttonLarge.getStyleClass().remove("menu_button_collapsed");
        buttonLarge.getStyleClass().add("menu_button");
    }
}
