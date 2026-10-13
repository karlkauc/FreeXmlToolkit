package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A dashboard counter card of the Welcome page: icon tile, a large number and a caption.
 *
 * <p>FXML: {@code <WelcomeStatCard iconLiteral="bi-star" caption="Favorites"
 * numberId="welcome-stat-favorites"/>}; the number is set through {@link #setValue(String)}.
 */
public final class WelcomeStatCard extends HBox {

    private final IconifyIcon icon = new IconifyIcon();
    private final Label number = new Label("0");
    private final Label caption = new Label();

    public WelcomeStatCard() {
        super(12);
        icon.setIconSize(16);
        StackPane tile = new StackPane(icon);
        tile.getStyleClass().add("fxt-card-icon");
        number.getStyleClass().add("fxt-welcome-stat-number");
        caption.getStyleClass().add("fxt-welcome-stat-label");
        getChildren().addAll(tile, new VBox(0, number, caption));
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("fxt-welcome-stat-card");
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

    /** The text below the number. */
    public StringProperty captionProperty() {
        return caption.textProperty();
    }

    public String getCaption() {
        return caption.getText();
    }

    public void setCaption(String text) {
        caption.setText(text);
    }

    /** The displayed number. */
    public StringProperty valueProperty() {
        return number.textProperty();
    }

    public String getValue() {
        return number.getText();
    }

    public void setValue(String text) {
        number.setText(text);
    }

    /** The node id of the number label (for tests and lookups). */
    public String getNumberId() {
        return number.getId();
    }

    public void setNumberId(String id) {
        number.setId(id);
    }
}
