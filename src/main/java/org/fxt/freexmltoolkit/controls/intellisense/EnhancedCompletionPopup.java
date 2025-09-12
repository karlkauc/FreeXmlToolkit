package org.fxt.freexmltoolkit.controls.intellisense;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Enhanced completion popup with preview panel and documentation.
 * Features:
 * - Rich visual completion items with icons
 * - Live XML preview
 * - XSD documentation display
 * - Fuzzy search support
 * - Type-aware color coding
 */
public class EnhancedCompletionPopup {

    private static final Logger logger = LogManager.getLogger(EnhancedCompletionPopup.class);

    private final Popup popup;
    private final VBox mainContainer;
    private final ListView<CompletionItem> completionListView;
    private final WebView previewPane;
    private final WebView documentationPane;
    private final TextField searchField;
    private final Label statusLabel;

    private final ObservableList<CompletionItem> allItems;
    private final ObservableList<CompletionItem> filteredItems;
    private Consumer<CompletionItem> onItemSelected;
    // private CodeArea codeArea; // Not needed for demo

    // Styling constants
    private static final String POPUP_STYLE =
            "-fx-background-color: white; " +
                    "-fx-border-color: #4a90e2; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 2, 2);";

    public EnhancedCompletionPopup() {
        this.popup = new Popup();
        this.mainContainer = new VBox();
        this.completionListView = new ListView<>();
        this.previewPane = new WebView();
        this.documentationPane = new WebView();
        this.searchField = new TextField();
        this.statusLabel = new Label();

        this.allItems = FXCollections.observableArrayList();
        this.filteredItems = FXCollections.observableArrayList();

        initializeUI();
        setupEventHandlers();
    }

    private void initializeUI() {
        // Main container setup
        mainContainer.setPrefWidth(800);
        mainContainer.setPrefHeight(500);
        mainContainer.setStyle(POPUP_STYLE);
        mainContainer.setSpacing(0);

        // Header with search
        HBox header = createHeader();

        // Main content area with split panes
        SplitPane contentSplitPane = new SplitPane();
        contentSplitPane.setDividerPositions(0.4, 0.7);

        // Left: Completion list
        VBox leftPane = createCompletionListPane();

        // Middle: Preview pane
        VBox middlePane = createPreviewPane();

        // Right: Documentation pane
        VBox rightPane = createDocumentationPane();

        contentSplitPane.getItems().addAll(leftPane, middlePane, rightPane);
        VBox.setVgrow(contentSplitPane, Priority.ALWAYS);

        // Footer with status
        HBox footer = createFooter();

        mainContainer.getChildren().addAll(header, contentSplitPane, footer);
        popup.getContent().add(mainContainer);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
    }

