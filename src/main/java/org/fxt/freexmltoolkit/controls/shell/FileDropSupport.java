package org.fxt.freexmltoolkit.controls.shell;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import org.fxt.freexmltoolkit.service.DragDropService;

/**
 * Installs single-file drag-and-drop support on a file-picker control (e.g. the Explorer's
 * stylesheet/Schematron MenuButtons or a side-panel source row) with live visual feedback:
 * while a matching file hovers the target it gets {@link #DROP_VALID_CLASS} (success tint),
 * a non-matching file gets {@link #DROP_INVALID_CLASS} (danger tint) and is rejected.
 *
 * <p>Both the drag-over and the drop event are consumed whenever the dragboard carries files,
 * so the shell-wide open-file drop handlers ({@code UnifiedShellView}/{@code EditorHost})
 * never also open a file that was aimed at the picker. Dragboards without files (internal
 * node drags such as section reordering) pass through untouched.</p>
 */
public final class FileDropSupport {

    /** Style class applied to the target while a loadable file hovers it. */
    public static final String DROP_VALID_CLASS = "fxt-drop-valid";

    /** Style class applied to the target while a non-loadable file hovers it. */
    public static final String DROP_INVALID_CLASS = "fxt-drop-invalid";

    private FileDropSupport() {
    }

    /**
     * Enables drop handling on {@code target}: the first dropped regular file whose name ends
     * with one of {@code allowedExtensions} (case-insensitive, e.g. {@code ".xsl"}) is passed
     * to {@code onFileDropped}.
     */
    public static void install(Node target, List<String> allowedExtensions, Consumer<File> onFileDropped) {
        target.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (!dragboard.hasFiles()) {
                return; // internal drag (no files) — let it bubble to other handlers
            }
            if (applyDragOverFeedback(target, dragboard.getFiles(), allowedExtensions)) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        target.setOnDragExited(event -> clearFeedback(target));
        target.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                Optional<File> match = firstMatch(dragboard.getFiles(), allowedExtensions);
                if (match.isPresent()) {
                    onFileDropped.accept(match.get());
                    success = true;
                }
            }
            clearFeedback(target);
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /** @return the first regular file matching one of the allowed extensions. */
    static Optional<File> firstMatch(List<File> files, List<String> allowedExtensions) {
        return files.stream()
                .filter(File::isFile)
                .filter(file -> DragDropService.matchesExtension(file, allowedExtensions))
                .findFirst();
    }

    /**
     * Toggles the valid/invalid feedback class on {@code target} for the hovered files.
     * Idempotent — {@link DragEvent#DRAG_OVER} fires continuously during a hover.
     *
     * @return true if the hovered files contain a loadable file (drop should be accepted)
     */
    static boolean applyDragOverFeedback(Node target, List<File> files, List<String> allowedExtensions) {
        boolean valid = firstMatch(files, allowedExtensions).isPresent();
        setFeedbackClass(target, valid ? DROP_VALID_CLASS : DROP_INVALID_CLASS,
                valid ? DROP_INVALID_CLASS : DROP_VALID_CLASS);
        return valid;
    }

    /** Removes both feedback classes from {@code target}. */
    static void clearFeedback(Node target) {
        target.getStyleClass().removeAll(DROP_VALID_CLASS, DROP_INVALID_CLASS);
    }

    private static void setFeedbackClass(Node target, String add, String remove) {
        target.getStyleClass().remove(remove);
        if (!target.getStyleClass().contains(add)) {
            target.getStyleClass().add(add);
        }
    }
}
