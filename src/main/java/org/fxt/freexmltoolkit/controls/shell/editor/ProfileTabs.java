package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.ProfileView;
import org.fxt.freexmltoolkit.service.XsltProfile;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.ProfiledTransformation;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * The profile tool tab, shared by the XSLT profiler (Transform panel) and the XQuery profiler
 * (Run ▾ → Profile Query): shows a {@link ProfileView}, re-measures on Profile Again (off the FX
 * thread, in the same tab) and opens the source at a line on a double-click.
 */
final class ProfileTabs {

    private ProfileTabs() {
    }

    /**
     * Shows {@code profile} in a new tool tab, or in {@code tab} when profiling again (FX thread).
     *
     * @param again     measures once more (runs on a worker thread)
     * @param onFailure gets the error message when measuring again failed (FX thread)
     */
    static void show(EditorHost host, String title, XsltProfile profile, XsltTransformationResult result,
                     Supplier<ProfiledTransformation> again, Consumer<String> onFailure, Tab tab) {
        ProfileView view = new ProfileView(profile, result);
        view.setSourceOpener((systemId, line) -> openSource(host, systemId, line));
        Tab target = tab != null ? tab : host.openToolTab(title, "bi-speedometer2", view);
        if (tab != null) {
            tab.setContent(view);
        }
        view.setOnProfileAgain(() -> FxtGui.executorService.submit(() -> {
            ProfiledTransformation next = again.get();
            Platform.runLater(() -> {
                if (next.profile() != null) {
                    show(host, title, next.profile(), next.result(), again, onFailure, target);
                } else {
                    onFailure.accept(next.result().getErrorMessage());
                }
            });
        }));
    }

    /** Opens a module at a line from the profile (a double-click there). */
    static void openSource(EditorHost host, String systemId, int line) {
        Path path = ProfileView.toPath(systemId);
        if (path == null) {
            return;
        }
        host.openFile(path);
        goToLineWhenOpen(host, path, line, 15);
    }

    private static void goToLineWhenOpen(EditorHost host, Path path, int line, int attemptsLeft) {
        boolean ready = host.getActiveDocument().map(d -> path.equals(d.getPath())).orElse(false)
                && host.getActiveText().map(t -> !t.isEmpty()).orElse(false);
        if (ready || attemptsLeft <= 0) {
            if (ready && line > 0) {
                host.goToLine(line);
            }
            return;
        }
        var retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        retry.setOnFinished(e -> goToLineWhenOpen(host, path, line, attemptsLeft - 1));
        retry.play();
    }
}
