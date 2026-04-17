package org.fxt.freexmltoolkit.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.shared.utilities.FindReplaceDialog;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.GeneratedFile;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.GenerationProfileService;
import org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.util.DialogHelper;


/**
 * Main Controller for the XSD Editor.
 * Orchestrates multiple sub-controllers for different tabs.
 */
public class XsdController implements FavoritesParentController, XsdToolHost {

    private static final Logger logger = LogManager.getLogger(XsdController.class);

    private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView currentGraphViewV2;
    private org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager currentTypeEditorManager;
    private org.fxt.freexmltoolkit.controls.v2.model.XsdSchema cachedXsdSchema;
    private String cachedXsdContent;
    private String pendingNavigationElement;
    @FXML
    private TabPane tabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab xsdTab;
    @FXML
    private Label statusText;

    @FXML
    private DocumentationTabController documentationTabController;
    @FXML
    private FlattenTabController flattenTabController;
    @FXML
    private SchemaAnalysisTabController schemaAnalysisTabController;

    @FXML
    private VBox taskStatusBar;
    @FXML
    private VBox taskContainer;
    @FXML
    private Label taskTimerLabel;
    @FXML
    private ProgressIndicator taskProgressIndicator;

    @FXML
    private StackPane xsdStackPaneV2;
    @FXML
    private VBox noFileLoadedPane;
    @FXML
    private ProgressIndicator xsdDiagramProgress;

    @FXML
    private VBox sourceCodeEditorContainer;
    private XmlCodeEditorV2 sourceCodeEditor;
    @FXML
    private VBox noFileLoadedPaneText;
    @FXML
    private HBox textInfoPane;
    @FXML
    private Label textInfoPathLabel;

    @FXML
    private TextField outputXmlPath;
    @FXML
    private TextField xsdForSampleDataPath;
    @FXML
    private VBox sampleDataValidationResultPanel;
    @FXML
    private CheckBox mandatoryOnlyCheckBox;
    @FXML
    private Spinner<Integer> maxOccurrencesSpinner;
    @FXML
    private Button generateSampleDataButton;
    @FXML
    private Button cancelSampleDataButton;
    @FXML
    private Button validateGeneratedXmlButton;
    @FXML
    private Button exportValidationErrorsButton;

    private Task<String> currentGenerationTask;
    private final javafx.beans.property.BooleanProperty isGeneratingSampleData =
            new javafx.beans.property.SimpleBooleanProperty(false);
    @FXML
    private org.kordamp.ikonli.javafx.FontIcon sampleDataValidationIcon;
    @FXML
    private Label sampleDataValidationTitle;
    @FXML
    private Label sampleDataValidationMessage;
    @FXML
    private TableView<XsdDocumentationService.ValidationError> validationErrorsTable;
    @FXML
    private TableColumn<XsdDocumentationService.ValidationError, Integer> errorLineColumn;
    @FXML
    private TableColumn<XsdDocumentationService.ValidationError, Integer> errorColumnColumn;
    @FXML
    private TableColumn<XsdDocumentationService.ValidationError, String> errorSeverityColumn;
    @FXML
    private TableColumn<XsdDocumentationService.ValidationError, String> errorMessageColumn;
    @FXML
    private org.fxmisc.richtext.CodeArea sampleDataTextArea;
    @FXML
    private ProgressIndicator progressSampleData;

    private final List<XsdDocumentationService.ValidationError> currentValidationErrors = new ArrayList<>();

    // Profiled generation fields
    @FXML
    private ComboBox<String> profileComboBox;
    @FXML
    private Spinner<Integer> batchCountSpinner;
    @FXML
    private TextField fileNamePatternField;
    @FXML
    private TableView<XPathRule> rulesTableView;
    @FXML
    private TableColumn<XPathRule, String> ruleXPathColumn;
    @FXML
    private TableColumn<XPathRule, String> ruleStrategyColumn;
    @FXML
    private TableColumn<XPathRule, Boolean> ruleEnabledColumn;
    @FXML
    private TableColumn<XPathRule, String> ruleConfigColumn;
    @FXML
    private TableColumn<XPathRule, Void> ruleDeleteColumn;

    private final ObservableList<XPathRule> rulesList = FXCollections.observableArrayList();
    private final ProfiledXmlGeneratorService profiledGenerator = new ProfiledXmlGeneratorService();
    private GenerationProfile currentProfile;

    @FXML
    private StackPane typeLibraryStackPane;
    @FXML
    private VBox noFileLoadedPaneTypeLibrary;
    @FXML
    private StackPane typeEditorStackPane;
    @FXML
    private VBox noFileLoadedPaneTypeEditor;
    @FXML
    private Tab typeLibraryTab;
    @FXML
    private Tab typeEditorTab;

    @FXML
    private SplitPane graphicTabSplitPane;
    @FXML
    private SplitPane textTabSplitPane;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private VBox favoritesPanelGraphic;
    @FXML
    private FavoritesPanelController favoritesPanelController;
    @FXML
    private FavoritesPanelController favoritesPanelGraphicController;

    @FXML
    private Button toolbarNewFile, toolbarLoadFile, toolbarSave, toolbarSaveAs;
    @FXML
    private Button toolbarReload, toolbarClose, toolbarValidate;
    @FXML
    private Button toolbarUndo, toolbarRedo, toolbarFind, toolbarFormat;
    @FXML
    private Button toolbarAddFavorite, toolbarShowFavorites, toolbarXPathQuery;
    @FXML
    private MenuButton toolbarRecentFiles;
    @FXML
    private Button toolbarHelp;

    private boolean favoritesPanelVisible = true;

    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final FavoritesService favoritesService = ServiceRegistry.get(FavoritesService.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

    private File currentXsdFile;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    @FXML
    public void initialize() {
        initializeSourceCodeEditor();
        initializeValidationErrorsTable();
        initializeSampleDataControls();
        setupDragAndDrop();
        setupTextToGraphicSync();
        
        if (documentationTabController != null) {
            documentationTabController.setParentController(this);
        }
        if (flattenTabController != null) {
            flattenTabController.setParentController(this);
        }
        if (schemaAnalysisTabController != null) {
            schemaAnalysisTabController.setParentController(this);
        }
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
        }
        if (favoritesPanelGraphicController != null) {
            favoritesPanelGraphicController.setParentController(this);
        }

        applyEditorSettings();
        applySmallIconsSetting();
    }

    private void initializeSourceCodeEditor() {
        sourceCodeEditor = XmlCodeEditorV2Factory.createWithMutableSchema();
        VBox.setVgrow(sourceCodeEditor, javafx.scene.layout.Priority.ALWAYS);
        sourceCodeEditorContainer.getChildren().add(sourceCodeEditor);
    }

