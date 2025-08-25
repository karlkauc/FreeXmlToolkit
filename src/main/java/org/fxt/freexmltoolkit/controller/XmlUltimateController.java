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
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
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
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;

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
<<<<<<< HEAD
    public TabPane xmlFilesPane;
=======
    TabPane xmlFilesPane;
>>>>>>> 74fde297ea26dfb59bc2e61c16a6583b47c7050b

    // Note: Document tree, validation, properties, and namespaces are now handled by individual tab sidebars

<<<<<<< HEAD
    // Note: Smart Templates and Schema Generator are now popup dialogs
=======
    // XSD Schema Configuration (merged from XmlEditorSidebar)
    @FXML
    private TextField xsdPathField;
    @FXML
    private Button changeXsdButton;
    @FXML
    private Label validationStatusLabel;
    @FXML
    private CheckBox continuousValidationCheckBox;

    // Validation Errors (merged from XmlEditorSidebar)
    @FXML
    private TitledPane validationErrorsPane;
    @FXML
    private Label validationErrorsCountLabel;
    @FXML
    private ListView<String> validationErrorsListView;

    // Schematron Configuration and Errors (merged from XmlEditorSidebar)
    @FXML
    private TextField schematronPathField;
    @FXML
    private Button changeSchematronButton;
    @FXML
    private Label schematronValidationStatusLabel;
    @FXML
    private Button schematronDetailsButton;
    @FXML
    private CheckBox continuousSchematronValidationCheckBox;
    @FXML
    private TitledPane schematronErrorsPane;
    @FXML
    private Label schematronErrorsCountLabel;
    @FXML
    private ListView<String> schematronErrorsListView;

    // Cursor Information (merged from XmlEditorSidebar)
    @FXML
    private TextField xpathField;
    @FXML
    private TextField elementNameField;
    @FXML
    private TextField elementTypeField;

    // Node Documentation (merged from XmlEditorSidebar)
    @FXML
    private TextArea documentationTextArea;

    // Example Values and Child Elements (merged from XmlEditorSidebar)
    @FXML
    private ListView<String> exampleValuesListView;
    @FXML
    private ListView<String> childElementsListView;

    // Smart Templates Panel
    @FXML
    private TitledPane templatesPanel;
    @FXML
    private ComboBox<String> templateCategoryCombo;
    @FXML
    private Button refreshTemplatesButton;
    @FXML
    private ListView<XmlTemplate> templatesListView;
    @FXML
    private Button applyTemplateButton;
    @FXML
    private Button previewTemplateButton;

    // Schema Generation Panel
    @FXML
    private TitledPane schemaPanel;
    @FXML
    private CheckBox inferTypesCheckbox;
    @FXML
    private CheckBox flattenSchemaCheckbox;
    @FXML
    private Button generateSchemaButton;
    @FXML
    private Button exportSchemaButton;
    @FXML
    private TextArea schemaPreviewArea;
