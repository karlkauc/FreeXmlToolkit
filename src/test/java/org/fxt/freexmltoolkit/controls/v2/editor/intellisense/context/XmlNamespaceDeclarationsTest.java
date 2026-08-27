package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reads the {@code xmlns} declarations of the instance document being edited. */
class XmlNamespaceDeclarationsTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <invoice xmlns="urn:example:invoice"
                     xmlns:c="http://schemas.example.org/common/1.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <billTo>
                <""";

    @Test
    void defaultNamespaceMapsToEmptyPrefix() {
        XmlNamespaceDeclarations decl = XmlNamespaceDeclarations.scan(XML);
        assertEquals("", decl.prefixFor("urn:example:invoice").orElseThrow());
    }

    @Test
    void prefixedNamespaceMapsToItsPrefix() {
        XmlNamespaceDeclarations decl = XmlNamespaceDeclarations.scan(XML);
        assertEquals("c", decl.prefixFor("http://schemas.example.org/common/1.0").orElseThrow());
    }

    @Test
    void undeclaredNamespaceIsAbsent() {
        assertTrue(XmlNamespaceDeclarations.scan(XML).prefixFor("urn:nope").isEmpty());
    }

    @Test
    void laterDeclarationWins() {
        String xml = "<a xmlns:p=\"urn:one\"><b xmlns:p=\"urn:two\"><";
        XmlNamespaceDeclarations decl = XmlNamespaceDeclarations.scan(xml);
        assertEquals("p", decl.prefixFor("urn:two").orElseThrow());
    }

    @Test
    void singleQuotesAndCommentsAreHandled() {
        String xml = "<!-- xmlns:x=\"urn:comment\" --><a xmlns:q='urn:q'><";
        XmlNamespaceDeclarations decl = XmlNamespaceDeclarations.scan(xml);
        assertEquals("q", decl.prefixFor("urn:q").orElseThrow());
        assertTrue(decl.prefixFor("urn:comment").isEmpty());
    }

    @Test
    void qualifiesNamesWithInstancePrefixOrFallback() {
        XmlNamespaceDeclarations decl = XmlNamespaceDeclarations.scan(XML);
        assertEquals("c:street", decl.qualify("street", "http://schemas.example.org/common/1.0", "zz"));
        assertEquals("number", decl.qualify("number", "urn:example:invoice", "inv"));
        assertEquals("local", decl.qualify("local", null, "inv"));
        assertEquals("ds:Signature", decl.qualify("Signature", "urn:undeclared", "ds"));
        assertEquals("Signature", decl.qualify("Signature", "urn:undeclared", null));
    }
}
