package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
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
 * Full-featured Unified Editor tab for XSD Schema files.
 * <p>
 * Features:
 * <ul>
 *   <li>Text view with syntax highlighting (XmlCodeEditorV2)</li>
 *   <li>Graphic view with XsdGraphView (XMLSpy-style visualization)</li>
 *   <li>Embedded properties panel in graphic view</li>
 *   <li>Undo/redo support via XsdEditorContext</li>
 * </ul>
 *
 * @since 2.0
 */
public class XsdUnifiedTab extends AbstractUnifiedEditorTab {

    private static final Logger logger = LogManager.getLogger(XsdUnifiedTab.class);

    // UI Components
    private final TabPane viewTabPane;
    private final Tab textTab;
    private final Tab graphicTab;
    private final XmlCodeEditorV2 textEditor;
    private XsdGraphView graphView;
    private XsdEditorContext editorContext;

    /**
     * Callback invoked whenever the underlying XsdEditorContext (and thus selection model)
     * is recreated (e.g. when rebuilding the graphic view).
     */
    private Runnable onEditorContextChangedCallback;

    // Services
    private final XmlService xmlService;

    // Model
    private XsdSchema xsdSchema;

    // State
    private String lastSavedContent;
    private LinkedFileDetector linkDetector;
    private boolean syncingViews = false;

