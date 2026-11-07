package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.Test;

/** The parser shares repeated short strings and keeps the serialized text unchanged. */
class StreamingXmlParserMemoryTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <orders>
              <order id="1" status="open">
                <city>Vienna</city>
              </order>
              <order id="2" status="open">
                <city>Vienna</city>
              </order>
            </orders>
            """;

    @Test
    void repeatedValuesAndTextsAreOneInstance() throws Exception {
        XmlDocument doc = new StreamingXmlParser().parse(XML);
        List<XmlElement> orders = doc.getRootElement().getChildElements("order");
        assertSame(orders.get(0).getAttribute("status"), orders.get(1).getAttribute("status"));
        assertSame(text(orders.get(0)), text(orders.get(1)));
        assertSame(orders.get(0).getName(), orders.get(1).getName());
    }

    @Test
    void serializationIsUnchangedAndStableAcrossRuns() throws Exception {
        XmlDocument doc = new StreamingXmlParser().parse(XML);
        String first = doc.serialize(0, 2);
        assertEquals(XML, first + (first.endsWith("\n") ? "" : "\n"));
        assertEquals(first, doc.serialize(0, 2), "the sized builder of the second run gives the same text");
    }

    private static String text(XmlElement order) {
        return ((XmlText) order.getChildElement("city").getChildren().getFirst()).getText();
    }
}
