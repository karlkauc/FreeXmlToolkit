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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;
import org.xml.sax.SAXParseException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Controller class for handling XSD validation operations.
 */
public class XsdValidationController {

    private static final Logger logger = LogManager.getLogger(XsdValidationController.class);
    private final XmlService xmlService = XmlServiceImpl.getInstance();
    private final FileChooser xmlFileChooser = new FileChooser();
    private final FileChooser xsdFileChooser = new FileChooser();
    private final FileChooser excelFileChooser = new FileChooser();
    private List<SAXParseException> validationErrors;
    private MainController parentController;

    // KORREKTUR: Enum für einen sauberen Status-Wechsel
    private enum ValidationStatus {SUCCESS, ERROR, READY}

    @FXML
    private VBox rootVBox;
    @FXML
    private Button xmlLoadButton, xsdLoadButton, excelExport, clearResults;
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
     */
    @FXML
    private void toggleAutoDetection() {
        boolean disable = !xsdLoadButton.isDisable();
        xsdLoadButton.setDisable(disable);
        xsdFileName.setDisable(disable);
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

        // Setze den initialen Status der UI
        resetUI();
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
     *
     * @param event the drag event
     */
    private void handlePageDragOver(javafx.scene.input.DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            // Check if at least one XML file is in the drag
            boolean hasXmlFile = event.getDragboard().getFiles().stream()
                    .anyMatch(file -> file.getName().toLowerCase().endsWith(".xml"));

            if (hasXmlFile) {
                event.acceptTransferModes(TransferMode.COPY);
                logger.debug("Validation page accepting XML file drag");
            }
        }
        event.consume(); // Prevent event from bubbling to global handler
    }

    /**
     * Handles drag dropped event for the entire validation page.
     * Loads the first XML file into the validation view (not the XML editor).
     *
     * @param event the drag event
     */
    private void handlePageDragDropped(javafx.scene.input.DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            logger.info("Files dropped on validation page: processing {} files", db.getFiles().size());

            // Filter for XML files
            var xmlFiles = db.getFiles().stream()
                    .filter(file -> file.getName().toLowerCase().endsWith(".xml"))
                    .toList();

            if (!xmlFiles.isEmpty()) {
                // Load the first XML file for validation
                File firstXmlFile = xmlFiles.get(0);
                processXmlFile(firstXmlFile);
                success = true;

                logger.info("Loaded XML file for validation via drag and drop: {}", firstXmlFile.getName());

                if (xmlFiles.size() > 1) {
                    logger.info("Multiple XML files dropped ({}). Only the first file was loaded: {}",
                            xmlFiles.size(), firstXmlFile.getName());
                }
            } else {
                logger.debug("No XML files found in dropped files on validation page");
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
     * Processes the currently selected XML file.
     */
    @FXML
    private void processXmlFile() {
        processXmlFile(xmlService.getCurrentXmlFile());
    }

    /**
     * Processes the specified XML file.
     *
     * @param file the XML file
     */
    private void processXmlFile(File file) {
        if (file == null) {
            updateStatus(ValidationStatus.ERROR, "No XML file selected.");
            return;
        }

        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0.1);
        resetUI();

        Platform.runLater(() -> {
            xmlService.setCurrentXmlFile(file);
            xmlService.prettyFormatCurrentFile();
            xmlFileName.setText(file.getName());
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
                updateStatus(ValidationStatus.ERROR, "Schema not found. Please select a schema manually or ensure autodetect can find it.");
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
        updateStatus(ValidationStatus.READY, "Ready for validation.");
    }

    /**
     * Displays the validation results in the UI.
     */
    private void displayValidationResults() {
        if (validationErrors != null && !validationErrors.isEmpty()) {
            logger.warn(validationErrors.toString());
            updateStatus(ValidationStatus.ERROR, "Validation failed. " + validationErrors.size() + " error(s) found.");
            for (int i = 0; i < validationErrors.size(); i++) {
                SAXParseException ex = validationErrors.get(i);
                TextFlow textFlow = createTextFlow(i, ex);
                errorListBox.getChildren().addAll(textFlow, createGoToErrorButton(), new Separator());
            }
        } else {
            // KORREKTUR: Deutliche Erfolgsmeldung anzeigen
            logger.debug("No errors in validation");
            updateStatus(ValidationStatus.SUCCESS, "Validation successful. No errors found.");

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
     * KORREKTUR: Neue zentrale Methode zur Steuerung der Statusanzeige.
     *
     * @param status  Der Validierungsstatus (SUCCESS, ERROR, READY).
     * @param message Die anzuzeigende Nachricht.
     */
    private void updateStatus(ValidationStatus status, String message) {
        if (statusPane == null || statusLabel == null || statusImage == null) return;

        statusLabel.setText(message);
        String style = "-fx-background-radius: 5; -fx-padding: 10;";
        String imagePath = null;

        switch (status) {
            case SUCCESS -> {
                style += "-fx-background-color: #e0f8e0;"; // Hellgrün
                imagePath = "/img/icons8-ok-48.png";
            }
            case ERROR -> {
                style += "-fx-background-color: #f8e0e0;"; // Hellrot
                imagePath = "/img/icons8-stornieren-48.png";
            }
            case READY -> {
                style += "-fx-background-color: -fx-background-color-subtle;"; // Standard-Hintergrund
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
    private void clearResultAction() {
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
    private void excelExport() {
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
    private void showHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("XSD Validation - Help");
        helpDialog.setHeaderText("How to use the XSD Validation Tool");

        String helpText = """
                1. Load XML File:
                   - Click the XML file button in the toolbar or use the file selector
                   - Drag and drop is also supported

                2. Schema Detection:
                   - Enable "Autodetect Schema" to automatically find the XSD from the XML file
                   - Or manually select an XSD file using the XSD file button

                3. Start Validation:
                   - Click the validation button (checkmark icon) to validate the XML
                   - Results will be displayed in the "Validation Results" section

                4. Review Results:
                   - Green status = Validation successful
                   - Red status = Validation failed (errors will be listed)
                   - Click "Go to error" to jump to the XML editor tab

                5. Export Results:
                   - Click the Excel export button to save validation errors to an Excel file

                6. Clear Results:
                   - Click the eraser button to reset the validation interface

                Keyboard Shortcuts:
                - F1: Show this help dialog
                """;

        helpDialog.setContentText(helpText);
        helpDialog.getDialogPane().setMinWidth(600);

        // Add icon to help dialog
        FontIcon helpIcon = new FontIcon("bi-question-circle");
        helpIcon.setIconSize(48);
        helpIcon.setIconColor(Color.web("#17a2b8"));
        helpDialog.setGraphic(helpIcon);

        helpDialog.showAndWait();
        logger.debug("Help dialog shown");
    }

    /**
     * Test method for processing a sample XML file.
     */
}