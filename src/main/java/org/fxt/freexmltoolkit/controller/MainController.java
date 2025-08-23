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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    XmlController xmlController;
    XsdController xsdController;
    SchematronController schematronController;

    // Integration service for cross-controller communication
    private org.fxt.freexmltoolkit.service.SchematronXmlIntegrationService integrationService;

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    public final ExecutorService service = Executors.newCachedThreadPool();
    final Runtime runtime = Runtime.getRuntime();

    @FXML
    Label version;

    @FXML
    AnchorPane contentPane;

    @FXML
    Button xslt, xml, xsd, xsdValidation, schematron, fop, signature, help, settings, exit;

    @FXML
    MenuItem menuItemExit;

    @FXML
    Menu lastOpenFilesMenu;

    @FXML
    CheckMenuItem xmlEditorSidebarMenuItem;

    @FXML
    CheckMenuItem xpathQueryPaneMenuItem;

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
        scheduler.scheduleAtFixedRate(this::updateMemoryUsage, 1, 2, TimeUnit.SECONDS);
        exit.setOnAction(e -> Platform.exit());
        menuItemExit.setOnAction(e -> Platform.exit());
        loadLastOpenFiles();
        loadXmlEditorSidebarPreference();
        loadXPathQueryPanePreference();
        loadPageFromPath("/pages/welcome.fxml");
        Platform.runLater(this::applyTheme);
    }

    public void applyTheme() {
        try {
            Scene scene = contentPane.getScene();
            if (scene == null) {
                logger.warn("Scene not available. Cannot apply theme yet.");
                return;
            }

            scene.getStylesheets().removeIf(s -> s.contains("light-theme.css") || s.contains("dark-theme.css"));

            String theme = propertiesService.get("ui.theme");
            logger.debug("Attempting to apply theme: {}", theme);

            if ("dark".equals(theme)) {
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/dark-theme.css")).toExternalForm());
                logger.info("Dark theme applied.");
            } else {
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/light-theme.css")).toExternalForm());
                logger.info("Light theme applied.");
            }
        } catch (Exception e) {
            logger.error("Could not apply theme. Make sure dark-theme.css and light-theme.css are in the resources/css folder.", e);
        }
    }

    private void loadXmlEditorSidebarPreference() {
        String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
        boolean isVisible = sidebarVisible == null || Boolean.parseBoolean(sidebarVisible);

        if (xmlEditorSidebarMenuItem != null) {
            xmlEditorSidebarMenuItem.setSelected(isVisible);
        }

        logger.debug("Loaded XML Editor Sidebar preference: {}", isVisible);
    }

    private void loadXPathQueryPanePreference() {
        String paneVisible = propertiesService.get("xpathQueryPane.visible");
        boolean isVisible = paneVisible == null || Boolean.parseBoolean(paneVisible);

        if (xpathQueryPaneMenuItem != null) {
            xpathQueryPaneMenuItem.setSelected(isVisible);
        }

        logger.debug("Loaded XPath Query Pane preference: {}", isVisible);
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

    @FXML
    public void shutdown() {
        logger.info("Application is shutting down. Starting cleanup tasks...");

        if (xmlController != null) {
            xmlController.shutdown();
        }
        if (xsdController != null) {
            xsdController.shutdown();
        }

        logger.info("Shutting down ExecutorServices...");
        scheduler.shutdownNow();
        service.shutdownNow();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("Scheduler service was not terminated within 1 second.");
            }
            if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("Service was not terminated within 1 second.");
            }
        } catch (InterruptedException e) {
            logger.error("Waiting for service shutdown was interrupted.", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Cleanup tasks completed. Application will be closed.");
    }

    private void loadLastOpenFiles() {
        lastOpenFilesMenu.getItems().clear();
        lastOpenFiles.clear();

        lastOpenFiles = propertiesService.getLastOpenFiles();
        logger.debug("Last open Files: {}", lastOpenFiles.toString());

        if (lastOpenFiles.isEmpty()) {
            lastOpenFilesMenu.setDisable(true);
            return;
        }

        lastOpenFilesMenu.setDisable(false);
        for (File f : lastOpenFiles) {
            MenuItem m = new MenuItem(f.getName());
            m.setOnAction(event -> {
                logger.debug("File {} selected from recent files.", f.getAbsolutePath());

                String fileName = f.getName().toLowerCase();
                if (fileName.endsWith(".xml")) {
                    switchToXmlViewAndLoadFile(f);
                } else if (fileName.endsWith(".xsd")) {
                    switchToXsdViewAndLoadFile(f);
                } else if (fileName.endsWith(".sch") || fileName.endsWith(".schematron")) {
                    switchToSchematronViewAndLoadFile(f);
                } else {
                    logger.warn("Unhandled file type from recent files list: {}", f.getName());
                    new Alert(Alert.AlertType.INFORMATION, "This file type cannot be opened directly from the 'Recently opened' list.").show();
                }
            });
            lastOpenFilesMenu.getItems().add(m);
        }
    }

    public void addFileToRecentFiles(File file) {
        propertiesService.addLastOpenFile(file);
        loadLastOpenFiles();
    }

    @FXML
    public void loadPage(ActionEvent ae) {
        Button currentButton = (Button) ae.getSource();
        String pagePath = switch (currentButton.getId()) {
            case "xslt" -> "/pages/tab_xslt.fxml";
            case "xml" -> "/pages/tab_xml.fxml";
            case "xsd" -> "/pages/tab_xsd.fxml";
            case "xsdValidation" -> "/pages/tab_validation.fxml";
            case "schematron" -> "/pages/tab_schematron.fxml";
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

    private void loadPageFromPath(String pagePath) {
        try {
            loader = new FXMLLoader(getClass().getResource(pagePath));
            Pane newLoadedPane = loader.load();
            setParentController(loader.getController());
            contentPane.getChildren().clear();
            contentPane.getChildren().add(newLoadedPane);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void switchToXmlViewAndLoadFile(File fileToLoad) {
        if (xml == null) {
            logger.error("XML-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        xml.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        xml.getStyleClass().add("active");

        loadPageFromPath("/pages/tab_xml.fxml");

        if (this.xmlController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                xmlController.loadFile(fileToLoad);
            });
        } else {
            logger.warn("XmlController ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
        }
    }

    public void switchToXsdViewAndLoadFile(File fileToLoad) {
        if (xsd == null) {
            logger.error("XSD-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        xsd.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        xsd.getStyleClass().add("active");

        loadPageFromPath("/pages/tab_xsd.fxml");

        if (this.xsdController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                xsdController.openXsdFile(fileToLoad);
                xsdController.selectTextTab();
            });
        } else {
            logger.warn("XsdController ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
        }
    }

    public void switchToSchematronViewAndLoadFile(File fileToLoad) {
        if (schematron == null) {
            logger.error("Schematron-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        schematron.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        schematron.getStyleClass().add("active");

        loadPageFromPath("/pages/tab_schematron.fxml");

        if (this.schematronController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                schematronController.loadSchematronFile(fileToLoad);
            });
        } else {
            logger.warn("SchematronController ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
        }
    }

    private void setParentController(Object controller) {
        switch (controller) {
            case XmlController xmlController1 -> {
                logger.debug("set XML Controller");
                this.xmlController = xmlController1;
                xmlController1.setParentController(this);
                initializeIntegrationService();
            }
            case XsdValidationController xsdValidationController -> xsdValidationController.setParentController(this);
            case SettingsController settingsController -> settingsController.setParentController(this);
            case WelcomeController welcomeController -> welcomeController.setParentController(this);
            case XsdController xsdController1 -> {
                logger.debug("set XSD Controller");
                this.xsdController = xsdController1;
                xsdController1.setParentController(this);
            }
            case SchematronController schematronController1 -> {
                logger.debug("set Schematron Controller");
                this.schematronController = schematronController1;
                schematronController1.setParentController(this);
                initializeIntegrationService();
            }
            case XsltController xsltController -> {
                logger.debug("set XSLT Controller");
                xsltController.setParentController(this);
            }
            case FopController fopController -> {
                logger.debug("set FOP Controller");
                fopController.setParentController(this);
            }
            case SignatureController signatureController -> {
                logger.debug("set Signature Controller");
                signatureController.setParentController(this);
            }
            case HelpController helpController -> {
                logger.debug("set Help Controller");
            }
            case null, default -> {
                if (controller != null) {
                    logger.error("no valid controller found: {}", controller.getClass());
                } else {
                    logger.error("no controller found");
                }
            }
        }
        if (controller != null) {
            logger.debug("Controller Class: {}", controller.getClass());
        }
    }

    /**
     * Initialize the integration service for cross-controller communication
     */
    private void initializeIntegrationService() {
        if (integrationService == null) {
            integrationService = new org.fxt.freexmltoolkit.service.SchematronXmlIntegrationService();
        }

        // Initialize the service with available controllers
        integrationService.initialize(this, xmlController, schematronController);

        logger.debug("Integration service initialized with XML: {}, Schematron: {}",
                xmlController != null, schematronController != null);
    }

    /**
     * Get the integration service for cross-controller communication
     */
    public org.fxt.freexmltoolkit.service.SchematronXmlIntegrationService getIntegrationService() {
        return integrationService;
    }

    @FXML
    private void handleAboutAction() {
        Alert aboutDialog = new Alert(Alert.AlertType.INFORMATION);
        aboutDialog.setTitle("About FreeXMLToolkit");
        aboutDialog.setHeaderText("FreeXMLToolkit - Universal Toolkit for XML");

        try {
            Stage stage = (Stage) aboutDialog.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/logo.png"))));
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog window.", e);
        }

        try {
            ImageView logo = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/logo.png"))));
            logo.setFitHeight(60);
            logo.setPreserveRatio(true);
            aboutDialog.setGraphic(logo);
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog graphic.", e);
        }

        aboutDialog.setContentText(
                """
                        Version: 2024.1
                        Copyright (c) Karl Kauc 2024.
                        
                        Dieses Produkt ist unter der Apache License, Version 2.0, lizenziert. \
                        Eine Kopie der Lizenz erhalten Sie unter:
                        http://www.apache.org/licenses/LICENSE-2.0"""
        );

        aboutDialog.showAndWait();
    }

    @FXML
    private void toggleMenuBar() {
        logger.debug("Show Menu: {}", showMenu);
        if (showMenu) {
            setMenuSize(50, ">>", "", 15, 75);
            setButtonSize("menu_button_collapsed", xml, xsd, xsdValidation, schematron, xslt, fop, help, settings, exit, signature);
        } else {
            setMenuSize(200, "FundsXML Toolkit", "Enterprise Edition", 75, 100);
            setButtonSize("menu_button", xml, xsd, xsdValidation, schematron, xslt, fop, help, settings, exit, signature);
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

    @FXML
    private void toggleXmlEditorSidebar() {
        boolean isVisible = xmlEditorSidebarMenuItem.isSelected();
        logger.debug("Toggle XML Editor Sidebar: {}", isVisible);

        propertiesService.set("xmlEditorSidebar.visible", String.valueOf(isVisible));

        if (xmlController != null) {
            xmlController.setXmlEditorSidebarVisible(isVisible);
        }
    }

    public boolean isXmlEditorSidebarVisible() {
        String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
        return sidebarVisible == null || Boolean.parseBoolean(sidebarVisible);
    }

    public void toggleXmlEditorSidebarFromSidebar(boolean visible) {
        logger.debug("Toggle XML Editor Sidebar from sidebar button: {}", visible);

        if (xmlEditorSidebarMenuItem != null) {
            xmlEditorSidebarMenuItem.setSelected(visible);
        }

        propertiesService.set("xmlEditorSidebar.visible", String.valueOf(visible));

        if (xmlController != null) {
            xmlController.setXmlEditorSidebarVisible(visible);
        }
    }

    @FXML
    private void toggleXPathQueryPane() {
        boolean isVisible = xpathQueryPaneMenuItem.isSelected();
        logger.debug("Toggle XPath Query Pane: {}", isVisible);

        propertiesService.set("xpathQueryPane.visible", String.valueOf(isVisible));

        if (xmlController != null) {
            xmlController.setXPathQueryPaneVisible(isVisible);
        } else {
            logger.debug("XmlController not yet available - preference saved, will be applied when XML tab is loaded");
        }
    }

    public boolean isXPathQueryPaneVisible() {
        String paneVisible = propertiesService.get("xpathQueryPane.visible");
        return paneVisible == null || Boolean.parseBoolean(paneVisible);
    }

    @FXML
    private void handleNewFile() {
        logger.debug("New file action triggered");

        ChoiceDialog<String> dialog = new ChoiceDialog<>("XML", "XML", "XSD", "XSLT");
        dialog.setTitle("New File");
        dialog.setHeaderText("Select File Type");
        dialog.setContentText("Choose the type of file you want to create:");

        dialog.showAndWait().ifPresent(fileType -> {
            logger.debug("Selected file type: {}", fileType);
            switch (fileType) {
                case "XML" -> {
                    xml.fire();
                    Platform.runLater(() -> {
                        if (xmlController != null) {
                            xmlController.newFilePressed();
                        }
                    });
                }
                case "XSD" -> {
                    xsd.fire();
                }
                case "XSLT" -> {
                    xslt.fire();
                }
            }
        });
    }

    @FXML
    private void handleOpenFile() {
        logger.debug("Open file action triggered");

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Open File");

        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("All Supported Files", "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron"),
                new javafx.stage.FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"),
                new javafx.stage.FileChooser.ExtensionFilter("XSD files (*.xsd)", "*.xsd"),
                new javafx.stage.FileChooser.ExtensionFilter("XSLT files (*.xsl, *.xslt)", "*.xsl", "*.xslt"),
                new javafx.stage.FileChooser.ExtensionFilter("Schematron files (*.sch, *.schematron)", "*.sch", "*.schematron"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File selectedFile = fileChooser.showOpenDialog(contentPane.getScene().getWindow());

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected file: {}", selectedFile.getAbsolutePath());

            if (selectedFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(selectedFile.getParentFile().getAbsolutePath());
            }

            String fileName = selectedFile.getName().toLowerCase();
            if (fileName.endsWith(".xml")) {
                xml.fire();
                Platform.runLater(() -> {
                    if (xmlController != null) {
                        xmlController.loadFile(selectedFile);
                    }
                });
            } else if (fileName.endsWith(".xsd")) {
                xsd.fire();
                Platform.runLater(() -> {
                    if (xsdController != null) {
                        xsdController.openXsdFile(selectedFile);
                    }
                });
            } else if (fileName.endsWith(".sch") || fileName.endsWith(".schematron")) {
                schematron.fire();
                Platform.runLater(() -> {
                    if (schematronController != null) {
                        schematronController.loadSchematronFile(selectedFile);
                    }
                });
            } else if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
                xslt.fire();
            } else {
                xml.fire();
                Platform.runLater(() -> {
                    if (xmlController != null) {
                        xmlController.loadFile(selectedFile);
                    }
                });
            }

            addFileToRecentFiles(selectedFile);
        }
    }
}
