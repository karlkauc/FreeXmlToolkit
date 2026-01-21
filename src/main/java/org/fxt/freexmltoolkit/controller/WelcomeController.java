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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.dashboard.FeatureProgressGrid;
import org.fxt.freexmltoolkit.controls.dashboard.StatisticsCard;
import org.fxt.freexmltoolkit.controls.dashboard.TipsBanner;
import org.fxt.freexmltoolkit.controls.dashboard.TrendSparkline;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.domain.statistics.UsageStatistics;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.service.UsageTrackingService;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

/**
 * Controller for the Welcome/Dashboard page.
 * Provides quick access to main features and recent files.
 */
public class WelcomeController {

    private static final Logger logger = LogManager.getLogger(WelcomeController.class);

    /**
     * Creates a new WelcomeController instance.
     * The controller is initialized by the FXML loader.
     */
    public WelcomeController() {
        // Default constructor for FXML loader
    }

    private PropertiesService propertiesService;
    private UpdateCheckService updateCheckService;
    private Properties properties;
    private MainController parentController;
    private String latestVersionUrl = "https://github.com/karlkauc/FreeXmlToolkit/releases/latest";

    // FXML Components
    @FXML
    private AnchorPane rootPane;
    @FXML
    private HBox versionUpdate;
    @FXML
    private Label durationLabel, versionLabel, welcomeLabel, versionBadge;
    @FXML
    private Hyperlink updateLink;
    @FXML
    private VBox dragDropArea;

    // Recent Files Grid
    @FXML
    private FlowPane recentFilesGrid;
    @FXML
    private VBox emptyFilesPlaceholder;

    // Dashboard/Statistics Components
    @FXML
    private TitledPane statisticsPane;
    @FXML
    private HBox statisticsCardsContainer;
    @FXML
    private VBox statisticsDashboard;

    private StatisticsCard validationsCard;
    private StatisticsCard errorsFixedCard;
    private StatisticsCard transformationsCard;
    private StatisticsCard productivityCard;
    private TrendSparkline trendSparkline;
    private FeatureProgressGrid featureGrid;
    private TipsBanner tipsBanner;

    private final ObservableList<RecentFileEntry> recentFiles = FXCollections.observableArrayList();

    /**
     * Sets the parent controller for navigation and file handling.
     *
     * @param parentController the main controller instance
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
        // Load recent files when parent controller is set
        Platform.runLater(this::loadRecentFiles);
    }

    /**
     * Initializes the controller after FXML loading.
     * Sets up services, UI components, drag-and-drop, and the statistics dashboard.
     */
    @FXML
    public void initialize() {
        // Initialize services
        propertiesService = ServiceRegistry.get(PropertiesService.class);
        updateCheckService = ServiceRegistry.get(UpdateCheckService.class);

        properties = propertiesService.loadProperties();
        logger.debug("Properties: {}", properties);

        // Set current version
        String currentVersion = updateCheckService.getCurrentVersion();
        versionLabel.setText("Version: " + currentVersion);
        if (versionBadge != null) {
            versionBadge.setText("v" + currentVersion);
        }

        // Set welcome message with user name if available
        String userName = properties.getProperty("user.name", "").trim();
        if (!userName.isEmpty()) {
            welcomeLabel.setText("Welcome back, " + userName);
        } else {
            welcomeLabel.setText("Welcome back, Developer");
        }

        // Hide update notification initially
        versionUpdate.setVisible(false);
        versionUpdate.setManaged(false);

        // Check for updates asynchronously
        checkForUpdates();

        // Set usage duration
        int oldSeconds = Integer.parseInt(properties.getProperty("usageDuration", "0"));
        durationLabel.setText(oldSeconds > 0 ? formatSecondsHumanReadable(oldSeconds) : "You are here the first time!");

        // Set up drag and drop
        setupDragAndDrop();

        // Setup recent files grid
        setupRecentFilesGrid();

        // Initialize statistics dashboard
        initializeStatisticsDashboard();
    }

