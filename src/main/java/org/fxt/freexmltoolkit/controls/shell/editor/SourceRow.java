package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A bound-source row shared by the Validation, Transform, FOP and Signature panels:
 * file-type icon · file name · (extras such as a favourite star) · a visible "Change"
 * button. Keeps the {@code fxt-vp-source-row} style class so the drag-and-drop feedback
 * and click-to-open behaviour of the existing rows are untouched.
 *
 * <p>FXML: {@code <SourceRow iconLiteral="bi-code-slash" onChange="#onChangeXml">} with the
 * file-name label in a {@code <name>} element; extras built in code go in via
 * {@link #addExtras(Node...)}.
 */
public final class SourceRow extends HBox {

    private final IconifyIcon icon = new IconifyIcon();
    private final Button change;
    private final ObjectProperty<EventHandler<ActionEvent>> onChange =
            new SimpleObjectProperty<>(this, "onChange");
    private Label name;

    public SourceRow() {
        super(8);
        icon.setIconSize(15);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // Text-only: the row already carries the file-type icon and up to five extras
        // (favourites, browse arrows), so the button must stay narrow; its outline keeps
        // it recognisable as a button.
        change = PanelActionList.inlineRow("Change", null, () -> {
            EventHandler<ActionEvent> handler = getOnChange();
            if (handler != null) {
                handler.handle(new ActionEvent(this, this));
            }
        });
        getChildren().addAll(icon, spacer, change);
        getStyleClass().add("fxt-vp-source-row");
        setAlignment(Pos.CENTER_LEFT);
    }

    SourceRow(String iconLiteral, Label nameLabel, Runnable changeAction, Node... extras) {
        this();
        setIconLiteral(iconLiteral);
        setName(nameLabel);
        setOnChange(e -> changeAction.run());
        addExtras(extras);
    }

    /** Adds trailing controls (e.g. a favourites menu) between the file name and "Change". */
    public void addExtras(Node... extras) {
        getChildren().addAll(getChildren().indexOf(change), java.util.List.of(extras));
    }

    /** @return the "Change" button (for tests and for callers that want to disable it) */
    Button changeButton() {
        return change;
    }

    /** The file-type icon, a bundled Bootstrap icon name ({@code bi-*}). */
    public StringProperty iconLiteralProperty() {
        return icon.iconLiteralProperty();
    }

    public String getIconLiteral() {
        return icon.getIconLiteral();
    }

    public void setIconLiteral(String literal) {
        icon.setIconLiteral(literal);
    }

    /** @return the label showing the bound file's name, right after the icon */
    public Label getName() {
        return name;
    }

    public void setName(Label nameLabel) {
        getChildren().remove(name);
        name = nameLabel;
        if (nameLabel != null) {
            getChildren().add(1, nameLabel);
        }
    }

    /** Invoked when the user clicks "Change". */
    public ObjectProperty<EventHandler<ActionEvent>> onChangeProperty() {
        return onChange;
    }

    public EventHandler<ActionEvent> getOnChange() {
        return onChange.get();
    }

    public void setOnChange(EventHandler<ActionEvent> handler) {
        onChange.set(handler);
    }
}
