package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the rebuilt
 * Validation side panel with seeded batch results and problems, for visual
 * comparison against the Figma mockup "Redesign · Unified — Validation" (40:48).
 */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelSnapshotTest {

    private EditorHost host;
    private ValidationPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ValidationPanel(host);
        Scene scene = new Scene(panel, 260, 820);
        for (String sheet : new String[]{"/css/design-tokens.css", "/css/unified-shell.css"}) {
            var css = getClass().getResource(sheet);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        }
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void snapshotValidationPanel() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);

        // Seed RESULTS + PROBLEMS so the mockup's colored rows/badges render.
        Path tmp = java.nio.file.Files.createTempDirectory("fxt_vp_snapshot");
        File failedFile = tmp.resolve("FundsXML_433_Bond_Fund.xml").toFile();
        File okFile = tmp.resolve("sample_eqty_02.xml").toFile();
        File warnFile = tmp.resolve("legacy_2019.xml").toFile();
        var problems = List.of(
                new ValidationProblem("XSD", "error", 6,
                        "cvc-datatype-valid.1.2.1: '2024-13-45' is not a valid value for 'xs:date'"),
                new ValidationProblem("XSD", "error", 16,
                        "cvc-minLength-valid: value '5299X' has length 5; minLength is 20"),
                new ValidationProblem("Schematron", "warning", 5,
                        "chk-doc-date: DocumentGenerated should not be after ContentDate"));
        var seeded = List.of(
                new ValidationRunner.FileValidationResult(failedFile, problems, null),
                new ValidationRunner.FileValidationResult(okFile, List.of(), null),
                new ValidationRunner.FileValidationResult(warnFile,
                        List.of(new ValidationProblem("Schematron", "warning", 3, "legacy hint")), null));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.showBatchResults(seeded, "seeded snapshot report");
            @SuppressWarnings("unchecked")
            var results = (javafx.scene.control.ListView<ValidationRunner.FileValidationResult>)
                    panel.lookup("#validation-results-list");
            results.getSelectionModel().select(0); // failed row → fills the PROBLEMS list
            return null;
        });

        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> panel.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("validation_panel.png").toFile());
    }
}
