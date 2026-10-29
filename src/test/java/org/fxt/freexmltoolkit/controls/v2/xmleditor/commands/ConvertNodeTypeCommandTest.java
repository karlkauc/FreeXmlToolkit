package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Node-type changes in place (XMLSpy type icon) and Wrap in Element, both undoable. */
class ConvertNodeTypeCommandTest {

    private XmlElement root;

    @BeforeEach
    void setUp() {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<root a=\"1\" b=\"2\"><name>Fred</name><box><x/></box><!--note--></root>");
        root = context.getDocument().getRootElement();
    }

    @Test
    void elementBecomesAttributeAndBack() {
        XmlElement name = root.getChildElement("name");
        ConvertNodeTypeCommand cmd = ConvertNodeTypeCommand.ofNode(name, ConvertNodeTypeCommand.Kind.ATTRIBUTE);
        assertTrue(cmd.execute());
        assertEquals("Fred", root.getAttribute("name"));
        assertNull(root.getChildElement("name"));

        assertTrue(cmd.undo());
        assertNull(root.getAttribute("name"));
        assertSame(name, root.getChildElement("name"), "the original node comes back");
        assertEquals(0, root.indexOf(name), "at its old position");
        assertEquals(List.of("a", "b"), List.copyOf(root.getAttributes().keySet()));
    }

    @Test
    void attributeBecomesFirstChildElementAndUndoKeepsTheAttributeOrder() {
        ConvertNodeTypeCommand cmd = ConvertNodeTypeCommand.ofAttribute(root, "a", ConvertNodeTypeCommand.Kind.ELEMENT);
        assertTrue(cmd.execute());
        assertFalse(root.hasAttribute("a"));
        XmlNode first = root.getChild(0);
        assertInstanceOf(XmlElement.class, first);
        assertEquals("a", ((XmlElement) first).getName());
        assertEquals("1", ((XmlElement) first).getTextContent());

        assertTrue(cmd.undo());
        assertEquals(List.of("a", "b"), List.copyOf(root.getAttributes().keySet()), "position preserved");
        assertEquals("name", ((XmlElement) root.getChild(0)).getName());
    }

    @Test
    void valueOnlyNodesKeepTheirTextAndGetADefaultName() {
        XmlComment comment = (XmlComment) root.getChild(root.getChildCount() - 1);
        ConvertNodeTypeCommand toPi = ConvertNodeTypeCommand.ofNode(comment,
                ConvertNodeTypeCommand.Kind.PROCESSING_INSTRUCTION);
        assertTrue(toPi.execute());
        XmlProcessingInstruction pi = (XmlProcessingInstruction) root.getChild(root.getChildCount() - 1);
        assertEquals("pi", pi.getTarget());
        assertEquals("note", pi.getData());

        ConvertNodeTypeCommand toText = ConvertNodeTypeCommand.ofNode(pi, ConvertNodeTypeCommand.Kind.TEXT);
        assertTrue(toText.execute());
        assertEquals("note", ((XmlText) root.getChild(root.getChildCount() - 1)).getText());
    }

    @Test
    void containersAndTheRootCannotChangeTheirType() {
        assertTrue(ConvertNodeTypeCommand.targetsOfNode(root.getChildElement("box")).isEmpty(), "has child elements");
        assertTrue(ConvertNodeTypeCommand.targetsOfNode(root).isEmpty(), "the root element");
        assertFalse(ConvertNodeTypeCommand.ofNode(root.getChildElement("box"), ConvertNodeTypeCommand.Kind.TEXT).execute());
        assertEquals(5, ConvertNodeTypeCommand.targetsOfNode(root.getChildElement("name")).size());
    }

    @Test
    void attributeNamesStayUnique() {
        root.getChildElement("name").setName("a");
        ConvertNodeTypeCommand cmd = ConvertNodeTypeCommand.ofNode(root.getChildElement("a"),
                ConvertNodeTypeCommand.Kind.ATTRIBUTE);
        assertTrue(cmd.execute());
        assertEquals("1", root.getAttribute("a"), "the existing attribute is untouched");
        assertEquals("Fred", root.getAttribute("a2"));
    }

    @Test
    void wrapInElementInsertsANewParentAndUndoRestores() {
        XmlElement name = root.getChildElement("name");
        WrapInElementCommand wrap = new WrapInElementCommand(name, "person");
        assertTrue(wrap.execute());
        XmlElement person = root.getChildElement("person");
        assertSame(wrap.getWrapper(), person);
        assertEquals(0, root.indexOf(person));
        assertSame(name, person.getChild(0));
        assertSame(person, name.getParent());

        assertTrue(wrap.undo());
        assertNull(root.getChildElement("person"));
        assertSame(root, name.getParent());
        assertEquals(0, root.indexOf(name));
    }
}
