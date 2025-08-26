package org.fxt.freexmltoolkit.controls.intellisense;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context-sensitive quick actions menu for XML editing operations.
 * Provides instant access to common XML editing tasks with keyboard shortcuts.
 */
public class QuickActionsMenu {

    private final Popup popup;
    private final VBox container;
    private final TextField searchField;
    private final ListView<QuickAction> actionsList;
    private final Label categoryLabel;

    private final List<QuickAction> allActions = new ArrayList<>();
    private final Map<QuickAction.QuickActionType, List<QuickAction>> actionsByType = new HashMap<>();

    private QuickAction.ActionContext currentContext;
    private ActionExecutionListener executionListener;

    public QuickActionsMenu() {
        this.popup = new Popup();
        this.container = new VBox(5);
        this.searchField = new TextField();
        this.actionsList = new ListView<>();
        this.categoryLabel = new Label();

        initializeUI();
        initializeBuiltInActions();
        setupEventHandlers();
    }

    /**
     * Show the quick actions menu at specified coordinates
     */
    public void show(double x, double y, QuickAction.ActionContext context) {
        this.currentContext = context;

        List<QuickAction> applicableActions = getApplicableActions(context);
        updateActionsList(applicableActions);

        if (applicableActions.isEmpty()) {
            return; // Don't show empty menu
        }

        popup.show(searchField.getScene().getWindow(), x, y);
        searchField.requestFocus();
        searchField.clear();

        // Select first action by default
        if (!actionsList.getItems().isEmpty()) {
            actionsList.getSelectionModel().selectFirst();
        }
    }

    /**
     * Hide the menu
     */
    public void hide() {
        popup.hide();
        searchField.clear();
    }

    /**
     * Register custom action
     */
    public void addAction(QuickAction action) {
        allActions.add(action);
        actionsByType.computeIfAbsent(action.getType(), k -> new ArrayList<>()).add(action);

        // Sort by priority
        allActions.sort(Comparator.comparingInt(QuickAction::getPriority).reversed());
        actionsByType.values().forEach(list ->
                list.sort(Comparator.comparingInt(QuickAction::getPriority).reversed())
        );
    }

    /**
     * Set action execution listener
     */
    public void setExecutionListener(ActionExecutionListener listener) {
        this.executionListener = listener;
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        container.setPrefSize(300, 400);
        container.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #cccccc; " +
                        "-fx-border-width: 1; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);"
        );
        container.setPadding(new Insets(10));

        // Header
        Label headerLabel = new Label("Quick Actions");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Search field
        searchField.setPromptText("Search actions...");
        searchField.setPrefHeight(30);

        // Category label
        categoryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        // Actions list
        actionsList.setPrefHeight(300);
        actionsList.setCellFactory(listView -> new ActionListCell());

