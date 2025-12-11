/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Advanced XSLT Developer - Revolutionary Feature #2
 * Professional XSLT 3.0 development with live preview and performance profiling
 */
public class XsltDeveloperController implements FavoritesParentController {
    private static final Logger logger = LogManager.getLogger(XsltDeveloperController.class);

    // Revolutionary Services - injected via ServiceRegistry
    private final XsltTransformationEngine xsltEngine = XsltTransformationEngine.getInstance();
    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("XsltDeveloper-Thread");
        return t;
    });

    // UI Components - Header Controls
    @FXML
    private ComboBox<String> xsltVersionCombo;
    @FXML
    private ToggleButton liveTransformToggle;
    @FXML
    private Button transformBtn;

    // UI Components - Input Section
    @FXML
    private TabPane inputTabPane;
    @FXML
    private StackPane xmlInputEditorPane;
    @FXML
    private StackPane xsltInputEditorPane;

    // XmlCodeEditor instances for enhanced editing
    private XmlCodeEditor xmlInputEditor;
    private XmlCodeEditor xsltInputEditor;
    
    @FXML
    private Button loadXmlBtn;
    @FXML
    private Button validateXmlBtn;
    @FXML
    private Button loadXsltBtn;
    @FXML
    private Button saveXsltBtn;
    @FXML
    private Button validateXsltBtn;

    // UI Components - Parameters (Grid Layout)
    @FXML
    private VBox parametersContainer;
    @FXML
    private ScrollPane parametersScrollPane;
    @FXML
    private Button addParameterBtn;

    private final ObservableList<XsltParameter> parameters = FXCollections.observableArrayList();

    // XSD 1.0 and 1.1 built-in types
    private static final List<String> XSD_TYPES = List.of(
            // Most commonly used types first
            "xs:string", "xs:boolean", "xs:integer", "xs:decimal", "xs:double", "xs:float",
            "xs:date", "xs:dateTime", "xs:time", "xs:duration",
            // Numeric types
            "xs:int", "xs:long", "xs:short", "xs:byte",
            "xs:nonPositiveInteger", "xs:negativeInteger", "xs:nonNegativeInteger", "xs:positiveInteger",
            "xs:unsignedLong", "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte",
            // String-derived types
            "xs:normalizedString", "xs:token", "xs:language", "xs:Name", "xs:NCName",
            "xs:ID", "xs:IDREF", "xs:IDREFS", "xs:ENTITY", "xs:ENTITIES", "xs:NMTOKEN", "xs:NMTOKENS",
            // Other types
            "xs:anyURI", "xs:QName", "xs:NOTATION", "xs:hexBinary", "xs:base64Binary",
            // Date/Time types
            "xs:gYear", "xs:gYearMonth", "xs:gMonth", "xs:gMonthDay", "xs:gDay",
            // XSD 1.1 types
            "xs:dateTimeStamp", "xs:yearMonthDuration", "xs:dayTimeDuration"
    );

    // UI Components - Output Configuration
    @FXML
    private ComboBox<String> outputFormatCombo;
    @FXML
    private ComboBox<String> encodingCombo;
    @FXML
    private CheckBox indentOutputCheckbox;

    // UI Components - Results
    @FXML
    private TabPane resultsTabPane;
    @FXML
    private TextArea transformationResultArea;
    @FXML
    private WebView previewWebView;
    @FXML
    private Button copyResultBtn;
    @FXML
    private Button saveResultBtn;
    @FXML
    private Button refreshPreviewBtn;
    @FXML
    private Label resultStatsLabel;

    // UI Components - Performance
    @FXML
    private Label executionTimeLabel;
    @FXML
    private Label compilationTimeLabel;
    @FXML
    private Label memoryUsageLabel;
    @FXML
    private Label outputSizeLabel;
    @FXML
    private ListView<String> featuresListView;
    @FXML
    private TextArea performanceReportArea;

    // UI Components - Debug
    @FXML
    private CheckBox enableDebugMode;
    @FXML
    private Button clearDebugBtn;
    @FXML
    private ListView<String> messagesListView;
    @FXML
    private TextArea traceArea;

    // UI Components - Favorites (unified FavoritesPanel)
    @FXML
    private Button addToFavoritesBtn;
    @FXML
    private Button toggleFavoritesButton;
    @FXML
    private SplitPane rightSplitPane;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private FavoritesPanelController favoritesPanelController;

    // Empty State
    @FXML
    private VBox emptyStatePane;
    @FXML
    private SplitPane contentPane;
    @FXML
    private Button emptyStateLoadXmlButton;
    @FXML
    private Button emptyStateLoadXsltButton;
    @FXML
    private Button helpBtn;

    // State Management
    private XsltTransformationResult lastResult;
    private final Map<String, String> currentParameters = new HashMap<>();
    private File currentXmlFile;
    private File currentXsltFile;

    @FXML
    private void initialize() {
        logger.info("Initializing Advanced XSLT Developer Controller - Revolutionary Feature #2");

        initializeUI();
        initializeEditors();
        setupEventHandlers();
        setDefaultValues();
        setupFavorites();
        initializeEmptyState();
        setupKeyboardShortcuts();
        setupDragAndDrop();

        // Apply small icons setting from user preferences
        applySmallIconsSetting();

        logger.info("XSLT Developer Controller initialized successfully");
    }

    /**
     * Set up drag and drop functionality for the XSLT Developer controller.
     * Accepts XML files for input and XSLT files for stylesheet.
     */
    private void setupDragAndDrop() {
        if (contentPane == null && emptyStatePane == null) {
            logger.warn("Cannot setup drag and drop: no valid container available");
            return;
        }

        // Set up drag and drop on both the empty state pane and content pane
        if (emptyStatePane != null) {
            DragDropService.setupDragDrop(emptyStatePane, DragDropService.XML_AND_XSLT, this::handleDroppedFiles);
        }
        if (contentPane != null) {
            DragDropService.setupDragDrop(contentPane, DragDropService.XML_AND_XSLT, this::handleDroppedFiles);
        }
        logger.debug("Drag and drop initialized for XSLT Developer controller");
    }

    /**
     * Handle files dropped on the XSLT Developer controller.
     * Routes XML files to the XML input and XSLT files to the stylesheet input.
     *
     * @param files the dropped files
     */
    private void handleDroppedFiles(java.util.List<File> files) {
        logger.info("Files dropped on XSLT Developer: {} file(s)", files.size());

        for (File file : files) {
            DragDropService.FileType fileType = DragDropService.getFileType(file);
            if (fileType == DragDropService.FileType.XSLT) {
                // Load as XSLT stylesheet
                loadXsltFileInternal(file);
                if (inputTabPane != null) {
                    inputTabPane.getSelectionModel().select(1); // Switch to XSLT tab
                }
            } else if (fileType == DragDropService.FileType.XML) {
                // Load as XML input
                loadXmlFileInternal(file);
                if (inputTabPane != null) {
                    inputTabPane.getSelectionModel().select(0); // Switch to XML tab
                }
            }
        }

        // Show content pane after loading
        showContent();
    }

    /**
     * Internal method to load an XML file.
     */
    private void loadXmlFileInternal(File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (xmlInputEditor != null) {
                xmlInputEditor.getCodeArea().replaceText(content);
            }
            currentXmlFile = file;
            logger.debug("Loaded XML file: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to load XML file", e);
            showAlert("Load Error", "Failed to load XML file: " + e.getMessage());
        }
    }

    /**
     * Sets up keyboard shortcuts for XSLT Developer Controller actions.
     */
    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (transformBtn == null || transformBtn.getScene() == null) {
                // Scene not ready yet, try again later
                Platform.runLater(this::setupKeyboardShortcuts);
                return;
            }

            Scene scene = transformBtn.getScene();

            // Ctrl+L - Toggle Live Transform
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                () -> {
                    if (liveTransformToggle != null) {
                        liveTransformToggle.setSelected(!liveTransformToggle.isSelected());
                        liveTransformToggle.fire();
                    }
                }
            );

            // Ctrl+Shift+C - Copy Result
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::copyResult
            );

            // Ctrl+Alt+S - Save Result
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN),
                this::saveResult
            );

            // Ctrl+R - Reload/Transform (same as F5)
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                this::performTransformation
            );

            logger.debug("XSLT Developer Controller keyboard shortcuts registered");
        });
    }

    private void setupFavorites() {
        // Setup unified FavoritesPanel
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
        }

        // Setup add to favorites button
        if (addToFavoritesBtn != null) {
            addToFavoritesBtn.setOnAction(e -> addCurrentToFavorites());
        }

        // Setup toggle favorites button
        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }

        // Initially hide favorites panel
        if (favoritesPanel != null && rightSplitPane != null) {
            rightSplitPane.getItems().remove(favoritesPanel);
        }

        logger.debug("Favorites panel setup completed");
    }

    private void initializeEmptyState() {
        // Wire up empty state buttons to trigger file loading actions
        if (emptyStateLoadXmlButton != null) {
            emptyStateLoadXmlButton.setOnAction(e -> loadXmlFile());
        }
        if (emptyStateLoadXsltButton != null) {
            emptyStateLoadXsltButton.setOnAction(e -> loadXsltFile());
        }

        logger.debug("Empty state initialized");
    }

    private void showContent() {
        if (emptyStatePane != null && contentPane != null) {
            emptyStatePane.setVisible(false);
            emptyStatePane.setManaged(false);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            logger.debug("Switched from empty state to content view");
        }
    }

    private void showEmptyState() {
        if (emptyStatePane != null && contentPane != null) {
            emptyStatePane.setVisible(true);
            emptyStatePane.setManaged(true);
            contentPane.setVisible(false);
            contentPane.setManaged(false);
            logger.debug("Switched from content to empty state view");
        }
    }

    /**
     * Adds the current file(s) to favorites using the unified FavoritesPanel.
     * The FavoritesPanelController will use getCurrentFile() to get the current file.
     */
    private void addCurrentToFavorites() {
        if (favoritesPanelController != null) {
            File currentFile = getCurrentFile();
            if (currentFile != null) {
                // Show the favorites panel if hidden, then trigger add
                if (!rightSplitPane.getItems().contains(favoritesPanel)) {
                    toggleFavoritesPanel();
                }
                // Use a simple dialog approach - same as FavoritesPanelController
                TextInputDialog dialog = new TextInputDialog(currentFile.getName());
                dialog.setTitle("Add to Favorites");
                dialog.setHeaderText("Add " + currentFile.getName() + " to favorites");
                dialog.setContentText("Enter alias (optional):");

                dialog.showAndWait().ifPresent(alias -> {
                    org.fxt.freexmltoolkit.domain.FileFavorite favorite =
                        new org.fxt.freexmltoolkit.domain.FileFavorite(
                            alias.isEmpty() ? currentFile.getName() : alias,
                            currentFile.getAbsolutePath(),
                            getCategoryForFile(currentFile.getAbsolutePath())
                        );
                    ServiceRegistry.get(org.fxt.freexmltoolkit.service.FavoritesService.class).addFavorite(favorite);
                    showInfo("Added to Favorites", currentFile.getName() + " has been added to favorites.");
                });
            } else {
                showAlert("No Files Loaded", "Please load an XML or XSLT file before adding to favorites.");
            }
        }
    }

    private String getCategoryForFile(String filePath) {
        if (filePath.endsWith(".xml")) return "XML Documents";
        if (filePath.endsWith(".xsd")) return "XSD Schemas";
        if (filePath.endsWith(".xsl") || filePath.endsWith(".xslt")) return "XSLT Stylesheets";
        return "Other";
    }

    /**
     * Toggles the visibility of the favorites panel using SplitPane.
     */
    private void toggleFavoritesPanel() {
        toggleFavoritesPanelInternal();
    }

    private void toggleFavoritesPanelInternal() {
        if (favoritesPanel == null || rightSplitPane == null) {
            logger.warn("Cannot toggle favorites panel - favoritesPanel or rightSplitPane is null");
            return;
        }

        // Ensure content is visible when showing favorites
        if (contentPane != null && !contentPane.isVisible()) {
            showContent();
        }

        boolean isCurrentlyShown = rightSplitPane.getItems().contains(favoritesPanel);

        if (isCurrentlyShown) {
            rightSplitPane.getItems().remove(favoritesPanel);
            logger.debug("Favorites panel hidden");
        } else {
            rightSplitPane.getItems().add(favoritesPanel);
            rightSplitPane.setDividerPositions(0.75);
            logger.debug("Favorites panel shown");
        }
    }

    // ==================== PUBLIC KEYBOARD SHORTCUT METHODS ====================

    /**
     * Public wrapper for keyboard shortcut access to toggle favorites panel.
     */
    public void toggleFavoritesPanelPublic() {
        toggleFavoritesPanelInternal();
    }

    /**
     * Public wrapper for keyboard shortcut access to add current file to favorites.
     */
    public void addCurrentToFavoritesPublic() {
        addCurrentToFavorites();
    }

    // FavoritesParentController interface implementation

    @Override
    public void loadFileToNewTab(File file) {
        if (file == null || !file.exists()) {
            showAlert("File Not Found", "The selected file does not exist.");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

            if (file.getName().endsWith(".xml")) {
                if (xmlInputEditor != null) {
                    xmlInputEditor.getCodeArea().replaceText(content);
                    currentXmlFile = file;

                    // Set the XML file in the service for stylesheet detection
                    xmlService.setCurrentXmlFile(file);

                    // Auto-load linked XSLT stylesheet if user hasn't manually loaded one
                    if (currentXsltFile == null) {
                        tryLoadLinkedStylesheet();
                    }
                }
            } else if (file.getName().endsWith(".xsl") || file.getName().endsWith(".xslt")) {
                if (xsltInputEditor != null) {
                    xsltInputEditor.getCodeArea().replaceText(content);
                    currentXsltFile = file;
                }
            }

            // Show content when file is loaded
            showContent();

            logger.info("Loaded file from favorites: {}", file.getName());
        } catch (IOException e) {
            logger.error("Failed to load file from favorites", e);
            showAlert("Load Error", "Failed to load file: " + e.getMessage());
        }
    }

    @Override
    public File getCurrentFile() {
        // Return file based on currently selected tab
        if (inputTabPane != null) {
            int selectedIndex = inputTabPane.getSelectionModel().getSelectedIndex();
            // Tab 0 = XML Source, Tab 1 = XSLT Stylesheet, Tab 2 = Parameters
            if (selectedIndex == 1 && currentXsltFile != null) {
                return currentXsltFile;
            } else if (selectedIndex == 0 && currentXmlFile != null) {
                return currentXmlFile;
            }
        }
        // Fallback: return any available file
        if (currentXmlFile != null) {
            return currentXmlFile;
        }
        return currentXsltFile;
    }

    private void initializeUI() {
        // Initialize version combo
        if (xsltVersionCombo != null) {
            xsltVersionCombo.setItems(FXCollections.observableArrayList(
                    "XSLT 3.0", "XSLT 2.0", "XSLT 1.0"
            ));
            xsltVersionCombo.setValue("XSLT 3.0");
        }

        // Initialize output format combo
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(FXCollections.observableArrayList(
                    "XML", "HTML", "Text", "JSON"
            ));
            outputFormatCombo.setValue("XML");
        }

        // Initialize encoding combo
        if (encodingCombo != null) {
            encodingCombo.setItems(FXCollections.observableArrayList(
                    "UTF-8", "UTF-16", "ISO-8859-1"
            ));
            encodingCombo.setValue("UTF-8");
        }

        // Parameters container is initialized dynamically in setDefaultValues()

        // Initialize features list
        if (featuresListView != null) {
            featuresListView.setItems(FXCollections.observableArrayList());
        }

        // Initialize messages list
        if (messagesListView != null) {
            messagesListView.setItems(FXCollections.observableArrayList());
        }
    }

    private void initializeEditors() {
        logger.info("Initializing XmlCodeEditor instances for XSLT Developer");

        // Initialize XML Input Editor
        if (xmlInputEditorPane != null) {
            xmlInputEditor = new XmlCodeEditor();
            xmlInputEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    <!-- Enter or load your XML source document here -->\n</root>");
            xmlInputEditorPane.getChildren().add(xmlInputEditor);
            logger.debug("XML Input Editor initialized");
        }

        // Initialize XSLT Input Editor
        if (xsltInputEditorPane != null) {
            xsltInputEditor = new XmlCodeEditor();
            xsltInputEditor.getCodeArea().replaceText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n    <!-- Enter or load your XSLT stylesheet here -->\n    <xsl:template match=\"/\">\n        <html>\n            <body>\n                <xsl:apply-templates/>\n            </body>\n        </html>\n    </xsl:template>\n</xsl:stylesheet>");
            xsltInputEditorPane.getChildren().add(xsltInputEditor);
            logger.debug("XSLT Input Editor initialized");
        }

        logger.info("XmlCodeEditor instances initialized successfully");
    }

    private void setupEventHandlers() {
        // Live transform toggle
        if (liveTransformToggle != null) {
            liveTransformToggle.setOnAction(e -> {
                if (liveTransformToggle.isSelected()) {
                    setupLiveTransform();
                } else {
                    disableLiveTransform();
                }
            });
        }

        // Parameter changes are handled directly in createParameterRow()

        // Input area changes for live transform
        if (xmlInputEditor != null) {
            xmlInputEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            });
        }

        if (xsltInputEditor != null) {
            xsltInputEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            });
        }
    }

    private void setDefaultValues() {
        if (indentOutputCheckbox != null) indentOutputCheckbox.setSelected(true);

        // Default content is now set in initializeEditors() method

        // Initialize development environment settings
        if (enableDebugMode != null) {
            enableDebugMode.setSelected(false);
        }

        // Set default encoding
        if (encodingCombo != null) {
            encodingCombo.setValue("UTF-8");
        }

        // Add some sample parameters
        if (parametersContainer != null && parameters.isEmpty()) {
            addDefaultParameter("title", "Advanced Book Catalog", "xs:string");
            addDefaultParameter("showGenre", "true", "xs:boolean");
            addDefaultParameter("maxPrice", "50.00", "xs:decimal");
        }
    }

    /**
     * Adds a default parameter to the grid.
     */
    private void addDefaultParameter(String name, String value, String type) {
        XsltParameter param = new XsltParameter(name, value, type);
        parameters.add(param);
        currentParameters.put(name, value);
        if (parametersContainer != null) {
            parametersContainer.getChildren().add(createParameterRow(param));
        }
    }

    /**
     * Creates a parameter row with editable Name, Value, Type (ComboBox), and Delete button.
     */
    private HBox createParameterRow(XsltParameter param) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));

        // Name TextField
        TextField nameField = new TextField(param.getName());
        nameField.setMinWidth(150);
        nameField.setPrefWidth(150);
        nameField.setPromptText("Parameter name");
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                currentParameters.remove(param.getName());
                param.setName(newVal);
                currentParameters.put(newVal, param.getValue());
            }
        });

        // Value TextField
        TextField valueField = new TextField(param.getValue());
        valueField.setMinWidth(200);
        valueField.setPromptText("Value");
        HBox.setHgrow(valueField, Priority.ALWAYS);
        valueField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                param.setValue(newVal);
                currentParameters.put(param.getName(), newVal);
                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            }
        });

        // Type ComboBox with all XSD types
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(XSD_TYPES);
        typeCombo.setValue(param.getType() != null ? param.getType() : "xs:string");
        typeCombo.setMinWidth(180);
        typeCombo.setPrefWidth(180);
        typeCombo.setEditable(true); // Allow custom types
        typeCombo.setPromptText("Select type");
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                param.setType(newVal);
            }
        });

        // Delete Button
        Button deleteBtn = new Button();
        deleteBtn.setGraphic(new FontIcon("bi-trash"));
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        deleteBtn.setTooltip(new Tooltip("Remove parameter"));
        deleteBtn.setOnAction(e -> removeParameter(param, row));

        row.getChildren().addAll(nameField, valueField, typeCombo, deleteBtn);
        return row;
    }

    /**
     * Removes a parameter from the grid.
     */
    private void removeParameter(XsltParameter param, HBox row) {
        parameters.remove(param);
        currentParameters.remove(param.getName());
        if (parametersContainer != null) {
            parametersContainer.getChildren().remove(row);
        }
    }

    private void setupLiveTransform() {
        logger.debug("Live transform enabled");
        if (transformBtn != null) transformBtn.setDisable(true);
        performTransformation();
    }

    private void disableLiveTransform() {
        logger.debug("Live transform disabled");
        if (transformBtn != null) transformBtn.setDisable(false);
    }

    @FXML
    public void executeTransformation() {
        performTransformation();
    }

    private void performTransformation() {
        String xmlContent = xmlInputEditor != null ? xmlInputEditor.getCodeArea().getText().trim() : "";
        String xsltContent = xsltInputEditor != null ? xsltInputEditor.getCodeArea().getText().trim() : "";

        if (xmlContent.isEmpty() || xsltContent.isEmpty()) {
            if (!liveTransformToggle.isSelected()) {
                showAlert("Input Required", "Please provide both XML source and XSLT stylesheet.");
            }
            return;
        }

        Task<XsltTransformationResult> transformTask = new Task<>() {
            @Override
            protected XsltTransformationResult call() throws Exception {
                String outputFormat = outputFormatCombo != null ? outputFormatCombo.getValue() : "XML";
                XsltTransformationEngine.OutputFormat format = XsltTransformationEngine.OutputFormat.valueOf(outputFormat.toUpperCase());

                Map<String, Object> params = new HashMap<>(currentParameters);
                return xsltEngine.transform(xmlContent, xsltContent, params, format);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    lastResult = getValue();
                    displayTransformationResults(lastResult);
                    logger.debug("XSLT transformation completed in {}ms", lastResult.getTransformationTime());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("XSLT transformation failed", getException());

                    if (!liveTransformToggle.isSelected()) {
                        showAlert("Transformation Error", "XSLT transformation failed: " + getException().getMessage());
                    } else {
                        // For live transform, just show error in result area
                        if (transformationResultArea != null) {
                            transformationResultArea.setText("Transformation Error: " + getException().getMessage());
                        }
                    }

                    // Add error to messages list
                    if (messagesListView != null) {
                        messagesListView.getItems().add("ERROR: " + getException().getMessage());
                    }
                });
            }
        };

        executorService.submit(transformTask);
    }

    private void displayTransformationResults(XsltTransformationResult result) {
        // Display transformation result
        if (transformationResultArea != null) {
            transformationResultArea.setText(result.getOutputContent());
        }

        // Update preview if HTML output
        if (previewWebView != null && "HTML".equals(outputFormatCombo.getValue())) {
            previewWebView.getEngine().loadContent(result.getOutputContent());
        }

        // Update result statistics
        if (resultStatsLabel != null) {
            resultStatsLabel.setText(String.format(
                    "Transformation completed in %dms | Output size: %d chars | Format: %s",
                    result.getTransformationTime(),
                    result.getOutputSize(),
                    result.getOutputFormat()
            ));
        }

        // Update performance metrics
        updatePerformanceMetrics(result);

        // Update features used (if available)
        updateFeaturesUsed(result);
    }

    private void updatePerformanceMetrics(XsltTransformationResult result) {
        if (executionTimeLabel != null)
            executionTimeLabel.setText(result.getTransformationTime() + "ms");
        if (compilationTimeLabel != null)
            compilationTimeLabel.setText("N/A"); // Saxon doesn't expose this separately
        if (memoryUsageLabel != null)
            memoryUsageLabel.setText("N/A"); // Would need JVM memory monitoring
        if (outputSizeLabel != null)
            outputSizeLabel.setText(formatBytes(result.getOutputSize()));

        // Performance report
        if (performanceReportArea != null) {
            String xsltVersion = xsltVersionCombo != null ? xsltVersionCombo.getValue() : "XSLT 3.0";
            String report = "XSLT Transformation Performance Report\n" +
                    "=====================================\n\n" +
                    "Execution Time: " + result.getTransformationTime() + "ms\n" +
                    "Output Format: " + result.getOutputFormat() + "\n" +
                    "Output Size: " + result.getOutputSize() + " characters\n" +
                    "XSLT Version: " + xsltVersion + "\n";

            performanceReportArea.setText(report);
        }
    }

    private void updateFeaturesUsed(XsltTransformationResult result) {
        if (featuresListView != null) {
            // This is a placeholder - in a real implementation, we'd extract
            // XSLT features from the stylesheet analysis
            featuresListView.getItems().clear();
            featuresListView.getItems().addAll(
                    "XSLT Templates",
                    "XPath Expressions",
                    "Output Method: " + result.getOutputFormat()
            );
        }
    }

    @FXML
    private void addParameter() {
        if (parametersContainer != null) {
            XsltParameter param = new XsltParameter(
                    "param" + (parameters.size() + 1),
                    "",
                    "xs:string"
            );
            parameters.add(param);
            HBox row = createParameterRow(param);
            parametersContainer.getChildren().add(row);
        }
    }

    @FXML
    private void loadXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XML Source Document");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(loadXmlBtn.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (xmlInputEditor != null) {
                    xmlInputEditor.getCodeArea().replaceText(content);
                }
                currentXmlFile = file;
                logger.debug("Loaded XML file: {}", file.getAbsolutePath());

                // Set the XML file in the service for stylesheet detection
                xmlService.setCurrentXmlFile(file);

                // Auto-load linked XSLT stylesheet if user hasn't manually loaded one
                if (currentXsltFile == null) {
                    tryLoadLinkedStylesheet();
                }

                // Show content when file is loaded
                showContent();

                if (liveTransformToggle != null && liveTransformToggle.isSelected()) {
                    performTransformation();
                }
            } catch (IOException e) {
                logger.error("Failed to load XML file", e);
                showAlert("Load Error", "Failed to load XML file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void validateXml() {
        if (xmlInputEditor == null || xmlInputEditor.getCodeArea().getText().trim().isEmpty()) {
            showAlert("No Content", "No XML content to validate.");
            return;
        }

        String xmlContent = xmlInputEditor.getCodeArea().getText();

        Task<String> validationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                try {
                    // Basic XML well-formedness validation
                    javax.xml.parsers.DocumentBuilderFactory factory =
                            javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();

                    // Custom error handler to collect validation messages
                    java.util.List<String> errors = new java.util.ArrayList<>();
                    builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                        @Override
                        public void warning(org.xml.sax.SAXParseException e) {
                            errors.add("WARNING: " + e.getMessage());
                        }

                        @Override
                        public void error(org.xml.sax.SAXParseException e) {
                            errors.add("ERROR: " + e.getMessage());
                        }

                        @Override
                        public void fatalError(org.xml.sax.SAXParseException e) {
                            errors.add("FATAL: " + e.getMessage());
                        }
                    });

                    builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlContent)));

                    if (errors.isEmpty()) {
                        return "XML is well-formed and valid.";
                    } else {
                        return "Validation issues found:\n" + String.join("\n", errors);
                    }

                } catch (Exception e) {
                    return "XML validation failed: " + e.getMessage();
                }
            }
        };

        validationTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                String result = validationTask.getValue();
                if (messagesListView != null) {
                    messagesListView.getItems().add("XML Validation: " + result);
                }

                if (result.startsWith("XML is well-formed")) {
                    showInfo("Validation Result", result);
                } else {
                    showAlert("Validation Result", result);
                }
            });
        });

        validationTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                logger.error("XML validation failed", validationTask.getException());
                showAlert("Validation Error", "Failed to validate XML: " + validationTask.getException().getMessage());
            });
        });

        executorService.submit(validationTask);
    }

    @FXML
    private void validateXslt() {
        if (xsltInputEditor == null || xsltInputEditor.getCodeArea().getText().trim().isEmpty()) {
            showAlert("No Content", "No XSLT content to validate.");
            return;
        }

        String xsltContent = xsltInputEditor.getCodeArea().getText();

        Task<String> validationTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                try {
                    // Use Saxon to validate XSLT stylesheet
                    net.sf.saxon.s9api.Processor processor = xsltEngine.getSaxonProcessor();
                    net.sf.saxon.s9api.XsltCompiler compiler = processor.newXsltCompiler();

                    // Compile the stylesheet to check for syntax errors
                    javax.xml.transform.stream.StreamSource source =
                            new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xsltContent));
                    net.sf.saxon.s9api.XsltExecutable executable = compiler.compile(source);

                    return "XSLT stylesheet is valid and compiles successfully.";

                } catch (net.sf.saxon.s9api.SaxonApiException e) {
                    return "XSLT validation failed: " + e.getMessage();
                } catch (Exception e) {
                    return "XSLT validation error: " + e.getMessage();
                }
            }
        };

        validationTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                String result = validationTask.getValue();
                if (messagesListView != null) {
                    messagesListView.getItems().add("XSLT Validation: " + result);
                }

                if (result.contains("valid and compiles")) {
                    showInfo("Validation Result", result);
                } else {
                    showAlert("Validation Result", result);
                }
            });
        });

        validationTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                logger.error("XSLT validation failed", validationTask.getException());
                showAlert("Validation Error", "Failed to validate XSLT: " + validationTask.getException().getMessage());
            });
        });

        executorService.submit(validationTask);
    }

    @FXML
    private void loadXsltFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XSLT Stylesheet");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(loadXsltBtn.getScene().getWindow());
        if (file != null) {
            loadXsltFileInternal(file);
        }
    }

    /**
     * Load an XSLT file from an external source (e.g., drag and drop, MainController routing).
     * This method is public to allow external callers to load files programmatically.
     *
     * @param file the XSLT file to load
     */
    public void loadXsltFileExternal(File file) {
        if (file != null && file.exists()) {
            loadXsltFileInternal(file);
            // Switch to XSLT tab after loading
            if (inputTabPane != null) {
                inputTabPane.getSelectionModel().select(1); // XSLT tab is index 1
            }
        }
    }

    /**
     * Internal method to load an XSLT file.
     */
    private void loadXsltFileInternal(File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (xsltInputEditor != null) {
                xsltInputEditor.getCodeArea().replaceText(content);
            }
            currentXsltFile = file;
            logger.debug("Loaded XSLT file: {}", file.getAbsolutePath());

            // Show content when file is loaded
            showContent();
        } catch (IOException e) {
            logger.error("Failed to load XSLT file", e);
            showAlert("Load Error", "Failed to load XSLT file: " + e.getMessage());
        }
    }

    @FXML
    private void saveXsltFile() {
        if (xsltInputEditor == null || xsltInputEditor.getCodeArea().getText().isEmpty()) {
            showAlert("No Content", "No XSLT content to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save XSLT Stylesheet");
        fileChooser.setInitialFileName("stylesheet.xsl");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(saveXsltBtn.getScene().getWindow());
        if (file != null) {
            try {
                // Add metadata before saving
                ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
                String xsltContent = xsltInputEditor.getCodeArea().getText();
                String contentWithMetadata = metadataService.addOrUpdateXmlMetadata(xsltContent);

                Files.write(file.toPath(), contentWithMetadata.getBytes(StandardCharsets.UTF_8));
                showInfo("Save Successful", "XSLT saved to: " + file.getAbsolutePath());
                logger.info("XSLT saved to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save XSLT file", e);
                showAlert("Save Error", "Failed to save XSLT file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void copyResult() {
        if (transformationResultArea != null && !transformationResultArea.getText().isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(transformationResultArea.getText()), null);
            showInfo("Copied", "Transformation result copied to clipboard.");
        }
    }

    @FXML
    private void saveResult() {
        if (transformationResultArea == null || transformationResultArea.getText().isEmpty()) {
            showAlert("No Result", "No transformation result to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transformation Result");

        String extension = getOutputExtension();
        fileChooser.setInitialFileName("result." + extension);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(extension.toUpperCase() + " Files", "*." + extension)
        );

        File file = fileChooser.showSaveDialog(saveResultBtn.getScene().getWindow());
        if (file != null) {
            try {
                String content = transformationResultArea.getText();

                // Add metadata for XML-based output formats
                if ("xml".equalsIgnoreCase(extension) || "xhtml".equalsIgnoreCase(extension)) {
                    ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
                    content = metadataService.addOrUpdateXmlMetadata(content);
                }

                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
                showInfo("Save Successful", "Result saved to: " + file.getAbsolutePath());
                logger.info("Result saved to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save result", e);
                showAlert("Save Error", "Failed to save result: " + e.getMessage());
            }
        }
    }

    @FXML
    private void refreshPreview() {
        if (lastResult != null && previewWebView != null && "HTML".equals(outputFormatCombo.getValue())) {
            previewWebView.getEngine().loadContent(lastResult.getOutputContent());
        }
    }

    @FXML
    private void clearDebug() {
        if (messagesListView != null) {
            messagesListView.getItems().clear();
        }
        if (traceArea != null) {
            traceArea.clear();
        }
    }

    // Utility Methods
    private String getOutputExtension() {
        String format = outputFormatCombo != null ? outputFormatCombo.getValue() : "XML";
        return switch (format.toLowerCase()) {
            case "html" -> "html";
            case "text" -> "txt";
            case "json" -> "json";
            default -> "xml";
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for XSLT Parameters
    public static class XsltParameter {
        private String name;
        private String value;
        private String type;

        public XsltParameter(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type != null ? type : "xs:string";
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    // Lifecycle
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("XSLT Developer Controller shutdown completed");
        }
    }

    /**
     * Attempts to load a linked XSLT stylesheet from the current XML file's xml-stylesheet processing instruction.
     * Only loads if user hasn't already manually selected a stylesheet.
     * Shows a warning if stylesheet is referenced but cannot be loaded.
     */
    private void tryLoadLinkedStylesheet() {
        try {
            var linkedStylesheet = xmlService.getLinkedStylesheetFromCurrentXMLFile();

            if (linkedStylesheet.isPresent()) {
                String stylesheetPath = linkedStylesheet.get();
                logger.debug("Found linked stylesheet: {}", stylesheetPath);

                File stylesheetFile = new File(stylesheetPath);

                // Check if file exists and is valid
                if (stylesheetFile.exists()) {
                    // Basic validation - check if it's an XSL file
                    String fileName = stylesheetFile.getName().toLowerCase();
                    if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
                        try {
                            String content = Files.readString(stylesheetFile.toPath(), StandardCharsets.UTF_8);
                            if (xsltInputEditor != null) {
                                xsltInputEditor.getCodeArea().replaceText(content);
                            }
                            currentXsltFile = stylesheetFile;
                            logger.info("Auto-loaded linked XSLT stylesheet: {}", stylesheetPath);
                        } catch (IOException e) {
                            logger.error("Failed to read stylesheet file: {}", e.getMessage());
                            showStylesheetWarning("Error Reading Stylesheet", "Failed to read the linked stylesheet:\n" + e.getMessage());
                        }
                    } else {
                        logger.warn("Linked file is not an XSLT stylesheet (doesn't end with .xsl or .xslt): {}", stylesheetPath);
                        showStylesheetWarning("Invalid Stylesheet", "The linked file is not an XSLT stylesheet: " + stylesheetPath);
                    }
                } else {
                    logger.warn("Linked stylesheet file not found: {}", stylesheetPath);
                    showStylesheetWarning("Stylesheet Not Found", "The linked XSLT stylesheet could not be found:\n" + stylesheetPath);
                }
            } else {
                logger.debug("No linked stylesheet found in XML file");
            }
        } catch (Exception e) {
            logger.error("Error while trying to load linked stylesheet: {}", e.getMessage(), e);
            showStylesheetWarning("Error Loading Stylesheet", "An error occurred while trying to load the linked stylesheet:\n" + e.getMessage());
        }
    }

    /**
     * Shows a warning dialog about stylesheet loading issues.
     */
    private void showStylesheetWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText("Linked XSLT Stylesheet Issue");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-code-slash", "XSLT 3.0 Support", "Full support for XSLT 3.0, 2.0, and 1.0 transformations with Saxon HE engine"},
                new String[]{"bi-lightning", "Live Transform Mode", "Enable real-time transformation preview as you edit XML or XSLT content"},
                new String[]{"bi-file-earmark-code", "Multiple Output Formats", "Generate XML, HTML, Text, or JSON output from your transformations"},
                new String[]{"bi-sliders", "XSLT Parameters", "Define typed parameters (xs:string, xs:boolean, xs:decimal, etc.) to pass to stylesheets"},
                new String[]{"bi-speedometer2", "Performance Profiling", "Monitor transformation time, output size, and execution metrics"},
                new String[]{"bi-bug", "Debug Mode", "Enable debug logging and trace output for troubleshooting transformations"},
                new String[]{"bi-star", "Favorites System", "Save frequently used XML and XSLT files for quick access"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"F5", "Execute XSLT transformation"},
                new String[]{"Ctrl+L", "Toggle live transform mode"},
                new String[]{"Ctrl+R", "Reload and transform"},
                new String[]{"Ctrl+Shift+C", "Copy transformation result"},
                new String[]{"Ctrl+Alt+S", "Save transformation result"},
                new String[]{"Ctrl+D", "Add current file to favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle favorites panel"},
                new String[]{"F1", "Show this help dialog"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "XSLT Developer - Help",
                "Advanced XSLT Developer",
                "Professional XSLT 3.0 development environment with live preview, performance profiling, and debugging capabilities.",
                "bi-code-square",
                DialogHelper.HeaderTheme.PRIMARY,
                features,
                shortcuts
        );
        helpDialog.showAndWait();
    }

    private void applySmallIconsSetting() {
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to XSLT Developer toolbar: {}", useSmallIcons);

        // Determine display mode and icon size
        javafx.scene.control.ContentDisplay displayMode = useSmallIcons
                ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                : javafx.scene.control.ContentDisplay.TOP;

        // Icon sizes: small = 14px, normal = 20px
        int iconSize = useSmallIcons ? 14 : 20;

        // Button style: compact padding for small icons
        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        // Apply to main toolbar buttons only (not inner tab buttons)
        applyButtonSettings(loadXmlBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(transformBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(liveTransformToggle, displayMode, iconSize, buttonStyle);
        applyButtonSettings(addToFavoritesBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toggleFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(helpBtn, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to XSLT Developer toolbar (size: {}px)", iconSize);
    }

    /**
     * Helper method to apply display mode, icon size, and style to a button.
     */
    private void applyButtonSettings(javafx.scene.control.ButtonBase button,
                                     javafx.scene.control.ContentDisplay displayMode,
                                     int iconSize,
                                     String style) {
        if (button == null) return;

        // Set content display mode
        button.setContentDisplay(displayMode);

        // Apply compact style
        button.setStyle(style);

        // Update icon size if the button has a FontIcon graphic
        if (button.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fontIcon) {
            fontIcon.setIconSize(iconSize);
        }
    }

    /**
     * Public method to refresh toolbar icons.
     * Can be called from Settings or MainController when icon size preference changes.
     */
    public void refreshToolbarIcons() {
        applySmallIconsSetting();
    }
}
