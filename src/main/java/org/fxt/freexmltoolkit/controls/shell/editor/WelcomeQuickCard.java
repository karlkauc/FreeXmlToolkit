package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A quick-action card of the Welcome page (New File, Open File, …): icon tile, title and
 * subtitle on one large button.
 *
 * <p>FXML: {@code <WelcomeQuickCard id="welcome-open" iconLiteral="bi-folder2-open"
 * title="Open File" subtitle="Browse your disk" onAction="#onOpen"/>}; add
 * {@code primary="true"} for the filled variant.
 */
public final class WelcomeQuickCard extends Button {

    private final IconifyIcon icon = new IconifyIcon();
    private final StackPane tile = new StackPane(icon);
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final BooleanProperty primary = new SimpleBooleanProperty(this, "primary", false);

    public WelcomeQuickCard() {
        icon.setIconSize(18);
        HBox graphic = new HBox(12, tile, new VBox(2, title, subtitle));
        graphic.setAlignment(Pos.CENTER_LEFT);
        setGraphic(graphic);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("fxt-quick-card");
        primary.addListener((obs, was, is) -> applyVariant());
        applyVariant();
    }

    private void applyVariant() {
        boolean on = isPrimary();
        tile.getStyleClass().setAll(on ? "fxt-card-icon-primary" : "fxt-card-icon");
        title.getStyleClass().removeAll("fxt-card-title", "fxt-card-title-on-primary");
        title.getStyleClass().add(on ? "fxt-card-title-on-primary" : "fxt-card-title");
        subtitle.getStyleClass().removeAll("fxt-card-subtitle", "fxt-card-subtitle-on-primary");
        subtitle.getStyleClass().add(on ? "fxt-card-subtitle-on-primary" : "fxt-card-subtitle");
        getStyleClass().remove("fxt-quick-card-primary");
        if (on) {
            getStyleClass().add("fxt-quick-card-primary");
        }
    }

    /** The tile icon, a bundled Bootstrap icon name ({@code bi-*}). */
    public StringProperty iconLiteralProperty() {
        return icon.iconLiteralProperty();
    }

    public String getIconLiteral() {
        return icon.getIconLiteral();
    }

    public void setIconLiteral(String literal) {
        icon.setIconLiteral(literal);
    }

    /** The card headline. */
    public StringProperty titleProperty() {
        return title.textProperty();
    }

    public String getTitle() {
        return title.getText();
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    /** The muted line below the headline. */
    public StringProperty subtitleProperty() {
        return subtitle.textProperty();
    }

    public String getSubtitle() {
        return subtitle.getText();
    }

    public void setSubtitle(String text) {
        subtitle.setText(text);
    }

    /** Whether the card is rendered as the filled primary variant. */
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
