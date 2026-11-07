package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser;
import org.junit.jupiter.api.Test;

/** The lines recorded while serializing are the lines the parser reports for that text. */
class LineRecorderTest {

    private static List<Integer> lines(XmlElement element, List<Integer> out) {
        out.add(element.getSourceLineNumber());
        for (XmlElement child : element.getChildElements()) {
            lines(child, out);
        }
        return out;
    }

    private static void clear(XmlElement element) {
        element.setSourceLineNumber(-1);
        element.getChildElements().forEach(LineRecorderTest::clear);
    }

    @Test
    void recordedLinesMatchTheParserForTheSerializedText() throws Exception {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                <orders xmlns:x="urn:x">
                  <!-- a comment
                       over two lines -->
                  <order id="1"><note>one
                two</note><empty/></order>
                  <?fxt-formula name="T" expression="count(order)"?>
                  <x:order id="2">
                    <customer><name>A</name><city>B</city></customer>
                  </x:order>
                  <mixed>text <b>bold</b> tail</mixed>
                </orders>
                """;
        XmlDocument model = new StreamingXmlParser().parse(source);
        clear(model.getRootElement());
        LineRecorder recorder = new LineRecorder();
        String text = model.serialize(2, recorder);
        assertEquals(model.serialize(0, 2), text, "recording changes nothing in the text");
        recorder.apply();

        // What a parse of exactly that text reports
        List<Integer> expected = lines(new StreamingXmlParser().parse(text).getRootElement(), new ArrayList<>());
        assertEquals(expected, lines(model.getRootElement(), new ArrayList<>()));
        assertEquals(expected.size(), recorder.size());
    }
}
