package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.fxt.freexmltoolkit.i18n.I18n;

import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks the user what to do with an open document whose file was changed or deleted on disk by
 * another program. An interface so tests can answer without a blocking dialog.
 */
interface ExternalChangePrompt {

    /** How many file names a question lists before it only counts the rest. */
    int MAX_NAMED_FILES = 3;

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
     * Files the document's model pulls in (includes, imports) were changed or deleted. Reloading
     * rebuilds the model from the document's current text, so unsaved text is kept.
     *
     * @param document the document using the files
     * @param files    the changed files, one question for all of them
     * @return the user's choice; dismissing the prompt counts as {@link ChangedChoice#IGNORE}
     */
    ChangedChoice dependencyChanged(OpenDocument document, List<Path> files);

    /**
     * @param document the document using the files
     * @param files    the changed files
     * @return the sentence naming them, e.g. "core.xsd, used by main.xsd, was changed on disk."
     */
    static String dependencySummary(OpenDocument document, List<Path> files) {
        int shown = Math.min(files.size(), MAX_NAMED_FILES);
        String names = files.subList(0, shown).stream()
                .map(file -> file.getFileName().toString())
                .collect(Collectors.joining(", "));
        if (files.size() > shown) {
            names = I18n.t("ExternalChangePrompt.names-and-more", names, files.size() - shown);
        }
        return files.size() == 1
                ? I18n.t("ExternalChangePrompt.used-by-was-changed", names, document.getDisplayName())
                : I18n.t("ExternalChangePrompt.used-by-were-changed", names, document.getDisplayName());
    }

    /**
     * The default prompt: a modal alert owned by the given window.
     *
     * @param owner supplies the owner window (may supply {@code null})
     */
    static ExternalChangePrompt dialogs(Supplier<Window> owner) {
        return new ExternalChangePrompt() {
            @Override
            public ChangedChoice fileChanged(OpenDocument document, boolean dirty) {
                ButtonType reload = new ButtonType(I18n.t("ExternalChangePrompt.reload"), ButtonBar.ButtonData.YES);
                ButtonType ignore = new ButtonType(I18n.t("ExternalChangePrompt.ignore"), ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = alert(owner.get(), I18n.t("ExternalChangePrompt.file-changed-on-disk"),
                        I18n.t("ExternalChangePrompt.was-changed-by-another-program", document.getDisplayName()),
                        dirty ? I18n.t("ExternalChangePrompt.reload-unsaved-lost")
                                : I18n.t("ExternalChangePrompt.do-you-want-to-reload"),
                        reload, ignore);
                return alert.showAndWait().filter(reload::equals).isPresent()
                        ? ChangedChoice.RELOAD : ChangedChoice.IGNORE;
            }

            @Override
            public DeletedChoice fileDeleted(OpenDocument document, boolean dirty) {
                ButtonType keep = new ButtonType(I18n.t("ExternalChangePrompt.keep"), ButtonBar.ButtonData.CANCEL_CLOSE);
                ButtonType close = new ButtonType(I18n.t("ExternalChangePrompt.close"), ButtonBar.ButtonData.NO);
                Alert alert = alert(owner.get(), I18n.t("ExternalChangePrompt.file-deleted-on-disk"),
                        I18n.t("ExternalChangePrompt.no-longer-exists-on-disk", document.getDisplayName()),
                        dirty ? I18n.t("ExternalChangePrompt.keep-or-close-unsaved")
                                : I18n.t("ExternalChangePrompt.keep-or-close"),
                        keep, close);
                return alert.showAndWait().filter(close::equals).isPresent()
                        ? DeletedChoice.CLOSE : DeletedChoice.KEEP;
            }

            @Override
            public ChangedChoice dependencyChanged(OpenDocument document, List<Path> files) {
                ButtonType reload = new ButtonType(I18n.t("ExternalChangePrompt.reload"), ButtonBar.ButtonData.YES);
                ButtonType ignore = new ButtonType(I18n.t("ExternalChangePrompt.ignore"), ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = alert(owner.get(), I18n.t("ExternalChangePrompt.schema-files-changed-on-disk"),
                        dependencySummary(document, files),
                        I18n.t("ExternalChangePrompt.do-you-want-to-reload-schema"),
                        reload, ignore);
                return alert.showAndWait().filter(reload::equals).isPresent()
                        ? ChangedChoice.RELOAD : ChangedChoice.IGNORE;
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
