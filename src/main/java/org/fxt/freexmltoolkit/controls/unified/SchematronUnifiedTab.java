package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.SchematronDocumentationGenerator;
import org.fxt.freexmltoolkit.controls.SchematronTester;
import org.fxt.freexmltoolkit.controls.SchematronVisualBuilder;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.LinkedFileDetector;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Full-featured Unified Editor tab for Schematron files.
 * <p>
 * Features:
 * <ul>
 *   <li>Code view with syntax highlighting (XmlCodeEditorV2)</li>
 *   <li>Visual Builder for creating rules graphically</li>
 *   <li>Test tab for testing rules against XML files</li>
 *   <li>Documentation generator for creating comprehensive docs</li>
 * </ul>
 *
 * @since 2.0
 */
public class SchematronUnifiedTab extends AbstractUnifiedEditorTab {

    private static final Logger logger = LogManager.getLogger(SchematronUnifiedTab.class);

    // UI Components
    private final TabPane innerTabPane;
    private final Tab codeTab;
    private final Tab visualBuilderTab;
    private final Tab testTab;
    private final Tab documentationTab;

    // Code editor
    private final XmlCodeEditorV2 codeEditor;

    // Feature components
    private SchematronVisualBuilder visualBuilder;
    private SchematronTester tester;
    private SchematronDocumentationGenerator documentationGenerator;

    // Code tab sidebar components
    private TitledPane quickHelpPane;
    private TitledPane structurePane;
    private TitledPane templatesPane;
    private TitledPane xpathTesterPane;
    private TextArea xpathResultArea;
    private TextField xpathInputField;
    private TreeView<String> structureTreeView;

    // Services
    private final XmlService xmlService;

    // State
    private String lastSavedContent;
    private LinkedFileDetector linkDetector;
    private boolean syncingViews = false;

