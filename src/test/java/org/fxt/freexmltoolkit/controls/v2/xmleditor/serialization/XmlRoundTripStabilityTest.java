package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parsing a text and serializing the model gives the same text: a grid edit writes the whole
 * document back, so anything that changes on the way (spaces in comments, line breaks in mixed
 * content) changed untouched parts of the file on every edit.
 */
class XmlRoundTripStabilityTest {

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static String roundTrip(String xml) {
        XmlDocument doc = new StreamingXmlParser().parse(xml);
        return doc.serialize(0, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<root>\n  <!-- a comment -->\n  <child/>\n</root>\n",
            "<root>\n  <!--tight-->\n  <child/>\n</root>\n",
            "<root>\n  <!-- a comment\n       over two lines -->\n  <child/>\n</root>\n",
            "<root>\n  <!---->\n  <child/>\n</root>\n",
            "<p>Text <b>bold</b> and <i>it</i> end</p>\n",
            "<p><b>x</b> <i>y</i> end</p>\n",
            "<doc>\n  <p>Some <em>very <b>nested</b></em> text.</p>\n  <q>plain</q>\n</doc>\n",
            "<root>\n  <a>1</a>\n  <b>\n    <c>2</c>\n  </b>\n</root>\n",
    })
    void parseThenSerializeGivesTheSameText(String body) {
        String xml = HEAD + body;
        assertEquals(xml, roundTrip(xml));
        assertEquals(xml, roundTrip(roundTrip(xml)), "stable over several write-backs");
    }

    @Test
    void aCommentAddedInTheEditorIsWrittenWithSpaces() {
        XmlElement root = new XmlElement("root");
        root.addChild(new XmlComment("note"));
        assertEquals("<root>\n  <!-- note -->\n</root>", root.serialize(0, 2));
        assertEquals("note", ((XmlComment) new StreamingXmlParser().parse(HEAD + "<r><!-- note --></r>")
                .getRootElement().getChildren().getFirst()).getText(), "the text without the spaces around it");
    }
}
