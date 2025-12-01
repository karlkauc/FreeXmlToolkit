package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controls.*;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.TestFile;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the Schematron Editor tab.
 * Provides functionality for editing, validating, and testing Schematron files.
 */
public class SchematronController implements FavoritesParentController {

    private static final Logger logger = LogManager.getLogger(SchematronController.class);

    @FXML
    private TabPane tabPane;

    @FXML
    private Tab codeTab;

    @FXML
    private Tab visualBuilderTab;

    @FXML
    private Tab testTab;

    @FXML
    private Tab documentationTab;

    @FXML
    private ToolBar toolbar;

    // Code Tab Buttons
    @FXML
    private Button loadSchematronFileButton;

    @FXML
    private Button newSchematronFileButton;

    @FXML
    private Button saveSchematronButton;

    @FXML
    private Button saveAsSchematronButton;

    @FXML
    private Button newRuleButton;

    @FXML
    private Button newPatternButton;

    @FXML
    private Button formatButton;

    @FXML
    private Button validateButton;

    @FXML
    private Button testRulesButton;
    @FXML
    private Button helpBtn;

    // Favorites (unified FavoritesPanel)
    @FXML
    private SplitPane mainSplitPane;

    @FXML
    private Button addToFavoritesBtn;

    @FXML
    private Button toggleFavoritesButton;

    @FXML
    private VBox favoritesPanel;

    @FXML
    private FavoritesPanelController favoritesPanelController;

    // Containers removed - components are directly in FXML now

    @FXML
    private TitledPane structurePane;

    @FXML
    private TitledPane templatesPane;

    @FXML
    private TitledPane xpathHelperPane;

    // Test Tab Buttons
    @FXML
    private Button loadSchemaFromFileButton;

    @FXML
    private Button useCurrentSchemaButton;

    @FXML
    private Button addXmlFileButton;

    @FXML
    private Button addFolderButton;

    @FXML
    private Button loadSampleXmlsButton;

    @FXML
    private Button runAllTestsButton;

    @FXML
    private Button testSingleFileButton;

    @FXML
    private Button exportResultsButton;

    @FXML
    private Button addFileButton;

    @FXML
    private Button removeSelectedButton;

    @FXML
    private Button removeAllButton;

    @FXML
    private Button addFirstXmlFileButton;

    @FXML
    private TableView<TestFile> testFilesTable;

    @FXML
    private TableColumn<TestFile, String> filenameColumn;

    @FXML
    private TableColumn<TestFile, String> statusColumn;

    @FXML
    private TableColumn<TestFile, Integer> violationsColumn;

    @FXML
    private TableColumn<TestFile, Integer> warningsColumn;

    @FXML
    private TableColumn<TestFile, String> actionsColumn;

    // Test Results Tabs
    @FXML
    private TabPane resultsTabPane;

    @FXML
    private Tab overviewResultsTab;

    @FXML
    private Tab detailsResultsTab;

    @FXML
    private Tab errorsResultsTab;

    @FXML
    private VBox overviewContent;

    @FXML
    private VBox detailsContent;

    @FXML
    private VBox errorsContent;

    @FXML
    private VBox overviewPlaceholder;

    @FXML
    private VBox detailsPlaceholder;

    @FXML
    private VBox errorsPlaceholder;

    @FXML
    private Label loadedSchemaLabel;

    // Core components from FXML
    @FXML
    private XmlCodeEditor xmlCodeEditor;

    @FXML
    private SchematronVisualBuilder visualBuilder;

    @FXML
    private SchematronDocumentationGenerator docGenerator;

    @FXML
    private SchematronTemplateLibrary templateLibrary;

    // Additional components
    private SchematronAutoComplete autoComplete;
    private SchematronErrorDetector errorDetector;
    private SchematronTester tester;
    private CodeArea codeArea;
    private SchematronService schematronService;
    private PropertiesService propertiesService;
    private MainController parentController;
    private ProgressManager progressManager;

    // Current file
    private File currentSchematronFile;

