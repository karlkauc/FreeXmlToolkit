package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for autocomplete suggestions when typing {@link} references.
 * Shows XPath or element name suggestions from the schema.
 *
 * @since 2.0
 */
public class LinkAutocompletePopup extends Popup {

    private static final Logger logger = LogManager.getLogger(LinkAutocompletePopup.class);

    /** The trigger text that activates autocomplete */
    public static final String LINK_TRIGGER = "{@link ";

    private final ListView<XsdElementPathExtractor.LinkSuggestion> listView;
    private final Label headerLabel;
    private final TextField filterField;
    private final XsdElementPathExtractor pathExtractor;

    private Consumer<String> onSelect;
    private TextInputControl targetControl;
    private int triggerPosition = -1;

    /**
     * Creates a new autocomplete popup.
     *
     * @param pathExtractor the path extractor for suggestions
     */
    public LinkAutocompletePopup(XsdElementPathExtractor pathExtractor) {
        this.pathExtractor = pathExtractor;

        setAutoHide(true);
        setHideOnEscape(true);

        // Create content
        VBox content = new VBox(5);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: white; -fx-border-color: #ccc; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");
        content.setPrefWidth(400);
        content.setMaxHeight(300);

        // Header
        headerLabel = new Label("Select element reference");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");

        // Filter field
        filterField = new TextField();
        filterField.setPromptText("Type to filter (/ for XPath, name for element)");
        filterField.textProperty().addListener((obs, oldVal, newVal) -> updateSuggestions(newVal));

        // List view
        listView = new ListView<>();
        listView.setPrefHeight(200);
        listView.setCellFactory(lv -> new SuggestionCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Handle selection
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectCurrent();
            }
        });

        // Keyboard navigation
        filterField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
        listView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        // Hint label
        Label hintLabel = new Label("↑↓ Navigate | Enter Select | Esc Cancel");
        hintLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

        content.getChildren().addAll(headerLabel, filterField, listView, hintLabel);
        getContent().add(content);

        // Initial suggestions
        updateSuggestions("");
    }

    /**
     * Shows the popup for the given text control.
     *
     * @param control         the text control (TextField or TextArea)
     * @param caretPosition   the caret position where {@code {@link} } was typed
     * @param onSelectHandler callback when a suggestion is selected
     */
    public void showFor(TextInputControl control, int caretPosition, Consumer<String> onSelectHandler) {
        this.targetControl = control;
        this.triggerPosition = caretPosition;
        this.onSelect = onSelectHandler;

        // Position popup near caret
        Window window = control.getScene().getWindow();
        Bounds bounds = control.localToScreen(control.getBoundsInLocal());

        if (bounds != null) {
            // Try to position below the control
            double x = bounds.getMinX();
            double y = bounds.getMaxY() + 5;

            // Reset filter and show
            filterField.clear();
            updateSuggestions("");

            show(window, x, y);

            // Focus filter field
            Platform.runLater(() -> filterField.requestFocus());
        }
    }

    /**
     * Updates the suggestion list based on the filter text.
     */
    private void updateSuggestions(String filter) {
        if (pathExtractor == null) {
            return;
        }

        List<XsdElementPathExtractor.LinkSuggestion> suggestions = pathExtractor.suggestLinks(filter);
        listView.getItems().setAll(suggestions);

        if (!suggestions.isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }

        // Update header based on mode
        if (filter != null && filter.startsWith("/")) {
            headerLabel.setText("XPath references");
        } else if (filter != null && !filter.isEmpty()) {
            headerLabel.setText("Element references");
        } else {
            headerLabel.setText("Select element reference");
        }
    }

    /**
     * Handles keyboard navigation.
     */
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case ENTER -> {
                selectCurrent();
                event.consume();
            }
            case ESCAPE -> {
                hide();
                event.consume();
            }
            case UP -> {
                if (event.getSource() == filterField) {
                    // Move focus to list
                    listView.requestFocus();
                    if (listView.getSelectionModel().isEmpty() && !listView.getItems().isEmpty()) {
                        listView.getSelectionModel().selectLast();
                    } else {
                        int idx = listView.getSelectionModel().getSelectedIndex();
                        if (idx > 0) {
                            listView.getSelectionModel().select(idx - 1);
                        }
                    }
                    event.consume();
                }
            }
            case DOWN -> {
                if (event.getSource() == filterField) {
                    // Move focus to list
                    listView.requestFocus();
                    if (listView.getSelectionModel().isEmpty() && !listView.getItems().isEmpty()) {
                        listView.getSelectionModel().selectFirst();
                    } else {
                        int idx = listView.getSelectionModel().getSelectedIndex();
                        if (idx < listView.getItems().size() - 1) {
                            listView.getSelectionModel().select(idx + 1);
                        }
                    }
                    event.consume();
                }
            }
            case TAB -> {
                // Tab completes with selected item
                selectCurrent();
                event.consume();
            }
            default -> {
                // For other keys in listView, redirect to filter field
                if (event.getSource() == listView && event.getCode().isLetterKey()) {
                    filterField.requestFocus();
                    // Let the event propagate to filter field
                }
            }
        }
    }

    /**
     * Selects the current item and closes the popup.
     */
    private void selectCurrent() {
        XsdElementPathExtractor.LinkSuggestion selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null && onSelect != null) {
            String linkText = "{@link " + selected.insertText() + "}";
            onSelect.accept(linkText);
            logger.debug("Selected link: {}", linkText);
        }
        hide();
    }

    /**
     * Checks if the given text ends with the link trigger.
     *
     * @param text the text to check
     * @return true if it ends with {@code {@link}}
     */
    public static boolean isLinkTrigger(String text) {
        return text != null && text.endsWith(LINK_TRIGGER);
    }

    /**
     * Gets the position of the link trigger in the text.
     *
     * @param text the text to search
     * @return the position, or -1 if not found
     */
    public static int findLinkTriggerPosition(String text) {
        if (text == null) return -1;
        return text.lastIndexOf(LINK_TRIGGER);
    }

    /**
     * Custom cell for displaying suggestions with type indicator.
     */
    private static class SuggestionCell extends ListCell<XsdElementPathExtractor.LinkSuggestion> {
        private final HBox content;
        private final FontIcon icon;
        private final Label textLabel;
        private final Label typeLabel;

        public SuggestionCell() {
            content = new HBox(8);
            content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            icon = new FontIcon();
            icon.setIconSize(14);

            textLabel = new Label();
            textLabel.setStyle("-fx-font-family: monospace;");
            HBox.setHgrow(textLabel, Priority.ALWAYS);

            typeLabel = new Label();
            typeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

            content.getChildren().addAll(icon, textLabel, typeLabel);
        }

        @Override
        protected void updateItem(XsdElementPathExtractor.LinkSuggestion item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                textLabel.setText(item.displayText());
                typeLabel.setText(item.type());

                // Set icon based on type
                if ("XPath".equals(item.type())) {
                    icon.setIconLiteral("bi-diagram-3");
                    icon.setIconColor(javafx.scene.paint.Color.DODGERBLUE);
                } else {
                    icon.setIconLiteral("bi-box");
                    icon.setIconColor(javafx.scene.paint.Color.FORESTGREEN);
                }

                setGraphic(content);
            }
        }
    }
}
