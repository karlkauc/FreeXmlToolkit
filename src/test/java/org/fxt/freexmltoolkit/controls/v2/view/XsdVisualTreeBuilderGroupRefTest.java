package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.CommandManager;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.menu.XsdContextMenuFactory;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/** References to named groups and attribute groups show the referenced content. */
@ExtendWith(ApplicationExtension.class) // cards forward model changes through Platform.runLater
class XsdVisualTreeBuilderGroupRefTest {

    private static final String DEFINITIONS = """
              <xs:group name="Names">
                <xs:sequence>
                  <xs:element name="first" type="xs:string"/>
                  <xs:group ref="Extra"/>
                </xs:sequence>
              </xs:group>
              <xs:group name="Extra">
                <xs:choice><xs:element name="nick" type="xs:string"/></xs:choice>
              </xs:group>
              <xs:attributeGroup name="Common">
                <xs:attribute name="id" type="xs:string"/>
                <xs:attributeGroup ref="Lang"/>
              </xs:attributeGroup>
              <xs:attributeGroup name="Lang">
                <xs:attribute name="lang" type="xs:string"/>
              </xs:attributeGroup>
            """;

    private static String schema(String body) {
        return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" + DEFINITIONS + body + "</xs:schema>";
    }

    private static VisualNode build(String body, int budget) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(schema(body));
        return new XsdVisualTreeBuilder().withNodeBudget(budget).buildFromSchema(schema, null, null);
    }

    private static List<VisualNode> all(VisualNode root) {
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

    private static List<String> labels(VisualNode root) {
        return all(root).stream().map(VisualNode::getLabel).toList();
    }

    @Test
    void groupInACompositorShowsItsContentAndCardinality() throws Exception {
        VisualNode root = build("""
                <xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:group ref="Names" minOccurs="0" maxOccurs="3"/>
                </xs:sequence></xs:complexType></xs:element>
                """, 2000);

        VisualNode card = all(root).stream().filter(n -> n.getType() == NodeWrapperType.GROUP)
                .filter(n -> "group: Names".equals(n.getLabel())).findFirst().orElseThrow();

        assertEquals(0, card.getMinOccurs());
        assertEquals(3, card.getMaxOccurs());
        assertTrue(labels(root).containsAll(List.of("first", "group: Extra", "nick")), labels(root).toString());
    }

    @Test
    void groupDirectlyInAComplexTypeAndInAnExtension() throws Exception {
        VisualNode root = build("""
                <xs:element name="root" type="Derived"/>
                <xs:complexType name="Base"><xs:group ref="Extra"/></xs:complexType>
                <xs:complexType name="Derived"><xs:complexContent><xs:extension base="Base">
                  <xs:group ref="Names"/>
                  <xs:attributeGroup ref="Lang"/>
                </xs:extension></xs:complexContent></xs:complexType>
                """, 2000);

        assertTrue(labels(root).containsAll(List.of("group: Extra", "group: Names", "first", "lang")),
                labels(root).toString());
    }

    @Test
    void attributeGroupsNestAndAppearInSimpleContent() throws Exception {
        VisualNode root = build("""
                <xs:element name="root"><xs:complexType>
                  <xs:simpleContent><xs:extension base="xs:string">
                    <xs:attributeGroup ref="Common"/>
                  </xs:extension></xs:simpleContent>
                </xs:complexType></xs:element>
                """, 2000);

        assertTrue(labels(root).containsAll(
                List.of("attributeGroup: Common", "id", "attributeGroup: Lang", "lang")), labels(root).toString());
    }

    @Test
    void deferredCardShowsGroupsToo() throws Exception {
        String body = """
                <xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:element name="child"><xs:complexType>
                    <xs:group ref="Names"/><xs:attributeGroup ref="Lang"/>
                  </xs:complexType></xs:element>
                </xs:sequence></xs:complexType></xs:element>
                """;

        assertEquals(labels(build(body, 2000)).stream().sorted().toList(),
                labels(build(body, 1)).stream().sorted().toList());
    }

    @Test
    void circularGroupsTerminate() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:group name="A"><xs:sequence><xs:group ref="B"/></xs:sequence></xs:group>
                  <xs:group name="B"><xs:sequence><xs:group ref="A"/></xs:sequence></xs:group>
                  <xs:attributeGroup name="X"><xs:attributeGroup ref="Y"/></xs:attributeGroup>
                  <xs:attributeGroup name="Y"><xs:attributeGroup ref="X"/></xs:attributeGroup>
                  <xs:element name="root"><xs:complexType>
                    <xs:group ref="A"/><xs:attributeGroup ref="X"/>
                  </xs:complexType></xs:element>
                </xs:schema>
                """);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                () -> new XsdVisualTreeBuilder().buildFromSchema(schema, null, null));
    }

    @Test
    void unknownGroupsAreNamed() throws Exception {
        VisualNode root = build("""
                <xs:element name="root"><xs:complexType>
                  <xs:group ref="Gone"/><xs:attributeGroup ref="AlsoGone"/>
                </xs:complexType></xs:element>
                """, 2000);

        assertTrue(labels(root).containsAll(
                List.of("Unresolved group: Gone", "Unresolved attribute group: AlsoGone")), labels(root).toString());
    }

    @Test
    void groupsFromAnIncludeResolve(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("groups.xsd"), schema(""));
        Path main = dir.resolve("main.xsd");
        String text = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="groups.xsd"/>
                  <xs:element name="root"><xs:complexType>
                    <xs:group ref="Names"/><xs:attributeGroup ref="Lang"/>
                  </xs:complexType></xs:element>
                </xs:schema>
                """;
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(text, main, dir);

        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, null);

        assertTrue(labels(root).containsAll(List.of("first", "lang")), labels(root).toString());
    }

    @Test
    void deletingTheCardRemovesTheReferenceOnly() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(schema("""
                <xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:group ref="Names"/>
                </xs:sequence></xs:complexType></xs:element>
                """));
        VisualNode card = all(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null)).stream()
                .filter(n -> "group: Names".equals(n.getLabel())).findFirst().orElseThrow();
        CommandManager commands = new CommandManager();

        commands.executeCommand(new DeleteNodeCommand(card.getModelNode()));
        XsdVisualTreeBuilder.invalidateCacheFor(schema);
        assertFalse(labels(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null)).contains("first"));
        assertEquals(1, schema.getChildren().stream()
                .filter(n -> "Names".equals(n.getName())).count(), "the definition stays");

        commands.undo();
        assertTrue(labels(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null)).contains("first"));
    }

    @Test
    void attributeGroupCardsStayClosedUntilOpened() throws Exception {
        VisualNode root = build("""
                <xs:element name="root"><xs:complexType>
                  <xs:attributeGroup ref="Lang"/>
                </xs:complexType></xs:element>
                """, 2000);
        VisualNode card = root.getChildren().getFirst();

        assertTrue(card.hasUnloadedChildren());
        assertFalse(card.isExpanded());

        card.toggleExpanded();

        assertEquals("lang", card.getChildren().getFirst().getLabel());
    }

    @Test
    void groupRefsSurviveARoundTripThroughTheModel() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:group name="G"><xs:sequence><xs:element name="e" type="xs:string"/></xs:sequence></xs:group>
                  <xs:attributeGroup name="Inner"><xs:attribute name="a" type="xs:string"/></xs:attributeGroup>
                  <xs:attributeGroup name="Outer">
                    <xs:attributeGroup ref="Inner"/>
                    <xs:anyAttribute namespace="##other"/>
                  </xs:attributeGroup>
                  <xs:complexType name="T"><xs:group ref="G" minOccurs="0"/></xs:complexType>
                </xs:schema>
                """);

        String saved = new XsdSerializer().serialize(schema);

        assertAll(saved,
                () -> assertTrue(saved.contains("ref=\"G\""), "group in a complexType"),
                () -> assertTrue(saved.contains("ref=\"Inner\""), "attribute group in an attribute group"),
                () -> assertTrue(saved.contains("anyAttribute"), "wildcard in an attribute group"));
    }

    @Test
    void menusOfferOnlyWhatIsValidOnAReference() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(schema("""
                <xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:group ref="Names"/>
                  <xs:group ref="Gone"/>
                </xs:sequence></xs:complexType></xs:element>
                """));
        List<VisualNode> cards = all(new XsdVisualTreeBuilder().buildFromSchema(schema, null, null));
        XsdContextMenuFactory menus = new XsdContextMenuFactory(new XsdEditorContext(schema));

        VisualNode group = cards.stream().filter(n -> "group: Names".equals(n.getLabel())).findFirst().orElseThrow();
        VisualNode placeholder = cards.stream().filter(n -> "Unresolved group: Gone".equals(n.getLabel()))
                .findFirst().orElseThrow();

        assertEquals(List.of("Delete", "Copy XPath"), WaitForAsyncUtils.asyncFx(() ->
                menus.createContextMenu(group).getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText).filter(java.util.Objects::nonNull).toList()).get());
        assertTrue(WaitForAsyncUtils.asyncFx(() -> menus.createContextMenu(placeholder).getItems().isEmpty()).get());
    }
}
