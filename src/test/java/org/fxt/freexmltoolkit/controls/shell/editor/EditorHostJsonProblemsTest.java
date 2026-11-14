package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.CombinedStyleManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Validation problems of a JSON document are shown in place: underlined in the text, with a
 * gutter badge, and dropped on the next edit.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonProblemsTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void problemsAreUnderlinedAndDroppedOnEdit(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("s.json");
        Files.writeString(schema, "{\"type\":\"object\",\"properties\":{\"age\":{\"type\":\"integer\"}}}");
        String text = "{\n  \"name\": \"x\",\n  \"age\": \"old\"\n}\n";
        Path json = tmp.resolve("data.json");
        Files.writeString(json, text);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("age")).orElse(false));

        List<ValidationProblem> problems = ValidationRunner.validateJson(text, schema.toFile());
        assertEquals(1, problems.size(), problems.toString());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(problems);
            return null;
        });
        JsonEditorView view = (JsonEditorView) WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView());
        int keyStart = text.indexOf("\"age\"");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getCodeArea().getStyleOfChar(keyStart + 1).contains(CombinedStyleManager.ERROR_STYLE)));
        assertEquals(1, view.getEditor().getProblems().size());
        assertEquals(2, view.getEditor().getProblems().get(0).line());
        assertFalse(view.getEditor().problemsAt(keyStart + 2).isEmpty(), "hovering the key shows the problem");
        // the gutter is rebuilt asynchronously (folding regions arrive from a worker)
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getCodeArea().lookup(".json-gutter-problem-badge") != null));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getCodeArea().insertText(0, " ");
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(1000, () -> view.getEditor().getProblems().isEmpty()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> !view.getCodeArea().getStyleOfChar(keyStart + 2).contains(CombinedStyleManager.ERROR_STYLE)));
    }
}
