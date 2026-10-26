package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The structural commands never produce an invalid schema: they refuse conflicting edits, and
 * their replacing variants drop the conflicting content in one undoable step.
 */
class StructuralEditGuardTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="a" type="xs:string"/>
                          <xs:element name="b" type="xs:string"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="amount" type="xs:int"/>
                    <xs:element name="person" type="PersonType"/>
                    <xs:element name="flagged">
                      <xs:complexType>
                        <xs:attribute name="on" type="xs:boolean"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="PersonType"><xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence></xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;
    private XsdEditorContext context;
    private CommandManager commands;

    @BeforeEach
    void setUp() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        context = new XsdEditorContext(schema);
        commands = context.getCommandManager();
    }

    private XsdElement element(String name) {
        return find(schema, name);
    }

    private static XsdElement find(XsdNode node, String name) {
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdElement e && name.equals(e.getName())) {
                return e;
            }
            XsdElement deeper = find(child, name);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private String xsd() {
        return new XsdSerializer().serialize(schema);
    }

    private static void assertValidSchema(String xsd) {
        assertDoesNotThrow(() -> SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd))), xsd);
    }

    @Test
    void reportedBug_typeOnElementWithSequenceIsRefusedUnlessReplacing() {
        assertValidSchema(xsd());
        XsdElement item = element("item");

        assertFalse(commands.executeCommand(new ChangeTypeCommand(context, item, "xs:int")));
        assertNull(item.getType());
        assertFalse(commands.canUndo(), "a refused edit leaves no undo step");
        assertValidSchema(xsd());

        assertTrue(commands.executeCommand(ChangeTypeCommand.replacingInlineType(context, item, "xs:int")));
        assertEquals("xs:int", item.getType());
        assertNull(XsdEditRules.inlineType(item), "the inline complex type is gone");
        assertValidSchema(xsd());

        assertTrue(commands.undo(), "one step undoes both");
        assertNull(item.getType());
        assertInstanceOf(XsdComplexType.class, XsdEditRules.inlineType(item));
        assertNotNull(element("a"), "the child elements are back");
        assertValidSchema(xsd());
    }

    @Test
    void compositorOnTypedElementIsRefusedUnlessReplacing() {
        XsdElement amount = element("amount");
        assertFalse(commands.executeCommand(new AddSequenceCommand(amount)));
        assertEquals("xs:int", amount.getType());

        assertTrue(commands.executeCommand(new AddSequenceCommand(amount).replacingType()));
        assertNull(amount.getType());
        assertTrue(amount.hasCompositor());
        assertValidSchema(xsd());

        assertTrue(commands.undo());
        assertEquals("xs:int", amount.getType());
        assertNull(XsdEditRules.inlineType(amount));
    }

    @Test
    void compositorGoesBeforeExistingAttributes() {
        XsdElement flagged = element("flagged");
        assertTrue(commands.executeCommand(new AddSequenceCommand(flagged)));
        XsdComplexType ct = (XsdComplexType) XsdEditRules.inlineType(flagged);
        assertInstanceOf(XsdSequence.class, ct.getChildren().get(0));
        assertInstanceOf(XsdAttribute.class, ct.getChildren().get(1));
        assertValidSchema(xsd());
    }

    @Test
    void contentUnderSimpleTypedElementsIsRefused() {
        XsdElement amount = element("amount");
        assertFalse(commands.executeCommand(new AddElementCommand(amount, "child")));
        assertFalse(commands.executeCommand(new AddAttributeCommand(amount, "attr")));
        assertTrue(amount.getChildren().isEmpty());

        XsdClipboard clipboard = new XsdClipboard();
        clipboard.copy(element("a"));
        assertFalse(commands.executeCommand(new PasteNodeCommand(clipboard, amount)));
        assertFalse(commands.executeCommand(new MoveNodeCommand(element("b"), amount, 0)));
        assertTrue(amount.getChildren().isEmpty());
        assertValidSchema(xsd());
    }

    @Test
    void attributeOnNamedComplexTypeGoesIntoThatType() {
        XsdElement person = element("person");
        assertTrue(commands.executeCommand(new AddAttributeCommand(person, "id")));
        assertTrue(person.getChildren().isEmpty(), "no inline complex type next to type=\"PersonType\"");
        XsdNode personType = XsdEditRules.findNamedType(person, "PersonType");
        assertTrue(personType.getChildren().stream().anyMatch(c -> c instanceof XsdAttribute a && "id".equals(a.getName())));
        assertValidSchema(xsd());
    }

    @Test
    void switchingTheCompositorKeepsItsChildren() {
        XsdElement item = element("item");
        assertTrue(commands.executeCommand(AddCompositorCommand.choice(item)));
        XsdNode choice = XsdEditRules.elementContainer(item);
        assertInstanceOf(org.fxt.freexmltoolkit.controls.v2.model.XsdChoice.class, choice);
        assertEquals(2, choice.getChildren().size(), "a and b moved into the choice");
        assertValidSchema(xsd());

        assertTrue(commands.undo());
        XsdNode sequence = XsdEditRules.elementContainer(item);
        assertInstanceOf(XsdSequence.class, sequence);
        assertEquals(2, sequence.getChildren().size(), "undo moves them back");
    }

    @Test
    void allRefusesRepeatingElementsInXsd10() {
        XsdElement item = element("item");
        element("a").setMaxOccurs(-1);
        EditCheck check = XsdEditRules.addCompositor(item, "all");
        assertTrue(check.isBlocked(), check.reason());
        assertTrue(check.reason().contains("unbounded"), check.reason());
        assertFalse(commands.executeCommand(AddCompositorCommand.all(item)));
        element("a").setMaxOccurs(1);
        assertTrue(commands.executeCommand(AddCompositorCommand.all(item)));
        assertValidSchema(xsd());
    }

    @Test
    void refNextToATypeIsRefused() {
        XsdElement amount = element("amount");
        assertFalse(commands.executeCommand(new ChangeRefCommand(context, amount, "order")));
        assertNull(amount.getRef());
    }
}
