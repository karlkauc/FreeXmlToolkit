package org.fxt.freexmltoolkit.debugger.ui;

import java.util.Optional;

import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks for a breakpoint condition (XPath). Shared by the editor gutter's context menu and the
 * debugger's Breakpoints panel. An empty answer means "unconditional".
 */
public final class BreakpointConditionDialog {

    /** Asks for a condition; returns empty when the user cancels. */
    @FunctionalInterface
    public interface Prompt {
        Optional<String> ask(Window owner, String location, String current);
    }

    /** The default prompt: a themed text input dialog. */
    public static final Prompt DEFAULT = BreakpointConditionDialog::show;

    private BreakpointConditionDialog() {
    }

    /**
     * @param owner    owner window (may be {@code null})
     * @param location e.g. "main.xsl, line 12"
     * @param current  the current condition ({@code ""} for none)
     * @return the new condition ({@code ""} = unconditional), or empty on cancel
     */
    public static Optional<String> show(Window owner, String location, String current) {
        TextInputDialog dialog = new TextInputDialog(current == null ? "" : current);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Breakpoint Condition");
        dialog.setHeaderText("Pause at " + location + " only when this XPath is true");
        dialog.setContentText("Condition:");
        IconifyIcon icon = new IconifyIcon("bi-funnel");
        icon.setIconSize(32);
        dialog.setGraphic(icon);
        dialog.getEditor().setPromptText("e.g. @id = '42', $count > 3, position() = last()");
        dialog.getEditor().setPrefColumnCount(40);
        dialog.getDialogPane().setExpandableContent(new javafx.scene.control.Label(
                "Evaluated each time the line runs, like xsl:if test: the context item and every\n"
                        + "variable in scope are available. Leave empty for an unconditional breakpoint.\n"
                        + "If the expression fails, the run pauses and the status shows the error."));
        try {
            dialog.getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }
        return dialog.showAndWait().map(String::strip);
    }
}
