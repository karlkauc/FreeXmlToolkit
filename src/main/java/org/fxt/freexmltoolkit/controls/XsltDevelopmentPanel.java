package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.service.TransformationProfile;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Interactive XSLT Development Panel - Revolutionary XSLT development environment.
 * Features:
 * - Live XSLT transformation with real-time preview
 * - XSLT 3.0 support via Saxon
 * - Interactive debugging and profiling
 * - Multiple output format support
 * - Template performance analysis
 * - Parameter management
 */
public class XsltDevelopmentPanel extends VBox {

    private final XsltTransformationEngine transformationEngine;
    private final ExecutorService executorService;

    // UI Components - Input
    private TextArea xmlInputArea;
    private TextArea xsltInputArea;
    private VBox parametersPanel;
    private ComboBox<XsltTransformationEngine.OutputFormat> outputFormatCombo;
    private CheckBox enableDebuggingCheckBox;
    private CheckBox enableProfilingCheckBox;
    private CheckBox liveTransformCheckBox;

    // UI Components - Output and Analysis
    private TabPane outputTabPane;
    private TextArea transformationResultArea;
    private WebView htmlPreviewView;
    private TextArea debugInfoArea;
    private TextArea performanceAnalysisArea;
    private TextArea eventLogArea;

    // UI Components - Controls
    private Button transformButton;
    private Button clearButton;
    private Button addParameterButton;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;

    // State
    private final Map<String, TextField> parameterFields;
    private XsltTransformationResult lastResult;
    private Consumer<XsltTransformationResult> resultCallback;
    private boolean isTransforming;

