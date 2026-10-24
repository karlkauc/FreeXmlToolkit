package org.fxt.freexmltoolkit.debugger.ui;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.SymbolBreakpoint;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks for a template / function / mode breakpoint: the kind and the name, with the names the
 * stylesheet declares as suggestions. Layout in {@code pages/dialogs/SymbolBreakpointDialog.fxml}.
 */
public final class SymbolBreakpointDialog extends Dialog<SymbolBreakpoint> {

    /** Asks for a symbol breakpoint; empty on cancel. */
    @FunctionalInterface
    public interface Prompt {
        Optional<SymbolBreakpoint> ask(Window owner, Map<SymbolBreakpoint.Kind, List<String>> suggestions);
    }

    /** The default prompt: this dialog, themed, modal to {@code owner}. */
    public static final Prompt DEFAULT = (owner, suggestions) -> {
        SymbolBreakpointDialog dialog = new SymbolBreakpointDialog(suggestions);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    };

    @FXML private ChoiceBox<SymbolBreakpoint.Kind> kindChoice;
    @FXML private ComboBox<String> nameCombo;

    public SymbolBreakpointDialog(Map<SymbolBreakpoint.Kind, List<String>> suggestions) {
        setTitle("Template / Function Breakpoint");
        setHeaderText("Pause whenever this template, function or mode is entered");
        IconifyIcon icon = new IconifyIcon("bi-braces-asterisk");
        icon.setIconSize(32);
        setGraphic(icon);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SymbolBreakpointDialog.fxml"));
        try {
            getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }
        Map<SymbolBreakpoint.Kind, List<String>> names = suggestions == null ? Map.of() : suggestions;
        kindChoice.getItems().setAll(SymbolBreakpoint.Kind.values());
        kindChoice.valueProperty().addListener((obs, old, kind) ->
                nameCombo.getItems().setAll(names.getOrDefault(kind, List.of())));
        kindChoice.setValue(SymbolBreakpoint.Kind.TEMPLATE_MATCH);
        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(nameCombo.getEditor().textProperty().isEmpty()
                .and(nameCombo.valueProperty().isNull()));
        setResultConverter(button -> button == ButtonType.OK ? current() : null);
    }

    /** @return the breakpoint the controls describe, or {@code null} without a name. */
    SymbolBreakpoint current() {
        String typed = nameCombo.getEditor().getText();
        String name = typed != null && !typed.isBlank() ? typed : nameCombo.getValue();
        return name == null || name.isBlank() ? null : new SymbolBreakpoint(kindChoice.getValue(), name, true);
    }

    /** Sets the controls (tests). */
    void set(SymbolBreakpoint.Kind kind, String name) {
        kindChoice.setValue(kind);
        nameCombo.getEditor().setText(name);
    }

    /** The suggested names for the chosen kind (tests). */
    List<String> suggestions() {
        return List.copyOf(nameCombo.getItems());
    }
}
