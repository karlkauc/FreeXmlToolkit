package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the "wrong Valid Children / wrong Type" bug: two distinct
 * elements that share a local name (FundsXML has {@code Account} under both
 * {@code AssetDetails} and {@code Position}) must be told apart by their FULL XPath.
 *
 * <p>The schema map is keyed by predicate-free full XPaths; an instance XPath may
 * carry positional predicates ({@code [n]}) for repeated siblings. The lookup must
 * strip those predicates and match the full path — never collapse to a name-only
 * match that returns the first same-named declaration.
 */
class XsdSchemaAdapterMatchingTest {

    private static final String ASSET_ACCOUNT = "/FundsXML4/AssetMasterData/Asset/AssetDetails/Account";
    private static final String POSITION_ACCOUNT =
            "/FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position/Account";

    private static XsdSchemaAdapter adapter() {
        Map<String, XsdExtendedElement> map = new HashMap<>();
        map.put(ASSET_ACCOUNT, element("Account", "AssetAccountType", ASSET_ACCOUNT));
        map.put(POSITION_ACCOUNT, element("Account", "PositionAccountType", POSITION_ACCOUNT));

        XsdDocumentationData data = new XsdDocumentationData();
        data.setExtendedXsdElementMap(map);

        XsdSchemaAdapter adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(data);
        return adapter;
    }

    private static XsdExtendedElement element(String name, String type, String xpath) {
        XsdExtendedElement e = new XsdExtendedElement();
        e.setElementName(name);
        e.setElementType(type);
        e.setCurrentXpath(xpath);
        return e;
    }

    @Test
    void fullPathDisambiguatesSameNamedElements() {
        XsdSchemaAdapter a = adapter();
        assertEquals("AssetAccountType",
                a.getElementTypeInfo(ASSET_ACCOUNT).orElseThrow().typeName());
        assertEquals("PositionAccountType",
                a.getElementTypeInfo(POSITION_ACCOUNT).orElseThrow().typeName());
    }

    @Test
    void positionalPredicatesAreStrippedBeforeMatching() {
        XsdSchemaAdapter a = adapter();
        assertEquals("AssetAccountType",
                a.getElementTypeInfo("/FundsXML4/AssetMasterData/Asset[2]/AssetDetails/Account")
                        .orElseThrow().typeName());
        assertEquals("PositionAccountType",
                a.getElementTypeInfo(
                        "/FundsXML4/Funds/Fund[1]/FundDynamicData/Portfolios/Portfolio[3]/Positions/Position[5]/Account")
                        .orElseThrow().typeName());
    }
}
