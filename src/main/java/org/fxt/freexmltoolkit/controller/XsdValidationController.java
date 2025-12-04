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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.BatchValidationFile;
import org.fxt.freexmltoolkit.domain.ValidationStatus;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.kordamp.ikonli.javafx.FontIcon;
import org.xml.sax.SAXParseException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller class for handling XSD validation operations.
 */
public class XsdValidationController implements FavoritesParentController {

    private static final Logger logger = LogManager.getLogger(XsdValidationController.class);
    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final FileChooser xmlFileChooser = new FileChooser();
    private final FileChooser xsdFileChooser = new FileChooser();
    private final FileChooser excelFileChooser = new FileChooser();
    private List<SAXParseException> validationErrors;
    private MainController parentController;

    // Enum for single file validation status display
    private enum SingleFileValidationStatus {SUCCESS, ERROR, READY}

    @FXML
    private VBox rootVBox;
    @FXML
    private Button xmlLoadButton, xsdLoadButton, excelExport, clearResults;
    @FXML
    private Button xmlLoadButtonGrid, xsdLoadButtonGrid;
    @FXML
    private TextField xmlFileName, xsdFileName, remoteXsdLocation;
    @FXML
    private VBox errorListBox;
    @FXML
    private CheckBox autodetect;
    @FXML
    private ProgressIndicator progressIndicator;

    // KORREKTUR: FXML-Felder für die neue Statusleiste
    @FXML
    private HBox statusPane;
    @FXML
    private ImageView statusImage;
    @FXML
    private Label statusLabel;

    // UI Components - Favorites (unified FavoritesPanel)
    @FXML
    private Button addToFavoritesBtn;
    @FXML
    private Button toggleFavoritesButton;
    @FXML
    private Button validateBtn, helpBtn;
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private FavoritesPanelController favoritesPanelController;

    // UI Components - Empty State
    @FXML
    private VBox emptyStatePane;
    @FXML
    private ScrollPane contentPane;
    @FXML
    private Button emptyStateOpenXmlButton;
    @FXML
    private Button emptyStateFavoritesButton;

    // UI Components - Batch Validation Mode
    @FXML
    private TabPane validationModeTabPane;
    @FXML
    private RadioButton sameXsdRadio;
    @FXML
    private RadioButton autoDetectXsdRadio;
    @FXML
    private TextField batchXsdFileName;
    @FXML
    private Button selectBatchXsdBtn;
    @FXML
    private Button addBatchFilesBtn;
    @FXML
    private Button addBatchFolderBtn;
    @FXML
    private Button removeBatchSelectedBtn;
    @FXML
    private Button clearBatchBtn;
    @FXML
    private Button runBatchBtn;
    @FXML
    private Button cancelBatchBtn;
    @FXML
    private Button exportBatchAllBtn;
    @FXML
    private Button exportBatchSelectedBtn;
    @FXML
    private HBox batchProgressPane;
    @FXML
    private ProgressBar batchProgressBar;
    @FXML
    private Label batchStatusLabel;
    @FXML
    private Label batchSummaryLabel;
    @FXML
    private TableView<BatchValidationFile> batchFilesTable;
    @FXML
    private TableColumn<BatchValidationFile, String> batchFileNameColumn;
    @FXML
    private TableColumn<BatchValidationFile, String> batchFilePathColumn;
    @FXML
    private TableColumn<BatchValidationFile, ValidationStatus> batchStatusColumn;
    @FXML
    private TableColumn<BatchValidationFile, Integer> batchErrorsColumn;
    @FXML
    private TableColumn<BatchValidationFile, String> batchXsdColumn;
    @FXML
    private TableColumn<BatchValidationFile, String> batchDurationColumn;
    @FXML
    private ComboBox<String> batchFilterCombo;
    @FXML
    private VBox batchErrorDetailsBox;

    // Batch validation state
    private final ObservableList<BatchValidationFile> batchFiles = FXCollections.observableArrayList();
    private FilteredList<BatchValidationFile> filteredBatchFiles;
    private File batchXsdFile;
    private volatile boolean batchCancelled = false;
    private final DirectoryChooser directoryChooser = new DirectoryChooser();

    /**
     * Sets the parent controller.
     *
     * @param parentController the parent controller
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Toggles the auto-detection of XSD files.
     * When autodetect is checked, manual XSD selection is disabled.
     * When autodetect is unchecked, manual XSD selection is enabled.
     */
    @FXML
    public void toggleAutoDetection() {
        // When autodetect is selected (ON), manual XSD selection should be disabled
        boolean disableManualSelection = autodetect.isSelected();
        xsdLoadButton.setDisable(disableManualSelection);
        xsdFileName.setDisable(disableManualSelection);
        if (xsdLoadButtonGrid != null) {
            xsdLoadButtonGrid.setDisable(disableManualSelection);
        }
    }

    /**
     * Initializes the controller and sets up the file choosers and event handlers.
     */
    @FXML
    private void initialize() {
        Path path = FileSystems.getDefault().getPath(".");
        xmlFileChooser.setInitialDirectory(path.toFile());
        xsdFileChooser.setInitialDirectory(path.toFile());
        xmlFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML File", "*.xml"));
        xsdFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD File", "*.xsd"));

        xmlLoadButton.setOnAction(ae -> loadFile(xmlFileChooser, this::processXmlFile));

        xsdLoadButton.setOnAction(ae -> loadFile(xsdFileChooser, file -> {
            xmlService.setCurrentXsdFile(file);
            xsdFileName.setText(file.getName());
            if (xmlService.getCurrentXmlFile() != null) processXmlFile();
        }));

        // Register drag and drop for the entire validation page
        setupDragAndDrop();

        if (System.getenv("debug") != null) {
            logger.debug("Debug mode enabled for XsdValidationController");
            // Debug mode - test functionality can be added here if needed
        }

        // Set initial UI state
        resetUI();
        initializeFavorites();
        initializeEmptyState();
        initializeBatchValidation();
        applySmallIconsSetting();
    }

