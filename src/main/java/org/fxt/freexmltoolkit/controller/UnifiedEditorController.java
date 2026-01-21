package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.unified.*;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

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
    @FXML
    private MenuButton convertMenu;
    @FXML
    private Button templatesButton;
    @FXML
    private Button generatorButton;

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

    // Search bar
    private UnifiedSearchBar searchBar;
    private VBox editorWithSearchContainer;

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

        // Setup Search bar
        setupSearchBar();

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

    /**
     * Creates a new XML file tab.
     */
    @FXML
    public void newXmlFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XML);
        updateStatus("Created new XML file");
    }

    /**
     * Creates a new XSD file tab.
     */
    @FXML
    public void newXsdFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XSD);
        updateStatus("Created new XSD file");
    }

    /**
     * Creates a new XSLT file tab.
     */
    @FXML
    public void newXsltFile() {
        tabManager.createNewTab(UnifiedEditorFileType.XSLT);
        updateStatus("Created new XSLT file");
    }

    /**
     * Creates a new Schematron file tab.
     */
    @FXML
    public void newSchematronFile() {
        tabManager.createNewTab(UnifiedEditorFileType.SCHEMATRON);
        updateStatus("Created new Schematron file");
    }

    /**
     * Creates a new JSON file tab.
     */
    @FXML
    public void newJsonFile() {
        tabManager.createNewTab(UnifiedEditorFileType.JSON);
        updateStatus("Created new JSON file");
    }

    /**
     * Opens a file chooser to select and open a file.
     */
    @FXML
    public void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported Files", "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json", "*.jsonc", "*.json5"),
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("XSD Schema Files", "*.xsd"),
                new FileChooser.ExtensionFilter("XSLT Stylesheets", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("Schematron Files", "*.sch", "*.schematron"),
                new FileChooser.ExtensionFilter("JSON Files", "*.json", "*.jsonc", "*.json5"),
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

            // Add to recent files
            propertiesService.addLastOpenFile(file);
            refreshRecentFilesMenu();

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

            // Add to recent files
            propertiesService.addLastOpenFile(file);
        }

        refreshRecentFilesMenu();
        updateStatus("Opened " + files.size() + " file(s)");
    }

    /**
     * Saves the current tab's file.
     */
    @FXML
    public void saveCurrentTab() {
        if (tabManager.saveCurrentTab()) {
            updateStatus("File saved");
        } else {
            updateStatus("Save failed");
        }
    }

    /**
     * Saves all open tabs.
     */
    @FXML
    public void saveAllTabs() {
        if (tabManager.saveAllTabs()) {
            updateStatus("All files saved");
        } else {
            updateStatus("Some files could not be saved");
        }
    }

    /**
     * Closes the current tab.
     */
    @FXML
    public void closeCurrentTab() {
        tabManager.closeCurrentTab();
    }

    // ==================== Edit Operations ====================

    /**
     * Validates the current tab's content.
     */
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

    /**
     * Formats the current tab's content.
     */
    @FXML
    public void formatCurrentTab() {
        tabManager.formatCurrentTab();
        updateStatus("Document formatted");
    }

    /**
     * Undoes the last action in the current tab.
     */
    @FXML
    public void undo() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            currentTab.undo();
            updateStatus("Undo");
        }
    }

    /**
     * Redoes the last undone action in the current tab.
     */
    @FXML
    public void redo() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            currentTab.redo();
            updateStatus("Redo");
        }
    }

    // ==================== XML Tools (Convert, Templates, Generator) ====================

    /**
     * Shows the XML to Spreadsheet converter dialog.
     */
    @FXML
    public void showXmlToSpreadsheet() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (!(currentTab instanceof XmlUnifiedTab xmlTab)) {
            showAlert(Alert.AlertType.WARNING, "Convert",
                    "Please open an XML file first to use the converter.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/dialogs/XmlSpreadsheetConverterDialog.fxml"));
            Parent dialogContent = loader.load();
            XmlSpreadsheetConverterDialogController controller = loader.getController();

            // Pre-fill with current XML content
            controller.setSourceXml(xmlTab.getEditorContent());
            // Select XML to Spreadsheet direction
            controller.selectXmlToSpreadsheetMode();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("XML to Excel/CSV Converter");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(editorTabPane.getScene().getWindow());
            dialogStage.setScene(new Scene(dialogContent));
            dialogStage.setMinWidth(900);
            dialogStage.setMinHeight(700);
            dialogStage.showAndWait();

            updateStatus("Converter dialog closed");
        } catch (IOException e) {
            logger.error("Failed to open converter dialog", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open converter: " + e.getMessage());
        }
    }

    /**
     * Shows the Spreadsheet to XML converter dialog.
     */
    @FXML
    public void showSpreadsheetToXml() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/dialogs/XmlSpreadsheetConverterDialog.fxml"));
            Parent dialogContent = loader.load();
            XmlSpreadsheetConverterDialogController controller = loader.getController();

            // Select Spreadsheet to XML direction
            controller.selectSpreadsheetToXmlMode();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Excel/CSV to XML Converter");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(editorTabPane.getScene().getWindow());
            dialogStage.setScene(new Scene(dialogContent));
            dialogStage.setMinWidth(900);
            dialogStage.setMinHeight(700);
            dialogStage.showAndWait();

            updateStatus("Converter dialog closed");
        } catch (IOException e) {
            logger.error("Failed to open converter dialog", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open converter: " + e.getMessage());
        }
    }

    /**
     * Shows the templates popup for inserting XML templates.
     */
    @FXML
    public void showTemplates() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (!(currentTab instanceof XmlUnifiedTab xmlTab)) {
            showAlert(Alert.AlertType.INFORMATION, "Templates",
                    "Templates are available for XML files. Please open an XML file first.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/popup_templates.fxml"));

            // Create and set controller instance BEFORE loading
            TemplateEngine templateEngine = TemplateEngine.getInstance();
            TemplateRepository templateRepository = TemplateRepository.getInstance();

            // Create an adapter that implements the required interface for templates
            UnifiedEditorTemplateAdapter adapter = new UnifiedEditorTemplateAdapter(xmlTab);
            TemplateManagerPopupController controller = new TemplateManagerPopupController(
                    adapter, templateEngine, templateRepository
            );
            loader.setController(controller);

            Parent root = loader.load();
            Scene scene = new Scene(root);

            Stage popup = new Stage();
            popup.setTitle("XML Templates");
            popup.initModality(Modality.APPLICATION_MODAL);
            popup.initOwner(editorTabPane.getScene().getWindow());
            popup.setScene(scene);
            popup.setMinWidth(800);
            popup.setMinHeight(600);
            popup.showAndWait();

            updateStatus("Template popup closed");
        } catch (Exception e) {
            logger.error("Failed to open templates popup", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open templates: " + e.getMessage());
        }
    }

    /**
     * Shows the schema generator popup for generating XSD from XML.
     */
    @FXML
    public void showGenerator() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (!(currentTab instanceof XmlUnifiedTab xmlTab)) {
            showAlert(Alert.AlertType.WARNING, "Schema Generator",
                    "Please open an XML file first to generate a schema.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/popup_schema_generator.fxml"));

            // Create and set controller instance BEFORE loading
            SchemaGenerationEngine schemaEngine = new SchemaGenerationEngine();

            // Create an adapter that implements the required interface for generator
            UnifiedEditorGeneratorAdapter adapter = new UnifiedEditorGeneratorAdapter(xmlTab, this);
            SchemaGeneratorPopupController controller = new SchemaGeneratorPopupController(
                    adapter, schemaEngine
            );
            loader.setController(controller);

            Parent root = loader.load();
            Scene scene = new Scene(root);

            Stage popup = new Stage();
            popup.setTitle("Generate XSD Schema from XML");
            popup.initModality(Modality.APPLICATION_MODAL);
            popup.initOwner(editorTabPane.getScene().getWindow());
            popup.setScene(scene);
            popup.setMinWidth(800);
            popup.setMinHeight(600);
            popup.showAndWait();

            updateStatus("Schema generator closed");
        } catch (Exception e) {
            logger.error("Failed to open schema generator popup", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open schema generator: " + e.getMessage());
        }
    }

    /**
     * Opens a new XSD tab with the given content.
     * Used by the schema generator to open generated schemas.
     */
    public void openNewXsdTab(String xsdContent) {
        if (xsdContent != null && !xsdContent.isEmpty()) {
            tabManager.createNewTab(UnifiedEditorFileType.XSD);
            AbstractUnifiedEditorTab newTab = tabManager.getCurrentTab();
            if (newTab != null) {
                newTab.setEditorContent(xsdContent);
                updateStatus("Generated XSD opened in new tab");
            }
        }
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.initOwner(editorTabPane.getScene().getWindow());
        alert.showAndWait();
    }

    // ==================== XPath/XQuery Panel ====================

    /**
     * Toggles the visibility of the XPath/XQuery panel.
     */
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

    /**
     * Toggles the visibility of the linked files panel.
     */
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

    /**
     * Opens the selected linked file.
     */
    @FXML
    public void openSelectedLinkedFile() {
        LinkedFileInfo selected = linkedFilesList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.isResolved()) {
            openFile(selected.resolvedFile());
        }
    }

    /**
     * Opens all resolved linked files.
     */
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

            // Update search bar to use the new tab's editor if visible
            if (searchBar != null && searchBar.isVisible()) {
                connectSearchBarToTab(unifiedTab);
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
                name.endsWith(".sch") || name.endsWith(".schematron") ||
                name.endsWith(".json") || name.endsWith(".jsonc") || name.endsWith(".json5");
    }

    // ==================== Search Bar ====================

    /**
     * Sets up the search bar component.
     */
    private void setupSearchBar() {
        searchBar = new UnifiedSearchBar();
        searchBar.setOnClose(() -> {
            // Return focus to the current editor
            AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
            if (currentTab != null) {
                Platform.runLater(currentTab::requestEditorFocus);
            }
        });
        logger.debug("Search bar initialized");
    }

    /**
     * Shows the search bar for the current tab.
     */
    public void showSearch() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab == null) {
            return;
        }

        // Connect search bar to the current tab's editor
        connectSearchBarToTab(currentTab);

        // Show the search bar if not already visible
        if (!searchBar.isVisible()) {
            // Add search bar to the editor container if not already there
            addSearchBarToUI();
        }

        searchBar.show();

        // Pre-fill with selected text if any
        String selectedText = getSelectedTextFromTab(currentTab);
        if (selectedText != null && !selectedText.isEmpty()) {
            searchBar.setSearchText(selectedText);
        }
    }

    /**
     * Shows the search bar in replace mode.
     */
    public void showReplace() {
        AbstractUnifiedEditorTab currentTab = tabManager.getCurrentTab();
        if (currentTab == null) {
            return;
        }

        // Connect search bar to the current tab's editor
        connectSearchBarToTab(currentTab);

        // Add search bar to the editor container if not already there
        addSearchBarToUI();

        searchBar.showReplace();

        // Pre-fill with selected text if any
        String selectedText = getSelectedTextFromTab(currentTab);
        if (selectedText != null && !selectedText.isEmpty()) {
            searchBar.setSearchText(selectedText);
        }
    }

    /**
     * Hides the search bar.
     */
    public void hideSearch() {
        if (searchBar != null) {
            searchBar.hide();
        }
    }

    /**
     * Connects the search bar to the given tab's editor.
     */
    private void connectSearchBarToTab(AbstractUnifiedEditorTab tab) {
        if (tab instanceof XmlUnifiedTab xmlTab) {
            searchBar.setCurrentEditor(xmlTab.getTextEditor());
        } else if (tab instanceof XsdUnifiedTab xsdTab) {
            searchBar.setCurrentEditor(xsdTab.getTextEditor());
        } else if (tab instanceof XsltUnifiedTab xsltTab) {
            searchBar.setCurrentCodeArea(xsltTab.getCodeArea());
        } else if (tab instanceof SchematronUnifiedTab schTab) {
            searchBar.setCurrentEditor(schTab.getCodeEditor());
        } else if (tab instanceof JsonUnifiedTab jsonTab) {
            searchBar.setCurrentCodeArea(jsonTab.getCodeArea());
        }
    }

    /**
     * Adds the search bar to the UI above the editor tab pane.
     */
    private void addSearchBarToUI() {
        // Check if search bar is already in the UI
        if (editorWithSearchContainer != null &&
                editorWithSearchContainer.getChildren().contains(searchBar)) {
            return;
        }

        // The structure is: mainSplitPane contains editorXPathSplitPane
        // editorXPathSplitPane contains a VBox with editorTabPane
        // We need to add the search bar to that VBox

        if (editorXPathSplitPane != null && !editorXPathSplitPane.getItems().isEmpty()) {
            var firstItem = editorXPathSplitPane.getItems().get(0);
            if (firstItem instanceof VBox vbox) {
                // Add search bar at the top if not already there
                if (!vbox.getChildren().contains(searchBar)) {
                    vbox.getChildren().add(0, searchBar);
                }
                editorWithSearchContainer = vbox;
            }
        }
    }

    /**
     * Gets the selected text from the current tab.
     */
    private String getSelectedTextFromTab(AbstractUnifiedEditorTab tab) {
        if (tab instanceof XmlUnifiedTab xmlTab) {
            return xmlTab.getCodeArea().getSelectedText();
        } else if (tab instanceof XsdUnifiedTab xsdTab) {
            return xsdTab.getCodeArea().getSelectedText();
        } else if (tab instanceof XsltUnifiedTab xsltTab) {
            return xsltTab.getCodeArea().getSelectedText();
        } else if (tab instanceof SchematronUnifiedTab schTab) {
            return schTab.getCodeArea().getSelectedText();
        } else if (tab instanceof JsonUnifiedTab jsonTab) {
            return jsonTab.getCodeArea().getSelectedText();
        }
        return null;
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

                // Ctrl+E - Convert XML to Spreadsheet
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                        this::showXmlToSpreadsheet
                );

                // Ctrl+T - Templates
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                        this::showTemplates
                );

                // Ctrl+G - Generate XSD Schema
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),
                        this::showGenerator
                );

                // Ctrl+F - Find (Search)
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                        this::showSearch
                );

                // Ctrl+H - Find and Replace
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN),
                        this::showReplace
                );
            }
        });
    }

    // ==================== Help ====================

    /**
     * Shows the help dialog.
     */
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
                - JSON files (.json, .jsonc, .json5)

                Features:
                - Multi-tab editing
                - Automatic linked file detection
                - Favorites panel for quick access
                - Drag and drop support
                - XML to Excel/CSV conversion
                - XML Templates
                - XSD Schema generation from XML
                - Find and Replace in text

                Keyboard Shortcuts:
                - Ctrl+O: Open file
                - Ctrl+S: Save
                - Ctrl+Shift+S: Save all
                - Ctrl+Shift+R: Recent files
                - Ctrl+W: Close tab
                - Ctrl+Z: Undo
                - Ctrl+Y: Redo
                - Ctrl+F: Find (Search)
                - Ctrl+H: Find and Replace
                - Ctrl+D: Add to favorites
                - F5: Validate
                - Ctrl+Shift+F: Format
                - Ctrl+L: Toggle linked files panel
                - Ctrl+Shift+X: Toggle XPath/XQuery panel
                - Ctrl+Shift+P: Toggle properties panel
                - Ctrl+Shift+B: Show favorites
                - Ctrl+E: XML to Spreadsheet converter
                - Ctrl+T: XML Templates
                - Ctrl+G: Generate XSD Schema
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
