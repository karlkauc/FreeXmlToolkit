package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.geometry.Orientation;
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
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.domain.ValidationError;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.*;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Full-featured Unified Editor tab for XML files.
 * <p>
 * Features:
 * <ul>
 *   <li>Text view with syntax highlighting (XmlCodeEditorV2)</li>
 *   <li>Graphic view for visual XML structure</li>
 *   <li>XSD and Schematron linking</li>
 *   <li>Validation support</li>
 * </ul>
 *
 * @since 2.0
 */
public class XmlUnifiedTab extends AbstractUnifiedEditorTab {

    private static final Logger logger = LogManager.getLogger(XmlUnifiedTab.class);

    // UI Components
    private final TabPane viewTabPane;
    private final Tab xmlTab;
    private final Tab graphicTab;
    private final XmlCodeEditorV2 textEditor;
    private final org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider schemaProvider;
    private final javafx.scene.layout.StackPane mainContainer = new javafx.scene.layout.StackPane();
    private final javafx.scene.control.SplitPane splitPane = new javafx.scene.control.SplitPane();
    private ViewMode currentViewMode = ViewMode.TABS;

    // Services
    private final XmlService xmlService;
    private final XsdDocumentationService xsdDocumentationService;
    private final SchematronService schematronService;
    private DocumentBuilder documentBuilder;

