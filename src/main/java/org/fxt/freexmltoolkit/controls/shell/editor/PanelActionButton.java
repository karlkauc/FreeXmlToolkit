package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

/**
 * One row of a {@link PanelActionList}: a full-width button with icon and label (style class
 * {@code fxt-action-row}). The row has a fixed height and only changes colour on hover, press
 * and focus. A click on a row with an id is reported as a panel-action usage event.
 *
 * <p>FXML: {@code <PanelActionButton id="fop-preview" iconLiteral="bi-eye" text="Preview PDF"
 * color="NAVIGATE" onAction="#onPreview"/>}; add {@code primary="true"} for the filled variant
 * (one per list at most).
 */
public final class PanelActionButton extends Button {

    private static final int ICON_SIZE = 15;

    private final StringProperty iconLiteral = new SimpleStringProperty(this, "iconLiteral");
    private final ObjectProperty<ActionColor> color =
            new SimpleObjectProperty<>(this, "color", ActionColor.NEUTRAL);
    private final BooleanProperty primary = new SimpleBooleanProperty(this, "primary", false);

    public PanelActionButton() {
        getStyleClass().add(PanelActionList.ROW_STYLE_CLASS);
        setMaxWidth(Double.MAX_VALUE);
        setAlignment(Pos.CENTER_LEFT);
        setMnemonicParsing(false);
        setWrapText(false);
        setTextOverrun(OverrunStyle.ELLIPSIS);
        addEventHandler(ActionEvent.ACTION, e ->
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.panelAction(getId()));

        iconLiteral.addListener(obs -> refreshIcon());
        color.addListener(obs -> refreshIcon());
        primary.addListener((obs, was, is) -> {
            getStyleClass().remove(PanelActionList.PRIMARY_STYLE_CLASS);
            if (is) {
                getStyleClass().add(PanelActionList.PRIMARY_STYLE_CLASS);
            }
            refreshIcon();
        });
    }

    private void refreshIcon() {
        String literal = getIconLiteral();
        if (literal == null || literal.isBlank()) {
            setGraphic(null);
            return;
        }
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(ICON_SIZE);
        // Primary rows leave the colour to CSS (white icon on the workflow fill). Otherwise it
        // is bound, not set: the .fxt-action-row .iconify-icon CSS rule would repaint it.
        ActionColor role = getColor();
        if (!isPrimary() && role != null) {
            SemanticIcon.bind(icon, role);
        }
        setGraphic(icon);
    }

    /** A bundled Bootstrap icon name ({@code bi-*}), or {@code null} for a text-only row. */
    public StringProperty iconLiteralProperty() {
        return iconLiteral;
    }

    public String getIconLiteral() {
        return iconLiteral.get();
    }

    public void setIconLiteral(String literal) {
        iconLiteral.set(literal);
    }

    /** The action-colour role tinting the icon; {@code null} leaves the colour to CSS. */
    public ObjectProperty<ActionColor> colorProperty() {
        return color;
    }

    public ActionColor getColor() {
        return color.get();
    }

    public void setColor(ActionColor role) {
        color.set(role);
    }

    /** Whether the row is rendered as the filled primary variant. */
    public BooleanProperty primaryProperty() {
        return primary;
    }

    public boolean isPrimary() {
        return primary.get();
    }

    public void setPrimary(boolean value) {
        primary.set(value);
    }
}
