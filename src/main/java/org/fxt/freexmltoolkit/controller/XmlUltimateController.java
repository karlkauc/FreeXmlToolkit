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
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.*;
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
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ultimate XML Controller - The Complete XML Editor with All Features
 * Provides comprehensive XML editing, validation, transformation, and generation capabilities
 */
public class XmlUltimateController implements Initializable {
    private static final Logger logger = LogManager.getLogger(XmlUltimateController.class);

    // Services
    private final XmlService xmlService = XmlServiceImpl.getInstance();
    private final TemplateEngine templateEngine = TemplateEngine.getInstance();
    private final TemplateRepository templateRepository = TemplateRepository.getInstance();
    private final XsltTransformationEngine xsltEngine = XsltTransformationEngine.getInstance();
    private final SchemaGenerationEngine schemaEngine = SchemaGenerationEngine.getInstance();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private final FavoritesService favoritesService = FavoritesService.getInstance();

    // Parent controller reference
    private MainController parentController;

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("UltimateXML-Thread");
        return t;
    });

    // XPath/XQuery Code Areas
    private final CodeArea codeAreaXpath = new CodeArea();
    private final CodeArea codeAreaXQuery = new CodeArea();
    private VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    private VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    // Toolbar Buttons
    @FXML
    private Button newFile;
    @FXML
    private Button openFile;
    @FXML
    private Button saveFile;
    @FXML
    private Button saveAsFile;
    @FXML
    private Button prettyPrint;
    // Removed from UI
    // @FXML
    // private Button minifyButton;
    @FXML
    private Button validateButton;
    // Removed from UI
    // @FXML
    // private Button lintButton;
    @FXML
    private Button runXpathQuery;
    @FXML
    private Button templateManagerButton;
    @FXML
    private Button schemaGeneratorButton;
    @FXML
    private Button xsltDeveloperButton;
    // Removed from UI
    // @FXML
    // private ToggleButton treeViewToggle;
    @FXML
    private Button addToFavoritesButton;
    @FXML
    private ToggleButton toggleFavoritesButton;

    // Main Editor
    @FXML
    private TabPane xmlFilesPane;

    // Sidebar Components
    @FXML
    private TreeView<String> documentTreeView;
    @FXML
    private ComboBox<String> schemaCombo;
    @FXML
    private ListView<String> validationResultsList;
    @FXML
    private TableView<PropertyEntry> propertiesTable;
    @FXML
    private TableColumn<PropertyEntry, String> propertyNameColumn;
    @FXML
    private TableColumn<PropertyEntry, String> propertyValueColumn;
    @FXML
    private ListView<String> namespacesList;

    // Development Panels
    @FXML
    private TabPane developmentTabPane;
    @FXML
    private SplitPane mainSplitPane; // Add this FXML element
    @FXML
    private SplitPane horizontalSplitPane; // Horizontal split for favorites panel

    // Favorites Panel
    @FXML
    private FavoritesPanelController favoritesPanelController;

    // Store favorites panel node for show/hide functionality
    private javafx.scene.Node favoritesPanelNode;

    // XSLT Development Code Editors
    private XmlCodeEditor xmlSourceEditor;
    private XmlCodeEditor xsltStylesheetEditor;

    // XPath/XQuery
    @FXML
    private Tab xPathQueryTab;
    @FXML
    private TabPane xPathQueryPane;
    @FXML
    private Tab xPathTab;
    @FXML
    private StackPane stackPaneXPath;
    @FXML
    private Tab xQueryTab;
    @FXML
    private StackPane stackPaneXQuery;
    @FXML
    private Label queryResultLabel;
    @FXML
    private Button executeQueryButton;
    @FXML
    private Button clearQueryButton;

    // XSLT Development
    @FXML
    private Tab xsltDevelopmentTab;
    @FXML
    private Button loadXsltButton;
    @FXML
    private Button saveXsltButton;
    @FXML
    private Button transformButton;
    @FXML
    private StackPane xmlSourceEditorPane;
    @FXML
    private StackPane xsltEditorPane;
    @FXML
    private Button loadXmlSourceButton;
    @FXML
    private Button saveXmlSourceButton;
    @FXML
    private ComboBox<String> outputFormatCombo;
    @FXML
    private CheckBox livePreviewCheckbox;
    @FXML
    private TextArea transformationResultArea;
    @FXML
    private WebView transformationPreviewWeb;
    @FXML
    private TextArea performanceResultArea;

    // Template Development
    @FXML
    private Tab templateDevelopmentTab;
    @FXML
    private Button addParameterButton;
    @FXML
    private TableView<TemplateParameter> templateParametersTable;
    @FXML
    private TableColumn<TemplateParameter, String> parameterNameColumn;
    @FXML
    private TableColumn<TemplateParameter, String> parameterValueColumn;
    @FXML
    private TableColumn<TemplateParameter, String> parameterTypeColumn;
    @FXML
    private Button validateParametersButton;
    @FXML
    private Button resetParametersButton;
    @FXML
    private Button generateTemplateButton;
    @FXML
    private Button insertTemplateButton;
    @FXML
    private TextArea templatePreviewArea;

    // Console
    @FXML
    private TextArea consoleOutput;

    // State
    private File currentXmlFile;
    private String currentXmlContent = "";
    private XmlTemplate selectedTemplate;
    private final Map<String, String> currentTemplateParams = new HashMap<>();
    private String currentXsltContent = "";
    private final String generatedSchemaContent = "";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing Ultimate XML Controller - The Complete XML Editor");
        initializeComboBoxes();
        initializeUI();
        initializeTables();
        initializeXPathXQuery();
        initializeXsltDevelopment();
        loadTemplates();
        createInitialTab();
        initializeFavorites();
        updateButtonStates();
        logger.info("Ultimate XML Controller initialized successfully");
    }

    private void initializeComboBoxes() {
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(FXCollections.observableArrayList(
                    "XML", "HTML", "Text", "JSON"
            ));
            outputFormatCombo.getSelectionModel().selectFirst();
        }
    }

    private void initializeUI() {
        if (consoleOutput != null) {
            consoleOutput.appendText("Ultimate XML Editor initialized.\n");
            consoleOutput.appendText("All revolutionary features are available.\n");
        }

        // Add tab selection listener to refresh syntax highlighting and update button states
        if (xmlFilesPane != null) {
            xmlFilesPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab instanceof XmlEditor xmlEditor) {
                    Platform.runLater(() -> {
                        xmlEditor.getXmlCodeEditor().refreshHighlighting();
                        updateButtonStates();
                    });
                }
            });
        }

        // Initialize validation results list with double-click navigation
        if (validationResultsList != null) {
            validationResultsList.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selectedError = validationResultsList.getSelectionModel().getSelectedItem();
                    if (selectedError != null) {
                        navigateToValidationError(selectedError);
                    }
                }
            });
        }

        // Initialize drag and drop functionality for XML files
        initializeDragAndDrop();
    }

    /**
     * Navigate to the line number specified in a validation error message
     */
    private void navigateToValidationError(String errorMessage) {
        logger.debug("Navigating to validation error: {}", errorMessage);

        // Extract line number from error message (various formats)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("line\\s*(\\d+)|Line\\s*(\\d+)|:(\\d+):");
        java.util.regex.Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find()) {
            String lineNumberStr = matcher.group(1);
            if (lineNumberStr == null) lineNumberStr = matcher.group(2);
            if (lineNumberStr == null) lineNumberStr = matcher.group(3);

            try {
                int lineNumber = Integer.parseInt(lineNumberStr);
                navigateToLine(lineNumber);
                logger.debug("Navigated to line: {}", lineNumber);
            } catch (NumberFormatException e) {
                logger.warn("Could not parse line number from error: {}", errorMessage);
            }
        } else {
            logger.warn("No line number found in error message: {}", errorMessage);
        }
    }

    /**
     * Navigate to a specific line in the current XML editor
     */
    private void navigateToLine(int lineNumber) {
        XmlEditor currentEditor = getCurrentEditor();
        if (currentEditor != null) {
            currentEditor.navigateToLine(lineNumber);
        }
    }

    /**
     * Initialize drag and drop functionality for the XML editor TabPane
     */
    private void initializeDragAndDrop() {
        if (xmlFilesPane == null) {
            logger.warn("Cannot initialize drag and drop: xmlFilesPane is null");
            return;
        }

        logger.info("Setting up drag and drop functionality for XML files");

        // Allow files to be dropped on the TabPane
        xmlFilesPane.setOnDragOver(this::handleDragOver);
        xmlFilesPane.setOnDragDropped(this::handleDragDropped);

        logger.debug("Drag and drop event handlers registered for XML files TabPane");
    }

    /**
     * Handle drag over event - determine if files can be accepted
     */
    private void handleDragOver(DragEvent event) {
        Dragboard dragboard = event.getDragboard();

        // Accept files if they exist and at least one is an XML file
        if (dragboard.hasFiles() && hasXmlFiles(dragboard.getFiles())) {
            event.acceptTransferModes(TransferMode.COPY);
            logger.debug("Drag over accepted: {} files detected", dragboard.getFiles().size());
        } else {
            logger.debug("Drag over rejected: no XML files found");
        }

        event.consume();
    }

    /**
     * Handle drag dropped event - open the dropped XML files in new tabs
     */
    private void handleDragDropped(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        boolean success = false;

        if (dragboard.hasFiles()) {
            logger.info("Files dropped: processing {} files", dragboard.getFiles().size());

            var xmlFiles = dragboard.getFiles().stream()
                    .filter(this::isXmlFile)
                    .toList();

            if (!xmlFiles.isEmpty()) {
                success = true;

                // Open each XML file in a new tab
                for (var file : xmlFiles) {
                    try {
                        openFileInNewTab(file);
                        logger.info("Opened dropped file in new tab: {}", file.getName());
                    } catch (Exception e) {
                        logger.error("Failed to open dropped file: {}", file.getName(), e);
                        logToConsole("Error opening file " + file.getName() + ": " + e.getMessage());
                    }
                }

                logToConsole("Successfully opened " + xmlFiles.size() + " XML file(s) via drag and drop");
            } else {
                logger.info("No XML files found in dropped files");
                logToConsole("No XML files found in dropped files");
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Check if the list of files contains at least one XML file
     */
    private boolean hasXmlFiles(java.util.List<java.io.File> files) {
        return files.stream().anyMatch(this::isXmlFile);
    }

    /**
     * Check if a file is an XML file based on its extension
     */
    private boolean isXmlFile(java.io.File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".xml") || fileName.endsWith(".xsd") || fileName.endsWith(".xsl") ||
                fileName.endsWith(".xslt") || fileName.endsWith(".wsdl");
    }

    /**
     * Open a specific file in a new tab (extracted from openFile() method)
     */
    private void openFileInNewTab(java.io.File file) throws Exception {
        String content = java.nio.file.Files.readString(file.toPath());

        // Create new XML editor tab
        XmlEditor xmlEditor = new XmlEditor();
        xmlEditor.setMainController(parentController);
        xmlEditor.setText(file.getName());
        xmlEditor.getXmlCodeEditor().setText(content);

        // Set the XML file to trigger automatic XSD schema detection
        xmlEditor.setXmlFile(file);

        // Store the File object in userData so favorites can access it
        xmlEditor.setUserData(file);

        // Apply current sidebar visibility setting
        String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
        if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
            xmlEditor.setXmlEditorSidebarVisible(false);
        }

        // Add tab and select it
        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);

        // Update current file references (for the last opened file)
        currentXmlFile = file;
        currentXmlContent = content;

        // Update document tree and validate
        updateDocumentTree(content);
        validateCurrentXml();

        // Add file to recent files
        if (parentController != null) {
            parentController.addFileToRecentFiles(file);
        } else {
            // Fallback in case the parent controller is not set for some reason
            propertiesService.addLastOpenFile(file);
        }
    }

    private void initializeXPathXQuery() {
        logger.debug("Initializing XPath/XQuery components");

        if (stackPaneXPath == null || stackPaneXQuery == null) {
            logger.error("StackPanes for XPath/XQuery are null! Check FXML bindings.");
            return;
        }

        // Initialize XPath code area
        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        codeAreaXpath.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 13px; -fx-background-color: white;");
        codeAreaXpath.setPrefHeight(100);
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        virtualizedScrollPaneXpath.setStyle("-fx-background-color: white;");
        stackPaneXPath.getChildren().clear();
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);

        // Add placeholder text
        codeAreaXpath.replaceText("// Enter your XPath expression here\n// Example: //book[@category='fiction']/title");

        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> {
                    try {
                        codeAreaXpath.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText));
                    } catch (Exception e) {
                        logger.debug("Error highlighting XPath: {}", e.getMessage());
                    }
                });
            }
        });

        // Initialize XQuery code area
        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        codeAreaXQuery.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 13px; -fx-background-color: white;");
        codeAreaXQuery.setPrefHeight(100);
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        virtualizedScrollPaneXQuery.setStyle("-fx-background-color: white;");
        stackPaneXQuery.getChildren().clear();
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);

        // Add placeholder text
        codeAreaXQuery.replaceText("(: Enter your XQuery expression here :)\n(: Example: for $x in //book return $x/title :)");

        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> {
                    try {
                        codeAreaXQuery.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText));
                    } catch (Exception e) {
                        logger.debug("Error highlighting XQuery: {}", e.getMessage());
                    }
                });
            }
        });

        logger.info("XPath/XQuery code areas initialized successfully");
    }

    private void initializeTables() {
        // Properties table
        if (propertyNameColumn != null && propertyValueColumn != null) {
            propertyNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            propertyValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
            propertyValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
            propertyValueColumn.setOnEditCommit(event -> {
                event.getRowValue().setValue(event.getNewValue());
                updateCurrentXmlFromProperties();
            });
        }

        // Template parameters table
        if (parameterNameColumn != null && parameterValueColumn != null && parameterTypeColumn != null) {
            parameterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            parameterValueColumn.setCellValueFactory(new PropertyValueFactory<>("defaultValue"));
            parameterTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

            parameterValueColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
            parameterValueColumn.setOnEditCommit(event -> {
                TemplateParameter param = event.getRowValue();
                param.setDefaultValue(event.getNewValue());
                currentTemplateParams.put(param.getName(), event.getNewValue());
                if (livePreviewCheckbox != null && livePreviewCheckbox.isSelected()) {
                    generateTemplateXml();
                }
            });
        }
    }

    private void initializeXsltDevelopment() {
        logger.info("Initializing XSLT Development code editors");

        // Initialize XML Source Editor
        if (xmlSourceEditorPane != null) {
            xmlSourceEditor = new XmlCodeEditor();
            xmlSourceEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    <!-- XML Source for XSLT transformation -->\n</root>");

            xmlSourceEditorPane.getChildren().add(xmlSourceEditor);

            logger.debug("XML Source editor initialized in XSLT Development panel");
        }

        // Initialize XSLT Stylesheet Editor
        if (xsltEditorPane != null) {
            xsltStylesheetEditor = new XmlCodeEditor();
            xsltStylesheetEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n    <!-- XSLT Stylesheet -->\n    <xsl:template match=\"/\">\n        <html>\n            <body>\n                <xsl:apply-templates/>\n            </body>\n        </html>\n    </xsl:template>\n</xsl:stylesheet>");

            xsltEditorPane.getChildren().add(xsltStylesheetEditor);

            logger.debug("XSLT Stylesheet editor initialized in XSLT Development panel");
        }

        logger.info("XSLT Development code editors initialized successfully");
    }

    private void loadTemplates() {
        // Template loading moved to popup
    }

    private void createInitialTab() {
        if (xmlFilesPane != null) {
            XmlEditor xmlEditor = new XmlEditor();
            xmlEditor.setMainController(parentController);
            xmlEditor.setText("Untitled.xml");
            xmlEditor.getXmlCodeEditor().setText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>");

            // Apply current sidebar visibility setting
            String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
            if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
                xmlEditor.setXmlEditorSidebarVisible(false);
            }
            
            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);
            updateButtonStates();
        }
    }

    private void initializeFavorites() {
        // Initialize the favorites panel controller
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
            logger.debug("Favorites panel controller initialized");
        } else {
            logger.warn("Favorites panel controller is null - favorites panel may not be properly integrated");
        }

        // Store the favorites panel node for show/hide functionality
        if (horizontalSplitPane != null && horizontalSplitPane.getItems().size() > 1) {
            favoritesPanelNode = horizontalSplitPane.getItems().get(1); // Second item is the favorites panel
            logger.debug("Favorites panel node stored for toggle functionality");
        }

        // Initialize toggle button state - panel starts hidden
        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setSelected(false);
            setFavoritesPanelVisible(false);
        }
        
        logger.debug("Favorites system initialized");
    }

    /**
     * Basic File Operations
     */
    @FXML
    public void newFilePressed() {
        logger.info("Creating new XML document");
        logToConsole("Creating new XML document...");

        if (xmlFilesPane != null) {
            XmlEditor xmlEditor = new XmlEditor();
            xmlEditor.setMainController(parentController);
            xmlEditor.setText("Untitled" + (xmlFilesPane.getTabs().size() + 1) + ".xml");
            xmlEditor.getXmlCodeEditor().setText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>");

            // Apply current sidebar visibility setting
            String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
            if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
                xmlEditor.setXmlEditorSidebarVisible(false);
            }
            
            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);
            updateButtonStates();
        }
    }

    @FXML
    private void openFile() {
        logger.info("Opening XML document");
        logToConsole("Opening XML document...");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open XML File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                currentXmlFile = file;
                currentXmlContent = content;

                if (xmlFilesPane != null) {
                    XmlEditor xmlEditor = new XmlEditor();
                    xmlEditor.setMainController(parentController);
                    xmlEditor.setText(file.getName());
                    xmlEditor.getXmlCodeEditor().setText(content);

                    // Set the XML file to trigger automatic XSD schema detection
                    xmlEditor.setXmlFile(file);

                    // Store the File object in userData so favorites can access it
                    xmlEditor.setUserData(file);

                    // Apply current sidebar visibility setting
                    String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
                    if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
                        xmlEditor.setXmlEditorSidebarVisible(false);
                    }
                    
                    xmlFilesPane.getTabs().add(xmlEditor);
                    xmlFilesPane.getSelectionModel().select(xmlEditor);
                }

                updateDocumentTree(content);
                validateCurrentXml();

                // Add file to recent files
                if (parentController != null) {
                    parentController.addFileToRecentFiles(file);
                } else {
                    // Fallback in case the parent controller is not set for some reason
                    propertiesService.addLastOpenFile(file);
                }

                // Also set last open directory
                if (file.getParent() != null) {
                    propertiesService.setLastOpenDirectory(file.getParent());
                }
                
                logToConsole("Opened file: " + file.getAbsolutePath());
            } catch (IOException e) {
                showError("File Error", "Could not open file: " + e.getMessage());
                logger.error("Failed to open file", e);
            }
        }
    }

    /**
     * Load a specific XML file programmatically (used for recent files, drag & drop, etc.)
     */
    public void loadXmlFile(File file) {
        if (file == null || !file.exists()) {
            logger.warn("Cannot load file - file is null or does not exist: {}", file);
            return;
        }

        try {
            logger.info("Loading XML file programmatically: {}", file.getAbsolutePath());
            logToConsole("Loading XML file: " + file.getName());

            String content = Files.readString(file.toPath());
            currentXmlFile = file;
            currentXmlContent = content;

            if (xmlFilesPane != null) {
                XmlEditor xmlEditor = new XmlEditor();
                xmlEditor.setMainController(parentController);
                xmlEditor.setText(file.getName());
                xmlEditor.getXmlCodeEditor().setText(content);

                // Set the XML file to trigger automatic XSD schema detection
                xmlEditor.setXmlFile(file);

                // Store the File object in userData so favorites can access it
                xmlEditor.setUserData(file);

                // Apply current sidebar visibility setting
                String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
                if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
                    xmlEditor.setXmlEditorSidebarVisible(false);
                }
                
                xmlFilesPane.getTabs().add(xmlEditor);
                xmlFilesPane.getSelectionModel().select(xmlEditor);
            }

            updateDocumentTree(content);

            // Update recent files list
            if (parentController != null) {
                parentController.addFileToRecentFiles(file);
            }

            // Also set last open directory
            if (file.getParent() != null) {
                propertiesService.setLastOpenDirectory(file.getParent());
            }

            logToConsole("Loaded file: " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("File Error", "Could not load file: " + e.getMessage());
            logger.error("Failed to load file", e);
        }
    }

    @FXML
    private void saveFile() {
        logger.info("Saving XML document");
        logToConsole("Saving XML document...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            currentXmlContent = editor.getXmlCodeEditor().getText();
            File editorFile = editor.getXmlFile();

            if (editorFile == null) {
                // No file associated, redirect to Save As
                saveAsFile();
                return;
            }

            try {
                Files.writeString(editorFile.toPath(), currentXmlContent);
                editor.getXmlCodeEditor().notifyFileSaved();
                currentTab.setText(editorFile.getName());
                logToConsole("Saved file: " + editorFile.getAbsolutePath());
            } catch (IOException e) {
                showError("Save Error", "Could not save file: " + e.getMessage());
                logger.error("Failed to save file", e);
            }
        }
    }

    @FXML
    private void saveAsFile() {
        logger.info("Saving XML document as...");
        logToConsole("Saving XML document as...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            currentXmlContent = editor.getXmlCodeEditor().getText();

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XML File As");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            // Set initial directory and filename from current file if available
            File currentFile = editor.getXmlFile();
            if (currentFile != null) {
                // Set directory from current file
                if (currentFile.getParent() != null) {
                    File parentDir = new File(currentFile.getParent());
                    if (parentDir.exists() && parentDir.isDirectory()) {
                        fileChooser.setInitialDirectory(parentDir);
                    }
                }
                // Set initial filename
                fileChooser.setInitialFileName(currentFile.getName());
            } else {
                // Fallback to last open directory if no current file
                String lastDir = propertiesService.getLastOpenDirectory();
                if (lastDir != null) {
                    File initialDir = new File(lastDir);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        fileChooser.setInitialDirectory(initialDir);
                    }
                }
            }

            File selectedFile = fileChooser.showSaveDialog(null);
            if (selectedFile != null) {
                try {
                    Files.writeString(selectedFile.toPath(), currentXmlContent);
                    editor.getXmlCodeEditor().notifyFileSaved();

                    // Update the editor with the new file
                    editor.setXmlFile(selectedFile);
                    editor.setUserData(selectedFile);
                    currentTab.setText(selectedFile.getName());
                    currentXmlFile = selectedFile;

                    // Update last open directory
                    if (selectedFile.getParent() != null) {
                        propertiesService.setLastOpenDirectory(selectedFile.getParent());
                    }

                    // Add to recent files
                    if (parentController != null) {
                        parentController.addFileToRecentFiles(selectedFile);
                    }

                    // Update button states
                    updateButtonStates();

                    logToConsole("Saved file as: " + selectedFile.getAbsolutePath());
                } catch (IOException e) {
                    showError("Save Error", "Could not save file: " + e.getMessage());
                    logger.error("Failed to save file", e);
                }
            }
        }
    }

    /**
     * Format Operations
     */
    @FXML
    private void prettifyingXmlText() {
        logger.info("Prettifying XML");
        logToConsole("Formatting XML with pretty print...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.getXmlCodeEditor().getText();

            try {
                String formatted = formatXml(xml, true);
                editor.codeArea.replaceText(formatted);
                logToConsole("XML formatted successfully");
            } catch (Exception e) {
                showError("Format Error", "Could not format XML: " + e.getMessage());
                logger.error("Failed to format XML", e);
            }
        }
    }

    // Removed from UI - method no longer used
    // @FXML
    private void minifyXmlText() {
        logger.info("Minifying XML");
        logToConsole("Minifying XML...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.getXmlCodeEditor().getText();

            try {
                String minified = xml.replaceAll(">\\s+<", "><").trim();
                editor.codeArea.replaceText(minified);
                logToConsole("XML minified successfully");
            } catch (Exception e) {
                showError("Minify Error", "Could not minify XML: " + e.getMessage());
                logger.error("Failed to minify XML", e);
            }
        }
    }

    /**
     * Validation Operations
     */
    @FXML
    private void validateXml() {
        logger.info("Validating XML");
        logToConsole("Validating XML structure and schema...");
        validateCurrentXml();
    }

    private void validateCurrentXml() {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.getXmlCodeEditor().getText();

            Task<List<String>> validationTask = new Task<>() {
                @Override
                protected List<String> call() throws Exception {
                    List<String> errors = new ArrayList<>();

                    // Well-formedness check
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        builder.parse(new InputSource(new StringReader(xml)));
                        errors.add("✓ XML is well-formed");
                    } catch (Exception e) {
                        errors.add("✗ XML is not well-formed: " + e.getMessage());
                    }

                    // Schema validation if available
                    String schemaFile = schemaCombo != null ? schemaCombo.getValue() : null;
                    if (schemaFile != null && !schemaFile.isEmpty()) {
                        // Add schema validation logic here
                        errors.add("✓ Schema validation: " + schemaFile);
                    }

                    return errors;
                }
            };

            validationTask.setOnSucceeded(e -> {
                List<String> results = validationTask.getValue();
                if (validationResultsList != null) {
                    validationResultsList.setItems(FXCollections.observableArrayList(results));
                }
                results.forEach(this::logToConsole);
            });

            executorService.submit(validationTask);
        }
    }

    // Removed from UI - method no longer used
    // @FXML
    private void lintXml() {
        logger.info("Linting XML");
        logToConsole("Checking XML for potential issues...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.getXmlCodeEditor().getText();

            List<String> issues = new ArrayList<>();

            // Check for common issues
            if (!xml.startsWith("<?xml")) {
                issues.add("⚠ Missing XML declaration");
            }
            if (xml.contains("&") && !xml.contains("&amp;") && !xml.contains("&#")) {
                issues.add("⚠ Unescaped ampersand detected");
            }
            if (xml.contains("<![CDATA[") && !xml.contains("]]>")) {
                issues.add("⚠ Unclosed CDATA section");
            }

            if (issues.isEmpty()) {
                issues.add("✓ No linting issues found");
            }

            if (validationResultsList != null) {
                validationResultsList.setItems(FXCollections.observableArrayList(issues));
            }
            issues.forEach(this::logToConsole);
        }
    }

    /**
     * Query Operations - Placeholder (real implementation below)
     */

    /**
     * Revolutionary Features
     */
    @FXML
    private void showTemplateManager() {
        logger.info("Opening Template Manager Popup");
        logToConsole("Opening Smart Templates System...");
        openTemplateManagerPopup();
    }

    @FXML
    private void addCurrentFileToFavorites() {
        logger.info("Adding current file to favorites");

        Tab selectedTab = xmlFilesPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || selectedTab.getUserData() == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No file is currently open.");
            return;
        }

        File currentFile = (File) selectedTab.getUserData();
        if (!currentFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Current file does not exist on disk.");
            return;
        }

        // Check if already in favorites
        if (favoritesService.isFavorite(currentFile.getAbsolutePath())) {
            showAlert(Alert.AlertType.INFORMATION, "Information", "This file is already in your favorites.");
            return;
        }

        // Show dialog to add to favorites with category selection
        showAddToFavoritesDialog(currentFile);
    }

    private void showAddToFavoritesDialog(File file) {
        Dialog<org.fxt.freexmltoolkit.domain.FileFavorite> dialog = new Dialog<>();
        dialog.setTitle("Add to Favorites");
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
        categoryCombo.setValue("General");
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
                            categoryCombo.getValue() != null ? categoryCombo.getValue().trim() : "General"
                    );
                    if (!descriptionField.getText().trim().isEmpty()) {
                        favorite.setDescription(descriptionField.getText().trim());
                    }
                    return favorite;
                } catch (Exception e) {
                    logger.error("Error creating favorite", e);
                    return null;
                }
            }
            return null;
        });

        Optional<org.fxt.freexmltoolkit.domain.FileFavorite> result = dialog.showAndWait();
        result.ifPresent(favorite -> {
            favoritesService.addFavorite(favorite);
            showAlert(Alert.AlertType.INFORMATION, "Success", "File added to favorites successfully!");
            logger.info("Added {} to favorites in category {}", favorite.getName(), favorite.getFolderName());
        });
    }

    @FXML
    public void toggleFavoritesPanel() {
        logger.info("Toggling Favorites Panel");

        if (toggleFavoritesButton == null || horizontalSplitPane == null) {
            logger.warn("Cannot toggle favorites panel: required components are null");
            return;
        }

        // Use the toggle button's selected state to determine what to do
        boolean shouldBeVisible = toggleFavoritesButton.isSelected();

        setFavoritesPanelVisible(shouldBeVisible);

        logToConsole("Favorites panel " + (shouldBeVisible ? "shown" : "hidden"));
        logger.debug("Favorites panel toggled to: {}", shouldBeVisible ? "visible" : "hidden");
    }



    @FXML
    private void showSchemaGenerator() {
        logger.info("Opening Schema Generator Popup");
        logToConsole("Opening Intelligent Schema Generator...");
        openSchemaGeneratorPopup();
    }

    @FXML
    private void showXsltDeveloper() {
        logger.info("Opening XSLT Developer");
        logToConsole("Opening Advanced XSLT Developer...");
        if (developmentTabPane != null && xsltDevelopmentTab != null) {
            developmentTabPane.getSelectionModel().select(xsltDevelopmentTab);
        }
    }

    /**
     * View Operations
     */
    // Removed from UI - method no longer used
    // @FXML
    private void toggleTreeView() {
        logger.info("Toggling Tree View");
        // treeViewToggle removed from UI
        // boolean selected = treeViewToggle.isSelected();
        boolean selected = false; // Default to false since button is removed
        logToConsole("Tree view " + (selected ? "enabled" : "disabled"));

        if (selected && documentTreeView != null) {
            Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
            if (currentTab != null && currentTab instanceof XmlEditor editor) {
                updateDocumentTree(editor.getXmlCodeEditor().getText());
            }
        }
    }

    // Template operations moved to popup controller

    // Schema generation operations moved to popup controller

    /**
     * XSLT Operations
     */
    @FXML
    private void loadXsltFile() {
        logger.info("Loading XSLT");
        logToConsole("Loading XSLT stylesheet...");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XSLT File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null && xsltStylesheetEditor != null) {
            try {
                currentXsltContent = Files.readString(file.toPath());
                xsltStylesheetEditor.getCodeArea().replaceText(currentXsltContent);
                logToConsole("XSLT loaded: " + file.getName());
            } catch (IOException e) {
                showError("Load Error", "Could not load XSLT: " + e.getMessage());
                logger.error("Failed to load XSLT", e);
            }
        }
    }

    @FXML
    private void loadXmlSourceFile() {
        logger.info("Loading XML Source for XSLT");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open XML Source File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null && xmlSourceEditor != null) {
            try {
                String xmlContent = Files.readString(file.toPath());
                xmlSourceEditor.getCodeArea().replaceText(xmlContent);
                logToConsole("XML Source loaded: " + file.getName());
            } catch (IOException e) {
                showError("Load Error", "Could not load XML: " + e.getMessage());
                logger.error("Failed to load XML Source", e);
            }
        }
    }

    @FXML
    private void saveXmlSourceFile() {
        logger.info("Saving XML Source");
        logToConsole("Saving XML Source...");

        if (xmlSourceEditor != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XML Source File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), xmlSourceEditor.getCodeArea().getText());
                    logToConsole("XML Source saved: " + file.getName());
                } catch (IOException e) {
                    showError("Save Error", "Could not save XML: " + e.getMessage());
                    logger.error("Failed to save XML Source", e);
                }
            }
        }
    }

    @FXML
    private void saveXsltFile() {
        logger.info("Saving XSLT");
        logToConsole("Saving XSLT stylesheet...");

        if (xsltStylesheetEditor != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XSLT File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSLT Files", "*.xsl")
            );

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), xsltStylesheetEditor.getCodeArea().getText());
                    logToConsole("XSLT saved: " + file.getName());
                } catch (IOException e) {
                    showError("Save Error", "Could not save XSLT: " + e.getMessage());
                    logger.error("Failed to save XSLT", e);
                }
            }
        }
    }

    @FXML
    private void executeTransformation() {
        logger.info("Executing XSLT Transformation");
        logToConsole("Executing XSLT transformation...");

        // Use XML from xmlSourceEditor instead of current tab
        if (xmlSourceEditor != null && xsltStylesheetEditor != null) {
            String xml = xmlSourceEditor.getCodeArea().getText();
            String xslt = xsltStylesheetEditor.getCodeArea().getText();

            if (xslt.isEmpty()) {
                showError("XSLT Error", "Please load or enter an XSLT stylesheet");
                return;
            }

            Task<XsltTransformationResult> transformTask = new Task<>() {
                @Override
                protected XsltTransformationResult call() throws Exception {
                    return xsltEngine.transform(xml, xslt, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML);
                }
            };

            transformTask.setOnSucceeded(e -> {
                XsltTransformationResult result = transformTask.getValue();

                if (transformationResultArea != null) {
                    transformationResultArea.setText(result.getOutputContent());
                }

                if (transformationPreviewWeb != null && "HTML".equals(outputFormatCombo.getValue())) {
                    transformationPreviewWeb.getEngine().loadContent(result.getOutputContent());
                }

                if (performanceResultArea != null) {
                    performanceResultArea.setText(
                            "Transformation Time: " + result.getTransformationTime() + "ms\n" +
                                    "Output Size: " + result.getOutputSize() + " bytes\n" +
                                    "Success: " + result.isSuccess()
                    );
                }

                logToConsole("Transformation completed in " + result.getTransformationTime() + "ms");
            });

            transformTask.setOnFailed(e -> {
                showError("Transformation Error", "Could not transform XML: " + transformTask.getException().getMessage());
                logger.error("Failed to transform XML", transformTask.getException());
            });

            executorService.submit(transformTask);
        }
    }

    /**
     * Template Parameter Operations
     */
    @FXML
    private void addTemplateParameter() {
        logger.info("Adding Template Parameter");
        logToConsole("Adding new template parameter...");

        if (templateParametersTable != null && selectedTemplate != null) {
            TextInputDialog dialog = new TextInputDialog("newParam");
            dialog.setTitle("Add Parameter");
            dialog.setHeaderText("Add Template Parameter");
            dialog.setContentText("Parameter name:");

            dialog.showAndWait().ifPresent(name -> {
                TemplateParameter newParam = TemplateParameter.stringParam(name, "");
                selectedTemplate.addParameter(newParam);
                templateParametersTable.getItems().add(newParam);
                currentTemplateParams.put(name, "");
                logToConsole("Added parameter: " + name);
            });
        }
    }

    @FXML
    private void validateTemplateParameters() {
        logger.info("Validating Template Parameters");
        logToConsole("Validating template parameters...");

        if (selectedTemplate != null) {
            List<String> errors = new ArrayList<>();

            for (TemplateParameter param : selectedTemplate.getParameters()) {
                String value = currentTemplateParams.get(param.getName());

                if (param.isRequired() && (value == null || value.isEmpty())) {
                    errors.add("Parameter '" + param.getName() + "' is required");
                }

                if (param.getValidationPattern() != null && value != null && !value.matches(param.getValidationPattern())) {
                    errors.add("Parameter '" + param.getName() + "' does not match pattern: " + param.getValidationPattern());
                }
            }

            if (errors.isEmpty()) {
                logToConsole("✓ All parameters are valid");
                showInfo("Validation Success", "All template parameters are valid");
            } else {
                String errorMsg = String.join("\n", errors);
                logToConsole("✗ Validation errors:\n" + errorMsg);
                showError("Validation Failed", errorMsg);
            }
        }
    }

    @FXML
    private void resetTemplateParameters() {
        logger.info("Resetting Template Parameters");
        logToConsole("Resetting template parameters to defaults...");

        if (selectedTemplate != null && templateParametersTable != null) {
            currentTemplateParams.clear();
            for (TemplateParameter param : selectedTemplate.getParameters()) {
                String defaultValue = param.getDefaultValue();
                if (defaultValue == null) defaultValue = "";
                currentTemplateParams.put(param.getName(), defaultValue);
                param.setDefaultValue(defaultValue);
            }
            templateParametersTable.refresh();
            logToConsole("Parameters reset to defaults");
        }
    }

    @FXML
    private void generateTemplateXml() {
        logger.info("Generating Template XML");
        logToConsole("Generating XML from template...");

        if (selectedTemplate != null && templatePreviewArea != null) {
            try {
                String generatedXml = selectedTemplate.processTemplate(currentTemplateParams);
                templatePreviewArea.setText(generatedXml);
                logToConsole("Template XML generated successfully");
            } catch (Exception e) {
                templatePreviewArea.setText("Error: " + e.getMessage());
                showError("Generation Error", "Could not generate XML: " + e.getMessage());
                logger.error("Failed to generate template XML", e);
            }
        }
    }

    @FXML
    private void insertGeneratedTemplate() {
        logger.info("Inserting Generated Template");
        logToConsole("Inserting generated XML into editor...");

        if (selectedTemplate != null) {
            try {
                String generatedXml = selectedTemplate.processTemplate(currentTemplateParams);

                Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
                if (currentTab != null && currentTab instanceof XmlEditor editor) {
                    String currentContent = editor.getXmlCodeEditor().getText();

                    // Insert at cursor position or append
                    editor.codeArea.replaceText(currentContent + "\n\n" + generatedXml);
                    logToConsole("Template inserted into current document");
                }
            } catch (Exception e) {
                showError("Insert Error", "Could not insert template: " + e.getMessage());
                logger.error("Failed to insert template", e);
            }
        }
    }

    /**
     * Console Operations
     */
    @FXML
    private void clearConsole() {
        if (consoleOutput != null) {
            consoleOutput.clear();
            consoleOutput.appendText("Console cleared.\n");
        }
    }

    /**
     * Helper Methods
     */
    private void logToConsole(String message) {
        if (consoleOutput != null) {
            Platform.runLater(() ->
                    consoleOutput.appendText("[" + java.time.LocalTime.now() + "] " + message + "\n")
            );
        }
    }

    private void updateDocumentTree(String xml) {
        if (documentTreeView != null) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(xml)));

                TreeItem<String> root = new TreeItem<>(doc.getDocumentElement().getNodeName());
                // TODO: Recursively build tree from DOM
                documentTreeView.setRoot(root);
            } catch (Exception e) {
                logger.error("Failed to update document tree", e);
            }
        }
    }

    private void updateCurrentXmlFromProperties() {
        // Update current XML based on property changes
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor) {
            // TODO: Implement XML update from properties
        }
    }

    private String formatXml(String xml, boolean indent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        return writer.toString();
    }

    // XPath/XQuery Methods
    private String executeXQuery(String xmlContent, String xQuery) throws Exception {
        try {
            net.sf.saxon.s9api.Processor saxon = new net.sf.saxon.s9api.Processor(false);
            net.sf.saxon.s9api.XQueryCompiler compiler = saxon.newXQueryCompiler();
            net.sf.saxon.s9api.XQueryExecutable executable = compiler.compile(xQuery);

            net.sf.saxon.s9api.DocumentBuilder builder = saxon.newDocumentBuilder();
            javax.xml.transform.Source src = new javax.xml.transform.stream.StreamSource(
                    new java.io.StringReader(xmlContent));
            net.sf.saxon.s9api.XdmNode doc = builder.build(src);

            net.sf.saxon.s9api.XQueryEvaluator evaluator = executable.load();
            evaluator.setContextItem(doc);
            net.sf.saxon.s9api.XdmValue result = evaluator.evaluate();

            StringBuilder resultBuilder = new StringBuilder();
            for (net.sf.saxon.s9api.XdmItem item : result) {
                if (resultBuilder.length() > 0) {
                    resultBuilder.append(System.lineSeparator());
                }
                resultBuilder.append(item.toString());
            }

            return resultBuilder.toString();
        } catch (Exception e) {
            logger.error("XQuery execution failed: {}", e.getMessage(), e);
            throw new Exception("XQuery execution failed: " + e.getMessage(), e);
        }
    }

    private XmlEditor getCurrentEditor() {
        if (xmlFilesPane != null) {
            Tab selectedTab = xmlFilesPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof XmlEditor) {
                return (XmlEditor) selectedTab;
            }
        }
        return null;
    }

    /**
     * Toggles the minimap for the current XML editor.
     */
    public void toggleMinimapForCurrentEditor(boolean visible) {
        XmlEditor currentEditor = getCurrentEditor();
        if (currentEditor != null && currentEditor.getXmlCodeEditor() != null) {
            if (visible) {
                currentEditor.getXmlCodeEditor().toggleMinimap();
                if (!currentEditor.getXmlCodeEditor().isMinimapVisible()) {
                    currentEditor.getXmlCodeEditor().toggleMinimap(); // Ensure it's visible
                }
            } else {
                if (currentEditor.getXmlCodeEditor().isMinimapVisible()) {
                    currentEditor.getXmlCodeEditor().toggleMinimap(); // Hide it
                }
            }
            logger.debug("Minimap toggled for current editor: {}", visible);
        } else {
            logger.debug("No current XML editor available for minimap toggle");
        }
    }

    @FXML
    private void runXpathQueryPressed() {
        XmlEditor currentEditor = getCurrentEditor();
        if (currentEditor == null || currentEditor.getXmlCodeEditor() == null) {
            showError("No Editor", "No XML editor is currently active");
            return;
        }

        CodeArea currentCodeArea = currentEditor.getXmlCodeEditor().getCodeArea();
        if (currentCodeArea == null || currentCodeArea.getText() == null) {
            return;
        }

        String xml = currentCodeArea.getText();
        Tab selectedItem = xPathQueryPane.getSelectionModel().getSelectedItem();

        final String query;
        try {
            if ("xPathTab".equals(selectedItem.getId())) {
                query = codeAreaXpath.getText();
            } else if ("xQueryTab".equals(selectedItem.getId())) {
                query = codeAreaXQuery.getText();
            } else {
                return;
            }
        } catch (Exception e) {
            logger.error("Error accessing query text: {}", e.getMessage());
            return;
        }

        if (query == null || query.trim().isEmpty()) {
            logger.warn("Query is empty, nothing to execute");
            return;
        }

        logger.debug("Executing query: {}", query);

        // Update status label
        if (queryResultLabel != null) {
            Platform.runLater(() -> queryResultLabel.setText("Executing query..."));
        }

        Task<String> queryTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                if ("xQueryTab".equals(selectedItem.getId())) {
                    // Execute XQuery with XML content as context
                    return executeXQuery(xml, query);
                } else if ("xPathTab".equals(selectedItem.getId())) {
                    return currentEditor.getXmlService().getXmlFromXpath(xml, query);
                } else {
                    return "";
                }
            }
        };

        queryTask.setOnSucceeded(event -> {
            String queryResult = queryTask.getValue();
            if (queryResult != null && !queryResult.isEmpty()) {
                logger.debug("Query result length: {}", queryResult.length());
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, queryResult);
                if (queryResultLabel != null) {
                    queryResultLabel.setText("Query executed successfully (" + queryResult.length() + " chars)");
                }
                logToConsole("Query executed successfully");
            } else {
                logger.debug("Query returned empty result");
                if (queryResultLabel != null) {
                    queryResultLabel.setText("Query returned no results");
                }
                logToConsole("Query returned no results");
            }
        });

        queryTask.setOnFailed(event -> {
            logger.error("Query execution failed", queryTask.getException());
            if (queryResultLabel != null) {
                queryResultLabel.setText("Error: " + queryTask.getException().getMessage());
            }
            showError("Query Error", "Failed to execute query: " + queryTask.getException().getMessage());
        });

        executorService.submit(queryTask);
    }

    @FXML
    private void clearXpathQuery() {
        Tab selectedTab = xPathQueryPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            try {
                if ("xPathTab".equals(selectedTab.getId())) {
                    codeAreaXpath.clear();
                } else if ("xQueryTab".equals(selectedTab.getId())) {
                    codeAreaXQuery.clear();
                }
                if (queryResultLabel != null) {
                    queryResultLabel.setText("Query cleared");
                }
            } catch (Exception e) {
                logger.error("Error clearing query: {}", e.getMessage());
            }
        }
    }

    @FXML
    private void insertXPathExample1() {
        codeAreaXpath.replaceText("//node");
        xPathQueryPane.getSelectionModel().select(0);
    }

    @FXML
    private void insertXPathExample2() {
        codeAreaXpath.replaceText("/root/child[@attr='value']");
        xPathQueryPane.getSelectionModel().select(0);
    }

    @FXML
    private void insertXPathExample3() {
        codeAreaXpath.replaceText("//text()");
        xPathQueryPane.getSelectionModel().select(0);
    }

    @FXML
    private void insertXPathExample4() {
        codeAreaXpath.replaceText("count(//element)");
        xPathQueryPane.getSelectionModel().select(0);
    }

    @FXML
    private void insertXQueryExample1() {
        codeAreaXQuery.replaceText("for $x in //item return $x");
        xPathQueryPane.getSelectionModel().select(1);
    }

    @FXML
    private void insertXQueryExample2() {
        codeAreaXQuery.replaceText("for $x in //item where $x/@id='1' return $x/name");
        xPathQueryPane.getSelectionModel().select(1);
    }

    @FXML
    private void reloadXmlText() {
        XmlEditor currentEditor = getCurrentEditor();
        if (currentEditor != null && currentEditor.getXmlFile() != null && currentEditor.getXmlFile().exists()) {
            currentEditor.refresh();
            logToConsole("XML reloaded from file");
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("Ultimate XML Editor");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Property entry class for properties table
     */
    public static class PropertyEntry {
        private String name;
        private String value;

        public PropertyEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * Set the parent MainController reference
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
        logger.debug("Parent controller set for Ultimate XML Controller");
    }

    /**
     * Open Template Manager Popup
     */
    private void openTemplateManagerPopup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/popup_templates.fxml"));

            // Create and set controller instance BEFORE loading
            TemplateManagerPopupController controller = new TemplateManagerPopupController(
                    this, templateEngine, templateRepository
            );
            loader.setController(controller);

            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/popups.css").toExternalForm());

            Stage popupStage = new Stage();
            popupStage.setTitle("Smart Templates Manager");
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setResizable(true);
            popupStage.setScene(scene);

            popupStage.showAndWait();
            logToConsole("Template Manager popup closed");
        } catch (Exception e) {
            logger.error("Failed to open Template Manager popup", e);
            showError("Popup Error", "Could not open Template Manager: " + e.getMessage());
        }
    }

    /**
     * Open Schema Generator Popup
     */
    private void openSchemaGeneratorPopup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/popup_schema_generator.fxml"));

            // Create and set controller instance BEFORE loading
            SchemaGeneratorPopupController controller = new SchemaGeneratorPopupController(
                    this, schemaEngine
            );
            loader.setController(controller);

            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/popups.css").toExternalForm());

            Stage popupStage = new Stage();
            popupStage.setTitle("Intelligent Schema Generator");
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setResizable(true);
            popupStage.setScene(scene);

            popupStage.showAndWait();
            logToConsole("Schema Generator popup closed");
        } catch (Exception e) {
            logger.error("Failed to open Schema Generator popup", e);
            showError("Popup Error", "Could not open Schema Generator: " + e.getMessage());
        }
    }

    /**
     * Get current XML content for popup controllers
     */
    public String getCurrentXmlContent() {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            return editor.getXmlCodeEditor().getText();
        }
        return "";
    }

    /**
     * Set XML content in current editor
     */
    public void setCurrentXmlContent(String content) {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            editor.getXmlCodeEditor().setText(content);
        }
    }

    /**
     * Insert XML content at cursor position in current editor
     */
    public void insertXmlContent(String content) {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            int caretPosition = editor.getXmlCodeEditor().getCodeArea().getCaretPosition();
            editor.getXmlCodeEditor().getCodeArea().insertText(caretPosition, content);
        }
    }

    /**
     * Sets the visibility of the development pane (XPath/XQuery, XSLT, Templates, Console).
     * This method is called from the MainController based on user preference.
     *
     * @param isVisible true to show the pane, false to hide it.
     */
    public void setDevelopmentPaneVisible(boolean isVisible) {
        if (mainSplitPane == null || developmentTabPane == null) {
            logger.warn("Cannot set development pane visibility: mainSplitPane or developmentTabPane is null.");
            return;
        }

        if (isVisible) {
            // Add the developmentTabPane back to the SplitPane if it's not already there
            if (!mainSplitPane.getItems().contains(developmentTabPane)) {
                mainSplitPane.getItems().add(developmentTabPane);
                // Restore the divider position to make the development pane visible
                mainSplitPane.setDividerPositions(0.75); // Adjust as needed
                logger.debug("Development pane set to visible.");
            } else {
                logger.debug("Development pane is already visible.");
            }
        } else {
            // Remove the developmentTabPane from the SplitPane
            if (mainSplitPane.getItems().contains(developmentTabPane)) {
                mainSplitPane.getItems().remove(developmentTabPane);
                logger.debug("Development pane set to hidden.");
            } else {
                logger.debug("Development pane is already hidden.");
            }
        }
    }

    /**
     * Sets the visibility of the XML Editor sidebar for all open XML editor tabs.
     * This method is called from the MainController based on the Windows menu checkbox.
     *
     * @param isVisible true to show the sidebar, false to hide it.
     */
    public void setXmlEditorSidebarVisible(boolean isVisible) {
        logger.debug("Setting XML Editor sidebar visibility to: {}", isVisible);

        if (xmlFilesPane != null) {
            // Apply to all XML editor tabs
            for (Tab tab : xmlFilesPane.getTabs()) {
                if (tab instanceof XmlEditor xmlEditor) {
                    xmlEditor.setXmlEditorSidebarVisible(isVisible);
                }
            }
            logger.debug("Applied sidebar visibility to {} XML editor tabs", xmlFilesPane.getTabs().size());
        } else {
            logger.warn("Cannot set XML Editor sidebar visibility: xmlFilesPane is null");
        }
    }

    /**
     * Sets the visibility of the Favorites Panel by completely removing/adding it to the SplitPane
     * When hidden, replaces the SplitPane with just the XML editor to remove empty space
     *
     * @param isVisible true to show the panel, false to hide it completely
     */
    public void setFavoritesPanelVisible(boolean isVisible) {
        logger.debug("Setting Favorites Panel visibility to: {}", isVisible);

        if (mainSplitPane == null || horizontalSplitPane == null || xmlFilesPane == null || favoritesPanelNode == null) {
            logger.warn("Cannot set Favorites Panel visibility: required components are null");
            return;
        }

        if (isVisible) {
            // Show the favorites panel by restoring the horizontal SplitPane structure
            if (!mainSplitPane.getItems().contains(horizontalSplitPane)) {
                // Replace xmlFilesPane with horizontalSplitPane in mainSplitPane
                int xmlFilesIndex = mainSplitPane.getItems().indexOf(xmlFilesPane);
                if (xmlFilesIndex >= 0) {
                    mainSplitPane.getItems().set(xmlFilesIndex, horizontalSplitPane);
                } else {
                    mainSplitPane.getItems().add(0, horizontalSplitPane);
                }

                // Ensure both components are in the horizontal SplitPane
                if (!horizontalSplitPane.getItems().contains(xmlFilesPane)) {
                    horizontalSplitPane.getItems().add(0, xmlFilesPane);
                }
                if (!horizontalSplitPane.getItems().contains(favoritesPanelNode)) {
                    horizontalSplitPane.getItems().add(favoritesPanelNode);
                }

                // Set the divider position to give 25% to favorites panel
                Platform.runLater(() -> horizontalSplitPane.setDividerPositions(0.75));
                logger.debug("Favorites panel restored in horizontal SplitPane with 25% width");
            } else {
                // Horizontal SplitPane already exists, just add favorites panel if needed
                if (!horizontalSplitPane.getItems().contains(favoritesPanelNode)) {
                    horizontalSplitPane.getItems().add(favoritesPanelNode);
                    Platform.runLater(() -> horizontalSplitPane.setDividerPositions(0.75));
                    logger.debug("Favorites panel added to existing horizontal SplitPane");
                }
            }
        } else {
            // Hide the favorites panel by replacing horizontal SplitPane with just xmlFilesPane
            if (mainSplitPane.getItems().contains(horizontalSplitPane)) {
                // Remove xmlFilesPane from horizontal SplitPane first
                horizontalSplitPane.getItems().remove(xmlFilesPane);
                // Replace horizontal SplitPane with xmlFilesPane directly in mainSplitPane
                int horizontalSplitIndex = mainSplitPane.getItems().indexOf(horizontalSplitPane);
                mainSplitPane.getItems().set(horizontalSplitIndex, xmlFilesPane);
                logger.debug("Horizontal SplitPane replaced with xmlFilesPane directly - favorites panel completely hidden");
            } else {
                logger.debug("Favorites panel already hidden");
            }
        }
    }

    /**
     * Loads a file to a new tab in the XML editor.
     * Used by the Favorites panel to open files.
     *
     * @param file The file to load
     */
    public void loadFileToNewTab(File file) {
        if (file != null && file.exists()) {
            try {
                String content = Files.readString(file.toPath());

                if (xmlFilesPane != null) {
                    XmlEditor xmlEditor = new XmlEditor();
                    xmlEditor.setMainController(parentController);
                    xmlEditor.setText(file.getName());
                    xmlEditor.getXmlCodeEditor().setText(content);

                    // Set the XML file to trigger automatic XSD schema detection
                    xmlEditor.setXmlFile(file);

                    // Store the File object in userData so favorites can access it
                    xmlEditor.setUserData(file);

                    // Apply current sidebar visibility setting
                    String sidebarVisible = propertiesService.get("xmlEditorSidebar.visible");
                    if (sidebarVisible != null && !Boolean.parseBoolean(sidebarVisible)) {
                        xmlEditor.setXmlEditorSidebarVisible(false);
                    }

                    xmlFilesPane.getTabs().add(xmlEditor);
                    xmlFilesPane.getSelectionModel().select(xmlEditor);
                }

                updateDocumentTree(content);
                validateCurrentXml();

                logger.info("Loaded file from favorites: {}", file.getName());
                logToConsole("Loaded: " + file.getAbsolutePath());

            } catch (IOException e) {
                logger.error("Failed to read file: {}", file.getAbsolutePath(), e);
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the currently selected file from the active tab.
     *
     * @return The current file or null if no file is open
     */
    public File getCurrentFile() {
        if (xmlFilesPane != null) {
            Tab currentTab = xmlFilesPane.getSelectionModel().getSelectedItem();
            if (currentTab != null && currentTab instanceof XmlEditor xmlEditor) {
                Object userData = xmlEditor.getUserData();
                if (userData instanceof File) {
                    return (File) userData;
                } else if (xmlEditor.getXmlFile() != null) {
                    return xmlEditor.getXmlFile();
                }
            }
        }
        return null;
    }

    /**
     * Update button states based on current file association status
     */
    private void updateButtonStates() {
        XmlEditor currentEditor = getCurrentEditor();
        boolean hasAssociatedFile = currentEditor != null && currentEditor.getXmlFile() != null;

        if (saveFile != null) {
            saveFile.setDisable(!hasAssociatedFile);
        }
        if (saveAsFile != null) {
            // Save As is always enabled when there's an editor
            saveAsFile.setDisable(currentEditor == null);
        }
    }
}
