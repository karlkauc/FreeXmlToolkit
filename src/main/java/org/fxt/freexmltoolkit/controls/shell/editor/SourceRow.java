package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A bound-source slot shared by the Validation, Transform, FOP and Signature panels, laid
 * out on two lines so the file name never competes with the row's controls:
 * <pre>
 *   [icon] CAPTION ············ (extras such as a favourite star) [Change]
 *          file-name-shortened-in-the…middle.xsl
 * </pre>
 * The name uses the full panel width, ellipsises in the middle (start and extension stay
 * readable) and shows the full path as tooltip. While no file is bound it shows the
 * {@linkplain #emptyTextProperty() empty text} (e.g. "No stylesheet — choose or drop a
 * file") in a muted style, and a click on it acts like "Change". Keeps the
 * {@code fxt-vp-source-row} style class so drag-and-drop feedback is untouched.
 *
 * <p>FXML: {@code <SourceRow iconLiteral="bi-code-slash" caption="XSD"
 * emptyText="No XSD — choose or drop a file" onChange="#onChangeXml">} with the
 * file-name label in a {@code <name>} element; extras built in code go in via
 * {@link #addExtras(Node...)}.
 */
public final class SourceRow extends VBox {

    /** Style class of the name label while no source is bound. */
    static final String NONE_STYLE_CLASS = "fxt-vp-source-none";
    private static final String DEFAULT_EMPTY_TEXT = "No file — choose or drop one";

    private final IconifyIcon icon = new IconifyIcon();
    private final Label captionLabel = new Label();
    private final HBox head;
    private final Button change;
    private final ObjectProperty<EventHandler<ActionEvent>> onChange =
            new SimpleObjectProperty<>(this, "onChange");
    private final StringProperty caption = new SimpleStringProperty(this, "caption", "");
    private final StringProperty emptyText = new SimpleStringProperty(this, "emptyText", DEFAULT_EMPTY_TEXT);
    private Label name;

    public SourceRow() {
        super(2);
        icon.setIconSize(15);
        captionLabel.getStyleClass().add("fxt-vp-source-caption");
        captionLabel.textProperty().bind(caption);
        captionLabel.visibleProperty().bind(caption.isNotEmpty());
        captionLabel.managedProperty().bind(captionLabel.visibleProperty());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // Text-only: the head line already carries the file-type icon and up to five extras
        // (favourites, browse arrows), so the button must stay narrow; its outline keeps
        // it recognisable as a button.
        change = PanelActionList.inlineRow("Change", null, this::fireChange);
        // Tight spacing: the head line can carry up to five extras (recent, star, ◀ pos ▶).
        head = new HBox(4, icon, captionLabel, spacer, change);
        head.getStyleClass().add("fxt-vp-source-head");
        head.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(head);
        getStyleClass().add("fxt-vp-source-row");
        emptyText.addListener((obs, oldV, newV) -> {
            if (name != null && name.getStyleClass().contains(NONE_STYLE_CLASS)) {
                name.setText(newV);
            }
        });
    }

    SourceRow(String iconLiteral, Label nameLabel, Runnable changeAction, Node... extras) {
        this();
        setIconLiteral(iconLiteral);
        setName(nameLabel);
        setOnChange(e -> changeAction.run());
        addExtras(extras);
    }

    /** Adds trailing controls (e.g. a favourites menu) on the head line, before "Change". */
    public void addExtras(Node... extras) {
        head.getChildren().addAll(head.getChildren().indexOf(change), java.util.List.of(extras));
    }

    /** @return the head line: icon · caption · spacer · extras · "Change" (for tests) */
    HBox head() {
        return head;
    }

    /** @return the "Change" button (for tests and for callers that want to disable it) */
    Button changeButton() {
        return change;
    }

    private void fireChange() {
        EventHandler<ActionEvent> handler = getOnChange();
        if (handler != null) {
            handler.handle(new ActionEvent(this, this));
        }
    }

    // ----- shown source ---------------------------------------------------------------

    /**
     * Shows a bound file: its name (middle-ellipsised) with the full path as tooltip, or
     * the muted empty text when {@code file} is {@code null}.
     */
    public void showFile(File file) {
        showName(file != null ? file.getName() : null, file != null ? file.getAbsolutePath() : null);
    }

    /**
     * Shows a source by display name (e.g. an unsaved editor document) with an optional
     * tooltip, or the muted empty text when {@code displayName} is {@code null}.
     */
    public void showName(String displayName, String tooltip) {
        if (name == null) {
            return;
        }
        boolean none = displayName == null;
        name.setText(none ? getEmptyText() : displayName);
        // The empty hint is a sentence: wrap it; a bound name stays one line, cut in the middle.
        name.setWrapText(none);
        name.getStyleClass().remove(NONE_STYLE_CLASS);
        if (none) {
            name.getStyleClass().add(NONE_STYLE_CLASS);
        }
        String tip = none ? getEmptyText() : tooltip;
        if (tip == null || tip.isBlank()) {
            name.setTooltip(null);
        } else if (name.getTooltip() == null) {
            name.setTooltip(new Tooltip(tip));
        } else {
            name.getTooltip().setText(tip);
        }
    }

    /** @return whether the row currently shows its empty text (no source bound) */
    public boolean isEmpty() {
        return name == null || name.getStyleClass().contains(NONE_STYLE_CLASS);
    }

    // ----- properties -----------------------------------------------------------------

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

    /**
     * Short role of the slot shown on the head line (e.g. "XSD", "Schematron"); empty
     * hides it. Only needed where one section holds several slots.
     */
    public StringProperty captionProperty() {
        return caption;
    }

    public String getCaption() {
        return caption.get();
    }

    public void setCaption(String value) {
        caption.set(value != null ? value : "");
    }

    /** Text shown while no source is bound; it names the action that fills the slot. */
    public StringProperty emptyTextProperty() {
        return emptyText;
    }

    public String getEmptyText() {
        return emptyText.get();
    }

    public void setEmptyText(String value) {
        emptyText.set(value != null && !value.isBlank() ? value : DEFAULT_EMPTY_TEXT);
    }

    /** @return the label showing the bound file's name, on the second line */
    public Label getName() {
        return name;
    }

    public void setName(Label nameLabel) {
        getChildren().remove(name);
        name = nameLabel;
        if (nameLabel == null) {
            return;
        }
        nameLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setMinWidth(0);
        nameLabel.getStyleClass().add("fxt-vp-source-name-line");
        // An empty slot is a target: clicking its text behaves like "Change". Added as a
        // handler (not setOnMouseClicked) so panel click handlers, e.g. open-in-editor,
        // keep working for bound files.
        nameLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && nameLabel.getStyleClass().contains(NONE_STYLE_CLASS)) {
                fireChange();
            }
        });
        // FXML initial text "none" (or empty) means unset: show the empty text instead.
        String initial = nameLabel.getText();
        if (initial == null || initial.isBlank() || "none".equals(initial)
                || nameLabel.getStyleClass().contains(NONE_STYLE_CLASS)) {
            if (!nameLabel.getStyleClass().contains(NONE_STYLE_CLASS)) {
                nameLabel.getStyleClass().add(NONE_STYLE_CLASS);
            }
            nameLabel.setText(getEmptyText());
            nameLabel.setWrapText(true);
        }
        getChildren().add(nameLabel);
    }

    /** Invoked when the user clicks "Change" (or an empty slot). */
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
