package org.fxt.freexmltoolkit.controls.v2.editor;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.dialogs.TypeUsageDialog;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.AbstractTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageLocation;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.ComplexTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SchemaStatisticsTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypesListTab;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

import java.util.HashMap;
import java.util.List;
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

    private static final Logger logger = LogManager.getLogger(TypeEditorTabManager.class);

    private final TabPane tabPane;
    private final XsdSchema mainSchema;
    private final Map<String, AbstractTypeEditorTab> openTypeTabs;
    private SchemaStatisticsTab statisticsTab;

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
            logger.debug("ComplexType tab already open: {}", complexType.getName());
        } else {
            // Performance tracking
            long startTime = System.currentTimeMillis();

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

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Opened ComplexType tab '{}' in {}ms", complexType.getName(), duration);
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
            logger.debug("SimpleType tab already open: {}", simpleType.getName());
        } else {
            // Performance tracking
            long startTime = System.currentTimeMillis();

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

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Opened SimpleType tab '{}' in {}ms", simpleType.getName(), duration);
        }
    }

    /**
     * Creates a new ComplexType and opens it in the editor.
     * Prompts the user for a type name and validates uniqueness.
     */
    public void createNewComplexType() {
        TextInputDialog dialog = new TextInputDialog("NewComplexType");
        dialog.setTitle("Create New Complex Type");
        dialog.setHeaderText("Create New ComplexType");
        dialog.setContentText("Please enter a name for the new ComplexType:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(typeName -> {
            if (!typeName.trim().isEmpty()) {
                // Check if name already exists (across both SimpleTypes and ComplexTypes)
                if (typeNameExists(typeName)) {
                    showErrorAlert("Duplicate Name",
                        "A type with the name '" + typeName + "' already exists.",
                        "Please choose a different name.");
                    return;
                }

                // Create new ComplexType with user-provided name
                XsdComplexType newType = new XsdComplexType(typeName);
                // Auto-create a Sequence element to allow immediate element addition
                // This follows the most common XSD pattern and improves UX
                XsdSequence sequence = new XsdSequence();
                newType.addChild(sequence);
                mainSchema.addChild(newType);
                openComplexTypeTab(newType);

                logger.info("Created new ComplexType: {}", typeName);
            }
        });
    }

    /**
     * Creates a new SimpleType and opens it in the editor.
     * Prompts the user for a type name and validates uniqueness.
     */
    public void createNewSimpleType() {
        TextInputDialog dialog = new TextInputDialog("NewSimpleType");
        dialog.setTitle("Create New Simple Type");
        dialog.setHeaderText("Create New SimpleType");
        dialog.setContentText("Please enter a name for the new SimpleType:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(typeName -> {
            if (!typeName.trim().isEmpty()) {
                // Check if name already exists (across both SimpleTypes and ComplexTypes)
                if (typeNameExists(typeName)) {
                    showErrorAlert("Duplicate Name",
                        "A type with the name '" + typeName + "' already exists.",
                        "Please choose a different name.");
                    return;
                }

                // Create new SimpleType with user-provided name
                XsdSimpleType newType = new XsdSimpleType(typeName);
                mainSchema.addChild(newType);
                openSimpleTypeTab(newType);

                logger.info("Created new SimpleType: {}", typeName);
            }
        });
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
                // Check usage before deleting
                TypeUsageFinder finder = new TypeUsageFinder(mainSchema);
                List<TypeUsageLocation> usages = finder.findUsages(simpleType.getName());

                if (!usages.isEmpty()) {
                    // Type is used - show warning dialog
                    Alert warningAlert = new Alert(Alert.AlertType.WARNING);
                    warningAlert.setTitle("Type In Use");
                    warningAlert.setHeaderText("Cannot delete type '" + simpleType.getName() + "'");
                    warningAlert.setContentText("This type is used in " + usages.size() +
                            " location(s). Remove all usages before deleting.\n\n" +
                            "Click 'Show Usages' to see where this type is used.");

                    ButtonType showUsagesBtn = new ButtonType("Show Usages");
                    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    warningAlert.getButtonTypes().setAll(showUsagesBtn, cancelBtn);

                    Optional<ButtonType> result = warningAlert.showAndWait();
                    if (result.isPresent() && result.get() == showUsagesBtn) {
                        // Show the usage dialog
                        TypeUsageDialog usageDialog = new TypeUsageDialog(simpleType.getName(), usages);
                        usageDialog.showAndWait();
                    }
                    // Don't delete - type is still in use
                } else {
                    // Type is not used - confirm deletion
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("Confirm Deletion");
                    confirmAlert.setHeaderText("Delete type '" + simpleType.getName() + "'?");
                    confirmAlert.setContentText("This type is not used anywhere and can be safely deleted.");

                    Optional<ButtonType> result = confirmAlert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        mainSchema.removeChild(simpleType);
                        logger.info("Deleted SimpleType: {}", simpleType.getName());
                    }
                }
            });

            tab.setOnFindUsage(simpleType -> {
                // Find all usages and show dialog
                TypeUsageFinder finder = new TypeUsageFinder(mainSchema);
                List<TypeUsageLocation> usages = finder.findUsages(simpleType.getName());

                logger.debug("Found {} usages of type '{}'", usages.size(), simpleType.getName());

                TypeUsageDialog dialog = new TypeUsageDialog(simpleType.getName(), usages);
                dialog.setOnNavigateToNode(node -> {
                    // Navigate to the node in the schema tree
                    // This would need integration with the main schema view
                    logger.info("Navigate to node: {} ({})", node.getName(), node.getNodeType());
                });
                dialog.showAndWait();
            });

            tab.setOnAddType(() -> {
                // Phase 6: Input validation for new SimpleType name
                String newTypeName = promptForTypeName("NewSimpleType");

                if (newTypeName != null && !newTypeName.trim().isEmpty()) {
                    // Check if name already exists
                    if (typeNameExists(newTypeName)) {
                        showErrorAlert("Duplicate Name",
                            "A type with the name '" + newTypeName + "' already exists.",
                            "Please choose a different name.");
                        return;
                    }

                    // Create new SimpleType with user-provided name
                    XsdSimpleType newType = new XsdSimpleType();
                    newType.setName(newTypeName);
                    mainSchema.addChild(newType);
                    openSimpleTypeTab(newType);
                }
                // If cancelled or empty, do nothing
            });

            // Set close handler (list tab cannot be dirty, so no check needed)
            tab.setOnCloseRequest(e -> removeTab(tabId));

            tabPane.getTabs().add(tab);
            openTypeTabs.put(tabId, tab);
            tabPane.getSelectionModel().select(tab);
        }
    }

    /**
     * Opens the schema statistics tab.
     * Only one instance of this tab can be open (singleton).
     */
    public void openSchemaStatisticsTab() {
        if (statisticsTab != null && tabPane.getTabs().contains(statisticsTab)) {
            // Tab already open, just select it
            tabPane.getSelectionModel().select(statisticsTab);
            logger.debug("Statistics tab already open, selecting it");
        } else {
            // Performance tracking
            long startTime = System.currentTimeMillis();

            // Create new statistics tab
            statisticsTab = new SchemaStatisticsTab(mainSchema);

            // Set close handler
            statisticsTab.setOnClosed(e -> {
                statisticsTab = null;
                logger.debug("Statistics tab closed");
            });

            tabPane.getTabs().add(statisticsTab);
            tabPane.getSelectionModel().select(statisticsTab);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Opened Schema Statistics tab in {}ms", duration);
        }
    }

    /**
     * Gets the schema statistics tab if it's open.
     *
     * @return the statistics tab, or null if not open
     */
    public SchemaStatisticsTab getSchemaStatisticsTab() {
        return statisticsTab;
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

    /**
     * Prompts the user for a type name with an input dialog.
     * Phase 6: Input Validation
     *
     * @param defaultName the default name to suggest
     * @return the entered name, or null if cancelled
     */
    private String promptForTypeName(String defaultName) {
        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle("New SimpleType");
        dialog.setHeaderText("Create New SimpleType");
        dialog.setContentText("Please enter a name for the new SimpleType:");

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Checks if a type with the given name already exists in the schema.
     * Phase 6: Input Validation
     *
     * @param typeName the name to check
     * @return true if the name exists
     */
    private boolean typeNameExists(String typeName) {
        return mainSchema.getChildren().stream()
            .filter(node -> node instanceof XsdSimpleType || node instanceof XsdComplexType)
            .anyMatch(node -> typeName.equals(node.getName()));
    }

    /**
     * Shows an error alert to the user.
     * Phase 6: Error Handling
     *
     * @param title the alert title
     * @param header the alert header
     * @param content the alert content
     */
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
