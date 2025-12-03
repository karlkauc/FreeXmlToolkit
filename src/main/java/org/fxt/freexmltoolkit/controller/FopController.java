/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller class for handling FOP (Formatting Objects Processor) related actions.
 */
public class FopController implements FavoritesParentController {
    private static final Logger logger = LogManager.getLogger(FopController.class);
    private final FOPService fopService = new FOPService();
    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final FileChooser fileChooser = new FileChooser();
    private String lastOpenDir = ".";
    private File xmlFile, xslFile, pdfFile;
    private MainController parentController;

    @FXML
    private TextField xmlFileName, xslFileName, pdfFileName, producer, author, creationDate, title, keywords, subject;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private ScrollPane pdfScrollPane;
    @FXML
    private VBox pdfViewContainer;

    // UI Components - Favorites (unified FavoritesPanel)
    @FXML
    private Button addToFavoritesBtn;
    @FXML
    private Button toggleFavoritesButton;
    @FXML
    private SplitPane rightSplitPane;
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
    @FXML
    private Button openXmlBtn, openXslBtn, pdfOutBtn, generateBtn, helpBtn;

    /**
     * Sets the parent controller.
     *
     * @param parentController the parent controller
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Initializes the controller. Sets default values and configurations.
     */
    @FXML
    private void initialize() {
        progressIndicator.setVisible(false);
        if ("true".equals(System.getenv("debug"))) {
            xmlFile = new File("src/test/resources/projectteam.xml");
            xslFile = new File("src/test/resources/projectteam2fo.xsl");
            pdfFile = new File("output/ResultXML2PDF.pdf");
            xmlFileName.setText(xmlFile.getName());
            xslFileName.setText(xslFile.getName());
            pdfFileName.setText(pdfFile.getName());
        }
        creationDate.setText(new Date().toString());
        author.setText(System.getProperty("user.name"));

        xmlFileName.setOnDragOver(this::handleDragOver);
        xmlFileName.setOnDragDropped(event -> handleDragDropped(event, file -> {
            xmlFile = file;
            xmlFileName.setText(file.getName());
        }));

        xslFileName.setOnDragOver(this::handleDragOver);
        xslFileName.setOnDragDropped(event -> handleDragDropped(event, file -> {
            xslFile = file;
            xslFileName.setText(file.getName());
        }));

        initializeFavorites();
        initializeEmptyState();
        applySmallIconsSetting();
        setupKeyboardShortcuts();
        setupGlobalDragAndDrop();
    }

    /**
     * Set up global drag and drop functionality for the FOP controller.
     * Accepts XML files for input and XSL/XSLT files for stylesheet.
     * This supplements the field-specific drag and drop already in place.
     */
    private void setupGlobalDragAndDrop() {
        // Set up drag and drop on both the empty state pane and content pane
        if (emptyStatePane != null) {
            DragDropService.setupDragDrop(emptyStatePane, DragDropService.XML_AND_XSLT, this::handleGlobalDroppedFiles);
        }
        if (contentPane != null) {
            DragDropService.setupDragDrop(contentPane, DragDropService.XML_AND_XSLT, this::handleGlobalDroppedFiles);
        }
        logger.debug("Global drag and drop initialized for FOP controller");
    }

    /**
     * Handle files dropped on the FOP controller globally.
     * Routes XML files to XML input and XSL/XSLT files to stylesheet input.
     *
     * @param files the dropped files
     */
    private void handleGlobalDroppedFiles(java.util.List<File> files) {
        logger.info("Files dropped on FOP controller: {} file(s)", files.size());

        for (File file : files) {
            DragDropService.FileType fileType = DragDropService.getFileType(file);
            if (fileType == DragDropService.FileType.XSLT) {
                // Set as XSL file
                xslFile = file;
                if (xslFileName != null) {
                    xslFileName.setText(file.getName());
                }
                logger.debug("Dropped XSL file set: {}", file.getName());
            } else if (fileType == DragDropService.FileType.XML) {
                // Set as XML file
                xmlFile = file;
                if (xmlFileName != null) {
                    xmlFileName.setText(file.getName());
                }
                logger.debug("Dropped XML file set: {}", file.getName());
            }
        }

        // Show content pane after loading
        showContent();
    }

