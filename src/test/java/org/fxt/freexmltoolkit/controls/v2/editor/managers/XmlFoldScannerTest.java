package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** The foldable regions of an XML text, found by one character scan. */
class XmlFoldScannerTest {

    private static Map<Integer, FoldingRegion> scan(String text) {
        return XmlFoldScanner.scan(text, 2);
    }

    @Test
    void aStartTagOverSeveralLinesIsFoldableFromItsFirstLine() {
        String xml = """
                <?xml version="1.0"?>
                <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:noNamespaceSchemaLocation="https://example.org/FundsXML4.xsd">
                    <ControlData>
                        <UniqueDocumentID>1</UniqueDocumentID>
                    </ControlData>
                </FundsXML4>""";
        var regions = scan(xml);
        assertEquals(new FoldingRegion(1, 6, "FundsXML4", 0), regions.get(1), "the root, from the line of its '<'");
        assertEquals(new FoldingRegion(3, 5, "ControlData", 1), regions.get(3));
        assertEquals(2, regions.size());
    }

    @Test
    void tagsInsideCommentsCdataAndProcessingInstructionsDoNotCount() {
        String xml = """
                <root>
                  <!-- <ghost>
                       </ghost> -->
                  <data><![CDATA[
                    <fake>
                    </fake>
                  ]]></data>
                  <?pi <notatag> ?>
                </root>""";
        var regions = scan(xml);
        assertTrue(regions.containsKey(0), "root");
        assertTrue(regions.containsKey(3), "data (its content spans lines)");
        assertFalse(regions.values().stream().anyMatch(r -> r.elementName().equals("ghost")
                || r.elementName().equals("fake") || r.elementName().equals("notatag")));
    }

    @Test
    void greaterThanAndSlashInAttributeValuesDoNotEndTheTag() {
        String xml = """
                <root>
                  <a expr="x > 1" path="a/>b"
                     other='q'>
                    <b/>
                  </a>
                </root>""";
        var regions = scan(xml);
        assertEquals(new FoldingRegion(1, 4, "a", 0), regions.get(1));
    }

    @Test
    void aSelfClosingElementOpensNoRegionAndDoesNotHideItsNeighbours() {
        String xml = """
                <root><empty/>
                  <list attr="1"
                        other="2"/>
                  <item>
                    <x/>
                  </item>
                </root>""";
        var regions = scan(xml);
        assertTrue(regions.containsKey(0), "a line with a self-closing tag still opens root");
        assertFalse(regions.containsKey(1), "the multi-line self-closing list opens nothing");
        assertEquals(new FoldingRegion(3, 5, "item", 0), regions.get(3));
    }

    @Test
    void nestedSameNamesAndTheDoctypeAreHandled() {
        String xml = """
                <!DOCTYPE root [
                  <!ELEMENT root ANY>
                ]>
                <root>
                  <node>
                    <node>
                      <leaf/>
                    </node>
                  </node>
                </root>""";
        var regions = scan(xml);
        assertEquals(new FoldingRegion(3, 9, "root", 0), regions.get(3));
        assertEquals(new FoldingRegion(4, 8, "node", 0), regions.get(4));
        assertEquals(new FoldingRegion(5, 7, "node", 1), regions.get(5));
        assertFalse(regions.containsKey(0), "the DOCTYPE is no element");
    }

    @Test
    void anUnfinishedDocumentStillFoldsAndAStrayEndTagIsIgnored() {
        String xml = """
                <root>
                  </stray>
                  <open>
                    <a attr="unfinished
                    <b>
                      text
                </root>""";
        var regions = scan(xml);
        assertEquals(new FoldingRegion(0, 6, "root", 0), regions.get(0), "root still closes");
        assertFalse(regions.containsKey(2), "open, left open inside root, ends without a region");
        assertFalse(regions.containsKey(1));
    }

    @Test
    void elementsStillOpenAtTheEndRunToTheLastLine() {
        String xml = "<root>\n  <a>\n    text\n";
        var regions = scan(xml);
        assertEquals("root", regions.get(0).elementName());
        assertEquals(3, regions.get(0).endLine());
    }
}
