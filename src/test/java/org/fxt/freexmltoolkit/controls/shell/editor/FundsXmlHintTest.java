package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The FundsXML hint recognises a document by its root element (UX plan 5.5). */
class FundsXmlHintTest {

    @Test
    void recognisesFundsXmlRootsBehindThePrologAndComments() {
        assertTrue(FundsXmlHint.isFundsXml("<?xml version=\"1.0\"?>\n<!-- uses <tags> -->\n<FundsXML4 a=\"1\">"));
        assertTrue(FundsXmlHint.isFundsXml("<fx:FundsXML4 xmlns:fx=\"urn:x\"/>"));
        assertTrue(FundsXmlHint.isFundsXml("<!DOCTYPE x>\n<FundsXML>"));
    }

    @Test
    void ignoresOtherDocuments() {
        assertFalse(FundsXmlHint.isFundsXml("<order><FundsXML4/></order>"), "only the root element counts");
        assertFalse(FundsXmlHint.isFundsXml("<!-- FundsXML4 example -->\n<order/>"));
        assertFalse(FundsXmlHint.isFundsXml("{\"FundsXML4\": 1}"));
        assertFalse(FundsXmlHint.isFundsXml(""));
        assertFalse(FundsXmlHint.isFundsXml(null));
        assertFalse(FundsXmlHint.isFundsXml("<!-- never closed"));
    }

    @Test
    void readsTheRootElementName() {
        assertEquals("order", FundsXmlHint.rootElementName("<?xml version=\"1.0\"?><order id=\"1\">"));
        assertEquals("a:b", FundsXmlHint.rootElementName("<a:b/>"));
    }
}
