package org.fxt.freexmltoolkit.controls.v2.editor;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.AbstractTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.ComplexTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypesListTab;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages tabs for the Type Editor.
 * Handles opening, closing, and switching between different type editor tabs.
 *
 * Features:
 * - Prevents duplicate tabs (same type can only be open once)
 * - Handles unsaved changes warnings
 * - Save/Discard/Cancel dialogs
 * - Tab lifecycle management
 *
 * @since 2.0
 */
public class TypeEditorTabManager {

    private final TabPane tabPane;
    private final XsdSchema mainSchema;
    private final Map<String, AbstractTypeEditorTab> openTypeTabs;

    /**
     * Creates a new TypeEditorTabManager.
     *
     * @param tabPane the tab pane to manage
     * @param mainSchema the main XSD schema (for type editing)
     */
    public TypeEditorTabManager(TabPane tabPane, XsdSchema mainSchema) {
        this.tabPane = tabPane;
        this.mainSchema = mainSchema;
        this.openTypeTabs = new HashMap<>();
    }

    /**
     * Opens a ComplexType in a new tab.
     * If the type is already open, switches to that tab.
     *
     * @param complexType the complex type to edit
     */
    public void openComplexTypeTab(XsdComplexType complexType) {
        String typeId = complexType.getId();

        if (openTypeTabs.containsKey(typeId)) {
            // Tab already open, just select it
            tabPane.getSelectionModel().select(openTypeTabs.get(typeId));
        } else {
            // Create new tab with main schema
            ComplexTypeEditorTab tab = new ComplexTypeEditorTab(complexType, mainSchema);

            // Set close handler with unsaved changes check
            tab.setOnCloseRequest(e -> {
                if (!handleTabCloseRequest(tab)) {
                    e.consume(); // Cancel close
                }
            });

            tabPane.getTabs().add(tab);
            openTypeTabs.put(typeId, tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    /**
     * Opens a SimpleType in a new tab.
     * If the type is already open, switches to that tab.
     *
     * @param simpleType the simple type to edit
     */
    public void openSimpleTypeTab(XsdSimpleType simpleType) {
        String typeId = simpleType.getId();

        if (openTypeTabs.containsKey(typeId)) {
            // Tab already open, just select it
            tabPane.getSelectionModel().select(openTypeTabs.get(typeId));
        } else {
            // Create new tab with main schema
            SimpleTypeEditorTab tab = new SimpleTypeEditorTab(simpleType, mainSchema);

            // Set close handler with unsaved changes check
            tab.setOnCloseRequest(e -> {
                if (!handleTabCloseRequest(tab)) {
                    e.consume(); // Cancel close
                }
            });

            tabPane.getTabs().add(tab);
            openTypeTabs.put(typeId, tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    /**
     * Opens the SimpleTypes list tab.
     * Only one instance of this tab can be open.
     */
    public void openSimpleTypesListTab() {
        String tabId = "simple-types-list";

        if (openTypeTabs.containsKey(tabId)) {
            // Tab already open, just select it
            tabPane.getSelectionModel().select(openTypeTabs.get(tabId));
        } else {
            // Create new tab
            SimpleTypesListTab tab = new SimpleTypesListTab(mainSchema);

            // Wire up callbacks for list actions
            tab.setOnEditType(this::openSimpleTypeTab);

            tab.setOnDuplicateType(simpleType -> {
                // Duplicate the type with a suffix
                XsdSimpleType duplicate = (XsdSimpleType) simpleType.deepCopy("_copy");
                mainSchema.addChild(duplicate);
                openSimpleTypeTab(duplicate);
            });

            tab.setOnDeleteType(simpleType -> {
                // TODO Phase 5: Check usage before deleting
                mainSchema.removeChild(simpleType);
                // Refresh the list tab (it will auto-update from schema changes)
            });

            tab.setOnFindUsage(simpleType -> {
                // TODO Phase 5: Implement usage finder
                System.out.println("Find usage for: " + simpleType.getName());
            });

            tab.setOnAddType(() -> {
                // Create new SimpleType with default name
                XsdSimpleType newType = new XsdSimpleType();
                newType.setName("NewSimpleType");
                mainSchema.addChild(newType);
                openSimpleTypeTab(newType);
            });

            // Set close handler (list tab cannot be dirty, so no check needed)
            tab.setOnCloseRequest(e -> removeTab(tabId));

            tabPane.getTabs().add(tab);
            openTypeTabs.put(tabId, tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    /**
     * Handles tab close request.
     * Checks for unsaved changes and shows confirmation dialog if needed.
     *
     * @param tab the tab to close
     * @return true if tab can be closed, false to cancel close
     */
    private boolean handleTabCloseRequest(AbstractTypeEditorTab tab) {
        if (tab.isDirty()) {
            // Show confirmation dialog
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("Tab has unsaved changes");
            alert.setContentText("Do you want to save changes before closing?");

            ButtonType saveBtn = new ButtonType("Save");
            ButtonType discardBtn = new ButtonType("Discard");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveBtn) {
                    // Try to save
                    boolean saved = tab.save();
                    if (saved) {
                        removeTab(tab);
                        return true;
                    } else {
                        // Save failed, don't close
                        return false;
                    }
                } else if (result.get() == discardBtn) {
                    // Discard changes and close
                    tab.discardChanges();
                    removeTab(tab);
                    return true;
                } else {
                    // Cancel - don't close
                    return false;
                }
            }
            return false; // Dialog closed without selection
        } else {
            // No unsaved changes, close immediately
            removeTab(tab);
            return true;
        }
    }

    /**
     * Removes a tab from the tracking map.
     *
     * @param tab the tab to remove
     */
    private void removeTab(AbstractTypeEditorTab tab) {
        // Find and remove by tab reference
        openTypeTabs.entrySet().removeIf(entry -> entry.getValue() == tab);
    }

    /**
     * Removes a tab from the tracking map by ID.
     *
     * @param tabId the tab ID
     */
    private void removeTab(String tabId) {
        openTypeTabs.remove(tabId);
    }

    /**
     * Closes all type editor tabs.
     * Checks each tab for unsaved changes before closing.
     *
     * @return true if all tabs were closed, false if user cancelled
     */
    public boolean closeAllTypeTabs() {
        // Create a copy to avoid ConcurrentModificationException
        var tabsCopy = new HashMap<>(openTypeTabs);

        for (AbstractTypeEditorTab tab : tabsCopy.values()) {
            if (!handleTabCloseRequest(tab)) {
                // User cancelled, stop closing
                return false;
            }
        }

        // All tabs closed successfully
        openTypeTabs.clear();
        tabPane.getTabs().clear();
        return true;
    }

    /**
     * Saves all open tabs.
     *
     * @return true if all saves were successful
     */
    public boolean saveAllTabs() {
        for (AbstractTypeEditorTab tab : openTypeTabs.values()) {
            if (tab.isDirty()) {
                if (!tab.save()) {
                    // Save failed for this tab
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the number of currently open type tabs.
     *
     * @return number of open tabs
     */
    public int getOpenTabCount() {
        return openTypeTabs.size();
    }

    /**
     * Checks if a specific type is currently open.
     *
     * @param typeId the type ID
     * @return true if the type is open in a tab
     */
    public boolean isTypeOpen(String typeId) {
        return openTypeTabs.containsKey(typeId);
    }
}
