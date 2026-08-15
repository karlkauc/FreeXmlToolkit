package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * User-entry quick fixes in the shell: the prompt's values flow into the applied
 * fix, the evaluated defaults are offered, and cancelling leaves the document
 * untouched. (The prompt is stubbed — headless runs must not block on a modal.)
 */
@org.junit.jupiter.api.extension.ExtendWith(ApplicationExtension.class)
class QuickFixUserEntryFlowTest {

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

    private ValidationProblem validateFixture(Path tmp) throws Exception {
        Path sch = tmp.resolve("user-entry.sch");
        Files.copy(Path.of("src/test/resources/sqf/user-entry.sch"), sch);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<people><person><name>John</name></person></people>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("person")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveProblems().stream().anyMatch(ValidationProblem::hasFixes));
        return host.getActiveProblems().stream()
                .filter(ValidationProblem::hasFixes).findFirst().orElseThrow();
    }

    @Test
    void promptValueFlowsIntoTheAppliedFix(@TempDir Path tmp) throws Exception {
        ValidationProblem problem = validateFixture(tmp);
        SqfFixSuggestion fix = problem.fixes().get(0);
        assertTrue(fix.needsUserInput());

        StringBuilder seenDefault = new StringBuilder();
        host.getQuickFixController().setUserEntryPrompt((title, entries, defaults) -> {
            seenDefault.append(defaults.getOrDefault("nick", ""));
            return Optional.of(Map.of("nick", "JD"));
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.getQuickFixController().applyFix(problem, fix);
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("nickname=\"JD\"")).orElse(false));
        assertEquals("Johny", seenDefault.toString(),
                "the @default XPath (concat(name,'y')) must be offered as pre-filled value");
    }

    @Test
    void cancellingThePromptLeavesTheDocumentUntouched(@TempDir Path tmp) throws Exception {
        ValidationProblem problem = validateFixture(tmp);
        SqfFixSuggestion fix = problem.fixes().get(0);
        String before = host.getActiveText().orElseThrow();

        host.getQuickFixController().setUserEntryPrompt((title, entries, defaults) -> Optional.empty());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.getQuickFixController().applyFix(problem, fix);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(400); // give a (wrongly started) async computation time to land
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(before, host.getActiveText().orElseThrow(), "cancel must change nothing");
    }
}
