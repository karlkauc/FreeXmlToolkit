package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Collection;

import javafx.application.Platform;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.DenseLineGuard;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;

/**
 * Highlights a result area (transform output, query results) off the FX thread: computing the spans of
 * a result up to the highlight limit took up to hundreds of ms per run, and live preview re-runs on every
 * typing pause. The spans are applied only if the area still shows that text.
 */
final class ResultHighlighter {

    /** Per-area request counter (in the area's properties): an older result is dropped. */
    private static final String REQUEST_KEY = ResultHighlighter.class.getName() + ".request";

    private ResultHighlighter() {
    }

    /**
     * Highlights {@code text} (already shown in {@code area}) as XML or JSON. FX thread.
     *
     * @param xml  whether to use the XML highlighter
     * @param json whether to use the JSON highlighter (ignored when {@code xml} is set)
     */
    static void highlight(CodeArea area, String text, boolean xml, boolean json) {
        long request = nextRequest(area);
        if (!xml && !json) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            StyleSpans<Collection<String>> spans;
            try {
                spans = DenseLineGuard.flattenDenseLines(text, xml
                        ? XmlSyntaxHighlighter.computeHighlighting(text)
                        : JsonSyntaxHighlighter.computeHighlighting(text));
            } catch (RuntimeException e) {
                return; // unhighlighted is fine
            }
            Platform.runLater(() -> {
                if (currentRequest(area) == request && area.getLength() == text.length()) {
                    area.setStyleSpans(0, spans);
                }
            });
        });
    }

    private static long nextRequest(CodeArea area) {
        long next = currentRequest(area) + 1;
        area.getProperties().put(REQUEST_KEY, next);
        return next;
    }

    private static long currentRequest(CodeArea area) {
        return area.getProperties().get(REQUEST_KEY) instanceof Long value ? value : 0L;
    }
}