    private void initializeFavorites() {
        // Initialize the unified FavoritesPanel controller
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
            logger.debug("FavoritesPanelController initialized");
        }

        // Initially hide favorites panel
        if (favoritesPanel != null && mainSplitPane != null) {
            mainSplitPane.getItems().remove(favoritesPanel);
            favoritesPanel.setVisible(false);
            favoritesPanel.setManaged(false);
        }

        // Wire up toolbar buttons
        if (addToFavoritesBtn != null) {
            addToFavoritesBtn.setOnAction(e -> addCurrentToFavorites());
        }

        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }
    }

    /**
     * Initializes the empty state UI and wires up button actions.
     */
    private void initializeEmptyState() {
        // Wire up empty state buttons to trigger main actions
        if (emptyStateOpenXmlButton != null) {
            emptyStateOpenXmlButton.setOnAction(e -> xmlLoadButton.fire());
        }

        if (emptyStateFavoritesButton != null) {
            emptyStateFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }
    }

    /**
     * Shows the main content and hides the empty state placeholder.
     * Called when files are loaded.
     */
    private void showContent() {
        if (emptyStatePane != null && contentPane != null) {
            emptyStatePane.setVisible(false);
            emptyStatePane.setManaged(false);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            logger.debug("Switched from empty state to content view");
        }
    }

    /**
     * Adds the current XML or XSD file to favorites.
     */
    private void addCurrentToFavorites() {
        File currentFile = getCurrentFile();

        if (currentFile == null) {
            showAlert("No Files Loaded", "Please load an XML or XSD file before adding to favorites.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Validation");
        dialog.setTitle("Add to Favorites");
        dialog.setHeaderText("Add " + currentFile.getName() + " to favorites");
        dialog.setContentText("Category:");

        dialog.showAndWait().ifPresent(category -> {
            org.fxt.freexmltoolkit.domain.FileFavorite fav = new org.fxt.freexmltoolkit.domain.FileFavorite(
                    currentFile.getName(),
                    currentFile.getAbsolutePath(),
                    category
            );
            ServiceRegistry.get(FavoritesService.class).addFavorite(fav);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Favorites");
            alert.setHeaderText(null);
            alert.setContentText("File added to favorites");
            alert.showAndWait();

            logger.info("Added to favorites: {}", currentFile.getName());
        });
    }

    /**
     * Toggles the favorites panel visibility.
     * Also callable from MainController for Ctrl+Shift+D shortcut
     */
    public void toggleFavoritesPanelPublic() {
        toggleFavoritesPanelInternal();
    }

    private void toggleFavoritesPanel() {
        toggleFavoritesPanelInternal();
    }

    private void toggleFavoritesPanelInternal() {
        if (favoritesPanel == null || mainSplitPane == null) {
            return;
        }

        boolean isCurrentlyShown = mainSplitPane.getItems().contains(favoritesPanel);

        if (!isCurrentlyShown) {
            // Show the panel
            favoritesPanel.setVisible(true);
            favoritesPanel.setManaged(true);
            mainSplitPane.getItems().add(favoritesPanel);
            mainSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            mainSplitPane.getItems().remove(favoritesPanel);
            favoritesPanel.setVisible(false);
            favoritesPanel.setManaged(false);
        }

        logger.debug("Favorites panel toggled: {}", !isCurrentlyShown ? "shown" : "hidden");
    }

    // ======================================================================
    // FavoritesParentController Interface Implementation
    // ======================================================================

    /**
     * Load a file from favorites into the validation tab.
     * Implementation of FavoritesParentController interface.
     *
     * @param file the file to load
     */
    @Override
    public void loadFileToNewTab(File file) {
        if (file == null || !file.exists()) {
            logger.warn("Cannot load file from favorites - file is null or does not exist: {}", file);
            return;
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xml")) {
            processXmlFile(file);
            logger.info("Loaded XML file from favorites: {}", file.getAbsolutePath());
        } else if (fileName.endsWith(".xsd")) {
            xmlService.setCurrentXsdFile(file);
            xsdFileName.setText(file.getName());
            showContent();
            logger.info("Loaded XSD file from favorites: {}", file.getAbsolutePath());
        } else {
            showAlert("Unsupported File", "Only XML and XSD files are supported for validation.");
        }
    }

    /**
     * Get the currently loaded file (XML has priority over XSD).
     * Implementation of FavoritesParentController interface.
     *
     * @return the current file, or null if no file is open
     */
    @Override
    public File getCurrentFile() {
        File currentXmlFile = xmlService.getCurrentXmlFile();
        if (currentXmlFile != null) {
            return currentXmlFile;
        }
        return xmlService.getCurrentXsdFile();
    }

    /**
     * Sets up drag and drop functionality for the entire validation page.
     * This prevents the global drag and drop handler from interfering with validation-specific behavior.
     */
    private void setupDragAndDrop() {
        logger.debug("Setting up drag and drop for validation page");

        rootVBox.setOnDragOver(this::handlePageDragOver);
        rootVBox.setOnDragDropped(this::handlePageDragDropped);

        logger.debug("Drag and drop registered for validation page");
    }

    /**
     * Handles drag over event for the entire validation page.
     * Accepts both XML files (for validation) and XSD files (as schema).
     *
     * @param event the drag event
     */
    private void handlePageDragOver(javafx.scene.input.DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            // Check if at least one XML or XSD file is in the drag
            boolean hasValidFile = event.getDragboard().getFiles().stream()
                    .anyMatch(file -> {
                        String name = file.getName().toLowerCase();
                        return name.endsWith(".xml") || name.endsWith(".xsd");
                    });

            if (hasValidFile) {
                event.acceptTransferModes(TransferMode.COPY);
                logger.debug("Validation page accepting XML/XSD file drag");
            }
        }
        event.consume(); // Prevent event from bubbling to global handler
    }

    /**
     * Handles drag dropped event for the entire validation page.
     * Loads XML files for validation and XSD files as schema.
     *
     * @param event the drag event
     */
    private void handlePageDragDropped(javafx.scene.input.DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            logger.info("Files dropped on validation page: processing {} files", db.getFiles().size());

            // Separate XML and XSD files
            var xmlFiles = db.getFiles().stream()
                    .filter(file -> file.getName().toLowerCase().endsWith(".xml"))
                    .toList();
            var xsdFiles = db.getFiles().stream()
                    .filter(file -> file.getName().toLowerCase().endsWith(".xsd"))
                    .toList();

            // Process XSD files first (set as schema)
            if (!xsdFiles.isEmpty()) {
                File firstXsdFile = xsdFiles.get(0);
                xmlService.setCurrentXsdFile(firstXsdFile);
                xsdFileName.setText(firstXsdFile.getName());
                showContent();
                success = true;
                logger.info("Loaded XSD schema via drag and drop: {}", firstXsdFile.getName());

                if (xsdFiles.size() > 1) {
                    logger.info("Multiple XSD files dropped ({}). Only the first file was used as schema: {}",
                            xsdFiles.size(), firstXsdFile.getName());
                }
            }

            // Process XML files (for validation)
            if (!xmlFiles.isEmpty()) {
                File firstXmlFile = xmlFiles.get(0);
                processXmlFile(firstXmlFile);
                success = true;
                logger.info("Loaded XML file for validation via drag and drop: {}", firstXmlFile.getName());

                if (xmlFiles.size() > 1) {
                    logger.info("Multiple XML files dropped ({}). Only the first file was loaded: {}",
                            xmlFiles.size(), firstXmlFile.getName());
                }
            } else if (xsdFiles.isEmpty()) {
                logger.debug("No XML or XSD files found in dropped files on validation page");
            }
        }

        event.setDropCompleted(success);
        event.consume(); // Prevent event from bubbling to global handler
    }

    /**
     * Loads a file using the specified file chooser and processes it with the given file processor.
     *
     * @param fileChooser   the file chooser
     * @param fileProcessor the file processor
     */
    private void loadFile(FileChooser fileChooser, java.util.function.Consumer<File> fileProcessor) {
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            logger.debug("Loaded File: {}", file.getAbsolutePath());
            fileProcessor.accept(file);
        }
    }

    /**
     * FXML handler for XML file selection button in the grid.
     * Opens a file chooser dialog to select an XML file for validation.
     */
    @FXML
    public void selectXmlFile() {
        File file = xmlFileChooser.showOpenDialog(null);
        if (file != null) {
            logger.debug("Selected XML file: {}", file.getAbsolutePath());
            processXmlFile(file);
        }
    }

    /**
     * FXML handler for XSD file selection button in the grid.
     * Opens a file chooser dialog to select an XSD schema file.
     */
    @FXML
    public void selectXsdFile() {
        File file = xsdFileChooser.showOpenDialog(null);
        if (file != null) {
            logger.debug("Selected XSD file: {}", file.getAbsolutePath());
            xmlService.setCurrentXsdFile(file);
            xsdFileName.setText(file.getName());
            showContent();
            // If XML file is already loaded, trigger validation
            if (xmlService.getCurrentXmlFile() != null) {
                processXmlFile();
            }
        }
    }

    /**
     * Processes the currently selected XML file.
     * Also callable from MainController for F5 shortcut
     */
    @FXML
    public void processXmlFile() {
        processXmlFile(xmlService.getCurrentXmlFile());
    }

    /**
     * Processes the specified XML file.
     *
     * @param file the XML file
     */
    private void processXmlFile(File file) {
        if (file == null) {
            updateStatus(SingleFileValidationStatus.ERROR, "No XML file selected.");
            return;
        }

        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0.1);
        resetUI();

        Platform.runLater(() -> {
            xmlService.setCurrentXmlFile(file);
            xmlService.prettyFormatCurrentFile();
            xmlFileName.setText(file.getName());
            showContent();  // Show main content, hide empty state
            progressIndicator.setProgress(0.2);

            if (autodetect.isSelected()) {
                xmlService.getSchemaNameFromCurrentXMLFile().ifPresentOrElse(
                        schemaName -> {
                            if (xmlService.loadSchemaFromXMLFile()) {
                                logger.debug("Loaded remote schema successfully!");
                                xsdFileName.setText(schemaName);
                            } else {
                                logger.debug("Could not load remote schema");
                                xsdFileName.setText("");
                                xmlService.setCurrentXsdFile(null);
                            }
                        },
                        () -> {
                            logger.debug("Could not load remote schema");
                            xsdFileName.setText("");
                            xmlService.setCurrentXsdFile(null);
                        }
                );
            } else if (xmlService.getCurrentXsdFile() != null) {
                xsdFileName.setText(xmlService.getCurrentXsdFile().getName());
            }

            if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXsdFile() != null) {
                progressIndicator.setProgress(0.4);
                validationErrors = xmlService.validate();
                displayValidationResults();
            } else {
                logger.debug("Schema not found!");
                updateStatus(SingleFileValidationStatus.ERROR, "Schema not found. Please select a schema manually or ensure autodetect can find it.");
            }

            progressIndicator.setProgress(1.0);
            progressIndicator.setVisible(false);
        });
    }

    /**
     * Resets the UI elements to their default state.
     */
    private void resetUI() {
        remoteXsdLocation.setText("");
        errorListBox.getChildren().clear();
        updateStatus(SingleFileValidationStatus.READY, "Ready for validation.");
    }

    /**
     * Displays the validation results in the UI.
     */
    private void displayValidationResults() {
        // Add header with filename and timestamp
        addValidationResultsHeader();

        if (validationErrors != null && !validationErrors.isEmpty()) {
            logger.warn(validationErrors.toString());
            updateStatus(SingleFileValidationStatus.ERROR, "Validation failed. " + validationErrors.size() + " error(s) found.");
            for (int i = 0; i < validationErrors.size(); i++) {
                SAXParseException ex = validationErrors.get(i);
                TextFlow textFlow = createTextFlow(i, ex);
                errorListBox.getChildren().addAll(textFlow, createGoToErrorButton(), new Separator());
            }
        } else {
            // Show clear success message
            logger.debug("No errors in validation");
            updateStatus(SingleFileValidationStatus.SUCCESS, "Validation successful. No errors found.");

            Label successLabel = new Label("The XML file is valid according to the provided schema.");
            successLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green; -fx-font-size: 14px;");
            FontIcon icon = new FontIcon("bi-check-circle-fill");
            icon.setIconColor(Color.GREEN);
            icon.setIconSize(18);
            successLabel.setGraphic(icon);
            successLabel.setGraphicTextGap(10);

            errorListBox.getChildren().add(successLabel);
        }
    }

    /**
     * Adds a header with filename and timestamp to the validation results.
     */
    private void addValidationResultsHeader() {
        File currentFile = xmlService.getCurrentXmlFile();
        if (currentFile == null) return;

        // Create header with filename
        Label fileLabel = new Label("File: " + currentFile.getName());
        fileLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        FontIcon fileIcon = new FontIcon("bi-file-earmark-code");
        fileIcon.setIconSize(16);
        fileLabel.setGraphic(fileIcon);
        fileLabel.setGraphicTextGap(8);

        // Create timestamp label
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Label timestampLabel = new Label("Validated: " + timestamp);
        timestampLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        FontIcon clockIcon = new FontIcon("bi-clock");
        clockIcon.setIconSize(14);
        clockIcon.setIconColor(Color.web("#6c757d"));
        timestampLabel.setGraphic(clockIcon);
        timestampLabel.setGraphicTextGap(6);

        // Create header container
        VBox headerBox = new VBox(3);
        headerBox.setStyle("-fx-padding: 0 0 10 0; -fx-border-color: transparent transparent #dee2e6 transparent; -fx-border-width: 0 0 1 0;");
        headerBox.getChildren().addAll(fileLabel, timestampLabel);

        errorListBox.getChildren().add(headerBox);
    }

    /**
     * KORREKTUR: Neue zentrale Methode zur Steuerung der Statusanzeige.
     *
     * @param status  Der Validierungsstatus (SUCCESS, ERROR, READY).
     * @param message Die anzuzeigende Nachricht.
     */
    private void updateStatus(SingleFileValidationStatus status, String message) {
        if (statusPane == null || statusLabel == null || statusImage == null) return;

        statusLabel.setText(message);
        String style = "-fx-background-radius: 5; -fx-padding: 10;";
        String imagePath = null;

        switch (status) {
            case SUCCESS -> {
                style += "-fx-background-color: #e0f8e0;"; // Light green
                imagePath = "/img/icons8-ok-48.png";
            }
            case ERROR -> {
                style += "-fx-background-color: #f8e0e0;"; // Light red
                imagePath = "/img/icons8-stornieren-48.png";
            }
            case READY -> {
                style += "-fx-background-color: -fx-background-color-subtle;"; // Default background
                imagePath = null;
            }
        }

        statusPane.setStyle(style);
        if (imagePath != null) {
            statusImage.setImage(new Image(Objects.requireNonNull(getClass().getResource(imagePath)).toString()));
        } else {
            statusImage.setImage(null);
        }
    }

    /**
     * Creates a TextFlow element for displaying a validation error.
     *
     * @param index the index of the error
     * @param ex    the SAXParseException representing the error
     * @return the created TextFlow element
     */
    private TextFlow createTextFlow(int index, SAXParseException ex) {
        TextFlow textFlow = new TextFlow();
        textFlow.setLineSpacing(5.0);
        textFlow.getChildren().addAll(
                createText("#" + (index + 1) + ": " + ex.getLocalizedMessage(), 14),
                createText("Line: " + ex.getLineNumber() + " Column: " + ex.getColumnNumber()),
                createText(getFileLine(ex.getLineNumber() - 1)),
                createText(getFileLine(ex.getLineNumber())),
                createText(getFileLine(ex.getLineNumber() + 1))
        );
        return textFlow;
    }

    private Text createText(String content, int fontSize) {
        Text text = new Text(content + System.lineSeparator());
        text.setFont(Font.font("Verdana", fontSize));
        return text;
    }

    private Text createText(String content) {
        return new Text(content + System.lineSeparator());
    }

    private String getFileLine(int lineNumber) {
        if (lineNumber <= 0) return "";
        try {
            return Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(lineNumber - 1).trim();
        } catch (Exception e) {
            // IOException or IndexOutOfBoundsException
            return "";
        }
    }

    private Button createGoToErrorButton() {
        Button goToError = new Button("Go to error");
        goToError.setOnAction(ae -> {
            try {
                TabPane tabPane = (TabPane) rootVBox.getParent().getParent();
                tabPane.getTabs().stream()
                        .filter(tab -> "tabPaneXml".equals(tab.getId()))
                        .findFirst()
                        .ifPresent(tabPane.getSelectionModel()::select);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        });
        return goToError;
    }

    /**
     * Clears the validation results from the UI.
     */
    @FXML
    public void clearResultAction() {
        logger.debug("clear results");
        resetUI();
        xmlFileName.clear();
        xsdFileName.clear();
        remoteXsdLocation.clear();
        xmlService.setCurrentXmlFile(null);
        xmlService.setCurrentXsdFile(null);
        validationErrors = null;
    }

    /**
     * Exports the validation errors to an Excel file.
     */
    @FXML
    public void excelExport() {
        if (validationErrors != null && !validationErrors.isEmpty()) {
            excelFileChooser.setInitialFileName("ValidationErrors.xlsx");
            File exportFile = excelFileChooser.showSaveDialog(null);
            if (exportFile != null) {
                var result = xmlService.createExcelValidationReport(exportFile, validationErrors);
                logger.debug("Written {} bytes.", result.length());
                openFile(exportFile);
            }
        } else {
            showAlert("Export not possible.", "No Errors found.");
        }
    }

    private void openFile(File file) {
        if (file.exists() && file.length() > 0) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                logger.error("Could not open File: {}", file);
            }
        }
    }

    private void showAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Shows the help dialog with usage instructions.
     */
    @FXML
    public void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-file-earmark-code", "Load XML", "Load XML files via toolbar, file selector, or drag & drop"},
                new String[]{"bi-search", "Schema Detection", "Auto-detect XSD from XML or manually select schema file"},
                new String[]{"bi-check-circle", "Validation", "Validate XML against XSD with detailed error reporting"},
                new String[]{"bi-list-check", "Results Review", "Green = success, Red = errors with line numbers"},
                new String[]{"bi-file-earmark-excel", "Export Results", "Export validation errors to Excel spreadsheet"},
                new String[]{"bi-folder2-open", "Batch Validation", "Validate multiple XML files against the same schema"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"F5", "Start validation"},
                new String[]{"Ctrl+D", "Add current file to favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle favorites panel"},
                new String[]{"F1", "Show this help dialog"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "XSD Validation - Help",
                "XSD Validation",
                "Validate XML documents against XSD schemas",
                "bi-check2-square",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts
        );

        helpDialog.showAndWait();
        logger.debug("Help dialog shown");
    }

    /**
     * Applies the small icons setting from user preferences.
     * When enabled, toolbar buttons display in compact mode with smaller icons (14px) and no text labels.
     * When disabled, buttons show both icon and text (TOP display) with normal icon size (20px).
     */
    private void applySmallIconsSetting() {
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to XSD Validation toolbar: {}", useSmallIcons);

        // Determine display mode and icon size
        ContentDisplay displayMode = useSmallIcons
                ? ContentDisplay.GRAPHIC_ONLY
                : ContentDisplay.TOP;

        // Icon sizes: small = 14px, normal = 20px
        int iconSize = useSmallIcons ? 14 : 20;

        // Button style: compact padding for small icons
        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        // Apply to all toolbar buttons
        applyButtonSettings(xmlLoadButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(xsdLoadButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(validateBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(excelExport, displayMode, iconSize, buttonStyle);
        applyButtonSettings(clearResults, displayMode, iconSize, buttonStyle);
        applyButtonSettings(addToFavoritesBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toggleFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(helpBtn, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to XSD Validation toolbar (size: {}px)", iconSize);
    }

    /**
     * Helper method to apply display mode, icon size, and style to a button.
     */
    private void applyButtonSettings(ButtonBase button, ContentDisplay displayMode, int iconSize, String style) {
        if (button == null) return;

        // Set content display mode
        button.setContentDisplay(displayMode);

        // Apply compact style
        button.setStyle(style);

        // Update icon size if the button has a FontIcon graphic
        if (button.getGraphic() instanceof FontIcon fontIcon) {
            fontIcon.setIconSize(iconSize);
        }
    }

    /**
     * Public method to refresh toolbar icons.
     * Can be called from Settings or MainController when icon size preference changes.
     */
    public void refreshToolbarIcons() {
        applySmallIconsSetting();
    }

    // ======================================================================
    // Batch Validation Methods
    // ======================================================================

    /**
     * Initializes the batch validation tab components.
     */
    private void initializeBatchValidation() {
        if (batchFilesTable == null) {
            logger.debug("Batch validation components not available");
            return;
        }

        // Setup filtered list
        filteredBatchFiles = new FilteredList<>(batchFiles, p -> true);
        batchFilesTable.setItems(filteredBatchFiles);

        // Setup table columns
        setupBatchTableColumns();

        // Setup selection listener for error details
        batchFilesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> showFileErrors(newSel));

        // Setup XSD mode radio button listeners
        if (sameXsdRadio != null) {
            sameXsdRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (batchXsdFileName != null) batchXsdFileName.setDisable(!newVal);
                if (selectBatchXsdBtn != null) selectBatchXsdBtn.setDisable(!newVal);
            });
        }

        // Set filter options and default value
        if (batchFilterCombo != null) {
            batchFilterCombo.getItems().addAll("All", "Passed", "Failed", "Pending");
            batchFilterCombo.setValue("All");
        }

        // Initialize directory chooser
        directoryChooser.setTitle("Select Folder with XML Files");
        directoryChooser.setInitialDirectory(FileSystems.getDefault().getPath(".").toFile());

        // Setup multi-select file chooser for batch files
        xmlFileChooser.setTitle("Select XML Files");

        // Setup drag and drop for batch validation
        setupBatchDragAndDrop();

        logger.debug("Batch validation initialized");
    }

    /**
     * Sets up drag and drop functionality for the batch validation table.
     * Allows users to drag and drop XML files and/or folders onto the table.
     */
    private void setupBatchDragAndDrop() {
        if (batchFilesTable == null) {
            logger.debug("Batch files table not available for drag and drop setup");
            return;
        }

        batchFilesTable.setOnDragOver(this::handleBatchDragOver);
        batchFilesTable.setOnDragDropped(this::handleBatchDragDropped);

        logger.debug("Drag and drop registered for batch validation table");
    }

    /**
     * Handles drag over event for the batch validation table.
     * Accepts XML files, XSD files (as schema), and directories.
     *
     * @param event the drag event
     */
    private void handleBatchDragOver(javafx.scene.input.DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            // Accept if at least one XML, XSD file or directory is in the drag
            boolean hasValidContent = event.getDragboard().getFiles().stream()
                    .anyMatch(file -> {
                        if (file.isDirectory()) return true;
                        String name = file.getName().toLowerCase();
                        return name.endsWith(".xml") || name.endsWith(".xsd");
                    });

            if (hasValidContent) {
                event.acceptTransferModes(TransferMode.COPY);
                logger.debug("Batch table accepting XML/XSD file/folder drag");
            }
        }
        event.consume();
    }

    /**
     * Handles drag dropped event for the batch validation table.
     * Processes dropped XML files and recursively scans dropped directories.
     * XSD files are used as the batch schema (selects "Use same XSD" mode).
     *
     * @param event the drag event
     */
    private void handleBatchDragDropped(javafx.scene.input.DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            List<File> allXmlFiles = new ArrayList<>();
            List<File> xsdFiles = new ArrayList<>();

            for (File file : db.getFiles()) {
                if (file.isDirectory()) {
                    // Recursively collect all XML files from the directory
                    collectXmlFilesRecursively(file, allXmlFiles);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".xml")) {
                        allXmlFiles.add(file);
                    } else if (name.endsWith(".xsd")) {
                        xsdFiles.add(file);
                    }
                }
            }

            // Process XSD files first (set as batch schema)
            if (!xsdFiles.isEmpty()) {
                File firstXsdFile = xsdFiles.get(0);
                batchXsdFile = firstXsdFile;
                if (batchXsdFileName != null) {
                    batchXsdFileName.setText(firstXsdFile.getName());
                }
                // Enable "Use same XSD" mode
                if (sameXsdRadio != null) {
                    sameXsdRadio.setSelected(true);
                    if (batchXsdFileName != null) batchXsdFileName.setDisable(false);
                    if (selectBatchXsdBtn != null) selectBatchXsdBtn.setDisable(false);
                }
                success = true;
                logger.info("Set batch XSD schema via drag and drop: {}", firstXsdFile.getName());

                if (xsdFiles.size() > 1) {
                    logger.info("Multiple XSD files dropped ({}). Only the first file was used as schema: {}",
                            xsdFiles.size(), firstXsdFile.getName());
                }
            }

            // Process XML files
            if (!allXmlFiles.isEmpty()) {
                addDroppedFilesToBatch(allXmlFiles);
                success = true;
                logger.info("Dropped {} XML files onto batch validation table", allXmlFiles.size());
            } else if (xsdFiles.isEmpty()) {
                logger.debug("No XML or XSD files found in dropped items on batch table");
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Recursively collects all XML files from a directory and its subdirectories.
     *
     * @param directory the directory to scan
     * @param xmlFiles  the list to add found XML files to
     */
    private void collectXmlFilesRecursively(File directory, List<File> xmlFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectXmlFilesRecursively(file, xmlFiles);
                } else if (file.getName().toLowerCase().endsWith(".xml")) {
                    xmlFiles.add(file);
                }
            }
        }
    }

    /**
     * Adds dropped files to the batch list, filtering out duplicates.
     *
     * @param files the list of XML files to add
     */
    private void addDroppedFilesToBatch(List<File> files) {
        int addedCount = 0;
        int duplicateCount = 0;

        for (File file : files) {
            // Check for duplicates
            boolean alreadyExists = batchFiles.stream()
                    .anyMatch(bf -> bf.getXmlFile().getAbsolutePath().equals(file.getAbsolutePath()));

            if (!alreadyExists) {
                batchFiles.add(new BatchValidationFile(file));
                addedCount++;
            } else {
                duplicateCount++;
            }
        }

        updateBatchSummary();

        if (duplicateCount > 0) {
            logger.info("Added {} files to batch, skipped {} duplicates", addedCount, duplicateCount);
        } else {
            logger.info("Added {} files to batch", addedCount);
        }
    }

    /**
     * Sets up the table columns for the batch files table.
     */
    private void setupBatchTableColumns() {
        if (batchFileNameColumn != null) {
            batchFileNameColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getFileName()));
        }

        if (batchFilePathColumn != null) {
            batchFilePathColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getFilePath()));
        }

        if (batchStatusColumn != null) {
            batchStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
            batchStatusColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(ValidationStatus status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        setText(status.getDisplayText());
                        FontIcon icon = new FontIcon(status.getIconLiteral());
                        icon.setIconColor(Color.web(status.getColor()));
                        icon.setIconSize(14);
                        setGraphic(icon);
                        setStyle("-fx-text-fill: " + status.getColor() + ";");
                    }
                }
            });
        }

        if (batchErrorsColumn != null) {
            batchErrorsColumn.setCellValueFactory(new PropertyValueFactory<>("errorCount"));
            batchErrorsColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(Integer count, boolean empty) {
                    super.updateItem(count, empty);
                    if (empty || count == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(String.valueOf(count));
                        if (count > 0) {
                            setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #28a745;");
                        }
                    }
                }
            });
        }

        if (batchXsdColumn != null) {
            batchXsdColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getXsdFileName()));
        }

        if (batchDurationColumn != null) {
            batchDurationColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getDurationText()));
        }
    }

    /**
     * Opens file chooser to select XSD schema for batch validation.
     */
    @FXML
    public void selectBatchXsd() {
        File file = xsdFileChooser.showOpenDialog(null);
        if (file != null) {
            batchXsdFile = file;
            if (batchXsdFileName != null) {
                batchXsdFileName.setText(file.getName());
            }
            logger.debug("Selected batch XSD: {}", file.getAbsolutePath());
        }
    }

    /**
     * Opens file chooser to add multiple XML files to batch.
     */
    @FXML
    public void addFilesToBatch() {
        List<File> files = xmlFileChooser.showOpenMultipleDialog(null);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                // Avoid duplicates
                boolean alreadyExists = batchFiles.stream()
                        .anyMatch(bf -> bf.getXmlFile().getAbsolutePath().equals(file.getAbsolutePath()));
                if (!alreadyExists) {
                    batchFiles.add(new BatchValidationFile(file));
                }
            }
            updateBatchSummary();
            logger.info("Added {} files to batch", files.size());
        }
    }

    /**
     * Opens directory chooser to add all XML files from a folder (recursively).
     */
    @FXML
    public void addFolderToBatch() {
        File folder = directoryChooser.showDialog(null);
        if (folder != null && folder.isDirectory()) {
            try {
                List<File> xmlFiles = Files.walk(folder.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                        .map(Path::toFile)
                        .toList();

                int addedCount = 0;
                for (File file : xmlFiles) {
                    boolean alreadyExists = batchFiles.stream()
                            .anyMatch(bf -> bf.getXmlFile().getAbsolutePath().equals(file.getAbsolutePath()));
                    if (!alreadyExists) {
                        batchFiles.add(new BatchValidationFile(file));
                        addedCount++;
                    }
                }

                updateBatchSummary();
                logger.info("Added {} XML files from folder: {}", addedCount, folder.getAbsolutePath());

            } catch (IOException e) {
                logger.error("Error scanning folder: {}", e.getMessage());
                showAlert("Error", "Could not scan folder: " + e.getMessage());
            }
        }
    }

    /**
     * Removes selected files from the batch list.
     */
    @FXML
    public void removeBatchSelected() {
        List<BatchValidationFile> selected = new ArrayList<>(batchFilesTable.getSelectionModel().getSelectedItems());
        if (!selected.isEmpty()) {
            batchFiles.removeAll(selected);
            updateBatchSummary();
            logger.debug("Removed {} files from batch", selected.size());
        }
    }

    /**
     * Clears all files from the batch list.
     */
    @FXML
    public void clearBatch() {
        batchFiles.clear();
        batchErrorDetailsBox.getChildren().clear();
        updateBatchSummary();
        logger.debug("Batch cleared");
    }

    /**
     * Runs batch validation on all files in the background.
     */
    @FXML
    public void runBatchValidation() {
        if (batchFiles.isEmpty()) {
            showAlert("No Files", "Please add XML files to validate.");
            return;
        }

        // Check XSD configuration
        if (sameXsdRadio != null && sameXsdRadio.isSelected() && batchXsdFile == null) {
            showAlert("No XSD Selected", "Please select an XSD schema file for batch validation.");
            return;
        }

        // Reset all files to pending
        for (BatchValidationFile file : batchFiles) {
            file.reset();
        }

        // Setup UI for running state
        batchCancelled = false;
        if (batchProgressPane != null) {
            batchProgressPane.setVisible(true);
            batchProgressPane.setManaged(true);
        }
        if (runBatchBtn != null) runBatchBtn.setDisable(true);
        if (cancelBatchBtn != null) cancelBatchBtn.setDisable(false);
        if (batchProgressBar != null) batchProgressBar.setProgress(0);

        int total = batchFiles.size();

        Thread validationThread = new Thread(() -> {
            for (int i = 0; i < batchFiles.size() && !batchCancelled; i++) {
                BatchValidationFile batchFile = batchFiles.get(i);
                int current = i + 1;

                // Update UI: mark as running
                Platform.runLater(() -> {
                    batchFile.setStatus(ValidationStatus.RUNNING);
                    if (batchStatusLabel != null) {
                        batchStatusLabel.setText("Validating " + current + "/" + total + ": " + batchFile.getFileName());
                    }
                    if (batchProgressBar != null) {
                        batchProgressBar.setProgress((double) current / total);
                    }
                    batchFilesTable.refresh();
                });

                // Determine XSD to use
                File xsdToUse = null;
                if (sameXsdRadio != null && sameXsdRadio.isSelected()) {
                    xsdToUse = batchXsdFile;
                } else {
                    // Auto-detect XSD from XML file
                    xsdToUse = detectXsdFromXmlFile(batchFile.getXmlFile());
                }

                // Perform validation
                long startTime = System.currentTimeMillis();
                List<SAXParseException> errors = new ArrayList<>();
                ValidationStatus finalStatus;

                try {
                    if (xsdToUse != null) {
                        errors = xmlService.validateFile(batchFile.getXmlFile(), xsdToUse);
                        finalStatus = errors.isEmpty() ? ValidationStatus.PASSED : ValidationStatus.FAILED;
                        batchFile.setXsdFile(xsdToUse);
                    } else {
                        finalStatus = ValidationStatus.ERROR;
                        batchFile.setErrorMessage("Could not find or detect XSD schema");
                    }
                } catch (Exception e) {
                    finalStatus = ValidationStatus.ERROR;
                    batchFile.setErrorMessage(e.getMessage());
                    logger.error("Validation error for {}: {}", batchFile.getFileName(), e.getMessage());
                }

                long duration = System.currentTimeMillis() - startTime;

                // Final status update
                final ValidationStatus status = finalStatus;
                final List<SAXParseException> finalErrors = errors;
                Platform.runLater(() -> {
                    batchFile.setStatus(status);
                    batchFile.setErrors(finalErrors);
                    batchFile.setValidationTimeMs(duration);
                    batchFilesTable.refresh();
                    updateBatchSummary();
                });
            }

            // Validation complete
            Platform.runLater(() -> {
                if (batchStatusLabel != null) {
                    batchStatusLabel.setText(batchCancelled ? "Cancelled" : "Complete");
                }
                if (batchProgressPane != null) {
                    batchProgressPane.setVisible(false);
                    batchProgressPane.setManaged(false);
                }
                if (runBatchBtn != null) runBatchBtn.setDisable(false);
                if (cancelBatchBtn != null) cancelBatchBtn.setDisable(true);
                logger.info("Batch validation {} - processed {} files",
                        batchCancelled ? "cancelled" : "complete", total);
            });
        });

        validationThread.setDaemon(true);
        validationThread.setName("BatchValidation-Thread");
        validationThread.start();
    }

    /**
     * Cancels the running batch validation.
     */
    @FXML
    public void cancelBatchValidation() {
        batchCancelled = true;
        logger.debug("Batch validation cancellation requested");
    }

    /**
     * Filters the batch results based on the selected filter.
     */
    @FXML
    public void filterBatchResults() {
        if (batchFilterCombo == null || filteredBatchFiles == null) return;

        String filter = batchFilterCombo.getValue();
        filteredBatchFiles.setPredicate(file -> {
            if (filter == null || "All".equals(filter)) {
                return true;
            }
            return switch (filter) {
                case "Passed" -> file.getStatus() == ValidationStatus.PASSED;
                case "Failed" -> file.getStatus() == ValidationStatus.FAILED;
                case "Pending" -> file.getStatus() == ValidationStatus.PENDING;
                default -> true;
            };
        });
    }

    /**
     * Updates the batch summary label with current statistics.
     */
    private void updateBatchSummary() {
        if (batchSummaryLabel == null) return;

        long total = batchFiles.size();
        long passed = batchFiles.stream().filter(f -> f.getStatus() == ValidationStatus.PASSED).count();
        long failed = batchFiles.stream().filter(f -> f.getStatus() == ValidationStatus.FAILED).count();
        long errors = batchFiles.stream().filter(f -> f.getStatus() == ValidationStatus.ERROR).count();

        batchSummaryLabel.setText(String.format("Total: %d | Passed: %d | Failed: %d | Errors: %d",
                total, passed, failed, errors));
    }

    /**
     * Shows error details for the selected file in the error details pane.
     */
    private void showFileErrors(BatchValidationFile file) {
        if (batchErrorDetailsBox == null) return;

        batchErrorDetailsBox.getChildren().clear();

        if (file == null) {
            Label placeholder = new Label("Select a file to view error details");
            placeholder.setStyle("-fx-text-fill: #6c757d;");
            batchErrorDetailsBox.getChildren().add(placeholder);
            return;
        }

        // Show file info header
        Label fileHeader = new Label("File: " + file.getFileName());
        fileHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        batchErrorDetailsBox.getChildren().add(fileHeader);

        // Show status
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label statusLabel = new Label("Status: " + file.getStatus().getDisplayText());
        FontIcon statusIcon = new FontIcon(file.getStatus().getIconLiteral());
        statusIcon.setIconColor(Color.web(file.getStatus().getColor()));
        statusIcon.setIconSize(16);
        statusBox.getChildren().addAll(statusIcon, statusLabel);
        batchErrorDetailsBox.getChildren().add(statusBox);

        // Show error message if present
        if (file.getErrorMessage() != null && !file.getErrorMessage().isEmpty()) {
            Label errorMsg = new Label("Error: " + file.getErrorMessage());
            errorMsg.setStyle("-fx-text-fill: #dc3545;");
            errorMsg.setWrapText(true);
            batchErrorDetailsBox.getChildren().add(errorMsg);
        }

        // Show validation errors
        List<SAXParseException> errors = file.getErrors();
        if (errors != null && !errors.isEmpty()) {
            batchErrorDetailsBox.getChildren().add(new Separator());
            Label errorsHeader = new Label("Validation Errors (" + errors.size() + "):");
            errorsHeader.setStyle("-fx-font-weight: bold;");
            batchErrorDetailsBox.getChildren().add(errorsHeader);

            for (int i = 0; i < errors.size(); i++) {
                SAXParseException ex = errors.get(i);
                VBox errorBox = new VBox(3);
                errorBox.setStyle("-fx-padding: 5; -fx-background-color: #fff3cd; -fx-background-radius: 3;");

                Label errorLabel = new Label("#" + (i + 1) + ": " + ex.getLocalizedMessage());
                errorLabel.setWrapText(true);
                errorLabel.setStyle("-fx-font-size: 12px;");

                Label locationLabel = new Label("Line: " + ex.getLineNumber() + ", Column: " + ex.getColumnNumber());
                locationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

                errorBox.getChildren().addAll(errorLabel, locationLabel);
                batchErrorDetailsBox.getChildren().add(errorBox);
            }
        } else if (file.getStatus() == ValidationStatus.PASSED) {
            Label successLabel = new Label("No validation errors found.");
            successLabel.setStyle("-fx-text-fill: #28a745;");
            batchErrorDetailsBox.getChildren().add(successLabel);
        }
    }

    /**
     * Exports all batch results to Excel.
     */
    @FXML
    public void exportBatchAll() {
        if (batchFiles.isEmpty()) {
            showAlert("No Files", "No files to export.");
            return;
        }

        excelFileChooser.setInitialFileName("BatchValidationResults.xlsx");
        File exportFile = excelFileChooser.showSaveDialog(null);
        if (exportFile != null) {
            File result = xmlService.createBatchExcelReport(batchFiles, exportFile);
            if (result != null && result.exists()) {
                logger.info("Batch validation results exported to: {}", result.getAbsolutePath());
                openFile(result);
            }
        }
    }

    /**
     * Exports errors for the selected file to Excel.
     */
    @FXML
    public void exportBatchSelected() {
        BatchValidationFile selected = batchFilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a file to export its errors.");
            return;
        }

        if (selected.getErrors() == null || selected.getErrors().isEmpty()) {
            showAlert("No Errors", "The selected file has no validation errors to export.");
            return;
        }

        excelFileChooser.setInitialFileName(selected.getFileName().replace(".xml", "_errors.xlsx"));
        File exportFile = excelFileChooser.showSaveDialog(null);
        if (exportFile != null) {
            var result = xmlService.createExcelValidationReport(exportFile, selected.getErrors());
            if (result != null && result.exists()) {
                logger.info("Validation errors exported for {}", selected.getFileName());
                openFile(result);
            }
        }
    }

    /**
     * Attempts to detect the XSD schema from the XML file's schemaLocation attribute.
     */
    private File detectXsdFromXmlFile(File xmlFile) {
        try {
            // Temporarily set the XML file in the service
            File originalXml = xmlService.getCurrentXmlFile();
            xmlService.setCurrentXmlFile(xmlFile);

            // Try to get schema location and load it
            var schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
            if (schemaName.isPresent()) {
                if (xmlService.loadSchemaFromXMLFile()) {
                    File xsd = xmlService.getCurrentXsdFile();
                    // Restore original
                    xmlService.setCurrentXmlFile(originalXml);
                    return xsd;
                }
            }

            // Restore original
            xmlService.setCurrentXmlFile(originalXml);
            return null;

        } catch (Exception e) {
            logger.warn("Could not detect XSD for file {}: {}", xmlFile.getName(), e.getMessage());
            return null;
        }
    }
}