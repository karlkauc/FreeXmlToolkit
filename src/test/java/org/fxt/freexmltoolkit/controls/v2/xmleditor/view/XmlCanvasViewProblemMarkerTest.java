package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Validation problems mark the rows whose element starts on the problem's line, until the next edit. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewProblemMarkerTest {

    private static final String XML = "<root>\n  <a>1</a>\n  <b>2</b>\n</root>\n";

    private XmlCanvasView view;

    @Start
    void start(Stage stage) throws Exception {
        // The shell parses line-aware (StreamingXmlParser), as here
        XmlEditorContext context = new XmlEditorContext(
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser().parse(XML));
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 400));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void problemsMarkTheirLinesAndGoStaleOnEdit() {
        int lineOfB = view.getAdapter().sourceLine(row("b"));
        assertTrue(lineOfB > 0, "the parser records source lines");
        fx(() -> view.setProblems(Map.of(lineOfB, List.of("Value '2' is not allowed for element <b>"))));

        assertEquals(List.of("Value '2' is not allowed for element <b>"), view.problemsOf(row("b")));
        assertNull(view.problemsOf(row("a")));

        fx(() -> {
            view.getAdapter().commitRowEdit(row("a"), false, "changed");
            view.refresh(); // an edit through the grid: line numbers are stale now
        });
        assertNull(view.problemsOf(row("b")), "markers are dropped after a grid edit");
    }
}
