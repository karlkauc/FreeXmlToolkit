/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collection;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XPathSyntaxHighlighter")
class XPathSyntaxHighlighterTest {

    /** The CSS class at a given character position, or "" when unstyled. */
    private static String styleAt(StyleSpans<Collection<String>> spans, int position) {
        int pos = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            if (position >= pos && position < pos + span.getLength()) {
                return span.getStyle().isEmpty() ? "" : span.getStyle().iterator().next();
            }
            pos += span.getLength();
        }
        return "OUT_OF_RANGE";
    }

    @Test
    @DisplayName("null and empty input produce empty spans without throwing")
    void nullAndEmpty() {
        assertEquals(0, XPathSyntaxHighlighter.computeHighlighting(null).length());
        assertEquals(0, XPathSyntaxHighlighter.computeHighlighting("").length());
    }

    @Test
    @DisplayName("span length always equals the (normalized) text length")
    void spanLengthMatchesText() {
        String[] inputs = {
                "//book/title",
                "for $x in //a return $x",
                "count(//item[@id = '5'])",
                "(: a comment :) $v + 1.5",
                "child::node()/following-sibling::*"
        };
        for (String input : inputs) {
            StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(input);
            assertNotNull(spans);
            assertEquals(input.length(), spans.length(), "length mismatch for: " + input);
        }
    }

    @Test
    @DisplayName("FLWOR keywords are styled as xq-keyword")
    void flworKeywords() {
        //  for $x in //a return $x
        //  0123456789...
        String text = "for $x in //a return $x";
        StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(text);

        assertEquals("xq-keyword", styleAt(spans, 0), "f of for");
        assertEquals("xq-var", styleAt(spans, text.indexOf("$x")), "$ of $x");
        assertEquals("xq-keyword", styleAt(spans, text.indexOf("in")), "i of in");
        assertEquals("xq-keyword", styleAt(spans, text.indexOf("return")), "r of return");
    }

    @Test
    @DisplayName("function name before '(' is xq-function")
    void functionCall() {
        String text = "count(//item)";
        StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(text);
        assertEquals("xq-function", styleAt(spans, 0), "c of count");
        assertEquals("xq-function", styleAt(spans, 4), "t of count");
        // '(' itself is not part of the function token
        assertEquals("", styleAt(spans, 5), "( is unstyled");
    }

    @Test
    @DisplayName("axes ending in '::' are xq-axis")
    void axes() {
        String text = "child::a/descendant::b";
        StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(text);
        assertEquals("xq-axis", styleAt(spans, 0), "c of child::");
        assertEquals("xq-axis", styleAt(spans, 6), ": of child::");
        int d = text.indexOf("descendant::");
        assertEquals("xq-axis", styleAt(spans, d), "d of descendant::");
    }

    @Test
    @DisplayName("string literals and numbers are styled, comments span their full range")
    void stringsNumbersComments() {
        //  (: c :) '5' = 1.5
        String text = "(: c :) '5' = 1.5";
        StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(text);

        // comment covers the whole (: ... :)
        assertEquals("xq-comment", styleAt(spans, 0));
        assertEquals("xq-comment", styleAt(spans, 6));
        // string literal '5'
        int s = text.indexOf("'5'");
        assertEquals("xq-string", styleAt(spans, s));
        assertEquals("xq-string", styleAt(spans, s + 2));
        // number 1.5
        int n = text.indexOf("1.5");
        assertEquals("xq-number", styleAt(spans, n));
        assertEquals("xq-number", styleAt(spans, n + 2));
    }

    @Test
    @DisplayName("CRLF input aligns spans with the normalized (\\n) text length")
    void crlfNormalization() {
        String input = "for $x in //a\r\nreturn $x";
        String normalized = input.replace("\r\n", "\n");
        StyleSpans<Collection<String>> spans = XPathSyntaxHighlighter.computeHighlighting(input);
        assertEquals(normalized.length(), spans.length());
        assertEquals("xq-keyword", styleAt(spans, normalized.indexOf("return")));
    }
}
