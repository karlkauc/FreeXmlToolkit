package org.fxt.freexmltoolkit.controls.v2.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.CommandManager;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaModel;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Central context for the XSD editor.
 * Manages model, command history, selection, and editor state.
 *
 * @since 2.0
 */
public class XsdEditorContext {

    private static final Logger logger = LogManager.getLogger(XsdEditorContext.class);

    private final XsdSchemaModel model;
    private final CommandManager commandManager;
    private final SelectionModel selectionModel;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private boolean editMode = false;
    private boolean dirty = false;

    /**
     * Creates a new editor context for the given schema model.
     *
     * @param model the XSD schema model
     */
    public XsdEditorContext(XsdSchemaModel model) {
        this(model, null);
    }

    /**
     * Creates a new editor context for the given schema model with a shared selection model.
     *
     * @param model          the XSD schema model
     * @param selectionModel the selection model to use (if null, a new one will be created)
     */
    public XsdEditorContext(XsdSchemaModel model, SelectionModel selectionModel) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }

        this.model = model;
        this.commandManager = new CommandManager();
        this.selectionModel = selectionModel != null ? selectionModel : new SelectionModel();

        // Listen to command manager changes to update dirty flag
        commandManager.addPropertyChangeListener(evt -> {
            if ("canUndo".equals(evt.getPropertyName()) && Boolean.TRUE.equals(evt.getNewValue())) {
                setDirty(true);
            }
        });

        logger.info("XSD Editor Context initialized");
    }

    /**
     * Gets the schema model.
     *
     * @return the schema model
     */
    public XsdSchemaModel getModel() {
        return model;
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
}
