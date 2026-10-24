package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;

import org.junit.jupiter.api.Test;

/** The input line of a context node, as the debugger marks it in the input document. */
class InputLocationTest {

    private static XdmNode parse(String xml, String systemId) throws Exception {
        Processor p = new Processor(false);
        p.getUnderlyingConfiguration().setLineNumbering(true);
        StreamSource source = new StreamSource(new StringReader(xml));
        source.setSystemId(systemId);
        return p.newDocumentBuilder().build(source);
    }

    private static final String XML = "<?xml version=\"1.0\"?>\n<order id=\"7\">\n  <item n=\"1\">a</item>\n  <item n=\"2\"/>\n</order>\n";

    @Test
    void elementsAttributesTextAndTheDocumentHaveLines() throws Exception {
        XdmNode doc = parse(XML, "file:/tmp/in.xml");
        var xp = new Processor(false).newXPathCompiler();
        XdmNode item2 = (XdmNode) doc.select(net.sf.saxon.s9api.streams.Steps.descendant("item")).toList().get(1);
        assertEquals(new InputLocation("file:/tmp/in.xml", 4), InputLocation.of(item2).orElseThrow());
        XdmNode attr = (XdmNode) item2.select(net.sf.saxon.s9api.streams.Steps.attribute("n")).findFirst().orElseThrow();
        assertEquals(4, InputLocation.of(attr).orElseThrow().line(), "an attribute reports its element's line");
        XdmNode text = (XdmNode) doc.select(net.sf.saxon.s9api.streams.Steps.descendant("item")).findFirst()
                .orElseThrow().select(net.sf.saxon.s9api.streams.Steps.child()).findFirst().orElseThrow();
        assertEquals(3, InputLocation.of(text).orElseThrow().line(), "text reports its line");
        assertEquals(2, InputLocation.of(doc).orElseThrow().line(), "the document node points at its root element");
        assertNotNull(xp);
    }

    @Test
    void inMemoryInputHasNoSystemIdAndAtomicsHaveNoLocation() throws Exception {
        XdmNode doc = parse(XML, null);
        assertEquals("", InputLocation.of(doc).orElseThrow().systemId());
        assertTrue(InputLocation.of(new XdmAtomicValue(3)).isEmpty());
        assertTrue(InputLocation.of(null).isEmpty());
    }
}
