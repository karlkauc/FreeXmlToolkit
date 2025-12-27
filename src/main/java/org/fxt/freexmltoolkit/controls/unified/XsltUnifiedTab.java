package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.LinkedFileDetector;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-featured Unified Editor tab for XSLT Stylesheet files.
 * <p>
 * Features:
 * <ul>
 *   <li>XSLT editor with syntax highlighting</li>
 *   <li>XML input panel for test transformation</li>
 *   <li>Live transform toggle</li>
 *   <li>Output preview (text and HTML)</li>
 *   <li>XSLT 3.0 support via Saxon</li>
 * </ul>
 *
 * @since 2.0
 */
public class XsltUnifiedTab extends AbstractUnifiedEditorTab {

    private static final Logger logger = LogManager.getLogger(XsltUnifiedTab.class);

    // UI Components
    private final SplitPane mainSplitPane;
    private final XmlCodeEditorV2 xsltEditor;
    private final XmlCodeEditorV2 xmlInputEditor;
    private final TabPane outputTabPane;
    private final CodeArea textOutputArea;
    private final WebView htmlPreview;
    private final ToggleButton liveTransformToggle;
    private final Button runTransformButton;
    private final Label statusLabel;

    // Services
    private final XmlService xmlService;
    private final XsltTransformationEngine xsltEngine;

    // Background execution
    private final ExecutorService executorService;

    // State
    private String lastSavedContent;
    private LinkedFileDetector linkDetector;
    private File xmlInputFile;