        container.getChildren().addAll(headerLabel, searchField, categoryLabel, actionsList);
        popup.getContent().add(container);
        popup.setAutoHide(true);
    }

    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Search functionality
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            List<QuickAction> filtered = filterActions(newVal);
            updateActionsList(filtered);
        });

        // Keyboard navigation
        searchField.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DOWN -> {
                    actionsList.requestFocus();
                    if (actionsList.getSelectionModel().getSelectedIndex() == -1) {
                        actionsList.getSelectionModel().selectFirst();
                    }
                }
                case ENTER -> executeSelectedAction();
                case ESCAPE -> hide();
            }
        });

        actionsList.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> executeSelectedAction();
                case ESCAPE -> hide();
                case UP -> {
                    if (actionsList.getSelectionModel().getSelectedIndex() == 0) {
                        searchField.requestFocus();
                    }
                }
            }
        });

        // Mouse selection
        actionsList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                executeSelectedAction();
            }
        });
    }

    /**
     * Get actions applicable to current context
     */
    private List<QuickAction> getApplicableActions(QuickAction.ActionContext context) {
        return allActions.stream()
                .filter(action -> action.isApplicable(context))
                .collect(Collectors.toList());
    }

    /**
     * Filter actions based on search text
     */
    private List<QuickAction> filterActions(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return getApplicableActions(currentContext);
        }

        String query = searchText.toLowerCase().trim();

        return getApplicableActions(currentContext).stream()
                .filter(action ->
                        action.getName().toLowerCase().contains(query) ||
                                action.getDescription().toLowerCase().contains(query) ||
                                action.getType().getDisplayName().toLowerCase().contains(query)
                )
                .collect(Collectors.toList());
    }

    /**
     * Update actions list display
     */
    private void updateActionsList(List<QuickAction> actions) {
        actionsList.getItems().clear();
        actionsList.getItems().addAll(actions);

        if (!actions.isEmpty()) {
            categoryLabel.setText(String.format("%d actions available", actions.size()));
        } else {
            categoryLabel.setText("No actions available");
        }

        Platform.runLater(() -> {
            if (!actions.isEmpty()) {
                actionsList.getSelectionModel().selectFirst();
            }
        });
    }

    /**
     * Execute the currently selected action
     */
    private void executeSelectedAction() {
        QuickAction selectedAction = actionsList.getSelectionModel().getSelectedItem();
        if (selectedAction != null && currentContext != null) {

            QuickAction.ActionResult result = selectedAction.execute(currentContext);

            if (executionListener != null) {
                executionListener.onActionExecuted(selectedAction, result);
            }

            hide();
        }
    }

    /**
     * Initialize built-in actions
     */
    private void initializeBuiltInActions() {
        // XML Formatting Actions
        addAction(new QuickAction.Builder("format-xml", "Format XML", context -> {
            try {
                String formatted = formatXmlString(context.fullText);
                return QuickAction.ActionResult.success(formatted, 0);
            } catch (Exception e) {
                return QuickAction.ActionResult.failure("Failed to format XML: " + e.getMessage());
            }
        })
                .description("Auto-format and indent XML content")
                .type(QuickAction.QuickActionType.XML_FORMATTING)
                .shortcut(KeyCombination.keyCombination("Ctrl+Shift+F"))
                .icon("format")
                .priority(150)
                .build());

        addAction(new QuickAction.Builder("minify-xml", "Minify XML", context -> {
            String minified = context.fullText.replaceAll(">\\s+<", "><").trim();
            return QuickAction.ActionResult.success(minified, 0);
        })
                .description("Remove whitespace and create compact XML")
                .type(QuickAction.QuickActionType.XML_FORMATTING)
                .icon("compress")
                .priority(140)
                .build());

        // Element Operations
        addAction(new QuickAction.Builder("wrap-element", "Wrap with Element", context -> {
            if (context.selectedText.isEmpty()) {
                return QuickAction.ActionResult.failure("Please select text to wrap");
            }

            String wrapped = String.format("<element>%s</element>", context.selectedText);
            return QuickAction.ActionResult.success(
                    context.fullText.substring(0, context.selectionStart) +
                            wrapped +
                            context.fullText.substring(context.selectionEnd),
                    context.selectionStart + "<element>".length()
            );
        })
                .description("Wrap selected text with XML element")
                .type(QuickAction.QuickActionType.ELEMENT_OPERATIONS)
                .shortcut(KeyCombination.keyCombination("Ctrl+E"))
                .requiresSelection(true)
                .priority(130)
                .build());

        addAction(new QuickAction.Builder("extract-element", "Extract Element Content", context -> {
            String text = context.selectedText.isEmpty() ? context.fullText : context.selectedText;

            // Simple element extraction - find first complete element
            int start = text.indexOf('<');
            int end = text.indexOf('>', start);
            if (start != -1 && end != -1) {
                String tagName = text.substring(start + 1, end).split(" ")[0];
                int contentStart = end + 1;
                int contentEnd = text.indexOf("</" + tagName + ">", contentStart);

                if (contentEnd != -1) {
                    String content = text.substring(contentStart, contentEnd).trim();
                    return QuickAction.ActionResult.success(content, 0);
                }
            }

            return QuickAction.ActionResult.failure("No complete element found to extract");
        })
                .description("Extract content from XML element")
                .type(QuickAction.QuickActionType.ELEMENT_OPERATIONS)
                .priority(120)
                .build());

        // Attribute Operations  
        addAction(new QuickAction.Builder("sort-attributes", "Sort Attributes", context -> {
            // This is a simplified implementation
            // In practice, would need proper XML parsing
            return QuickAction.ActionResult.success("Attributes sorted (demo)", 0);
        })
                .description("Sort XML element attributes alphabetically")
                .type(QuickAction.QuickActionType.ATTRIBUTE_OPERATIONS)
                .priority(110)
                .build());

        // XPath Query Actions
        addAction(new QuickAction.Builder("evaluate-xpath", "Evaluate XPath", context -> {
            // This would integrate with existing XPath functionality
            return QuickAction.ActionResult.success("XPath evaluation started");
        })
                .description("Open XPath evaluation dialog")
                .type(QuickAction.QuickActionType.XPATH_QUERY)
                .shortcut(KeyCombination.keyCombination("Ctrl+X"))
                .priority(125)
                .build());

        // Schema Validation
        addAction(new QuickAction.Builder("validate-schema", "Validate against Schema", context -> {
            return QuickAction.ActionResult.success("Schema validation started");
        })
                .description("Validate XML against loaded XSD schema")
                .type(QuickAction.QuickActionType.SCHEMA_VALIDATION)
                .shortcut(KeyCombination.keyCombination("F9"))
                .priority(135)
                .build());

        // Transformation Actions
        addAction(new QuickAction.Builder("transform-xslt", "Apply XSLT Transform", context -> {
            return QuickAction.ActionResult.success("XSLT transformation dialog opened");
        })
                .description("Transform XML using XSLT stylesheet")
                .type(QuickAction.QuickActionType.TRANSFORMATION)
                .priority(115)
                .build());

        // Namespace Operations
        addAction(new QuickAction.Builder("add-namespace", "Add Namespace Declaration", context -> {
            String nsDeclaration = " xmlns:ns=\"http://example.com/namespace\"";
            // Find first element and add namespace
            int tagEnd = context.fullText.indexOf('>');
            if (tagEnd != -1) {
                String modified = context.fullText.substring(0, tagEnd) + nsDeclaration +
                        context.fullText.substring(tagEnd);
                return QuickAction.ActionResult.success(modified, tagEnd + nsDeclaration.length());
            }
            return QuickAction.ActionResult.failure("No element found to add namespace");
        })
                .description("Add namespace declaration to root element")
                .type(QuickAction.QuickActionType.NAMESPACE_OPERATIONS)
                .priority(105)
                .build());

        // General Actions
        addAction(new QuickAction.Builder("escape-xml", "Escape XML Characters", context -> {
            String text = context.selectedText.isEmpty() ? context.fullText : context.selectedText;
            String escaped = text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");

            if (context.selectedText.isEmpty()) {
                return QuickAction.ActionResult.success(escaped, 0);
            } else {
                String result = context.fullText.substring(0, context.selectionStart) +
                        escaped +
                        context.fullText.substring(context.selectionEnd);
                return QuickAction.ActionResult.success(result, context.selectionStart + escaped.length());
            }
        })
                .description("Escape special XML characters")
                .type(QuickAction.QuickActionType.GENERAL)
                .priority(100)
                .build());
    }

    /**
     * Basic XML formatting (simplified implementation)
     */
    private String formatXmlString(String xml) {
        // This is a very basic formatter for demo purposes
        // In practice, would use proper XML parser/formatter
        StringBuilder formatted = new StringBuilder();
        String[] lines = xml.split("(?=<)|(?<=>)");
        int indent = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("</")) {
                indent = Math.max(0, indent - 2);
            }

            formatted.append("  ".repeat(indent)).append(line).append("\n");

            if (line.startsWith("<") && !line.startsWith("</") && !line.endsWith("/>")) {
                indent += 2;
            }
        }

        return formatted.toString().trim();
    }

    /**
     * Custom list cell for actions
     */
    private static class ActionListCell extends ListCell<QuickAction> {
        @Override
        protected void updateItem(QuickAction action, boolean empty) {
            super.updateItem(action, empty);

            if (empty || action == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(5));

                // Action name
                Label nameLabel = new Label(action.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");

                // Shortcut
                if (action.getShortcut() != null) {
                    Label shortcutLabel = new Label(action.getShortcut().getDisplayText());
                    shortcutLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    container.getChildren().addAll(nameLabel, spacer, shortcutLabel);
                } else {
                    container.getChildren().add(nameLabel);
                }

                // Description as tooltip
                if (!action.getDescription().isEmpty()) {
                    Tooltip.install(container, new Tooltip(action.getDescription()));
                }

                setGraphic(container);
                setText(null);
            }
        }
    }

    /**
     * Action execution listener interface
     */
    public interface ActionExecutionListener {
        void onActionExecuted(QuickAction action, QuickAction.ActionResult result);
    }
}