    /**
     * Initializes the statistics dashboard with usage tracking data.
     */
    private void initializeStatisticsDashboard() {
        try {
            UsageTrackingService trackingService = ServiceRegistry.get(UsageTrackingService.class);
            if (trackingService == null || !trackingService.isTrackingEnabled()) {
                hideStatisticsDashboard();
                return;
            }

            // Create dashboard components programmatically if container exists
            if (statisticsDashboard != null) {
                createStatisticsDashboard(trackingService);
            } else {
                logger.debug("Statistics dashboard container not found in FXML");
            }
        } catch (Exception e) {
            logger.debug("Statistics dashboard not available: {}", e.getMessage());
            hideStatisticsDashboard();
        }
    }

    /**
     * Creates and populates the statistics dashboard.
     */
    private void createStatisticsDashboard(UsageTrackingService trackingService) {
        UsageStatistics stats = trackingService.getStatistics();

        // Clear existing content
        statisticsDashboard.getChildren().clear();
        statisticsDashboard.setSpacing(16);

        // Statistics Cards Row
        HBox cardsRow = new HBox(12);
        cardsRow.setAlignment(Pos.CENTER);

        validationsCard = StatisticsCard.createValidationsCard(
            stats.getFilesValidated(),
            trackingService.getWeeklyChange("validations")
        );

        errorsFixedCard = StatisticsCard.createErrorsFixedCard(
            stats.getErrorsCorrected(),
            trackingService.getWeeklyChange("errors_fixed")
        );

        transformationsCard = StatisticsCard.createTransformationsCard(
            stats.getTransformationsPerformed(),
            trackingService.getWeeklyChange("transformations")
        );

        productivityCard = StatisticsCard.createProductivityCard(
            trackingService.getProductivityScore(),
            trackingService.getProductivityLevel()
        );

        cardsRow.getChildren().addAll(validationsCard, errorsFixedCard, transformationsCard, productivityCard);

        // Trend Sparkline
        trendSparkline = new TrendSparkline();
        trendSparkline.updateData(trackingService.getDailyStats(7));

        // Feature Progress Grid
        featureGrid = new FeatureProgressGrid();
        featureGrid.updateFeatures(trackingService.getAllFeatures());
        featureGrid.setOnFeatureClick(pageId -> {
            if (parentController != null) {
                parentController.navigateToPage(pageId);
            }
        });

        // Tips Banner
        tipsBanner = new TipsBanner();
        tipsBanner.setTips(trackingService.getRelevantTips());
        tipsBanner.setOnActionClick(pageId -> {
            if (parentController != null) {
                parentController.navigateToPage(pageId);
            }
        });

        // Add all components
        statisticsDashboard.getChildren().addAll(cardsRow, trendSparkline, featureGrid, tipsBanner);

        logger.debug("Statistics dashboard initialized with {} validations, score: {}",
            stats.getFilesValidated(), trackingService.getProductivityScore());
    }

    /**
     * Hides the statistics dashboard when tracking is disabled.
     */
    private void hideStatisticsDashboard() {
        if (statisticsPane != null) {
            statisticsPane.setVisible(false);
            statisticsPane.setManaged(false);
        }
        if (statisticsDashboard != null) {
            statisticsDashboard.setVisible(false);
            statisticsDashboard.setManaged(false);
        }
    }

    /**
     * Sets up the Recent Files FlowPane grid.
     */
    private void setupRecentFilesGrid() {
        if (recentFilesGrid == null) {
            logger.warn("Recent files grid not available");
        }
        // Grid will be populated in loadRecentFiles()
    }

