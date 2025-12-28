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
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.ModernXmlThemeManager;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateNotificationDialog;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateProgressDialog;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController implements Initializable {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    XmlUltimateController xmlUltimateController;
    XsdController xsdController;
    SchematronController schematronController;
    XsdValidationController xsdValidationController;
    FopController fopController;
    XsltController xsltController;
    XsltDeveloperController xsltDeveloperController;
    SchemaGeneratorController schemaGeneratorController;
    TemplatesController templatesController;
    UnifiedEditorController unifiedEditorController;

    // Track currently active tab
    private String activeTabId = "welcome";

    // Integration service for cross-controller communication
    private org.fxt.freexmltoolkit.service.SchematronXmlIntegrationService integrationService;

    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    public final ExecutorService service = Executors.newCachedThreadPool();
    final Runtime runtime = Runtime.getRuntime();
    private boolean shutdownCalled = false;

    @FXML
    Label version;

    @FXML
    AnchorPane contentPane;

    @FXML
    Button xslt, xmlUltimate, xsd, xsdValidation, schematron, fop, signature, help, settings, exit, xsltDeveloper, schemaGenerator, unifiedEditor; // templates removed

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

    @FXML
    MenuItem menuItemUndo;

    @FXML
    MenuItem menuItemRedo;

    @FXML
    MenuItem menuItemSave;

    @FXML
    MenuItem menuItemSaveAs;

    @FXML
    MenuItem menuItemClose;

    @FXML
    MenuItem menuItemCloseAll;

    @FXML
    MenuItem menuItemSettings;

    @FXML
    MenuItem menuItemFind;

    @FXML
    MenuItem menuItemFindReplace;

    @FXML
    MenuItem menuItemFullScreen;

    @FXML
    MenuItem menuItemUserGuide;

    @FXML
    MenuItem menuItemKeyboardShortcuts;

    @FXML
    MenuItem menuItemCheckUpdates;

    @FXML
    MenuItem menuItemReportIssue;

    @FXML
    MenuItem menuItemAbout;

    List<File> lastOpenFiles = new LinkedList<>();

    Boolean showMenu = true;

    FXMLLoader loader;

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

    private void loadXmlEditorTheme() {
        String xmlTheme = propertiesService.get("xml.editor.theme");
        if (xmlTheme != null && !xmlTheme.isEmpty()) {
            ModernXmlThemeManager themeManager = ModernXmlThemeManager.getInstance();
            themeManager.setCurrentThemeByDisplayName(xmlTheme);
            logger.info("Loaded XML editor theme: {}", xmlTheme);
        } else {
            logger.debug("No XML editor theme preference found, using default");
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
        // Get version from UpdateCheckService (which has the correct fallback version)
        UpdateCheckService updateCheckService = ServiceRegistry.get(UpdateCheckService.class);
        String appVersion = updateCheckService != null ? updateCheckService.getCurrentVersion() : "1.1.0";

        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        String usedStr = FileUtils.byteCountToDisplaySize(used);
        String maxStr = FileUtils.byteCountToDisplaySize(max);
        int percent = Math.round((float) used / max * 100);

        String statusText = String.format("v%s | Memory: %s / %s (%d%%)", appVersion, usedStr, maxStr, percent);
        Platform.runLater(() -> version.setText(statusText));
    }

    @FXML
    public void shutdown() {
        // Prevent double shutdown
        if (shutdownCalled) {
            logger.info("Shutdown already called, skipping duplicate call");
            return;
        }
        shutdownCalled = true;

        logger.info("Application is shutting down. Starting cleanup tasks...");

        // Shutdown XmlUltimateController and its ExecutorService
        if (xmlUltimateController != null) {
            xmlUltimateController.shutdown();
        }

        // Shutdown XsdController
        if (xsdController != null) {
            xsdController.shutdown();
        }

        // Shutdown XsltController
        if (xsltController != null) {
            xsltController.shutdown();
        }

        // Shutdown XsltDeveloperController
        if (xsltDeveloperController != null) {
            xsltDeveloperController.shutdown();
        }

        // Shutdown SchemaGeneratorController
        if (schemaGeneratorController != null) {
            schemaGeneratorController.shutdown();
        }

        // Shutdown UpdateCheckService
        try {
            UpdateCheckService updateCheckService = ServiceRegistry.get(UpdateCheckService.class);
            if (updateCheckService != null) {
                updateCheckService.shutdown();
            }
        } catch (Exception e) {
            logger.warn("Failed to shutdown UpdateCheckService: {}", e.getMessage());
        }

        // Shutdown XsltTransformationEngine
        try {
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.getInstance().shutdown();
            logger.debug("XsltTransformationEngine shut down");
        } catch (Throwable e) {
            logger.warn("Failed to shutdown XsltTransformationEngine: {}", e.getMessage());
        }

        // Shutdown XPathExecutionEngine
        try {
            org.fxt.freexmltoolkit.service.XPathExecutionEngine.getInstance().shutdown();
            logger.debug("XPathExecutionEngine shut down");
        } catch (Throwable e) {
            logger.warn("Failed to shutdown XPathExecutionEngine: {}", e.getMessage());
        }

        // Shutdown ThreadPoolManager
        try {
            org.fxt.freexmltoolkit.service.ThreadPoolManager.getInstance().shutdown();
            logger.debug("ThreadPoolManager shut down");
        } catch (Throwable e) {
            logger.warn("Failed to shutdown ThreadPoolManager: {}", e.getMessage());
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

    /**
     * Handles application exit by triggering JavaFX shutdown sequence.
     * Platform.exit() will automatically call FxtGui.stop() which handles all cleanup.
     */
    @FXML
    private void handleExit() {
        logger.info("=== EXIT BUTTON CLICKED - Starting shutdown sequence ===");

        try {
            // Perform cleanup first
            logger.info("Step 1: Calling shutdown() for cleanup");
            shutdown();

            logger.info("Step 2: Calling Platform.exit() to trigger normal JavaFX shutdown");
            Platform.exit();

            // Platform.exit() will trigger the stop() method in FxtGui, which will save usageDuration
            // No need to call System.exit(0) - let JavaFX handle the shutdown lifecycle
        } catch (Exception e) {
            logger.error("Error during exit sequence", e);
            // On error, still try to exit gracefully
            Platform.exit();
        }
    }

    /**
     * Refreshes toolbar icons across all controllers based on the current small icons setting.
     * This method should be called when the user changes the icon size preference in settings.
     */
    public void refreshToolbarIcons() {
        logger.debug("Refreshing toolbar icons across all controllers");

        // Refresh XML Ultimate Controller toolbar
        if (xmlUltimateController != null) {
            xmlUltimateController.refreshToolbarIcons();
            logger.debug("Refreshed XML Ultimate Controller toolbar icons");
        }

        // Refresh XSD Controller toolbar
        if (xsdController != null) {
            xsdController.refreshToolbarIcons();
            logger.debug("Refreshed XSD Controller toolbar icons");
        }

        // Refresh Schematron Controller toolbar
        if (schematronController != null) {
            schematronController.refreshToolbarIcons();
            logger.debug("Refreshed Schematron Controller toolbar icons");
        }

        // Refresh XSLT Controller toolbar
        if (xsltController != null) {
            xsltController.refreshToolbarIcons();
            logger.debug("Refreshed XSLT Controller toolbar icons");
        }

        // Refresh XSLT Developer Controller toolbar
        if (xsltDeveloperController != null) {
            xsltDeveloperController.refreshToolbarIcons();
            logger.debug("Refreshed XSLT Developer Controller toolbar icons");
        }

        // Refresh FOP Controller toolbar
        if (fopController != null) {
            fopController.refreshToolbarIcons();
            logger.debug("Refreshed FOP Controller toolbar icons");
        }

        // Refresh Schema Generator Controller toolbar
        if (schemaGeneratorController != null) {
            schemaGeneratorController.refreshToolbarIcons();
            logger.debug("Refreshed Schema Generator Controller toolbar icons");
        }

        // Refresh XSD Validation Controller toolbar
        if (xsdValidationController != null) {
            xsdValidationController.refreshToolbarIcons();
            logger.debug("Refreshed XSD Validation Controller toolbar icons");
        }

        // Refresh Templates Controller toolbar
        if (templatesController != null) {
            templatesController.refreshToolbarIcons();
            logger.debug("Refreshed Templates Controller toolbar icons");
        }

        logger.info("Toolbar icon refresh completed for all controllers");
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
                    DialogHelper.showWarning("Open File", "Unsupported File Type",
                        "This file type cannot be opened directly from the 'Recently opened' list.\n"
                        + "Please use the specific tab for this file type.");
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
        String buttonId = currentButton.getId();
        String pagePath = switch (buttonId) {
            case "xslt" -> "/pages/tab_xslt.fxml";
            case "xml" -> "/pages/tab_xml.fxml";
            case "xmlEnhanced" -> "/pages/tab_xml_enhanced.fxml";
            case "xmlNew" -> "/pages/tab_xml_new.fxml";
            case "xmlUltimate" -> "/pages/tab_xml_ultimate.fxml";
            case "xsd" -> "/pages/tab_xsd.fxml";
            case "xsdValidation" -> "/pages/tab_validation.fxml";
            case "schematron" -> "/pages/tab_schematron.fxml";
            case "fop" -> "/pages/tab_fop.fxml";
            case "signature" -> "/pages/tab_signature.fxml";
            case "help" -> "/pages/tab_help.fxml";
            case "settings" -> "/pages/settings.fxml";
            // Revolutionary Features - Alleinstellungsmerkmale
            // case "templates" -> "/pages/tab_templates.fxml"; // Removed from menu
            case "schemaGenerator" -> "/pages/tab_schema_generator.fxml";
            case "xsltDeveloper" -> "/pages/tab_xslt_developer.fxml";
            case "unifiedEditor" -> "/pages/tab_unified_editor.fxml";
            default -> null;
        };

        if (pagePath != null) {
            loadPageFromPath(pagePath);
            activeTabId = buttonId; // Track active tab
            logger.debug("Active tab changed to: {}", activeTabId);
        }

        // Remove "active" from ALL menu buttons (they are split across two VBox containers)
        removeActiveFromAllMenuButtons();
        currentButton.getStyleClass().add("active");
    }

    /**
     * Removes the "active" style class from all menu buttons.
     * This is needed because menu buttons are split across two VBox containers (top and bottom).
     */
    private void removeActiveFromAllMenuButtons() {
        Button[] allMenuButtons = {
            xmlUltimate, xsd, xsdValidation, schematron, xslt, fop,
            signature, help, settings, schemaGenerator, xsltDeveloper, unifiedEditor
        };
        for (Button btn : allMenuButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
    }

    /**
     * Get the currently active tab ID.
     * @return the active tab ID (e.g., "xmlUltimate", "xsd", "schematron", etc.)
     */
    public String getActiveTabId() {
        return activeTabId;
    }

    /**
     * Navigates to a page by its ID.
     * This method can be called from other controllers (like WelcomeController) to switch pages.
     *
     * @param pageId the page ID (e.g., "xmlUltimate", "xsd", "schematron", "fop", "signature", etc.)
     */
    public void navigateToPage(String pageId) {
        String pagePath = switch (pageId) {
            case "xslt" -> "/pages/tab_xslt.fxml";
            case "xml" -> "/pages/tab_xml.fxml";
            case "xmlEnhanced" -> "/pages/tab_xml_enhanced.fxml";
            case "xmlNew" -> "/pages/tab_xml_new.fxml";
            case "xmlUltimate" -> "/pages/tab_xml_ultimate.fxml";
            case "xsd" -> "/pages/tab_xsd.fxml";
            case "xsdValidation" -> "/pages/tab_validation.fxml";
            case "schematron" -> "/pages/tab_schematron.fxml";
            case "fop" -> "/pages/tab_fop.fxml";
            case "signature" -> "/pages/tab_signature.fxml";
            case "help" -> "/pages/tab_help.fxml";
            case "settings" -> "/pages/settings.fxml";
            case "schemaGenerator" -> "/pages/tab_schema_generator.fxml";
            case "xsltDeveloper" -> "/pages/tab_xslt_developer.fxml";
            case "unifiedEditor" -> "/pages/tab_unified_editor.fxml";
            default -> null;
        };

        if (pagePath != null) {
            loadPageFromPath(pagePath);
            activeTabId = pageId;
            logger.debug("Navigated to page: {}", pageId);

            // Update menu button active state
            removeActiveFromAllMenuButtons();
            Button targetButton = getButtonForPageId(pageId);
            if (targetButton != null) {
                targetButton.getStyleClass().add("active");
            }
        } else {
            logger.warn("Unknown page ID: {}", pageId);
        }
    }

    /**
     * Returns the menu button associated with a page ID.
     */
    private Button getButtonForPageId(String pageId) {
        return switch (pageId) {
            case "xmlUltimate" -> xmlUltimate;
            case "xsd" -> xsd;
            case "xsdValidation" -> xsdValidation;
            case "schematron" -> schematron;
            case "xslt", "xsltDeveloper" -> xsltDeveloper;
            case "fop" -> fop;
            case "signature" -> signature;
            case "help" -> help;
            case "settings" -> settings;
            case "schemaGenerator" -> schemaGenerator;
            case "unifiedEditor" -> unifiedEditor;
            default -> null;
        };
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
        if (xmlUltimate == null) {
            logger.error("XML-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        removeActiveFromAllMenuButtons();
        xmlUltimate.getStyleClass().add("active");

        loadPageFromPath("/pages/tab_xml_ultimate.fxml");

        // Wait for controller to be initialized, then load the file
        Platform.runLater(() -> {
            if (this.xmlUltimateController != null && fileToLoad != null && fileToLoad.exists()) {
                logger.debug("Loading file through XmlUltimateController: {}", fileToLoad.getAbsolutePath());
                this.xmlUltimateController.loadXmlFile(fileToLoad);
            } else {
                logger.warn("XML Ultimate Controller ist nicht verfügbar oder die Datei existiert nicht. Kann die Datei nicht laden: {}", fileToLoad);
            }
        });
    }

    public void switchToXsdViewAndLoadFile(File fileToLoad) {
        if (xsd == null) {
            logger.error("XSD-Button ist nicht initialisiert, Tab-Wechsel nicht möglich.");
            return;
        }
        removeActiveFromAllMenuButtons();
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
        removeActiveFromAllMenuButtons();
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

    /**
     * Switch to XSLT Developer view and load the specified XSLT file.
     *
     * @param fileToLoad the XSLT file to load
     */
    public void switchToXsltDeveloperAndLoadFile(File fileToLoad) {
        if (xsltDeveloper == null) {
            logger.error("XSLT Developer Button is not initialized, cannot switch tabs.");
            return;
        }
        removeActiveFromAllMenuButtons();
        xsltDeveloper.getStyleClass().add("active");

        loadPageFromPath("/pages/tab_xslt_developer.fxml");
        activeTabId = "xsltDeveloper";

        if (this.xsltDeveloperController != null && fileToLoad != null && fileToLoad.exists()) {
            Platform.runLater(() -> {
                xsltDeveloperController.loadXsltFileExternal(fileToLoad);
            });
        } else {
            logger.warn("XsltDeveloperController is not available or file does not exist. Cannot load file: {}", fileToLoad);
        }
    }

    private void setParentController(Object controller) {
        switch (controller) {
            case XmlUltimateController xmlUltimateController1 -> {
                logger.debug("set Ultimate XML Controller");
                this.xmlUltimateController = xmlUltimateController1;
                xmlUltimateController1.setParentController(this);
                Platform.runLater(() -> {
                    boolean isVisible = isXPathQueryPaneVisible();
                    xmlUltimateController1.setDevelopmentPaneVisible(isVisible);
                });
                initializeIntegrationService();
            }
            case XsdValidationController xsdValidationController1 -> {
                logger.debug("set XSD Validation Controller");
                this.xsdValidationController = xsdValidationController1;
                xsdValidationController1.setParentController(this);
            }
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
            case XsltController xsltController1 -> {
                logger.debug("set XSLT Controller");
                this.xsltController = xsltController1;
                xsltController1.setParentController(this);
            }
            case FopController fopController1 -> {
                logger.debug("set FOP Controller");
                this.fopController = fopController1;
                fopController1.setParentController(this);
            }
            case SignatureController signatureController -> {
                logger.debug("set Signature Controller");
                signatureController.setParentController(this);
            }
            case HelpController helpController -> {
                logger.debug("set Help Controller");
            }
            case TemplatesController templatesController1 -> {
                logger.debug("set Smart Templates Controller");
                this.templatesController = templatesController1;
            }
            case SchemaGeneratorController schemaGeneratorController1 -> {
                logger.debug("set Intelligent Schema Generator Controller");
                this.schemaGeneratorController = schemaGeneratorController1;
            }
            case XsltDeveloperController xsltDeveloperController1 -> {
                logger.debug("set Advanced XSLT Developer Controller");
                this.xsltDeveloperController = xsltDeveloperController1;
            }
            case UnifiedEditorController unifiedEditorController1 -> {
                logger.debug("set Unified Editor Controller");
                this.unifiedEditorController = unifiedEditorController1;
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
        integrationService.initialize(this, xmlUltimateController, schematronController);

        logger.debug("Integration service initialized with XML: {}, Schematron: {}",
                xmlUltimateController != null, schematronController != null);
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

    // ======================================================================
    // Menu Handlers for Tools Menus (XSD Tools, XML Tools, Schematron Tools, PDF & Signatures)
    // ======================================================================

    // --- XSD Tools Menu Handlers ---

    @FXML
    public void openTypeLibrary() {
        switchToXsdAndSelectSubTab("typeLibraryTab");
    }

    @FXML
    public void openTypeEditor() {
        switchToXsdAndSelectSubTab("typeEditorTab");
    }

    @FXML
    public void openSchemaAnalysis() {
        switchToXsdAndSelectSubTab("schemaAnalysisTab");
    }

    @FXML
    public void openXsdDocumentation() {
        switchToXsdAndSelectSubTab("documentation");
    }

    @FXML
    public void openGenerateExampleData() {
        switchToXsdAndSelectSubTab("generateExampleData");
    }

    @FXML
    public void openFlattenSchema() {
        switchToXsdAndSelectSubTab("flattenTab");
    }

    // --- XML Tools Menu Handlers ---

    @FXML
    public void openXsltDeveloper() {
        switchToXmlUltimateAndSelectSubTab("xsltDevTab");
    }

    @FXML
    public void openTemplateBuilder() {
        switchToXmlUltimateAndSelectSubTab("templateTab");
    }

    @FXML
    public void openXmlToCsv() {
        switchToXmlUltimateAndSelectSubTab("convertTab");
    }

    @FXML
    public void openGenerateSchema() {
        switchToXmlUltimateAndSelectSubTab("generatorTab");
    }

    // --- Schematron Tools Menu Handlers ---

    @FXML
    public void openSchematronBuilder() {
        switchToSchematronAndSelectSubTab("visualBuilderTab");
    }

    @FXML
    public void openSchematronTest() {
        switchToSchematronAndSelectSubTab("testTab");
    }

    @FXML
    public void openSchematronDocumentation() {
        switchToSchematronAndSelectSubTab("documentationTab");
    }

    // --- PDF & Signatures Menu Handlers ---

    @FXML
    public void openGeneratePdf() {
        removeActiveFromAllMenuButtons();
        fop.getStyleClass().add("active");
        loadPageFromPath("/pages/tab_fop.fxml");
    }

    @FXML
    public void openCreateCertificate() {
        switchToSignatureAndSelectSubTab("createCertificateTab");
    }

    @FXML
    public void openSignXml() {
        switchToSignatureAndSelectSubTab("signXmlTab");
    }

    @FXML
    public void openValidateSignature() {
        switchToSignatureAndSelectSubTab("validateTab");
    }

    @FXML
    public void openExpertSigning() {
        switchToSignatureAndSelectSubTab("expertModeTab");
    }

    // --- Helper Methods for Menu Navigation ---

    private void switchToXsdAndSelectSubTab(String subTabId) {
        removeActiveFromAllMenuButtons();
        xsd.getStyleClass().add("active");
        loadPageFromPath("/pages/tab_xsd.fxml");
        Platform.runLater(() -> {
            if (xsdController != null) {
                xsdController.selectSubTab(subTabId);
            }
        });
    }

    private void switchToXmlUltimateAndSelectSubTab(String subTabId) {
        removeActiveFromAllMenuButtons();
        xmlUltimate.getStyleClass().add("active");
        loadPageFromPath("/pages/tab_xml_ultimate.fxml");
        Platform.runLater(() -> {
            if (xmlUltimateController != null) {
                xmlUltimateController.selectSubTab(subTabId);
            }
        });
    }

    private void switchToSchematronAndSelectSubTab(String subTabId) {
        removeActiveFromAllMenuButtons();
        schematron.getStyleClass().add("active");
        loadPageFromPath("/pages/tab_schematron.fxml");
        Platform.runLater(() -> {
            if (schematronController != null) {
                schematronController.selectSubTab(subTabId);
            }
        });
    }

    private void switchToSignatureAndSelectSubTab(String subTabId) {
        removeActiveFromAllMenuButtons();
        signature.getStyleClass().add("active");
        loadPageFromPath("/pages/tab_signature.fxml");
        Platform.runLater(() -> {
            // SignatureController is obtained from page loading
            Object controller = contentPane.getUserData();
            if (controller instanceof SignatureController sc) {
                sc.selectSubTab(subTabId);
            }
        });
    }

    @FXML
    private void toggleMenuBar() {
        logger.debug("Show Menu: {}", showMenu);
        if (showMenu) {
            setMenuSize(50, ">>", "", 15, 75);
            setButtonSize("menu_button_collapsed", xmlUltimate, xsd, xsdValidation, schematron, xslt, fop, help, settings, exit, signature, schemaGenerator, xsltDeveloper, unifiedEditor);
        } else {
            setMenuSize(200, "FundsXML Toolkit", "Enterprise Edition", 75, 100);
            setButtonSize("menu_button", xmlUltimate, xsd, xsdValidation, schematron, xslt, fop, help, settings, exit, signature, schemaGenerator, xsltDeveloper, unifiedEditor);
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
            // Preserve the "active" state while changing button size
            boolean isActive = button.getStyleClass().contains("active");

            button.getStyleClass().remove("menu_button");
            button.getStyleClass().remove("menu_button_collapsed");
            button.getStyleClass().add(styleClass);

            // Re-add "active" class if button was active
            if (isActive && !button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }

            // Hide/show button text based on collapsed state
            if (styleClass.equals("menu_button_collapsed")) {
                button.setText("");
            } else {
                // Restore original text based on button ID
                restoreButtonText(button);
            }
        }
    }

    private void restoreButtonText(Button button) {
        if (button == xmlUltimate) button.setText("XML Editor");
        else if (button == xsd) button.setText("XSD Editor");
        else if (button == xsdValidation) button.setText("XSD Validation");
        else if (button == schematron) button.setText("Schematron Editor");
        else if (button == xslt) button.setText("XSLT Viewer");
        else if (button == fop) button.setText("FOP");
        else if (button == signature) button.setText("Signature");
        else if (button == help) button.setText("Help");
        else if (button == settings) button.setText("Settings");
        else if (button == exit) button.setText("Exit");
        else if (button == xsltDeveloper) button.setText("XSLT Developer");
        else if (button == schemaGenerator) button.setText("Schema Generator");
        // else if (button == templates) button.setText("Smart Templates"); // Removed from menu
    }

    @FXML
    private void toggleXmlEditorSidebar() {
        if (xmlEditorSidebarMenuItem == null) {
            return;
        }
        boolean isVisible = xmlEditorSidebarMenuItem.isSelected();
        logger.debug("Toggle XML Editor Sidebar: {}", isVisible);

        propertiesService.set("xmlEditorSidebar.visible", String.valueOf(isVisible));

        if (xmlUltimateController != null) {
            xmlUltimateController.setXmlEditorSidebarVisible(isVisible);
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

        if (xmlUltimateController != null) {
            xmlUltimateController.setXmlEditorSidebarVisible(visible);
        }
    }

    @FXML
    private void toggleXPathQueryPane() {
        if (xpathQueryPaneMenuItem == null) {
            return;
        }
        boolean isVisible = xpathQueryPaneMenuItem.isSelected();
        logger.debug("Toggle XPath Query Pane: {}", isVisible);

        propertiesService.set("xpathQueryPane.visible", String.valueOf(isVisible));

        if (xmlUltimateController != null) {
            xmlUltimateController.setDevelopmentPaneVisible(isVisible);
        } else {
            logger.debug("XML Ultimate Controller not yet available - preference saved, will be applied when XML tab is loaded");
        }
    }

    public boolean isXPathQueryPaneVisible() {
        String paneVisible = propertiesService.get("xpathQueryPane.visible");
        return paneVisible == null || Boolean.parseBoolean(paneVisible);
    }

    /**
     * Toggles the XPath/XQuery pane from the sidebar button.
     *
     * @param visible whether the pane should be visible
     */
    public void toggleXPathPaneFromSidebar(boolean visible) {
        logger.debug("Toggle XPath/XQuery Pane from sidebar button: {}", visible);

        if (xpathQueryPaneMenuItem != null) {
            xpathQueryPaneMenuItem.setSelected(visible);
        }

        propertiesService.set("xpathQueryPane.visible", String.valueOf(visible));

        if (xmlUltimateController != null) {
            xmlUltimateController.setDevelopmentPaneVisible(visible);
        }
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
                    xmlUltimate.fire();
                    Platform.runLater(() -> {
                        if (xmlUltimateController != null) {
                            xmlUltimateController.newFilePressed();
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
    private void handleUndo() {
        logger.debug("Undo action triggered");

        // Check if XSD tab is active and has undo capability
        if (xsdController != null && xsdController.isXsdTabActive()) {
            xsdController.performUndo();
        }
    }

    @FXML
    private void handleRedo() {
        logger.debug("Redo action triggered");

        // Check if XSD tab is active and has redo capability
        if (xsdController != null && xsdController.isXsdTabActive()) {
            xsdController.performRedo();
        }
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
                xmlUltimate.fire();
                Platform.runLater(() -> {
                    if (xmlUltimateController != null) {
                        logger.debug("Loading selected XML file through XmlUltimateController: {}", selectedFile.getAbsolutePath());
                        xmlUltimateController.loadXmlFile(selectedFile);
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
                xmlUltimate.fire();
                Platform.runLater(() -> {
                    if (xmlUltimateController != null) {
                        logger.debug("Loading unknown file type as XML through XmlUltimateController: {}", selectedFile.getAbsolutePath());
                        xmlUltimateController.loadXmlFile(selectedFile);
                    }
                });
            }

            addFileToRecentFiles(selectedFile);
        }
    }

    // Methods for opening files in specific editors (used by SettingsController for favorites)

    public void openXmlFileInEditor(File file) {
        if (file != null && xmlUltimateController != null) {
            xmlUltimate.fire(); // Switch to XML tab
            Platform.runLater(() -> xmlUltimateController.loadXmlFile(file));
            logger.info("Opening XML file in editor: {}", file.getAbsolutePath());
        }
    }

    public void openXsdFileInEditor(File file) {
        if (file != null && xsdController != null) {
            xsd.fire(); // Switch to XSD tab
            Platform.runLater(() -> xsdController.openXsdFile(file));
            logger.info("Opening XSD file in editor: {}", file.getAbsolutePath());
        }
    }

    public void openSchematronFileInEditor(File file) {
        if (file != null && schematronController != null) {
            schematron.fire(); // Switch to Schematron tab
            Platform.runLater(() -> schematronController.loadSchematronFile(file));
            logger.info("Opening Schematron file in editor: {}", file.getAbsolutePath());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing MainController with drag and drop support");

        // Set up exit handlers
        logger.info("Setting up exit button and menu handlers");
        exit.setOnAction(e -> handleExit());
        menuItemExit.setOnAction(e -> handleExit());

        // Start memory monitoring
        scheduler.scheduleAtFixedRate(this::updateMemoryUsage, 1, 3, TimeUnit.SECONDS);

        // Load preferences and settings
        loadLastOpenFiles();
        loadXmlEditorSidebarPreference();
        loadXPathQueryPanePreference();
        loadXmlEditorTheme();

        // Initialize drag and drop
        initializeDragAndDrop();

        // Load the welcome page on startup
        Platform.runLater(() -> {
            loadWelcomePage();
            applyTheme();
            setupKeyboardShortcuts();

            // Check for updates asynchronously after startup (with small delay)
            scheduler.schedule(this::checkForUpdatesOnStartup, 2, TimeUnit.SECONDS);
        });
    }

    /**
     * Checks for available updates asynchronously on application startup.
     * Shows the update notification dialog if a new version is available.
     */
    private void checkForUpdatesOnStartup() {
        UpdateCheckService updateService = ServiceRegistry.get(UpdateCheckService.class);

        if (!updateService.isUpdateCheckEnabled()) {
            logger.debug("Update check is disabled in settings");
            return;
        }

        logger.info("Checking for updates on startup...");

        updateService.checkForUpdates()
                .thenAccept(updateInfo -> {
                    if (updateInfo.updateAvailable()) {
                        logger.info("Update available: {} -> {}",
                                updateInfo.currentVersion(), updateInfo.latestVersion());
                        Platform.runLater(() -> showUpdateDialog(updateInfo));
                    } else {
                        logger.debug("No update available. Current version: {}",
                                updateInfo.currentVersion());
                    }
                })
                .exceptionally(ex -> {
                    logger.warn("Failed to check for updates: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Shows the update notification dialog.
     *
     * @param updateInfo the update information to display
     */
    private void showUpdateDialog(UpdateInfo updateInfo) {
        try {
            UpdateNotificationDialog dialog = new UpdateNotificationDialog(updateInfo);
            dialog.showAndWait().ifPresent(action -> {
                if (action == UpdateNotificationDialog.UpdateAction.DOWNLOAD_AND_INSTALL) {
                    startAutoUpdate(updateInfo);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to show update dialog", e);
        }
    }

    /**
     * Starts the automatic update process.
     *
     * @param updateInfo the update information
     */
    private void startAutoUpdate(UpdateInfo updateInfo) {
        logger.info("Starting auto-update to version {}", updateInfo.latestVersion());

        UpdateProgressDialog progressDialog = new UpdateProgressDialog(updateInfo);
        progressDialog.startUpdate(result -> {
            if (result.success()) {
                logger.info("Auto-update initiated successfully. Application will restart.");
                // The UpdateProgressDialog will handle Platform.exit()
            } else {
                logger.warn("Auto-update failed: {}", result.errorMessage());
                // Show error dialog
                Platform.runLater(() -> {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Update Failed");
                    errorAlert.setHeaderText("Could not complete the update");
                    errorAlert.setContentText(result.errorMessage() +
                            "\n\nYou can try again or download the update manually from GitHub.");
                    errorAlert.showAndWait();
                });
            }
        });
    }

    /**
     * Load the welcome page as the initial content
     */
    private void loadWelcomePage() {
        try {
            logger.info("Loading welcome page on startup");
            loadPageFromPath("/pages/welcome.fxml");
        } catch (Exception e) {
            logger.error("Failed to load welcome page on startup", e);
        }
    }

    /**
     * Initialize drag and drop functionality for the main application
     */
    private void initializeDragAndDrop() {
        if (contentPane == null) {
            logger.warn("Cannot initialize drag and drop: contentPane is null");
            return;
        }

        logger.info("Setting up global drag and drop functionality for XML files");

        // Allow files to be dropped on the main content pane
        contentPane.setOnDragOver(this::handleDragOver);
        contentPane.setOnDragDropped(this::handleDragDropped);

        logger.debug("Drag and drop event handlers registered for main application");
    }

    /**
     * Handle drag over event - determine if files can be accepted.
     * Only handles events that haven't been consumed by tab-specific handlers.
     * Uses DragDropService for consistent file type detection.
     */
    private void handleDragOver(DragEvent event) {
        // Skip if event was already consumed by a tab-specific handler
        if (event.isConsumed()) {
            logger.debug("Drag over event already consumed by tab-specific handler");
            return;
        }

        Dragboard dragboard = event.getDragboard();

        // Accept all supported file types (XML, XSD, XSLT, Schematron, WSDL, Keystore)
        if (dragboard.hasFiles() && DragDropService.hasFilesWithExtensions(dragboard.getFiles(), DragDropService.ALL_XML_RELATED)) {
            event.acceptTransferModes(TransferMode.COPY);
            logger.debug("Global handler: Drag over accepted: {} files detected", dragboard.getFiles().size());
        } else {
            logger.debug("Global handler: Drag over rejected: no supported files found");
        }

        event.consume();
    }

    /**
     * Handle drag dropped event - route dropped files to appropriate editors based on file type.
     * Only handles events that haven't been consumed by tab-specific handlers.
     * Uses DragDropService for file type detection and routing.
     */
    private void handleDragDropped(DragEvent event) {
        // Skip if event was already consumed by a tab-specific handler
        if (event.isConsumed()) {
            logger.debug("Drag dropped event already consumed by tab-specific handler");
            return;
        }

        Dragboard dragboard = event.getDragboard();
        boolean success = false;

        if (dragboard.hasFiles()) {
            logger.info("Global handler: Files dropped on main application: processing {} files", dragboard.getFiles().size());

            var supportedFiles = DragDropService.filterByExtensions(dragboard.getFiles(), DragDropService.ALL_XML_RELATED);

            if (!supportedFiles.isEmpty()) {
                success = true;

                // Route each file to the appropriate editor based on its type
                for (File file : supportedFiles) {
                    try {
                        routeFileByType(file);
                        logger.info("Global handler: Routed dropped file to appropriate editor: {}", file.getName());
                    } catch (Exception e) {
                        logger.error("Failed to route dropped file: {}", file.getName(), e);
                    }
                }
            } else {
                logger.info("Global handler: No supported files found in dropped files");
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Route a file to the appropriate editor based on its file type.
     * Uses DragDropService.FileType for consistent file type detection.
     *
     * @param file the file to route
     */
    private void routeFileByType(File file) {
        DragDropService.FileType fileType = DragDropService.getFileType(file);
        logger.debug("Routing file '{}' with type: {}", file.getName(), fileType);

        switch (fileType) {
            case XSD -> switchToXsdViewAndLoadFile(file);
            case SCHEMATRON -> switchToSchematronViewAndLoadFile(file);
            case XSLT -> switchToXsltDeveloperAndLoadFile(file);
            case WSDL, XML -> switchToXmlViewAndLoadFile(file);
            default -> {
                logger.warn("Unknown file type for '{}', opening in XML editor", file.getName());
                switchToXmlViewAndLoadFile(file);
            }
        }
    }

    /**
     * Check if the list of files contains at least one XML file
     */
    private boolean hasXmlFiles(java.util.List<java.io.File> files) {
        return files.stream().anyMatch(this::isXmlFile);
    }

    /**
     * Check if a file is an XML file based on its extension
     */
    private boolean isXmlFile(java.io.File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".xml") || fileName.endsWith(".xsd") || fileName.endsWith(".xsl") ||
                fileName.endsWith(".xslt") || fileName.endsWith(".wsdl");
    }

    // ==================== KEYBOARD SHORTCUTS ====================

    /**
     * Setup global keyboard shortcuts for the application.
     * Called after the scene is available.
     */
    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = contentPane.getScene();
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPressed);
                logger.info("Global keyboard shortcuts initialized");
            } else {
                logger.warn("Scene not available for keyboard shortcuts setup");
            }
        });
    }

    /**
     * Handle global keyboard shortcuts.
     * Shortcuts: Ctrl+N (New), Ctrl+O (Open), Ctrl+S (Save), Ctrl+Shift+S (Save As),
     * Ctrl+D (Add to Favorites), Ctrl+Shift+D (Toggle Favorites), F5 (Execute main action)
     */
    private void handleGlobalKeyPressed(KeyEvent event) {
        // Skip if event was already consumed
        if (event.isConsumed()) {
            return;
        }

        KeyCode code = event.getCode();
        boolean isShortcut = event.isShortcutDown(); // Ctrl on Windows/Linux, Cmd on Mac
        boolean isShift = event.isShiftDown();

        // F5 - Execute main action for current tab
        if (code == KeyCode.F5) {
            handleF5Action();
            event.consume();
            return;
        }

        // Ctrl/Cmd shortcuts
        if (isShortcut) {
            switch (code) {
                case N -> {
                    if (!isShift) {
                        handleNewFile();
                        event.consume();
                    }
                }
                case O -> {
                    if (!isShift) {
                        handleOpenFile();
                        event.consume();
                    }
                }
                case S -> {
                    if (isShift) {
                        handleSaveAsAction();
                    } else {
                        handleSaveAction();
                    }
                    event.consume();
                }
                case D -> {
                    if (isShift) {
                        handleToggleFavoritesAction();
                    } else {
                        handleAddToFavoritesAction();
                    }
                    event.consume();
                }
                default -> {
                    // Not handled
                }
            }
        }
    }

    /**
     * Handle F5 - Execute main action based on active tab.
     * Each tab has a different main action (Validate, Generate, Transform, etc.)
     */
    private void handleF5Action() {
        logger.debug("F5 pressed - Active tab: {}", activeTabId);

        switch (activeTabId) {
            case "xmlUltimate" -> {
                if (xmlUltimateController != null) {
                    xmlUltimateController.validateXml();
                    logger.debug("F5: Triggered XML validation");
                }
            }
            case "xsd" -> {
                if (xsdController != null) {
                    xsdController.handleToolbarValidate();
                    logger.debug("F5: Triggered XSD validation");
                }
            }
            case "schematron" -> {
                if (schematronController != null) {
                    schematronController.runAllTests();
                    logger.debug("F5: Triggered Schematron tests");
                }
            }
            case "xsdValidation" -> {
                if (xsdValidationController != null) {
                    xsdValidationController.processXmlFile();
                    logger.debug("F5: Triggered XSD validation");
                }
            }
            case "fop" -> {
                if (fopController != null) {
                    fopController.buttonConversion();
                    logger.debug("F5: Triggered PDF generation");
                }
            }
            case "xsltDeveloper" -> {
                if (xsltDeveloperController != null) {
                    xsltDeveloperController.executeTransformation();
                    logger.debug("F5: Triggered XSLT transformation");
                }
            }
            case "schemaGenerator" -> {
                if (schemaGeneratorController != null) {
                    schemaGeneratorController.generateSchema();
                    logger.debug("F5: Triggered schema generation");
                }
            }
            default -> logger.debug("F5: No action defined for tab '{}'", activeTabId);
        }
    }

    /**
     * Handle Ctrl+S - Save action for current tab
     */
    private void handleSaveAction() {
        logger.debug("Ctrl+S pressed - Active tab: {}", activeTabId);

        switch (activeTabId) {
            case "xmlUltimate" -> {
                if (xmlUltimateController != null) {
                    xmlUltimateController.saveFile();
                    logger.debug("Ctrl+S: Triggered XML save");
                }
            }
            case "xsd" -> {
                if (xsdController != null) {
                    xsdController.handleToolbarSave();
                    logger.debug("Ctrl+S: Triggered XSD save");
                }
            }
            case "schematron" -> {
                if (schematronController != null) {
                    schematronController.saveSchematronPublic();
                    logger.debug("Ctrl+S: Triggered Schematron save");
                }
            }
            default -> logger.debug("Ctrl+S: No save action defined for tab '{}'", activeTabId);
        }
    }

    /**
     * Handle Ctrl+Shift+S - Save As action for current tab
     */
    private void handleSaveAsAction() {
        logger.debug("Ctrl+Shift+S pressed - Active tab: {}", activeTabId);

        switch (activeTabId) {
            case "xmlUltimate" -> {
                if (xmlUltimateController != null) {
                    xmlUltimateController.saveAsFile();
                    logger.debug("Ctrl+Shift+S: Triggered XML Save As");
                }
            }
            case "xsd" -> {
                if (xsdController != null) {
                    xsdController.handleToolbarSaveAs();
                    logger.debug("Ctrl+Shift+S: Triggered XSD Save As");
                }
            }
            case "schematron" -> {
                if (schematronController != null) {
                    schematronController.saveSchematronAsPublic();
                    logger.debug("Ctrl+Shift+S: Triggered Schematron Save As");
                }
            }
            default -> logger.debug("Ctrl+Shift+S: No Save As action defined for tab '{}'", activeTabId);
        }
    }

    /**
     * Handle Ctrl+D - Add current file to favorites
     */
    private void handleAddToFavoritesAction() {
        logger.debug("Ctrl+D pressed - Add to Favorites - Active tab: {}", activeTabId);

        switch (activeTabId) {
            case "xmlUltimate" -> {
                if (xmlUltimateController != null) {
                    xmlUltimateController.addCurrentFileToFavorites();
                    logger.debug("Ctrl+D: Added XML file to favorites");
                }
            }
            case "xsd" -> {
                if (xsdController != null) {
                    xsdController.handleToolbarAddFavorite();
                    logger.debug("Ctrl+D: Added XSD file to favorites");
                }
            }
            case "schematron" -> {
                if (schematronController != null) {
                    schematronController.addCurrentToFavoritesPublic();
                    logger.debug("Ctrl+D: Added Schematron file to favorites");
                }
            }
            default -> logger.debug("Ctrl+D: No add favorites action defined for tab '{}'", activeTabId);
        }
    }

    /**
     * Handle Ctrl+Shift+D - Toggle favorites panel
     */
    private void handleToggleFavoritesAction() {
        logger.debug("Ctrl+Shift+D pressed - Toggle Favorites Panel - Active tab: {}", activeTabId);

        switch (activeTabId) {
            case "xmlUltimate" -> {
                if (xmlUltimateController != null) {
                    xmlUltimateController.toggleFavoritesPanel();
                    logger.debug("Ctrl+Shift+D: Toggled XML favorites panel");
                }
            }
            case "xsd" -> {
                if (xsdController != null) {
                    xsdController.handleToolbarShowFavorites();
                    logger.debug("Ctrl+Shift+D: Toggled XSD favorites panel");
                }
            }
            case "schematron" -> {
                if (schematronController != null) {
                    schematronController.toggleFavoritesPanelPublic();
                    logger.debug("Ctrl+Shift+D: Toggled Schematron favorites panel");
                }
            }
            case "xsdValidation" -> {
                if (xsdValidationController != null) {
                    xsdValidationController.toggleFavoritesPanelPublic();
                    logger.debug("Ctrl+Shift+D: Toggled Validation favorites panel");
                }
            }
            case "fop" -> {
                if (fopController != null) {
                    fopController.toggleFavoritesPanelPublic();
                    logger.debug("Ctrl+Shift+D: Toggled FOP favorites panel");
                }
            }
            case "xsltDeveloper" -> {
                if (xsltDeveloperController != null) {
                    xsltDeveloperController.toggleFavoritesPanelPublic();
                    logger.debug("Ctrl+Shift+D: Toggled XSLT Developer favorites panel");
                }
            }
            case "schemaGenerator" -> {
                if (schemaGeneratorController != null) {
                    schemaGeneratorController.toggleFavoritesPanelPublic();
                    logger.debug("Ctrl+Shift+D: Toggled Schema Generator favorites panel");
                }
            }
            default -> logger.debug("Ctrl+Shift+D: No toggle favorites action defined for tab '{}'", activeTabId);
        }
    }

    // ==================== FILE MENU HANDLERS ====================

    /**
     * Handle Save menu action - delegates to the active tab's save functionality
     */
    @FXML
    public void handleSave() {
        handleSaveAction();
    }

    /**
     * Handle Save As menu action - delegates to the active tab's save as functionality
     */
    @FXML
    public void handleSaveAs() {
        handleSaveAsAction();
    }

    /**
     * Handle Close menu action - closes the current tab/file
     */
    @FXML
    public void handleClose() {
        logger.debug("Close action triggered - Active tab: {}", activeTabId);

        // For most tabs, return to welcome page
        loadWelcomePage();
        removeActiveFromAllMenuButtons();
        activeTabId = "welcome";
        logger.debug("Returned to welcome page");
    }

    /**
     * Handle Close All menu action - closes all open tabs/files
     */
    @FXML
    public void handleCloseAll() {
        logger.debug("Close All action triggered");

        // Return to welcome page
        loadWelcomePage();
        removeActiveFromAllMenuButtons();
        activeTabId = "welcome";
        logger.info("Closed all files and returned to welcome page");
    }

    /**
     * Handle Settings menu action - navigates to settings page
     */
    @FXML
    public void openSettings() {
        logger.debug("Opening Settings page from menu");
        removeActiveFromAllMenuButtons();
        settings.getStyleClass().add("active");
        loadPageFromPath("/pages/settings.fxml");
        activeTabId = "settings";
    }

    // ==================== EDIT MENU HANDLERS ====================

    /**
     * Handle Find menu action - activates find functionality in current editor.
     * Note: The search functionality is built into the editors via Ctrl+F.
     */
    @FXML
    public void handleFind() {
        logger.debug("Find action triggered - Active tab: {}", activeTabId);
        // The editors have their own search bar triggered by Ctrl+F
        // This menu item provides an alternative way to access it
        DialogHelper.showInformation("Find", "Use Search Bar",
                "Use Ctrl+F in the editor to search.\n\n" +
                        "The search bar will appear at the top of the editor.");
    }

    /**
     * Handle Find & Replace menu action - activates find/replace in current editor.
     * Note: Replace functionality is available in the search bar.
     */
    @FXML
    public void handleFindReplace() {
        logger.debug("Find & Replace action triggered - Active tab: {}", activeTabId);
        // The editors have their own search/replace via Ctrl+H
        DialogHelper.showInformation("Find & Replace", "Use Search & Replace Bar",
                "Use Ctrl+H in the editor for Find & Replace.\n\n" +
                        "The search bar with replace option will appear at the top of the editor.");
    }

    // ==================== WINDOW MENU HANDLERS ====================

    /**
     * Toggle full screen mode
     */
    @FXML
    public void toggleFullScreen() {
        logger.debug("Toggle full screen action triggered");

        Stage stage = (Stage) contentPane.getScene().getWindow();
        if (stage != null) {
            stage.setFullScreen(!stage.isFullScreen());
            logger.info("Full screen mode: {}", stage.isFullScreen());
        }
    }

    // ==================== HELP MENU HANDLERS ====================

    /**
     * Open User Guide in browser
     */
    @FXML
    public void openUserGuide() {
        logger.debug("Opening User Guide");
        try {
            java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://karlkauc.github.io/FreeXmlToolkit/")
            );
            logger.info("Opened User Guide in browser");
        } catch (Exception e) {
            logger.error("Failed to open User Guide", e);
            DialogHelper.showError("Error", "Could not open User Guide",
                    "Failed to open the User Guide in your browser. Please visit:\nhttps://karlkauc.github.io/FreeXmlToolkit/");
        }
    }

    /**
     * Show keyboard shortcuts dialog
     */
    @FXML
    public void showKeyboardShortcuts() {
        logger.debug("Showing keyboard shortcuts dialog");

        var features = java.util.List.of(
                new String[]{"bi-folder", "File Operations", "Create, open, save, and manage files"},
                new String[]{"bi-pencil", "Edit Operations", "Undo, redo, find and replace text"},
                new String[]{"bi-window", "View Controls", "Toggle full screen and window options"},
                new String[]{"bi-play-circle", "Actions", "Execute operations and manage favorites"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"Ctrl+N", "New File"},
                new String[]{"Ctrl+O", "Open File"},
                new String[]{"Ctrl+S", "Save"},
                new String[]{"Ctrl+Shift+S", "Save As"},
                new String[]{"Ctrl+W", "Close"},
                new String[]{"Ctrl+Z", "Undo"},
                new String[]{"Ctrl+Shift+Z", "Redo"},
                new String[]{"Ctrl+F", "Find"},
                new String[]{"Ctrl+H", "Find & Replace"},
                new String[]{"F11", "Toggle Full Screen"},
                new String[]{"F5", "Execute (Validate/Transform)"},
                new String[]{"Ctrl+D", "Add to Favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle Favorites Panel"},
                new String[]{"F1", "Show Help"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "Keyboard Shortcuts",
                "Keyboard Shortcuts",
                "Quick reference for all keyboard shortcuts in FreeXmlToolkit",
                "bi-keyboard",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts
        );

        helpDialog.showAndWait();
    }

    /**
     * Flag to prevent concurrent update checks
     */
    private volatile boolean isCheckingForUpdates = false;

    /**
     * Manually check for updates (menu action)
     */
    @FXML
    public void checkForUpdates() {
        // Prevent concurrent update checks
        if (isCheckingForUpdates) {
            logger.debug("Update check already in progress, ignoring duplicate request");
            return;
        }
        isCheckingForUpdates = true;
        logger.info("Manual update check triggered from menu");

        UpdateCheckService updateService = ServiceRegistry.get(UpdateCheckService.class);

        // Show progress indicator
        Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle("Check for Updates");
        progressDialog.setHeaderText("Checking for updates...");
        progressDialog.setContentText("Please wait while we check for available updates.");
        progressDialog.getButtonTypes().clear();
        progressDialog.show();

        updateService.checkForUpdates()
                .thenAccept(updateInfo -> {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        isCheckingForUpdates = false;

                        if (updateInfo.updateAvailable()) {
                            logger.info("Update available: {} -> {}",
                                    updateInfo.currentVersion(), updateInfo.latestVersion());
                            showUpdateDialog(updateInfo);
                        } else {
                            DialogHelper.showInformation("No Updates Available", "You're up to date!",
                                    "You are running the latest version: " + updateInfo.currentVersion());
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        isCheckingForUpdates = false;
                        DialogHelper.showError("Update Check Failed", "Could not check for updates",
                                "Failed to connect to update server. Please check your internet connection.\n\n" +
                                        "Error: " + ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Open GitHub Issues page to report an issue
     */
    @FXML
    public void reportIssue() {
        logger.debug("Opening GitHub Issues page");
        try {
            java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://github.com/karlkauc/FreeXmlToolkit/issues/new")
            );
            logger.info("Opened GitHub Issues page in browser");
        } catch (Exception e) {
            logger.error("Failed to open GitHub Issues page", e);
            DialogHelper.showError("Error", "Could not open browser",
                    "Failed to open the GitHub Issues page. Please visit:\nhttps://github.com/karlkauc/FreeXmlToolkit/issues/new");
        }
    }

}
