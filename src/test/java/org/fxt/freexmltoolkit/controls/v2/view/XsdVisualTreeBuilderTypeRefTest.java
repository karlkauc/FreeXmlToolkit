package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Only what really has content shows an expand button, eager or deferred. */
class XsdVisualTreeBuilderTypeRefTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                       xmlns:s="http://www.w3.org/2001/XMLSchema"
                       xmlns:my="urn:my" targetNamespace="urn:my">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="plainXs" type="xs:string"/>
                  <xs:element name="plainXsd" type="xsd:string"/>
                  <xs:element name="customPrefix" type="s:string"/>
                  <xs:element name="simple" type="my:Code"/>
                  <xs:element name="ownString" type="my:string"/>
                  <xs:element name="missing" type="my:Nowhere"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
              <xs:complexType name="string">
                <xs:attribute name="own" type="xs:string"/>
              </xs:complexType>
            </xs:schema>
            """;

    private static VisualNode card(int budget, String label) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(SCHEMA);
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(budget).buildFromSchema(schema, null, null);
        return all(root).stream().filter(n -> label.equals(n.getLabel())).findFirst().orElseThrow();
    }

    private static List<VisualNode> all(VisualNode root) {
        List<VisualNode> cards = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            cards.add(node);
            node.getChildren().forEach(pending::push);
        }
        return cards;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void typesWithoutContentGetNoExpandButton(int budget) throws Exception {
        for (String label : List.of("plainXs", "plainXsd", "customPrefix", "simple")) {
            assertFalse(card(budget, label).hasChildren(), label);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void userTypeNamedLikeABuiltinOneUnfolds(int budget) throws Exception {
        VisualNode card = card(budget, "ownString");

        card.toggleExpanded();

        assertEquals("own", card.getChildren().getFirst().getLabel());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void missingTypeIsNamedByAPlaceholder(int budget) throws Exception {
        VisualNode card = card(budget, "missing");

        card.toggleExpanded();

        assertEquals("Unresolved type: my:Nowhere", card.getChildren().getFirst().getLabel());
    }
}
