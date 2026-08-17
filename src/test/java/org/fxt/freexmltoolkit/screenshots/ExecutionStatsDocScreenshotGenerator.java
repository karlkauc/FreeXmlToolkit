package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.service.DeveloperPropertyKeys;
import org.fxt.freexmltoolkit.service.ExecutionStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the <strong>Execution Statistics</strong> documentation screenshots into
 * {@code docs/img/} (file names {@code execution-statistics-*.png}):
 * <ul>
 *   <li>{@code execution-statistics-settings.png} — the Settings page scrolled to the
 *       DEVELOPER card with the feature enabled,</li>
 *   <li>{@code execution-statistics-history.png} — the Execution Statistics tool tab
 *       after real runs (transform + validation), with a row selected so the detail
 *       report shows, and the status bar's "last run" item visible.</li>
 * </ul>
 *
 * <p>Temporarily enables the developer flag and restores it (and clears the recorded
 * history) afterwards, so the generator leaves no trace in the developer's configuration.
 * Run via {@code xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * --tests "org.fxt.freexmltoolkit.screenshots.ExecutionStatsDocScreenshotGenerator"}.</p>
 */
@ExtendWith(ApplicationExtension.class)
class ExecutionStatsDocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateExecutionStatsScreenshots() throws Exception {
        IMG_DIR.mkdirs();

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle();

        var props = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        String oldFlag = props.get(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED);
        try {
            props.set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED, "true");
            ExecutionStatsService.getInstance().clear();

            // --- Settings page, scrolled to the DEVELOPER card (checkbox enabled) ---
            onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
            settle(600);
            onFx(() -> {
                if (root.lookup(".fxt-settings-scroll")
                        instanceof javafx.scene.control.ScrollPane scroll) {
                    scroll.setVvalue(0.8); // the DEVELOPER card sits in the lower card rows
                }
            });
            settle(400);
            shot("execution-statistics-settings");

            // --- Record real runs: an XSLT transform and a validation ---
            File fundsXml = new File(EXAMPLES, "xml/FundsXML4_Equity_Fund.xml");
            File checkXslt = new File(EXAMPLES, "xslt/Check_FundsXML_File.xslt");
            onFx(() -> shell.getSelectionModel().select(Activity.TRANSFORM));
            settle();
            if (checkXslt.exists() && fundsXml.exists()
                    && shell.lookup(".fxt-transform-panel")
                            instanceof org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel transformPanel) {
                onFx(() -> {
                    transformPanel.selectInput(java.util.List.of(fundsXml), fundsXml);
                    transformPanel.selectXslt(java.util.List.of(checkXslt), checkXslt);
                });
                // The selection auto-runs the transform; wait for the recorded entry.
                WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                        () -> !ExecutionStatsService.getInstance().snapshot().isEmpty());
            } else {
                System.out.println("[stats-screenshot] transform scene skipped (panel or files missing)");
            }
            System.out.println("[stats-screenshot] entries after transform: "
                    + ExecutionStatsService.getInstance().snapshot().size());

            onFx(() -> host.openFile(fundsXml.toPath()));
            WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
            onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
            settle(600);
            int before = ExecutionStatsService.getInstance().snapshot().size();
            if (shell.lookup(".fxt-validation-panel")
                    instanceof org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel) {
                onFx(validationPanel::revalidate);
                // FundsXML4.xsd is large — allow the schema compile + validation to finish.
                WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS,
                        () -> ExecutionStatsService.getInstance().snapshot().size() > before);
            }

            // --- Execution Statistics tool tab with a selected row (detail report) ---
            onFx(() -> host.transformOutputPanel().hide());
            onFx(host::openExecutionStats);
            settle(600);
            onFx(() -> {
                if (root.lookup("#execution-stats-table")
                        instanceof javafx.scene.control.TableView<?> table) {
                    table.getSelectionModel().select(0);
                }
            });
            settle(600);
            shot("execution-statistics-history");
        } finally {
            props.set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                    oldFlag == null ? "false" : oldFlag);
            ExecutionStatsService.getInstance().clear();
        }
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle() {
        settle(400);
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[stats-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
