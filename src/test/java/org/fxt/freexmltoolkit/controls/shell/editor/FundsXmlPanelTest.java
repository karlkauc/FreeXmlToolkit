package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.GitHubReleaseClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class FundsXmlPanelTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @AfterEach
    void resetCoordinator() {
        FundsXmlDownloadCoordinator.setInstanceForTesting(null);
    }

    @Test
    void buildsWithoutThrowing() {
        FundsXmlPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, () -> new FundsXmlPanel(host));
        assertNotNull(panel);
        // Title + management/validate/docs sections + buttons + spacer/status.
        assertTrue(panel.getChildren().size() > 3,
                "panel should have built its sections, but had " + panel.getChildren().size() + " children");
    }

    @Test
    void progressBarIsHiddenInitiallyAndTracksCoordinatorEvents(@TempDir Path tempDir) throws Exception {
        ManualExecutor executor = new ManualExecutor();
        FundsXmlDownloadCoordinator coordinator =
                new FundsXmlDownloadCoordinator(stubService(tempDir), executor);
        FundsXmlDownloadCoordinator.setInstanceForTesting(coordinator);

        FundsXmlPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, () -> new FundsXmlPanel(host));
        ProgressBar bar = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (ProgressBar) findNode(panel, n -> n instanceof ProgressBar));
        assertNotNull(bar, "the panel must contain a progress bar");
        assertFalse(bar.isVisible(), "progress bar must be hidden while idle");

        assertTrue(coordinator.startBackgroundDownload("test"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            executor.runAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // The stub download finishes immediately: bar shown on start, hidden again on finish.
        assertFalse(bar.isVisible(), "progress bar must hide once the download finished");
        Label status = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (Label) findNode(panel, n -> n instanceof Label l
                        && l.getText() != null && l.getText().contains("Download failed")));
        assertNotNull(status, "the status label must report the (stub) download outcome");
    }

    @Test
    void openSchemaButtonHintsWhenNoSchemaIsCached() {
        FundsXmlPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, () -> new FundsXmlPanel(host));
        Button openSchema = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (Button) findNode(panel, n -> n instanceof Button b
                        && "Open Schema in Editor".equals(b.getText())));
        assertNotNull(openSchema, "the panel must offer 'Open Schema in Editor'");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            openSchema.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        Label status = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (Label) findNode(panel, n -> n instanceof Label l
                        && l.getText() != null && l.getText().contains("No active schema")));
        assertNotNull(status, "without cached content the button must show a hint instead of opening");
    }

    /** Depth-first search over the panel's children (callers run it on the FX thread). */
    private static Node findNode(Node root, java.util.function.Predicate<Node> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findNode(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static FundsXmlExtensionService stubService(Path tempDir) throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        FundsXmlCache cache = ctor.newInstance(tempDir.resolve("fundsxml"));
        return new FundsXmlExtensionService(cache, new GitHubReleaseClient(uri -> {
            throw new java.io.IOException("Network disabled in tests");
        })) {
            @Override
            public DownloadResult downloadOrUpdate(
                    org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback callback) {
                return DownloadResult.builder().error("no network in tests").build();
            }
        };
    }

    /** Collects submitted tasks; the test runs them explicitly. */
    private static class ManualExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }
}
