package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link TextWrap} using the estimating {@link TextMeasurer}
 * (one {@link GridFont#ROW} glyph = 7.2 px).
 */
class TextWrapTest {

    private static final TextMeasurer M = TextMeasurer.estimating();
    private static final double CHAR = 7.2;

    @Test
    void nullAndEmptyYieldOneEmptyLine() {
        assertEquals(List.of(""), TextWrap.wrap(null, GridFont.ROW, 100, M));
        assertEquals(List.of(""), TextWrap.wrap("", GridFont.ROW, 100, M));
    }

    @Test
    void textThatFitsStaysOnOneLine() {
        assertEquals(List.of("hello world"),
                TextWrap.wrap("hello world", GridFont.ROW, 11 * CHAR, M));
    }

    @Test
    void wrapsAtWordBoundaries() {
        assertEquals(List.of("aaa bbb", "ccc"),
                TextWrap.wrap("aaa bbb ccc", GridFont.ROW, 7 * CHAR, M));
    }

    @Test
    void hardBreaksTokensWiderThanTheLine() {
        assertEquals(List.of("abcde", "fghij", "klmno", "pqrst"),
                TextWrap.wrap("abcdefghijklmnopqrst", GridFont.ROW, 5 * CHAR, M));
    }

    @Test
    void splitsOnEmbeddedNewlines() {
        assertEquals(List.of("first", "second"),
                TextWrap.wrap("first\nsecond", GridFont.ROW, 100, M));
    }

    @Test
    void progressesEvenWhenLineIsNarrowerThanOneGlyph() {
        assertEquals(List.of("a", "b", "c"),
                TextWrap.wrap("abc", GridFont.ROW, 1, M));
    }

    @Test
    void collapsesWhitespaceAtLineBreaks() {
        // The break consumes the separating space; no leading blank on the next line.
        assertEquals(List.of("aaaa", "bbbb"),
                TextWrap.wrap("aaaa bbbb", GridFont.ROW, 5 * CHAR, M));
    }
}
