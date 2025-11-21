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
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller class for handling FOP (Formatting Objects Processor) related actions.
 */
public class FopController {
    private static final Logger logger = LogManager.getLogger(FopController.class);
    private final FOPService fopService = new FOPService();
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

    // UI Components - Favorites
    @FXML
    private Button addToFavoritesBtn;
    @FXML
    private ToggleButton toggleFavoritesButton;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private ComboBox<String> favoritesCategoryCombo;
    @FXML
    private ListView<org.fxt.freexmltoolkit.domain.FileFavorite> favoritesListView;

    // Favorites Service
    private final org.fxt.freexmltoolkit.service.FavoritesService favoritesService =
        org.fxt.freexmltoolkit.service.FavoritesService.getInstance();

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
    }

    private void initializeFavorites() {
        if (favoritesCategoryCombo != null) {
            favoritesCategoryCombo.getItems().addAll("All Categories", "FOP", "XML Files", "XSL Files");
            favoritesCategoryCombo.setValue("All Categories");
            favoritesCategoryCombo.setOnAction(e -> loadFavoritesForCategory(favoritesCategoryCombo.getValue()));
        }

        if (favoritesListView != null) {
            favoritesListView.setCellFactory(lv -> new ListCell<org.fxt.freexmltoolkit.domain.FileFavorite>() {
                @Override
                protected void updateItem(org.fxt.freexmltoolkit.domain.FileFavorite item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getName());
                        org.kordamp.ikonli.javafx.FontIcon icon = getIconForFile(item.getFilePath());
                        setGraphic(icon);
                    }
                }
            });

            favoritesListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    org.fxt.freexmltoolkit.domain.FileFavorite selected = favoritesListView.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        loadFavorite(selected);
                    }
                }
            });
        }

        if (addToFavoritesBtn != null) {
            addToFavoritesBtn.setOnAction(e -> addCurrentToFavorites());
        }

        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }

        loadFavoritesForCategory("All Categories");
    }

    private org.kordamp.ikonli.javafx.FontIcon getIconForFile(String filePath) {
        org.kordamp.ikonli.javafx.FontIcon icon;
        if (filePath.endsWith(".xml")) {
            icon = new org.kordamp.ikonli.javafx.FontIcon("bi-file-earmark-code");
            icon.setIconColor(javafx.scene.paint.Color.web("#007bff"));
        } else if (filePath.endsWith(".xsl")) {
            icon = new org.kordamp.ikonli.javafx.FontIcon("bi-file-earmark-text");
            icon.setIconColor(javafx.scene.paint.Color.web("#e27429"));
        } else if (filePath.endsWith(".pdf")) {
            icon = new org.kordamp.ikonli.javafx.FontIcon("bi-file-earmark-pdf");
            icon.setIconColor(javafx.scene.paint.Color.web("#dc3545"));
        } else {
            icon = new org.kordamp.ikonli.javafx.FontIcon("bi-file");
            icon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        }
        icon.setIconSize(14);
        return icon;
    }

    private void loadFavoritesForCategory(String category) {
        if (favoritesListView == null) return;

        java.util.List<org.fxt.freexmltoolkit.domain.FileFavorite> allFavorites = favoritesService.getAllFavorites();
        java.util.List<org.fxt.freexmltoolkit.domain.FileFavorite> filtered;

        if ("All Categories".equals(category)) {
            filtered = allFavorites;
        } else {
            filtered = allFavorites.stream()
                    .filter(f -> category.equals(f.getFolderName()))
                    .collect(java.util.stream.Collectors.toList());
        }

        favoritesListView.setItems(javafx.collections.FXCollections.observableArrayList(filtered));
    }

    private void addCurrentToFavorites() {
        if (xmlFile == null && xslFile == null) {
            showAlert("No Files Loaded", "Please load XML and/or XSL files before adding to favorites.");
            return;
        }

        Dialog<org.fxt.freexmltoolkit.domain.FileFavorite> dialog = new Dialog<>();
        dialog.setTitle("Add to Favorites");
        dialog.setHeaderText("Add Current Files to Favorites");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        if (xmlFile != null) {
            nameField.setText(xmlFile.getName());
        } else if (xslFile != null) {
            nameField.setText(xslFile.getName());
        }

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("FOP", "XML Files", "XSL Files");
        categoryCombo.setValue("FOP");

        TextField descField = new TextField();

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                if (xmlFile != null) {
                    org.fxt.freexmltoolkit.domain.FileFavorite fav = new org.fxt.freexmltoolkit.domain.FileFavorite(
                            nameField.getText() + " (XML)",
                            xmlFile.getAbsolutePath(),
                            categoryCombo.getValue()
                    );
                    fav.setDescription(descField.getText());
                    favoritesService.addFavorite(fav);
                }
                if (xslFile != null) {
                    org.fxt.freexmltoolkit.domain.FileFavorite fav = new org.fxt.freexmltoolkit.domain.FileFavorite(
                            nameField.getText() + " (XSL)",
                            xslFile.getAbsolutePath(),
                            categoryCombo.getValue()
                    );
                    fav.setDescription(descField.getText());
                    favoritesService.addFavorite(fav);
                }
            }
            return null;
        });

        dialog.showAndWait();
        loadFavoritesForCategory(favoritesCategoryCombo.getValue());
    }

    private void toggleFavoritesPanel() {
        if (favoritesPanel != null) {
            boolean isVisible = !favoritesPanel.isVisible();
            favoritesPanel.setVisible(isVisible);
            favoritesPanel.setManaged(isVisible);
        }
    }

    private void loadFavorite(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        File file = new File(favorite.getFilePath());
        if (!file.exists()) {
            showAlert("File Not Found", "The file no longer exists: " + favorite.getFilePath());
            return;
        }

        if (file.getName().endsWith(".xml")) {
            xmlFile = file;
            xmlFileName.setText(file.getName());
        } else if (file.getName().endsWith(".xsl")) {
            xslFile = file;
            xslFileName.setText(file.getName());
        }

        // Update access count
        favorite.setAccessCount(favorite.getAccessCount() + 1);
        favorite.setLastAccessed(java.time.LocalDateTime.now());
        favoritesService.updateFavorite(favorite);

        logger.info("Loaded favorite: {}", favorite.getName());
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
     */
    @FXML
    private void buttonConversion() {
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
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("FOP - Help");
        helpDialog.setHeaderText("How to use the PDF Generator (Apache FOP)");
        helpDialog.setContentText("""
                Use this tool to work with your documents.\n\n                Press F1 to show this help.
                """);
        helpDialog.showAndWait();
    }
}
