package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the Schematron Check result view. */
@ExtendWith(ApplicationExtension.class)
class SchematronCheckSnapshotTest {

    private SchematronCheckResultView view;

    @Start
    void start(Stage stage) {
        // A real detector run over a Schematron with a few issues.
        String schematron = "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\">\n"
                + "  <sch:pattern>\n"
                + "    <sch:rule>\n"
                + "      <sch:assert test=\"@id >< 0\">id must be positive</sch:assert>\n"
                + "    </sch:rule>\n"
                + "  </sch:pattern>\n"
                + "</sch:schema>\n";
        List<SchematronError> issues = SchematronCheckRunner.check(schematron);
        view = new SchematronCheckResultView(issues);
        stage.setScene(new Scene(view, 640, 420));
        stage.show();
    }

    @Test
    void snapshotCheckResult() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> view.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("schematron_check.png").toFile());
        System.out.println("[SMOKE] Schematron check issues=" + view.getIssueCount()
                + " summary=" + view.getSummaryText());
    }
}