    private HBox createHeader() {
        HBox header = new HBox(10);
        header.setPadding(new Insets(8));
        header.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Search icon
        FontIcon searchIcon = new FontIcon("bi-search");
        searchIcon.setIconSize(14);
        searchIcon.setIconColor(Color.web("#6c757d"));

        // Search field
        searchField.setPromptText("Type to filter...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-background-color: white; -fx-border-color: #4a90e2; " +
                "-fx-border-radius: 4; -fx-background-radius: 4;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Filter toggle buttons
        ToggleGroup filterGroup = new ToggleGroup();
        ToggleButton allButton = createFilterButton("All", filterGroup, true);
        ToggleButton elementsButton = createFilterButton("Elements", filterGroup, false);
        ToggleButton attributesButton = createFilterButton("Attributes", filterGroup, false);

        header.getChildren().addAll(searchIcon, searchField,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                allButton, elementsButton, attributesButton);

        return header;
    }

    private ToggleButton createFilterButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setStyle("-fx-background-color: " + (selected ? "#4a90e2" : "white") + "; " +
                "-fx-text-fill: " + (selected ? "white" : "#4a90e2") + "; " +
                "-fx-border-color: #4a90e2; -fx-border-radius: 4; -fx-background-radius: 4;");

        button.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            button.setStyle("-fx-background-color: " + (isSelected ? "#4a90e2" : "white") + "; " +
                    "-fx-text-fill: " + (isSelected ? "white" : "#4a90e2") + "; " +
                    "-fx-border-color: #4a90e2; -fx-border-radius: 4; -fx-background-radius: 4;");
            if (isSelected) {
                filterItems(text.toLowerCase());
            }
        });

        return button;
    }

    private VBox createCompletionListPane() {
        VBox pane = new VBox();
        pane.setPadding(new Insets(5));

        // List view setup
        completionListView.setCellFactory(lv -> new CompletionItemCell());
        completionListView.setItems(filteredItems);
        VBox.setVgrow(completionListView, Priority.ALWAYS);

        pane.getChildren().add(completionListView);

        return pane;
    }

    private VBox createPreviewPane() {
        VBox pane = new VBox(5);
        pane.setPadding(new Insets(5));

        // Title
        Label titleLabel = new Label("Preview");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web("#2c5aa0"));

        // WebView for preview
        previewPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        previewPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        // Initial content
        updatePreview("<div style='font-family: monospace; color: #666; padding: 10px;'>" +
                "Select an item to see preview...</div>");

        pane.getChildren().addAll(titleLabel, previewPane);

        return pane;
    }

    private VBox createDocumentationPane() {
        VBox pane = new VBox(5);
        pane.setPadding(new Insets(5));

        // Title
        Label titleLabel = new Label("Documentation");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web("#2c5aa0"));

        // WebView for documentation
        documentationPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        documentationPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(documentationPane, Priority.ALWAYS);

        // Initial content
        updateDocumentation("<div style='font-family: Arial, sans-serif; color: #666; padding: 10px;'>" +
                "Documentation will appear here...</div>");

        pane.getChildren().addAll(titleLabel, documentationPane);

        return pane;
    }

    private HBox createFooter() {
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(5));
        footer.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Keyboard shortcuts help
        Label shortcutsLabel = new Label("[Tab] Accept  [ESC] Cancel  [↑↓] Navigate  [Ctrl+Space] Toggle");
        shortcutsLabel.setFont(Font.font("Segoe UI", 10));
        shortcutsLabel.setTextFill(Color.web("#6c757d"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status label
        statusLabel.setFont(Font.font("Segoe UI", 10));
        statusLabel.setTextFill(Color.web("#6c757d"));
        updateStatus(filteredItems.size() + " items");

        footer.getChildren().addAll(shortcutsLabel, spacer, statusLabel);

        return footer;
    }

    private void setupEventHandlers() {
        // Search field
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            performFuzzySearch(newText);
        });

        // List selection
        completionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                updatePreview(generatePreview(newItem));
                updateDocumentation(generateDocumentation(newItem));
            }
        });

        // Double-click to accept
        completionListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                acceptSelectedItem();
            }
        });

        // Keyboard navigation
        completionListView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                acceptSelectedItem();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });

        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DOWN) {
                completionListView.requestFocus();
                completionListView.getSelectionModel().selectFirst();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
    }

    private void performFuzzySearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredItems.setAll(allItems);
        } else {
            List<CompletionItem> results = FuzzySearch.search(query, allItems);
            filteredItems.setAll(results);
        }

        updateStatus(filteredItems.size() + " items");

        if (!filteredItems.isEmpty()) {
            completionListView.getSelectionModel().selectFirst();
        }
    }

    private void filterItems(String type) {
        switch (type) {
            case "elements":
                filteredItems.setAll(allItems.filtered(item -> item.getType() == CompletionItemType.ELEMENT));
                break;
            case "attributes":
                filteredItems.setAll(allItems.filtered(item -> item.getType() == CompletionItemType.ATTRIBUTE));
                break;
            default:
                filteredItems.setAll(allItems);
        }
        updateStatus(filteredItems.size() + " items");
    }

    private String generatePreview(CompletionItem item) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: 'Courier New', monospace; font-size: 12px; margin: 10px; }");
        html.append(".element { color: #2c5aa0; font-weight: bold; }");
        html.append(".attribute { color: #8b6914; }");
        html.append(".value { color: #008000; }");
        html.append(".comment { color: #808080; font-style: italic; }");
        html.append("</style></head><body>");

        switch (item.getType()) {
            case ELEMENT:
                html.append("<span class='element'>&lt;").append(item.getLabel()).append("&gt;</span>");
                if (item.getRequiredAttributes() != null) {
                    for (String attr : item.getRequiredAttributes()) {
                        html.append("<br>&nbsp;&nbsp;<span class='attribute'>").append(attr)
                                .append("</span>=<span class='value'>\"...\"</span>");
                    }
                }
                html.append("<br>&nbsp;&nbsp;<span class='comment'>&lt;!-- content --&gt;</span>");
                html.append("<br><span class='element'>&lt;/").append(item.getLabel()).append("&gt;</span>");
                break;

            case ATTRIBUTE:
                html.append("<span class='attribute'>").append(item.getLabel())
                        .append("</span>=<span class='value'>\"").append(item.getDefaultValue() != null ?
                                item.getDefaultValue() : "...").append("\"</span>");
                break;

            case TEXT:
                html.append("<span class='value'>").append(item.getInsertText()).append("</span>");
                break;

            case SNIPPET:
                html.append("<pre>").append(item.getInsertText().replace("<", "&lt;")
                        .replace(">", "&gt;")).append("</pre>");
                break;
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String generateDocumentation(CompletionItem item) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 12px; margin: 10px; }");
        html.append("h3 { color: #2c5aa0; margin: 5px 0; }");
        html.append(".type { color: #6c757d; font-style: italic; }");
        html.append(".required { color: #dc3545; font-weight: bold; }");
        html.append(".optional { color: #28a745; }");
        html.append(".constraint { background: #f8f9fa; padding: 5px; margin: 5px 0; border-left: 3px solid #4a90e2; }");
        html.append("</style></head><body>");

        html.append("<h3>").append(item.getLabel()).append("</h3>");
        html.append("<div class='type'>Type: ").append(item.getType().toString()).append("</div>");

        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            html.append("<p>").append(item.getDescription()).append("</p>");
        }

        if (item.getDataType() != null) {
            html.append("<div class='constraint'><b>Data Type:</b> ").append(item.getDataType()).append("</div>");
        }

        if (item.getRequiredAttributes() != null && !item.getRequiredAttributes().isEmpty()) {
            html.append("<div class='required'>Required Attributes:</div><ul>");
            for (String attr : item.getRequiredAttributes()) {
                html.append("<li>").append(attr).append("</li>");
            }
            html.append("</ul>");
        }

        if (item.getOptionalAttributes() != null && !item.getOptionalAttributes().isEmpty()) {
            html.append("<div class='optional'>Optional Attributes:</div><ul>");
            for (String attr : item.getOptionalAttributes()) {
                html.append("<li>").append(attr).append("</li>");
            }
            html.append("</ul>");
        }

        if (item.getConstraints() != null && !item.getConstraints().isEmpty()) {
            html.append("<div class='constraint'><b>Constraints:</b><br>");
            html.append(item.getConstraints()).append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private void updatePreview(String html) {
        Platform.runLater(() -> previewPane.getEngine().loadContent(html));
    }

    private void updateDocumentation(String html) {
        Platform.runLater(() -> documentationPane.getEngine().loadContent(html));
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void acceptSelectedItem() {
        CompletionItem selected = completionListView.getSelectionModel().getSelectedItem();
        if (selected != null && onItemSelected != null) {
            onItemSelected.accept(selected);
            hide();
        }
    }

    public void show(javafx.scene.Node parentNode, List<CompletionItem> items, Point2D position) {
        // this.codeArea = codeArea; // Not needed for demo
        this.allItems.setAll(items);
        this.filteredItems.setAll(items);

        searchField.clear();
        updateStatus(items.size() + " items");

        if (!items.isEmpty()) {
            completionListView.getSelectionModel().selectFirst();
        }

        // Actually show the popup
        popup.setX(position.getX());
        popup.setY(position.getY());
        if (parentNode != null && parentNode.getScene() != null) {
            popup.show(parentNode.getScene().getWindow());
        } else {
            // Fallback to first available window
            javafx.stage.Window window = javafx.stage.Window.getWindows().stream().findFirst().orElse(null);
            if (window != null) {
                popup.show(window);
            }
        }
    }

    /**
     * Show popup at screen coordinates with completion items and context
     */
    public void show(double screenX, double screenY, List<CompletionItem> items, Object context) {
        this.allItems.setAll(items);
        this.filteredItems.setAll(items);

        searchField.clear();
        updateStatus(items.size() + " items");

        if (!items.isEmpty()) {
            completionListView.getSelectionModel().selectFirst();
        }

        popup.setX(screenX);
        popup.setY(screenY);

        if (!popup.isShowing()) {
            // Show popup with proper owner window
            try {
                // Get the first available stage as owner
                javafx.stage.Window ownerWindow = javafx.stage.Window.getWindows().stream()
                        .filter(w -> w.isShowing())
                        .findFirst()
                        .orElse(null);

                if (ownerWindow != null) {
                    popup.show(ownerWindow);
                    logger.debug("Popup shown with owner window: {}", ownerWindow.getClass().getSimpleName());
                } else {
                    logger.warn("No valid owner window found - cannot show popup");
                }
            } catch (Exception e) {
                logger.error("Failed to show popup: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Set completion selection handler
     */
    public void setOnCompletionSelected(java.util.function.Consumer<CompletionItem> handler) {
        this.completionHandler = handler;
    }

    private java.util.function.Consumer<CompletionItem> completionHandler;

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void setOnItemSelected(Consumer<CompletionItem> handler) {
        this.onItemSelected = handler;
    }

    /**
     * Custom list cell for completion items with icons and styling
     */
    private static class CompletionItemCell extends ListCell<CompletionItem> {
        private final HBox container;
        private final FontIcon icon;
        private final Label nameLabel;
        private final Label typeLabel;
        private final Label shortcutLabel;

        public CompletionItemCell() {
            container = new HBox(8);
            container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            container.setPadding(new Insets(4, 8, 4, 8));

            icon = new FontIcon();
            icon.setIconSize(16);

            nameLabel = new Label();
            nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

            typeLabel = new Label();
            typeLabel.setFont(Font.font("Segoe UI", 10));
            typeLabel.setTextFill(Color.web("#6c757d"));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            shortcutLabel = new Label();
            shortcutLabel.setFont(Font.font("Segoe UI", 10));
            shortcutLabel.setTextFill(Color.web("#6c757d"));

            container.getChildren().addAll(icon, nameLabel, typeLabel, spacer, shortcutLabel);
        }

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setStyle("");
            } else {
                // Set icon based on type
                switch (item.getType()) {
                    case ELEMENT:
                        icon.setIconLiteral("bi-tag");
                        icon.setIconColor(Color.web("#4a90e2"));
                        break;
                    case ATTRIBUTE:
                        icon.setIconLiteral("bi-at");
                        icon.setIconColor(Color.web("#d4a147"));
                        break;
                    case TEXT:
                        icon.setIconLiteral("bi-text-left");
                        icon.setIconColor(Color.web("#28a745"));
                        break;
                    case SNIPPET:
                        icon.setIconLiteral("bi-code-square");
                        icon.setIconColor(Color.web("#6c757d"));
                        break;
                }

                nameLabel.setText(item.getLabel());
                nameLabel.setTextFill(Color.web(item.getType() == CompletionItemType.ELEMENT ?
                        "#2c5aa0" : "#8b6914"));

                typeLabel.setText(item.getDataType() != null ? item.getDataType() : "");

                if (item.getShortcut() != null) {
                    shortcutLabel.setText(item.getShortcut());
                } else if (item.isRequired()) {
                    shortcutLabel.setText("Required");
                    shortcutLabel.setTextFill(Color.web("#dc3545"));
                } else {
                    shortcutLabel.setText("");
                }

                setGraphic(container);

                // Hover effect
                if (isSelected()) {
                    setStyle("-fx-background-color: #f0f8ff; -fx-border-color: #4a90e2; -fx-border-width: 1;");
                } else {
                    setStyle("-fx-background-color: transparent;");
                }
            }
        }
    }
}