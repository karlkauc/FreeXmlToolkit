package org.fxt.freexmltoolkit.service.standards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The XPath version a query runs in decides which syntax and functions exist. */
class XPathVersionsTest {

    private static final String XML = "<r><a>1</a><a>2</a><b x='y'>t</b></r>";

    private static String eval(String xpath, XPathVersion version) {
        return XmlServiceImpl.getInstance().getXmlFromXpath(XML, xpath, version).strip();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " @ ", quoteCharacter = '`', value = {
            "head(//a) @ V2_0",
            "let $x := 1 return $x @ V2_0",
            "(1,2) ! (. + 1) @ V2_0",
            "'a' || 'b' @ V2_0",
            "string-join(('a','b')) @ V2_0",
            "(1) => count() @ V3_0",
            "map{1:2} @ V3_0",
            "parse-json('{}') @ V3_0",
            "for $i in 1 to 3 return $i @ V1_0",
            "if (1) then 2 else 3 @ V1_0",
            "upper-case('a') @ V1_0"})
    void newerConstructsFailInOlderVersions(String xpath, XPathVersion version) {
        assertThrows(RuntimeException.class, () -> eval(xpath, version), xpath + " in " + version);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " @ ", quoteCharacter = '`', value = {
            "count(//a) @ V1_0 @ 2",
            "1 + '2' @ V1_0 @ 3",
            "string(//b/@x) @ V1_0 @ y",
            "string-join(for $i in //a return string($i), ',') @ V2_0 @ 1,2",
            "upper-case(//b) @ V2_0 @ T",
            "head(//a)/string() @ V3_0 @ 1",
            "(1, 2) => count() @ V3_1 @ 2"})
    void constructsOfTheVersionWork(String xpath, XPathVersion version, String expected) {
        assertEquals(expected, eval(xpath, version));
    }

    @Test
    void xpath10ReturnsNodesSerialized() {
        String result = eval("//b", XPathVersion.V1_0);
        assertTrue(result.contains("<b x=\"y\">t</b>"), result);
    }

    @Test
    void locatorRefusesXpath2InXpath10() {
        assertThrows(XPathMatchLocator.XPathEditException.class, () -> XPathMatchLocator.locateItems(XML,
                new XPathQuery("for $i in //a return $i", Map.of(), XPathVersion.V1_0)));
    }

    @Test
    void locatorFindsNodesInXpath10() throws Exception {
        var items = XPathMatchLocator.locateItems(XML, new XPathQuery("//a", Map.of(), XPathVersion.V1_0));
        assertEquals(2, items.size());
        assertTrue(items.getFirst().isLocated());
    }

    @Test
    void parseAcceptsCliValuesAndNames() {
        assertEquals(XPathVersion.V2_0, XPathVersion.parse("2.0"));
        assertEquals(XPathVersion.V2_0, XPathVersion.parse("v2_0"));
        assertEquals(XPathVersion.V3_1, XPathVersion.parse("bogus"));
        assertEquals(null, StandardVersion.parseStrict(XPathVersion.class, ""));
    }
}
