package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A card of the Settings page: tinted icon tile + title, then the section's controls. The colour
 * (usually the group's, set by {@link SettingsCardGrid}) tints the icon and its tile; the card itself
 * stays neutral.
 *
 * <p>FXML: {@code <SettingsCard title="EDITOR" iconLiteral="bi-pencil-square" color="#17a2b8">}
 * with the section's controls as children.
 */
public final class SettingsCard extends VBox {

    private final IconifyIcon icon = new IconifyIcon();
    private final StackPane tile = new StackPane(icon);
    private final Label label = new Label();
    private final StringProperty color = new SimpleStringProperty(this, "color");

    public SettingsCard() {
        super(8);
        icon.setIconSize(15);
        tile.getStyleClass().add("fxt-settings-card-icon");
        label.getStyleClass().add("fxt-settings-card-title");
        HBox header = new HBox(10, tile, label);
        header.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(header);
        getStyleClass().add("fxt-settings-card");
        color.addListener((obs, was, is) -> applyColor(is));
    }

    private void applyColor(String hex) {
        icon.iconColorProperty().unbind();
        if (hex == null || hex.isBlank()) {
            tile.setStyle(null);
            return;
        }
        // Bound, not set: themed CSS (-fx-icon-color) would otherwise repaint the icon.
        icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(Color.web(hex)));
        tile.setStyle("-fx-background-color: " + hex + "1F;"); // ~12% alpha tint
    }

    /** The card title, shown as given (upper case by convention). */
    public StringProperty titleProperty() {
        return label.textProperty();
    }

    public String getTitle() {
        return label.getText();
    }

    public void setTitle(String title) {
        label.setText(title);
    }

    /** The icon of the tile, a bundled Bootstrap icon name ({@code bi-*}). */
    public StringProperty iconLiteralProperty() {
        return icon.iconLiteralProperty();
    }

    public String getIconLiteral() {
        return icon.getIconLiteral();
    }

    public void setIconLiteral(String literal) {
        icon.setIconLiteral(literal);
    }

    /** The topic colour as a six-digit hex value, e.g. {@code #17a2b8}. */
    public StringProperty colorProperty() {
        return color;
    }

    public String getColor() {
        return color.get();
    }

    public void setColor(String hex) {
        color.set(hex);
    }
}
