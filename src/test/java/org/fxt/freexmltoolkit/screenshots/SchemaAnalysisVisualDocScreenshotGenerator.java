package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;

/**
 * Visual verification of the Schema Analysis tool tab in Light and Dark theme (output goes to
 * {@code build/visual-verify/}, not the docs). Run with
 * {@code xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*SchemaAnalysisVisualDocScreenshotGenerator*"}.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaAnalysisVisualDocScreenshotGenerator {

    private static final File OUT_DIR = new File("build/visual-verify");
    private static final File XSD = new File(System.getProperty("fxt.analysis.xsd", "release/examples/xsd/FundsXML_428.xsd"));

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
    void verifySchemaAnalysisVisually() throws Exception {
        OUT_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(800);

        onFx(() -> host.openFile(XSD.toPath()));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        settle(800);
        onFx(() -> {
            if (shell.lookup(".fxt-schema-panel") instanceof TypeLibraryPanel library) {
                library.analyzeActive();
            }
        });
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS, () -> {
            var done = new java.util.concurrent.atomic.AtomicBoolean();
            onFx(() -> done.set(shell.lookup("#analysis-status") instanceof Label l
                    && l.getText().startsWith("Analyzed")));
            return done.get();
        });
        settle(800);
        shot("analysis-statistics-light");
        selectSubTab(1);
        shot("analysis-quality-light");

        onFx(() -> ThemeManager.apply(shell.getScene(), true));
        settle(800);
        shot("analysis-quality-dark");
        selectSubTab(0);
        shot("analysis-statistics-dark");
        onFx(() -> ThemeManager.apply(shell.getScene(), false));
        settle(300);
    }

    private void selectSubTab(int index) {
        onFx(() -> {
            if (shell.lookup("#schema-analysis-tabs") instanceof TabPane tabs) {
                tabs.getSelectionModel().select(index);
                if (index == 1 && shell.lookup("#analysis-quality-table") instanceof TableView<?> table
                        && !table.getItems().isEmpty()) {
                    table.getSelectionModel().select(0);
                }
            }
        });
        settle(600);
        // Selecting an issue reveals its node in the Tree view (switches the document tab);
        // come back to the tool tab so the details pane is what gets captured.
        onFx(() -> shell.getEditorHost().openOrFocusToolTab(
                org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView.TITLE,
                org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView.ICON,
                () -> { throw new IllegalStateException("analysis tab should already be open"); }));
        settle(800);
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(OUT_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[visual] wrote " + out.getAbsolutePath());
    }
}