    // Test files and schema
    private final ObservableList<TestFile> testFiles = FXCollections.observableArrayList();
    private File testSchematronFile;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initialize the controller - called automatically by JavaFX
     */
    @FXML
    private void initialize() {
        logger.info("Initializing SchematronController");

        // Initialize services
        schematronService = new SchematronServiceImpl();
        propertiesService = ServiceRegistry.get(PropertiesService.class);
        progressManager = ProgressManager.getInstance();
        errorDetector = new SchematronErrorDetector();

        // Initialize code editor
        initializeCodeEditor();

        // Initialize visual builder
        initializeVisualBuilder();

        // Initialize testing framework
        initializeTester();

        // Initialize documentation generator
        initializeDocumentationGenerator();

        // Initialize toolbar buttons
        initializeToolbarButtons();

        // Initialize sidebar components
        initializeSidebarComponents();

        // Initialize favorites
        setupFavorites();

        // Initialize test tab components
        initializeTestTab();

        // Initialize the loaded schema label
        if (loadedSchemaLabel != null) {
            loadedSchemaLabel.setText("None");
            loadedSchemaLabel.setTooltip(new Tooltip("No schema loaded yet"));
        }

        // Add tab selection listener to refresh syntax highlighting
        if (tabPane != null && xmlCodeEditor != null) {
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab == codeTab) {
                    Platform.runLater(() -> xmlCodeEditor.refreshHighlighting());
                }
            });
        }

        setupKeyboardShortcuts();

        // Set up drag and drop for Schematron and XML files
        setupDragAndDrop();

        // Apply small icons setting from user preferences
        applySmallIconsSetting();

        logger.info("SchematronController initialization completed");
    }

    /**
     * Set up drag and drop functionality for the Schematron controller.
     * Accepts Schematron (.sch, .schematron) files as schema and XML files for validation.
     */
    private void setupDragAndDrop() {
        if (tabPane == null) {
            logger.warn("Cannot setup drag and drop: tabPane is null");
            return;
        }

        DragDropService.setupDragDrop(tabPane, DragDropService.XML_AND_SCHEMATRON, files -> {
            logger.info("Files dropped on Schematron controller: {} file(s)", files.size());

            for (File file : files) {
                DragDropService.FileType fileType = DragDropService.getFileType(file);
                if (fileType == DragDropService.FileType.SCHEMATRON) {
                    // Load as Schematron schema
                    loadSchematronFile(file);
                } else if (fileType == DragDropService.FileType.XML) {
                    // Add to test files table for validation
                    addTestFile(file);
                }
            }
        });
        logger.debug("Drag and drop initialized for Schematron controller");
    }

    /**
     * Add a test file to the test files table.
     *
     * @param file the XML file to add for testing
     */
    private void addTestFile(File file) {
        if (file != null && file.exists() && testFilesTable != null) {
            // Check if file already exists in the list
            boolean exists = testFiles.stream()
                    .anyMatch(tf -> tf.getFile().getAbsolutePath().equals(file.getAbsolutePath()));
            if (!exists) {
                testFiles.add(new TestFile(file));
                logger.debug("Added test file: {}", file.getName());
            } else {
                logger.debug("Test file already exists: {}", file.getName());
            }
        }
    }

    /**
     * Sets up keyboard shortcuts for Schematron Controller actions.
     */
    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (tabPane == null || tabPane.getScene() == null) {
                // Scene not ready yet, try again later
                Platform.runLater(this::setupKeyboardShortcuts);
                return;
            }

            Scene scene = tabPane.getScene();

            // Ctrl+R - Add New Rule
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                    this::insertNewRule
            );

            // Ctrl+T - Test Selected File
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                    this::testSingleFile
            );

            // Ctrl+E - Export Results
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                    this::exportResults
            );

            logger.debug("Schematron Controller keyboard shortcuts registered");
        });
    }

    /**
     * Initialize the code editor component
     */
    private void initializeCodeEditor() {
        try {
            // xmlCodeEditor is already initialized from FXML
            if (xmlCodeEditor != null) {
                codeArea = xmlCodeEditor.getCodeArea();

                // Enable Schematron mode in the XmlCodeEditor (this activates the built-in SchematronAutoComplete)
                xmlCodeEditor.setSchematronMode(true);

                // Get reference to the auto-completion instance
                autoComplete = xmlCodeEditor.getSchematronAutoComplete();

                logger.debug("Code editor initialized successfully with Schematron features");
                
                // Test the auto-completion setup
                testAutoCompletionSetup();
            } else {
                logger.error("SchematronCodeEditor not found in FXML");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize code editor", e);
            showError("Initialization Error", "Failed to initialize code editor: " + e.getMessage());
        }
    }
    
    /**
     * Test method to verify that Schematron auto-completion is correctly set up
     */
    private void testAutoCompletionSetup() {
        logger.debug("=== Testing Schematron Auto-Completion Setup ===");
        if (xmlCodeEditor != null) {
            logger.debug("XmlCodeEditor: {}", xmlCodeEditor.getClass().getSimpleName());
            logger.debug("Current editor mode: {}", xmlCodeEditor.getEditorMode());
            logger.debug("Is Schematron mode active: {}", xmlCodeEditor.isSchematronMode());
            
            var schematronAC = xmlCodeEditor.getSchematronAutoComplete();
            logger.debug("SchematronAutoComplete instance: {}", schematronAC != null ? "Available" : "NULL");
            if (schematronAC != null) {
                logger.debug("SchematronAutoComplete enabled: {}", schematronAC.isEnabled());
            }
        } else {
            logger.warn("xmlCodeEditor is null in SchematronController");
        }
        logger.debug("=== End Schematron Auto-Completion Setup Test ===");
    }

    /**
     * Initialize the visual builder component
     */
    private void initializeVisualBuilder() {
        try {
            // visualBuilder is already initialized from FXML
            if (visualBuilder != null) {
                URL cssResource = getClass().getResource("/css/schematron-editor.css");
                if (cssResource != null) {
                    visualBuilder.getStylesheets().add(cssResource.toExternalForm());
                }
                logger.debug("Visual builder initialized successfully");
            } else {
                logger.warn("VisualBuilder not found in FXML");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize visual builder", e);
            showError("Initialization Error", "Failed to initialize visual builder: " + e.getMessage());
        }
    }

    /**
     * Initialize the testing framework component
     */
    private void initializeTester() {
        try {
            // Testing is now handled directly in the FXML with Test Tab UI
            // Initialize the SchematronTester service component if needed
            tester = new SchematronTester();
            URL cssResource = getClass().getResource("/css/schematron-editor.css");
            if (cssResource != null && tester != null) {
                tester.getStylesheets().add(cssResource.toExternalForm());
            }
            logger.debug("Testing framework initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize testing framework", e);
            showError("Initialization Error", "Failed to initialize testing framework: " + e.getMessage());
        }
    }

    /**
     * Initialize the documentation generator component
     */
    private void initializeDocumentationGenerator() {
        try {
            // docGenerator is already initialized from FXML
            if (docGenerator != null) {
                URL cssResource = getClass().getResource("/css/schematron-editor.css");
                if (cssResource != null) {
                    docGenerator.getStylesheets().add(cssResource.toExternalForm());
                }
                logger.debug("Documentation generator initialized successfully");
            } else {
                logger.warn("DocumentationGenerator not found in FXML");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize documentation generator", e);
            showError("Initialization Error", "Failed to initialize documentation generator: " + e.getMessage());
        }
    }

    /**
     * Initialize toolbar button actions
     */
    private void initializeToolbarButtons() {
        if (newRuleButton != null) {
            newRuleButton.setOnAction(e -> insertNewRule());
        }

        if (newPatternButton != null) {
            newPatternButton.setOnAction(e -> insertNewPattern());
        }

        if (validateButton != null) {
            validateButton.setOnAction(e -> validateSchematron());
        }

        if (testRulesButton != null) {
            testRulesButton.setOnAction(e -> testRules());
        }
    }

    /**
     * Initialize sidebar components
     */
    private void initializeSidebarComponents() {
        try {
            // templateLibrary is already initialized from FXML
            if (templateLibrary != null) {
                URL cssResource = getClass().getResource("/css/schematron-editor.css");
                if (cssResource != null) {
                    templateLibrary.getStylesheets().add(cssResource.toExternalForm());
                }

                // Set callback to insert templates into code editor
                templateLibrary.setTemplateInsertCallback(template -> {
                    if (xmlCodeEditor != null) {
                        xmlCodeEditor.getCodeArea().insertText(xmlCodeEditor.getCodeArea().getCaretPosition(), template);
                        performErrorDetection();
                    }
                });

                logger.debug("Sidebar components initialized with template library");
            } else {
                logger.warn("TemplateLibrary not found in FXML");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize sidebar components", e);
        }
    }

    /**
     * Load a Schematron file into the editor
     */
    public void loadSchematronFile(File file) {
        if (file == null || !file.exists()) {
            logger.warn("Cannot load Schematron file: file is null or does not exist");
            return;
        }

        logger.info("Loading Schematron file: {}", file.getAbsolutePath());

        // Check if file is large and use progress indication
        long fileSize = file.length();
        boolean isLargeFile = fileSize > 100 * 1024; // 100KB threshold

        if (isLargeFile) {
            // Use progress manager for large files
            progressManager.executeWithProgress(
                    "Loading " + file.getName(),
                    () -> {
                        try {
                            return Files.readString(file.toPath());
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
                        }
                    },
                    content -> loadSchematronContent(file, content),
                    error -> {
                        logger.error("Failed to load Schematron file: {}", file.getAbsolutePath(), error);
                        showError("File Load Error", "Failed to load file: " + error.getMessage());
                    }
            );
        } else {
            // Load small files directly
            try {
                String content = Files.readString(file.toPath());
                loadSchematronContent(file, content);
            } catch (IOException e) {
                logger.error("Failed to load Schematron file: {}", file.getAbsolutePath(), e);
                showError("File Load Error", "Failed to load file: " + e.getMessage());
            }
        }
    }

    /**
     * Load Schematron content into the editor
     */
    private void loadSchematronContent(File file, String content) {
        Platform.runLater(() -> {
            if (xmlCodeEditor != null) {
                // Ensure Schematron mode is active before setting content
                xmlCodeEditor.setSchematronMode(true);
                
                // Check if auto-format is enabled for Schematron files
                String contentToLoad = content;
                if (propertiesService.isSchematronPrettyPrintOnLoad()) {
                    try {
                        // Format the content before loading
                        String formattedContent = XmlService.prettyFormat(content, 2);
                        if (formattedContent != null) {
                            contentToLoad = formattedContent;
                            logger.info("Schematron file auto-formatted on load");
                        }
                    } catch (Exception e) {
                        logger.warn("Could not auto-format Schematron file on load: {}", e.getMessage());
                        // Use original content if formatting fails
                    }
                }
                
                xmlCodeEditor.setText(contentToLoad);
                xmlCodeEditor.refreshHighlighting();

                // Update current file reference
                currentSchematronFile = file;

                // Update last open directory
                if (file.getParent() != null) {
                    propertiesService.setLastOpenDirectory(file.getParent());
                }

                // Notify integration service of Schematron file change
                if (parentController != null && parentController.getIntegrationService() != null) {
                    parentController.getIntegrationService().setCurrentSchematronFile(file);
                }

                // Add file to recent files
                if (parentController != null) {
                    parentController.addFileToRecentFiles(file);
                }

                // Update tab title to show filename
                if (tabPane != null && codeTab != null) {
                    codeTab.setText("Code - " + file.getName());
                }

                // Update tester with current file
                if (tester != null) {
                    tester.setSchematronFile(file);
                }

                // Update documentation generator with current file
                if (docGenerator != null) {
                    docGenerator.setSchematronFile(file);
                }

                // Perform error detection
                performErrorDetection();

                logger.info("Schematron file loaded successfully: {}", file.getName());
            } else {
                logger.error("Schematron code editor is not initialized");
            }
        });
    }

    /**
     * Create a new empty Schematron file
     */
    @FXML
    private void newSchematronFile() {
        createNewSchematron();
    }

    /**
     * Save As button action - enhanced version with current file pre-population
     */
    @FXML
    private void saveAsSchematron() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Schematron File As");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schematron files (*.sch)", "*.sch"),
                new FileChooser.ExtensionFilter("Schematron files (*.schematron)", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        // Set initial directory and filename from current file if available
        if (currentSchematronFile != null) {
            // Set directory from current file
            if (currentSchematronFile.getParent() != null) {
                File parentDir = new File(currentSchematronFile.getParent());
                if (parentDir.exists() && parentDir.isDirectory()) {
                    fileChooser.setInitialDirectory(parentDir);
                }
            }
            // Set initial filename
            fileChooser.setInitialFileName(currentSchematronFile.getName());
        } else {
            // Fallback to last open directory if no current file
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }
        }

        File selectedFile = fileChooser.showSaveDialog(xmlCodeEditor.getScene().getWindow());
        if (selectedFile != null) {
            try {
                String content = xmlCodeEditor.getText();
                Files.writeString(selectedFile.toPath(), content);

                // Update current file reference
                currentSchematronFile = selectedFile;

                // Update last open directory
                if (selectedFile.getParent() != null) {
                    propertiesService.setLastOpenDirectory(selectedFile.getParent());
                }

                // Add file to recent files
                if (parentController != null) {
                    parentController.addFileToRecentFiles(selectedFile);
                }

                // Update tab title
                if (tabPane != null && codeTab != null) {
                    codeTab.setText("Code - " + selectedFile.getName());
                }

                logger.info("Schematron file saved as: {}", selectedFile.getAbsolutePath());
                showInfo("File Saved", "File saved as: " + selectedFile.getName());

            } catch (IOException e) {
                logger.error("Failed to save Schematron file as", e);
                showError("Save Error", "Failed to save file: " + e.getMessage());
            }
        }
    }

    /**
     * Save the current Schematron file
     */
    public void saveSchematronFile() {
        if (currentSchematronFile == null) {
            saveSchematronFileAs();
            return;
        }

        try {
            String content = xmlCodeEditor.getText();
            Files.writeString(currentSchematronFile.toPath(), content);

            logger.info("Schematron file saved: {}", currentSchematronFile.getAbsolutePath());
            showInfo("File Saved", "File saved successfully: " + currentSchematronFile.getName());

        } catch (IOException e) {
            logger.error("Failed to save Schematron file", e);
            showError("Save Error", "Failed to save file: " + e.getMessage());
        }
    }

    /**
     * Save the current Schematron file with a new name
     */
    public void saveSchematronFileAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Schematron File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schematron files (*.sch)", "*.sch"),
                new FileChooser.ExtensionFilter("Schematron files (*.schematron)", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        // Set initial directory and filename from current file if available
        if (currentSchematronFile != null) {
            // Set directory from current file
            if (currentSchematronFile.getParent() != null) {
                File parentDir = new File(currentSchematronFile.getParent());
                if (parentDir.exists() && parentDir.isDirectory()) {
                    fileChooser.setInitialDirectory(parentDir);
                }
            }
            // Set initial filename
            fileChooser.setInitialFileName(currentSchematronFile.getName());
        } else {
            // Fallback to last open directory if no current file
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }
        }

        File file = fileChooser.showSaveDialog(xmlCodeEditor.getScene().getWindow());
        if (file != null) {
            currentSchematronFile = file;

            // Update last open directory
            if (file.getParent() != null) {
                propertiesService.setLastOpenDirectory(file.getParent());
            }

            // Add file to recent files
            if (parentController != null) {
                parentController.addFileToRecentFiles(file);
            }
            
            saveSchematronFile();
        }
    }

    /**
     * Create a completely empty Schematron file (for users who want to start from scratch)
     */
    public void createEmptySchematron() {
        Platform.runLater(() -> {
            // Ensure Schematron mode is active before setting empty content
            xmlCodeEditor.setSchematronMode(true);
            
            xmlCodeEditor.setText("");
            
            currentSchematronFile = null;
            codeTab.setText("Code - Empty Schematron");

            // Clear tester file reference
            if (tester != null) {
                tester.setSchematronFile(null);
            }

            // Clear documentation generator file reference
            if (docGenerator != null) {
                docGenerator.setSchematronFile(null);
            }

            logger.info("Empty Schematron file created - Schematron mode active for IntelliSense");
        });
    }

    /**
     * Create a new Schematron file from template
     */
    public void createNewSchematron() {
        String template = generateBasicSchematronTemplate();

        Platform.runLater(() -> {
            // Ensure Schematron mode is active before setting text
            xmlCodeEditor.setSchematronMode(true);
            
            xmlCodeEditor.setText(template);
            xmlCodeEditor.refreshHighlighting();

            currentSchematronFile = null;
            codeTab.setText("Code - New Schematron");

            // Clear tester file reference
            if (tester != null) {
                tester.setSchematronFile(null);
            }

            // Clear documentation generator file reference
            if (docGenerator != null) {
                docGenerator.setSchematronFile(null);
            }

            // Perform error detection on template
            performErrorDetection();

            logger.info("New Schematron file created from template");
        });
    }

    // ========== Toolbar Actions ==========

    /**
     * Insert a new rule template
     */
    private void insertNewRule() {
        String ruleTemplate = generateRuleTemplate();
        xmlCodeEditor.getCodeArea().insertText(xmlCodeEditor.getCodeArea().getCaretPosition(), ruleTemplate);
        performErrorDetection();
        logger.debug("New rule template inserted");
    }

    /**
     * Insert a new pattern template
     */
    private void insertNewPattern() {
        String patternTemplate = generatePatternTemplate();
        xmlCodeEditor.getCodeArea().insertText(xmlCodeEditor.getCodeArea().getCaretPosition(), patternTemplate);
        performErrorDetection();
        logger.debug("New pattern template inserted");
    }

    /**
     * Format the current Schematron file
     */
    @FXML
    private void formatSchematron() {
        if (xmlCodeEditor == null || xmlCodeEditor.getText().isEmpty()) {
            showWarning("Format", "No Schematron content to format");
            return;
        }

        try {
            String currentContent = xmlCodeEditor.getText();
            
            // Check if auto-format is enabled for Schematron files
            int indentSize = 2; // Default to 2 spaces
            
            // Use the XmlService pretty format method
            String formattedContent = XmlService.prettyFormat(currentContent, indentSize);
            
            if (formattedContent != null && !formattedContent.equals(currentContent)) {
                // Update the editor with formatted content
                xmlCodeEditor.setText(formattedContent);
                xmlCodeEditor.refreshHighlighting();
                
                showInfo("Format", "Schematron file formatted successfully");
                logger.info("Schematron file formatted");
            } else if (formattedContent == null) {
                showError("Format Error", "Failed to format Schematron file. Please check the XML syntax.");
            } else {
                showInfo("Format", "Schematron file is already properly formatted");
            }
        } catch (Exception e) {
            logger.error("Error formatting Schematron file", e);
            showError("Format Error", "Failed to format: " + e.getMessage());
        }
    }

    /**
     * Validate the current Schematron file
     */
    private void validateSchematron() {
        if (xmlCodeEditor == null || xmlCodeEditor.getText().isEmpty()) {
            showWarning("Validation", "No Schematron content to validate");
            return;
        }

        try {
            String content = xmlCodeEditor.getText();
            SchematronErrorDetector.SchematronErrorResult result = errorDetector.detectErrors(content);

            // Display validation results
            displayValidationResults(result);

            logger.info("Schematron validation completed: {} errors, {} warnings",
                    result.getErrors().size(), result.getWarnings().size());

        } catch (Exception e) {
            logger.error("Validation error", e);
            showError("Validation Error", e.getMessage());
        }
    }

    /**
     * Test rules against sample XML
     */
    private void testRules() {
        // Switch to test tab to show testing interface
        if (tabPane != null && testTab != null) {
            tabPane.getSelectionModel().select(testTab);
        }

        // Ensure current file is set in tester
        if (tester != null) {
            tester.setSchematronFile(currentSchematronFile);
        }

        logger.info("Switched to testing tab for rule testing");
    }

    // ========== Template Generation ==========

    /**
     * Generate a basic Schematron template
     */
    private String generateBasicSchematronTemplate() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                        queryBinding="xslt2">
                
                    <title>Schematron Rules</title>
                
                    <ns prefix="example" uri="http://example.org/ns"/>
                
                    <pattern>
                        <title>Basic Rules</title>
                
                        <rule context="/">
                            <assert test="true()">Root element validation</assert>
                        </rule>
                
                    </pattern>
                
                </schema>
                """;
    }

    /**
     * Generate a rule template
     */
    private String generateRuleTemplate() {
        return """
                
                    <rule context="">
                        <assert test="">Assertion message</assert>
                        <report test="">Report message</report>
                    </rule>
                """;
    }

    /**
     * Generate a pattern template
     */
    private String generatePatternTemplate() {
        return """
                
                <pattern>
                    <title>Pattern Title</title>
                
                    <rule context="">
                        <assert test="">Assertion message</assert>
                    </rule>
                
                </pattern>
                """;
    }

    // ========== Helper Methods ==========

    /**
     * Perform error detection on current content
     */
    private void performErrorDetection() {
        if (xmlCodeEditor != null && errorDetector != null) {
            String content = xmlCodeEditor.getText();
            if (!content.trim().isEmpty()) {
                // Run validation in background to avoid UI blocking
                Platform.runLater(() -> {
                    try {
                        SchematronErrorDetector.SchematronErrorResult result = errorDetector.detectErrors(content);
                        updateErrorMarkersInUI(result);
                        logger.debug("Error detection completed: {} issues found",
                                result.getAllIssues().size());
                    } catch (Exception e) {
                        logger.warn("Error detection failed", e);
                    }
                });
            }
        }
    }

    /**
     * Display validation results to user
     */
    private void displayValidationResults(SchematronErrorDetector.SchematronErrorResult result) {
        StringBuilder message = new StringBuilder();

        if (!result.hasAnyIssues()) {
            showInfo("Validation Complete", "No issues found. Document is valid!");
            return;
        }

        message.append("Validation Results:\n\n");

        if (result.hasErrors()) {
            message.append("ERRORS (").append(result.getErrors().size()).append("):\n");
            for (SchematronErrorDetector.SchematronError error : result.getErrors()) {
                message.append("  • Line ").append(error.line())
                        .append(": ").append(error.message()).append("\n");
            }
            message.append("\n");
        }

        if (result.hasWarnings()) {
            message.append("WARNINGS (").append(result.getWarnings().size()).append("):\n");
            for (SchematronErrorDetector.SchematronError warning : result.getWarnings()) {
                message.append("  • Line ").append(warning.line())
                        .append(": ").append(warning.message()).append("\n");
            }
            message.append("\n");
        }

        if (result.hasInfos()) {
            message.append("INFO (").append(result.getInfos().size()).append("):\n");
            for (SchematronErrorDetector.SchematronError info : result.getInfos()) {
                message.append("  • Line ").append(info.line())
                        .append(": ").append(info.message()).append("\n");
            }
        }

        if (result.hasErrors()) {
            showError("Validation Results", message.toString());
        } else {
            showWarning("Validation Results", message.toString());
        }
    }

    /**
     * Update UI with error markers based on validation results
     */
    private void updateErrorMarkersInUI(SchematronErrorDetector.SchematronErrorResult result) {
        if (errorsContent != null) {
            // Clear existing error markers
            errorsContent.getChildren().clear();

            if (result.hasAnyIssues()) {
                // Add error summary
                Label summaryLabel = new Label(String.format("Found %d issues (%d errors, %d warnings, %d info)",
                        result.getAllIssues().size(), result.getErrors().size(),
                        result.getWarnings().size(), result.getInfos().size()));
                summaryLabel.getStyleClass().add("error-summary");
                summaryLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5px;");
                errorsContent.getChildren().add(summaryLabel);

                // Add separator
                Separator separator = new Separator();
                errorsContent.getChildren().add(separator);

                // Add errors
                if (result.hasErrors()) {
                    addErrorSection("ERRORS", result.getErrors(), "error-item");
                }

                // Add warnings
                if (result.hasWarnings()) {
                    addErrorSection("WARNINGS", result.getWarnings(), "warning-item");
                }

                // Add info
                if (result.hasInfos()) {
                    addErrorSection("INFO", result.getInfos(), "info-item");
                }
            } else {
                // Show success message
                Label successLabel = new Label("✓ No validation errors found");
                successLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-padding: 10px;");
                errorsContent.getChildren().add(successLabel);
            }
        }
    }

    /**
     * Add an error section to the UI
     */
    private void addErrorSection(String title, java.util.List<SchematronErrorDetector.SchematronError> errors, String styleClass) {
        if (errors.isEmpty()) return;

        Label sectionLabel = new Label(title + " (" + errors.size() + "):");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5px 0px 2px 0px;");
        errorsContent.getChildren().add(sectionLabel);

        for (SchematronErrorDetector.SchematronError error : errors) {
            HBox errorItem = new HBox(5);
            errorItem.getStyleClass().add(styleClass);
            errorItem.setStyle("-fx-padding: 2px 10px; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1px 0;");

            // Line number label (clickable)
            Label lineLabel = new Label("Line " + error.line() + ":");
            lineLabel.setStyle("-fx-text-fill: #666; -fx-min-width: 60px; -fx-cursor: hand;");
            lineLabel.setOnMouseClicked(e -> navigateToLine(error.line()));

            // Error message
            Label messageLabel = new Label(error.message());
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(messageLabel, Priority.ALWAYS);

            // Set different colors based on error type
            if (styleClass.contains("error")) {
                messageLabel.setStyle("-fx-text-fill: #c53030;");
                lineLabel.setStyle(lineLabel.getStyle() + " -fx-text-fill: #c53030;");
            } else if (styleClass.contains("warning")) {
                messageLabel.setStyle("-fx-text-fill: #d69e2e;");
                lineLabel.setStyle(lineLabel.getStyle() + " -fx-text-fill: #d69e2e;");
            } else {
                messageLabel.setStyle("-fx-text-fill: #3182ce;");
                lineLabel.setStyle(lineLabel.getStyle() + " -fx-text-fill: #3182ce;");
            }

            errorItem.getChildren().addAll(lineLabel, messageLabel);
            errorsContent.getChildren().add(errorItem);
        }
    }

    /**
     * Navigate to a specific line in the code editor
     */
    private void navigateToLine(int lineNumber) {
        if (xmlCodeEditor != null) {
            try {
                // Calculate position for the specified line
                String text = xmlCodeEditor.getText();
                String[] lines = text.split("\n");

                int position = 0;
                for (int i = 0; i < Math.min(lineNumber - 1, lines.length); i++) {
                    position += lines[i].length() + 1; // +1 for newline character
                }

                // Move cursor to the calculated position
                xmlCodeEditor.getCodeArea().moveTo(position);
                xmlCodeEditor.requestFocus();

                // Scroll to make the line visible
                xmlCodeEditor.getCodeArea().requestFollowCaret();

                logger.debug("Navigated to line {} at position {}", lineNumber, position);
            } catch (Exception e) {
                logger.warn("Could not navigate to line {}: {}", lineNumber, e.getMessage());
            }
        }
    }

    /**
     * Transfer code from visual builder to code editor
     */
    public void transferFromVisualBuilder() {
        if (visualBuilder != null && xmlCodeEditor != null) {
            String generatedCode = visualBuilder.getGeneratedCode();
            xmlCodeEditor.setText(generatedCode);

            // Switch to code tab to show result
            if (tabPane != null && codeTab != null) {
                tabPane.getSelectionModel().select(codeTab);
            }

            // Perform error detection on generated code
            performErrorDetection();

            logger.info("Transferred code from visual builder to code editor");
        }
    }

    /**
     * Clear all visual builder data
     */
    public void clearVisualBuilder() {
        if (visualBuilder != null) {
            visualBuilder.clearAll();
            logger.debug("Cleared visual builder data");
        }
    }

    /**
     * Set the parent controller reference
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
        logger.debug("Parent controller set for SchematronController");
    }

    /**
     * Show error dialog
     * @deprecated Use {@link DialogHelper#showError(String, String, String)} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private void showError(String title, String message) {
        DialogHelper.showError(title, "", message);
    }

    /**
     * Show information dialog
     * @deprecated Use {@link DialogHelper#showInformation(String, String, String)} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private void showInfo(String title, String message) {
        DialogHelper.showInformation(title, "", message);
    }

    /**
     * Show warning dialog
     * @deprecated Use {@link DialogHelper#showWarning(String, String, String)} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private void showWarning(String title, String message) {
        DialogHelper.showWarning(title, "", message);
    }

    // ======================================================================
    // Favorites functionality (unified FavoritesPanel)
    // ======================================================================

    private void setupFavorites() {
        // Setup unified FavoritesPanel
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
        }

        // Setup add to favorites button
        if (addToFavoritesBtn != null) {
            addToFavoritesBtn.setOnAction(e -> addCurrentToFavorites());
        }

        // Setup toggle favorites button
        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }

        // Initially hide favorites panel
        if (favoritesPanel != null && mainSplitPane != null) {
            mainSplitPane.getItems().remove(favoritesPanel);
        }

        logger.debug("Favorites panel setup completed");
    }

    /**
     * Adds the current file to favorites using a dialog.
     */
    private void addCurrentToFavorites() {
        File currentFile = getCurrentFile();
        if (currentFile != null) {
            // Show the favorites panel if hidden
            if (mainSplitPane != null && !mainSplitPane.getItems().contains(favoritesPanel)) {
                toggleFavoritesPanel();
            }
            // Use a simple dialog approach
            TextInputDialog dialog = new TextInputDialog(currentFile.getName());
            dialog.setTitle("Add to Favorites");
            dialog.setHeaderText("Add " + currentFile.getName() + " to favorites");
            dialog.setContentText("Enter alias (optional):");

            dialog.showAndWait().ifPresent(alias -> {
                org.fxt.freexmltoolkit.domain.FileFavorite favorite =
                    new org.fxt.freexmltoolkit.domain.FileFavorite(
                        alias.isEmpty() ? currentFile.getName() : alias,
                        currentFile.getAbsolutePath(),
                        "Schematron Rules"
                    );
                ServiceRegistry.get(FavoritesService.class).addFavorite(favorite);
                showAlert(Alert.AlertType.INFORMATION, "Success", currentFile.getName() + " has been added to favorites.");
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "No File Loaded", "Please load a Schematron file before adding to favorites.");
        }
    }

    /**
     * Toggles the visibility of the favorites panel using SplitPane.
     */
    private void toggleFavoritesPanel() {
        if (favoritesPanel == null || mainSplitPane == null) {
            return;
        }

        boolean isCurrentlyShown = mainSplitPane.getItems().contains(favoritesPanel);

        if (isCurrentlyShown) {
            mainSplitPane.getItems().remove(favoritesPanel);
            logger.debug("Favorites panel hidden");
        } else {
            mainSplitPane.getItems().add(favoritesPanel);
            mainSplitPane.setDividerPositions(0.75);
            logger.debug("Favorites panel shown");
        }
    }

    // FavoritesParentController interface implementation

    @Override
    public void loadFileToNewTab(File file) {
        if (file == null || !file.exists()) {
            showAlert(Alert.AlertType.ERROR, "File Not Found", "The selected file does not exist.");
            return;
        }

        try {
            loadSchematronFile(file);
            logger.info("Loaded file from favorites: {}", file.getName());
        } catch (Exception e) {
            logger.error("Failed to load file from favorites", e);
            showAlert(Alert.AlertType.ERROR, "Load Error", "Failed to load file: " + e.getMessage());
        }
    }

    @Override
    public File getCurrentFile() {
        return currentSchematronFile;
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ======================================================================
    // Test Tab functionality
    // ======================================================================

    /**
     * Initialize the test tab components
     */
    private void initializeTestTab() {
        logger.info("Initializing test tab components");

        // Initialize TableView if it exists
        if (testFilesTable != null) {
            setupTestFilesTable();
        }

        logger.debug("Test tab components initialized");
    }

    /**
     * Setup the test files table
     */
    private void setupTestFilesTable() {
        // Find and initialize table columns
        ObservableList<TableColumn<TestFile, ?>> columns = testFilesTable.getColumns();
        if (columns.size() >= 4) {
            // Setup columns based on order in FXML
            TableColumn<TestFile, String> filenameCol = (TableColumn<TestFile, String>) columns.get(0);
            TableColumn<TestFile, String> statusCol = (TableColumn<TestFile, String>) columns.get(1);
            TableColumn<TestFile, Integer> violationsCol = (TableColumn<TestFile, Integer>) columns.get(2);
            TableColumn<TestFile, Integer> warningsCol = (TableColumn<TestFile, Integer>) columns.get(3);

            filenameCol.setCellValueFactory(new PropertyValueFactory<>("filename"));
            statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
            violationsCol.setCellValueFactory(new PropertyValueFactory<>("violations"));
            warningsCol.setCellValueFactory(new PropertyValueFactory<>("warnings"));

            // Add custom cell factory for status column with color coding
            statusCol.setCellFactory(column -> new TableCell<TestFile, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        // Color coding based on status
                        switch (item) {
                            case "Passed" -> setStyle("-fx-text-fill: #28a745;");
                            case "Failed" -> setStyle("-fx-text-fill: #dc3545;");
                            case "Testing..." -> setStyle("-fx-text-fill: #007bff;");
                            default -> setStyle("");
                        }
                    }
                }
            });

            // Add Actions column if it exists
            if (columns.size() >= 5) {
                TableColumn<TestFile, String> actionsCol = (TableColumn<TestFile, String>) columns.get(4);
                actionsCol.setCellFactory(column -> new TableCell<TestFile, String>() {
                    private final Button viewButton = new Button("View");
                    private final Button removeButton = new Button("Remove");
                    private final HBox buttonBox = new HBox(5, viewButton, removeButton);

                    {
                        viewButton.setStyle("-fx-font-size: 10px;");
                        removeButton.setStyle("-fx-font-size: 10px;");

                        viewButton.setOnAction(e -> {
                            TestFile testFile = getTableRow().getItem();
                            if (testFile != null) {
                                viewTestResults(testFile);
                            }
                        });

                        removeButton.setOnAction(e -> {
                            TestFile testFile = getTableRow().getItem();
                            if (testFile != null) {
                                testFiles.remove(testFile);
                            }
                        });
                    }

                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(buttonBox);
                        }
                    }
                });
            }
        }

        // Set items
        testFilesTable.setItems(testFiles);
    }

    /**
     * Load schema from file for testing
     */
    @FXML
    private void loadSchemaFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Schematron Schema");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schematron files (*.sch)", "*.sch"),
                new FileChooser.ExtensionFilter("Schematron files (*.schematron)", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File file = fileChooser.showOpenDialog(testFilesTable.getScene().getWindow());
        if (file != null && file.exists()) {
            testSchematronFile = file;

            // Update last open directory
            if (file.getParent() != null) {
                propertiesService.setLastOpenDirectory(file.getParent());
            }
            
            // Update the label to show the loaded file
            if (loadedSchemaLabel != null) {
                loadedSchemaLabel.setText(file.getName());
                loadedSchemaLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
            }
            
            showInfo("Schema Loaded", "Schematron schema loaded: " + file.getName());
            logger.info("Loaded test schema: {}", file.getAbsolutePath());
        }
    }

    /**
     * Use current schema from code editor for testing
     */
    @FXML
    private void useCurrentSchema() {
        if (xmlCodeEditor == null || xmlCodeEditor.getText().isEmpty()) {
            showWarning("No Schema", "No Schematron schema in the code editor");
            return;
        }

        // Save current content to a temporary file or use as-is
        testSchematronFile = currentSchematronFile;

        if (testSchematronFile == null) {
            // Create temporary file with current content
            try {
                Path tempFile = Files.createTempFile("schematron_test_", ".sch");
                Files.writeString(tempFile, xmlCodeEditor.getText());
                testSchematronFile = tempFile.toFile();
                testSchematronFile.deleteOnExit();
                
                // Update the label to show current editor is being used
                if (loadedSchemaLabel != null) {
                    loadedSchemaLabel.setText("Current Editor Content (temp)");
                    loadedSchemaLabel.setTooltip(new Tooltip("Using temporary file from current editor content"));
                }
                
                showInfo("Schema Ready", "Using current editor content for testing");
            } catch (IOException e) {
                logger.error("Failed to create temporary schema file", e);
                showError("Error", "Failed to prepare schema for testing: " + e.getMessage());
            }
        } else {
            // Update the label to show the current file
            if (loadedSchemaLabel != null) {
                loadedSchemaLabel.setText(testSchematronFile.getName());
                loadedSchemaLabel.setTooltip(new Tooltip(testSchematronFile.getAbsolutePath()));
            }
            
            showInfo("Schema Ready", "Using current schema: " + testSchematronFile.getName());
        }
    }

    /**
     * Add XML file for testing
     */
    @FXML
    private void addXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add XML File for Testing");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(testFilesTable.getScene().getWindow());
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            // Update last open directory from the first selected file
            if (selectedFiles.get(0).getParent() != null) {
                propertiesService.setLastOpenDirectory(selectedFiles.get(0).getParent());
            }
            
            for (File file : selectedFiles) {
                // Check if file is already in the list
                boolean exists = testFiles.stream()
                        .anyMatch(tf -> tf.getFile().equals(file));

                if (!exists) {
                    testFiles.add(new TestFile(file));
                    logger.debug("Added test file: {}", file.getName());
                }
            }

            showInfo("Files Added", "Added " + selectedFiles.size() + " file(s) for testing");
        }
    }

    /**
     * Add folder of XML files for testing
     */
    @FXML
    private void addFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder with XML Files");

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                directoryChooser.setInitialDirectory(lastDir);
            }
        }

        File directory = directoryChooser.showDialog(testFilesTable.getScene().getWindow());
        if (directory != null && directory.isDirectory()) {
            // Update last open directory
            propertiesService.setLastOpenDirectory(directory.getAbsolutePath());
            try {
                List<File> xmlFiles = Files.walk(directory.toPath())
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .filter(f -> f.getName().toLowerCase().endsWith(".xml"))
                        .collect(Collectors.toList());

                int addedCount = 0;
                for (File file : xmlFiles) {
                    boolean exists = testFiles.stream()
                            .anyMatch(tf -> tf.getFile().equals(file));

                    if (!exists) {
                        testFiles.add(new TestFile(file));
                        addedCount++;
                    }
                }

                showInfo("Folder Added", "Found and added " + addedCount + " XML file(s) from " + directory.getName());
                logger.info("Added {} XML files from folder: {}", addedCount, directory.getAbsolutePath());

            } catch (IOException e) {
                logger.error("Error scanning folder for XML files", e);
                showError("Error", "Failed to scan folder: " + e.getMessage());
            }
        }
    }

    /**
     * Load sample XML files for testing
     */
    @FXML
    private void loadSampleXmls() {
        // Check for sample files in resources or a predefined location
        File samplesDir = new File("release/examples/xml");

        if (!samplesDir.exists() || !samplesDir.isDirectory()) {
            // Try alternative location
            samplesDir = new File("examples/xml");
        }

        if (samplesDir.exists() && samplesDir.isDirectory()) {
            File[] xmlFiles = samplesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

            if (xmlFiles != null && xmlFiles.length > 0) {
                int addedCount = 0;
                for (File file : xmlFiles) {
                    boolean exists = testFiles.stream()
                            .anyMatch(tf -> tf.getFile().equals(file));

                    if (!exists) {
                        testFiles.add(new TestFile(file));
                        addedCount++;
                    }
                }

                showInfo("Samples Loaded", "Loaded " + addedCount + " sample XML file(s)");
                logger.info("Loaded {} sample XML files", addedCount);
            } else {
                showWarning("No Samples", "No sample XML files found in " + samplesDir.getAbsolutePath());
            }
        } else {
            showWarning("No Samples", "Sample directory not found. Please add XML files manually.");
        }
    }

    /**
     * Run all tests - also callable from MainController for F5 shortcut
     */
    @FXML
    public void runAllTests() {
        if (testSchematronFile == null) {
            showWarning("No Schema", "Please load a Schematron schema first");
            return;
        }

        if (testFiles.isEmpty()) {
            showWarning("No Test Files", "Please add XML files to test");
            return;
        }

        logger.info("Running all tests with schema: {}", testSchematronFile.getName());

        // Update status for all files
        for (TestFile testFile : testFiles) {
            testFile.setStatus("Testing...");
            testFile.setViolations(0);
            testFile.setWarnings(0);
        }

        // Run tests in background
        Platform.runLater(() -> {
            boolean schematronLoadError = false;
            for (TestFile testFile : testFiles) {
                runTestForFile(testFile);
                // Check if we encountered a Schematron load error
                if ("Error".equals(testFile.getStatus()) && 
                    testFile.getDetailedResults() != null && 
                    !testFile.getDetailedResults().isEmpty() &&
                    testFile.getDetailedResults().get(0).message().contains("Schematron load error")) {
                    schematronLoadError = true;
                    // Stop processing other files
                    break;
                }
            }

            // Only show summary if we didn't encounter a Schematron load error
            if (!schematronLoadError) {
                // Show summary
                int passed = 0;
                int failed = 0;
                for (TestFile tf : testFiles) {
                    if ("Passed".equals(tf.getStatus())) {
                        passed++;
                    } else if ("Failed".equals(tf.getStatus())) {
                        failed++;
                    }
                }

                showInfo("Test Results",
                        String.format("Tests completed:\n✓ Passed: %d\n✗ Failed: %d", passed, failed));
            }

            // Update results tabs
            updateResultsTabs();
        });
    }

    /**
     * Test single selected file
     */
    @FXML
    private void testSingleFile() {
        TestFile selected = testFilesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showWarning("No Selection", "Please select a file to test");
            return;
        }

        if (testSchematronFile == null) {
            showWarning("No Schema", "Please load a Schematron schema first");
            return;
        }

        logger.info("Testing single file: {}", selected.getFilename());
        runTestForFile(selected);

        // Update results tabs
        updateResultsTabs();
    }

    /**
     * Run test for a specific file
     */
    private void runTestForFile(TestFile testFile) {
        try {
            // Update status
            testFile.setStatus("Testing...");

            long startTime = System.currentTimeMillis();

            // Perform actual Schematron validation
            List<SchematronService.SchematronValidationError> validationErrors =
                    schematronService.validateXmlFile(testFile.getFile(), testSchematronFile);

            // Clear previous detailed results
            testFile.clearDetailedResults();

            // Process validation results
            int violations = 0;
            int warnings = 0;

            for (SchematronService.SchematronValidationError error : validationErrors) {
                if ("error".equals(error.severity())) {
                    violations++;
                    // Convert to TestResult format
                    TestFile.TestResult violation = new TestFile.TestResult(
                            error.ruleId() != null ? error.ruleId() : "rule-" + violations,
                            error.message(),
                            error.context() != null ? error.context() : "unknown",
                            "assert",
                            "Schematron Rule",
                            error.lineNumber()
                    );
                    testFile.addTestResult(violation);
                } else if ("warning".equals(error.severity())) {
                    warnings++;
                    // Convert to TestResult format
                    TestFile.TestResult warning = new TestFile.TestResult(
                            error.ruleId() != null ? error.ruleId() : "rule-warn-" + warnings,
                            error.message(),
                            error.context() != null ? error.context() : "unknown",
                            "report",
                            "Schematron Rule",
                            error.lineNumber()
                    );
                    testFile.addTestResult(warning);
                }
            }

            // Update test file status
            testFile.setViolations(violations);
            testFile.setWarnings(warnings);
            testFile.setStatus(violations > 0 ? "Failed" : "Passed");

            // Calculate and set test duration
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            testFile.setTestDuration(duration + "ms");

            // Update last tested time
            testFile.setLastTested(LocalDateTime.now().format(DATE_FORMATTER));

        } catch (SchematronLoadException e) {
            // Show error popup for Schematron loading issues
            logger.error("Failed to load Schematron file: {}", testSchematronFile.getName(), e);
            testFile.setStatus("Error");
            
            // Show error dialog to user
            Platform.runLater(() -> {
                showError("Schematron Loading Error", 
                    "Failed to load or compile the Schematron file:\n\n" + e.getMessage() + 
                    "\n\nPlease check that the Schematron file is valid and try again.");
            });
            
            // Add error as a test result
            TestFile.TestResult errorResult = new TestFile.TestResult(
                    "error",
                    "Schematron load error: " + e.getMessage(),
                    "N/A",
                    "error",
                    "Schematron Load Error",
                    0
            );
            testFile.clearDetailedResults();
            testFile.addTestResult(errorResult);
            testFile.setViolations(1);
            testFile.setWarnings(0);
            
            // Don't continue validation for other files if Schematron cannot be loaded
        } catch (Exception e) {
            logger.error("Error testing file: {}", testFile.getFilename(), e);
            testFile.setStatus("Error");

            // Add error as a test result
            TestFile.TestResult errorResult = new TestFile.TestResult(
                    "error",
                    "Validation error: " + e.getMessage(),
                    "N/A",
                    "error",
                    "System Error",
                    0
            );
            testFile.clearDetailedResults();
            testFile.addTestResult(errorResult);
            testFile.setViolations(1);
            testFile.setWarnings(0);
        }
    }

    /**
     * Export test results
     */
    @FXML
    private void exportResults() {
        if (testFiles.isEmpty()) {
            showWarning("No Results", "No test results to export");
            return;
        }

        // Show export options dialog
        ExportOptionsResult exportOptions = showExportOptionsDialog();
        if (exportOptions == null) {
            return; // User cancelled
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Test Results");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Report (*.html)", "*.html"),
                new FileChooser.ExtensionFilter("CSV File (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("JSON File (*.json)", "*.json")
        );

        // Set initial directory from properties
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File file = fileChooser.showSaveDialog(testFilesTable.getScene().getWindow());
        if (file != null) {
            try {
                String extension = getFileExtension(file);
                String content = "";

                switch (extension.toLowerCase()) {
                    case "html" -> content = generateHtmlReport(exportOptions.includeDetails);
                    case "csv" -> content = generateCsvReport(exportOptions.includeDetails);
                    case "json" -> content = generateJsonReport(exportOptions.includeDetails);
                    default -> content = generateTextReport(exportOptions.includeDetails);
                }

                Files.writeString(file.toPath(), content);

                // Update last open directory
                if (file.getParent() != null) {
                    propertiesService.setLastOpenDirectory(file.getParent());
                }
                
                showInfo("Export Complete", "Test results exported to: " + file.getName());
                logger.info("Exported test results to: {} (detailed: {})", file.getAbsolutePath(), exportOptions.includeDetails);

            } catch (IOException e) {
                logger.error("Failed to export test results", e);
                showError("Export Error", "Failed to export results: " + e.getMessage());
            }
        }
    }

    /**
     * Show export options dialog
     */
    private ExportOptionsResult showExportOptionsDialog() {
        Dialog<ExportOptionsResult> dialog = new Dialog<>();
        dialog.setTitle("Export Options");
        dialog.setHeaderText("Choose export options");

        // Set the button types
        ButtonType exportButtonType = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        CheckBox includeDetailsCheckBox = new CheckBox("Include detailed test results");
        includeDetailsCheckBox.setSelected(false);

        Label descriptionLabel = new Label("When enabled, the export will include detailed information about each rule violation and warning, including location, rule ID, and message.");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        grid.add(includeDetailsCheckBox, 0, 0);
        grid.add(descriptionLabel, 0, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == exportButtonType) {
                return new ExportOptionsResult(includeDetailsCheckBox.isSelected());
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
         * Helper class for export options
         */
        private record ExportOptionsResult(boolean includeDetails) {
    }

    /**
     * Remove selected files from test list
     */
    @FXML
    private void removeSelected() {
        ObservableList<TestFile> selectedItems = testFilesTable.getSelectionModel().getSelectedItems();

        if (selectedItems.isEmpty()) {
            showWarning("No Selection", "Please select files to remove");
            return;
        }

        testFiles.removeAll(new ArrayList<>(selectedItems));
        logger.debug("Removed {} files from test list", selectedItems.size());
    }

    /**
     * Remove all files from test list
     */
    @FXML
    private void removeAll() {
        if (testFiles.isEmpty()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Remove All");
        confirm.setHeaderText("Remove all test files?");
        confirm.setContentText("This will clear all " + testFiles.size() + " file(s) from the test list.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            testFiles.clear();
            logger.info("Cleared all test files");
        }
    }

    /**
     * View detailed test results for a file
     */
    private void viewTestResults(TestFile testFile) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Test Results - " + testFile.getFilename());
        alert.setHeaderText("Validation Results");

        StringBuilder content = new StringBuilder();
        content.append(String.format(
                "File: %s\n" +
                        "Status: %s\n" +
                        "Violations: %d\n" +
                        "Warnings: %d\n" +
                        "Duration: %s\n" +
                        "Last Tested: %s\n\n",
                testFile.getFilename(),
                testFile.getStatus(),
                testFile.getViolations(),
                testFile.getWarnings(),
                testFile.getTestDuration(),
                testFile.getLastTested()
        ));

        // Add detailed results if available
        if (testFile.getDetailedResults() != null && !testFile.getDetailedResults().isEmpty()) {
            content.append("Detailed Results:\n");
            content.append("================\n\n");

            for (TestFile.TestResult result : testFile.getDetailedResults()) {
                content.append(String.format(
                        "[%s] %s\n" +
                                "  Location: %s (Line %d)\n" +
                                "  Rule ID: %s\n" +
                                "  Pattern: %s\n\n",
                        result.type().toUpperCase(),
                        result.message(),
                        result.location(),
                        result.lineNumber(),
                        result.ruleId() != null ? result.ruleId() : "N/A",
                        result.pattern() != null ? result.pattern() : "N/A"
                ));
            }
        } else if (testFile.getViolations() > 0 || testFile.getWarnings() > 0) {
            content.append("Detailed results not available.\n");
            content.append("Run the test again to capture detailed information.");
        } else {
            content.append("No detailed results - file passed validation.");
        }

        alert.setContentText(content.toString());

        // Make the dialog resizable and larger for detailed content
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(600, 400);
        
        alert.showAndWait();
    }

    /**
     * Generate HTML report
     */
    private String generateHtmlReport(boolean includeDetails) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>Schematron Test Results</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append(".passed { color: #28a745; }\n");
        html.append(".failed { color: #dc3545; }\n");
        html.append(".details { margin-top: 30px; }\n");
        html.append(".file-details { margin-bottom: 25px; border: 1px solid #ccc; padding: 15px; border-radius: 5px; }\n");
        html.append(".file-details h3 { margin-top: 0; color: #333; }\n");
        html.append(".test-result { margin: 5px 0; padding: 8px; border-left: 4px solid #ddd; background-color: #f9f9f9; }\n");
        html.append(".test-result.assert { border-left-color: #dc3545; }\n");
        html.append(".test-result.report { border-left-color: #ffc107; }\n");
        html.append(".test-result.error { border-left-color: #dc3545; }\n");
        html.append(".result-type { font-weight: bold; text-transform: uppercase; font-size: 0.9em; }\n");
        html.append(".result-location { font-style: italic; color: #666; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>Schematron Test Results</h1>\n");
        html.append("<p>Generated: ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("</p>\n");

        if (testSchematronFile != null) {
            html.append("<p>Schema: ").append(testSchematronFile.getName()).append("</p>\n");
        }

        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>File</th><th>Status</th><th>Violations</th><th>Warnings</th>");
        if (includeDetails) {
            html.append("<th>Duration</th>");
        }
        html.append("<th>Last Tested</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");

        for (TestFile tf : testFiles) {
            String statusClass = "Passed".equals(tf.getStatus()) ? "passed" :
                    "Failed".equals(tf.getStatus()) ? "failed" : "";
            html.append("<tr>\n");
            html.append("<td>").append(tf.getFilename()).append("</td>\n");
            html.append("<td class='").append(statusClass).append("'>").append(tf.getStatus()).append("</td>\n");
            html.append("<td>").append(tf.getViolations()).append("</td>\n");
            html.append("<td>").append(tf.getWarnings()).append("</td>\n");
            if (includeDetails) {
                html.append("<td>").append(tf.getTestDuration()).append("</td>\n");
            }
            html.append("<td>").append(tf.getLastTested()).append("</td>\n");
            html.append("</tr>\n");
        }

        html.append("</tbody>\n</table>\n");

        // Add detailed results section if requested
        if (includeDetails) {
            html.append("<div class='details'>\n");
            html.append("<h2>Detailed Test Results</h2>\n");

            for (TestFile tf : testFiles) {
                if (tf.getDetailedResults() != null && !tf.getDetailedResults().isEmpty()) {
                    html.append("<div class='file-details'>\n");
                    html.append("<h3>").append(tf.getFilename()).append("</h3>\n");
                    html.append("<p><strong>Status:</strong> ").append(tf.getStatus()).append("</p>\n");
                    html.append("<p><strong>Test Duration:</strong> ").append(tf.getTestDuration()).append("</p>\n");

                    for (TestFile.TestResult result : tf.getDetailedResults()) {
                        html.append("<div class='test-result ").append(result.type()).append("'>\n");
                        html.append("<span class='result-type'>").append(result.type()).append("</span>: ");
                        html.append(result.message()).append("<br>\n");
                        html.append("<span class='result-location'>Location: ").append(result.location()).append(" (Line ").append(result.lineNumber()).append(")</span><br>\n");
                        if (result.ruleId() != null && !result.ruleId().trim().isEmpty()) {
                            html.append("<strong>Rule ID:</strong> ").append(result.ruleId()).append("<br>\n");
                        }
                        if (result.pattern() != null && !result.pattern().trim().isEmpty()) {
                            html.append("<strong>Pattern:</strong> ").append(result.pattern()).append("\n");
                        }
                        html.append("</div>\n");
                    }
                    html.append("</div>\n");
                } else if ("Failed".equals(tf.getStatus()) || tf.getViolations() > 0 || tf.getWarnings() > 0) {
                    // Show placeholder for files with issues but no detailed results
                    html.append("<div class='file-details'>\n");
                    html.append("<h3>").append(tf.getFilename()).append("</h3>\n");
                    html.append("<p><strong>Status:</strong> ").append(tf.getStatus()).append("</p>\n");
                    html.append("<p><em>Detailed results not available. Run test again to capture detailed information.</em></p>\n");
                    html.append("</div>\n");
                }
            }
            html.append("</div>\n");
        }

        html.append("</body>\n</html>");
        return html.toString();
    }

    /**
     * Generate CSV report
     */
    private String generateCsvReport(boolean includeDetails) {
        StringBuilder csv = new StringBuilder();

        if (includeDetails) {
            csv.append("File,Status,Violations,Warnings,Duration,Last Tested,Rule ID,Message,Location,Line,Type,Pattern\n");

            for (TestFile tf : testFiles) {
                if (tf.getDetailedResults() != null && !tf.getDetailedResults().isEmpty()) {
                    for (TestFile.TestResult result : tf.getDetailedResults()) {
                        csv.append(escapeCsv(tf.getFilename())).append(",");
                        csv.append(tf.getStatus()).append(",");
                        csv.append(tf.getViolations()).append(",");
                        csv.append(tf.getWarnings()).append(",");
                        csv.append(tf.getTestDuration()).append(",");
                        csv.append(tf.getLastTested()).append(",");
                        csv.append(escapeCsv(result.ruleId())).append(",");
                        csv.append(escapeCsv(result.message())).append(",");
                        csv.append(escapeCsv(result.location())).append(",");
                        csv.append(result.lineNumber()).append(",");
                        csv.append(result.type()).append(",");
                        csv.append(escapeCsv(result.pattern())).append("\n");
                    }
                } else {
                    // Add summary row even without detailed results
                    csv.append(escapeCsv(tf.getFilename())).append(",");
                    csv.append(tf.getStatus()).append(",");
                    csv.append(tf.getViolations()).append(",");
                    csv.append(tf.getWarnings()).append(",");
                    csv.append(tf.getTestDuration()).append(",");
                    csv.append(tf.getLastTested()).append(",");
                    csv.append(",,,,\n"); // Empty detailed fields
                }
            }
        } else {
            csv.append("File,Status,Violations,Warnings,Last Tested\n");

            for (TestFile tf : testFiles) {
                csv.append(escapeCsv(tf.getFilename())).append(",");
                csv.append(tf.getStatus()).append(",");
                csv.append(tf.getViolations()).append(",");
                csv.append(tf.getWarnings()).append(",");
                csv.append(tf.getLastTested()).append("\n");
            }
        }

        return csv.toString();
    }

    /**
     * Escape CSV values
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Generate JSON report
     */
    private String generateJsonReport(boolean includeDetails) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\",\n");

        if (testSchematronFile != null) {
            json.append("  \"schema\": \"").append(testSchematronFile.getName()).append("\",\n");
        }

        json.append("  \"includeDetails\": ").append(includeDetails).append(",\n");
        json.append("  \"results\": [\n");

        for (int i = 0; i < testFiles.size(); i++) {
            TestFile tf = testFiles.get(i);
            json.append("    {\n");
            json.append("      \"file\": \"").append(escapeJson(tf.getFilename())).append("\",\n");
            json.append("      \"status\": \"").append(tf.getStatus()).append("\",\n");
            json.append("      \"violations\": ").append(tf.getViolations()).append(",\n");
            json.append("      \"warnings\": ").append(tf.getWarnings()).append(",\n");

            if (includeDetails) {
                json.append("      \"testDuration\": \"").append(tf.getTestDuration()).append("\",\n");
            }

            json.append("      \"lastTested\": \"").append(tf.getLastTested()).append("\"");

            if (includeDetails && tf.getDetailedResults() != null && !tf.getDetailedResults().isEmpty()) {
                json.append(",\n      \"detailedResults\": [\n");

                List<TestFile.TestResult> results = tf.getDetailedResults();
                for (int j = 0; j < results.size(); j++) {
                    TestFile.TestResult result = results.get(j);
                    json.append("        {\n");
                    json.append("          \"ruleId\": \"").append(escapeJson(result.ruleId())).append("\",\n");
                    json.append("          \"message\": \"").append(escapeJson(result.message())).append("\",\n");
                    json.append("          \"location\": \"").append(escapeJson(result.location())).append("\",\n");
                    json.append("          \"lineNumber\": ").append(result.lineNumber()).append(",\n");
                    json.append("          \"type\": \"").append(result.type()).append("\",\n");
                    json.append("          \"pattern\": \"").append(escapeJson(result.pattern())).append("\"\n");
                    json.append("        }");

                    if (j < results.size() - 1) {
                        json.append(",");
                    }
                    json.append("\n");
                }

                json.append("      ]\n");
            } else {
                json.append("\n");
            }
            
            json.append("    }");

            if (i < testFiles.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n}");
        return json.toString();
    }

    /**
     * Escape JSON values
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generate plain text report
     */
    private String generateTextReport(boolean includeDetails) {
        StringBuilder text = new StringBuilder();
        text.append("Schematron Test Results\n");
        text.append("=======================\n\n");
        text.append("Generated: ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\n");

        if (testSchematronFile != null) {
            text.append("Schema: ").append(testSchematronFile.getName()).append("\n");
        }

        text.append("\nTest Results:\n");
        text.append("-------------\n");

        for (TestFile tf : testFiles) {
            text.append("\nFile: ").append(tf.getFilename()).append("\n");
            text.append("  Status: ").append(tf.getStatus()).append("\n");
            text.append("  Violations: ").append(tf.getViolations()).append("\n");
            text.append("  Warnings: ").append(tf.getWarnings()).append("\n");
            if (includeDetails) {
                text.append("  Duration: ").append(tf.getTestDuration()).append("\n");
            }
            text.append("  Last Tested: ").append(tf.getLastTested()).append("\n");

            if (includeDetails && tf.getDetailedResults() != null && !tf.getDetailedResults().isEmpty()) {
                text.append("\n  Detailed Results:\n");
                for (TestFile.TestResult result : tf.getDetailedResults()) {
                    text.append("    [").append(result.type().toUpperCase()).append("] ");
                    text.append(result.message()).append("\n");
                    text.append("      Location: ").append(result.location());
                    text.append(" (Line ").append(result.lineNumber()).append(")\n");
                    if (result.ruleId() != null && !result.ruleId().trim().isEmpty()) {
                        text.append("      Rule ID: ").append(result.ruleId()).append("\n");
                    }
                    if (result.pattern() != null && !result.pattern().trim().isEmpty()) {
                        text.append("      Pattern: ").append(result.pattern()).append("\n");
                    }
                    text.append("\n");
                }
            } else if (includeDetails && ("Failed".equals(tf.getStatus()) || tf.getViolations() > 0 || tf.getWarnings() > 0)) {
                text.append("  (Detailed results not available - run test again to capture)\n");
            }
        }

        return text.toString();
    }

    /**
     * Get file extension
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf + 1);
    }

    // ==================== CODE TAB HANDLERS ====================

    /**
     * Load a Schematron file using file chooser
     */
    @FXML
    private void loadSchematronFile() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Load Schematron File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Schematron files (*.sch)", "*.sch"),
                    new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"),
                    new FileChooser.ExtensionFilter("All files (*.*)", "*.*")
            );

            // Set initial directory from properties
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            } else {
                // Fallback to user's home directory
                File initialDir = new File(System.getProperty("user.home", "."));
                if (initialDir.exists() && initialDir.isDirectory()) {
                    fileChooser.setInitialDirectory(initialDir);
                }
            }

            File selectedFile = fileChooser.showOpenDialog(loadSchematronFileButton.getScene().getWindow());
            if (selectedFile != null && selectedFile.exists()) {
                loadSchematronFile(selectedFile);
                logger.info("Schematron file loaded from: {}", selectedFile.getAbsolutePath());
            }

        } catch (Exception e) {
            logger.error("Error loading Schematron file: {}", e.getMessage(), e);
            showError("Load Error", "Failed to load Schematron file: " + e.getMessage());
        }
    }

    /**
     * Save current Schematron content
     */
    @FXML
    private void saveSchematron() {
        try {
            if (currentSchematronFile != null) {
                // Save to existing file
                saveSchematronFile();
            } else {
                // Save as new file
                saveSchematronAs();
            }
        } catch (Exception e) {
            logger.error("Error saving Schematron: {}", e.getMessage(), e);
            showError("Save Error", "Failed to save Schematron file: " + e.getMessage());
        }
    }

    /**
     * Save Schematron content as new file
     */
    private void saveSchematronAs() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Schematron File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Schematron files (*.sch)", "*.sch"),
                    new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml")
            );

            // Set initial directory and filename
            if (currentSchematronFile != null) {
                // Use current file's directory and name
                fileChooser.setInitialDirectory(currentSchematronFile.getParentFile());
                fileChooser.setInitialFileName(currentSchematronFile.getName());
            } else {
                // Set initial directory from properties
                String lastDirString = propertiesService.getLastOpenDirectory();
                if (lastDirString != null) {
                    File lastDir = new File(lastDirString);
                    if (lastDir.exists() && lastDir.isDirectory()) {
                        fileChooser.setInitialDirectory(lastDir);
                    }
                } else {
                    // Fallback to user's home directory
                    File initialDir = new File(System.getProperty("user.home", "."));
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        fileChooser.setInitialDirectory(initialDir);
                    }
                }
                fileChooser.setInitialFileName("schematron.sch");
            }

            File selectedFile = fileChooser.showSaveDialog(saveSchematronButton.getScene().getWindow());
            if (selectedFile != null) {
                // Ensure .sch extension if not provided
                String fileName = selectedFile.getName();
                if (!fileName.contains(".")) {
                    selectedFile = new File(selectedFile.getParent(), fileName + ".sch");
                }

                currentSchematronFile = selectedFile;

                // Update last open directory
                if (selectedFile.getParent() != null) {
                    propertiesService.setLastOpenDirectory(selectedFile.getParent());
                }
                
                saveSchematronFile();
                logger.info("Schematron file saved as: {}", selectedFile.getAbsolutePath());
                showInfo("Save Successful", "Schematron file saved successfully.");
            }

        } catch (Exception e) {
            logger.error("Error saving Schematron file as: {}", e.getMessage(), e);
            showError("Save Error", "Failed to save Schematron file: " + e.getMessage());
        }
    }

    // ==================== RESULTS TABS IMPLEMENTATION ====================

    /**
     * Update all results tabs with current test data
     */
    private void updateResultsTabs() {
        updateOverviewTab();
        updateDetailsTab();
        updateErrorsTab();
    }

    /**
     * Update the Overview tab with summary statistics
     */
    private void updateOverviewTab() {
        if (overviewContent == null) return;

        // Clear existing content
        overviewContent.getChildren().clear();

        if (testFiles.isEmpty()) {
            overviewContent.getChildren().add(overviewPlaceholder);
            return;
        }

        // Calculate statistics
        int total = testFiles.size();
        int passed = 0;
        int failed = 0;
        int totalViolations = 0;
        int totalWarnings = 0;

        for (TestFile tf : testFiles) {
            if ("Passed".equals(tf.getStatus())) {
                passed++;
            } else if ("Failed".equals(tf.getStatus())) {
                failed++;
            }
            totalViolations += tf.getViolations();
            totalWarnings += tf.getWarnings();
        }

        // Create overview content
        VBox summaryBox = new VBox(15);
        summaryBox.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Header
        Label headerLabel = new Label("Test Summary");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Statistics grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(30);
        statsGrid.setVgap(15);
        statsGrid.setStyle("-fx-alignment: center;");

        // Total tests
        addStatistic(statsGrid, 0, 0, "Total Tests", String.valueOf(total), "#007bff");

        // Passed tests
        addStatistic(statsGrid, 1, 0, "Passed", String.valueOf(passed), "#28a745");

        // Failed tests
        addStatistic(statsGrid, 2, 0, "Failed", String.valueOf(failed), "#dc3545");

        // Total violations
        addStatistic(statsGrid, 0, 1, "Total Violations", String.valueOf(totalViolations), "#dc3545");

        // Total warnings
        addStatistic(statsGrid, 1, 1, "Total Warnings", String.valueOf(totalWarnings), "#ffc107");

        // Success rate
        double successRate = total > 0 ? (passed * 100.0 / total) : 0;
        addStatistic(statsGrid, 2, 1, "Success Rate", String.format("%.1f%%", successRate), "#17a2b8");

        summaryBox.getChildren().addAll(headerLabel, statsGrid);
        overviewContent.getChildren().add(summaryBox);
    }

    /**
     * Helper method to add a statistic to the grid
     */
    private void addStatistic(GridPane grid, int col, int row, String label, String value, String color) {
        VBox statBox = new VBox(5);
        statBox.setStyle("-fx-alignment: center;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label labelLabel = new Label(label);
        labelLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

        statBox.getChildren().addAll(valueLabel, labelLabel);
        grid.add(statBox, col, row);
    }

    /**
     * Update the Details tab with detailed test results
     */
    private void updateDetailsTab() {
        if (detailsContent == null) return;

        // Clear existing content
        detailsContent.getChildren().clear();

        if (testFiles.isEmpty()) {
            detailsContent.getChildren().add(detailsPlaceholder);
            return;
        }

        VBox detailsBox = new VBox(10);

        for (TestFile testFile : testFiles) {
            VBox fileBox = createFileDetailsBox(testFile);
            detailsBox.getChildren().add(fileBox);
        }

        ScrollPane scrollPane = new ScrollPane(detailsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        detailsContent.getChildren().add(scrollPane);
    }

    /**
     * Create a detailed view for a single test file
     */
    private VBox createFileDetailsBox(TestFile testFile) {
        VBox fileBox = new VBox(8);
        fileBox.setStyle("-fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-padding: 15; -fx-background-color: #f8f9fa;");

        // File header
        HBox headerBox = new HBox(10);
        headerBox.setStyle("-fx-alignment: center-left;");

        Label fileNameLabel = new Label(testFile.getFilename());
        fileNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label statusLabel = new Label(testFile.getStatus());
        String statusColor = "Passed".equals(testFile.getStatus()) ? "#28a745" :
                "Failed".equals(testFile.getStatus()) ? "#dc3545" : "#6c757d";
        statusLabel.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold;");

        headerBox.getChildren().addAll(fileNameLabel, statusLabel);

        // File stats
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-alignment: center-left;");

        Label violationsLabel = new Label("Violations: " + testFile.getViolations());
        violationsLabel.setStyle("-fx-font-size: 12px;");

        Label warningsLabel = new Label("Warnings: " + testFile.getWarnings());
        warningsLabel.setStyle("-fx-font-size: 12px;");

        Label durationLabel = new Label("Duration: " + testFile.getTestDuration());
        durationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

        statsBox.getChildren().addAll(violationsLabel, warningsLabel, durationLabel);

        fileBox.getChildren().addAll(headerBox, statsBox);

        // Add detailed results if available
        if (testFile.getDetailedResults() != null && !testFile.getDetailedResults().isEmpty()) {
            VBox resultsBox = new VBox(5);

            Label resultsHeader = new Label("Details:");
            resultsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #495057;");
            resultsBox.getChildren().add(resultsHeader);

            for (TestFile.TestResult result : testFile.getDetailedResults()) {
                VBox resultBox = createResultBox(result);
                resultsBox.getChildren().add(resultBox);
            }

            fileBox.getChildren().add(resultsBox);
        }

        return fileBox;
    }

    /**
     * Create a box for a single test result
     */
    private VBox createResultBox(TestFile.TestResult result) {
        VBox resultBox = new VBox(3);
        resultBox.setStyle("-fx-padding: 8; -fx-background-color: #ffffff; -fx-border-color: #e9ecef; -fx-border-radius: 3;");

        String typeColor = "assert".equals(result.type()) ? "#dc3545" : "#ffc107";

        Label typeLabel = new Label("[" + result.type().toUpperCase() + "] " + result.message());
        typeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + typeColor + ";");
        typeLabel.setWrapText(true);

        Label locationLabel = new Label("Location: " + result.location() + " (Line " + result.lineNumber() + ")");
        locationLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-font-style: italic;");

        resultBox.getChildren().addAll(typeLabel, locationLabel);

        if (result.ruleId() != null && !result.ruleId().trim().isEmpty()) {
            Label ruleLabel = new Label("Rule: " + result.ruleId());
            ruleLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
            resultBox.getChildren().add(ruleLabel);
        }

        return resultBox;
    }

    /**
     * Update the Errors tab with only errors and violations
     */
    private void updateErrorsTab() {
        if (errorsContent == null) return;

        // Clear existing content
        errorsContent.getChildren().clear();

        // Collect all errors and violations
        List<TestFile.TestResult> allErrors = new ArrayList<>();

        for (TestFile testFile : testFiles) {
            if (testFile.getDetailedResults() != null) {
                for (TestFile.TestResult result : testFile.getDetailedResults()) {
                    if ("assert".equals(result.type()) || "error".equals(result.type())) {
                        allErrors.add(result);
                    }
                }
            }
        }

        if (allErrors.isEmpty()) {
            VBox noErrorsBox = new VBox(15);
            noErrorsBox.setStyle("-fx-alignment: center; -fx-padding: 50;");

            Label iconLabel = new Label("✓");
            iconLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: #28a745;");

            Label messageLabel = new Label("No errors found!");
            messageLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #28a745; -fx-font-weight: bold;");

            Label subLabel = new Label("All tests passed validation");
            subLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");

            noErrorsBox.getChildren().addAll(iconLabel, messageLabel, subLabel);
            errorsContent.getChildren().add(noErrorsBox);
            return;
        }

        VBox errorsBox = new VBox(10);

        Label headerLabel = new Label("Validation Errors (" + allErrors.size() + ")");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #dc3545;");

        errorsBox.getChildren().add(headerLabel);

        for (TestFile.TestResult error : allErrors) {
            VBox errorBox = createErrorBox(error);
            errorsBox.getChildren().add(errorBox);
        }

        ScrollPane scrollPane = new ScrollPane(errorsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        errorsContent.getChildren().add(scrollPane);
    }

    /**
     * Create an error display box
     */
    private VBox createErrorBox(TestFile.TestResult error) {
        VBox errorBox = new VBox(5);
        errorBox.setStyle("-fx-padding: 12; -fx-background-color: #f8d7da; -fx-border-color: #dc3545; " +
                "-fx-border-radius: 5; -fx-border-width: 1;");

        Label messageLabel = new Label(error.message());
        messageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #721c24;");
        messageLabel.setWrapText(true);

        Label locationLabel = new Label("📍 " + error.location() + " (Line " + error.lineNumber() + ")");
        locationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #856a6d;");

        errorBox.getChildren().addAll(messageLabel, locationLabel);

        if (error.ruleId() != null && !error.ruleId().trim().isEmpty()) {
            Label ruleLabel = new Label("🔖 Rule: " + error.ruleId());
            ruleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #856a6d;");
            errorBox.getChildren().add(ruleLabel);
        }

        if (error.pattern() != null && !error.pattern().trim().isEmpty()) {
            Label patternLabel = new Label("📋 Pattern: " + error.pattern());
            patternLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #856a6d;");
            errorBox.getChildren().add(patternLabel);
        }

        return errorBox;
    }

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("Schematron Validator - Help");
        helpDialog.setHeaderText("Business Rules and Validation for XML Documents");
        helpDialog.setContentText("""
                WHAT IS SCHEMATRON?

                Schematron is a rule-based validation system for XML documents. It enables the definition of
                business rules and integrity constraints that go beyond the capabilities of XML Schema (XSD).

                FEATURES:

                • Code Editor: Create and edit Schematron rules with syntax highlighting and auto-completion
                • Visual Builder: Create rules visually without XML knowledge using a user-friendly interface
                • Testing: Validate XML documents against your Schematron rules and see detailed results
                • Documentation: Automatically generate documentation for your Schematron rules and validation logic

                GETTING STARTED:

                1. Go to the 'Code' tab to create a new Schematron schema or load an existing one
                2. Use the 'New Rule' and 'New Pattern' buttons to add validation rules
                3. Test your rules in the 'Test' tab with sample XML documents
                4. Generate documentation for your schema in the 'Documentation' tab

                KEYBOARD SHORTCUTS:

                - F5: Run all tests
                - Ctrl+S: Save Schematron file
                - Ctrl+Shift+S: Save As
                - Ctrl+D: Add to favorites
                - Ctrl+Shift+D: Toggle favorites panel
                - F1: Show this help dialog
                """);
        helpDialog.showAndWait();
    }

    // ==================== PUBLIC KEYBOARD SHORTCUT METHODS ====================

    /**
     * Public wrapper for saveSchematron() - called from MainController for Ctrl+S shortcut
     */
    public void saveSchematronPublic() {
        saveSchematron();
    }

    /**
     * Public wrapper for saveSchematronAs() - called from MainController for Ctrl+Shift+S shortcut
     */
    public void saveSchematronAsPublic() {
        saveSchematronAs();
    }

    /**
     * Public wrapper for addCurrentToFavorites() - called from MainController for Ctrl+D shortcut
     */
    public void addCurrentToFavoritesPublic() {
        addCurrentToFavorites();
    }

    /**
     * Public wrapper for toggleFavoritesPanel() - called from MainController for Ctrl+Shift+D shortcut
     */
    public void toggleFavoritesPanelPublic() {
        toggleFavoritesPanel();
    }

    /**
     * Applies the small icons setting from user preferences.
     * When enabled, toolbar buttons display in compact mode with smaller icons (14px) and no text labels.
     * When disabled, buttons show both icon and text (TOP display) with normal icon size (20px).
     */
    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to Schematron toolbar: {}", useSmallIcons);

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

        // Apply to main toolbar buttons only (not inner Code tab toolbar buttons)
        applyButtonSettings(newSchematronFileButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(loadSchematronFileButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(saveSchematronButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(saveAsSchematronButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(newRuleButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(addToFavoritesBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toggleFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(helpBtn, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to Schematron toolbar (size: {}px)", iconSize);
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
