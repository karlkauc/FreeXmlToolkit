package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.*;
import org.fxt.freexmltoolkit.domain.TestFile;
import org.fxt.freexmltoolkit.service.*;
import org.kordamp.ikonli.javafx.FontIcon;

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
public class SchematronController {

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
    private Button validateButton;

    @FXML
    private Button testRulesButton;

    @FXML
    private Button addSchematronToFavoritesButton;

    @FXML
    private MenuButton loadSchematronFavoritesButton;

    // Overview Tab Buttons
    @FXML
    private Button overviewCreateNewButton;

    @FXML
    private Button overviewLoadSchemaButton;

    @FXML
    private Button overviewShowExamplesButton;

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
    private FavoritesService favoritesService;
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
        favoritesService = FavoritesService.getInstance();
        propertiesService = PropertiesServiceImpl.getInstance();
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
        initializeFavorites();

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

        logger.info("SchematronController initialization completed");
    }

    /**
     * Initialize the code editor component
     */
    private void initializeCodeEditor() {
        try {
            // xmlCodeEditor is already initialized from FXML
            if (xmlCodeEditor != null) {
                codeArea = xmlCodeEditor.getCodeArea();

                // Initialize auto-completion
                autoComplete = new SchematronAutoComplete(codeArea);

                logger.debug("Code editor initialized successfully with Schematron features");
            } else {
                logger.error("SchematronCodeEditor not found in FXML");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize code editor", e);
            showError("Initialization Error", "Failed to initialize code editor: " + e.getMessage());
        }
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
                xmlCodeEditor.setText(content);
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
     * Create a new Schematron file from template
     */
    public void createNewSchematron() {
        String template = generateBasicSchematronTemplate();

        Platform.runLater(() -> {
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
                        // TODO: Update UI with error markers in future enhancement
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
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show information dialog
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show warning dialog
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ======================================================================
    // Favorites functionality for Schematron files
    // ======================================================================

    private void initializeFavorites() {
        // Initialize the favorites menu
        Platform.runLater(() -> refreshSchematronFavoritesMenu());
        logger.debug("Schematron favorites system initialized");
    }

    @FXML
    private void addCurrentSchematronToFavorites() {
        logger.info("Adding current Schematron file to favorites");

        if (currentSchematronFile == null || !currentSchematronFile.exists()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No Schematron file is currently loaded or file does not exist.");
            return;
        }

        // Check if already in favorites
        if (favoritesService.isFavorite(currentSchematronFile.getAbsolutePath())) {
            showAlert(Alert.AlertType.INFORMATION, "Information", "This Schematron file is already in your favorites.");
            return;
        }

        // Show dialog to add to favorites
        showAddSchematronToFavoritesDialog(currentSchematronFile);
    }

    private void showAddSchematronToFavoritesDialog(File file) {
        Dialog<org.fxt.freexmltoolkit.domain.FileFavorite> dialog = new Dialog<>();
        dialog.setTitle("Add Schematron to Favorites");
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
        categoryCombo.setValue("Schematron Rules");
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
                            categoryCombo.getValue() != null ? categoryCombo.getValue().trim() : "Schematron Rules"
                    );
                    if (!descriptionField.getText().trim().isEmpty()) {
                        favorite.setDescription(descriptionField.getText().trim());
                    }
                    return favorite;
                } catch (Exception e) {
                    logger.error("Error creating Schematron favorite", e);
                    return null;
                }
            }
            return null;
        });

        Optional<org.fxt.freexmltoolkit.domain.FileFavorite> result = dialog.showAndWait();
        result.ifPresent(favorite -> {
            favoritesService.addFavorite(favorite);
            refreshSchematronFavoritesMenu();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Schematron file added to favorites successfully!");
            logger.info("Added {} to favorites in category {}", favorite.getName(), favorite.getFolderName());
        });
    }

    private void refreshSchematronFavoritesMenu() {
        if (loadSchematronFavoritesButton == null) return;

        loadSchematronFavoritesButton.getItems().clear();

        // Get Schematron favorites
        List<org.fxt.freexmltoolkit.domain.FileFavorite> schematronFavorites = favoritesService.getFavoritesByType(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.SCHEMATRON);

        if (schematronFavorites.isEmpty()) {
            MenuItem noFavoritesItem = new MenuItem("No Schematron favorites yet");
            noFavoritesItem.setDisable(true);
            loadSchematronFavoritesButton.getItems().add(noFavoritesItem);
            return;
        }

        // Organize by categories
        java.util.Map<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> favoritesByCategory =
                schematronFavorites.stream().collect(java.util.stream.Collectors.groupingBy(
                        f -> f.getFolderName() != null ? f.getFolderName() : "Uncategorized"));

        for (java.util.Map.Entry<String, List<org.fxt.freexmltoolkit.domain.FileFavorite>> entry : favoritesByCategory.entrySet()) {
            String category = entry.getKey();
            List<org.fxt.freexmltoolkit.domain.FileFavorite> favoritesInCategory = entry.getValue();

            if (favoritesByCategory.size() > 1) {
                // Add category submenu if multiple categories
                Menu categoryMenu = new Menu(category);
                categoryMenu.getStyleClass().add("favorites-category");

                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createSchematronFavoriteMenuItem(favorite);
                    categoryMenu.getItems().add(favoriteItem);
                }

                loadSchematronFavoritesButton.getItems().add(categoryMenu);
            } else {
                // If only one category, add items directly
                for (org.fxt.freexmltoolkit.domain.FileFavorite favorite : favoritesInCategory) {
                    MenuItem favoriteItem = createSchematronFavoriteMenuItem(favorite);
                    loadSchematronFavoritesButton.getItems().add(favoriteItem);
                }
            }
        }

        // Add separator and management options
        loadSchematronFavoritesButton.getItems().add(new SeparatorMenuItem());

        MenuItem manageFavoritesItem = new MenuItem("Manage Favorites...");
        manageFavoritesItem.setOnAction(e -> showSchematronFavoritesManagement());
        loadSchematronFavoritesButton.getItems().add(manageFavoritesItem);
    }

    private MenuItem createSchematronFavoriteMenuItem(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        MenuItem item = new MenuItem(favorite.getName());

        // Set Schematron icon
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

        // Set action to load the Schematron file
        item.setOnAction(e -> loadSchematronFavoriteFile(favorite));

        return item;
    }

    private void loadSchematronFavoriteFile(org.fxt.freexmltoolkit.domain.FileFavorite favorite) {
        File file = new File(favorite.getFilePath());

        if (!file.exists()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("File Not Found");
            alert.setHeaderText("Cannot open favorite Schematron file");
            alert.setContentText("The file \"" + file.getName() + "\" no longer exists at:\n" + favorite.getFilePath());

            // Offer to remove from favorites
            ButtonType removeButton = new ButtonType("Remove from Favorites");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(removeButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == removeButton) {
                favoritesService.removeFavorite(favorite.getId());
                refreshSchematronFavoritesMenu();
            }
            return;
        }

        try {
            // Load the Schematron file
            loadSchematronFile(file);

            // Update last accessed time
            favoritesService.updateFavorite(favorite);

            logger.info("Loaded favorite Schematron file: {}", favorite.getName());
        } catch (Exception e) {
            logger.error("Error loading favorite Schematron file: {}", favorite.getName(), e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load favorite Schematron file:\n" + e.getMessage());
        }
    }

    private void showSchematronFavoritesManagement() {
        // This will be implemented when we create the settings UI
        showAlert(Alert.AlertType.INFORMATION, "Coming Soon", "Favorites management will be available in Settings.");
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
     * Run all tests
     */
    @FXML
    private void runAllTests() {
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
            return;
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

    // Overview Tab Button Actions

    /**
     * Create new schema from Overview tab
     */
    @FXML
    private void overviewCreateNew() {
        // Switch to Code tab
        if (tabPane != null && codeTab != null) {
            tabPane.getSelectionModel().select(codeTab);
        }
        // Create new Schematron
        createNewSchematron();
    }

    /**
     * Load schema from Overview tab
     */
    @FXML
    private void overviewLoadSchema() {
        // Switch to Code tab
        if (tabPane != null && codeTab != null) {
            tabPane.getSelectionModel().select(codeTab);
        }
        // Load Schematron file
        loadSchematronFile();
    }

    /**
     * Show examples from Overview tab
     */
    @FXML
    private void overviewShowExamples() {
        // Switch to Code tab and show a basic example
        if (tabPane != null && codeTab != null) {
            tabPane.getSelectionModel().select(codeTab);
        }

        // Create a more comprehensive example schema
        String exampleSchema = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                    <title>Example Schematron Schema</title>
                    <ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>
                
                    <!-- Example Pattern: Document Structure -->
                    <pattern id="document-structure">
                        <title>Document Structure Rules</title>
                
                        <rule context="document">
                            <assert test="@version">Document must have a version attribute</assert>
                            <assert test="string-length(@version) > 0">Version attribute cannot be empty</assert>
                        </rule>
                
                        <rule context="document/header">
                            <assert test="title">Document header must contain a title</assert>
                            <assert test="string-length(title) >= 3">Title must be at least 3 characters long</assert>
                        </rule>
                    </pattern>
                
                    <!-- Example Pattern: Business Rules -->
                    <pattern id="business-rules">
                        <title>Business Logic Validation</title>
                
                        <rule context="order">
                            <assert test="@order-date">Orders must have a date</assert>
                            <assert test="count(item) >= 1">Order must contain at least one item</assert>
                            <report test="@total-amount &lt; 0">Negative total amount detected: <value-of select="@total-amount"/></report>
                        </rule>
                
                        <rule context="item">
                            <assert test="@quantity and @quantity > 0">Item quantity must be positive</assert>
                            <assert test="price">Each item must have a price</assert>
                        </rule>
                    </pattern>
                </schema>""";

        Platform.runLater(() -> {
            if (xmlCodeEditor != null) {
                xmlCodeEditor.setText(exampleSchema);
                xmlCodeEditor.refreshHighlighting();

                // Clear current file reference since this is an example
                currentSchematronFile = null;
                codeTab.setText("Code - Example Schema");

                showInfo("Example Loaded", "Example Schematron schema loaded in Code tab");
            }
        });
    }
}