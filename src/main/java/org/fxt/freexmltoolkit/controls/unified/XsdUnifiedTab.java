package org.fxt.freexmltoolkit.controls.unified;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

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
    private Tab typeLibraryTab;
    private Tab typeEditorTab;
    private Tab analysisTab;
    private Tab documentationTab;
    private Tab sampleDataTab;
    private Tab flattenTab;
    private final XmlCodeEditorV2 textEditor;
    private XsdGraphView graphView;
    private XsdEditorContext editorContext;
    private final javafx.scene.layout.StackPane mainContainer = new javafx.scene.layout.StackPane();
    private final javafx.scene.control.SplitPane splitPane = new javafx.scene.control.SplitPane();
    private ViewMode currentViewMode = ViewMode.TABS;

    // XSD sub-tab panels (lazy-initialized)
    private org.fxt.freexmltoolkit.controls.unified.xsd.XsdTypeLibraryPanel typeLibraryPanel;
    private org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager typeEditorManager;
    private TabPane typeEditorTabPane;
    private org.fxt.freexmltoolkit.controls.unified.xsd.XsdSchemaAnalysisPanel analysisPanel;
    private org.fxt.freexmltoolkit.controls.unified.xsd.XsdDocumentationPanel documentationPanel;
    private org.fxt.freexmltoolkit.controls.unified.xsd.XsdSampleDataPanel sampleDataPanel;
    private org.fxt.freexmltoolkit.controls.unified.xsd.XsdFlattenPanel flattenPanel;

    /**
     * Callback invoked whenever the underlying XsdEditorContext (and thus selection model)
     * is recreated (e.g. when rebuilding the graphic view).
     */
    private Runnable onEditorContextChangedCallback;

    // Services
    private final XmlService xmlService;

    // Debouncing for real-time updates
    private final javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));

    // Model
    private XsdSchema xsdSchema;

    // State
    private String lastSavedContent;
    private LinkedFileDetector linkDetector;
    private boolean syncingViews = false;
    private String pendingNavigationElement;

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
        this.typeLibraryTab = new Tab("Type Library");
        this.typeEditorTab = new Tab("Type Editor");
        this.analysisTab = new Tab("Analysis");
        this.documentationTab = new Tab("Documentation");
        this.sampleDataTab = new Tab("Sample Data");
        this.flattenTab = new Tab("Flatten");

        initializeContent();

        // Setup debounced real-time updates
        debounce.setOnFinished(event -> {
            if (currentViewMode != ViewMode.TABS || graphicTab.isSelected()) {
                syncToGraphicView();
            }
        });

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
        viewTabPane.setSide(Side.BOTTOM);
        viewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Text tab
        FontIcon textIcon = new FontIcon("bi-code-slash");
        textIcon.setIconSize(14);
        textTab.setGraphic(textIcon);
        textTab.getStyleClass().add("primary-tab");

        // Graphic tab (initially with placeholder)
        FontIcon graphicIcon = new FontIcon("bi-diagram-3");
        graphicIcon.setIconSize(14);
        graphicTab.setGraphic(graphicIcon);
        graphicTab.getStyleClass().add("primary-tab");

        // Initial setup for TABS mode
        textTab.setContent(textEditor);
        graphicTab.setContent(new Label("Loading graphic view..."));

        // Sub-tabs with icons
        FontIcon typeLibIcon = new FontIcon("bi-collection");
        typeLibIcon.setIconSize(14);
        typeLibraryTab.setGraphic(typeLibIcon);
        typeLibraryTab.getStyleClass().add("utility-tab");
        typeLibraryTab.setContent(new Label("Load an XSD file to browse types"));

        FontIcon typeEdIcon = new FontIcon("bi-pencil-square");
        typeEdIcon.setIconSize(14);
        typeEditorTab.setGraphic(typeEdIcon);
        typeEditorTab.getStyleClass().add("primary-tab");
        typeEditorTab.setContent(new Label("Load an XSD file to edit types"));

        FontIcon analysisIcon = new FontIcon("bi-bar-chart");
        analysisIcon.setIconSize(14);
        analysisTab.setGraphic(analysisIcon);
        analysisTab.getStyleClass().add("utility-tab");
        analysisTab.setContent(new Label("Load an XSD file to analyze"));

        FontIcon docIcon = new FontIcon("bi-file-text");
        docIcon.setIconSize(14);
        documentationTab.setGraphic(docIcon);
        documentationTab.getStyleClass().add("output-tab");
        documentationTab.setContent(new Label("Load an XSD file for documentation"));

        FontIcon sampleIcon = new FontIcon("bi-file-earmark-code");
        sampleIcon.setIconSize(14);
        sampleDataTab.setGraphic(sampleIcon);
        sampleDataTab.getStyleClass().add("output-tab");
        sampleDataTab.setContent(new Label("Load an XSD file for sample generation"));

        FontIcon flattenIcon = new FontIcon("bi-layers");
        flattenIcon.setIconSize(14);
        flattenTab.setGraphic(flattenIcon);
        flattenTab.getStyleClass().add("utility-tab");
        flattenTab.setContent(new Label("Load an XSD file to flatten"));

        viewTabPane.getTabs().addAll(textTab, graphicTab, typeLibraryTab, typeEditorTab,
                analysisTab, documentationTab, sampleDataTab, flattenTab);

        // Tab switch listener to sync content
        textTab.setOnSelectionChanged(e -> {
            if (textTab.isSelected() && !syncingViews && currentViewMode == ViewMode.TABS) {
                syncFromGraphicView();
            }
        });

        graphicTab.setOnSelectionChanged(e -> {
            if (graphicTab.isSelected() && !syncingViews && currentViewMode == ViewMode.TABS) {
                syncToGraphicView();
            }
        });

        // Lazy initialization for sub-tabs
        typeLibraryTab.setOnSelectionChanged(e -> {
            if (typeLibraryTab.isSelected() && xsdSchema != null) {
                ensureTypeLibraryInitialized();
            }
        });

        typeEditorTab.setOnSelectionChanged(e -> {
            if (typeEditorTab.isSelected() && xsdSchema != null) {
                ensureTypeEditorInitialized();
            }
        });

        analysisTab.setOnSelectionChanged(e -> {
            if (analysisTab.isSelected() && xsdSchema != null) {
                ensureAnalysisInitialized();
            }
        });

        documentationTab.setOnSelectionChanged(e -> {
            if (documentationTab.isSelected()) {
                ensureDocumentationInitialized();
            }
        });

        sampleDataTab.setOnSelectionChanged(e -> {
            if (sampleDataTab.isSelected()) {
                ensureSampleDataInitialized();
            }
        });

        flattenTab.setOnSelectionChanged(e -> {
            if (flattenTab.isSelected()) {
                ensureFlattenInitialized();
            }
        });

        // Setup main container
        mainContainer.getChildren().add(viewTabPane);
        setContent(mainContainer);

        // Setup change listener for dirty tracking and real-time updates
        CodeArea codeArea = textEditor.getCodeArea();
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!syncingViews) {
                if (lastSavedContent != null && !lastSavedContent.equals(newText)) {
                    setDirty(true);
                }
                // Trigger debounced update if graphic view is visible
                if (currentViewMode != ViewMode.TABS || graphicTab.isSelected()) {
                    debounce.playFromStart();
                }
            }
        });
    }

    @Override
    public void setViewMode(ViewMode mode) {
        if (this.currentViewMode == mode) {
            return;
        }
        
        logger.info("Switching XSD view mode from {} to {}", currentViewMode, mode);
        this.currentViewMode = mode;
        
        syncingViews = true;
        try {
            // Clear current view
            mainContainer.getChildren().clear();
            
            if (mode == ViewMode.TABS) {
                // Restore TabPane structure
                splitPane.getItems().clear();
                textTab.setContent(textEditor);
                // Graphic content will be set by syncToGraphicView
                mainContainer.getChildren().add(viewTabPane);
                
                if (graphicTab.isSelected()) {
                    syncToGraphicView();
                }
            } else {
                // Use SplitPane structure
                textTab.setContent(null);
                graphicTab.setContent(null);
                
                splitPane.setOrientation(mode == ViewMode.SPLIT_TOP_BOTTOM ? Orientation.VERTICAL : Orientation.HORIZONTAL);
                splitPane.getItems().setAll(textEditor, new Label("Loading graphic view..."));
                
                mainContainer.getChildren().add(splitPane);
                
                // Ensure graphic view is updated
                syncToGraphicView();
            }
        } finally {
            syncingViews = false;
        }
    }

    @Override
    public ViewMode getViewMode() {
        return currentViewMode;
    }

    /**
     * Helper to set content to the graphic view regardless of current view mode.
     */
    private void setGraphicViewContent(javafx.scene.Node content) {
        if (currentViewMode == ViewMode.TABS) {
            graphicTab.setContent(content);
        } else {
            if (splitPane.getItems().size() > 1) {
                splitPane.getItems().set(1, content);
            } else {
                splitPane.getItems().add(content);
            }
        }
    }

    /**
     * Parses XSD content and builds the graphic view.
     */
    // ==================== Sub-Tab Lazy Initialization ====================

    private void ensureTypeLibraryInitialized() {
        if (typeLibraryPanel == null && xsdSchema != null) {
            typeLibraryPanel = new org.fxt.freexmltoolkit.controls.unified.xsd.XsdTypeLibraryPanel();
            typeLibraryPanel.setSchema(xsdSchema);
            typeLibraryTab.setContent(typeLibraryPanel);
        }
    }

    private void ensureTypeEditorInitialized() {
        if (typeEditorManager == null && xsdSchema != null) {
            typeEditorTabPane = new TabPane();
            typeEditorManager = new org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager(
                    typeEditorTabPane, xsdSchema);
            typeEditorTab.setContent(typeEditorTabPane);

            // Wire type library to type editor if both exist
            if (typeLibraryPanel != null && typeLibraryPanel.getTypeLibraryView() != null) {
                typeLibraryPanel.getTypeLibraryView().setTypeEditorTabManager(typeEditorManager);
            }
        }
    }

    private void ensureAnalysisInitialized() {
        if (analysisPanel == null) {
            analysisPanel = new org.fxt.freexmltoolkit.controls.unified.xsd.XsdSchemaAnalysisPanel();
            analysisTab.setContent(analysisPanel);
        }
        if (xsdSchema != null) {
            analysisPanel.setSchema(xsdSchema);
            analysisPanel.ensureInitialized();
        }
    }

    private void ensureDocumentationInitialized() {
        if (documentationPanel == null) {
            documentationPanel = new org.fxt.freexmltoolkit.controls.unified.xsd.XsdDocumentationPanel();
            documentationTab.setContent(documentationPanel);
        }
        if (sourceFile != null) {
            documentationPanel.setSourceFile(sourceFile);
        }
    }

    private void ensureSampleDataInitialized() {
        if (sampleDataPanel == null) {
            sampleDataPanel = new org.fxt.freexmltoolkit.controls.unified.xsd.XsdSampleDataPanel();
            sampleDataTab.setContent(sampleDataPanel);
        }
        if (sourceFile != null) {
            sampleDataPanel.setSourceFile(sourceFile);
        }
    }

    private void ensureFlattenInitialized() {
        if (flattenPanel == null) {
            flattenPanel = new org.fxt.freexmltoolkit.controls.unified.xsd.XsdFlattenPanel();
            flattenTab.setContent(flattenPanel);
        }
        if (sourceFile != null) {
            flattenPanel.setSourceFile(sourceFile);
        }
    }

    /**
     * Updates all sub-tab panels when the schema changes.
     */
    private void updateSubTabsForSchema() {
        if (typeLibraryPanel != null) {
            typeLibraryPanel.setSchema(xsdSchema);
        }
        if (typeEditorManager != null && xsdSchema != null) {
            // Recreate the type editor with the new schema
            typeEditorTabPane = new TabPane();
            typeEditorManager = new org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager(
                    typeEditorTabPane, xsdSchema);
            typeEditorTab.setContent(typeEditorTabPane);
        }
        if (analysisPanel != null) {
            analysisPanel.setSchema(xsdSchema);
            if (analysisTab.isSelected()) {
                analysisPanel.ensureInitialized();
            }
        }
        if (sampleDataPanel != null && sourceFile != null) {
            sampleDataPanel.setSourceFile(sourceFile);
        }
        if (flattenPanel != null && sourceFile != null) {
            flattenPanel.setSourceFile(sourceFile);
        }
        if (documentationPanel != null && sourceFile != null) {
            documentationPanel.setSourceFile(sourceFile);
        }
    }

    // ==================== Graph View ====================

    private void parseAndBuildGraphView(String content) {
        if (content == null || content.trim().isEmpty()) {
            setGraphicViewContent(new Label("No XSD content to display"));
            return;
        }

        try {
            // Parse XSD content into model
            XsdNodeFactory factory = new XsdNodeFactory();

            // Provide base directory for include/import resolution
            if (sourceFile != null && sourceFile.exists()) {
                xsdSchema = factory.fromString(content, sourceFile.getParentFile().toPath());
            } else {
                xsdSchema = factory.fromString(content);
            }

            // Create graph view
            graphView = new XsdGraphView(xsdSchema);

            // Set imported schemas for reference resolution (enables node expansion)
            graphView.setImportedSchemas(factory.getImportedSchemas());

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

            setGraphicViewContent(graphView);

            // Handle deferred navigation
            if (pendingNavigationElement != null) {
                String elementName = pendingNavigationElement;
                pendingNavigationElement = null;
                Platform.runLater(() -> graphView.navigateToElement(elementName));
            }

            logger.debug("XSD graphic view built successfully");

            // Update sub-tab panels with new schema
            updateSubTabsForSchema();

            // Notify listeners that the editor context changed (graph view rebuild creates new context)
            if (onEditorContextChangedCallback != null) {
                Platform.runLater(onEditorContextChangedCallback);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XSD for graphic view: {}", e.getMessage());
            setGraphicViewContent(new Label("Failed to parse XSD: " + e.getMessage()));
        }
    }

    /**
     * Syncs content from graphic view to text editor.
     */
    private void syncFromGraphicView() {
        if (graphView == null || xsdSchema == null) {
            return;
        }

        // Only serialize back if graphic view has actual changes.
        // This prevents whitespace drift from parse -> serialize round-trips.
        boolean graphicDirty = editorContext != null && editorContext.isDirty();
        if (!graphicDirty) {
            return;
        }

        try {
            syncingViews = true;

            // Serialize the model back to XML (excluding inlined include/import content)
            XsdSerializer serializer = new XsdSerializer();
            serializer.setExcludeIncludedNodes(true);
            String serialized = serializer.serialize(xsdSchema);
            if (serialized != null && !serialized.equals(textEditor.getText())) {
                textEditor.setText(serialized);
            }

            // Reset editor context dirty flag since we synced
            editorContext.setDirty(false);
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
            // Sync from graphic view first (excluding inlined include/import content)
            try {
                XsdSerializer serializer = new XsdSerializer();
                serializer.setExcludeIncludedNodes(true);
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

    /**
     * Navigates to a specific element in the XSD graphic view.
     * If the graphic view is not yet built, stores the element name for deferred navigation
     * (consumed when parseAndBuildGraphView succeeds).
     *
     * @param elementName the element to navigate to
     */
    public void navigateToElement(String elementName) {
        if (elementName == null || elementName.isEmpty()) {
            return;
        }

        // Store pending navigation — parseAndBuildGraphView will consume it
        // if the graphic tab switch triggers a rebuild
        pendingNavigationElement = elementName;

        // Switch to graphic tab to show the navigation result
        if (currentViewMode == ViewMode.TABS) {
            viewTabPane.getSelectionModel().select(graphicTab);
        }

        // If graphView already exists and no rebuild was triggered
        // (parseAndBuildGraphView would have consumed pendingNavigationElement),
        // navigate immediately
        if (pendingNavigationElement != null && graphView != null) {
            String name = pendingNavigationElement;
            pendingNavigationElement = null;
            Platform.runLater(() -> graphView.navigateToElement(name));
        } else if (pendingNavigationElement != null && graphView == null) {
            // Graphic tab already selected or in split mode — no rebuild triggered.
            // Force a rebuild so parseAndBuildGraphView consumes the pending element.
            syncToGraphicView();
        }
    }

    /**
     * Returns the pending navigation element name, or null if none.
     * Package-private for testing.
     */
    String getPendingNavigationElement() {
        return pendingNavigationElement;
    }

    @Override
    public String validate() {
        // Always get content directly from text editor for validation
        // This ensures we validate the actual current text, not cached/serialized content
        String content = textEditor.getText();

        logger.debug("Validating XSD content, length: {}", content != null ? content.length() : 0);

        if (content == null || content.trim().isEmpty()) {
            return "Empty document";
        }

        try {
            // First check XML well-formedness
            var errors = xmlService.validateText(content);
            if (errors != null && !errors.isEmpty()) {
                return "XML Error: " + errors.get(0).getMessage();
            }

            // Now validate as XSD schema using SchemaFactory
            // Force using the JDK's built-in SchemaFactory, not Xerces
            // Xerces may be too lenient with type resolution errors
            SchemaFactory schemaFactory;
            try {
                // Try to use JDK's implementation explicitly
                schemaFactory = SchemaFactory.newInstance(
                        XMLConstants.W3C_XML_SCHEMA_NS_URI,
                        "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory",
                        null);
            } catch (Exception e) {
                // Fall back to default
                schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            }
            logger.debug("Using SchemaFactory: {}", schemaFactory.getClass().getName());

            // Check if content contains potentially invalid types for debugging
            if (content.contains("decimal2") || content.contains("string2")) {
                logger.warn("Content contains potentially invalid type reference (decimal2 or string2)");
            }

            // Collect all validation errors using a custom error handler
            java.util.List<String> schemaErrors = new java.util.ArrayList<>();
            java.util.List<String> importWarnings = new java.util.ArrayList<>();
            schemaFactory.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException e) {
                    logger.debug("Schema warning: {}", e.getMessage());
                }

                @Override
                public void error(org.xml.sax.SAXParseException e) {
                    String msg = e.getMessage();
                    // Filter out import-related errors (external schemas not available)
                    // These typically reference namespaces like ds:, xlink:, etc.
                    if (msg != null && (msg.contains("ds:") || msg.contains("xlink:") ||
                            msg.contains("xml:") || msg.contains("xsi:"))) {
                        logger.debug("Ignoring import-related error: {}", msg);
                        importWarnings.add(msg);
                    } else {
                        // All other errors including xs: prefix type errors
                        logger.warn("Schema error: {}", msg);
                        schemaErrors.add(msg);
                    }
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    logger.error("Schema fatal error: {}", e.getMessage());
                    schemaErrors.add(e.getMessage());
                    throw e;
                }
            });

            schemaFactory.newSchema(new StreamSource(new StringReader(content)));

            // Log import warnings if any
            if (!importWarnings.isEmpty()) {
                logger.info("Ignored {} import-related warning(s) - external schemas not available", importWarnings.size());
            }

            // Check if any real errors were collected
            if (!schemaErrors.isEmpty()) {
                String errorMsg = schemaErrors.get(0);
                if (errorMsg.length() > 200) {
                    errorMsg = errorMsg.substring(0, 200) + "...";
                }
                logger.info("XSD validation failed with {} error(s): {}", schemaErrors.size(), errorMsg);
                return "XSD Schema Error: " + errorMsg;
            }

            logger.debug("XSD validation successful");
            return null; // Valid XSD schema
        } catch (org.xml.sax.SAXException e) {
            // SAXException indicates the XSD schema is invalid
            String message = e.getMessage();
            if (message != null && message.length() > 200) {
                message = message.substring(0, 200) + "...";
            }
            logger.info("XSD validation failed: {}", e.getMessage());
            return "XSD Schema Error: " + message;
        } catch (Exception e) {
            logger.error("Unexpected error during XSD validation", e);
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
