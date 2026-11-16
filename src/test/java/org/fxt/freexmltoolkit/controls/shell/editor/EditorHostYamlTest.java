package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.CombinedStyleManager;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A YAML document opens in the data-language editor: YAML highlighting, its declared JSON Schema
 * bound (modeline), schema completion, problems underlined on their key, Format that keeps
 * comments (by refusing) and no Minify.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostYamlTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.util.DialogHelper.class.getName(); // loaded before the test thread needs it
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void yamlTabHighlightsBindsCompletesAndValidates(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("s.json"), """
                {"type": "object", "required": ["kind"],
                 "properties": {"kind": {"enum": ["Service", "Job"], "description": "What it is"},
                                "replicas": {"type": "integer"}}}
                """);
        String text = "# yaml-language-server: $schema=./s.json\nreplicas: many\n\n";
        Path yaml = tmp.resolve("app.yaml");
        Files.writeString(yaml, text);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(yaml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveJsonSchema().isPresent());
        JsonEditorView view = (JsonEditorView) WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView());

        // YAML highlighting
        int keyOffset = text.indexOf("replicas") + 1;
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getCodeArea().getStyleOfChar(keyOffset).contains("json-key")));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getCodeArea().getStyleOfChar(1).contains("json-comment")));

        // completion of the missing required key on the empty line
        int caret = text.length() - 1;
        List<Proposal> proposals = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getCodeArea().requestFocus();
            view.getCodeArea().moveTo(caret);
            return view.getEditor().getIntelliSense().show(true);
        });
        assertEquals("kind", proposals.get(0).label(), proposals.toString());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getEditor().getIntelliSense().apply(proposals.get(0));
            return null;
        });
        List<Proposal> values = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.getEditor().getIntelliSense().show(true));
        assertEquals(List.of("Service", "Job"), values.stream().map(Proposal::label).toList());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getEditor().getIntelliSense().apply(values.get(1));
            return null;
        });
        String edited = host.getActiveText().orElseThrow();
        assertTrue(edited.endsWith("kind: Job\n") || edited.contains("kind: Job"), edited);

        // validation against the bound schema, underlined on the key
        List<ValidationProblem> problems = ValidationRunner.validateYaml(edited,
                host.activeSchemaProperty().get());
        assertEquals(1, problems.size(), problems.toString());
        assertEquals(2, problems.get(0).line());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(problems);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getCodeArea().getStyleOfChar(keyOffset).contains(CombinedStyleManager.ERROR_STYLE)));

        // hover help from the schema
        List<String> hover = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.getEditor().schemaInfoAt(edited.indexOf("kind") + 1));
        assertTrue(hover.contains("What it is"), hover.toString());

        // Minify does nothing to YAML
        assertFalse(host.minifyActive().get(5, TimeUnit.SECONDS));
        assertEquals(edited, host.getActiveText().orElseThrow());
    }

    @Test
    void formatReindentsYamlWithoutComments(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("plain.yml");
        Files.writeString(yaml, "a:    {b: 1, c: [x, y]}\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(yaml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.startsWith("a:")).orElse(false));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.formatActive()).get(5, TimeUnit.SECONDS));
        assertEquals("a:\n  b: 1\n  c:\n  - x\n  - y\n", host.getActiveText().orElseThrow());
        // formatting again changes nothing and does not mark the document changed again
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.formatActive()).get(5, TimeUnit.SECONDS));
    }
}
