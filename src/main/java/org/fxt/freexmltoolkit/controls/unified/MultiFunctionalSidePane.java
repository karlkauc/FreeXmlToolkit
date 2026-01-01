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

package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.FavoritesParentController;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdPropertiesPanel;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Multi-functional side pane for the Unified Editor.
 * <p>
 * This pane serves multiple purposes:
 * <ul>
 *   <li>Shows context-sensitive properties based on the active editor type</li>
 *   <li>Temporarily displays favorites when activated</li>
 *   <li>Can be toggled visible/hidden</li>
 * </ul>
 * <p>
 * State management:
 * <ul>
 *   <li>HIDDEN: Pane is not visible</li>
 *   <li>PROPERTIES: Showing context-sensitive properties for current editor</li>
 *   <li>FAVORITES: Temporarily showing favorites overlay</li>
 * </ul>
 *
 * @since 2.0
 */
public class MultiFunctionalSidePane extends VBox {

    private static final Logger logger = LogManager.getLogger(MultiFunctionalSidePane.class);

    /**
     * States for the multi-functional side pane.
     */
    public enum SidePaneState {
        HIDDEN,      // Pane not visible
        PROPERTIES,  // Showing context-sensitive properties
        FAVORITES    // Temporarily showing favorites
    }

    // UI Components
    private final HBox header;
    private final StackPane contentStack;
    private ToggleButton toggleButton;
    private Label titleLabel;
    private Button closeButton;

    // Content containers
    private final Map<UnifiedEditorFileType, Node> propertiesPanes = new EnumMap<>(UnifiedEditorFileType.class);
    private Node favoritesPane;
    private FavoritesPanelController favoritesPanelController;
    private Node currentPropertiesPane;

    // State
    private SidePaneState currentState = SidePaneState.PROPERTIES;
    private SidePaneState stateBeforeFavorites = SidePaneState.PROPERTIES;
    private UnifiedEditorFileType currentEditorType = null;

    // SplitPane management
    private javafx.scene.control.SplitPane parentSplitPane;
    private double lastDividerPosition = 0.75; // Default 75% for editor, 25% for pane

    // Callbacks
    private Consumer<File> onFavoriteSelected;
    private Runnable onVisibilityChanged;
    private Runnable onPaneShown;

    // Controllers for property panes
    private XmlEditorSidebarController xmlSidebarController;
    private XsdPropertiesPanel xsdPropertiesPanel;
    private XsdEditorContext currentXsdContext;

    /**
     * Creates a new MultiFunctionalSidePane.
     */
    public MultiFunctionalSidePane() {
        super();
        getStyleClass().add("multi-functional-pane");
        setMinWidth(250);
        setMaxWidth(400);
        setPrefWidth(300);
        setSpacing(0);

        // Create header
        header = createHeader();

        // Create content stack
        contentStack = new StackPane();
        contentStack.getStyleClass().add("content-stack");
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().addAll(header, new Separator(), contentStack);

        // Apply initial styles
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1;");
    }

    /**
     * Creates the header with toggle, title, and close buttons.
     */
    private HBox createHeader() {
        HBox headerBox = new HBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(8, 12, 8, 12));
        headerBox.setStyle("-fx-background-color: #e9ecef;");

        // Toggle button
        toggleButton = new ToggleButton();
        toggleButton.setSelected(true);
        FontIcon toggleIcon = new FontIcon("bi-layout-sidebar-inset-reverse");
        toggleIcon.setIconSize(16);
        toggleButton.setGraphic(toggleIcon);
        toggleButton.setTooltip(new Tooltip("Toggle Properties Panel (Ctrl+Shift+P)"));
        toggleButton.getStyleClass().add("flat-button");
        toggleButton.setOnAction(e -> toggleVisibility());

        // Title label
        titleLabel = new Label("Properties");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Close button
        closeButton = new Button();
        FontIcon closeIcon = new FontIcon("bi-x");
        closeIcon.setIconSize(14);
        closeButton.setGraphic(closeIcon);
        closeButton.setTooltip(new Tooltip("Hide Panel"));
        closeButton.getStyleClass().add("flat-button");
        closeButton.setOnAction(e -> hide());

