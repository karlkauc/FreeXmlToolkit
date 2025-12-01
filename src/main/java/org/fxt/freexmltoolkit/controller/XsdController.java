package org.fxt.freexmltoolkit.controller;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
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
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controller.dialogs.XsdOverviewDialogController;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.editor.FindReplaceDialog;
import org.fxt.freexmltoolkit.controls.intellisense.XmlCodeFoldingManager;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
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

public class XsdController implements FavoritesParentController {

    private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView currentGraphViewV2;

    // Type Editor integration
    private org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager typeEditorTabManager;
    @FXML
    private Tab typeEditorTab;
    @FXML
    private StackPane typeEditorStackPane;
    private TabPane typeEditorTabPane;

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab xsdTab;
    @FXML
    private Label statusText;

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

    // Service classes - injected via ServiceRegistry
    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final FavoritesService favoritesService = ServiceRegistry.get(FavoritesService.class);

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

    // --- Toolbar buttons ---
    @FXML
    private Button toolbarNewFile;
    @FXML
    private Button toolbarLoadFile;
    @FXML
    private Button toolbarSave;
    @FXML
    private Button toolbarSaveAs;
    @FXML
    private Button toolbarReload;
    @FXML
    private Button toolbarClose;
    @FXML
    private Button toolbarValidate;
    @FXML
    private Button toolbarUndo;
    @FXML
    private Button toolbarRedo;
    @FXML
    private Button toolbarFind;
    @FXML
    private Button toolbarFormat;
    @FXML
    private Button toolbarAddFavorite;
    @FXML
    private Button toolbarShowFavorites;
    @FXML
    private MenuButton toolbarRecentFiles;
    @FXML
    private Button toolbarHelp;



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
     * Perform undo operation (V1 removed, stub for compatibility)
     */
    public void performUndo() {
        logger.debug("performUndo called - V1 editor removed, no action taken");
    }

    /**
     * Perform redo operation (V1 removed, stub for compatibility)
     */
    public void performRedo() {
        logger.debug("performRedo called - V1 editor removed, no action taken");
    }

    // ======================================================================
    // Felder und Methoden für den "Graphic" Tab (XSD)
    // ======================================================================
    @FXML
    private StackPane xsdStackPaneV2;
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
    private SplitPane textTabSplitPane;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private FavoritesPanelController favoritesPanelController;

    // Fields for the graphic tab favorites system
    @FXML
    private SplitPane graphicTabSplitPane;
    @FXML
    private VBox favoritesPanelGraphic;
    @FXML
    private FavoritesPanelController favoritesPanelGraphicController;

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

