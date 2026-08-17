package org.fxt.freexmltoolkit.service.xmledit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathEditException;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner.PlanResult;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner.ReplaceMode;
import org.junit.jupiter.api.Test;

class XPathReplacePlannerTest {

    private static XPathQuery q(String xpath) {
        return new XPathQuery(xpath, Map.of());
    }

    private static String apply(String xml, String xpath, ReplaceMode mode, String argument)
            throws XPathEditException {
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, q(xpath));
        PlanResult result = XPathReplacePlanner.plan(xml, q(xpath), mode, argument, matches);
        return result.plan().applyTo(xml);
    }

    // ---- SET_VALUE --------------------------------------------------------

    @Test
    void setValueOnElementReplacesContentOnly() throws Exception {
        String xml = "<a>\n  <b attr=\"k\">old</b>\n</a>";
        assertEquals("<a>\n  <b attr=\"k\">new</b>\n</a>",
                apply(xml, "//b", ReplaceMode.SET_VALUE, "new"));
    }

    @Test
    void setValueOnSelfClosingElementExpandsIt() throws Exception {
        String xml = "<a><b x=\"1\"/></a>";
        assertEquals("<a><b x=\"1\">v</b></a>", apply(xml, "//b", ReplaceMode.SET_VALUE, "v"));
    }

    @Test
    void setValueOnAttributeReplacesQuotedValue() throws Exception {
        String xml = "<a><b ccy=\"EUR\">1</b><b ccy=\"EUR\">2</b></a>";
        assertEquals("<a><b ccy=\"CHF\">1</b><b ccy=\"CHF\">2</b></a>",
                apply(xml, "//b/@ccy", ReplaceMode.SET_VALUE, "CHF"));
    }

    @Test
    void setValueOnTextNode() throws Exception {
        String xml = "<a><b>old</b></a>";
        assertEquals("<a><b>new</b></a>", apply(xml, "//b/text()", ReplaceMode.SET_VALUE, "new"));
    }

    @Test
    void setValueEscapesMarkup() throws Exception {
        String xml = "<a><b>old</b><c v=\"x\"/></a>";
        // '>' needs no escaping in XML text content (same policy as the SQF engine)
        assertEquals("<a><b>&lt;tag> &amp; more</b><c v=\"x\"/></a>",
                apply(xml, "//b", ReplaceMode.SET_VALUE, "<tag> & more"));
        assertEquals("<a><b>old</b><c v=\"say &quot;hi&quot; &amp; go\"/></a>",
                apply(xml, "//c/@v", ReplaceMode.SET_VALUE, "say \"hi\" & go"));
    }

    @Test
    void setValueKeepsSurroundingFormatting() throws Exception {
        String xml = """
                <root>
                    <item   ccy="EUR"  >10</item>
                </root>
                """;
        assertEquals("""
                <root>
                    <item   ccy="EUR"  >99</item>
                </root>
                """, apply(xml, "//item", ReplaceMode.SET_VALUE, "99"));
    }

    // ---- COMPUTE_VALUE ----------------------------------------------------

    @Test
    void computeValueUsesMatchAsContext() throws Exception {
        String xml = "<a><p>10</p><p>20</p></a>";
        assertEquals("<a><p>20</p><p>40</p></a>",
                apply(xml, "//p", ReplaceMode.COMPUTE_VALUE, "string(number(.) * 2)"));
    }

    @Test
    void computeValueWithConcatSuffix() throws Exception {
        String xml = "<a><n ccy=\"EUR\"/><n ccy=\"USD\"/></a>";
        assertEquals("<a><n ccy=\"EUR-X\"/><n ccy=\"USD-X\"/></a>",
                apply(xml, "//n/@ccy", ReplaceMode.COMPUTE_VALUE, "concat(., '-X')"));
    }

    @Test
    void invalidComputeExpressionThrows() throws Exception {
        String xml = "<a><b>1</b></a>";
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, q("//b"));
        assertThrows(XPathEditException.class, () -> XPathReplacePlanner.plan(
                xml, q("//b"), ReplaceMode.COMPUTE_VALUE, "((broken", matches));
    }

    // ---- DELETE -----------------------------------------------------------

    @Test
    void deleteElementRemovesWholeLine() throws Exception {
        String xml = """
                <root>
                  <keep>1</keep>
                  <drop>2</drop>
                  <keep>3</keep>
                </root>
                """;
        assertEquals("""
                <root>
                  <keep>1</keep>
                  <keep>3</keep>
                </root>
                """, apply(xml, "//drop", ReplaceMode.DELETE, null));
    }

    @Test
    void deleteAttributeIncludesLeadingWhitespace() throws Exception {
        String xml = "<a><b keep=\"1\" drop=\"2\">x</b></a>";
        assertEquals("<a><b keep=\"1\">x</b></a>",
                apply(xml, "//b/@drop", ReplaceMode.DELETE, null));
    }

    @Test
    void deleteTextNodeClearsSegment() throws Exception {
        String xml = "<a><b>gone</b></a>";
        assertEquals("<a><b></b></a>", apply(xml, "//b/text()", ReplaceMode.DELETE, null));
    }

    @Test
    void deleteSubsumedDescendantsAreDroppedNotDoubled() throws Exception {
        String xml = "<root><outer><inner>1</inner></outer></root>";
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, q("//outer | //inner"));
        assertEquals(2, matches.size());
        PlanResult result = XPathReplacePlanner.plan(
                xml, q("//outer | //inner"), ReplaceMode.DELETE, null, matches);
        assertEquals(1, result.subsumed());
        assertEquals("<root></root>", result.plan().applyTo(xml));
    }

    // ---- REPLACE_FRAGMENT -------------------------------------------------

    @Test
    void replaceElementWithFragment() throws Exception {
        String xml = "<a><b>old</b></a>";
        assertEquals("<a><c attr=\"1\">new</c></a>",
                apply(xml, "//b", ReplaceMode.REPLACE_FRAGMENT, "<c attr=\"1\">new</c>"));
    }

    @Test
    void multiLineFragmentIsReindented() throws Exception {
        String xml = """
                <root>
                    <b>old</b>
                </root>
                """;
        String replaced = apply(xml, "//b", ReplaceMode.REPLACE_FRAGMENT,
                "<c>\n<d>1</d>\n</c>");
        assertEquals("""
                <root>
                    <c>
                    <d>1</d>
                    </c>
                </root>
                """, replaced);
    }

    @Test
    void malformedFragmentIsRejected() throws Exception {
        String xml = "<a><b>old</b></a>";
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, q("//b"));
        XPathEditException e = assertThrows(XPathEditException.class, () ->
                XPathReplacePlanner.plan(xml, q("//b"), ReplaceMode.REPLACE_FRAGMENT,
                        "<c>unclosed", matches));
        assertTrue(e.getMessage().contains("well-formed"));
    }

    @Test
    void fragmentOnAttributeIsRejected() throws Exception {
        String xml = "<a><b x=\"1\">v</b></a>";
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, q("//b/@x"));
        assertThrows(XPathEditException.class, () -> XPathReplacePlanner.plan(
                xml, q("//b/@x"), ReplaceMode.REPLACE_FRAGMENT, "<c/>", matches));
    }

    @Test
    void prefixedFragmentValidatesWithQueryNamespaces() throws Exception {
        String xml = "<a xmlns:p=\"urn:p\"><p:b>old</p:b></a>";
        XPathQuery query = new XPathQuery("//p:b", Map.of("p", "urn:p"));
        List<NodeMatch> matches = XPathMatchLocator.locate(xml, query);
        PlanResult result = XPathReplacePlanner.plan(xml, query,
                ReplaceMode.REPLACE_FRAGMENT, "<p:c>new</p:c>", matches);
        assertEquals("<a xmlns:p=\"urn:p\"><p:c>new</p:c></a>", result.plan().applyTo(xml));
    }
}
