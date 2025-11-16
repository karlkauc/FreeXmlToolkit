package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypesListView;

/**
 * Tab showing a list of all SimpleTypes in the schema.
 * Provides overview, filtering, and quick actions.
 *
 * DUMMY IMPLEMENTATION - Phase 0
 * This shows placeholder content to visualize the structure.
 *
 * @since 2.0
 */
public class SimpleTypesListTab extends AbstractTypeEditorTab {

    private SimpleTypesListView listView;

    /**
     * Creates a new SimpleTypes list tab.
     */
    public SimpleTypesListTab() {
        super(null, "SimpleTypes List");
        initializeContent(); // Call after field initialization
    }

    @Override
    protected void initializeContent() {
        // DUMMY: Create placeholder content
        listView = new SimpleTypesListView();
        setContent(listView);

        // TODO Phase 4:
        // - Create TableView with columns: Name, Base Type, Facets, Usage Count
        // - Add filter/search field
        // - Add sort options
        // - Add preview panel (shows XSD for selected type)
        // - Add action buttons (Edit, Delete, Duplicate, Find Usage)
        // - Wire up double-click to open SimpleType tab
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
