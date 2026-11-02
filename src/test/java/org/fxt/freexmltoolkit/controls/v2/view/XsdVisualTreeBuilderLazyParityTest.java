package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;

/** A card shows the same content whether it was built eagerly or by its lazy loader. */
class XsdVisualTreeBuilderLazyParityTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="withAll" type="AllExtension"/>
                  <xs:element name="restricted" type="Restricted"/>
                  <xs:element name="unresolved" type="Holder"/>
                  <xs:element name="simpleText" type="AmountType"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="Base">
                <xs:sequence><xs:element name="inherited" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="EmptyBase"/>
              <xs:complexType name="AllExtension">
                <xs:complexContent><xs:extension base="EmptyBase">
                  <xs:all><xs:element name="inAll" type="xs:string"/></xs:all>
                  <xs:attribute name="fromExtension" type="xs:string"/>
                  <xs:assert test="count(*) gt 0"/>
                </xs:extension></xs:complexContent>
              </xs:complexType>
              <xs:complexType name="Restricted">
                <xs:complexContent><xs:restriction base="Base">
                  <xs:sequence><xs:element name="inherited" type="xs:string"/></xs:sequence>
                  <xs:attribute name="fromRestriction" type="xs:string"/>
                </xs:restriction></xs:complexContent>
              </xs:complexType>
              <xs:complexType name="Holder">
                <!-- a note -->
                <xs:sequence><xs:element ref="other:missing" xmlns:other="urn:other"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="AmountType">
                <xs:simpleContent><xs:extension base="xs:decimal">
                  <xs:attribute name="currency" type="xs:string"/>
                </xs:extension></xs:simpleContent>
              </xs:complexType>
            </xs:schema>
            """;

    /** Label tree of a card, opening every deferred card on the way. */
    private static List<String> outline(VisualNode node) {
        List<String> lines = new ArrayList<>();
        outline(node, 0, lines);
        return lines;
    }

    private static void outline(VisualNode node, int depth, List<String> lines) {
        node.setExpanded(true);
        lines.add("  ".repeat(depth) + node.getConstraints() + " " + node.getType() + " " + node.getLabel());
        for (VisualNode child : node.getChildren()) {
            outline(child, depth + 1, lines);
        }
    }

    @Test
    void deferredCardsMatchEagerCards() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(SCHEMA);

        List<String> eager = outline(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null));
        List<String> deferred = outline(new XsdVisualTreeBuilder().withNodeBudget(1)
                .buildFromSchema(schema, null, null));

        assertEquals(eager, deferred);
    }

    @Test
    void outlineCoversTheContentTheLazyPathUsedToDrop() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(SCHEMA);

        String deferred = String.join("\n", outline(new XsdVisualTreeBuilder().withNodeBudget(1)
                .buildFromSchema(schema, null, null)));

        assertAll(
                () -> assertTrue(deferred.contains("inAll"), "xs:all in an extension\n" + deferred),
                () -> assertTrue(deferred.contains("fromRestriction"), "restriction content\n" + deferred),
                () -> assertTrue(deferred.contains("External: other:missing"), "unresolved ref\n" + deferred),
                () -> assertTrue(deferred.contains("COMMENT"), "comment\n" + deferred),
                () -> assertTrue(deferred.contains("currency"), "simple content\n" + deferred));
    }

    @Test
    void restrictionReplacesTheBaseContentModel() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(SCHEMA);

        List<String> outline = outline(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null));

        assertEquals(1, outline.stream().filter(line -> line.endsWith(" inherited")).count(), outline.toString());
    }
}
