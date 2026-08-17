package org.fxt.freexmltoolkit.service.xmledit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathEditException;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.junit.jupiter.api.Test;

import net.sf.saxon.s9api.XdmNodeKind;

class XPathMatchLocatorTest {

    private static final String DOC = """
            <order>
              <item ccy="EUR">
                <price>10.5</price>
              </item>
              <item ccy="USD">
                <price>20</price>
              </item>
            </order>
            """;

    private static XPathQuery q(String xpath) {
        return new XPathQuery(xpath, Map.of());
    }

    @Test
    void locatesElementsWithSpansAndLines() throws Exception {
        List<NodeMatch> matches = XPathMatchLocator.locate(DOC, q("//price"));
        assertEquals(2, matches.size());
        NodeMatch first = matches.get(0);
        assertTrue(first.located());
        assertEquals(XdmNodeKind.ELEMENT, first.kind());
        assertEquals(3, first.line());
        assertEquals("<price>10.5</price>", first.matchedText());
        assertEquals("10.5", DOC.substring(first.contentStart(), first.contentEnd()));
    }

    @Test
    void locatesAttributes() throws Exception {
        List<NodeMatch> matches = XPathMatchLocator.locate(DOC, q("//item/@ccy"));
        assertEquals(2, matches.size());
        NodeMatch first = matches.get(0);
        assertEquals(XdmNodeKind.ATTRIBUTE, first.kind());
        assertTrue(first.matchedText().contains("ccy=\"EUR\""));
        assertTrue(first.line() > 0, "attribute inherits the owning element's line");
    }

    @Test
    void locatesTextNodes() throws Exception {
        List<NodeMatch> matches = XPathMatchLocator.locate(DOC, q("//price/text()"));
        assertEquals(2, matches.size());
        assertEquals(XdmNodeKind.TEXT, matches.get(0).kind());
        assertEquals("10.5", matches.get(0).matchedText());
    }

    @Test
    void positionalPredicateSelectsOneNode() throws Exception {
        List<NodeMatch> matches = XPathMatchLocator.locate(DOC, q("//item[2]/price"));
        assertEquals(1, matches.size());
        assertEquals("<price>20</price>", matches.get(0).matchedText());
    }

    @Test
    void defaultNamespaceBindingWorks() throws Exception {
        String doc = "<root xmlns=\"urn:test\"><a>1</a></root>";
        // without the binding, //a matches nothing
        assertEquals(0, XPathMatchLocator.locate(doc, q("//a")).size());
        List<NodeMatch> matches = XPathMatchLocator.locate(doc,
                new XPathQuery("//a", Map.of("", "urn:test")));
        assertEquals(1, matches.size());
        assertEquals("<a>1</a>", matches.get(0).matchedText());
    }

    @Test
    void prefixedNamespaceBindingWorks() throws Exception {
        String doc = "<p:root xmlns:p=\"urn:p\"><p:a>1</p:a></p:root>";
        List<NodeMatch> matches = XPathMatchLocator.locate(doc,
                new XPathQuery("//x:a", Map.of("x", "urn:p")));
        assertEquals(1, matches.size());
        assertEquals("<p:a>1</p:a>", matches.get(0).matchedText());
    }

    @Test
    void atomicOnlyResultThrowsHelpfully() {
        XPathEditException e = assertThrows(XPathEditException.class,
                () -> XPathMatchLocator.locate(DOC, q("count(//item)")));
        assertTrue(e.getMessage().contains("values, not nodes"));
    }

    @Test
    void malformedDocumentThrows() {
        assertThrows(XPathEditException.class,
                () -> XPathMatchLocator.locate("<a><b></a>", q("//a")));
    }

    @Test
    void invalidXPathThrows() {
        assertThrows(XPathEditException.class,
                () -> XPathMatchLocator.locate(DOC, q("//[broken")));
    }

    @Test
    void xpath31FunctionNamespacesArePredeclared() throws Exception {
        // map/math prefixes usable without user declarations
        List<NodeMatch> matches = XPathMatchLocator.locate(DOC,
                q("//price[number(.) gt math:sqrt(200)]"));
        assertEquals(1, matches.size());
        assertEquals("<price>20</price>", matches.get(0).matchedText());
    }

    @Test
    void commentsAreReportedAsUnlocatable() throws Exception {
        String doc = "<a><!-- note --><b/></a>";
        List<NodeMatch> matches = XPathMatchLocator.locate(doc, q("//comment()"));
        assertEquals(1, matches.size());
        assertFalse(matches.get(0).located());
        assertNotNull(matches.get(0).error());
    }

    @Test
    void extractRootNamespacesFindsDefaultAndPrefixed() {
        String doc = "<root xmlns=\"urn:d\" xmlns:p=\"urn:p\"><p:a/></root>";
        Map<String, String> ns = XPathMatchLocator.extractRootNamespaces(doc);
        assertEquals("urn:d", ns.get(""));
        assertEquals("urn:p", ns.get("p"));
        assertFalse(ns.containsKey("xml"));
    }

    @Test
    void sameLocalNameDifferentNamespaceIsGuarded() throws Exception {
        // Two siblings share the local name "a" in different namespaces. The
        // local-name-only path resolves //y:a (2nd "a" overall) to position 1 of
        // its local name — i.e. the WRONG first <x:a> — the tag-name guard cannot
        // catch this (same local name), but the span must at least be a real "a"
        // element, never a crash. Document the known limitation here.
        String doc = "<r xmlns:x=\"urn:x\" xmlns:y=\"urn:y\"><x:a>1</x:a><y:a>2</y:a></r>";
        List<NodeMatch> matches = XPathMatchLocator.locate(doc,
                new XPathQuery("//y:a", Map.of("y", "urn:y")));
        assertEquals(1, matches.size());
        NodeMatch match = matches.get(0);
        assertTrue(match.located());
        assertEquals("<y:a>2</y:a>", match.matchedText(),
                "positional path counts same-local-name siblings, so y:a is position 2");
    }
}
