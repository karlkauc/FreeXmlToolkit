package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * An editable type-reference field ({@code type="…"} of an element or attribute) with a dropdown
 * that offers every built-in datatype and every named type of the schema, each with an
 * explanation ({@link TypePickerPopup}). Free text stays possible for references the picker does
 * not know.
 *
 * <p>Commit rules: Enter, picking an entry and leaving the field commit the text when it differs
 * from the last committed value; {@link #setValue(String)} never fires {@link #setOnCommit(Consumer)}
 * (hosts repopulate freely). Escape closes the dropdown, a second Escape restores the committed value.
 * Typing, Down, Ctrl+Space and the button open the dropdown; typed text filters it.
 *
 * <p>Layout in {@code pages/views/TypePickerField.fxml} (fx:root). The FXML carries no {@code id}s;
 * hosts call {@link #setId(String)} for tests.
 */
public final class TypePickerField extends HBox {

    @FXML private TextField typeField;
    @FXML private Button pickButton;

    private final ReadOnlyStringWrapper value = new ReadOnlyStringWrapper(this, "value", "");
    private Supplier<List<TypeChoice>> choices = List::of;
    private Predicate<TypeChoice> choiceFilter = c -> true;
    private Consumer<String> onCommit;
    private String committed = "";
    private boolean settingText;
    private TypePickerPopup popup;

    public TypePickerField() {
        FxmlViews.load(this, "/pages/views/TypePickerField.fxml");
        typeField.textProperty().addListener((o, ov, nv) -> {
            if (!settingText) {
                onTyped(nv);
            }
        });
        typeField.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        typeField.focusedProperty().addListener((o, was, now) -> {
            if (!now && !isPopupShowing()) {
                commitText();
            }
        });
        sceneProperty().addListener((o, os, ns) -> {
            if (ns == null) {
                hidePopup();
            }
        });
        disabledProperty().addListener((o, was, now) -> {
            if (now) {
                hidePopup();
            }
        });
    }

    // ----- API -----------------------------------------------------------------------------

    /** @param choices supplies the entries each time the dropdown opens (schema types may change). */
    public void setChoices(Supplier<List<TypeChoice>> choices) {
        this.choices = choices == null ? List::of : choices;
    }

    /** @param filter keeps only matching entries (e.g. simple types for a base-type field). */
    public void setChoiceFilter(Predicate<TypeChoice> filter) {
        this.choiceFilter = filter == null ? c -> true : filter;
    }

    /** @param handler receives the committed type reference (trimmed, may be empty) on user commits only. */
    public void setOnCommit(Consumer<String> handler) {
        this.onCommit = handler;
    }

    /** The last committed value (user commit or {@link #setValue(String)}). */
    public ReadOnlyStringProperty valueProperty() {
        return value.getReadOnlyProperty();
    }

    public String getValue() {
        return value.get();
    }

    /** Sets the shown and committed value without firing the commit handler. */
    public void setValue(String text) {
        String v = text == null ? "" : text.trim();
        hidePopup();
        committed = v;
        setText(v);
        value.set(v);
    }

    /** @return the text field, for styling and tests. */
    public TextField getEditor() {
        return typeField;
    }

    public void setPromptText(String prompt) {
        typeField.setPromptText(prompt);
    }

    public void setEditable(boolean editable) {
        typeField.setEditable(editable);
        pickButton.setDisable(!editable);
    }

    /** Opens the dropdown unfiltered with the current value highlighted. */
    public void showPopup() {
        openPopup(false);
    }

    public void hidePopup() {
        if (popup != null) {
            popup.hide();
        }
    }

    public boolean isPopupShowing() {
        return popup != null && popup.isShowing();
    }

    /** The dropdown button's handler. */
    @FXML
    public void onPick() {
        if (isPopupShowing()) {
            hidePopup();
        } else {
            typeField.requestFocus();
            openPopup(false);
        }
    }

    // ----- behaviour -----------------------------------------------------------------------

    private void onTyped(String text) {
        if (!typeField.isEditable()) {
            return;
        }
        if (isPopupShowing()) {
            popup.setFilter(text);
        } else if (typeField.isFocused()) {
            openPopup(true);
        }
    }

    private void openPopup(boolean filterByText) {
        if (!typeField.isEditable() || isDisabled()) {
            return;
        }
        if (popup == null) {
            popup = new TypePickerPopup();
        }
        List<TypeChoice> list = choices.get().stream().filter(choiceFilter).toList();
        popup.show(typeField, list, filterByText ? typeField.getText() : "", committed, this::pick);
    }

    private void pick(TypeChoice choice) {
        setText(choice.qualifiedName());
        typeField.positionCaret(typeField.getText().length());
        commitText();
    }

    private void onKeyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case DOWN -> {
                if (isPopupShowing()) {
                    popup.moveSelection(1);
                } else {
                    openPopup(false);
                }
                e.consume();
            }
            case UP -> {
                if (isPopupShowing()) {
                    popup.moveSelection(-1);
                    e.consume();
                }
            }
            case PAGE_DOWN -> {
                if (isPopupShowing()) {
                    popup.movePage(1);
                    e.consume();
                }
            }
            case PAGE_UP -> {
                if (isPopupShowing()) {
                    popup.movePage(-1);
                    e.consume();
                }
            }
            case ENTER -> {
                if (isPopupShowing()) {
                    if (!popup.pickSelected()) {
                        hidePopup();
                        commitText();
                    }
                    e.consume();
                } else {
                    // not consumed: a dialog's default button still fires after the commit
                    commitText();
                }
            }
            case ESCAPE -> {
                if (isPopupShowing()) {
                    hidePopup();
                    e.consume();
                } else if (!typeField.getText().trim().equals(committed)) {
                    setText(committed);
                    e.consume();
                }
            }
            case SPACE -> {
                if (e.isControlDown()) {
                    openPopup(false);
                    e.consume();
                }
            }
            case TAB -> hidePopup();
            default -> {
                // typing is handled by the text listener
            }
        }
    }

    private void commitText() {
        String text = typeField.getText() == null ? "" : typeField.getText().trim();
        if (text.equals(committed)) {
            return;
        }
        committed = text;
        value.set(text);
        if (onCommit != null) {
            onCommit.accept(text);
        }
    }

    private void setText(String text) {
        settingText = true;
        try {
            typeField.setText(text);
        } finally {
            settingText = false;
        }
    }

    /** @return the popup instance, created on demand (tests and hosts that inspect the offered rows). */
    public TypePickerPopup popup() {
        if (popup == null) {
            popup = new TypePickerPopup();
        }
        return popup;
    }
}
