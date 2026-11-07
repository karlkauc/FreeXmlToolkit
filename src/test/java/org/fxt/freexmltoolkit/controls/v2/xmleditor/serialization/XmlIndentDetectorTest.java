package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XmlIndentDetectorTest {

    @Test
    void findsTheSmallestIndentOfMarkupLines() {
        assertEquals(2, XmlIndentDetector.detect("<?xml version=\"1.0\"?>\n<a>\n  <b>\n    <c/>\n  </b>\n</a>\n"));
        assertEquals(4, XmlIndentDetector.detect("<a>\n    <b/>\n</a>"));
        assertEquals(3, XmlIndentDetector.detect("<a>\r\n   <b/>\r\n</a>"));
    }

    @Test
    void ignoresIndentedTextContent() {
        assertEquals(2, XmlIndentDetector.detect("<a>\n  <b>\n text with one space\n  </b>\n</a>"));
    }

    @Test
    void unknownWithoutSpaceIndentedMarkup() {
        assertEquals(-1, XmlIndentDetector.detect("<a><b/></a>"));
        assertEquals(-1, XmlIndentDetector.detect("<a>\n\t<b/>\n</a>"));
        assertEquals(-1, XmlIndentDetector.detect(""));
        assertEquals(-1, XmlIndentDetector.detect(null));
    }
}
