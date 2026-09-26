package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Objects;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

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
    static final String MENU_STYLE_CLASS = "fxt-action-row-menu";
    private static final int ICON_SIZE = 15;

    PanelActionList(PanelAction... actions) {
        getStyleClass().add("fxt-action-list");
        for (PanelAction action : actions) {
            add(action);
        }
    }

    /** Appends a row for {@code action} and returns its button. */
    Button add(PanelAction action) {
        Button button = row(action.label(), action.iconLiteral(), action.onAction(),
                action.primary() ? null : action.color());
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

    /**
     * Appends a drop-down row: the given {@link MenuButton} styled like an action row
     * (icon · label · ▾), for choices such as "Saved Queries" or "Examples". The caller
     * keeps ownership of the menu's items.
     */
    MenuButton addMenu(MenuButton menu, String id, String iconLiteral, String label) {
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(ICON_SIZE);
        SemanticIcon.bind(icon, ActionColor.NEUTRAL);
        menu.setGraphic(icon);
        menu.setText(label);
        menu.setId(id);
        menu.getStyleClass().addAll(ROW_STYLE_CLASS, MENU_STYLE_CLASS);
        menu.setMaxWidth(Double.MAX_VALUE);
        menu.setAlignment(Pos.CENTER_LEFT);
        menu.setMnemonicParsing(false);
        getChildren().add(menu);
        return menu;
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
                .filter(n -> n instanceof Labeled)
                .map(n -> ((Labeled) n).getText())
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
     * or "Add parameter" below a form). Same look and size stability, compact height;
     * {@code iconLiteral} may be {@code null} where horizontal space is scarce.
     */
    static Button inlineRow(String label, String iconLiteral, Runnable onAction) {
        Button button = row(label, iconLiteral, onAction, ActionColor.NEUTRAL);
        button.getStyleClass().add(INLINE_STYLE_CLASS);
        // Never shrink below the label: in a crowded SourceRow the file name ellipsises
        // first, the action verb stays legible.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        return button;
    }

    /**
     * @param color the action-colour role bound to the icon, or {@code null} to leave the colour to CSS
     *              (primary rows: white icon on the workflow fill)
     */
    private static Button row(String label, String iconLiteral, Runnable onAction, ActionColor color) {
        Button button = new Button(label);
        if (iconLiteral != null) {
            IconifyIcon icon = new IconifyIcon(iconLiteral);
            icon.setIconSize(ICON_SIZE);
            if (color != null) {
                // Bound, not set: the .fxt-action-row .iconify-icon CSS rule would otherwise repaint it.
                SemanticIcon.bind(icon, color);
            }
            button.setGraphic(icon);
        }
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