    public XsltDevelopmentPanel() {
        this.transformationEngine = XsltTransformationEngine.getInstance();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "XSLTDevelopment");
            t.setDaemon(true);
            return t;
        });
        this.parameterFields = new HashMap<>();

        initializeUI();
        setupEventHandlers();
        loadDefaultContent();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(10));
        setPrefWidth(800);
        setPrefHeight(600);

        // Header
        Label headerLabel = new Label("XSLT 3.0 Development Environment");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Main content - split between input and output
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.5);

        // Left side - Input
        VBox inputSection = createInputSection();

        // Right side - Output
        VBox outputSection = createOutputSection();

        mainSplitPane.getItems().addAll(inputSection, outputSection);

        // Status bar
        HBox statusBar = createStatusBar();

        getChildren().addAll(headerLabel, new Separator(), mainSplitPane, statusBar);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
    }

    private VBox createInputSection() {
        VBox inputSection = new VBox(5);
        inputSection.setPadding(new Insets(5));
        inputSection.setPrefWidth(400);

        // Input controls
        HBox inputControls = createInputControls();

        // XML Input
        Label xmlLabel = new Label("XML Input:");
        xmlLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        xmlInputArea = new TextArea();
        xmlInputArea.setPrefRowCount(8);
        xmlInputArea.setWrapText(false);
        xmlInputArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 11px;");

        // XSLT Input
        Label xsltLabel = new Label("XSLT Stylesheet:");
        xsltLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        xsltInputArea = new TextArea();
        xsltInputArea.setPrefRowCount(12);
        xsltInputArea.setWrapText(false);
        xsltInputArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 11px;");

        // Parameters section
        VBox parametersSection = createParametersSection();

        inputSection.getChildren().addAll(
                inputControls,
                xmlLabel, xmlInputArea,
                xsltLabel, xsltInputArea,
                parametersSection
        );

        VBox.setVgrow(xmlInputArea, Priority.SOMETIMES);
        VBox.setVgrow(xsltInputArea, Priority.ALWAYS);

        return inputSection;
    }

    private HBox createInputControls() {
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(5));

        // Transform button
        transformButton = new Button("Transform");
        transformButton.setDefaultButton(true);
        transformButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");

        // Clear button
        clearButton = new Button("Clear");

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        // Output format selection
        Label formatLabel = new Label("Format:");
        outputFormatCombo = new ComboBox<>();
        outputFormatCombo.getItems().addAll(XsltTransformationEngine.OutputFormat.values());
        outputFormatCombo.setValue(XsltTransformationEngine.OutputFormat.HTML);
        outputFormatCombo.setPrefWidth(100);

        // Options
        enableDebuggingCheckBox = new CheckBox("Debug");
        enableDebuggingCheckBox.setTooltip(new Tooltip("Enable detailed debugging information"));

        enableProfilingCheckBox = new CheckBox("Profile");
        enableProfilingCheckBox.setSelected(true);
        enableProfilingCheckBox.setTooltip(new Tooltip("Enable performance profiling"));

        liveTransformCheckBox = new CheckBox("Live");
        liveTransformCheckBox.setTooltip(new Tooltip("Transform automatically as you type"));

        controls.getChildren().addAll(
                transformButton, clearButton, progressIndicator,
                new Separator(Orientation.VERTICAL),
                formatLabel, outputFormatCombo,
                new Separator(Orientation.VERTICAL),
                enableDebuggingCheckBox, enableProfilingCheckBox, liveTransformCheckBox
        );

        return controls;
    }

    private VBox createParametersSection() {
        VBox section = new VBox(3);

        HBox headerBox = new HBox(5);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label paramLabel = new Label("XSLT Parameters:");
        paramLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));

        addParameterButton = new Button("+");
        addParameterButton.setTooltip(new Tooltip("Add parameter"));
        addParameterButton.setPrefSize(25, 25);

        headerBox.getChildren().addAll(paramLabel, addParameterButton);

        parametersPanel = new VBox(3);
        parametersPanel.setPrefHeight(60);

        ScrollPane parametersScroll = new ScrollPane(parametersPanel);
        parametersScroll.setFitToWidth(true);
        parametersScroll.setPrefHeight(60);
        parametersScroll.setStyle("-fx-background-color: transparent;");

        section.getChildren().addAll(headerBox, parametersScroll);

        return section;
    }

    private VBox createOutputSection() {
        VBox outputSection = new VBox(5);
        outputSection.setPadding(new Insets(5));
        outputSection.setPrefWidth(400);

        Label outputLabel = new Label("Transformation Results:");
        outputLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        // Output tabs
        outputTabPane = new TabPane();
        outputTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Result tab
        Tab resultTab = new Tab("Result");
        transformationResultArea = new TextArea();
        transformationResultArea.setEditable(false);
        transformationResultArea.setWrapText(false);
        transformationResultArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 11px;");
        resultTab.setContent(new ScrollPane(transformationResultArea));

        // HTML Preview tab
        Tab previewTab = new Tab("Preview");
        htmlPreviewView = new WebView();
        htmlPreviewView.setPrefHeight(300);
        previewTab.setContent(htmlPreviewView);

        // Debug Info tab
        Tab debugTab = new Tab("Debug");
        debugInfoArea = new TextArea();
        debugInfoArea.setEditable(false);
        debugInfoArea.setWrapText(true);
        debugInfoArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 10px;");
        debugTab.setContent(new ScrollPane(debugInfoArea));

        // Performance tab
        Tab performanceTab = new Tab("Performance");
        performanceAnalysisArea = new TextArea();
        performanceAnalysisArea.setEditable(false);
        performanceAnalysisArea.setWrapText(true);
        performanceAnalysisArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 10px;");
        performanceTab.setContent(new ScrollPane(performanceAnalysisArea));

        // Event Log tab
        Tab eventLogTab = new Tab("Events");
        eventLogArea = new TextArea();
        eventLogArea.setEditable(false);
        eventLogArea.setWrapText(true);
        eventLogArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 10px;");
        eventLogTab.setContent(new ScrollPane(eventLogArea));

        outputTabPane.getTabs().addAll(resultTab, previewTab, debugTab, performanceTab, eventLogTab);

        outputSection.getChildren().addAll(outputLabel, outputTabPane);
        VBox.setVgrow(outputTabPane, Priority.ALWAYS);

        return outputSection;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-border-color: lightgray; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("Ready");
        statusLabel.setFont(Font.font("System", 10));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label("XSLT 3.0 via Saxon");
        versionLabel.setFont(Font.font("System", 9));
        versionLabel.setStyle("-fx-text-fill: gray;");

        statusBar.getChildren().addAll(statusLabel, spacer, versionLabel);
        return statusBar;
    }

    private void setupEventHandlers() {
        // Transform button
        transformButton.setOnAction(e -> performTransformation());

        // Clear button
        clearButton.setOnAction(e -> clearAll());

        // Add parameter button
        addParameterButton.setOnAction(e -> addParameterField());

        // Live transformation
        liveTransformCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                setupLiveTransformation();
            } else {
                tearDownLiveTransformation();
            }
        });

        // Format change
        outputFormatCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (liveTransformCheckBox.isSelected()) {
                performTransformation();
            }
        });
    }

    private void performTransformation() {
        if (isTransforming) {
            return;
        }

        String xmlContent = xmlInputArea.getText();
        String xsltContent = xsltInputArea.getText();

        if (xmlContent.trim().isEmpty() || xsltContent.trim().isEmpty()) {
            showStatus("Please provide both XML and XSLT content", true);
            return;
        }

        // Collect parameters
        Map<String, Object> parameters = collectParameters();
        XsltTransformationEngine.OutputFormat format = outputFormatCombo.getValue();
        boolean enableDebugging = enableDebuggingCheckBox.isSelected();

        // Update UI
        isTransforming = true;
        transformButton.setDisable(true);
        progressIndicator.setVisible(true);
        showStatus("Transforming...", false);

        // Create transformation task
        Task<XsltTransformationResult> transformationTask = new Task<XsltTransformationResult>() {
            @Override
            protected XsltTransformationResult call() throws Exception {
                if (enableDebugging) {
                    return transformationEngine.liveTransform(xmlContent, xsltContent, parameters, format, true);
                } else {
                    return transformationEngine.transform(xmlContent, xsltContent, parameters, format);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    XsltTransformationResult result = getValue();
                    displayTransformationResult(result);
                    isTransforming = false;
                    transformButton.setDisable(false);
                    progressIndicator.setVisible(false);

                    if (resultCallback != null) {
                        resultCallback.accept(result);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    XsltTransformationResult errorResult = XsltTransformationResult.error(
                            "Transformation failed: " + exception.getMessage());
                    displayTransformationResult(errorResult);
                    isTransforming = false;
                    transformButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });
            }
        };

        executorService.submit(transformationTask);
    }

    private void displayTransformationResult(XsltTransformationResult result) {
        lastResult = result;

        // Update result area
        if (result.isSuccess()) {
            transformationResultArea.setText(result.getFormattedOutput());
            showStatus(result.getResultSummary(), false);

            // Update preview if HTML
            if (result.isPreviewable()) {
                WebEngine webEngine = htmlPreviewView.getEngine();
                webEngine.loadContent(result.getPreviewHtml());
            }

        } else {
            transformationResultArea.setText("Transformation failed:\n" + result.getErrorMessage());
            showStatus("Error: " + result.getErrorMessage(), true);

            // Clear preview
            htmlPreviewView.getEngine().loadContent("<p>No preview available</p>");
        }

        // Update debug info
        if (enableDebuggingCheckBox.isSelected()) {
            debugInfoArea.setText(result.getDebuggingInfo());
        } else {
            debugInfoArea.setText("Enable debugging to see detailed information");
        }

        // Update performance analysis
        if (enableProfilingCheckBox.isSelected() && result.getProfile() != null) {
            TransformationProfile profile = result.getProfile();
            performanceAnalysisArea.setText(profile.getPerformanceSummary());
            eventLogArea.setText(profile.getDetailedEventLog());

            // Show performance warnings if any
            var warnings = profile.getPerformanceWarnings();
            if (!warnings.isEmpty()) {
                StringBuilder warningText = new StringBuilder("Performance Warnings:\n\n");
                for (String warning : warnings) {
                    warningText.append("• ").append(warning).append("\n");
                }
                warningText.append("\n").append(profile.getPerformanceSummary());
                performanceAnalysisArea.setText(warningText.toString());
            }
        } else {
            performanceAnalysisArea.setText("Enable profiling to see performance analysis");
            eventLogArea.setText("Enable profiling to see event log");
        }

        // Auto-switch to relevant tab based on result
        if (!result.isSuccess()) {
            outputTabPane.getSelectionModel().select(0); // Result tab to show error
        } else if (result.isPreviewable()) {
            outputTabPane.getSelectionModel().select(1); // Preview tab
        }
    }

    private Map<String, Object> collectParameters() {
        Map<String, Object> parameters = new HashMap<>();

        for (Map.Entry<String, TextField> entry : parameterFields.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue().getText();
            if (paramValue != null && !paramValue.trim().isEmpty()) {
                parameters.put(paramName, paramValue);
            }
        }

        return parameters;
    }

    private void addParameterField() {
        String paramName = "param" + (parameterFields.size() + 1);
        addParameterField(paramName, "");
    }

    private void addParameterField(String name, String value) {
        HBox paramBox = new HBox(5);
        paramBox.setAlignment(Pos.CENTER_LEFT);

        TextField nameField = new TextField(name);
        nameField.setPrefWidth(80);
        nameField.setPromptText("Name");

        TextField valueField = new TextField(value);
        valueField.setPrefWidth(120);
        valueField.setPromptText("Value");

        Button removeButton = new Button("×");
        removeButton.setPrefSize(25, 25);
        removeButton.setOnAction(e -> {
            parametersPanel.getChildren().remove(paramBox);
            parameterFields.remove(nameField.getText());
        });

        paramBox.getChildren().addAll(nameField, new Label("="), valueField, removeButton);
        parametersPanel.getChildren().add(paramBox);

        parameterFields.put(name, valueField);

        // Update parameter map when name changes
        nameField.textProperty().addListener((obs, oldName, newName) -> {
            if (!oldName.equals(newName)) {
                parameterFields.remove(oldName);
                if (!newName.trim().isEmpty()) {
                    parameterFields.put(newName, valueField);
                }
            }
        });
    }

    private void setupLiveTransformation() {
        // Add change listeners for live transformation
        xmlInputArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (liveTransformCheckBox.isSelected() && !isTransforming) {
                // Debounce - transform after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(1000); // 1 second delay
                        if (liveTransformCheckBox.isSelected() && newValue.equals(xmlInputArea.getText())) {
                            Platform.runLater(this::performTransformation);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            }
        });

        xsltInputArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (liveTransformCheckBox.isSelected() && !isTransforming) {
                // Debounce - transform after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(1000); // 1 second delay
                        if (liveTransformCheckBox.isSelected() && newValue.equals(xsltInputArea.getText())) {
                            Platform.runLater(this::performTransformation);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            }
        });
    }

    private void tearDownLiveTransformation() {
        // Live transformation listeners are automatically removed when checkbox is unchecked
        // due to the condition check in the listeners
    }

    private void clearAll() {
        xmlInputArea.clear();
        xsltInputArea.clear();
        transformationResultArea.clear();
        debugInfoArea.clear();
        performanceAnalysisArea.clear();
        eventLogArea.clear();
        parametersPanel.getChildren().clear();
        parameterFields.clear();

        htmlPreviewView.getEngine().loadContent("");

        showStatus("Ready", false);
    }

    private void loadDefaultContent() {
        // Load example XML
        xmlInputArea.setText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog>
                    <book id="1">
                        <title>XML Processing</title>
                        <author>John Doe</author>
                        <price>29.99</price>
                        <category>Technical</category>
                    </book>
                    <book id="2">
                        <title>XSLT Transformations</title>
                        <author>Jane Smith</author>
                        <price>34.99</price>
                        <category>Technical</category>
                    </book>
                </catalog>
                """);

        // Load example XSLT
        xsltInputArea.setText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                
                    <xsl:output method="html" indent="yes"/>
                
                    <xsl:template match="/">
                        <html>
                            <head>
                                <title>Book Catalog</title>
                                <style>
                                    body { font-family: Arial, sans-serif; }
                                    .book { margin: 20px; padding: 15px; border: 1px solid #ccc; border-radius: 5px; }
                                    .title { font-weight: bold; font-size: 18px; color: #007bff; }
                                    .author { color: #666; }
                                    .price { color: #28a745; font-weight: bold; }
                                </style>
                            </head>
                            <body>
                                <h1>Book Catalog</h1>
                                <xsl:apply-templates select="catalog/book"/>
                            </body>
                        </html>
                    </xsl:template>
                
                    <xsl:template match="book">
                        <div class="book">
                            <div class="title"><xsl:value-of select="title"/></div>
                            <div class="author">by <xsl:value-of select="author"/></div>
                            <div class="price">$<xsl:value-of select="price"/></div>
                            <div class="category">Category: <xsl:value-of select="category"/></div>
                        </div>
                    </xsl:template>
                
                </xsl:stylesheet>
                """);
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: black;");
        });
    }

    // ========== Public API ==========

    /**
     * Set XML content
     */
    public void setXmlContent(String xmlContent) {
        xmlInputArea.setText(xmlContent);
    }

    /**
     * Set XSLT content
     */
    public void setXsltContent(String xsltContent) {
        xsltInputArea.setText(xsltContent);
    }

    /**
     * Get current transformation result
     */
    public XsltTransformationResult getCurrentResult() {
        return lastResult;
    }

    /**
     * Set result callback for external integration
     */
    public void setResultCallback(Consumer<XsltTransformationResult> callback) {
        this.resultCallback = callback;
    }

    /**
     * Enable/disable live transformation
     */
    public void setLiveTransformation(boolean enabled) {
        liveTransformCheckBox.setSelected(enabled);
    }

    /**
     * Add predefined parameter
     */
    public void addParameter(String name, String value) {
        addParameterField(name, value);
    }

    public void shutdown() {
        executorService.shutdown();
    }
}