>>>>>>> 74fde297ea26dfb59bc2e61c16a6583b47c7050b

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
    private String generatedSchemaContent = "";

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
        // Note: Template category combo is now created in the popup dialog
    }

    private void initializeUI() {
        if (consoleOutput != null) {
            consoleOutput.appendText("Ultimate XML Editor initialized.\n");
            consoleOutput.appendText("All revolutionary features are available.\n");
        }
        // Note: Template list view is now created in the popup dialog
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
        // Note: Properties table is now handled by individual tab sidebars
        
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
        // Note: Templates are now loaded directly in the popup dialog
        logger.debug("Templates loaded: {} templates available", templateRepository.getAllTemplates().size());
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
                String content = readString(file.toPath());
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
                    writeString(currentXmlFile.toPath(), currentXmlContent);
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

                    // Note: Schema validation is now handled by individual tab sidebars
                    
                    return errors;
                }
            };

            validationTask.setOnSucceeded(e -> {
                List<String> results = validationTask.getValue();
                // Note: Validation results are now displayed in individual tab sidebars
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

            // Note: Lint results are now displayed in individual tab sidebars
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
        logger.info("Opening Template Manager");
        logToConsole("Opening Smart Templates System...");

        // Create popup dialog for Smart Templates
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Smart Templates");
        dialog.setHeaderText("Intelligent XML templates for rapid development");
        dialog.setResizable(true);

        // Create dialog content
        VBox content = new VBox(10);
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        content.setPadding(new javafx.geometry.Insets(10));

        // Template category selection
        HBox categoryBox = new HBox(10);
        categoryBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label categoryLabel = new Label("Category:");
        ComboBox<String> templateCategoryCombo = new ComboBox<>();
        templateCategoryCombo.setItems(FXCollections.observableArrayList(
                "All Templates", "Finance", "Healthcare", "Automotive",
                "Government", "Generic", "Web Services", "Configuration"
        ));
        templateCategoryCombo.setValue("All Templates");
        templateCategoryCombo.setPrefWidth(150);

        Button refreshButton = new Button();
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setTooltip(new Tooltip("Refresh template library"));
        refreshButton.setOnAction(e -> loadTemplates());

        categoryBox.getChildren().addAll(categoryLabel, templateCategoryCombo, refreshButton);

        // Template list
        ListView<XmlTemplate> templatesListView = new ListView<>();
        templatesListView.setCellFactory(lv -> new ListCell<XmlTemplate>() {
            @Override
            protected void updateItem(XmlTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " - " + item.getDescription());
            }
        });
        templatesListView.setItems(FXCollections.observableArrayList(templateRepository.getAllTemplates()));
        VBox.setVgrow(templatesListView, javafx.scene.layout.Priority.ALWAYS);

        // Preview area
        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefHeight(150);
        previewArea.setPromptText("Select a template to preview");

        // Template selection handler
        templatesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTemplate = newVal;
                try {
                    // Initialize template parameters with defaults
                    Map<String, String> params = new HashMap<>();
                    for (TemplateParameter param : newVal.getParameters()) {
                        String defaultValue = param.getDefaultValue();
                        if (defaultValue == null || defaultValue.isEmpty()) {
                            defaultValue = param.isRequired() ? "REQUIRED_VALUE" : "";
                        }
                        params.put(param.getName(), defaultValue);
                    }
                    String preview = newVal.processTemplate(params);
                    previewArea.setText(preview);
                } catch (Exception e) {
                    previewArea.setText("Error: " + e.getMessage());
                }
            }
        });

        content.getChildren().addAll(categoryBox, new Separator(), templatesListView, new Separator(),
                new Label("Preview:"), previewArea);

        dialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType applyButtonType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

        // Handle apply
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == applyButtonType) {
            XmlTemplate selected = templatesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                applyTemplate(selected);
            }
        }
    }

    private void applyTemplate(XmlTemplate template) {
        try {
            // Initialize parameters with defaults
            Map<String, String> params = new HashMap<>();
            for (TemplateParameter param : template.getParameters()) {
                String defaultValue = param.getDefaultValue();
                if (defaultValue == null || defaultValue.isEmpty()) {
                    defaultValue = param.isRequired() ? "REQUIRED_VALUE" : "";
                }
                params.put(param.getName(), defaultValue);
            }

            String generatedXml = template.processTemplate(params);

            Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
            if (currentTab instanceof XmlEditor editor) {
                editor.codeArea.replaceText(generatedXml);
                logToConsole("Template '" + template.getName() + "' applied successfully");
            }
        } catch (Exception e) {
            showError("Template Error", "Could not apply template: " + e.getMessage());
            logger.error("Failed to apply template", e);
        }
    }

    @FXML
    private void showSchemaGenerator() {
        logger.info("Opening Schema Generator");
        logToConsole("Opening Intelligent Schema Generator...");

        Tab currentTab = xmlFilesPane != null ? xmlFilesPane.getSelectionModel().getSelectedItem() : null;
        if (!(currentTab instanceof XmlEditor editor)) {
            showError("No XML", "Please open an XML document first");
            return;
        }

        String xml = editor.codeArea.getText();
        if (xml == null || xml.trim().isEmpty()) {
            showError("Empty Document", "The current XML document is empty");
            return;
        }

        // Create popup dialog for Schema Generator
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Schema Generator");
        dialog.setHeaderText("Generate XSD schema from current XML document");
        dialog.setResizable(true);

        // Create dialog content
        VBox content = new VBox(10);
        content.setPrefWidth(600);
        content.setPrefHeight(450);
        content.setPadding(new javafx.geometry.Insets(10));

        // Options
        CheckBox inferTypesCheckbox = new CheckBox("Smart type inference");
        inferTypesCheckbox.setSelected(true);
        inferTypesCheckbox.setTooltip(new Tooltip("Automatically detect data types (string, int, date, etc.)"));

        CheckBox flattenSchemaCheckbox = new CheckBox("Flatten unnecessary structure");
        flattenSchemaCheckbox.setTooltip(new Tooltip("Remove redundant nested elements"));

        HBox optionsBox = new HBox(20);
        optionsBox.getChildren().addAll(inferTypesCheckbox, flattenSchemaCheckbox);

        // Schema preview area
        TextArea schemaPreviewArea = new TextArea();
        schemaPreviewArea.setEditable(false);
        schemaPreviewArea.setStyle("-fx-font-family: monospace");
        schemaPreviewArea.setPromptText("Generated XSD schema will appear here...");
        VBox.setVgrow(schemaPreviewArea, javafx.scene.layout.Priority.ALWAYS);

        // Progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);

        // Generate button
        Button generateButton = new Button("Generate Schema");
        generateButton.setGraphic(new FontIcon("bi-diagram-3"));
        generateButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        generateButton.setOnAction(e -> {
            progressIndicator.setVisible(true);
            generateButton.setDisable(true);

            Task<String> schemaTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    SchemaGenerationOptions options = new SchemaGenerationOptions();
                    options.setEnableSmartTypeInference(inferTypesCheckbox.isSelected());
                    options.setFlattenUnnecessaryStructure(flattenSchemaCheckbox.isSelected());

                    SchemaGenerationResult result = schemaEngine.generateSchema(xml, options);
                    return result.getXsdContent();
                }
            };

            schemaTask.setOnSucceeded(evt -> {
                generatedSchemaContent = schemaTask.getValue();
                schemaPreviewArea.setText(generatedSchemaContent);
                progressIndicator.setVisible(false);
                generateButton.setDisable(false);
                logToConsole("Schema generated successfully");
            });

            schemaTask.setOnFailed(evt -> {
                showError("Schema Generation Error", "Could not generate schema: " + schemaTask.getException().getMessage());
                progressIndicator.setVisible(false);
                generateButton.setDisable(false);
                logger.error("Failed to generate schema", schemaTask.getException());
            });

            executorService.submit(schemaTask);
        });

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(generateButton, progressIndicator);

        content.getChildren().addAll(
                new Label("Generation Options:"),
                optionsBox,
                new Separator(),
                buttonBox,
                new Separator(),
                new Label("Generated XSD Schema:"),
                schemaPreviewArea
        );

        dialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType exportButtonType = new ButtonType("Export", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CLOSE);

        // Get export button and disable initially
        Button exportBtn = (Button) dialog.getDialogPane().lookupButton(exportButtonType);
        exportBtn.setDisable(true);
        exportBtn.setGraphic(new FontIcon("bi-download"));

        // Enable export when schema is generated
        schemaPreviewArea.textProperty().addListener((obs, oldVal, newVal) -> {
            exportBtn.setDisable(newVal == null || newVal.trim().isEmpty());
        });

        // Handle result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == exportButtonType) {
                exportGeneratedSchema();
                return null; // Keep dialog open
            }
            return dialogButton;
        });

        dialog.showAndWait();
    }

    private void exportGeneratedSchema() {
        if (generatedSchemaContent != null && !generatedSchemaContent.isEmpty()) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export XSD Schema");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSD Files", "*.xsd")
            );
            fileChooser.setInitialFileName("schema.xsd");

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    writeString(file.toPath(), generatedSchemaContent);
                    logToConsole("Schema exported to: " + file.getAbsolutePath());
                    showInfo("Export Successful", "Schema exported to: " + file.getName());
                } catch (IOException e) {
                    showError("Export Error", "Could not export schema: " + e.getMessage());
                    logger.error("Failed to export schema", e);
                }
            }
        }
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

        // Note: Tree view is now handled by individual tab sidebars
        logToConsole("Tree view functionality is available in each XML tab's sidebar.");
    }

    /**
     * Template Operations - Now handled via popup dialogs
     */

    /**
     * Schema Generation Operations - Now handled via popup dialogs
     */

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
                currentXsltContent = readString(file.toPath());
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
                    writeString(file.toPath(), xsltEditorArea.getText());
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
        // Note: Document tree updates are now handled by individual tab sidebars
        logger.debug("Document tree update delegated to tab sidebars");
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
     * Load a file into the Ultimate XML Editor (called from MainController)
     */
    public void loadFileFromExternal(File file) {
        if (file == null || !file.exists()) {
            logger.warn("Cannot load file - file is null or doesn't exist: {}", file);
            return;
        }

        try {
            String content = readString(file.toPath());
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
            logToConsole("Loaded file from recent files: " + file.getAbsolutePath());
            logger.info("Successfully loaded file from external call: {}", file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to load file from external call: {}", e.getMessage(), e);
            showError("File Error", "Could not load file: " + e.getMessage());
        }
    }

    /**
     * Show Schematron validation details in a dialog
     */
    public void showSchematronDetails() {
        // Implementation for showing Schematron validation details
        // This would typically open a dialog with detailed Schematron validation results
        logger.info("Show Schematron details requested");
        logToConsole("Schematron details functionality - implementation needed");
    }
}