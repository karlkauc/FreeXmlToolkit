package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;

/**
 * The lightbulb chooser: a small popup listing the quick fixes available for the
 * problems on a line (modeled on the IntelliSense popup — arrow keys, Enter
 * applies, Escape closes, double-click applies).
 */
public final class QuickFixPopup {

    private final Popup popup = new Popup();
    private final ListView<SqfFixSuggestion> listView = new ListView<>();
    private Consumer<SqfFixSuggestion> onFixSelected;

    public QuickFixPopup() {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);

        listView.setPrefWidth(420);
        listView.setPrefHeight(-1);
        listView.setMaxHeight(220);
        listView.getStyleClass().add("quickfix-list");
        listView.setCellFactory(lv -> new FixCell());
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCurrentItem();
            }
        });
        listView.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    selectCurrentItem();
                    event.consume();
                }
                case ESCAPE -> {
                    hide();
                    event.consume();
                }
                default -> {
                }
            }
        });

        VBox container = new VBox(listView);
        container.getStyleClass().add("quickfix-popup");
        popup.getContent().add(container);
    }

    private static final class FixCell extends ListCell<SqfFixSuggestion> {
        @Override
        protected void updateItem(SqfFixSuggestion item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            setGraphic(QuickFixIcons.lightbulb(16));
            setText(item.title() + (item.needsUserInput() ? "…" : ""));
            setTooltip(item.description().isBlank() ? null : new Tooltip(item.description()));
        }
    }

    /** Shows the popup at the given screen position. */
    public void show(List<SqfFixSuggestion> fixes, Window owner, double x, double y) {
        if (fixes == null || fixes.isEmpty() || owner == null) {
            hide();
            return;
        }
        // clearSelection before setAll: avoids the ListViewBehavior IndexOutOfBounds
        listView.getSelectionModel().clearSelection();
        listView.getItems().setAll(fixes);
        listView.setPrefHeight(Math.min(fixes.size(), 8) * 28.0 + 12);
        listView.getSelectionModel().selectFirst();
        if (!popup.isShowing()) {
            popup.show(owner, x, y);
        }
        listView.requestFocus();
    }

    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Sets the callback invoked with the chosen fix (popup hides itself first). */
    public void setOnFixSelected(Consumer<SqfFixSuggestion> handler) {
        this.onFixSelected = handler;
    }

    private void selectCurrentItem() {
        SqfFixSuggestion selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null && onFixSelected != null) {
            hide();
            onFixSelected.accept(selected);
        }
    }
}