    /**
     * Sets up keyboard shortcuts for FOP Controller actions.
     */
    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (pdfViewContainer == null || pdfViewContainer.getScene() == null) {
                // Scene not ready yet, try again later
                Platform.runLater(this::setupKeyboardShortcuts);
                return;
            }

            Scene scene = pdfViewContainer.getScene();

            // Ctrl+1 - Open XML File
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN),
                    this::openXmlFile
            );

            // Ctrl+2 - Open XSL File
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN),
                    this::openXslFile
            );

            // Ctrl+3 - Select PDF Output File
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN),
                    this::openPdfFile
            );

            logger.debug("FOP Controller keyboard shortcuts registered");
        });
    }

    private void initializeFavorites() {
        // Initialize the unified FavoritesPanel controller
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
            logger.debug("FavoritesPanelController initialized");
        }

        // Initially hide favorites panel
        if (favoritesPanel != null && rightSplitPane != null) {
            rightSplitPane.getItems().remove(favoritesPanel);
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
     * Adds the current XML or XSL file to favorites.
     */
    private void addCurrentToFavorites() {
        File currentFile = getCurrentFile();

        if (currentFile == null) {
            showAlert("No Files Loaded", "Please load an XML or XSL file before adding to favorites.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("FOP");
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
        if (favoritesPanel == null || rightSplitPane == null) {
            return;
        }

        boolean isCurrentlyShown = rightSplitPane.getItems().contains(favoritesPanel);

        if (!isCurrentlyShown) {
            // Show the panel
            favoritesPanel.setVisible(true);
            favoritesPanel.setManaged(true);
            rightSplitPane.getItems().add(favoritesPanel);
            rightSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            rightSplitPane.getItems().remove(favoritesPanel);
            favoritesPanel.setVisible(false);
            favoritesPanel.setManaged(false);
        }

        logger.debug("Favorites panel toggled: {}", !isCurrentlyShown ? "shown" : "hidden");
    }

    // ======================================================================
    // FavoritesParentController Interface Implementation
    // ======================================================================

    /**
     * Load a file from favorites into the FOP tab.
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
            xmlFile = file;
            xmlFileName.setText(file.getName());
            xmlService.setCurrentXmlFile(file);

            // Auto-load linked XSLT stylesheet if user hasn't manually loaded one
            if (xslFile == null) {
                tryLoadLinkedStylesheet();
            }

            showContent();
            logger.info("Loaded XML file from favorites: {}", file.getAbsolutePath());
        } else if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
            xslFile = file;
            xslFileName.setText(file.getName());
            showContent();
            logger.info("Loaded XSL file from favorites: {}", file.getAbsolutePath());
        } else {
            showAlert("Unsupported File", "Only XML and XSL files are supported for FOP.");
        }
    }

    /**
     * Get the currently loaded file (XML has priority over XSL).
     * Implementation of FavoritesParentController interface.
     *
     * @return the current file, or null if no file is open
     */
    @Override
    public File getCurrentFile() {
        if (xmlFile != null) {
            return xmlFile;
        }
        return xslFile;
    }

    /**
     * Initializes the empty state UI and wires up button actions.
     */
    private void initializeEmptyState() {
        if (emptyStateOpenXmlButton != null) {
            emptyStateOpenXmlButton.setOnAction(e -> openXmlFile());
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Handles the drag over event for file loading.
     *
     * @param event the drag event
     */
    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
        else event.consume();
    }

    /**
     * Handles the drag dropped event for file loading.
     *
     * @param event        the drag event
     * @param fileConsumer the consumer to handle the dropped file
     */
    private void handleDragDropped(DragEvent event, java.util.function.Consumer<File> fileConsumer) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            logger.debug("Dropped Files: {}", db.getFiles());
            fileConsumer.accept(db.getFiles().getFirst());
            event.setDropCompleted(true);
        } else event.setDropCompleted(false);
        event.consume();
    }

    /**
     * Opens a file chooser dialog to select an XML file.
     */
    @FXML
    private void openXmlFile() {
        openFile("XML files (*.xml)", "*.xml", file -> {
            xmlFile = file;
            xmlFileName.setText(file.getName());

            // Set the XML file in the service for stylesheet detection
            xmlService.setCurrentXmlFile(file);

            // Auto-load linked XSLT stylesheet if user hasn't manually loaded one
            if (xslFile == null) {
                tryLoadLinkedStylesheet();
            }

            showContent();  // Show content when XML file is loaded
        });
    }

    /**
     * Opens a file chooser dialog to select an XSL file.
     */
    @FXML
    private void openXslFile() {
        openFile("XSL files (*.xsl)", "*.xsl", file -> {
            xslFile = file;
            xslFileName.setText(file.getName());
            showContent();  // Show content when XSL file is loaded
        });
    }

    /**
     * Opens a file chooser dialog to select a PDF file.
     */
    @FXML
    private void openPdfFile() {
        pdfFile = saveFile("PDF files (*.pdf)", "*.pdf");
        if (pdfFile != null) {
            pdfFileName.setText(pdfFile.getName());
        }
    }

    /**
     * Opens a file chooser dialog with the specified description and extension filter.
     *
     * @param description  the description of the file type
     * @param extension    the file extension filter
     * @param fileConsumer the consumer to handle the selected file
     */
    private void openFile(String description, String extension, java.util.function.Consumer<File> fileConsumer) {
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(description, extension));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null && selectedFile.exists()) {
            lastOpenDir = selectedFile.getParent();
            fileConsumer.accept(selectedFile);
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
        } else {
            logger.debug("No file selected");
        }
    }

    /**
     * Opens a file chooser dialog to save a file with the specified description and extension filter.
     *
     * @param description the description of the file type
     * @param extension   the file extension filter
     * @return the selected file
     */
    private File saveFile(String description, String extension) {
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(description, extension));
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            lastOpenDir = selectedFile.getParent();
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            return selectedFile;
        } else {
            logger.debug("No file selected");
        }
        return null;
    }

    /**
     * Starts the conversion process from XML and XSL to PDF.
     * Also callable from MainController for F5 shortcut
     */
    @FXML
    public void buttonConversion() {
        logger.debug("Start Conversion!");

        // =====================================================================
        // Validierungs-Block
        // =====================================================================
        StringBuilder validationErrors = new StringBuilder();

        if (xmlFile == null) {
            validationErrors.append("- No XML source file has been selected.\n");
        } else if (!xmlFile.exists()) {
            validationErrors.append("- The selected XML file does not exist: ").append(xmlFile.getAbsolutePath()).append("\n");
        }

        if (xslFile == null) {
            validationErrors.append("- No XSL-FO stylesheet has been selected.\n");
        } else if (!xslFile.exists()) {
            validationErrors.append("- The selected XSL-FO stylesheet does not exist: ").append(xslFile.getAbsolutePath()).append("\n");
        }

        if (pdfFile == null) {
            validationErrors.append("- No output path for the PDF file has been defined.\n");
        }

        // Wenn es Validierungsfehler gab, zeige einen Alert und brich ab.
        if (!validationErrors.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Please correct the following issues before creating the PDF:");
            alert.setContentText(validationErrors.toString());
            alert.showAndWait();
            return; // Wichtig: Die Methode hier beenden!
        }
        // =====================================================================
        // Ende des Validierungs-Blocks
        // =====================================================================

        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0);

        PDFSettings pdfSettings = new PDFSettings(
                new HashMap<>(Map.of("versionParam", "3")),
                producer.getText(), author.getText(), "created with FreeXMLToolkit",
                creationDate.getText(), title.getText(), keywords.getText()
        );

        // Die PDF-Erstellung und Anzeige in einen Hintergrund-Thread verlagern
        new Thread(() -> {
            try {
                File createdPdf = fopService.createPdfFile(xmlFile, xslFile, pdfFile, pdfSettings);
                Platform.runLater(() -> progressIndicator.setProgress(0.5));

                if (createdPdf != null && createdPdf.exists()) {
                    logger.debug("Written {} bytes in File {}", createdPdf.length(), createdPdf.getAbsoluteFile());
                    Platform.runLater(() -> {
                        renderPdf(createdPdf);
                        progressIndicator.setProgress(1.0);
                        progressIndicator.setVisible(false);
                    });
                } else {
                    logger.warn("PDF File does not exist after creation attempt.");
                    Platform.runLater(() -> progressIndicator.setVisible(false));
                }
            } catch (Exception e) {
                logger.error("PDF conversion failed.", e);
                // Zeige einen Fehler-Alert an
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "PDF creation failed: " + e.getMessage());
                    alert.showAndWait();
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    /**
     * Rendert eine gegebene PDF-Datei Seite für Seite in den pdfViewContainer.
     * Die Arbeit wird auf einem Hintergrund-Thread ausgeführt, um die UI nicht zu blockieren.
     *
     * @param pdfFile Die anzuzeigende PDF-Datei.
     */
    private void renderPdf(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            pdfViewContainer.getChildren().clear();
            pdfViewContainer.getChildren().add(new Label("Please create a PDF first."));
            return;
        }

        // UI für den Ladevorgang vorbereiten
        pdfViewContainer.getChildren().clear();
        ProgressIndicator viewerProgress = new ProgressIndicator();
        pdfViewContainer.getChildren().add(viewerProgress);

        // PDF-Rendering ist langsam, daher in einem neuen Thread ausführen
        new Thread(() -> {
            try (PDDocument document = Loader.loadPDF((pdfFile))) {
                PDFRenderer renderer = new PDFRenderer(document);

                // UI-Updates müssen auf dem JavaFX Application Thread ausgeführt werden
                Platform.runLater(pdfViewContainer.getChildren()::clear);

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    // Seite als Bild rendern
                    BufferedImage bufferedImage = renderer.renderImageWithDPI(i, 150); // 150 DPI ist ein guter Kompromiss
                    Image image = SwingFXUtils.toFXImage(bufferedImage, null);

                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    // Bild an die Breite des Scroll-Bereichs anpassen
                    imageView.fitWidthProperty().bind(pdfScrollPane.widthProperty().subtract(25));

                    // Bild zur VBox hinzufügen (wieder auf dem UI-Thread)
                    final int pageNum = i + 1;
                    Platform.runLater(() -> {
                        Label pageLabel = new Label("Seite " + pageNum);
                        pageLabel.setStyle("-fx-text-fill: white;"); // Bessere Sichtbarkeit auf dunklem Hintergrund
                        pdfViewContainer.getChildren().addAll(pageLabel, imageView);
                    });
                }
            } catch (IOException e) {
                logger.error("Fehler beim Laden oder Rendern des PDFs", e);
                Platform.runLater(() -> {
                    pdfViewContainer.getChildren().clear();
                    Label errorLabel = new Label("Fehler beim Anzeigen des PDFs: " + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: red;");
                    pdfViewContainer.getChildren().add(errorLabel);
                });
            }
        }).start();
    }

    /**
     * Attempts to load a linked XSLT stylesheet from the current XML file's xml-stylesheet processing instruction.
     * Only loads if user hasn't already manually selected a stylesheet.
     * Shows a warning if stylesheet is referenced but cannot be loaded.
     */
    private void tryLoadLinkedStylesheet() {
        try {
            var linkedStylesheet = xmlService.getLinkedStylesheetFromCurrentXMLFile();

            if (linkedStylesheet.isPresent()) {
                String stylesheetPath = linkedStylesheet.get();
                logger.debug("Found linked stylesheet: {}", stylesheetPath);

                File stylesheetFile = new File(stylesheetPath);

                // Check if file exists and is valid
                if (stylesheetFile.exists()) {
                    // Basic validation - check if it's an XSL file
                    String fileName = stylesheetFile.getName().toLowerCase();
                    if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
                        xslFile = stylesheetFile;
                        xslFileName.setText(stylesheetFile.getName());
                        logger.info("Auto-loaded linked XSLT stylesheet: {}", stylesheetPath);
                    } else {
                        logger.warn("Linked file is not an XSLT stylesheet (doesn't end with .xsl or .xslt): {}", stylesheetPath);
                        showStylesheetWarning("Invalid Stylesheet", "The linked file is not an XSLT stylesheet: " + stylesheetPath);
                    }
                } else {
                    logger.warn("Linked stylesheet file not found: {}", stylesheetPath);
                    showStylesheetWarning("Stylesheet Not Found", "The linked XSLT stylesheet could not be found:\n" + stylesheetPath);
                }
            } else {
                logger.debug("No linked stylesheet found in XML file");
            }
        } catch (Exception e) {
            logger.error("Error while trying to load linked stylesheet: {}", e.getMessage(), e);
            showStylesheetWarning("Error Loading Stylesheet", "An error occurred while trying to load the linked stylesheet:\n" + e.getMessage());
        }
    }

    /**
     * Shows a warning dialog about stylesheet loading issues.
     */
    private void showStylesheetWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText("Linked XSLT Stylesheet Issue");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-file-earmark-richtext", "PDF Generation", "Generate professional PDF documents from XML data"},
                new String[]{"bi-file-earmark-code", "XSL-FO Support", "Use XSL-FO stylesheets for precise layout control"},
                new String[]{"bi-eye", "Live Preview", "Preview generated PDFs directly in the application"},
                new String[]{"bi-gear", "Customization", "Configure paper size, margins, and formatting options"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"F5", "Generate PDF document"},
                new String[]{"Ctrl+D", "Add current file to favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle favorites panel"},
                new String[]{"F1", "Show this help dialog"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "FOP - Help",
                "PDF Generator",
                "Generate PDF documents from XML using Apache FOP",
                "bi-file-earmark-richtext",
                DialogHelper.HeaderTheme.DANGER,
                features,
                shortcuts
        );

        helpDialog.showAndWait();
    }

    /**
     * Apply small icons setting to toolbar buttons.
     * Follows the pattern from XsdController for consistent UI behavior.
     */
    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to FOP toolbar: {}", useSmallIcons);

        // Determine display mode and icon size
        javafx.scene.control.ContentDisplay displayMode = useSmallIcons
                ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                : javafx.scene.control.ContentDisplay.TOP;

        // Icon sizes: small = 14px, normal = 20px
        int iconSize = useSmallIcons ? 14 : 20;

        // Button style: compact padding for small icons
        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        // Apply to all FOP toolbar buttons
        applyButtonSettings(addToFavoritesBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toggleFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(emptyStateOpenXmlButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(emptyStateFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(openXmlBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(openXslBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(pdfOutBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(generateBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(helpBtn, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to FOP toolbar (size: {}px)", iconSize);
    }

    /**
     * Helper method to apply display mode, icon size, and style to a button.
     */
    private void applyButtonSettings(javafx.scene.control.ButtonBase button,
                                     javafx.scene.control.ContentDisplay displayMode,
                                     int iconSize,
                                     String style) {
        if (button == null) return;

        // Set content display mode
        button.setContentDisplay(displayMode);

        // Apply compact style
        button.setStyle(style);

        // Update icon size if the button has a FontIcon graphic
        if (button.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fontIcon) {
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
}
