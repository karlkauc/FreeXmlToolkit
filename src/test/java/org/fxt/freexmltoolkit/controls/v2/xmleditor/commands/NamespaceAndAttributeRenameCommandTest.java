package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;

/** Pure (no-UI) tests for the new XML element namespace + attribute-rename commands. */
class NamespaceAndAttributeRenameCommandTest {

    @Test
    void setNamespaceAddsDeclarationAndPrefixedName() {
        XmlElement element = new XmlElement("note");
        element.setAttribute("id", "1");

        SetElementNamespaceCommand cmd =
                new SetElementNamespaceCommand(element, "ns", "http://example.com/ns");
        assertTrue(cmd.execute());

        assertEquals("ns", element.getNamespacePrefix());
        assertEquals("http://example.com/ns", element.getNamespaceURI());
        assertEquals("ns:note", element.getQualifiedName(), "tag uses the prefix");
        assertEquals("http://example.com/ns", element.getAttribute("xmlns:ns"),
                "an xmlns declaration must be added so the namespace round-trips");

        assertTrue(cmd.undo());
        assertNull(element.getNamespacePrefix());
        assertNull(element.getNamespaceURI());
        assertFalse(element.hasAttribute("xmlns:ns"), "undo removes the declaration");
        assertEquals("1", element.getAttribute("id"), "other attributes are preserved");
    }

    @Test
    void clearingUriRemovesDeclaration() {
        XmlElement element = new XmlElement("note", "ns", "http://example.com/ns");
        element.setAttribute("xmlns:ns", "http://example.com/ns");

        SetElementNamespaceCommand cmd = new SetElementNamespaceCommand(element, "", "");
        assertTrue(cmd.execute());
        assertNull(element.getNamespacePrefix());
        assertFalse(element.hasAttribute("xmlns:ns"));
        assertEquals("note", element.getQualifiedName());
    }

    @Test
    void renameAttributePreservesValueAndOrder() {
        XmlElement element = new XmlElement("note");
        element.setAttribute("id", "42");
        element.setAttribute("type", "memo");

        RenameAttributeCommand cmd = new RenameAttributeCommand(element, "id", "name");
        assertTrue(cmd.execute());

        assertFalse(element.hasAttribute("id"));
        assertEquals("42", element.getAttribute("name"), "value is preserved");
        assertEquals(new ArrayList<>(java.util.List.of("name", "type")),
                new ArrayList<>(element.getAttributes().keySet()), "position is preserved");

        assertTrue(cmd.undo());
        assertEquals(new ArrayList<>(java.util.List.of("id", "type")),
                new ArrayList<>(element.getAttributes().keySet()));
        assertEquals("42", element.getAttribute("id"));
    }

    @Test
    void renameRejectsUnknownOrBlank() {
        XmlElement element = new XmlElement("note");
        element.setAttribute("id", "1");
        assertFalse(new RenameAttributeCommand(element, "missing", "x").execute());
        assertFalse(new RenameAttributeCommand(element, "id", " ").execute());
        assertFalse(new RenameAttributeCommand(element, "id", "id").execute());
    }
}
