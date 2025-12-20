package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CommandManager;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlSerializer;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Central context for the XML Editor V2.
 *
 * <p>Coordinates all editor components and manages the XML document lifecycle.
 * This is the main entry point for interacting with the XML editor.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Document management (load, save, new)</li>
 *   <li>Command execution via CommandManager</li>
 *   <li>Selection tracking via SelectionModel</li>
 *   <li>Edit mode and dirty flag management</li>
 *   <li>PropertyChangeSupport for UI binding</li>
 * </ul>
 *
 * <p>PropertyChangeEvents fired:</p>
 * <ul>
 *   <li>"document" - Document changed (oldValue, newValue)</li>
 *   <li>"filePath" - File path changed (oldValue, newValue)</li>
 *   <li>"editMode" - Edit mode changed (oldValue, newValue)</li>
 *   <li>"dirty" - Dirty flag changed (oldValue, newValue)</li>
 *   <li>"canUndo" - Undo availability changed (oldValue, newValue)</li>
 *   <li>"canRedo" - Redo availability changed (oldValue, newValue)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlEditorContext context = new XmlEditorContext();
 *
 * // Load document
 * context.loadDocument("example.xml");
 *
 * // Listen for changes
 * context.addPropertyChangeListener("dirty", evt -> {
 *     updateSaveButton(context.isDirty());
 * });
 *
 * // Execute command
 * XmlElement element = new XmlElement("child");
 * AddElementCommand cmd = new AddElementCommand(context.getDocument().getRootElement(), element);
 * context.executeCommand(cmd);
 *
 * // Undo/Redo
 * context.undo();
 * context.redo();
 *
 * // Save
 * context.save();
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlEditorContext {

    /**
     * PropertyChangeSupport for firing events.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * The XML document being edited.
     */
    private XmlDocument document;

    /**
     * Command manager for undo/redo.
     */
    private final CommandManager commandManager;

    /**
     * Selection model for tracking selected nodes.
     */
    private final SelectionModel selectionModel;

    /**
     * XML parser for loading documents.
     */
    private final XmlParser parser;

    /**
     * XML serializer for saving documents.
     */
    private final XmlSerializer serializer;

    /**
     * File path of the currently loaded document (null if new document).
     */
    private String filePath;

    /**
     * Whether the editor is in edit mode (true) or read-only mode (false).
     */
    private boolean editMode = true;

    /**
     * Whether the document has unsaved changes.
     */
    private boolean dirty = false;

    /**
     * Schema provider for XSD-aware editing (can be null).
     */
    private XmlSchemaProvider schemaProvider;

    /**
     * Widget factory for type-aware editing controls.
     */
    private TypeAwareWidgetFactory widgetFactory;

    // ==================== Constructor ====================

    /**
     * Constructs a new XmlEditorContext with an empty document.
     */
    public XmlEditorContext() {
        this(new XmlDocument());
    }

    /**
     * Constructs a new XmlEditorContext with the specified document.
     *
     * @param document the initial document
     */
    public XmlEditorContext(XmlDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        this.document = document;
        this.commandManager = new CommandManager();
        this.selectionModel = new SelectionModel();
        this.parser = new XmlParser();
        this.serializer = new XmlSerializer();

        // Listen to command manager for dirty flag and undo/redo changes
        commandManager.addPropertyChangeListener("dirty", evt -> {
            setDirty((Boolean) evt.getNewValue());
        });

        commandManager.addPropertyChangeListener("canUndo", evt -> {
            pcs.firePropertyChange("canUndo", evt.getOldValue(), evt.getNewValue());
        });

        commandManager.addPropertyChangeListener("canRedo", evt -> {
            pcs.firePropertyChange("canRedo", evt.getOldValue(), evt.getNewValue());
        });
    }

    // ==================== Document Management ====================

    /**
     * Creates a new empty document.
     * Clears undo/redo history and selection.
     */
    public void newDocument() {
        XmlDocument oldDoc = this.document;
        this.document = new XmlDocument();
        this.filePath = null;

        commandManager.clear();
        selectionModel.clearSelection();
        setDirty(false);

        pcs.firePropertyChange("document", oldDoc, this.document);
        pcs.firePropertyChange("filePath", filePath, null);
    }

    /**
     * Loads an XML document from a file.
     *
     * @param filePath the file path to load
     * @throws IOException                 if loading fails
     * @throws XmlParser.XmlParseException if parsing fails
     */
    public void loadDocument(String filePath) throws IOException {
        XmlDocument newDoc = parser.parseFile(filePath);
        setDocument(newDoc, filePath);
    }

    /**
     * Loads an XML document from a string.
     *
     * @param xmlString the XML string to parse
     * @throws XmlParser.XmlParseException if parsing fails
     */
    public void loadDocumentFromString(String xmlString) {
        XmlDocument newDoc = parser.parse(xmlString);
        setDocument(newDoc, null);
    }

    /**
     * Sets the document and file path.
     *
     * @param document the new document
     * @param filePath the file path (null if new document)
     */
    private void setDocument(XmlDocument document, String filePath) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        XmlDocument oldDoc = this.document;
        String oldPath = this.filePath;

        this.document = document;
        this.filePath = filePath;

        commandManager.clear();
        selectionModel.clearSelection();
        setDirty(false);

        // Clear all table caches when loading a new document
        RepeatingElementsTable.clearAllCaches();

        pcs.firePropertyChange("document", oldDoc, this.document);
        pcs.firePropertyChange("filePath", oldPath, this.filePath);

        // Detect mixed content elements and notify listeners
        List<XmlElement> mixedContentElements = findMixedContentElements();
        if (!mixedContentElements.isEmpty()) {
            pcs.firePropertyChange("mixedContentDetected", null, mixedContentElements);
        }
    }

    /**
     * Finds all elements with mixed content (both text and child elements).
     *
     * @return list of elements with mixed content
     */
    public List<XmlElement> findMixedContentElements() {
        List<XmlElement> mixedElements = new ArrayList<>();
        XmlElement root = document.getRootElement();
        if (root != null) {
            findMixedContentRecursive(root, mixedElements);
        }
        return mixedElements;
    }

    /**
     * Recursively finds elements with mixed content.
     * Only considers non-whitespace text content as mixed content.
     *
     * @param element       the current element to check
     * @param mixedElements the list to add mixed content elements to
     */
    private void findMixedContentRecursive(XmlElement element, List<XmlElement> mixedElements) {
        if (element.hasNonWhitespaceTextContent() && element.hasElementChildren()) {
            mixedElements.add(element);
        }
        for (XmlElement child : element.getChildElements()) {
            findMixedContentRecursive(child, mixedElements);
        }
    }

    /**
     * Saves the document to its current file path.
     * Creates a timestamped backup.
     *
     * @throws IOException           if saving fails
     * @throws IllegalStateException if no file path is set
     */
    public void save() throws IOException {
        if (filePath == null) {
            throw new IllegalStateException("No file path set. Use saveAs() instead.");
        }

        serializer.saveToFile(document, filePath, true);
        commandManager.markAsSaved();
        setDirty(false);
    }

    /**
     * Saves the document to a new file path.
     *
     * @param filePath     the new file path
     * @param createBackup whether to create a backup if file exists
     * @throws IOException if saving fails
     */
    public void saveAs(String filePath, boolean createBackup) throws IOException {
        String oldPath = this.filePath;

        serializer.saveToFile(document, filePath, createBackup);
        this.filePath = filePath;

        commandManager.markAsSaved();
        setDirty(false);

        pcs.firePropertyChange("filePath", oldPath, this.filePath);
    }

    /**
     * Saves the document to a new file path (with backup).
     *
     * @param filePath the new file path
     * @throws IOException if saving fails
     */
    public void saveAs(String filePath) throws IOException {
        saveAs(filePath, true);
    }

    /**
     * Serializes the document to an XML string.
     *
     * @return the XML string
     */
    public String serializeToString() {
        return serializer.serialize(document);
    }

    // ==================== Command Execution ====================

    /**
     * Executes a command via the CommandManager.
     *
     * @param command the command to execute
     * @return true if executed successfully
     */
    public boolean executeCommand(org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand command) {
        if (!editMode) {
            throw new IllegalStateException("Cannot execute commands in read-only mode");
        }
        return commandManager.executeCommand(command);
    }

    /**
     * Undoes the last command.
     *
     * @return true if undo was successful
     */
    public boolean undo() {
        return commandManager.undo();
    }

    /**
     * Redoes the last undone command.
     *
     * @return true if redo was successful
     */
    public boolean redo() {
        return commandManager.redo();
    }

    /**
     * Checks if undo is available.
     *
     * @return true if can undo
     */
    public boolean canUndo() {
        return commandManager.canUndo();
    }

    /**
     * Checks if redo is available.
     *
     * @return true if can redo
     */
    public boolean canRedo() {
        return commandManager.canRedo();
    }

    /**
     * Clears undo/redo history.
     */
    public void clearHistory() {
        commandManager.clear();
    }

    // ==================== Getters and Setters ====================

    /**
     * Returns the current document.
     *
     * @return the XML document
     */
    public XmlDocument getDocument() {
        return document;
    }

    /**
     * Returns the command manager.
     *
     * @return the command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Returns the selection model.
     *
     * @return the selection model
     */
    public SelectionModel getSelectionModel() {
        return selectionModel;
    }

    /**
     * Returns the XML parser.
     *
     * @return the parser
     */
    public XmlParser getParser() {
        return parser;
    }

    /**
     * Returns the XML serializer.
     *
     * @return the serializer
     */
    public XmlSerializer getSerializer() {
        return serializer;
    }

    /**
     * Returns the file path of the current document.
     *
     * @return the file path, or null if new document
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Returns the file name (without path) of the current document.
     *
     * @return the file name, or "Untitled" if new document
     */
    public String getFileName() {
        if (filePath == null) {
            return "Untitled";
        }
        return Path.of(filePath).getFileName().toString();
    }

    /**
     * Checks if the editor is in edit mode.
     *
     * @return true if in edit mode
     */
    public boolean isEditMode() {
        return editMode;
    }

    /**
     * Sets the edit mode.
     *
     * @param editMode true for edit mode, false for read-only
     */
    public void setEditMode(boolean editMode) {
        boolean oldValue = this.editMode;
        this.editMode = editMode;
        pcs.firePropertyChange("editMode", oldValue, editMode);
    }

    /**
     * Checks if the document has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag.
     *
     * @param dirty true if document has unsaved changes
     */
    private void setDirty(boolean dirty) {
        boolean oldValue = this.dirty;
        this.dirty = dirty;
        pcs.firePropertyChange("dirty", oldValue, dirty);
    }

    /**
     * Manually marks the document as dirty.
     * Useful when model is modified outside of the command system.
     */
    public void markAsDirty() {
        setDirty(true);
    }

    // ==================== Schema Support ====================

    /**
     * Sets the XSD schema for schema-aware editing.
     *
     * @param xsdData the XSD documentation data
     */
    public void setSchema(XsdDocumentationData xsdData) {
        XsdSchemaAdapter adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(xsdData);
        setSchemaProvider(adapter);
    }

    /**
     * Sets the schema provider directly.
     *
     * @param schemaProvider the schema provider
     */
    public void setSchemaProvider(XmlSchemaProvider schemaProvider) {
        XmlSchemaProvider oldProvider = this.schemaProvider;
        this.schemaProvider = schemaProvider;
        this.widgetFactory = new TypeAwareWidgetFactory(schemaProvider);
        pcs.firePropertyChange("schemaProvider", oldProvider, schemaProvider);
    }

    /**
     * Returns the schema provider.
     *
     * @return the schema provider, or null if no schema is set
     */
    public XmlSchemaProvider getSchemaProvider() {
        return schemaProvider;
    }

    /**
     * Checks if a schema is available for schema-aware editing.
     *
     * @return true if schema is available
     */
    public boolean hasSchema() {
        return schemaProvider != null && schemaProvider.hasSchema();
    }

    /**
     * Returns the type-aware widget factory.
     *
     * @return the widget factory
     */
    public TypeAwareWidgetFactory getWidgetFactory() {
        if (widgetFactory == null) {
            widgetFactory = new TypeAwareWidgetFactory(schemaProvider);
        }
        return widgetFactory;
    }

    /**
     * Gets valid child element names for the given parent element.
     *
     * @param parentXPath the XPath of the parent element
     * @return list of valid child element names
     */
    public List<String> getValidChildElements(String parentXPath) {
        if (!hasSchema()) {
            return new ArrayList<>();
        }
        return schemaProvider.getValidChildElements(parentXPath);
    }

    /**
     * Gets valid attribute names for the given element.
     *
     * @param elementXPath the XPath of the element
     * @return list of valid attribute names
     */
    public List<String> getValidAttributes(String elementXPath) {
        if (!hasSchema()) {
            return new ArrayList<>();
        }
        return schemaProvider.getValidAttributes(elementXPath);
    }

    // ==================== PropertyChangeSupport Methods ====================

    /**
     * Adds a PropertyChangeListener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    // ==================== Utility Methods ====================

    /**
     * Returns a string representation of the editor context.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "XmlEditorContext{" +
                "fileName='" + getFileName() + '\'' +
                ", editMode=" + editMode +
                ", dirty=" + dirty +
                ", canUndo=" + canUndo() +
                ", canRedo=" + canRedo() +
                '}';
    }
}