    /**
     * Creates a new Schematron Unified Editor tab.
     *
     * @param sourceFile the file to edit (can be null for new files)
     */
    public SchematronUnifiedTab(File sourceFile) {
        super(sourceFile, UnifiedEditorFileType.SCHEMATRON);

        // Initialize services
        this.xmlService = new XmlServiceImpl();

        // Create code editor
        this.codeEditor = XmlCodeEditorV2Factory.createWithoutSchema();
        codeEditor.setDocumentUri(sourceFile != null ? sourceFile.toURI().toString() : "untitled:" + System.nanoTime() + ".sch");

        // Create inner tab pane
        this.innerTabPane = new TabPane();
        this.codeTab = new Tab("Code");
        this.visualBuilderTab = new Tab("Visual Builder");
        this.testTab = new Tab("Test");
        this.documentationTab = new Tab("Documentation");

        initializeContent();

        // Load file content if provided
        if (sourceFile != null && sourceFile.exists()) {
            loadFile();
        } else {
            // New file with Schematron template
            String template = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <sch:schema xmlns:sch="http://purl.oclc.org/dsl/schematron"
                                queryBinding="xslt2">

                        <sch:title>Validation Rules</sch:title>

                        <sch:pattern id="example-pattern">
                            <sch:title>Example Pattern</sch:title>

                            <sch:rule context="/*">
                                <sch:assert test="true()">
                                    This assertion always passes.
                                </sch:assert>
                            </sch:rule>
                        </sch:pattern>

                    </sch:schema>
                    """;
            codeEditor.setText(template);
            lastSavedContent = template;
        }
    }

    @Override
    protected void initializeContent() {
        // Setup inner tab pane
        innerTabPane.setSide(Side.TOP);
        innerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Code tab with sidebar
        FontIcon codeIcon = new FontIcon("bi-code-slash");
        codeIcon.setIconSize(16);
        codeTab.setGraphic(codeIcon);
        codeTab.setContent(createCodeTabContent());

        // Visual Builder tab
        FontIcon visualIcon = new FontIcon("bi-grid-3x3-gap");
        visualIcon.setIconSize(16);
        visualBuilderTab.setGraphic(visualIcon);
        visualBuilder = new SchematronVisualBuilder();
        ScrollPane visualScrollPane = new ScrollPane(visualBuilder);
        visualScrollPane.setFitToWidth(true);
        visualBuilderTab.setContent(visualScrollPane);

        // Test tab
        FontIcon testIcon = new FontIcon("bi-check-circle");
        testIcon.setIconSize(16);
        testTab.setGraphic(testIcon);
        tester = new SchematronTester();
        testTab.setContent(tester);

        // Documentation tab
        FontIcon docsIcon = new FontIcon("bi-file-text");
        docsIcon.setIconSize(16);
        documentationTab.setGraphic(docsIcon);
        documentationGenerator = new SchematronDocumentationGenerator();
        documentationTab.setContent(documentationGenerator);

        innerTabPane.getTabs().addAll(codeTab, visualBuilderTab, testTab, documentationTab);

        // Tab switch listener
        codeTab.setOnSelectionChanged(e -> {
            if (codeTab.isSelected() && !syncingViews) {
                syncFromVisualBuilder();
            }
        });

        visualBuilderTab.setOnSelectionChanged(e -> {
            if (visualBuilderTab.isSelected() && !syncingViews) {
                // Visual builder doesn't need sync on selection
            }
        });

        testTab.setOnSelectionChanged(e -> {
            if (testTab.isSelected() && sourceFile != null) {
                tester.setSchematronFile(sourceFile);
            }
        });

        documentationTab.setOnSelectionChanged(e -> {
            if (documentationTab.isSelected() && sourceFile != null) {
                documentationGenerator.setSchematronFile(sourceFile);
            }
        });

        // Set as main content
        setContent(innerTabPane);

        // Setup change listener for dirty tracking in code editor
        CodeArea codeArea = codeEditor.getCodeArea();
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!syncingViews && lastSavedContent != null && !lastSavedContent.equals(newText)) {
                setDirty(true);
            }
            updateStructureTree();
        });
    }

    /**
     * Creates the code tab content with editor and sidebar.
     */
    private SplitPane createCodeTabContent() {
        // Main split pane: editor + sidebar
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.75);

        // Editor on the left
        VBox editorContainer = new VBox();
        VBox.setVgrow(codeEditor, Priority.ALWAYS);
        editorContainer.getChildren().add(codeEditor);

        // Sidebar on the right
        VBox sidebar = createSidebar();

        splitPane.getItems().addAll(editorContainer, sidebar);

        return splitPane;
    }

    /**
     * Creates the sidebar with quick help, structure, templates, and XPath tester.
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox(5);
        sidebar.setPadding(new Insets(5));
        sidebar.setMinWidth(250);
        sidebar.setPrefWidth(280);

        // Quick Help pane
        quickHelpPane = createQuickHelpPane();

        // Structure pane
        structurePane = createStructurePane();

        // Templates pane
        templatesPane = createTemplatesPane();

        // XPath Tester pane
        xpathTesterPane = createXPathTesterPane();

        // Add accordion for collapsible sections
        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(quickHelpPane, structurePane, templatesPane, xpathTesterPane);
        accordion.setExpandedPane(quickHelpPane);

        VBox.setVgrow(accordion, Priority.ALWAYS);
        sidebar.getChildren().add(accordion);

        return sidebar;
    }

    /**
     * Creates the Quick Help titled pane.
     */
    private TitledPane createQuickHelpPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label title = new Label("Schematron Quick Reference");
        title.setStyle("-fx-font-weight: bold;");

        TextArea helpText = new TextArea();
        helpText.setEditable(false);
        helpText.setWrapText(true);
        helpText.setPrefRowCount(8);
        helpText.setText("""
                Schematron is a rule-based validation language for XML.

                Key Elements:
                • <sch:schema> - Root element
                • <sch:pattern> - Group of related rules
                • <sch:rule context="..."> - Rule with XPath context
                • <sch:assert test="..."> - Must be true
                • <sch:report test="..."> - Warning if true

                Query Bindings:
                • xslt - XSLT 1.0
                • xslt2 - XSLT 2.0 (recommended)
                • xslt3 - XSLT 3.0
                """);

        content.getChildren().addAll(title, helpText);

        TitledPane pane = new TitledPane("Quick Help", content);
        FontIcon icon = new FontIcon("bi-question-circle");
        icon.setIconSize(14);
        pane.setGraphic(icon);

        return pane;
    }

    /**
     * Creates the Document Structure titled pane.
     */
    private TitledPane createStructurePane() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        structureTreeView = new TreeView<>();
        structureTreeView.setPrefHeight(200);
        structureTreeView.setShowRoot(true);

        // Double-click to navigate to element
        structureTreeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<String> selected = structureTreeView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    navigateToElement(selected.getValue());
                }
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> updateStructureTree());

        content.getChildren().addAll(structureTreeView, refreshBtn);
        VBox.setVgrow(structureTreeView, Priority.ALWAYS);

        TitledPane pane = new TitledPane("Document Structure", content);
        FontIcon icon = new FontIcon("bi-list-nested");
        icon.setIconSize(14);
        pane.setGraphic(icon);

        return pane;
    }

    /**
     * Creates the Rule Templates titled pane.
     */
    private TitledPane createTemplatesPane() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        ListView<String> templatesList = new ListView<>();
        templatesList.getItems().addAll(
                "Pattern",
                "Rule",
                "Assert",
                "Report",
                "Let Variable",
                "Namespace",
                "Diagnostic"
        );
        templatesList.setPrefHeight(150);

        Button insertBtn = new Button("Insert Template");
        insertBtn.setOnAction(e -> {
            String selected = templatesList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                insertTemplate(selected);
            }
        });

        content.getChildren().addAll(templatesList, insertBtn);

        TitledPane pane = new TitledPane("Rule Templates", content);
        FontIcon icon = new FontIcon("bi-file-earmark-plus");
        icon.setIconSize(14);
        pane.setGraphic(icon);

        return pane;
    }

    /**
     * Creates the XPath Tester titled pane.
     */
    private TitledPane createXPathTesterPane() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        xpathInputField = new TextField();
        xpathInputField.setPromptText("Enter XPath expression...");

        Button testBtn = new Button("Test XPath");
        testBtn.setOnAction(e -> testXPath());

        HBox inputRow = new HBox(5);
        inputRow.getChildren().addAll(xpathInputField, testBtn);
        HBox.setHgrow(xpathInputField, Priority.ALWAYS);

        xpathResultArea = new TextArea();
        xpathResultArea.setEditable(false);
        xpathResultArea.setPrefRowCount(5);
        xpathResultArea.setPromptText("XPath results will appear here...");

        content.getChildren().addAll(inputRow, xpathResultArea);

        TitledPane pane = new TitledPane("XPath Tester", content);
        FontIcon icon = new FontIcon("bi-terminal");
        icon.setIconSize(14);
        pane.setGraphic(icon);

        return pane;
    }

    /**
     * Syncs content from Visual Builder to code editor.
     */
    private void syncFromVisualBuilder() {
        if (visualBuilder == null) {
            return;
        }

        try {
            syncingViews = true;
            String generatedCode = visualBuilder.getGeneratedCode();
            // Only sync if the visual builder has actual content beyond the template
            // This prevents overwriting user's code with empty template
            if (generatedCode != null && !generatedCode.isBlank()) {
                // Ask user if they want to replace current code
                // For now, just log - in production, you might want a dialog
                logger.debug("Visual builder has generated code available");
            }
        } finally {
            syncingViews = false;
        }
    }

    /**
     * Updates the document structure tree.
     */
    private void updateStructureTree() {
        String content = codeEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            structureTreeView.setRoot(null);
            return;
        }

        try {
            TreeItem<String> root = new TreeItem<>("Schema");
            root.setExpanded(true);

            // Simple parsing for patterns and rules
            java.util.regex.Pattern patternRegex = java.util.regex.Pattern.compile(
                    "<sch:pattern[^>]*(?:id=[\"']([^\"']*)[\"'])?[^>]*>",
                    java.util.regex.Pattern.MULTILINE
            );
            java.util.regex.Pattern ruleRegex = java.util.regex.Pattern.compile(
                    "<sch:rule[^>]*context=[\"']([^\"']*)[\"'][^>]*>",
                    java.util.regex.Pattern.MULTILINE
            );

            java.util.regex.Matcher patternMatcher = patternRegex.matcher(content);
            while (patternMatcher.find()) {
                String patternId = patternMatcher.group(1);
                TreeItem<String> patternItem = new TreeItem<>(
                        "Pattern" + (patternId != null ? ": " + patternId : "")
                );
                patternItem.setExpanded(true);
                root.getChildren().add(patternItem);
            }

            java.util.regex.Matcher ruleMatcher = ruleRegex.matcher(content);
            while (ruleMatcher.find()) {
                String context = ruleMatcher.group(1);
                TreeItem<String> ruleItem = new TreeItem<>("Rule: " + context);

                // Add to last pattern if exists
                if (!root.getChildren().isEmpty()) {
                    root.getChildren().get(root.getChildren().size() - 1).getChildren().add(ruleItem);
                } else {
                    root.getChildren().add(ruleItem);
                }
            }

            structureTreeView.setRoot(root);
        } catch (Exception e) {
            logger.warn("Failed to update structure tree: {}", e.getMessage());
        }
    }

    /**
     * Navigates to an element in the code editor.
     */
    private void navigateToElement(String elementInfo) {
        String content = codeEditor.getText();
        if (content == null || elementInfo == null) {
            return;
        }

        // Try to find the element in the content
        String searchText = elementInfo;
        if (elementInfo.startsWith("Pattern:")) {
            searchText = "id=\"" + elementInfo.substring(9).trim() + "\"";
        } else if (elementInfo.startsWith("Rule:")) {
            searchText = "context=\"" + elementInfo.substring(5).trim() + "\"";
        }

        int index = content.indexOf(searchText);
        if (index >= 0) {
            CodeArea codeArea = codeEditor.getCodeArea();
            codeArea.moveTo(index);
            codeArea.requestFollowCaret();
            codeArea.requestFocus();
        }
    }

    /**
     * Inserts a template at the cursor position.
     */
    private void insertTemplate(String templateType) {
        String template = switch (templateType) {
            case "Pattern" -> """

                    <sch:pattern id="new-pattern">
                        <sch:title>Pattern Title</sch:title>

                    </sch:pattern>
                    """;
            case "Rule" -> """

                    <sch:rule context="/*">

                    </sch:rule>
                    """;
            case "Assert" -> """

                    <sch:assert test="condition">
                        Error message when condition is false.
                    </sch:assert>
                    """;
            case "Report" -> """

                    <sch:report test="condition">
                        Warning message when condition is true.
                    </sch:report>
                    """;
            case "Let Variable" -> """

                    <sch:let name="variableName" value="xpath-expression"/>
                    """;
            case "Namespace" -> """

                    <sch:ns prefix="prefix" uri="namespace-uri"/>
                    """;
            case "Diagnostic" -> """

                    <sch:diagnostics>
                        <sch:diagnostic id="diagnostic-id">
                            Diagnostic message with details.
                        </sch:diagnostic>
                    </sch:diagnostics>
                    """;
            default -> "";
        };

        if (!template.isEmpty()) {
            CodeArea codeArea = codeEditor.getCodeArea();
            int caretPos = codeArea.getCaretPosition();
            codeArea.insertText(caretPos, template);
            codeArea.requestFocus();
        }
    }

    /**
     * Tests an XPath expression against the current document.
     */
    private void testXPath() {
        String xpath = xpathInputField.getText();
        String content = codeEditor.getText();

        if (xpath == null || xpath.trim().isEmpty()) {
            xpathResultArea.setText("Please enter an XPath expression.");
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            xpathResultArea.setText("No document content to test against.");
            return;
        }

        try {
            // Use Saxon for XPath evaluation
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(
                    new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
            );

            javax.xml.xpath.XPathFactory xpathFactory = javax.xml.xpath.XPathFactory.newInstance();
            javax.xml.xpath.XPath xpathEvaluator = xpathFactory.newXPath();

            // Try to evaluate as node list first
            try {
                javax.xml.xpath.XPathExpression expr = xpathEvaluator.compile(xpath);
                Object result = expr.evaluate(document, javax.xml.xpath.XPathConstants.NODESET);
                org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList) result;

                StringBuilder sb = new StringBuilder();
                sb.append("Found ").append(nodes.getLength()).append(" node(s):\n\n");

                for (int i = 0; i < Math.min(nodes.getLength(), 10); i++) {
                    org.w3c.dom.Node node = nodes.item(i);
                    sb.append(i + 1).append(". ").append(node.getNodeName());
                    if (node.getTextContent() != null && !node.getTextContent().trim().isEmpty()) {
                        String text = node.getTextContent().trim();
                        if (text.length() > 50) {
                            text = text.substring(0, 50) + "...";
                        }
                        sb.append(" = ").append(text);
                    }
                    sb.append("\n");
                }

                if (nodes.getLength() > 10) {
                    sb.append("\n... and ").append(nodes.getLength() - 10).append(" more nodes");
                }

                xpathResultArea.setText(sb.toString());
            } catch (Exception e) {
                // Try as string
                String result = xpathEvaluator.evaluate(xpath, document);
                xpathResultArea.setText("Result: " + result);
            }
        } catch (Exception e) {
            xpathResultArea.setText("Error: " + e.getMessage());
        }
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
            codeEditor.setText(content);
            lastSavedContent = content;
            setDirty(false);

            // Update structure tree
            updateStructureTree();

            // Update test and documentation tabs
            tester.setSchematronFile(sourceFile);
            documentationGenerator.setSchematronFile(sourceFile);

            logger.info("Loaded Schematron file: {}", sourceFile.getName());
        } catch (IOException e) {
            logger.error("Failed to load Schematron file: {}", sourceFile, e);
        }
    }

    // ==================== File Operations ====================

    @Override
    public String getEditorContent() {
        // Always return from code editor (primary view)
        return codeEditor.getText();
    }

    @Override
    public void setEditorContent(String content) {
        codeEditor.setText(content);
        updateStructureTree();
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

            // Update other tabs after save
            tester.setSchematronFile(sourceFile);
            documentationGenerator.setSchematronFile(sourceFile);

            logger.info("Saved Schematron file: {}", sourceFile.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save Schematron file: {}", sourceFile, e);
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

            // Update other tabs
            tester.setSchematronFile(file);
            documentationGenerator.setSchematronFile(file);

            logger.info("Saved Schematron file as: {}", file.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save Schematron file as: {}", file, e);
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
            codeEditor.setText(lastSavedContent);
            updateStructureTree();
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
            // Use XmlService for well-formedness check
            var errors = xmlService.validateText(content);
            if (errors == null || errors.isEmpty()) {
                return null; // Valid
            } else {
                return "Schematron Error: " + errors.get(0).getMessage();
            }
        } catch (Exception e) {
            return "Schematron Error: " + e.getMessage();
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
                codeEditor.setText(formatted);
            }
        } catch (Exception e) {
            logger.warn("Failed to format Schematron: {}", e.getMessage());
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

        return linkDetector.detectSchematronLinks(sourceFile);
    }

    @Override
    public String getCaretPosition() {
        CodeArea codeArea = codeEditor.getCodeArea();
        int line = codeArea.getCurrentParagraph() + 1;
        int col = codeArea.getCaretColumn() + 1;
        return String.format("Ln %d, Col %d", line, col);
    }

    @Override
    public void requestEditorFocus() {
        Platform.runLater(() -> {
            if (codeTab.isSelected()) {
                codeEditor.getCodeArea().requestFocus();
            }
        });
    }

    // ==================== Accessors ====================

    /**
     * Gets the underlying code editor.
     *
     * @return the XmlCodeEditorV2 instance used for Schematron code editing
     */
    public XmlCodeEditorV2 getCodeEditor() {
        return codeEditor;
    }

    /**
     * Gets the code area for direct access.
     *
     * @return the CodeArea instance from the underlying code editor
     */
    public CodeArea getCodeArea() {
        return codeEditor.getCodeArea();
    }

    /**
     * Gets the visual builder component.
     *
     * @return the SchematronVisualBuilder instance for graphical rule creation
     */
    public SchematronVisualBuilder getVisualBuilder() {
        return visualBuilder;
    }

    /**
     * Gets the tester component.
     *
     * @return the SchematronTester instance for testing rules against XML files
     */
    public SchematronTester getTester() {
        return tester;
    }

    /**
     * Gets the documentation generator component.
     *
     * @return the SchematronDocumentationGenerator instance for creating comprehensive documentation
     */
    public SchematronDocumentationGenerator getDocumentationGenerator() {
        return documentationGenerator;
    }

    /**
     * Applies generated code from visual builder to the code editor.
     */
    public void applyGeneratedCode() {
        if (visualBuilder != null) {
            String generatedCode = visualBuilder.getGeneratedCode();
            if (generatedCode != null && !generatedCode.isBlank()) {
                codeEditor.setText(generatedCode);
                setDirty(true);
                updateStructureTree();
                // Switch to code tab
                innerTabPane.getSelectionModel().select(codeTab);
            }
        }
    }

    /**
     * Deprecated method for backward compatibility.
     *
     * @return the XmlCodeEditorV2 instance used for Schematron code editing
     * @deprecated Use getCodeEditor() instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public XmlCodeEditorV2 getEditor() {
        return codeEditor;
    }

    @Override
    public CodeArea getPrimaryCodeArea() {
        return codeEditor.getCodeArea();
    }
}
