package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.unified.AbstractUnifiedEditorTab;
import org.fxt.freexmltoolkit.controls.unified.XmlUnifiedTab;
import org.fxt.freexmltoolkit.controls.unified.XsdUnifiedTab;
import org.fxt.freexmltoolkit.controls.unified.MultiFunctionalSidePane;
import org.fxt.freexmltoolkit.controls.unified.UnifiedEditorTabManager;
import org.fxt.freexmltoolkit.controls.unified.UnifiedXPathQueryPanel;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.LinkedFileDetector;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.CompletableFuture;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Unified Editor page.
 * Manages multiple file editing tabs with different file types (XML, XSD, XSLT, Schematron).
 *
 * Features:
 * - Multi-tab file editing
 * - Auto-detection of linked files
 * - Unified toolbar for common operations
 * - Drag and drop file support
 * - Integrated favorites panel
 *
 * @since 2.0
 */
public class UnifiedEditorController implements Initializable, FavoritesParentController {

    private static final Logger logger = LogManager.getLogger(UnifiedEditorController.class);

    // FXML Components - Toolbar
    @FXML private MenuButton newFileMenu;
    @FXML private MenuButton recentFilesMenu;
    @FXML private Button openFileButton;
    @FXML private Button saveButton;
    @FXML private Button saveAllButton;
    @FXML private Button closeTabButton;
    @FXML private Button validateButton;
    @FXML private Button formatButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private ToggleButton linkedFilesToggle;
    @FXML private ToggleButton xpathPanelToggle;
    @FXML private ToggleButton propertiesToggle;
    @FXML private Button favoritesButton;
    @FXML private Button helpButton;

    // FXML Components - Main Content
    @FXML private SplitPane mainSplitPane;
    @FXML private TabPane editorTabPane;
    @FXML private VBox linkedFilesPanel;
    @FXML private ListView<LinkedFileInfo> linkedFilesList;

    // FXML Components - Status Bar
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private Label fileTypeLabel;
    @FXML private Label cursorPositionLabel;
    @FXML private Label linkedFilesCountLabel;

    // Internal components
    private UnifiedEditorTabManager tabManager;
    private LinkedFileDetector linkDetector;
    private MainController parentController;

    // Services
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final ObservableList<LinkedFileInfo> linkedFiles = FXCollections.observableArrayList();

    // XPath/XQuery panel
    private UnifiedXPathQueryPanel xpathPanel;
    private SplitPane editorXPathSplitPane;

    // Multi-functional side pane (replaces separate favorites panel)
    private MultiFunctionalSidePane multiFunctionalPane;

    // Linked files panel management
    private boolean linkedFilesPanelInSplitPane = true;
    private int linkedFilesPanelIndex = 1; // Default index after editorXPathSplitPane
    private double linkedFilesDividerPosition = 0.75;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing UnifiedEditorController");

        // Initialize components
        tabManager = new UnifiedEditorTabManager(editorTabPane);
        linkDetector = new LinkedFileDetector();

        // Setup linked files list
        linkedFilesList.setItems(linkedFiles);
        linkedFilesList.setCellFactory(lv -> new LinkedFileListCell());

