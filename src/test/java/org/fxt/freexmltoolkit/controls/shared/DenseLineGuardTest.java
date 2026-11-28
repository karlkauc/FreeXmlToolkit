package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

/**
 * Minified lines must lose their styles (their span count freezes the editor's line layout),
 * while ordinary lines of the same document stay highlighted.
 */
class DenseLineGuardTest {

    private static String minified(int items) {
        StringBuilder sb = new StringBuilder("<root>");
        for (int i = 0; i < items; i++) {
            sb.append("<item id=\"1\" name=\"abc\">value</item>");
        }
        return sb.append("</root>").toString();
    }

    private static int styledSpans(StyleSpans<Collection<String>> spans, int from, int to) {
        int position = 0;
        int styled = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            int end = position + span.getLength();
            if (end > from && position < to && !span.getStyle().isEmpty()) {
                styled++;
            }
            position = end;
        }
        return styled;
    }

    @Test
    void ordinaryDocumentIsReturnedUnchanged() {
        String text = "<root>\n  <a id=\"1\">x</a>\n  <b>y</b>\n</root>\n";
        StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
        assertSame(spans, DenseLineGuard.flattenDenseLines(text, spans));
    }

    @Test
    void minifiedSingleLineBecomesOneUnstyledSpan() {
        String text = minified(2000);
        StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
        assertTrue(spans.getSpanCount() > DenseLineGuard.MAX_SPANS_PER_LINE, "test setup: line must be dense");

        StyleSpans<Collection<String>> flat = DenseLineGuard.flattenDenseLines(text, spans);

        assertEquals(text.length(), flat.length(), "spans must still cover the whole text");
        assertEquals(1, flat.getSpanCount());
        assertEquals(0, styledSpans(flat, 0, text.length()));
    }

    @Test
    void onlyTheDenseLineLosesItsStyles() {
        String head = "<doc>\n  <title lang=\"en\">Hello</title>\n";
        String dense = minified(2000) + "\n";
        String tail = "  <footer id=\"f\">Bye</footer>\n</doc>\n";
        String text = head + dense + tail;
        StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

        StyleSpans<Collection<String>> flat = DenseLineGuard.flattenDenseLines(text, spans);

        assertEquals(text.length(), flat.length());
        int denseStart = head.length();
        int denseEnd = denseStart + dense.length();
        assertEquals(0, styledSpans(flat, denseStart, denseEnd), "the minified line must be unstyled");
        assertEquals(styledSpans(spans, 0, denseStart), styledSpans(flat, 0, denseStart),
                "lines before the minified one keep their highlighting");
        assertEquals(styledSpans(spans, denseEnd, text.length()), styledSpans(flat, denseEnd, text.length()),
                "lines after the minified one keep their highlighting");
        assertTrue(flat.getSpanCount() < 100, "span count was " + flat.getSpanCount());
    }

    @Test
    void longLineWithFewSpansStaysHighlighted() {
        // e.g. embedded base64: one long text node is a single span and lays out fast.
        String text = "<root>\n  <data>" + "QUJD".repeat(20_000) + "</data>\n</root>\n";
        StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
        assertSame(spans, DenseLineGuard.flattenDenseLines(text, spans));
    }

    @Test
    void customLimitIsHonoured() {
        String text = minified(5);
        StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
        StyleSpans<Collection<String>> flat = DenseLineGuard.flattenDenseLines(text, spans, 10);
        assertEquals(1, flat.getSpanCount());
        assertEquals(text.length(), flat.length());
    }

    @org.junit.jupiter.api.Test
    void longLinesAreNotGivenToTheHighlighter() {
        java.util.List<String> seen = new java.util.ArrayList<>();
        java.util.function.Function<String, org.fxmisc.richtext.model.StyleSpans<java.util.Collection<String>>> highlighter =
                t -> {
                    seen.add(t);
                    var b = new org.fxmisc.richtext.model.StyleSpansBuilder<java.util.Collection<String>>();
                    b.add(java.util.Collections.singleton("x"), t.length());
                    return b.create();
                };
        String text = "short\n" + "y".repeat(50) + "\nend";
        var spans = DenseLineGuard.highlightSkippingLongLines(text, highlighter, 20);
        org.junit.jupiter.api.Assertions.assertEquals(text.length(), spans.length(), "spans cover the whole text");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("short", "end"), seen, "the long line is skipped");
        // styled - newline - plain long line - newline - styled
        java.util.List<java.util.Collection<String>> styles = new java.util.ArrayList<>();
        spans.forEach(s -> styles.add(s.getStyle()));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("x"), java.util.Set.copyOf(styles.getFirst()));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("x"), java.util.Set.copyOf(styles.getLast()));
        org.junit.jupiter.api.Assertions.assertEquals(3, spans.getSpanCount(), "styled, plain run, styled");

        // without a long line: one call on the whole text
        seen.clear();
        DenseLineGuard.highlightSkippingLongLines("a\nb", highlighter, 20);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("a\nb"), seen);
        org.junit.jupiter.api.Assertions.assertTrue(DenseLineGuard.hasLineLongerThan("ab\n" + "c".repeat(21), 20));
        org.junit.jupiter.api.Assertions.assertFalse(DenseLineGuard.hasLineLongerThan("ab\ncd", 20));
    }
}