    /**
     * Creates a new XSLT Unified Editor tab.
     *
     * @param sourceFile the file to edit (can be null for new files)
     */
    public XsltUnifiedTab(File sourceFile) {
        super(sourceFile, UnifiedEditorFileType.XSLT);

        // Initialize services
        this.xmlService = new XmlServiceImpl();
        this.xsltEngine = XsltTransformationEngine.getInstance();

        // Background executor
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("XsltUnifiedTab-Transform-Thread");
            t.setDaemon(true);
            return t;
        });

        // Create XSLT editor
        this.xsltEditor = XmlCodeEditorV2Factory.createWithoutSchema();
        xsltEditor.setDocumentUri(sourceFile != null ? sourceFile.toURI().toString() : "untitled:" + System.nanoTime() + ".xslt");

        // Create XML input editor
        this.xmlInputEditor = XmlCodeEditorV2Factory.createWithoutSchema();
        xmlInputEditor.setDocumentUri("xml-input:" + System.nanoTime() + ".xml");

        // Create output components
        this.textOutputArea = new CodeArea();
        textOutputArea.setEditable(false);
        textOutputArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 13px;");
        this.htmlPreview = new WebView();

        // Create output tab pane
        this.outputTabPane = new TabPane();

        // Create toolbar components
        this.liveTransformToggle = new ToggleButton("Live Transform");
        this.runTransformButton = new Button("Run");
        this.statusLabel = new Label("Ready");

        // Create main split pane
        this.mainSplitPane = new SplitPane();

        initializeContent();

        // Load file content if provided
        if (sourceFile != null && sourceFile.exists()) {
            loadFile();
        } else {
            // New file with XSLT template
            String template = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xsl:stylesheet version="3.0"
                                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

                        <xsl:output method="xml" indent="yes"/>

                        <!-- Identity transform -->
                        <xsl:template match="@* | node()">
                            <xsl:copy>
                                <xsl:apply-templates select="@* | node()"/>
                            </xsl:copy>
                        </xsl:template>

                    </xsl:stylesheet>
                    """;
            xsltEditor.setText(template);
            lastSavedContent = template;

            // Default XML input
            xmlInputEditor.setText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    <item>Example</item>\n</root>");
        }
    }

    @Override
    protected void initializeContent() {
        // Create toolbar
        ToolBar toolbar = createToolbar();

        // Create left panel (XSLT + XML input stacked)
        SplitPane inputSplitPane = new SplitPane();
        inputSplitPane.setOrientation(Orientation.VERTICAL);
        inputSplitPane.setDividerPositions(0.65);

        // XSLT section
        VBox xsltSection = new VBox(5);
        Label xsltLabel = new Label("XSLT Stylesheet");
        xsltLabel.setStyle("-fx-font-weight: bold;");
        xsltSection.getChildren().addAll(xsltLabel, xsltEditor);
        VBox.setVgrow(xsltEditor, Priority.ALWAYS);

        // XML input section
        VBox xmlSection = new VBox(5);
        ToolBar xmlToolbar = createXmlInputToolbar();
        xmlSection.getChildren().addAll(xmlToolbar, xmlInputEditor);
        VBox.setVgrow(xmlInputEditor, Priority.ALWAYS);

        inputSplitPane.getItems().addAll(xsltSection, xmlSection);

        // Create output section
        Tab textTab = new Tab("Text Output");
        textTab.setClosable(false);
        FontIcon textIcon = new FontIcon("bi-file-text");
        textIcon.setIconSize(14);
        textTab.setGraphic(textIcon);
        textTab.setContent(new VirtualizedScrollPane<>(textOutputArea));

        Tab htmlTab = new Tab("HTML Preview");
        htmlTab.setClosable(false);
        FontIcon htmlIcon = new FontIcon("bi-globe");
        htmlIcon.setIconSize(14);
        htmlTab.setGraphic(htmlIcon);
        htmlTab.setContent(htmlPreview);

        outputTabPane.getTabs().addAll(textTab, htmlTab);
        outputTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox outputSection = new VBox(5);
        Label outputLabel = new Label("Output");
        outputLabel.setStyle("-fx-font-weight: bold;");
        outputSection.getChildren().addAll(outputLabel, outputTabPane);
        VBox.setVgrow(outputTabPane, Priority.ALWAYS);

        // Main horizontal split pane
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.55);
        mainSplitPane.getItems().addAll(inputSplitPane, outputSection);

        // Main container
        VBox container = new VBox();
        container.getChildren().addAll(toolbar, mainSplitPane);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);

        setContent(container);

        // Setup change listeners
        CodeArea xsltCodeArea = xsltEditor.getCodeArea();
        xsltCodeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (lastSavedContent != null && !lastSavedContent.equals(newText)) {
                setDirty(true);
            }
            if (liveTransformToggle.isSelected()) {
                runTransformAsync();
            }
        });

        xmlInputEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
            if (liveTransformToggle.isSelected()) {
                runTransformAsync();
            }
        });
    }

    /**
     * Creates the main toolbar.
     */
    private ToolBar createToolbar() {
        // Live transform toggle
        FontIcon liveIcon = new FontIcon("bi-lightning-charge");
        liveIcon.setIconSize(14);
        liveTransformToggle.setGraphic(liveIcon);
        liveTransformToggle.setTooltip(new Tooltip("Enable live transformation on edit"));

        // Run button
        FontIcon runIcon = new FontIcon("bi-play-fill");
        runIcon.setIconSize(14);
        runTransformButton.setGraphic(runIcon);
        runTransformButton.setTooltip(new Tooltip("Run transformation (Ctrl+Enter)"));
        runTransformButton.setOnAction(e -> runTransformAsync());

        // Clear output button
        Button clearButton = new Button();
        FontIcon clearIcon = new FontIcon("bi-trash");
        clearIcon.setIconSize(14);
        clearButton.setGraphic(clearIcon);
        clearButton.setTooltip(new Tooltip("Clear output"));
        clearButton.setOnAction(e -> clearOutput());

        // Status label
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        ToolBar toolbar = new ToolBar(
                liveTransformToggle,
                runTransformButton,
                new Separator(),
                clearButton,
                new Separator(),
                statusLabel
        );

        return toolbar;
    }

    /**
     * Creates the XML input toolbar.
     */
    private ToolBar createXmlInputToolbar() {
        Label xmlLabel = new Label("XML Input");
        xmlLabel.setStyle("-fx-font-weight: bold;");

        Button loadXmlButton = new Button();
        FontIcon loadIcon = new FontIcon("bi-folder2-open");
        loadIcon.setIconSize(14);
        loadXmlButton.setGraphic(loadIcon);
        loadXmlButton.setTooltip(new Tooltip("Load XML file"));
        loadXmlButton.setOnAction(e -> loadXmlInput());

        Button clearXmlButton = new Button();
        FontIcon clearIcon = new FontIcon("bi-x-circle");
        clearIcon.setIconSize(14);
        clearXmlButton.setGraphic(clearIcon);
        clearXmlButton.setTooltip(new Tooltip("Clear XML input"));
        clearXmlButton.setOnAction(e -> xmlInputEditor.setText(""));

        return new ToolBar(xmlLabel, loadXmlButton, clearXmlButton);
    }

    /**
     * Loads an XML file for input.
     */
    private void loadXmlInput() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XML Input");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );

        if (sourceFile != null && sourceFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(sourceFile.getParentFile());
        }

        File file = fileChooser.showOpenDialog(mainSplitPane.getScene().getWindow());
        if (file != null && file.exists()) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                xmlInputEditor.setText(content);
                xmlInputFile = file;
                setStatus("Loaded XML: " + file.getName(), false);
            } catch (IOException e) {
                logger.error("Failed to load XML file: {}", file, e);
                setStatus("Failed to load XML", true);
            }
        }
    }

    /**
     * Runs the transformation asynchronously.
     */
    private void runTransformAsync() {
        String xsltContent = xsltEditor.getText();
        String xmlContent = xmlInputEditor.getText();

        if (xsltContent == null || xsltContent.trim().isEmpty()) {
            setStatus("No XSLT content", true);
            return;
        }

        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            setStatus("No XML input", true);
            return;
        }

        setStatus("Transforming...", false);

        executorService.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                var transformResult = xsltEngine.transform(
                        xmlContent,
                        xsltContent,
                        java.util.Collections.emptyMap(),
                        XsltTransformationEngine.OutputFormat.XML
                );

                long duration = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    if (transformResult.isSuccess()) {
                        String result = transformResult.getOutputContent();
                        textOutputArea.replaceText(result);

                        // Update HTML preview if output looks like HTML
                        if (result.contains("<html") || result.contains("<!DOCTYPE html")) {
                            htmlPreview.getEngine().loadContent(result);
                        } else {
                            // Wrap XML in simple HTML for viewing
                            htmlPreview.getEngine().loadContent("<pre>" + escapeHtml(result) + "</pre>");
                        }

                        setStatus("Transformed in " + duration + "ms", false);
                    } else {
                        String errorMsg = transformResult.getErrorMessage();
                        textOutputArea.replaceText("Error: " + errorMsg);
                        htmlPreview.getEngine().loadContent("<pre style='color:red;'>Error: " + escapeHtml(errorMsg) + "</pre>");
                        setStatus("Transform failed", true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    textOutputArea.replaceText("Error: " + e.getMessage());
                    htmlPreview.getEngine().loadContent("<pre style='color:red;'>Error: " + escapeHtml(e.getMessage()) + "</pre>");
                    setStatus("Transform failed", true);
                });
                logger.error("XSLT transformation failed", e);
            }
        });
    }

    /**
     * Clears the output areas.
     */
    private void clearOutput() {
        textOutputArea.clear();
        htmlPreview.getEngine().loadContent("");
        setStatus("Output cleared", false);
    }

    /**
     * Sets the status label.
     */
    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #dc3545;"
                : "-fx-text-fill: #6c757d;");
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Loads the content from the source file.
     */
    private void loadFile() {
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }

        try {
            String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
            xsltEditor.setText(content);
            lastSavedContent = content;
            setDirty(false);
            logger.info("Loaded XSLT file: {}", sourceFile.getName());
        } catch (IOException e) {
            logger.error("Failed to load XSLT file: {}", sourceFile, e);
        }
    }

    // ==================== File Operations ====================

    @Override
    public String getEditorContent() {
        return xsltEditor.getText();
    }

    @Override
    public void setEditorContent(String content) {
        xsltEditor.setText(content);
    }

    @Override
    public boolean save() {
        if (sourceFile == null) {
            logger.warn("Cannot save: no source file specified");
            return false;
        }

        try {
            String content = getEditorContent();
            Files.writeString(sourceFile.toPath(), content, StandardCharsets.UTF_8);
            lastSavedContent = content;
            setDirty(false);
            logger.info("Saved XSLT file: {}", sourceFile.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save XSLT file: {}", sourceFile, e);
            return false;
        }
    }

    @Override
    public boolean saveAs(File file) {
        if (file == null) {
            return false;
        }

        try {
            String content = getEditorContent();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            lastSavedContent = content;
            setDirty(false);
            updateTitle(file.getName());
            logger.info("Saved XSLT file as: {}", file.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save XSLT file as: {}", file, e);
            return false;
        }
    }

    @Override
    public void reload() {
        loadFile();
    }

    @Override
    public void discardChanges() {
        if (lastSavedContent != null) {
            xsltEditor.setText(lastSavedContent);
            setDirty(false);
        }
    }

    @Override
    public String validate() {
        String content = getEditorContent();
        if (content == null || content.trim().isEmpty()) {
            return "Empty document";
        }

        try {
            var errors = xmlService.validateText(content);
            if (errors == null || errors.isEmpty()) {
                return null; // Valid
            } else {
                return "XSLT Error: " + errors.get(0).getMessage();
            }
        } catch (Exception e) {
            return "XSLT Error: " + e.getMessage();
        }
    }

    @Override
    public void format() {
        String content = getEditorContent();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String formatted = XmlService.prettyFormat(content, 4);
            if (formatted != null && !formatted.equals(content)) {
                xsltEditor.setText(formatted);
            }
        } catch (Exception e) {
            logger.warn("Failed to format XSLT: {}", e.getMessage());
        }
    }

    @Override
    public List<LinkedFileInfo> detectLinkedFiles() {
        if (sourceFile == null) {
            return Collections.emptyList();
        }

        if (linkDetector == null) {
            linkDetector = new LinkedFileDetector();
        }

        return linkDetector.detectXsltLinks(sourceFile);
    }

    @Override
    public String getCaretPosition() {
        CodeArea codeArea = xsltEditor.getCodeArea();
        int line = codeArea.getCurrentParagraph() + 1;
        int col = codeArea.getCaretColumn() + 1;
        return String.format("Ln %d, Col %d", line, col);
    }

    @Override
    public void requestEditorFocus() {
        Platform.runLater(() -> xsltEditor.getCodeArea().requestFocus());
    }

    // ==================== Accessors ====================

    /**
     * Gets the underlying XSLT editor.
     */
    public XmlCodeEditorV2 getXsltEditor() {
        return xsltEditor;
    }

    /**
     * Gets the XML input editor.
     */
    public XmlCodeEditorV2 getXmlInputEditor() {
        return xmlInputEditor;
    }

    /**
     * Gets the code area for direct access.
     */
    public CodeArea getCodeArea() {
        return xsltEditor.getCodeArea();
    }

    /**
     * Gets the text output area.
     */
    public CodeArea getTextOutputArea() {
        return textOutputArea;
    }

    /**
     * Deprecated method for backward compatibility.
     * @deprecated Use getXsltEditor() instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public XmlCodeEditorV2 getEditor() {
        return xsltEditor;
    }

    /**
     * Disposes resources when the tab is closed.
     */
    public void dispose() {
        try {
            executorService.shutdownNow();
        } catch (Exception e) {
            logger.warn("Error disposing XSLT tab: {}", e.getMessage());
        }
    }

    @Override
    public CodeArea getPrimaryCodeArea() {
        return xsltEditor.getCodeArea();
    }
}
