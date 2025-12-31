package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup window for displaying IntelliSense completion suggestions.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Custom cell rendering with icons and descriptions</li>
 *   <li>Keyboard navigation (Up/Down, Enter, Escape)</li>
 *   <li>Mouse selection</li>
 *   <li>Auto-positioning near caret</li>
 * </ul>
 */
public class IntelliSensePopup {

    private static final Logger logger = LogManager.getLogger(IntelliSensePopup.class);

    private final Popup popup;
    private final ListView<CompletionItem> listView;
    private Consumer<CompletionItem> onItemSelected;

    public IntelliSensePopup() {
        this.popup = new Popup();
        this.listView = new ListView<>();

        setupPopup();
        setupListView();

        logger.debug("IntelliSensePopup created");
    }

    private void setupPopup() {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);

        VBox container = new VBox(listView);
        container.getStyleClass().add("intellisense-popup");

        // Load IntelliSense CSS styles
        String cssPath = getClass().getResource("/css/xml-intellisense.css").toExternalForm();
        container.getStylesheets().add(cssPath);

        popup.getContent().add(container);
    }

    private void setupListView() {
        listView.setPrefWidth(550);  // Wider to accommodate extended info
        listView.setPrefHeight(350);
        listView.setMaxHeight(400);
        listView.getStyleClass().add("intellisense-list");

        // Custom cell factory for rich display
        listView.setCellFactory(lv -> new CompletionItemCell());

        // Handle selection
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCurrentItem();
            }
        });

        // Handle Enter key
        listView.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    selectCurrentItem();
                    event.consume();
                    break;
                case ESCAPE:
                    hide();
                    event.consume();
                    break;
                default:
                    break;
            }
        });
    }

    /**
     * Shows the popup with the given completion items.
     *
     * @param items  the completion items to display
     * @param ownerWindow the owner window for the popup
     * @param x      the x-coordinate (screen coordinates)
     * @param y      the y-coordinate (screen coordinates)
     */
    public void show(List<CompletionItem> items, javafx.stage.Window ownerWindow, double x, double y) {
        if (items == null || items.isEmpty()) {
            logger.debug("No items to show in popup");
            hide();
            return;
        }

        if (ownerWindow == null) {
            logger.warn("Cannot show popup - owner window is null");
            return;
        }

        listView.getItems().setAll(items);

        // Select first item by default
        if (!items.isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }

        if (!popup.isShowing()) {
            popup.show(ownerWindow, x, y);
            listView.requestFocus();
            logger.debug("Popup shown with {} items at ({}, {})", items.size(), x, y);
        }
    }

    /**
     * Hides the popup.
     */
    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
            logger.debug("Popup hidden");
        }
    }

    /**
     * Checks if the popup is currently showing.
     *
     * @return true if showing
     */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /**
     * Sets the callback for when an item is selected.
     *
     * @param handler the selection handler
     */
    public void setOnItemSelected(Consumer<CompletionItem> handler) {
        this.onItemSelected = handler;
    }

    /**
     * Gets the currently selected item.
     *
     * @return the selected item or null
     */
    public CompletionItem getSelectedItem() {
        return listView.getSelectionModel().getSelectedItem();
    }

    /**
     * Updates the items in the popup without reopening it.
     * Preserves selection if possible, otherwise selects first item.
     * Hides popup if items are empty.
     *
     * @param items the new items to display
     */
    public void updateItems(List<CompletionItem> items) {
        if (items == null || items.isEmpty()) {
            hide();
            return;
        }

        CompletionItem previousSelection = getSelectedItem();
        listView.getItems().setAll(items);

        // Try to preserve selection
        if (previousSelection != null && items.contains(previousSelection)) {
            listView.getSelectionModel().select(previousSelection);
        } else {
            listView.getSelectionModel().selectFirst();
        }

        logger.debug("Updated popup items: {} items", items.size());
    }

    /**
     * Selects the next item in the list.
     */
    public void selectNext() {
        int currentIndex = listView.getSelectionModel().getSelectedIndex();
        if (currentIndex < listView.getItems().size() - 1) {
            listView.getSelectionModel().selectNext();
        }
    }

    /**
     * Selects the previous item in the list.
     */
    public void selectPrevious() {
        listView.getSelectionModel().selectPrevious();
    }

    private void selectCurrentItem() {
        CompletionItem selected = getSelectedItem();
        if (selected != null && onItemSelected != null) {
            logger.debug("Item selected: {}", selected.getLabel());
            onItemSelected.accept(selected);
            hide();
        }
    }

    /**
     * Custom cell renderer for CompletionItem with two-line layout.
     *
     * <p>Layout:</p>
     * <pre>
     * ┌─────────────────────────────────────────────────────────────┐
     * │ [icon] elementName    : xs:string   (0..1)  *  = "default" │
     * │        📋 pattern | Examples: "DE", "AT"                    │
     * └─────────────────────────────────────────────────────────────┘
     * </pre>
     */
    private static class CompletionItemCell extends ListCell<CompletionItem> {

        // Icon mappings for completion types
        private static final String ICON_ELEMENT = "bi-code-slash";
        private static final String ICON_ATTRIBUTE = "bi-at";
        private static final String ICON_VALUE = "bi-chat-quote";
        private static final String ICON_SNIPPET = "bi-lightning";
        private static final String ICON_FUNCTION = "bi-gear";
        private static final String ICON_AXIS = "bi-arrows-angle-expand";
        private static final String ICON_OPERATOR = "bi-calculator";
        private static final String ICON_KEYWORD = "bi-key";
        private static final String ICON_NODE_TEST = "bi-bullseye";
        private static final String ICON_VARIABLE = "bi-braces";
        private static final String ICON_TYPE = "bi-diagram-3";
        private static final String ICON_DEFAULT = "bi-circle";

        // Color mappings - WCAG AA compliant (minimum 4.5:1 contrast on white)
        private static final Color COLOR_ELEMENT = Color.web("#0056b3");    // Darker blue (7.0:1)
        private static final Color COLOR_ATTRIBUTE = Color.web("#1e7e34");  // Darker green (5.3:1)
        private static final Color COLOR_VALUE = Color.web("#495057");      // Darker gray (7.4:1)
        private static final Color COLOR_SNIPPET = Color.web("#856404");    // Dark gold (6.2:1)
        private static final Color COLOR_FUNCTION = Color.web("#0c5460");   // Dark cyan (8.6:1)
        private static final Color COLOR_AXIS = Color.web("#5a32a3");       // Darker purple (7.1:1)
        private static final Color COLOR_TYPE = Color.web("#c45000");       // Darker orange (5.1:1)
        private static final Color COLOR_REQUIRED = Color.web("#bd2130");   // Darker red (5.5:1)

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                setTooltip(null);
                getStyleClass().removeIf(s -> s.startsWith("completion-"));
            } else {
                setText(null);  // Use graphic instead
                setGraphic(createCellContent(item));

                // Apply style class based on type
                getStyleClass().removeIf(s -> s.startsWith("completion-"));
                getStyleClass().add("completion-item");
                if (item.getType() != null) {
                    getStyleClass().add("completion-" + item.getType().name().toLowerCase().replace("_", "-"));
                }
                if (item.isRequired()) {
                    getStyleClass().add("completion-required");
                }

                // Set tooltip with full description
                if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                    Tooltip tooltip = new Tooltip(item.getDescription());
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(400);
                    setTooltip(tooltip);
                }
            }
        }

        /**
         * Creates the cell content with icon, labels, and optional second line.
         */
        private VBox createCellContent(CompletionItem item) {
            VBox container = new VBox(2);
            container.setPadding(new Insets(4, 8, 4, 4));

            // === Line 1: Icon + Name + Type + Cardinality + Required + Default ===
            HBox line1 = createFirstLine(item);

            container.getChildren().add(line1);

            // === Line 2: Facets + Examples (only if has extended info) ===
            if (item.hasExtendedInfo()) {
                HBox line2 = createSecondLine(item);
                if (line2.getChildren().size() > 1) { // More than just the spacer
                    container.getChildren().add(line2);
                }
            }

            return container;
        }

        /**
         * Creates the first line with icon, name, type info, cardinality, etc.
         */
        private HBox createFirstLine(CompletionItem item) {
            HBox line = new HBox(6);
            line.setAlignment(Pos.CENTER_LEFT);

            // Icon
            FontIcon icon = createIcon(item.getType());
            icon.setIconSize(16);
            line.getChildren().add(icon);

            // Element/Attribute name (bold) - color via CSS
            Label nameLabel = new Label(item.getLabel());
            nameLabel.getStyleClass().add("completion-name");
            line.getChildren().add(nameLabel);

            // Data type - color via CSS class
            if (item.getDataType() != null && !item.getDataType().isEmpty()) {
                Label typeLabel = new Label(": " + item.getDataType());
                typeLabel.getStyleClass().add("completion-datatype");
                line.getChildren().add(typeLabel);
            }

            // Cardinality - color via CSS class
            if (item.getCardinality() != null && !item.getCardinality().isEmpty()) {
                Label cardLabel = new Label("(" + item.getCardinality() + ")");
                cardLabel.getStyleClass().add("completion-cardinality");
                line.getChildren().add(cardLabel);
            }

            // Required indicator - color via CSS class
            if (item.isRequired()) {
                Label reqLabel = new Label("*");
                reqLabel.getStyleClass().add("completion-required-marker");
                line.getChildren().add(reqLabel);
            }

            // Default value - color via CSS class
            if (item.getDefaultValue() != null && !item.getDefaultValue().isEmpty()) {
                Label defaultLabel = new Label("= \"" + truncate(item.getDefaultValue(), 20) + "\"");
                defaultLabel.getStyleClass().add("completion-default");
                line.getChildren().add(defaultLabel);
            }

            // Spacer to push content left
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            line.getChildren().add(spacer);

            // Namespace prefix badge - color via CSS class
            if (item.getPrefix() != null && !item.getPrefix().isEmpty()) {
                Label nsLabel = new Label(item.getPrefix());
                nsLabel.getStyleClass().add("completion-namespace");
                line.getChildren().add(nsLabel);
            }

            return line;
        }

        /**
         * Creates the second line with facets and examples.
         * All colors controlled via CSS for proper selection state handling.
         */
        private HBox createSecondLine(CompletionItem item) {
            HBox line = new HBox(8);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setPadding(new Insets(0, 0, 0, 22)); // Indent to align with text after icon
            line.getStyleClass().add("completion-second-line");

            // Facet hints - color via CSS
            List<String> facets = item.getFacetHints();
            if (facets != null && !facets.isEmpty()) {
                FontIcon facetIcon = new FontIcon("bi-list-check");
                facetIcon.setIconSize(12);
                facetIcon.getStyleClass().add("completion-facet-icon");
                line.getChildren().add(facetIcon);

                String facetText = String.join(", ", facets);
                Label facetLabel = new Label(truncate(facetText, 30));
                facetLabel.getStyleClass().add("completion-facets");
                line.getChildren().add(facetLabel);
            }

            // Separator if both facets and examples
            if (!facets.isEmpty() && !item.getExamples().isEmpty()) {
                Label sep = new Label("|");
                sep.getStyleClass().add("completion-separator");
                line.getChildren().add(sep);
            }

            // Examples - color via CSS
            List<String> examples = item.getExamples();
            if (examples != null && !examples.isEmpty()) {
                Label exLabel = new Label("e.g.: ");
                exLabel.getStyleClass().add("completion-example-label");
                line.getChildren().add(exLabel);

                String exampleText = String.join(", ", examples);
                Label exValues = new Label(truncate(exampleText, 35));
                exValues.getStyleClass().add("completion-examples");
                line.getChildren().add(exValues);
            }

            // Required attributes hint - color via CSS
            List<String> reqAttrs = item.getRequiredAttributes();
            if (reqAttrs != null && !reqAttrs.isEmpty()) {
                if (!line.getChildren().isEmpty()) {
                    Label sep = new Label("|");
                    sep.getStyleClass().add("completion-separator");
                    line.getChildren().add(sep);
                }
                FontIcon attrIcon = new FontIcon("bi-exclamation-triangle");
                attrIcon.setIconSize(12);
                attrIcon.getStyleClass().add("completion-attr-icon");
                line.getChildren().add(attrIcon);

                Label attrLabel = new Label("needs: " + truncate(String.join(", ", reqAttrs), 25));
                attrLabel.getStyleClass().add("completion-required-attrs");
                line.getChildren().add(attrLabel);
            }

            return line;
        }

        /**
         * Creates an icon for the given completion type.
         * Colors are controlled via CSS classes for proper selection state handling.
         */
        private FontIcon createIcon(CompletionItemType type) {
            String iconLiteral;
            String styleClass;

            if (type == null) {
                FontIcon icon = new FontIcon(ICON_DEFAULT);
                icon.getStyleClass().add("completion-icon-default");
                return icon;
            }

            switch (type) {
                case ELEMENT:
                    iconLiteral = ICON_ELEMENT;
                    styleClass = "completion-icon-element";
                    break;
                case ATTRIBUTE:
                    iconLiteral = ICON_ATTRIBUTE;
                    styleClass = "completion-icon-attribute";
                    break;
                case VALUE:
                    iconLiteral = ICON_VALUE;
                    styleClass = "completion-icon-value";
                    break;
                case SNIPPET:
                    iconLiteral = ICON_SNIPPET;
                    styleClass = "completion-icon-snippet";
                    break;
                case XPATH_FUNCTION:
                    iconLiteral = ICON_FUNCTION;
                    styleClass = "completion-icon-function";
                    break;
                case XPATH_AXIS:
                    iconLiteral = ICON_AXIS;
                    styleClass = "completion-icon-axis";
                    break;
                case XPATH_OPERATOR:
                    iconLiteral = ICON_OPERATOR;
                    styleClass = "completion-icon-operator";
                    break;
                case XQUERY_KEYWORD:
                    iconLiteral = ICON_KEYWORD;
                    styleClass = "completion-icon-keyword";
                    break;
                case XPATH_NODE_TEST:
                    iconLiteral = ICON_NODE_TEST;
                    styleClass = "completion-icon-nodetest";
                    break;
                case XPATH_VARIABLE:
                    iconLiteral = ICON_VARIABLE;
                    styleClass = "completion-icon-variable";
                    break;
                case TYPE:
                    iconLiteral = ICON_TYPE;
                    styleClass = "completion-icon-type";
                    break;
                default:
                    iconLiteral = ICON_DEFAULT;
                    styleClass = "completion-icon-default";
                    break;
            }

            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add(styleClass);
            return icon;
        }

        /**
         * Truncates a string to maxLength, adding "..." if truncated.
         */
        private String truncate(String text, int maxLength) {
            if (text == null) {
                return "";
            }
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