    /**
     * Creates a file card for the grid view.
     */
    private VBox createFileCard(RecentFileEntry entry) {
        VBox card = new VBox(6);
        card.getStyleClass().add("file-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefWidth(180);
        card.setMinWidth(180);
        card.setMaxWidth(180);
        card.setPrefHeight(100);
        card.setMinHeight(100);
        card.setMaxHeight(100);
        card.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand;");

        // Icon wrapper
        StackPane iconWrapper = new StackPane();
        iconWrapper.setMinSize(36, 36);
        iconWrapper.setMaxSize(36, 36);
        iconWrapper.setStyle("-fx-background-color: " + getBackgroundColorForType(entry.fileType()) + "; -fx-background-radius: 6;");

        FontIcon icon = new FontIcon(getIconForType(entry.fileType()));
        icon.setIconSize(18);
        icon.setIconColor(Color.web(getColorForType(entry.fileType())));
        iconWrapper.getChildren().add(icon);

        // File name
        Label fileName = new Label(entry.fileName());
        fileName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        fileName.setWrapText(true);
        fileName.setMaxWidth(156);

        // File type and time
        Label metaInfo = new Label(entry.fileType() + " • " + entry.lastModified());
        metaInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        card.getChildren().addAll(iconWrapper, fileName, metaInfo);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand;"));

        // Click to open
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                openRecentFile(entry);
            }
        });

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.setGraphic(new FontIcon("bi-folder2-open"));
        openItem.setOnAction(e -> openRecentFile(entry));

        MenuItem removeItem = new MenuItem("Remove from list");
        removeItem.setGraphic(new FontIcon("bi-x"));
        removeItem.setOnAction(e -> {
            recentFiles.remove(entry);
            recentFilesGrid.getChildren().remove(card);
            updateEmptyState();
        });

        contextMenu.getItems().addAll(openItem, new SeparatorMenuItem(), removeItem);
        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }

    /**
     * Gets the background color for a file type icon wrapper.
     */
    private String getBackgroundColorForType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "xml doc", "xml document" -> "#ffedd5";
            case "xsd schema" -> "#f3e8ff";
            case "xslt stylesheet" -> "#dbeafe";
            case "schematron" -> "#fce7f3";
            case "maven project" -> "#ffedd5";
            default -> "#f3f4f6";
        };
    }

    /**
     * Updates the empty state visibility.
     */
    private void updateEmptyState() {
        boolean isEmpty = recentFilesGrid.getChildren().isEmpty();
        if (emptyFilesPlaceholder != null) {
            emptyFilesPlaceholder.setVisible(isEmpty);
            emptyFilesPlaceholder.setManaged(isEmpty);
        }
    }

    /**
     * Gets the icon literal for a file type.
     */
    private String getIconForType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "xml doc", "xml document" -> "bi-file-earmark-code";
            case "xsd schema" -> "bi-diagram-3";
            case "xslt stylesheet" -> "bi-arrow-repeat";
            case "schematron" -> "bi-shield-check";
            case "maven project" -> "bi-box";
            default -> "bi-file-earmark";
        };
    }

    /**
     * Gets the color for a file type.
     */
    private String getColorForType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "xml doc", "xml document" -> "#ea580c";
            case "xsd schema" -> "#9333ea";
            case "xslt stylesheet" -> "#2563eb";
            case "schematron" -> "#be185d";
            case "maven project" -> "#ea580c";
            default -> "#6b7280";
        };
    }

    /**
     * Loads recent files from the properties service and populates the grid.
     */
    private void loadRecentFiles() {
        if (parentController == null || propertiesService == null) {
            return;
        }

        recentFiles.clear();
        if (recentFilesGrid != null) {
            recentFilesGrid.getChildren().clear();
        }

        List<File> lastOpenFiles = propertiesService.getLastOpenFiles();

        for (File file : lastOpenFiles) {
            if (file.exists()) {
                RecentFileEntry entry = createRecentFileEntry(file);
                recentFiles.add(entry);
                if (recentFilesGrid != null) {
                    recentFilesGrid.getChildren().add(createFileCard(entry));
                }
            }
        }

        updateEmptyState();
        logger.debug("Loaded {} recent files", recentFiles.size());
    }

    /**
     * Creates a RecentFileEntry from a File.
     */
    private RecentFileEntry createRecentFileEntry(File file) {
        String fileName = file.getName();
        String fileType = determineFileType(file);
        String path = shortenPath(file.getParent(), 25);
        String lastModified = getRelativeTime(file);

        return new RecentFileEntry(fileName, fileType, path, lastModified, file.getAbsolutePath());
    }

    /**
     * Determines the file type based on extension.
     */
    private String determineFileType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xsd")) {
            return "XSD Schema";
        } else if (name.endsWith(".xslt") || name.endsWith(".xsl")) {
            return "XSLT Stylesheet";
        } else if (name.endsWith(".sch")) {
            return "Schematron";
        } else if (name.equals("pom.xml")) {
            return "Maven Project";
        } else if (name.endsWith(".xml")) {
            return "XML Doc";
        }
        return "File";
    }

    /**
     * Shortens a path to fit the display.
     */
    private String shortenPath(String path, int maxLength) {
        if (path == null) {
            return "";
        }
        if (path.length() <= maxLength) {
            return path;
        }
        return path.substring(0, maxLength - 3) + "...";
    }

    /**
     * Gets the relative time since file was modified.
     */
    private String getRelativeTime(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            Instant lastModified = attrs.lastModifiedTime().toInstant();
            LocalDateTime modTime = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
            LocalDateTime now = LocalDateTime.now();

            long minutes = ChronoUnit.MINUTES.between(modTime, now);
            long hours = ChronoUnit.HOURS.between(modTime, now);
            long days = ChronoUnit.DAYS.between(modTime, now);

            if (minutes < 60) {
                return minutes + "m ago";
            } else if (hours < 24) {
                return hours + "h ago";
            } else if (days == 1) {
                return "Yesterday";
            } else if (days < 7) {
                return days + "d ago";
            } else {
                return modTime.format(DateTimeFormatter.ofPattern("MMM d"));
            }
        } catch (IOException e) {
            return "Unknown";
        }
    }

    /**
     * Opens a recent file in the appropriate editor.
     */
    private void openRecentFile(RecentFileEntry entry) {
        if (parentController == null) {
            return;
        }

        File file = new File(entry.fullPath());
        if (!file.exists()) {
            showError("File not found", "The file no longer exists: " + entry.fullPath());
            recentFiles.remove(entry);
            return;
        }

        DragDropService.FileType fileType = DragDropService.getFileType(file);
        switch (fileType) {
            case XSD -> parentController.switchToXsdViewAndLoadFile(file);
            case SCHEMATRON -> parentController.switchToSchematronViewAndLoadFile(file);
            case XSLT -> parentController.switchToXsltDeveloperAndLoadFile(file);
            default -> parentController.switchToXmlViewAndLoadFile(file);
        }

        parentController.addFileToRecentFiles(file);
    }

    /**
     * Set up drag and drop functionality for the welcome page.
     */
    private void setupDragAndDrop() {
        if (rootPane == null) {
            logger.warn("Cannot setup drag and drop: rootPane is null");
            return;
        }

        // Setup drag and drop on the entire pane
        DragDropService.setupDragDrop(rootPane, DragDropService.ALL_XML_RELATED, this::handleDroppedFiles);

        // Add visual feedback for drag area
        if (dragDropArea != null) {
            dragDropArea.setOnDragOver(event -> {
                if (event.getDragboard().hasFiles()) {
                    dragDropArea.setStyle("-fx-border-color: #2563eb; -fx-border-width: 2; -fx-border-style: dashed; -fx-background-color: #eff6ff;");
                }
                event.consume();
            });

            dragDropArea.setOnDragExited(event -> {
                dragDropArea.setStyle("");
                event.consume();
            });
        }

        logger.debug("Drag and drop initialized for Welcome page");
    }

    /**
     * Handle files dropped on the welcome page.
     */
    private void handleDroppedFiles(List<File> files) {
        if (parentController == null) {
            logger.warn("Cannot route files: parentController is null");
            return;
        }

        logger.info("Files dropped on Welcome page: {} file(s)", files.size());

        for (File file : files) {
            DragDropService.FileType fileType = DragDropService.getFileType(file);
            logger.debug("Routing file '{}' with type: {}", file.getName(), fileType);

            switch (fileType) {
                case XSD -> parentController.switchToXsdViewAndLoadFile(file);
                case SCHEMATRON -> parentController.switchToSchematronViewAndLoadFile(file);
                case XSLT -> parentController.switchToXsltDeveloperAndLoadFile(file);
                case WSDL, XML -> parentController.switchToXmlViewAndLoadFile(file);
                default -> {
                    logger.warn("Unknown file type for '{}', opening in XML editor", file.getName());
                    parentController.switchToXmlViewAndLoadFile(file);
                }
            }

            parentController.addFileToRecentFiles(file);
        }

        // Refresh recent files list
        Platform.runLater(this::loadRecentFiles);
    }

    /**
     * Checks for updates asynchronously.
     */
    private void checkForUpdates() {
        if (!updateCheckService.isUpdateCheckEnabled()) {
            logger.debug("Update check is disabled");
            return;
        }

        updateCheckService.checkForUpdates()
                .thenAccept(this::handleUpdateInfo)
                .exceptionally(ex -> {
                    logger.warn("Failed to check for updates: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Handles update information.
     */
    private void handleUpdateInfo(UpdateInfo updateInfo) {
        Platform.runLater(() -> {
            if (updateInfo.updateAvailable()) {
                logger.info("Update available: {} -> {}", updateInfo.currentVersion(), updateInfo.latestVersion());

                versionLabel.setText(String.format("Version: %s (Update available: %s)",
                        updateInfo.currentVersion(), updateInfo.latestVersion()));

                if (updateInfo.downloadUrl() != null) {
                    latestVersionUrl = updateInfo.downloadUrl();
                }

                versionUpdate.setVisible(true);
                versionUpdate.setManaged(true);
            } else {
                logger.debug("No update available. Current version: {}", updateInfo.currentVersion());
                versionLabel.setText("Version: " + updateInfo.currentVersion() + " (up to date)");
            }
        });
    }

    // ========== Action Methods ==========

    /**
     * Opens the update page in the default browser.
     */
    @FXML
    public void openUpdatePage() {
        try {
            Desktop.getDesktop().browse(URI.create(latestVersionUrl));
        } catch (IOException e) {
            logger.error("Failed to open update page: {}", e.getMessage());
        }
    }

    /**
     * Creates a new XML file by navigating to the XML editor.
     */
    @FXML
    public void createNewXmlFile() {
        // Navigate to XML editor to create a new file
        navigateTo("xmlUltimate");
    }

    /**
     * Opens the XML editor page.
     */
    @FXML
    public void openXmlEditor() {
        navigateTo("xmlUltimate");
    }

    /**
     * Opens the XML editor with formatting capabilities.
     */
    @FXML
    public void openXmlEditorAndFormat() {
        // Navigate to XML editor - format functionality is available there
        navigateTo("xmlUltimate");
    }

    /**
     * Opens the XSD tools page.
     */
    @FXML
    public void openXsdTools() {
        navigateTo("xsd");
    }

    /**
     * Opens the Schematron validation page.
     */
    @FXML
    public void openSchematron() {
        navigateTo("schematron");
    }

    /**
     * Opens the JSON editor page.
     */
    @FXML
    public void openJsonEditor() {
        navigateTo("json");
    }

    /**
     * Opens the XSLT Developer page.
     */
    @FXML
    public void openXsltDeveloper() {
        navigateTo("xsltDeveloper");
    }

    /**
     * Opens the FOP (PDF generation) page.
     */
    @FXML
    public void openFop() {
        navigateTo("fop");
    }

    /**
     * Opens the XML signature page.
     */
    @FXML
    public void openSignature() {
        navigateTo("signature");
    }

    /**
     * Opens the Schema Generator page.
     */
    @FXML
    public void openSchemaGenerator() {
        navigateTo("schemaGenerator");
    }

    /**
     * Opens the help page.
     */
    @FXML
    public void openHelp() {
        navigateTo("help");
    }

    /**
     * Opens the XSD validation page.
     */
    @FXML
    public void openValidation() {
        navigateTo("xsdValidation");
    }

    /**
     * Opens the XML compare functionality.
     */
    @FXML
    public void openCompare() {
        // Compare functionality - navigate to XML editor which has compare features
        navigateTo("xmlUltimate");
    }

    /**
     * Opens the recent files dialog via the settings page.
     */
    @FXML
    public void openRecentFilesDialog() {
        // Show settings page where recent files can be managed
        navigateTo("settings");
    }

    /**
     * Opens the changelog page in the default browser.
     */
    @FXML
    public void openChangelog() {
        try {
            Desktop.getDesktop().browse(URI.create("https://github.com/karlkauc/FreeXmlToolkit/releases"));
        } catch (IOException e) {
            logger.error("Failed to open changelog: {}", e.getMessage());
        }
    }

    /**
     * Navigates to a page using the parent controller.
     */
    private void navigateTo(String pageId) {
        if (parentController != null) {
            parentController.navigateToPage(pageId);
        } else {
            logger.warn("Cannot navigate: parentController is null");
        }
    }

    private String formatSecondsHumanReadable(int seconds) {
        logger.debug("Format: {}", seconds);
        return LocalTime.MIN.plusSeconds(seconds).toString();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Record for recent file entries.
     * @param fileName The name of the file
     * @param fileType The type of the file
     * @param path The path to the file
     * @param lastModified The last modified date
     * @param fullPath The full path to the file
     */
    public record RecentFileEntry(String fileName, String fileType, String path, String lastModified, String fullPath) {
    }
}
