package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XPathCompiler;

/** Watches evaluate against the paused context item and the variables in scope. */
class WatchEvaluatorTest {

    private static final Processor PROCESSOR = new Processor(false);

    private static PausedSnapshot snapshot() throws Exception {
        XdmNode doc = PROCESSOR.newDocumentBuilder().build(new StreamSource(new StringReader(
                "<order><item n='1'/><item n='2'/><item n='3'/></order>")));
        XPathCompiler xp = PROCESSOR.newXPathCompiler();
        XdmNode order = (XdmNode) xp.evaluateSingle("/order", doc);
        Map<QName, XdmValue> locals = Map.of(new QName("count"), new XdmAtomicValue(3));
        Map<QName, XdmValue> globals = Map.of(
                new QName("count"), new XdmAtomicValue(99),         // shadowed by the local
                new QName("title"), new XdmAtomicValue("Order #7"));
        return new PausedSnapshot("file:/tmp/a.xsl", 5, List.of(), List.of(), "<order>", order,
                locals, globals, 2, 4);
    }

    @Test
    void evaluatesAgainstTheContextItem() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertEquals("3", eval.evaluate("count(item)", snapshot()));
        assertEquals("order", eval.evaluate("name(.)", snapshot()));
    }

    @Test
    void localsShadowGlobalsAndGlobalsStayVisible() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertEquals("3", eval.evaluate("$count", snapshot()), "the local $count wins");
        assertEquals("Order #7", eval.evaluate("$title", snapshot()));
        assertEquals("Order #7 (3)", eval.evaluate("concat($title, ' (', $count, ')')", snapshot()));
    }

    @Test
    void focusFunctionsComeFromTheSnapshot() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertEquals("2", eval.evaluate("position()", snapshot()));
        assertEquals("4", eval.evaluate("last()", snapshot()));
    }

    @Test
    void errorsAndMissingPauseAreReportedAsText() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertTrue(eval.evaluate("$unknown", snapshot()).startsWith("ERROR:"));
        assertTrue(eval.evaluate("count(", snapshot()).startsWith("ERROR:"));
        assertEquals("<not paused>", eval.evaluate("count(item)", null));
        assertEquals("", eval.evaluate("   ", snapshot()));
    }

    @Test
    void stylesheetNamespacesAreAvailable() throws Exception {
        String xslt = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
                + "    xmlns:f='urn:f' xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                + "  <xsl:template match=\"/\"/>\n</xsl:stylesheet>";
        Map<String, String> ns = WatchEvaluator.namespacesOf(xslt);
        assertEquals("urn:f", ns.get("f"));
        assertEquals("http://www.w3.org/1999/XSL/Transform", ns.get("xsl"));
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, ns);
        assertEquals("true", eval.evaluate("xs:integer($count) instance of xs:integer", snapshot()));
    }

    @Test
    void rendersSequencesAndNodes() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        String items = eval.evaluate("item/@n/string()", snapshot());
        assertTrue(items.startsWith("1, 2, 3") && items.contains("(3 items)"), items);
        assertTrue(eval.evaluate("item[1]", snapshot()).contains("<item n=\"1\"/>"));
        assertEquals("<empty sequence>", eval.evaluate("item[9]", snapshot()));
        assertEquals("xs:integer", WatchEvaluator.describeType(new XdmAtomicValue(new net.sf.saxon.value.Int64Value(1))));
        assertEquals("element(order)", WatchEvaluator.describeType(snapshot().contextXdm()));
    }
}
