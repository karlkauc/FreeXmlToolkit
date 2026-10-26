package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Adding an element or attribute with all its properties in one command and one undo step. */
class AddNodeWithSpecCommandTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="id" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdSchema schema;
    private CommandManager commands;

    @BeforeEach
    void setUp() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        commands = new XsdEditorContext(schema).getCommandManager();
    }

    private XsdElement order() {
        return (XsdElement) schema.getChildren().get(0);
    }

    private XsdSequence orderSequence() {
        return (XsdSequence) order().getChildren().get(0).getChildren().get(0);
    }

    private void assertValidXsd() {
        String xsd = new XsdSerializer().serialize(schema);
        assertDoesNotThrow(() -> SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd))), xsd);
    }

    @Test
    void elementGetsEveryPropertyAndOneUndoRemovesIt() {
        NewElementSpec spec = new NewElementSpec("item", "xs:int", 0, ChangeCardinalityCommand.UNBOUNDED,
                "One order line.", true, false, "5", null, "qualified", null);

        assertTrue(commands.executeCommand(AddElementCommand.of(order(), spec)));

        XsdElement item = (XsdElement) orderSequence().getChildren().get(1);
        assertEquals("item", item.getName());
        assertEquals("xs:int", item.getType());
        assertEquals(0, item.getMinOccurs());
        assertEquals(ChangeCardinalityCommand.UNBOUNDED, item.getMaxOccurs());
        assertEquals("One order line.", item.getDocumentation());
        assertTrue(item.isNillable());
        assertEquals("5", item.getDefaultValue());
        assertEquals("qualified", item.getForm());
        String xsd = new XsdSerializer().serialize(schema);
        assertTrue(xsd.contains("maxOccurs=\"unbounded\""), xsd);
        assertTrue(xsd.contains("minOccurs=\"0\""), xsd);
        assertValidXsd();

        assertTrue(commands.undo());
        assertEquals(1, orderSequence().getChildren().size(), "one undo removes the whole element");
        assertFalse(commands.canUndo());
    }

    @Test
    void globalElementCanBeAbstractWithASubstitutionGroup() {
        assertTrue(commands.executeCommand(AddElementCommand.of(schema,
                new NewElementSpec("shape", "xs:string", 1, 1, null, false, true, null, null, null, null))));
        assertTrue(commands.executeCommand(AddElementCommand.of(schema,
                new NewElementSpec("circle", "xs:string", 1, 1, null, false, false, null, null, null, "shape"))));

        XsdElement circle = (XsdElement) schema.getChildren().get(2);
        assertEquals("shape", circle.getSubstitutionGroup());
        assertTrue(((XsdElement) schema.getChildren().get(1)).isAbstract());
        assertValidXsd();
    }

    @Test
    void invalidSpecIsRefusedWithoutUndoEntry() {
        assertFalse(commands.executeCommand(AddElementCommand.of(order(),
                new NewElementSpec("item", null, 3, 2, null, false, false, null, null, null, null))));
        assertFalse(commands.executeCommand(AddElementCommand.of(schema,
                new NewElementSpec("order", null, 1, 1, null, false, false, null, null, null, null))),
                "a top-level element named 'order' exists");
        assertFalse(commands.executeCommand(AddAttributeCommand.of(order(),
                new NewAttributeSpec("status", null, "required", null, "x", null, null))));
        assertEquals(1, orderSequence().getChildren().size());
        assertFalse(commands.canUndo());
    }

    @Test
    void attributeGetsEveryPropertyAndOneUndoRemovesIt() {
        assertTrue(commands.executeCommand(AddAttributeCommand.of(order(),
                new NewAttributeSpec("status", "xs:token", "required", "Order status.", null, null, null))));
        assertTrue(commands.executeCommand(AddAttributeCommand.of(order(),
                new NewAttributeSpec("currency", "xs:string", "optional", null, "EUR", null, "unqualified"))));

        var complexType = order().getChildren().get(0);
        XsdAttribute status = (XsdAttribute) complexType.getChildren().get(1);
        XsdAttribute currency = (XsdAttribute) complexType.getChildren().get(2);
        assertEquals("xs:token", status.getType());
        assertEquals("required", status.getUse());
        assertEquals("Order status.", status.getDocumentation());
        assertEquals("EUR", currency.getDefaultValue());
        assertEquals("optional", currency.getUse());
        assertEquals("unqualified", currency.getForm());
        assertValidXsd();

        assertTrue(commands.undo());
        assertTrue(commands.undo());
        assertEquals(1, complexType.getChildren().size());
    }
}
