package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controller.controls.SearchReplaceController;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.XsdDiagramView;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.*;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XsdController {

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab xsdTab;
    @FXML
    private Label statusText;

    // schema nivelieren
    @FXML
    private Tab flattenTab;
    @FXML
    private TextField xsdToFlattenPath;
    @FXML
    private TextField flattenedXsdPath;
    @FXML
    private Button flattenXsdButton;
    @FXML
    private Label flattenStatusLabel;
    @FXML
    private CodeArea flattenedXsdTextArea;
    @FXML
    private ProgressIndicator flattenProgress;

    // ExecutorService für Hintergrund-Tasks
    private final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Service-Klassen
    private final XmlService xmlService = new XmlServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private MainController parentController;

    // --- NEU: Such- und Ersetzen-Funktionalität ---
    private SearchReplaceController searchController;
    private PopOver searchPopOver;

    private enum SearchMode {SEARCH, REPLACE}



    /**
     * Allows a parent controller to set itself for communication.
     * @param parentController The parent controller instance.
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }


    // ======================================================================
    // Felder und Methoden für den "Graphic" Tab (XSD)
    // ======================================================================
    @FXML
    private StackPane xsdStackPane;
    @FXML
    private VBox noFileLoadedPane;
    @FXML
    private TitledPane xsdInfoPane;
    @FXML
    private ProgressIndicator xsdDiagramProgress;
    @FXML
    private Label xsdInfoPathLabel, xsdInfoNamespaceLabel, xsdInfoVersionLabel;
    @FXML
    private ProgressIndicator progressSampleData;

    // Felder für den text tab

    @FXML
    private HBox textInfoPane;
    @FXML
    private Label textInfoPathLabel;
    @FXML
    private VBox noFileLoadedPaneText;
    @FXML
    private XmlCodeEditor sourceCodeEditor;
    @FXML
    private ProgressIndicator textProgress;

    @FXML
    private Button generateSampleDataButton;

    // ======================================================================
    // Felder und Methoden für den "Documentation" Tab
    // ======================================================================
    @FXML
    private Tab documentation;
    @FXML
    private TextField xsdFilePath;
    @FXML
    private TextField documentationOutputDirPath;
    @FXML
    private CheckBox useMarkdownRenderer;
    @FXML
    private CheckBox openFileAfterCreation;
    @FXML
    private CheckBox createExampleData;
    @FXML
    private ChoiceBox<String> grafikFormat;
    @FXML
    private VBox xsdPane;
    @FXML
    private ScrollPane progressScrollPane;
    @FXML
    private VBox progressContainer;
    @FXML
    private Button openDocFolder;

    // ======================================================================
    // Felder und Methoden für den "Generate Example Data" Tab
    // ======================================================================
    @FXML
    private TextField xsdForSampleDataPath;
    @FXML
    private TextField outputXmlPath;
    @FXML
    private CheckBox mandatoryOnlyCheckBox;
    @FXML
    private Spinner<Integer> maxOccurrencesSpinner;
    @FXML
    private CodeArea sampleDataTextArea;

    @FXML
    private VBox taskStatusBar;
    @FXML
    private VBox taskContainer;
    @FXML
    private final static Logger logger = LogManager.getLogger(XsdController.class);

    private record DiagramData(XsdNodeInfo rootNode, String targetNamespace, String version, String documentation, String javadoc, String fileContent) {
    }

    // Eine Map, um die UI-Zeile für jeden Task zu speichern
    private final ConcurrentHashMap<Task<?>, HBox> taskUiMap = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        xsdTab.setOnSelectionChanged(event -> {
            if (xsdTab.isSelected()) {
                if (xmlService.getCurrentXsdFile() == null) {
                    noFileLoadedPane.setVisible(true);
                    noFileLoadedPane.setManaged(true);
                    xsdInfoPane.setVisible(false);
                    xsdInfoPane.setManaged(false);
                }
            }
        });
        textTab.setOnSelectionChanged(event -> {
            if (textTab.isSelected()) {
                if (xmlService.getCurrentXsdFile() == null) {
                    noFileLoadedPaneText.setVisible(true);
                    noFileLoadedPaneText.setManaged(true);
                    textInfoPane.setVisible(false);
                    textInfoPane.setManaged(false);
                    sourceCodeEditor.setVisible(false);
                    sourceCodeEditor.setManaged(false);
                }
            }
        });

        generateSampleDataButton.disableProperty().bind(xsdForSampleDataPath.textProperty().isEmpty());

        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1);
        maxOccurrencesSpinner.setValueFactory(valueFactory);

        // Setup for the sample data CodeArea
        sampleDataTextArea.setParagraphGraphicFactory(LineNumberFactory.get(sampleDataTextArea));

        // NEU: Such-Popup und Tastenkürzel für den XSD-Texteditor hinzufügen
        try {
            initializeSearchPopup();
            sourceCodeEditor.getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown()) {
                    switch (event.getCode()) {
                        case F -> {
                            showSearchPopup(SearchMode.SEARCH);
                            event.consume();
                        }
                        case R -> {
                            showSearchPopup(SearchMode.REPLACE);
                            event.consume();
                        }
                    }
                }
            });
        } catch (IOException e) {
            logger.error("Failed to initialize search popup for XSD editor.", e);
        }
    }

    private void initializeSearchPopup() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/controls/SearchReplaceControl.fxml"));
        Pane searchPane = loader.load();
        searchController = loader.getController();
        searchController.setXmlCodeEditor(this.sourceCodeEditor); // Verbindet die Suche mit dem XSD-Editor
        searchPopOver = new PopOver(searchPane);
        searchPopOver.setDetachable(false);
        searchPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        searchPopOver.setTitle("Find/Replace");
    }


    @FXML
    private File openXsdFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open XSD Schema");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File selectedFile = fileChooser.showOpenDialog(tabPane.getScene().getWindow());
        if (selectedFile != null) {
            // Die zentrale Methode `openXsdFile` ist nun für alles verantwortlich.
            openXsdFile(selectedFile);
            return selectedFile;
        }
        return null;
    }

    /**
     * NEU: Zeigt das Such-Popup für den XSD-Editor an.
     */
    private void showSearchPopup(SearchMode mode) {
        if (searchPopOver == null) return;
        if (mode == SearchMode.SEARCH) {
            searchController.selectTab(searchController.getSearchTab());
        } else {
            searchController.selectTab(searchController.getReplaceTab());
        }
        searchPopOver.show(sourceCodeEditor.getCodeArea(), -5);
        searchController.focusFindField();
    }


    // ======================================================================
    // Methoden für den "Graphic" Tab
    // ======================================================================

    /**
     * Die Methode ist jetzt public, damit sie vom MainController
     * aufgerufen werden kann, wenn eine XSD-Datei aus der "Zuletzt geöffnet"-Liste
     * ausgewählt wird.
     *
     * @param file Die zu öffnende XSD-Datei.
     */
    public void openXsdFile(File file) {
        // Die Datei muss ZUERST im Service gesetzt werden,
        // damit alle nachfolgenden Methoden den korrekten Zustand haben.
        xmlService.setCurrentXsdFile(file);

        // Rufe die zentrale Methode im MainController auf.
        // Diese fügt die Datei nicht nur zur Liste hinzu, sondern aktualisiert auch das Menü.
        if (parentController != null) {
            parentController.addFileToRecentFiles(file);
        } else {
            // Fallback, falls der Parent-Controller aus irgendeinem Grund nicht gesetzt ist.
            propertiesService.addLastOpenFile(file);
        }

        if (file.getParent() != null) {
            propertiesService.setLastOpenDirectory(file.getParent());
        }
        // Populate the file path fields on other tabs to keep the UI in sync
        String absolutePath = file.getAbsolutePath();
        xsdFilePath.setText(absolutePath);
        xsdForSampleDataPath.setText(absolutePath);
        xsdToFlattenPath.setText(absolutePath); // Auch für den Flatten-Tab setzen

        setupXsdDiagram();
    }

    /**
     * Richtet die Diagrammansicht für die aktuell geladene XSD-Datei ein.
     * Diese Methode erstellt einen Hintergrund-Task, um die XSD-Datei zu parsen und den Baum aufzubauen. Dies wird
     * ausgeführt, um die Benutzeroberfläche nicht zu blockieren.
     */
    private void setupXsdDiagram() {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null) {
            // Zeige den "Keine Datei geladen"-Platzhalter an
            noFileLoadedPane.setVisible(true);
            noFileLoadedPane.setManaged(true);
            xsdInfoPane.setVisible(false);
            xsdInfoPane.setManaged(false);
            return;
        }
        // Zeige den Lade-Indikator an und verstecke den Platzhalter
        xsdDiagramProgress.setVisible(true);
        textProgress.setVisible(true);
        noFileLoadedPane.setVisible(false);
        noFileLoadedPane.setManaged(false);
        noFileLoadedPaneText.setVisible(false);
        noFileLoadedPaneText.setManaged(false);
        xsdStackPane.getChildren().clear(); // Alte Ansicht entfernen

        Task<DiagramData> task = new Task<>() {
            @Override
            protected DiagramData call() throws Exception {
                updateMessage("Parsing XSD and building diagram...");

                String fileContent = Files.readString(currentXsdFile.toPath());

                // 1. Service für Baum und Doku verwenden (neue Implementierung)
                XsdViewService viewService = new XsdViewService();
                XsdNodeInfo rootNode = viewService.buildLightweightTree(fileContent);
                XsdViewService.DocumentationParts docParts = viewService.extractDocumentationParts(fileContent);

                // 2. Metadaten (targetNamespace, version) mit JAXP/DOM auslesen
                String targetNamespace = "Not defined";
                String version = "Not specified";

                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    // Sichere Verarbeitung ist wichtig
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.setXIncludeAware(false);
                    factory.setExpandEntityReferences(false);

                    DocumentBuilder builder = factory.newDocumentBuilder();
                    // Wichtig: InputSource mit StringReader verwenden, um aus dem String zu parsen
                    Document doc = builder.parse(new InputSource(new StringReader(fileContent)));

                    org.w3c.dom.Element schemaElement = doc.getDocumentElement();
                    if (schemaElement != null && "schema".equals(schemaElement.getLocalName())) {
                        if (schemaElement.hasAttribute("targetNamespace")) {
                            targetNamespace = schemaElement.getAttribute("targetNamespace");
                        }
                        if (schemaElement.hasAttribute("version")) {
                            version = schemaElement.getAttribute("version");
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not read metadata (targetNamespace, version).", e);
                    // Standardwerte werden beibehalten
                }

                return new DiagramData(rootNode, targetNamespace, version, docParts.mainDocumentation(), docParts.javadocContent(), fileContent);
            }
        };

        task.setOnSucceeded(event -> {
            DiagramData result = task.getValue();
            xsdInfoPane.setVisible(true);
            xsdInfoPane.setManaged(true);
            xsdInfoPathLabel.setText(currentXsdFile.getAbsolutePath());
            xsdInfoNamespaceLabel.setText(result.targetNamespace());
            xsdInfoVersionLabel.setText(result.version());

            if (result.rootNode() != null) {
                XsdDiagramView diagramView = new XsdDiagramView(result.rootNode(), this, result.documentation(), result.javadoc());
                logger.debug("lade diagramm...");
                xsdStackPane.getChildren().add(diagramView.build());
            } else {
                Label infoLabel = new Label("No root element found in schema.");
                xsdStackPane.getChildren().add(infoLabel);
            }
            xsdDiagramProgress.setVisible(false);

            // Text-Tab UI aktualisieren
            textInfoPane.setVisible(true);
            textInfoPane.setManaged(true);
            textInfoPathLabel.setText(currentXsdFile.getAbsolutePath());

            sourceCodeEditor.getCodeArea().replaceText(result.fileContent());
            sourceCodeEditor.setVisible(true);
            sourceCodeEditor.setManaged(true);

            textProgress.setVisible(false);

            statusText.setText("XSD loaded successfully.");
        });

        task.setOnFailed(event -> {
            xsdDiagramProgress.setVisible(false);
            textProgress.setVisible(false);
            Throwable e = task.getException();
            logger.error("Error creating the diagram view in the background.", e);
            statusText.setText("Error: " + e.getMessage());
            xsdStackPane.getChildren().add(new Label("Failed to load XSD: " + e.getMessage()));
        });

        executeTask(task);
    }

    public void saveDocumentation(String mainDoc, String javadoc) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            new Alert(Alert.AlertType.WARNING, "No XSD file loaded to save to.").showAndWait();
            return;
        }

        // Javadoc-Teil nur anhängen, wenn er Inhalt hat
        String fullDocumentation = javadoc.trim().isEmpty() ? mainDoc.trim() : mainDoc.trim() + "\n\n" + javadoc.trim();

        // Task zum Speichern der Datei erstellen
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving documentation...");
                // Service aufrufen, der die eigentliche Arbeit macht
                xmlService.updateRootDocumentation(currentXsdFile, fullDocumentation);
                return null;
            }
        };

        saveTask.setOnSucceeded(event -> {
            statusText.setText("Documentation saved successfully.");
            // After a successful save, reload the diagram view to reflect the changes
            // and reset the "dirty" state of the editor.
            setupXsdDiagram();
        });

        saveTask.setOnFailed(event -> {
            logger.error("Failed to save documentation", saveTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not save documentation: " + saveTask.getException().getMessage()).showAndWait();
            statusText.setText("Failed to save documentation.");
        });

        executeTask(saveTask);
    }

    public void saveExampleValues(String xpath, List<String> exampleValues) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            new Alert(Alert.AlertType.WARNING, "No XSD file loaded to save to.").showAndWait();
            return;
        }
        if (xpath == null || xpath.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "No element selected to save examples for.").showAndWait();
            return;
        }

        Task<Void> saveExamplesTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving example values...");
                // This service method needs to be implemented as described in step 1c.
                xmlService.updateExampleValues(currentXsdFile, xpath, exampleValues);
                return null;
            }
        };

        saveExamplesTask.setOnSucceeded(event -> {
            statusText.setText("Example values saved successfully.");
            // Reload the view to reflect the changes
            setupXsdDiagram();
        });

        saveExamplesTask.setOnFailed(event -> {
            logger.error("Failed to save example values for xpath: " + xpath, saveExamplesTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not save example values: " + saveExamplesTask.getException().getMessage()).showAndWait();
            statusText.setText("Failed to save example values.");
        });

        executeTask(saveExamplesTask);
    }

    /**
     * Wählt programmatisch den "Text"-Tab in der Tab-Ansicht aus.
     */
    public void selectTextTab() {
        logger.debug("select text tab");
        if (tabPane != null && textTab != null) {
            logger.debug("tabpane und texttab != null");
            tabPane.getSelectionModel().select(textTab);
        }
    }

    // ======================================================================
    // Handler-Methoden für die FXML-Tabs
    // ======================================================================
    @FXML
    private void generateDocumentation() {
        // 1. Validate inputs
        String xsdPath = xsdFilePath.getText();
        String outputPath = documentationOutputDirPath.getText();

        if (xsdPath == null || xsdPath.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Please provide a source XSD file.").showAndWait();
            return;
        }
        if (outputPath == null || outputPath.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Please select an output directory.").showAndWait();
            return;
        }

        File xsdFile = new File(xsdPath);
        File outputDir = new File(outputPath);

        if (!xsdFile.exists()) {
            new Alert(Alert.AlertType.ERROR, "The specified XSD file does not exist: " + xsdPath).showAndWait();
            return;
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            new Alert(Alert.AlertType.ERROR, "Could not create the output directory: " + outputPath).showAndWait();
            return;
        }
        if (!outputDir.isDirectory()) {
            new Alert(Alert.AlertType.ERROR, "The specified output path is not a directory: " + outputPath).showAndWait();
            return;
        }

        // 2. Prepare UI for background task
        progressScrollPane.setVisible(true);
        progressContainer.getChildren().clear();
        openDocFolder.setDisable(true);
        statusText.setText("Starting documentation generation...");

        Task<Void> generationTask = getGenerationTask(xsdFile, outputDir);

        // 4. Start the task
        executeTask(generationTask);
    }

    private @NotNull Task<Void> getGenerationTask(File xsdFile, File outputDir) {
        Task<Void> generationTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Generating Documentation...");
                XsdDocumentationService docService = new XsdDocumentationService();

                // Set options from UI
                docService.setXsdFilePath(xsdFile.getAbsolutePath());
                docService.setUseMarkdownRenderer(useMarkdownRenderer.isSelected());

                // Note: JPG is not supported by the service, it will default to SVG.
                String format = grafikFormat.getValue();
                if ("PNG".equalsIgnoreCase(format)) {
                    docService.setMethod(XsdDocumentationService.ImageOutputMethod.PNG);
                } else { // Default to SVG
                    docService.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
                }

                // Set up progress listener to update UI from background thread
                docService.setProgressListener(progressUpdate -> Platform.runLater(() -> {
                    String message = String.format("[%s] %s", progressUpdate.status(), progressUpdate.taskName());
                    if (progressUpdate.status() == TaskProgressListener.ProgressUpdate.Status.FINISHED) {
                        message += " (took " + progressUpdate.durationMillis() + "ms)";
                    }
                    progressContainer.getChildren().add(new Label(message));
                    progressScrollPane.setVvalue(1.0); // Auto-scroll to bottom
                }));

                // This is the main long-running operation
                docService.generateXsdDocumentation(outputDir);
                return null;
            }
        };

        // 3. Define what happens on success or failure
        generationTask.setOnSucceeded(event -> handleDocumentationSuccess(outputDir));
        generationTask.setOnFailed(event -> handleDocumentationFailure(generationTask.getException()));
        return generationTask;
    }

    @FXML
    private void openOutputFolderDialog() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Folder for Documentation");

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                directoryChooser.setInitialDirectory(lastDir);
            }
        }

        File selectedDirectory = directoryChooser.showDialog(tabPane.getScene().getWindow());

        if (selectedDirectory != null) {
            documentationOutputDirPath.setText(selectedDirectory.getAbsolutePath());
        }
    }

    /**
     * Handles UI updates after the documentation has been successfully generated.
     * @param outputDir The directory where the documentation was created.
     */
    private void handleDocumentationSuccess(File outputDir) {
        statusText.setText("Documentation generated successfully in " + outputDir.getAbsolutePath());
        openDocFolder.setDisable(false);
        openDocFolder.setOnAction(e -> openFolderInExplorer(outputDir));

        if (openFileAfterCreation.isSelected()) {
            File indexFile = new File(outputDir, "index.html");
            if (indexFile.exists()) {
                try {
                    // Use Desktop API to open the default browser
                    java.awt.Desktop.getDesktop().browse(indexFile.toURI());
                } catch (IOException ex) {
                    logger.error("Could not open index.html in browser.", ex);
                    // Fallback to just opening the folder if the browser fails
                    openFolderInExplorer(outputDir);
                }
            }
        }
    }

    /**
     * Handles UI updates when the documentation generation fails.
     * @param e The exception that occurred.
     */
    private void handleDocumentationFailure(Throwable e) {
        progressScrollPane.setVisible(false);
        logger.error("Failed to generate documentation.", e);
        statusText.setText("Error generating documentation.");
        new Alert(Alert.AlertType.ERROR, "Failed to generate documentation: " + e.getMessage()).showAndWait();
    }

    private void openFolderInExplorer(File folder) {
        try {
            java.awt.Desktop.getDesktop().open(folder);
        } catch (IOException ex) {
            logger.error("Could not open output directory: {}", folder.getAbsolutePath(), ex);
            new Alert(Alert.AlertType.ERROR, "Could not open the output directory. Please navigate to it manually:\n" + folder.getAbsolutePath()).showAndWait();
        }
    }

    @FXML
    private void selectOutputXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Sample XML As...");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        fileChooser.setInitialFileName("sample.xml");

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());

        if (file != null) {
            outputXmlPath.setText(file.getAbsolutePath());
            // Update last used directory
            if (file.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(file.getParentFile().getAbsolutePath());
            }
        }
    }

    @FXML
    private void generateSampleDataAction() {
        String xsdPath = xsdForSampleDataPath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Please load an XSD source file first.").showAndWait();
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            new Alert(Alert.AlertType.ERROR, "The specified XSD file does not exist: " + xsdPath).showAndWait();
            return;
        }

        boolean mandatoryOnly = mandatoryOnlyCheckBox.isSelected();
        int maxOccurrences = maxOccurrencesSpinner.getValue();

        progressSampleData.setVisible(true);
        sampleDataTextArea.clear();

        Task<String> generationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Generating sample XML...");
                XsdDocumentationService docService = new XsdDocumentationService();
                docService.setXsdFilePath(xsdFile.getAbsolutePath());
                return docService.generateSampleXml(mandatoryOnly, maxOccurrences);
            }
        };

        generationTask.setOnSucceeded(event -> {
            progressSampleData.setVisible(false);
            String resultXml = generationTask.getValue();

            sampleDataTextArea.replaceText(resultXml);
            sampleDataTextArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(resultXml));

            statusText.setText("Sample XML generated successfully.");

            // Optional: Save to file if a path is provided
            String outputPath = outputXmlPath.getText();
            if (outputPath != null && !outputPath.isBlank()) {
                saveStringToFile(resultXml, new File(outputPath));
            }
        });

        generationTask.setOnFailed(event -> {
            progressSampleData.setVisible(false);
            Throwable e = generationTask.getException();
            logger.error("Failed to generate sample XML data.", e);
            statusText.setText("Error generating sample XML.");
            new Alert(Alert.AlertType.ERROR, "Failed to generate sample XML: " + e.getMessage()).showAndWait();
        });

        executeTask(generationTask);
    }

    @FXML
    private void test() {
        logger.info("Test button clicked.");
        new Alert(Alert.AlertType.INFORMATION, "Test successful!").showAndWait();
    }

    private void saveStringToFile(String content, File file) {
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving to " + file.getName() + "...");
                Files.writeString(file.toPath(), content);
                return null;
            }
        };

        saveTask.setOnSucceeded(event -> {
            statusText.setText("Sample XML generated and saved successfully.");
        });

        saveTask.setOnFailed(event -> {
            logger.error("Failed to save content to file: " + file.getAbsolutePath(), saveTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not save file: " + saveTask.getException().getMessage()).showAndWait();
        });

        executeTask(saveTask);
    }

    // ... (Rest der Klasse, z.B. Task-Management, etc.)

    private <T> void executeTask(Task<T> task) {
        taskStatusBar.setVisible(true);
        taskStatusBar.setManaged(true);

        HBox taskRow = new HBox(10);
        taskRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        Label taskLabel = new Label();
        taskLabel.textProperty().bind(task.messageProperty());

        taskRow.getChildren().addAll(progressBar, taskLabel);
        taskContainer.getChildren().add(taskRow);
        taskUiMap.put(task, taskRow);

        task.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            if (!isRunning) { // Task is finished, remove its UI entry
                HBox completedTaskRow = taskUiMap.remove(task);
                if (completedTaskRow != null) {
                    taskContainer.getChildren().remove(completedTaskRow);
                }
                if (taskUiMap.isEmpty()) {
                    taskStatusBar.setVisible(false);
                    taskStatusBar.setManaged(false);
                }
            }
        });

        executorService.submit(task);
    }

    // Methoden für schema nivelieren:
    // In XsdController.java hinzufügen

    @FXML
    private void openXsdToFlattenChooser() {
        File selectedFile = openXsdFileChooser();
        if (selectedFile != null) {
            xsdToFlattenPath.setText(selectedFile.getAbsolutePath());
            flattenedXsdPath.setText(getFlattenedPath(selectedFile.getAbsolutePath()));
            flattenXsdButton.setDisable(false);
        }
    }

    @FXML
    private void selectFlattenedXsdPath() {
        File file = showSaveDialog("Save Flattened XSD", "Flattened XSD Files", "*.xsd");
        if (file != null) {
            flattenedXsdPath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void flattenXsdAction() {
        String sourcePath = xsdToFlattenPath.getText();
        String destinationPath = flattenedXsdPath.getText();

        if (sourcePath == null || sourcePath.isBlank() || destinationPath == null || destinationPath.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please specify both a source and a destination file.");
            return;
        }

        File sourceFile = new File(sourcePath);
        File destinationFile = new File(destinationPath);

        flattenProgress.setVisible(true);
        flattenStatusLabel.setText("Flattening in progress...");
        flattenedXsdTextArea.clear();

        Task<String> flattenTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                XsdFlattenerService flattener = new XsdFlattenerService();
                return flattener.flatten(sourceFile, destinationFile);
            }
        };

        flattenTask.setOnSucceeded(event -> {
            flattenProgress.setVisible(false);
            String flattenedContent = flattenTask.getValue();

            // GEÄNDERT: Highlighting für die flattenedXsdTextArea anwenden
            flattenedXsdTextArea.replaceText(flattenedContent);
            flattenedXsdTextArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(flattenedContent));

            flattenStatusLabel.setText("Successfully flattened and saved to: " + destinationFile.getAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Success", "XSD has been flattened successfully.");
        });

        flattenTask.setOnFailed(event -> {
            flattenProgress.setVisible(false);
            flattenStatusLabel.setText("Error during flattening process.");
            Throwable ex = flattenTask.getException();
            showAlert(Alert.AlertType.ERROR, "Flattening Failed", "An error occurred: " + ex.getMessage());
            ex.printStackTrace();
        });

        executeTask(flattenTask);
    }

    private String getFlattenedPath(String originalPath) {
        if (originalPath == null || !originalPath.toLowerCase().endsWith(".xsd")) {
            return "";
        }
        return originalPath.substring(0, originalPath.length() - 4) + "_flattened.xsd";
    }

    // Stellen Sie sicher, dass diese Methode bereits existiert oder fügen Sie sie hinzu
    private File showSaveDialog(String title, String description, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
        return fileChooser.showSaveDialog(tabPane.getScene().getWindow());
    }

    // In XsdController.java hinzufügen

    /**
     * Zeigt ein standardmäßiges JavaFX-Alert-Dialogfenster an.
     *
     * @param alertType Der Typ des Alerts (z.B. INFORMATION, ERROR, WARNING).
     * @param title     Der Titel des Dialogfensters.
     * @param content   Die Nachricht, die dem Benutzer angezeigt werden soll.
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null); // Wir verwenden keinen Header-Text für ein einfacheres Aussehen
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Fährt den internen ExecutorService herunter, um sicherzustellen, dass alle
     * Hintergrund-Threads sauber beendet werden, wenn die Anwendung geschlossen wird.
     */
    public void shutdown() {
        logger.info("Shutting down XsdController's ExecutorService...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                logger.warn("ExecutorService did not terminate in time, forcing shutdown.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown of ExecutorService was interrupted.", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt(); // Set the interrupt flag again
        }
        logger.info("XsdController shutdown complete.");
    }
}