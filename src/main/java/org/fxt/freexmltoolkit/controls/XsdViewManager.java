package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages multiple synchronized views of XSD content.
 * Provides a tabbed interface with different visualization modes
 * and keeps all views in sync when the underlying XSD changes.
 */
public class XsdViewManager extends BorderPane {
    private static final Logger logger = LogManager.getLogger(XsdViewManager.class);

    // Available view types
    public enum ViewType {
        TREE_VIEW("Tree", "bi-diagram-3", "Hierarchical tree view"),
        GRID_VIEW("Grid", "bi-grid-3x3", "Table/grid view"),
        SOURCE_VIEW("Source", "bi-code", "Source code view"),
        UML_VIEW("UML", "bi-diagram-2", "UML-style diagram view");

        private final String displayName;
        private final String iconLiteral;
        private final String description;

        ViewType(String displayName, String iconLiteral, String description) {
            this.displayName = displayName;
            this.iconLiteral = iconLiteral;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIconLiteral() {
            return iconLiteral;
        }

        public String getDescription() {
            return description;
        }
    }

    // View synchronization listeners
    public interface ViewSynchronizationListener {
        void onSelectionChanged(XsdNodeInfo selectedNode);

        void onContentChanged();

        void onViewChanged(ViewType newView);
    }

    // Components
    private final TabPane viewTabPane;
    private final XsdDomManipulator domManipulator;
    private final List<ViewSynchronizationListener> synchronizationListeners;

    // Properties
    private final ObjectProperty<XsdNodeInfo> selectedNodeProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<ViewType> currentViewProperty = new SimpleObjectProperty<>();

    // View instances
    private XsdDiagramView treeView;
    private XsdGridView gridView;
    private XsdSourceView sourceView;
    private XsdUmlView umlView;

    public XsdViewManager(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
        this.synchronizationListeners = new CopyOnWriteArrayList<>();
        this.viewTabPane = new TabPane();

        initializeComponents();
        setupEventHandlers();
        createDefaultViews();

        // Set initial view
        currentViewProperty.set(ViewType.TREE_VIEW);
    }

    private void initializeComponents() {
        // Configure tab pane
        viewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        viewTabPane.getStyleClass().add("view-manager-tabs");

        // Set layout
        setCenter(viewTabPane);

        // Add toolbar for view management
        ToolBar viewToolbar = createViewToolbar();
        setTop(viewToolbar);
    }

    private ToolBar createViewToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("view-manager-toolbar");

        // View selection buttons
        ToggleGroup viewToggleGroup = new ToggleGroup();

        for (ViewType viewType : ViewType.values()) {
            ToggleButton viewButton = new ToggleButton(viewType.getDisplayName());
            viewButton.setGraphic(new FontIcon(viewType.getIconLiteral()));
            viewButton.setToggleGroup(viewToggleGroup);
            viewButton.setUserData(viewType);
            viewButton.setTooltip(new Tooltip(viewType.getDescription()));

            viewButton.setOnAction(e -> switchToView(viewType));

            toolbar.getItems().add(viewButton);

            // Select tree view by default
            if (viewType == ViewType.TREE_VIEW) {
                viewButton.setSelected(true);
            }
        }

        // Separator
        toolbar.getItems().add(new Separator());

        // Sync controls
        Button syncAllButton = new Button("Sync All");
        syncAllButton.setGraphic(new FontIcon("bi-arrow-repeat"));
        syncAllButton.setTooltip(new Tooltip("Synchronize all views"));
        syncAllButton.setOnAction(e -> synchronizeAllViews());

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setTooltip(new Tooltip("Refresh current view"));
        refreshButton.setOnAction(e -> refreshCurrentView());

        toolbar.getItems().addAll(syncAllButton, refreshButton);

        return toolbar;
    }

