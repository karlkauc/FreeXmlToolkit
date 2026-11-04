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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Content that comes from a named complex type is marked as that type's frame (XMLSpy style) and
 * is read-only in place; an extension's base content gets its own frame inside.
 */
class XsdVisualTreeBuilderTypeFrameTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="office">
                <xs:complexType><xs:sequence>
                  <xs:element name="Address_EU" type="EUAddress"/>
                  <xs:element name="Branch" type="Derived"/>
                  <xs:element name="inline">
                    <xs:complexType><xs:sequence>
                      <xs:element name="x" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                  <xs:element name="folder" type="FolderType"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="EUAddress">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                  <xs:element name="street" type="xs:string"/>
                </xs:sequence>
                <xs:attribute name="country" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="Derived">
                <xs:complexContent>
                  <xs:extension base="EUAddress">
                    <xs:sequence>
                      <xs:element name="extra" type="xs:string"/>
                    </xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:complexType name="FolderType">
                <xs:sequence>
                  <xs:element name="folder" type="FolderType" minOccurs="0"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    /** A type editor's diagram: its root is a stand-in element whose type is the edited type. */
    private static final String TYPE_EDITOR_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Derived" type="Derived"/>
              <xs:complexType name="EUAddress">
                <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="Derived">
                <xs:complexContent>
                  <xs:extension base="EUAddress">
                    <xs:sequence><xs:element name="extra" type="xs:string"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    private static VisualNode build(String xsd, int budget, boolean editableRootType) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(budget)
                .withEditableRootType(editableRootType).buildFromSchema(schema, null, null);
        loadAll(root);
        return root;
    }

    /** Loads every deferred card (the builder stops at circular types, so this ends). */
    private static void loadAll(VisualNode root) {
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            node.loadChildrenIfNeeded();
            node.getChildren().forEach(pending::push);
        }
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

    /** The first card with this label below {@code under}, in pre-order. */
    private static VisualNode card(VisualNode under, String label) {
        return all(under).stream().filter(n -> label.equals(n.getLabel())).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void namedTypeContentIsOneFrame(int budget) throws Exception {
        VisualNode address = card(build(SCHEMA, budget, false), "Address_EU");

        assertEquals(1, address.getTypeFrames().size());
        TypeFrame frame = address.getTypeFrames().getFirst();
        assertEquals("EUAddress", frame.typeName());
        assertEquals("EUAddress", frame.type().getName());
        assertEquals(0, frame.from());
        assertEquals(address.getChildren().size(), frame.to());
        assertEquals(0, frame.level());
        assertTrue(address.getChildren().size() >= 2, "sequence and attribute");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void nodesInsideAFrameAreLockedByTheirType(int budget) throws Exception {
        VisualNode root = build(SCHEMA, budget, false);
        VisualNode address = card(root, "Address_EU");

        assertNull(address.lockingFrame(), "the element itself is the office's, not the type's");
        assertEquals("EUAddress", card(address, "street").lockingFrame().typeName());
        assertEquals("EUAddress", card(address, "country").lockingFrame().typeName());
        assertTrue(card(address, "street").isLockedByType());
        assertNull(card(root, "x").lockingFrame(), "an anonymous inline type is edited in place");
        assertTrue(card(root, "inline").getTypeFrames().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void extensionBaseIsANestedFrame(int budget) throws Exception {
        VisualNode branch = card(build(SCHEMA, budget, false), "Branch");

        List<TypeFrame> frames = branch.getTypeFrames();
        assertEquals(2, frames.size());
        TypeFrame base = frames.get(0);
        TypeFrame derived = frames.get(1);
        assertEquals("EUAddress", base.typeName());
        assertEquals(0, base.level());
        assertEquals("Derived", derived.typeName());
        assertEquals(1, derived.level());
        assertTrue(derived.encloses(base));
        assertEquals(branch.getChildren().size(), derived.to());
        assertTrue(base.to() < derived.to(), "the extension's own content follows the base");

        assertEquals("EUAddress", card(branch, "street").lockingFrame().typeName(), "innermost type");
        assertEquals("Derived", card(branch, "extra").lockingFrame().typeName());
    }

    @Test
    void circularTypeIsFramedAtEveryLevelAndEnds() throws Exception {
        VisualNode folder = card(build(SCHEMA, 2000, false), "folder");

        assertEquals("FolderType", folder.getTypeFrames().getFirst().typeName());
        VisualNode inner = card(folder.getChildren().getFirst(), "folder");
        assertEquals("FolderType", inner.lockingFrame().typeName());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void typeEditorRootKeepsItsOwnTypeEditableButFramesTheBase(int budget) throws Exception {
        VisualNode root = build(TYPE_EDITOR_SCHEMA, budget, true);

        assertEquals(List.of("EUAddress"), root.getTypeFrames().stream().map(TypeFrame::typeName).toList());
        assertNull(card(root, "extra").lockingFrame(), "the edited type's own content");
        assertEquals("EUAddress", card(root, "street").lockingFrame().typeName());
    }

    @Test
    void outsideATypeEditorTheRootTypeIsFramedToo() throws Exception {
        VisualNode root = build(TYPE_EDITOR_SCHEMA, 2000, false);

        assertEquals(List.of("EUAddress", "Derived"),
                root.getTypeFrames().stream().map(TypeFrame::typeName).toList());
        assertEquals("Derived", card(root, "extra").lockingFrame().typeName());
    }
}
