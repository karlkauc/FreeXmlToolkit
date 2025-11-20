package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypesListView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

import java.util.function.Consumer;

/**
 * Tab showing a list of all SimpleTypes in the schema.
 * Provides overview, filtering, and quick actions.
 *
 * Phase 4 Implementation - Functional with schema integration
 *
 * @since 2.0
 */
public class SimpleTypesListTab extends AbstractTypeEditorTab {

    private static final Logger logger = LogManager.getLogger(SimpleTypesListTab.class);

    private final XsdSchema schema;
    private SimpleTypesListView listView;

    // Callbacks for actions
    private Consumer<XsdSimpleType> onEditTypeCallback;
    private Consumer<XsdSimpleType> onDuplicateTypeCallback;
    private Consumer<XsdSimpleType> onDeleteTypeCallback;
    private Consumer<XsdSimpleType> onFindUsageCallback;
    private Runnable onAddTypeCallback;

    /**
     * Creates a new SimpleTypes list tab.
     *
     * @param schema the schema to display types from
     */
    public SimpleTypesListTab(XsdSchema schema) {
        super(null, "SimpleTypes List");
        this.schema = schema;
        initializeContent(); // Call after field initialization
    }

    /**
     * Sets the callback for editing a type.
     *
     * @param callback the callback
     */
    public void setOnEditType(Consumer<XsdSimpleType> callback) {
        this.onEditTypeCallback = callback;
        if (listView != null) {
            listView.setOnEditType(callback);
        }
    }

    /**
     * Sets the callback for duplicating a type.
     *
     * @param callback the callback
     */
    public void setOnDuplicateType(Consumer<XsdSimpleType> callback) {
        this.onDuplicateTypeCallback = callback;
        if (listView != null) {
            listView.setOnDuplicateType(callback);
        }
    }

    /**
     * Sets the callback for deleting a type.
     *
     * @param callback the callback
     */
    public void setOnDeleteType(Consumer<XsdSimpleType> callback) {
        this.onDeleteTypeCallback = callback;
        if (listView != null) {
            listView.setOnDeleteType(callback);
        }
    }

    /**
     * Sets the callback for finding usage.
     *
     * @param callback the callback
     */
    public void setOnFindUsage(Consumer<XsdSimpleType> callback) {
        this.onFindUsageCallback = callback;
        if (listView != null) {
            listView.setOnFindUsage(callback);
        }
    }

    /**
     * Sets the callback for adding a new type.
     *
     * @param callback the callback
     */
    public void setOnAddType(Runnable callback) {
        this.onAddTypeCallback = callback;
        if (listView != null) {
            listView.setOnAddType(callback);
        }
    }

    @Override
    protected void initializeContent() {
        // Phase 4: Create functional list view with schema
        listView = new SimpleTypesListView(schema);

        // Wire up callbacks if they were set before initialization
        if (onEditTypeCallback != null) {
            listView.setOnEditType(onEditTypeCallback);
        }
        if (onDuplicateTypeCallback != null) {
            listView.setOnDuplicateType(onDuplicateTypeCallback);
        }
        if (onDeleteTypeCallback != null) {
            listView.setOnDeleteType(onDeleteTypeCallback);
        }
        if (onFindUsageCallback != null) {
            listView.setOnFindUsage(onFindUsageCallback);
        }
        if (onAddTypeCallback != null) {
            listView.setOnAddType(onAddTypeCallback);
        }

        setContent(listView);
        logger.debug("SimpleTypesListTab initialized with {} SimpleTypes", schema.getChildren().stream()
                .filter(child -> child instanceof XsdSimpleType).count());
    }

    @Override
    public boolean save() {
        // List tab doesn't have save functionality
        return true;
    }

    @Override
    public void discardChanges() {
        // List tab doesn't have unsaved changes to discard
    }

    @Override
    public void setDirty(boolean dirty) {
        // List tab can't be dirty
    }

    @Override
    public boolean isDirty() {
        return false;
    }
}