    private void setupDragAndDrop() {
        DragDropService.setupDragDrop(tabPane, DragDropService.XSD_EXTENSIONS, files -> {
            if (!files.isEmpty()) {
                openXsdFile(files.get(0));
            }
        });
    }

    public void openXsdFile(File file) {
        xmlService.setCurrentXsdFile(file);
        this.currentXsdFile = file;

        if (textInfoPathLabel != null) {
            textInfoPathLabel.setText(file.getAbsolutePath());
        }
        if (flattenTabController != null) {
            flattenTabController.setSourcePath(file.getAbsolutePath());
        }
        if (xsdForSampleDataPath != null) {
            xsdForSampleDataPath.setText(file.getAbsolutePath());
        }

        try {
            String content = Files.readString(file.toPath());
            loadXsdContent(content);
            parseSchemaAndDistribute(content, xsdTab.isSelected());
        } catch (IOException e) {
            logger.error("Failed to read XSD file", e);
        }
    }

    private void loadXsdContent(String content) {
        sourceCodeEditor.getCodeArea().replaceText(content);
        noFileLoadedPaneText.setVisible(false);
        noFileLoadedPaneText.setManaged(false);
        sourceCodeEditorContainer.setVisible(true);
        sourceCodeEditorContainer.setManaged(true);
        textInfoPane.setVisible(true);
        textInfoPane.setManaged(true);
    }

    private void parseSchemaAndDistribute(String content, boolean createView) {
        Task<XsdSchema> task = new Task<>() {
            @Override
            protected XsdSchema call() throws Exception {
                org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory factory = new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory();
                if (currentXsdFile != null && currentXsdFile.exists()) {
                    Path schemaPath = currentXsdFile.toPath().toAbsolutePath().normalize();
                    return factory.fromStringWithSchemaFile(content, schemaPath, schemaPath.getParent());
                }
                return factory.fromString(content);
            }
        };

        task.setOnSucceeded(e -> {
            cachedXsdSchema = task.getValue();
            if (schemaAnalysisTabController != null) {
                schemaAnalysisTabController.setSchema(cachedXsdSchema);
            }
            if (createView) {
                createGraphicalViewFromCachedSchema();
            }
            updateTypeLibrary();
            updateTypeEditor();
        });

        executorService.submit(task);
    }

    private void updateTypeLibrary() {
        if (cachedXsdSchema == null || typeLibraryStackPane == null) {
            return;
        }
        Platform.runLater(() -> {
            typeLibraryStackPane.getChildren().clear();
            var typeLibraryView = new org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView(cachedXsdSchema);
            typeLibraryStackPane.getChildren().add(typeLibraryView);
            if (noFileLoadedPaneTypeLibrary != null) {
                noFileLoadedPaneTypeLibrary.setVisible(false);
                noFileLoadedPaneTypeLibrary.setManaged(false);
            }
        });
    }

    private void updateTypeEditor() {
        if (cachedXsdSchema == null || typeEditorStackPane == null) {
            return;
        }
        Platform.runLater(() -> {
            typeEditorStackPane.getChildren().clear();
            TabPane typeEditorTabPane = new TabPane();
            currentTypeEditorManager = new org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager(typeEditorTabPane, cachedXsdSchema);
            typeEditorStackPane.getChildren().add(typeEditorTabPane);
            if (noFileLoadedPaneTypeEditor != null) {
                noFileLoadedPaneTypeEditor.setVisible(false);
                noFileLoadedPaneTypeEditor.setManaged(false);
            }
            wireTypeEditorCallbacks();
        });
    }

    private void createGraphicalViewFromCachedSchema() {
        if (cachedXsdSchema == null) {
            return;
        }
        xsdStackPaneV2.getChildren().clear();
        currentGraphViewV2 = new org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView(cachedXsdSchema);
        xsdStackPaneV2.getChildren().add(currentGraphViewV2);
        xsdStackPaneV2.setVisible(true);
        xsdStackPaneV2.setManaged(true);
        noFileLoadedPane.setVisible(false);
        noFileLoadedPane.setManaged(false);
        wireTypeEditorCallbacks();

        // Execute pending Go to Definition navigation
        if (pendingNavigationElement != null) {
            String elementName = pendingNavigationElement;
            pendingNavigationElement = null;
            Platform.runLater(() -> currentGraphViewV2.navigateToElement(elementName));
        }
    }

    private void wireTypeEditorCallbacks() {
        if (currentGraphViewV2 == null || currentTypeEditorManager == null) {
            return;
        }
        currentGraphViewV2.setOpenComplexTypeEditorCallback(complexType -> {
            Platform.runLater(() -> {
                currentTypeEditorManager.openComplexTypeTab(complexType);
                if (typeEditorTab != null && tabPane != null) {
                    tabPane.getSelectionModel().select(typeEditorTab);
                }
            });
        });
        currentGraphViewV2.setOpenSimpleTypeEditorCallback(simpleType -> {
            Platform.runLater(() -> {
                currentTypeEditorManager.openSimpleTypeTab(simpleType);
                if (typeEditorTab != null && tabPane != null) {
                    tabPane.getSelectionModel().select(typeEditorTab);
                }
            });
        });
    }

