package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;

/**
 * Cards of the graphic view must open by the same path the expand button uses, including cards
 * past the node budget, which is what a schema gets large enough to reach through its includes.
 */
class XsdVisualTreeBuilderIncludeExpandTest {

    private static final Path FUNDS_XML =
            Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");

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

    private static VisualNode card(VisualNode root, String label) {
        return loaded(root).stream()
                .filter(n -> label.equals(n.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no card " + label));
    }

    /** {@code levels} types, each a sequence of four elements of the next type. */
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

    @Test
    void toggleExpandedLoadsDeferredCard() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(nestedReuseSchema(6));
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(50).buildFromSchema(schema, null, null);

        VisualNode deferred = loaded(root).stream()
                .filter(VisualNode::hasUnloadedChildren).findFirst().orElseThrow();

        deferred.toggleExpanded();
        assertTrue(deferred.isExpanded());
        assertFalse(deferred.getChildren().isEmpty(), "the expand button loads a deferred card");

        deferred.toggleExpanded();
        assertFalse(deferred.isExpanded(), "a second click closes the card again");
    }

    @Test
    void extensionBaseContentIsShownInEagerBuild() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="Derived"/>
                  <xs:complexType name="Base">
                    <xs:sequence><xs:element name="inherited" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="Derived">
                    <xs:complexContent><xs:extension base="Base"/></xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """);

        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, null);

        assertNotNull(card(root, "inherited"), "the base type's content is part of the card");
    }

    @Test
    void circularBaseTypeTerminates() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="A"/>
                  <xs:complexType name="A">
                    <xs:complexContent><xs:extension base="B"/></xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="B">
                    <xs:complexContent><xs:extension base="A"/></xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                () -> new XsdVisualTreeBuilder().buildFromSchema(schema, null, null));
    }

    @Test
    void includedFundsXmlSectionsCanBeExpanded() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(
                Files.readString(FUNDS_XML), FUNDS_XML, FUNDS_XML.toAbsolutePath().getParent());
        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, null);

        for (String section : List.of("AssetMasterData", "Documents")) {
            VisualNode card = card(root, section);
            if (!card.isExpanded()) {
                card.toggleExpanded();
            }
            assertFalse(card.getChildren().isEmpty(), section + " opens");
        }
    }
}
