package org.fxt.freexmltoolkit.controls.unified;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages tabs for the Unified Editor.
 * Handles opening, closing, and switching between different editor tabs.
 *
 * Features:
 * - Prevents duplicate tabs (same file can only be open once)
 * - Handles unsaved changes warnings
 * - Save/Discard/Cancel dialogs
 * - Tab lifecycle management
 * - File type detection for tab creation
 *
 * @since 2.0
 */
public class UnifiedEditorTabManager {

    private static final Logger logger = LogManager.getLogger(UnifiedEditorTabManager.class);

    private final TabPane tabPane;
    private final Map<String, AbstractUnifiedEditorTab> openTabs;

    /**
     * Creates a new UnifiedEditorTabManager.
     *
     * @param tabPane the tab pane to manage
     */
    public UnifiedEditorTabManager(TabPane tabPane) {
        this.tabPane = tabPane;
        this.openTabs = new HashMap<>();
    }

    /**
     * Opens a file in the appropriate editor tab.
     * If the file is already open, switches to that tab.
     *
     * @param file the file to open
     * @return the created or existing tab, or null if file type is unsupported
     */
    public AbstractUnifiedEditorTab openFile(File file) {
        if (file == null) {
            return createNewTab(UnifiedEditorFileType.XML);
        }

        String tabId = file.getAbsolutePath();

        // Check if already open
        if (openTabs.containsKey(tabId)) {
            AbstractUnifiedEditorTab existingTab = openTabs.get(tabId);
            tabPane.getSelectionModel().select(existingTab);
            logger.debug("File already open, switching to tab: {}", file.getName());
            return existingTab;
        }

        // Detect file type and create appropriate tab
        UnifiedEditorFileType fileType = UnifiedEditorFileType.fromFile(file);
        AbstractUnifiedEditorTab tab = createTab(file, fileType);

        if (tab != null) {
            registerTab(tabId, tab);
            tabPane.getSelectionModel().select(tab);
            logger.info("Opened file: {} (type: {})", file.getName(), fileType);
        }

        return tab;
    }

    /**
     * Creates a new unsaved tab of the specified type.
     *
     * @param fileType the type of file to create
     * @return the created tab
     */
    public AbstractUnifiedEditorTab createNewTab(UnifiedEditorFileType fileType) {
        AbstractUnifiedEditorTab tab = createTab(null, fileType);

        if (tab != null) {
            String tabId = tab.getTabId();
            registerTab(tabId, tab);
            tabPane.getSelectionModel().select(tab);
            logger.info("Created new {} tab", fileType);
        }

        return tab;
    }

    /**
     * Creates a tab for the given file and type.
     */
    private AbstractUnifiedEditorTab createTab(File file, UnifiedEditorFileType fileType) {
        long startTime = System.currentTimeMillis();

        AbstractUnifiedEditorTab tab = switch (fileType) {
            case XML -> new XmlUnifiedTab(file);
            case XSD -> new XsdUnifiedTab(file);
            case XSLT -> new XsltUnifiedTab(file);
            case SCHEMATRON -> new SchematronUnifiedTab(file);
        };

        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Created {} tab in {}ms", fileType, duration);

        return tab;
    }

    /**
     * Registers a tab with close handler.
     */
    private void registerTab(String tabId, AbstractUnifiedEditorTab tab) {
        // Set close handler with unsaved changes check
        tab.setOnCloseRequest(e -> {
            if (!handleTabCloseRequest(tab)) {
                e.consume(); // Cancel close
            } else {
                openTabs.remove(tabId);
            }
        });

        tabPane.getTabs().add(tab);
        openTabs.put(tabId, tab);
    }