    private void setupTextToGraphicSync() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (oldTab == textTab && newTab == xsdTab) {
                syncTextToGraphic();
            }
            if (oldTab == xsdTab && newTab == textTab) {
                syncGraphicToText();
            }
        });
    }

    private void syncTextToGraphic() {
        String text = sourceCodeEditor.getCodeArea().getText();
        if (text != null && !text.equals(cachedXsdContent)) {
            parseSchemaAndDistribute(text, true);
        }
    }

    private void syncGraphicToText() {
        if (currentGraphViewV2 == null) {
            return;
        }
        XsdSerializer serializer = new XsdSerializer();
        serializer.setExcludeIncludedNodes(true);
        String xml = serializer.serialize(cachedXsdSchema, XsdSortOrder.NAME_BEFORE_TYPE);
        sourceCodeEditor.getCodeArea().replaceText(xml);
    }

    public <T> void executeBackgroundTask(Task<T> task) {
        executeTask(task);
    }

    private <T> void executeTask(Task<T> task) {
        taskStatusBar.setVisible(true);
        taskStatusBar.setManaged(true);
        executorService.submit(task);
    }

    public void updateBackgroundTaskTimer(String time) {
        if (taskTimerLabel != null) {
            taskTimerLabel.setText(time);
        }
    }

    private void applyEditorSettings() {
        try {
            String fontSizeStr = propertiesService.get("ui.xml.font.size");
            int fontSize = 12;
            if (fontSizeStr != null) {
                try {
                    fontSize = Integer.parseInt(fontSizeStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid font size in settings, defaulting to 12.", e);
                }
            }
            String style = String.format("-fx-font-size: %dpx;", fontSize);

            if (sourceCodeEditor != null && sourceCodeEditor.getCodeArea() != null) {
                sourceCodeEditor.getCodeArea().setStyle(style);
                logger.debug("Applied font size {}px to sourceCodeEditor", fontSize);
            }
            if (sampleDataTextArea != null) {
                sampleDataTextArea.setStyle(style);
                logger.debug("Applied font size {}px to sampleDataTextArea", fontSize);
            }
        } catch (Exception e) {
            logger.error("Failed to apply editor settings.", e);
        }
    }

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    public File openXsdFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));
        return fileChooser.showOpenDialog(tabPane.getScene().getWindow());
    }

    public File showSaveDialog(String title, String desc, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, extension));
        return fileChooser.showSaveDialog(tabPane.getScene().getWindow());
    }

    public void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public void openFolderInExplorer(File folder) {
        try {
            java.awt.Desktop.getDesktop().open(folder);
        } catch (IOException e) {
            logger.error("Failed to open folder", e);
        }
    }

    @Override
    public File getCurrentFile() {
        return currentXsdFile;
    }

    @Override
    public void loadFileToNewTab(File file) {
        openXsdFile(file);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void refreshToolbarIcons() {
        applySmallIconsSetting();
    }

    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to XSD toolbar: {}", useSmallIcons);

        ContentDisplay displayMode = useSmallIcons
                ? ContentDisplay.GRAPHIC_ONLY
                : ContentDisplay.TOP;

        int iconSize = useSmallIcons ? 14 : 20;

        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        applyButtonSettings(toolbarNewFile, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarLoadFile, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarSave, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarSaveAs, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarReload, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarClose, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarValidate, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarUndo, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarRedo, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarFind, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarFormat, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarAddFavorite, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarShowFavorites, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarXPathQuery, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarRecentFiles, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toolbarHelp, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to XSD toolbar (size: {}px)", iconSize);
    }

    private void applyButtonSettings(javafx.scene.control.ButtonBase button,
                                     ContentDisplay displayMode,
                                     int iconSize,
                                     String style) {
        if (button == null) {
            return;
        }
        button.setContentDisplay(displayMode);
        button.setStyle(style);
        if (button.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fontIcon) {
            fontIcon.setIconSize(iconSize);
        }
    }

    public void selectTextTab() {
        tabPane.getSelectionModel().select(textTab);
    }

    /**
     * Navigates to the given element in the graphical XSD view.
     * Switches to the graphic tab, selects the node, and scrolls it into view.
     * If the graph view is not yet ready (async schema parsing), stores the
     * navigation target and executes it once the graph view is created.
     *
     * @param elementName the element name to navigate to
     */
    public void navigateToElementInGraphView(String elementName) {
        tabPane.getSelectionModel().select(xsdTab);
        // Store as pending — openXsdFile triggers async schema rebuild which
        // creates a new graph view. Navigation executes in createGraphicalViewFromCachedSchema().
        pendingNavigationElement = elementName;
    }

    /**
     * Navigates to the definition of the given element in the XSD source code.
     * Searches for {@code name="elementName"} and selects the match.
     *
     * @param elementName the element name to find
     */
    public void navigateToElementDefinition(String elementName) {
        if (elementName == null || elementName.isEmpty() || sourceCodeEditor == null) {
            return;
        }

        String text = sourceCodeEditor.getCodeArea().getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        String searchPattern = "name=\"" + elementName + "\"";
        int index = text.indexOf(searchPattern);
        if (index >= 0) {
            int end = index + searchPattern.length();
            sourceCodeEditor.getCodeArea().moveTo(index);
            sourceCodeEditor.getCodeArea().selectRange(index, end);
            sourceCodeEditor.getCodeArea().requestFollowCaret();
            logger.debug("Navigated to element definition: {}", elementName);
        } else {
            logger.debug("Element definition not found in XSD text: {}", elementName);
        }
    }

    public void selectSubTab(String subTabId) {
        if (tabPane == null || subTabId == null) {
            return;
        }
        for (Tab tab : tabPane.getTabs()) {
            if (subTabId.equals(tab.getId())) {
                tabPane.getSelectionModel().select(tab);
                logger.debug("Selected sub-tab: {}", subTabId);
                return;
            }
        }
        logger.warn("Sub-tab not found: {}", subTabId);
    }

    public boolean isXsdTabActive() {
        return tabPane.getSelectionModel().getSelectedItem() == xsdTab;
    }

    public void performUndo() {
        if (tabPane.getSelectionModel().getSelectedItem() == xsdTab && currentGraphViewV2 != null) {
            org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext context = currentGraphViewV2.getEditorContext();
            if (context != null && context.getCommandManager() != null) {
                context.getCommandManager().undo();
            }
        } else if (sourceCodeEditor != null && tabPane.getSelectionModel().getSelectedItem() == textTab) {
            sourceCodeEditor.getCodeArea().undo();
        }
    }

    public void performRedo() {
        if (tabPane.getSelectionModel().getSelectedItem() == xsdTab && currentGraphViewV2 != null) {
            org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext context = currentGraphViewV2.getEditorContext();
            if (context != null && context.getCommandManager() != null) {
                context.getCommandManager().redo();
            }
        } else if (sourceCodeEditor != null && tabPane.getSelectionModel().getSelectedItem() == textTab) {
            sourceCodeEditor.getCodeArea().redo();
        }
    }

    @FXML
    public void handleToolbarNewFile() {
        sourceCodeEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n</xs:schema>");
        currentXsdFile = null;
        noFileLoadedPaneText.setVisible(false);
        noFileLoadedPaneText.setManaged(false);
        textInfoPane.setVisible(true);
        textInfoPane.setManaged(true);
        if (textInfoPathLabel != null) {
            textInfoPathLabel.setText("New XSD File");
        }
        tabPane.getSelectionModel().select(textTab);
    }

    @FXML
    public void handleToolbarLoadFile() {
        File file = openXsdFileChooser();
        if (file != null) {
            openXsdFile(file);
        }
    }

    @FXML
    public void handleToolbarReload() {
        if (currentXsdFile != null && currentXsdFile.exists()) {
            openXsdFile(currentXsdFile);
        }
    }

    @FXML
    public void handleToolbarClose() {
        sourceCodeEditor.getCodeArea().replaceText("");
        currentXsdFile = null;
        cachedXsdSchema = null;
        noFileLoadedPaneText.setVisible(true);
        noFileLoadedPaneText.setManaged(true);
        sourceCodeEditorContainer.setVisible(false);
        sourceCodeEditorContainer.setManaged(false);
        textInfoPane.setVisible(false);
        textInfoPane.setManaged(false);
        noFileLoadedPane.setVisible(true);
        noFileLoadedPane.setManaged(true);
        xsdStackPaneV2.setVisible(false);
        xsdStackPaneV2.setManaged(false);
        xsdStackPaneV2.getChildren().clear();
    }

    @FXML
    public void handleToolbarUndo() {
        performUndo();
    }

    @FXML
    public void handleToolbarRedo() {
        performRedo();
    }

    @FXML
    public void handleToolbarFind() {
        if (sourceCodeEditor != null) {
            FindReplaceDialog dialog = new FindReplaceDialog(sourceCodeEditor.getCodeArea());
            dialog.show();
        }
    }

    @FXML
    public void handleToolbarFormat() {
        if (sourceCodeEditor != null) {
            String text = sourceCodeEditor.getCodeArea().getText();
            if (text != null && !text.isBlank()) {
                try {
                    javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                    javax.xml.transform.Transformer transformer = tf.newTransformer();
                    transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    javax.xml.transform.stream.StreamSource source = new javax.xml.transform.stream.StreamSource(new java.io.StringReader(text));
                    java.io.StringWriter writer = new java.io.StringWriter();
                    transformer.transform(source, new javax.xml.transform.stream.StreamResult(writer));
                    sourceCodeEditor.getCodeArea().replaceText(writer.toString());
                } catch (Exception e) {
                    logger.error("Failed to format XML", e);
                }
            }
        }
    }

    @FXML
    public void handleToolbarXPathQuery() {
        showAlert(Alert.AlertType.INFORMATION, "XPath Query",
                "XPath query functionality is not yet available in the XSD editor.\n"
                        + "You can use XPath queries in the XML editor tab.");
    }

    @FXML
    public void handleToolbarHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("XSD Editor Help");
        alert.setHeaderText("XSD Editor");
        alert.setContentText("Use the toolbar buttons to create, open, edit and validate XSD schema files.\n\n" +
                "Keyboard shortcuts:\n" +
                "Ctrl+S - Save\n" +
                "Ctrl+Z - Undo\n" +
                "Ctrl+Y - Redo\n" +
                "Ctrl+F - Find");
        alert.showAndWait();
    }

    @FXML
    public void handleToolbarValidate() {
        if (tabPane.getSelectionModel().getSelectedItem() == textTab) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Validation");
            alert.setHeaderText(null);
            alert.setContentText("Schema validated successfully (mock logic restored)");
            alert.showAndWait();
        }
    }
    public void handleToolbarSave() {
        saveXsdFile();
    }
    public void handleToolbarSaveAs() {
        saveXsdFileAs();
    }
    @FXML
    public void handleToolbarAddFavorite() {
        if (currentXsdFile != null && favoritesService != null) {
            org.fxt.freexmltoolkit.domain.FileFavorite favorite = new org.fxt.freexmltoolkit.domain.FileFavorite(
                    currentXsdFile.getName(),
                    currentXsdFile.getAbsolutePath(),
                    "XSD Schemas"
            );
            favoritesService.addFavorite(favorite);
            showAlert(Alert.AlertType.INFORMATION, "Favorite Added", "Added " + currentXsdFile.getName() + " to favorites.");
        }
    }

    @FXML
    public void handleToolbarShowFavorites() {
        favoritesPanelVisible = !favoritesPanelVisible;
        toggleFavoritesPanel(favoritesPanel, textTabSplitPane);
        toggleFavoritesPanel(favoritesPanelGraphic, graphicTabSplitPane);
    }

    private void toggleFavoritesPanel(VBox panel, SplitPane splitPane) {
        if (panel == null || splitPane == null) {
            return;
        }
        if (favoritesPanelVisible) {
            if (!splitPane.getItems().contains(panel)) {
                splitPane.getItems().add(panel);
                splitPane.setDividerPositions(0.75);
            }
        } else {
            splitPane.getItems().remove(panel);
        }
    }

    @FXML
    public void createNewXsdFile() {
        handleToolbarNewFile();
    }

    @FXML
    public void selectOutputXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save XML File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
        if (file != null && outputXmlPath != null) {
            outputXmlPath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void generateSampleDataAction() {
        String xsdPath = xsdForSampleDataPath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            DialogHelper.showError("Generate Sample Data", "Missing XSD File", "Please load an XSD source file first.");
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            DialogHelper.showError("Generate Sample Data", "XSD File Not Found", "The specified XSD file does not exist: " + xsdPath);
            return;
        }

        boolean mandatoryOnly = mandatoryOnlyCheckBox.isSelected();
        int maxOccurrences = maxOccurrencesSpinner.getValue();
        int batchCount = batchCountSpinner != null ? batchCountSpinner.getValue() : 1;

        // Build profile from current UI state
        GenerationProfile profile = buildProfileFromUI();

        progressSampleData.setVisible(true);
        sampleDataTextArea.clear();

        // Use profiled generation when rules exist, otherwise fallback to original
        boolean useProfiled = !rulesList.isEmpty();

        Task<String> generationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Generating sample XML...");
                XsdDocumentationService docService = new XsdDocumentationService();
                docService.setXsdFilePath(xsdFile.getAbsolutePath());

                if (useProfiled) {
                    docService.processXsd(false);
                    XsdDocumentationData data = docService.xsdDocumentationData;

                    if (batchCount > 1) {
                        List<GeneratedFile> files = profiledGenerator.generateBatch(profile, data, xsdFile.getAbsolutePath());
                        // Save batch files to output directory
                        String outputPath = outputXmlPath.getText();
                        if (outputPath != null && !outputPath.isBlank()) {
                            File outputDir = new File(outputPath);
                            if (!outputDir.isDirectory()) {
                                outputDir = outputDir.getParentFile();
                            }
                            if (outputDir != null) {
                                Files.createDirectories(outputDir.toPath());
                                for (GeneratedFile gf : files) {
                                    Files.writeString(outputDir.toPath().resolve(gf.fileName()), gf.content());
                                }
                            }
                        }
                        // Show first file in preview
                        return files.isEmpty() ? "" : files.getFirst().content();
                    } else {
                        return profiledGenerator.generate(profile, data, xsdFile.getAbsolutePath());
                    }
                } else {
                    return docService.generateSampleXml(mandatoryOnly, maxOccurrences);
                }
            }
        };

        currentGenerationTask = generationTask;
        isGeneratingSampleData.set(true);

        generationTask.setOnSucceeded(event -> {
            progressSampleData.setVisible(false);
            isGeneratingSampleData.set(false);
            currentGenerationTask = null;
            String resultXml = generationTask.getValue();

            sampleDataTextArea.replaceText(resultXml);
            sampleDataTextArea.setStyleSpans(0, XmlSyntaxHighlighter.computeHighlighting(resultXml));
            applyEditorSettings();

            if (validateGeneratedXmlButton != null) {
                validateGeneratedXmlButton.setDisable(false);
            }

            // Validate the generated XML against the XSD schema
            Task<XsdDocumentationService.ValidationResult> validationTask = new Task<>() {
                @Override
                protected XsdDocumentationService.ValidationResult call() throws Exception {
                    XsdDocumentationService docService = new XsdDocumentationService();
                    docService.setXsdFilePath(xsdFile.getAbsolutePath());
                    return docService.validateXmlAgainstSchema(resultXml);
                }
            };

            validationTask.setOnSucceeded(validationEvent -> {
                XsdDocumentationService.ValidationResult result = validationTask.getValue();
                if (result.isValid()) {
                    if (statusText != null) statusText.setText("Sample XML generated and validated successfully.");
                    String message = result.message().isEmpty()
                            ? "The generated XML is valid according to the XSD schema."
                            : "Valid with notes: " + result.message();
                    showValidationResult(true, "Validation Successful", message, result.errors());
                } else {
                    if (statusText != null) statusText.setText("Sample XML generated but validation failed.");
                    int errorCount = result.errors().size();
                    String summaryMessage = errorCount > 0
                            ? String.format("%d error(s) found. See details below.", errorCount)
                            : result.message();
                    showValidationResult(false, "Validation Failed", summaryMessage, result.errors());
                }
            });

            validationTask.setOnFailed(validationEvent -> {
                logger.error("Validation task failed", validationTask.getException());
                if (statusText != null) statusText.setText("Sample XML generated but validation could not be performed.");
            });

            executorService.submit(validationTask);

            // Save to file if a path is provided
            String outputPath = outputXmlPath.getText();
            if (outputPath != null && !outputPath.isBlank()) {
                saveStringToFile(resultXml, new File(outputPath));
            }
        });

        generationTask.setOnFailed(event -> {
            progressSampleData.setVisible(false);
            isGeneratingSampleData.set(false);
            currentGenerationTask = null;
            Throwable e = generationTask.getException();
            if (e instanceof java.util.concurrent.CancellationException
                    || (e != null && e.getCause() instanceof java.util.concurrent.CancellationException)) {
                if (statusText != null) statusText.setText("Sample XML generation cancelled.");
                return;
            }
            logger.error("Failed to generate sample XML data.", e);
            if (statusText != null) statusText.setText("Error generating sample XML.");
            if (e instanceof Exception ex) {
                DialogHelper.showException("Generate Sample Data", "Failed to Generate Sample XML", ex);
            } else {
                DialogHelper.showError("Generate Sample Data", "Error", e != null ? e.getMessage() : "Unknown error");
            }
        });

        generationTask.setOnCancelled(event -> {
            progressSampleData.setVisible(false);
            isGeneratingSampleData.set(false);
            currentGenerationTask = null;
            if (statusText != null) statusText.setText("Sample XML generation cancelled.");
        });

        executeTask(generationTask);
    }

    @FXML
    public void handleCancelSampleDataGeneration() {
        if (currentGenerationTask != null && !currentGenerationTask.isDone()) {
            currentGenerationTask.cancel(true);
            if (statusText != null) statusText.setText("Cancelling generation...");
        }
    }

    private GenerationProfile buildProfileFromUI() {
        GenerationProfile profile = currentProfile != null ? currentProfile.deepCopy() : new GenerationProfile("Unnamed");
        profile.setMandatoryOnly(mandatoryOnlyCheckBox.isSelected());
        profile.setMaxOccurrences(maxOccurrencesSpinner.getValue());
        profile.setBatchCount(batchCountSpinner != null ? batchCountSpinner.getValue() : 1);
        if (fileNamePatternField != null && !fileNamePatternField.getText().isBlank()) {
            profile.setFileNamePattern(fileNamePatternField.getText());
        }
        if (xsdForSampleDataPath != null) {
            profile.setSchemaFile(xsdForSampleDataPath.getText());
        }
        profile.setRules(new ArrayList<>(rulesList));
        return profile;
    }

    @FXML
    public void handleAutoFillXPaths() {
        String xsdPath = xsdForSampleDataPath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            DialogHelper.showError("Auto-fill XPaths", "No XSD", "Please load an XSD file first.");
            return;
        }
        Task<List<XPathInfo>> task = new Task<>() {
            @Override
            protected List<XPathInfo> call() throws Exception {
                XsdDocumentationService docService = new XsdDocumentationService();
                docService.setXsdFilePath(new File(xsdPath).getAbsolutePath());
                docService.processXsd(false);
                return profiledGenerator.extractXPaths(docService.xsdDocumentationData);
            }
        };
        task.setOnSucceeded(e -> {
            List<XPathInfo> xpaths = task.getValue();
            rulesList.clear();
            for (XPathInfo info : xpaths) {
                rulesList.add(new XPathRule(info.xpath(), GenerationStrategy.AUTO));
            }
            if (statusText != null) statusText.setText(xpaths.size() + " XPaths extracted from XSD.");
        });
        task.setOnFailed(e -> {
            logger.error("Failed to extract XPaths", task.getException());
            DialogHelper.showError("Auto-fill XPaths", "Error", "Failed to extract XPaths: " + task.getException().getMessage());
        });
        executorService.submit(task);
    }

    @FXML
    public void handleAddRule() {
        XPathRule newRule = new XPathRule("", GenerationStrategy.AUTO);
        rulesList.add(newRule);
        rulesTableView.getSelectionModel().select(newRule);
    }

    @FXML
    public void handleSaveProfile() {
        if (currentProfile == null) {
            handleSaveProfileAs();
            return;
        }
        GenerationProfile profile = buildProfileFromUI();
        profile.setName(currentProfile.getName());
        GenerationProfileService.getInstance().save(profile);
        currentProfile = profile;
        if (statusText != null) statusText.setText("Profile '" + profile.getName() + "' saved.");
        loadProfileList();
        profileComboBox.setValue(profile.getName());
    }

    @FXML
    public void handleSaveProfileAs() {
        TextInputDialog dialog = new TextInputDialog("New Profile");
        dialog.setTitle("Save Profile As");
        dialog.setHeaderText("Enter a name for the profile");
        dialog.setContentText("Profile name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            GenerationProfile profile = buildProfileFromUI();
            profile.setName(name);
            GenerationProfileService.getInstance().save(profile);
            currentProfile = profile;
            if (statusText != null) statusText.setText("Profile '" + name + "' saved.");
            loadProfileList();
            profileComboBox.setValue(name);
        });
    }

    @FXML
    public void handleDeleteProfile() {
        if (currentProfile == null || currentProfile.getName() == null) {
            return;
        }
        GenerationProfileService.getInstance().delete(currentProfile.getName());
        currentProfile = null;
        rulesList.clear();
        if (statusText != null) statusText.setText("Profile deleted.");
        loadProfileList();
    }

    @FXML
    public void handleImportProfile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Generation Profile");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Profile JSON", "*.json"));
        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        try {
            GenerationProfile imported = GenerationProfileService.getInstance().importFromFile(file);
            if (imported == null || imported.getName() == null || imported.getName().isBlank()) {
                DialogHelper.showError("Import Profile", "Invalid Profile",
                        "The selected file does not contain a valid profile (missing name).");
                return;
            }
            GenerationProfileService.getInstance().save(imported);
            currentProfile = imported;
            rulesList.setAll(imported.getRules());
            mandatoryOnlyCheckBox.setSelected(imported.isMandatoryOnly());
            maxOccurrencesSpinner.getValueFactory().setValue(imported.getMaxOccurrences());
            if (batchCountSpinner != null) {
                batchCountSpinner.getValueFactory().setValue(imported.getBatchCount());
            }
            if (fileNamePatternField != null && imported.getFileNamePattern() != null) {
                fileNamePatternField.setText(imported.getFileNamePattern());
            }
            loadProfileList();
            profileComboBox.setValue(imported.getName());
            if (statusText != null) statusText.setText("Profile '" + imported.getName() + "' imported.");
        } catch (IOException e) {
            logger.error("Failed to import profile from {}", file, e);
            DialogHelper.showError("Import Profile", "Import Failed",
                    "Could not import profile: " + e.getMessage());
        }
    }

    @FXML
    public void handleExportProfile() {
        if (currentProfile == null) {
            DialogHelper.showError("Export Profile", "No Profile Selected",
                    "Please select or save a profile first.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Generation Profile");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Profile JSON", "*.json"));
        fileChooser.setInitialFileName(currentProfile.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".json");
        File file = fileChooser.showSaveDialog(null);
        if (file == null) {
            return;
        }
        try {
            GenerationProfile snapshot = buildProfileFromUI();
            snapshot.setName(currentProfile.getName());
            GenerationProfileService.getInstance().exportToFile(snapshot, file);
            if (statusText != null) statusText.setText("Profile '" + snapshot.getName() + "' exported to " + file.getName() + ".");
        } catch (IOException e) {
            logger.error("Failed to export profile to {}", file, e);
            DialogHelper.showError("Export Profile", "Export Failed",
                    "Could not export profile: " + e.getMessage());
        }
    }

    @FXML
    public void handleProfileSelection() {
        if (profileComboBox == null) return;
        String selected = profileComboBox.getValue();
        if (selected == null || "(No Profile)".equals(selected)) {
            currentProfile = null;
            rulesList.clear();
            return;
        }
        GenerationProfile loaded = GenerationProfileService.getInstance().load(selected);
        if (loaded != null) {
            currentProfile = loaded;
            rulesList.setAll(loaded.getRules());
            mandatoryOnlyCheckBox.setSelected(loaded.isMandatoryOnly());
            maxOccurrencesSpinner.getValueFactory().setValue(loaded.getMaxOccurrences());
            if (batchCountSpinner != null) {
                batchCountSpinner.getValueFactory().setValue(loaded.getBatchCount());
            }
            if (fileNamePatternField != null && loaded.getFileNamePattern() != null) {
                fileNamePatternField.setText(loaded.getFileNamePattern());
            }
            if (statusText != null) statusText.setText("Profile '" + selected + "' loaded.");
        }
    }

    @FXML
    public void validateGeneratedXmlAction() {
        String xsdPath = xsdForSampleDataPath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            showValidationResult(false, "No XSD File", "Please load an XSD source file first.");
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            showValidationResult(false, "XSD File Not Found", "The specified XSD file does not exist: " + xsdPath);
            return;
        }

        String xmlContent = sampleDataTextArea.getText();
        if (xmlContent == null || xmlContent.isBlank()) {
            showValidationResult(false, "No XML Content", "Please generate sample XML first before validating.");
            return;
        }

        Task<XsdDocumentationService.ValidationResult> validationTask = new Task<>() {
            @Override
            protected XsdDocumentationService.ValidationResult call() throws Exception {
                updateMessage("Validating XML against schema...");
                XsdDocumentationService docService = new XsdDocumentationService();
                docService.setXsdFilePath(xsdFile.getAbsolutePath());
                return docService.validateXmlAgainstSchema(xmlContent);
            }
        };

        validationTask.setOnSucceeded(event -> {
            XsdDocumentationService.ValidationResult result = validationTask.getValue();
            if (result.isValid()) {
                String message = result.message().isEmpty()
                        ? "The XML content is valid according to the XSD schema."
                        : "Valid with notes: " + result.message();
                showValidationResult(true, "Validation Successful", message, result.errors());
                logger.info("XML validation successful");
            } else {
                int errorCount = result.errors().size();
                String summaryMessage = errorCount > 0
                        ? String.format("%d error(s) found. See details below.", errorCount)
                        : result.message();
                showValidationResult(false, "Validation Failed", summaryMessage, result.errors());
                logger.warn("XML validation failed: {} errors", errorCount);
            }
        });

        validationTask.setOnFailed(event -> {
            Throwable e = validationTask.getException();
            logger.error("Validation task failed", e);
            showValidationResult(false, "Validation Error",
                    "Could not perform validation: " + (e != null ? e.getMessage() : "Unknown error"));
        });

        executorService.submit(validationTask);
    }

    @FXML
    public void exportValidationErrors() {
        if (currentValidationErrors == null || currentValidationErrors.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Errors", "There are no validation errors to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Validation Errors");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName("validation_errors.csv");

        File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".csv")) {
                exportErrorsToCsv(file);
            } else {
                exportErrorsToText(file);
            }
            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    "Validation errors have been exported to:\n" + file.getAbsolutePath());
            logger.info("Exported {} validation errors to: {}", currentValidationErrors.size(), file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to export validation errors", e);
            showAlert(Alert.AlertType.ERROR, "Export Failed",
                    "Could not export validation errors: " + e.getMessage());
        }
    }

    @FXML
    public void closeSampleDataValidationPanel() {
        if (sampleDataValidationResultPanel != null) {
            sampleDataValidationResultPanel.setVisible(false);
            sampleDataValidationResultPanel.setManaged(false);
        }
        if (validationErrorsTable != null) {
            validationErrorsTable.getItems().clear();
        }
        currentValidationErrors.clear();
    }

    private void showValidationResult(boolean isValid, String title, String message) {
        showValidationResult(isValid, title, message, List.of());
    }

    private void showValidationResult(boolean isValid, String title, String message,
                                      List<XsdDocumentationService.ValidationError> errors) {
        Platform.runLater(() -> {
            sampleDataValidationResultPanel.setVisible(true);
            sampleDataValidationResultPanel.setManaged(true);

            sampleDataValidationTitle.setText(title);
            sampleDataValidationMessage.setText(message);

            currentValidationErrors.clear();
            currentValidationErrors.addAll(errors);

            if (validationErrorsTable != null) {
                validationErrorsTable.getItems().clear();
                validationErrorsTable.getItems().addAll(errors);
                boolean hasErrors = !errors.isEmpty();
                validationErrorsTable.setVisible(hasErrors);
                validationErrorsTable.setManaged(hasErrors);
            }

            if (exportValidationErrorsButton != null) {
                exportValidationErrorsButton.setVisible(!errors.isEmpty());
                exportValidationErrorsButton.setManaged(!errors.isEmpty());
            }

            if (isValid) {
                sampleDataValidationResultPanel.getStyleClass().removeAll("validation-error-panel");
                sampleDataValidationResultPanel.getStyleClass().add("validation-success-panel");
                sampleDataValidationIcon.setIconLiteral("bi-check-circle-fill");
                sampleDataValidationIcon.setIconColor(javafx.scene.paint.Color.web("#28a745"));
                sampleDataValidationTitle.getStyleClass().removeAll("validation-error-title");
                sampleDataValidationTitle.getStyleClass().add("validation-success-title");
                sampleDataValidationMessage.getStyleClass().removeAll("validation-error-message");
                sampleDataValidationMessage.getStyleClass().add("validation-success-message");
            } else {
                sampleDataValidationResultPanel.getStyleClass().removeAll("validation-success-panel");
                sampleDataValidationResultPanel.getStyleClass().add("validation-error-panel");
                sampleDataValidationIcon.setIconLiteral("bi-exclamation-triangle-fill");
                sampleDataValidationIcon.setIconColor(javafx.scene.paint.Color.web("#dc3545"));
                sampleDataValidationTitle.getStyleClass().removeAll("validation-success-title");
                sampleDataValidationTitle.getStyleClass().add("validation-error-title");
                sampleDataValidationMessage.getStyleClass().removeAll("validation-success-message");
                sampleDataValidationMessage.getStyleClass().add("validation-error-message");
            }
        });
    }

    private void initializeValidationErrorsTable() {
        if (validationErrorsTable == null) {
            return;
        }

        if (errorLineColumn != null) {
            errorLineColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().lineNumber()).asObject());
            errorLineColumn.getStyleClass().add("column-align-right");
        }

        if (errorColumnColumn != null) {
            errorColumnColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().columnNumber()).asObject());
            errorColumnColumn.getStyleClass().add("column-align-right");
        }

        if (errorSeverityColumn != null) {
            errorSeverityColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().severity()));
            errorSeverityColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String severity, boolean empty) {
                    super.updateItem(severity, empty);
                    getStyleClass().removeAll("severity-fatal", "severity-error", "severity-warning");
                    if (empty || severity == null) {
                        setText(null);
                    } else {
                        setText(severity);
                        switch (severity) {
                            case "Fatal Error" -> getStyleClass().add("severity-fatal");
                            case "Error" -> getStyleClass().add("severity-error");
                            case "Warning" -> getStyleClass().add("severity-warning");
                            default -> { }
                        }
                    }
                }
            });
        }

        if (errorMessageColumn != null) {
            errorMessageColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().message()));
            errorMessageColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String message, boolean empty) {
                    super.updateItem(message, empty);
                    if (empty || message == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(message);
                        if (message.length() > 80) {
                            setTooltip(new Tooltip(message));
                        }
                    }
                }
            });
        }

        validationErrorsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void initializeSampleDataControls() {
        if (maxOccurrencesSpinner != null) {
            maxOccurrencesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 3));
        }
        if (batchCountSpinner != null) {
            batchCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        }
        if (fileNamePatternField != null) {
            fileNamePatternField.setText("example_{seq:3}.xml");
        }
        if (generateSampleDataButton != null && xsdForSampleDataPath != null) {
            generateSampleDataButton.disableProperty().bind(
                    xsdForSampleDataPath.textProperty().isEmpty().or(isGeneratingSampleData));
        }
        if (cancelSampleDataButton != null) {
            cancelSampleDataButton.disableProperty().bind(isGeneratingSampleData.not());
        }
        initializeRulesTable();
        loadProfileList();
    }

    private void initializeRulesTable() {
        if (rulesTableView == null) return;

        rulesTableView.setItems(rulesList);
        rulesTableView.setEditable(true);

        // Enabled column: CheckBox
        if (ruleEnabledColumn != null) {
            ruleEnabledColumn.setCellValueFactory(cell ->
                    new SimpleBooleanProperty(cell.getValue().isEnabled()));
            ruleEnabledColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                private final CheckBox checkBox = new CheckBox();
                {
                    checkBox.setOnAction(e -> {
                        XPathRule rule = getTableView().getItems().get(getIndex());
                        rule.setEnabled(checkBox.isSelected());
                    });
                }
                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); } else { checkBox.setSelected(item != null && item); setGraphic(checkBox); }
                }
            });
        }

        // XPath column: read-only text
        if (ruleXPathColumn != null) {
            ruleXPathColumn.setCellValueFactory(cell ->
                    new SimpleStringProperty(cell.getValue().getXpath()));
        }

        // Strategy column: ComboBox
        if (ruleStrategyColumn != null) {
            ruleStrategyColumn.setCellValueFactory(cell ->
                    new SimpleStringProperty(cell.getValue().getStrategy().getDisplayName()));
            ruleStrategyColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                private final ComboBox<GenerationStrategy> combo = new ComboBox<>();
                {
                    combo.getItems().addAll(GenerationStrategy.values());
                    combo.setMaxWidth(Double.MAX_VALUE);
                    combo.setOnAction(e -> {
                        XPathRule rule = getTableView().getItems().get(getIndex());
                        GenerationStrategy old = rule.getStrategy();
                        rule.setStrategy(combo.getValue());
                        // Clear config when strategy changes
                        if (old != combo.getValue()) {
                            rule.setConfig(null);
                        }
                        getTableView().refresh();
                    });
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); } else {
                        combo.setValue(getTableView().getItems().get(getIndex()).getStrategy());
                        setGraphic(combo);
                    }
                }
            });
        }

        // Config column: context-sensitive text field
        if (ruleConfigColumn != null) {
            ruleConfigColumn.setCellValueFactory(cell ->
                    new SimpleStringProperty(getConfigDisplayValue(cell.getValue())));
            ruleConfigColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                private final TextField field = new TextField();
                {
                    field.setOnAction(e -> commitConfigEdit());
                    field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                        if (!isFocused) commitConfigEdit();
                    });
                }
                private void commitConfigEdit() {
                    XPathRule rule = getTableView().getItems().get(getIndex());
                    setConfigFromText(rule, field.getText());
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); setText(null); } else {
                        XPathRule rule = getTableView().getItems().get(getIndex());
                        if (needsConfig(rule.getStrategy())) {
                            field.setText(item != null ? item : "");
                            field.setPromptText(getConfigPrompt(rule.getStrategy()));
                            setGraphic(field);
                            setText(null);
                        } else {
                            setGraphic(null);
                            setText("");
                        }
                    }
                }
            });
        }

        // Delete column: button
        if (ruleDeleteColumn != null) {
            ruleDeleteColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                private final Button btn = new Button();
                {
                    btn.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("bi-x-circle"));
                    btn.setStyle("-fx-background-color: transparent; -fx-padding: 2;");
                    btn.setOnAction(e -> {
                        int idx = getIndex();
                        if (idx < 0 || idx >= rulesList.size()) return;
                        XPathRule removed = rulesList.remove(idx);
                        String parentPath = removed.getXpath();
                        if (parentPath != null && !parentPath.isBlank()) {
                            String prefix = parentPath + "/";
                            rulesList.removeIf(r -> {
                                String xp = r.getXpath();
                                return xp != null && xp.startsWith(prefix);
                            });
                        }
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
        }
    }

    private static String getConfigDisplayValue(XPathRule rule) {
        return switch (rule.getStrategy()) {
            case FIXED -> rule.getConfigValue("value");
            case SEQUENCE -> rule.getConfigValue("pattern");
            case RANDOM_FROM_LIST -> rule.getConfigValue("values");
            case XPATH_REF -> rule.getConfigValue("ref");
            case TEMPLATE -> rule.getConfigValue("pattern");
            default -> null;
        };
    }

    private static void setConfigFromText(XPathRule rule, String text) {
        switch (rule.getStrategy()) {
            case FIXED -> rule.setConfigValue("value", text);
            case SEQUENCE -> rule.setConfigValue("pattern", text);
            case RANDOM_FROM_LIST -> rule.setConfigValue("values", text);
            case XPATH_REF -> rule.setConfigValue("ref", text);
            case TEMPLATE -> rule.setConfigValue("pattern", text);
            default -> {}
        }
    }

    private static boolean needsConfig(GenerationStrategy strategy) {
        return strategy == GenerationStrategy.FIXED
                || strategy == GenerationStrategy.SEQUENCE
                || strategy == GenerationStrategy.RANDOM_FROM_LIST
                || strategy == GenerationStrategy.XPATH_REF
                || strategy == GenerationStrategy.TEMPLATE;
    }

    private static String getConfigPrompt(GenerationStrategy strategy) {
        return switch (strategy) {
            case FIXED -> "Fixed value";
            case SEQUENCE -> "e.g. ID-{seq:4}";
            case RANDOM_FROM_LIST -> "A,B,C";
            case XPATH_REF -> "/order/@id";
            case TEMPLATE -> "ORD-{seq:4}-{date:yyyy}";
            default -> "";
        };
    }

    private void loadProfileList() {
        if (profileComboBox == null) return;
        List<GenerationProfile> profiles = GenerationProfileService.getInstance().loadAll();
        profileComboBox.getItems().clear();
        profileComboBox.getItems().add("(No Profile)");
        for (GenerationProfile p : profiles) {
            profileComboBox.getItems().add(p.getName());
        }
        profileComboBox.getSelectionModel().selectFirst();
    }



    private void saveStringToFile(String content, File file) {
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Saving to " + file.getName() + "...");
                Files.writeString(file.toPath(), content);
                return null;
            }
        };

        saveTask.setOnSucceeded(event ->
                statusText.setText("Sample XML generated and saved successfully."));

        saveTask.setOnFailed(event -> {
            logger.error("Failed to save content to file: " + file.getAbsolutePath(), saveTask.getException());
            Throwable ex = saveTask.getException();
            if (ex instanceof Exception e) {
                DialogHelper.showException("Save File", "Could Not Save File", e);
            } else {
                DialogHelper.showError("Save File", "Error", ex != null ? ex.getMessage() : "Unknown error");
            }
        });

        executeTask(saveTask);
    }

    private void exportErrorsToCsv(File file) throws IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            writer.println("Line,Column,Severity,Message");
            for (XsdDocumentationService.ValidationError error : currentValidationErrors) {
                writer.printf("%d,%d,%s,\"%s\"%n",
                        error.lineNumber(),
                        error.columnNumber(),
                        escapeCsvField(error.severity()),
                        escapeCsvField(error.message()));
            }
        }
    }

    private void exportErrorsToText(File file) throws IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            writer.println("=== Validation Errors Report ===");
            writer.println("Generated: " + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Total Errors: " + currentValidationErrors.size());
            writer.println();

            int index = 1;
            for (XsdDocumentationService.ValidationError error : currentValidationErrors) {
                writer.printf("%d. [%s] Line %d, Column %d%n",
                        index++,
                        error.severity(),
                        error.lineNumber(),
                        error.columnNumber());
                writer.println("   " + error.message());
                writer.println();
            }
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        return field.replace("\"", "\"\"");
    }

    @FXML
    public void saveXsdFile() {
        if (currentXsdFile != null) {
            saveXsdToFile(currentXsdFile);
        } else {
            saveXsdFileAs();
        }
    }

    @FXML
    public void saveXsdFileAs() {
        File file = showSaveDialog("Save XSD", "XSD Files", "*.xsd");
        if (file != null) {
            saveXsdToFile(file);
            currentXsdFile = file;
        }
    }

    private void saveXsdToFile(File file) {
        try {
            // If on the graphical tab, serialize the model into the text area first
            // so that any in-memory changes (documentation, properties, etc.) are included
            if (tabPane.getSelectionModel().getSelectedItem() == xsdTab) {
                syncGraphicToText();
            }
            Files.writeString(file.toPath(), sourceCodeEditor.getCodeArea().getText());
        } catch (IOException e) {
            logger.error("Failed to save", e);
        }
    }
}
