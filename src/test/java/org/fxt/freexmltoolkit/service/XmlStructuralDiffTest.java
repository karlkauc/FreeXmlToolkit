package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Change;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Difference;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.NodeKind;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link XmlStructuralDiff}: what counts as a difference, lines, XPaths, options. */
@DisplayName("XmlStructuralDiff")
class XmlStructuralDiffTest {

    private static final Options DEFAULTS = Options.DEFAULTS;

    private static List<Difference> diff(String left, String right, Options options) {
        Result result = XmlStructuralDiff.compare(left, right, options);
        assertTrue(result.compared(), result.leftError() + " / " + result.rightError());
        return result.differences();
    }

    @Test
    @DisplayName("formatting, attribute order and prefixes do not count")
    void equivalent() {
        String left = "<p:order xmlns:p=\"urn:o\" id=\"1\" date=\"x\"><p:item>A</p:item></p:order>";
        String right = """
                <?xml version="1.0" encoding="UTF-8"?>
                <o:order date="x"   id="1" xmlns:o="urn:o">
                    <o:item>
                        A
                    </o:item>
                </o:order>
                """;
        assertTrue(XmlStructuralDiff.compare(left, right, DEFAULTS).equivalent());
        List<Difference> prefixes = diff(left, right, new Options(true, false, true, false, List.of()));
        assertFalse(prefixes.isEmpty());
        assertTrue(prefixes.stream().allMatch(d -> d.kind() == NodeKind.NAMESPACE), prefixes.toString());
        assertFalse(XmlStructuralDiff.compare(left, right, new Options(false, false, false, false, List.of())).equivalent(),
                "exact whitespace sees the indentation");
    }

    @Test
    @DisplayName("added, removed and changed nodes with lines and XPaths on both sides")
    void changes() {
        String left = """
                <order>
                  <item sku="A" qty="1">Apple</item>
                  <note>old</note>
                  <gone/>
                </order>
                """;
        String right = """
                <order>
                  <item sku="A" qty="2" color="red">Apple</item>
                  <note>new</note>
                  <extra>x</extra>
                </order>
                """;
        List<Difference> differences = diff(left, right, DEFAULTS);
        Difference qty = find(differences, Change.CHANGED, NodeKind.ATTRIBUTE, "qty");
        assertEquals("1", qty.leftValue());
        assertEquals("2", qty.rightValue());
        assertEquals(2, qty.leftLine());
        assertEquals(2, qty.rightLine());
        assertEquals("/order[1]/item[1]/@qty", qty.leftXPath());

        Difference color = find(differences, Change.ADDED, NodeKind.ATTRIBUTE, "color");
        assertEquals("red", color.rightValue());

        Difference note = find(differences, Change.CHANGED, NodeKind.TEXT, "#text");
        assertEquals("old", note.leftValue());
        assertEquals("new", note.rightValue());
        assertEquals(3, note.leftLine());

        Difference gone = find(differences, Change.REMOVED, NodeKind.ELEMENT, "gone");
        assertEquals(4, gone.leftLine());
        assertEquals(1, gone.rightLine(), "the parent's line on the other side");
        Difference extra = find(differences, Change.ADDED, NodeKind.ELEMENT, "extra");
        assertEquals(4, extra.rightLine());
        assertTrue(extra.rightValue().startsWith("<extra>x</extra>"), extra.rightValue());
        assertTrue(qty.describe().contains("\"1\" → \"2\""), qty.describe());
    }

    @Test
    @DisplayName("element order: reported as moves, or ignored; key attributes pair repeated elements")
    void order() {
        String left = "<r><a/><b/><item id=\"1\">x</item><item id=\"2\">y</item></r>";
        String right = "<r><b/><a/><item id=\"2\">y</item><item id=\"1\">x</item></r>";
        List<Difference> strict = diff(left, right, DEFAULTS);
        assertTrue(strict.stream().anyMatch(d -> d.change() == Change.MOVED), strict.toString());

        List<Difference> unordered = diff(left, right, new Options(true, false, false, true, List.of()));
        assertTrue(unordered.isEmpty(), unordered.toString());

        List<Difference> keyed = diff(left, right, new Options(true, false, false, false, Options.parseKeys("id")));
        assertTrue(keyed.stream().noneMatch(d -> d.kind() == NodeKind.TEXT),
                "items paired by id, so their texts are equal: " + keyed);
        assertTrue(keyed.stream().allMatch(d -> d.change() == Change.MOVED), keyed.toString());
    }

    @Test
    @DisplayName("comments are compared unless ignored")
    void comments() {
        String left = "<r><!-- one --><a/></r>";
        String right = "<r><!-- two --><a/></r>";
        Difference comment = find(diff(left, right, DEFAULTS), Change.CHANGED, NodeKind.COMMENT, "#comment");
        assertEquals(" one ", comment.leftValue());
        assertTrue(XmlStructuralDiff.compare(left, right, new Options(true, true, false, false, List.of())).equivalent());
    }

    @Test
    @DisplayName("a document that is not well-formed is named with its line; no external entities")
    void errors() {
        Result result = XmlStructuralDiff.compare("<a>\n<b></a>", "<a/>", DEFAULTS);
        assertFalse(result.compared());
        assertNotNull(result.leftError());
        assertTrue(result.leftError().contains("line 2"), result.leftError());
        assertEquals(null, result.rightError());

        Result xxe = XmlStructuralDiff.compare(
                "<!DOCTYPE a [<!ENTITY e SYSTEM \"file:///etc/hostname\">]><a>&e;</a>", "<a/>", DEFAULTS);
        assertTrue(!xxe.compared() || xxe.differences().stream().noneMatch(d -> d.leftValue().length() > 0
                && d.kind() == NodeKind.TEXT), "external entity must not be read: " + xxe);
        assertTrue(XmlStructuralDiff.looksLikeXml("﻿  <a/>"));
        assertFalse(XmlStructuralDiff.looksLikeXml("{\"a\": 1}"));
    }

    private static Difference find(List<Difference> differences, Change change, NodeKind kind, String name) {
        return differences.stream().filter(d -> d.change() == change && d.kind() == kind && d.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(change + " " + kind + " " + name + " not in " + differences));
    }
}
