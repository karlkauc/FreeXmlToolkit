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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
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
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XmlController {
    private final static Logger logger = LogManager.getLogger(XmlController.class);

    private final static int XML_INDENT = 4;

    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    private MainController mainController;

    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, runXpathQuery, minifyButton;

    // New Revolutionary Features UI Components
    @FXML
    Button templateManagerButton, schemaGeneratorButton, xsltDeveloperButton;

    @FXML
    TitledPane templatesPanel, schemaPanel;

    @FXML
    ComboBox<String> templateCategoryCombo, outputFormatCombo;

    @FXML
    ListView<XmlTemplate> templatesListView;

    @FXML
    Button refreshTemplatesButton, applyTemplateButton, previewTemplateButton;

    @FXML
    CheckBox inferTypesCheckbox, flattenSchemaCheckbox, livePreviewCheckbox;

    @FXML
    Button generateSchemaButton, exportSchemaButton;

    @FXML
    TextArea schemaPreviewArea, xsltEditorArea, transformationResultArea, performanceResultArea, templatePreviewArea;

    @FXML
    WebView transformationPreviewWeb;

    @FXML
    TabPane developmentTabPane, xPathQueryPane, transformationResultsPane;

    @FXML
    Tab xsltDevelopmentTab, templateDevelopmentTab;

    @FXML
    Button loadXsltButton, saveXsltButton, transformButton;

    @FXML
    TableView<TemplateParameter> templateParametersTable;

    @FXML
    TableColumn<TemplateParameter, String> parameterNameColumn, parameterValueColumn, parameterTypeColumn;

    @FXML
    Button addParameterButton, validateParametersButton, resetParametersButton, generateTemplateButton, insertTemplateButton;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;

    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;

    @FXML
    Tab xPathTab, xQueryTab, xPathQueryTab;

    @FXML
    TabPane xmlFilesPane;

    @FXML
    TitledPane xPathQueryTitledPane;

    // Revolutionary Features Service Integration
    private final TemplateEngine templateEngine = TemplateEngine.getInstance();
    private final TemplateRepository templateRepository = TemplateRepository.getInstance();
    private final XsltTransformationEngine xsltEngine = XsltTransformationEngine.getInstance();
    private final SchemaGenerationEngine schemaEngine = SchemaGenerationEngine.getInstance();

    private XmlTemplate selectedTemplate;
    private final Map<String, String> currentTemplateParameters = new HashMap<>();

    private SplitPane parentVerticalSplitPane;

    @FXML
    TextArea textAreaTemp;

    @FXML
    XmlEditor emptyXmlEditor;

    private final ExecutorService formattingExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        return t;
    });

    @FXML
    private void initialize() {
        logger.debug("Initializing XML Controller");

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);

        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText)));
            }
        });

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);

        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText)));
            }
        });

        reloadXmlText();
        applyEditorSettings();
        initializeRevolutionaryFeatures();

        Platform.runLater(() -> {
            // Initialize development panels
            if (developmentTabPane != null) {
                developmentTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
                logger.debug("Development TabPane initialized");
            }
        });

        xmlFilesPane.setOnDragOver(this::handleFileOverEvent);
        xmlFilesPane.setOnDragExited(this::handleDragExitedEvent);
        xmlFilesPane.setOnDragDropped(this::handleFileDroppedEvent);
    }

    private void applyEditorSettings() {
        try {
            String fontSizeStr = propertiesService.get("ui.xml.font.size");
            int fontSize = 12;
            if (fontSizeStr != null) {
                try {
                    fontSize = Integer.parseInt(fontSizeStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid font size in settings, defaulting to 12.", e);
                }
            }
            String style = String.format("-fx-font-size: %dpx;", fontSize);

            if (codeAreaXpath != null) {
                codeAreaXpath.setStyle(style);
                logger.debug("Applied font size {}px to codeAreaXpath", fontSize);
            }
            if (codeAreaXQuery != null) {
                codeAreaXQuery.setStyle(style);
                logger.debug("Applied font size {}px to codeAreaXQuery", fontSize);
            }
        } catch (Exception e) {
            logger.error("Failed to apply editor settings.", e);
        }
    }

    private void initializeRevolutionaryFeatures() {
        logger.debug("Initializing Revolutionary XML Editor Features");

        // Initialize Templates Panel
        if (templateCategoryCombo != null) {
            templateCategoryCombo.setItems(FXCollections.observableArrayList(
                    "All", "Finance", "Healthcare", "Automotive", "Government", "Generic"
            ));
            templateCategoryCombo.setValue("All");
        }

        if (templatesListView != null) {
            templatesListView.setCellFactory(listView -> new ListCell<XmlTemplate>() {
                @Override
                protected void updateItem(XmlTemplate template, boolean empty) {
                    super.updateItem(template, empty);
                    if (empty || template == null) {
                        setText(null);
                    } else {
                        setText(template.getName() + " - " + template.getCategory());
                    }
                }
            });
            refreshTemplates();
        }

        // Initialize Schema Generation Panel
        if (inferTypesCheckbox != null) {
            inferTypesCheckbox.setSelected(true);
        }

        // Initialize XSLT Development Panel
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(FXCollections.observableArrayList("XML", "HTML", "Text", "JSON"));
            outputFormatCombo.setValue("XML");
        }

        // Initialize Template Parameters Table
        if (templateParametersTable != null) {
            parameterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            parameterValueColumn.setCellValueFactory(new PropertyValueFactory<>("defaultValue"));
            parameterTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        }

        logger.debug("Revolutionary XML Editor Features initialized successfully");
    }

    private void createAndAddXmlTab(File file) {
        XmlEditor xmlEditor = new XmlEditor(file);
        xmlEditor.setMainController(this.mainController);

        try {
            String fontSizeStr = propertiesService.get("ui.xml.font.size");
            int fontSize = 12;
            if (fontSizeStr != null) {
                fontSize = Integer.parseInt(fontSizeStr);
            }
            if (xmlEditor.getXmlCodeEditor() != null && xmlEditor.getXmlCodeEditor().getCodeArea() != null) {
                xmlEditor.getXmlCodeEditor().getCodeArea().setStyle(String.format("-fx-font-size: %dpx;", fontSize));
                logger.debug("Applied font size {}px to new XmlEditor tab.", fontSize);
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid font size in settings, using default for new tab.", e);
        } catch (Exception e) {
            logger.error("Failed to apply font size to new XmlEditor tab.", e);
        }

        xmlEditor.refresh();

        if (mainController != null) {
            boolean sidebarVisible = mainController.isXmlEditorSidebarVisible();
            xmlEditor.setXmlEditorSidebarVisible(sidebarVisible);
            logger.debug("Applied sidebar visibility setting to new tab: {}", sidebarVisible);
        }

        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);

        if (file != null) {
            mainController.addFileToRecentFiles(file);

            // Notify integration service of XML file change
            if (mainController.getIntegrationService() != null) {
                mainController.getIntegrationService().setCurrentXmlFile(file);
            }
        }
    }

    @FXML
    void handleFileOverEvent(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!xmlFilesPane.getStyleClass().contains("xmlPaneFileDragDrop-active")) {
                xmlFilesPane.getStyleClass().add("xmlPaneFileDragDrop-active");
            }
        } else {
            event.consume();
        }
    }

    @FXML
    void handleDragExitedEvent(DragEvent event) {
        xmlFilesPane.getStyleClass().clear();
        xmlFilesPane.getStyleClass().add("tab-pane");
    }

    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();

        for (File f : db.getFiles()) {
            logger.debug("add File: '{}': {}", f.getName(), f.getAbsolutePath());
            createAndAddXmlTab(f);
        }
    }

    public void loadFile(File f) {
        logger.debug("Loading file {} via createAndAddXmlTab", f.getAbsolutePath());
        createAndAddXmlTab(f);
    }

    @FXML
    public void newFilePressed() {
        logger.debug("New File Pressed");
        createAndAddXmlTab(null);
    }

    private XmlEditor getCurrentXmlEditor() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        return (XmlEditor) active;
    }

    @FXML
    private void increaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().increaseFontSize();
        }
    }

    @FXML
    private void decreaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().decreaseFontSize();
        }
    }

    private CodeArea getCurrentCodeArea() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null && editor.getXmlCodeEditor() != null) {
            return editor.getXmlCodeEditor().getCodeArea();
        }
        return null;
    }

    @FXML
    private void runXpathQueryPressed() {
        var currentCodeArea = getCurrentCodeArea();

        if (currentCodeArea != null && currentCodeArea.getText() != null) {
            String xml = currentCodeArea.getText();
            Tab selectedItem = xPathQueryPane.getSelectionModel().getSelectedItem();

            final String query;
            try {
                query = ((CodeArea) ((VirtualizedScrollPane<?>) ((StackPane) selectedItem.getContent()).getChildren().getFirst()).getContent()).getText();
            } catch (Exception e) {
                logger.error("Error accessing query text: {}", e.getMessage());
                return;
            }

            if (query == null || query.trim().isEmpty()) {
                logger.warn("Query is empty, nothing to execute");
                return;
            }

            logger.debug("QUERY: {}", query);

            Task<String> queryTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return switch (selectedItem.getId()) {
                        case "xQueryTab" ->
                                String.join(System.lineSeparator(), getCurrentXmlEditor().getXmlService().getXQueryResult(query));
                        case "xPathTab" -> getCurrentXmlEditor().getXmlService().getXmlFromXpath(xml, query);
                        default -> "";
                    };
                }
            };

            queryTask.setOnSucceeded(event -> {
                String queryResult = queryTask.getValue();
                if (queryResult != null && !queryResult.isEmpty()) {
                    logger.debug("Query result length: {}", queryResult.length());
                    currentCodeArea.clear();
                    currentCodeArea.replaceText(0, 0, queryResult);
                } else {
                    logger.debug("Query returned empty result");
                }
            });

            queryTask.setOnFailed(event -> {
                logger.error("Query execution failed", queryTask.getException());
            });

            new Thread(queryTask).start();
        }
    }

    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.mainController = parentController;
        if (mainController != null) {
            boolean xpathPaneVisible = mainController.isXPathQueryPaneVisible();
            Platform.runLater(() -> setXPathQueryPaneVisible(xpathPaneVisible));
        }
    }

    public void displayFileContent(File file) {
        if (file != null && file.exists()) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

                if (mainController != null) {
                    mainController.addFileToRecentFiles(file);
                }

                var area = getCurrentCodeArea();
                if (area != null) {
                    area.replaceText(content);
                    logger.debug("File {} displayed.", file.getName());
                }

            } catch (IOException e) {
                logger.error("Could not read file {}", file.getAbsolutePath(), e);
            }
        }
    }

    @FXML
    public void reloadXmlText() {
        try {
            XmlEditor xmlEditor = getCurrentXmlEditor();
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().exists()) {
                xmlEditor.refresh();

                if (xmlEditor.getXmlFile() != null && textAreaTemp != null) {
                    textAreaTemp.setText(xmlEditor.getXmlFile().getName());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void saveFile() {
        XmlEditor currentEditor = getCurrentXmlEditor();
        CodeArea currentCodeArea = getCurrentCodeArea();

        if (currentEditor == null || currentCodeArea == null) {
            logger.warn("Save action triggered, but no active editor found.");
            return;
        }

        String contentToValidate = currentCodeArea.getText();
        XmlService service = currentEditor.getXmlService();

        File schemaToUse = service.getCurrentXsdFile();

        List<SAXParseException> errors = service.validateText(contentToValidate, schemaToUse);

        if (errors != null && !errors.isEmpty()) {
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationDialog.setTitle("Validation Errors");
            confirmationDialog.setHeaderText(errors.size() + " validation errors found.");
            confirmationDialog.setContentText("The XML is not well-formed or not schema-compliant.\n\nReally save?");

            Optional<ButtonType> result = confirmationDialog.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        saveTextToFile(currentEditor);
    }

    private void saveTextToFile(XmlEditor editor) {
        File targetFile = editor.getXmlFile();
        String content = editor.getXmlCodeEditor().getCodeArea().getText();

        if (targetFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XML File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));

            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }

            File selectedFile = fileChooser.showSaveDialog(xmlFilesPane.getScene().getWindow());

            if (selectedFile == null) {
                return;
            }

            targetFile = selectedFile;
            editor.setXmlFile(targetFile);
            editor.getXmlService().setCurrentXmlFile(targetFile);
            mainController.addFileToRecentFiles(targetFile);

            if (targetFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(targetFile.getParentFile().getAbsolutePath());
            }
        }

        try {
            Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
            logger.info("File successfully saved: {}", targetFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing file: {}", targetFile.getAbsolutePath(), e);
            new Alert(Alert.AlertType.ERROR, "Could not save file:\n" + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void minifyXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String xml = currentCodeArea.getText();
        if (xml == null || xml.isBlank()) return;

        minifyButton.setDisable(true);

        Task<String> minifyTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return XmlService.convertXmlToOneLineFast(xml);
            }
        };

        minifyTask.setOnSucceeded(event -> {
            String minifiedString = minifyTask.getValue();
            if (minifiedString != null && !minifiedString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, minifiedString);
            }
            minifyButton.setDisable(false);
        });

        minifyTask.setOnFailed(event -> {
            logger.error("Failed to minify XML", minifyTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not minify XML: " + minifyTask.getException().getMessage()).showAndWait();
            minifyButton.setDisable(false);
        });

        formattingExecutor.submit(minifyTask);
    }

    @FXML
    private void prettifyingXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String text = currentCodeArea.getText();
        if (text == null || text.isBlank()) return;

        prettyPrint.setDisable(true);

        Task<String> formatTask = new Task<>() {
            @Override
            protected String call() {
                return XmlService.prettyFormat(text, XML_INDENT);
            }
        };

        formatTask.setOnSucceeded(event -> {
            String prettyString = formatTask.getValue();
            if (prettyString != null && !prettyString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, prettyString);
            }
            prettyPrint.setDisable(false);
        });

        formatTask.setOnFailed(event -> {
            logger.error("Failed to pretty-print XML", formatTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not format XML: " + formatTask.getException().getMessage()).showAndWait();
            prettyPrint.setDisable(false);
        });

        formattingExecutor.submit(formatTask);
    }

    @FXML
    private void moveUp() {
        logger.debug("Moving caret and scrollbar to the beginning.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveUp();
        }
    }

    @FXML
    private void moveDown() {
        logger.debug("Moving caret and scrollbar to the end.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveDown();
        }
    }

    @FXML
    private void openFile() {
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        fileChooser.getExtensionFilters().clear();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());

            if (selectedFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(selectedFile.getParentFile().getAbsolutePath());
            }

            createAndAddXmlTab(selectedFile);

            Platform.runLater(() -> {
                XmlEditor xmlEditor = getCurrentXmlEditor();
                xmlFilesPane.requestFocus();

                if (xmlEditor.getXmlCodeEditor().getCodeArea() != null) {
                    xmlEditor.getXmlCodeEditor().getCodeArea().requestFocus();
                    xmlEditor.getXmlCodeEditor().moveUp();
                }
            });
        } else {
            logger.debug("No file selected");
        }
    }

    public void shutdown() {
        if (!formattingExecutor.isShutdown()) {
            formattingExecutor.shutdownNow();
            logger.debug("Formatting-Executor-Service shut down.");
        }
        logger.info("XmlController shutdown completed.");
    }

    @FXML
    private void test() {
        XmlEditor currentEditor = getCurrentXmlEditor();

        if (currentEditor == null) {
            logger.info("Test button clicked, but no active editor found. Creating a new one.");
            createAndAddXmlTab(null);
            currentEditor = getCurrentXmlEditor();
        }

        Path xmlExampleFile = Paths.get("release/examples/xml/FundsXML_422_Bond_Fund.xml");
        Path xsdExampleFile = Paths.get("release/examples/xsd/FundsXML4.xsd");

        if (!Files.exists(xmlExampleFile)) {
            logger.error("Test file not found at path: {}", xmlExampleFile.toAbsolutePath());
            new Alert(Alert.AlertType.ERROR, "Test file not found: " + xmlExampleFile).showAndWait();
            return;
        }

        logger.debug("Loading test file '{}' into the current editor.", xmlExampleFile.getFileName());
        currentEditor.setXmlFile(xmlExampleFile.toFile());

        XmlService service = currentEditor.getXmlService();
        service.setCurrentXmlFile(xmlExampleFile.toFile());
        service.setCurrentXsdFile(xsdExampleFile.toFile());

        currentEditor.refresh();

        XmlEditor finalCurrentEditor = currentEditor;
        Platform.runLater(() -> {
            if (finalCurrentEditor.getXmlCodeEditor() != null) {
                finalCurrentEditor.getXmlCodeEditor().moveUp();

                finalCurrentEditor.getXmlCodeEditor().refreshSyntaxHighlighting();

                finalCurrentEditor.getXmlCodeEditor().refreshFoldingRegions();
            }
        });

        logger.debug("Test file loading complete.");
    }

    public void setXmlEditorSidebarVisible(boolean visible) {
        logger.debug("Setting XML Editor Sidebar visibility to: {}", visible);

        if (xmlFilesPane != null) {
            for (Tab tab : xmlFilesPane.getTabs()) {
                if (tab instanceof XmlEditor xmlEditor) {
                    xmlEditor.setXmlEditorSidebarVisible(visible);
                }
            }
        }
    }

    public void setXPathQueryPaneVisible(boolean visible) {
        logger.debug("Setting XPath Query Pane visibility to: {}", visible);
        logger.debug("xPathQueryTitledPane is null: {}", xPathQueryTitledPane == null);
        logger.debug("parentVerticalSplitPane is null: {}", parentVerticalSplitPane == null);

        if (xPathQueryTitledPane != null && parentVerticalSplitPane != null) {
            logger.debug("Current SplitPane items count: {}", parentVerticalSplitPane.getItems().size());
            logger.debug("TitledPane currently in SplitPane: {}", parentVerticalSplitPane.getItems().contains(xPathQueryTitledPane));

            if (visible) {
                if (!parentVerticalSplitPane.getItems().contains(xPathQueryTitledPane)) {
                    parentVerticalSplitPane.getItems().add(xPathQueryTitledPane);
                    parentVerticalSplitPane.setDividerPositions(0.8);
                    logger.debug("Added XPath Query TitledPane back to SplitPane");
                } else {
                    logger.debug("TitledPane already present in SplitPane");
                }
            } else {
                boolean removed = parentVerticalSplitPane.getItems().remove(xPathQueryTitledPane);
                logger.debug("Removed XPath Query TitledPane from SplitPane: {} - XmlEditor should now take full space", removed);
            }
            logger.debug("Final SplitPane items count: {}", parentVerticalSplitPane.getItems().size());
        } else {
            logger.warn("Cannot set XPath Query Pane visibility - missing references: xPathQueryTitledPane={}, parentVerticalSplitPane={}",
                    xPathQueryTitledPane != null, parentVerticalSplitPane != null);
        }
    }

    // ============== REVOLUTIONARY FEATURES EVENT HANDLERS ==============

    @FXML
    private void showTemplateManager() {
        logger.debug("Opening Template Manager");
        if (templatesPanel != null) {
            templatesPanel.setExpanded(true);
        }
        refreshTemplates();
    }

    @FXML
    private void showSchemaGenerator() {
        logger.debug("Opening Schema Generator");
        if (schemaPanel != null) {
            schemaPanel.setExpanded(true);
        }
    }

    @FXML
    private void showXsltDeveloper() {
        logger.debug("Opening XSLT Developer");
        if (developmentTabPane != null && xsltDevelopmentTab != null) {
            developmentTabPane.getSelectionModel().select(xsltDevelopmentTab);
        }
    }

    @FXML
    private void refreshTemplates() {
        if (templatesListView != null && templateCategoryCombo != null) {
            String selectedCategory = templateCategoryCombo.getValue();

            Task<List<XmlTemplate>> task = new Task<>() {
                @Override
                protected List<XmlTemplate> call() throws Exception {
                    if ("All".equals(selectedCategory)) {
                        return templateRepository.getAllTemplates();
                    } else {
                        return templateRepository.getTemplatesByCategory(selectedCategory);
                    }
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        templatesListView.setItems(FXCollections.observableList(getValue()));
                        logger.debug("Loaded {} templates for category: {}", getValue().size(), selectedCategory);
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to load templates", getException());
                    });
                }
            };

            formattingExecutor.submit(task);
        }
    }

    @FXML
    private void onTemplateSelected() {
        if (templatesListView != null) {
            selectedTemplate = templatesListView.getSelectionModel().getSelectedItem();
            if (selectedTemplate != null) {
                logger.debug("Selected template: {}", selectedTemplate.getName());
                updateTemplateParametersTable();
            }
        }
    }

    @FXML
    private void applySelectedTemplate() {
        if (selectedTemplate != null) {
            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return selectedTemplate.processTemplate(currentTemplateParameters);
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        String generatedXml = getValue();
                        insertXmlIntoCurrentEditor(generatedXml);
                        logger.debug("Applied template: {}", selectedTemplate.getName());
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to apply template", getException());
                        showAlert("Template Error", "Failed to apply template: " + getException().getMessage());
                    });
                }
            };

            formattingExecutor.submit(task);
        }
    }

    @FXML
    private void previewSelectedTemplate() {
        if (selectedTemplate != null && templatePreviewArea != null) {
            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return selectedTemplate.processTemplate(currentTemplateParameters);
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        templatePreviewArea.setText(getValue());
                        if (developmentTabPane != null && templateDevelopmentTab != null) {
                            developmentTabPane.getSelectionModel().select(templateDevelopmentTab);
                        }
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to preview template", getException());
                    });
                }
            };

            formattingExecutor.submit(task);
        }
    }

    @FXML
    private void generateSchema() {
        XmlEditor currentEditor = getCurrentXmlEditor();
        if (currentEditor != null && schemaPreviewArea != null) {
            Task<SchemaGenerationResult> task = new Task<>() {
                @Override
                protected SchemaGenerationResult call() throws Exception {
                    String xmlContent = currentEditor.getXmlCodeEditor().getCodeArea().getText();
                    SchemaGenerationOptions options = new SchemaGenerationOptions();
                    if (inferTypesCheckbox != null && inferTypesCheckbox.isSelected()) {
                        options.setInferComplexTypes(true);
                        options.setGenerateComplexTypes(true);
                    }
                    if (flattenSchemaCheckbox != null && flattenSchemaCheckbox.isSelected()) {
                        options.setOptimizeSchema(true);
                    }
                    return schemaEngine.generateSchema(xmlContent, options);
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        SchemaGenerationResult result = getValue();
                        schemaPreviewArea.setText(result.getXsdContent());
                        logger.debug("Generated schema with {} complex types and {} simple types",
                                result.getTotalComplexTypesGenerated(), result.getTotalSimpleTypesGenerated());
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to generate schema", getException());
                        showAlert("Schema Error", "Failed to generate schema: " + getException().getMessage());
                    });
                }
            };

            formattingExecutor.submit(task);
        }
    }

    @FXML
    private void exportSchema() {
        if (schemaPreviewArea != null && !schemaPreviewArea.getText().isEmpty()) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Schema");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

            File file = fileChooser.showSaveDialog(schemaPreviewArea.getScene().getWindow());
            if (file != null) {
                try {
                    Files.write(file.toPath(), schemaPreviewArea.getText().getBytes(StandardCharsets.UTF_8));
                    logger.debug("Schema exported to: {}", file.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("Failed to export schema", e);
                    showAlert("Export Error", "Failed to export schema: " + e.getMessage());
                }
            }
        }
    }

    @FXML
    private void executeTransformation() {
        XmlEditor currentEditor = getCurrentXmlEditor();
        if (currentEditor != null && xsltEditorArea != null && transformationResultArea != null) {
            Task<XsltTransformationResult> task = new Task<>() {
                @Override
                protected XsltTransformationResult call() throws Exception {
                    String xmlContent = currentEditor.getXmlCodeEditor().getCodeArea().getText();
                    String xsltContent = xsltEditorArea.getText();
                    String outputFormat = outputFormatCombo != null ? outputFormatCombo.getValue() : "XML";

                    return xsltEngine.transform(xmlContent, xsltContent, new HashMap<>(),
                            XsltTransformationEngine.OutputFormat.valueOf(outputFormat));
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        XsltTransformationResult result = getValue();
                        transformationResultArea.setText(result.getOutputContent());

                        if (performanceResultArea != null) {
                            performanceResultArea.setText(String.format(
                                    "Transformation completed in %d ms\nOutput size: %d characters\nFormat: %s",
                                    result.getTransformationTime(), result.getOutputSize(), result.getOutputFormat()));
                        }

                        if (transformationPreviewWeb != null && "HTML".equals(outputFormatCombo.getValue())) {
                            transformationPreviewWeb.getEngine().loadContent(result.getOutputContent());
                        }

                        logger.debug("XSLT transformation completed in {}ms", result.getTransformationTime());
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("XSLT transformation failed", getException());
                        showAlert("Transformation Error", "XSLT transformation failed: " + getException().getMessage());
                    });
                }
            };

            formattingExecutor.submit(task);
        }
    }

    // Helper methods
    private void updateTemplateParametersTable() {
        if (selectedTemplate != null && templateParametersTable != null) {
            templateParametersTable.setItems(FXCollections.observableList(selectedTemplate.getParameters()));
        }
    }

    private void insertXmlIntoCurrentEditor(String xml) {
        XmlEditor currentEditor = getCurrentXmlEditor();
        if (currentEditor != null) {
            currentEditor.getXmlCodeEditor().getCodeArea().insertText(
                    currentEditor.getXmlCodeEditor().getCodeArea().getCaretPosition(), xml);
        }
    }


    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Placeholder methods for additional functionality
    @FXML
    private void loadXsltFile() { /* TODO: Implement XSLT file loading */ }

    @FXML
    private void saveXsltFile() { /* TODO: Implement XSLT file saving */ }

    @FXML
    private void addTemplateParameter() { /* TODO: Implement parameter addition */ }

    @FXML
    private void validateTemplateParameters() { /* TODO: Implement parameter validation */ }

    @FXML
    private void resetTemplateParameters() { /* TODO: Implement parameter reset */ }

    @FXML
    private void generateTemplateXml() { /* TODO: Implement template XML generation */ }

    @FXML
    private void insertGeneratedTemplate() { /* TODO: Implement template insertion */ }
}
