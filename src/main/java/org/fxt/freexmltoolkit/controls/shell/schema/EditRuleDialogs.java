package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * The dialogs of the structural edit rules ({@code XsdEditRules}): a confirmation for an edit
 * that replaces conflicting content (or that changes more than it seems, {@link #confirm}), and a
 * notice for an edit that is not valid XSD.
 *
 * <p>Tests install {@link #setConfirmer} / {@link #setBlockedHandler} so no modal dialog opens;
 * with {@code fxt.suppressErrorDialogs} set and no handler, a blocked notice is only logged.
 */
public final class EditRuleDialogs {

    private static final Logger logger = LogManager.getLogger(EditRuleDialogs.class);

    private static BiPredicate<String, EditCheck> confirmer;
    private static BiConsumer<String, EditCheck> blockedHandler;

    private EditRuleDialogs() {
    }

    /** @param testConfirmer answers replacement confirmations instead of a dialog ({@code null} = dialog) */
    public static void setConfirmer(BiPredicate<String, EditCheck> testConfirmer) {
        confirmer = testConfirmer;
    }

    /** @param handler receives blocked notices instead of a dialog ({@code null} = dialog) */
    public static void setBlockedHandler(BiConsumer<String, EditCheck> handler) {
        blockedHandler = handler;
    }

    /**
     * Asks whether the conflicting content may be replaced.
     *
     * @param owner the owner window, may be {@code null}
     * @param title the dialog title, e.g. "Change Type"
     * @param check a {@link EditCheck.Status#REPLACEABLE} verdict
     * @return {@code true} when the user chose the replacement
     */
    public static boolean confirmReplace(Window owner, String title, EditCheck check) {
        if (confirmer != null) {
            return confirmer.test(title, check);
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText("This change would make the schema invalid");
        alert.setContentText(check.reason());
        ButtonType replace = new ButtonType(check.replaceLabel(), ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(replace, ButtonType.CANCEL);
        style(alert, owner);
        return alert.showAndWait().filter(replace::equals).isPresent();
    }

    /**
     * Asks whether a valid but far-reaching edit may run (e.g. one that changes a shared type).
     * Tests answer it through {@link #setConfirmer} like a replacement confirmation; the check they
     * receive is a {@link EditCheck.Status#REPLACEABLE} verdict carrying {@code message}.
     *
     * @param owner   the owner window, may be {@code null}
     * @param title   the dialog title
     * @param header  the header line
     * @param message what the edit affects
     * @param okLabel the confirming button's text
     * @return {@code true} when the user confirmed
     */
    public static boolean confirm(Window owner, String title, String header, String message, String okLabel) {
        if (confirmer != null) {
            return confirmer.test(title, EditCheck.replaceable(message, okLabel));
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        ButtonType ok = new ButtonType(okLabel, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ok, ButtonType.CANCEL);
        style(alert, owner);
        return alert.showAndWait().filter(ok::equals).isPresent();
    }

    /**
     * Explains why an edit is not possible.
     *
     * @param owner the owner window, may be {@code null}
     * @param title the dialog title
     * @param check a {@link EditCheck.Status#BLOCKED} verdict
     */
    public static void showBlocked(Window owner, String title, EditCheck check) {
        if (blockedHandler != null) {
            blockedHandler.accept(title, check);
            return;
        }
        logger.info("{} not possible: {}", title, check.reason());
        if (Boolean.getBoolean("fxt.suppressErrorDialogs")) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("This change is not valid in XML Schema");
        alert.setContentText(check.reason());
        style(alert, owner);
        alert.showAndWait();
    }

    /**
     * Runs the dialog matching {@code check}.
     *
     * @return {@code null} when the edit must not run, {@code false} to run it as is,
     * {@code true} to run its replacing variant
     */
    public static Boolean resolve(Window owner, String title, EditCheck check) {
        if (check.isBlocked()) {
            showBlocked(owner, title, check);
            return null;
        }
        if (check.needsConfirmation()) {
            return confirmReplace(owner, title, check) ? Boolean.TRUE : null;
        }
        return Boolean.FALSE;
    }

    private static void style(Alert alert, Window owner) {
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        alert.getDialogPane().setMinWidth(460);
    }
}
