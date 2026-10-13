package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.theme.WorkflowStyle;

/**
 * A card of the Welcome page's Tools grid: workflow-coloured icon chip, title, subtitle and a
 * chevron; clicking it switches to the shell activity named by {@link #activityProperty()}.
 *
 * <p>FXML: {@code <WelcomeToolCard id="tool-schema" activity="schema" iconLiteral="bi-diagram-3"
 * title="Schema" subtitle="Graphical XSD editor" onAction="#onTool"/>}.
 */
public final class WelcomeToolCard extends Button {

    private final IconifyIcon icon = new IconifyIcon();
    private final StackPane tile = new StackPane(icon);
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final StringProperty activity = new SimpleStringProperty(this, "activity");

    public WelcomeToolCard() {
        icon.setIconSize(16);
        tile.getStyleClass().add("fxt-card-icon");
        title.getStyleClass().add("fxt-card-title");
        subtitle.getStyleClass().add("fxt-card-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        IconifyIcon chevron = new IconifyIcon("bi-chevron-right");
        chevron.setIconSize(14);
        HBox graphic = new HBox(12, tile, new VBox(2, title, subtitle), spacer, chevron);
        graphic.setAlignment(Pos.CENTER_LEFT);
        // Make the graphic fill the button so the chevron sits flush right (CSS padding is 16).
        graphic.prefWidthProperty().bind(widthProperty().subtract(32));

        setGraphic(graphic);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("fxt-tool-card");

        // Workflow colour (spec 2026-09-26 §2, Welcome tool cards): bg tint + accent icon on the
        // chip, and the class on the card itself for the hover border (.fxt-tool-card.fxt-wf-<id>:hover).
        activity.addListener((obs, was, is) -> {
            WorkflowStyle.clear(tile);
            WorkflowStyle.clear(this);
            Activity.fromId(is).ifPresent(a -> {
                WorkflowStyle.apply(tile, a.workflow());
                WorkflowStyle.apply(this, a.workflow());
            });
        });
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

    /** The id of the shell activity the card opens, e.g. {@code schema}. */
    public StringProperty activityProperty() {
        return activity;
    }

    public String getActivity() {
        return activity.get();
    }

    public void setActivity(String id) {
        activity.set(id);
    }
}