        headerBox.getChildren().addAll(toggleButton, titleLabel, spacer, closeButton);
        return headerBox;
    }

    /**
     * Shows the properties pane for the given editor type.
     *
     * @param editorType the type of editor to show properties for
     */
    public void showPropertiesForEditor(UnifiedEditorFileType editorType) {
        if (editorType == null) {
            return;
        }

        this.currentEditorType = editorType;

        // Update title based on editor type
        updateTitleForEditorType(editorType);

        // Get or create the properties pane for this editor type
        Node propertiesPane = propertiesPanes.computeIfAbsent(editorType, this::createPropertiesPaneForType);

        // Update content stack
        if (currentState == SidePaneState.PROPERTIES || currentState == SidePaneState.HIDDEN) {
            contentStack.getChildren().clear();
            if (propertiesPane != null) {
                contentStack.getChildren().add(propertiesPane);
                currentPropertiesPane = propertiesPane;
            }
        }

        // If currently showing favorites, just store for later
        if (currentState == SidePaneState.FAVORITES) {
            currentPropertiesPane = propertiesPane;
        }
    }

    /**
     * Updates the title label based on the editor type.
     */
    private void updateTitleForEditorType(UnifiedEditorFileType editorType) {
        String title = switch (editorType) {
            case XML -> "XML Properties";
            case XSD -> "Schema Properties";
            case XSLT -> "XSLT Output";
            case SCHEMATRON -> "Schematron Properties";
            case JSON -> "JSON Properties";
        };

        // Only update if showing properties (not favorites)
        if (currentState != SidePaneState.FAVORITES) {
            titleLabel.setText(title);
        }
    }

    /**
     * Creates a properties pane for the given editor type.
     */
    private Node createPropertiesPaneForType(UnifiedEditorFileType type) {
        return switch (type) {
            case XML -> createXmlPropertiesPane();
            case XSD -> createXsdPropertiesPane();
            case XSLT -> createXsltOutputPane();
            case SCHEMATRON -> createSchematronPropertiesPane();
            case JSON -> createJsonPropertiesPane();
        };
    }

    /**
     * Creates the XML properties pane (loads XmlEditorSidebar.fxml).
     */
    private Node createXmlPropertiesPane() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/controls/XmlEditorSidebar.fxml"));
            VBox sidebar = loader.load();
            xmlSidebarController = loader.getController();
            xmlSidebarController.setSidebarContainer(sidebar);

            // Initially disable XSD-dependent panes
            xmlSidebarController.updateXsdDependentPanes(false);

            logger.debug("Created XML properties pane");
            return sidebar;
        } catch (IOException e) {
            logger.error("Failed to load XML properties pane", e);
            return createErrorPlaceholder("Failed to load XML properties");
        }
    }

    /**
     * Creates the XSD properties pane placeholder.
     * The actual XsdPropertiesPanel is created when connecting to an XSD tab,
     * as it requires an XsdEditorContext in its constructor.
     */
    private Node createXsdPropertiesPane() {
        // Create a placeholder - the real panel is created in connectToXsdTab
        VBox placeholder = new VBox(16);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(20));

        FontIcon icon = new FontIcon("bi-diagram-3");
        icon.setIconSize(48);
        icon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));

        Label label = new Label("Open an XSD file to see schema properties");
        label.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 13px;");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);

        placeholder.getChildren().addAll(icon, label);
        logger.debug("Created XSD properties placeholder");
        return placeholder;
    }

    /**
     * Creates the XSLT output pane with full configuration options.
     */
    private Node createXsltOutputPane() {
        XsltOutputPane outputPane = new XsltOutputPane();
        logger.debug("Created XSLT output pane");
        return outputPane;
    }

    /**
     * Creates the Schematron properties pane with templates and XPath tester.
     */
    private Node createSchematronPropertiesPane() {
        SchematronPropertiesPane propertiesPane = new SchematronPropertiesPane();
        logger.debug("Created Schematron properties pane");
        return propertiesPane;
    }

    /**
     * Creates the JSON properties pane.
     * For now, a simple placeholder. Will be expanded in Phase 4+.
     */
    private Node createJsonPropertiesPane() {
        VBox placeholder = new VBox(16);
        placeholder.setPadding(new Insets(20));
        placeholder.setAlignment(Pos.TOP_CENTER);

        FontIcon icon = new FontIcon("bi-filetype-json");
        icon.setIconSize(48);
        icon.setIconColor(javafx.scene.paint.Color.web("#f57c00"));

        Label titleLabel = new Label("JSON Properties");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label infoLabel = new Label("JSON property panel coming in Phase 4");
        infoLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 13px;");
        infoLabel.setWrapText(true);
        infoLabel.setAlignment(Pos.CENTER);

        // Basic info section
        Label formatLabel = new Label("Supports: JSON, JSONC, JSON5");
        formatLabel.setStyle("-fx-text-fill: #17a2b8; -fx-font-size: 12px;");

        placeholder.getChildren().addAll(icon, titleLabel, infoLabel, formatLabel);
        logger.debug("Created JSON properties placeholder");
        return placeholder;
    }

    /**
     * Creates an error placeholder for when pane loading fails.
     */
    private Node createErrorPlaceholder(String message) {
        VBox errorBox = new VBox(10);
        errorBox.setPadding(new Insets(16));
        errorBox.setAlignment(Pos.TOP_CENTER);

        FontIcon icon = new FontIcon("bi-exclamation-triangle");
        icon.setIconSize(32);
        icon.setIconColor(javafx.scene.paint.Color.web("#dc3545"));

        Label label = new Label(message);
        label.setStyle("-fx-text-fill: #dc3545;");
        label.setWrapText(true);

        errorBox.getChildren().addAll(icon, label);
        return errorBox;
    }

    // ==================== Favorites Management ====================

    /**
     * Shows the favorites panel temporarily.
     * Saves the current state so it can be restored after selection.
     */
    public void showFavorites() {
        // Save current state
        stateBeforeFavorites = currentState;

        // Load favorites pane if not already loaded
        if (favoritesPane == null) {
            favoritesPane = loadFavoritesPane();
        }

        // Show favorites
        contentStack.getChildren().clear();
        if (favoritesPane != null) {
            contentStack.getChildren().add(favoritesPane);
        }

        titleLabel.setText("Favorites");
        currentState = SidePaneState.FAVORITES;

        // Make sure pane is visible
        setVisible(true);
        setManaged(true);

        logger.debug("Showing favorites overlay");
    }

    /**
     * Hides the favorites panel and restores the previous state.
     */
    public void hideFavorites() {
        if (currentState != SidePaneState.FAVORITES) {
            return;
        }

        // Restore previous state
        currentState = stateBeforeFavorites;

        if (currentState == SidePaneState.HIDDEN) {
            hide();
        } else {
            // Restore properties pane
            contentStack.getChildren().clear();
            if (currentPropertiesPane != null) {
                contentStack.getChildren().add(currentPropertiesPane);
            }
            if (currentEditorType != null) {
                updateTitleForEditorType(currentEditorType);
            }
        }

        logger.debug("Hidden favorites overlay, restored state: {}", currentState);
    }

    /**
     * Loads the favorites panel from FXML.
     */
    private Node loadFavoritesPane() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/controls/FavoritesPanel.fxml"));
            VBox favorites = loader.load();
            favoritesPanelController = loader.getController();

            // Create a wrapper that implements FavoritesParentController
            // and notifies us when a file is selected
            favoritesPanelController.setParentController(new FavoritesParentController() {
                @Override
                public void loadFileToNewTab(File file) {
                    // Notify callback and hide favorites
                    if (onFavoriteSelected != null) {
                        Platform.runLater(() -> {
                            onFavoriteSelected.accept(file);
                            hideFavorites();
                        });
                    }
                }

                @Override
                public File getCurrentFile() {
                    // This would need to come from the parent controller
                    return null;
                }
            });

            logger.debug("Loaded favorites panel");
            return favorites;
        } catch (IOException e) {
            logger.error("Failed to load favorites panel", e);
            return createErrorPlaceholder("Failed to load favorites");
        }
    }

    // ==================== Visibility Control ====================

    /**
     * Toggles the visibility of the pane.
     */
    public void toggleVisibility() {
        if (isVisible() && isManaged()) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Shows the pane.
     */
    public void show() {
        setVisible(true);
        setManaged(true);
        currentState = SidePaneState.PROPERTIES;
        toggleButton.setSelected(true);

        // Ensure properties are showing
        if (currentPropertiesPane != null && currentState == SidePaneState.PROPERTIES) {
            contentStack.getChildren().clear();
            contentStack.getChildren().add(currentPropertiesPane);
            if (currentEditorType != null) {
                updateTitleForEditorType(currentEditorType);
            }
        }

        // Restore divider position in parent SplitPane
        restoreDividerPosition();

        if (onVisibilityChanged != null) {
            onVisibilityChanged.run();
        }

        // Notify that pane is shown so controller can reconnect to current tab
        if (onPaneShown != null) {
            onPaneShown.run();
        }

        logger.debug("Showing multi-functional pane");
    }

    /**
     * Hides the pane.
     */
    public void hide() {
        // Save divider position before hiding
        saveDividerPosition();

        setVisible(false);
        setManaged(false);
        currentState = SidePaneState.HIDDEN;
        toggleButton.setSelected(false);

        if (onVisibilityChanged != null) {
            onVisibilityChanged.run();
        }

        logger.debug("Hiding multi-functional pane");
    }

    /**
     * Saves the current divider position from the parent SplitPane.
     */
    private void saveDividerPosition() {
        if (parentSplitPane == null) {
            findParentSplitPane();
        }
        if (parentSplitPane != null && parentSplitPane.getDividerPositions().length > 0) {
            int myIndex = parentSplitPane.getItems().indexOf(this);
            if (myIndex > 0 && parentSplitPane.getDividerPositions().length >= myIndex) {
                lastDividerPosition = parentSplitPane.getDividerPositions()[myIndex - 1];
                logger.debug("Saved divider position: {}", lastDividerPosition);
            }
        }
    }

    /**
     * Restores the divider position in the parent SplitPane.
     */
    private void restoreDividerPosition() {
        if (parentSplitPane == null) {
            findParentSplitPane();
        }
        if (parentSplitPane != null) {
            int myIndex = parentSplitPane.getItems().indexOf(this);
            if (myIndex > 0 && parentSplitPane.getDividerPositions().length >= myIndex) {
                javafx.application.Platform.runLater(() -> {
                    parentSplitPane.setDividerPosition(myIndex - 1, lastDividerPosition);
                    logger.debug("Restored divider position: {}", lastDividerPosition);
                });
            }
        }
    }

    /**
     * Finds the parent SplitPane if not already set.
     */
    private void findParentSplitPane() {
        javafx.scene.Parent parent = getParent();
        while (parent != null) {
            if (parent instanceof javafx.scene.control.SplitPane splitPane) {
                parentSplitPane = splitPane;
                break;
            }
            parent = parent.getParent();
        }
    }

    /**
     * Sets the parent SplitPane for divider management.
     */
    public void setParentSplitPane(javafx.scene.control.SplitPane splitPane) {
        this.parentSplitPane = splitPane;
    }

    // ==================== Accessors ====================

    /**
     * Gets the current state of the pane.
     */
    public SidePaneState getCurrentState() {
        return currentState;
    }

    /**
     * Gets the XML sidebar controller for integration.
     */
    public XmlEditorSidebarController getXmlSidebarController() {
        return xmlSidebarController;
    }

    /**
     * Gets the favorites panel controller.
     */
    public FavoritesPanelController getFavoritesPanelController() {
        return favoritesPanelController;
    }

    /**
     * Sets the callback for when a favorite file is selected.
     */
    public void setOnFavoriteSelected(Consumer<File> callback) {
        this.onFavoriteSelected = callback;
    }

    /**
     * Sets the callback for when visibility changes.
     */
    public void setOnVisibilityChanged(Runnable callback) {
        this.onVisibilityChanged = callback;
    }

    /**
     * Sets the callback for when the pane is shown.
     * Used by the controller to reconnect to the current tab.
     */
    public void setOnPaneShown(Runnable callback) {
        this.onPaneShown = callback;
    }

    /**
     * Checks if favorites is currently showing.
     */
    public boolean isFavoritesShowing() {
        return currentState == SidePaneState.FAVORITES;
    }

    /**
     * Gets the current editor type being displayed.
     */
    public UnifiedEditorFileType getCurrentEditorType() {
        return currentEditorType;
    }

    // ==================== Tab Connection ====================

    /**
     * Connects the XML properties sidebar to an XmlUnifiedTab.
     * Sets up listeners to update XPath, element info, etc. when cursor moves.
     *
     * @param tab the XmlUnifiedTab to connect to, or null to disconnect
     */
    public void connectToXmlTab(XmlUnifiedTab tab) {
        if (xmlSidebarController == null) {
            // Ensure the XML properties pane is created
            propertiesPanes.computeIfAbsent(UnifiedEditorFileType.XML, this::createPropertiesPaneForType);
        }

        if (xmlSidebarController == null) {
            logger.warn("Cannot connect to XML tab: sidebar controller not initialized");
            return;
        }

        // Set the unified tab reference for XSD operations
        xmlSidebarController.setXmlUnifiedTab(tab);

        if (tab == null) {
            // Disconnect - clear the fields
            xmlSidebarController.setXPath("");
            xmlSidebarController.setElementName("");
            xmlSidebarController.setElementType("");
            xmlSidebarController.setDocumentation("");
            xmlSidebarController.setPossibleChildElements(java.util.Collections.emptyList());
            return;
        }

        // Get the CodeArea from the tab
        org.fxmisc.richtext.CodeArea codeArea = tab.getPrimaryCodeArea();
        if (codeArea == null) {
            logger.warn("Cannot connect to XML tab: no CodeArea available");
            return;
        }

        // Setup caret position listener to update XPath and element info
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            updateXmlSidebarForPosition(tab, newPos.intValue());
        });

        // Setup text change listener for continuous validation
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (xmlSidebarController != null && xmlSidebarController.isContinuousValidationSelected()) {
                // Debounce validation to avoid excessive calls during typing
                Platform.runLater(() -> {
                    xmlSidebarController.performUnifiedTabValidation();
                    // Also validate Schematron if continuous Schematron validation is enabled
                    if (xmlSidebarController.isContinuousSchematronValidationSelected()) {
                        xmlSidebarController.performUnifiedTabSchematronValidation();
                    }
                });
            }
        });

        // Initial update
        updateXmlSidebarForPosition(tab, codeArea.getCaretPosition());

        // Update XSD-dependent panes based on linked XSD
        boolean hasXsd = tab.getXsdFile() != null;
        xmlSidebarController.updateXsdDependentPanes(hasXsd);

        // Set XSD path if available (use loadXsdDataForSidebar to avoid loop)
        if (hasXsd) {
            xmlSidebarController.setXsdPathField(tab.getXsdFile().getAbsolutePath());
            xmlSidebarController.loadXsdDataForSidebar(tab.getXsdFile());
        }

        // Set Schematron path if available
        if (tab.getSchematronFile() != null) {
            xmlSidebarController.setSchematronPathField(tab.getSchematronFile().getAbsolutePath());
        }

        // Register callback for async XSD loading (called when XSD is loaded in XmlUnifiedTab)
        tab.setOnXsdLoadedCallback(() -> {
            logger.debug("XSD loaded callback triggered for tab: {}", tab.getTabId());
            File loadedXsd = tab.getXsdFile();
            if (loadedXsd != null) {
                logger.debug("XSD file loaded: {}", loadedXsd.getName());

                // Check if tab has XSD documentation data
                org.fxt.freexmltoolkit.domain.XsdDocumentationData tabXsdData = tab.getXsdDocumentationData();
                if (tabXsdData != null && tabXsdData.getExtendedXsdElementMap() != null) {
                    logger.debug("Tab has XSD data with {} elements",
                            tabXsdData.getExtendedXsdElementMap().size());
                } else {
                    logger.warn("Tab XSD data is null or element map is empty");
                }

                // Update sidebar UI (don't call setXsdFileDirectly to avoid loop!)
                xmlSidebarController.setXsdPathField(loadedXsd.getAbsolutePath());

                // Load XSD data directly into sidebar without triggering XmlUnifiedTab again
                xmlSidebarController.loadXsdDataForSidebar(loadedXsd);
                xmlSidebarController.updateXsdDependentPanes(true);

                // Update the current position info with XSD data
                org.fxmisc.richtext.CodeArea tabCodeArea = tab.getPrimaryCodeArea();
                if (tabCodeArea != null) {
                    updateXmlSidebarForPosition(tab, tabCodeArea.getCaretPosition());
                }
            }
        });

        logger.debug("Connected XML sidebar to tab: {}", tab.getTabId());
    }

    /**
     * Connects the XSD properties panel to an XsdUnifiedTab.
     * Creates a new XsdPropertiesPanel with the tab's editor context.
     *
     * @param tab the XsdUnifiedTab to connect to, or null to disconnect
     */
    public void connectToXsdTab(XsdUnifiedTab tab) {
        if (tab == null) {
            // Disconnect - show placeholder
            currentXsdContext = null;
            xsdPropertiesPanel = null;
            Node placeholder = createXsdPropertiesPane();
            propertiesPanes.put(UnifiedEditorFileType.XSD, placeholder);

            // Update content stack if currently showing XSD
            if (currentEditorType == UnifiedEditorFileType.XSD) {
                contentStack.getChildren().clear();
                contentStack.getChildren().add(placeholder);
                currentPropertiesPane = placeholder;
            }
            return;
        }

        // Reconnect automatically if XsdUnifiedTab rebuilds the graphic view and recreates the editorContext.
        // This is the main reason properties don't update until the pane is closed/reopened.
        tab.setOnEditorContextChangedCallback(() -> Platform.runLater(() -> connectToXsdTab(tab)));

        // Get the editor context from the tab
        XsdEditorContext context = tab.getEditorContext();
        if (context == null) {
            logger.warn("Cannot connect XSD properties panel: no editor context available from tab");
            return;
        }

        // Check if we already have a panel for this context
        if (xsdPropertiesPanel != null && currentXsdContext == context) {
            logger.debug("XSD properties panel already connected to this context");
            // Even if panel exists, ensure it's displayed and update current selection
            // This handles the case where the panel was created before GraphView was fully initialized
            Platform.runLater(() -> {
                if (currentEditorType == UnifiedEditorFileType.XSD) {
                    Node existingPane = propertiesPanes.get(UnifiedEditorFileType.XSD);
                    if (existingPane != null) {
                        contentStack.getChildren().clear();
                        contentStack.getChildren().add(existingPane);
                        currentPropertiesPane = existingPane;
                    }
                    // Update properties with current selection to ensure they're displayed
                    SelectionModel selectionModel = context.getSelectionModel();
                    if (selectionModel != null && !selectionModel.getSelectedNodes().isEmpty()) {
                        VisualNode selectedNode = selectionModel.getPrimarySelection();
                        if (selectedNode != null && xsdPropertiesPanel != null) {
                            xsdPropertiesPanel.updateProperties(selectedNode);
                            logger.debug("Updated properties panel with current selection: {}", selectedNode.getLabel());
                        }
                    } else {
                        // No selection - try to select root node
                        org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView graphView = tab.getGraphView();
                        if (graphView != null) {
                            VisualNode rootNode = graphView.getRootNode();
                            if (rootNode != null && selectionModel != null) {
                                selectionModel.select(rootNode);
                                logger.debug("Auto-selected root node to display properties");
                            }
                        }
                    }
                }
            });
            return;
        }

        // Create a new XsdPropertiesPanel with the context
        currentXsdContext = context;
        xsdPropertiesPanel = new XsdPropertiesPanel(context);

        // Wrap in ScrollPane for scrolling
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(xsdPropertiesPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        // Store in map
        propertiesPanes.put(UnifiedEditorFileType.XSD, scrollPane);

        // Always update the content stack when connecting to XSD tab
        // Use Platform.runLater to ensure UI is fully initialized
        final javafx.scene.control.ScrollPane finalScrollPane = scrollPane;
        Platform.runLater(() -> {
            // Double-check we're still showing XSD
            if (currentEditorType == UnifiedEditorFileType.XSD) {
                contentStack.getChildren().clear();
                contentStack.getChildren().add(finalScrollPane);
                currentPropertiesPane = finalScrollPane;
                logger.debug("Updated content stack with XSD properties panel");
            }

            // Ensure a node is selected so properties are displayed
            // Check if there's already a selection
            SelectionModel selectionModel = context.getSelectionModel();
            if (selectionModel != null && selectionModel.getSelectedNodes().isEmpty()) {
                // No selection - try to select the root node or first available node
                org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView graphView = tab.getGraphView();
                if (graphView != null) {
                    org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode rootNode = graphView.getRootNode();
                    if (rootNode != null) {
                        // Select the root node to display its properties
                        selectionModel.select(rootNode);
                        logger.debug("Auto-selected root node to display properties");
                    }
                }
            }
        });

        logger.info("Connected XSD properties panel to tab: {}", tab.getTabId());
    }

    /**
     * Updates the XML sidebar for the given caret position.
     */
    private void updateXmlSidebarForPosition(XmlUnifiedTab tab, int caretPosition) {
        if (xmlSidebarController == null) return;

        String content = tab.getEditorContent();
        if (content == null || content.isEmpty()) {
            xmlSidebarController.setXPath("/");
            xmlSidebarController.setElementName("");
            return;
        }

        try {
            // Calculate XPath for current position
            String xpath = calculateXPathForPosition(content, caretPosition);
            xmlSidebarController.setXPath(xpath);

            // Get element info at current position
            ElementInfo elementInfo = getElementInfoAtPosition(content, caretPosition);
            if (elementInfo != null) {
                xmlSidebarController.setElementName(elementInfo.name);

                // If XSD is linked, get full element information
                org.fxt.freexmltoolkit.domain.XsdDocumentationData xsdData = tab.getXsdDocumentationData();
                logger.debug("XSD data for sidebar update: xsdData={}, elementName='{}', xpath='{}'",
                        xsdData != null ? "present (elements=" + (xsdData.getExtendedXsdElementMap() != null ? xsdData.getExtendedXsdElementMap().size() : 0) + ")" : "null",
                        elementInfo.name, xpath);
                if (xsdData != null && elementInfo.name != null) {
                    // Try to find the element in XSD
                    org.fxt.freexmltoolkit.domain.XsdExtendedElement xsdElement = findElementInXsd(xsdData, elementInfo.name, xpath);

                    if (xsdElement != null) {
                        logger.debug("Found XSD element: name='{}', type='{}', xpath='{}'",
                                xsdElement.getElementName(), xsdElement.getElementType(), xsdElement.getCurrentXpath());

                        // Set element type from XSD
                        String elementType = xsdElement.getElementType();
                        xmlSidebarController.setElementType(elementType != null ? elementType : "");

                        // Set documentation (strip HTML tags for plain text display)
                        String doc = xsdElement.getDocumentationAsHtml();
                        xmlSidebarController.setDocumentation(stripHtmlTags(doc));

                        // Set example values
                        java.util.List<String> examples = xsdElement.getExampleValues();
                        if (examples != null && !examples.isEmpty()) {
                            xmlSidebarController.setExampleValues(examples);
                        } else {
                            xmlSidebarController.setExampleValues(java.util.List.of("No example values available"));
                        }

                        // Set child elements (format XPaths for display)
                        java.util.List<String> children = xsdElement.getChildren();
                        if (children != null && !children.isEmpty()) {
                            java.util.List<String> formattedChildren = formatChildElementsForDisplay(children, xsdData);
                            xmlSidebarController.setPossibleChildElements(formattedChildren);
                        } else {
                            xmlSidebarController.setPossibleChildElements(java.util.List.of("No child elements"));
                        }
                    } else {
                        // Element not found in XSD - log element map keys for debugging
                        logger.debug("Element not found in XSD for xpath='{}', elementName='{}'", xpath, elementInfo.name);
                        if (xsdData.getExtendedXsdElementMap() != null) {
                            logger.debug("Available XSD element keys (first 10): {}",
                                    xsdData.getExtendedXsdElementMap().keySet().stream().limit(10).toList());
                        }
                        xmlSidebarController.setElementType("");
                        xmlSidebarController.setDocumentation("Element '" + elementInfo.name + "' not found in XSD schema");
                        xmlSidebarController.setExampleValues(java.util.List.of("No example values available"));
                        xmlSidebarController.setPossibleChildElements(java.util.List.of("No child elements"));
                    }
                } else {
                    // No XSD linked
                    xmlSidebarController.setElementType(elementInfo.type != null ? elementInfo.type : "");
                    xmlSidebarController.setDocumentation("");
                    xmlSidebarController.setExampleValues(java.util.List.of("Link XSD to see example values"));
                    xmlSidebarController.setPossibleChildElements(java.util.List.of("Link XSD to see child elements"));
                }
            }
        } catch (Exception e) {
            logger.trace("Error updating sidebar for position {}: {}", caretPosition, e.getMessage());
        }
    }

    /**
     * Finds an element in the XSD data using path context matching.
     * The map keys are full XPaths that include XSD structure like SEQUENCE_, CHOICE_, ALL_.
     * For example: "/FundsXML4/SEQUENCE_1/Funds/SEQUENCE_43/Fund/..."
     * But XML XPaths are simple: "/FundsXML4/Fund"
     *
     * This method cleans up the container elements and matches based on the full path context.
     */
    private org.fxt.freexmltoolkit.domain.XsdExtendedElement findElementInXsd(
            org.fxt.freexmltoolkit.domain.XsdDocumentationData xsdData,
            String elementName,
            String xpath) {
        if (xsdData == null) {
            return null;
        }

        var elementMap = xsdData.getExtendedXsdElementMap();
        if (elementMap == null || elementMap.isEmpty()) {
            logger.debug("XSD element map is null or empty");
            return null;
        }

        // Try exact XPath match first (unlikely to work due to SEQUENCE/CHOICE nodes)
        if (xpath != null && elementMap.containsKey(xpath)) {
            logger.debug("Found exact XPath match: {}", xpath);
            return elementMap.get(xpath);
        }

        // Clean the XML XPath (remove empty parts)
        java.util.List<String> cleanXmlPath = new java.util.ArrayList<>();
        if (xpath != null) {
            for (String part : xpath.split("/")) {
                if (!part.isEmpty()) {
                    cleanXmlPath.add(part);
                }
            }
        }

        if (cleanXmlPath.isEmpty() || elementName == null) {
            return null;
        }

        // Try to find a map entry whose cleaned path matches the full XML path context
        org.fxt.freexmltoolkit.domain.XsdExtendedElement bestMatch = null;
        int bestMatchScore = -1;

        for (var entry : elementMap.entrySet()) {
            String mapXPath = entry.getKey();
            org.fxt.freexmltoolkit.domain.XsdExtendedElement element = entry.getValue();

            // Skip container elements
            String elName = element.getElementName();
            if (elName == null || elName.startsWith("SEQUENCE_") || elName.startsWith("CHOICE_") || elName.startsWith("ALL_")) {
                continue;
            }

            // Check if element name matches
            if (!elName.equals(elementName)) {
                continue;
            }

            // Clean the map XPath (remove container elements)
            java.util.List<String> cleanMapPath = new java.util.ArrayList<>();
            for (String part : mapXPath.split("/")) {
                if (!part.isEmpty() && !part.startsWith("SEQUENCE_") && !part.startsWith("CHOICE_") && !part.startsWith("ALL_")) {
                    cleanMapPath.add(part);
                }
            }

            // Calculate score: compare full path from the start
            // This ensures we match the correct context (e.g., /FundsXML4/AssetMasterData/Asset vs /FundsXML4/Fund/Asset)
            int score = 0;
            boolean fullMatch = cleanXmlPath.size() == cleanMapPath.size();

            // Score based on how many path elements match from the start
            int minLen = Math.min(cleanXmlPath.size(), cleanMapPath.size());
            boolean pathMatches = true;
            for (int i = 0; i < minLen; i++) {
                if (cleanXmlPath.get(i).equals(cleanMapPath.get(i))) {
                    score++;
                } else {
                    pathMatches = false;
                    break;
                }
            }

            // If the full path matches exactly, give it a much higher score
            if (fullMatch && pathMatches && score == cleanXmlPath.size()) {
                score += 1000; // Bonus for exact match
            }

            // Prefer matches with higher score
            if (score > bestMatchScore) {
                bestMatchScore = score;
                bestMatch = element;
                logger.debug("New best match: score={}, cleanXmlPath={}, cleanMapPath={}",
                        score, cleanXmlPath, cleanMapPath);
            }
        }

        if (bestMatch != null) {
            logger.debug("Found element by path matching (score={}): elementName='{}', xpath='{}'",
                    bestMatchScore, bestMatch.getElementName(), bestMatch.getCurrentXpath());
            return bestMatch;
        }

        logger.debug("No match found for xpath='{}', elementName='{}'", xpath, elementName);
        return null;
    }

    /**
     * Formats child element XPaths for display, extracting just the element name.
     * Filters out attributes (starting with @) and container elements (SEQUENCE_, CHOICE_, ALL_).
     */
    private java.util.List<String> formatChildElementsForDisplay(
            java.util.List<String> childXPaths,
            org.fxt.freexmltoolkit.domain.XsdDocumentationData xsdData) {
        if (childXPaths == null || childXPaths.isEmpty()) {
            return java.util.List.of("No child elements");
        }

        var elementMap = xsdData != null ? xsdData.getExtendedXsdElementMap() : null;
        java.util.List<String> formatted = new java.util.ArrayList<>();

        for (String childXPath : childXPaths) {
            // Skip attributes
            if (childXPath.contains("/@")) {
                continue;
            }

            // Extract element name from XPath
            String elementName = childXPath;
            int lastSlash = childXPath.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < childXPath.length() - 1) {
                elementName = childXPath.substring(lastSlash + 1);
            }

            // Skip container elements (SEQUENCE_, CHOICE_, ALL_)
            if (elementName.startsWith("SEQUENCE_") || elementName.startsWith("CHOICE_") || elementName.startsWith("ALL_")) {
                // For containers, look at their children instead
                if (elementMap != null && elementMap.containsKey(childXPath)) {
                    var containerElement = elementMap.get(childXPath);
                    if (containerElement.getChildren() != null) {
                        formatted.addAll(formatChildElementsForDisplay(containerElement.getChildren(), xsdData));
                    }
                }
                continue;
            }

            // Get type information if available
            String displayText = elementName;
            if (elementMap != null && elementMap.containsKey(childXPath)) {
                var childElement = elementMap.get(childXPath);
                String type = childElement.getElementType();
                if (type != null && !type.isEmpty() && !type.equals("(container)")) {
                    displayText = elementName + " : " + type;
                }
                // Add cardinality indicator
                if (childElement.isMandatory()) {
                    displayText += " *";
                }
            }

            if (!formatted.contains(displayText)) {
                formatted.add(displayText);
            }
        }

        return formatted.isEmpty() ? java.util.List.of("No child elements") : formatted;
    }

    /**
     * Calculates the XPath for the given position in the XML content.
     */
    private String calculateXPathForPosition(String content, int position) {
        if (content == null || position < 0 || position > content.length()) {
            return "/";
        }

        StringBuilder xpath = new StringBuilder("/");
        java.util.List<String> pathParts = new java.util.ArrayList<>();

        int depth = 0;
        int i = 0;
        String currentTag = null;

        while (i < position && i < content.length()) {
            if (content.charAt(i) == '<') {
                int tagEnd = content.indexOf('>', i);
                if (tagEnd == -1) break;

                String tagContent = content.substring(i + 1, tagEnd).trim();

                if (tagContent.startsWith("?") || tagContent.startsWith("!")) {
                    // XML declaration or comment, skip
                } else if (tagContent.startsWith("/")) {
                    // Closing tag
                    if (!pathParts.isEmpty()) {
                        pathParts.remove(pathParts.size() - 1);
                    }
                    depth--;
                } else if (!tagContent.endsWith("/")) {
                    // Opening tag (not self-closing)
                    String tagName = tagContent.split("\\s")[0];
                    pathParts.add(tagName);
                    currentTag = tagName;
                    depth++;
                } else {
                    // Self-closing tag
                    String tagName = tagContent.replace("/", "").split("\\s")[0];
                    currentTag = tagName;
                }

                i = tagEnd;
            }
            i++;
        }

        if (pathParts.isEmpty()) {
            return "/";
        }

        // Build XPath without double slash
        StringBuilder result = new StringBuilder();
        for (String part : pathParts) {
            result.append("/").append(part);
        }

        return result.toString();
    }

    /**
     * Gets element information at the given position.
     */
    private ElementInfo getElementInfoAtPosition(String content, int position) {
        if (content == null || position < 0) {
            return null;
        }

        // Find the element at the current position
        int searchStart = Math.max(0, position - 500);
        int searchEnd = Math.min(content.length(), position + 100);
        String searchArea = content.substring(searchStart, searchEnd);

        // Find the last opening tag before the position
        int lastOpenTag = -1;
        int relativePos = position - searchStart;

        for (int i = relativePos; i >= 0; i--) {
            if (i > 0 && searchArea.charAt(i - 1) == '<' && searchArea.charAt(i) != '/' && searchArea.charAt(i) != '!') {
                lastOpenTag = i - 1;
                break;
            }
        }

        if (lastOpenTag == -1) {
            return null;
        }

        int tagEnd = searchArea.indexOf('>', lastOpenTag);
        if (tagEnd == -1) {
            return null;
        }

        String tagContent = searchArea.substring(lastOpenTag + 1, tagEnd).trim();
        String tagName = tagContent.split("\\s")[0].replace("/", "");

        return new ElementInfo(tagName, null);
    }

    /**
     * Gets documentation for an element from XSD data.
     */
    private String getDocumentationForElement(org.fxt.freexmltoolkit.domain.XsdDocumentationData xsdData, String elementName) {
        if (xsdData == null || elementName == null) {
            return null;
        }

        // Try to get documentation from XSD element map
        var elementMap = xsdData.getExtendedXsdElementMap();
        if (elementMap != null && elementMap.containsKey(elementName)) {
            var element = elementMap.get(elementName);
            if (element != null) {
                return element.getDocumentationAsHtml();
            }
        }

        return null;
    }

    /**
     * Strips HTML tags from a string for plain text display.
     * Converts common HTML entities and preserves line breaks.
     */
    private String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // Replace paragraph and line break tags with newlines
        String text = html.replaceAll("(?i)</p>\\s*<p>", "\n\n");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n");
        text = text.replaceAll("(?i)<p>", "");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Decode common HTML entities
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");

        // Trim leading/trailing whitespace and normalize line breaks
        text = text.trim();
        text = text.replaceAll("\\n{3,}", "\n\n"); // Max 2 consecutive newlines

        return text;
    }

    // ==================== Validation Status Updates ====================

    /**
     * Updates the validation status displayed in the XML sidebar.
     *
     * @param status the status message to display
     * @param isValid true if validation passed, false otherwise
     * @param errors list of validation errors (can be null)
     */
    public void updateValidationStatus(String status, boolean isValid, java.util.List<org.fxt.freexmltoolkit.domain.ValidationError> errors) {
        if (xmlSidebarController == null) {
            // Ensure the XML properties pane is created
            propertiesPanes.computeIfAbsent(UnifiedEditorFileType.XML, this::createPropertiesPaneForType);
        }

        if (xmlSidebarController != null) {
            String color = isValid ? "#28a745" : "#dc3545"; // green for valid, red for invalid
            xmlSidebarController.updateValidationStatus(status, color, errors);
            logger.debug("Updated validation status: {} (valid={})", status, isValid);
        }
    }

    /**
     * Updates the Schematron validation status displayed in the XML sidebar.
     *
     * @param status the status message to display
     * @param isValid true if validation passed, false otherwise
     * @param errors list of Schematron validation errors (can be null)
     */
    public void updateSchematronValidationStatus(String status, boolean isValid,
            java.util.List<org.fxt.freexmltoolkit.service.SchematronService.SchematronValidationError> errors) {
        if (xmlSidebarController == null) {
            // Ensure the XML properties pane is created
            propertiesPanes.computeIfAbsent(UnifiedEditorFileType.XML, this::createPropertiesPaneForType);
        }

        if (xmlSidebarController != null) {
            String color = isValid ? "#28a745" : "#dc3545"; // green for valid, red for invalid
            xmlSidebarController.updateSchematronValidationStatus(status, color, errors);
            logger.debug("Updated Schematron validation status: {} (valid={})", status, isValid);
        }
    }

    /**
     * Simple class to hold element information.
     */
    private record ElementInfo(String name, String type) {}
}
