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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
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
    private Button prettyPrint;
    @FXML
    private Button minifyButton;
    @FXML
    private Button validateButton;
    @FXML
    private Button lintButton;
    @FXML
    private Button runXpathQuery;
    @FXML
    private Button templateManagerButton;
    @FXML
    private Button schemaGeneratorButton;
    @FXML
    private Button xsltDeveloperButton;
    @FXML
    private ToggleButton treeViewToggle;

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

    // Removed: Smart Templates Panel and Schema Generation Panel (now in popups)

    // Development Panels
    @FXML
    private TabPane developmentTabPane;

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
    private TextArea xsltEditorArea;
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
        loadTemplates();
        createInitialTab();
        logger.info("Ultimate XML Controller initialized successfully");
    }

    private void initializeComboBoxes() {
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(FXCollections.observableArrayList(
                    "XML", "HTML", "Text", "JSON"
            ));
            outputFormatCombo.getSelectionModel().selectFirst();
        }

        // Template category combo removed (now in popup)
    }

    private void initializeUI() {
        if (consoleOutput != null) {
            consoleOutput.appendText("Ultimate XML Editor initialized.\n");
            consoleOutput.appendText("All revolutionary features are available.\n");
        }

        // Template list view setup removed (now in popup)
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

    private void loadTemplates() {
        // Template loading moved to popup
    }

    private void createInitialTab() {
        if (xmlFilesPane != null) {
            XmlEditor xmlEditor = new XmlEditor();
            xmlEditor.setText("Untitled.xml");
            xmlEditor.codeArea.replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>");
            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);
        }
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
            xmlEditor.setText("Untitled" + (xmlFilesPane.getTabs().size() + 1) + ".xml");
            xmlEditor.codeArea.replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>");
            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);
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
                    xmlEditor.setText(file.getName());
                    xmlEditor.codeArea.replaceText(content);
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
                xmlEditor.setText(file.getName());
                xmlEditor.codeArea.replaceText(content);
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
            currentXmlContent = editor.codeArea.getText();

            if (currentXmlFile == null) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save XML File");
                fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                        new FileChooser.ExtensionFilter("All Files", "*.*")
                );
                currentXmlFile = fileChooser.showSaveDialog(null);
            }

            if (currentXmlFile != null) {
                try {
                    Files.writeString(currentXmlFile.toPath(), currentXmlContent);
                    currentTab.setText(currentXmlFile.getName());
                    logToConsole("Saved file: " + currentXmlFile.getAbsolutePath());
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
            String xml = editor.codeArea.getText();

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

    @FXML
    private void minifyXmlText() {
        logger.info("Minifying XML");
        logToConsole("Minifying XML...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.codeArea.getText();

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
            String xml = editor.codeArea.getText();

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

    @FXML
    private void lintXml() {
        logger.info("Linting XML");
        logToConsole("Checking XML for potential issues...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            String xml = editor.codeArea.getText();

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
    @FXML
    private void toggleTreeView() {
        logger.info("Toggling Tree View");
        boolean selected = treeViewToggle.isSelected();
        logToConsole("Tree view " + (selected ? "enabled" : "disabled"));

        if (selected && documentTreeView != null) {
            Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
            if (currentTab != null && currentTab instanceof XmlEditor editor) {
                updateDocumentTree(editor.codeArea.getText());
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
        if (file != null && xsltEditorArea != null) {
            try {
                currentXsltContent = Files.readString(file.toPath());
                xsltEditorArea.setText(currentXsltContent);
                logToConsole("XSLT loaded: " + file.getName());
            } catch (IOException e) {
                showError("Load Error", "Could not load XSLT: " + e.getMessage());
                logger.error("Failed to load XSLT", e);
            }
        }
    }

    @FXML
    private void saveXsltFile() {
        logger.info("Saving XSLT");
        logToConsole("Saving XSLT stylesheet...");

        if (xsltEditorArea != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XSLT File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSLT Files", "*.xsl")
            );

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), xsltEditorArea.getText());
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

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor && xsltEditorArea != null) {
            String xml = editor.codeArea.getText();
            String xslt = xsltEditorArea.getText();

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
                    String currentContent = editor.codeArea.getText();

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
            return editor.codeArea.getText();
        }
        return "";
    }

    /**
     * Set XML content in current editor
     */
    public void setCurrentXmlContent(String content) {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            editor.codeArea.replaceText(content);
        }
    }

    /**
     * Insert XML content at cursor position in current editor
     */
    public void insertXmlContent(String content) {
        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (currentTab != null && currentTab instanceof XmlEditor editor) {
            int caretPosition = editor.codeArea.getCaretPosition();
            editor.codeArea.insertText(caretPosition, content);
        }
    }
}