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

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlSyntaxHighlighter")
class XmlSyntaxHighlighterTest {

    /**
     * Helper: converts StyleSpans to a list of (style, length) pairs for easier assertion.
     */
    private static List<SpanInfo> toSpanList(StyleSpans<Collection<String>> spans) {
        List<SpanInfo> result = new ArrayList<>();
        for (StyleSpan<Collection<String>> span : spans) {
            String style = span.getStyle().isEmpty() ? "" : span.getStyle().iterator().next();
            result.add(new SpanInfo(style, span.getLength()));
        }
        return result;
    }

    /**
     * Helper: finds the style at a given character position.
     */
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

    record SpanInfo(String style, int length) {
        @Override
        public String toString() {
            return style.isEmpty() ? "empty(" + length + ")" : style + "(" + length + ")";
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Returns spans for null input without throwing")
        void nullInput() {
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(null);
            assertNotNull(spans);
            assertEquals(0, spans.length());
        }

        @Test
        @DisplayName("Returns empty span for empty string")
        void emptyInput() {
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting("");
            assertNotNull(spans);
            assertEquals(0, spans.length());
        }

        @Test
        @DisplayName("Plain text with no tags returns unstyled span")
        void plainText() {
            String text = "Hello World";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
            assertNotNull(spans);
            assertEquals(text.length(), spans.length());
            assertEquals("", styleAt(spans, 0));
            assertEquals("", styleAt(spans, 5));
        }
    }

    @Nested
    @DisplayName("Simple Tags")
    class SimpleTagTests {

        @Test
        @DisplayName("<Root> — open bracket is tagmark, element name is anytag, close bracket is tagmark")
        void simpleOpenTag() {
            //  <Root>
            //  012345
            String text = "<Root>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);
            List<SpanInfo> list = toSpanList(spans);

            assertEquals(text.length(), spans.length());
            // < = tagmark(1), Root = anytag(4), > = tagmark(1)
            // First span might be empty(0) for gap before match
            assertEquals("tagmark", styleAt(spans, 0), "< should be tagmark");
            assertEquals("anytag", styleAt(spans, 1), "R should be anytag");
            assertEquals("anytag", styleAt(spans, 2), "o should be anytag");
            assertEquals("anytag", styleAt(spans, 3), "o should be anytag");
            assertEquals("anytag", styleAt(spans, 4), "t should be anytag");
            assertEquals("tagmark", styleAt(spans, 5), "> should be tagmark");
        }

        @Test
        @DisplayName("</Root> — closing tag bracket includes /")
        void simpleCloseTag() {
            //  </Root>
            //  0123456
            String text = "</Root>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< should be tagmark");
            assertEquals("tagmark", styleAt(spans, 1), "/ should be tagmark");
            assertEquals("anytag", styleAt(spans, 2), "R should be anytag");
            assertEquals("anytag", styleAt(spans, 5), "t should be anytag");
            assertEquals("tagmark", styleAt(spans, 6), "> should be tagmark");
        }

