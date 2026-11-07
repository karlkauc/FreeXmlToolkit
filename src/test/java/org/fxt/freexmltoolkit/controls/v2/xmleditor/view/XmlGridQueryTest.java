package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.Test;

/**
 * {@link XmlGridQuery} evaluates against the model without copying it: namespaces declared on
 * ancestors stay in scope, selected nodes map back to the model, and the model is unchanged.
 */
class XmlGridQueryTest {

    @Test
    void namespacesOfAncestorsAreInScopeAndNodesMapBackToTheModel() {
        XmlElement root = new XmlElement("root", "p", "urn:p");
        root.setAttribute("xmlns:p", "urn:p");
        XmlElement table = new XmlElement("table", "p", "urn:p");
        root.addChild(table);
        XmlElement second = null;
        for (String amount : new String[] {"10", "32"}) {
            XmlElement row = new XmlElement("row", "p", "urn:p");
            row.setAttribute("amount", amount);
            row.addChild(new XmlText("x"));
            table.addChild(row);
            second = row;
        }
        String before = table.serialize(0);

        XmlGridQuery.Result sum = XmlGridQuery.evaluate(table, "sum(p:row/@amount)");
        XmlGridQuery.Result selected = XmlGridQuery.evaluate(table, "p:row[@amount = '32']/@amount");

        assertEquals("42", sum.text());
        assertEquals(1, selected.nodes().size());
        assertSame(second, selected.nodes().iterator().next());
        assertEquals(before, table.serialize(0), "the model is not modified");
        assertTrue(!table.hasAttribute("xmlns:p"), "no declaration added to the model");
    }

    @Test
    void declarationsAreInsertedOnlyWhereMissing() {
        XmlElement el = new XmlElement("a");
        el.setAttribute("xmlns:q", "urn:q");
        String xml = XmlGridQuery.withDeclarations("<a xmlns:q=\"urn:q\"/>", "<a", el,
                java.util.Map.of("q", "urn:q", "", "urn:\"d\""));

        assertEquals("<a xmlns=\"urn:&quot;d&quot;\" xmlns:q=\"urn:q\"/>", xml);
    }
}
