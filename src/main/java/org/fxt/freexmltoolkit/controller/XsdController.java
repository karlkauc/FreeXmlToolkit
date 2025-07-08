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
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.controls.XsdDiagramView;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XsdController {
    XmlService xmlService = XmlServiceImpl.getInstance();

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Pane xsdPane, xmlPane;

    @FXML
    Button newFile, openFile, saveFile, prettyPrint, validateSchema, openDocFolder;

    DirectoryChooser documentationOutputDirectory;
    File selectedDocumentationOutputDirectory;

    @FXML
    TextField documentationOutputDirPath, xsdFilePath;

    @FXML
    Label schemaValidText, statusText;

    @FXML
    StackPane stackPane, xsdStackPane;

    @FXML
    CheckBox openFileAfterCreation, useMarkdownRenderer, createExampleData;

    @FXML
    ProgressIndicator progressDocumentation = new ProgressIndicator(0);

    @FXML
    ChoiceBox<String> grafikFormat;

    @FXML
    TabPane tabPane;
    @FXML
    Tab documentation;

    // FXML-Felder für den "Generate Example Data" Tab
    @FXML
    private TextField xsdForSampleDataPath;
    @FXML
    private TextField outputXmlPath;
    @FXML
    private CheckBox mandatoryOnlyCheckBox;
    @FXML
    private Spinner<Integer> maxOccurrencesSpinner;
    @FXML
    private TextArea sampleDataTextArea;
    @FXML
    private ProgressIndicator progressSampleData;

    private File selectedOutputXmlFile;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XsdController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }


    @FXML
    private void initialize() {
        setupCodeArea();
        setupDragAndDrop();
        reloadXmlText();
        setupXsdDiagram();

        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1);
        maxOccurrencesSpinner.setValueFactory(valueFactory);
    }

    private void setupXsdDiagram() {
        xsdStackPane.getChildren().clear();
        File currentXsdFile = this.xmlService.getCurrentXsdFile();

        if (currentXsdFile == null || !currentXsdFile.exists()) {
            Label infoLabel = new Label("Bitte eine XSD-Datei laden.");
            xsdStackPane.getChildren().add(infoLabel);
            StackPane.setAlignment(infoLabel, Pos.CENTER_LEFT);
            xsdStackPane.setAlignment(Pos.CENTER_LEFT);
            return;
        }

        logger.debug("Lade XSD-Diagramm für: {}", currentXsdFile.getAbsolutePath());

        try {
            // 1. Service instanziieren und Pfad setzen
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(currentXsdFile.getPath());

            // 2. Den gesamten rekursiven Baum vom Service holen
            XsdNodeInfo rootNode = docService.getRootNodeInfo();

            if (rootNode != null) {
                // 3. Die neue View mit dem Baum-Objekt instanziieren
                XsdDiagramView diagramView = new XsdDiagramView(rootNode);

                // 4. Die UI-Komponente bauen lassen
                Node view = diagramView.build();

                // 5. Die fertige Komponente zur Szene hinzufügen
                xsdStackPane.getChildren().add(view);
                // Die Ausrichtung ist weiterhin CENTER_LEFT, wie im FXML definiert

            } else {
                Label infoLabel = new Label("Kein Wurzelelement im Schema gefunden.");
                xsdStackPane.getChildren().add(infoLabel);
                StackPane.setAlignment(infoLabel, Pos.CENTER);
            }

        } catch (Exception e) {
            logger.error("Fehler beim Ermitteln des Wurzelelements aus dem XSD.", e);
            Label errorLabel = new Label("Fehler beim Parsen des XSDs.");
            xsdStackPane.getChildren().add(errorLabel);
            StackPane.setAlignment(errorLabel, Pos.CENTER);
        }
    }

    private void setupCodeArea() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < 2 * 1024 * 1024) { // Max 2 MB
                Platform.runLater(() -> codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(newText)));
            }
        });
        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        stackPane.getChildren().add(virtualizedScrollPane);
    }

    private void setupDragAndDrop() {
        xsdPane.setOnDragOver(this::handleFileOverEvent);
        xsdPane.setOnDragExited(event -> resetDragStyle());
        xsdPane.setOnDragDropped(this::handleFileDroppedEvent);
    }

    private void resetDragStyle() {
        xsdPane.getStyleClass().clear();
        xsdPane.getStyleClass().add("tab-pane");
    }

    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XSD Text");
        codeArea.clear();
        codeArea.setBackground(null);

        try {
            if (xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXsdFile().toPath()));
                codeArea.scrollToPixel(1, 1);

                logger.debug("Caret Position: {}", codeArea.getCaretPosition());
                logger.debug("Caret Column: {}", codeArea.getCaretColumn());
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error("Error in reloadXMLText: ");
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void loadXsdFile() throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Schema Definition", "*.xsd")
        );
        fileChooser.setInitialDirectory(new File("."));
        var xsdFile = fileChooser.showOpenDialog(null);

        if (xsdFile != null && xsdFile.exists()) {
            logger.debug("open File: {}", xsdFile.getAbsolutePath());
            xmlService.setCurrentXsdFile(xsdFile);
            xsdFilePath.setText(xsdFile.getAbsolutePath());
            xsdForSampleDataPath.setText(xsdFile.getAbsolutePath());

            reloadXmlText();
            setupXsdDiagram();
        }
    }

    @FXML
    private void openOutputFolderDialog() {
        documentationOutputDirectory = new DirectoryChooser();
        documentationOutputDirectory.setTitle("Output Directory");
        documentationOutputDirectory.setInitialDirectory(new File("."));
        selectedDocumentationOutputDirectory = documentationOutputDirectory.showDialog(null);

        if (selectedDocumentationOutputDirectory != null && selectedDocumentationOutputDirectory.exists()) {
            logger.debug("Directory: {}", selectedDocumentationOutputDirectory.getAbsolutePath());
            documentationOutputDirPath.setText(selectedDocumentationOutputDirectory.getAbsolutePath());
        } else {
            logger.debug("no dir selected");
            documentationOutputDirPath.setText(null);
        }
    }

    @FXML
    private void generateDocumentation() {
        if (isValidDocumentationSetup()) {
            progressDocumentation.setVisible(true);
            Task<Void> task = createDocumentationTask();
            progressDocumentation.progressProperty().bind(task.progressProperty());
            statusText.textProperty().bind(task.messageProperty());
            executeTask(task);
        } else {
            logger.debug("Invalid setup for documentation generation");
        }
    }

    private boolean isValidDocumentationSetup() {
        return selectedDocumentationOutputDirectory != null &&
                xmlService.getCurrentXsdFile() != null &&
                xmlService.getCurrentXsdFile().exists();
    }

    private Task<Void> createDocumentationTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                try {
                    XsdDocumentationService xsdDocService = new XsdDocumentationService();
                    xsdDocService.setXsdFilePath(xmlService.getCurrentXsdFile().getPath());
                    xsdDocService.setMethod(grafikFormat.getValue().equals("SVG") ?
                            XsdDocumentationService.ImageOutputMethod.SVG :
                            XsdDocumentationService.ImageOutputMethod.PNG);
                    xsdDocService.generateXsdDocumentation(selectedDocumentationOutputDirectory);

                    if (openFileAfterCreation.isSelected()) {
                        Desktop.getDesktop().open(new File(selectedDocumentationOutputDirectory, "index.html"));
                    }
                } catch (IOException e) {
                    logger.error("Error generating documentation", e);
                }
                return null;
            }
        };
    }

    @FXML
    void handleFileOverEvent(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!xsdPane.getStyleClass().contains("xmlPaneFileDragDrop-active")) {
                xsdPane.getStyleClass().add("xmlPaneFileDragDrop-active");
            }
        } else {
            event.consume();
        }
    }

    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();

        for (File f : db.getFiles()) {
            logger.debug("FILE: {}", f.getAbsoluteFile());
            if (f.isFile() && f.exists() && f.getAbsolutePath().toLowerCase().endsWith(".xsd")) {
                this.xmlService.setCurrentXsdFile(f);
                this.xsdFilePath.setText(f.getAbsolutePath());
                this.xsdForSampleDataPath.setText(f.getAbsolutePath());

                setupXsdDiagram();
                reloadXmlText();
            }
        }
    }

    // ======================================================================
    // Methoden für den "Generate Example Data" Tab
    // ======================================================================
    @FXML
    private void selectOutputXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Sample XML to...");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        fileChooser.setInitialFileName("sample.xml");
        selectedOutputXmlFile = fileChooser.showSaveDialog(null);

        if (selectedOutputXmlFile != null) {
            outputXmlPath.setText(selectedOutputXmlFile.getAbsolutePath());
        }
    }

    @FXML
    private void generateSampleDataAction() {
        File currentXsd = xmlService.getCurrentXsdFile();
        if (currentXsd == null || !currentXsd.exists()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please load a valid XSD file first.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        boolean mandatoryOnly = mandatoryOnlyCheckBox.isSelected();
        int maxOccurrences = maxOccurrencesSpinner.getValue();

        Task<String> generationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Generating sample XML...");
                XsdDocumentationService docService = new XsdDocumentationService();
                docService.setXsdFilePath(currentXsd.getPath());
                return docService.generateSampleXml(mandatoryOnly, maxOccurrences);
            }
        };

        // Binden der Sichtbarkeit des ProgressIndicators an den laufenden Task
        progressSampleData.visibleProperty().bind(generationTask.runningProperty());
        sampleDataTextArea.visibleProperty().bind(generationTask.runningProperty().not());


        generationTask.setOnSucceeded(event -> {
            String generatedXml = generationTask.getValue();
            sampleDataTextArea.setText(generatedXml);

            if (selectedOutputXmlFile != null) {
                try (FileWriter writer = new FileWriter(selectedOutputXmlFile)) {
                    writer.write(generatedXml);
                    logger.info("Sample XML saved to: {}", selectedOutputXmlFile.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("Could not save sample XML to file", e);
                    new Alert(Alert.AlertType.ERROR, "Could not save file: " + e.getMessage()).showAndWait();
                }
            }
        });

        generationTask.setOnFailed(event -> {
            logger.error("Sample XML generation failed", generationTask.getException());
            sampleDataTextArea.setText("Error during generation:\n" + generationTask.getException().getMessage());
        });

        executeTask(generationTask);
    }

    // Generische executeTask Methode anpassen, um auch mit Task<String> zu arbeiten
    private void executeTask(Task<?> task) {
        if (parentController != null) {
            parentController.service.execute(task);
        } else {
            logger.warn("Parent controller is null, cannot execute task.");
            // Fallback, wenn kein Parent-Controller da ist
            new Thread(task).start();
        }
    }

    @FXML
    private void test() {
        final var testFilePath = Paths.get("release/examples/xsd/FundsXML4.xsd");
        final var outputFilePath = Paths.get("output/test");

        if (Files.exists(testFilePath)) {
            this.xmlService.setCurrentXsdFile(testFilePath.toFile());
            this.xsdFilePath.setText(testFilePath.toFile().getAbsolutePath());
            this.xsdForSampleDataPath.setText(testFilePath.toFile().getAbsolutePath());

            setupXsdDiagram();
            reloadXmlText();

            this.selectedDocumentationOutputDirectory = outputFilePath.toFile();
            this.documentationOutputDirPath.setText(outputFilePath.toFile().getAbsolutePath());
        } else {
            logger.debug("test file not found: {}", testFilePath.toFile().getAbsolutePath());
        }
    }
}