        @Test
        @DisplayName("<Item/> — self-closing /> is fully tagmark")
        void selfClosingTag() {
            //  <Item/>
            //  0123456
            String text = "<Item/>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< should be tagmark");
            assertEquals("anytag", styleAt(spans, 1), "I should be anytag");
            assertEquals("anytag", styleAt(spans, 4), "m should be anytag");
            assertEquals("tagmark", styleAt(spans, 5), "/ should be tagmark");
            assertEquals("tagmark", styleAt(spans, 6), "> should be tagmark");
        }
    }

    @Nested
    @DisplayName("Tags with Attributes")
    class AttributeTests {

        @Test
        @DisplayName("Tag with single attribute has correct highlighting")
        void tagWithSingleAttribute() {
            //  <Elem attr="val">
            //  01234567890123456
            String text = "<Elem attr=\"val\">";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "E is anytag");
            assertEquals("anytag", styleAt(spans, 4), "m is anytag");
            // space at position 5 is unstyled
            assertEquals("", styleAt(spans, 5), "space is unstyled");
            // attr at positions 6-9
            assertEquals("attribute", styleAt(spans, 6), "a(ttr) is attribute");
            assertEquals("attribute", styleAt(spans, 9), "(att)r is attribute");
            // = at position 10
            assertEquals("tagmark", styleAt(spans, 10), "= is tagmark");
            // "val" at positions 11-15
            assertEquals("avalue", styleAt(spans, 11), "\"val\" is avalue");
            assertEquals("avalue", styleAt(spans, 15), "closing \" is avalue");
            // > at position 16
            assertEquals("tagmark", styleAt(spans, 16), "> is tagmark");
        }

        @Test
        @DisplayName("Self-closing tag with attributes — /> is fully tagmark")
        void selfClosingWithAttributes() {
            //  <Elem attr="val"/>
            //  012345678901234567
            String text = "<Elem attr=\"val\"/>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "E is anytag");
            assertEquals("attribute", styleAt(spans, 6), "attr is attribute");
            assertEquals("tagmark", styleAt(spans, 10), "= is tagmark");
            assertEquals("avalue", styleAt(spans, 11), "\"val\" is avalue");
            assertEquals("tagmark", styleAt(spans, 16), "/ is tagmark");
            assertEquals("tagmark", styleAt(spans, 17), "> is tagmark");
        }

        @Test
        @DisplayName("Tag with multiple attributes")
        void multipleAttributes() {
            String text = "<E a=\"1\" b=\"2\">";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "E is anytag");
            // a="1" at positions 3-7
            assertEquals("attribute", styleAt(spans, 3), "a is attribute");
            assertEquals("tagmark", styleAt(spans, 4), "= is tagmark");
            assertEquals("avalue", styleAt(spans, 5), "\"1\" is avalue");
            // b="2" at positions 9-13
            assertEquals("attribute", styleAt(spans, 9), "b is attribute");
            assertEquals("tagmark", styleAt(spans, 10), "= is tagmark");
            assertEquals("avalue", styleAt(spans, 11), "\"2\" is avalue");
            assertEquals("tagmark", styleAt(spans, 14), "> is tagmark");
        }
    }

    @Nested
    @DisplayName("Comments")
    class CommentTests {

        @Test
        @DisplayName("XML comment is fully styled as comment")
        void xmlComment() {
            String text = "<!-- comment -->";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            for (int i = 0; i < text.length(); i++) {
                assertEquals("comment", styleAt(spans, i), "Position " + i + " should be comment");
            }
        }

        @Test
        @DisplayName("Comment between tags does not interfere")
        void commentBetweenTags() {
            String text = "<A><!-- c --></A>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            // <A>
            assertEquals("tagmark", styleAt(spans, 0));
            assertEquals("anytag", styleAt(spans, 1));
            assertEquals("tagmark", styleAt(spans, 2));
            // <!-- c -->
            assertEquals("comment", styleAt(spans, 3));
            assertEquals("comment", styleAt(spans, 12));
            // </A>
            assertEquals("tagmark", styleAt(spans, 13));
            assertEquals("tagmark", styleAt(spans, 14));
            assertEquals("anytag", styleAt(spans, 15));
            assertEquals("tagmark", styleAt(spans, 16));
        }
    }

    @Nested
    @DisplayName("Mixed Content")
    class MixedContentTests {

        @Test
        @DisplayName("Text between tags is unstyled")
        void textBetweenTags() {
            //  <A>text</A>
            //  01234567890
            String text = "<A>text</A>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "A is anytag");
            assertEquals("tagmark", styleAt(spans, 2), "> is tagmark");
            // text at 3-6
            assertEquals("", styleAt(spans, 3), "t is unstyled");
            assertEquals("", styleAt(spans, 6), "t is unstyled");
            // </A> at 7-10
            assertEquals("tagmark", styleAt(spans, 7), "< is tagmark");
            assertEquals("tagmark", styleAt(spans, 8), "/ is tagmark");
            assertEquals("anytag", styleAt(spans, 9), "A is anytag");
            assertEquals("tagmark", styleAt(spans, 10), "> is tagmark");
        }

        @Test
        @DisplayName("Self-closing tag with text content around it")
        void selfClosingInContext() {
            String text = "pre<B/>post";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("", styleAt(spans, 0), "p is unstyled");
            assertEquals("", styleAt(spans, 2), "e is unstyled");
            assertEquals("tagmark", styleAt(spans, 3), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 4), "B is anytag");
            assertEquals("tagmark", styleAt(spans, 5), "/ is tagmark");
            assertEquals("tagmark", styleAt(spans, 6), "> is tagmark");
            assertEquals("", styleAt(spans, 7), "p is unstyled");
        }
    }

    @Nested
    @DisplayName("Multiple Tags — No Cumulative Offset")
    class CumulativeOffsetTests {

        @Test
        @DisplayName("Adjacent tags <A><B><C> — each < is tagmark at correct position")
        void adjacentOpenTags() {
            String text = "<A><B><C>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            // <A> at 0-2
            assertEquals("tagmark", styleAt(spans, 0), "<A>: < at 0 is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "<A>: A at 1 is anytag");
            assertEquals("tagmark", styleAt(spans, 2), "<A>: > at 2 is tagmark");
            // <B> at 3-5
            assertEquals("tagmark", styleAt(spans, 3), "<B>: < at 3 is tagmark");
            assertEquals("anytag", styleAt(spans, 4), "<B>: B at 4 is anytag");
            assertEquals("tagmark", styleAt(spans, 5), "<B>: > at 5 is tagmark");
            // <C> at 6-8
            assertEquals("tagmark", styleAt(spans, 6), "<C>: < at 6 is tagmark");
            assertEquals("anytag", styleAt(spans, 7), "<C>: C at 7 is anytag");
            assertEquals("tagmark", styleAt(spans, 8), "<C>: > at 8 is tagmark");
        }

        @Test
        @DisplayName("Nested tags <A><B><C></C></B></A> — all brackets at correct positions")
        void nestedTags() {
            String text = "<A><B><C></C></B></A>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            // Verify every < is tagmark
            int[] openBrackets = {0, 3, 6, 9, 13, 17};
            for (int pos : openBrackets) {
                assertEquals("tagmark", styleAt(spans, pos),
                        "< at position " + pos + " should be tagmark");
            }
            // Verify every > is tagmark
            int[] closeBrackets = {2, 5, 8, 12, 16, 20};
            for (int pos : closeBrackets) {
                assertEquals("tagmark", styleAt(spans, pos),
                        "> at position " + pos + " should be tagmark");
            }
        }

        @Test
        @DisplayName("Multiple self-closing tags — each /> is fully tagmark")
        void multipleSelfClosingTags() {
            String text = "<A/><B/><C/>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            // <A/> at 0-3
            assertEquals("tagmark", styleAt(spans, 0), "< at 0");
            assertEquals("anytag", styleAt(spans, 1), "A at 1");
            assertEquals("tagmark", styleAt(spans, 2), "/ at 2");
            assertEquals("tagmark", styleAt(spans, 3), "> at 3");
            // <B/> at 4-7
            assertEquals("tagmark", styleAt(spans, 4), "< at 4");
            assertEquals("anytag", styleAt(spans, 5), "B at 5");
            assertEquals("tagmark", styleAt(spans, 6), "/ at 6");
            assertEquals("tagmark", styleAt(spans, 7), "> at 7");
            // <C/> at 8-11
            assertEquals("tagmark", styleAt(spans, 8), "< at 8");
            assertEquals("anytag", styleAt(spans, 9), "C at 9");
            assertEquals("tagmark", styleAt(spans, 10), "/ at 10");
            assertEquals("tagmark", styleAt(spans, 11), "> at 11");
        }

        @Test
        @DisplayName("Cumulative offset regression: 10 consecutive elements have correct positions")
        void cumulativeOffsetRegression() {
            StringBuilder sb = new StringBuilder();
            String[] names = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon",
                    "Zeta", "Eta", "Theta", "Iota", "Kappa"};
            for (String name : names) {
                sb.append("<").append(name).append("/>");
            }
            String text = sb.toString();
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());

            // Verify each tag's < starts at the correct position with tagmark style
            int expectedPos = 0;
            for (String name : names) {
                // <Name/>
                assertEquals("tagmark", styleAt(spans, expectedPos),
                        "< for <" + name + "/> at position " + expectedPos + " should be tagmark");
                assertEquals("anytag", styleAt(spans, expectedPos + 1),
                        "First char of " + name + " at position " + (expectedPos + 1) + " should be anytag");
                assertEquals("anytag", styleAt(spans, expectedPos + name.length()),
                        "Last char of " + name + " at position " + (expectedPos + name.length()) + " should be anytag");
                assertEquals("tagmark", styleAt(spans, expectedPos + name.length() + 1),
                        "/ for <" + name + "/> at position " + (expectedPos + name.length() + 1) + " should be tagmark");
                assertEquals("tagmark", styleAt(spans, expectedPos + name.length() + 2),
                        "> for <" + name + "/> at position " + (expectedPos + name.length() + 2) + " should be tagmark");
                expectedPos += name.length() + 3; // <Name/> = 1 + name.length + 2
            }
        }

        @Test
        @DisplayName("Multi-line XML — positions are correct across newlines")
        void multiLineXml() {
            String text = "<Root>\n  <Child/>\n</Root>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());

            // <Root> at 0-5
            assertEquals("tagmark", styleAt(spans, 0));
            assertEquals("anytag", styleAt(spans, 1));
            assertEquals("tagmark", styleAt(spans, 5));

            // \n  = positions 6-8 (unstyled)
            assertEquals("", styleAt(spans, 6));

            // <Child/> at 9-16
            assertEquals("tagmark", styleAt(spans, 9), "< of <Child/>");
            assertEquals("anytag", styleAt(spans, 10), "C of Child");
            assertEquals("anytag", styleAt(spans, 14), "d of Child");
            assertEquals("tagmark", styleAt(spans, 15), "/ of />");
            assertEquals("tagmark", styleAt(spans, 16), "> of />");

            // \n = position 17 (unstyled)
            assertEquals("", styleAt(spans, 17));

            // </Root> at 18-24: < / R o o t >
            assertEquals("tagmark", styleAt(spans, 18), "< of </Root>");
            assertEquals("tagmark", styleAt(spans, 19), "/ of </Root>");
            assertEquals("anytag", styleAt(spans, 20), "R of Root");
            assertEquals("anytag", styleAt(spans, 23), "t of Root");
            assertEquals("tagmark", styleAt(spans, 24), "> of </Root>");
        }

        @Test
        @DisplayName("Mixed self-closing and regular tags with attributes — no offset")
        void mixedTagsWithAttributes() {
            String text = "<A x=\"1\"/><B y=\"2\"><C/></B>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());

            // Verify the < positions
            assertEquals("tagmark", styleAt(spans, 0), "< of <A>");
            assertEquals("tagmark", styleAt(spans, 10), "< of <B>");
            assertEquals("tagmark", styleAt(spans, 18), "< of <C/>");
            assertEquals("tagmark", styleAt(spans, 22), "< of </B>");
        }
    }

    @Nested
    @DisplayName("Namespaced Elements")
    class NamespaceTests {

        @Test
        @DisplayName("Namespaced element xs:element — prefix is anytag")
        void namespacedElement() {
            // The regex \\w+ matches word characters (letters, digits, underscore)
            // 'xs' matches as the element name, ':element' goes to attributes section
            String text = "<xs:element/>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());
            assertEquals("tagmark", styleAt(spans, 0), "< is tagmark");
            assertEquals("anytag", styleAt(spans, 1), "x is anytag");
            assertEquals("anytag", styleAt(spans, 2), "s is anytag");
            // : and element go into attributes section (unstyled, no = so not attribute)
            assertEquals("tagmark", styleAt(spans, text.length() - 2), "/ is tagmark");
            assertEquals("tagmark", styleAt(spans, text.length() - 1), "> is tagmark");
        }
    }

    @Nested
    @DisplayName("Span Length Integrity")
    class SpanLengthTests {

        @Test
        @DisplayName("Total span length equals text length for various inputs")
        void totalSpanLengthMatchesTextLength() {
            String[] inputs = {
                    "<Root/>",
                    "<A><B/></A>",
                    "<A attr=\"val\">text</A>",
                    "<!-- comment --><Tag/>",
                    "text<A/>more<B>inner</B>end",
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                            "  <xs:element name=\"root\" type=\"xs:string\"/>\n" +
                            "</xs:schema>"
            };

            for (String input : inputs) {
                StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(input);
                assertEquals(input.length(), spans.length(),
                        "Span length mismatch for: " + input);
            }
        }

        @Test
        @DisplayName("No span has negative or zero length (except leading empty gap)")
        void noNegativeOrZeroSpans() {
            String text = "<A attr=\"1\"><B/><C x=\"2\" y=\"3\">text</C></A>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            int totalLength = 0;
            boolean firstSpan = true;
            for (StyleSpan<Collection<String>> span : spans) {
                if (firstSpan) {
                    // First span might be zero-length empty gap if match starts at position 0
                    assertTrue(span.getLength() >= 0,
                            "First span should have non-negative length, got " + span.getLength());
                    firstSpan = false;
                } else {
                    assertTrue(span.getLength() > 0,
                            "Non-first span should have positive length, got " + span.getLength() +
                                    " at cumulative position " + totalLength);
                }
                totalLength += span.getLength();
            }
            assertEquals(text.length(), totalLength);
        }
    }

    @Nested
    @DisplayName("XML Declaration")
    class XmlDeclarationTests {

        @Test
        @DisplayName("XML declaration followed by tags — subsequent tags are correctly positioned")
        void xmlDeclarationDoesNotShift() {
            // <?xml version="1.0"?> is captured as an ELEMENT by the regex
            // (the ? in version matches \\w, and ?> matches /?>)
            String text = "<?xml version=\"1.0\"?><Root/>";
            StyleSpans<Collection<String>> spans = XmlSyntaxHighlighter.computeHighlighting(text);

            assertEquals(text.length(), spans.length());

            // The declaration ends at position 20, <Root/> starts at 21
            int rootStart = text.indexOf("<Root/>");
            assertEquals("tagmark", styleAt(spans, rootStart), "< of <Root/>");
            assertEquals("anytag", styleAt(spans, rootStart + 1), "R of Root");
            assertEquals("tagmark", styleAt(spans, rootStart + 5), "/ of />");
            assertEquals("tagmark", styleAt(spans, rootStart + 6), "> of />");
        }
    }
}
