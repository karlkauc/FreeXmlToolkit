package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;

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
        popup.getContent().add(container);
    }

    private void setupListView() {
        listView.setPrefWidth(400);
        listView.setPrefHeight(300);
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
     * Custom cell renderer for CompletionItem.
     */
    private static class CompletionItemCell extends ListCell<CompletionItem> {

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                // Build display text
                StringBuilder display = new StringBuilder();
                display.append(item.getLabel());

                // Add type indicator
                if (item.getType() != null) {
                    display.append("  [").append(item.getType()).append("]");
                }

                // Add data type if available
                if (item.getDataType() != null && !item.getDataType().isEmpty()) {
                    display.append("  : ").append(item.getDataType());
                }

                // Add required indicator
                if (item.isRequired()) {
                    display.append("  *");
                }

                setText(display.toString());

                // Style based on type
                String styleClass = "completion-item";
                if (item.getType() != null) {
                    switch (item.getType()) {
                        case ELEMENT:
                            styleClass += " completion-element";
                            break;
                        case ATTRIBUTE:
                            styleClass += " completion-attribute";
                            break;
                        case VALUE:
                            styleClass += " completion-value";
                            break;
                        default:
                            break;
                    }
                }

                getStyleClass().removeIf(s -> s.startsWith("completion-"));
                getStyleClass().add(styleClass);

                // Set tooltip with description
                if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                    setTooltip(new javafx.scene.control.Tooltip(item.getDescription()));
                }
            }
        }
    }
}
