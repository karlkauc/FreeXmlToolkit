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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Advanced XSLT Developer - Revolutionary Feature #2
 * Professional XSLT 3.0 development with live preview and performance profiling
 */
public class XsltDeveloperController {
    private static final Logger logger = LogManager.getLogger(XsltDeveloperController.class);

    // Revolutionary Services
    private final XsltTransformationEngine xsltEngine = XsltTransformationEngine.getInstance();

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("XsltDeveloper-Thread");
        return t;
    });

    // UI Components - Header Controls
    @FXML
    private ComboBox<String> xsltVersionCombo;
    @FXML
    private ToggleButton liveTransformToggle;
    @FXML
    private Button transformBtn;

    // UI Components - Input Section
    @FXML
    private TabPane inputTabPane;
    @FXML
    private StackPane xmlInputEditorPane;
    @FXML
    private StackPane xsltInputEditorPane;

    // XmlCodeEditor instances for enhanced editing
    private XmlCodeEditor xmlInputEditor;
    private XmlCodeEditor xsltInputEditor;
    
    @FXML
    private Button loadXmlBtn;
    @FXML
    private Button validateXmlBtn;
    @FXML
    private Button loadXsltBtn;
    @FXML
    private Button saveXsltBtn;
    @FXML
    private Button validateXsltBtn;

    // UI Components - Parameters
    @FXML
    private TableView<XsltParameter> parametersTable;
    @FXML
    private TableColumn<XsltParameter, String> paramNameColumn;
    @FXML
    private TableColumn<XsltParameter, String> paramValueColumn;
    @FXML
    private TableColumn<XsltParameter, String> paramTypeColumn;
    @FXML
    private Button addParameterBtn;
    @FXML
    private Button removeParameterBtn;

    // UI Components - Output Configuration
    @FXML
    private ComboBox<String> outputFormatCombo;
    @FXML
    private ComboBox<String> encodingCombo;
    @FXML
    private CheckBox indentOutputCheckbox;

    // UI Components - Results
    @FXML
    private TabPane resultsTabPane;
    @FXML
    private TextArea transformationResultArea;
    @FXML
    private WebView previewWebView;
    @FXML
    private Button copyResultBtn;
    @FXML
    private Button saveResultBtn;
    @FXML
    private Button refreshPreviewBtn;
    @FXML
    private Label resultStatsLabel;

    // UI Components - Performance
    @FXML
    private Label executionTimeLabel;
    @FXML
    private Label compilationTimeLabel;
    @FXML
    private Label memoryUsageLabel;
    @FXML
    private Label outputSizeLabel;
    @FXML
    private ListView<String> featuresListView;
    @FXML
    private TextArea performanceReportArea;

    // UI Components - Debug
    @FXML
    private CheckBox enableDebugMode;
    @FXML
    private Button clearDebugBtn;
    @FXML
    private ListView<String> messagesListView;
    @FXML
    private TextArea traceArea;

    // State Management
    private XsltTransformationResult lastResult;
    private final Map<String, String> currentParameters = new HashMap<>();

    @FXML
    private void initialize() {
        logger.info("Initializing Advanced XSLT Developer Controller - Revolutionary Feature #2");

        initializeUI();
        initializeEditors();
        setupEventHandlers();
        setDefaultValues();

        logger.info("XSLT Developer Controller initialized successfully");
    }

    private void initializeUI() {
        // Initialize version combo
        if (xsltVersionCombo != null) {
            xsltVersionCombo.setItems(FXCollections.observableArrayList(
                    "XSLT 3.0", "XSLT 2.0", "XSLT 1.0"
            ));
            xsltVersionCombo.setValue("XSLT 3.0");
        }

        // Initialize output format combo
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(FXCollections.observableArrayList(
                    "XML", "HTML", "Text", "JSON"
            ));
            outputFormatCombo.setValue("XML");
        }

        // Initialize encoding combo
        if (encodingCombo != null) {
            encodingCombo.setItems(FXCollections.observableArrayList(
                    "UTF-8", "UTF-16", "ISO-8859-1"
            ));
            encodingCombo.setValue("UTF-8");
        }

        // Initialize parameters table
        if (parametersTable != null) {
            paramNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            paramValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
            paramValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
            paramTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        }

        // Initialize features list
        if (featuresListView != null) {
            featuresListView.setItems(FXCollections.observableArrayList());
        }

        // Initialize messages list
        if (messagesListView != null) {
            messagesListView.setItems(FXCollections.observableArrayList());
        }
    }

    private void initializeEditors() {
        logger.info("Initializing XmlCodeEditor instances for XSLT Developer");

        // Initialize XML Input Editor
        if (xmlInputEditorPane != null) {
            xmlInputEditor = new XmlCodeEditor();
            xmlInputEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    <!-- Enter or load your XML source document here -->\n</root>");
            xmlInputEditorPane.getChildren().add(xmlInputEditor);
            logger.debug("XML Input Editor initialized");
        }

        // Initialize XSLT Input Editor
        if (xsltInputEditorPane != null) {
            xsltInputEditor = new XmlCodeEditor();
            xsltInputEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n    <!-- Enter or load your XSLT stylesheet here -->\n    <xsl:template match=\"/\">\n        <html>\n            <body>\n                <xsl:apply-templates/>\n            </body>\n        </html>\n    </xsl:template>\n</xsl:stylesheet>");
            xsltInputEditorPane.getChildren().add(xsltInputEditor);
            logger.debug("XSLT Input Editor initialized");
        }

        logger.info("XmlCodeEditor instances initialized successfully");
    }

    private void setupEventHandlers() {
        // Live transform toggle
        if (liveTransformToggle != null) {
            liveTransformToggle.setOnAction(e -> {
                if (liveTransformToggle.isSelected()) {
                    setupLiveTransform();
                } else {
                    disableLiveTransform();
                }
            });
        }

        // Parameters table value changes
        if (parametersTable != null) {
            paramValueColumn.setOnEditCommit(event -> {
                XsltParameter parameter = event.getRowValue();
                String newValue = event.getNewValue();
                parameter.setValue(newValue);
                currentParameters.put(parameter.getName(), newValue);

                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            });
        }

        // Input area changes for live transform
        if (xmlInputEditor != null) {
            xmlInputEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            });
        }

        if (xsltInputEditor != null) {
            xsltInputEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            });
        }
    }

    private void setDefaultValues() {
        if (indentOutputCheckbox != null) indentOutputCheckbox.setSelected(true);

        // Default content is now set in initializeEditors() method

        // Initialize development environment settings
        if (enableDebugMode != null) {
            enableDebugMode.setSelected(false);
        }

        // Set default encoding
        if (encodingCombo != null) {
            encodingCombo.setValue("UTF-8");
        }

        // Add some sample parameters
        if (parametersTable != null && parametersTable.getItems().isEmpty()) {
            parametersTable.getItems().addAll(
                    new XsltParameter("title", "Advanced Book Catalog", "string"),
                    new XsltParameter("showGenre", "true", "boolean"),
                    new XsltParameter("maxPrice", "50.00", "number")
            );

            // Update current parameters map
            currentParameters.put("title", "Advanced Book Catalog");
            currentParameters.put("showGenre", "true");
            currentParameters.put("maxPrice", "50.00");
        }
    }

    private void setupLiveTransform() {
        logger.debug("Live transform enabled");
        if (transformBtn != null) transformBtn.setDisable(true);
        performTransformation();
    }

    private void disableLiveTransform() {
        logger.debug("Live transform disabled");
        if (transformBtn != null) transformBtn.setDisable(false);
    }

    @FXML
    private void executeTransformation() {
        performTransformation();
    }

    private void performTransformation() {
        String xmlContent = xmlInputEditor != null ? xmlInputEditor.getCodeArea().getText().trim() : "";
        String xsltContent = xsltInputEditor != null ? xsltInputEditor.getCodeArea().getText().trim() : "";

        if (xmlContent.isEmpty() || xsltContent.isEmpty()) {
            if (!liveTransformToggle.isSelected()) {
                showAlert("Input Required", "Please provide both XML source and XSLT stylesheet.");
            }
            return;
        }

        Task<XsltTransformationResult> transformTask = new Task<>() {
            @Override
            protected XsltTransformationResult call() throws Exception {
                String outputFormat = outputFormatCombo != null ? outputFormatCombo.getValue() : "XML";
                XsltTransformationEngine.OutputFormat format = XsltTransformationEngine.OutputFormat.valueOf(outputFormat);

                Map<String, Object> params = new HashMap<>(currentParameters);
                return xsltEngine.transform(xmlContent, xsltContent, params, format);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    lastResult = getValue();
                    displayTransformationResults(lastResult);
                    logger.debug("XSLT transformation completed in {}ms", lastResult.getTransformationTime());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("XSLT transformation failed", getException());

                    if (!liveTransformToggle.isSelected()) {
                        showAlert("Transformation Error", "XSLT transformation failed: " + getException().getMessage());
                    } else {
                        // For live transform, just show error in result area
                        if (transformationResultArea != null) {
                            transformationResultArea.setText("Transformation Error: " + getException().getMessage());
                        }
                    }

                    // Add error to messages list
                    if (messagesListView != null) {
                        messagesListView.getItems().add("ERROR: " + getException().getMessage());
                    }
                });
            }
        };

        executorService.submit(transformTask);
    }

    private void displayTransformationResults(XsltTransformationResult result) {
        // Display transformation result
        if (transformationResultArea != null) {
            transformationResultArea.setText(result.getOutputContent());
        }

        // Update preview if HTML output
        if (previewWebView != null && "HTML".equals(outputFormatCombo.getValue())) {
            previewWebView.getEngine().loadContent(result.getOutputContent());
        }

        // Update result statistics
        if (resultStatsLabel != null) {
            resultStatsLabel.setText(String.format(
                    "Transformation completed in %dms | Output size: %d chars | Format: %s",
                    result.getTransformationTime(),
                    result.getOutputSize(),
                    result.getOutputFormat()
            ));
        }

        // Update performance metrics
        updatePerformanceMetrics(result);

        // Update features used (if available)
        updateFeaturesUsed(result);
    }

    private void updatePerformanceMetrics(XsltTransformationResult result) {
        if (executionTimeLabel != null)
            executionTimeLabel.setText(result.getTransformationTime() + "ms");
        if (compilationTimeLabel != null)
            compilationTimeLabel.setText("N/A"); // Saxon doesn't expose this separately
        if (memoryUsageLabel != null)
            memoryUsageLabel.setText("N/A"); // Would need JVM memory monitoring
        if (outputSizeLabel != null)
            outputSizeLabel.setText(formatBytes(result.getOutputSize()));

        // Performance report
        if (performanceReportArea != null) {
            String report = "XSLT Transformation Performance Report\n" +
                    "=====================================\n\n" +
                    "Execution Time: " + result.getTransformationTime() + "ms\n" +
                    "Output Format: " + result.getOutputFormat() + "\n" +
                    "Output Size: " + result.getOutputSize() + " characters\n" +
                    "XSLT Version: " + xsltVersionCombo.getValue() + "\n";

            performanceReportArea.setText(report);
        }
    }

    private void updateFeaturesUsed(XsltTransformationResult result) {
        if (featuresListView != null) {
            // This is a placeholder - in a real implementation, we'd extract
            // XSLT features from the stylesheet analysis
            featuresListView.getItems().clear();
            featuresListView.getItems().addAll(
                    "XSLT Templates",
                    "XPath Expressions",
                    "Output Method: " + result.getOutputFormat()
            );
        }
    }

    @FXML
    private void addParameter() {
        if (parametersTable != null) {
            XsltParameter newParam = new XsltParameter("param" + (parametersTable.getItems().size() + 1), "", "string");
            parametersTable.getItems().add(newParam);
        }
    }

    @FXML
    private void removeParameter() {
        if (parametersTable != null && parametersTable.getSelectionModel().getSelectedItem() != null) {
            XsltParameter selected = parametersTable.getSelectionModel().getSelectedItem();
            parametersTable.getItems().remove(selected);
            currentParameters.remove(selected.getName());
        }
    }

    @FXML
    private void loadXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XML Source Document");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(loadXmlBtn.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (xmlInputEditor != null) {
                    xmlInputEditor.getCodeArea().replaceText(content);
                }
                logger.debug("Loaded XML file: {}", file.getAbsolutePath());

                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            } catch (IOException e) {
                logger.error("Failed to load XML file", e);
                showAlert("Load Error", "Failed to load XML file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void validateXml() {
        if (xmlInputEditor == null || xmlInputEditor.getCodeArea().getText().trim().isEmpty()) {
            showAlert("No Content", "No XML content to validate.");
            return;
        }

        String xmlContent = xmlInputEditor.getCodeArea().getText();

        Task<String> validationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                try {
                    // Basic XML well-formedness validation
                    javax.xml.parsers.DocumentBuilderFactory factory =
                            javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();

                    // Custom error handler to collect validation messages
                    java.util.List<String> errors = new java.util.ArrayList<>();
                    builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                        @Override
                        public void warning(org.xml.sax.SAXParseException e) {
                            errors.add("WARNING: " + e.getMessage());
                        }

                        @Override
                        public void error(org.xml.sax.SAXParseException e) {
                            errors.add("ERROR: " + e.getMessage());
                        }

                        @Override
                        public void fatalError(org.xml.sax.SAXParseException e) {
                            errors.add("FATAL: " + e.getMessage());
                        }
                    });

                    builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlContent)));

                    if (errors.isEmpty()) {
                        return "XML is well-formed and valid.";
                    } else {
                        return "Validation issues found:\n" + String.join("\n", errors);
                    }

                } catch (Exception e) {
                    return "XML validation failed: " + e.getMessage();
                }
            }
        };

        validationTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                String result = validationTask.getValue();
                if (messagesListView != null) {
                    messagesListView.getItems().add("XML Validation: " + result);
                }

                if (result.startsWith("XML is well-formed")) {
                    showInfo("Validation Result", result);
                } else {
                    showAlert("Validation Result", result);
                }
            });
        });

        validationTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                logger.error("XML validation failed", validationTask.getException());
                showAlert("Validation Error", "Failed to validate XML: " + validationTask.getException().getMessage());
            });
        });

        executorService.submit(validationTask);
    }

    @FXML
    private void validateXslt() {
        if (xsltInputEditor == null || xsltInputEditor.getCodeArea().getText().trim().isEmpty()) {
            showAlert("No Content", "No XSLT content to validate.");
            return;
        }

        String xsltContent = xsltInputEditor.getCodeArea().getText();

        Task<String> validationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                try {
                    // Use Saxon to validate XSLT stylesheet
                    net.sf.saxon.s9api.Processor processor = xsltEngine.getSaxonProcessor();
                    net.sf.saxon.s9api.XsltCompiler compiler = processor.newXsltCompiler();

                    // Compile the stylesheet to check for syntax errors
                    javax.xml.transform.stream.StreamSource source =
                            new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xsltContent));
                    net.sf.saxon.s9api.XsltExecutable executable = compiler.compile(source);

                    return "XSLT stylesheet is valid and compiles successfully.";

                } catch (net.sf.saxon.s9api.SaxonApiException e) {
                    return "XSLT validation failed: " + e.getMessage();
                } catch (Exception e) {
                    return "XSLT validation error: " + e.getMessage();
                }
            }
        };

        validationTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                String result = validationTask.getValue();
                if (messagesListView != null) {
                    messagesListView.getItems().add("XSLT Validation: " + result);
                }

                if (result.contains("valid and compiles")) {
                    showInfo("Validation Result", result);
                } else {
                    showAlert("Validation Result", result);
                }
            });
        });

        validationTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                logger.error("XSLT validation failed", validationTask.getException());
                showAlert("Validation Error", "Failed to validate XSLT: " + validationTask.getException().getMessage());
            });
        });

        executorService.submit(validationTask);
    }

    @FXML
    private void loadXsltFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XSLT Stylesheet");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(loadXsltBtn.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (xsltInputEditor != null) {
                    xsltInputEditor.getCodeArea().replaceText(content);
                }
                logger.debug("Loaded XSLT file: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to load XSLT file", e);
                showAlert("Load Error", "Failed to load XSLT file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void saveXsltFile() {
        if (xsltInputEditor == null || xsltInputEditor.getCodeArea().getText().isEmpty()) {
            showAlert("No Content", "No XSLT content to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save XSLT Stylesheet");
        fileChooser.setInitialFileName("stylesheet.xsl");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(saveXsltBtn.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), xsltInputEditor.getCodeArea().getText().getBytes(StandardCharsets.UTF_8));
                showInfo("Save Successful", "XSLT saved to: " + file.getAbsolutePath());
                logger.info("XSLT saved to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save XSLT file", e);
                showAlert("Save Error", "Failed to save XSLT file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void copyResult() {
        if (transformationResultArea != null && !transformationResultArea.getText().isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(transformationResultArea.getText()), null);
            showInfo("Copied", "Transformation result copied to clipboard.");
        }
    }

    @FXML
    private void saveResult() {
        if (transformationResultArea == null || transformationResultArea.getText().isEmpty()) {
            showAlert("No Result", "No transformation result to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transformation Result");

        String extension = getOutputExtension();
        fileChooser.setInitialFileName("result." + extension);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(extension.toUpperCase() + " Files", "*." + extension)
        );

        File file = fileChooser.showSaveDialog(saveResultBtn.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), transformationResultArea.getText().getBytes(StandardCharsets.UTF_8));
                showInfo("Save Successful", "Result saved to: " + file.getAbsolutePath());
                logger.info("Result saved to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save result", e);
                showAlert("Save Error", "Failed to save result: " + e.getMessage());
            }
        }
    }

    @FXML
    private void refreshPreview() {
        if (lastResult != null && previewWebView != null && "HTML".equals(outputFormatCombo.getValue())) {
            previewWebView.getEngine().loadContent(lastResult.getOutputContent());
        }
    }

    @FXML
    private void clearDebug() {
        if (messagesListView != null) {
            messagesListView.getItems().clear();
        }
        if (traceArea != null) {
            traceArea.clear();
        }
    }

    // Utility Methods
    private String getOutputExtension() {
        String format = outputFormatCombo != null ? outputFormatCombo.getValue() : "XML";
        return switch (format.toLowerCase()) {
            case "html" -> "html";
            case "text" -> "txt";
            case "json" -> "json";
            default -> "xml";
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for XSLT Parameters
    public static class XsltParameter {
        private String name;
        private String value;
        private String type;

        public XsltParameter(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }

        // Getters and setters
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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    // Lifecycle
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("XSLT Developer Controller shutdown completed");
        }
    }

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("XSLT Developer - Help");
        helpDialog.setHeaderText("How to use the Advanced XSLT Developer");
        helpDialog.setContentText("""
                Use this tool to work with your documents.\n\n                Press F1 to show this help.
                """);
        helpDialog.showAndWait();
    }
}
