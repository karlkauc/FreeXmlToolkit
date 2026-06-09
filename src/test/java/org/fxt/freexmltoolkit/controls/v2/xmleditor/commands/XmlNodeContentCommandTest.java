package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;

/** Pure tests for the comment / CDATA / processing-instruction content commands. */
class XmlNodeContentCommandTest {

    @Test
    void commentTextRoundTrips() {
        XmlComment comment = new XmlComment("old");
        SetCommentTextCommand cmd = new SetCommentTextCommand(comment, "new");
        assertTrue(cmd.execute());
        assertEquals("new", comment.getText());
        assertTrue(cmd.undo());
        assertEquals("old", comment.getText());
    }

    @Test
    void cdataTextRoundTrips() {
        XmlCData cdata = new XmlCData("a < b");
        SetCDataTextCommand cmd = new SetCDataTextCommand(cdata, "c > d");
        assertTrue(cmd.execute());
        assertEquals("c > d", cdata.getText());
        assertTrue(cmd.undo());
        assertEquals("a < b", cdata.getText());
    }

    @Test
    void processingInstructionRoundTrips() {
        XmlProcessingInstruction pi = new XmlProcessingInstruction("xml-stylesheet", "type=\"text/xsl\"");
        SetProcessingInstructionCommand cmd =
                new SetProcessingInstructionCommand(pi, "xml-stylesheet", "href=\"s.xsl\"");
        assertTrue(cmd.execute());
        assertEquals("xml-stylesheet", pi.getTarget());
        assertEquals("href=\"s.xsl\"", pi.getData());
        assertTrue(cmd.undo());
        assertEquals("type=\"text/xsl\"", pi.getData());
    }

    @Test
    void xmlDeclarationRoundTrips() {
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc =
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument();
        String oldEncoding = doc.getEncoding();
        SetXmlDeclarationCommand cmd = new SetXmlDeclarationCommand(doc, "1.1", "ISO-8859-1", Boolean.TRUE);
        assertTrue(cmd.execute());
        assertEquals("1.1", doc.getVersion());
        assertEquals("ISO-8859-1", doc.getEncoding());
        assertEquals(Boolean.TRUE, doc.getStandalone());
        assertTrue(cmd.undo());
        assertEquals("1.0", doc.getVersion());
        assertEquals(oldEncoding, doc.getEncoding());
        assertNull(doc.getStandalone());
    }
}
