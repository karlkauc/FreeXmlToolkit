package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * End-to-end quick-fix flow in the shell: a Schematron with SQF fixes yields
 * problems that carry fix suggestions; applying a fix corrects the editor text
 * (preserving formatting), the problem disappears after revalidation, and the
 * editor's native undo restores the original text.
 */
@ExtendWith(ApplicationExtension.class)
class QuickFixFlowTest {

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
                        xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
              <sch:pattern>
                <sch:rule context="person">
                  <sch:assert test="@id" sqf:fix="addId">Person must have an id.</sch:assert>
                  <sqf:fix id="addId">
                    <sqf:description><sqf:title>Add default id</sqf:title></sqf:description>
                    <sqf:add node-type="attribute" target="id">generated</sqf:add>
                  </sqf:fix>
                </sch:rule>
              </sch:pattern>
            </sch:schema>
            """;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <people>
                <person>
                    <name>John</name>
                </person>
            </people>
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
    void problemsCarryFixesAndApplyingOneCorrectsTheDocument(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("person")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);

        List<ValidationProblem> problems = List.copyOf(host.getActiveProblems());
        ValidationProblem problem = problems.stream()
                .filter(ValidationProblem::hasFixes).findFirst().orElse(null);
        assertTrue(problem != null, "the Schematron problem must carry the SQF fix: " + problems);
        SqfFixSuggestion fix = problem.fixes().get(0);
        assertEquals("Add default id", fix.title());

        String before = host.getActiveText().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.getQuickFixController().applyFix(problem, fix);
            return null;
        });
        // the fix computes off-thread, applies on FX, then triggers revalidation
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<person id=\"generated\">")).orElse(false));
        String after = host.getActiveText().orElseThrow();
        assertEquals(before.replace("<person>", "<person id=\"generated\">"), after,
                "only the start tag may change — formatting must be preserved");

        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> host.getActiveProblems().isEmpty());
        assertTrue(host.getActiveProblems().isEmpty(), "the fixed problem must disappear");

        // native editor undo restores the original text as one step
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().map(before::equals).orElse(false));
        assertEquals(before, host.getActiveText().orElseThrow(), "undo must restore the original text");
    }

    @Test
    void problemsWithoutSqfCarryNoFixes(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("plain.sch");
        Files.writeString(sch, """
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                  <sch:pattern><sch:rule context="person">
                    <sch:assert test="@id">Person must have an id.</sch:assert>
                  </sch:rule></sch:pattern>
                </sch:schema>
                """);
        Path xml = tmp.resolve("doc2.xml");
        Files.writeString(xml, XML);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("person")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertFalse(host.getActiveProblems().stream().anyMatch(ValidationProblem::hasFixes),
                "plain Schematron problems must not offer fixes");
    }
}
