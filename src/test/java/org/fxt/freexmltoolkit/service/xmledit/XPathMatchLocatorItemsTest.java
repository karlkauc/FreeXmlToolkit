package org.fxt.freexmltoolkit.service.xmledit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.junit.jupiter.api.Test;

/** Query results as hits: nodes located, atomic values and constructed nodes listed without a place. */
class XPathMatchLocatorItemsTest {

    private static final String XML = """
            <Positions>
              <Position id="a"><Name>A</Name><TotalPercentage>40</TotalPercentage></Position>
              <Position id="b"><Name>B</Name><TotalPercentage>20</TotalPercentage></Position>
              <Position id="c"><Name>C</Name><TotalPercentage>35</TotalPercentage></Position>
            </Positions>
            """;

    @Test
    void nodesAreLocatedWithTheirPath() throws Exception {
        var items = XPathMatchLocator.locateItems(XML, new XPathQuery("/Positions/Position[TotalPercentage > 30]", Map.of()));
        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(XPathMatchLocator.ResultItem::isLocated));
        var second = items.get(1).match();
        assertEquals(3, second.path().get(1).position(), "the third Position");
        assertEquals(4, second.line());
    }

    @Test
    void atomicValuesAreItemsWithoutAPlaceAndNoError() throws Exception {
        var items = XPathMatchLocator.locateItems(XML, new XPathQuery("sum(//TotalPercentage)", Map.of()));
        assertEquals(1, items.size());
        assertFalse(items.getFirst().isLocated());
        assertEquals("95", items.getFirst().value());

        var mixed = XPathMatchLocator.locateItems(XML, new XPathQuery("(//@id, count(//Position))", Map.of()));
        assertEquals(4, mixed.size());
        assertEquals(3, mixed.stream().filter(XPathMatchLocator.ResultItem::isLocated).count());
        assertEquals("id", mixed.getFirst().match().attrName());
    }

    @Test
    void xqueryLocatesDocumentNodesButNotConstructedOnes() throws Exception {
        var items = XPathMatchLocator.locateXQueryItems(XML,
                "for $p in //Position[TotalPercentage > 30] return ($p/Name, <hit>{string($p/@id)}</hit>)", Map.of());
        assertEquals(4, items.size());
        assertTrue(items.get(0).isLocated() && items.get(2).isLocated(), "the Name elements");
        assertFalse(items.get(1).isLocated(), "a constructed element");
        assertEquals("a", items.get(1).value());
    }
}
