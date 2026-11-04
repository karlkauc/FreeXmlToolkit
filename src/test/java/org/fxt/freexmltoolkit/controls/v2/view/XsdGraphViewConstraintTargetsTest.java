package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Switching "constraints on target nodes" changes an open diagram at once, without a rebuild. */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewConstraintTargetsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="library">
                <xs:complexType><xs:sequence>
                  <xs:element name="book" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="isbn" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
                <xs:key name="bookKey"><xs:selector xpath="book"/><xs:field xpath="isbn"/></xs:key>
              </xs:element>
            </xs:schema>
            """;

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        XsdDiagramOptions.showConstraintsProperty().set(true);
        XsdDiagramOptions.showConstraintTargetsProperty().set(false);
        graph = new XsdGraphView(new XsdEditorContext(new XsdNodeFactory().fromString(XSD)));
        stage.setScene(new Scene(graph, 1000, 700));
        stage.show();
    }

    @AfterEach
    void restore() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> XsdDiagramOptions.showConstraintTargetsProperty().set(false));
    }

    @Test
    void togglingAddsAndRemovesTheRowOnTheOpenDiagram() {
        VisualNode root = graph.getRootNode();
        VisualNode isbn = find(root, "isbn");
        assertTrue(isbn.getConstraints().isEmpty());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> XsdDiagramOptions.showConstraintTargetsProperty().set(true));
        assertEquals(List.of("← bookKey  @ library"), isbn.getConstraints().stream().map(ConstraintRow::text).toList());
        assertEquals(root, graph.getRootNode(), "the tree is not rebuilt");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> XsdDiagramOptions.showConstraintTargetsProperty().set(false));
        assertTrue(isbn.getConstraints().isEmpty());
    }

    /** The FundsXML4 example: BenchmarkID under FundStaticData lists the key on Fund. */
    @Test
    void fundsXml4BenchmarkIdShowsTheKeyOfFund() throws Exception {
        Path xsd = Path.of("release/examples/xsd/FundsXML4.xsd");
        XsdSchema schema = new XsdNodeFactory().fromString(Files.readString(xsd), xsd.getParent());
        XsdDiagramOptions.showConstraintTargetsProperty().set(true);
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(1_000_000).buildFromSchema(schema, null, null);

        VisualNode staticId = findUnder(root, "BenchmarkID", "FundStaticData");
        List<String> rows = staticId.getConstraints().stream().filter(ConstraintRow::incoming)
                .map(ConstraintRow::text).toList();
        assertTrue(rows.contains("← benchmarkID  @ Fund"), rows.toString());

        VisualNode dynamicId = findUnder(root, "BenchmarkID", "FundDynamicData");
        List<String> dynamicRows = dynamicId.getConstraints().stream().filter(ConstraintRow::incoming)
                .map(ConstraintRow::text).toList();
        assertTrue(dynamicRows.contains("← benchmarkDynamicRef → benchmarkID  @ Fund"), dynamicRows.toString());
    }

    private static VisualNode find(VisualNode root, String label) {
        return findUnder(root, label, null);
    }

    private static VisualNode findUnder(VisualNode root, String label, String ancestor) {
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            if (label.equals(node.getLabel()) && (ancestor == null || hasAncestor(node, ancestor))) {
                return node;
            }
            if (node.hasChildren() && node.getChildren().isEmpty()) {
                node.setExpanded(true); // loads lazy children
            }
            node.getChildren().forEach(pending::push);
        }
        throw new AssertionError(label + " under " + ancestor);
    }

    private static boolean hasAncestor(VisualNode node, String label) {
        for (VisualNode p = node.getParent(); p != null; p = p.getParent()) {
            if (label.equals(p.getLabel())) {
                return true;
            }
        }
        return false;
    }
}
