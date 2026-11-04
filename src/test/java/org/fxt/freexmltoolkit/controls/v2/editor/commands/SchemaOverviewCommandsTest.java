package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Commands and rules behind the Schema Overview: adding, ordering, renaming global
 * components, schema references, namespaces, identity constraints, asserts.
 */
class SchemaOverviewCommandsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:t" xmlns:o="urn:other"
                       targetNamespace="urn:t" elementFormDefault="qualified">
              <xs:import namespace="urn:other" schemaLocation="other.xsd"/>
              <!-- the order type -->
              <xs:complexType name="OrderType">
                <xs:sequence>
                  <xs:element name="id" type="t:IdType"/>
                </xs:sequence>
                <xs:attribute name="kind" type="t:IdType"/>
              </xs:complexType>
              <xs:simpleType name="IdType">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
              <xs:element name="order" type="t:OrderType">
                <xs:key name="orderKey">
                  <xs:selector xpath="t:id"/>
                  <xs:field xpath="."/>
                </xs:key>
              </xs:element>
              <xs:attributeGroup name="Common"/>
            </xs:schema>
            """;

    private XsdSchema schema;

    @BeforeEach
    void parse() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
    }

    private XsdNode child(String name) {
        return schema.getChildren().stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    private List<String> names() {
        return schema.getChildren().stream()
                .map(c -> c instanceof XsdComment ? "#comment" : c instanceof XsdImport ? "#import" : c.getName())
                .toList();
    }

    private String save() {
        return new XsdSerializer().serialize(schema, XsdSortOrder.PRESERVE);
    }

    @Nested
    class AddComponent {

        @Test
        @DisplayName("Adds a group at the given index and undoes it")
        void addsAtIndex() {
            var cmd = new AddGlobalComponentCommand(schema, GlobalComponentKind.GROUP, "G", 2);
            assertTrue(cmd.execute());
            assertEquals("G", schema.getChildren().get(2).getName());
            assertInstanceOf(XsdGroup.class, cmd.getAdded());
            assertTrue(save().contains("<xs:group name=\"G\">"));
            assertTrue(cmd.undo());
            assertFalse(names().contains("G"));
        }

        @Test
        @DisplayName("A component never lands in front of the imports")
        void clampsBehindDirectives() {
            var cmd = new AddGlobalComponentCommand(schema, GlobalComponentKind.ELEMENT, "first", 0);
            assertTrue(cmd.execute());
            assertEquals("#import", names().getFirst());
            assertEquals("first", names().get(1));
        }

        @Test
        @DisplayName("Types share one symbol space, elements have their own")
        void symbolSpaces() {
            assertTrue(SchemaLayoutRules.addComponent(schema, GlobalComponentKind.COMPLEX_TYPE, "IdType").isBlocked());
            assertTrue(SchemaLayoutRules.addComponent(schema, GlobalComponentKind.ELEMENT, "IdType").isAllowed());
            assertTrue(SchemaLayoutRules.addComponent(schema, GlobalComponentKind.ELEMENT, "order").isBlocked());
            assertTrue(SchemaLayoutRules.addComponent(schema, GlobalComponentKind.NOTATION, "1bad").isBlocked());
            assertFalse(new AddGlobalComponentCommand(schema, GlobalComponentKind.SIMPLE_TYPE, "OrderType", -1).execute());
        }

        @Test
        @DisplayName("Notation, attribute and annotation are written correctly")
        void otherKinds() {
            new AddGlobalComponentCommand(schema, GlobalComponentKind.NOTATION, "png", -1).execute();
            new AddGlobalComponentCommand(schema, GlobalComponentKind.ATTRIBUTE, "lang", -1).execute();
            new AddGlobalComponentCommand(schema, GlobalComponentKind.ANNOTATION, null, 0).execute();
            String out = save();
            assertTrue(out.contains("<xs:notation name=\"png\" public=\"png\"/>"), out);
            assertTrue(out.contains("<xs:attribute name=\"lang\" type=\"xs:string\"/>"), out);
            assertTrue(out.indexOf("<xs:annotation>") < out.indexOf("<xs:import"), out);
        }
    }

    @Nested
    class Ordering {

        @Test
        @DisplayName("Sort by type then name keeps the import on top and comments with their component")
        void sortTypeThenName() {
            var cmd = ReorderSchemaChildrenCommand.sorted(schema, ReorderSchemaChildrenCommand.SortMode.TYPE_THEN_NAME);
            assertTrue(cmd.execute());
            assertEquals(List.of("#import", "Common", "#comment", "OrderType", "order", "IdType"), names());
            assertTrue(cmd.undo());
            assertEquals(List.of("#import", "#comment", "OrderType", "IdType", "order", "Common"), names());
        }

        @Test
        @DisplayName("Sort by name then type")
        void sortNameThenType() {
            ReorderSchemaChildrenCommand.sorted(schema, ReorderSchemaChildrenCommand.SortMode.NAME_THEN_TYPE).execute();
            assertEquals(List.of("#import", "Common", "IdType", "order", "#comment", "OrderType"), names());
        }

        @Test
        @DisplayName("Move up/down and the order survives a save")
        void moveAndSave() throws Exception {
            assertTrue(ReorderSchemaChildrenCommand.moved(schema, List.of(child("order")), -1).execute());
            assertEquals(List.of("#import", "#comment", "OrderType", "order", "IdType", "Common"), names());
            XsdSchema reread = new XsdNodeFactory().fromString(save());
            assertEquals("order", reread.getChildren().get(3).getName());
        }

        @Test
        @DisplayName("A move skips rows the Overview hides")
        void moveSkipsHiddenRows() {
            XsdNode idType = child("IdType");
            var cmd = ReorderSchemaChildrenCommand.moved(schema, List.of(child("Common")), -1,
                    node -> node != child("order"));
            assertTrue(cmd.execute());
            // the hidden "order" keeps its place; Common swaps with the visible IdType
            assertEquals(List.of("#import", "#comment", "OrderType", "Common", "order", "IdType"), names());
            assertNotNull(idType);
        }

        @Test
        @DisplayName("Content of an included file never blocks a reorder")
        void includedContentIgnoredByOrderRule() {
            XsdElement included = new XsdElement("fromInclude");
            included.setSourceInfo(IncludeSourceInfo.forIncludedSchema(java.nio.file.Path.of("inc.xsd"), "inc.xsd",
                    new XsdInclude("inc.xsd")));
            List<XsdNode> order = new java.util.ArrayList<>(schema.getChildren());
            order.addFirst(included);
            assertTrue(SchemaLayoutRules.checkOrder(order).isAllowed());
        }

        @Test
        @DisplayName("A component cannot move above an import")
        void blocksComponentAboveDirective() {
            var cmd = ReorderSchemaChildrenCommand.movedTo(schema, List.of(child("Common")), 0);
            assertTrue(cmd.check().isBlocked());
            assertFalse(cmd.execute());
        }
    }

    @Nested
    class Renaming {

        @Test
        @DisplayName("Renaming a type updates type and base references, undo restores them")
        void renameWithReferences() {
            XsdNode idType = child("IdType");
            var cmd = new RenameGlobalComponentCommand(idType, "Identifier", true);
            assertEquals(2, cmd.referenceCount());
            assertTrue(cmd.execute());
            String out = save();
            assertTrue(out.contains("type=\"t:Identifier\""), out);
            assertFalse(out.contains("IdType"), out);
            assertTrue(cmd.undo());
            assertEquals(2, save().split("t:IdType", -1).length - 1);
        }

        @Test
        @DisplayName("Renaming without references leaves them alone")
        void renameOnly() {
            new RenameGlobalComponentCommand(child("OrderType"), "Order2", false).execute();
            assertTrue(save().contains("type=\"t:OrderType\""));
        }

        @Test
        @DisplayName("Rename is blocked on a name clash")
        void clash() {
            assertTrue(new RenameGlobalComponentCommand(child("IdType"), "OrderType", true).check().isBlocked());
        }
    }

    @Nested
    class SchemaLevel {

        @Test
        @DisplayName("Edit an import's namespace and location")
        void editImport() {
            XsdImport imp = (XsdImport) schema.getChildren().getFirst();
            var cmd = new EditSchemaReferenceCommand(imp, "urn:new", "new.xsd");
            assertTrue(cmd.execute());
            assertEquals("urn:new", imp.getNamespace());
            assertEquals("new.xsd", imp.getSchemaLocation());
            cmd.undo();
            assertEquals("other.xsd", imp.getSchemaLocation());
            assertTrue(new EditSchemaReferenceCommand(new XsdInclude("a.xsd"), null, "").check().isBlocked());
        }

        @Test
        @DisplayName("Namespaces: add, in-use protection, xs protection, undo")
        void namespaces() {
            var add = new ChangeNamespaceDeclarationCommand(schema, null, "n", "urn:n");
            assertTrue(add.execute());
            assertEquals("urn:n", schema.getNamespaces().get("n"));
            assertTrue(new ChangeNamespaceDeclarationCommand(schema, "t", null, null).check().isBlocked());
            assertTrue(new ChangeNamespaceDeclarationCommand(schema, "xs", "xsd",
                    "http://www.w3.org/2001/XMLSchema").check().isBlocked());
            var removeUnused = new ChangeNamespaceDeclarationCommand(schema, "n", null, null);
            assertTrue(removeUnused.execute());
            assertFalse(schema.getNamespaces().containsKey("n"));
            removeUnused.undo();
            add.undo();
            assertFalse(schema.getNamespaces().containsKey("n"));
        }

        @Test
        @DisplayName("Schema documentation reads and writes the first annotation node")
        void schemaDocumentation() {
            assertTrue(schema.getDocumentations().isEmpty());
            schema.setDocumentations(List.of(new XsdDocumentation("Intro")));
            assertInstanceOf(XsdAnnotation.class, schema.getChildren().getFirst());
            assertTrue(save().contains("Intro"));
            schema.setDocumentations(List.of());
            assertFalse(schema.getChildren().getFirst() instanceof XsdAnnotation);
        }

        @Test
        @DisplayName("Notation identifiers: one of them is required")
        void notation() {
            XsdNotation notation = new XsdNotation("png");
            var cmd = new ChangeNotationCommand(notation, "image/png", null);
            assertTrue(cmd.execute());
            assertEquals("image/png", notation.getPublicId());
            assertTrue(new ChangeNotationCommand(notation, " ", null).check().isBlocked());
        }
    }

    @Nested
    class ComponentDetails {

        @Test
        @DisplayName("Edit a key: name, selector, more fields; undo restores")
        void editKey() {
            XsdKey key = (XsdKey) child("order").getChildren().stream()
                    .filter(XsdKey.class::isInstance).findFirst().orElseThrow();
            var cmd = new EditIdentityConstraintCommand(key, "k2", "t:item", List.of("@a", "@b"), null);
            assertTrue(cmd.execute());
            assertEquals("k2", key.getName());
            assertEquals("t:item", key.getSelector().getXpath());
            assertEquals(2, key.getFields().size());
            cmd.undo();
            assertEquals("orderKey", key.getName());
            assertEquals(1, key.getFields().size());
            assertEquals(".", key.getFields().getFirst().getXpath());
            assertTrue(new EditIdentityConstraintCommand(key, "k", "x", List.of(" "), null).check().isBlocked());
        }

        @Test
        @DisplayName("Assertions on a complex type: add, edit, written after the attributes")
        void asserts() {
            XsdNode type = child("OrderType");
            var add = new AssertCommands.Add(type, "@kind != ''", null);
            assertTrue(add.execute());
            new AssertCommands.Edit(add.getCreated(), "count(t:id) = 1", "##targetNamespace").execute();
            assertEquals(1, AssertCommands.assertsOf(type).size());
            String out = save();
            assertTrue(out.indexOf("<xs:assert") > out.indexOf("name=\"kind\""), out);
            assertTrue(out.contains("xpathDefaultNamespace=\"##targetNamespace\""), out);
            add.undo();
            assertTrue(AssertCommands.assertsOf(type).isEmpty());
        }

        @Test
        @DisplayName("Attribute group reference goes into the complex type")
        void attributeGroupRef() {
            var cmd = new AddAttributeGroupRefCommand(child("OrderType"), "t:Common");
            assertTrue(cmd.execute());
            assertTrue(save().contains("<xs:attributeGroup ref=\"t:Common\"/>"));
            assertTrue(new AddAttributeGroupRefCommand(child("Common"), "t:Common").check().isBlocked());
        }

        @Test
        @DisplayName("A failing part rolls the composite back")
        void compositeRollback() {
            var ok = new AddGlobalComponentCommand(schema, GlobalComponentKind.GROUP, "G1", -1);
            var fails = new AddGlobalComponentCommand(schema, GlobalComponentKind.GROUP, "G1", -1);
            assertFalse(new XsdCompositeCommand("two", List.of(ok, fails)).execute());
            assertFalse(names().contains("G1"));
        }
    }
}
