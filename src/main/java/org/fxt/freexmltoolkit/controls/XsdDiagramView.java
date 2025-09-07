package org.fxt.freexmltoolkit.controls;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.controls.commands.*;
import org.fxt.freexmltoolkit.controls.dialogs.ExtractComplexTypeDialog;
import org.fxt.freexmltoolkit.controls.dialogs.ImportIncludeManagerDialog;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.domain.command.ConvertAttributeToElementCommand;
import org.fxt.freexmltoolkit.domain.command.ConvertElementToAttributeCommand;
import org.fxt.freexmltoolkit.domain.command.ExtractComplexTypeCommand;
import org.fxt.freexmltoolkit.domain.command.InlineTypeDefinitionCommand;
import org.fxt.freexmltoolkit.service.XsdClipboardService;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.fxt.freexmltoolkit.service.XsdLiveValidationService;
import org.fxt.freexmltoolkit.service.XsdViewService;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class XsdDiagramView {

    private static final Logger logger = LogManager.getLogger(XsdDiagramView.class);

    private XsdNodeInfo rootNode;
    private final XsdController controller;
    private final XsdDomManipulator domManipulator;

    // Live validation
    private final XsdLiveValidationService validationService;
    private final List<Node> validationErrorNodes = new ArrayList<>();

    // Undo/Redo system
    private final XsdUndoManager undoManager;
    private Button undoButton;
    private Button redoButton;

    public XsdUndoManager getUndoManager() {
        return undoManager;
    }

    // Drag & Drop system
    private final XsdDragDropManager dragDropManager;

    // Search/Filter system
    private TextField searchField;
    private Button clearSearchButton;
    private final List<XsdNodeInfo> allNodes = new ArrayList<>();
    private final List<XsdNodeInfo> filteredNodes = new ArrayList<>();
    private final Map<XsdNodeInfo, Node> nodeViewCache = new HashMap<>();
    private String currentSearchText = "";
    private boolean isSearchActive = false;
    private final List<String> searchHistory = new ArrayList<>();
    private static final int MAX_SEARCH_HISTORY = 10;
    private Timeline searchDebounceTimer;

    private XsdNodeInfo selectedNode;
    private XsdPropertyPanel propertyPanel;
    private XsdValidationPanel validationPanel;

    private ScrollPane treeScrollPane;

    // Enhanced Styles inspired by Altova XMLSpy
    private static final String NODE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f0f8ff); " +
                    "-fx-border-color: #4a90e2; -fx-border-width: 2px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 8px 12px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c5aa0; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String ATTRIBUTE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fffef7, #f9f5e7); " +
                    "-fx-border-color: #d4a147; -fx-border-width: 1.5px; " +
                    "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                    "-fx-padding: 4px 8px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: normal; -fx-text-fill: #8b6914; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 1, 0, 0.5, 0.5);";

    private static final String SEQUENCE_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                    "-fx-border-color: #6c757d; -fx-border-width: 2px; -fx-border-style: solid; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #495057; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String CHOICE_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fffbf0, #fff3cd); " +
                    "-fx-border-color: #ff8c00; -fx-border-width: 2px; -fx-border-style: dashed; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #b45309; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String ANY_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #dee2e6); " +
                    "-fx-border-color: #adb5bd; -fx-border-width: 1.5px; -fx-border-style: dotted; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #6c757d; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 1, 0, 0.5, 0.5);";

    private static final String CARDINALITY_LABEL_STYLE =
            "-fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-font-family: 'Consolas', 'Monaco', monospace; " +
                    "-fx-background-color: #f8f9fa; -fx-padding: 2px 4px; -fx-background-radius: 3px; " +
                    "-fx-border-color: #dee2e6; -fx-border-width: 1px; -fx-border-radius: 3px;";

    // Style for optional elements with dashed border
    private static final String OPTIONAL_NODE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f0f8ff); " +
                    "-fx-border-color: #4a90e2; -fx-border-width: 2px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 8px; -fx-font-size: 12px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #2c5aa0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 1, 1); " +
                    "-fx-border-style: dashed;";

    // Style for optional attributes with dashed border
    private static final String OPTIONAL_ATTRIBUTE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fff5e6, #ffe4b3); " +
                    "-fx-border-color: #d4a147; -fx-border-width: 1px; " +
                    "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                    "-fx-padding: 4px 6px; -fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #8b6914; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 1, 0, 0.5, 0.5); " +
                    "-fx-border-style: dashed;";

    // Style for repeatable elements (maxOccurs > 1) with double border effect
    private static final String REPEATABLE_NODE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f0f8ff); " +
                    "-fx-border-color: #4a90e2, #87ceeb; -fx-border-width: 2px, 1px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 8px; -fx-font-size: 12px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #2c5aa0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 1, 1); " +
                    "-fx-border-insets: 0, 2px;";

    // Style for repeatable attributes (maxOccurs > 1) with double border effect
    private static final String REPEATABLE_ATTRIBUTE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fff5e6, #ffe4b3); " +
                    "-fx-border-color: #d4a147, #f4c430; -fx-border-width: 1px, 1px; " +
                    "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                    "-fx-padding: 4px 6px; -fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #8b6914; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 1, 0, 0.5, 0.5); " +
                    "-fx-border-insets: 0, 1px;";

    // Style for optional AND repeatable elements (minOccurs=0 AND maxOccurs>1) 
    private static final String OPTIONAL_REPEATABLE_NODE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f0f8ff); " +
                    "-fx-border-color: #4a90e2, #87ceeb; -fx-border-width: 2px, 1px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 8px; -fx-font-size: 12px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #2c5aa0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 1, 1); " +
                    "-fx-border-style: dashed; -fx-border-insets: 0, 2px;";

    // Style for optional AND repeatable attributes (minOccurs=0 AND maxOccurs>1)
    private static final String OPTIONAL_REPEATABLE_ATTRIBUTE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fff5e6, #ffe4b3); " +
                    "-fx-border-color: #d4a147, #f4c430; -fx-border-width: 1px, 1px; " +
                    "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                    "-fx-padding: 4px 6px; -fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #8b6914; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 1, 0, 0.5, 0.5); " +
                    "-fx-border-style: dashed; -fx-border-insets: 0, 1px;";

    private static final String TOGGLE_BUTTON_STYLE =
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4a90e2; " +
                    "-fx-background-color: #ffffff; -fx-border-color: #4a90e2; -fx-border-width: 1px; " +
                    "-fx-border-radius: 50%; -fx-background-radius: 50%; -fx-padding: 2px 6px; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 1, 0, 0.5, 0.5);";

    private static final String DOC_LABEL_STYLE =
            "-fx-font-size: 11px; -fx-text-fill: #6c757d; -fx-font-style: italic; " +
                    "-fx-padding: 4px 8px 4px 12px; -fx-background-color: rgba(108, 117, 125, 0.05); " +
                    "-fx-background-radius: 3px; -fx-border-color: rgba(108, 117, 125, 0.15); " +
                    "-fx-border-width: 0 0 0 3px; -fx-border-radius: 0 3px 3px 0;";

    private static final String DETAIL_LABEL_STYLE = "-fx-font-weight: bold; -fx-text-fill: #333;";

    // Search highlighting styles
    private static final String SEARCH_HIGHLIGHT_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fff3cd, #ffeaa7); " +
                    "-fx-border-color: #ff6b35; -fx-border-width: 2px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(255,107,53,0.4), 4, 0, 0, 0); " +
                    "-fx-padding: 6px 8px; -fx-font-weight: bold;";

    private static final String SEARCH_HIGHLIGHT_TEXT_STYLE = "-fx-fill: #d63031; -fx-font-weight: bold;";


    public XsdDiagramView(XsdNodeInfo rootNode, XsdController controller, String initialDoc, String initialJavadoc) {
        this(rootNode, controller, initialDoc, initialJavadoc, null);
    }

    public XsdDiagramView(XsdNodeInfo rootNode, XsdController controller, String initialDoc, String initialJavadoc, XsdDomManipulator existingManipulator) {
        this.rootNode = rootNode;
        this.controller = controller;
        // initialDoc and initialJavadoc are no longer used since they're handled in propertyPanel
        this.domManipulator = existingManipulator != null ? existingManipulator : new XsdDomManipulator();
        this.validationService = XsdLiveValidationService.getInstance();

        // Initialize undo/redo system
        this.undoManager = new XsdUndoManager();
        this.undoManager.setListener(new XsdUndoManager.UndoRedoListener() {
            @Override
            public void onUndoRedoStateChanged(boolean canUndo, boolean canRedo) {
                Platform.runLater(() -> {
                    if (undoButton != null) {
                        undoButton.setDisable(!canUndo);
                        undoButton.setTooltip(new Tooltip(canUndo ? "Undo: " + undoManager.getUndoDescription() : "Nothing to undo"));
                    }
                    if (redoButton != null) {
                        redoButton.setDisable(!canRedo);
                        redoButton.setTooltip(new Tooltip(canRedo ? "Redo: " + undoManager.getRedoDescription() : "Nothing to redo"));
                    }
                });
            }
        });

        // Initialize drag & drop system
        this.dragDropManager = new XsdDragDropManager(this, domManipulator, undoManager);

        // Setup validation listener
        this.validationService.addValidationListener(new XsdLiveValidationService.ValidationListener() {
            @Override
            public void onValidationComplete(XsdLiveValidationService.ValidationResult result) {
                updateValidationUI(result);
            }

            @Override
            public void onElementValidated(XsdLiveValidationService.ValidationResult result, Element element) {
                updateElementValidationUI(result, element);
            }
        });
    }

    public void setXsdContent(String xsdContent) {
        // Store the XSD content in the DOM manipulator
        try {
            System.out.println("DEBUG: Loading XSD content into DOM manipulator");
            System.out.println("DEBUG: XSD content length: " + (xsdContent != null ? xsdContent.length() : "null"));

            domManipulator.loadXsd(xsdContent);

            System.out.println("DEBUG: XSD loaded successfully into DOM manipulator");

            // Update property panel with DOM manipulator
            if (propertyPanel != null) {
                propertyPanel.setDomManipulator(domManipulator);
                System.out.println("DEBUG: Property panel updated with DOM manipulator");
            }

            // Trigger live validation
            triggerLiveValidation();

        } catch (Exception e) {
            System.out.println("DEBUG: Error loading XSD: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public XsdDomManipulator getDomManipulator() {
        return domManipulator;
    }

    public Node build() {
        if (rootNode == null) {
            return new Label("No element information found.");
        }

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);

        // Create main left container with toolbar and diagram
        VBox leftContainer = new VBox();

        // Create toolbar with undo/redo buttons
        HBox toolbar = createToolbar();
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1px 0;");
        
        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        diagramContainer.setAlignment(Pos.TOP_CENTER); // Root-Node zentrieren
        Node rootNodeView = createNodeView(rootNode);
        diagramContainer.getChildren().add(rootNodeView);

        this.treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(false);
        treeScrollPane.setFitToHeight(false);

        leftContainer.getChildren().addAll(toolbar, treeScrollPane);
        VBox.setVgrow(treeScrollPane, javafx.scene.layout.Priority.ALWAYS);

        // Create tabbed right pane with Properties and Validation
        TabPane rightTabPane = new TabPane();
        rightTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Properties Tab
        Tab propertiesTab = new Tab("Properties");
        propertiesTab.setGraphic(createColoredIcon("bi-gear", "#6c757d"));

        propertyPanel = new XsdPropertyPanel();
        propertyPanel.setDomManipulator(domManipulator);
        propertyPanel.setController(controller);
        propertyPanel.setOnPropertyChanged(message -> {
            System.out.println("DEBUG: Property changed callback triggered: " + message);

            // Use selective refresh instead of full rebuild to preserve expansion states
            String updatedXsd = domManipulator.getXsdAsString();
            if (updatedXsd != null) {
                // Update text editor only, don't rebuild diagram
                controller.updateXsdContent(updatedXsd, false);

                // Perform selective refresh of only the changed node
                performSelectiveNodeRefresh();
            }
            
            triggerLiveValidation();
        });

        // Set up command-based property changes (we'll need to modify XsdPropertyPanel for this)
        // For now, the existing callback will work, but ideally we'd integrate commands here too

        ScrollPane propertyScrollPane = new ScrollPane(propertyPanel);
        propertyScrollPane.setFitToWidth(true);
        propertyScrollPane.setFitToHeight(true);
        propertyScrollPane.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        propertiesTab.setContent(propertyScrollPane);

        // Validation Tab
        Tab validationTab = new Tab("Validation");
        validationTab.setGraphic(createColoredIcon("bi-shield-check", "#28a745"));

        validationPanel = new XsdValidationPanel();

        ScrollPane validationScrollPane = new ScrollPane(validationPanel);
        validationScrollPane.setFitToWidth(true);
        validationScrollPane.setFitToHeight(true);
        validationScrollPane.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        validationTab.setContent(validationScrollPane);

        rightTabPane.getTabs().addAll(propertiesTab, validationTab);

        splitPane.getItems().addAll(leftContainer, rightTabPane);
        return splitPane;
    }

    /**
     * Creates the toolbar with undo/redo buttons
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Undo button
        undoButton = new Button();
        undoButton.setGraphic(createColoredIcon("bi-arrow-counterclockwise", "#007bff"));
        undoButton.setTooltip(new Tooltip("Nothing to undo"));
        undoButton.setDisable(true);
        undoButton.setOnAction(e -> {
            if (undoManager.undo()) {
                refreshView();
                triggerLiveValidation();
            }
        });

        // Redo button
        redoButton = new Button();
        redoButton.setGraphic(createColoredIcon("bi-arrow-clockwise", "#007bff"));
        redoButton.setTooltip(new Tooltip("Nothing to redo"));
        redoButton.setDisable(true);
        redoButton.setOnAction(e -> {
            if (undoManager.redo()) {
                refreshView();
                triggerLiveValidation();
            }
        });

        // Style buttons
        String buttonStyle = "-fx-background-color: transparent; -fx-border-color: #6c757d; " +
                "-fx-border-width: 1px; -fx-border-radius: 3px; " +
                "-fx-padding: 4px 8px; -fx-cursor: hand;";
        String buttonHoverStyle = buttonStyle + "-fx-background-color: #e9ecef;";

        undoButton.setStyle(buttonStyle);
        undoButton.setOnMouseEntered(e -> undoButton.setStyle(buttonHoverStyle));
        undoButton.setOnMouseExited(e -> undoButton.setStyle(buttonStyle));

        redoButton.setStyle(buttonStyle);
        redoButton.setOnMouseEntered(e -> redoButton.setStyle(buttonHoverStyle));
        redoButton.setOnMouseExited(e -> redoButton.setStyle(buttonStyle));

        // Separator
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Status label
        Label statusLabel = new Label("XSD Editor");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");

        // Second separator for search section
        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Search components
        HBox searchBox = createSearchComponents();

        // Spacer to push search to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        toolbar.getChildren().addAll(
                undoButton, redoButton, separator, statusLabel,
                spacer, separator2, searchBox
        );

        return toolbar;
    }

    /**
     * Create search and filter components
     */
    private HBox createSearchComponents() {
        HBox searchBox = new HBox(5);
        searchBox.setAlignment(Pos.CENTER_LEFT);


        // Search TextField
        searchField = new TextField();
        searchField.setPromptText("Search nodes...");
        searchField.setPrefWidth(200);
        searchField.setTooltip(new Tooltip("Search by name, type, or documentation"));

        // Live search as user types with debounce
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            // Cancel previous timer if exists
            if (searchDebounceTimer != null) {
                searchDebounceTimer.stop();
            }

            // Create new timer with 300ms delay
            searchDebounceTimer = new Timeline(new KeyFrame(
                    Duration.millis(300),
                    e -> performSearch()
            ));
            searchDebounceTimer.play();
        });

        // Clear search button
        clearSearchButton = new Button();
        clearSearchButton.setGraphic(createColoredIcon("bi-x-circle", "#dc3545"));
        clearSearchButton.setTooltip(new Tooltip("Clear search"));
        clearSearchButton.setDisable(true);
        clearSearchButton.setOnAction(e -> clearSearch());

        // Style components
        String searchStyle = "-fx-background-color: #ffffff; -fx-border-color: #ced4da; " +
                "-fx-border-width: 1px; -fx-border-radius: 4px; " +
                "-fx-padding: 4px 8px; -fx-font-size: 12px;";

        String buttonStyle = "-fx-background-color: transparent; -fx-border-color: #6c757d; " +
                "-fx-border-width: 1px; -fx-border-radius: 3px; " +
                "-fx-padding: 4px 8px; -fx-cursor: hand;";

        searchField.setStyle(searchStyle);
        clearSearchButton.setStyle(buttonStyle);

        // Add focus styling
        searchField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                searchField.setStyle(searchStyle + "-fx-border-color: #80bdff; -fx-effect: dropshadow(three-pass-box, rgba(0,123,255,.25), 0, 0, 0, 2);");
            } else {
                searchField.setStyle(searchStyle);
            }
        });

        searchBox.getChildren().addAll(
                new Label("Search:"), searchField, clearSearchButton
        );

        return searchBox;
    }

    /**
     * Perform search and filter operation
     */
    private void performSearch() {
        String searchText = searchField.getText().toLowerCase().trim();

        // Enable/disable clear button
        boolean hasSearch = !searchText.isEmpty();
        clearSearchButton.setDisable(!hasSearch);

        if (!hasSearch) {
            // No search active, show all nodes normally
            refreshNodeVisibility(null, null);
            return;
        }

        // Build list of all nodes
        allNodes.clear();
        collectAllNodes(rootNode, allNodes);

        // Filter nodes based on search text
        filteredNodes.clear();
        for (XsdNodeInfo node : allNodes) {
            if (matchesFilter(node, searchText)) {
                filteredNodes.add(node);
            }
        }

        // Add to search history if it's a meaningful search
        if (searchText.length() > 1) {
            addToSearchHistory(searchText);
        }

        // Update UI to highlight matching nodes
        refreshNodeVisibility(searchText, filteredNodes);

        logger.info("Search '{}' found {} results",
                searchText, filteredNodes.size());
    }

    /**
     * Clear search and show all nodes
     */
    private void clearSearch() {
        searchField.clear();
        clearSearchButton.setDisable(true);
        refreshNodeVisibility(null, null);
        logger.info("Search cleared");
    }

    /**
     * Check if node matches search and filter criteria
     */
    private boolean matchesFilter(XsdNodeInfo node, String searchText) {
        // Search in node name
        if (node.name() != null && node.name().toLowerCase().contains(searchText)) {
            return true;
        }

        // Search in type
        if (node.type() != null && node.type().toLowerCase().contains(searchText)) {
            return true;
        }

        // Search in documentation
        if (node.documentation() != null && node.documentation().toLowerCase().contains(searchText)) {
            return true;
        }

        // Fuzzy search in name (allow some typos)
        return node.name() != null && fuzzyMatch(node.name().toLowerCase(), searchText);
    }

    /**
     * Simple fuzzy matching algorithm
     */
    private boolean fuzzyMatch(String text, String pattern) {
        if (pattern.length() > text.length()) return false;

        int patternIdx = 0;
        for (int i = 0; i < text.length() && patternIdx < pattern.length(); i++) {
            if (text.charAt(i) == pattern.charAt(patternIdx)) {
                patternIdx++;
            }
        }
        return patternIdx == pattern.length();
    }

    /**
     * Collect all nodes recursively for search
     */
    private void collectAllNodes(XsdNodeInfo node, List<XsdNodeInfo> result) {
        result.add(node);
        if (node.children() != null) {
            for (XsdNodeInfo child : node.children()) {
                collectAllNodes(child, result);
            }
        }
    }

    /**
     * Update node visibility and highlighting based on search results
     */
    private void refreshNodeVisibility(String searchText, List<XsdNodeInfo> matchingNodes) {
        currentSearchText = searchText != null ? searchText : "";
        isSearchActive = matchingNodes != null && !matchingNodes.isEmpty();

        // Only rebuild diagram if search state has changed or we have matches
        if (isSearchActive || (matchingNodes != null && matchingNodes.isEmpty())) {
            // Rebuild the diagram to apply/remove search highlighting
            rebuildDiagram();
        }

        // If search is active and we have results, scroll to first result
        if (isSearchActive && !matchingNodes.isEmpty()) {
            Platform.runLater(() -> scrollToFirstSearchResult(matchingNodes.get(0)));
        }
    }

    /**
     * Scroll to and highlight the first search result
     */
    private void scrollToFirstSearchResult(XsdNodeInfo firstResult) {
        // Find the node view for the first result and scroll to it
        if (treeScrollPane != null) {
            // Simple scroll to top for now - could be enhanced to find specific node
            treeScrollPane.setVvalue(0.0);
            logger.info("Scrolled to first search result: {}", firstResult.name());
        }
    }

    /**
     * Check if a node matches current search criteria (for highlighting)
     */
    private boolean isNodeHighlighted(XsdNodeInfo node) {
        if (!isSearchActive || currentSearchText.isEmpty()) {
            return false;
        }
        return filteredNodes.contains(node);
    }

    /**
     * Apply search highlighting to a label if the node matches search criteria
     */
    private void applySearchHighlighting(Label label, XsdNodeInfo node) {
        if (isNodeHighlighted(node)) {
            String currentStyle = label.getStyle();
            label.setStyle(currentStyle + "; " + SEARCH_HIGHLIGHT_STYLE);
            label.setTextFill(javafx.scene.paint.Color.valueOf("#d63031"));

            // Add a subtle animation to draw attention
            Platform.runLater(() -> {
                label.setScaleX(1.1);
                label.setScaleY(1.1);

                Timeline timeline = new Timeline(
                        new KeyFrame(Duration.millis(200),
                                e -> {
                                    label.setScaleX(1.0);
                                    label.setScaleY(1.0);
                                })
                );
                timeline.play();
            });
        }
    }

    /**
     * Create a highlighted text if it matches search criteria
     */
    private Label createHighlightedLabel(String text, XsdNodeInfo node) {
        Label label = new Label(text);
        applySearchHighlighting(label, node);
        return label;
    }

    /**
     * Add search term to history, avoiding duplicates and maintaining max size
     */
    private void addToSearchHistory(String searchTerm) {
        // Remove if already exists
        searchHistory.remove(searchTerm);

        // Add to front
        searchHistory.add(0, searchTerm);

        // Maintain max size
        while (searchHistory.size() > MAX_SEARCH_HISTORY) {
            searchHistory.remove(searchHistory.size() - 1);
        }

        // Update search field with history (could add autocomplete later)
        updateSearchFieldTooltip();
    }

    /**
     * Update search field tooltip with recent searches
     */
    private void updateSearchFieldTooltip() {
        if (searchHistory.isEmpty()) {
            searchField.setTooltip(new Tooltip("Search by name, type, or documentation"));
        } else {
            StringBuilder tooltipText = new StringBuilder("Search by name, type, or documentation\nRecent searches:\n");
            for (int i = 0; i < Math.min(5, searchHistory.size()); i++) {
                tooltipText.append("• ").append(searchHistory.get(i)).append("\n");
            }
            searchField.setTooltip(new Tooltip(tooltipText.toString()));
        }
    }

    /**
     * Haupt-Dispatch-Methode, die entscheidet, welche Art von Ansicht für einen Knoten erstellt wird.
     */
    private Node createNodeView(XsdNodeInfo node) {
        Node viewNode = switch (node.nodeType()) {
            case ELEMENT -> createElementNodeView(node);
            case ATTRIBUTE -> createAttributeNodeView(node);
            case ANY -> createAnyNodeView(node);
            case SEQUENCE -> createStructuralNodeView(node, "SEQUENCE", SEQUENCE_NODE_STYLE);
            case CHOICE -> createStructuralNodeView(node, "CHOICE", CHOICE_NODE_STYLE);
            default -> new Label("Unknown Node Type: " + node.name());
        };

        nodeViewCache.put(node, viewNode);

        return viewNode;
    }

    /**
     * Erstellt die Ansicht für ein <xs:element>.
     * Dies ist die Kernmethode für das neue Kaskaden-Layout.
     */
    private Node createElementNodeView(XsdNodeInfo node) {
        // Hauptcontainer für die gesamte Elementansicht (Info links, Kinder rechts)
        HBox mainContainer = new HBox(10);
        mainContainer.setAlignment(Pos.TOP_LEFT);

        // Linker Teil: Infos über das Element selbst und seine Attribute
        VBox elementInfoContainer = new VBox(5);
        elementInfoContainer.setPadding(new Insets(5));
        elementInfoContainer.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 1px 0 0; -fx-border-style: dotted; -fx-padding: 5;");

        // Kinder in Attribute und Strukturelemente aufteilen
        List<XsdNodeInfo> attributes = node.children().stream()
                .filter(c -> c.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE)
                .toList();
        List<XsdNodeInfo> structuralChildren = node.children().stream()
                .filter(c -> c.nodeType() != XsdNodeInfo.NodeType.ATTRIBUTE)
                .collect(Collectors.toList());

        // Wenn strukturelle Kinder vorhanden sind, das Element vertikal mittig ausrichten
        if (!structuralChildren.isEmpty()) {
            elementInfoContainer.setAlignment(Pos.CENTER_LEFT);
        }

        // Rechter Teil: Container für strukturelle Kinder (sequence, choice) mit Verbindungslinien
        VBox structuralChildrenContainer = new VBox(5);
        structuralChildrenContainer.setPadding(new Insets(0, 0, 0, 20));
        structuralChildrenContainer.setVisible(false);
        structuralChildrenContainer.setManaged(false);
        structuralChildrenContainer.setAlignment(Pos.TOP_LEFT);

        // Add visual connector line
        structuralChildrenContainer.setStyle("-fx-border-color: #4a90e2; -fx-border-width: 0 0 0 2px; -fx-border-style: solid;");

        // --- Linken Teil befüllen (elementInfoContainer) ---
        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = createHighlightedLabel(node.name(), node);
        // Determine appropriate style based on cardinality
        String labelStyle = determineNodeLabelStyle(node, false);
        nameLabel.setStyle(labelStyle);

        // Apply additional search highlighting if needed
        applySearchHighlighting(nameLabel, node);

        // Add type-specific icon
        FontIcon elementIcon = createTypeSpecificIcon(node.type());
        nameLabel.setGraphic(elementIcon);

        nameLabel.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                showContextMenu(nameLabel, node);
            } else {
                updateDetailPane(node);
            }
        });

        // Enable drag and drop for this node
        dragDropManager.makeDraggable(nameLabel, node);
        dragDropManager.makeDropTarget(nameLabel, node);

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            nameAndToggleRow.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            nameAndToggleRow.getChildren().add(nameLabel);
        }
        elementInfoContainer.getChildren().add(nameAndToggleRow);

        // Dokumentation für das Element hinzufügen
        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            elementInfoContainer.getChildren().add(docLabel);
        }

        // Attribute direkt zum linken Container hinzufügen
        if (!attributes.isEmpty()) {
            VBox attributeBox = new VBox(3);
            attributeBox.setPadding(new Insets(8, 0, 0, 15));
            for (XsdNodeInfo attr : attributes) {
                attributeBox.getChildren().add(createNodeView(attr));
            }
            elementInfoContainer.getChildren().add(attributeBox);
        }

        // --- Rechten Teil einrichten (structuralChildrenContainer) ---
        if (!structuralChildren.isEmpty()) {
            // Der Toggle-Button steuert jetzt den rechten Container
            addToggleButton(nameAndToggleRow, structuralChildrenContainer, structuralChildren);

            // Add horizontal connector line between element and children
            HBox connectorContainer = new HBox();
            Line horizontalLine = new Line();
            horizontalLine.setStartX(0);
            horizontalLine.setStartY(0);
            horizontalLine.setEndX(15);
            horizontalLine.setEndY(0);
            horizontalLine.setStroke(Color.web("#4a90e2"));
            horizontalLine.setStrokeWidth(2);
            connectorContainer.getChildren().add(horizontalLine);
            connectorContainer.setAlignment(Pos.CENTER_LEFT);
            mainContainer.getChildren().addAll(elementInfoContainer, connectorContainer, structuralChildrenContainer);
        } else {
            mainContainer.getChildren().add(elementInfoContainer);
        }

        return mainContainer;
    }

    /**
     * Erstellt die Ansicht für ein Attribut.
     */
    private Node createAttributeNodeView(XsdNodeInfo node) {
        HBox attributeContainer = new HBox(5);
        attributeContainer.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = createHighlightedLabel(node.name(), node);
        // Determine appropriate style based on cardinality
        String labelStyle = determineNodeLabelStyle(node, true);
        nameLabel.setStyle(labelStyle);

        // Apply additional search highlighting if needed
        applySearchHighlighting(nameLabel, node);

        // Add attribute icon
        FontIcon attributeIcon = createColoredIcon("bi-at", "#ffc107");
        attributeIcon.setIconColor(javafx.scene.paint.Color.web("#d4a147"));
        attributeIcon.setIconSize(12);
        nameLabel.setGraphic(attributeIcon);

        nameLabel.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                showContextMenu(nameLabel, node);
            } else {
                updateDetailPane(node);
            }
        });

        // Enable drag and drop for attributes
        dragDropManager.makeDraggable(nameLabel, node);

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            attributeContainer.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            attributeContainer.getChildren().add(nameLabel);
        }
        return attributeContainer;
    }

    /**
     * Erstellt die Ansicht für ein Strukturelement (<xs:sequence> oder <xs:choice>).
     */
    private Node createStructuralNodeView(XsdNodeInfo node, String title, String style) {
        HBox mainContainer = new HBox(10);
        mainContainer.setAlignment(Pos.TOP_LEFT);

        // Linker Teil: Das "SEQUENCE" oder "CHOICE" Label - vertikal mittig
        VBox titleContainer = new VBox();
        titleContainer.setAlignment(Pos.CENTER_LEFT); // Vertikal mittig, horizontal linksbündig
        HBox titleRow = new HBox(5);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setStyle(style);

        Label titleLabel = new Label(title);

        // Add appropriate icon for structural elements
        FontIcon structuralIcon;
        if ("SEQUENCE".equals(title)) {
            structuralIcon = createColoredIcon("bi-list-ol", "#17a2b8");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        } else if ("CHOICE".equals(title)) {
            structuralIcon = createColoredIcon("bi-option", "#fd7e14");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#ff8c00"));
        } else {
            structuralIcon = createColoredIcon("bi-diagram-3", "#6f42c1");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        }
        structuralIcon.setIconSize(12);
        titleLabel.setGraphic(structuralIcon);

        // Enable drag and drop for structural elements
        titleLabel.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                showContextMenu(titleLabel, node);
            } else {
                updateDetailPane(node);
            }
        });
        dragDropManager.makeDraggable(titleLabel, node);
        dragDropManager.makeDropTarget(titleLabel, node);

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            titleRow.getChildren().addAll(titleLabel, cardinalityLabel);
        } else {
            titleRow.getChildren().add(titleLabel);
        }
        titleContainer.getChildren().add(titleRow);

        // Rechter Teil: Die eigentlichen Kinder der Sequenz/Choice mit Verbindungslinien
        VBox childrenVBox = new VBox(5);
        childrenVBox.setPadding(new Insets(0, 0, 5, 20));
        childrenVBox.setVisible(false);
        childrenVBox.setManaged(false);
        childrenVBox.setAlignment(Pos.TOP_LEFT);

        // Enhanced connector line style based on structural type
        if ("SEQUENCE".equals(title)) {
            childrenVBox.setStyle("-fx-border-color: #6c757d; -fx-border-width: 0 0 0 2px; -fx-border-style: solid;");
        } else if ("CHOICE".equals(title)) {
            childrenVBox.setStyle("-fx-border-color: #ff8c00; -fx-border-width: 0 0 0 2px; -fx-border-style: dashed;");
        } else {
            childrenVBox.setStyle("-fx-border-color: #adb5bd; -fx-border-width: 0 0 0 1px; -fx-border-style: dotted;");
        }

        if (!node.children().isEmpty()) {
            addToggleButton(titleRow, childrenVBox, node.children());

            // Add horizontal connector for structural nodes
            HBox connectorContainer = new HBox();
            Line horizontalLine = new Line();
            horizontalLine.setStartX(0);
            horizontalLine.setStartY(0);
            horizontalLine.setEndX(15);
            horizontalLine.setEndY(0);
            if ("SEQUENCE".equals(title)) {
                horizontalLine.setStroke(Color.web("#6c757d"));
            } else if ("CHOICE".equals(title)) {
                horizontalLine.setStroke(Color.web("#ff8c00"));
                horizontalLine.getStrokeDashArray().addAll(5.0, 5.0);
            } else {
                horizontalLine.setStroke(Color.web("#adb5bd"));
            }
            horizontalLine.setStrokeWidth(2);
            connectorContainer.getChildren().add(horizontalLine);
            connectorContainer.setAlignment(Pos.CENTER_LEFT);
            mainContainer.getChildren().addAll(titleContainer, connectorContainer, childrenVBox);
        } else {
            mainContainer.getChildren().add(titleContainer);
        }
        return mainContainer;
    }

    /**
     * Erstellt die Ansicht für ein <xs:any> Element.
     */
    private Node createAnyNodeView(XsdNodeInfo node) {
        HBox nodeContainer = new HBox(5);
        nodeContainer.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(ANY_NODE_STYLE);

        // Add any element icon
        FontIcon anyIcon = createColoredIcon("bi-asterisk", "#20c997");
        anyIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        anyIcon.setIconSize(12);
        nameLabel.setGraphic(anyIcon);

        nameLabel.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                showContextMenu(nameLabel, node);
            } else {
                updateDetailPane(node);
            }
        });

        // Enable drag and drop for any elements
        dragDropManager.makeDraggable(nameLabel, node);

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            nodeContainer.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            nodeContainer.getChildren().add(nameLabel);
        }
        return nodeContainer;
    }


    /**
     * Fügt einen Toggle-Button hinzu, der die Kind-Knoten erst dann erstellt (lazy loading),
     * wenn er zum ersten Mal geklickt wird. Scrollt den neu sichtbaren Bereich automatisch
     * in die Ansicht.
     *
     * @param parentRow         Die HBox, zu der der Button hinzugefügt wird.
     * @param childrenContainer Der VBox-Container, der die Kinder aufnehmen wird.
     * @param childrenToRender  Die Liste der Kind-Datenmodelle, die gerendert werden sollen.
     */
    private void addToggleButton(HBox parentRow, VBox childrenContainer, List<XsdNodeInfo> childrenToRender) {
        Label toggleButton = new Label("+");
        toggleButton.setStyle(TOGGLE_BUTTON_STYLE);

        final boolean[] isExpanded = {false};
        final boolean[] childrenLoaded = {false};

        toggleButton.setOnMouseClicked(event -> {
            isExpanded[0] = !isExpanded[0];

            if (isExpanded[0] && !childrenLoaded[0]) {
                for (XsdNodeInfo childNode : childrenToRender) {
                    childrenContainer.getChildren().add(createNodeView(childNode));
                }
                childrenLoaded[0] = true;
            }

            childrenContainer.setVisible(isExpanded[0]);
            childrenContainer.setManaged(isExpanded[0]);
            toggleButton.setText(isExpanded[0] ? "−" : "+");

            // Auto-expand sequence and choice nodes when expanding
            if (isExpanded[0]) {
                autoExpandSequenceAndChoiceNodes(childrenContainer);
            }

            // Scrollt den neu sichtbaren Bereich in die Ansicht
            if (isExpanded[0]) {
                // Platform.runLater stellt sicher, dass das Layout aktualisiert wurde,
                // bevor wir versuchen, die Positionen zu berechnen.
                Platform.runLater(() -> {
                    if (treeScrollPane == null || treeScrollPane.getContent() == null) {
                        return;
                    }
                    Node content = treeScrollPane.getContent();

                    // Berechne die Position des Kind-Containers relativ zum gesamten Inhalt
                    Bounds childBounds = childrenContainer.localToScene(childrenContainer.getBoundsInLocal());
                    Bounds contentBounds = content.localToScene(content.getBoundsInLocal());

                    if (childBounds == null || contentBounds == null) return;

                    double layoutX = childBounds.getMinX() - contentBounds.getMinX();

                    // Berechne die Dimensionen des sichtbaren Bereichs und des Gesamtinhalts
                    double contentWidth = content.getBoundsInLocal().getWidth();
                    double viewportWidth = treeScrollPane.getViewportBounds().getWidth();

                    // Scrolle horizontal, wenn nötig
                    if (contentWidth > viewportWidth) {
                        // Ziel ist es, den Anfang des Kind-Containers sichtbar zu machen.
                        // Wir berechnen den hvalue, der erforderlich ist, um den Container an den Anfang
                        // des sichtbaren Bereichs zu bringen.
                        double targetHValue = layoutX / (contentWidth - viewportWidth);

                        // Scrolle nur, wenn der neue Bereich rechts außerhalb der aktuellen Ansicht liegt.
                        // Ein Zurückscrollen nach links wird vermieden, um die Ansicht ruhig zu halten.
                        if (targetHValue > treeScrollPane.getHvalue()) {
                            treeScrollPane.setHvalue(Math.min(1.0, targetHValue));
                        }
                    }
                });
            }
        });
        parentRow.getChildren().add(toggleButton);
    }

    private String formatCardinality(String minOccurs, String maxOccurs) {
        String min = (minOccurs == null) ? "1" : minOccurs;
        String max = (maxOccurs == null) ? "1" : maxOccurs;
        if ("unbounded".equalsIgnoreCase(max)) {
            max = "∞";
        }

        // Special formatting for common cases
        if ("1".equals(min) && "1".equals(max)) {
            return ""; // Don't show [1..1] as it's the default
        } else if ("0".equals(min) && "1".equals(max)) {
            return "0..1";
        } else if ("1".equals(min) && "∞".equals(max)) {
            return "1..∞";
        } else if ("0".equals(min) && "∞".equals(max)) {
            return "0..∞";
        } else {
            return String.format("%s..%s", min, max);
        }
    }

    private void updateDetailPane(XsdNodeInfo node) {
        // Resolve the freshest node by XPath against the current root tree
        if (node != null && node.xpath() != null && this.rootNode != null) {
            XsdNodeInfo resolved = findNodeByXPath(this.rootNode, node.xpath());
            if (resolved != null) {
                node = resolved;
            }
        }

        this.selectedNode = node;

        // Update property panel with current data and force DOM sync
        if (propertyPanel != null) {
            propertyPanel.setSelectedNode(node);
            propertyPanel.refreshTypes();
            propertyPanel.forceUpdateFromDOM();
        }

        // Update validation panel
        if (validationPanel != null) {
            validationPanel.updateForNode(node);
        }
    }

    private void addDetailRow(GridPane grid, int rowIndex, String labelText, String valueText) {
        if (valueText == null) {
            valueText = "";
        }

        Label label = new Label(labelText);
        label.setStyle(DETAIL_LABEL_STYLE);
        GridPane.setValignment(label, VPos.TOP);

        Label value = new Label(valueText);
        value.setWrapText(true);
        value.setMaxWidth(300);

        grid.add(label, 0, rowIndex);
        grid.add(value, 1, rowIndex);
    }

    /**
     * Creates a type-specific icon based on XSD data type
     *
     * @param type The XSD type (e.g., "xs:string", "xs:date", etc.)
     * @return FontIcon with appropriate icon and color
     */
    private FontIcon createTypeSpecificIcon(String type) {
        String iconLiteral;
        String iconColor;

        if (type == null || type.isEmpty()) {
            // Default icon for unknown/empty types
            iconLiteral = "bi-box";
            iconColor = "#4a90e2";
        } else {
            // Remove namespace prefix if present (xs:string -> string)
            String cleanType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

            switch (cleanType.toLowerCase()) {
                // String types
                case "string", "normalizedstring", "token", "nmtoken", "name", "ncname", "id", "idref", "idrefs",
                     "entity", "entities" -> {
                    iconLiteral = "bi-chat-quote";
                    iconColor = "#28a745"; // Green
                }

                // Numeric types
                case "int", "integer", "long", "short", "byte", "positiveinteger", "negativeinteger",
                     "nonpositiveinteger", "nonnegativeinteger", "unsignedlong", "unsignedint",
                     "unsignedshort", "unsignedbyte" -> {
                    iconLiteral = "bi-plus-circle";
                    iconColor = "#007bff"; // Blue
                }

                // Decimal/Float types
                case "decimal", "float", "double" -> {
                    iconLiteral = "bi-calculator";
                    iconColor = "#007bff"; // Blue
                }

                // Date/Time types
                case "date", "datetime", "time", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday", "duration" -> {
                    iconLiteral = "bi-calendar-date";
                    iconColor = "#fd7e14"; // Orange
                }

                // Boolean type
                case "boolean" -> {
                    iconLiteral = "bi-check-square";
                    iconColor = "#6f42c1"; // Purple
                }

                // Binary types
                case "base64binary", "hexbinary" -> {
                    iconLiteral = "bi-file-binary";
                    iconColor = "#6c757d"; // Gray
                }

                // URI type
                case "anyuri" -> {
                    iconLiteral = "bi-link-45deg";
                    iconColor = "#17a2b8"; // Cyan
                }

                // QName type
                case "qname" -> {
                    iconLiteral = "bi-tag";
                    iconColor = "#e83e8c"; // Pink
                }

                // Language type
                case "language" -> {
                    iconLiteral = "bi-globe";
                    iconColor = "#20c997"; // Teal
                }

                // Complex or custom types
                default -> {
                    if (cleanType.endsWith("type") || cleanType.contains("complex")) {
                        iconLiteral = "bi-diagram-3";
                        iconColor = "#dc3545"; // Red
                    } else {
                        // Default icon for unrecognized types
                        iconLiteral = "bi-box";
                        iconColor = "#4a90e2"; // Default blue
                    }
                }
            }
        }

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(iconColor));
        icon.setIconSize(14);
        return icon;
    }

    /**
     * Determines the appropriate label style based on node cardinality (optional/required and single/repeatable)
     *
     * @param node        The XSD node
     * @param isAttribute Whether this is an attribute (true) or element (false)
     * @return The appropriate CSS style string
     */
    private String determineNodeLabelStyle(XsdNodeInfo node, boolean isAttribute) {
        boolean isOptional = "0".equals(node.minOccurs());
        boolean isRepeatable = isRepeatable(node.maxOccurs());

        if (isAttribute) {
            if (isOptional && isRepeatable) {
                return OPTIONAL_REPEATABLE_ATTRIBUTE_LABEL_STYLE;
            } else if (isOptional) {
                return OPTIONAL_ATTRIBUTE_LABEL_STYLE;
            } else if (isRepeatable) {
                return REPEATABLE_ATTRIBUTE_LABEL_STYLE;
            } else {
                return ATTRIBUTE_LABEL_STYLE;
            }
        } else {
            if (isOptional && isRepeatable) {
                return OPTIONAL_REPEATABLE_NODE_LABEL_STYLE;
            } else if (isOptional) {
                return OPTIONAL_NODE_LABEL_STYLE;
            } else if (isRepeatable) {
                return REPEATABLE_NODE_LABEL_STYLE;
            } else {
                return NODE_LABEL_STYLE;
            }
        }
    }

    /**
     * Checks if a maxOccurs value indicates the node can repeat (> 1 or "unbounded")
     *
     * @param maxOccurs The maxOccurs value from XSD
     * @return true if the node can occur multiple times
     */
    private boolean isRepeatable(String maxOccurs) {
        if (maxOccurs == null || maxOccurs.isEmpty()) {
            return false;
        }

        // "unbounded" means infinite occurrences
        if ("unbounded".equals(maxOccurs)) {
            return true;
        }

        // Try to parse as integer and check if > 1
        try {
            int max = Integer.parseInt(maxOccurs);
            return max > 1;
        } catch (NumberFormatException e) {
            // If we can't parse it, assume single occurrence
            return false;
        }
    }

    /**
     * Automatically expands sequence and choice nodes within the given container
     *
     * @param container The container to search for sequence and choice nodes
     */
    private void autoExpandSequenceAndChoiceNodes(VBox container) {
        // Use Platform.runLater to ensure the UI is updated after the initial expansion
        Platform.runLater(() -> {
            autoExpandSequenceAndChoiceNodesRecursive(container);
        });
    }

    /**
     * Recursively searches for and expands sequence and choice toggle buttons
     *
     * @param parent The parent node to search within
     */
    private void autoExpandSequenceAndChoiceNodesRecursive(javafx.scene.Node parent) {
        if (parent instanceof VBox vbox) {
            for (javafx.scene.Node child : vbox.getChildren()) {
                autoExpandSequenceAndChoiceNodesRecursive(child);
            }
        } else if (parent instanceof HBox hbox) {
            autoExpandSequenceAndChoiceHBox(hbox);
        } else if (parent instanceof TitledPane titledPane) {
            // Check if this is a sequence or choice node by examining the title
            String title = titledPane.getText();
            if (title != null && (title.contains("SEQUENCE") || title.contains("CHOICE"))) {
                // Expand this titled pane
                if (!titledPane.isExpanded()) {
                    titledPane.setExpanded(true);
                }
            }
            // Continue searching within the content
            javafx.scene.Node content = titledPane.getContent();
            if (content != null) {
                autoExpandSequenceAndChoiceNodesRecursive(content);
            }
        }
    }

    /**
     * Processes an HBox for auto-expansion of sequence/choice nodes
     *
     * @param hbox The HBox to process
     */
    private void autoExpandSequenceAndChoiceHBox(HBox hbox) {
        Label toggleButton = null;
        boolean isSequenceOrChoice = false;

        // First pass: identify toggle buttons and sequence/choice indicators
        for (javafx.scene.Node child : hbox.getChildren()) {
            if (child instanceof Label label) {
                String text = label.getText();
                String style = label.getStyle();

                // Check if this is a toggle button by style and text
                if (("+".equals(text) || "−".equals(text)) &&
                        style != null && style.contains("-fx-background-color: #ffffff")) {
                    toggleButton = label;
                }
                // Check if this HBox represents a sequence or choice
                else if (text != null && (text.contains("SEQUENCE") || text.contains("CHOICE"))) {
                    isSequenceOrChoice = true;
                }
            }
        }

        // If we found a sequence/choice toggle button that's not expanded, expand it
        if (toggleButton != null && isSequenceOrChoice && "+".equals(toggleButton.getText())) {
            // Get the click handler from the toggle button and execute it
            var handler = toggleButton.getOnMouseClicked();
            if (handler != null) {
                handler.handle(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, false, false, false, false, false, false, null
                ));
            }
        }

        // Continue recursive search in children
        for (javafx.scene.Node child : hbox.getChildren()) {
            autoExpandSequenceAndChoiceNodesRecursive(child);
        }
    }

    /**
     * Shows context menu for XSD nodes with editing options
     */
    private void showContextMenu(Node targetNode, XsdNodeInfo nodeInfo) {
        ContextMenu contextMenu = new ContextMenu();

        // Schema-level menu items - only show for root elements that represent the schema
        boolean isSchemaRoot = nodeInfo.xpath().equals("/" + nodeInfo.name()) && nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT;
        if (isSchemaRoot) {
            MenuItem addSimpleTypeItem = new MenuItem("Add SimpleType");
            addSimpleTypeItem.setGraphic(createColoredIcon("bi-type", "#20c997"));
            addSimpleTypeItem.setOnAction(e -> showAddSimpleTypeDialog(createSchemaNodeInfo()));
            contextMenu.getItems().add(addSimpleTypeItem);

            MenuItem addComplexTypeItem = new MenuItem("Add ComplexType");
            addComplexTypeItem.setGraphic(createColoredIcon("bi-diagram-3", "#6f42c1"));
            addComplexTypeItem.setOnAction(e -> showAddComplexTypeDialog(createSchemaNodeInfo()));
            contextMenu.getItems().add(addComplexTypeItem);

            MenuItem namespaceManagerItem = new MenuItem("Manage Namespaces");
            namespaceManagerItem.setGraphic(createColoredIcon("bi-diagram-2", "#17a2b8"));
            namespaceManagerItem.setOnAction(e -> showNamespaceManagerDialog());
            contextMenu.getItems().add(namespaceManagerItem);

            MenuItem importIncludeManagerItem = new MenuItem("Manage Imports & Includes");
            importIncludeManagerItem.setGraphic(createColoredIcon("bi-files", "#007bff"));
            importIncludeManagerItem.setOnAction(e -> showImportIncludeManagerDialog());
            contextMenu.getItems().add(importIncludeManagerItem);

            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // Type-based editing options - check if element uses a custom SimpleType or ComplexType
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT && nodeInfo.type() != null) {
            String type = nodeInfo.type();
            // Check if it's a custom type (not built-in XSD type)
            if (!type.startsWith("xs:") && !type.startsWith("xsd:")) {
                // Try to determine if it's a SimpleType or ComplexType by checking the XSD
                XsdNodeInfo typeNodeInfo = findTypeNodeInfo(nodeInfo, type);
                if (typeNodeInfo != null) {
                    if (typeNodeInfo.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE) {
                        MenuItem editSimpleTypeItem = new MenuItem("Edit SimpleType '" + type + "'");
                        editSimpleTypeItem.setGraphic(createColoredIcon("bi-pencil", "#fd7e14"));
                        editSimpleTypeItem.setOnAction(e -> showEditSimpleTypeDialog(typeNodeInfo));
                        contextMenu.getItems().add(editSimpleTypeItem);
                    } else if (typeNodeInfo.nodeType() == XsdNodeInfo.NodeType.COMPLEX_TYPE) {
                        MenuItem editComplexTypeItem = new MenuItem("Edit ComplexType '" + type + "'");
                        editComplexTypeItem.setGraphic(createColoredIcon("bi-pencil", "#fd7e14"));
                        editComplexTypeItem.setOnAction(e -> showEditComplexTypeDialog(typeNodeInfo));
                        contextMenu.getItems().add(editComplexTypeItem);
                    }
                    contextMenu.getItems().add(new SeparatorMenuItem());
                }
            }
        }


        // Add Element menu item
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.SEQUENCE ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.CHOICE) {

            // Check if this element can have children (not a simple type)
            boolean canHaveChildren = canNodeHaveChildren(nodeInfo);

            MenuItem addElementItem = new MenuItem("Add Element");
            addElementItem.setGraphic(createColoredIcon("bi-plus-circle", "#28a745"));
            addElementItem.setOnAction(e -> showAddElementDialog(nodeInfo));
            addElementItem.setDisable(!canHaveChildren);
            contextMenu.getItems().add(addElementItem);

            MenuItem addAttributeItem = new MenuItem("Add Attribute");
            addAttributeItem.setGraphic(createColoredIcon("bi-at", "#ffc107"));
            addAttributeItem.setOnAction(e -> showAddAttributeDialog(nodeInfo));
            addAttributeItem.setDisable(!canHaveChildren);
            contextMenu.getItems().add(addAttributeItem);

            if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
                MenuItem addSequenceItem = new MenuItem("Add Sequence");
                addSequenceItem.setGraphic(createColoredIcon("bi-list-ol", "#17a2b8"));
                addSequenceItem.setOnAction(e -> addSequence(nodeInfo));
                addSequenceItem.setDisable(!canHaveChildren);
                contextMenu.getItems().add(addSequenceItem);

                MenuItem addChoiceItem = new MenuItem("Add Choice");
                addChoiceItem.setGraphic(createColoredIcon("bi-option", "#fd7e14"));
                addChoiceItem.setOnAction(e -> addChoice(nodeInfo));
                addChoiceItem.setDisable(!canHaveChildren);
                contextMenu.getItems().add(addChoiceItem);
            }

            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // Rename menu item
        MenuItem renameItem = new MenuItem("Rename");
        renameItem.setGraphic(createColoredIcon("bi-pencil", "#fd7e14"));
        renameItem.setOnAction(e -> showRenameDialog(nodeInfo));
        contextMenu.getItems().add(renameItem);

        // Type-specific editing options for simple and complex types
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.COMPLEX_TYPE) {
            MenuItem editTypeInGraphicEditor = new MenuItem("Edit Type in Graphic Editor");
            editTypeInGraphicEditor.setGraphic(createColoredIcon("bi-diagram-3", "#6f42c1"));
            editTypeInGraphicEditor.setOnAction(e -> openTypeInGraphicEditor(nodeInfo));
            contextMenu.getItems().add(editTypeInGraphicEditor);
            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // Safe Rename menu item (for elements and types)
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.COMPLEX_TYPE) {
            MenuItem safeRenameItem = new MenuItem("Safe Rename with Preview");
            safeRenameItem.setGraphic(createColoredIcon("bi-stars", "#ffc107"));
            safeRenameItem.setOnAction(e -> showSafeRenameDialog(nodeInfo));
            contextMenu.getItems().add(safeRenameItem);
        }

        // Delete menu item
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(createColoredIcon("bi-trash", "#dc3545"));
        deleteItem.setOnAction(e -> deleteNode(nodeInfo));
        contextMenu.getItems().add(deleteItem);

        contextMenu.getItems().add(new SeparatorMenuItem());

        // Validation Rules menu item
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE) {
            MenuItem validationRulesItem = new MenuItem("Validation Rules");
            validationRulesItem.setGraphic(createColoredIcon("bi-shield-check", "#28a745"));
            validationRulesItem.setOnAction(e -> showValidationRulesDialog(nodeInfo));
            contextMenu.getItems().add(validationRulesItem);

            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // Properties menu item
        MenuItem propertiesItem = new MenuItem("Properties");
        propertiesItem.setGraphic(createColoredIcon("bi-gear", "#6c757d"));
        propertiesItem.setOnAction(e -> showPropertiesDialog(nodeInfo));
        contextMenu.getItems().add(propertiesItem);

        // Move up/down menu items (for elements that can be reordered)
        if (canMoveNode(nodeInfo)) {
            contextMenu.getItems().add(new SeparatorMenuItem());

            MenuItem moveUpItem = new MenuItem("Move Up");
            moveUpItem.setGraphic(createColoredIcon("bi-arrow-up", "#6f42c1"));
            moveUpItem.setOnAction(e -> moveNodeUp(nodeInfo));
            moveUpItem.setDisable(!MoveNodeUpCommand.canMoveUp(domManipulator, nodeInfo));
            contextMenu.getItems().add(moveUpItem);

            MenuItem moveDownItem = new MenuItem("Move Down");
            moveDownItem.setGraphic(createColoredIcon("bi-arrow-down", "#6f42c1"));
            moveDownItem.setOnAction(e -> moveNodeDown(nodeInfo));
            moveDownItem.setDisable(!MoveNodeDownCommand.canMoveDown(domManipulator, nodeInfo));
            contextMenu.getItems().add(moveDownItem);
        }

        // Refactoring Tools
        if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE) {
            contextMenu.getItems().add(new SeparatorMenuItem());

            // Convert Element to Attribute (only for elements)
            if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
                MenuItem convertToAttributeItem = new MenuItem("Convert to Attribute");
                convertToAttributeItem.setGraphic(createColoredIcon("bi-arrow-right", "#007bff"));
                convertToAttributeItem.setOnAction(e -> convertElementToAttribute(nodeInfo));
                convertToAttributeItem.setDisable(!canConvertElementToAttribute(nodeInfo));
                contextMenu.getItems().add(convertToAttributeItem);
            }

            // Convert Attribute to Element (only for attributes)
            if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE) {
                MenuItem convertToElementItem = new MenuItem("Convert to Element");
                convertToElementItem.setGraphic(createColoredIcon("bi-arrow-left", "#007bff"));
                convertToElementItem.setOnAction(e -> convertAttributeToElement(nodeInfo));
                contextMenu.getItems().add(convertToElementItem);
            }

            // Extract ComplexType (only for elements with inline complexTypes)
            if (nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
                MenuItem extractComplexTypeItem = new MenuItem("Extract ComplexType");
                extractComplexTypeItem.setGraphic(createColoredIcon("bi-box-arrow-up", "#17a2b8"));
                extractComplexTypeItem.setOnAction(e -> extractComplexType(nodeInfo));
                extractComplexTypeItem.setDisable(!canExtractComplexType(nodeInfo));
                contextMenu.getItems().add(extractComplexTypeItem);

                // Inline Type Definition (only for elements with global type references)
                MenuItem inlineTypeItem = new MenuItem("Inline Type Definition");
                inlineTypeItem.setGraphic(createColoredIcon("bi-box-arrow-in-down", "#20c997"));
                inlineTypeItem.setOnAction(e -> inlineTypeDefinition(nodeInfo));
                inlineTypeItem.setDisable(!canInlineTypeDefinition(nodeInfo));
                contextMenu.getItems().add(inlineTypeItem);
            }
        }

        // Copy/Paste menu items
        contextMenu.getItems().add(new SeparatorMenuItem());

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setGraphic(createColoredIcon("bi-files", "#007bff"));
        copyItem.setOnAction(e -> copyNode(nodeInfo));
        contextMenu.getItems().add(copyItem);

        // Paste menu item with dynamic status
        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setGraphic(createColoredIcon("bi-clipboard", "#28a745"));
        pasteItem.setOnAction(e -> pasteNode(nodeInfo));

        // Enable/disable based on clipboard content and show what's in clipboard
        boolean hasContent = XsdClipboardService.hasClipboardContent();
        pasteItem.setDisable(!hasContent);

        if (hasContent) {
            String clipboardDesc = XsdClipboardService.getClipboardDescription();
            pasteItem.setText("Paste (" + clipboardDesc + ")");
        } else {
            pasteItem.setText("Paste");
        }

        contextMenu.getItems().add(pasteItem);

        // Apply uniform font styling to context menu
        contextMenu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        contextMenu.show(targetNode, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * Determines if a node can be moved up or down in the order
     */
    private boolean canMoveNode(XsdNodeInfo nodeInfo) {
        // Only elements, sequences, choices, and attributes within their parent can be reordered
        return nodeInfo.nodeType() == XsdNodeInfo.NodeType.ELEMENT ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.SEQUENCE ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.CHOICE ||
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE;
    }

    /**
     * Moves a node up in the order within its parent
     */
    private void moveNodeUp(XsdNodeInfo nodeInfo) {
        try {
            MoveNodeUpCommand command = new MoveNodeUpCommand(domManipulator, nodeInfo);
            if (command.execute()) {
                // Add to command history for undo
                undoManager.executeCommand(command);

                // Refresh the view
                refreshView();

                logger.info("Node '{}' moved up successfully", nodeInfo.name());
            } else {
                logger.warn("Failed to move node '{}' up", nodeInfo.name());
            }
        } catch (Exception e) {
            logger.error("Error moving node up: " + nodeInfo.name(), e);
            showErrorAlert("Error", "Failed to move node up: " + e.getMessage());
        }
    }

    /**
     * Moves a node down in the order within its parent
     */
    private void moveNodeDown(XsdNodeInfo nodeInfo) {
        try {
            MoveNodeDownCommand command = new MoveNodeDownCommand(domManipulator, nodeInfo);
            if (command.execute()) {
                // Add to command history for undo
                undoManager.executeCommand(command);

                // Refresh the view
                refreshView();

                logger.info("Node '{}' moved down successfully", nodeInfo.name());
            } else {
                logger.warn("Failed to move node '{}' down", nodeInfo.name());
            }
        } catch (Exception e) {
            logger.error("Error moving node down: " + nodeInfo.name(), e);
            showErrorAlert("Error", "Failed to move node down: " + e.getMessage());
        }
    }

    private XsdNodeInfo copiedNode = null;

    private void showAddElementDialog(XsdNodeInfo parentNode) {
        // Create advanced element creation dialog
        Dialog<ElementCreationResult> dialog = createAdvancedElementDialog(parentNode);

        Optional<ElementCreationResult> result = dialog.showAndWait();
        result.ifPresent(elementInfo -> {
            String name = elementInfo.name();
            String type = elementInfo.type();
            String minOccurs = elementInfo.minOccurs();
            String maxOccurs = elementInfo.maxOccurs();
            // Create and execute command
            AddElementCommand command = new AddElementCommand(
                    domManipulator, parentNode, name,
                    type != null && !type.trim().isEmpty() ? type : null,
                    minOccurs != null && !minOccurs.trim().isEmpty() ? minOccurs : "1",
                    maxOccurs != null && !maxOccurs.trim().isEmpty() ? maxOccurs : "1"
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            } else {
                // Show error dialog if creation failed
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cannot Add Element");
                alert.setHeaderText("Cannot add element to " + parentNode.name());
                alert.setContentText("This element has a simple type (like xs:string) and cannot contain child elements.\n\n" +
                        "To add child elements, you need to change the element's type to a complex type first.");
                alert.showAndWait();
            }
        });
    }

    private void showAddAttributeDialog(XsdNodeInfo parentNode) {
        // Create advanced attribute creation dialog
        Dialog<AttributeCreationResult> dialog = createAdvancedAttributeDialog(parentNode);

        Optional<AttributeCreationResult> result = dialog.showAndWait();
        result.ifPresent(attributeInfo -> {
            String name = attributeInfo.name();
            String type = attributeInfo.type();
            String use = attributeInfo.use();
            // Create and execute command
            AddAttributeCommand command = new AddAttributeCommand(
                    domManipulator, parentNode, name,
                    type != null && !type.trim().isEmpty() ? type : null,
                    use != null && !use.trim().isEmpty() ? use : "optional"
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            } else {
                // Show error dialog if creation failed
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cannot Add Attribute");
                alert.setHeaderText("Cannot add attribute to " + parentNode.name());
                alert.setContentText("This element has a simple type (like xs:string) and cannot contain attributes.\n\n" +
                        "To add attributes, you need to change the element's type to a complex type first.");
                alert.showAndWait();
            }
        });
    }

    private void addSequence(XsdNodeInfo parentNode) {
        // Show advanced sequence creation dialog
        Dialog<SequenceCreationResult> dialog = createAdvancedSequenceDialog(parentNode);

        Optional<SequenceCreationResult> result = dialog.showAndWait();
        result.ifPresent(sequenceInfo -> {
            // Create and execute command
            AddSequenceCommand command = new AddSequenceCommand(
                    domManipulator, parentNode, sequenceInfo.minOccurs(), sequenceInfo.maxOccurs()
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            } else {
                // Show error dialog if creation failed
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cannot Add Sequence");
                alert.setHeaderText("Cannot add sequence to " + parentNode.name());
                alert.setContentText("This element has a simple type (like xs:string) and cannot contain child elements.\n\n" +
                        "To add sequences, you need to change the element's type to a complex type first.");
                alert.showAndWait();
            }
        });
    }

    private void addChoice(XsdNodeInfo parentNode) {
        // Show advanced choice creation dialog
        Dialog<ChoiceCreationResult> dialog = createAdvancedChoiceDialog(parentNode);

        Optional<ChoiceCreationResult> result = dialog.showAndWait();
        result.ifPresent(choiceInfo -> {
            // Create and execute command
            AddChoiceCommand command = new AddChoiceCommand(
                    domManipulator, parentNode, choiceInfo.minOccurs(), choiceInfo.maxOccurs()
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            } else {
                // Show error dialog if creation failed
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cannot Add Choice");
                alert.setHeaderText("Cannot add choice to " + parentNode.name());
                alert.setContentText("This element has a simple type (like xs:string) and cannot contain child elements.\n\n" +
                        "To add choices, you need to change the element's type to a complex type first.");
                alert.showAndWait();
            }
        });
    }

    private void showRenameDialog(XsdNodeInfo node) {
        TextInputDialog dialog = new TextInputDialog(node.name());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename " + node.name());
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            // Create and execute command
            RenameNodeCommand command = new RenameNodeCommand(
                    domManipulator, node, newName
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            }
        });
    }

    private void deleteNode(XsdNodeInfo node) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Confirmation");
        alert.setHeaderText("Delete " + node.name());
        alert.setContentText("Are you sure you want to delete this node and all its children?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Create and execute command
            DeleteNodeCommand command = new DeleteNodeCommand(
                    domManipulator, node
            );

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
            }
        }
    }

    private void showPropertiesDialog(XsdNodeInfo node) {
        // Switch to Properties tab and select the node
        updateDetailPane(node);

        // Find the properties tab and select it
        if (propertyPanel != null && propertyPanel.getParent() != null) {
            Node parent = propertyPanel.getParent();
            while (parent != null && !(parent instanceof TabPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof TabPane tabPane) {
                // Select the Properties tab (should be the second tab)
                if (tabPane.getTabs().size() > 1) {
                    tabPane.getSelectionModel().select(1);
                }
            }
        }
    }

    /**
     * Show dialog to add a new SimpleType
     */
    private void showAddSimpleTypeDialog(XsdNodeInfo parentNode) {
        XsdSimpleTypeEditor editor = new XsdSimpleTypeEditor();
        editor.setTitle("Add SimpleType");
        editor.setHeaderText("Create a new XSD SimpleType definition");

        Optional<SimpleTypeResult> result = editor.showAndWait();
        if (result.isPresent()) {
            AddSimpleTypeCommand command = new AddSimpleTypeCommand(domManipulator, parentNode, result.get());

            if (undoManager.executeCommand(command)) {
                // Refresh the diagram view
                Platform.runLater(() -> {
                    try {
                        // Serialize document to string and update controller
                        String updatedXsd = serializeDocumentToString();
                        controller.updateXsdContent(updatedXsd);
                        logger.info("SimpleType '{}' added successfully", result.get().name());
                    } catch (Exception e) {
                        logger.error("Error refreshing view after adding SimpleType", e);
                    }
                });
            } else {
                logger.error("Failed to add SimpleType: {}", result.get().name());
                // Show error dialog
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to add SimpleType");
                alert.setContentText("Could not add the SimpleType '" + result.get().name() + "'. Check the log for details.");
                alert.showAndWait();
            }
        }
    }

    /**
     * Show dialog to edit an existing SimpleType
     */
    private void showEditSimpleTypeDialog(XsdNodeInfo simpleTypeNode) {
        // Extract current SimpleType information for editing
        Element simpleTypeElement = (Element) domManipulator.findNodeByPath(simpleTypeNode.xpath());
        if (simpleTypeElement == null) {
            logger.error("SimpleType element not found: {}", simpleTypeNode.xpath());
            return;
        }

        // Create editor with current values
        XsdSimpleTypeEditor editor = new XsdSimpleTypeEditor();
        editor.setTitle("Edit SimpleType");
        editor.setHeaderText("Edit XSD SimpleType definition: " + simpleTypeNode.name());

        // TODO: Populate editor with current values from simpleTypeElement
        // This requires parsing the existing restriction and facets

        Optional<SimpleTypeResult> result = editor.showAndWait();
        if (result.isPresent()) {
            EditSimpleTypeCommand command = new EditSimpleTypeCommand(domManipulator, simpleTypeNode, result.get());

            if (undoManager.executeCommand(command)) {
                // Refresh the diagram view
                Platform.runLater(() -> {
                    try {
                        // Serialize document to string and update controller
                        String updatedXsd = serializeDocumentToString();
                        controller.updateXsdContent(updatedXsd);
                        logger.info("SimpleType '{}' edited successfully", result.get().name());
                    } catch (Exception e) {
                        logger.error("Error refreshing view after editing SimpleType", e);
                    }
                });
            } else {
                logger.error("Failed to edit SimpleType: {}", result.get().name());
                // Show error dialog
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to edit SimpleType");
                alert.setContentText("Could not edit the SimpleType '" + result.get().name() + "'. Check the log for details.");
                alert.showAndWait();
            }
        }
    }

    /**
     * Show dialog to add a new ComplexType
     */
    private void showAddComplexTypeDialog(XsdNodeInfo parentNode) {
        XsdComplexTypeEditor editor = new XsdComplexTypeEditor(domManipulator.getDocument());
        editor.setTitle("Add ComplexType");
        editor.setHeaderText("Create a new XSD ComplexType definition");

        Optional<ComplexTypeResult> result = editor.showAndWait();
        if (result.isPresent()) {
            AddComplexTypeCommand command = new AddComplexTypeCommand(domManipulator, parentNode, result.get());

            if (undoManager.executeCommand(command)) {
                // Refresh the diagram view
                Platform.runLater(() -> {
                    try {
                        // Serialize document to string and update controller
                        String updatedXsd = serializeDocumentToString();
                        controller.updateXsdContent(updatedXsd);
                        logger.info("ComplexType '{}' added successfully", result.get().name());
                    } catch (Exception e) {
                        logger.error("Error refreshing view after adding ComplexType", e);
                    }
                });
            } else {
                logger.error("Failed to add ComplexType: {}", result.get().name());
                // Show error dialog
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to add ComplexType");
                alert.setContentText("Could not add the ComplexType '" + result.get().name() + "'. Check the log for details.");
                alert.showAndWait();
            }
        }
    }

    /**
     * Show dialog to edit an existing ComplexType
     */
    private void showEditComplexTypeDialog(XsdNodeInfo complexTypeNode) {
        // Extract current ComplexType information for editing
        Element complexTypeElement = (Element) domManipulator.findNodeByPath(complexTypeNode.xpath());
        if (complexTypeElement == null) {
            logger.error("ComplexType element not found: {}", complexTypeNode.xpath());
            return;
        }

        // Create editor with current values
        XsdComplexTypeEditor editor = new XsdComplexTypeEditor(domManipulator.getDocument(), complexTypeElement);
        editor.setTitle("Edit ComplexType");
        editor.setHeaderText("Edit XSD ComplexType definition: " + complexTypeNode.name());

        Optional<ComplexTypeResult> result = editor.showAndWait();
        if (result.isPresent()) {
            EditComplexTypeCommand command = new EditComplexTypeCommand(domManipulator, complexTypeNode, result.get());

            if (undoManager.executeCommand(command)) {
                // Refresh the diagram view
                Platform.runLater(() -> {
                    try {
                        // Serialize document to string and update controller
                        String updatedXsd = serializeDocumentToString();
                        controller.updateXsdContent(updatedXsd);
                        logger.info("ComplexType '{}' edited successfully", result.get().name());
                    } catch (Exception e) {
                        logger.error("Error refreshing view after editing ComplexType", e);
                    }
                });
            } else {
                logger.error("Failed to edit ComplexType: {}", result.get().name());
                // Show error dialog
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to edit ComplexType");
                alert.setContentText("Could not edit the ComplexType '" + result.get().name() + "'. Check the log for details.");
                alert.showAndWait();
            }
        }
    }

    private void copyNode(XsdNodeInfo node) {
        // Use command pattern for consistency, though copy doesn't modify document
        CopyNodeCommand command = new CopyNodeCommand(domManipulator, node);

        if (undoManager.executeCommand(command)) {
            // Also keep legacy reference for backward compatibility
            copiedNode = node;
            logger.info("Node copied to clipboard: {}", node.name());
        }
    }

    private void pasteNode(XsdNodeInfo targetNode) {
        // Check if there's content to paste
        if (!XsdClipboardService.hasClipboardContent()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Paste");
            alert.setHeaderText("Nothing to paste");
            alert.setContentText("The clipboard is empty. Copy a node first to paste it here.");
            alert.showAndWait();
            return;
        }

        // Get clipboard data
        XsdClipboardService.XsdClipboardData clipboardData = XsdClipboardService.getClipboardData();
        if (clipboardData == null) {
            return;
        }

        // Show paste options dialog if there might be conflicts
        boolean resolveConflicts = true;
        if (hasNameConflict(targetNode, clipboardData.getNodeInfo())) {
            resolveConflicts = showPasteOptionsDialog(clipboardData.getNodeInfo(), targetNode);
            if (!resolveConflicts && !confirmOverwrite(clipboardData.getNodeInfo(), targetNode)) {
                return; // User cancelled
            }
        }

        // Create and execute paste command
        PasteNodeCommand command = new PasteNodeCommand(domManipulator, targetNode, clipboardData, resolveConflicts);

        if (undoManager.executeCommand(command)) {
            refreshView();
            triggerLiveValidation();
            logger.info("Node pasted successfully: {} -> {}",
                    clipboardData.getNodeInfo().name(), targetNode.name());
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Paste Failed");
            alert.setHeaderText("Could not paste " + clipboardData.getNodeInfo().name());
            alert.setContentText("The paste operation failed. This might be due to schema constraints or invalid target location.");
            alert.showAndWait();
        }
    }

    /**
     * Check if pasting the source node into target would create a name conflict
     */
    private boolean hasNameConflict(XsdNodeInfo targetParent, XsdNodeInfo sourceNode) {
        // Check if any child of the target parent has the same name as the source node
        return targetParent.children().stream()
                .anyMatch(child -> sourceNode.name().equals(child.name()));
    }

    /**
     * Show paste options dialog for conflict resolution
     */
    private boolean showPasteOptionsDialog(XsdNodeInfo sourceNode, XsdNodeInfo targetParent) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Paste Options");
        alert.setHeaderText("Name conflict detected");
        alert.setContentText(String.format(
                "An element named '%s' already exists in '%s'.\n\n" +
                        "Choose how to handle this conflict:",
                sourceNode.name(), targetParent.name()
        ));

        ButtonType renameButton = new ButtonType("Rename automatically");
        ButtonType overwriteButton = new ButtonType("Overwrite existing");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(renameButton, overwriteButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == renameButton) {
                return true; // Resolve conflicts by renaming
            } else if (result.get() == overwriteButton) {
                return false; // Don't resolve conflicts (overwrite)
            }
        }

        return false; // Default or cancelled
    }

    /**
     * Convert an element to an attribute
     */
    private void convertElementToAttribute(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element from the node info
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                showErrorAlert("Convert to Attribute", "Could not find element in DOM");
                return;
            }

            // Create and execute the conversion command
            ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                    domManipulator.getDocument(), element, domManipulator);

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
                logger.info("Element '{}' converted to attribute successfully", nodeInfo.name());
            } else {
                showErrorAlert("Convert to Attribute", "Failed to convert element to attribute");
            }

        } catch (Exception e) {
            logger.error("Error converting element to attribute", e);
            showErrorAlert("Convert to Attribute", "Error: " + e.getMessage());
        }
    }

    /**
     * Check if an element can be converted to an attribute
     */
    private boolean canConvertElementToAttribute(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                return false;
            }

            // Use the same logic as in ConvertElementToAttributeCommand
            // Element must not have child elements (complex content)
            var childElements = element.getElementsByTagName("*");

            // Check for inline complexType or simpleType that would indicate complex content
            for (int i = 0; i < childElements.getLength(); i++) {
                Element child = (Element) childElements.item(i);
                String localName = child.getLocalName();

                // If it has complexType with content model, it cannot be an attribute
                if ("complexType".equals(localName) || "sequence".equals(localName) ||
                        "choice".equals(localName) || "all".equals(localName) ||
                        "element".equals(localName)) {
                    return false;
                }
            }

            // Element should not have maxOccurs > 1 (attributes can't repeat)
            String maxOccurs = element.getAttribute("maxOccurs");
            if (maxOccurs != null && !maxOccurs.isEmpty() &&
                    !("1".equals(maxOccurs) || "unbounded".equals(maxOccurs))) {
                try {
                    int max = Integer.parseInt(maxOccurs);
                    if (max > 1) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("Error checking if element can be converted to attribute", e);
            return false;
        }
    }

    /**
     * Convert an attribute to an element
     */
    private void convertAttributeToElement(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM attribute from the node info
            Element attribute = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (attribute == null) {
                showErrorAlert("Convert to Element", "Could not find attribute in DOM");
                return;
            }

            // Create and execute the conversion command
            ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                    domManipulator.getDocument(), attribute, domManipulator);

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
                logger.info("Attribute '{}' converted to element successfully", nodeInfo.name());
            } else {
                showErrorAlert("Convert to Element", "Failed to convert attribute to element");
            }

        } catch (Exception e) {
            logger.error("Error converting attribute to element", e);
            showErrorAlert("Convert to Element", "Error: " + e.getMessage());
        }
    }

    /**
     * Extract inline complexType to global complexType
     */
    private void extractComplexType(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element from the node info
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                showErrorAlert("Extract ComplexType", "Could not find element in DOM");
                return;
            }

            // Show dialog to get new type name
            ExtractComplexTypeDialog dialog = new ExtractComplexTypeDialog(nodeInfo.name());
            var result = dialog.showAndWait();

            if (result.isPresent()) {
                ExtractComplexTypeDialog.ExtractComplexTypeResult extractResult = result.get();

                // Create and execute the extraction command
                ExtractComplexTypeCommand command = new ExtractComplexTypeCommand(
                        domManipulator.getDocument(), element, extractResult.typeName(), domManipulator);

                if (undoManager.executeCommand(command)) {
                    refreshView();
                    triggerLiveValidation();
                    logger.info("ComplexType extracted from element '{}' to global type '{}'",
                            nodeInfo.name(), extractResult.typeName());
                } else {
                    showErrorAlert("Extract ComplexType", "Failed to extract complexType");
                }
            }

        } catch (Exception e) {
            logger.error("Error extracting complexType", e);
            showErrorAlert("Extract ComplexType", "Error: " + e.getMessage());
        }
    }

    /**
     * Check if an element can have its complexType extracted
     */
    private boolean canExtractComplexType(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                return false;
            }

            return ExtractComplexTypeCommand.canExtractComplexType(element);
        } catch (Exception e) {
            logger.error("Error checking if complexType can be extracted", e);
            return false;
        }
    }

    /**
     * Inline a global type definition into the element
     */
    private void inlineTypeDefinition(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element from the node info
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                showErrorAlert("Inline Type Definition", "Could not find element in DOM");
                return;
            }

            // Get the type name from the element
            String typeName = element.getAttribute("type");
            if (typeName == null || typeName.isEmpty()) {
                showErrorAlert("Inline Type Definition", "Element does not have a type reference");
                return;
            }

            // Show confirmation dialog
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Inline Type Definition");
            confirmAlert.setHeaderText("Inline global type '" + typeName + "'");
            confirmAlert.setContentText("This will convert the global type reference to an inline type definition. " +
                    "The global type will remain available for other elements.\n\n" +
                    "Do you want to continue?");

            var confirmResult = confirmAlert.showAndWait();
            if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.OK) {
                return;
            }

            // Create and execute the inline command
            InlineTypeDefinitionCommand command = new InlineTypeDefinitionCommand(
                    domManipulator.getDocument(), element, typeName, domManipulator);

            if (undoManager.executeCommand(command)) {
                refreshView();
                triggerLiveValidation();
                logger.info("Type '{}' inlined into element '{}' successfully",
                        typeName, nodeInfo.name());
            } else {
                showErrorAlert("Inline Type Definition", "Failed to inline type definition");
            }

        } catch (Exception e) {
            logger.error("Error inlining type definition", e);
            showErrorAlert("Inline Type Definition", "Error: " + e.getMessage());
        }
    }

    /**
     * Check if an element can have its type definition inlined
     */
    private boolean canInlineTypeDefinition(XsdNodeInfo nodeInfo) {
        try {
            // Get the DOM element
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) {
                return false;
            }

            return InlineTypeDefinitionCommand.canInlineType(element, domManipulator);
        } catch (Exception e) {
            logger.error("Error checking if type can be inlined", e);
            return false;
        }
    }

    /**
     * Confirm overwrite operation
     */
    private boolean confirmOverwrite(XsdNodeInfo sourceNode, XsdNodeInfo targetParent) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Confirm Overwrite");
        alert.setHeaderText("Overwrite existing element?");
        alert.setContentText(String.format(
                "This will replace the existing '%s' element in '%s' with the copied one.\n\n" +
                        "This action cannot be easily undone. Continue?",
                sourceNode.name(), targetParent.name()
        ));

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public void refreshView() {
        refreshView(false);
    }

    public void refreshView(boolean forceFullRebuild) {
        System.out.println("DEBUG: refreshView called with forceFullRebuild=" + forceFullRebuild);
        if (domManipulator != null && controller != null) {
            if (forceFullRebuild) {
                System.out.println("DEBUG: Performing FULL rebuild");
                String updatedXsd = domManipulator.getXsdAsString();
                if (updatedXsd != null) {
                    controller.updateXsdContent(updatedXsd);
                }
            } else {
                System.out.println("DEBUG: Performing SELECTIVE refresh");
                refreshViewSelectively();
            }
        }
    }

    private void refreshViewSelectively() {
        System.out.println("DEBUG: refreshViewSelectively called");
        try {
            String updatedXsd = domManipulator.getXsdAsString();
            if (updatedXsd == null) return;

            System.out.println("DEBUG: Updating text editor only (no diagram rebuild)");
            controller.updateXsdContent(updatedXsd, false);

            System.out.println("DEBUG: Building new tree structure for comparison");
            XsdViewService viewService = new XsdViewService();
            XsdNodeInfo newRootNode = viewService.buildLightweightTree(updatedXsd);
            if (newRootNode != null) {
                System.out.println("DEBUG: Updating changed nodes selectively");
                updateChangedNodes(rootNode, newRootNode);
                triggerLiveValidation();

                if (propertyPanel != null) {
                    propertyPanel.refreshTypes();
                }
                System.out.println("DEBUG: Selective refresh completed successfully");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Selective refresh FAILED, falling back to full rebuild: " + e.getMessage());
            logger.warn("Selective refresh failed, falling back to full rebuild", e);
            String updatedXsd = domManipulator.getXsdAsString();
            if (updatedXsd != null) {
                controller.updateXsdContent(updatedXsd, true);
            }
        }
    }

    private void updateChangedNodes(XsdNodeInfo oldNode, XsdNodeInfo newNode) {
        if (oldNode == null || newNode == null) return;

        if (!nodesAreEqual(oldNode, newNode)) {
            System.out.println("DEBUG: Node changed: " + oldNode.name() + " (type: " + oldNode.type() + " -> " + newNode.type() + ")");
            Node visualNode = nodeViewCache.get(oldNode);
            if (visualNode != null) {
                System.out.println("DEBUG: Updating visual node for: " + oldNode.name());
                updateSingleNodeView(visualNode, newNode);
                nodeViewCache.put(newNode, visualNode);
                nodeViewCache.remove(oldNode);
            } else {
                System.out.println("DEBUG: No visual node found in cache for: " + oldNode.name());
            }
        }

        if (oldNode.children() != null && newNode.children() != null) {
            Map<String, XsdNodeInfo> oldChildrenMap = new HashMap<>();
            for (XsdNodeInfo child : oldNode.children()) {
                String key = child.xpath() != null ? child.xpath() : child.name();
                oldChildrenMap.put(key, child);
            }

            Map<String, XsdNodeInfo> newChildrenMap = new HashMap<>();
            for (XsdNodeInfo child : newNode.children()) {
                String key = child.xpath() != null ? child.xpath() : child.name();
                if (!newChildrenMap.containsKey(key)) {
                    newChildrenMap.put(key, child);
                }
            }

            for (Map.Entry<String, XsdNodeInfo> newChild : newChildrenMap.entrySet()) {
                XsdNodeInfo oldChild = oldChildrenMap.get(newChild.getKey());
                updateChangedNodes(oldChild, newChild.getValue());
            }
        }
    }

    private boolean nodesAreEqual(XsdNodeInfo oldNode, XsdNodeInfo newNode) {
        return Objects.equals(oldNode.name(), newNode.name()) &&
                Objects.equals(oldNode.type(), newNode.type()) &&
                Objects.equals(oldNode.documentation(), newNode.documentation()) &&
                Objects.equals(oldNode.minOccurs(), newNode.minOccurs()) &&
                Objects.equals(oldNode.maxOccurs(), newNode.maxOccurs()) &&
                Objects.equals(oldNode.nodeType(), newNode.nodeType());
    }

    private void updateSingleNodeView(Node visualNode, XsdNodeInfo newNodeInfo) {
        Platform.runLater(() -> {
            if (visualNode instanceof VBox nodeContainer) {
                for (Node child : nodeContainer.getChildren()) {
                    if (child instanceof HBox headerBox) {
                        for (Node headerChild : headerBox.getChildren()) {
                            if (headerChild instanceof Label nameLabel && nameLabel.getStyleClass().contains("node-name")) {
                                nameLabel.setText(newNodeInfo.name());
                            }
                            if (headerChild instanceof Label typeLabel && typeLabel.getStyleClass().contains("node-type")) {
                                typeLabel.setText(newNodeInfo.type());
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Performs selective refresh of only the currently selected node
     * This preserves expansion states and only updates what changed
     */
    private void performSelectiveNodeRefresh() {
        if (selectedNode == null) {
            System.out.println("DEBUG: No selected node for selective refresh");
            return;
        }

        System.out.println("DEBUG: Performing selective refresh for node: " + selectedNode.name());

        try {
            // Get the updated node info from the DOM
            String updatedXsd = domManipulator.getXsdAsString();
            if (updatedXsd == null) return;

            // Build new tree structure to get updated node info
            XsdViewService viewService = new XsdViewService();
            XsdNodeInfo newRootNode = viewService.buildLightweightTree(updatedXsd);
            if (newRootNode == null) return;

            // Find the corresponding node in the new tree
            XsdNodeInfo updatedNodeInfo = findNodeByXPath(newRootNode, selectedNode.xpath());
            if (updatedNodeInfo == null) {
                System.out.println("DEBUG: Could not find updated node info, falling back to full refresh");
                refreshView();
                return;
            }

            // Update the rootNode reference to the new tree
            this.rootNode = newRootNode;

            // Update the visual representation of the selected node
            updateSingleNodeVisual(selectedNode, updatedNodeInfo);

            // Update the selectedNode reference to the new info
            selectedNode = updatedNodeInfo;

            // Update property panel with fresh data from DOM
            if (propertyPanel != null) {
                // Force refresh of property panel with updated node info
                propertyPanel.setSelectedNode(updatedNodeInfo);
                // Also refresh types to ensure all available types are up to date
                propertyPanel.refreshTypes();

                // Force update of property panel fields with current DOM values
                Platform.runLater(() -> {
                    propertyPanel.forceUpdateFromDOM();
                    // Also force a complete field update to ensure all values are current
                    propertyPanel.updateFields();
                });
            }

            System.out.println("DEBUG: Selective refresh completed successfully");

        } catch (Exception e) {
            System.out.println("DEBUG: Selective refresh failed, falling back to full refresh: " + e.getMessage());
            logger.warn("Selective refresh failed, falling back to full refresh", e);
            refreshView();
        }
    }

    /**
     * Finds a node by XPath in the tree structure
     */
    private XsdNodeInfo findNodeByXPath(XsdNodeInfo rootNode, String targetXPath) {
        if (rootNode == null || targetXPath == null) return null;

        if (targetXPath.equals(rootNode.xpath())) {
            return rootNode;
        }

        if (rootNode.children() != null) {
            for (XsdNodeInfo child : rootNode.children()) {
                XsdNodeInfo found = findNodeByXPath(child, targetXPath);
                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * Updates the visual representation of a single node
     */
    private void updateSingleNodeVisual(XsdNodeInfo oldNodeInfo, XsdNodeInfo newNodeInfo) {
        Node visualNode = nodeViewCache.get(oldNodeInfo);
        if (visualNode == null) {
            System.out.println("DEBUG: No visual node found in cache for: " + oldNodeInfo.name() +
                    " - node may not be expanded yet");
            // If the node is not in cache, it means it's not currently visible (not expanded)
            // In this case, we just skip the visual update since the node is not visible
            return;
        }

        System.out.println("DEBUG: Updating visual node for: " + oldNodeInfo.name());

        // Update the visual node with new information
        Platform.runLater(() -> {
            updateNodeLabel(visualNode, newNodeInfo);
            updateNodeStyle(visualNode, newNodeInfo);

            // Update cache mapping
            nodeViewCache.remove(oldNodeInfo);
            nodeViewCache.put(newNodeInfo, visualNode);
        });
    }

    /**
     * Updates the label text and content of a visual node
     */
    private void updateNodeLabel(Node visualNode, XsdNodeInfo nodeInfo) {
        // Handle different visual node types
        if (visualNode instanceof HBox hbox) {
            updateHBoxNodeLabel(hbox, nodeInfo);
        } else if (visualNode instanceof VBox vbox) {
            updateVBoxNodeLabel(vbox, nodeInfo);
        }
    }

    /**
     * Updates HBox-based node labels (most common case)
     */
    private void updateHBoxNodeLabel(HBox hbox, XsdNodeInfo nodeInfo) {
        for (Node child : hbox.getChildren()) {
            if (child instanceof Label label) {
                // Check if this is the main name label
                if (isMainNameLabel(label, nodeInfo)) {
                    updateMainLabel(label, nodeInfo);
                    updateCardinalityLabel(hbox, nodeInfo);
                    break;
                }
            }
        }
    }

    /**
     * Updates VBox-based node labels (for complex structures)
     */
    private void updateVBoxNodeLabel(VBox vbox, XsdNodeInfo nodeInfo) {
        // Find the first HBox in the VBox that contains the main label
        for (Node child : vbox.getChildren()) {
            if (child instanceof HBox hbox) {
                updateHBoxNodeLabel(hbox, nodeInfo);
                break;
            }
        }
    }

    /**
     * Checks if a label is the main name label for the node
     */
    private boolean isMainNameLabel(Label label, XsdNodeInfo nodeInfo) {
        String text = label.getText();
        String style = label.getStyle();

        // Check by text content
        if (text != null && text.equals(nodeInfo.name())) {
            return true;
        }

        // Check by style (look for node or attribute label styles)
        return style != null && (
                style.contains("NODE_LABEL_STYLE") ||
                        style.contains("ATTRIBUTE_LABEL_STYLE") ||
                        style.contains("SEQUENCE_NODE_STYLE") ||
                        style.contains("CHOICE_NODE_STYLE") ||
                        style.contains("ANY_NODE_STYLE"));
    }

    /**
     * Updates the main label with new node information
     */
    private void updateMainLabel(Label label, XsdNodeInfo nodeInfo) {
        // Update text
        label.setText(nodeInfo.name());

        // Update type icon
        FontIcon newIcon = createTypeSpecificIcon(nodeInfo.type());
        label.setGraphic(newIcon);

        // Update style based on node type and cardinality
        String newStyle = determineNodeLabelStyle(nodeInfo,
                nodeInfo.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE);
        label.setStyle(newStyle);

        // Update tooltip
        String tooltipText = nodeInfo.name();
        if (nodeInfo.type() != null && !nodeInfo.type().isEmpty()) {
            tooltipText += " (" + nodeInfo.type() + ")";
        }
        label.setTooltip(new Tooltip(tooltipText));
    }

    /**
     * Updates the cardinality label in an HBox
     */
    private void updateCardinalityLabel(HBox hbox, XsdNodeInfo nodeInfo) {
        String cardinality = formatCardinality(nodeInfo.minOccurs(), nodeInfo.maxOccurs());

        // Find existing cardinality label
        Label cardinalityLabel = null;
        for (Node child : hbox.getChildren()) {
            if (child instanceof Label label &&
                    label.getStyle().contains("CARDINALITY_LABEL_STYLE")) {
                cardinalityLabel = label;
                break;
            }
        }

        if (cardinality.isEmpty()) {
            // Remove cardinality label if not needed
            if (cardinalityLabel != null) {
                hbox.getChildren().remove(cardinalityLabel);
            }
        } else {
            // Add or update cardinality label
            if (cardinalityLabel == null) {
                cardinalityLabel = new Label(cardinality);
                cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
                hbox.getChildren().add(cardinalityLabel);
            } else {
                cardinalityLabel.setText(cardinality);
            }
        }
    }

    /**
     * Updates the style of a visual node based on node info
     */
    private void updateNodeStyle(Node visualNode, XsdNodeInfo nodeInfo) {
        // Style updates are handled in updateNodeLabel for labels
        // Additional style updates can be added here if needed
    }

    /**
     * Rebuild the diagram view to reflect current search highlighting
     */
    private void rebuildDiagram() {
        if (rootNode == null) return;

        // Store current search text and caret position
        String currentText = searchField != null ? searchField.getText() : "";
        int caretPosition = searchField != null ? searchField.getCaretPosition() : 0;

        // Store expansion state before rebuilding
        Map<String, Boolean> expansionState = saveExpansionState();

        // Clear caches before rebuilding
        nodeViewCache.clear();

        // Refresh property panel types when diagram rebuilds
        if (propertyPanel != null) {
            propertyPanel.refreshTypes();
        }

        // Find the diagram container
        Platform.runLater(() -> {
            try {
                // Navigate to the diagram container (VBox inside ScrollPane)
                if (treeScrollPane != null && treeScrollPane.getContent() instanceof VBox diagramContainer) {
                    // Clear and rebuild the diagram
                    diagramContainer.getChildren().clear();
                    Node rootNodeView = createNodeView(rootNode);
                    diagramContainer.getChildren().add(rootNodeView);

                    // Restore expansion state after rebuild
                    restoreExpansionState(diagramContainer, expansionState);

                    // Restore focus and text to search field
                    if (searchField != null) {
                        searchField.setText(currentText);
                        searchField.positionCaret(caretPosition);
                        searchField.requestFocus();
                    }
                }
            } catch (Exception e) {
                logger.error("Error rebuilding diagram for search highlighting", e);
            }
        });
    }

    /**
     * Save the current expansion state of all nodes in the tree
     */
    private Map<String, Boolean> saveExpansionState() {
        Map<String, Boolean> expansionState = new HashMap<>();
        if (treeScrollPane != null && treeScrollPane.getContent() instanceof VBox diagramContainer) {
            saveExpansionStateRecursive(diagramContainer, expansionState);
        }
        return expansionState;
    }

    /**
     * Recursively save expansion state of nodes
     */
    private void saveExpansionStateRecursive(javafx.scene.Parent parent, Map<String, Boolean> expansionState) {
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof VBox nodeView) {
                // Find the node label to get the XPath
                String xpath = getXPathFromNodeView(nodeView);
                if (xpath != null) {
                    // Check if children container is visible (expanded)
                    VBox childrenContainer = findChildrenContainer(nodeView);
                    if (childrenContainer != null) {
                        boolean isExpanded = childrenContainer.isVisible();
                        expansionState.put(xpath, isExpanded);

                        // Recursively save children expansion states
                        if (isExpanded) {
                            saveExpansionStateRecursive(childrenContainer, expansionState);
                        }
                    }
                }
            } else if (child instanceof javafx.scene.Parent parentChild) {
                saveExpansionStateRecursive(parentChild, expansionState);
            }
        }
    }

    /**
     * Restore the expansion state of nodes after rebuild
     */
    private void restoreExpansionState(VBox diagramContainer, Map<String, Boolean> expansionState) {
        Platform.runLater(() -> restoreExpansionStateRecursive(diagramContainer, expansionState));
    }

    /**
     * Recursively restore expansion state of nodes
     */
    private void restoreExpansionStateRecursive(javafx.scene.Parent parent, Map<String, Boolean> expansionState) {
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof VBox nodeView) {
                String xpath = getXPathFromNodeView(nodeView);
                if (xpath != null && expansionState.containsKey(xpath)) {
                    boolean shouldExpand = expansionState.get(xpath);

                    // Find toggle button and children container
                    Label toggleButton = findToggleButton(nodeView);
                    VBox childrenContainer = findChildrenContainer(nodeView);

                    if (toggleButton != null && childrenContainer != null && shouldExpand) {
                        // Simulate click to expand
                        toggleButton.fireEvent(new javafx.scene.input.MouseEvent(
                                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                                javafx.scene.input.MouseButton.PRIMARY, 1,
                                false, false, false, false, false, false, false, false, false, false, null
                        ));

                        // Recursively restore children
                        if (childrenContainer.isVisible()) {
                            restoreExpansionStateRecursive(childrenContainer, expansionState);
                        }
                    }
                }
            } else if (child instanceof javafx.scene.Parent parentChild) {
                restoreExpansionStateRecursive(parentChild, expansionState);
            }
        }
    }

    /**
     * Get XPath from a node view by examining its label
     */
    private String getXPathFromNodeView(VBox nodeView) {
        for (javafx.scene.Node child : nodeView.getChildren()) {
            if (child instanceof HBox headerBox) {
                for (javafx.scene.Node headerChild : headerBox.getChildren()) {
                    if (headerChild instanceof Label label && label.getUserData() instanceof XsdNodeInfo nodeInfo) {
                        return nodeInfo.xpath();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the toggle button in a node view
     */
    private Label findToggleButton(VBox nodeView) {
        for (javafx.scene.Node child : nodeView.getChildren()) {
            if (child instanceof HBox headerBox) {
                for (javafx.scene.Node headerChild : headerBox.getChildren()) {
                    if (headerChild instanceof Label label &&
                            ("+".equals(label.getText()) || "−".equals(label.getText()))) {
                        return label;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the children container in a node view
     */
    private VBox findChildrenContainer(VBox nodeView) {
        for (javafx.scene.Node child : nodeView.getChildren()) {
            if (child instanceof VBox vbox && child != nodeView.getChildren().get(0)) {
                return vbox;
            }
        }
        return null;
    }

    /**
     * Check if a node can have children based on its type
     */
    private boolean canNodeHaveChildren(XsdNodeInfo node) {
        if (node.nodeType() == XsdNodeInfo.NodeType.SEQUENCE ||
                node.nodeType() == XsdNodeInfo.NodeType.CHOICE) {
            return true; // Structural elements can always have children
        }

        if (node.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
            String type = node.type();
            if (type == null || type.isEmpty()) {
                return true; // No type specified, assume it can have children
            }

            // Check if it's a built-in simple type
            return !type.startsWith("xs:") && !type.startsWith("xsd:"); // Built-in simple types cannot have children

            // Check if it's a simple type (no complex type)
            // For now, assume it can have children unless it's clearly a simple type
        }

        return false; // Other node types cannot have children
    }

    /**
     * Creates a virtual schema node info for schema-level operations
     */
    private XsdNodeInfo createSchemaNodeInfo() {
        return new XsdNodeInfo("schema", "schema", "/xs:schema", "XSD Schema Root",
                Collections.emptyList(), Collections.emptyList(), "1", "1", XsdNodeInfo.NodeType.SCHEMA);
    }

    /**
     * Finds the type definition node info for a given type name
     */
    private XsdNodeInfo findTypeNodeInfo(XsdNodeInfo contextNode, String typeName) {
        // Strip namespace prefix
        String cleanTypeName = typeName.contains(":") ? typeName.substring(typeName.indexOf(":") + 1) : typeName;

        // First try to find it as a complexType
        String complexTypeXPath = "/xs:schema/xs:complexType[@name='" + cleanTypeName + "']";
        Element complexTypeElement = domManipulator.findElementByXPath(complexTypeXPath);

        if (complexTypeElement != null) {
            return new XsdNodeInfo(cleanTypeName, typeName, complexTypeXPath, "",
                    Collections.emptyList(), Collections.emptyList(), "1", "1", XsdNodeInfo.NodeType.COMPLEX_TYPE);
        }

        // If not found as complexType, try simpleType
        String simpleTypeXPath = "/xs:schema/xs:simpleType[@name='" + cleanTypeName + "']";
        Element simpleTypeElement = domManipulator.findElementByXPath(simpleTypeXPath);

        if (simpleTypeElement != null) {
            return new XsdNodeInfo(cleanTypeName, typeName, simpleTypeXPath, "",
                    Collections.emptyList(), Collections.emptyList(), "1", "1", XsdNodeInfo.NodeType.SIMPLE_TYPE);
        }

        // Type not found
        logger.debug("Type '{}' not found in XSD", cleanTypeName);
        return null;
    }

    /**
     * Shows the Namespace Manager dialog
     */
    private void showNamespaceManagerDialog() {
        try {
            // Extract current namespace configuration from DOM
            NamespaceResult currentConfig = extractCurrentNamespaceConfiguration();

            // Show namespace editor dialog
            XsdNamespaceEditor namespaceEditor = new XsdNamespaceEditor(currentConfig);
            Optional<NamespaceResult> result = namespaceEditor.showAndWait();

            result.ifPresent(namespaceConfig -> {
                // Create and execute update command
                UpdateNamespacesCommand command = new UpdateNamespacesCommand(domManipulator, namespaceConfig);
                boolean success = undoManager.executeCommand(command);

                if (success) {
                    // Refresh the view to show updated namespaces
                    refreshView();
                    triggerLiveValidation();

                    // Show success message
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Namespace Update");
                    alert.setHeaderText("Namespaces Updated Successfully");
                    alert.setContentText("The namespace configuration has been updated. " +
                            "Use Undo (Ctrl+Z) if you want to revert the changes.");
                    alert.show();
                } else {
                    // Show error message
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Update Failed");
                    alert.setHeaderText("Failed to Update Namespaces");
                    alert.setContentText("An error occurred while updating the namespace configuration. " +
                            "Please check the XSD structure and try again.");
                    alert.showAndWait();
                }
            });

        } catch (Exception e) {
            logger.error("Error showing namespace manager dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Namespace Manager Error");
            alert.setContentText("An unexpected error occurred: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void showImportIncludeManagerDialog() {
        try {
            ImportIncludeManagerDialog dialog = new ImportIncludeManagerDialog(domManipulator, undoManager);
            dialog.showAndWait();

            // Refresh the view to show any changes made to imports/includes
            refreshView();
            triggerLiveValidation();

        } catch (Exception e) {
            logger.error("Error showing import/include manager dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Import/Include Manager Error");
            alert.setContentText("An unexpected error occurred: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Extracts current namespace configuration from the DOM
     */
    private NamespaceResult extractCurrentNamespaceConfiguration() {
        if (domManipulator == null || domManipulator.getDocument() == null) {
            return NamespaceResult.createDefault();
        }

        org.w3c.dom.Document doc = domManipulator.getDocument();
        org.w3c.dom.Element schemaElement = doc.getDocumentElement();

        if (schemaElement == null || !"schema".equals(schemaElement.getLocalName())) {
            return NamespaceResult.createDefault();
        }

        String targetNamespace = schemaElement.getAttribute("targetNamespace");
        String defaultNamespace = schemaElement.getAttribute("xmlns");

        boolean elementFormDefault = "qualified".equals(schemaElement.getAttribute("elementFormDefault"));
        boolean attributeFormDefault = "qualified".equals(schemaElement.getAttribute("attributeFormDefault"));

        Map<String, String> mappings = new HashMap<>();
        org.w3c.dom.NamedNodeMap attributes = schemaElement.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node attr = attributes.item(i);
            String attrName = attr.getNodeName();

            if (attrName.startsWith("xmlns:")) {
                String prefix = attrName.substring(6);
                mappings.put(prefix, attr.getNodeValue());
            }
        }

        return new NamespaceResult(targetNamespace, defaultNamespace, elementFormDefault, attributeFormDefault, mappings);
    }

    // === Live Validation Methods ===

    /**
     * Triggers live validation of the current XSD document.
     */
    public void triggerLiveValidation() {
        if (domManipulator != null && domManipulator.getDocument() != null) {
            validationService.validateLive(domManipulator.getDocument());
        }
    }

    /**
     * Updates the UI with validation results.
     */
    private void updateValidationUI(XsdLiveValidationService.ValidationResult result) {
        // Clear previous validation indicators
        clearValidationIndicators();

        // Add new validation indicators
        for (XsdLiveValidationService.ValidationIssue issue : result.getAllIssues()) {
            addValidationIndicator(issue);
        }

        // Update status in controller if available
        if (controller != null) {
            String statusMessage = createValidationStatusMessage(result);
            Platform.runLater(() -> controller.updateValidationStatus(statusMessage, result.hasErrors()));
        }
    }

    /**
     * Updates UI for a specific element validation.
     */
    private void updateElementValidationUI(XsdLiveValidationService.ValidationResult result, Element element) {
        // Find corresponding visual node and update it
        if (result.hasErrors()) {
            highlightElementErrors(element, result.getErrors());
        }
    }

    /**
     * Clears all validation indicators from the UI.
     */
    private void clearValidationIndicators() {
        // Remove all validation error nodes
        for (Node errorNode : validationErrorNodes) {
            if (errorNode.getParent() instanceof VBox) {
                ((VBox) errorNode.getParent()).getChildren().remove(errorNode);
            }
        }
        validationErrorNodes.clear();

        // Reset node styles
        resetNodeStyles();
    }

    /**
     * Adds a validation indicator to the UI.
     */
    private void addValidationIndicator(XsdLiveValidationService.ValidationIssue issue) {
        Element element = issue.element();
        if (element == null) return;

        // Create validation indicator
        Label indicator = createValidationIndicator(issue);

        // Find the corresponding visual node to attach indicator
        Node visualNode = findVisualNodeForElement(element);
        if (visualNode != null && visualNode.getParent() instanceof VBox parent) {
            parent.getChildren().add(parent.getChildren().indexOf(visualNode) + 1, indicator);
            validationErrorNodes.add(indicator);
        }

        // Update node style based on issue severity
        updateNodeStyleForIssue(visualNode, issue);
    }

    /**
     * Creates a validation indicator label.
     */
    private Label createValidationIndicator(XsdLiveValidationService.ValidationIssue issue) {
        Label indicator = new Label();
        indicator.setText(issue.message());
        indicator.setWrapText(true);
        indicator.setMaxWidth(300);

        switch (issue.severity()) {
            case ERROR:
                indicator.setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; " +
                        "-fx-border-color: #ef5350; -fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                        "-fx-padding: 4px 8px; -fx-font-size: 11px;");
                indicator.setGraphic(createColoredIcon("bi-exclamation-circle-fill", "#dc3545"));
                break;
            case WARNING:
                indicator.setStyle("-fx-background-color: #fff8e1; -fx-text-fill: #f57f17; " +
                        "-fx-border-color: #ffca28; -fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                        "-fx-padding: 4px 8px; -fx-font-size: 11px;");
                indicator.setGraphic(createColoredIcon("bi-exclamation-triangle-fill", "#ffc107"));
                break;
            case INFO:
                indicator.setStyle("-fx-background-color: #e3f2fd; -fx-text-fill: #1976d2; " +
                        "-fx-border-color: #42a5f5; -fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                        "-fx-padding: 4px 8px; -fx-font-size: 11px;");
                indicator.setGraphic(createColoredIcon("bi-info-circle-fill", "#17a2b8"));
                break;
        }

        return indicator;
    }

    /**
     * Creates validation status message.
     */
    private String createValidationStatusMessage(XsdLiveValidationService.ValidationResult result) {
        int errors = result.getErrors().size();
        int warnings = result.getWarnings().size();

        if (errors == 0 && warnings == 0) {
            return "✓ XSD is valid";
        } else if (errors > 0) {
            return String.format("✗ %d error%s, %d warning%s",
                    errors, errors == 1 ? "" : "s",
                    warnings, warnings == 1 ? "" : "s");
        } else {
            return String.format("⚠ %d warning%s", warnings, warnings == 1 ? "" : "s");
        }
    }

    /**
     * Highlights element errors visually.
     */
    private void highlightElementErrors(Element element, List<XsdLiveValidationService.ValidationIssue> errors) {
        Node visualNode = findVisualNodeForElement(element);
        if (visualNode != null) {
            // Add error style
            String currentStyle = visualNode.getStyle();
            String errorStyle = "-fx-border-color: #f44336; -fx-border-width: 2px; ";
            if (!currentStyle.contains("border-color")) {
                visualNode.setStyle(currentStyle + errorStyle);
            }

            // Add tooltip with error messages
            String errorText = errors.stream()
                    .map(XsdLiveValidationService.ValidationIssue::message)
                    .collect(Collectors.joining("\n"));
            Tooltip tooltip = new Tooltip(errorText);
            if (visualNode instanceof Control) {
                ((Control) visualNode).setTooltip(tooltip);
            } else {
                Tooltip.install(visualNode, tooltip);
            }
        }
    }

    /**
     * Finds the visual node corresponding to an XSD element.
     */
    private Node findVisualNodeForElement(Element element) {
        // This is a simplified implementation
        // In a real implementation, we would maintain a mapping between XSD elements and visual nodes
        String elementName = element.getAttribute("name");
        if (!elementName.isEmpty()) {
            return findNodeByName(treeScrollPane, elementName);
        }
        return null;
    }

    /**
     * Finds a node by name in the visual tree (recursive).
     */
    private Node findNodeByName(Node parent, String name) {
        if (parent instanceof Label label) {
            if (label.getText().contains(name)) {
                return label;
            }
        }

        if (parent instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) parent).getChildrenUnmodifiable()) {
                Node found = findNodeByName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Updates node style based on validation issue.
     */
    private void updateNodeStyleForIssue(Node node, XsdLiveValidationService.ValidationIssue issue) {
        if (node == null) return;

        String currentStyle = node.getStyle();
        String issueStyle;

        switch (issue.severity()) {
            case ERROR:
                issueStyle = "-fx-border-color: #f44336; -fx-border-width: 2px; ";
                break;
            case WARNING:
                issueStyle = "-fx-border-color: #ff9800; -fx-border-width: 1px; ";
                break;
            case INFO:
                issueStyle = "-fx-border-color: #2196f3; -fx-border-width: 1px; ";
                break;
            default:
                return;
        }

        // Merge styles
        if (!currentStyle.contains("border-color")) {
            node.setStyle(currentStyle + issueStyle);
        }
    }

    /**
     * Resets all node styles to remove validation indicators.
     */
    private void resetNodeStyles() {
        resetNodeStylesRecursive(treeScrollPane);
    }

    /**
     * Recursively resets node styles.
     */
    private void resetNodeStylesRecursive(Node parent) {
        if (parent instanceof Label label) {
            String currentStyle = label.getStyle();
            // Remove validation-specific styles
            String cleanStyle = currentStyle
                    .replaceAll("-fx-border-color: #[a-fA-F0-9]{6};", "")
                    .replaceAll("-fx-border-width: [0-9]+px;", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            label.setStyle(cleanStyle);

            // Remove validation tooltips
            Tooltip tooltip = label.getTooltip();
            if (tooltip != null && tooltip.getText() != null &&
                    (tooltip.getText().contains("error") || tooltip.getText().contains("warning"))) {
                label.setTooltip(null);
            }
        }

        if (parent instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) parent).getChildrenUnmodifiable()) {
                resetNodeStylesRecursive(child);
            }
        }
    }

    /**
     * Validates a specific element after editing.
     */
    public void validateElement(Element element) {
        if (element != null && domManipulator != null && domManipulator.getDocument() != null) {
            validationService.validateElementLive(element, domManipulator.getDocument());
        }
    }

    /**
     * Creates an advanced element creation dialog with type selection.
     */
    private Dialog<ElementCreationResult> createAdvancedElementDialog(XsdNodeInfo parentNode) {
        Dialog<ElementCreationResult> dialog = new Dialog<>();
        dialog.setTitle("Add Element");
        dialog.setHeaderText("Create new element in " + parentNode.name());
        dialog.setResizable(true);

        // Load CSS for styling
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-type-selector.css").toExternalForm()
        );

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        // Name field
        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField();
        nameField.setPromptText("Element name (required)");
        nameField.setPrefWidth(200);

        // Type selection
        Label typeLabel = new Label("Type:");
        HBox typeBox = new HBox(10);
        TextField typeField = new TextField("xs:string");
        typeField.setPrefWidth(200);
        typeField.setPromptText("Element type");

        Button typeButton = new Button("Browse...");
        typeButton.setGraphic(createColoredIcon("bi-search", "#6c757d"));
        typeButton.setOnAction(e -> {
            XsdTypeSelector typeSelector = new XsdTypeSelector(domManipulator != null ? domManipulator.getDocument() : null);
            Optional<String> selectedType = typeSelector.showAndWait();
            selectedType.ifPresent(typeField::setText);
        });

        typeBox.getChildren().addAll(typeField, typeButton);

        // Cardinality fields
        Label cardinalityLabel = new Label("Cardinality:");
        HBox cardinalityBox = new HBox(10);

        TextField minOccursField = new TextField("1");
        minOccursField.setPrefWidth(80);
        minOccursField.setPromptText("min");

        Label toLabel = new Label("to");

        ComboBox<String> maxOccursCombo = new ComboBox<>();
        maxOccursCombo.getItems().addAll("1", "2", "5", "10", "unbounded");
        maxOccursCombo.setValue("1");
        maxOccursCombo.setPrefWidth(100);
        maxOccursCombo.setEditable(true);

        cardinalityBox.getChildren().addAll(minOccursField, toLabel, maxOccursCombo);

        // Options
        Label optionsLabel = new Label("Options:");
        VBox optionsBox = new VBox(8);

        CheckBox nillableCheck = new CheckBox("Nillable");
        CheckBox abstractCheck = new CheckBox("Abstract");

        optionsBox.getChildren().addAll(nillableCheck, abstractCheck);

        // Documentation
        Label docLabel = new Label("Documentation:");
        TextArea docArea = new TextArea();
        docArea.setPromptText("Element documentation (optional)");
        docArea.setPrefRowCount(3);
        docArea.setWrapText(true);

        // Add to grid
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(typeLabel, 0, 1);
        grid.add(typeBox, 1, 1);
        grid.add(cardinalityLabel, 0, 2);
        grid.add(cardinalityBox, 1, 2);
        grid.add(optionsLabel, 0, 3);
        grid.add(optionsBox, 1, 3);
        grid.add(docLabel, 0, 4);
        grid.add(docArea, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        // Enable OK when name is provided
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });

        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ElementCreationResult(
                        nameField.getText().trim(),
                        typeField.getText().trim(),
                        minOccursField.getText().trim(),
                        maxOccursCombo.getValue(),
                        nillableCheck.isSelected(),
                        abstractCheck.isSelected(),
                        docArea.getText().trim()
                );
            }
            return null;
        });

        return dialog;
    }

    /**
     * Creates an advanced attribute creation dialog with type selection.
     */
    private Dialog<AttributeCreationResult> createAdvancedAttributeDialog(XsdNodeInfo parentNode) {
        Dialog<AttributeCreationResult> dialog = new Dialog<>();
        dialog.setTitle("Add Attribute");
        dialog.setHeaderText("Create new attribute in " + parentNode.name());
        dialog.setResizable(true);

        // Load CSS for styling
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-type-selector.css").toExternalForm()
        );

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        // Name field
        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField();
        nameField.setPromptText("Attribute name (required)");
        nameField.setPrefWidth(200);

        // Type selection
        Label typeLabel = new Label("Type:");
        HBox typeBox = new HBox(10);
        TextField typeField = new TextField("xs:string");
        typeField.setPrefWidth(200);
        typeField.setPromptText("Attribute type");

        Button typeButton = new Button("Browse...");
        typeButton.setGraphic(createColoredIcon("bi-search", "#6c757d"));
        typeButton.setOnAction(e -> {
            XsdTypeSelector typeSelector = new XsdTypeSelector(domManipulator != null ? domManipulator.getDocument() : null);
            Optional<String> selectedType = typeSelector.showAndWait();
            selectedType.ifPresent(typeField::setText);
        });

        typeBox.getChildren().addAll(typeField, typeButton);

        // Use field
        Label useLabel = new Label("Use:");
        ComboBox<String> useCombo = new ComboBox<>();
        useCombo.getItems().addAll("optional", "required", "prohibited");
        useCombo.setValue("optional");
        useCombo.setPrefWidth(120);

        // Default/Fixed value
        Label valueLabel = new Label("Value:");
        VBox valueBox = new VBox(8);

        RadioButton defaultRadio = new RadioButton("Default:");
        RadioButton fixedRadio = new RadioButton("Fixed:");
        RadioButton noValueRadio = new RadioButton("No default value");

        ToggleGroup valueGroup = new ToggleGroup();
        defaultRadio.setToggleGroup(valueGroup);
        fixedRadio.setToggleGroup(valueGroup);
        noValueRadio.setToggleGroup(valueGroup);
        noValueRadio.setSelected(true);

        TextField valueField = new TextField();
        valueField.setPromptText("Value");
        valueField.setDisable(true);

        // Enable/disable value field based on selection
        valueGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            valueField.setDisable(newVal == noValueRadio);
        });

        valueBox.getChildren().addAll(noValueRadio,
                new HBox(10, defaultRadio, new Label("Value:")),
                new HBox(10, fixedRadio, valueField));

        // Documentation
        Label docLabel = new Label("Documentation:");
        TextArea docArea = new TextArea();
        docArea.setPromptText("Attribute documentation (optional)");
        docArea.setPrefRowCount(3);
        docArea.setWrapText(true);

        // Add to grid
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(typeLabel, 0, 1);
        grid.add(typeBox, 1, 1);
        grid.add(useLabel, 0, 2);
        grid.add(useCombo, 1, 2);
        grid.add(valueLabel, 0, 3);
        grid.add(valueBox, 1, 3);
        grid.add(docLabel, 0, 4);
        grid.add(docArea, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        // Enable OK when name is provided
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });

        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String valueType = noValueRadio.isSelected() ? null :
                        defaultRadio.isSelected() ? "default" : "fixed";
                String value = noValueRadio.isSelected() ? null : valueField.getText().trim();

                return new AttributeCreationResult(
                        nameField.getText().trim(),
                        typeField.getText().trim(),
                        useCombo.getValue(),
                        valueType,
                        value,
                        docArea.getText().trim()
                );
            }
            return null;
        });

        return dialog;
    }

    // Result Records for Dialog Data Transfer

    /**
     * Result record for element creation.
     */
    public record ElementCreationResult(
            String name,
            String type,
            String minOccurs,
            String maxOccurs,
            boolean nillable,
            boolean abstractElement,
            String documentation
    ) {
    }

    /**
     * Result record for sequence creation
     */
    private record SequenceCreationResult(String minOccurs, String maxOccurs) {
    }

    /**
     * Result record for choice creation
     */
    private record ChoiceCreationResult(String minOccurs, String maxOccurs) {
    }

    /**
     * Result record for attribute creation.
     */
    public record AttributeCreationResult(
            String name,
            String type,
            String use,
            String valueType, // "default", "fixed", or null
            String value,
            String documentation
    ) {
    }

    /**
     * Serialize the current DOM document to XML string
     */
    private String serializeDocumentToString() throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(domManipulator.getDocument()), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Shows validation rules dialog for editing XSD validation constraints
     */
    private void showValidationRulesDialog(XsdNodeInfo nodeInfo) {
        try {
            logger.info("Opening validation rules dialog for node: {} (type: {})", nodeInfo.name(), nodeInfo.nodeType());

            // Create and configure the validation rules editor
            XsdValidationRulesEditor editor = new XsdValidationRulesEditor(nodeInfo);
            editor.initOwner(treeScrollPane.getScene().getWindow());

            // Show the dialog and handle the result
            Optional<ValidationRulesResult> result = editor.showAndWait();
            if (result.isPresent()) {
                ValidationRulesResult validationRules = result.get();
                logger.info("Validation rules updated: {}", validationRules.getSummary());

                // Create and execute the update command
                UpdateValidationRulesCommand command = new UpdateValidationRulesCommand(
                        domManipulator, nodeInfo, validationRules);

                if (command.execute()) {
                    // Refresh the view to show changes
                    refreshView();

                    logger.info("Successfully applied validation rules to node: {}", nodeInfo.name());
                } else {
                    logger.error("Failed to apply validation rules to node: {}", nodeInfo.name());
                    showErrorAlert("Validation Rules Error",
                            "Failed to apply validation rules to element '" + nodeInfo.name() + "'.\n" +
                                    "Please check the element structure and try again.");
                }
            }

        } catch (Exception e) {
            logger.error("Error opening validation rules dialog for node: " + nodeInfo.name(), e);
            showErrorAlert("Validation Rules Dialog Error",
                    "Failed to open validation rules dialog:\n" + e.getMessage());
        }
    }

    /**
     * Shows safe rename dialog with preview of affected references
     */
    private void showSafeRenameDialog(XsdNodeInfo nodeInfo) {
        try {
            logger.info("Opening safe rename dialog for node: {} (type: {})", nodeInfo.name(), nodeInfo.nodeType());

            // Create and configure the safe rename dialog
            XsdSafeRenameDialog dialog = new XsdSafeRenameDialog(nodeInfo, domManipulator);
            dialog.initOwner(treeScrollPane.getScene().getWindow());

            // Show the dialog and handle the result
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String newName = result.get();
                logger.info("Safe rename requested: '{}' -> '{}'", nodeInfo.name(), newName);

                // Create and execute the safe rename command
                SafeRenameCommand command = new SafeRenameCommand(
                        domManipulator,
                        nodeInfo,
                        newName,
                        dialog.getAffectedReferences(),
                        dialog.shouldUpdateReferences()
                );

                if (command.execute()) {
                    // Add command to undo stack if XsdController supports it
                    if (controller != null) {
                        // TODO: Add command to undo stack when XsdController supports it
                        logger.debug("Command executed successfully, undo stack integration pending");
                    }

                    // Refresh the view to show changes
                    refreshView();

                    // Show success feedback
                    int updatedRefs = command.getUpdatedReferencesCount();
                    String message = String.format("Successfully renamed '%s' to '%s'",
                            command.getOriginalName(), command.getNewName());
                    if (updatedRefs > 0) {
                        message += String.format("\n%d reference(s) were updated.", updatedRefs);
                    }

                    showSuccessAlert("Safe Rename Completed", message);

                    logger.info("Successfully completed safe rename: '{}' -> '{}' with {} reference updates",
                            command.getOriginalName(), command.getNewName(), updatedRefs);
                } else {
                    logger.error("Failed to execute safe rename for node: {}", nodeInfo.name());
                    showErrorAlert("Safe Rename Error",
                            "Failed to rename element '" + nodeInfo.name() + "'.\n" +
                                    "Please check the element structure and try again.");
                }
            }

        } catch (Exception e) {
            logger.error("Error opening safe rename dialog for node: " + nodeInfo.name(), e);
            showErrorAlert("Safe Rename Dialog Error",
                    "Failed to open safe rename dialog:\n" + e.getMessage());
        }
    }

    /**
     * Shows a success alert dialog
     */
    private void showSuccessAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows an error alert dialog
     */
    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Creates an advanced sequence creation dialog with cardinality selection.
     */
    private Dialog<SequenceCreationResult> createAdvancedSequenceDialog(XsdNodeInfo parentNode) {
        Dialog<SequenceCreationResult> dialog = new Dialog<>();
        dialog.setTitle("Add Sequence");
        dialog.setHeaderText("Create new sequence in " + parentNode.name());
        dialog.setResizable(true);

        // Load CSS for styling
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-type-selector.css").toExternalForm()
        );

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        // Cardinality fields
        Label cardinalityLabel = new Label("Cardinality:");
        HBox cardinalityBox = new HBox(10);

        TextField minOccursField = new TextField("1");
        minOccursField.setPrefWidth(80);
        minOccursField.setPromptText("min");

        Label toLabel = new Label("to");

        ComboBox<String> maxOccursCombo = new ComboBox<>();
        maxOccursCombo.getItems().addAll("1", "2", "5", "10", "unbounded");
        maxOccursCombo.setValue("1");
        maxOccursCombo.setPrefWidth(100);
        maxOccursCombo.setEditable(true);

        cardinalityBox.getChildren().addAll(minOccursField, toLabel, maxOccursCombo);

        // Description
        Label descLabel = new Label("Description:");
        Label descText = new Label("A sequence requires elements to appear in the specified order.");
        descText.setWrapText(true);
        descText.setStyle("-fx-font-style: italic; -fx-text-fill: #666;");

        // Add to grid
        grid.add(cardinalityLabel, 0, 0);
        grid.add(cardinalityBox, 1, 0);
        grid.add(descLabel, 0, 1);
        grid.add(descText, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new SequenceCreationResult(
                        minOccursField.getText().trim().isEmpty() ? "1" : minOccursField.getText().trim(),
                        maxOccursCombo.getValue() != null ? maxOccursCombo.getValue() : "1"
                );
            }
            return null;
        });

        return dialog;
    }

    /**
     * Creates an advanced choice creation dialog with cardinality selection.
     */
    private Dialog<ChoiceCreationResult> createAdvancedChoiceDialog(XsdNodeInfo parentNode) {
        Dialog<ChoiceCreationResult> dialog = new Dialog<>();
        dialog.setTitle("Add Choice");
        dialog.setHeaderText("Create new choice in " + parentNode.name());
        dialog.setResizable(true);

        // Load CSS for styling
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-type-selector.css").toExternalForm()
        );

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        // Cardinality fields
        Label cardinalityLabel = new Label("Cardinality:");
        HBox cardinalityBox = new HBox(10);

        TextField minOccursField = new TextField("1");
        minOccursField.setPrefWidth(80);
        minOccursField.setPromptText("min");

        Label toLabel = new Label("to");

        ComboBox<String> maxOccursCombo = new ComboBox<>();
        maxOccursCombo.getItems().addAll("1", "2", "5", "10", "unbounded");
        maxOccursCombo.setValue("1");
        maxOccursCombo.setPrefWidth(100);
        maxOccursCombo.setEditable(true);

        cardinalityBox.getChildren().addAll(minOccursField, toLabel, maxOccursCombo);

        // Description
        Label descLabel = new Label("Description:");
        Label descText = new Label("A choice allows selection of one element from multiple alternatives.");
        descText.setWrapText(true);
        descText.setStyle("-fx-font-style: italic; -fx-text-fill: #666;");

        // Add to grid
        grid.add(cardinalityLabel, 0, 0);
        grid.add(cardinalityBox, 1, 0);
        grid.add(descLabel, 0, 1);
        grid.add(descText, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ChoiceCreationResult(
                        minOccursField.getText().trim().isEmpty() ? "1" : minOccursField.getText().trim(),
                        maxOccursCombo.getValue() != null ? maxOccursCombo.getValue() : "1"
                );
            }
            return null;
        });

        return dialog;
    }

    /**
     * Opens the specified type in a dedicated graphic editor tab
     */
    private void openTypeInGraphicEditor(XsdNodeInfo typeNodeInfo) {
        try {
            logger.info("Opening type '{}' in graphic editor", typeNodeInfo.name());

            // Find the actual DOM element for this type
            Element typeElement = findTypeElement(typeNodeInfo);
            if (typeElement == null) {
                logger.warn("Could not find DOM element for type: {}", typeNodeInfo.name());
                showErrorAlert("Type Not Found",
                        "Could not find the DOM element for type '" + typeNodeInfo.name() + "'");
                return;
            }

            // Delegate to controller to open the type editor
            if (controller != null) {
                controller.openTypeInGraphicEditor(typeElement);
            } else {
                logger.error("Controller not available for opening type editor");
                showErrorAlert("Controller Error",
                        "Cannot open type editor: Controller not available");
            }

        } catch (Exception e) {
            logger.error("Failed to open type '{}' in graphic editor", typeNodeInfo.name(), e);
            showErrorAlert("Error Opening Type Editor",
                    "Failed to open type editor: " + e.getMessage());
        }
    }

    /**
     * Finds the DOM element for the given type node info
     */
    private Element findTypeElement(XsdNodeInfo typeNodeInfo) {
        if (domManipulator == null || domManipulator.getDocument() == null) {
            return null;
        }

        try {
            // Use XPath to find the type element
            String xpath = typeNodeInfo.xpath();
            if (xpath == null || xpath.isEmpty()) {
                // Fallback: search by name and type
                String elementName = typeNodeInfo.nodeType() == XsdNodeInfo.NodeType.SIMPLE_TYPE ?
                        "simpleType" : "complexType";
                xpath = "//xs:" + elementName + "[@name='" + typeNodeInfo.name() + "']";
            }

            return domManipulator.findElementByXPath(xpath);

        } catch (Exception e) {
            logger.warn("Failed to find type element using XPath: {}", typeNodeInfo.xpath(), e);
            return null;
        }
    }

    /**
     * Creates a colored FontIcon for menu items
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(12);
        return icon;
    }
}