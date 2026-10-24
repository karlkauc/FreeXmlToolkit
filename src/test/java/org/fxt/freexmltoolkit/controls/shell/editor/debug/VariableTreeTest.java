package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmArray;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.junit.jupiter.api.Test;

/** How variable values unfold, and that every row's XPath really reaches that row's value. */
class VariableTreeTest {

    private static final Processor PROCESSOR = new Processor(false);

    private static XdmNode order() throws Exception {
        XdmNode doc = PROCESSOR.newDocumentBuilder().build(new StreamSource(new StringReader(
                "<order id='7' xmlns:x='urn:x'>\n  <item n='1'>a</item>\n  <item n='2'>b</item>\n  <x:note>hi</x:note>\n  <!-- c --></order>")));
        return (XdmNode) PROCESSOR.newXPathCompiler().evaluateSingle("/order", doc);
    }

    private static VariableTree.Node root(String name, XdmValue v) {
        return VariableTree.Node.of(new VariableBinding(name, "", "", VariableScope.LOCAL), v);
    }

    /** Evaluates a row's XPath with the variable bound, to prove the path is right. */
    private static XdmValue eval(String expr, String var, XdmValue value) throws Exception {
        PausedSnapshot snap = new PausedSnapshot("", 1, List.of(), List.of(), "", null,
                Map.of(new QName(var), value), Map.of(), -1, -1);
        return new WatchEvaluator(PROCESSOR, Map.of()).evaluateValue(expr, snap, null);
    }

    @Test
    void anElementUnfoldsIntoAttributesAndChildrenWithWorkingPaths() throws Exception {
        XdmNode order = order();
        VariableTree.Node root = root("$o", order);
        assertTrue(root.hasChildren());
        List<VariableTree.Node> rows = VariableTree.children(root);
        assertEquals(List.of("@id", "<item>", "<item>", "<x:note>", "comment()"),
                rows.stream().map(VariableTree.Node::name).toList(), "indentation text is skipped");
        assertEquals("$o/@id", rows.get(0).expr());
        assertEquals("$o/item[2]", rows.get(2).expr(), "repeated siblings get a position");
        assertEquals("$o/Q{urn:x}note", rows.get(3).expr(), "namespaced names need no prefix");
        for (VariableTree.Node row : rows) {
            XdmValue reached = eval(row.expr(), "o", order);
            assertEquals(1, reached.size(), row.expr());
            assertEquals(((XdmNode) row.xdm()).getUnderlyingNode(), ((XdmNode) reached.itemAt(0)).getUnderlyingNode(),
                    "the path reaches the row's own node: " + row.expr());
        }
        List<VariableTree.Node> item = VariableTree.children(rows.get(1));
        assertEquals("$o/item[1]/@n", item.get(0).expr());
        assertEquals("$o/item[1]/text()", item.get(1).expr());
    }

    @Test
    void sequencesMapsAndArraysUnfold() throws Exception {
        XdmValue seq = new XdmValue(List.of(new XdmAtomicValue(5), new XdmAtomicValue("x")));
        List<VariableTree.Node> items = VariableTree.children(root("$s", seq));
        assertEquals(List.of("[1]", "[2]"), items.stream().map(VariableTree.Node::name).toList());
        assertEquals("x", eval(items.get(1).expr(), "s", seq).toString().replace("\"", ""));

        XdmMap map = XdmMap.makeMap(Map.of("total", 3));
        List<VariableTree.Node> entries = VariableTree.children(root("$m", map));
        assertEquals("$m?('total')", entries.get(0).expr());
        assertEquals("3", eval(entries.get(0).expr(), "m", map).toString());

        XdmArray array = new XdmArray(new XdmValue[]{new XdmAtomicValue("a"), new XdmAtomicValue("b")});
        List<VariableTree.Node> members = VariableTree.children(root("$a", array));
        assertEquals("$a?2", members.get(1).expr());
        assertEquals("b", eval(members.get(1).expr(), "a", array).toString().replace("\"", ""));

        assertFalse(root("$n", new XdmAtomicValue(1)).hasChildren(), "an atomic value is a leaf");
        assertFalse(root("$n", null).hasChildren(), "no value known: a leaf");
    }

    @Test
    void longSequencesAreCapped() {
        XdmAtomicValue[] many = new XdmAtomicValue[VariableTree.MAX_CHILDREN + 7];
        for (int i = 0; i < many.length; i++) {
            many[i] = new XdmAtomicValue(i);
        }
        List<VariableTree.Node> rows = VariableTree.children(root("$big", new XdmValue(List.of(many))));
        assertEquals(VariableTree.MAX_CHILDREN + 1, rows.size());
        assertEquals("7 more", rows.get(rows.size() - 1).value());
        assertNull(VariableTree.Node.of(new VariableBinding("$$slot1", "", "", VariableScope.LOCAL), null).expr(),
                "an unnamed slot has no XPath");
    }
}
