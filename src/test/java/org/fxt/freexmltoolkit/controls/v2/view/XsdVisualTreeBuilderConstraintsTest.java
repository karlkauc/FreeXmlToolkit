package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/** Cards list the identity constraints of their declaration and the assertions of their type. */
@ExtendWith(ApplicationExtension.class)
class XsdVisualTreeBuilderConstraintsTest {

    private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    @TempDir
    Path dir;

    private static String schema(String body) {
        return "<xs:schema " + XS + ">" + body + "</xs:schema>";
    }

    private static VisualNode build(String xsd) throws Exception {
        return new XsdVisualTreeBuilder().buildFromSchema(new XsdNodeFactory().fromString(xsd));
    }

    private static VisualNode card(VisualNode root, String label) {
        Deque<VisualNode> open = new ArrayDeque<>(List.of(root));
        while (!open.isEmpty()) {
            VisualNode node = open.pop();
            if (label.equals(node.getLabel())) {
                return node;
            }
            node.setExpanded(true);
            open.addAll(node.getChildren());
        }
        throw new AssertionError("no card " + label);
    }

    private static List<String> rows(VisualNode node) {
        return node.getConstraints().stream().map(row -> row.kind() + " " + row.text()).toList();
    }

    @Test
    void anElementListsItsIdentityConstraints() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="library"><xs:complexType/>
                  <xs:key name="k"><xs:selector xpath="book"/><xs:field xpath="@id"/></xs:key>
                  <xs:unique name="u"><xs:selector xpath="m"/><xs:field xpath="@id"/></xs:unique>
                  <xs:keyref name="r" refer="k"><xs:selector xpath="loan"/><xs:field xpath="@b"/></xs:keyref>
                </xs:element>"""));

        assertEquals(List.of("KEY k  @id", "UNIQUE u  @id", "KEYREF r → k"), rows(root));
    }

    @Test
    void aReferenceShowsTheConstraintsOfTheGlobalElement() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:element ref="target"/>
                </xs:sequence></xs:complexType></xs:element>
                <xs:element name="other" type="xs:string"/>
                <xs:element name="target"><xs:complexType/>
                  <xs:key name="k"><xs:selector xpath="a"/><xs:field xpath="@id"/></xs:key>
                </xs:element>"""));

        VisualNode reference = card(card(root, "root"), "target");
        assertEquals(List.of("KEY k  @id"), rows(reference));
        assertTrue(reference.getConstraints().getFirst().tooltip().endsWith("from element target"));
    }

    @Test
    void assertsOfAnInlineTypeAreListed() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root"><xs:complexType>
                  <xs:sequence/><xs:assert test="count(*) gt 0"/>
                </xs:complexType></xs:element>"""));

        assertEquals(List.of("ASSERT count(*) gt 0"), rows(root));
    }

    @Test
    void assertsAreInheritedAlongTheBaseChain() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root" type="Leaf"/>
                <xs:complexType name="Base"><xs:assert test="base"/></xs:complexType>
                <xs:complexType name="Middle"><xs:complexContent><xs:extension base="Base">
                  <xs:assert test="middle"/>
                </xs:extension></xs:complexContent></xs:complexType>
                <xs:complexType name="Leaf"><xs:complexContent><xs:restriction base="Middle">
                  <xs:assert test="leaf"/>
                </xs:restriction></xs:complexContent></xs:complexType>"""));

        assertEquals(List.of("ASSERT leaf", "ASSERT middle", "ASSERT base"), rows(root));
        assertTrue(root.getConstraints().get(2).tooltip().endsWith("from type Base"), "the type declaring it");
    }

    @Test
    void aCircularBaseChainEnds() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root" type="A"/>
                <xs:complexType name="A"><xs:complexContent><xs:extension base="B">
                  <xs:assert test="a"/>
                </xs:extension></xs:complexContent></xs:complexType>
                <xs:complexType name="B"><xs:complexContent><xs:extension base="A">
                  <xs:assert test="b"/>
                </xs:extension></xs:complexContent></xs:complexType>"""));

        assertEquals(List.of("ASSERT a", "ASSERT b"), rows(root));
    }

    @Test
    void simpleTypeAssertionsReachElementsAndAttributes() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root"><xs:complexType>
                  <xs:sequence><xs:element name="even" type="Even"/></xs:sequence>
                  <xs:attribute name="code" type="Narrow"/>
                </xs:complexType></xs:element>
                <xs:simpleType name="Even"><xs:restriction base="xs:integer">
                  <xs:assertion test="$value mod 2 = 0"/>
                </xs:restriction></xs:simpleType>
                <xs:simpleType name="Narrow"><xs:restriction base="Even">
                  <xs:assertion test="$value lt 10"/>
                </xs:restriction></xs:simpleType>"""));

        assertEquals(List.of("ASSERT $value mod 2 = 0"), rows(card(root, "even")));
        assertEquals(List.of("ASSERT $value lt 10", "ASSERT $value mod 2 = 0"), rows(card(root, "code")));
    }

    @Test
    void anInlineSimpleTypeAssertionIsListedOnce() throws Exception {
        VisualNode root = build(schema("""
                <xs:element name="root"><xs:simpleType><xs:restriction base="xs:integer">
                  <xs:assertion test="$value gt 0"/>
                </xs:restriction></xs:simpleType></xs:element>"""));

        assertEquals(List.of("ASSERT $value gt 0"), rows(root));
    }

    @Test
    void anAssertionAddedToTheModelReachesTheCard() throws Exception {
        VisualNode root = build(schema("<xs:element name=\"root\" type=\"xs:string\"/>"));
        assertTrue(root.getConstraints().isEmpty());

        // "assertions" changes no children, so no rebuild refreshes the card
        ((XsdElement) root.getModelNode()).addAssertion("string-length(.) gt 0");
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(List.of("ASSERT string-length(.) gt 0"), rows(root));
    }

    @Test
    void redefinedTypesShowTheirOwnAndTheOriginalAssertions() throws Exception {
        Files.writeString(dir.resolve("base.xsd"), schema("""
                <xs:element name="root" type="T"/>
                <xs:complexType name="T"><xs:assert test="original"/></xs:complexType>"""));
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, schema("""
                <xs:redefine schemaLocation="base.xsd">
                  <xs:complexType name="T"><xs:complexContent><xs:extension base="T">
                    <xs:assert test="redefined"/>
                  </xs:extension></xs:complexContent></xs:complexType>
                </xs:redefine>"""));
        XsdSchema schema = new XsdNodeFactory().fromFile(main);

        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, schema.getImportedSchemas());

        assertEquals(List.of("ASSERT redefined", "ASSERT original"), rows(card(root, "root")));
    }

    @Test
    void assertionFacetsSurviveARoundTrip() throws Exception {
        String xsd = schema("""
                <xs:simpleType name="Even"><xs:restriction base="xs:integer">
                  <xs:assertion test="$value &lt; 10">
                    <xs:annotation><xs:documentation>Small</xs:documentation></xs:annotation>
                  </xs:assertion>
                </xs:restriction></xs:simpleType>""");

        XsdSchema schema = new XsdNodeFactory().fromString(xsd);
        String saved = new XsdSerializer().serialize(schema);
        XsdSchema reloaded = new XsdNodeFactory().fromString(saved);

        assertTrue(saved.contains("<xs:assertion test=\"$value &lt; 10\">"), saved);
        assertTrue(saved.contains("Small"), saved);
        XsdSimpleType type = (XsdSimpleType) reloaded.getChildren().stream()
                .filter(XsdSimpleType.class::isInstance).findFirst().orElseThrow();
        XsdRestriction restriction = (XsdRestriction) type.getChildren().getFirst();
        assertEquals(XsdFacetType.ASSERTION, restriction.getFacets().getFirst().getFacetType());
        assertEquals("$value < 10", restriction.getFacets().getFirst().getValue());
    }
}
