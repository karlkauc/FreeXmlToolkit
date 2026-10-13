package org.fxt.freexmltoolkit.controls.shell;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.MenuItem;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

/**
 * A menu-bar item with an icon and an action-colour role, so the menu layout can be written
 * in FXML: {@code <ShellMenuItem id="menu-file-save" text="Save" iconLiteral="bi-save"
 * color="TOOL" accelerator="Shortcut+S" onAction="#onSave"/>}.
 */
public class ShellMenuItem extends MenuItem {

    private static final int ICON_SIZE = 16;

    private final StringProperty iconLiteral = new SimpleStringProperty(this, "iconLiteral");
    private final ObjectProperty<ActionColor> color =
            new SimpleObjectProperty<>(this, "color", ActionColor.NEUTRAL);

    public ShellMenuItem() {
        iconLiteral.addListener(obs -> refreshIcon());
        color.addListener(obs -> refreshIcon());
    }

    /**
     * @param text        the item text
     * @param iconLiteral a bundled Bootstrap icon name ({@code bi-*})
     * @param color       the action-colour role tinting the icon
     */
    public ShellMenuItem(String text, String iconLiteral, ActionColor color) {
        this();
        setText(text);
        setColor(color);
        setIconLiteral(iconLiteral);
    }

    private void refreshIcon() {
        setGraphic(icon(getIconLiteral(), getColor()));
    }

    /** @return a menu-sized icon tinted with {@code role}, or {@code null} without a literal. */
    static IconifyIcon icon(String literal, ActionColor role) {
        if (literal == null || literal.isBlank()) {
            return null;
        }
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(ICON_SIZE);
        // Bound, not set: the themed menu CSS would repaint a merely set colour.
        return SemanticIcon.bind(icon, role);
    }

    /** A bundled Bootstrap icon name ({@code bi-*}), or {@code null} for a text-only item. */
    public StringProperty iconLiteralProperty() {
        return iconLiteral;
    }

    public String getIconLiteral() {
        return iconLiteral.get();
    }

    public void setIconLiteral(String literal) {
        iconLiteral.set(literal);
    }

    /** The action-colour role tinting the icon. */
    public ObjectProperty<ActionColor> colorProperty() {
        return color;
    }

    public ActionColor getColor() {
        return color.get();
    }

    public void setColor(ActionColor role) {
        color.set(role);
    }
}
