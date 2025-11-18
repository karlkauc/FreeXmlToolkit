package org.fxt.freexmltoolkit.controls.v2.editor.core;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.IntelliSenseEngine;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/**
 * Central context for the XML editor.
 * Coordinates all editor components and provides access to shared state.
 *
 * <p>Uses PropertyChangeSupport for observable properties to enable reactive UI updates.</p>
 */
public class EditorContext {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Core components
    private final CodeArea codeArea;
    private final EditorEventBus eventBus;
    private final XmlSchemaProvider schemaProvider;

    // Editor state
    private EditorMode currentMode;
    private boolean dirty;
    private String documentUri;

    // IntelliSense (will be set after construction)
    private IntelliSenseEngine intelliSenseEngine;

    /**
     * Creates a new editor context.
     *
     * @param codeArea       the code area component
     * @param eventBus       the event bus
     * @param schemaProvider the XSD schema provider
     */
    public EditorContext(CodeArea codeArea, EditorEventBus eventBus, XmlSchemaProvider schemaProvider) {
        this.codeArea = Objects.requireNonNull(codeArea, "CodeArea cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus cannot be null");
        this.schemaProvider = Objects.requireNonNull(schemaProvider, "SchemaProvider cannot be null");
        this.currentMode = EditorMode.XML_WITHOUT_XSD;
        this.dirty = false;
    }

    // ==================== Core Component Access ====================

    /**
     * Gets the code area component.
     *
     * @return the code area
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Gets the event bus.
     *
     * @return the event bus
     */
    public EditorEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the XSD schema provider.
     *
     * @return the schema provider
     */
    public XmlSchemaProvider getSchemaProvider() {
        return schemaProvider;
    }

    /**
     * Gets the IntelliSense engine.
     *
     * @return the IntelliSense engine, or null if not yet initialized
     */
    public IntelliSenseEngine getIntelliSenseEngine() {
        return intelliSenseEngine;
    }

    /**
     * Sets the IntelliSense engine.
     * Called during editor initialization.
     *
     * @param intelliSenseEngine the IntelliSense engine
     */
    public void setIntelliSenseEngine(IntelliSenseEngine intelliSenseEngine) {
        this.intelliSenseEngine = intelliSenseEngine;
    }

    // ==================== Editor State ====================

    /**
     * Gets the current editor mode.
     *
     * @return the current mode
     */
    public EditorMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Sets the editor mode.
     * Fires a MODE_CHANGED event.
     *
     * @param newMode the new mode
     */
    public void setCurrentMode(EditorMode newMode) {
        Objects.requireNonNull(newMode, "Mode cannot be null");
        if (this.currentMode != newMode) {
            EditorMode oldMode = this.currentMode;
            this.currentMode = newMode;
            pcs.firePropertyChange("currentMode", oldMode, newMode);
            eventBus.publish(new EditorEvent.ModeChangedEvent(oldMode, newMode));
        }
    }

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return true if dirty, false otherwise
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag.
     *
     * @param dirty the new dirty state
     */
    public void setDirty(boolean dirty) {
        if (this.dirty != dirty) {
            boolean oldDirty = this.dirty;
            this.dirty = dirty;
            pcs.firePropertyChange("dirty", oldDirty, dirty);
        }
    }

    /**
     * Gets the document URI.
     *
     * @return the document URI, or null if not set
     */
    public String getDocumentUri() {
        return documentUri;
    }

    /**
     * Sets the document URI.
     *
     * @param documentUri the document URI
     */
    public void setDocumentUri(String documentUri) {
        String oldUri = this.documentUri;
        this.documentUri = documentUri;
        pcs.firePropertyChange("documentUri", oldUri, documentUri);
    }

    // ==================== Convenience Methods ====================

    /**
     * Gets the current text from the code area.
     *
     * @return the current text
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Gets the current caret position.
     *
     * @return the caret position
     */
    public int getCaretPosition() {
        return codeArea.getCaretPosition();
    }

    /**
     * Gets the current XML context at the caret position.
     * This is a convenience method that delegates to the IntelliSense engine.
     *
     * @return the current XML context, or null if IntelliSense is not initialized
     */
    public XmlContext getCurrentXmlContext() {
        if (intelliSenseEngine != null) {
            return intelliSenseEngine.analyzeContext(getText(), getCaretPosition());
        }
        return null;
    }

    /**
     * Checks if an XSD schema is currently loaded.
     *
     * @return true if schema is available, false otherwise
     */
    public boolean hasSchema() {
        return schemaProvider.hasSchema();
    }

    // ==================== PropertyChangeSupport ====================

    /**
     * Adds a property change listener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a property change listener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a property change listener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    @Override
    public String toString() {
        return "EditorContext{" +
                "mode=" + currentMode +
                ", dirty=" + dirty +
                ", hasSchema=" + hasSchema() +
                ", documentUri='" + documentUri + '\'' +
                '}';
    }
}
