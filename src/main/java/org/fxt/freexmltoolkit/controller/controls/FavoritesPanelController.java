package org.fxt.freexmltoolkit.controller.controls;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.XmlUltimateController;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the enhanced Favorites Panel with XMLSpy styling.
 * Manages smart collections, categories, projects, and templates.
 */
public class FavoritesPanelController implements Initializable {

    private static final Logger logger = LogManager.getLogger(FavoritesPanelController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private TextField searchField;
    @FXML
    private MenuButton filterButton;
    @FXML
    private Button addButton;
    @FXML
    private Button syncButton;
    @FXML
    private Button settingsButton;

    @FXML
    private VBox contentArea;
    @FXML
    private TitledPane smartCollectionsPane;
    @FXML
    private TitledPane categoriesPane;
    @FXML
    private TitledPane projectsPane;
    @FXML
    private TitledPane templatesPane;

    @FXML
    private TreeView<FavoriteTreeItem> categoriesTreeView;
    @FXML
    private TreeView<FavoriteTreeItem> projectsTreeView;
    @FXML
    private ListView<FileFavorite> templatesListView;

    @FXML
    private Label recentCountLabel;
    @FXML
    private Label popularCountLabel;
    @FXML
    private Label weekCountLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label countLabel;

    private FavoritesService favoritesService;
    private XmlUltimateController parentController;
    private ObservableList<FileFavorite> allFavorites;
    private ObservableList<FileFavorite> filteredFavorites;

    // Drag and Drop support
    private static final DataFormat FAVORITE_DATA_FORMAT = new DataFormat("application/x-favorite");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        favoritesService = FavoritesService.getInstance();
        allFavorites = FXCollections.observableArrayList();
        filteredFavorites = FXCollections.observableArrayList();

        initializeUI();
        setupEventHandlers();
        loadFavorites();
        setupDragAndDrop();
        startAutoRefresh();
    }

    private void initializeUI() {
        // Initialize TreeViews
        categoriesTreeView.setRoot(new TreeItem<>(new FavoriteTreeItem("All Categories", null)));
        categoriesTreeView.getRoot().setExpanded(true);
        categoriesTreeView.setCellFactory(tv -> new FavoriteTreeCell());

        projectsTreeView.setRoot(new TreeItem<>(new FavoriteTreeItem("All Projects", null)));
        projectsTreeView.getRoot().setExpanded(true);
        projectsTreeView.setCellFactory(tv -> new FavoriteTreeCell());

        // Initialize ListView for templates
        templatesListView.setCellFactory(lv -> new FavoriteListCell());

        // Setup search field
        searchField.textProperty().addListener((obs, oldText, newText) -> filterFavorites(newText));

        // Tooltips are now defined in FXML
    }

