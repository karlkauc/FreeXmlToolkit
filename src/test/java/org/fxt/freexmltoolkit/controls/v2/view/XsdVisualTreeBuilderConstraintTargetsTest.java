package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Field targets list the identity constraints of their ancestors while the option is on. */
class XsdVisualTreeBuilderConstraintTargetsTest {

    /** FundsXML-like: a key on Fund whose field is BenchmarkID; a type shared by two places. */
    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Funds">
                <xs:complexType><xs:sequence>
                  <xs:element name="Fund" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Benchmarks">
                        <xs:complexType><xs:sequence>
                          <xs:element name="Benchmark" type="BenchmarkType" maxOccurs="unbounded"/>
                        </xs:sequence></xs:complexType>
                      </xs:element>
                      <xs:element name="Other" type="BenchmarkType" minOccurs="0"/>
                    </xs:sequence></xs:complexType>
                    <xs:key name="BenchmarkKey">
                      <xs:selector xpath="Benchmarks/Benchmark"/>
                      <xs:field xpath="BenchmarkID"/>
                    </xs:key>
                    <xs:unique name="CodeOnce">
                      <xs:selector xpath=".//Benchmark"/>
                      <xs:field xpath="@code"/>
                    </xs:unique>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="BenchmarkType">
                <xs:sequence><xs:element name="BenchmarkID" type="xs:string"/></xs:sequence>
                <xs:attribute name="code" type="xs:string"/>
              </xs:complexType>
            </xs:schema>
            """;

    @AfterEach
    void restore() {
        XsdDiagramOptions.showConstraintTargetsProperty().set(false);
    }

    private static List<VisualNode> cards() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(SCHEMA);
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(5000).buildFromSchema(schema, null, null);
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

    private static VisualNode card(List<VisualNode> cards, String label, String parentLabel) {
        return cards.stream()
                .filter(n -> label.equals(n.getLabel()))
                .filter(n -> parentLabel == null || hasAncestor(n, parentLabel))
                .findFirst().orElseThrow(() -> new AssertionError(label + " under " + parentLabel));
    }

    private static boolean hasAncestor(VisualNode node, String label) {
        for (VisualNode p = node.getParent(); p != null; p = p.getParent()) {
            if (label.equals(p.getLabel())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void offByDefaultTheTargetListsNothing() throws Exception {
        XsdDiagramOptions.showConstraintTargetsProperty().set(false);
        assertTrue(card(cards(), "BenchmarkID", "Benchmark").getConstraints().isEmpty());
    }

    @Test
    void theFieldTargetListsTheConstraintOfItsAncestor() throws Exception {
        XsdDiagramOptions.showConstraintTargetsProperty().set(true);
        List<VisualNode> cards = cards();

        List<ConstraintRow> rows = card(cards, "BenchmarkID", "Benchmark").getConstraints();
        assertEquals(1, rows.size(), rows.toString());
        ConstraintRow row = rows.getFirst();
        assertTrue(row.incoming());
        assertEquals(ConstraintRow.Kind.KEY, row.kind());
        assertEquals("← BenchmarkKey  @ Fund", row.text());
        assertTrue(row.tooltip().startsWith("key BenchmarkKey (defined on Fund)"), row.tooltip());

        // the declaring card keeps its own rows, the selected element gets none
        assertTrue(card(cards, "Fund", null).getConstraints().stream().noneMatch(ConstraintRow::incoming));
        assertTrue(card(cards, "Benchmark", null).getConstraints().isEmpty());
    }

    @Test
    void aSharedTypeOnlyMatchesWhereThePathFits() throws Exception {
        XsdDiagramOptions.showConstraintTargetsProperty().set(true);
        List<VisualNode> cards = cards();
        assertTrue(card(cards, "BenchmarkID", "Other").getConstraints().isEmpty(),
                "Other/BenchmarkID is not selected by Benchmarks/Benchmark");
    }

    @Test
    void attributeFieldsAndDescendantSelectors() throws Exception {
        XsdDiagramOptions.showConstraintTargetsProperty().set(true);
        List<ConstraintRow> rows = card(cards(), "code", "Benchmark").getConstraints();
        assertEquals(List.of("← CodeOnce  @ Fund"), rows.stream().map(ConstraintRow::text).toList());
    }
}
