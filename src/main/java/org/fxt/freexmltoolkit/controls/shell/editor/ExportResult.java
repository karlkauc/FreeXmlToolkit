package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.DesktopActions;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * The one confirmation after an export (Excel, CSV, reports, ZIP, a folder of files …): "… saved" with the path and
 * the buttons <b>Open</b> (the file in the program the system uses for it, or the folder), <b>Show in Folder</b>
 * (selected in Explorer / Finder / the file manager) and <b>OK</b>. "Don't show again" switches to a short note at
 * the bottom right with an Open link (setting {@value #CONFIRM_KEY}, back on in Settings → Files).
 *
 * <p>Called from the UI only (panels, success branch on the FX thread) - never from runners or exporters, so the
 * command line never shows it. Exports whose result already shows (a PDF preview, a tab, documentation opened in the
 * browser) do not call it.
 */
public final class ExportResult {

    private static final Logger logger = LogManager.getLogger(ExportResult.class);

    /** Setting: show the confirmation dialog (default) or only a note with an Open link. */
    public static final String CONFIRM_KEY = "export.confirm";
    /** System property: no dialog and no note at all (the test task sets it). */
    static final String SUPPRESS_PROPERTY = "fxt.suppressExportDialogs";

    /**
     * An export that finished.
     *
     * @param what   what was exported, in the user's language ("Problems", "Schema analysis report" …)
     * @param target the file, or the folder of a folder export
     * @param folder whether {@code target} is a folder
     * @param files  the number of files written into the folder (0 for a file)
     */
    public record Saved(String what, Path target, boolean folder, int files) {
    }

    /** Test hook: gets the finished export instead of the dialog / note. */
    static volatile Consumer<Saved> handler;
    /** Test hook: opens ({@code "open"}) or reveals ({@code "reveal"}) a file instead of the desktop. */
    static volatile BiConsumer<String, File> desktop;

    private ExportResult() {
    }

    /** A file was written. FX thread. */
    public static void saved(Window owner, String what, Path file) {
        show(owner, new Saved(what, file, false, 0));
    }

    /** A file was written. FX thread. */
    public static void saved(Window owner, String what, File file) {
        saved(owner, what, file.toPath());
    }

    /** Files were written into a folder. FX thread. */
    public static void savedFolder(Window owner, String what, Path folder, int files) {
        show(owner, new Saved(what, folder, true, files));
    }

    private static void show(Window owner, Saved saved) {
        Consumer<Saved> test = handler;
        if (test != null) {
            test.accept(saved);
            return;
        }
        if (Boolean.getBoolean(SUPPRESS_PROPERTY)) {
            logger.debug("Export confirmation suppressed: {}", saved.target());
            return;
        }
        if (!confirmEnabled()) {
            note(owner, saved);
            return;
        }
        dialog(owner, saved);
    }

    /** @return whether the dialog is shown (setting {@value #CONFIRM_KEY}, on unless switched off) */
    static boolean confirmEnabled() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(CONFIRM_KEY);
            return value == null || value.isBlank() || Boolean.parseBoolean(value.strip());
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void setConfirmEnabled(boolean on) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(CONFIRM_KEY, String.valueOf(on));
        } catch (RuntimeException e) {
            logger.debug("Export confirmation setting not saved: {}", e.toString());
        }
    }

    /** The confirmation: path, Open · Show in Folder · OK, "Don't show again". */
    private static void dialog(Window owner, Saved saved) {
        Alert alert = DialogHelper.createStyledAlert(Alert.AlertType.INFORMATION, I18n.t("ExportResult.title"),
                I18n.t("ExportResult.saved-header", saved.what()), null);
        if (owner != null && alert.getOwner() == null) {
            try {
                alert.initOwner(owner);
            } catch (IllegalStateException ignored) {
                // owner already set by DialogHelper
            }
        }
        TextField path = new TextField(saved.target().toAbsolutePath().toString());
        path.setEditable(false);
        path.setId("export-result-path");
        path.setAccessibleText(I18n.t("ExportResult.path"));
        path.setPrefColumnCount(42);
        VBox content = new VBox(8, path);
        if (saved.folder()) {
            content.getChildren().add(new Label(I18n.t("ExportResult.folder-files", saved.files())));
        }
        CheckBox dontShow = new CheckBox(I18n.t("ExportResult.dont-show"));
        dontShow.setId("export-result-dont-show");
        content.getChildren().add(dontShow);
        alert.getDialogPane().setContent(content);

        ButtonType open = new ButtonType(I18n.t(saved.folder() ? "ExportResult.open-folder" : "ExportResult.open"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType reveal = new ButtonType(I18n.t("ExportResult.reveal"), ButtonBar.ButtonData.OTHER);
        ButtonType close = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(open, reveal, close);
        alert.showAndWait().ifPresent(choice -> {
            if (choice == open) {
                open(saved);
            } else if (choice == reveal) {
                reveal(saved);
            }
        });
        if (dontShow.isSelected()) {
            setConfirmEnabled(false);
        }
    }

    /** The short note at the bottom right with an Open link (when the dialog is switched off). */
    private static void note(Window owner, Saved saved) {
        Window window = owner != null ? owner : DialogHelper.defaultOwner();
        String name = saved.target().getFileName() == null ? saved.target().toString()
                : saved.target().getFileName().toString();
        ToastNotification.success(window, I18n.t("ExportResult.toast", saved.what(), name),
                I18n.t(saved.folder() ? "ExportResult.open-folder" : "ExportResult.open"), () -> open(saved));
    }

    /** Opens the file in its program (or the folder). */
    static void open(Saved saved) {
        File file = saved.target().toFile();
        BiConsumer<String, File> test = desktop;
        if (test != null) {
            test.accept("open", file);
            return;
        }
        DesktopActions.open(file, e -> failed(saved, e));
    }

    /** Shows the file selected in the file manager (a folder is opened). */
    static void reveal(Saved saved) {
        File file = saved.target().toFile();
        BiConsumer<String, File> test = desktop;
        if (test != null) {
            test.accept("reveal", file);
            return;
        }
        if (saved.folder()) {
            DesktopActions.open(file, e -> failed(saved, e));
        } else {
            DesktopActions.reveal(file, e -> failed(saved, e));
        }
    }

    private static void failed(Saved saved, Exception e) {
        File file = saved.target().toFile();
        DialogHelper.showActionError(I18n.t("ExportResult.title"),
                I18n.t("ExportResult.open-failed", file.getName()),
                I18n.t("ExportResult.open-remedy", file.getParent() == null ? file.getPath() : file.getParent()),
                String.valueOf(e.getMessage()));
    }
}
