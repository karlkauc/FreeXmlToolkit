package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Declaring top-level types: placement, content, undo, name checks and a valid schema. */
class AddGlobalTypeCommandTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order" type="OrderType"/>
              <xs:complexType name="OrderType"><xs:sequence><xs:element name="id" type="CodeType"/></xs:sequence></xs:complexType>
              <xs:simpleType name="CodeType"><xs:restriction base="xs:string"/></xs:simpleType>
              <xs:element name="note" type="xs:string"/>
            </xs:schema>
            """;

    private XsdSchema schema;
    private CommandManager commands;

    @BeforeEach
    void setUp() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        commands = new XsdEditorContext(schema).getCommandManager();
    }

    private List<String> topLevel() {
        return schema.getChildren().stream().map(XsdNode::getName).toList();
    }

    private void assertValid() throws Exception {
        String xsd = new XsdSerializer().serialize(schema);
        assertDoesNotThrow(() -> SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd))), xsd);
    }

    @Test
    void complexTypeStartsWithAnEmptySequenceAfterTheLastComplexType() throws Exception {
        var command = new AddGlobalTypeCommand(schema, Kind.COMPLEX, " AddressType ");
        assertTrue(commands.executeCommand(command));
        XsdComplexType added = assertInstanceOf(XsdComplexType.class, command.getAddedType());
        assertEquals("AddressType", added.getName());
        assertInstanceOf(XsdSequence.class, added.getChildren().getFirst());
        assertEquals(List.of("order", "OrderType", "AddressType", "CodeType", "note"), topLevel());
        assertTrue(XsdEditRules.addElement(added.getChildren().getFirst()).isAllowed(),
                "the new type can take child elements right away");
        assertValid();

        assertTrue(commands.undo());
        assertEquals(List.of("order", "OrderType", "CodeType", "note"), topLevel());
    }

    @Test
    void simpleTypeRestrictsStringAfterTheLastSimpleType() throws Exception {
        var command = new AddGlobalTypeCommand(schema, Kind.SIMPLE, "ISINType");
        assertTrue(commands.executeCommand(command));
        XsdSimpleType added = assertInstanceOf(XsdSimpleType.class, command.getAddedType());
        assertEquals("xs:string", assertInstanceOf(XsdRestriction.class, added.getChildren().getFirst()).getBase());
        assertEquals(List.of("order", "OrderType", "CodeType", "ISINType", "note"), topLevel());
        assertValid();
    }

    @Test
    void firstTypeOfItsKindGoesToTheEnd() throws Exception {
        XsdSchema empty = new XsdNodeFactory().fromString("""
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"><xsd:element name="a" type="xsd:string"/></xsd:schema>
                """);
        var command = new AddGlobalTypeCommand(empty, Kind.SIMPLE, "S");
        assertTrue(command.execute());
        assertSame(command.getAddedType(), empty.getChildren().getLast());
        String base = ((XsdRestriction) command.getAddedType().getChildren().getFirst()).getBase();
        assertEquals(XsdEditRules.xsdPrefix(empty) + ":string", base);
        assertTrue(XsdEditRules.isBuiltinRef(empty, base), "the base resolves to the XSD built-in");
    }

    @Test
    void takenOrInvalidNamesAreRefusedWithoutAnUndoStep() {
        assertFalse(commands.executeCommand(new AddGlobalTypeCommand(schema, Kind.SIMPLE, "OrderType")),
                "simple and complex types share one symbol space");
        assertFalse(commands.executeCommand(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "1Type")));
        assertFalse(commands.executeCommand(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "my type")));
        assertFalse(commands.executeCommand(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "p:Type")));
        assertFalse(commands.executeCommand(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "  ")));
        assertFalse(commands.canUndo());
        assertEquals(4, schema.getChildren().size());
    }

    @Test
    void nameRuleExplainsAndSuggestsAFreeName() {
        assertTrue(XsdEditRules.addGlobalType(schema, "CodeType").reason().contains("already has a simple type"));
        assertTrue(XsdEditRules.addGlobalType(schema, "9x").reason().contains("not a valid XML name"));
        assertTrue(XsdEditRules.addGlobalType(schema, "note").isAllowed(), "element names do not clash with types");
        assertTrue(XsdEditRules.addGlobalType(schema, "_a.b-c1").isAllowed());
        assertEquals("NewType", XsdEditRules.uniqueTypeName(schema, "NewType"));
        assertTrue(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "NewType").execute());
        assertTrue(new AddGlobalTypeCommand(schema, Kind.COMPLEX, "NewType2").execute());
        assertEquals("NewType3", XsdEditRules.uniqueTypeName(schema, "NewType"));
    }
}