    private void setupEventHandlers() {
        // Listen to tab selection changes
        viewTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                ViewType newViewType = (ViewType) newTab.getUserData();
                if (newViewType != null && !newViewType.equals(currentViewProperty.get())) {
                    currentViewProperty.set(newViewType);
                    notifyViewChanged(newViewType);
                }
            }
        });

        // Listen to property changes
        selectedNodeProperty.addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                notifySelectionChanged(newNode);
            }
        });
    }

    private void createDefaultViews() {
        // Create tree view (always available)
        createTreeView();

        // Grid view will be created on demand
        // Source view will be created on demand  
        // UML view will be created on demand
    }

    private void createTreeView() {
        if (treeView == null) {
            // Create a placeholder for the tree view
            // In the full implementation, this would be properly integrated with the existing XsdDiagramView
            treeView = null; // Placeholder - will be set externally
        }

        Tab treeTab = new Tab(ViewType.TREE_VIEW.getDisplayName());
        treeTab.setGraphic(new FontIcon(ViewType.TREE_VIEW.getIconLiteral()));
        treeTab.setUserData(ViewType.TREE_VIEW);

        // Create placeholder content
        Label placeholderLabel = new Label("Tree View\n(Integrated with existing XSD Editor)");
        placeholderLabel.setStyle("-fx-font-size: 16px; -fx-text-alignment: center;");
        treeTab.setContent(placeholderLabel);

        treeTab.setTooltip(new Tooltip(ViewType.TREE_VIEW.getDescription()));

        if (!viewTabPane.getTabs().contains(treeTab)) {
            viewTabPane.getTabs().add(treeTab);
        }
    }

    private void createGridView() {
        if (gridView == null) {
            gridView = new XsdGridView(domManipulator);

            // Add selection listener
            gridView.selectedNodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null && !newNode.equals(selectedNodeProperty.get())) {
                    selectedNodeProperty.set(newNode);
                }
            });
        }

        Tab gridTab = new Tab(ViewType.GRID_VIEW.getDisplayName());
        gridTab.setGraphic(new FontIcon(ViewType.GRID_VIEW.getIconLiteral()));
        gridTab.setUserData(ViewType.GRID_VIEW);
        gridTab.setContent(gridView);
        gridTab.setTooltip(new Tooltip(ViewType.GRID_VIEW.getDescription()));

        if (!viewTabPane.getTabs().stream().anyMatch(t -> t.getUserData() == ViewType.GRID_VIEW)) {
            viewTabPane.getTabs().add(gridTab);
        }
    }

    private void createSourceView() {
        if (sourceView == null) {
            sourceView = new XsdSourceView(domManipulator);
        }

        Tab sourceTab = new Tab(ViewType.SOURCE_VIEW.getDisplayName());
        sourceTab.setGraphic(new FontIcon(ViewType.SOURCE_VIEW.getIconLiteral()));
        sourceTab.setUserData(ViewType.SOURCE_VIEW);
        sourceTab.setContent(sourceView);
        sourceTab.setTooltip(new Tooltip(ViewType.SOURCE_VIEW.getDescription()));

        if (!viewTabPane.getTabs().stream().anyMatch(t -> t.getUserData() == ViewType.SOURCE_VIEW)) {
            viewTabPane.getTabs().add(sourceTab);
        }
    }

    private void createUmlView() {
        if (umlView == null) {
            umlView = new XsdUmlView(domManipulator);

            // Add selection listener
            umlView.selectedNodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null && !newNode.equals(selectedNodeProperty.get())) {
                    selectedNodeProperty.set(newNode);
                }
            });
        }

        Tab umlTab = new Tab(ViewType.UML_VIEW.getDisplayName());
        umlTab.setGraphic(new FontIcon(ViewType.UML_VIEW.getIconLiteral()));
        umlTab.setUserData(ViewType.UML_VIEW);
        umlTab.setContent(umlView);
        umlTab.setTooltip(new Tooltip(ViewType.UML_VIEW.getDescription()));

        if (!viewTabPane.getTabs().stream().anyMatch(t -> t.getUserData() == ViewType.UML_VIEW)) {
            viewTabPane.getTabs().add(umlTab);
        }
    }

    /**
     * Switch to a specific view type
     */
    public void switchToView(ViewType viewType) {
        // Create view if it doesn't exist
        switch (viewType) {
            case TREE_VIEW -> createTreeView();
            case GRID_VIEW -> createGridView();
            case SOURCE_VIEW -> createSourceView();
            case UML_VIEW -> createUmlView();
        }

        // Select the tab
        viewTabPane.getTabs().stream()
                .filter(tab -> tab.getUserData() == viewType)
                .findFirst()
                .ifPresent(tab -> viewTabPane.getSelectionModel().select(tab));
    }

    /**
     * Synchronize all views with the current state
     */
    public void synchronizeAllViews() {
        logger.info("Synchronizing all views");

        // Refresh all created views
        // Tree view is integrated with main XSD editor - will be refreshed externally
        if (gridView != null) {
            gridView.refreshView();
        }
        if (sourceView != null) {
            sourceView.refreshView();
        }
        if (umlView != null) {
            umlView.refreshView();
        }

        notifyContentChanged();
    }

    /**
     * Refresh the currently selected view
     */
    public void refreshCurrentView() {
        ViewType currentView = currentViewProperty.get();
        if (currentView != null) {
            switch (currentView) {
                case TREE_VIEW -> { /* Tree view refresh handled externally */ }
                case GRID_VIEW -> {
                    if (gridView != null) gridView.refreshView();
                }
                case SOURCE_VIEW -> {
                    if (sourceView != null) sourceView.refreshView();
                }
                case UML_VIEW -> {
                    if (umlView != null) umlView.refreshView();
                }
            }
        }
    }

    /**
     * Add a synchronization listener
     */
    public void addSynchronizationListener(ViewSynchronizationListener listener) {
        synchronizationListeners.add(listener);
    }

    /**
     * Remove a synchronization listener
     */
    public void removeSynchronizationListener(ViewSynchronizationListener listener) {
        synchronizationListeners.remove(listener);
    }

    // Notification methods
    private void notifySelectionChanged(XsdNodeInfo selectedNode) {
        for (ViewSynchronizationListener listener : synchronizationListeners) {
            try {
                listener.onSelectionChanged(selectedNode);
            } catch (Exception e) {
                logger.error("Error notifying selection change", e);
            }
        }
    }

    private void notifyContentChanged() {
        for (ViewSynchronizationListener listener : synchronizationListeners) {
            try {
                listener.onContentChanged();
            } catch (Exception e) {
                logger.error("Error notifying content change", e);
            }
        }
    }

    private void notifyViewChanged(ViewType newView) {
        for (ViewSynchronizationListener listener : synchronizationListeners) {
            try {
                listener.onViewChanged(newView);
            } catch (Exception e) {
                logger.error("Error notifying view change", e);
            }
        }
    }

    // Property accessors
    public ObjectProperty<XsdNodeInfo> selectedNodeProperty() {
        return selectedNodeProperty;
    }

    public XsdNodeInfo getSelectedNode() {
        return selectedNodeProperty.get();
    }

    public void setSelectedNode(XsdNodeInfo node) {
        selectedNodeProperty.set(node);
    }

    public ObjectProperty<ViewType> currentViewProperty() {
        return currentViewProperty;
    }

    public ViewType getCurrentView() {
        return currentViewProperty.get();
    }

    public XsdDiagramView getTreeView() {
        return treeView;
    }

    public XsdGridView getGridView() {
        return gridView;
    }

    public XsdSourceView getSourceView() {
        return sourceView;
    }

    public XsdUmlView getUmlView() {
        return umlView;
    }
}