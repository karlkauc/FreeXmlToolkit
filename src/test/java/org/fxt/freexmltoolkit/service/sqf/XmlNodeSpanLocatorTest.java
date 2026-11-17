package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.ElementRegion;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Span;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Step;
import org.junit.jupiter.api.Test;

class XmlNodeSpanLocatorTest {

    private static List<Step> path(Object... parts) {
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < parts.length; i += 2) {
            steps.add(new Step((String) parts[i], (Integer) parts[i + 1]));
        }
        return steps;
    }

    @Test
    void locatesNestedRepeatedSiblings() {
        String xml = "<root><item><name>a</name></item><item><name>b</name></item></root>";
        Optional<ElementRegion> region = XmlNodeSpanLocator.elementRegion(
                xml, path("root", 1, "item", 2, "name", 1));
        assertTrue(region.isPresent());
        Span span = region.get().span();
        assertEquals("<name>b</name>", xml.substring(span.start(), span.end()));
    }

    @Test
    void locatesSelfClosingElement() {
        String xml = "<root><a/><b attr=\"x\"/></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1, "b", 1)).orElseThrow();
        assertEquals("<b attr=\"x\"/>", xml.substring(region.span().start(), region.span().end()));
        assertTrue(region.selfClosing());
    }

    @Test
    void ignoresTagLikeTextInCommentsAndCdata() {
        String xml = "<root><!-- <item>no</item> --><![CDATA[<item>no</item>]]><item>yes</item></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1, "item", 1)).orElseThrow();
        assertEquals("<item>yes</item>", xml.substring(region.span().start(), region.span().end()));
    }

    @Test
    void handlesGtInAttributeValues() {
        String xml = "<root><item note=\"a > b\">x</item></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1, "item", 1)).orElseThrow();
        assertEquals("<item note=\"a > b\">x</item>", xml.substring(region.span().start(), region.span().end()));
        assertFalse(region.selfClosing());
        assertEquals("x", xml.substring(region.contentStart(), region.contentEnd()));
    }

    @Test
    void attributeSpanCoversNameAndValueWithLeadingSpace() {
        String xml = "<root><item first=\"1\" second='2'>x</item></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1, "item", 1)).orElseThrow();
        Span second = XmlNodeSpanLocator.attributeSpan(xml, region, "second").orElseThrow();
        assertEquals(" second='2'", xml.substring(second.start(), second.end()));
        assertTrue(XmlNodeSpanLocator.attributeSpan(xml, region, "missing").isEmpty());
    }

    @Test
    void textSegmentsAreTheGapsBetweenChildElements() {
        String xml = "<root>alpha<child/>beta<child/></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1)).orElseThrow();
        List<Span> segments = XmlNodeSpanLocator.textSegmentSpans(xml, region);
        assertEquals(2, segments.size());
        assertEquals("alpha", xml.substring(segments.get(0).start(), segments.get(0).end()));
        assertEquals("beta", xml.substring(segments.get(1).start(), segments.get(1).end()));
    }

    @Test
    void mixedPrefixSiblingsCountByLocalName() {
        // positional paths count same *local* names, matching Saxon's local-name walk
        String xml = "<root xmlns:a=\"urn:a\" xmlns:b=\"urn:b\"><a:item>1</a:item><b:item>2</b:item></root>";
        ElementRegion region = XmlNodeSpanLocator.elementRegion(xml, path("root", 1, "item", 2)).orElseThrow();
        assertEquals("<b:item>2</b:item>", xml.substring(region.span().start(), region.span().end()));
    }

    @Test
    void indentationAtReturnsLeadingWhitespaceOfLine() {
        String xml = "<root>\n    <item>\n\t<sub/>\n    </item>\n</root>";
        int itemOffset = xml.indexOf("<item>");
        assertEquals("    ", XmlNodeSpanLocator.indentationAt(xml, itemOffset));
        int subOffset = xml.indexOf("<sub/>");
        assertEquals("\t", XmlNodeSpanLocator.indentationAt(xml, subOffset));
    }
    @Test
    void resolvesManyPathsWithOneScan() {
        StringBuilder xml = new StringBuilder("<root><list>");
        for (int i = 1; i <= 5000; i++) {
            xml.append("<item><v>").append(i).append("</v></item>");
        }
        xml.append("</list></root>");
        String text = xml.toString();
        List<List<Step>> paths = new java.util.ArrayList<>();
        for (int i = 1; i <= 5000; i++) {
            paths.add(path("root", 1, "list", 1, "item", i, "v", 1));
        }
        paths.add(path("root", 1, "list", 1, "item", 5001));
        paths.add(path("root", 1, "missing", 1));
        paths.add(List.of());
        List<Optional<ElementRegion>> regions = XmlNodeSpanLocator.elementRegions(text, paths);
        assertEquals(paths.size(), regions.size());
        for (int i : new int[] {1, 2, 4999, 5000}) {
            ElementRegion region = regions.get(i - 1).orElseThrow();
            assertEquals(String.valueOf(i), text.substring(region.contentStart(), region.contentEnd()));
            assertEquals(XmlNodeSpanLocator.elementRegion(text, paths.get(i - 1)), regions.get(i - 1));
        }
        assertFalse(regions.get(5000).isPresent());
        assertFalse(regions.get(5001).isPresent());
        assertFalse(regions.get(5002).isPresent());
    }
}