        // Setup tab selection listener
        editorTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> onTabSelected(newTab));

        // Setup drag and drop
        setupDragAndDrop();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Setup XPath/XQuery panel
        setupXPathPanel();

        // Setup Multi-functional side pane (replaces separate favorites panel)
        setupMultiFunctionalPane();

        // Initialize Recent Files menu
        initializeRecentFilesMenu();

        // Hide linked files panel initially - remove from SplitPane to free space
        linkedFilesPanel.setVisible(false);
        linkedFilesPanel.setManaged(false);
        // Find and store the panel index, then remove it
        linkedFilesPanelIndex = mainSplitPane.getItems().indexOf(linkedFilesPanel);
        if (linkedFilesPanelIndex >= 0) {
            mainSplitPane.getItems().remove(linkedFilesPanel);
            linkedFilesPanelInSplitPane = false;
        }

        updateStatus("Ready");
        logger.info("UnifiedEditorController initialized");
    }

    /**
     * Sets up the multi-functional side pane.
     */
    private void setupMultiFunctionalPane() {
        multiFunctionalPane = new MultiFunctionalSidePane();

        // Set callback for when a favorite is selected
        multiFunctionalPane.setOnFavoriteSelected(file -> {
            if (file != null && file.exists()) {
                openFile(file);
            }
        });

        // Set callback for visibility changes
        multiFunctionalPane.setOnVisibilityChanged(() -> {
            if (propertiesToggle != null) {
                propertiesToggle.setSelected(multiFunctionalPane.isVisible() && multiFunctionalPane.isManaged());
            }
        });

        // Set callback for when pane is shown - reconnect to current tab
        multiFunctionalPane.setOnPaneShown(() -> {
            AbstractUnifiedEditorTab currentTab = tabManager != null ? tabManager.getCurrentTab() : null;
            if (currentTab != null) {
                multiFunctionalPane.showPropertiesForEditor(currentTab.getFileType());

                // Reconnect sidebar to the current tab based on type
                if (currentTab instanceof XmlUnifiedTab xmlTab) {
                    multiFunctionalPane.connectToXmlTab(xmlTab);
                } else if (currentTab instanceof XsdUnifiedTab xsdTab) {
                    multiFunctionalPane.connectToXsdTab(xsdTab);
                }
            }
        });

        // Initially show the pane
        multiFunctionalPane.setVisible(true);
        multiFunctionalPane.setManaged(true);
        if (propertiesToggle != null) {
            propertiesToggle.setSelected(true);
        }

        // Add to main split pane (at the end, after linked files panel)
        mainSplitPane.getItems().add(multiFunctionalPane);

        logger.debug("Multi-functional side pane initialized");
    }

    /**
     * Sets up the XPath/XQuery panel at the bottom of the editor.
     */
    private void setupXPathPanel() {
        // Create the XPath panel
        xpathPanel = new UnifiedXPathQueryPanel();
        xpathPanel.setVisible(false);
        xpathPanel.setManaged(false);

        // Find the center content from mainSplitPane and wrap it
        // The mainSplitPane contains: editorTabPane + linkedFilesPanel
        // We need to create a vertical split pane that contains:
        // - The tab pane (top)
        // - The XPath panel (bottom, hidden by default)

        // Create the vertical split pane for editor + xpath
        editorXPathSplitPane = new SplitPane();
        editorXPathSplitPane.setOrientation(Orientation.VERTICAL);
        editorXPathSplitPane.setDividerPositions(1.0); // XPath panel hidden initially

        // Create wrapper for tab pane
        VBox editorWrapper = new VBox(editorTabPane);
        VBox.setVgrow(editorTabPane, Priority.ALWAYS);

        // Add to vertical split pane
        editorXPathSplitPane.getItems().addAll(editorWrapper, xpathPanel);

        // Replace the first item in mainSplitPane with our new vertical split
        // The mainSplitPane should have editorTabPane as first item
        if (!mainSplitPane.getItems().isEmpty()) {
            int tabPaneIndex = mainSplitPane.getItems().indexOf(editorTabPane);
            if (tabPaneIndex >= 0) {
                double[] dividerPositions = mainSplitPane.getDividerPositions();
                mainSplitPane.getItems().set(tabPaneIndex, editorXPathSplitPane);
                // Restore divider positions if we have any
                if (dividerPositions.length > 0) {
                    mainSplitPane.setDividerPositions(dividerPositions);
                }
            }
        }

        // Set close handler for XPath panel
        xpathPanel.setOnCloseRequested(this::hideXPathPanel);
    }

    /**
     * Sets the parent controller.
     *
     * @param parentController the main controller
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    // ==================== File Operations ====================

    @FXML
    public void newXmlFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XML);
        updateStatus("Created new XML file");
    }

    @FXML
    public void newXsdFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XSD);
        updateStatus("Created new XSD file");
    }

    @FXML
    public void newXsltFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XSLT);
        updateStatus("Created new XSLT file");
    }

    @FXML
    public void newSchematronFile() {
        tabManager.createNewTab(UnifiedEditorFileType.SCHEMATRON);
        updateStatus("Created new Schematron file");
    }

    @FXML
    public void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported Files", "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron"),
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("XSD Schema Files", "*.xsd"),
                new FileChooser.ExtensionFilter("XSLT Stylesheets", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("Schematron Files", "*.sch", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        List<File> files = fileChooser.showOpenMultipleDialog(editorTabPane.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            openFiles(files);
        }
    }

    /**
     * Opens a single file in the editor.
     *
     * @param file the file to open
     */
    public void openFile(File file) {
        if (file != null && file.exists()) {
            tabManager.openFile(file);
            updateStatus("Opened: " + file.getName());
        }
    }

    /**
     * Opens multiple files in the editor.
     *
     * @param files the files to open
     */
    public void openFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        for (File file : files) {
            tabManager.openFile(file);
        }

        updateStatus("Opened " + files.size() + " file(s)");
    }

    @FXML
    public void saveCurrentTab() {
        if (tabManager.saveCurrentTab()) {
            updateStatus("File saved");
        } else {
            updateStatus("Save failed");
        }
    }

    @FXML
    public void saveAllTabs() {
        if (tabManager.saveAllTabs()) {
            updateStatus("All files saved");
        } else {
            updateStatus("Some files could not be saved");
        }
    }

    @FXML
    public void closeCurrentTab() {
        tabManager.closeCurrentTab();
    }

    // ==================== Edit Operations ====================

    @FXML
    public void validateCurrentTab() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        String result = tabManager.validateCurrentTab();
        boolean isValid = result == null;

        if (isValid) {
            updateStatus("Validation successful");
            showValidationResult("Valid", true);
        } else {
            updateStatus("Validation failed: " + result);
            showValidationResult(result, false);
        }

        // Update sidebar validation status for XML tabs
        if (multiFunctionalPane != null && currentTab instanceof XmlUnifiedTab xmlTab) {
            java.util.List<org.fxt.freexmltoolkit.domain.ValidationError> errors =
                    isValid ? java.util.List.of() : xmlTab.validateXml();
            multiFunctionalPane.updateValidationStatus(
                    isValid ? "Valid" : result,
                    isValid,
                    errors
            );

            // Also validate Schematron if linked
            var schematronErrors = xmlTab.validateSchematron();
            if (!schematronErrors.isEmpty()) {
                multiFunctionalPane.updateSchematronValidationStatus(
                        schematronErrors.size() + " error(s)",
                        false,
                        schematronErrors
                );
            } else if (xmlTab.getSchematronFile() != null) {
                multiFunctionalPane.updateSchematronValidationStatus(
                        "Valid",
                        true,
                        java.util.List.of()
                );
            }
        }
    }

    @FXML
    public void formatCurrentTab() {
        tabManager.formatCurrentTab();
        updateStatus("Document formatted");
    }

    @FXML
    public void undo() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            currentTab.undo();
            updateStatus("Undo");
        }
    }

    @FXML
    public void redo() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            currentTab.redo();
            updateStatus("Redo");
        }
    }

    // ==================== XPath/XQuery Panel ====================

    @FXML
    public void toggleXPathPanel() {
        if (xpathPanelToggle != null) {
            boolean show = xpathPanelToggle.isSelected();
            setXPathPanelVisible(show);
        } else {
            // If no toggle button, just toggle the current state
            setXPathPanelVisible(!xpathPanel.isVisible());
        }
    }

    /**
     * Shows or hides the XPath panel.
     */
    public void setXPathPanelVisible(boolean visible) {
        if (xpathPanel == null) {
            return;
        }

        xpathPanel.setVisible(visible);
        xpathPanel.setManaged(visible);

        if (xpathPanelToggle != null) {
            xpathPanelToggle.setSelected(visible);
        }

        if (visible) {
            // Set divider position to show the panel (70% editor, 30% xpath)
            editorXPathSplitPane.setDividerPositions(0.7);

            // Connect to current tab's content
            updateXPathPanelContent();
        } else {
            // Hide panel by moving divider to bottom
            editorXPathSplitPane.setDividerPositions(1.0);
        }
    }

    /**
     * Hides the XPath panel.
     */
    public void hideXPathPanel() {
        setXPathPanelVisible(false);
    }

    /**
     * Updates the XPath panel's content provider based on the current tab.
     */
    private void updateXPathPanelContent() {
        if (xpathPanel == null) {
            return;
        }

        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null && currentTab.supportsXPath()) {
            xpathPanel.setActiveContentProvider(currentTab::getEditorContent);
        } else {
            xpathPanel.setActiveContentProvider(null);
        }
    }

    /**
     * Gets the XPath panel.
     */
    public UnifiedXPathQueryPanel getXPathPanel() {
        return xpathPanel;
    }

    /**
     * Checks if the XPath panel is visible.
     */
    public boolean isXPathPanelVisible() {
        return xpathPanel != null && xpathPanel.isVisible();
    }

    // ==================== Linked Files ====================

    @FXML
    public void toggleLinkedFilesPanel() {
        boolean show = linkedFilesToggle.isSelected();

        if (show) {
            // Add panel back to SplitPane if not already there
            if (!linkedFilesPanelInSplitPane) {
                linkedFilesPanel.setVisible(true);
                linkedFilesPanel.setManaged(true);

                // Insert at the correct position (before multiFunctionalPane)
                int insertIndex = Math.min(linkedFilesPanelIndex, mainSplitPane.getItems().size());
                mainSplitPane.getItems().add(insertIndex, linkedFilesPanel);
                linkedFilesPanelInSplitPane = true;

                // Restore divider position after a short delay to allow layout
                Platform.runLater(() -> {
                    if (mainSplitPane.getDividerPositions().length > 0) {
                        // Adjust divider positions to accommodate the panel
                        double[] positions = mainSplitPane.getDividerPositions();
                        if (positions.length >= insertIndex && insertIndex > 0) {
                            mainSplitPane.setDividerPosition(insertIndex - 1, linkedFilesDividerPosition);
                        }
                    }
                });
            }

            // Refresh linked files for current tab
            refreshLinkedFiles();
        } else {
            // Remove panel from SplitPane to free space
            if (linkedFilesPanelInSplitPane) {
                // Save divider position before removing
                int panelIndex = mainSplitPane.getItems().indexOf(linkedFilesPanel);
                if (panelIndex > 0 && mainSplitPane.getDividerPositions().length >= panelIndex) {
                    linkedFilesDividerPosition = mainSplitPane.getDividerPositions()[panelIndex - 1];
                }

                linkedFilesPanel.setVisible(false);
                linkedFilesPanel.setManaged(false);
                mainSplitPane.getItems().remove(linkedFilesPanel);
                linkedFilesPanelInSplitPane = false;
            }
        }
    }

    @FXML
    public void openSelectedLinkedFile() {
        LinkedFileInfo selected = linkedFilesList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.isResolved()) {
            openFile(selected.resolvedFile());
        }
    }

    @FXML
    public void openAllLinkedFiles() {
        for (LinkedFileInfo link : linkedFiles) {
            if (link.isResolved()) {
                openFile(link.resolvedFile());
            }
        }
    }

    private void refreshLinkedFiles() {
        linkedFiles.clear();

        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            List<LinkedFileInfo> detected = currentTab.detectLinkedFiles();
            linkedFiles.addAll(detected);

            int count = detected.size();
            int resolved = (int) detected.stream().filter(LinkedFileInfo::isResolved).count();

            if (count > 0) {
                linkedFilesCountLabel.setText(resolved + "/" + count + " linked");
            } else {
                linkedFilesCountLabel.setText("");
            }
        }
    }

    // ==================== Multi-Functional Pane ====================

    /**
     * Toggles the properties panel visibility.
     */
    @FXML
    public void togglePropertiesPanel() {
        if (multiFunctionalPane == null) {
            return;
        }

        multiFunctionalPane.toggleVisibility();
        // Note: onPaneShown callback handles reconnection to current tab when shown
    }

    /**
     * Shows the favorites panel temporarily.
     */
    @FXML
    public void showFavoritesPanel() {
        if (multiFunctionalPane == null) {
            return;
        }

        // If currently showing favorites, hide them
        if (multiFunctionalPane.isFavoritesShowing()) {
            multiFunctionalPane.hideFavorites();
        } else {
            // Show favorites temporarily
            multiFunctionalPane.showFavorites();
        }
    }

    /**
     * Adds the current file to favorites.
     */
    @FXML
    public void addCurrentFileToFavorites() {
        File currentFile = getCurrentFile();
        if (currentFile != null && currentFile.exists()) {
            FavoritesService.getInstance().addFavorite(currentFile);
            updateStatus("Added to favorites: " + currentFile.getName());
        } else {
            updateStatus("No file to add to favorites");
        }
    }

    /**
     * Gets the multi-functional side pane.
     */
    public MultiFunctionalSidePane getMultiFunctionalPane() {
        return multiFunctionalPane;
    }

    // ==================== FavoritesParentController Implementation ====================

    @Override
    public void loadFileToNewTab(File file) {
        if (file != null && file.exists()) {
            openFile(file);
        }
    }

    @Override
    public File getCurrentFile() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            return currentTab.getSourceFile();
        }
        return null;
    }

    // ==================== Tab Selection ====================

    private void onTabSelected(Tab tab) {
        if (tab instanceof AbstractUnifiedEditorTab unifiedTab) {
            // Update file type label
            fileTypeLabel.setText(unifiedTab.getFileType().getDisplayName());

            // Update cursor position
            updateCursorPosition(unifiedTab);

            // Refresh linked files if panel is visible
            if (linkedFilesPanelInSplitPane) {
                refreshLinkedFiles();
            }

            // Update XPath panel content provider if visible
            if (xpathPanel != null && xpathPanel.isVisible()) {
                updateXPathPanelContent();
            }

            // Update multi-functional pane to show properties for this editor type
            if (multiFunctionalPane != null && !multiFunctionalPane.isFavoritesShowing()) {
                multiFunctionalPane.showPropertiesForEditor(unifiedTab.getFileType());

                // Connect sidebar to the current tab based on type
                if (unifiedTab instanceof XmlUnifiedTab xmlTab) {
                    multiFunctionalPane.connectToXmlTab(xmlTab);
                } else if (unifiedTab instanceof XsdUnifiedTab xsdTab) {
                    multiFunctionalPane.connectToXsdTab(xsdTab);
                }
            }

            // Focus the editor
            Platform.runLater(unifiedTab::requestEditorFocus);
        } else {
            fileTypeLabel.setText("");
            cursorPositionLabel.setText("");
            linkedFiles.clear();
            linkedFilesCountLabel.setText("");

            // Clear XPath panel content provider
            if (xpathPanel != null) {
                xpathPanel.setActiveContentProvider(null);
            }
        }
    }

    private void updateCursorPosition(AbstractUnifiedEditorTab tab) {
        if (tab != null) {
            cursorPositionLabel.setText(tab.getCaretPosition());
        }
    }

    // ==================== Drag and Drop ====================

    private void setupDragAndDrop() {
        editorTabPane.setOnDragOver(this::handleDragOver);
        editorTabPane.setOnDragDropped(this::handleDragDropped);
    }

    private void handleDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            boolean hasValidFile = db.getFiles().stream()
                    .anyMatch(this::isSupportedFile);
            if (hasValidFile) {
                event.acceptTransferModes(TransferMode.COPY);
            }
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            List<File> supportedFiles = db.getFiles().stream()
                    .filter(this::isSupportedFile)
                    .toList();

            if (!supportedFiles.isEmpty()) {
                openFiles(supportedFiles);
                success = true;
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    private boolean isSupportedFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        return name.endsWith(".xml") || name.endsWith(".xsd") ||
                name.endsWith(".xsl") || name.endsWith(".xslt") ||
                name.endsWith(".sch") || name.endsWith(".schematron");
    }

    // ==================== Keyboard Shortcuts ====================

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            var scene = editorTabPane.getScene();
            if (scene != null) {
                // Ctrl+O - Open
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                        this::openFile
                );

                // Ctrl+S - Save
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                        this::saveCurrentTab
                );

                // Ctrl+Shift+S - Save All
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::saveAllTabs
                );

                // Ctrl+W - Close Tab
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                        this::closeCurrentTab
                );

                // F5 - Validate
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F5),
                        this::validateCurrentTab
                );

                // Ctrl+Shift+F - Format
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::formatCurrentTab
                );

                // Ctrl+L - Toggle Linked Files
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                        () -> {
                            linkedFilesToggle.setSelected(!linkedFilesToggle.isSelected());
                            toggleLinkedFilesPanel();
                        }
                );

                // Ctrl+Shift+X - Toggle XPath Panel
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                            if (xpathPanelToggle != null) {
                                xpathPanelToggle.setSelected(!xpathPanelToggle.isSelected());
                            }
                            toggleXPathPanel();
                        }
                );

                // Ctrl+Shift+P - Toggle Properties Panel
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                            if (propertiesToggle != null) {
                                propertiesToggle.setSelected(!propertiesToggle.isSelected());
                            }
                            togglePropertiesPanel();
                        }
                );

                // Ctrl+Shift+B - Show Favorites (Bookmarks) Panel
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::showFavoritesPanel
                );

                // Ctrl+D - Add current file to favorites
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                        this::addCurrentFileToFavorites
                );

                // Ctrl+Z - Undo
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                        this::undo
                );

                // Ctrl+Y - Redo
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                        this::redo
                );

                // Ctrl+Shift+R - Show Recent Files menu
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::showRecentFilesMenu
                );
            }
        });
    }

    // ==================== Help ====================

    @FXML
    public void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Unified Editor Help");
        alert.setHeaderText("Unified Editor - Experimental Feature");
        alert.setContentText("""
                The Unified Editor allows you to edit multiple related files in one view:

                Supported File Types:
                - XML files (.xml)
                - XSD Schema files (.xsd)
                - XSLT Stylesheets (.xsl, .xslt)
                - Schematron files (.sch)

                Features:
                - Multi-tab editing
                - Automatic linked file detection
                - Favorites panel for quick access
                - Drag and drop support
                - Common toolbar operations

                Keyboard Shortcuts:
                - Ctrl+O: Open file
                - Ctrl+S: Save
                - Ctrl+Shift+S: Save all
                - Ctrl+Shift+R: Recent files
                - Ctrl+W: Close tab
                - Ctrl+Z: Undo
                - Ctrl+Y: Redo
                - Ctrl+D: Add to favorites
                - F5: Validate
                - Ctrl+Shift+F: Format
                - Ctrl+L: Toggle linked files panel
                - Ctrl+Shift+X: Toggle XPath/XQuery panel
                - Ctrl+Shift+P: Toggle properties panel
                - Ctrl+Shift+B: Show favorites
                """);
        alert.showAndWait();
    }

    // ==================== Utility Methods ====================

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void showValidationResult(String message, boolean isValid) {
        Alert alert = new Alert(isValid ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle("Validation Result");
        alert.setHeaderText(isValid ? "Document is valid" : "Validation failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Checks if there are unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return tabManager.hasUnsavedChanges();
    }

    /**
     * Gets the tab manager.
     *
     * @return the tab manager
     */
    public UnifiedEditorTabManager getTabManager() {
        return tabManager;
    }

    // ==================== Recent Files ====================

    /**
     * Initialize the Recent Files menu in the toolbar.
     */
    private void initializeRecentFilesMenu() {
        if (recentFilesMenu != null) {
            refreshRecentFilesMenu();
            logger.debug("Recent files menu initialized");
        }
    }

    /**
     * Refresh the Recent Files menu with current recent files list.
     * File existence checks are performed asynchronously for better performance.
     */
    private void refreshRecentFilesMenu() {
        if (recentFilesMenu == null) {
            return;
        }

        recentFilesMenu.getItems().clear();

        List<File> recentFiles = propertiesService.getLastOpenFiles();
        if (recentFiles == null || recentFiles.isEmpty()) {
            MenuItem noFiles = new MenuItem("No recent files");
            noFiles.setDisable(true);
            recentFilesMenu.getItems().add(noFiles);
            return;
        }

        // Show loading indicator while checking files in background
        MenuItem loadingItem = new MenuItem("Loading...");
        loadingItem.setDisable(true);
        recentFilesMenu.getItems().add(loadingItem);

        // Perform file.exists() checks in background thread to avoid blocking UI
        CompletableFuture.supplyAsync(() -> {
            List<File> existingFiles = new java.util.ArrayList<>();
            for (File file : recentFiles) {
                if (file.exists() && isSupportedFile(file)) {
                    existingFiles.add(file);
                }
            }
            return existingFiles;
        }).thenAcceptAsync(existingFiles -> {
            recentFilesMenu.getItems().clear();

            if (existingFiles.isEmpty()) {
                MenuItem noFiles = new MenuItem("No recent files");
                noFiles.setDisable(true);
                recentFilesMenu.getItems().add(noFiles);
            } else {
                for (File file : existingFiles) {
                    MenuItem item = new MenuItem(file.getName());

                    // Add icon based on file type
                    UnifiedEditorFileType fileType = UnifiedEditorFileType.fromFile(file);
                    if (fileType != null) {
                        FontIcon icon = new FontIcon(fileType.getIcon());
                        icon.setIconSize(16);
                        icon.setIconColor(javafx.scene.paint.Color.web(fileType.getColor()));
                        item.setGraphic(icon);
                    }

                    item.setOnAction(e -> {
                        openFile(file);
                        logger.info("Opened recent file from toolbar: {}", file.getAbsolutePath());
                    });
                    recentFilesMenu.getItems().add(item);
                }
            }
        }, Platform::runLater);
    }

    /**
     * Show the Recent Files menu when keyboard shortcut is pressed.
     */
    private void showRecentFilesMenu() {
        if (recentFilesMenu != null) {
            refreshRecentFilesMenu();
            recentFilesMenu.show();
        }
    }

    // ==================== Linked File List Cell ====================

    /**
     * Custom list cell for displaying linked file information.
     */
    private static class LinkedFileListCell extends ListCell<LinkedFileInfo> {

        @Override
        protected void updateItem(LinkedFileInfo item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                setText(item.getDisplayName());

                // Set icon based on file type
                FontIcon icon = new FontIcon(item.getFileType().getIcon());
                icon.setIconSize(16);

                if (item.isResolved()) {
                    icon.setIconColor(javafx.scene.paint.Color.web(item.getFileType().getColor()));
                    setStyle("-fx-text-fill: #212529;");
                } else {
                    icon.setIconColor(javafx.scene.paint.Color.web("#dc3545"));
                    setStyle("-fx-text-fill: #dc3545; -fx-font-style: italic;");
                }

                setGraphic(icon);
                setTooltip(new Tooltip(item.getTooltipText()));
            }
        }
    }
}
