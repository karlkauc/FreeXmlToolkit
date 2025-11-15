package org.fxt.freexmltoolkit.controller;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.*;
import org.fxt.freexmltoolkit.controls.editor.FindReplaceDialog;
import org.fxt.freexmltoolkit.controls.intellisense.XmlCodeFoldingManager;
import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.*;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XsdController {

    private XsdDiagramView currentDiagramView;
    private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView currentGraphViewV2;

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab xsdTab;
    @FXML
    private Label statusText;
    @FXML
    private Button saveAsXsdButton;
    @FXML
    private Button prettyPrintButton;

    // schema flattening
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

    // ExecutorService for background tasks
    private final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Service classes
    private final XmlService xmlService = new XmlServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private final FavoritesService favoritesService = FavoritesService.getInstance();
    private XsdDomManipulator currentDomManipulator;

    private MainController parentController;
    private boolean hasUnsavedChanges = false;
    private File currentXsdFile;

    // --- NEW: Search and replace functionality ---
    private FindReplaceDialog findReplaceDialog;
    private FindReplaceDialog sampleDataFindReplaceDialog;
    private javafx.event.EventHandler<KeyEvent> searchKeyEventFilter;
    private javafx.event.EventHandler<KeyEvent> sampleDataSearchKeyEventFilter;

    // --- Code folding functionality ---
    private XmlCodeFoldingManager sampleDataCodeFoldingManager;

    // --- Auto-save functionality ---
    private Timer autoSaveTimer;
    private static final String AUTO_SAVE_PREFIX = ".autosave_";


    // --- NEW: Fields for documentation preview ---
    @FXML
    private Tab docPreviewTab;
    @FXML
    private WebView docWebView;
    @FXML
    private Button zoomInButton;
    @FXML
    private Button zoomOutButton;
    @FXML
    private Button zoomResetButton;
    @FXML
    private Label zoomLabel;
    private double currentZoom = 1.0;  // 100% zoom
    private static final double ZOOM_STEP = 0.1;  // 10% per step
    private static final double MIN_ZOOM = 0.1;  // 10% minimum
    private static final double MAX_ZOOM = 5.0;   // 500% maximum
    private HttpServer docServer;
    private static final int DOC_SERVER_PORT = 8080;

    // --- NEW: Fields for type library ---
    @FXML
    private Tab typeLibraryTab;
    @FXML
    private StackPane typeLibraryStackPane;
    private XsdTypeLibraryPanel typeLibraryPanel;



    /**
     * Allows a parent controller to set itself for communication.
     * @param parentController The parent controller instance.
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Check if the XSD tab is currently active
     */
    public boolean isXsdTabActive() {
        return tabPane != null && tabPane.getSelectionModel().getSelectedItem() == xsdTab;
    }

    /**
     * Perform undo operation on current XSD diagram
     */
    public void performUndo() {
        if (currentDiagramView != null && currentDiagramView.getUndoManager() != null) {
            if (currentDiagramView.getUndoManager().undo()) {
                // The diagram view will handle the refresh internally
                logger.debug("Undo operation performed in XSD editor");
            }
        }
    }

    /**
     * Perform redo operation on current XSD diagram
     */
    public void performRedo() {
        if (currentDiagramView != null && currentDiagramView.getUndoManager() != null) {
            if (currentDiagramView.getUndoManager().redo()) {
                // The diagram view will handle the refresh internally
                logger.debug("Redo operation performed in XSD editor");
            }
        }
    }

    /**
     * Adds a command to the undo stack if undo functionality is available
     *
     * @param command The command to add to the undo stack
     */
    public void addCommandToUndoStack(org.fxt.freexmltoolkit.controls.XsdCommand command) {
        if (currentDiagramView != null && currentDiagramView.getUndoManager() != null && command.canUndo()) {
            currentDiagramView.getUndoManager().addExecutedCommand(command);
            logger.debug("Command added to undo stack: {}", command.getDescription());
        } else {
            logger.debug("Undo stack not available or command doesn't support undo: {}",
                    command != null ? command.getDescription() : "null command");
        }
    }

    // ======================================================================
    // Felder und Methoden für den "Graphic" Tab (XSD)
    // ======================================================================
    @FXML
    private StackPane xsdStackPane;
    @FXML
    private StackPane xsdStackPaneV2;
    @FXML
    private ToggleButton editorVersionToggle;
    @FXML
    private VBox noFileLoadedPane;
    @FXML
    private TitledPane xsdInfoPane;
    @FXML
    private ProgressIndicator xsdDiagramProgress;
    @FXML
    private Label xsdInfoPathLabel, xsdInfoNamespaceLabel, xsdInfoVersionLabel;

    // New fields for visualization (UML, Grid)

    // Toggle group created programmatically
    @FXML
    private ProgressIndicator progressSampleData;

    // Fields for the text tab

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
    @FXML
    private Button addXsdToFavoritesButton;
    @FXML
    private MenuButton loadXsdFavoritesButton;
    @FXML
    private ToggleButton toggleFavoritesButton;
    @FXML
    private Button saveXsdButton;
    @FXML
    private SplitPane textTabSplitPane;
    @FXML
    private VBox favoritesPanel;

    // Fields for the graphic tab favorites system
    @FXML
    private Button addXsdToFavoritesButtonGraphic;
    @FXML
    private MenuButton loadXsdFavoritesButtonGraphic;
    @FXML
    private ToggleButton toggleFavoritesButtonGraphic;
    @FXML
    private Button saveXsdButtonGraphic;
    @FXML
    private Button saveAsXsdButtonGraphic;
    @FXML
    private Button prettyPrintButtonGraphic;
    @FXML
    private SplitPane graphicTabSplitPane;
    @FXML
    private VBox favoritesPanelGraphic;

    // Fields for the overview tab favorites system
    @FXML
    private Button loadXsdFavoritesOverview;
    @FXML
    private VBox recentFavoritesContainer;
    @FXML
    private ListView<String> recentFavoritesList;

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
    private CheckBox includeTypeDefinitionsInSourceCode;
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

    private final static Logger logger = LogManager.getLogger(XsdController.class);

    private record DiagramData(XsdNodeInfo rootNode, String targetNamespace, String version, String documentation, String javadoc, String fileContent) {
    }

    // Eine Map, um die UI-Zeile für jeden Task zu speichern
    private final ConcurrentHashMap<Task<?>, HBox> taskUiMap = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        // Initialize ChoiceBox items
        if (grafikFormat != null) {
            grafikFormat.setItems(FXCollections.observableArrayList("SVG", "PNG", "JPG"));
            grafikFormat.setValue("SVG");
        }

        // Initialize favorites menu
        Platform.runLater(() -> {
            refreshXsdFavoritesMenu();
            setupOverviewFavorites();
        });

        // Initialize favorites panel visibility for text tab
        if (favoritesPanel != null) {
            favoritesPanel.setVisible(false);
            favoritesPanel.setManaged(false);
        }

        // Initialize favorites panel visibility for graphic tab
        if (favoritesPanelGraphic != null) {
            favoritesPanelGraphic.setVisible(false);
            favoritesPanelGraphic.setManaged(false);
        }

        // Hide panel initially by removing it from split pane
        if (textTabSplitPane != null && favoritesPanel != null) {
            textTabSplitPane.getItems().remove(favoritesPanel);
        }

        // Hide graphic panel initially by removing it from split pane
        if (graphicTabSplitPane != null && favoritesPanelGraphic != null) {
            graphicTabSplitPane.getItems().remove(favoritesPanelGraphic);
        }

        // Initialize type library
        initializeTypeLibrary();
        
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
                // Always ensure search shortcuts are available when text tab is selected
                if (sourceCodeEditor != null) {
                    ensureSourceCodeEditorInitialized();
                }
                
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

        // Lazy initialization for CodeAreas - they will be initialized when first accessed

        // Setup keyboard shortcuts for search/replace (will initialize on first use)
        setupSearchKeyboardShortcuts();
        // NEW: Initially hide the preview tab and make it inaccessible
        if (docPreviewTab != null) {
            docPreviewTab.setDisable(true);
        }

        // Initialize visualization toggle buttons
        setupVisualizationToggleButtons();

        // Setup bidirectional synchronization between text and graphic tabs
        setupTextToGraphicSync();

        // Setup zoom functionality for documentation preview WebView
        setupWebViewZoom();

        applyEditorSettings();

        // Setup Ctrl+S keyboard shortcut for V2 editor
        setupV2EditorSaveShortcut();
    }

    private void applyEditorSettings() {
        try {
            String fontSizeStr = propertiesService.get("ui.xml.font.size");
            int fontSize = 12; // Default size
            if (fontSizeStr != null) {
                try {
                    fontSize = Integer.parseInt(fontSizeStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid font size in settings, defaulting to 12.", e);
                }
            }
            String style = String.format("-fx-font-size: %dpx;", fontSize);

            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                sourceCodeEditor.getCodeArea().setStyle(style);
                logger.debug("Applied font size {}px to sourceCodeEditor", fontSize);
            }
            if (sampleDataTextArea != null) {
                sampleDataTextArea.setStyle(style);
                logger.debug("Applied font size {}px to sampleDataTextArea", fontSize);
            }
            if (flattenedXsdTextArea != null) {
                flattenedXsdTextArea.setStyle(style);
                logger.debug("Applied font size {}px to flattenedXsdTextArea", fontSize);
            }
        } catch (Exception e) {
            logger.error("Failed to apply editor settings.", e);
        }
    }

    /**
     * Sets up automatic synchronization from text tab to graphic tab.
     * When the user switches from text tab to graphic tab, the graphic view
     * is automatically updated with the current text content.
     */
    private void setupTextToGraphicSync() {
        // Track the last selected tab to detect tab switches
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            // When switching FROM text tab TO graphic tab
            if (oldTab == textTab && newTab == xsdTab) {
                syncTextToGraphic();
            }
            // When switching FROM graphic tab TO text tab
            else if (oldTab == xsdTab && newTab == textTab) {
                syncGraphicToText();
            }
        });

        logger.debug("Bidirectional text-graphic synchronization setup completed");
    }

    /**
     * Synchronizes the text editor content to the graphic view.
     * This is called when switching from text tab to graphic tab.
     */
    private void syncTextToGraphic() {
        if (sourceCodeEditor == null || sourceCodeEditor.getCodeArea() == null) {
            return;
        }

        String currentText = sourceCodeEditor.getCodeArea().getText();
        if (currentText == null || currentText.trim().isEmpty()) {
            return;
        }

        try {
            // Update DOM manipulator with current text
            if (currentDomManipulator == null) {
                currentDomManipulator = new XsdDomManipulator();
            }
            currentDomManipulator.loadXsd(currentText);

            // Reload graphic view without triggering text update
            // Check which editor version is active
            if (editorVersionToggle != null && editorVersionToggle.isSelected()) {
                loadXsdIntoGraphicViewV2(currentText);
                logger.debug("Synchronized text content to V2 graphic view");
            } else {
                loadXsdIntoGraphicView(currentText);
                logger.debug("Synchronized text content to V1 graphic view");
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize text to graphic view", e);
        }
    }

    /**
     * Synchronizes the graphic view content to the text editor.
     * This is called when switching from graphic tab to text tab.
     */
    private void syncGraphicToText() {
        if (sourceCodeEditor == null || sourceCodeEditor.getCodeArea() == null) {
            return;
        }

        if (currentDomManipulator == null) {
            return;
        }

        try {
            String xsdContent = currentDomManipulator.getXsdAsString();
            if (xsdContent == null || xsdContent.trim().isEmpty()) {
                return;
            }

            // Only update if content has changed to avoid unnecessary updates
            String currentText = sourceCodeEditor.getCodeArea().getText();
            if (!xsdContent.equals(currentText)) {
                sourceCodeEditor.getCodeArea().replaceText(xsdContent);
                logger.debug("Synchronized graphic content to text editor");
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize graphic to text view", e);
        }
    }

    /**
     * Toggles between V1 (stable) and V2 (beta) XSD editor.
     * This method is called when the user clicks the editor version toggle button.
     */
    @FXML
    private void toggleEditorVersion() {
        boolean useV2 = editorVersionToggle.isSelected();

        // Toggle visibility of V1 editor
        xsdStackPane.setVisible(!useV2);
        xsdStackPane.setManaged(!useV2);

        // Toggle visibility of V2 editor
        if (xsdStackPaneV2 != null) {
            xsdStackPaneV2.setVisible(useV2);
            xsdStackPaneV2.setManaged(useV2);
        }

        // If switching to V2 and we have content but V2 is not loaded yet, load it
        if (useV2 && currentGraphViewV2 == null && sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
            String xsdContent = sourceCodeEditor.getCodeArea().getText();
            if (xsdContent != null && !xsdContent.trim().isEmpty()) {
                loadXsdIntoGraphicViewV2(xsdContent);
            }
        }

        logger.info("Switched to XSD Editor {}", useV2 ? "V2 (Beta)" : "V1 (Stable)");
    }

    /**
     * Sets up keyboard shortcuts for search/replace without initializing the popup
     */
    private void setupSearchKeyboardShortcuts() {
        // Initialize the search key event filter
        initializeSearchKeyEventFilter();
        
        // Add event filter only when sourceCodeEditor is first accessed
        textTab.setOnSelectionChanged(event -> {
            if (textTab.isSelected() && sourceCodeEditor != null) {
                ensureSourceCodeEditorInitialized();
            }
        });
    }

    /**
     * Initializes the search key event filter
     */
    private void initializeSearchKeyEventFilter() {
        searchKeyEventFilter = event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case F -> {
                        showFindReplaceDialog();
                        event.consume();
                    }
                    case R -> {
                        showFindReplaceDialog();
                        event.consume();
                    }
                }
            }
        };
    }

    /**
     * Ensures the source code editor is properly initialized with line numbers and search shortcuts
     */
    private void ensureSourceCodeEditorInitialized() {
        if (sourceCodeEditor != null) {
            // Initialize line numbers if not already set
            if (sourceCodeEditor.getCodeArea().getParagraphGraphicFactory() == null) {
                sourceCodeEditor.getCodeArea().setParagraphGraphicFactory(LineNumberFactory.get(sourceCodeEditor.getCodeArea()));
            }

            // Always ensure search shortcuts are properly set - remove any existing filters first
            setupSearchKeyboardShortcutsForEditor();
        }
    }

    /**
     * Sets up keyboard shortcuts specifically for the source code editor
     */
    private void setupSearchKeyboardShortcutsForEditor() {
        if (sourceCodeEditor == null || sourceCodeEditor.getCodeArea() == null) {
            return;
        }

        // Initialize search key event filter if not already done
        if (searchKeyEventFilter == null) {
            initializeSearchKeyEventFilter();
        }

        // Remove existing event filters to avoid duplicates
        sourceCodeEditor.getCodeArea().removeEventFilter(KeyEvent.KEY_PRESSED, searchKeyEventFilter);

        // Add the search key event filter
        sourceCodeEditor.getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, searchKeyEventFilter);

        logger.debug("Search keyboard shortcuts set up for source code editor");
    }

    /**
     * Lazy initialization for sample data CodeArea
     */
    private void ensureSampleDataTextAreaInitialized() {
        // Initialize code folding manager if not already done
        if (sampleDataCodeFoldingManager == null) {
            sampleDataCodeFoldingManager = new XmlCodeFoldingManager(sampleDataTextArea);
            logger.debug("Code folding manager initialized for sample data text area");
        }

        // Setup search keyboard shortcuts for sample data text area
        setupSampleDataSearchKeyboardShortcuts();
    }

    /**
     * Sets up keyboard shortcuts for search/replace in the sample data text area
     */
    private void setupSampleDataSearchKeyboardShortcuts() {
        if (sampleDataTextArea == null) {
            return;
        }

        // Initialize the search key event filter if not already done
        if (sampleDataSearchKeyEventFilter == null) {
            sampleDataSearchKeyEventFilter = event -> {
                if (event.isControlDown() || event.isMetaDown()) {
                    switch (event.getCode()) {
                        case F -> {
                            showSampleDataFindReplaceDialog();
                            event.consume();
                        }
                        case R -> {
                            showSampleDataFindReplaceDialog();
                            event.consume();
                        }
                    }
                }
            };
        }

        // Remove existing event filters to avoid duplicates
        sampleDataTextArea.removeEventFilter(KeyEvent.KEY_PRESSED, sampleDataSearchKeyEventFilter);

        // Add the search key event filter
        sampleDataTextArea.addEventFilter(KeyEvent.KEY_PRESSED, sampleDataSearchKeyEventFilter);

        logger.debug("Search keyboard shortcuts set up for sample data text area");
    }

    /**
     * Lazy initialization of FindReplaceDialog for sample data text area
     */
    private void initializeSampleDataFindReplaceDialog() {
        if (sampleDataFindReplaceDialog != null) {
            return; // Already initialized
        }

        try {
            if (sampleDataTextArea != null) {
                sampleDataFindReplaceDialog = new FindReplaceDialog(sampleDataTextArea);
                logger.debug("FindReplaceDialog initialized successfully for sample data text area");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize FindReplaceDialog for sample data text area", e);
        }
    }

    /**
     * Shows the FindReplaceDialog for the sample data text area with lazy initialization
     */
    private void showSampleDataFindReplaceDialog() {
        initializeSampleDataFindReplaceDialog(); // Lazy initialization
        if (sampleDataFindReplaceDialog != null) {
            sampleDataFindReplaceDialog.show();
            logger.debug("FindReplaceDialog shown for sample data text area");
        }
    }

    /**
     * Gets the code folding manager for the sample data text area
     * @return The XmlCodeFoldingManager instance, or null if not initialized
     */
    public XmlCodeFoldingManager getSampleDataCodeFoldingManager() {
        return sampleDataCodeFoldingManager;
    }

    /**
     * Folds all foldable regions in the sample data text area
     */
    public void foldAllSampleData() {
        if (sampleDataCodeFoldingManager != null) {
            sampleDataCodeFoldingManager.foldAll();
            logger.debug("Folded all regions in sample data text area");
        }
    }

    /**
     * Unfolds all folded regions in the sample data text area
     */
    public void unfoldAllSampleData() {
        if (sampleDataCodeFoldingManager != null) {
            sampleDataCodeFoldingManager.unfoldAll();
            logger.debug("Unfolded all regions in sample data text area");
        }
    }

    /**
     * Lazy initialization for flattened XSD CodeArea
     */
    private void ensureFlattenedXsdTextAreaInitialized() {
        if (flattenedXsdTextArea.getParagraphGraphicFactory() == null) {
            flattenedXsdTextArea.setParagraphGraphicFactory(LineNumberFactory.get(flattenedXsdTextArea));
        }
    }

    /**
     * Lazy initialization of FindReplaceDialog - only creates it when first needed
     */
    private void initializeFindReplaceDialog() {
        if (findReplaceDialog != null) {
            return; // Already initialized
        }

        try {
            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                findReplaceDialog = new FindReplaceDialog(sourceCodeEditor.getCodeArea());
                logger.debug("FindReplaceDialog initialized successfully for XSD editor");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize FindReplaceDialog for XSD editor", e);
        }
    }

    /**
     * Shows the FindReplaceDialog for the XSD editor with lazy initialization
     */
    private void showFindReplaceDialog() {
        initializeFindReplaceDialog(); // Lazy initialization
        if (findReplaceDialog != null) {
            findReplaceDialog.show();
            logger.debug("FindReplaceDialog shown for XSD editor");
        }
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
            // The central method `openXsdFile` is now responsible for everything.
            openXsdFile(selectedFile);
            return selectedFile;
        }
        return null;
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
        try {
            // Check for auto-save recovery first
            checkForAutoSaveRecovery(file);
            
            // The file must be set FIRST in the service,
            // so that all subsequent methods have the correct state.
            xmlService.setCurrentXsdFile(file);

            // Check if the file was successfully loaded
            if (xmlService.getCurrentXsdFile() == null) {
                // File loading failed - get detailed error message
                String errorMessage = xmlService.getLastXsdError();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "The XSD file could not be loaded. Please check if it's a valid XSD schema file.";
                }
                showXsdLoadingError(file, errorMessage);
                return;
            }

            // Call the central method in the MainController.
            // This not only adds the file to the list, but also updates the menu.
            if (parentController != null) {
                parentController.addFileToRecentFiles(file);
            } else {
                // Fallback in case the parent controller is not set for some reason.
                propertiesService.addLastOpenFile(file);
            }

            if (file.getParent() != null) {
                propertiesService.setLastOpenDirectory(file.getParent());
            }

            // Initialize auto-save for this file
            initializeAutoSave();
            
            // Populate the file path fields on other tabs to keep the UI in sync
            String absolutePath = file.getAbsolutePath();
            xsdFilePath.setText(absolutePath);
            xsdForSampleDataPath.setText(absolutePath);
            xsdToFlattenPath.setText(absolutePath); // Also set for the Flatten tab

            setupXsdDiagram();

        } catch (Exception e) {
            // Handle any unexpected exceptions
            logger.error("Unexpected error opening XSD file: {}", file.getAbsolutePath(), e);
            showXsdLoadingError(file, "An unexpected error occurred while loading the XSD file: " + e.getMessage());
        }
    }

    /**
     * Sets up the diagram view for the currently loaded XSD file.
     * This method creates a background task to parse the XSD file and build the tree. This is
     * executed to avoid blocking the user interface.
     */
    private void setupXsdDiagram() {
        currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null) {
            // Show the "No file loaded" placeholder
            noFileLoadedPane.setVisible(true);
            noFileLoadedPane.setManaged(true);
            xsdInfoPane.setVisible(false);
            xsdInfoPane.setManaged(false);
            return;
        }
        // Show the loading indicator and hide the placeholder
        xsdDiagramProgress.setVisible(true);
        textProgress.setVisible(true);
        noFileLoadedPane.setVisible(false);
        noFileLoadedPane.setManaged(false);
        noFileLoadedPaneText.setVisible(false);
        noFileLoadedPaneText.setManaged(false);
        xsdStackPane.getChildren().clear(); // Remove old view

        Task<DiagramData> task = new Task<>() {
            @Override
            protected DiagramData call() throws Exception {
                updateMessage("Parsing XSD and building diagram...");

                String fileContent = Files.readString(currentXsdFile.toPath());

                // 1. Use service for tree and documentation (new implementation)
                XsdViewService viewService = new XsdViewService();
                XsdNodeInfo rootNode = viewService.buildLightweightTree(fileContent);
                XsdViewService.DocumentationParts docParts = viewService.extractDocumentationParts(fileContent);

                // 2. Read metadata (targetNamespace, version) with JAXP/DOM
                String targetNamespace = "Not defined";
                String version = "Not specified";

                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    // Secure processing is important
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.setXIncludeAware(false);
                    factory.setExpandEntityReferences(false);

                    DocumentBuilder builder = factory.newDocumentBuilder();
                    // Important: Use InputSource with StringReader to parse from string
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
                    // Default values are retained
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
                diagramView.setXsdContent(result.fileContent());

                // Store the manipulator reference
                currentDomManipulator = diagramView.getDomManipulator();
                currentDiagramView = diagramView;
                
                logger.debug("lade diagramm...");
                xsdStackPane.getChildren().add(diagramView.build());

                // Show XSD info pane now that content is loaded
                xsdInfoPane.setVisible(true);
                xsdInfoPane.setManaged(true);

            } else {
                Label infoLabel = new Label("No root element found in schema.");
                xsdStackPane.getChildren().add(infoLabel);
            }
            xsdDiagramProgress.setVisible(false);

            // Update text tab UI
            textInfoPane.setVisible(true);
            textInfoPane.setManaged(true);
            textInfoPathLabel.setText(currentXsdFile.getAbsolutePath());

            ensureSourceCodeEditorInitialized();
            sourceCodeEditor.getCodeArea().replaceText(result.fileContent());
            sourceCodeEditor.setVisible(true);
            sourceCodeEditor.setManaged(true);

            textProgress.setVisible(false);

            statusText.setText("XSD loaded successfully.");
            applyEditorSettings();
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

        // Only append Javadoc part if it has content
        String fullDocumentation = javadoc.trim().isEmpty() ? mainDoc.trim() : mainDoc.trim() + "\n\n" + javadoc.trim();

        // Create task to save the file
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving documentation...");
                // Call service that does the actual work
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

    public void saveElementDocumentation(String xpath, String documentation, String javadoc) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            new Alert(Alert.AlertType.WARNING, "No XSD file loaded to save to.").showAndWait();
            return;
        }
        if (xpath == null || xpath.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "No element selected to save documentation for.").showAndWait();
            return;
        }

        Task<Void> saveDocTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving element documentation...");
                // Save documentation for the specific element
                xmlService.updateElementDocumentation(currentXsdFile, xpath, documentation, javadoc);
                return null;
            }
        };

        saveDocTask.setOnSucceeded(event -> {
            statusText.setText("Element documentation saved successfully.");
            // Reload the view to reflect the changes
            setupXsdDiagram();
        });

        saveDocTask.setOnFailed(event -> {
            logger.error("Failed to save element documentation", saveDocTask.getException());
            statusText.setText("Failed to save element documentation.");
        });

        executeTask(saveDocTask);
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
     * Programmatically selects the "Text" tab in the tab view.
     */
    public void selectTextTab() {
        logger.debug("select text tab");
        if (tabPane != null && textTab != null) {
            logger.debug("tabpane und texttab != null");
            tabPane.getSelectionModel().select(textTab);
        }
    }

    /**
     * Updates the XSD content after editing operations
     */
    public void updateXsdContent(String updatedXsd) {
        updateXsdContent(updatedXsd, true);
    }

    /**
     * Updates the XSD content with option to skip diagram rebuild
     */
    public void updateXsdContent(String updatedXsd, boolean rebuildDiagram) {
        if (updatedXsd != null && sourceCodeEditor != null) {
            // Update the text editor
            sourceCodeEditor.getCodeArea().replaceText(updatedXsd);

            // Rebuild the diagram view only if requested
            if (rebuildDiagram) {
                loadXsdIntoGraphicView(updatedXsd);
            }

            // Mark as modified
            hasUnsavedChanges = true;
            statusText.setText("XSD modified - changes not saved to file");

            // Enable save buttons
            if (saveXsdButton != null) {
                saveXsdButton.setDisable(false);
            }
            if (saveXsdButtonGraphic != null) {
                saveXsdButtonGraphic.setDisable(false);
            }

            // Always update the DOM manipulator with the latest content
            // This ensures that changes from graphic view are reflected in the manipulator
            try {
                if (currentDomManipulator == null) {
                    currentDomManipulator = new XsdDomManipulator();
                }
                currentDomManipulator.loadXsd(updatedXsd);
                logger.debug("Updated DOM manipulator with latest XSD content");
            } catch (Exception e) {
                logger.error("Failed to load XSD into manipulator", e);
            }
        }
    }

    /**
     * Analyzes XSD content and extracts structure and metadata
     */
    public DiagramData analyzeXsdContent(String xsdContent) throws Exception {
        // Use service for tree and documentation
        XsdViewService viewService = new XsdViewService();
        XsdNodeInfo rootNode = viewService.buildLightweightTree(xsdContent);
        XsdViewService.DocumentationParts docParts = viewService.extractDocumentationParts(xsdContent);

        // Read metadata (targetNamespace, version) with JAXP/DOM
        String targetNamespace = "Not defined";
        String version = "Not specified";

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xsdContent)));

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
        }

        return new DiagramData(rootNode, targetNamespace, version, docParts.mainDocumentation(), docParts.javadocContent(), xsdContent);
    }

    /**
     * Reloads the XSD diagram view with updated content
     */
    private void loadXsdIntoGraphicView(String xsdContent) {
        Task<DiagramData> task = new Task<>() {
            @Override
            protected DiagramData call() throws Exception {
                return analyzeXsdContent(xsdContent);
            }
        };

        task.setOnSucceeded(event -> {
            DiagramData result = task.getValue();
            xsdStackPane.getChildren().clear();

            if (result.rootNode() != null) {
                XsdDiagramView diagramView = new XsdDiagramView(result.rootNode(), this, result.documentation(), result.javadoc(), currentDomManipulator);
                diagramView.setXsdContent(xsdContent);

                // Keep the existing manipulator reference (don't overwrite it)
                if (currentDomManipulator == null) {
                    currentDomManipulator = diagramView.getDomManipulator();
                }
                currentDiagramView = diagramView;

                xsdStackPane.getChildren().add(diagramView.build());

                // Show XSD info pane now that content is loaded
                xsdInfoPane.setVisible(true);
                xsdInfoPane.setManaged(true);

            } else {
                Label infoLabel = new Label("No root element found in schema.");
                xsdStackPane.getChildren().add(infoLabel);
            }
        });

        task.setOnFailed(event -> {
            logger.error("Failed to reload XSD diagram", task.getException());
            statusText.setText("Error reloading XSD diagram");
        });

        executorService.submit(task);
    }

    /**
     * Loads XSD content into the V2 graphical view using the new model-based architecture.
     */
    private void loadXsdIntoGraphicViewV2(String xsdContent) {
        Task<org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> task = new Task<>() {
            @Override
            protected org.fxt.freexmltoolkit.controls.v2.model.XsdSchema call() throws Exception {
                // Use new XsdNodeFactory to parse the schema
                org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory factory =
                        new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory();
                return factory.fromString(xsdContent);
            }
        };

        task.setOnSucceeded(event -> {
            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = task.getValue();
            xsdStackPaneV2.getChildren().clear();

            if (schema != null) {
                // Use XsdSchema-based constructor
                currentGraphViewV2 = new org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView(schema);

                // Enable edit mode by creating and setting up an editor context
                org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext =
                        new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(schema);
                editorContext.setEditMode(true);
                currentGraphViewV2.setEditorContext(editorContext);

                // Set save callback for Save button in toolbar
                currentGraphViewV2.setOnSaveCallback(this::handleSaveV2Editor);

                xsdStackPaneV2.getChildren().add(currentGraphViewV2);

                logger.info("XSD loaded into V2 editor with edit mode enabled: {} global elements",
                        schema.getChildren().stream()
                                .filter(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement)
                                .count());
            } else {
                javafx.scene.control.Label errorLabel = new javafx.scene.control.Label("Failed to parse XSD schema");
                xsdStackPaneV2.getChildren().add(errorLabel);
            }
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load XSD into V2 editor", task.getException());
            javafx.scene.control.Label errorLabel = new javafx.scene.control.Label(
                    "Error loading XSD: " + task.getException().getMessage());
            xsdStackPaneV2.getChildren().clear();
            xsdStackPaneV2.getChildren().add(errorLabel);
        });

        executorService.submit(task);
    }

    // ======================================================================
    // Save XSD functionality
    // ======================================================================

    /**
     * Saves the modified XSD file
     */
    @FXML
    private void createNewXsdFile() {
        logger.info("Creating new XSD file");

        // Show dialog to get schema details
        Dialog<NewXsdResult> dialog = createNewXsdDialog();
        Optional<NewXsdResult> result = dialog.showAndWait();

        result.ifPresent(xsdInfo -> {
            try {
                // Generate XSD content based on user input
                String xsdContent = generateXsdTemplate(xsdInfo);

                // Load the new XSD content into the editor
                loadXsdContent(xsdContent);

                // Clear current file reference (this is a new unsaved file)
                currentXsdFile = null;

                // Show UI panels for loaded content (hide empty state panels)
                Platform.runLater(() -> {
                    if (noFileLoadedPaneText != null) {
                        noFileLoadedPaneText.setVisible(false);
                        noFileLoadedPaneText.setManaged(false);
                    }
                    if (noFileLoadedPane != null) {
                        noFileLoadedPane.setVisible(false);
                        noFileLoadedPane.setManaged(false);
                    }
                    if (textInfoPane != null) {
                        textInfoPane.setVisible(true);
                        textInfoPane.setManaged(true);
                    }
                    if (xsdInfoPane != null) {
                        xsdInfoPane.setVisible(true);
                        xsdInfoPane.setManaged(true);
                    }
                    // Enable unsaved changes tracking
                    hasUnsavedChanges = true;
                    if (saveXsdButton != null) {
                        saveXsdButton.setDisable(true); // Disable regular save (only Save As works for new files)
                    }
                    if (saveXsdButtonGraphic != null) {
                        saveXsdButtonGraphic.setDisable(true);
                    }
                    if (saveAsXsdButton != null) {
                        saveAsXsdButton.setDisable(false);
                    }
                    if (saveAsXsdButtonGraphic != null) {
                        saveAsXsdButtonGraphic.setDisable(false);
                    }
                });

                // Update UI to show it's a new file
                updateFileInfo("New XSD Schema (unsaved)", xsdInfo.targetNamespace(), "1.0");

                // Switch to Text tab to show the new content
                if (tabPane != null && textTab != null) {
                    tabPane.getSelectionModel().select(textTab);
                }

                logger.info("New XSD file created successfully");

            } catch (Exception e) {
                logger.error("Error creating new XSD file", e);
                showAlertDialog(Alert.AlertType.ERROR, "Error",
                        "Failed to create new XSD file:\n" + e.getMessage());
            }
        });
    }

    /**
     * Data transfer object for new XSD creation parameters
     */
    private record NewXsdResult(String schemaName, String targetNamespace, String rootElement, String template) {
    }

    /**
     * Creates a dialog for new XSD file creation
     */
    private Dialog<NewXsdResult> createNewXsdDialog() {
        Dialog<NewXsdResult> dialog = new Dialog<>();
        dialog.setTitle("Create New XSD Schema");
        dialog.setHeaderText("Configure your new XML Schema Definition");

        // Set icon
        dialog.setGraphic(new FontIcon("bi-file-plus"));

        // Create form fields
        TextField schemaNameField = new TextField();
        schemaNameField.setPromptText("e.g., MySchema");

        TextField namespaceField = new TextField();
        namespaceField.setPromptText("e.g., http://example.com/myschema");

        TextField rootElementField = new TextField();
        rootElementField.setPromptText("e.g., document, root, data");

        ComboBox<String> templateComboBox = new ComboBox<>();
        templateComboBox.getItems().addAll(
                "Basic Schema",
                "Document Structure",
                "Data Collection",
                "Configuration File",
                "Empty Schema"
        );
        templateComboBox.setValue("Basic Schema");

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Schema Name:"), 0, 0);
        grid.add(schemaNameField, 1, 0);

        grid.add(new Label("Target Namespace:"), 0, 1);
        grid.add(namespaceField, 1, 1);

        grid.add(new Label("Root Element:"), 0, 2);
        grid.add(rootElementField, 1, 2);

        grid.add(new Label("Template:"), 0, 3);
        grid.add(templateComboBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Add buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Disable Create button if required fields are empty
        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        // Validation
        Runnable validation = () -> {
            boolean valid = !schemaNameField.getText().trim().isEmpty() &&
                    !rootElementField.getText().trim().isEmpty();
            createButton.setDisable(!valid);
        };

        schemaNameField.textProperty().addListener((obs, oldVal, newVal) -> validation.run());
        rootElementField.textProperty().addListener((obs, oldVal, newVal) -> validation.run());

        // Set default values
        schemaNameField.setText("MySchema");
        rootElementField.setText("root");
        namespaceField.setText("http://example.com/myschema");

        // Focus on schema name field
        Platform.runLater(() -> schemaNameField.requestFocus());

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new NewXsdResult(
                        schemaNameField.getText().trim(),
                        namespaceField.getText().trim(),
                        rootElementField.getText().trim(),
                        templateComboBox.getValue()
                );
            }
            return null;
        });

        return dialog;
    }

    /**
     * Generates XSD template based on user selections
     */
    private String generateXsdTemplate(NewXsdResult xsdInfo) {
        StringBuilder xsd = new StringBuilder();

        // XML declaration and schema opening
        xsd.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xsd.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");

        if (!xsdInfo.targetNamespace().isEmpty()) {
            xsd.append("           targetNamespace=\"").append(xsdInfo.targetNamespace()).append("\"\n");
            xsd.append("           xmlns=\"").append(xsdInfo.targetNamespace()).append("\"\n");
        }

        xsd.append("           elementFormDefault=\"qualified\">\n\n");

        // Generate content based on template
        switch (xsdInfo.template()) {
            case "Basic Schema" -> generateBasicTemplate(xsd, xsdInfo);
            case "Document Structure" -> generateDocumentTemplate(xsd, xsdInfo);
            case "Data Collection" -> generateDataTemplate(xsd, xsdInfo);
            case "Configuration File" -> generateConfigTemplate(xsd, xsdInfo);
            case "Empty Schema" -> generateEmptyTemplate(xsd, xsdInfo);
            default -> generateBasicTemplate(xsd, xsdInfo);
        }

        xsd.append("</xs:schema>");

        return xsd.toString();
    }

    private void generateBasicTemplate(StringBuilder xsd, NewXsdResult xsdInfo) {
        xsd.append("    <!-- Root element -->\n");
        xsd.append("    <xs:element name=\"").append(xsdInfo.rootElement()).append("\" type=\"").append(xsdInfo.rootElement()).append("Type\"/>\n\n");

        xsd.append("    <!-- Root element type -->\n");
        xsd.append("    <xs:complexType name=\"").append(xsdInfo.rootElement()).append("Type\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"title\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"content\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"id\" type=\"xs:string\" use=\"optional\"/>\n");
        xsd.append("    </xs:complexType>\n\n");
    }

    private void generateDocumentTemplate(StringBuilder xsd, NewXsdResult xsdInfo) {
        xsd.append("    <!-- Root document element -->\n");
        xsd.append("    <xs:element name=\"").append(xsdInfo.rootElement()).append("\" type=\"DocumentType\"/>\n\n");

        xsd.append("    <!-- Document type with header, body, and footer -->\n");
        xsd.append("    <xs:complexType name=\"DocumentType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"header\" type=\"HeaderType\"/>\n");
        xsd.append("            <xs:element name=\"body\" type=\"BodyType\"/>\n");
        xsd.append("            <xs:element name=\"footer\" type=\"FooterType\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"version\" type=\"xs:string\" use=\"optional\"/>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"HeaderType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"title\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"subtitle\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"BodyType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"section\" type=\"SectionType\" maxOccurs=\"unbounded\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"SectionType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"heading\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"content\" type=\"xs:string\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"id\" type=\"xs:string\" use=\"optional\"/>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"FooterType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"copyright\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("            <xs:element name=\"contact\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");
    }

    private void generateDataTemplate(StringBuilder xsd, NewXsdResult xsdInfo) {
        xsd.append("    <!-- Root data collection element -->\n");
        xsd.append("    <xs:element name=\"").append(xsdInfo.rootElement()).append("\" type=\"DataCollectionType\"/>\n\n");

        xsd.append("    <xs:complexType name=\"DataCollectionType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"metadata\" type=\"MetadataType\" minOccurs=\"0\"/>\n");
        xsd.append("            <xs:element name=\"item\" type=\"ItemType\" maxOccurs=\"unbounded\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"version\" type=\"xs:string\" use=\"optional\"/>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"MetadataType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"created\" type=\"xs:dateTime\"/>\n");
        xsd.append("            <xs:element name=\"description\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"ItemType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"name\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"value\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"type\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>\n");
        xsd.append("    </xs:complexType>\n\n");
    }

    private void generateConfigTemplate(StringBuilder xsd, NewXsdResult xsdInfo) {
        xsd.append("    <!-- Configuration root element -->\n");
        xsd.append("    <xs:element name=\"").append(xsdInfo.rootElement()).append("\" type=\"ConfigurationType\"/>\n\n");

        xsd.append("    <xs:complexType name=\"ConfigurationType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"settings\" type=\"SettingsType\"/>\n");
        xsd.append("            <xs:element name=\"database\" type=\"DatabaseType\" minOccurs=\"0\"/>\n");
        xsd.append("            <xs:element name=\"logging\" type=\"LoggingType\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("        <xs:attribute name=\"environment\" type=\"xs:string\" use=\"optional\"/>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"SettingsType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"property\" type=\"PropertyType\" maxOccurs=\"unbounded\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"PropertyType\">\n");
        xsd.append("        <xs:simpleContent>\n");
        xsd.append("            <xs:extension base=\"xs:string\">\n");
        xsd.append("                <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>\n");
        xsd.append("                <xs:attribute name=\"type\" type=\"PropertyTypeEnum\" use=\"optional\" default=\"string\"/>\n");
        xsd.append("            </xs:extension>\n");
        xsd.append("        </xs:simpleContent>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:simpleType name=\"PropertyTypeEnum\">\n");
        xsd.append("        <xs:restriction base=\"xs:string\">\n");
        xsd.append("            <xs:enumeration value=\"string\"/>\n");
        xsd.append("            <xs:enumeration value=\"integer\"/>\n");
        xsd.append("            <xs:enumeration value=\"boolean\"/>\n");
        xsd.append("        </xs:restriction>\n");
        xsd.append("    </xs:simpleType>\n\n");

        xsd.append("    <xs:complexType name=\"DatabaseType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"host\" type=\"xs:string\"/>\n");
        xsd.append("            <xs:element name=\"port\" type=\"xs:int\"/>\n");
        xsd.append("            <xs:element name=\"database\" type=\"xs:string\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:complexType name=\"LoggingType\">\n");
        xsd.append("        <xs:sequence>\n");
        xsd.append("            <xs:element name=\"level\" type=\"LogLevelEnum\"/>\n");
        xsd.append("            <xs:element name=\"file\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        xsd.append("        </xs:sequence>\n");
        xsd.append("    </xs:complexType>\n\n");

        xsd.append("    <xs:simpleType name=\"LogLevelEnum\">\n");
        xsd.append("        <xs:restriction base=\"xs:string\">\n");
        xsd.append("            <xs:enumeration value=\"DEBUG\"/>\n");
        xsd.append("            <xs:enumeration value=\"INFO\"/>\n");
        xsd.append("            <xs:enumeration value=\"WARN\"/>\n");
        xsd.append("            <xs:enumeration value=\"ERROR\"/>\n");
        xsd.append("        </xs:restriction>\n");
        xsd.append("    </xs:simpleType>\n\n");
    }

    private void generateEmptyTemplate(StringBuilder xsd, NewXsdResult xsdInfo) {
        xsd.append("    <!-- Empty schema - add your elements here -->\n");
        xsd.append("    <xs:element name=\"").append(xsdInfo.rootElement()).append("\" type=\"xs:string\"/>\n\n");
    }

    /**
     * Sets up XSD diagram from content without requiring a physical file
     */
    private void setupXsdDiagramFromContent(String xsdContent) {
        logger.debug("Setting up XSD diagram from content");

        // Show the info pane instead of no file loaded pane
        noFileLoadedPane.setVisible(false);
        noFileLoadedPane.setManaged(false);
        xsdInfoPane.setVisible(true);
        xsdInfoPane.setManaged(true);

        // Show loading indicator
        xsdDiagramProgress.setVisible(true);
        xsdStackPane.getChildren().clear();

        Task<DiagramData> parseTask = new Task<>() {
            @Override
            protected DiagramData call() throws Exception {
                return analyzeXsdContent(xsdContent);
            }
        };

        parseTask.setOnSucceeded(e -> {
            DiagramData result = parseTask.getValue();

            // Update UI labels for new file
            xsdInfoPathLabel.setText("New XSD Schema");
            xsdInfoNamespaceLabel.setText(result.targetNamespace());
            xsdInfoVersionLabel.setText(result.version());

            if (result.rootNode() != null) {
                XsdDiagramView diagramView = new XsdDiagramView(result.rootNode(), this, result.documentation(), result.javadoc());
                diagramView.setXsdContent(xsdContent);

                // Store the manipulator reference
                currentDomManipulator = diagramView.getDomManipulator();
                currentDiagramView = diagramView;

                logger.debug("Loading diagram for new XSD...");
                xsdStackPane.getChildren().add(diagramView.build());
            } else {
                Label infoLabel = new Label("No root element found in schema.");
                infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");
                xsdStackPane.getChildren().add(infoLabel);
            }

            xsdDiagramProgress.setVisible(false);
        });

        parseTask.setOnFailed(e -> {
            logger.error("Failed to parse new XSD content", parseTask.getException());
            xsdDiagramProgress.setVisible(false);

            Label errorLabel = new Label("Failed to parse XSD content: " + parseTask.getException().getMessage());
            errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d32f2f;");
            xsdStackPane.getChildren().add(errorLabel);
        });

        Thread thread = new Thread(parseTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Updates file information in the UI
     */
    private void updateFileInfo(String filePath, String targetNamespace, String version) {
        Platform.runLater(() -> {
            if (textInfoPathLabel != null) {
                textInfoPathLabel.setText(filePath);
            }
            if (xsdInfoPathLabel != null) {
                xsdInfoPathLabel.setText(filePath);
            }
            if (xsdInfoNamespaceLabel != null) {
                xsdInfoNamespaceLabel.setText(targetNamespace != null && !targetNamespace.isEmpty()
                        ? targetNamespace : "No namespace");
            }
            if (xsdInfoVersionLabel != null) {
                xsdInfoVersionLabel.setText(version != null && !version.isEmpty() ? version : "1.0");
            }
        });
    }

    /**
     * Loads XSD content into the editor
     */
    private void loadXsdContent(String xsdContent) {
        if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
            sourceCodeEditor.getCodeArea().replaceText(xsdContent);
            sourceCodeEditor.setVisible(true);
            sourceCodeEditor.setManaged(true);
        }

        // Load into DOM manipulator for graphic view
        try {
            currentDomManipulator = new XsdDomManipulator();
            currentDomManipulator.loadXsd(xsdContent);

            logger.info("Loading XSD content into DOM manipulator");
            logger.info("XSD content length: {}", xsdContent.length());
            logger.info("XSD loaded successfully into DOM manipulator");


            // Update the graphic view immediately
            Platform.runLater(() -> {
                try {
                    setupXsdDiagramFromContent(xsdContent);
                    logger.info("Graphic view updated for new XSD content");

                } catch (Exception ex) {
                    logger.error("Error updating graphic view", ex);
                }
            });

        } catch (Exception e) {
            logger.error("Error loading XSD content into DOM manipulator", e);
            showAlertDialog(Alert.AlertType.ERROR, "Error",
                    "Failed to load XSD content:\n" + e.getMessage());
        }
    }

    @FXML
    private void saveXsdFile() {
        // Check if there are unsaved changes
        boolean hasChanges = hasUnsavedChanges;

        // Also check V2 editor dirty flag
        if (currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null) {
            hasChanges = hasChanges || currentGraphViewV2.getEditorContext().isDirty();
        }

        // Require either currentDomManipulator OR currentGraphViewV2
        boolean hasEditor = (currentDomManipulator != null) ||
                           (currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null);

        if (!hasChanges || !hasEditor || currentXsdFile == null) {
            logger.debug("Cannot save: hasChanges={}, hasEditor={}, currentXsdFile={}",
                        hasChanges, hasEditor, currentXsdFile);
            return;
        }

        saveXsdToFile(currentXsdFile);
    }

    /**
     * Save As functionality - prompts user to select a new file location
     */
    @FXML
    private void saveXsdFileAs() {
        // Check if we have any XSD content to save
        boolean hasContent = false;

        // Check V2 editor
        if (currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null &&
            currentGraphViewV2.getEditorContext().getSchema() != null) {
            hasContent = true;
        }

        // Check DOM manipulator
        if (!hasContent && currentDomManipulator != null) {
            hasContent = true;
        }

        // Check text editor
        if (!hasContent && sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
            String textContent = sourceCodeEditor.getCodeArea().getText();
            if (textContent != null && !textContent.trim().isEmpty()) {
                hasContent = true;
            }
        }

        if (!hasContent) {
            showError("No XSD Content", "There is no XSD content to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save XSD Schema As");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        // Set initial directory and filename
        if (currentXsdFile != null) {
            fileChooser.setInitialDirectory(currentXsdFile.getParentFile());
            fileChooser.setInitialFileName(currentXsdFile.getName());
        } else {
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }
            fileChooser.setInitialFileName("schema.xsd");
        }

        File selectedFile = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
        if (selectedFile != null) {
            saveXsdToFile(selectedFile);
            currentXsdFile = selectedFile;

            // Update recent files
            if (parentController != null) {
                parentController.addFileToRecentFiles(selectedFile);
            } else {
                propertiesService.addLastOpenFile(selectedFile);
            }

            if (selectedFile.getParent() != null) {
                propertiesService.setLastOpenDirectory(selectedFile.getParent());
            }
        }
    }

    /**
     * Common save logic for both Save and Save As
     */
    private void saveXsdToFile(File file) {
        try {
            // Priority 1: Use V2 editor if available (visual graph editor)
            if (currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null) {
                org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext =
                    currentGraphViewV2.getEditorContext();

                // Get the XSD schema model from editor context
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = editorContext.getSchema();

                if (schema != null) {
                    // Create and execute SaveCommand
                    boolean createBackup = propertiesService.isXsdBackupEnabled();
                    org.fxt.freexmltoolkit.controls.v2.editor.commands.SaveCommand saveCommand =
                        new org.fxt.freexmltoolkit.controls.v2.editor.commands.SaveCommand(
                            editorContext, schema, file.toPath(), createBackup);

                    boolean success = saveCommand.execute();

                    if (success) {
                        // Update UI - V2 editor already resets dirty flag via editorContext.resetDirty()
                        hasUnsavedChanges = false;
                        saveXsdButton.setDisable(true);
                        if (saveXsdButtonGraphic != null) {
                            saveXsdButtonGraphic.setDisable(true);
                        }
                        statusText.setText("XSD saved successfully: " + file.getName());

                        // Clean up auto-save file after successful save
                        cleanupAutoSave();

                        // Show success notification
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Save Successful");
                        alert.setHeaderText(null);
                        alert.setContentText("XSD file saved successfully!");
                        alert.showAndWait();

                        logger.info("Successfully saved XSD using V2 editor SaveCommand");
                        return;
                    } else {
                        showError("Save Failed", "Failed to save XSD file using V2 editor.");
                        return;
                    }
                } else {
                    logger.warn("V2 editor context has no schema model, falling back to text editor");
                }
            }

            // Priority 2: Use text editor if it has content
            // Create backup if enabled
            createBackupIfEnabled(file);

            // Get the XSD content - prioritize the text editor if it has content
            // This ensures that what the user sees in the text tab is what gets saved
            String updatedXsd = null;
            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                String textContent = sourceCodeEditor.getCodeArea().getText();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    updatedXsd = textContent;
                    logger.debug("Saving XSD from text editor (current view)");
                }
            }

            // Priority 3: Fallback to DOM manipulator only if text editor is empty
            if (updatedXsd == null && currentDomManipulator != null) {
                updatedXsd = currentDomManipulator.getXsdAsString();
                logger.debug("Saving XSD from DOM manipulator (fallback)");
            }

            if (updatedXsd == null || updatedXsd.trim().isEmpty()) {
                showError("Failed to get XSD content", "Could not retrieve the XSD content.");
                return;
            }

            // Pretty print if enabled
            if (propertiesService.isXsdPrettyPrintOnSave()) {
                updatedXsd = formatXsd(updatedXsd);
            }

            // Save to file
            Files.writeString(file.toPath(), updatedXsd, java.nio.charset.StandardCharsets.UTF_8);

            // Update UI
            hasUnsavedChanges = false;
            saveXsdButton.setDisable(true);
            if (saveXsdButtonGraphic != null) {
                saveXsdButtonGraphic.setDisable(true);
            }
            statusText.setText("XSD saved successfully: " + file.getName());

            // Clean up auto-save file after successful save
            cleanupAutoSave();

            // Show success notification
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Save Successful");
            alert.setHeaderText(null);
            alert.setContentText("XSD file saved successfully!");
            alert.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to save XSD file", e);
            showError("Save Failed", "Failed to save XSD file: " + e.getMessage());
        }
    }

    /**
     * Creates a backup of the XSD file before saving
     */
    private void createBackup(File file) throws IOException {
        if (file.exists()) {
            Path sourcePath = file.toPath();
            Path backupPath = sourcePath.resolveSibling(file.getName() + ".bak");
            Files.copy(sourcePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created: {}", backupPath);
        }
    }

    /**
     * Creates backup with versioning if enabled in settings
     */
    private void createBackupIfEnabled(File file) throws IOException {
        if (!file.exists() || !propertiesService.isXsdBackupEnabled()) {
            return;
        }

        int backupVersions = propertiesService.getXsdBackupVersions();
        if (backupVersions <= 0) {
            return;
        }

        Path sourcePath = file.toPath();
        String baseName = file.getName();

        // Rotate existing backups (e.g., .bak3 -> .bak4, .bak2 -> .bak3, etc.)
        for (int i = backupVersions - 1; i > 0; i--) {
            Path oldBackup = sourcePath.resolveSibling(baseName + ".bak" + i);
            Path newBackup = sourcePath.resolveSibling(baseName + ".bak" + (i + 1));
            if (Files.exists(oldBackup)) {
                if (i == backupVersions - 1) {
                    // Delete the oldest backup if we're at max versions
                    Files.deleteIfExists(newBackup);
                }
                Files.move(oldBackup, newBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Create the new backup as .bak1
        Path newBackupPath = sourcePath.resolveSibling(baseName + ".bak1");
        Files.copy(sourcePath, newBackupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        logger.info("Backup created: {}", newBackupPath);
    }

    /**
     * Pretty prints the current XSD content in the editor
     */
    @FXML
    private void prettyPrintXsd() {
        try {
            String xsdContent = null;

            // Get content from the appropriate source
            if (tabPane.getSelectionModel().getSelectedItem() == textTab && sourceCodeEditor != null) {
                xsdContent = sourceCodeEditor.getCodeArea().getText();
            } else if (currentDomManipulator != null) {
                xsdContent = currentDomManipulator.getXsdAsString();
            }

            if (xsdContent == null || xsdContent.trim().isEmpty()) {
                showError("No Content", "There is no XSD content to format.");
                return;
            }

            // Format the XSD
            String formattedXsd = formatXsd(xsdContent);

            // Update the appropriate editor
            if (tabPane.getSelectionModel().getSelectedItem() == textTab && sourceCodeEditor != null) {
                sourceCodeEditor.getCodeArea().replaceText(formattedXsd);
                hasUnsavedChanges = true;
                saveXsdButton.setDisable(false);
                if (saveXsdButtonGraphic != null) {
                    saveXsdButtonGraphic.setDisable(false);
                }
            } else {
                // If in diagram view, switch to text view and update
                tabPane.getSelectionModel().select(textTab);
                if (sourceCodeEditor != null) {
                    sourceCodeEditor.getCodeArea().replaceText(formattedXsd);
                    hasUnsavedChanges = true;
                    saveXsdButton.setDisable(false);
                    if (saveXsdButtonGraphic != null) {
                        saveXsdButtonGraphic.setDisable(false);
                    }
                }
            }

            statusText.setText("XSD formatted successfully");

        } catch (Exception e) {
            logger.error("Failed to format XSD", e);
            showError("Format Error", "Failed to format XSD: " + e.getMessage());
        }
    }

    /**
     * Formats XSD content with pretty printing using XML indentation settings
     */
    private String formatXsd(String xsdContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xsdContent)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            // Use indentation settings from properties
            int indentSpaces = propertiesService.getXmlIndentSpaces();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indentSpaces));

            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            logger.error("Failed to format XSD content", e);
            // Return original content if formatting fails
            return xsdContent;
        }
    }

    /**
     * Shows an error dialog
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ======================================================================
    // Auto-save functionality
    // ======================================================================

    /**
     * Initializes the auto-save timer if enabled in settings
     */
    private void initializeAutoSave() {
        stopAutoSave(); // Stop any existing timer

        if (!propertiesService.isXsdAutoSaveEnabled()) {
            return;
        }

        int intervalMinutes = propertiesService.getXsdAutoSaveInterval();
        if (intervalMinutes <= 0) {
            return;
        }

        autoSaveTimer = new Timer("XSD-AutoSave", true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> performAutoSave());
            }
        }, intervalMinutes * 60 * 1000L, intervalMinutes * 60 * 1000L);

        logger.info("Auto-save initialized with interval: {} minutes", intervalMinutes);
    }

    /**
     * Performs auto-save of the current XSD file
     */
    private void performAutoSave() {
        if (!hasUnsavedChanges || currentXsdFile == null) {
            return;
        }

        try {
            // Get the XSD content
            String xsdContent;
            if (currentDomManipulator != null) {
                xsdContent = currentDomManipulator.getXsdAsString();
            } else if (sourceCodeEditor != null) {
                xsdContent = sourceCodeEditor.getCodeArea().getText();
            } else {
                return;
            }

            if (xsdContent == null || xsdContent.isEmpty()) {
                return;
            }

            // Create auto-save file
            Path autoSavePath = currentXsdFile.toPath().resolveSibling(
                    AUTO_SAVE_PREFIX + currentXsdFile.getName()
            );

            // Pretty print if enabled
            if (propertiesService.isXsdPrettyPrintOnSave()) {
                xsdContent = formatXsd(xsdContent);
            }

            Files.writeString(autoSavePath, xsdContent, java.nio.charset.StandardCharsets.UTF_8);
            logger.info("Auto-saved XSD to: {}", autoSavePath);

            // Update status
            Platform.runLater(() -> {
                statusText.setText("Auto-saved at " + LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                ));
            });

        } catch (Exception e) {
            logger.error("Auto-save failed", e);
        }
    }

    /**
     * Stops the auto-save timer
     */
    private void stopAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
        }
    }

    /**
     * Recovers from auto-save file if it exists
     */
    private void checkForAutoSaveRecovery(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        Path autoSavePath = file.toPath().resolveSibling(AUTO_SAVE_PREFIX + file.getName());
        if (!Files.exists(autoSavePath)) {
            return;
        }

        try {
            // Check if auto-save is newer than the actual file
            long fileTime = Files.getLastModifiedTime(file.toPath()).toMillis();
            long autoSaveTime = Files.getLastModifiedTime(autoSavePath).toMillis();

            if (autoSaveTime > fileTime) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Auto-Save Recovery");
                alert.setHeaderText("An auto-saved version of this file was found.");
                alert.setContentText("The auto-saved version is newer than the file. Do you want to recover from the auto-save?");

                ButtonType recoverButton = new ButtonType("Recover");
                ButtonType discardButton = new ButtonType("Discard Auto-Save");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(recoverButton, discardButton, cancelButton);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == recoverButton) {
                        // Load auto-save content
                        String autoSaveContent = Files.readString(autoSavePath);
                        loadXsdContent(autoSaveContent);
                        hasUnsavedChanges = true;
                        logger.info("Recovered from auto-save: {}", autoSavePath);
                    } else if (result.get() == discardButton) {
                        // Delete auto-save file
                        Files.deleteIfExists(autoSavePath);
                        logger.info("Discarded auto-save: {}", autoSavePath);
                    }
                }
            } else {
                // Auto-save is older, delete it
                Files.deleteIfExists(autoSavePath);
            }
        } catch (Exception e) {
            logger.error("Failed to check auto-save recovery", e);
        }
    }

    /**
     * Cleans up auto-save file after successful save
     */
    private void cleanupAutoSave() {
        if (currentXsdFile == null) {
            return;
        }

        try {
            Path autoSavePath = currentXsdFile.toPath().resolveSibling(
                    AUTO_SAVE_PREFIX + currentXsdFile.getName()
            );
            Files.deleteIfExists(autoSavePath);
        } catch (Exception e) {
            logger.warn("Failed to cleanup auto-save file", e);
        }
    }

    /**
     * Prompts to save unsaved changes
     */
    public boolean promptSaveIfNeeded() {
        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes in the XSD editor.");
            alert.setContentText("Do you want to save your changes?");

            ButtonType saveButton = new ButtonType("Save");
            ButtonType discardButton = new ButtonType("Discard");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveButton) {
                    saveXsdFile();
                    return true;
                } else if (result.get() == discardButton) {
                    hasUnsavedChanges = false;
                    return true;
                } else {
                    return false; // Cancel
                }
            }
        }
        return true;
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
                docService.setIncludeTypeDefinitionsInSourceCode(includeTypeDefinitionsInSourceCode.isSelected());

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
            startDocServerAndShowPreview(outputDir);
        }
    }

    /**
     * Startet den eingebetteten Webserver und lädt die Vorschau in die WebView.
     *
     * @param outputDir Das Stammverzeichnis für den Webserver.
     */
    private void startDocServerAndShowPreview(File outputDir) {
        stopDocServer(); // Zuerst einen eventuell laufenden alten Server stoppen

        Path docRootPath = outputDir.toPath().toAbsolutePath().normalize();
        docServer = SimpleFileServer.createFileServer(
                new InetSocketAddress(DOC_SERVER_PORT),
                docRootPath,
                SimpleFileServer.OutputLevel.INFO);
        docServer.start();
        logger.info("Documentation server started on http://localhost:{}", DOC_SERVER_PORT);

        String url = "http://localhost:" + DOC_SERVER_PORT + "/index.html";
        Platform.runLater(() -> {
            docWebView.getEngine().load(url);
            docPreviewTab.setDisable(false);
            tabPane.getSelectionModel().select(docPreviewTab);
        });
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

            ensureSampleDataTextAreaInitialized();
            sampleDataTextArea.replaceText(resultXml);
            sampleDataTextArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(resultXml));
            applyEditorSettings();

            // Update code folding regions after text is set
            if (sampleDataCodeFoldingManager != null) {
                sampleDataCodeFoldingManager.updateFoldingRegions(resultXml);
                logger.debug("Updated code folding regions for sample data");
            }

            // Validate the generated XML against the XSD schema
            Task<XsdDocumentationService.ValidationResult> validationTask = new Task<>() {
                @Override
                protected XsdDocumentationService.ValidationResult call() throws Exception {
                    XsdDocumentationService docService = new XsdDocumentationService();
                    docService.setXsdFilePath(xsdFile.getAbsolutePath());
                    return docService.validateXmlAgainstSchema(resultXml);
                }
            };

            validationTask.setOnSucceeded(validationEvent -> {
                XsdDocumentationService.ValidationResult result = validationTask.getValue();
                if (result.isValid()) {
                    statusText.setText("Sample XML generated and validated successfully.");
                    if (!result.message().isEmpty()) {
                        logger.info("Validation warnings: " + result.message());
                    }
                } else {
                    statusText.setText("Sample XML generated but validation failed.");
                    logger.warn("XML validation failed: " + result.message());
                    // Show validation error in a dialog
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("XML Validation Warning");
                        alert.setHeaderText("Generated XML does not fully conform to the XSD schema");
                        alert.setContentText("The generated XML may have validation issues:\n\n" + result.message());
                        alert.showAndWait();
                    });
                }
            });

            validationTask.setOnFailed(validationEvent -> {
                logger.error("Validation task failed", validationTask.getException());
                statusText.setText("Sample XML generated but validation could not be performed.");
            });

            executorService.submit(validationTask);

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

            // CHANGED: Apply highlighting for the flattenedXsdTextArea
            ensureFlattenedXsdTextAreaInitialized();
            flattenedXsdTextArea.replaceText(flattenedContent);
            flattenedXsdTextArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(flattenedContent));
            applyEditorSettings();

            flattenStatusLabel.setText("Successfully flattened and saved to: " + destinationFile.getAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Success", "XSD has been flattened successfully.");
        });

        flattenTask.setOnFailed(event -> {
            flattenProgress.setVisible(false);
            flattenStatusLabel.setText("Error during flattening process.");
            Throwable ex = flattenTask.getException();
            logger.error("Flattening failed", ex);
            showAlert(Alert.AlertType.ERROR, "Flattening Failed", "An error occurred: " + ex.getMessage());
        });

        executeTask(flattenTask);
    }

    private String getFlattenedPath(String originalPath) {
        if (originalPath == null || !originalPath.toLowerCase().endsWith(".xsd")) {
            return "";
        }
        return originalPath.substring(0, originalPath.length() - 4) + "_flattened.xsd";
    }

    // Make sure this method already exists or add it
    private File showSaveDialog(String title, String description, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
        return fileChooser.showSaveDialog(tabPane.getScene().getWindow());
    }

    // In XsdController.java hinzufügen

    /**
     * Shows a standard JavaFX alert dialog window.
     *
     * @param alertType The type of alert (e.g., INFORMATION, ERROR, WARNING).
     * @param title     The title of the dialog window.
     * @param content   The message to be displayed to the user.
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null); // We don't use header text for a simpler appearance
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Shuts down the internal ExecutorService to ensure that all
     * background threads are cleanly terminated when the application is closed.
     */
    public void shutdown() {
        stopDocServer();

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

    /**
     * Stops the running documentation web server, if present.
     */
    private void stopDocServer() {
        if (docServer != null) {
            docServer.stop(0); // 0 seconds delay when stopping
            docServer = null;
            logger.info("Documentation server stopped.");
        }
    }

    // ======================================================================
    // Favorites functionality for XSD files
    // ======================================================================

    @FXML
    private void addCurrentXsdToFavorites() {
        logger.info("Adding current XSD file to favorites");

        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            showAlertDialog(Alert.AlertType.WARNING, "Warning", "No XSD file is currently loaded or file does not exist.");
            return;
        }

        // Check if already in favorites
        if (favoritesService.isFavorite(currentXsdFile.getAbsolutePath())) {
            showAlertDialog(Alert.AlertType.INFORMATION, "Information", "This XSD file is already in your favorites.");
            return;
        }

        // Show dialog to add to favorites
        showAddXsdToFavoritesDialog(currentXsdFile);
    }

    private void showAddXsdToFavoritesDialog(File file) {
        Dialog<org.fxt.freexmltoolkit.domain.FileFavorite> dialog = new Dialog<>();
        dialog.setTitle("Add XSD to Favorites");
        dialog.setHeaderText("Add \"" + file.getName() + "\" to favorites");

        // Set the button types
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(file.getName().replaceFirst("[.][^.]+$", ""));
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setEditable(true);
        categoryCombo.getItems().addAll(favoritesService.getAllFolders());
        categoryCombo.setValue("XSD Schemas");
        TextField descriptionField = new TextField();

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descriptionField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Enable/disable add button depending on whether name is entered
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(nameField.getText().trim().isEmpty());
        nameField.textProperty().addListener((observable, oldValue, newValue) ->
                addButton.setDisable(newValue.trim().isEmpty()));

        Platform.runLater(() -> nameField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    org.fxt.freexmltoolkit.domain.FileFavorite favorite = new org.fxt.freexmltoolkit.domain.FileFavorite(
                            nameField.getText().trim(),
                            file.getAbsolutePath(),
                            categoryCombo.getValue() != null ? categoryCombo.getValue().trim() : "XSD Schemas"
                    );
                    if (!descriptionField.getText().trim().isEmpty()) {
                        favorite.setDescription(descriptionField.getText().trim());
                    }
                    return favorite;
                } catch (Exception e) {
                    logger.error("Error creating XSD favorite", e);
                    return null;
                }
            }
            return null;
        });

        Optional<org.fxt.freexmltoolkit.domain.FileFavorite> result = dialog.showAndWait();
        result.ifPresent(favorite -> {
            favoritesService.addFavorite(favorite);
            refreshXsdFavoritesMenu();
            showAlertDialog(Alert.AlertType.INFORMATION, "Success", "XSD file added to favorites successfully!");
            logger.info("Added {} to favorites in category {}", favorite.getName(), favorite.getFolderName());
        });
    }

    private void refreshXsdFavoritesMenu() {
        refreshXsdFavoritesMenuForButton(loadXsdFavoritesButton);
        refreshXsdFavoritesMenuForButton(loadXsdFavoritesButtonGraphic);
        // Also refresh the overview favorites list
        Platform.runLater(this::populateRecentFavoritesList);
    }

    private void refreshXsdFavoritesMenuForButton(MenuButton menuButton) {
        if (menuButton == null) return;

        menuButton.getItems().clear();

        // Get XSD favorites
        List<org.fxt.freexmltoolkit.domain.FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSD);

        if (xsdFavorites.isEmpty()) {
            MenuItem noFavoritesItem = new MenuItem("No XSD favorites yet");
            noFavoritesItem.setDisable(true);
            menuButton.getItems().add(noFavoritesItem);
            return;
        }

        // Organize by categories
        java.util.Map<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> favoritesByCategory =
                xsdFavorites.stream().collect(java.util.stream.Collectors.groupingBy(
                        f -> f.getFolderName() != null ? f.getFolderName() : "Uncategorized"));

        for (java.util.Map.Entry<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> entry : favoritesByCategory.entrySet()) {
            String category = entry.getKey();
            List<org.fxt.freexmltoolkit.domain.FileFavorite> favoritesInCategory = entry.getValue();

            if (favoritesByCategory.size() > 1) {
                // Add category submenu if multiple categories
                Menu categoryMenu = new Menu(category);
                categoryMenu.getStyleClass().add("favorites-category");

                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createXsdFavoriteMenuItem(favorite);
                    categoryMenu.getItems().add(favoriteItem);
                }

                menuButton.getItems().add(categoryMenu);
            } else {
                // If only one category, add items directly
                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createXsdFavoriteMenuItem(favorite);
                    menuButton.getItems().add(favoriteItem);
                }
            }
        }

        // Add separator and management options
        menuButton.getItems().add(new SeparatorMenuItem());

        MenuItem manageFavoritesItem = new MenuItem("Manage Favorites...");
        manageFavoritesItem.setOnAction(e -> showXsdFavoritesManagement());
        menuButton.getItems().add(manageFavoritesItem);
    }

    private MenuItem createXsdFavoriteMenuItem(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        MenuItem item = new MenuItem(favorite.getName());

        // Set XSD icon
        FontIcon icon = new FontIcon(favorite.getFileType().getIconLiteral());
        icon.setIconColor(javafx.scene.paint.Color.web(favorite.getFileType().getDefaultColor()));
        icon.setIconSize(14);
        item.setGraphic(icon);

        // Add tooltip with file path and description
        String tooltipText = favorite.getFilePath();
        if (favorite.getDescription() != null && !favorite.getDescription().trim().isEmpty()) {
            tooltipText += "\n" + favorite.getDescription();
        }
        Tooltip.install(item.getGraphic(), new Tooltip(tooltipText));

        // Set action to load the XSD file
        item.setOnAction(e -> loadXsdFavoriteFile(favorite));

        return item;
    }

    private void loadXsdFavoriteFile(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        File file = new File(favorite.getFilePath());

        if (!file.exists()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("File Not Found");
            alert.setHeaderText("Cannot open favorite XSD file");
            alert.setContentText("The file \"" + file.getName() + "\" no longer exists at:\n" + favorite.getFilePath());

            // Offer to remove from favorites
            ButtonType removeButton = new ButtonType("Remove from Favorites");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(removeButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == removeButton) {
                favoritesService.removeFavorite(favorite.getId());
                refreshXsdFavoritesMenu();
            }
            return;
        }

        try {
            // Load the XSD file
            openXsdFile(file);

            // Update last accessed time
            favorite.setLastAccessed(LocalDateTime.now());
            favorite.setAccessCount(favorite.getAccessCount() + 1);
            favoritesService.updateFavorite(favorite);

            logger.info("Loaded favorite XSD file: {}", favorite.getName());
        } catch (Exception e) {
            logger.error("Error loading favorite XSD file: {}", favorite.getName(), e);
            showAlertDialog(Alert.AlertType.ERROR, "Error", "Failed to load favorite XSD file:\n" + e.getMessage());
        }
    }

    private void showXsdFavoritesManagement() {
        // This will be implemented when we create the settings UI
        showAlertDialog(Alert.AlertType.INFORMATION, "Coming Soon", "Favorites management will be available in Settings.");
    }

    @FXML
    private void toggleFavoritesPanel() {
        if (favoritesPanel == null || textTabSplitPane == null) {
            return;
        }

        boolean isSelected = toggleFavoritesButton != null && toggleFavoritesButton.isSelected();

        // Toggle visibility of the favorites panel
        favoritesPanel.setVisible(isSelected);
        favoritesPanel.setManaged(isSelected);

        if (isSelected) {
            // Show the panel with proper divider position
            if (!textTabSplitPane.getItems().contains(favoritesPanel)) {
                textTabSplitPane.getItems().add(favoritesPanel);
            }
            textTabSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            textTabSplitPane.getItems().remove(favoritesPanel);
        }

        logger.info("Favorites panel toggled: {}", isSelected ? "shown" : "hidden");
    }

    @FXML
    private void toggleFavoritesPanelGraphic() {
        if (favoritesPanelGraphic == null || graphicTabSplitPane == null) {
            return;
        }

        boolean isSelected = toggleFavoritesButtonGraphic != null && toggleFavoritesButtonGraphic.isSelected();

        // Toggle visibility of the favorites panel
        favoritesPanelGraphic.setVisible(isSelected);
        favoritesPanelGraphic.setManaged(isSelected);

        if (isSelected) {
            // Show the panel with proper divider position
            if (!graphicTabSplitPane.getItems().contains(favoritesPanelGraphic)) {
                graphicTabSplitPane.getItems().add(favoritesPanelGraphic);
            }
            graphicTabSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            graphicTabSplitPane.getItems().remove(favoritesPanelGraphic);
        }

        logger.info("Graphic tab favorites panel toggled: {}", isSelected ? "shown" : "hidden");
    }

    // ======================================================================
    // Overview Tab Favorites Functionality
    // ======================================================================

    private void setupOverviewFavorites() {
        // Setup the overview favorites button
        if (loadXsdFavoritesOverview != null) {
            loadXsdFavoritesOverview.setOnAction(e -> showOverviewFavoritesMenu());
        }

        // Populate the recent favorites list
        populateRecentFavoritesList();

        // Setup double-click handler for recent favorites list
        if (recentFavoritesList != null) {
            recentFavoritesList.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selectedItem = recentFavoritesList.getSelectionModel().getSelectedItem();
                    if (selectedItem != null) {
                        loadFavoriteFromListItem(selectedItem);
                    }
                }
            });
        }
    }

    private void showOverviewFavoritesMenu() {
        // Get XSD favorites
        List<org.fxt.freexmltoolkit.domain.FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSD);

        if (xsdFavorites.isEmpty()) {
            showAlertDialog(Alert.AlertType.INFORMATION, "No Favorites",
                    "You haven't added any XSD files to your favorites yet.\n\nUse the '★ Add' button in any tab to add files to favorites.");
            return;
        }

        // Create a context menu to show favorites
        ContextMenu favoritesMenu = new ContextMenu();

        // Organize by categories
        java.util.Map<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> favoritesByCategory =
                xsdFavorites.stream().collect(java.util.stream.Collectors.groupingBy(
                        org.fxt.freexmltoolkit.domain.FileFavorite::getFolderName));

        for (java.util.Map.Entry<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> entry : favoritesByCategory.entrySet()) {
            String categoryName = entry.getKey();
            List<org.fxt.freexmltoolkit.domain.FileFavorite> favoritesInCategory = entry.getValue();

            if (favoritesByCategory.size() > 1) {
                // If more than one category, create sub-menus
                Menu categoryMenu = new Menu(categoryName);
                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createOverviewFavoriteMenuItem(favorite);
                    categoryMenu.getItems().add(favoriteItem);
                }
                favoritesMenu.getItems().add(categoryMenu);
            } else {
                // If only one category, add items directly
                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createOverviewFavoriteMenuItem(favorite);
                    favoritesMenu.getItems().add(favoriteItem);
                }
            }
        }

        // Show the context menu
        favoritesMenu.show(loadXsdFavoritesOverview, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private MenuItem createOverviewFavoriteMenuItem(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        MenuItem item = new MenuItem(favorite.getName());
        item.setGraphic(new FontIcon("bi-file-earmark-code"));

        // Add file path as tooltip if it's different from the name
        if (!favorite.getName().equals(favorite.getFilePath())) {
            Tooltip tooltip = new Tooltip(favorite.getFilePath());
            Tooltip.install(item.getGraphic(), tooltip);
        }

        item.setOnAction(e -> loadXsdFavoriteFromOverview(favorite));
        return item;
    }

    private void populateRecentFavoritesList() {
        if (recentFavoritesList == null) return;

        List<org.fxt.freexmltoolkit.domain.FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSD);

        // Sort by last accessed time (most recent first) and take up to 5
        // Handle null lastAccessed values safely
        List<String> recentItems = xsdFavorites.stream()
                .sorted((f1, f2) -> {
                    if (f1.getLastAccessed() == null && f2.getLastAccessed() == null) return 0;
                    if (f1.getLastAccessed() == null) return 1;  // f1 goes to end
                    if (f2.getLastAccessed() == null) return -1; // f2 goes to end
                    return f2.getLastAccessed().compareTo(f1.getLastAccessed());
                })
                .limit(5)
                .map(favorite -> "📄 " + favorite.getName())
                .toList();

        recentFavoritesList.setItems(FXCollections.observableArrayList(recentItems));

        // Hide the container if no favorites exist
        if (recentFavoritesContainer != null) {
            recentFavoritesContainer.setVisible(!recentItems.isEmpty());
            recentFavoritesContainer.setManaged(!recentItems.isEmpty());
        }
    }

    private void loadFavoriteFromListItem(String listItem) {
        // Extract the name from the list item (remove the emoji prefix)
        String favoriteName = listItem.replace("📄 ", "");

        // Find the favorite by name
        List<org.fxt.freexmltoolkit.domain.FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSD);

        Optional<org.fxt.freexmltoolkit.domain.FileFavorite> favorite = xsdFavorites.stream()
                .filter(f -> f.getName().equals(favoriteName))
                .findFirst();

        if (favorite.isPresent()) {
            loadXsdFavoriteFromOverview(favorite.get());
        }
    }

    private void loadXsdFavoriteFromOverview(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        File file = new File(favorite.getFilePath());

        if (!file.exists() || !file.canRead()) {
            showAlertDialog(Alert.AlertType.ERROR, "File Not Found",
                    "The favorite file could not be found or is not readable:\n" + favorite.getFilePath() +
                            "\n\nThe file may have been moved or deleted.");

            // Optionally remove the favorite if file doesn't exist
            Optional<ButtonType> result = showConfirmationDialog(
                    "Remove Favorite?",
                    "Would you like to remove this favorite since the file no longer exists?");

            if (result.isPresent() && result.get() == ButtonType.OK) {
                favoritesService.removeFavorite(favorite.getId());
                refreshXsdFavoritesMenu();
                populateRecentFavoritesList();
            }
            return;
        }

        try {
            // Load the XSD file
            openXsdFile(file);

            // Update last accessed time
            favorite.setLastAccessed(LocalDateTime.now());
            favorite.setAccessCount(favorite.getAccessCount() + 1);
            favoritesService.updateFavorite(favorite);

            // Refresh the recent favorites list
            populateRecentFavoritesList();

            // Switch to Text tab to show the loaded content
            if (tabPane != null && textTab != null) {
                tabPane.getSelectionModel().select(textTab);
            }

            logger.info("Loaded favorite XSD file from overview: {}", favorite.getName());

        } catch (Exception e) {
            logger.error("Error loading favorite XSD file from overview: {}", favorite.getName(), e);
            showAlertDialog(Alert.AlertType.ERROR, "Error", "Failed to load favorite XSD file:\n" + e.getMessage());
        }
    }

    private Optional<ButtonType> showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait();
    }

    private void showAlertDialog(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Sets up Ctrl+S keyboard shortcut for saving in the V2 editor.
     * The shortcut is attached to the xsdTab and works when the tab is active.
     */
    private void setupV2EditorSaveShortcut() {
        if (xsdTab == null) {
            logger.warn("xsdTab is null, cannot setup V2 editor save shortcut");
            return;
        }

        // Add keyboard event filter to the xsdTab
        xsdTab.setOnSelectionChanged(event -> {
            if (xsdTab.isSelected() && xsdStackPaneV2 != null) {
                // Add event filter when tab is selected
                xsdStackPaneV2.addEventFilter(KeyEvent.KEY_PRESSED, this::handleV2EditorKeyPress);
            }
        });

        logger.debug("V2 editor save shortcut (Ctrl+S) has been configured");
    }

    /**
     * Handles keyboard events for the V2 editor, specifically Ctrl+S for save.
     */
    private void handleV2EditorKeyPress(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.S) {
            event.consume();
            handleSaveV2Editor();
        }
    }

    /**
     * Handles saving the V2 editor XSD schema to file.
     * Uses the SaveCommand to serialize the XsdSchema model to XSD XML.
     */
    private void handleSaveV2Editor() {
        if (currentGraphViewV2 == null) {
            logger.warn("No V2 editor active");
            showAlertDialog(Alert.AlertType.WARNING, "No Editor Active",
                    "The XSD V2 editor is not currently active.");
            return;
        }

        if (currentXsdFile == null) {
            logger.warn("No file to save");
            showAlertDialog(Alert.AlertType.WARNING, "No File Open",
                    "No XSD file is currently open. Please open a file first.");
            return;
        }

        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = currentGraphViewV2.getXsdSchema();
        org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext context = currentGraphViewV2.getEditorContext();

        if (schema == null || context == null) {
            logger.error("Schema or context is null - cannot save");
            showAlertDialog(Alert.AlertType.ERROR, "Save Failed",
                    "Cannot access schema or editor context. The editor may not be properly initialized.");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            long fileSizeBefore = currentXsdFile.exists() ? currentXsdFile.length() : 0;

            // Create and execute save command
            org.fxt.freexmltoolkit.controls.v2.editor.commands.SaveCommand saveCmd =
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.SaveCommand(
                            context, schema, currentXsdFile.toPath(), true);

            if (saveCmd.execute()) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                long fileSizeAfter = currentXsdFile.length();

                // Get backup file path from SaveCommand
                java.nio.file.Path backupPath = saveCmd.getBackupPath();

                logger.info("XSD file saved successfully: {}", currentXsdFile.getAbsolutePath());

                // Build detailed success message
                StringBuilder message = new StringBuilder();
                message.append("✓ File saved successfully\n\n");
                message.append("File: ").append(currentXsdFile.getName()).append("\n");
                message.append("Path: ").append(currentXsdFile.getAbsolutePath()).append("\n");
                message.append("Size: ").append(formatFileSize(fileSizeAfter));

                if (fileSizeBefore > 0) {
                    long sizeDiff = fileSizeAfter - fileSizeBefore;
                    if (sizeDiff != 0) {
                        message.append(" (").append(sizeDiff > 0 ? "+" : "")
                                .append(formatFileSize(Math.abs(sizeDiff))).append(")");
                    }
                }
                message.append("\n");

                message.append("Duration: ").append(duration).append(" ms\n");

                if (backupPath != null && java.nio.file.Files.exists(backupPath)) {
                    message.append("Backup: ").append(backupPath.getFileName()).append("\n");
                    message.append("Backup size: ").append(formatFileSize(java.nio.file.Files.size(backupPath))).append("\n");
                }

                message.append("\nTimestamp: ").append(java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                final String detailedMessage = message.toString();

                Platform.runLater(() -> {
                    showDetailedAlertDialog(Alert.AlertType.INFORMATION, "Save Successful", detailedMessage);
                });
            } else {
                logger.error("Save command execution failed");
                Platform.runLater(() -> {
                    String message = "✗ Save operation failed\n\n" +
                            "File: " + currentXsdFile.getName() + "\n" +
                            "Path: " + currentXsdFile.getAbsolutePath() + "\n\n" +
                            "The save command returned false. Check the log for details.";

                    showDetailedAlertDialog(Alert.AlertType.ERROR, "Save Failed", message);
                });
            }
        } catch (Exception e) {
            logger.error("Error saving XSD file", e);
            Platform.runLater(() -> {
                String message = "✗ An error occurred during save\n\n" +
                        "File: " + currentXsdFile.getName() + "\n" +
                        "Error: " + e.getClass().getSimpleName() + "\n" +
                        "Message: " + e.getMessage() + "\n\n" +
                        "Timestamp: " + LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                showDetailedAlertDialog(Alert.AlertType.ERROR, "Save Failed", message);
            });
        }
    }

    /**
     * Formats a file size in bytes to a human-readable format.
     *
     * @param bytes the file size in bytes
     * @return formatted string (e.g., "1.5 KB", "2.3 MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Shows a detailed alert dialog with formatted message.
     * The dialog is wider to accommodate detailed information.
     */
    private void showDetailedAlertDialog(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Make dialog wider to accommodate detailed information
        alert.getDialogPane().setMinWidth(600);
        alert.getDialogPane().setPrefWidth(600);

        // Use monospace font for better alignment of details
        alert.getDialogPane().setStyle("-fx-font-family: 'monospace';");

        alert.showAndWait();
    }

    /**
     * Shows an error dialog when XSD file loading fails.
     */
    private void showXsdLoadingError(File file, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("XSD Loading Error");
        alert.setHeaderText("Failed to load XSD file: " + file.getName());

        // Create detailed error message
        String detailedMessage = "Error details:\n" + message + "\n\nFile path: " + file.getAbsolutePath();

        // For long error messages, use expandable content
        if (message.length() > 150) {
            alert.setContentText("The XSD file could not be loaded due to validation errors.");

            // Create expandable Exception area
            Label label = new Label("Error details:");
            TextArea textArea = new TextArea(detailedMessage);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            VBox expandableContent = new VBox();
            expandableContent.setMaxWidth(Double.MAX_VALUE);
            expandableContent.getChildren().addAll(label, textArea);

            alert.getDialogPane().setExpandableContent(expandableContent);
        } else {
            alert.setContentText(detailedMessage);
        }

        // Make the dialog resizable and set preferred size
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(600);

        alert.showAndWait();
        logger.info("Shown XSD loading error dialog for file: {}", file.getAbsolutePath());
    }

    /**
     * Updates the validation status in the UI.
     * Called by XsdDiagramView when live validation results are available.
     */
    public void updateValidationStatus(String statusMessage, boolean hasErrors) {
        Platform.runLater(() -> {
            try {
                logger.debug("Validation status update: {} (errors: {})", statusMessage, hasErrors);

                // Update the status text label with validation status
                if (statusText != null) {
                    if (hasErrors) {
                        statusText.setText("⚠ " + statusMessage);
                        statusText.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
                        logger.warn("XSD validation has errors: {}", statusMessage);
                    } else {
                        statusText.setText("✓ " + statusMessage);
                        statusText.setStyle("-fx-text-fill: #388e3c; -fx-font-weight: normal;");
                        logger.info("XSD validation status: {}", statusMessage);
                    }
                }

            } catch (Exception e) {
                logger.error("Error updating validation status", e);
            }
        });
    }

    // ======================================================================
    // Type Library Methods
    // ======================================================================

    /**
     * Initialize the type library panel
     */
    private void initializeTypeLibrary() {
        // Set up type library tab selection handler
        if (typeLibraryTab != null) {
            typeLibraryTab.setOnSelectionChanged(event -> {
                if (typeLibraryTab.isSelected()) {
                    refreshTypeLibrary();
                }
            });
        }
    }

    /**
     * Create or refresh the type library panel
     */
    private void refreshTypeLibrary() {
        if (typeLibraryStackPane == null) {
            logger.warn("Type library stack pane is not initialized");
            return;
        }

        try {
            // Clear existing content
            typeLibraryStackPane.getChildren().clear();

            if (currentDomManipulator != null) {
                // Create type library panel with command executor and refresh callback
                typeLibraryPanel = new XsdTypeLibraryPanel(
                        currentDomManipulator,
                        this::executeTypeCommand,
                        this::refreshTypeLibrary
                );

                // Add CSS styling
                typeLibraryPanel.getStylesheets().add(
                        getClass().getResource("/css/xsd-type-library.css").toExternalForm()
                );

                typeLibraryStackPane.getChildren().add(typeLibraryPanel);
                logger.info("Type library panel initialized successfully");
            } else {
                // Show message when no XSD is loaded
                Label noDataLabel = new Label("No XSD file loaded. Please open an XSD file to view type definitions.");
                noDataLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14px;");
                StackPane.setAlignment(noDataLabel, javafx.geometry.Pos.CENTER);
                typeLibraryStackPane.getChildren().add(noDataLabel);
            }

        } catch (Exception e) {
            logger.error("Error initializing type library panel", e);
            Label errorLabel = new Label("Error loading type library: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 14px;");
            StackPane.setAlignment(errorLabel, javafx.geometry.Pos.CENTER);
            typeLibraryStackPane.getChildren().add(errorLabel);
        }
    }

    /**
     * Execute a type-related command with proper undo support
     */
    private void executeTypeCommand(XsdCommand command) {
        try {
            logger.info("Executing type command: {}", command.getDescription());

            boolean success = command.execute();
            if (success) {
                // Handle specific command types that need result display
                if (command instanceof org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand findCommand) {
                    displayTypeUsageResults(findCommand);
                }
                
                // Add to undo stack if the command supports it
                if (command.canUndo()) {
                    addCommandToUndoStack(command);
                }

                // Refresh UI components that depend on the schema
                Platform.runLater(() -> {
                    try {
                        // Refresh the XSD diagram if it exists
                        String updatedContent = currentDomManipulator.getXmlContent();
                        if (updatedContent != null) {
                            // Update the text editor with the modified content
                            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                                sourceCodeEditor.getCodeArea().replaceText(updatedContent);
                                logger.debug("Text editor updated with modified XSD content after type command");
                            }

                            // Refresh the graphic view without rebuilding (false parameter)
                            updateXsdContent(updatedContent, false);

                            // Refresh the type library
                            if (typeLibraryPanel != null) {
                                typeLibraryPanel.loadTypes();
                            }

                            // Mark as modified
                            markAsModified();
                        }
                    } catch (Exception e) {
                        logger.error("Error refreshing UI after command execution", e);
                    }
                });

                logger.info("Type command executed successfully: {}", command.getDescription());
            } else {
                logger.error("Type command execution failed: {}", command.getDescription());
            }

        } catch (Exception e) {
            logger.error("Error executing type command: " + command.getDescription(), e);
        }
    }

    /**
     * Opens a specific XSD type (simple or complex) in a dedicated graphic editor tab
     */
    public void openTypeInGraphicEditor(Element typeElement) {
        try {
            String typeName = typeElement.getAttribute("name");
            boolean isSimpleType = "simpleType".equals(typeElement.getLocalName());

            logger.info("Opening {} type '{}' in dedicated graphic editor",
                    isSimpleType ? "simple" : "complex", typeName);

            // Create the type editor tab
            XsdTypeEditor typeEditor = new XsdTypeEditor(typeElement, this);

            // Add to the main tab pane
            if (tabPane != null) {
                Platform.runLater(() -> {
                    tabPane.getTabs().add(typeEditor);
                    tabPane.getSelectionModel().select(typeEditor);
                });

                logger.info("Type editor tab created and selected for type: {}", typeName);
            } else {
                logger.error("TabPane not available for adding type editor");

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("UI Error");
                    alert.setHeaderText("Cannot Open Type Editor");
                    alert.setContentText("The tab panel is not available to open the type editor.");
                    alert.showAndWait();
                });
            }

        } catch (Exception e) {
            logger.error("Failed to open type in graphic editor", e);

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Type Editor Error");
                alert.setHeaderText("Failed to Open Type Editor");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            });
        }
    }

    /**
     * Marks the current XSD as modified and updates UI accordingly.
     * This updates the unsaved changes flag, enables save buttons,
     * and updates the status text to inform the user.
     */
    public void markAsModified() {
        logger.debug("XSD marked as modified");

        // Update the internal modified state
        hasUnsavedChanges = true;

        // Enable save buttons
        Platform.runLater(() -> {
            if (saveXsdButton != null) {
                saveXsdButton.setDisable(false);
            }
            if (saveXsdButtonGraphic != null) {
                saveXsdButtonGraphic.setDisable(false);
            }

            // Update status text to indicate unsaved changes
            if (statusText != null) {
                String currentFile = currentXsdFile != null ? currentXsdFile.getName() : "XSD";
                statusText.setText("● " + currentFile + " - modified (unsaved changes)");
                statusText.setStyle("-fx-text-fill: #ff9800; -fx-font-weight: bold;");
            }

            logger.debug("UI updated to reflect modified state");
        });
    }

    /**
     * Refreshes the main diagram view
     */
    public void refreshDiagramView() {
        if (currentDiagramView != null) {
            Platform.runLater(() -> {
                try {
                    // Trigger a refresh of the diagram
                    currentDiagramView.refreshView();
                    logger.debug("Diagram view refreshed");
                } catch (Exception e) {
                    logger.error("Failed to refresh diagram view", e);
                }
            });
        }
    }

    /**
     * Display the results of a find type usages command
     */
    private void displayTypeUsageResults(org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand findCommand) {
        List<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage> usages = findCommand.getFoundUsages();

        if (usages.isEmpty()) {
            // Show info dialog when no usages found
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Find Type Usages");
            alert.setHeaderText("No usages found");
            alert.setContentText("Type '" + findCommand.getTypeInfo().name() + "' is not used anywhere in the schema.");
            alert.showAndWait();
            return;
        }

        // Create dialog to display usage results
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Find Type Usages - " + findCommand.getTypeInfo().name());
        dialog.setHeaderText("Found " + usages.size() + " usage(s) of type '" + findCommand.getTypeInfo().name() + "'");

        // Create table to display usage results
        TableView<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage> usageTable = new TableView<>();
        usageTable.setPrefSize(600, 300);

        // Create columns
        TableColumn<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage, String> elementColumn = new TableColumn<>("Element");
        elementColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getElementName()));
        elementColumn.setPrefWidth(120);

        TableColumn<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage, String> usageTypeColumn = new TableColumn<>("Usage Type");
        usageTypeColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUsageType().getDescription()));
        usageTypeColumn.setPrefWidth(120);

        TableColumn<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage, String> contextColumn = new TableColumn<>("Context");
        contextColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUsageContext()));
        contextColumn.setPrefWidth(150);

        TableColumn<org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand.TypeUsage, String> xpathColumn = new TableColumn<>("XPath");
        xpathColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getXPath()));
        xpathColumn.setPrefWidth(200);

        usageTable.getColumns().addAll(elementColumn, usageTypeColumn, contextColumn, xpathColumn);

        // Add data to table
        usageTable.getItems().addAll(usages);

        // Create content for dialog
        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Click on a row to see more details:"),
                usageTable
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Show the dialog
        dialog.showAndWait();

        logger.info("Displayed {} type usages for '{}'", usages.size(), findCommand.getTypeInfo().name());
    }

    /**
     * Setup visualization toggle buttons for switching between tree, UML, and grid views
     */
    private void setupVisualizationToggleButtons() {
        // UML and Grid views removed - only tree view remains active
    }

    /**
     * Gets XSD documentation info for a specific element
     */
    public XsdDocInfo getElementDocInfo(String xpath) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists() || xpath == null || xpath.isBlank()) {
            return null;
        }

        try {
            return xmlService.getElementDocInfo(currentXsdFile, xpath);
        } catch (Exception e) {
            logger.warn("Could not extract XSD doc info for element at xpath: {}", xpath, e);
            return null;
        }
    }

    // ==================== WebView Zoom Handlers ====================

    /**
     * Handles zoom in button click.
     */
    @FXML
    private void handleZoomIn() {
        setZoom(currentZoom + ZOOM_STEP);
    }

    /**
     * Handles zoom out button click.
     */
    @FXML
    private void handleZoomOut() {
        setZoom(currentZoom - ZOOM_STEP);
    }

    /**
     * Handles reset zoom button click (100%).
     */
    @FXML
    private void handleZoomReset() {
        setZoom(1.0);
    }

    /**
     * Sets the zoom level for the documentation WebView.
     *
     * @param zoom the new zoom level (1.0 = 100%)
     */
    private void setZoom(double zoom) {
        // Clamp zoom to min/max range
        currentZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

        // Apply zoom to WebView
        if (docWebView != null) {
            docWebView.setZoom(currentZoom);
        }

        // Update label
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("%.0f%%", currentZoom * 100));
        }

        logger.debug("Zoom set to {}%", currentZoom * 100);
    }

    /**
     * Initializes mouse wheel zoom and keyboard shortcuts for the documentation WebView.
     * Call this from the initialize() method.
     */
    private void setupWebViewZoom() {
        if (docWebView == null || docPreviewTab == null) {
            return;
        }

        // Setup mouse wheel zoom on the WebView parent (BorderPane)
        // We need to wait until the scene is available
        Platform.runLater(() -> {
            if (docWebView.getParent() != null) {
                docWebView.getParent().setOnScroll(event -> {
                    // Check if Ctrl (or Cmd on Mac) is pressed
                    if (event.isControlDown()) {
                        event.consume();

                        // Determine zoom direction from scroll delta
                        double deltaY = event.getDeltaY();
                        if (deltaY > 0) {
                            // Scroll up = zoom in
                            setZoom(currentZoom + ZOOM_STEP);
                        } else if (deltaY < 0) {
                            // Scroll down = zoom out
                            setZoom(currentZoom - ZOOM_STEP);
                        }
                    }
                });
            }

            // Setup keyboard shortcuts on the tab's content
            if (docPreviewTab.getContent() != null) {
                docPreviewTab.getContent().setOnKeyPressed(event -> {
                    if (event.isControlDown()) {
                        switch (event.getCode()) {
                            case PLUS, EQUALS -> {
                                // CTRL + or CTRL =
                                event.consume();
                                setZoom(currentZoom + ZOOM_STEP);
                            }
                            case MINUS -> {
                                // CTRL -
                                event.consume();
                                setZoom(currentZoom - ZOOM_STEP);
                            }
                            case DIGIT0, NUMPAD0 -> {
                                // CTRL 0
                                event.consume();
                                setZoom(1.0);
                            }
                        }
                    }
                });

                // Make sure the content is focusable to receive key events
                docPreviewTab.getContent().setFocusTraversable(true);
            }
        });
    }

}
