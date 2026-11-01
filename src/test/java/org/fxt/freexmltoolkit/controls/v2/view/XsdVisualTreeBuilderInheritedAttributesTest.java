package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.menu.XsdContextMenuFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/** Attributes of base types and wildcards belong to what a card shows. */
@ExtendWith(ApplicationExtension.class)
class XsdVisualTreeBuilderInheritedAttributesTest {

    private static String schema(String body) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="T"/>
                  <xs:complexType name="Base">
                    <xs:sequence><xs:element name="particle" type="xs:string"/></xs:sequence>
                    <xs:attribute name="kept" type="xs:string"/>
                    <xs:attribute name="changed" type="xs:string"/>
                    <xs:attribute name="dropped" type="xs:string"/>
                  </xs:complexType>
                """ + body + "</xs:schema>";
    }

    private static List<VisualNode> cards(String xsd, int budget) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(budget).buildFromSchema(schema, null, null);
        List<VisualNode> cards = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            node.setExpanded(true);
            cards.add(node);
            node.getChildren().forEach(pending::push);
        }
        return cards;
    }

    private static List<String> labels(String xsd, int budget) throws Exception {
        return cards(xsd, budget).stream().map(VisualNode::getLabel).toList();
    }

    private static final String RESTRICTION = schema("""
            <xs:complexType name="T"><xs:complexContent><xs:restriction base="Base">
              <xs:sequence><xs:element name="narrowed" type="xs:string"/></xs:sequence>
              <xs:attribute name="changed" type="xs:token"/>
              <xs:attribute name="dropped" use="prohibited"/>
            </xs:restriction></xs:complexContent></xs:complexType>
            """);

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void restrictionKeepsTheBaseAttributes(int budget) throws Exception {
        List<String> labels = labels(RESTRICTION, budget);

        assertTrue(labels.containsAll(List.of("narrowed", "kept", "changed")), labels.toString());
        assertFalse(labels.contains("particle"), "the content model is restated: " + labels);
        assertEquals(1, labels.stream().filter("changed"::equals).count(), "redeclared once: " + labels);
    }

    @Test
    void prohibitedAttributeShowsOnlyItsOwnDeclaration() throws Exception {
        assertEquals(1, labels(RESTRICTION, 2000).stream().filter("dropped"::equals).count());
    }

    @Test
    void inheritanceRunsThroughAChain() throws Exception {
        List<String> labels = labels(schema("""
                <xs:complexType name="Middle"><xs:complexContent><xs:extension base="Base">
                  <xs:attribute name="middle" type="xs:string"/>
                </xs:extension></xs:complexContent></xs:complexType>
                <xs:complexType name="T"><xs:complexContent><xs:restriction base="Middle">
                  <xs:attribute name="own" type="xs:string"/>
                </xs:restriction></xs:complexContent></xs:complexType>
                """), 2000);

        assertTrue(labels.containsAll(List.of("kept", "middle", "own")), labels.toString());
    }

    @Test
    void simpleContentTakesTheBaseAttributes() throws Exception {
        List<String> labels = labels(schema("""
                <xs:complexType name="Amount"><xs:simpleContent><xs:extension base="xs:decimal">
                  <xs:attribute name="currency" type="xs:string"/>
                </xs:extension></xs:simpleContent></xs:complexType>
                <xs:complexType name="T"><xs:simpleContent><xs:extension base="Amount">
                  <xs:attribute name="rate" type="xs:string"/>
                </xs:extension></xs:simpleContent></xs:complexType>
                """), 2000);

        assertTrue(labels.containsAll(List.of("currency", "rate")), labels.toString());
    }

    @Test
    void unknownBaseIsNamed() throws Exception {
        assertTrue(labels(schema("""
                <xs:complexType name="T"><xs:complexContent><xs:restriction base="Gone"/></xs:complexContent></xs:complexType>
                """), 2000).contains("Unresolved type: Gone"));
    }

    @Test
    void circularBaseTerminates() throws Exception {
        String xsd = schema("""
                <xs:complexType name="T"><xs:simpleContent><xs:restriction base="U"/></xs:simpleContent></xs:complexType>
                <xs:complexType name="U"><xs:simpleContent><xs:restriction base="T"/></xs:simpleContent></xs:complexType>
                """);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> labels(xsd, 2000));
    }

    private static final String WILDCARDS = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:attributeGroup name="Open"><xs:anyAttribute namespace="##any"/></xs:attributeGroup>
              <xs:element name="root"><xs:complexType>
                <xs:sequence><xs:any namespace="##other" processContents="lax" maxOccurs="unbounded"/></xs:sequence>
                <xs:attributeGroup ref="Open"/>
                <xs:anyAttribute/>
              </xs:complexType></xs:element>
            </xs:schema>
            """;

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void wildcardsAreShown(int budget) throws Exception {
        List<VisualNode> cards = cards(WILDCARDS, budget);

        VisualNode any = cards.stream().filter(n -> "any".equals(n.getLabel())).findFirst().orElseThrow();
        assertEquals(-1, any.getMaxOccurs(), "unbounded");
        assertTrue(any.getDetail().contains("##other") && any.getDetail().contains("lax"), any.getDetail());
        assertEquals(2, cards.stream().filter(n -> "anyAttribute".equals(n.getLabel())).count());
    }

    @Test
    void wildcardMenuHasNothingToRename() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(WILDCARDS);
        VisualNode any = new XsdVisualTreeBuilder().buildFromSchema(schema, null, null)
                .getChildren().getFirst().getChildren().getFirst();
        XsdContextMenuFactory menus = new XsdContextMenuFactory(new XsdEditorContext(schema));

        assertEquals("any", any.getLabel());
        assertEquals(List.of("Delete", "Copy XPath"), WaitForAsyncUtils.asyncFx(() ->
                menus.createContextMenu(any).getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText).filter(java.util.Objects::nonNull).toList()).get());
    }
}
