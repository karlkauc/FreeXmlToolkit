package org.fxt.freexmltoolkit.controls.v2.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.CommandManager;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.IncludeSourceInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central context for the XSD editor.
 * Manages model, command history, selection, and editor state.
 *
 * @since 2.0
 */
public class XsdEditorContext {

    private static final Logger logger = LogManager.getLogger(XsdEditorContext.class);

    private final XsdSchema schema;
    private final CommandManager commandManager;
    private final SelectionModel selectionModel;
    private final XsdClipboard clipboard;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private boolean editMode = true;  // Default to true to enable editing
    private boolean dirty = false;

    // Per-file dirty tracking for multi-file XSD schemas
    private final Map<Path, Boolean> fileDirtyMap = new HashMap<>();

    /**
     * Creates a new editor context for the given schema.
     *
     * @param schema the XSD schema
     */
    public XsdEditorContext(XsdSchema schema) {
        this(schema, null);
    }

    /**
     * Creates a new editor context for the given schema with a shared selection model.
     *
     * @param schema         the XSD schema
     * @param selectionModel the selection model to use (if null, a new one will be created)
     */
    public XsdEditorContext(XsdSchema schema, SelectionModel selectionModel) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        this.schema = schema;
        this.commandManager = new CommandManager();
        this.selectionModel = selectionModel != null ? selectionModel : new SelectionModel();
        this.clipboard = new XsdClipboard();

        // Listen to command manager changes to update dirty flag
        commandManager.addPropertyChangeListener(evt -> {
            if ("canUndo".equals(evt.getPropertyName()) && Boolean.TRUE.equals(evt.getNewValue())) {
                setDirty(true);
            }
        });

        logger.info("XSD Editor Context initialized");
    }

    /**
     * Gets the XSD schema.
     *
     * @return the schema
     */
    public XsdSchema getSchema() {
        return schema;
    }

    /**
     * Gets the command manager.
     *
     * @return the command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Gets the selection model.
     *
     * @return the selection model
     */
    public SelectionModel getSelectionModel() {
        return selectionModel;
    }

    /**
     * Gets the clipboard.
     *
     * @return the clipboard
     */
    public XsdClipboard getClipboard() {
        return clipboard;
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
     * @param editMode true to enable edit mode
     */
    public void setEditMode(boolean editMode) {
        boolean oldValue = this.editMode;
        this.editMode = editMode;
        propertyChangeSupport.firePropertyChange("editMode", oldValue, editMode);
        logger.debug("Edit mode: {}", editMode);
    }

    /**
     * Checks if the model has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag.
     *
     * @param dirty true if model has unsaved changes
     */
    public void setDirty(boolean dirty) {
        boolean oldValue = this.dirty;
        this.dirty = dirty;
        propertyChangeSupport.firePropertyChange("dirty", oldValue, dirty);
        logger.debug("Dirty flag: {}", dirty);
    }

    /**
     * Resets the dirty flag (called after save).
     */
    public void resetDirty() {
        setDirty(false);
    }

    /**
     * Adds a property change listener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Adds a property change listener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a property change listener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    // Per-file dirty tracking methods

    /**
     * Sets the dirty flag for a specific file.
     * Used for multi-file XSD schemas where each file tracks its own changes.
     *
     * @param file  the file path
     * @param dirty true if the file has unsaved changes
     */
    public void setFileDirty(Path file, boolean dirty) {
        if (file != null) {
            fileDirtyMap.put(file, dirty);
            updateOverallDirty();
            logger.debug("File dirty: {} = {}", file.getFileName(), dirty);
        }
    }

    /**
     * Checks if a specific file has unsaved changes.
     *
     * @param file the file path
     * @return true if the file is dirty, false otherwise
     */
    public boolean isFileDirty(Path file) {
        return file != null && fileDirtyMap.getOrDefault(file, false);
    }

    /**
     * Gets the set of files that have unsaved changes.
     *
     * @return set of dirty file paths
     */
    public Set<Path> getDirtyFiles() {
        return fileDirtyMap.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Clears the dirty flag for a specific file.
     * Called after successfully saving the file.
     *
     * @param file the file path
     */
    public void clearFileDirty(Path file) {
        if (file != null) {
            fileDirtyMap.put(file, false);
            updateOverallDirty();
            logger.debug("Cleared dirty flag for: {}", file.getFileName());
        }
    }

    /**
     * Clears all per-file dirty flags.
     * Called after saving all files.
     */
    public void clearAllFileDirty() {
        fileDirtyMap.clear();
        updateOverallDirty();
        logger.debug("Cleared all per-file dirty flags");
    }

    /**
     * Gets an unmodifiable view of the per-file dirty map.
     *
     * @return unmodifiable map of file paths to dirty flags
     */
    public Map<Path, Boolean> getFileDirtyMap() {
        return Collections.unmodifiableMap(fileDirtyMap);
    }

    /**
     * Updates the overall dirty flag based on per-file dirty states.
     */
    private void updateOverallDirty() {
        boolean anyDirty = fileDirtyMap.values().stream().anyMatch(Boolean::booleanValue);
        if (anyDirty != dirty) {
            setDirty(anyDirty);
        }
    }

    /**
     * Marks the file containing the given node as dirty.
     * This method automatically determines the source file from the node's
     * {@link IncludeSourceInfo} and marks that file as dirty.
     * <p>
     * If the node doesn't have source info or the source file cannot be determined,
     * the main schema file is marked as dirty as a fallback.
     *
     * @param node the node that was modified
     */
    public void markNodeDirty(XsdNode node) {
        Path sourceFile = getNodeSourceFile(node);
        String nodeDesc = (node != null)
                ? (node.getName() != null ? node.getName() : node.getId().toString())
                : "(null node)";

        if (sourceFile != null) {
            setFileDirty(sourceFile, true);
            logger.debug("Marked file dirty from node: {} -> {}", nodeDesc, sourceFile.getFileName());
        } else {
            // Fallback: mark main schema dirty
            Path mainPath = schema.getMainSchemaPath();
            if (mainPath != null) {
                setFileDirty(mainPath, true);
                logger.debug("Marked main schema dirty (fallback) from node: {}", nodeDesc);
            } else {
                // If no main path, just set global dirty flag
                setDirty(true);
                logger.debug("Set global dirty flag (no path available) from node: {}", nodeDesc);
            }
        }
    }

    /**
     * Gets the source file path for a node.
     * Uses the node's {@link IncludeSourceInfo} if available,
     * otherwise returns the main schema path.
     *
     * @param node the node to get the source file for
     * @return the source file path, or null if not determinable
     */
    private Path getNodeSourceFile(XsdNode node) {
        if (node == null) {
            return schema.getMainSchemaPath();
        }

        IncludeSourceInfo sourceInfo = node.getSourceInfo();
        if (sourceInfo != null && sourceInfo.getSourceFile() != null) {
            return sourceInfo.getSourceFile();
        }

        // Fallback to main schema path
        return schema.getMainSchemaPath();
    }
}