    /**
     * Creates a new XSD Unified Editor tab.
     *
     * @param sourceFile the file to edit (can be null for new files)
     */
    public XsdUnifiedTab(File sourceFile) {
        super(sourceFile, UnifiedEditorFileType.XSD);

        // Initialize services
        this.xmlService = new XmlServiceImpl();

        // Create text editor
        this.textEditor = XmlCodeEditorV2Factory.createWithoutSchema();
        textEditor.setDocumentUri(sourceFile != null ? sourceFile.toURI().toString() : "untitled:" + System.nanoTime() + ".xsd");

        // Create view tabs
        this.viewTabPane = new TabPane();
        this.textTab = new Tab("Text");
        this.graphicTab = new Tab("Graphic");

        initializeContent();

        // Load file content if provided
        if (sourceFile != null && sourceFile.exists()) {
            loadFile();
        } else {
            // New file with XSD template
            String template = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                               elementFormDefault="qualified">

                        <!-- Define your schema here -->

                    </xs:schema>
                    """;
            textEditor.setText(template);
            lastSavedContent = template;
            parseAndBuildGraphView(template);
        }
    }

    @Override
    protected void initializeContent() {
        // Setup view tabs
        viewTabPane.setSide(Side.LEFT);
        viewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Text tab
        FontIcon textIcon = new FontIcon("bi-code-slash");
        textIcon.setIconSize(16);
        textTab.setGraphic(textIcon);
        textTab.setContent(textEditor);

        // Graphic tab (initially with placeholder)
        FontIcon graphicIcon = new FontIcon("bi-diagram-3");
        graphicIcon.setIconSize(16);
        graphicTab.setGraphic(graphicIcon);
        graphicTab.setContent(new Label("Loading graphic view..."));

        viewTabPane.getTabs().addAll(textTab, graphicTab);

        // Tab switch listener to sync content
        textTab.setOnSelectionChanged(e -> {
            if (textTab.isSelected() && !syncingViews) {
                // Switching to text view - sync from graphic view if it has unsaved changes
                syncFromGraphicView();
            }
        });

        graphicTab.setOnSelectionChanged(e -> {
            if (graphicTab.isSelected() && !syncingViews) {
                // Switching to graphic view - rebuild from text
                syncToGraphicView();
            }
        });

        // Set main content
        VBox container = new VBox(viewTabPane);
        VBox.setVgrow(viewTabPane, Priority.ALWAYS);
        setContent(container);

        // Setup change listener for dirty tracking in text editor
        CodeArea codeArea = textEditor.getCodeArea();
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!syncingViews && lastSavedContent != null && !lastSavedContent.equals(newText)) {
                setDirty(true);
            }
        });
    }

    /**
     * Parses XSD content and builds the graphic view.
     */
    private void parseAndBuildGraphView(String content) {
        if (content == null || content.trim().isEmpty()) {
            graphicTab.setContent(new Label("No XSD content to display"));
            return;
        }

        try {
            // Parse XSD content into model
            XsdNodeFactory factory = new XsdNodeFactory();
            xsdSchema = factory.fromString(content);

            // Create graph view
            graphView = new XsdGraphView(xsdSchema);
            editorContext = graphView.getEditorContext();

            // Hide embedded properties panel - use MultiFunctionalSidePane instead
            graphView.hideEmbeddedPropertiesPanel();

            // Listen for model changes to track dirty state
            if (editorContext != null) {
                editorContext.addPropertyChangeListener(evt -> {
                    if ("dirty".equals(evt.getPropertyName()) && Boolean.TRUE.equals(evt.getNewValue()) && !syncingViews) {
                        setDirty(true);
                    }
                });
            }

            graphicTab.setContent(graphView);
            logger.debug("XSD graphic view built successfully");

            // Notify listeners that the editor context changed (graph view rebuild creates new context)
            if (onEditorContextChangedCallback != null) {
                Platform.runLater(onEditorContextChangedCallback);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XSD for graphic view: {}", e.getMessage());
            graphicTab.setContent(new Label("Failed to parse XSD: " + e.getMessage()));
        }
    }

    /**
     * Syncs content from graphic view to text editor.
     */
    private void syncFromGraphicView() {
        if (graphView == null || xsdSchema == null) {
            return;
        }

        try {
            syncingViews = true;

            // Serialize the model back to XML
            XsdSerializer serializer = new XsdSerializer();
            String serialized = serializer.serialize(xsdSchema);
            if (serialized != null && !serialized.equals(textEditor.getText())) {
                textEditor.setText(serialized);
            }

            // Reset editor context dirty flag since we synced
            if (editorContext != null) {
                editorContext.setDirty(false);
            }
        } catch (Exception e) {
            logger.warn("Failed to sync from graphic view: {}", e.getMessage());
        } finally {
            syncingViews = false;
        }
    }

    /**
     * Syncs content from text editor to graphic view.
     */
    private void syncToGraphicView() {
        String content = textEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            syncingViews = true;
            parseAndBuildGraphView(content);
        } finally {
            syncingViews = false;
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
            textEditor.setText(content);
            lastSavedContent = content;
            setDirty(false);

            // Build graphic view
            parseAndBuildGraphView(content);

            logger.info("Loaded XSD file: {}", sourceFile.getName());
        } catch (IOException e) {
            logger.error("Failed to load XSD file: {}", sourceFile, e);
        }
    }

    // ==================== File Operations ====================

    @Override
    public String getEditorContent() {
        // Return text from active view
        if (graphicTab.isSelected() && graphView != null && xsdSchema != null) {
            // Sync from graphic view first
            try {
                XsdSerializer serializer = new XsdSerializer();
                return serializer.serialize(xsdSchema);
            } catch (Exception e) {
                logger.warn("Failed to serialize from graphic view: {}", e.getMessage());
            }
        }
        return textEditor.getText();
    }

    @Override
    public void setEditorContent(String content) {
        textEditor.setText(content);
        parseAndBuildGraphView(content);
    }

    @Override
    public boolean save() {
        if (sourceFile == null) {
            logger.warn("Cannot save: no source file specified");
            return false;
        }

        try {
            // Get content from active view
            String content = getEditorContent();
            Files.writeString(sourceFile.toPath(), content, StandardCharsets.UTF_8);
            lastSavedContent = content;
            setDirty(false);

            // Reset editor context dirty flag
            if (editorContext != null) {
                editorContext.setDirty(false);
            }

            logger.info("Saved XSD file: {}", sourceFile.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save XSD file: {}", sourceFile, e);
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

            if (editorContext != null) {
                editorContext.setDirty(false);
            }

            logger.info("Saved XSD file as: {}", file.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save XSD file as: {}", file, e);
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
            textEditor.setText(lastSavedContent);
            parseAndBuildGraphView(lastSavedContent);
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
                return "XSD Error: " + errors.get(0).getMessage();
            }
        } catch (Exception e) {
            return "XSD Error: " + e.getMessage();
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
                textEditor.setText(formatted);
                if (graphicTab.isSelected()) {
                    parseAndBuildGraphView(formatted);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to format XSD: {}", e.getMessage());
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

        return linkDetector.detectXsdLinks(sourceFile);
    }

    @Override
    public String getCaretPosition() {
        CodeArea codeArea = textEditor.getCodeArea();
        int line = codeArea.getCurrentParagraph() + 1;
        int col = codeArea.getCaretColumn() + 1;
        return String.format("Ln %d, Col %d", line, col);
    }

    @Override
    public void requestEditorFocus() {
        Platform.runLater(() -> {
            if (textTab.isSelected()) {
                textEditor.getCodeArea().requestFocus();
            } else if (graphView != null) {
                graphView.requestFocus();
            }
        });
    }

    // ==================== Accessors ====================

    /**
     * Gets the underlying text editor.
     */
    public XmlCodeEditorV2 getTextEditor() {
        return textEditor;
    }

    /**
     * Gets the code area for direct access.
     */
    public CodeArea getCodeArea() {
        return textEditor.getCodeArea();
    }

    /**
     * Gets the graphic view.
     */
    public XsdGraphView getGraphView() {
        return graphView;
    }

    /**
     * Gets the XSD editor context.
     */
    public XsdEditorContext getEditorContext() {
        return editorContext;
    }

    /**
     * Registers a callback that will be invoked whenever the XSD graphic view is rebuilt
     * and a new {@link XsdEditorContext} is created.
     *
     * @param callback callback to invoke (may be null to clear)
     */
    public void setOnEditorContextChangedCallback(Runnable callback) {
        this.onEditorContextChangedCallback = callback;
    }

    /**
     * Gets the XSD schema model.
     */
    public XsdSchema getXsdSchema() {
        return xsdSchema;
    }

    /**
     * Deprecated method for backward compatibility.
     * @deprecated Use getTextEditor() instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public XmlCodeEditorV2 getEditor() {
        return textEditor;
    }

    @Override
    public CodeArea getPrimaryCodeArea() {
        return textEditor.getCodeArea();
    }
}
