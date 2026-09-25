package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Objects;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The shared "follow-on actions" block of the activity side panels: a vertical list of
 * full-width rows (icon + label, style class {@code fxt-action-row}) built from
 * {@link PanelAction}s. Rows have a fixed height and only change colour on hover, press
 * and focus, so nothing in the panel moves while the user interacts with it.
 *
 * <p>Wrap a list in a collapsible section with {@link #section(String, boolean, PanelActionList)}.
 * The rendered buttons are plain {@link Button}s; keep a reference via {@link #add} or
 * {@link #button(String)} when the caller needs to swap the graphic or toggle state.
 */
final class PanelActionList extends VBox {

    static final String ROW_STYLE_CLASS = "fxt-action-row";
    static final String PRIMARY_STYLE_CLASS = "fxt-action-row-primary";
    static final String INLINE_STYLE_CLASS = "fxt-action-row-inline";
    private static final int ICON_SIZE = 15;

    PanelActionList(PanelAction... actions) {
        getStyleClass().add("fxt-action-list");
        for (PanelAction action : actions) {
            add(action);
        }
    }

    /** Appends a row for {@code action} and returns its button. */
    Button add(PanelAction action) {
        Button button = row(action.label(), action.iconLiteral(), action.onAction());
        button.setId(action.id());
        if (action.primary()) {
            button.getStyleClass().add(PRIMARY_STYLE_CLASS);
        }
        if (action.tooltip() != null && !action.tooltip().isBlank()) {
            button.setTooltip(new Tooltip(action.tooltip()));
        }
        if (action.disabledWhen() != null) {
            button.disableProperty().bind(action.disabledWhen());
        }
        if (action.visibleWhen() != null) {
            button.visibleProperty().bind(action.visibleWhen());
            button.managedProperty().bind(action.visibleWhen());
        }
        getChildren().add(button);
        return button;
    }

    /** @return the row button with the given id, or {@code null} */
    Button button(String id) {
        for (var child : getChildren()) {
            if (child instanceof Button button && Objects.equals(id, button.getId())) {
                return button;
            }
        }
        return null;
    }

    /** @return the row labels in display order (for tests/observers) */
    List<String> labels() {
        return getChildren().stream()
                .filter(n -> n instanceof Button)
                .map(n -> ((Button) n).getText())
                .toList();
    }

    /**
     * A collapsible section (header with chevron + this list) using the shared
     * {@link SidePanelLayout#sectionHeader} style, so action lists sit next to the
     * panels' other sections without a visual seam.
     */
    static VBox section(String title, boolean collapsed, PanelActionList list) {
        return new VBox(SidePanelLayout.sectionHeader(collapsed, new Label(title), list), list);
    }

    /**
     * A single action row outside a list (e.g. the "Change" button of a {@link SourceRow}
     * or "Add parameter" below a form). Same look and size stability, compact height.
     */
    static Button inlineRow(String label, String iconLiteral, Runnable onAction) {
        Button button = row(label, iconLiteral, onAction);
        button.getStyleClass().add(INLINE_STYLE_CLASS);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private static Button row(String label, String iconLiteral, Runnable onAction) {
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(ICON_SIZE);
        Button button = new Button(label, icon);
        button.getStyleClass().add(ROW_STYLE_CLASS);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMnemonicParsing(false);
        button.setWrapText(false);
        button.setTextOverrun(OverrunStyle.ELLIPSIS);
        button.setOnAction(e -> onAction.run());
        return button;
    }
}