    private void setupEventHandlers() {
        // Add button action
        addButton.setOnAction(e -> addCurrentFileToFavorites());

        // Sync button action
        syncButton.setOnAction(e -> syncFavorites());

        // Settings button action
        settingsButton.setOnAction(e -> showSettingsDialog());

        // Filter menu items
        filterButton.getItems().forEach(item ->
                item.setOnAction(e -> applyFilter(((MenuItem) e.getSource()).getText()))
        );

        // Smart collections click handlers
        setupSmartCollectionHandlers();

        // Tree selection handlers
        categoriesTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        filterByCategory(newSelection.getValue());
                    }
                }
        );

        projectsTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        filterByProject(newSelection.getValue());
                    }
                }
        );

        // Double-click to open
        categoriesTreeView.setOnMouseClicked(this::handleTreeDoubleClick);
        projectsTreeView.setOnMouseClicked(this::handleTreeDoubleClick);
        templatesListView.setOnMouseClicked(this::handleListDoubleClick);
    }

    private void setupSmartCollectionHandlers() {
        // Find smart collection items in the pane
        VBox collectionsBox = (VBox) smartCollectionsPane.getContent();
        collectionsBox.getChildren().forEach(node -> {
            if (node instanceof HBox) {
                node.setOnMouseClicked(e -> {
                    String collectionName = getCollectionName((HBox) node);
                    loadSmartCollection(collectionName);
                });
            }
        });
    }

    private String getCollectionName(HBox collectionItem) {
        return collectionItem.getChildren().stream()
                .filter(node -> node instanceof Label && !(node.getId() != null && node.getId().contains("Count")))
                .map(node -> ((Label) node).getText())
                .findFirst()
                .orElse("");
    }

    private void loadFavorites() {
        allFavorites.clear();
        allFavorites.addAll(favoritesService.getAllFavorites());

        updateCategoriesTree();
        updateProjectsTree();
        updateTemplates();
        updateSmartCollectionCounts();
        updateStatusBar();

        logger.info("Loaded {} favorites", allFavorites.size());
    }

    private void updateCategoriesTree() {
        TreeItem<FavoriteTreeItem> root = categoriesTreeView.getRoot();
        root.getChildren().clear();

        Map<String, List<FileFavorite>> byCategory = allFavorites.stream()
                .collect(Collectors.groupingBy(f -> getCategoryForFile(f.getFilePath())));

        byCategory.forEach((category, favorites) -> {
            TreeItem<FavoriteTreeItem> categoryNode = new TreeItem<>(
                    new FavoriteTreeItem(category + " (" + favorites.size() + ")", null)
            );

            // Add individual files
            favorites.forEach(fav -> {
                TreeItem<FavoriteTreeItem> fileNode = new TreeItem<>(
                        new FavoriteTreeItem(getFileName(fav.getFilePath()), fav)
                );
                categoryNode.getChildren().add(fileNode);
            });

            categoryNode.setExpanded(true);
            root.getChildren().add(categoryNode);
        });
    }

    private void updateProjectsTree() {
        TreeItem<FavoriteTreeItem> root = projectsTreeView.getRoot();
        root.getChildren().clear();

        Map<String, List<FileFavorite>> byProject = allFavorites.stream()
                .collect(Collectors.groupingBy(f -> getProjectForFile(f.getFilePath())));

        byProject.forEach((project, favorites) -> {
            TreeItem<FavoriteTreeItem> projectNode = new TreeItem<>(
                    new FavoriteTreeItem(project, null)
            );

            favorites.forEach(fav -> {
                TreeItem<FavoriteTreeItem> fileNode = new TreeItem<>(
                        new FavoriteTreeItem(getFileName(fav.getFilePath()), fav)
                );
                projectNode.getChildren().add(fileNode);
            });

            projectNode.setExpanded(false);
            root.getChildren().add(projectNode);
        });
    }

    private void updateTemplates() {
        List<FileFavorite> templates = allFavorites.stream()
                .filter(f -> f.getCategory() != null && f.getCategory().equals("Template"))
                .collect(Collectors.toList());

        templatesListView.setItems(FXCollections.observableArrayList(templates));
    }

    private void updateSmartCollectionCounts() {
        // Recent files (last 7 days)
        long recentCount = allFavorites.stream()
                .filter(f -> f.getLastAccessed() != null &&
                        f.getLastAccessed().isAfter(LocalDateTime.now().minusDays(7)))
                .count();
        recentCountLabel.setText(String.valueOf(recentCount));

        // Most popular (accessed > 10 times)
        long popularCount = allFavorites.stream()
                .filter(f -> f.getAccessCount() > 10)
                .count();
        popularCountLabel.setText(String.valueOf(popularCount));

        // This week (modified this week)
        long weekCount = allFavorites.stream()
                .filter(f -> f.getDateAdded() != null &&
                        f.getDateAdded().isAfter(LocalDateTime.now().minusWeeks(1)))
                .count();
        weekCountLabel.setText(String.valueOf(weekCount));
    }

    private void updateStatusBar() {
        countLabel.setText(allFavorites.size() + " favorites");
        statusLabel.setText("Ready");
    }

    private void filterFavorites(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredFavorites.clear();
            filteredFavorites.addAll(allFavorites);
        } else {
            String search = searchText.toLowerCase();
            filteredFavorites = allFavorites.stream()
                    .filter(f -> f.getFilePath().toLowerCase().contains(search) ||
                            (f.getAlias() != null && f.getAlias().toLowerCase().contains(search)) ||
                            (f.getCategory() != null && f.getCategory().toLowerCase().contains(search)))
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));
        }

        // Refresh views
        updateCategoriesTree();
        updateProjectsTree();
    }

    private void applyFilter(String filterType) {
        filterButton.setText(filterType);

        switch (filterType) {
            case "XML Documents":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getFilePath().endsWith(".xml"))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            case "XSD Schemas":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getFilePath().endsWith(".xsd"))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            case "XSLT Stylesheets":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getFilePath().endsWith(".xsl") || f.getFilePath().endsWith(".xslt"))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            case "Recent":
                loadSmartCollection("Recently Used");
                break;
            case "Modified":
                filteredFavorites = allFavorites.stream()
                        .sorted(Comparator.comparing(FileFavorite::getLastAccessed,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            default:
                filteredFavorites.clear();
                filteredFavorites.addAll(allFavorites);
        }

        updateCategoriesTree();
        updateProjectsTree();
    }

    private void loadSmartCollection(String collectionName) {
        statusLabel.setText("Loading " + collectionName + "...");

        switch (collectionName) {
            case "Recently Used":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getLastAccessed() != null &&
                                f.getLastAccessed().isAfter(LocalDateTime.now().minusDays(7)))
                        .sorted(Comparator.comparing(FileFavorite::getLastAccessed,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            case "Most Popular":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getAccessCount() > 10)
                        .sorted(Comparator.comparing(FileFavorite::getAccessCount).reversed())
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
            case "This Week":
                filteredFavorites = allFavorites.stream()
                        .filter(f -> f.getDateAdded() != null &&
                                f.getDateAdded().isAfter(LocalDateTime.now().minusWeeks(1)))
                        .sorted(Comparator.comparing(FileFavorite::getDateAdded,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList));
                break;
        }

        updateCategoriesTree();
        statusLabel.setText(collectionName + " loaded");
    }

    private void filterByCategory(FavoriteTreeItem item) {
        if (item.favorite() != null) {
            openFavorite(item.favorite());
        }
    }

    private void filterByProject(FavoriteTreeItem item) {
        if (item.favorite() != null) {
            openFavorite(item.favorite());
        }
    }

    private void handleTreeDoubleClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            TreeView<FavoriteTreeItem> tree = (TreeView<FavoriteTreeItem>) event.getSource();
            TreeItem<FavoriteTreeItem> selected = tree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().favorite() != null) {
                openFavorite(selected.getValue().favorite());
            }
        }
    }

    private void handleListDoubleClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            FileFavorite selected = templatesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openFavorite(selected);
            }
        }
    }

    private void openFavorite(FileFavorite favorite) {
        if (parentController != null) {
            File file = new File(favorite.getFilePath());
            if (file.exists()) {
                parentController.loadFileToNewTab(file);

                // Update access count and time
                favorite.setAccessCount(favorite.getAccessCount() + 1);
                favorite.setLastAccessed(LocalDateTime.now());
                favoritesService.updateFavorite(favorite);

                statusLabel.setText("Opened: " + file.getName());
            } else {
                showAlert(Alert.AlertType.WARNING, "File Not Found",
                        "The file no longer exists: " + favorite.getFilePath());
            }
        }
    }

    private void addCurrentFileToFavorites() {
        if (parentController != null) {
            File currentFile = parentController.getCurrentFile();
            if (currentFile != null) {
                // Create dialog for adding favorite
                TextInputDialog dialog = new TextInputDialog(currentFile.getName());
                dialog.setTitle("Add to Favorites");
                dialog.setHeaderText("Add " + currentFile.getName() + " to favorites");
                dialog.setContentText("Enter alias (optional):");

                dialog.showAndWait().ifPresent(alias -> {
                    FileFavorite favorite = new FileFavorite(
                            alias.isEmpty() ? currentFile.getName() : alias,
                            currentFile.getAbsolutePath(),
                            getCategoryForFile(currentFile.getAbsolutePath())
                    );

                    favoritesService.addFavorite(favorite);
                    loadFavorites();

                    statusLabel.setText("Added: " + currentFile.getName());
                });
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No File",
                        "Please open a file first before adding to favorites");
            }
        }
    }

    private void syncFavorites() {
        statusLabel.setText("Synchronizing favorites...");

        // For now, just reload from disk
        loadFavorites();

        Platform.runLater(() -> {
            statusLabel.setText("Sync completed");
        });
    }

    private void showSettingsDialog() {
        showAlert(Alert.AlertType.INFORMATION, "Settings",
                "Favorites settings will be available in the next version");
    }

    private void setupDragAndDrop() {
        // Enable drag from tree views
        setupTreeDragAndDrop(categoriesTreeView);
        setupTreeDragAndDrop(projectsTreeView);

        // Enable drag from list view
        templatesListView.setOnDragDetected(event -> {
            FileFavorite selected = templatesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Dragboard dragboard = templatesListView.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(selected.getFilePath());
                dragboard.setContent(content);
                event.consume();
            }
        });

        // Enable drop on content area for reordering
        contentArea.setOnDragOver(event -> {
            if (event.getGestureSource() != contentArea &&
                    event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                contentArea.getStyleClass().add("drag-over");
            }
            event.consume();
        });

        contentArea.setOnDragExited(event -> {
            contentArea.getStyleClass().remove("drag-over");
            event.consume();
        });

        contentArea.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;

            if (dragboard.hasString()) {
                // Handle dropped file
                String filePath = dragboard.getString();
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupTreeDragAndDrop(TreeView<FavoriteTreeItem> treeView) {
        treeView.setOnDragDetected(event -> {
            TreeItem<FavoriteTreeItem> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().favorite() != null) {
                Dragboard dragboard = treeView.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(selected.getValue().favorite().getFilePath());
                dragboard.setContent(content);
                event.consume();
            }
        });
    }

    private void startAutoRefresh() {
        // Auto-refresh every 30 seconds to update smart collections
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000);
                    Platform.runLater(this::updateSmartCollectionCounts);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private String getCategoryForFile(String filePath) {
        if (filePath.endsWith(".xml")) return "XML Documents";
        if (filePath.endsWith(".xsd")) return "XSD Schemas";
        if (filePath.endsWith(".xsl") || filePath.endsWith(".xslt")) return "XSLT Stylesheets";
        if (filePath.endsWith(".xq") || filePath.endsWith(".xquery")) return "XQuery Scripts";
        return "Other";
    }

    private String getProjectForFile(String filePath) {
        // Extract project name from path (simplified logic)
        File file = new File(filePath);
        File parent = file.getParentFile();

        while (parent != null) {
            if (new File(parent, ".git").exists() ||
                    new File(parent, "pom.xml").exists() ||
                    new File(parent, "build.gradle").exists()) {
                return parent.getName();
            }
            parent = parent.getParentFile();
        }

        return "No Project";
    }

    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void setParentController(XmlUltimateController controller) {
        this.parentController = controller;
    }

    // Inner classes for TreeView items
        private record FavoriteTreeItem(String label, FileFavorite favorite) {

        @Override
            public String toString() {
                return label;
            }
        }

    // Custom TreeCell with icons and styling
    private class FavoriteTreeCell extends TreeCell<FavoriteTreeItem> {
        @Override
        protected void updateItem(FavoriteTreeItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.label());

                if (item.favorite() != null) {
                    // File node - add appropriate icon
                    FontIcon icon = createIconForFile(item.favorite().getFilePath());
                    setGraphic(icon);

                    // Add context menu
                    setContextMenu(createContextMenu(item.favorite()));
                } else {
                    // Category/Project node
                    FontIcon icon = new FontIcon("bi-folder");
                    icon.setIconColor(Color.web("#d4a147"));
                    setGraphic(icon);
                }
            }
        }

        private FontIcon createIconForFile(String filePath) {
            FontIcon icon;
            if (filePath.endsWith(".xml")) {
                icon = new FontIcon("bi-file-earmark-code");
                icon.setIconColor(Color.web("#4a90e2"));
            } else if (filePath.endsWith(".xsd")) {
                icon = new FontIcon("bi-diagram-3");
                icon.setIconColor(Color.web("#d4a147"));
            } else if (filePath.endsWith(".xsl") || filePath.endsWith(".xslt")) {
                icon = new FontIcon("bi-arrow-repeat");
                icon.setIconColor(Color.web("#e27429"));
            } else {
                icon = new FontIcon("bi-file");
                icon.setIconColor(Color.web("#6c757d"));
            }
            icon.setIconSize(14);
            return icon;
        }
    }

    // Custom ListCell for templates
    private class FavoriteListCell extends ListCell<FileFavorite> {
        @Override
        protected void updateItem(FileFavorite item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox card = createFavoriteCard(item);
                setGraphic(card);
            }
        }

        private VBox createFavoriteCard(FileFavorite favorite) {
            VBox card = new VBox(4);
            card.getStyleClass().add("favorite-card");

            // Title
            Label title = new Label(favorite.getAlias());
            title.getStyleClass().add("favorite-card-title");

            // Path
            Label path = new Label(favorite.getFilePath());
            path.getStyleClass().add("favorite-card-path");

            // Metadata
            HBox metadata = new HBox(8);
            if (favorite.getLastAccessed() != null) {
                Label accessed = new Label("Last: " + DATE_FORMAT.format(favorite.getLastAccessed()));
                accessed.getStyleClass().add("favorite-card-metadata");
                metadata.getChildren().add(accessed);
            }

            Label count = new Label("Used: " + favorite.getAccessCount() + " times");
            count.getStyleClass().add("favorite-card-metadata");
            metadata.getChildren().add(count);

            card.getChildren().addAll(title, path, metadata);

            // Add context menu
            card.setOnContextMenuRequested(e -> {
                createContextMenu(favorite).show(card, e.getScreenX(), e.getScreenY());
            });

            return card;
        }
    }

    private ContextMenu createContextMenu(FileFavorite favorite) {
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem("Open");
        open.setGraphic(new FontIcon("bi-folder2-open"));
        open.setOnAction(e -> openFavorite(favorite));

        MenuItem rename = new MenuItem("Rename");
        rename.setGraphic(new FontIcon("bi-pencil"));
        rename.setOnAction(e -> renameFavorite(favorite));

        MenuItem remove = new MenuItem("Remove");
        remove.setGraphic(new FontIcon("bi-trash"));
        remove.setOnAction(e -> removeFavorite(favorite));

        MenuItem properties = new MenuItem("Properties");
        properties.setGraphic(new FontIcon("bi-info-circle"));
        properties.setOnAction(e -> showProperties(favorite));

        menu.getItems().addAll(open, rename, new SeparatorMenuItem(), remove, properties);

        return menu;
    }

    private void renameFavorite(FileFavorite favorite) {
        TextInputDialog dialog = new TextInputDialog(favorite.getAlias());
        dialog.setTitle("Rename Favorite");
        dialog.setHeaderText("Rename " + favorite.getAlias());
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            favorite.setAlias(newName);
            favoritesService.updateFavorite(favorite);
            loadFavorites();
        });
    }

    private void removeFavorite(FileFavorite favorite) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Favorite");
        confirm.setContentText("Remove " + favorite.getAlias() + " from favorites?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                favoritesService.removeFavorite(favorite);
                loadFavorites();
            }
        });
    }

    private void showProperties(FileFavorite favorite) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Favorite Properties");
        dialog.setHeaderText(favorite.getAlias());

        String content = String.format(
                "Path: %s\nCategory: %s\nAdded: %s\nLast Accessed: %s\nAccess Count: %d",
                favorite.getFilePath(),
                favorite.getCategory(),
                favorite.getDateAdded() != null ? DATE_FORMAT.format(favorite.getDateAdded()) : "Unknown",
                favorite.getLastAccessed() != null ? DATE_FORMAT.format(favorite.getLastAccessed()) : "Never",
                favorite.getAccessCount()
        );

        dialog.setContentText(content);
        dialog.showAndWait();
    }
}