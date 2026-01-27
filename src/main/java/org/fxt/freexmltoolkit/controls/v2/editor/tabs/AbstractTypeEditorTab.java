package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.scene.control.Tab;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Abstract base class for all Type Editor tabs.
 * Provides common functionality for ComplexType, SimpleType, and List tabs.
 *
 * Features:
 * - Dirty tracking (unsaved changes)
 * - Tab title updates with "*" indicator
 * - Save/Discard abstract methods
 * - Type node reference
 * - Post-save callback for file persistence
 *
 * @since 2.0
 */
public abstract class AbstractTypeEditorTab extends Tab {

    /** Flag indicating if the tab has unsaved changes. */
    protected boolean isDirty = false;
    /** The type node being edited. */
    protected final XsdNode typeNode;
    private final String originalTitle;
    /** Callback invoked after successful save (for file persistence). */
    private Runnable onPostSaveCallback;

    /**
     * Creates a new Type Editor tab.
     *
     * @param typeNode the type node being edited (can be null for list tabs)
     * @param title the tab title
     */
    protected AbstractTypeEditorTab(XsdNode typeNode, String title) {
        super(title);
        this.typeNode = typeNode;
        this.originalTitle = title;
        setClosable(true);

        // Note: initializeContent() is called by subclasses after field initialization
    }

    /**
     * Initializes the tab content.
     * To be implemented by subclasses.
     *
     * DUMMY: Abstract method for phase 1
     */
    protected abstract void initializeContent();

    /**
     * Marks this tab as dirty (has unsaved changes).
     * Updates tab title to show "*" indicator when dirty.
     *
     * @param dirty true if tab has unsaved changes
     */
    public void setDirty(boolean dirty) {
        boolean wasDirty = this.isDirty;
        this.isDirty = dirty;

        // Update tab title with "*" indicator
        if (dirty && !wasDirty) {
            // Becoming dirty: add "*"
            setText(originalTitle + "*");
        } else if (!dirty && wasDirty) {
            // No longer dirty: remove "*"
            setText(originalTitle);
        }
    }

    /**
     * Checks if this tab has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Saves changes to the underlying model.
     * Subclasses must implement this method to perform actual save logic.
     * Should call setDirty(false) upon successful save.
     *
     * @return true if save was successful, false otherwise
     */
    public abstract boolean save();

    /**
     * Discards all unsaved changes.
     * Subclasses must implement this method to revert to saved state.
     * Should call setDirty(false) after discarding.
     */
    public abstract void discardChanges();

    /**
     * Gets the type node being edited.
     *
     * @return the type node, or null for list tabs
     */
    public XsdNode getTypeNode() {
        return typeNode;
    }

    /**
     * Sets the callback to be invoked after a successful save.
     * This allows the TypeEditorTabManager to trigger file persistence.
     *
     * @param callback the callback to run after save
     */
    public void setOnPostSaveCallback(Runnable callback) {
        this.onPostSaveCallback = callback;
    }

    /**
     * Invokes the post-save callback if set.
     * Should be called by subclasses after successful save operations.
     */
    protected void invokePostSaveCallback() {
        if (onPostSaveCallback != null) {
            onPostSaveCallback.run();
        }
    }
}
