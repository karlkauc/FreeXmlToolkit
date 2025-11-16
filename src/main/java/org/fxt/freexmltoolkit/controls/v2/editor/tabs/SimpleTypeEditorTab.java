package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypeEditorView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Tab for editing a SimpleType.
 * Shows panels for General, Restriction, List, Union, and Annotation.
 *
 * Phase 3 Implementation - Real panels with model integration
 *
 * @since 2.0
 */
public class SimpleTypeEditorTab extends AbstractTypeEditorTab {

    private static final Logger logger = LogManager.getLogger(SimpleTypeEditorTab.class);

    private final XsdSimpleType simpleType;
    private final XsdSchema mainSchema;
    private final XsdEditorContext editorContext;
    private SimpleTypeEditorView editorView;

    /**
     * Creates a new SimpleType editor tab.
     *
     * @param simpleType the simple type to edit
     * @param mainSchema the main schema (for context)
     */
    public SimpleTypeEditorTab(XsdSimpleType simpleType, XsdSchema mainSchema) {
        super(simpleType, "SimpleType: " + simpleType.getName());
        this.simpleType = simpleType;
        this.mainSchema = mainSchema;
        this.editorContext = new XsdEditorContext(mainSchema);
        initializeContent(); // Call after field initialization
    }

    @Override
    protected void initializeContent() {
        // Create real editor view with editor context
        editorView = new SimpleTypeEditorView(simpleType, editorContext);
        setContent(editorView);

        // Setup change tracking
        editorView.setOnChangeCallback(() -> setDirty(true));
    }

    /**
     * Gets the SimpleType being edited.
     *
     * @return the simple type
     */
    public XsdSimpleType getSimpleType() {
        return simpleType;
    }

    @Override
    public boolean save() {
        try {
            // For SimpleType, changes are applied directly to the model
            // (unlike ComplexType which uses a virtual schema)
            // So save just means: accept all changes and clear dirty flag

            setDirty(false);
            logger.info("SimpleType saved successfully: {}", simpleType.getName());
            return true;

        } catch (Exception e) {
            logger.error("Error saving SimpleType: {}", simpleType.getName(), e);
            return false;
        }
    }

    @Override
    public void discardChanges() {
        try {
            // Discard changes by recreating the view
            // This will reload all data from the model's current state
            // (assuming model is reloaded from original source externally)

            editorView = new SimpleTypeEditorView(simpleType, editorContext);
            setContent(editorView);
            editorView.setOnChangeCallback(() -> setDirty(true));

            setDirty(false);
            logger.info("SimpleType changes discarded: {}", simpleType.getName());

        } catch (Exception e) {
            logger.error("Error discarding changes for SimpleType: {}", simpleType.getName(), e);
        }
    }
}
