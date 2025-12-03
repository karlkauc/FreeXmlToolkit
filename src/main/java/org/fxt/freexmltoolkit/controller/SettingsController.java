/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.ModernXmlThemeManager;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Properties;

public class SettingsController {

    Properties props;
    private final static Logger logger = LogManager.getLogger(SettingsController.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
    private final FavoritesService favoritesService = ServiceRegistry.get(FavoritesService.class);
    private final UpdateCheckService updateCheckService = ServiceRegistry.get(UpdateCheckService.class);

    @FXML
    RadioButton noProxy, systemProxy, manualProxy, useSystemTempFolder, useCustomTempFolder, lightTheme, darkTheme;

    @FXML
    CheckBox autoFormatXmlAfterLoading;

    @FXML
    CheckBox schematronPrettyPrintOnLoad;

    // XML Auto-Creation Settings
    @FXML
    CheckBox createAllChildNodes;

    @FXML
    CheckBox fillNodesWithSampleValues;

    // XSD Editor Settings
    @FXML
    CheckBox xsdAutoSaveEnabled, xsdBackupEnabled, xsdPrettyPrintOnSave;

    // SSL Settings
    @FXML
    CheckBox trustAllCerts;

    // Update Settings
    @FXML
    CheckBox checkForUpdatesOnStartup;

    @FXML
    Spinner<Integer> xsdAutoSaveInterval, xsdBackupVersions;

    @FXML
    TextField customTempFolder, httpProxyHost, httpProxyUser, noProxyHost;

    @FXML
    PasswordField httpProxyPass;

    @FXML
    Spinner<Integer> portSpinner, xmlIndentSpaces, xmlFontSize;

    @FXML
    Button checkConnection, checkForUpdatesButton;

    @FXML
    ToggleGroup proxy, tempFolder, theme;

    @FXML
    ComboBox<String> xmlThemeComboBox;

    @FXML
    ComboBox<XmlParserType> xmlParserComboBox;

    // Favorites Management FXML components
    @FXML
    TableView<FileFavorite> favoritesTable;

    @FXML
    TableColumn<FileFavorite, String> nameColumn, categoryColumn, fileTypeColumn, pathColumn;

    @FXML
    Button openFavoriteButton, editFavoriteButton, removeFavoriteButton, removeNonExistentButton, createCategoryButton;

    @FXML
    TextField newCategoryField;

    // Usage Statistics & Cache FXML components
    @FXML
    CheckBox sendUsageStatistics;

    // Toolbar Icon Settings
    @FXML
    CheckBox useSmallIcons;

    @FXML
    Button clearCacheButton, openCacheFolderButton;

    @FXML
    Label cacheSizeLabel, cachePathLabel;

    // Data for favorites table
    private final ObservableList<FileFavorite> favoritesData = FXCollections.observableArrayList();

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 8080));
        xmlIndentSpaces.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 4));
        xmlFontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 24, 12));

        // Initialize XSD settings spinners
        xsdAutoSaveInterval.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 5));
        xsdBackupVersions.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3));

        // Initialize XML theme combo box
        ModernXmlThemeManager themeManager = ModernXmlThemeManager.getInstance();
        xmlThemeComboBox.getItems().addAll(themeManager.getThemeNames());

        // Initialize XML parser ComboBox
        xmlParserComboBox.setItems(FXCollections.observableArrayList(XmlParserType.values()));
        xmlParserComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(XmlParserType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });
        xmlParserComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(XmlParserType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });

        // Initialize favorites table
        initializeFavoritesTable();

        // Initialize cache size label
        updateCacheSizeLabel();

        loadCurrentSettings();

        // Listener to enable/disable input fields
        manualProxy.selectedProperty().addListener((observable, oldValue, newValue) -> enableProxyFields(newValue));
        useCustomTempFolder.selectedProperty().addListener((observable, oldValue, newValue) -> enableTempFolderFields(newValue));

        // XSD auto-save interval only enabled when auto-save is enabled
        xsdAutoSaveEnabled.selectedProperty().addListener((observable, oldValue, newValue) ->
                xsdAutoSaveInterval.setDisable(!newValue));

        // XSD backup versions only enabled when backup is enabled
        xsdBackupEnabled.selectedProperty().addListener((observable, oldValue, newValue) ->
                xsdBackupVersions.setDisable(!newValue));
    }

    private void enableTempFolderFields(boolean enable) {
        customTempFolder.setDisable(!enable);
    }

    private void enableProxyFields(boolean enable) {
        httpProxyHost.setDisable(!enable);
        portSpinner.setDisable(!enable);
        httpProxyUser.setDisable(!enable);
        httpProxyPass.setDisable(!enable);
        noProxyHost.setDisable(!enable);
    }

    @FXML
    private void performCheck() {
        logger.debug("Perform Connection Check");

        // Create temporary properties with current UI values
        Properties testProps = new Properties();
        testProps.setProperty("useSystemProxy", String.valueOf(systemProxy.isSelected()));
        testProps.setProperty("manualProxy", String.valueOf(manualProxy.isSelected()));
        testProps.setProperty("http.proxy.host", httpProxyHost.getText());
        testProps.setProperty("http.proxy.port", portSpinner.getValue().toString());
        testProps.setProperty("http.proxy.user", httpProxyUser.getText());
        testProps.setProperty("http.proxy.password", httpProxyPass.getText());
        testProps.setProperty("noProxyHost", noProxyHost.getText());
        // Include SSL settings from current UI state
        testProps.setProperty("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));
        
        logger.debug("Connection test with settings: manualProxy={}, host={}, port={}, trustAllCerts={}", 
                    manualProxy.isSelected(), httpProxyHost.getText(), portSpinner.getValue(), trustAllCerts.isSelected());

        try {
            var connectionResult = connectionService.testHttpRequest(new URI("https://www.github.com"), testProps);
            // Check if a result was returned.
            if (connectionResult == null) {
                logger.error("Connection check failed. The connection service returned a null result.");
                showAlert(Alert.AlertType.ERROR, "Connection Error", "The connection check failed. The connection service did not return a result. Please check proxy settings and application logs.");
                return;
            }

            logger.debug("HTTP Status: {}", connectionResult.httpStatus());

            // HTTP-Statuscodes im 2xx-Bereich als Erfolg werten
            boolean isSuccess = connectionResult.httpStatus() >= 200 && connectionResult.httpStatus() < 300;

            Alert alert = new Alert(isSuccess ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.setHeaderText(isSuccess ? "Connection Check successful." : "Connection Check failed.");
            alert.setTitle("Connection Check");

            StringBuilder headerString = new StringBuilder();
            if (connectionResult.resultHeader() != null) {
                List.of(connectionResult.resultHeader()).forEach(h -> headerString.append(h).append(System.lineSeparator()));
            }
            alert.setContentText(getString(headerString, connectionResult));
            alert.setResizable(true); // Macht das Fenster in der Größe veränderbar
            alert.showAndWait();

        } catch (Exception e) {
            // Log the complete exception for better error analysis
            logger.error("An unexpected error occurred during the connection check.", e);
            showAlert(Alert.AlertType.ERROR, "Connection Error", "An unexpected error occurred: " + e.getClass().getSimpleName() + ". See logs for details.");
        }
    }

    @FXML
    private void checkForUpdatesNow() {
        logger.debug("Manual update check triggered");
        checkForUpdatesButton.setDisable(true);
        checkForUpdatesButton.setText("Checking...");

        updateCheckService.checkForUpdates()
                .thenAccept(updateInfo -> Platform.runLater(() -> {
                    checkForUpdatesButton.setDisable(false);
                    checkForUpdatesButton.setText("Check for Updates Now");

                    if (updateInfo == null || updateInfo.latestVersion() == null) {
                        showAlert(Alert.AlertType.ERROR, "Update Check Failed",
                                "Could not check for updates. Please verify your network connection and try again.");
                        return;
                    }

                    if (updateInfo.updateAvailable()) {
                        showUpdateAvailableAlert(updateInfo);
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "No Updates Available",
                                "You are running the latest version.\n\n" +
                                        "Current version: " + updateInfo.currentVersion());
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        checkForUpdatesButton.setDisable(false);
                        checkForUpdatesButton.setText("Check for Updates Now");
                        logger.error("Update check failed", ex);
                        showAlert(Alert.AlertType.ERROR, "Update Check Failed",
                                "An error occurred while checking for updates: " + ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Shows an alert with update information and option to download.
     *
     * @param updateInfo The update information to display
     */
    private void showUpdateAvailableAlert(UpdateInfo updateInfo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update Available");
        alert.setHeaderText("A new version is available!");

        StringBuilder content = new StringBuilder();
        content.append("Current version: ").append(updateInfo.currentVersion()).append("\n");
        content.append("Latest version: ").append(updateInfo.latestVersion()).append("\n");

        if (updateInfo.publishedDate() != null) {
            content.append("Released: ").append(updateInfo.publishedDate()).append("\n");
        }

        if (updateInfo.releaseName() != null) {
            content.append("\nRelease: ").append(updateInfo.releaseName()).append("\n");
        }

        if (updateInfo.releaseNotes() != null && !updateInfo.releaseNotes().isBlank()) {
            String notes = updateInfo.releaseNotes();
            if (notes.length() > 300) {
                notes = notes.substring(0, 300) + "...";
            }
            content.append("\nRelease Notes:\n").append(notes);
        }

        if (updateInfo.downloadUrl() != null) {
            content.append("\n\nDownload at:\n").append(updateInfo.downloadUrl());
        }

        alert.setContentText(content.toString());
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    private static @NotNull String getString(StringBuilder headerString, ConnectionResult connectionResult) {
        final String temp = headerString.toString().trim();
        final String body = connectionResult.resultBody() != null ? connectionResult.resultBody().trim() : "";

        return "URL: " + connectionResult.url() + "\n\n" +
                "Http Status: " + connectionResult.httpStatus() + "\n\n" +
                "Duration: " + connectionResult.duration() + " ms\n\n" +
                "Result Header: \n" + temp.substring(0, Math.min(temp.length(), 100)) + "\n\n" +
                "Result Body: \n" + body.substring(0, Math.min(body.length(), 100)) + "...";
    }

    @FXML
    private void performSave() {
        try {
            props.setProperty("customTempFolder", customTempFolder.getText());
            props.setProperty("useCustomTempFolder", String.valueOf(useCustomTempFolder.isSelected()));
            props.setProperty("useSystemTempFolder", String.valueOf(useSystemTempFolder.isSelected()));

            props.setProperty("useSystemProxy", String.valueOf(systemProxy.isSelected()));
            props.setProperty("manualProxy", String.valueOf(manualProxy.isSelected()));
            props.setProperty("noProxyHost", noProxyHost.getText());
            props.setProperty("http.proxy.host", httpProxyHost.getText());
            props.setProperty("http.proxy.port", portSpinner.getValue().toString());
            props.setProperty("http.proxy.user", httpProxyUser.getText());
            props.setProperty("http.proxy.password", httpProxyPass.getText());
            
            // Save SSL settings
            props.setProperty("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));

            // Save Update settings
            propertiesService.setUpdateCheckEnabled(checkForUpdatesOnStartup.isSelected());

            props.setProperty("xml.indent.spaces", xmlIndentSpaces.getValue().toString());
            props.setProperty("xml.autoformat.after.loading", String.valueOf(autoFormatXmlAfterLoading.isSelected()));

            // Save Schematron settings
            propertiesService.setSchematronPrettyPrintOnLoad(schematronPrettyPrintOnLoad.isSelected());

            // Save XML Auto-Creation settings
            props.setProperty("xml.createAllChildNodes", String.valueOf(createAllChildNodes.isSelected()));
            props.setProperty("xml.fillWithSampleValues", String.valueOf(fillNodesWithSampleValues.isSelected()));

            // Save XSD Editor settings
            props.setProperty("xsd.autoSave.enabled", String.valueOf(xsdAutoSaveEnabled.isSelected()));
            props.setProperty("xsd.autoSave.interval", xsdAutoSaveInterval.getValue().toString());
            props.setProperty("xsd.backup.enabled", String.valueOf(xsdBackupEnabled.isSelected()));
            props.setProperty("xsd.backup.versions", xsdBackupVersions.getValue().toString());
            props.setProperty("xsd.prettyPrint.onSave", String.valueOf(xsdPrettyPrintOnSave.isSelected()));

            // Save UI settings
            props.setProperty("ui.theme", darkTheme.isSelected() ? "dark" : "light");
            props.setProperty("ui.xml.font.size", xmlFontSize.getValue().toString());

            // Save XML editor theme
            String selectedTheme = xmlThemeComboBox.getSelectionModel().getSelectedItem();
            if (selectedTheme != null) {
                props.setProperty("xml.editor.theme", selectedTheme);
                // Apply the theme immediately
                ModernXmlThemeManager.getInstance().setCurrentThemeByDisplayName(selectedTheme);
            }

            // Save XML parser type
            XmlParserType selectedParser = xmlParserComboBox.getSelectionModel().getSelectedItem();
            if (selectedParser != null) {
                propertiesService.setXmlParserType(selectedParser);
            }

            // Save usage statistics setting
            props.setProperty("sendUsageStatistics", String.valueOf(sendUsageStatistics.isSelected()));

            // Save toolbar icon size setting
            propertiesService.setUseSmallIcons(useSmallIcons.isSelected());

            propertiesService.saveProperties(props);

            // Apply toolbar icon size changes immediately
            if (parentController != null) {
                parentController.refreshToolbarIcons();
                logger.debug("Applied toolbar icon size changes immediately");
            }

            // Show success message
            showAlert(Alert.AlertType.INFORMATION, "Settings Saved", "Your settings have been saved successfully.");

        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            // Show error message
            showAlert(Alert.AlertType.ERROR, "Save Error", "Could not save settings. Please check the logs for details.\n" + e.getMessage());
        }
    }

    @FXML
    private void selectCustomTempFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Custom Temporary Folder");
        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            customTempFolder.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void loadCurrentSettings() {
        props = propertiesService.loadProperties();

        // Load proxy settings
        boolean useSystem = Boolean.parseBoolean(props.getProperty("useSystemProxy", "true"));
        boolean useManual = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));

        if (useManual) {
            manualProxy.setSelected(true);
            enableProxyFields(true);
            httpProxyHost.setText(props.getProperty("http.proxy.host", ""));
            int port = 8080;
            try {
                port = Integer.parseInt(props.getProperty("http.proxy.port", "8080"));
            } catch (NumberFormatException e) {
                logger.warn("Invalid port in settings, defaulting to 8080.");
            }
            portSpinner.getValueFactory().setValue(port);
            httpProxyUser.setText(props.getProperty("http.proxy.user", ""));
            httpProxyPass.setText(props.getProperty("http.proxy.password", ""));
            noProxyHost.setText(props.getProperty("noProxyHost", ""));
        } else if (useSystem) {
            systemProxy.setSelected(true);
            enableProxyFields(false);
        } else {
            noProxy.setSelected(true);
            enableProxyFields(false);
        }

        // Load SSL settings
        boolean trustAllCertificates = Boolean.parseBoolean(props.getProperty("ssl.trustAllCerts", "false"));
        trustAllCerts.setSelected(trustAllCertificates);

        // Load Update settings
        checkForUpdatesOnStartup.setSelected(propertiesService.isUpdateCheckEnabled());

        // Load temp folder settings
        if (props.get("customTempFolder") != null && !props.getProperty("customTempFolder").isBlank()) {
            useCustomTempFolder.setSelected(true);
            customTempFolder.setText(props.get("customTempFolder").toString());
            enableTempFolderFields(true);
        } else {
            useSystemTempFolder.setSelected(true);
            enableTempFolderFields(false);
        }

        // Load XML indent spaces setting
        int indentSpaces = propertiesService.getXmlIndentSpaces();
        xmlIndentSpaces.getValueFactory().setValue(indentSpaces);

        // Load XML autoformat setting
        boolean autoFormat = Boolean.parseBoolean(props.getProperty("xml.autoformat.after.loading", "false"));
        autoFormatXmlAfterLoading.setSelected(autoFormat);

        // Load Schematron autoformat setting
        schematronPrettyPrintOnLoad.setSelected(propertiesService.isSchematronPrettyPrintOnLoad());

        // Load XML Auto-Creation settings
        boolean createAllChildren = Boolean.parseBoolean(props.getProperty("xml.createAllChildNodes", "false"));
        createAllChildNodes.setSelected(createAllChildren);

        boolean fillWithSampleValues = Boolean.parseBoolean(props.getProperty("xml.fillWithSampleValues", "false"));
        fillNodesWithSampleValues.setSelected(fillWithSampleValues);

        // Load XSD Editor settings
        xsdAutoSaveEnabled.setSelected(propertiesService.isXsdAutoSaveEnabled());
        xsdAutoSaveInterval.getValueFactory().setValue(propertiesService.getXsdAutoSaveInterval());
        xsdAutoSaveInterval.setDisable(!propertiesService.isXsdAutoSaveEnabled());

        xsdBackupEnabled.setSelected(propertiesService.isXsdBackupEnabled());
        xsdBackupVersions.getValueFactory().setValue(propertiesService.getXsdBackupVersions());
        xsdBackupVersions.setDisable(!propertiesService.isXsdBackupEnabled());

        xsdPrettyPrintOnSave.setSelected(propertiesService.isXsdPrettyPrintOnSave());

        // Load UI settings
        String theme = props.getProperty("ui.theme", "light");
        if ("dark".equals(theme)) {
            darkTheme.setSelected(true);
        } else {
            lightTheme.setSelected(true);
        }

        int fontSize = 12;
        try {
            fontSize = Integer.parseInt(props.getProperty("ui.xml.font.size", "12"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid font size in settings, defaulting to 12.");
        }
        xmlFontSize.getValueFactory().setValue(fontSize);

        // Load XML editor theme
        String xmlTheme = props.getProperty("xml.editor.theme", "Modern Light");
        ModernXmlThemeManager themeManager = ModernXmlThemeManager.getInstance();
        if (xmlThemeComboBox.getItems().contains(xmlTheme)) {
            xmlThemeComboBox.getSelectionModel().select(xmlTheme);
        } else {
            // Select current theme if saved theme not found
            String currentThemeName = themeManager.getCurrentTheme().getDisplayName();
            xmlThemeComboBox.getSelectionModel().select(currentThemeName);
        }

        // Load XML parser type
        XmlParserType parserType = propertiesService.getXmlParserType();
        xmlParserComboBox.getSelectionModel().select(parserType);

        // Load usage statistics setting
        boolean sendStats = Boolean.parseBoolean(props.getProperty("sendUsageStatistics", "false"));
        sendUsageStatistics.setSelected(sendStats);

        // Load toolbar icon size setting
        boolean smallIcons = propertiesService.isUseSmallIcons();
        useSmallIcons.setSelected(smallIcons);
    }

    // Favorites Management Methods

    private void initializeFavoritesTable() {
        // Set up table columns
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("folderName"));
        fileTypeColumn.setCellValueFactory(new PropertyValueFactory<>("fileType"));
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("filePath"));

        // Set the data source
        favoritesTable.setItems(favoritesData);

        // Load favorites data
        refreshFavoritesTable();

        // Enable/disable buttons based on selection
        favoritesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            openFavoriteButton.setDisable(!hasSelection);
            editFavoriteButton.setDisable(!hasSelection);
            removeFavoriteButton.setDisable(!hasSelection);
        });

        // Initially disable action buttons
        openFavoriteButton.setDisable(true);
        editFavoriteButton.setDisable(true);
        removeFavoriteButton.setDisable(true);
    }

    private void refreshFavoritesTable() {
        favoritesData.clear();
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        favoritesData.addAll(favorites);
        logger.debug("Loaded {} favorites into table", favorites.size());
    }

    @FXML
    private void openSelectedFavorite() {
        FileFavorite selectedFavorite = favoritesTable.getSelectionModel().getSelectedItem();
        if (selectedFavorite == null) {
            return;
        }

        File file = new File(selectedFavorite.getFilePath());
        if (!file.exists()) {
            showAlert(Alert.AlertType.ERROR, "File Not Found",
                    "The file no longer exists at: " + selectedFavorite.getFilePath());
            return;
        }

        // Open the file in the appropriate editor based on file type
        if (parentController != null) {
            try {
                switch (selectedFavorite.getFileType()) {
                    case XML -> parentController.openXmlFileInEditor(file);
                    case XSD -> parentController.openXsdFileInEditor(file);
                    case SCHEMATRON -> parentController.openSchematronFileInEditor(file);
                    default -> parentController.openXmlFileInEditor(file); // fallback to XML editor
                }
                showAlert(Alert.AlertType.INFORMATION, "File Opened",
                        "Opened " + selectedFavorite.getName() + " in the appropriate editor.");
            } catch (Exception e) {
                logger.error("Failed to open favorite file", e);
                showAlert(Alert.AlertType.ERROR, "Error Opening File",
                        "Could not open file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void editSelectedFavorite() {
        FileFavorite selectedFavorite = favoritesTable.getSelectionModel().getSelectedItem();
        if (selectedFavorite == null) {
            return;
        }

        // Create a dialog to edit the favorite
        Dialog<FileFavorite> dialog = new Dialog<>();
        dialog.setTitle("Edit Favorite");
        dialog.setHeaderText("Edit favorite: " + selectedFavorite.getName());

        // Set up dialog buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(selectedFavorite.getName());
        TextField categoryField = new TextField(selectedFavorite.getFolderName());
        TextArea descriptionArea = new TextArea(selectedFavorite.getDescription());
        descriptionArea.setPrefRowCount(3);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descriptionArea, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Convert result when Save is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                selectedFavorite.setName(nameField.getText().trim());
                selectedFavorite.setFolderName(categoryField.getText().trim());
                selectedFavorite.setDescription(descriptionArea.getText().trim());
                return selectedFavorite;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(favorite -> {
            favoritesService.updateFavorite(favorite);
            refreshFavoritesTable();
            showAlert(Alert.AlertType.INFORMATION, "Favorite Updated",
                    "Successfully updated favorite: " + favorite.getName());
        });
    }

    @FXML
    private void removeSelectedFavorite() {
        FileFavorite selectedFavorite = favoritesTable.getSelectionModel().getSelectedItem();
        if (selectedFavorite == null) {
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Remove Favorite");
        confirmDialog.setHeaderText("Remove favorite?");
        confirmDialog.setContentText("Are you sure you want to remove \"" + selectedFavorite.getName() + "\" from favorites?");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                favoritesService.removeFavorite(selectedFavorite.getId());
                refreshFavoritesTable();
                showAlert(Alert.AlertType.INFORMATION, "Favorite Removed",
                        "Successfully removed favorite: " + selectedFavorite.getName());
            }
        });
    }

    @FXML
    private void removeNonExistentFavorites() {
        List<FileFavorite> allFavorites = favoritesService.getAllFavorites();
        List<FileFavorite> nonExistentFavorites = allFavorites.stream()
                .filter(favorite -> !new File(favorite.getFilePath()).exists())
                .toList();

        if (nonExistentFavorites.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Non-Existent Files",
                    "All favorites point to existing files.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Remove Non-Existent Favorites");
        confirmDialog.setHeaderText("Remove " + nonExistentFavorites.size() + " non-existent favorites?");
        confirmDialog.setContentText("This will remove all favorites that point to files that no longer exist.");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int removedCount = 0;
                for (FileFavorite favorite : nonExistentFavorites) {
                    favoritesService.removeFavorite(favorite.getId());
                    removedCount++;
                }
                refreshFavoritesTable();
                showAlert(Alert.AlertType.INFORMATION, "Favorites Cleaned",
                        "Removed " + removedCount + " non-existent favorites.");
            }
        });
    }

    @FXML
    private void createNewCategory() {
        String categoryName = newCategoryField.getText().trim();
        if (categoryName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Category Name",
                    "Please enter a category name.");
            return;
        }

        // Check if category already exists
        List<String> existingCategories = favoritesService.getAllFavorites().stream()
                .map(FileFavorite::getFolderName)
                .distinct()
                .toList();

        if (existingCategories.contains(categoryName)) {
            showAlert(Alert.AlertType.WARNING, "Category Exists",
                    "A category with the name \"" + categoryName + "\" already exists.");
            return;
        }

        // Create a placeholder favorite in this category (will be removed when first real favorite is added)
        FileFavorite placeholder = new FileFavorite();
        placeholder.setName("_placeholder_");
        placeholder.setFolderName(categoryName);
        placeholder.setFilePath("");
        placeholder.setFileType(FileFavorite.FileType.OTHER);
        placeholder.setDescription("Category placeholder - will be removed automatically");

        favoritesService.addFavorite(placeholder);

        // Clear the input field and show success message
        newCategoryField.clear();
        showAlert(Alert.AlertType.INFORMATION, "Category Created",
                "Created new category: " + categoryName + "\n\nThe category will appear in dropdowns when adding favorites.");

        logger.info("Created new favorites category: {}", categoryName);
    }

    /**
     * A helper method to display notifications.
     *
     * @param alertType The type of alert (e.g., INFORMATION or ERROR).
     * @param title     The title of the window.
     * @param content   The message to display.
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Cache Management Methods

    /**
     * Updates the cache size and path labels with current cache folder information.
     */
    private void updateCacheSizeLabel() {
        try {
            File cacheDir = getCacheFolder();
            if (cacheDir != null) {
                // Update cache path label
                cachePathLabel.setText(cacheDir.getAbsolutePath());

                if (cacheDir.exists()) {
                    long size = calculateFolderSize(cacheDir);
                    cacheSizeLabel.setText("Cache size: " + formatFileSize(size));
                } else {
                    cacheSizeLabel.setText("Cache size: 0 B (folder does not exist yet)");
                }
            } else {
                cachePathLabel.setText("Not configured");
                cacheSizeLabel.setText("Cache size: 0 B");
            }
        } catch (Exception e) {
            logger.warn("Could not calculate cache size: {}", e.getMessage());
            cachePathLabel.setText("Error loading path");
            cacheSizeLabel.setText("Cache size: unknown");
        }
    }

    /**
     * Gets the cache folder.
     * This returns the folder where cached files are stored.
     *
     * @return The cache folder (~/.freeXmlToolkit/cache/)
     */
    private File getCacheFolder() {
        return new File(
                org.apache.commons.io.FileUtils.getUserDirectory(),
                ".freeXmlToolkit" + File.separator + "cache"
        );
    }

    /**
     * Clears the local cache folder.
     */
    @FXML
    private void clearCache() {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Clear Cache");
        confirmDialog.setHeaderText("Clear Local Cache?");
        confirmDialog.setContentText("This will delete all temporary files created by the application.\n\nAre you sure you want to continue?");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    File cacheDir = getCacheFolder();
                    if (cacheDir != null && cacheDir.exists()) {
                        int deletedFiles = deleteDirectoryContents(cacheDir);
                        updateCacheSizeLabel();
                        showAlert(Alert.AlertType.INFORMATION, "Cache Cleared",
                                "Successfully deleted " + deletedFiles + " cached file(s).");
                        logger.info("Cache cleared: {} files deleted from {}", deletedFiles, cacheDir.getAbsolutePath());
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Cache Empty",
                                "The cache folder is empty or does not exist.");
                    }
                } catch (Exception e) {
                    logger.error("Failed to clear cache", e);
                    showAlert(Alert.AlertType.ERROR, "Error Clearing Cache",
                            "Could not clear the cache: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Opens the cache folder in the system file explorer.
     */
    @FXML
    private void openCacheFolder() {
        try {
            File cacheDir = getCacheFolder();
            if (cacheDir != null) {
                // Create folder if it doesn't exist
                if (!cacheDir.exists()) {
                    boolean created = cacheDir.mkdirs();
                    if (!created) {
                        showAlert(Alert.AlertType.ERROR, "Error",
                                "Could not create cache folder: " + cacheDir.getAbsolutePath());
                        return;
                    }
                    logger.info("Created cache folder: {}", cacheDir.getAbsolutePath());
                }

                // Open folder in system file explorer
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(cacheDir);
                    logger.debug("Opened cache folder: {}", cacheDir.getAbsolutePath());
                } else {
                    // Fallback: show the path in a dialog
                    showAlert(Alert.AlertType.INFORMATION, "Cache Folder Location",
                            "Cache folder path:\n\n" + cacheDir.getAbsolutePath() +
                                    "\n\nNote: Your system does not support opening folders directly.");
                }
            } else {
                showAlert(Alert.AlertType.WARNING, "No Cache Folder",
                        "Cache folder is not configured.");
            }
        } catch (IOException e) {
            logger.error("Failed to open cache folder", e);
            showAlert(Alert.AlertType.ERROR, "Error Opening Folder",
                    "Could not open the cache folder: " + e.getMessage());
        }
    }

    /**
     * Recursively calculates the total size of a folder.
     *
     * @param folder The folder to calculate
     * @return The total size in bytes
     */
    private long calculateFolderSize(File folder) {
        long size = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateFolderSize(file);
                }
            }
        }
        return size;
    }

    /**
     * Deletes the contents of a directory (but not the directory itself).
     *
     * @param directory The directory to clear
     * @return The number of files deleted
     */
    private int deleteDirectoryContents(File directory) {
        int deletedCount = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deletedCount += deleteDirectoryContents(file);
                    file.delete();
                } else {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
        }
        return deletedCount;
    }

    /**
     * Formats a file size in bytes to a human-readable string.
     *
     * @param size Size in bytes
     * @return Human-readable string (e.g., "1.5 MB")
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
