package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.*;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

    @FXML
    private Button newRuleButton;

    @FXML
    private Button newPatternButton;

    @FXML
    private Button validateButton;

    @FXML
    private Button testRulesButton;

    @FXML
    private VBox codeEditorContainer;

    @FXML
    private VBox sidebarContainer;

    @FXML
    private VBox visualBuilderContainer;

    @FXML
    private VBox testingContainer;

    @FXML
    private VBox documentationContainer;

    @FXML
    private TitledPane structurePane;

    @FXML
    private TitledPane templatesPane;

    @FXML
    private TitledPane xpathHelperPane;

    // Core components
    private SchematronCodeEditor schematronCodeEditor;
    private SchematronAutoComplete autoComplete;
    private SchematronErrorDetector errorDetector;
    private SchematronVisualBuilder visualBuilder;
    private SchematronTester tester;
    private SchematronDocumentationGenerator docGenerator;
    private SchematronTemplateLibrary templateLibrary;
    private CodeArea codeArea;
    private SchematronService schematronService;
    private MainController parentController;
    private ProgressManager progressManager;

    // Current file
    private File currentSchematronFile;

    /**
     * Initialize the controller - called automatically by JavaFX
     */
    @FXML
    private void initialize() {
        logger.info("Initializing SchematronController");

        // Initialize services
        schematronService = new SchematronServiceImpl();
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

        logger.info("SchematronController initialization completed");
    }

    /**
     * Initialize the code editor component
     */
    private void initializeCodeEditor() {
        try {
            // Initialize the enhanced Schematron code editor
            schematronCodeEditor = new SchematronCodeEditor();
            codeArea = schematronCodeEditor.getCodeArea();

            // Initialize auto-completion
            autoComplete = new SchematronAutoComplete(codeArea);

            // Add the code editor to the container
            codeEditorContainer.getChildren().add(schematronCodeEditor);

            logger.debug("Code editor initialized successfully with Schematron features");
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
            visualBuilder = new SchematronVisualBuilder();
            visualBuilder.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());

            // Add the visual builder to the container
            visualBuilderContainer.getChildren().add(visualBuilder);

            logger.debug("Visual builder initialized successfully");
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
            tester = new SchematronTester();
            tester.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());

            // Add the tester to the container
            testingContainer.getChildren().add(tester);

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
            docGenerator = new SchematronDocumentationGenerator();
            docGenerator.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());

            // Add the doc generator to the container
            documentationContainer.getChildren().add(docGenerator);

            logger.debug("Documentation generator initialized successfully");
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
            // Initialize template library
            templateLibrary = new SchematronTemplateLibrary();
            templateLibrary.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());

            // Set callback to insert templates into code editor
            templateLibrary.setTemplateInsertCallback(template -> {
                if (schematronCodeEditor != null) {
                    schematronCodeEditor.insertTemplate(template);
                    performErrorDetection();
                }
            });

            // Replace placeholder in templates pane
            if (templatesPane != null) {
                templatesPane.setContent(templateLibrary);
                templatesPane.setExpanded(true);
            }

            logger.debug("Sidebar components initialized with template library");

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
            if (schematronCodeEditor != null) {
                schematronCodeEditor.setText(content);

                // Update current file reference
                currentSchematronFile = file;

                // Notify integration service of Schematron file change
                if (parentController != null && parentController.getIntegrationService() != null) {
                    parentController.getIntegrationService().setCurrentSchematronFile(file);
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
     * Save the current Schematron file
     */
    public void saveSchematronFile() {
        if (currentSchematronFile == null) {
            saveSchematronFileAs();
            return;
        }

        try {
            String content = schematronCodeEditor.getText();
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

        File file = fileChooser.showSaveDialog(schematronCodeEditor.getScene().getWindow());
        if (file != null) {
            currentSchematronFile = file;
            saveSchematronFile();
        }
    }

    /**
     * Create a new Schematron file from template
     */
    public void createNewSchematron() {
        String template = generateBasicSchematronTemplate();

        Platform.runLater(() -> {
            schematronCodeEditor.setText(template);

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
        schematronCodeEditor.insertTemplate(ruleTemplate);
        performErrorDetection();
        logger.debug("New rule template inserted");
    }

    /**
     * Insert a new pattern template
     */
    private void insertNewPattern() {
        String patternTemplate = generatePatternTemplate();
        schematronCodeEditor.insertTemplate(patternTemplate);
        performErrorDetection();
        logger.debug("New pattern template inserted");
    }

    /**
     * Validate the current Schematron file
     */
    private void validateSchematron() {
        if (schematronCodeEditor == null || schematronCodeEditor.getText().isEmpty()) {
            showWarning("Validation", "No Schematron content to validate");
            return;
        }

        try {
            String content = schematronCodeEditor.getText();
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
        if (schematronCodeEditor != null && errorDetector != null) {
            String content = schematronCodeEditor.getText();
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
        if (visualBuilder != null && schematronCodeEditor != null) {
            String generatedCode = visualBuilder.getGeneratedCode();
            schematronCodeEditor.setText(generatedCode);

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
}