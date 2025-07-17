package org.fxt.freexmltoolkit.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.*;
import org.reactfx.Subscription;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.XsdSchema;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.fxt.freexmltoolkit.controls.XmlEditor.computeHighlighting;

public class XsdController {

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab xsdTab;
    @FXML
    private Label statusText;

    // ExecutorService für Hintergrund-Tasks
    private final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Service-Klassen
    private final XmlService xmlService = new XmlServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private Object parentController;

    /**
     * Allows a parent controller to set itself for communication.
     * @param parentController The parent controller instance.
     */
    public void setParentController(Object parentController) {
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
    private HBox xsdInfoPane;
    @FXML
    private ProgressIndicator xsdDiagramProgress;
    @FXML
    private Label xsdInfoPathLabel, xsdInfoNamespaceLabel, xsdInfoVersionLabel;
    @FXML
    private ProgressIndicator progressSampleData;
    @FXML
    private HBox textInfoPane;
    @FXML
    private Label textInfoPathLabel;
    @FXML
    private VBox noFileLoadedPaneText;
    @FXML
    private CodeArea sourceCodeTextArea;
    @FXML
    private ProgressIndicator textProgress;

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
    private TextArea sampleDataTextArea;

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
                    sourceCodeTextArea.setVisible(false);
                    sourceCodeTextArea.setManaged(false);
                }
            }
        });

        sourceCodeTextArea.setParagraphGraphicFactory(LineNumberFactory.get(sourceCodeTextArea));
        Subscription RTHLSubscription = sourceCodeTextArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(200))
                .subscribe(ignore -> {
                    sourceCodeTextArea.setStyleSpans(0, computeHighlighting(sourceCodeTextArea.getText()));
                });
    }

    @FXML
    private void openXsdFileChooser() {
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
            xmlService.setCurrentXsdFile(selectedFile);
            openXsdFile(selectedFile);
        }
    }

    /**
     * Loads a given XSD file programmatically.
     * This can be called from a parent controller, for example to load a file from a recent files list.
     *
     * @param file The XSD file to load.
     */
    public void loadXsdFile(File file) {
        if (file != null && file.exists()) {
            xmlService.setCurrentXsdFile(file);
            openXsdFile(file);
        }
    }

    // ======================================================================
    // Methoden für den "Graphic" Tab
    // ======================================================================

    private void openXsdFile(File file) {
        // Update properties with the newly opened file and its directory
        propertiesService.addLastOpenFile(file);
        if (file.getParent() != null) {
            propertiesService.setLastOpenDirectory(file.getParent());
        }
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
                // Langwierige Operation: Parsen und Baum erstellen
                XsdParser parser = new XsdParser(currentXsdFile.getAbsolutePath());
                XsdDocumentationService docService = new XsdDocumentationService();
                XsdNodeInfo rootNode = docService.buildLightweightTree(parser);

                // Metadaten und Dokumentation extrahieren
                String targetNamespace = "Not defined";
                String version = "Not specified";
                XsdSchema rootSchema = parser.getResultXsdSchemas().findFirst().orElse(null);
                if (rootSchema != null) {
                    targetNamespace = rootSchema.getTargetNamespace();
                    if (rootSchema.getVersion() != null) {
                        version = rootSchema.getVersion();
                    }

                    // Dokumentation und Javadoc auslesen
                    var docParts = docService.extractDocumentationParts(rootSchema);
                    return new DiagramData(rootNode, targetNamespace, version, docParts.mainDocumentation(), docParts.javadocContent(), fileContent);
                }

                return new DiagramData(rootNode, targetNamespace, version, "", "", fileContent);
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
                org.fxt.freexmltoolkit.controls.XsdDiagramView diagramView = new org.fxt.freexmltoolkit.controls.XsdDiagramView(result.rootNode(), this, result.documentation(), result.javadoc());
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
            sourceCodeTextArea.replaceText(result.fileContent());
            sourceCodeTextArea.setVisible(true);
            sourceCodeTextArea.setManaged(true);
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

    // ======================================================================
    // Handler-Methoden für die FXML-Tabs
    // ======================================================================
    @FXML
    private void generateDocumentation() {
        logger.warn("generateDocumentation called. (Not implemented yet)");
        statusText.setText("Function 'Generate Documentation' is not yet implemented.");
    }

    @FXML
    private void openOutputFolderDialog() {
        logger.warn("openOutputFolderDialog called. (Not implemented yet)");
    }

    @FXML
    private void selectOutputXmlFile() {
        logger.warn("selectOutputXmlFile called. (Not implemented yet)");
    }

    @FXML
    private void generateSampleDataAction() {
        logger.warn("generateSampleDataAction called. (Not implemented yet)");
    }

    @FXML
    private void test() {
        logger.info("Test button clicked.");
        new Alert(Alert.AlertType.INFORMATION, "Test successful!").showAndWait();
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
}