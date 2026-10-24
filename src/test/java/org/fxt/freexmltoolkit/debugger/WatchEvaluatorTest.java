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

    // ----- value viewer: full serialization and XPath against a value -------------------

    private static XdmNode longOrder() throws Exception {
        StringBuilder xml = new StringBuilder("<order id='4711'>");
        for (int i = 1; i <= 20; i++) {
            xml.append("<item n='").append(i).append("'><price>").append(i * 10).append(".50</price></item>");
        }
        xml.append("</order>");
        XdmNode doc = PROCESSOR.newDocumentBuilder().build(new StreamSource(new StringReader(xml.toString())));
        return (XdmNode) PROCESSOR.newXPathCompiler().evaluateSingle("/order", doc);
    }

    @Test
    void serializeKeepsTheWholeValueWithoutDeclaration() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        XdmNode order = longOrder();
        String flat = eval.serialize(order, false);
        assertTrue(flat.length() > VariableBinding.MAX_VALUE_LENGTH, "longer than the table cell, was " + flat.length());
        assertTrue(flat.startsWith("<order id=\"4711\">"), flat);
        assertTrue(flat.endsWith("</order>"), "not truncated");
        assertFalse(flat.contains("<?xml"), "no XML declaration");
        assertFalse(flat.contains("\n"), "no indentation when pretty print is off");
        String pretty = eval.serialize(order, true);
        assertTrue(pretty.contains("\n   <item n=\"1\">") || pretty.contains("\n  <item n=\"1\">"),
                "indented when pretty print is on: " + pretty.substring(0, 80));
    }

    @Test
    void serializeWritesOneItemPerLine() {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        XdmValue seq = new XdmValue(List.of(new XdmAtomicValue(1), new XdmAtomicValue("two"), new XdmAtomicValue(3.5)));
        assertEquals("1\ntwo\n3.5", eval.serialize(seq, true));
        assertEquals("<empty sequence>", eval.serialize(net.sf.saxon.s9api.XdmEmptySequence.getInstance(), true));
    }

    @Test
    void evaluateValueUsesTheFocusAsContextAndBindsValue() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        XdmNode order = longOrder();
        assertEquals("20.50", eval.evaluateValue("item[@n='2']/price/string()", snapshot(), order).toString());
        assertEquals("20", eval.evaluateValue("count($value/item)", snapshot(), order).toString());
        assertEquals("3", eval.evaluateValue("$count", snapshot(), order).toString(),
                "the pause's variables stay in scope");
        XdmValue seq = new XdmValue(List.of(new XdmAtomicValue(5), new XdmAtomicValue(7)));
        assertEquals("7", eval.evaluateValue("$value[2]", snapshot(), seq).toString(),
                "a sequence is reachable through $value");
    }

    @Test
    void evaluateValueWithoutFocusBehavesLikeAWatch() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertEquals("3", eval.evaluateValue("count(item)", snapshot(), null).toString());
    }

    @Test
    void aStylesheetVariableNamedValueWins() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        PausedSnapshot base = snapshot();
        PausedSnapshot withValue = new PausedSnapshot(base.systemId(), base.lineNumber(), List.of(), List.of(),
                base.contextItem(), base.contextXdm(), Map.of(new QName("value"), new XdmAtomicValue("mine")),
                Map.of(), 1, 1);
        assertEquals("mine", eval.evaluateValue("$value", withValue, new XdmAtomicValue("viewed")).toString());
    }

    @Test
    void evaluateValueThrowsOnBadXPath() {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertThrows(net.sf.saxon.s9api.SaxonApiException.class,
                () -> eval.evaluateValue("item[", snapshot(), null));
    }

    @Test
    void conditionsUseEffectiveBooleanValue() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertTrue(eval.test("count(item) = 3", snapshot()));
        assertFalse(eval.test("$count > 5", snapshot()));
        assertTrue(eval.test("item[@n = '2']", snapshot()), "a non-empty node sequence is true");
        assertFalse(eval.test("item[@n = '9']", snapshot()), "an empty sequence is false");
        assertTrue(eval.test("  ", snapshot()), "no condition = always");
        assertThrows(net.sf.saxon.s9api.SaxonApiException.class, () -> eval.test("$nope", snapshot()));
        // The compiled expression is cached, the values are not: a second snapshot answers anew.
        PausedSnapshot other = new PausedSnapshot("file:/tmp/a.xsl", 5, List.of(), List.of(), "", null,
                Map.of(new QName("count"), new XdmAtomicValue(9)), Map.of(), 1, 1);
        assertTrue(eval.test("$count > 5", other));
    }

    @Test
    void logTemplatesExpandXPathPartsAndKeepLiteralBraces() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        assertEquals("3 items of Order #7 {x}", eval.formatLog("{$count} items of {$title} {{x}}", snapshot()));
        assertEquals("n=2", eval.formatLog("n={item[2]/@n/string()}", snapshot()));
        String failed = eval.formatLog("a {$nope} b", snapshot());
        assertTrue(failed.startsWith("a {ERROR: ") && failed.endsWith("} b"), "the rest still logs: " + failed);
        assertEquals("open { end", eval.formatLog("open { end", snapshot()), "an unclosed brace stays text");
    }

    @Test
    void conditionsSeeThePausedFocus() throws Exception {
        WatchEvaluator eval = new WatchEvaluator(PROCESSOR, Map.of());
        PausedSnapshot second = snapshot(); // position 2 of 4
        PausedSnapshot base = snapshot();
        PausedSnapshot fourth = new PausedSnapshot(base.systemId(), base.lineNumber(), List.of(), List.of(), "",
                base.contextXdm(), base.localVariables(), base.globalVariables(), 4, 4);
        assertFalse(eval.test("position() = last()", second));
        assertTrue(eval.test("position() = last()", fourth));
        assertTrue(eval.test("position() mod 2 = 0 and $count = 3", second));
        assertEquals("item 2 of 4", eval.evaluate("concat('item ', position(), ' of ', last())", second));
        assertEquals("3", eval.evaluate("string(item[position() = last()]/@n)", second),
                "a predicate keeps its own focus");
    }

    @Test
    void bindFocusOnlyRewritesCallsOnThePausedFocus() {
        String pos = "$Q{http://freexmltoolkit.org/ns/debug-focus}position";
        String last = "$Q{http://freexmltoolkit.org/ns/debug-focus}last";
        assertEquals(pos + " = " + last, WatchEvaluator.bindFocus("position() = last()", true));
        assertEquals(pos + " = last()", WatchEvaluator.bindFocus("position() = last()", false));
        assertEquals("item[position() = 1]", WatchEvaluator.bindFocus("item[position() = 1]", true));
        assertEquals("item/position()", WatchEvaluator.bindFocus("item/position()", true));
        assertEquals("item ! last()", WatchEvaluator.bindFocus("item ! last()", true));
        assertEquals("'position()' || \"last()\"", WatchEvaluator.bindFocus("'position()' || \"last()\"", true));
        assertEquals("(: position() :) " + pos, WatchEvaluator.bindFocus("(: position() :) position ( )", true));
        assertEquals("$position + my-last()", WatchEvaluator.bindFocus("$position + my-last()", true));
        assertEquals(pos + " != " + last, WatchEvaluator.bindFocus("position() != last()", true));
    }
}
