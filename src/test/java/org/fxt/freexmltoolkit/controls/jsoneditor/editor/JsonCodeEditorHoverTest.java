package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The hover reads the whole text only for documents up to {@link JsonCodeEditor#HOVER_PATH_MAX_CHARS}. */
@ExtendWith(ApplicationExtension.class)
class JsonCodeEditorHoverTest {

    private final AtomicInteger pathLookups = new AtomicInteger();

    @Start
    void start(Stage stage) {
        // toolkit only
    }

    private JsonCodeEditor editor(String text) {
        CodeSyntax json = CodeSyntax.JSON;
        CodeSyntax counting = new CodeSyntax(json.formatLabel(), json.highlighter(), json.formatDetector(),
                json.foldScanner(), json.completer(), json.completionTriggers(), json.pathAt(), (t, offset) -> {
                    pathLookups.incrementAndGet();
                    return json.hoverInfo().apply(t, offset);
                });
        return WaitForAsyncUtils.waitForAsyncFx(10000, () -> {
            JsonCodeEditor editor = new JsonCodeEditor(counting);
            editor.setText(text);
            return editor;
        });
    }

    @Test
    void aSmallDocumentShowsThePath() {
        String text = "{\"name\": \"value\"}";
        JsonCodeEditor editor = editor(text);
        String hover = WaitForAsyncUtils.waitForAsyncFx(10000, () -> editor.hoverTextAt(text.indexOf("value")));
        assertNotNull(hover);
        assertTrue(hover.contains("Path: "), hover);
        assertEquals(1, pathLookups.get());
    }

    @Test
    void aLargeDocumentShowsOnlyProblemsWithoutReadingTheText() {
        String text = "{\"data\": \"" + "x".repeat(JsonCodeEditor.HOVER_PATH_MAX_CHARS) + "\"}";
        JsonCodeEditor editor = editor(text);
        assertNull(WaitForAsyncUtils.waitForAsyncFx(10000, () -> editor.hoverTextAt(3)));

        String hover = WaitForAsyncUtils.waitForAsyncFx(10000, () -> {
            editor.setProblems(List.of(new JsonCodeEditor.ProblemRange(0, 8, 0, "Something is wrong")));
            return editor.hoverTextAt(3);
        });
        assertEquals("⚠ Something is wrong", hover);
        assertEquals(0, pathLookups.get(), "no path lookup over the whole text");
    }

    @Test
    void noHoverWhileALineIsVeryLong() {
        JsonCodeEditor small = editor("{\n  \"a\": 1\n}");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(10000, small::hoverOffered));

        String minified = "[" + "{\"id\":1,\"name\":\"x\"},".repeat(JsonCodeEditor.HOVER_MAX_LINE / 10) + "{}]";
        JsonCodeEditor editor = editor(minified);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(10000, editor::longestLine) > JsonCodeEditor.HOVER_MAX_LINE);
        org.junit.jupiter.api.Assertions.assertFalse(WaitForAsyncUtils.waitForAsyncFx(10000, editor::hoverOffered));

        // formatted again: the hover comes back
        WaitForAsyncUtils.waitForAsyncFx(10000, () -> editor.setText("{\n  \"a\": 1\n}"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(10000, editor::hoverOffered));
    }
}
