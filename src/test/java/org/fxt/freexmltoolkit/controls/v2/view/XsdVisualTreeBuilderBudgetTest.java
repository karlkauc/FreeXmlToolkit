package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;

/**
 * A small schema that reuses its types in nested places unfolds into exponentially many cards; the
 * builder stops building eagerly at its node budget and leaves the rest to lazy loaders.
 */
class XsdVisualTreeBuilderBudgetTest {

    /** {@code levels} types, each a sequence of four elements of the next type: 4^levels leaf cards. */
    private static String nestedReuseSchema(int levels) {
        StringBuilder sb = new StringBuilder("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="T0"/>
                """);
        for (int i = 0; i < levels; i++) {
            sb.append("  <xs:complexType name=\"T").append(i).append("\"><xs:sequence>");
            for (int c = 0; c < 4; c++) {
                sb.append("<xs:element name=\"e").append(i).append('_').append(c)
                        .append("\" type=\"T").append(i + 1).append("\"/>");
            }
            sb.append("</xs:sequence></xs:complexType>\n");
        }
        sb.append("  <xs:complexType name=\"T").append(levels)
                .append("\"><xs:attribute name=\"a\" type=\"xs:string\"/></xs:complexType>\n");
        return sb.append("</xs:schema>").toString();
    }

    private static List<VisualNode> loaded(VisualNode root) {
        List<VisualNode> all = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            all.add(node);
            node.getChildren().forEach(pending::push);
        }
        return all;
    }

    @Test
    void nestedTypeReuseStopsAtTheBudget() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(nestedReuseSchema(9)); // 4^9 = 262,144 leaves

        long start = System.nanoTime();
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(500).buildFromSchema(schema, null, null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        List<VisualNode> cards = loaded(root);
        long elements = cards.stream().filter(n -> n.getType() == XsdNodeRenderer.NodeWrapperType.ELEMENT).count();
        assertTrue(elements <= 500 + 4 * 9, "eager element cards: " + elements);
        assertTrue(millis < 5_000, "build took " + millis + " ms");

        VisualNode deferred = cards.stream().filter(VisualNode::hasUnloadedChildren).findFirst().orElseThrow();
        deferred.setExpanded(true);
        assertFalse(deferred.getChildren().isEmpty(), "a deferred card loads its content when expanded");
    }

    @Test
    void schemaWithinTheBudgetIsBuiltCompletely() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(nestedReuseSchema(3)); // 84 element cards

        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, null);

        List<VisualNode> cards = loaded(root);
        assertTrue(cards.stream().noneMatch(VisualNode::hasUnloadedChildren));
        assertEquals(1 + 4 + 16 + 64, cards.stream()
                .filter(n -> n.getType() == XsdNodeRenderer.NodeWrapperType.ELEMENT).count());
    }
}