    // Debouncing for real-time updates
    private final javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));

    // Linked files
    private File xsdFile;
    private File schematronFile;
    private XsdDocumentationData xsdDocumentationData;
    private XmlEditorContext graphicViewContext;

    // State
    private String lastSavedContent;
    private LinkedFileDetector linkDetector;
    private boolean syncingViews = false;

    // Callback for XSD loading notification
    private Runnable onXsdLoadedCallback;

    /**
     * Creates a new XML Unified Editor tab.
     *
     * @param sourceFile the file to edit (can be null for new files)
     */
    public XmlUnifiedTab(File sourceFile) {
        super(sourceFile, UnifiedEditorFileType.XML);

        // Initialize services
        this.xmlService = new XmlServiceImpl();
        this.xsdDocumentationService = new XsdDocumentationService();
        this.schematronService = new SchematronServiceImpl();

        try {
            this.documentBuilder = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            logger.error("Failed to create DocumentBuilder", e);
        }

        // Create mutable schema provider and text editor
        this.schemaProvider = new org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider();
        this.textEditor = XmlCodeEditorV2Factory.createWithMutableSchema(schemaProvider);
        textEditor.setDocumentUri(sourceFile != null ? sourceFile.toURI().toString() : "untitled:" + System.nanoTime() + ".xml");

        // Create view tabs
        this.viewTabPane = new TabPane();
        this.xmlTab = new Tab("XML");
        this.graphicTab = new Tab("Graphic");

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
            // New file with template
            String template = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>";
            textEditor.setText(template);
            lastSavedContent = template;
        }
    }

    @Override
    protected void initializeContent() {
        // Setup view tabs
        viewTabPane.setSide(Side.LEFT);
        viewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // XML (Text) tab
        FontIcon xmlIcon = new FontIcon("bi-code-slash");
        xmlIcon.setIconSize(16);
        xmlTab.setGraphic(xmlIcon);

        // Graphic tab
        FontIcon graphicIcon = new FontIcon("bi-columns-gap");
        graphicIcon.setIconSize(16);
        graphicTab.setGraphic(graphicIcon);
        
        // Initial setup for TABS mode
        xmlTab.setContent(textEditor);
        graphicTab.setContent(new Label("Loading graphic view..."));

        viewTabPane.getTabs().addAll(xmlTab, graphicTab);

        // Tab switch listener to sync content
        xmlTab.setOnSelectionChanged(e -> {
            if (xmlTab.isSelected() && !syncingViews && currentViewMode == ViewMode.TABS) {
                // Switching to text view - sync from graphic view if it has changes
                syncFromGraphicView();
            }
        });

        graphicTab.setOnSelectionChanged(e -> {
            if (graphicTab.isSelected() && !syncingViews && currentViewMode == ViewMode.TABS) {
                // Switching to graphic view - sync from text
                syncToGraphicView();
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

        // Cursor position listener
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorInformation());
    }

    @Override
    public void setViewMode(ViewMode mode) {
        if (this.currentViewMode == mode) return;
        
        logger.info("Switching XML view mode from {} to {}", currentViewMode, mode);
        this.currentViewMode = mode;
        
        syncingViews = true;
        try {
            // Clear current view
            mainContainer.getChildren().clear();
            
            if (mode == ViewMode.TABS) {
                // Restore TabPane structure
                splitPane.getItems().clear();
                xmlTab.setContent(textEditor);
                // Graphic content will be set by syncToGraphicView
                mainContainer.getChildren().add(viewTabPane);
                
                if (graphicTab.isSelected()) {
                    syncToGraphicView();
                }
            } else {
                // Use SplitPane structure
                xmlTab.setContent(null);
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
     * Refreshes the graphic view based on current XML content.
     */
    private void refreshGraphicView() {
        String content = textEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            setGraphicViewContent(new Label("No XML content to display"));
            return;
        }

        try {
            // Use high-performance streaming parser for better memory management
            org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser parser = 
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser();
            XmlDocument xmlDocument = parser.parse(content);

            // Create editor context
            graphicViewContext = new XmlEditorContext(xmlDocument);

            // Apply XSD schema if available
            if (xsdDocumentationData != null) {
                graphicViewContext.setSchema(xsdDocumentationData);
            }

            // Listen for model changes to track dirty state and potentially sync back
            graphicViewContext.addPropertyChangeListener(evt -> {
                if ("dirty".equals(evt.getPropertyName())
                    && Boolean.TRUE.equals(evt.getNewValue())
                    && !syncingViews) {
                    setDirty(true);
                    
                    // In Split mode, we might want real-time sync back to text, 
                    // but that's expensive for large files.
                    // For now, only sync back when switching away or manually.
                }
            });

            XmlCanvasView canvasView = new XmlCanvasView(graphicViewContext);
            VBox container = new VBox(canvasView);
            VBox.setVgrow(canvasView, Priority.ALWAYS);
            setGraphicViewContent(container);
        } catch (Exception e) {
            setGraphicViewContent(new Label("Invalid XML: " + e.getMessage()));
        }
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
     * Syncs content from text editor to graphic view.
     */
    private void syncToGraphicView() {
        try {
            syncingViews = true;
            refreshGraphicView();
        } finally {
            syncingViews = false;
        }
    }

    /**
     * Syncs content from graphic view to text editor.
     */
    private void syncFromGraphicView() {
        if (graphicViewContext == null) {
            return;
        }

        try {
            syncingViews = true;

            // Serialize the XmlDocument model back to XML text
            String serialized = graphicViewContext.serializeToString();
            if (serialized != null && !serialized.equals(textEditor.getText())) {
                textEditor.setText(serialized);
            }

            // Reset dirty flag since we synced
            if (graphicViewContext.getCommandManager() != null) {
                graphicViewContext.getCommandManager().markAsSaved();
            }

        } catch (Exception e) {
            logger.warn("Failed to sync from graphic view: {}", e.getMessage());
        } finally {
            syncingViews = false;
        }
    }

    /**
     * Validates the XML content.
     * @return list of validation errors, empty if valid
     */
    public List<ValidationError> validateXml() {
        String content = getEditorContent();
        var saxErrors = xmlService.validateText(content);

        // Convert SAXParseException to ValidationError
        return saxErrors.stream()
                .map(e -> new ValidationError(
                        e.getLineNumber(),
                        e.getColumnNumber(),
                        e.getMessage(),
                        "ERROR"
                ))
                .toList();
    }

    /**
     * Validates against Schematron.
     * @return list of Schematron validation errors, empty if valid
     */
    public List<SchematronService.SchematronValidationError> validateSchematron() {
        if (schematronFile != null) {
            try {
                String content = getEditorContent();
                return schematronService.validateXml(content, schematronFile);
            } catch (Exception e) {
                logger.warn("Schematron validation failed: {}", e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Updates cursor information - displayed in UnifiedEditorController status bar.
     */
    private void updateCursorInformation() {
        // Cursor info is handled by the UnifiedEditorController's status bar
        // via getCaretPosition() method
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

            // Detect linked XSD and Schematron files
            detectLinkedSchemaFiles();

            logger.info("Loaded file: {}", sourceFile.getName());
        } catch (IOException e) {
            logger.error("Failed to load file: {}", sourceFile, e);
        }
    }

    /**
     * Detects XSD and Schematron files linked in the XML.
     * Supports both local files and remote URLs (which are downloaded and cached).
     */
    private void detectLinkedSchemaFiles() {
        if (linkDetector == null) {
            linkDetector = new LinkedFileDetector();
        }

        boolean xsdLoaded = false;

        // First try local file detection
        List<LinkedFileInfo> links = linkDetector.detectXmlLinks(sourceFile);
        logger.debug("Detected {} linked files in {}", links.size(), sourceFile != null ? sourceFile.getName() : "null");

        for (LinkedFileInfo link : links) {
            logger.debug("  Link: {} - resolved={}, type={}, path={}",
                    link.referencePath(), link.isResolved(), link.getFileType(), link.resolvedFile());

            if (link.isResolved()) {
                if (link.getFileType() == UnifiedEditorFileType.XSD) {
                    logger.info("Found local XSD: {}", link.resolvedFile());
                    setXsdFile(link.resolvedFile());
                    xsdLoaded = true;
                } else if (link.getFileType() == UnifiedEditorFileType.SCHEMATRON) {
                    setSchematronFile(link.resolvedFile());
                }
            }
        }

        // If no local XSD was found, try to load remote schema via XmlService
        if (!xsdLoaded && sourceFile != null) {
            logger.debug("No local XSD found, trying remote schema loading...");
            loadRemoteSchemaAsync();
        }
    }

    /**
     * Loads a remote XSD schema asynchronously using the XmlService.
     * The XmlService handles downloading and caching of remote schemas.
     */
    private void loadRemoteSchemaAsync() {
        logger.debug("Starting async schema loading for: {}", sourceFile != null ? sourceFile.getName() : "null");

        // Run in background to avoid blocking UI
        Thread schemaLoader = new Thread(() -> {
            try {
                // Set the current XML file in the service
                xmlService.setCurrentXmlFile(sourceFile);
                logger.debug("Set current XML file in xmlService: {}", sourceFile);

                // Try to load schema from XML file (handles remote URLs)
                boolean loaded = xmlService.loadSchemaFromXMLFile();
                logger.debug("xmlService.loadSchemaFromXMLFile() returned: {}", loaded);

                if (loaded) {
                    File loadedXsd = xmlService.getCurrentXsdFile();
                    String remoteLocation = xmlService.getRemoteXsdLocation();
                    logger.debug("Loaded XSD file: {}, remote location: {}", loadedXsd, remoteLocation);

                    if (loadedXsd != null && loadedXsd.exists()) {
                        logger.info("Auto-loaded remote schema from: {} (cached at: {})",
                                remoteLocation, loadedXsd.getAbsolutePath());

                        // Update UI on JavaFX thread
                        Platform.runLater(() -> setXsdFile(loadedXsd));
                    } else {
                        logger.warn("Loaded XSD file is null or doesn't exist: {}", loadedXsd);
                    }
                } else {
                    logger.info("No remote schema found or loading failed for: {}", sourceFile.getName());
                }
            } catch (Exception e) {
                logger.warn("Failed to auto-load remote schema: {}", e.getMessage(), e);
            }
        }, "Schema-Loader-" + (sourceFile != null ? sourceFile.getName() : "unnamed"));

        schemaLoader.setDaemon(true);
        schemaLoader.start();
    }

    // ==================== File Operations ====================

    @Override
    public String getEditorContent() {
        return textEditor.getText();
    }

    @Override
    public void setEditorContent(String content) {
        textEditor.setText(content);
    }

    /**
     * Inserts text at the current cursor position in the editor.
     *
     * @param content the content to insert
     */
    public void insertAtCursor(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        CodeArea codeArea = textEditor.getCodeArea();
        int caretPosition = codeArea.getCaretPosition();
        codeArea.insertText(caretPosition, content);
        logger.debug("Inserted {} characters at position {}", content.length(), caretPosition);
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
            logger.info("Saved file: {}", sourceFile.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save file: {}", sourceFile, e);
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
            logger.info("Saved file as: {}", file.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save file as: {}", file, e);
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
                return "XML Error: " + errors.get(0).getMessage();
            }
        } catch (Exception e) {
            return "XML Error: " + e.getMessage();
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
            }
        } catch (Exception e) {
            logger.warn("Failed to format XML: {}", e.getMessage());
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

        return linkDetector.detectXmlLinks(sourceFile);
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
        Platform.runLater(() -> textEditor.getCodeArea().requestFocus());
    }

    // ==================== Linked File Management ====================

    /**
     * Sets the linked XSD file.
     */
    public void setXsdFile(File xsdFile) {
        this.xsdFile = xsdFile;
        if (xsdFile != null) {
            // Load XSD documentation asynchronously
            loadXsdDocumentation();
        }
    }

    /**
     * Gets the linked XSD file.
     */
    public File getXsdFile() {
        return xsdFile;
    }

    /**
     * Sets the linked Schematron file.
     */
    public void setSchematronFile(File schematronFile) {
        this.schematronFile = schematronFile;
    }

    /**
     * Gets the linked Schematron file.
     */
    public File getSchematronFile() {
        return schematronFile;
    }

    /**
     * Loads XSD documentation data asynchronously.
     */
    private void loadXsdDocumentation() {
        if (xsdFile == null || !xsdFile.exists()) {
            return;
        }

        Platform.runLater(() -> {
            try {
                // Load schema into the mutable schema provider (updates status line automatically)
                boolean loaded = schemaProvider.loadSchema(xsdFile);
                if (loaded) {
                    xsdDocumentationData = schemaProvider.getXsdDocumentationData();
                    logger.info("Loaded XSD documentation from: {}", xsdFile.getName());

                    // Refresh the status line
                    textEditor.refreshStatusLine();

                    // Refresh graphic view if visible
                    if (graphicTab.isSelected()) {
                        refreshGraphicView();
                    }

                    // Notify callback if registered
                    if (onXsdLoadedCallback != null) {
                        onXsdLoadedCallback.run();
                    }
                } else {
                    logger.warn("Failed to load XSD schema: {}", xsdFile.getName());
                }
            } catch (Exception e) {
                logger.warn("Failed to load XSD documentation: {}", e.getMessage());
            }
        });
    }

    /**
     * Sets a callback to be notified when the XSD is loaded.
     * This is used by the sidebar to update XSD-dependent panes.
     *
     * @param callback the callback to run when XSD is loaded
     */
    public void setOnXsdLoadedCallback(Runnable callback) {
        this.onXsdLoadedCallback = callback;
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
     * Gets the XSD documentation data.
     */
    public XsdDocumentationData getXsdDocumentationData() {
        return xsdDocumentationData;
    }

    /**
     * Gets the mutable schema provider.
     * This can be used to dynamically load XSD schemas.
     */
    public org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider getSchemaProvider() {
        return schemaProvider;
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
