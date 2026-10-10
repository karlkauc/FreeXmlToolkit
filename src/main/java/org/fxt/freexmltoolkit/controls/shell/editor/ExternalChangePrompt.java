package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Supplier;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks the user what to do with an open document whose file was changed or deleted on disk by
 * another program. An interface so tests can answer without a blocking dialog.
 */
interface ExternalChangePrompt {

    /** Answer to "the file changed on disk". */
    enum ChangedChoice { RELOAD, IGNORE }

    /** Answer to "the file was deleted on disk". */
    enum DeletedChoice { KEEP, CLOSE }

    /**
     * @param document the affected document
     * @param dirty    whether it has unsaved changes a reload would discard
     * @return the user's choice; dismissing the prompt counts as {@link ChangedChoice#IGNORE}
     */
    ChangedChoice fileChanged(OpenDocument document, boolean dirty);

    /**
     * @param document the affected document
     * @param dirty    whether it has unsaved changes closing would discard
     * @return the user's choice; dismissing the prompt counts as {@link DeletedChoice#KEEP}
     */
    DeletedChoice fileDeleted(OpenDocument document, boolean dirty);

    /**
     * The default prompt: a modal alert owned by the given window.
     *
     * @param owner supplies the owner window (may supply {@code null})
     */
    static ExternalChangePrompt dialogs(Supplier<Window> owner) {
        return new ExternalChangePrompt() {
            @Override
            public ChangedChoice fileChanged(OpenDocument document, boolean dirty) {
                ButtonType reload = new ButtonType("Reload", ButtonBar.ButtonData.YES);
                ButtonType ignore = new ButtonType("Ignore", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = alert(owner.get(), "File Changed on Disk",
                        document.getDisplayName() + " was changed by another program.",
                        dirty ? "Do you want to reload it from disk? "
                                + "Your unsaved changes will be lost if you reload."
                                : "Do you want to reload it from disk?",
                        reload, ignore);
                return alert.showAndWait().filter(reload::equals).isPresent()
                        ? ChangedChoice.RELOAD : ChangedChoice.IGNORE;
            }

            @Override
            public DeletedChoice fileDeleted(OpenDocument document, boolean dirty) {
                ButtonType keep = new ButtonType("Keep", ButtonBar.ButtonData.CANCEL_CLOSE);
                ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.NO);
                Alert alert = alert(owner.get(), "File Deleted on Disk",
                        document.getDisplayName() + " no longer exists on disk.",
                        "Keep it open in the editor (saving recreates the file) or close the tab?"
                                + (dirty ? " Closing discards your unsaved changes." : ""),
                        keep, close);
                return alert.showAndWait().filter(close::equals).isPresent()
                        ? DeletedChoice.CLOSE : DeletedChoice.KEEP;
            }
        };
    }

    private static Alert alert(Window owner, String title, String header, String content,
                               ButtonType... buttons) {
        Alert alert = DialogHelper.createStyledAlert(Alert.AlertType.WARNING, title, header, content);
        alert.getButtonTypes().setAll(buttons);
        if (owner != null) {
            alert.initOwner(owner);
        }
        return alert;
    }
}
