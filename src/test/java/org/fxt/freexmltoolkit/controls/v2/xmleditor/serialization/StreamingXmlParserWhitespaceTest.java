package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.junit.jupiter.api.Test;

class StreamingXmlParserWhitespaceTest {

    @Test
    void testStreamingXmlParserStripsWhitespace() {
        String xml = "<root>\n  <child/>\n</root>";
        StreamingXmlParser parser = new StreamingXmlParser();
        XmlDocument doc = parser.parse(xml);
        
        XmlElement root = doc.getRootElement();
        // If it strips whitespace, it should have only 1 child (the element)
        assertEquals(1, root.getChildCount(), "StreamingXmlParser should strip whitespace between elements");
    }
    
    @Test
    void testStreamingXmlParserPreservesMixedContentWhitespace() {
        String xml = "<root>Text <child/> more text</root>";
        StreamingXmlParser parser = new StreamingXmlParser();
        XmlDocument doc = parser.parse(xml);
        
        XmlElement root = doc.getRootElement();
        assertEquals(3, root.getChildCount(), "StreamingXmlParser should preserve whitespace in mixed content");
        assertEquals("Text ", ((XmlText)root.getChildren().get(0)).getText());
        assertEquals(" more text", ((XmlText)root.getChildren().get(2)).getText());
    }

    @Test
    void testStreamingXmlParserPreservesOnlyWhitespaceIfNoElementChildren() {
        String xml = "<root>   </root>";
        StreamingXmlParser parser = new StreamingXmlParser();
        XmlDocument doc = parser.parse(xml);
        
        XmlElement root = doc.getRootElement();
        assertEquals(1, root.getChildCount(), "StreamingXmlParser should preserve whitespace if it's the only content");
        assertEquals("   ", ((XmlText)root.getChildren().get(0)).getText());
    }
}
