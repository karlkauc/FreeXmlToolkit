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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hauptcontroller für die Anwendung.
 */
public class MainController {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    Properties properties = propertiesService.loadProperties();

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

    /**
     * Initialisiert den Controller.
     */
    @FXML
    public void initialize() {
        scheduler.scheduleAtFixedRate(this::updateMemoryUsage, 1, 2, TimeUnit.SECONDS);
        exit.setOnAction(e -> shutdown());
        menuItemExit.setOnAction(e -> shutdown());
        loadLastOpenFiles();
        loadPageFromPath("/pages/welcome.fxml");
    }

    private void updateMemoryUsage() {
        String date = new Date().toString();
        long allocated = runtime.totalMemory();
        long used = allocated - runtime.freeMemory();
        long max = runtime.maxMemory();
        long available = max - used;
        String size = String.format("Max: %s Allocated: %s Used: %s Available: %s",
                FileUtils.byteCountToDisplaySize(max),
                FileUtils.byteCountToDisplaySize(allocated),
                FileUtils.byteCountToDisplaySize(used),
                FileUtils.byteCountToDisplaySize(available));
        String percent = Math.round((float) used / available * 100) + "%";
        Platform.runLater(() -> version.setText(date + " " + size + " " + percent));
    }

    /**
     * Bereitet das Herunterfahren der Anwendung vor.
     */
    private void shutdown() {
        prepareShutdown();
        System.exit(0);
    }

    private void prepareShutdown() {
        shutdownExecutor(scheduler);
        shutdownExecutor(service);
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.info("Forcing executor shutdown");
            executor.shutdownNow();
        }
    }

    /**
     * Lädt die zuletzt geöffneten Dateien.
     */
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

    /**
     * Lädt eine Seite basierend auf dem ActionEvent.
     *
     * @param ae das ActionEvent
     */
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

        currentButton.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        currentButton.getStyleClass().add("active");

    }

    /**
     * Lädt eine Seite von einem bestimmten Pfad.
     *
     * @param pagePath der Pfad zur Seite
     */
    private void loadPageFromPath(String pagePath) {
        try {
            loader = new FXMLLoader(getClass().getResource(pagePath));
            Pane newLoadedPane = loader.load();
            System.gc();
            setParentController(loader.getController());
            contentPane.getChildren().clear();
            contentPane.getChildren().add(newLoadedPane);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void setParentController(Object controller) {
        if (controller instanceof XmlController) {
            ((XmlController) controller).setParentController(this);
        } else if (controller instanceof XsdValidationController) {
            ((XsdValidationController) controller).setParentController(this);
        } else if (controller instanceof SettingsController) {
            ((SettingsController) controller).setParentController(this);
        } else if (controller instanceof WelcomeController) {
            ((WelcomeController) controller).setParentController(this);
        } else if (controller instanceof XsdController) {
            logger.debug("set XSD Controller");
            ((XsdController) controller).setParentController(this);
        } else if (controller instanceof XsltController) {
            logger.debug("set XSLT Controller");
            ((XsltController) controller).setParentController(this);
        } else if (controller instanceof FopController) {
            logger.debug("set FOP Controller");
            ((FopController) controller).setParentController(this);
        } else if (controller instanceof SignatureController) {
            logger.debug("set Signature Controller");
            ((SignatureController) controller).setParentController(this);
        }
        logger.debug("Controller Class: {}", controller.getClass());
    }

    @FXML
    private void toggleMenuBar() {
        logger.debug("Show Menu: {}", showMenu);
        if (showMenu) {
            setMenuSize(50, ">>", "", 15, 75);
            setButtonSize("menu_button_collapsed", xml, xsd, xsdValidation, xslt, fop, help, settings, exit, signature);
        } else {
            setMenuSize(200, "FundsXML Toolkit", "Enterprise Edition", 75, 100);
            setButtonSize("menu_button", xml, xsd, xsdValidation, xslt, fop, help, settings, exit, signature);
        }
        showMenu = !showMenu;
    }

    private void setMenuSize(int width, String text1, String text2, int logoHeight, int logoWidth) {
        leftMenu.setMaxWidth(width);
        leftMenu.setMinWidth(width);
        menuText1.setText(text1);
        menuText2.setText(text2);
        logoImageView.setFitHeight(logoHeight);
        logoImageView.setFitWidth(logoWidth);
        logoImageView.setPreserveRatio(true);
    }

    private void setButtonSize(String styleClass, Button... buttons) {
        for (Button button : buttons) {
            button.getStyleClass().remove("menu_button");
            button.getStyleClass().remove("menu_button_collapsed");
            button.getStyleClass().add(styleClass);
        }
    }
}