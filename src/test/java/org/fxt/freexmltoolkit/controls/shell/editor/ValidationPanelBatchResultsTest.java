package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Validation panel's RESULTS list (Figma mockup):
 * a batch run fills one row per file, selecting a failed row shows its
 * problems, and the segmented Single-file/Batch toggle switches modes.
 */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelBatchResultsTest {

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern><sch:rule context="root">
                <sch:assert test="name">root must have a name child</sch:assert>
              </sch:rule></sch:pattern>
            </sch:schema>
            """;

    private EditorHost host;
    private ValidationPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ValidationPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void selectingAFailedBatchRowShowsItsProblems(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(bad));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.runBatch(java.util.List.of(bad.toFile(), good.toFile()));
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> panel.batchResultCount() == 2);

        ListView<?> results = (ListView<?>) panel.lookup("#validation-results-list");
        assertTrue(results.isVisible(), "the RESULTS list must show after a batch run");

        // Selecting the failed file (index 0 = bad.xml) shows its problems.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            results.getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0,
                "selecting the failed batch row must list its problems");
    }

    @Test
    void segmentedToggleSwitchesBetweenSingleAndBatchMode() {
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.isBatchMode(), "Single file is the default mode");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setBatchMode(true);
            return null;
        });
        assertTrue(panel.isBatchMode());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setBatchMode(false);
            return null;
        });
        assertFalse(panel.isBatchMode());
    }
}