        // Initialize favorites panel controllers with this as parent
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
            logger.debug("Favorites panel controller (text tab) initialized");
        }
        if (favoritesPanelGraphicController != null) {
            favoritesPanelGraphicController.setParentController(this);
            logger.debug("Favorites panel controller (graphic tab) initialized");
        }

        // Hide panel initially by removing it from split pane
        if (textTabSplitPane != null && favoritesPanel != null) {
            textTabSplitPane.getItems().remove(favoritesPanel);
        }

        // Hide graphic panel initially by removing it from split pane
        if (graphicTabSplitPane != null && favoritesPanelGraphic != null) {
            graphicTabSplitPane.getItems().remove(favoritesPanelGraphic);
        }

        // Initialize type editor
        initializeTypeEditor();

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

        // Apply small icons setting from user preferences
        applySmallIconsSetting();

        // Setup Ctrl+S keyboard shortcut for V2 editor
        setupV2EditorSaveShortcut();

        // Setup tab navigation and help dialog shortcuts
        setupTabNavigationShortcuts();

        // Initialize toolbar recent files menu
        Platform.runLater(this::initializeRecentFilesMenu);

        // Show overview dialog on startup if preference is enabled
        Platform.runLater(() -> {
            if (XsdOverviewDialogController.shouldShowOnStartup()) {
                // Delay slightly to ensure the window is fully loaded
                Platform.runLater(this::showOverviewDialog);
            }
        });

        // Set up drag and drop for XSD files
        setupDragAndDrop();
    }

    /**
     * Set up drag and drop functionality for the XSD controller.
     * Accepts .xsd files only on this tab.
     */
    private void setupDragAndDrop() {
        if (tabPane == null) {
            logger.warn("Cannot setup drag and drop: tabPane is null");
            return;
        }

        DragDropService.setupDragDrop(tabPane, DragDropService.XSD_EXTENSIONS, files -> {
            logger.info("XSD files dropped on XSD controller: {} file(s)", files.size());
            // Load the first XSD file
            if (!files.isEmpty()) {
                openXsdFile(files.get(0));
            }
        });
        logger.debug("Drag and drop initialized for XSD controller");
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
     * Sets up automatic bidirectional synchronization between text tab and graphic tab.
     * - When switching from text tab to graphic tab: graphic view is updated with text content
     * - When switching from graphic tab to text tab: text view is updated with current model
     */
    private void setupTextToGraphicSync() {
        // Track the last selected tab to detect tab switches
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            // When switching FROM text tab TO graphic tab
            if (oldTab == textTab && newTab == xsdTab) {
                syncTextToGraphic();
            }
            // When switching FROM graphic tab TO text tab - sync model back to text
            if (oldTab == xsdTab && newTab == textTab) {
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
            // Reload V2 graphic view without triggering text update
            // V2 editor is now always active (toggle button removed)
            loadXsdIntoGraphicViewV2(currentText);
            logger.debug("Synchronized text content to V2 graphic view");
        } catch (Exception e) {
            logger.error("Failed to synchronize text to graphic view", e);
        }
    }

    /**
     * Synchronizes the V2 graphic editor model to the text view.
     * This is called when switching from graphic tab to text tab.
     * Serializes the current XsdSchema model and updates the text editor.
     */
    private void syncGraphicToText() {
        if (currentGraphViewV2 == null) {
            logger.debug("No V2 graph view to sync - skipping");
            return;
        }

        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = currentGraphViewV2.getXsdSchema();
        if (schema == null) {
            logger.debug("No schema in V2 graph view - skipping sync");
            return;
        }

        try {
            // Serialize the current model to XSD XML
            org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer serializer =
                    new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer();
            String xsdContent = serializer.serialize(schema);

            if (xsdContent != null && !xsdContent.isEmpty()) {
                // Update the text editor with the serialized content
                if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                    sourceCodeEditor.getCodeArea().replaceText(xsdContent);
                    // Apply syntax highlighting
                    sourceCodeEditor.getCodeArea().setStyleSpans(0,
                            org.fxt.freexmltoolkit.controls.XmlCodeEditor.computeHighlighting(xsdContent));
                    logger.info("Synchronized V2 graphic model to text view ({} characters)", xsdContent.length());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize graphic model to text view", e);
        }
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

            // Store the file reference for save operations
            this.currentXsdFile = file;
            logger.debug("Set currentXsdFile to: {}", file.getAbsolutePath());

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

            // Show progress indicator and status text
            if (xsdDiagramProgress != null) {
                xsdDiagramProgress.setVisible(true);
                xsdDiagramProgress.setProgress(-1); // Indeterminate progress
            }
            if (statusText != null) {
                // Unbind first in case a previous task is still bound
                statusText.textProperty().unbind();
                statusText.setText("Loading XSD file...");
            }

            // Load the XSD content asynchronously into all relevant tabs
            Task<String> loadTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    updateMessage("Reading XSD file from disk...");
                    return Files.readString(file.toPath());
                }
            };

            // Bind status text to task message (already unbound above before setText)
            if (statusText != null) {
                statusText.textProperty().bind(loadTask.messageProperty());
            }

            loadTask.setOnSucceeded(event -> {
                // Unbind status text
                if (statusText != null) {
                    statusText.textProperty().unbind();
                }

                String xsdContent = loadTask.getValue();
                try {
                    if (statusText != null) {
                        statusText.setText("Processing XSD content...");
                    }
                    loadXsdContent(xsdContent);

                    // Also load into V2 graphic view (will show its own progress)
                    loadXsdIntoGraphicViewV2(xsdContent);
                } catch (Exception ex) {
                    logger.error("Error processing XSD content: {}", file.getAbsolutePath(), ex);
                    showXsdLoadingError(file, "Could not process XSD content: " + ex.getMessage());
                    if (xsdDiagramProgress != null) {
                        xsdDiagramProgress.setVisible(false);
                    }
                    if (statusText != null) {
                        statusText.setText("Error loading XSD file");
                    }
                }
            });

            loadTask.setOnFailed(event -> {
                // Unbind status text
                if (statusText != null) {
                    statusText.textProperty().unbind();
                }

                Throwable ex = loadTask.getException();
                logger.error("Error reading XSD file: {}", file.getAbsolutePath(), ex);
                if (ex instanceof IOException) {
                    showXsdLoadingError(file, "Could not read XSD file: " + ex.getMessage());
                } else {
                    showXsdLoadingError(file, "Unexpected error reading XSD file: " + ex.getMessage());
                }
                if (xsdDiagramProgress != null) {
                    xsdDiagramProgress.setVisible(false);
                }
                if (statusText != null) {
                    statusText.setText("Error loading XSD file");
                }
            });

            executorService.submit(loadTask);

        } catch (Exception e) {
            // Handle any unexpected exceptions
            logger.error("Unexpected error opening XSD file: {}", file.getAbsolutePath(), e);
            showXsdLoadingError(file, "An unexpected error occurred while loading the XSD file: " + e.getMessage());
            if (xsdDiagramProgress != null) {
                xsdDiagramProgress.setVisible(false);
            }
            if (statusText != null) {
                statusText.setText("Error opening XSD file");
            }
        }
    }

    // V1 setupXsdDiagram() method removed - V2 editor loads on demand when user switches to XSD tab

    public void saveDocumentation(String mainDoc, String javadoc) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            DialogHelper.showWarning("Save Documentation", "No XSD File", "No XSD file loaded to save to.");
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
            // V1 diagram reload removed - V2 editor updates automatically
        });

        saveTask.setOnFailed(event -> {
            logger.error("Failed to save documentation", saveTask.getException());
            DialogHelper.showException("Save Documentation", "Failed to Save Documentation",
                (Exception) saveTask.getException());
            statusText.setText("Failed to save documentation.");
        });

        executeTask(saveTask);
    }

    public void saveElementDocumentation(String xpath, String documentation, String javadoc) {
        File currentXsdFile = xmlService.getCurrentXsdFile();
        if (currentXsdFile == null || !currentXsdFile.exists()) {
            DialogHelper.showWarning("Save Element Documentation", "No XSD File", "No XSD file loaded to save to.");
            return;
        }
        if (xpath == null || xpath.isBlank()) {
            DialogHelper.showWarning("Save Element Documentation", "No Element Selected", "No element selected to save documentation for.");
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
            // V1 diagram reload removed - V2 editor updates automatically
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
            DialogHelper.showWarning("Save Example Values", "No XSD File", "No XSD file loaded to save to.");
            return;
        }
        if (xpath == null || xpath.isBlank()) {
            DialogHelper.showWarning("Save Example Values", "No Element Selected", "No element selected to save examples for.");
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
            // V1 diagram reload removed - V2 editor updates automatically
        });

        saveExamplesTask.setOnFailed(event -> {
            logger.error("Failed to save example values for xpath: " + xpath, saveExamplesTask.getException());
            Throwable ex = saveExamplesTask.getException();
            if (ex instanceof Exception) {
                DialogHelper.showException("Save Example Values", "Failed to Save Example Values", (Exception) ex);
            } else {
                DialogHelper.showError("Save Example Values", "Error", ex != null ? ex.getMessage() : "Unknown error");
            }
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

            // Rebuild the V2 diagram view only if requested
            // V2 editor is now always active (toggle button removed)
            if (rebuildDiagram) {
                loadXsdIntoGraphicViewV2(updatedXsd);
            }

            // Mark as modified
            hasUnsavedChanges = true;
            statusText.setText("XSD modified - changes not saved to file");

            // V1 DOM manipulator update removed - V2 handles updates automatically
        }
    }

    // V1 analyzeXsdContent() method removed - V2 uses XsdNodeFactory

    /**
     * Loads XSD content into the V2 graphical view using the new model-based architecture.
     */
    private void loadXsdIntoGraphicViewV2(String xsdContent) {
        // Store factory reference to get imported schemas after parsing
        final org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory[] factoryRef =
                new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory[1];

        Task<org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> task = new Task<>() {
            @Override
            protected org.fxt.freexmltoolkit.controls.v2.model.XsdSchema call() throws Exception {
                updateMessage("Parsing XSD schema...");

                // Use new XsdNodeFactory to parse the schema
                org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory factory =
                        new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory();
                factoryRef[0] = factory;  // Store factory reference

                updateMessage("Processing schema elements...");
                java.nio.file.Path baseDir = currentXsdFile != null ? currentXsdFile.toPath().getParent() : null;
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = factory.fromString(xsdContent, baseDir);

                updateMessage("Creating graphical representation...");
                return schema;
            }
        };

        // Bind status text to task message (unbind first in case previous task is still bound)
        if (statusText != null) {
            statusText.textProperty().unbind();
            statusText.textProperty().bind(task.messageProperty());
        }

        task.setOnSucceeded(event -> {
            // Unbind status text
            if (statusText != null) {
                statusText.textProperty().unbind();
            }

            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = task.getValue();
            xsdStackPaneV2.getChildren().clear();

            if (schema != null) {
                // Use XsdSchema-based constructor
                currentGraphViewV2 = new org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView(schema);

                // Set imported schemas from factory (for ref resolution to imported elements)
                if (factoryRef[0] != null) {
                    java.util.Map<String, org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> importedSchemas =
                            factoryRef[0].getImportedSchemas();
                    currentGraphViewV2.setImportedSchemas(importedSchemas);
                }

                // Enable edit mode by creating and setting up an editor context
                org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext =
                        new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(schema);
                editorContext.setEditMode(true);
                currentGraphViewV2.setEditorContext(editorContext);

                // IMPORTANT: Set callbacks AFTER setEditorContext (which recreates contextMenuFactory)
                // Set Type Editor callbacks for context menu
                currentGraphViewV2.setOpenComplexTypeEditorCallback(this::openComplexTypeEditor);
                currentGraphViewV2.setOpenSimpleTypeEditorCallback(this::openSimpleTypeEditor);

                // Update TypeEditorTabManager with loaded schema
                updateTypeEditorWithSchema(schema);

                xsdStackPaneV2.getChildren().add(currentGraphViewV2);

                // Update UI visibility for graphic tab
                if (noFileLoadedPane != null) {
                    noFileLoadedPane.setVisible(false);
                    noFileLoadedPane.setManaged(false);
                }
                if (xsdStackPaneV2 != null) {
                    xsdStackPaneV2.setVisible(true);
                    xsdStackPaneV2.setManaged(true);
                }
                if (xsdInfoPane != null) {
                    xsdInfoPane.setVisible(true);
                    xsdInfoPane.setManaged(true);

                    // Update info labels
                    if (xsdInfoPathLabel != null && currentXsdFile != null) {
                        xsdInfoPathLabel.setText(currentXsdFile.getAbsolutePath());
                    }
                    if (xsdInfoNamespaceLabel != null) {
                        xsdInfoNamespaceLabel.setText(schema.getTargetNamespace() != null ?
                            schema.getTargetNamespace() : "No target namespace");
                    }
                    if (xsdInfoVersionLabel != null) {
                        // Detect XSD version based on features used (1.0 or 1.1)
                        xsdInfoVersionLabel.setText("XSD " + schema.detectXsdVersion());
                    }
                }

                logger.info("XSD loaded into V2 editor with edit mode enabled: {} global elements",
                        schema.getChildren().stream()
                                .filter(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement)
                                .count());

                // Hide progress indicator and update status
                if (xsdDiagramProgress != null) {
                    xsdDiagramProgress.setVisible(false);
                }
                if (statusText != null) {
                    statusText.setText("XSD file successfully loaded");
                }

                // Enable save buttons after successful V2 editor load
            } else {
                javafx.scene.control.Label errorLabel = new javafx.scene.control.Label("Failed to parse XSD schema");
                xsdStackPaneV2.getChildren().add(errorLabel);

                // Hide progress indicator and show error
                if (xsdDiagramProgress != null) {
                    xsdDiagramProgress.setVisible(false);
                }
                if (statusText != null) {
                    statusText.setText("Error parsing XSD schema");
                }
            }
        });

        task.setOnFailed(event -> {
            // Unbind status text
            if (statusText != null) {
                statusText.textProperty().unbind();
            }

            logger.error("Failed to load XSD into V2 editor", task.getException());
            javafx.scene.control.Label errorLabel = new javafx.scene.control.Label(
                    "Error loading XSD: " + task.getException().getMessage());
            xsdStackPaneV2.getChildren().clear();
            xsdStackPaneV2.getChildren().add(errorLabel);

            // Hide progress indicator and show error
            if (xsdDiagramProgress != null) {
                xsdDiagramProgress.setVisible(false);
            }
            if (statusText != null) {
                statusText.setText("Error loading into V2 editor: " + task.getException().getMessage());
            }
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
                    // Keep all save buttons enabled for convenience
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

    // V1 setupXsdDiagramFromContent() method removed - V2 loads on demand

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

        // Update UI visibility for text tab
        if (noFileLoadedPaneText != null) {
            noFileLoadedPaneText.setVisible(false);
            noFileLoadedPaneText.setManaged(false);
        }
        if (textInfoPane != null) {
            textInfoPane.setVisible(true);
            textInfoPane.setManaged(true);
        }

        // Update file path label
        if (textInfoPathLabel != null && currentXsdFile != null) {
            textInfoPathLabel.setText(currentXsdFile.getAbsolutePath());
        }
    }

    @FXML
    private void saveXsdFile() {
        // Require V2 editor or text content
        boolean hasEditor = (currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null);

        if (!hasEditor) {
            logger.debug("Cannot save: no editor available");
            return;
        }

        // If file hasn't been saved yet, redirect to Save As
        if (currentXsdFile == null) {
            logger.info("No file path set, redirecting to Save As...");
            saveXsdFileAs();
            return;
        }

        // Save to existing file
        saveXsdToFile(currentXsdFile);
    }

    /**
     * Save As functionality - prompts user to select a new file location
     */
    @FXML
    private void saveXsdFileAs() {
        // Check if we have any XSD content to save
        boolean hasContent = currentGraphViewV2 != null && currentGraphViewV2.getEditorContext() != null &&
                currentGraphViewV2.getEditorContext().getSchema() != null;

        // Check V2 editor

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
                        // Keep save buttons enabled for convenience
                        // saveXsdButton.setDisable(true);
                        // if (saveXsdButtonGraphic != null) {
                        //     saveXsdButtonGraphic.setDisable(true);
                        // }
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
                    logger.warn("V2 editor context has no schema model");
                    showError("Save Failed", "Cannot save: V2 editor has no schema model. Please reload the file.");
                    return;
                }
            }

            // Priority 2: Use text editor if V2 editor is not available
            // First sync the graphic model to text to ensure we have the latest content
            if (currentGraphViewV2 != null) {
                syncGraphicToText();
            }

            // Create backup if enabled
            createBackupIfEnabled(file);

            // Get the XSD content from text editor
            String updatedXsd = null;
            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                String textContent = sourceCodeEditor.getCodeArea().getText();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    updatedXsd = textContent;
                    logger.debug("Saving XSD from text editor");
                }
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
            // Keep save buttons enabled for convenience
            // saveXsdButton.setDisable(true);
            // if (saveXsdButtonGraphic != null) {
            //     saveXsdButtonGraphic.setDisable(true);
            // }
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

            // Get content from text editor
            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                xsdContent = sourceCodeEditor.getCodeArea().getText();
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
            } else {
                // If in diagram view, switch to text view and update
                tabPane.getSelectionModel().select(textTab);
                if (sourceCodeEditor != null) {
                    sourceCodeEditor.getCodeArea().replaceText(formattedXsd);
                    hasUnsavedChanges = true;
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
     * @deprecated Use {@link DialogHelper#showError(String, String, String)} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private void showError(String title, String content) {
        DialogHelper.showError(title, "", content);
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
            // Get the XSD content from text editor
            String xsdContent;
            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
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
            DialogHelper.showError("Generate Documentation", "Missing XSD File", "Please provide a source XSD file.");
            return;
        }
        if (outputPath == null || outputPath.isBlank()) {
            DialogHelper.showError("Generate Documentation", "Missing Output Directory", "Please select an output directory.");
            return;
        }

        File xsdFile = new File(xsdPath);
        File outputDir = new File(outputPath);

        if (!xsdFile.exists()) {
            DialogHelper.showError("Generate Documentation", "XSD File Not Found", "The specified XSD file does not exist: " + xsdPath);
            return;
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            DialogHelper.showError("Generate Documentation", "Cannot Create Directory", "Could not create the output directory: " + outputPath);
            return;
        }
        if (!outputDir.isDirectory()) {
            DialogHelper.showError("Generate Documentation", "Invalid Output Path", "The specified output path is not a directory: " + outputPath);
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
        if (e instanceof Exception) {
            DialogHelper.showException("Generate Documentation", "Failed to Generate Documentation", (Exception) e);
        } else {
            DialogHelper.showError("Generate Documentation", "Error", e.getMessage());
        }
    }

    private void openFolderInExplorer(File folder) {
        try {
            java.awt.Desktop.getDesktop().open(folder);
        } catch (IOException ex) {
            logger.error("Could not open output directory: {}", folder.getAbsolutePath(), ex);
            DialogHelper.showError("Open Folder", "Could Not Open Directory",
                "Could not open the output directory. Please navigate to it manually:\n" + folder.getAbsolutePath());
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
            DialogHelper.showError("Generate Sample Data", "Missing XSD File", "Please load an XSD source file first.");
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            DialogHelper.showError("Generate Sample Data", "XSD File Not Found", "The specified XSD file does not exist: " + xsdPath);
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
            if (e instanceof Exception) {
                DialogHelper.showException("Generate Sample Data", "Failed to Generate Sample XML", (Exception) e);
            } else {
                DialogHelper.showError("Generate Sample Data", "Error", e != null ? e.getMessage() : "Unknown error");
            }
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
            Throwable ex = saveTask.getException();
            if (ex instanceof Exception) {
                DialogHelper.showException("Save File", "Could Not Save File", (Exception) ex);
            } else {
                DialogHelper.showError("Save File", "Error", ex != null ? ex.getMessage() : "Unknown error");
            }
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

        // Toggle: check if panel is currently in the split pane
        boolean isCurrentlyShown = textTabSplitPane.getItems().contains(favoritesPanel);
        boolean shouldShow = !isCurrentlyShown;

        if (shouldShow) {
            // Show the panel with proper divider position
            favoritesPanel.setVisible(true);
            favoritesPanel.setManaged(true);
            textTabSplitPane.getItems().add(favoritesPanel);
            textTabSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            textTabSplitPane.getItems().remove(favoritesPanel);
            favoritesPanel.setVisible(false);
            favoritesPanel.setManaged(false);
        }

        logger.info("Favorites panel toggled: {}", shouldShow ? "shown" : "hidden");
    }

    @FXML
    private void toggleFavoritesPanelGraphic() {
        if (favoritesPanelGraphic == null || graphicTabSplitPane == null) {
            return;
        }

        // Toggle: check if panel is currently in the split pane
        boolean isCurrentlyShown = graphicTabSplitPane.getItems().contains(favoritesPanelGraphic);
        boolean shouldShow = !isCurrentlyShown;

        if (shouldShow) {
            // Show the panel with proper divider position
            favoritesPanelGraphic.setVisible(true);
            favoritesPanelGraphic.setManaged(true);
            graphicTabSplitPane.getItems().add(favoritesPanelGraphic);
            graphicTabSplitPane.setDividerPositions(0.75);
        } else {
            // Hide the panel
            graphicTabSplitPane.getItems().remove(favoritesPanelGraphic);
            favoritesPanelGraphic.setVisible(false);
            favoritesPanelGraphic.setManaged(false);
        }

        logger.info("Graphic tab favorites panel toggled: {}", shouldShow ? "shown" : "hidden");
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
     * Called when live validation results are available.
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
                    logger.debug("Type library tab selected");
                    // Type library is now automatically populated via TypeEditorTabManager
                    // when a schema is loaded via updateTypeEditorWithSchema()
                }
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

    // ======================================================================
    // Type Editor Methods
    // ======================================================================

    /**
     * Initialize the type editor tab and manager.
     * Creates a new tab with embedded TabPane for managing type editor subtabs.
     */
    private void initializeTypeEditor() {
        try {
            // Create TabPane for type editor subtabs (used when editing individual types)
            typeEditorTabPane = new TabPane();
            typeEditorTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

            // Get schema from current graph view, or create empty schema
            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema;
            if (currentGraphViewV2 != null && currentGraphViewV2.getXsdSchema() != null) {
                schema = currentGraphViewV2.getXsdSchema();
                logger.debug("Using schema from currentGraphViewV2");
            } else {
                // Create temporary empty schema - will be replaced when actual schema is loaded
                schema = new org.fxt.freexmltoolkit.controls.v2.model.XsdSchema();
                schema.setTargetNamespace("http://temp.placeholder");
                logger.debug("Created temporary placeholder schema");
            }

            // Initialize TypeEditorTabManager
            typeEditorTabManager = new org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager(typeEditorTabPane, schema);

            // Add typeEditorTabPane to typeEditorStackPane (from FXML)
            if (typeEditorStackPane != null) {
                typeEditorStackPane.getChildren().clear();
                typeEditorStackPane.getChildren().add(typeEditorTabPane);
                logger.info("Type Editor TabPane added to StackPane");
            } else {
                logger.warn("Type Editor StackPane is not initialized (FXML injection failed?)");
            }

            // Initialize Type Library View (shows all types with usage info)
            if (typeLibraryStackPane != null) {
                typeLibraryStackPane.getChildren().clear();
                org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView typeLibraryView =
                    new org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView(schema);
                typeLibraryStackPane.getChildren().add(typeLibraryView);
                logger.info("Type Library tab initialized with TypeLibraryView");
            } else {
                logger.warn("Type Library StackPane is not initialized");
            }

        } catch (Exception e) {
            logger.error("Error initializing Type Editor", e);
        }
    }

    /**
     * Updates the Type Editor with a newly loaded schema.
     * Re-creates the TypeEditorTabManager with the new schema.
     *
     * @param schema the loaded XSD schema
     */
    private void updateTypeEditorWithSchema(org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema) {
        if (typeEditorTabPane != null && schema != null) {
            try {
                // Close all existing type editor tabs first
                if (typeEditorTabManager != null) {
                    typeEditorTabManager.closeAllTypeTabs();
                }

                // Re-create TypeEditorTabManager with new schema
                typeEditorTabManager = new org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager(typeEditorTabPane, schema);
                logger.info("Type Editor updated with loaded schema: {}", schema.getTargetNamespace());

                // Update Type Library View with new schema
                if (typeLibraryStackPane != null) {
                    typeLibraryStackPane.getChildren().clear();
                    org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView typeLibraryView =
                        new org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView(schema);
                    typeLibraryStackPane.getChildren().add(typeLibraryView);
                    logger.info("Type Library view updated with new schema");
                }

            } catch (Exception e) {
                logger.error("Error updating Type Editor with schema", e);
            }
        }
    }

    /**
     * Opens a ComplexType in the Type Editor.
     *
     * @param complexType The ComplexType to open
     */
    public void openComplexTypeEditor(org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
        logger.info("openComplexTypeEditor called for: {}", complexType != null ? complexType.getName() : "null");

        if (typeEditorTabManager == null) {
            logger.error("Type Editor not initialized - typeEditorTabManager is null");
            return;
        }

        try {
            typeEditorTabManager.openComplexTypeTab(complexType);

            // Switch to Type Editor tab
            logger.debug("Switching to Type Editor tab: tabPane={}, typeEditorTab={}",
                    tabPane != null ? "exists" : "NULL",
                    typeEditorTab != null ? typeEditorTab.getText() : "NULL");

            if (tabPane != null && typeEditorTab != null) {
                tabPane.getSelectionModel().select(typeEditorTab);
                logger.info("Switched to Type Editor tab successfully");
            } else {
                logger.warn("Cannot switch to Type Editor tab - tabPane or typeEditorTab is null!");
            }

            logger.info("Opened ComplexType: {}", complexType.getName());

        } catch (Exception e) {
            logger.error("Error opening ComplexType editor", e);
            showError("Error", "Could not open ComplexType editor: " + e.getMessage());
        }
    }

    /**
     * Opens a SimpleType in the Type Editor.
     *
     * @param simpleType The SimpleType to open
     */
    public void openSimpleTypeEditor(org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
        if (typeEditorTabManager == null) {
            logger.error("Type Editor not initialized");
            return;
        }

        try {
            typeEditorTabManager.openSimpleTypeTab(simpleType);

            // Switch to Type Editor tab
            if (tabPane != null && typeEditorTab != null) {
                tabPane.getSelectionModel().select(typeEditorTab);
            }

            logger.info("Opened SimpleType: {}", simpleType.getName());

        } catch (Exception e) {
            logger.error("Error opening SimpleType editor", e);
            showError("Error", "Could not open SimpleType editor: " + e.getMessage());
        }
    }

    /**
     * Opens the SimpleTypes List view.
     */
    public void openSimpleTypesList() {
        if (typeEditorTabManager == null) {
            logger.error("Type Editor not initialized");
            return;
        }

        try {
            typeEditorTabManager.openSimpleTypesListTab();

            // Switch to Type Editor tab
            if (tabPane != null && typeEditorTab != null) {
                tabPane.getSelectionModel().select(typeEditorTab);
            }

            logger.info("Opened SimpleTypes List");

        } catch (Exception e) {
            logger.error("Error opening SimpleTypes List", e);
            showError("Error", "Could not open SimpleTypes List: " + e.getMessage());
        }
    }

    // ======================================================================
    // Public API methods for XSD Overview Dialog
    // ======================================================================

    /**
     * Public API for creating a new XSD file.
     * Called from XsdOverviewDialog.
     */
    public void handleCreateNewXsdFile() {
        createNewXsdFile();
    }

    /**
     * Public API for opening the XSD file chooser dialog.
     * Called from XsdOverviewDialog.
     */
    public void handleOpenXsdFileChooser() {
        File file = openXsdFileChooser();
        if (file != null) {
            logger.info("File selected from dialog: {}", file.getAbsolutePath());
        }
    }

    /**
     * Public API for toggling the favorites panel.
     * Called from toolbar and XsdOverviewDialog.
     */
    public void handleShowFavorites() {
        // Make sure we're on the graphic tab
        if (tabPane != null && xsdTab != null) {
            tabPane.getSelectionModel().select(xsdTab);
        }
        // Toggle the favorites panel (show if hidden, hide if shown)
        if (favoritesPanelGraphic != null && graphicTabSplitPane != null) {
            toggleFavoritesPanelGraphic();
        }
    }

    /**
     * Setup keyboard shortcuts for tab navigation and help dialog.
     * Ctrl+1 through Ctrl+7: Switch between tabs
     * Ctrl+H or F1: Show overview/help dialog
     */
    private void setupTabNavigationShortcuts() {
        // Need to wait for the scene to be available
        Platform.runLater(() -> {
            if (tabPane == null || tabPane.getScene() == null) {
                logger.warn("Cannot setup tab navigation shortcuts - TabPane or Scene not available");
                return;
            }

            tabPane.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                // Check for Ctrl+Number shortcuts (Ctrl+1 through Ctrl+7)
                if (event.isControlDown() && !event.isShiftDown() && !event.isAltDown()) {
                    switch (event.getCode()) {
                        case DIGIT1, NUMPAD1 -> {
                            if (xsdTab != null) {
                                tabPane.getSelectionModel().select(xsdTab);
                                event.consume();
                                logger.debug("Switched to Graphic tab via Ctrl+1");
                            }
                        }
                        case DIGIT2, NUMPAD2 -> {
                            if (typeLibraryTab != null) {
                                tabPane.getSelectionModel().select(typeLibraryTab);
                                event.consume();
                                logger.debug("Switched to Type Library tab via Ctrl+2");
                            }
                        }
                        case DIGIT3, NUMPAD3 -> {
                            if (textTab != null) {
                                tabPane.getSelectionModel().select(textTab);
                                event.consume();
                                logger.debug("Switched to Text tab via Ctrl+3");
                            }
                        }
                        case DIGIT4, NUMPAD4 -> {
                            if (documentation != null) {
                                tabPane.getSelectionModel().select(documentation);
                                event.consume();
                                logger.debug("Switched to Documentation tab via Ctrl+4");
                            }
                        }
                        case DIGIT5, NUMPAD5 -> {
                            if (docPreviewTab != null) {
                                tabPane.getSelectionModel().select(docPreviewTab);
                                event.consume();
                                logger.debug("Switched to Preview tab via Ctrl+5");
                            }
                        }
                        case DIGIT6, NUMPAD6 -> {
                            // Generate Example Data tab has no fx:id, need to find by position
                            if (tabPane.getTabs().size() > 5) {
                                tabPane.getSelectionModel().select(5); // Index 5 = 6th tab
                                event.consume();
                                logger.debug("Switched to Generate Example Data tab via Ctrl+6");
                            }
                        }
                        case DIGIT7, NUMPAD7 -> {
                            if (flattenTab != null) {
                                tabPane.getSelectionModel().select(flattenTab);
                                event.consume();
                                logger.debug("Switched to Flatten Schema tab via Ctrl+7");
                            }
                        }
                        case H -> {
                            // Ctrl+H: Show overview/help dialog
                            showOverviewDialog();
                            event.consume();
                            logger.debug("Showing overview dialog via Ctrl+H");
                        }
                        case R -> {
                            // Ctrl+R: Reload file
                            handleToolbarReload();
                            event.consume();
                            logger.debug("Reload triggered via Ctrl+R");
                        }
                        case W -> {
                            // Ctrl+W: Close file
                            handleToolbarClose();
                            event.consume();
                            logger.debug("Close triggered via Ctrl+W");
                        }
                    }
                } else if (event.isControlDown() && event.isShiftDown() && !event.isAltDown()) {
                    // Ctrl+Shift shortcuts
                    if (event.getCode() == javafx.scene.input.KeyCode.R) {
                        // Ctrl+Shift+R: Show recent files menu
                        if (toolbarRecentFiles != null) {
                            toolbarRecentFiles.show();
                            event.consume();
                            logger.debug("Recent files menu shown via Ctrl+Shift+R");
                        }
                    }
                } else if (event.isControlDown() && event.isAltDown() && !event.isShiftDown()) {
                    // Ctrl+Alt shortcuts
                    if (event.getCode() == javafx.scene.input.KeyCode.F) {
                        // Ctrl+Alt+F: Format/Pretty Print
                        handleToolbarFormat();
                        event.consume();
                        logger.debug("Format triggered via Ctrl+Alt+F");
                    }
                } else if (event.getCode() == javafx.scene.input.KeyCode.F1) {
                    // F1: Show overview/help dialog
                    showOverviewDialog();
                    event.consume();
                    logger.debug("Showing overview dialog via F1");
                }
            });

            logger.info("Tab navigation shortcuts initialized (Ctrl+1-7, Ctrl+H, Ctrl+R, Ctrl+W, Ctrl+Alt+F, Ctrl+Shift+R, F1)");
        });
    }

    /**
     * Show the XSD Overview Dialog.
     * Can be called from keyboard shortcuts or menu items.
     */
    private void showOverviewDialog() {
        try {
            // Load the FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/dialogs/XsdOverviewDialog.fxml"));
            Parent root = loader.load();

            // Get the controller and set this XsdController
            XsdOverviewDialogController controller = loader.getController();
            controller.setXsdController(this);

            // Create and configure the dialog stage
            Stage dialogStage = new Stage();
            dialogStage.setTitle("XSD Toolkit Overview");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (tabPane.getScene() != null && tabPane.getScene().getWindow() != null) {
                dialogStage.initOwner(tabPane.getScene().getWindow());
            }

            // Set the dialog stage in the controller
            controller.setDialogStage(dialogStage);

            // Create and show the scene
            javafx.scene.Scene dialogScene = new javafx.scene.Scene(root);
            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(true);
            dialogStage.showAndWait();

            logger.info("XSD Overview Dialog shown");

        } catch (Exception e) {
            logger.error("Error showing XSD Overview Dialog", e);
            showError("Error", "Could not show overview dialog: " + e.getMessage());
        }
    }

    // ======================================================================
    // Toolbar Event Handlers
    // ======================================================================

    @FXML
    private void handleToolbarNewFile() {
        logger.info("Toolbar: New File clicked");
        handleCreateNewXsdFile();
    }

    @FXML
    private void handleToolbarLoadFile() {
        logger.info("Toolbar: Load File clicked");
        handleOpenXsdFileChooser();
    }

    @FXML
    public void handleToolbarSave() {
        logger.info("Toolbar: Save clicked");
        saveXsdFile();
    }

    @FXML
    public void handleToolbarSaveAs() {
        logger.info("Toolbar: Save As clicked");
        saveXsdFileAs();
    }

    @FXML
    private void handleToolbarReload() {
        logger.info("Toolbar: Reload clicked");
        if (currentXsdFile != null && currentXsdFile.exists()) {
            openXsdFile(currentXsdFile);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No File");
            alert.setHeaderText(null);
            alert.setContentText("No file to reload");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleToolbarClose() {
        logger.info("Toolbar: Close clicked");
        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes");
            alert.setContentText("Do you want to save before closing?");

            ButtonType saveButton = new ButtonType("Save");
            ButtonType discardButton = new ButtonType("Discard");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == saveButton) {
                saveXsdFile();
            } else if (result.isPresent() && result.get() == cancelButton) {
                return;
            }
        }

        // Clear the file and UI
        currentXsdFile = null;
        xmlService.setCurrentXsdFile(null);
        clearXsdContent();
        hasUnsavedChanges = false;
    }

    @FXML
    public void handleToolbarValidate() {
        logger.info("Toolbar: Validate clicked");
        // Trigger validation in current tab
        if (tabPane.getSelectionModel().getSelectedItem() == textTab) {
            // Validate in text tab - save first if needed
            if (hasUnsavedChanges) {
                saveXsdFile();
            }
            // Validation happens automatically on save
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Validation");
            alert.setHeaderText(null);
            alert.setContentText("Schema validated successfully");
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Validation");
            alert.setHeaderText(null);
            alert.setContentText("Switch to Text tab to validate XSD schema");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleToolbarUndo() {
        logger.info("Toolbar: Undo clicked");
        // V2 editor uses CommandManager for undo
        if (tabPane.getSelectionModel().getSelectedItem() == xsdTab && currentGraphViewV2 != null) {
            org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext context = currentGraphViewV2.getEditorContext();
            if (context != null && context.getCommandManager() != null) {
                context.getCommandManager().undo();
            }
        } else if (sourceCodeEditor != null && tabPane.getSelectionModel().getSelectedItem() == textTab) {
            // Text editor undo
            sourceCodeEditor.getCodeArea().undo();
        }
    }

    @FXML
    private void handleToolbarRedo() {
        logger.info("Toolbar: Redo clicked");
        // V2 editor uses CommandManager for redo
        if (tabPane.getSelectionModel().getSelectedItem() == xsdTab && currentGraphViewV2 != null) {
            org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext context = currentGraphViewV2.getEditorContext();
            if (context != null && context.getCommandManager() != null) {
                context.getCommandManager().redo();
            }
        } else if (sourceCodeEditor != null && tabPane.getSelectionModel().getSelectedItem() == textTab) {
            // Text editor redo
            sourceCodeEditor.getCodeArea().redo();
        }
    }

    @FXML
    private void handleToolbarFind() {
        logger.info("Toolbar: Find clicked");
        // Show find/replace dialog - always works on text editor
        // If not on text tab, switch to it first
        if (tabPane.getSelectionModel().getSelectedItem() != textTab) {
            tabPane.getSelectionModel().select(textTab);
        }
        showFindReplaceDialog();
    }

    @FXML
    private void handleToolbarFormat() {
        logger.info("Toolbar: Format clicked");
        // Format code in current tab - trigger pretty print directly
        prettyPrintXsd();
    }

    @FXML
    public void handleToolbarAddFavorite() {
        logger.info("Toolbar: Add to Favorites clicked");
        if (currentXsdFile != null) {
            TextInputDialog dialog = new TextInputDialog("XSD Schemas");
            dialog.setTitle("Add to Favorites");
            dialog.setHeaderText("Add current file to favorites");
            dialog.setContentText("Category:");

            Optional<String> category = dialog.showAndWait();
            category.ifPresent(cat -> {
                favoritesService.addFavorite(
                    currentXsdFile.getAbsolutePath(),
                    currentXsdFile.getName().replace(".xsd", ""),
                    cat
                );
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Favorites");
                alert.setHeaderText(null);
                alert.setContentText("File added to favorites");
                alert.showAndWait();
            });
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No File");
            alert.setHeaderText(null);
            alert.setContentText("No file to add to favorites");
            alert.showAndWait();
        }
    }

    @FXML
    public void handleToolbarShowFavorites() {
        logger.info("Toolbar: Show Favorites clicked");
        handleShowFavorites();
    }

    @FXML
    private void handleToolbarHelp() {
        logger.info("Toolbar: Help clicked");
        showOverviewDialog();
    }

    /**
     * Initialize the Recent Files menu button with recent files.
     * Called from initialize() method.
     */
    private void initializeRecentFilesMenu() {
        if (toolbarRecentFiles != null) {
            refreshRecentFilesMenu();
        }
    }

    /**
     * Refresh the Recent Files menu with current recent files list.
     */
    private void refreshRecentFilesMenu() {
        if (toolbarRecentFiles == null) {
            return;
        }

        toolbarRecentFiles.getItems().clear();

        List<File> recentFiles = propertiesService.getLastOpenFiles();
        if (recentFiles == null || recentFiles.isEmpty()) {
            MenuItem noFiles = new MenuItem("No recent files");
            noFiles.setDisable(true);
            toolbarRecentFiles.getItems().add(noFiles);
            return;
        }

        for (File file : recentFiles) {
            if (file.exists()) {
                MenuItem item = new MenuItem(file.getName());
                item.setOnAction(e -> {
                    openXsdFile(file);
                    logger.info("Opened recent file from toolbar: {}", file.getAbsolutePath());
                });
                toolbarRecentFiles.getItems().add(item);
            }
        }
    }

    /**
     * Clear XSD content from all tabs.
     */
    private void clearXsdContent() {
        if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
            sourceCodeEditor.getCodeArea().clear();
        }
        if (xsdFilePath != null) {
            xsdFilePath.clear();
        }
        if (xsdForSampleDataPath != null) {
            xsdForSampleDataPath.clear();
        }
        if (xsdToFlattenPath != null) {
            xsdToFlattenPath.clear();
        }
        // Clear graphic view
        if (currentGraphViewV2 != null) {
            // Clear the graphic view content
            logger.debug("Cleared graphic view content");
        }
    }

    // ======================================================================
    // FavoritesParentController Interface Implementation
    // ======================================================================

    /**
     * Load a file into the XSD editor.
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

        if (!file.getName().toLowerCase().endsWith(".xsd")) {
            logger.warn("Cannot load file from favorites - not an XSD file: {}", file);
            DialogHelper.showWarning("Invalid File", "Not an XSD File",
                    "The selected file is not an XSD schema file.");
            return;
        }

        logger.info("Loading XSD file from favorites: {}", file.getAbsolutePath());
        openXsdFile(file);
    }

    /**
     * Get the currently loaded XSD file.
     * Implementation of FavoritesParentController interface.
     *
     * @return the current XSD file, or null if no file is open
     */
    @Override
    public File getCurrentFile() {
        return currentXsdFile;
    }

    /**
     * Applies the small icons setting from user preferences.
     * When enabled, toolbar buttons display in compact mode with smaller icons (14px) and no text labels.
     * When disabled, buttons show both icon and text (TOP display) with normal icon size (20px).
     */
    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to XSD toolbar: {}", useSmallIcons);

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

        // Apply to all toolbar buttons
        applyButtonSettings(toolbarNewFile, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarLoadFile, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarSave, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarSaveAs, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarReload, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarClose, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarValidate, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarUndo, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarRedo, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarFind, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarFormat, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarAddFavorite, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarShowFavorites, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarHelp, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to XSD toolbar (size: {}px)", iconSize);
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
