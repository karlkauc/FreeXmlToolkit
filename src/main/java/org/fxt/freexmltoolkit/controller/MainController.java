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

/**
 * Hauptcontroller für die Anwendung.
 */
public class MainController {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    XmlController xmlController;
    XsdController xsdController;

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
    CheckMenuItem xmlEditorSidebarMenuItem;

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
        exit.setOnAction(e -> Platform.exit());
        menuItemExit.setOnAction(e -> Platform.exit());
        loadLastOpenFiles();
        loadXmlEditorSidebarPreference();
        loadPageFromPath("/pages/welcome.fxml");
    }

    private void loadXmlEditorSidebarPreference() {
        // Load preference - default is visible (true)
        String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
        boolean isVisible = sidebarVisible == null || Boolean.parseBoolean(sidebarVisible);

        // Set the menu item state
        if (xmlEditorSidebarMenuItem != null) {
            xmlEditorSidebarMenuItem.setSelected(isVisible);
        }

        logger.debug("Loaded XML Editor Sidebar preference: {}", isVisible);
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
        logger.info("Applikation wird beendet. Starte Aufräumarbeiten...");

        // Rufen Sie die Shutdown-Methode für jeden relevanten Controller auf.
        if (xmlController != null) {
            xmlController.shutdown();
        }
        if (xsdController != null) {
            xsdController.shutdown();
        }

        // Fährt die ExecutorServices herunter. Dies ist entscheidend, um Thread-Leaks zu verhindern.
        logger.info("Fahre ExecutorServices herunter...");
        scheduler.shutdownNow();
        service.shutdownNow();
        try {
            // Warten Sie kurz, um den Executoren Zeit zum Beenden zu geben.
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("Scheduler-Dienst wurde nicht innerhalb von 1 Sekunde beendet.");
            }
            if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("Service-Dienst wurde nicht innerhalb von 1 Sekunde beendet.");
            }
        } catch (InterruptedException e) {
            logger.error("Warten auf das Herunterfahren der Dienste wurde unterbrochen.", e);
            Thread.currentThread().interrupt(); // Setzt das Interrupted-Flag erneut.
        }

        logger.info("Aufräumarbeiten abgeschlossen. Anwendung wird geschlossen.");
    }

    /**
     * Lädt die zuletzt geöffneten Dateien und aktualisiert das Menü.
     */
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

                // KORREKTUR: Prüfe den Dateityp und rufe die entsprechende Methode auf.
                String fileName = f.getName().toLowerCase();
                if (fileName.endsWith(".xml")) {
                    switchToXmlViewAndLoadFile(f);
                } else if (fileName.endsWith(".xsd")) {
                    switchToXsdViewAndLoadFile(f);
                } else {
                    logger.warn("Unhandled file type from recent files list: {}", f.getName());
                    // Optional: Einen Alert anzeigen
                    new Alert(Alert.AlertType.INFORMATION, "Dieser Dateityp kann aus der 'Zuletzt geöffnet'-Liste nicht direkt geöffnet werden.").show();
                }
            });
            lastOpenFilesMenu.getItems().add(m);
        }
    }


    /**
     * Öffentliche Methode, die von anderen Controllern aufgerufen werden kann,
     * um eine Datei zur Liste der zuletzt geöffneten Dateien hinzuzufügen.
     *
     * @param file die geöffnete Datei
     */
    public void addFileToRecentFiles(File file) {
        propertiesService.addLastOpenFile(file);
        loadLastOpenFiles();
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

    /**
     * Wechselt programmatisch zum XML-Tab und lädt eine Datei.
     *
     * @param fileToLoad Die zu ladende Datei.
     */
    public void switchToXmlViewAndLoadFile(File fileToLoad) {
        if (xml == null) {
            logger.error("XML-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        // Visuellen Stil des Menü-Buttons anpassen, um den aktiven Tab zu zeigen
        xml.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        xml.getStyleClass().add("active");

        // Die XML-Seite laden
        loadPageFromPath("/pages/tab_xml.fxml");

        // Sicherstellen, dass der XmlController initialisiert ist und die Datei laden.
        // Platform.runLater wird verwendet, um sicherzustellen, dass die UI-Updates
        // nach dem Laden und Anzeigen der neuen Szene ausgeführt werden.
        if (this.xmlController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                // Ruft loadFile auf, um die Datei in einem neuen Tab zu öffnen,
                // anstatt den Inhalt des aktuellen Tabs zu ersetzen.
                xmlController.loadFile(fileToLoad);
            });
        } else {
            logger.warn("XmlController ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
        }
    }

    /**
     * Wechselt programmatisch zum XSD-Tab und lädt eine Datei.
     *
     * @param fileToLoad Die zu ladende XSD-Datei.
     */
    public void switchToXsdViewAndLoadFile(File fileToLoad) {
        if (xsd == null) {
            logger.error("XSD-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        // Visuellen Stil des Menü-Buttons anpassen
        xsd.getParent().getChildrenUnmodifiable().forEach(node -> node.getStyleClass().remove("active"));
        xsd.getStyleClass().add("active");

        // Die XSD-Seite laden
        loadPageFromPath("/pages/tab_xsd.fxml");

        // Sicherstellen, dass der XsdController initialisiert ist und die Datei laden.
        if (this.xsdController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                // Ruft openXsdFile auf, um die Datei in der grafischen und Text-Ansicht zu laden.
                xsdController.openXsdFile(fileToLoad);
                xsdController.selectTextTab();
            });
        } else {
            logger.warn("XsdController ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
        }
    }

    private void setParentController(Object controller) {
        switch (controller) {
            case XmlController xmlController1 -> {
                logger.debug("set XML Controller");
                this.xmlController = xmlController1;
                xmlController1.setParentController(this);
            }
            case XsdValidationController xsdValidationController -> xsdValidationController.setParentController(this);
            case SettingsController settingsController -> settingsController.setParentController(this);
            case WelcomeController welcomeController -> welcomeController.setParentController(this);
            case XsdController xsdController1 -> {
                logger.debug("set XSD Controller");
                this.xsdController = xsdController1;
                xsdController1.setParentController(this);
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
                // helpController.setParentController(this);
                // no need to set the main controller
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
     * Zeigt den "Über"-Dialog an, wenn der entsprechende Menüpunkt geklickt wird.
     */
    @FXML
    private void handleAboutAction() {
        Alert aboutDialog = new Alert(Alert.AlertType.INFORMATION);
        aboutDialog.setTitle("About FreeXMLToolkit");
        aboutDialog.setHeaderText("FreeXMLToolkit - Universal Toolkit for XML");

        // Setzt das Icon für das Dialogfenster
        try {
            Stage stage = (Stage) aboutDialog.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/logo.png"))));
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog window.", e);
        }

        // Setzt die Grafik (Logo) im Header-Panel
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

    @FXML
    private void toggleXmlEditorSidebar() {
        boolean isVisible = xmlEditorSidebarMenuItem.isSelected();
        logger.debug("Toggle XML Editor Sidebar: {}", isVisible);

        // Save preference
        propertiesService.set("xmlEditorSidebar.visible", String.valueOf(isVisible));

        // Apply to current XML controller if available
        if (xmlController != null) {
            xmlController.setXmlEditorSidebarVisible(isVisible);
        }
    }

    /**
     * Gets the current sidebar visibility setting from preferences.
     *
     * @return true if sidebar should be visible, false otherwise
     */
    public boolean isXmlEditorSidebarVisible() {
        String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
        return sidebarVisible == null || Boolean.parseBoolean(sidebarVisible);
    }

    /**
     * Toggles XML Editor Sidebar visibility from the sidebar button.
     * This method synchronizes the menu state and applies the global toggle.
     *
     * @param visible true to show the sidebar, false to hide it
     */
    public void toggleXmlEditorSidebarFromSidebar(boolean visible) {
        logger.debug("Toggle XML Editor Sidebar from sidebar button: {}", visible);

        // Update the menu checkbox to reflect the new state
        if (xmlEditorSidebarMenuItem != null) {
            xmlEditorSidebarMenuItem.setSelected(visible);
        }

        // Save preference
        propertiesService.set("xmlEditorSidebar.visible", String.valueOf(visible));

        // Apply to current XML controller if available
        if (xmlController != null) {
            xmlController.setXmlEditorSidebarVisible(visible);
        }
    }
}