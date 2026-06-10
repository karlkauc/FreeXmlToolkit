package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the PROBLEMS panel below the editor: it mirrors
 * {@link EditorHost#getActiveProblems()}, shows severity counts, jumps to a
 * problem's line on selection, collapses to its header and hides while empty.
 */
@ExtendWith(ApplicationExtension.class)
class ProblemsPanelTest {

    private EditorHost host;
    private ProblemsPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ProblemsPanel(host);
        VBox column = new VBox(host, panel);
        VBox.setVgrow(host, Priority.ALWAYS);
        stage.setScene(new Scene(column, 1000, 600));
        stage.show();
    }

    @Test
    void hiddenInitiallyAndShowsWithCountsWhenProblemsArrive() {
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.isVisible(), "panel must be hidden while there are no problems");
        assertFalse(panel.isManaged());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element"),
                    new ValidationProblem("XSD", "error", 5, "another error"),
                    new ValidationProblem("Schematron", "warning", 7, "questionable date")));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(panel.isVisible(), "panel must auto-show when problems arrive");
        assertEquals(3, panel.getProblemCount());
        Label errorChip = (Label) panel.lookup(".fxt-problems-chip-error");
        Label warningChip = (Label) panel.lookup(".fxt-problems-chip-warning");
        assertEquals("2", errorChip.getText());
        assertEquals("1", warningChip.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.isVisible(), "panel must hide again when the problems clear");
    }

    @Test
    void selectingAProblemJumpsToItsLine(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n<a/>\n<b/>\n<c/>\n</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element")));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveCaretLineColumn()[0] == 3);
        assertEquals(3, host.getActiveCaretLineColumn()[0],
                "selecting the problem must move the caret to its line");
    }

    @Test
    void collapseTogglesListVisibilityButKeepsHeader() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 1, "x")));
            panel.setCollapsed(true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.isCollapsed());
        assertTrue(panel.isVisible(), "collapsed panel keeps its header visible");
        assertFalse(panel.lookup("#problems-panel-list").isVisible());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCollapsed(false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookup("#problems-panel-list").isVisible());
    }

    @Test
    void replacingProblemsWhileARowIsSelectedDoesNotCrash() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 1, "first"),
                    new ValidationProblem("XSD", "error", 2, "second")));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(1);
            return null;
        });
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                host.setActiveProblems(List.of(
                        new ValidationProblem("XSD", "error", 1, "replaced")));
                return null;
            }));
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertEquals(1, panel.getProblemCount());
    }
}
