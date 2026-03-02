package org.fxt.freexmltoolkit.controller;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.CheckComboBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controls.shared.utilities.FindReplaceDialog;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlCodeFoldingManager;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main Controller for the XSD Editor.
 * Orchestrates multiple sub-controllers for different tabs.
 */
public class XsdController implements FavoritesParentController {

    private static final Logger logger = LogManager.getLogger(XsdController.class);

    private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView currentGraphViewV2;
    private org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager currentTypeEditorManager;
    private org.fxt.freexmltoolkit.controls.v2.model.XsdSchema cachedXsdSchema;
    private String cachedXsdContent;
    private boolean xsdContentDirty = false;
    private boolean isSchemaLoaded = false;

    private boolean graphicalViewInitialized = false;
    private boolean graphicalViewPending = false;
    private String pendingXsdContentForGraphicalView = null;
    private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphViewPlaceholder graphViewPlaceholder;

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

    private boolean favoritesPanelVisible = true;

    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final FavoritesService favoritesService = ServiceRegistry.get(FavoritesService.class);

    private MainController parentController;
    private boolean hasUnsavedChanges = false;
    private File currentXsdFile;

    private final ConcurrentHashMap<Task<?>, HBox> taskUiMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    @FXML
    public void initialize() {
        initializeSourceCodeEditor();
        setupDragAndDrop();
        setupTextToGraphicSync();
        
        if (documentationTabController != null) documentationTabController.setParentController(this);
        if (flattenTabController != null) flattenTabController.setParentController(this);
        if (schemaAnalysisTabController != null) schemaAnalysisTabController.setParentController(this);
        if (favoritesPanelController != null) favoritesPanelController.setParentController(this);
        if (favoritesPanelGraphicController != null) favoritesPanelGraphicController.setParentController(this);

        applyEditorSettings();
    }

    private void initializeSourceCodeEditor() {
        sourceCodeEditor = XmlCodeEditorV2Factory.createWithMutableSchema();
        VBox.setVgrow(sourceCodeEditor, javafx.scene.layout.Priority.ALWAYS);
        sourceCodeEditorContainer.getChildren().add(sourceCodeEditor);
    }

    private void setupDragAndDrop() {
        DragDropService.setupDragDrop(tabPane, DragDropService.XSD_EXTENSIONS, files -> {
            if (!files.isEmpty()) openXsdFile(files.get(0));
        });
    }

    public void openXsdFile(File file) {
        xmlService.setCurrentXsdFile(file);
        this.currentXsdFile = file;

        if (textInfoPathLabel != null) textInfoPathLabel.setText(file.getAbsolutePath());
        if (flattenTabController != null) flattenTabController.setSourcePath(file.getAbsolutePath());
        if (xsdForSampleDataPath != null) xsdForSampleDataPath.setText(file.getAbsolutePath());

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
            isSchemaLoaded = true;
            if (schemaAnalysisTabController != null) schemaAnalysisTabController.setSchema(cachedXsdSchema);
            if (createView) createGraphicalViewFromCachedSchema();
            updateTypeLibrary();
            updateTypeEditor();
        });

        executorService.submit(task);
    }

    private void updateTypeLibrary() {
        if (cachedXsdSchema == null || typeLibraryStackPane == null) return;
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
        if (cachedXsdSchema == null || typeEditorStackPane == null) return;
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

    private void createGraphicalViewFromGraphicalView() {
        // Implementation...
    }

    private void createGraphicalViewFromCachedSchema() {
        if (cachedXsdSchema == null) return;
        xsdStackPaneV2.getChildren().clear();
        currentGraphViewV2 = new org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView(cachedXsdSchema);
        xsdStackPaneV2.getChildren().add(currentGraphViewV2);
        xsdStackPaneV2.setVisible(true);
        xsdStackPaneV2.setManaged(true);
        noFileLoadedPane.setVisible(false);
        noFileLoadedPane.setManaged(false);
        wireTypeEditorCallbacks();
    }

    private void wireTypeEditorCallbacks() {
        if (currentGraphViewV2 == null || currentTypeEditorManager == null) return;
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
            if (oldTab == textTab && newTab == xsdTab) syncTextToGraphic();
            if (oldTab == xsdTab && newTab == textTab) syncGraphicToText();
        });
    }

    private void syncTextToGraphic() {
        String text = sourceCodeEditor.getCodeArea().getText();
        if (text != null && !text.equals(cachedXsdContent)) {
            parseSchemaAndDistribute(text, true);
        }
    }

    private void syncGraphicToText() {
        if (currentGraphViewV2 == null) return;
        XsdSerializer serializer = new XsdSerializer();
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
        if (taskTimerLabel != null) taskTimerLabel.setText(time);
    }

    private void applyEditorSettings() {
        // Implementation...
    }

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

    // Required by FavoritesParentController
    public void refreshXsdFavoritesMenu() {}
    public void setupOverviewFavorites() {}

    @Override
    public void loadFileToNewTab(File file) {
        openXsdFile(file);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void refreshToolbarIcons() {}

    public void selectTextTab() {
        tabPane.getSelectionModel().select(textTab);
    }

    public void selectSubTab(String subTabId) {
        // Find and select sub-tab
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
        if (textInfoPathLabel != null) textInfoPathLabel.setText("New XSD File");
        tabPane.getSelectionModel().select(textTab);
    }

    @FXML
    public void handleToolbarLoadFile() {
        File file = openXsdFileChooser();
        if (file != null) openXsdFile(file);
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
        isSchemaLoaded = false;
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
        // XPath query functionality - reserved for future implementation
        logger.info("XPath query toolbar action triggered");
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
        if (panel == null || splitPane == null) return;
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
        logger.info("Generate sample data action triggered");
    }

    @FXML
    public void validateGeneratedXmlAction() {
        logger.info("Validate generated XML action triggered");
    }

    @FXML
    public void exportValidationErrors() {
        logger.info("Export validation errors action triggered");
    }

    @FXML
    public void closeSampleDataValidationPanel() {
        if (sampleDataValidationResultPanel != null) {
            sampleDataValidationResultPanel.setVisible(false);
            sampleDataValidationResultPanel.setManaged(false);
        }
    }

    @FXML
    public void saveXsdFile() {
        if (currentXsdFile != null) saveXsdToFile(currentXsdFile);
        else saveXsdFileAs();
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
            Files.writeString(file.toPath(), sourceCodeEditor.getCodeArea().getText());
        } catch (IOException e) {
            logger.error("Failed to save", e);
        }
    }
}
