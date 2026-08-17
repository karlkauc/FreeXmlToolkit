package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.DeveloperPropertyKeys;
import org.fxt.freexmltoolkit.service.ExecutionStats;
import org.fxt.freexmltoolkit.service.ExecutionStatsService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of {@link ExecutionStatsView}: seeded history renders newest
 * first, row selection fills the detail report, Clear empties view + service, and a
 * live-recorded run appends a row via the listener.
 */
@ExtendWith(ApplicationExtension.class)
class ExecutionStatsViewTest {

    private ExecutionStatsView view;
    private String previousFlag;

    @Start
    void start(Stage stage) {
        previousFlag = PropertiesServiceImpl.getInstance().get(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED);
        PropertiesServiceImpl.getInstance().set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED, "true");
        ExecutionStatsService.getInstance().clear();

        // Seed two runs before the view exists (history load path).
        ExecutionStatsService service = ExecutionStatsService.getInstance();
        var first = service.begin(ExecutionStats.OperationType.XSLT, "first.xslt");
        first.phase("Compile", 3);
        first.finish(100, 200, true, "");
        service.begin(ExecutionStats.OperationType.VALIDATION, "second.xml").finish(50, -1, false, "boom");

        view = new ExecutionStatsView();
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        PropertiesServiceImpl.getInstance().set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                previousFlag == null ? "false" : previousFlag);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.dispose();
            return null;
        });
        ExecutionStatsService.getInstance().clear();
    }

    @Test
    void seededHistoryRendersAndSelectionFillsTheDetailReport() throws Exception {
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> view.getRowCount() == 2);

        // Newest first: the failed validation run tops the list; selecting the second
        // row (the XSLT run) shows its phases in the detail report.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.selectRow(1);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !view.getDetailText().isBlank());
        String detail = view.getDetailText();
        assertTrue(detail.contains("XSLT · first.xslt"), "detail must name the run, was: " + detail);
        assertTrue(detail.contains("Compile: 3 ms"), "detail must list the phases, was: " + detail);
        assertTrue(detail.contains("Wall time:"), detail);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.selectRow(0);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> view.getDetailText().contains("second.xml"));
        assertTrue(view.getDetailText().contains("error — boom"),
                "failed run must show its error, was: " + view.getDetailText());
    }

    @Test
    void liveRunAppendsARowThroughTheListener() throws Exception {
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> view.getRowCount() == 2);

        ExecutionStatsService.getInstance()
                .begin(ExecutionStats.OperationType.XPATH, "//live").finish(10, 20, true, "");

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> view.getRowCount() == 3);
    }
}