    /**
     * Handles a tab close request with unsaved changes dialog.
     *
     * @param tab the tab being closed
     * @return true if the tab should be closed, false to cancel
     */
    private boolean handleTabCloseRequest(AbstractUnifiedEditorTab tab) {
        if (!tab.isDirty()) {
            return true;
        }

        // Show save/discard/cancel dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Save changes to " + tab.getText().replace(" *", "") + "?");
        alert.setContentText("Your changes will be lost if you don't save them.");

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discardButton = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == saveButton) {
                boolean saved = tab.save();
                if (!saved) {
                    // Save failed, show error and cancel close
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Save Failed");
                    errorAlert.setHeaderText("Could not save file");
                    errorAlert.setContentText("The file could not be saved. Please try again.");
                    errorAlert.showAndWait();
                    return false;
                }
                return true;
            } else if (result.get() == discardButton) {
                return true;
            }
        }

        // Cancel or closed dialog
        return false;
    }

    /**
     * Closes a specific tab.
     *
     * @param tab the tab to close
     * @return true if the tab was closed, false if cancelled
     */
    public boolean closeTab(AbstractUnifiedEditorTab tab) {
        if (tab == null) {
            return false;
        }

        if (handleTabCloseRequest(tab)) {
            tabPane.getTabs().remove(tab);
            openTabs.remove(tab.getTabId());
            return true;
        }

        return false;
    }

    /**
     * Closes the currently selected tab.
     *
     * @return true if the tab was closed, false if cancelled
     */
    public boolean closeCurrentTab() {
        AbstractUnifiedEditorTab currentTab = getCurrentTab();
        return currentTab != null && closeTab(currentTab);
    }

    /**
     * Closes all tabs, checking for unsaved changes.
     *
     * @return true if all tabs were closed, false if any close was cancelled
     */
    public boolean closeAllTabs() {
        // Process in reverse order to avoid index issues
        List<AbstractUnifiedEditorTab> tabs = openTabs.values().stream().toList();

        for (AbstractUnifiedEditorTab tab : tabs) {
            if (!closeTab(tab)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Saves the currently selected tab.
     *
     * @return true if save was successful or no tab selected
     */
    public boolean saveCurrentTab() {
        AbstractUnifiedEditorTab currentTab = getCurrentTab();
        return currentTab == null || currentTab.save();
    }

    /**
     * Saves all dirty tabs.
     *
     * @return true if all saves were successful
     */
    public boolean saveAllTabs() {
        boolean allSaved = true;

        for (AbstractUnifiedEditorTab tab : openTabs.values()) {
            if (tab.isDirty()) {
                if (!tab.save()) {
                    allSaved = false;
                    logger.warn("Failed to save tab: {}", tab.getText());
                }
            }
        }

        return allSaved;
    }

    /**
     * Checks if any tab has unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return openTabs.values().stream().anyMatch(AbstractUnifiedEditorTab::isDirty);
    }

    /**
     * Gets all tabs with unsaved changes.
     *
     * @return list of dirty tabs
     */
    public List<AbstractUnifiedEditorTab> getDirtyTabs() {
        return openTabs.values().stream()
                .filter(AbstractUnifiedEditorTab::isDirty)
                .collect(Collectors.toList());
    }

    /**
     * Gets the currently selected tab.
     *
     * @return the current tab, or null if none selected
     */
    public AbstractUnifiedEditorTab getCurrentTab() {
        var selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof AbstractUnifiedEditorTab) {
            return (AbstractUnifiedEditorTab) selected;
        }
        return null;
    }

    /**
     * Gets the tab for a specific file.
     *
     * @param file the file
     * @return the tab, or null if not open
     */
    public AbstractUnifiedEditorTab getTabForFile(File file) {
        if (file == null) {
            return null;
        }
        return openTabs.get(file.getAbsolutePath());
    }

    /**
     * Checks if a file is currently open.
     *
     * @param file the file to check
     * @return true if the file is open
     */
    public boolean isFileOpen(File file) {
        return file != null && openTabs.containsKey(file.getAbsolutePath());
    }

    /**
     * Gets the number of open tabs.
     *
     * @return the tab count
     */
    public int getTabCount() {
        return openTabs.size();
    }

    /**
     * Gets all open tabs.
     *
     * @return list of all open tabs
     */
    public List<AbstractUnifiedEditorTab> getAllTabs() {
        return List.copyOf(openTabs.values());
    }

    /**
     * Reloads the currently selected tab from disk.
     */
    public void reloadCurrentTab() {
        AbstractUnifiedEditorTab currentTab = getCurrentTab();
        if (currentTab != null) {
            currentTab.reload();
        }
    }

    /**
     * Formats the content of the currently selected tab.
     */
    public void formatCurrentTab() {
        AbstractUnifiedEditorTab currentTab = getCurrentTab();
        if (currentTab != null) {
            currentTab.format();
        }
    }

    /**
     * Validates the content of the currently selected tab.
     *
     * @return validation result message, or null if valid
     */
    public String validateCurrentTab() {
        AbstractUnifiedEditorTab currentTab = getCurrentTab();
        if (currentTab != null) {
            return currentTab.validate();
        }
        return null;
    }
}
