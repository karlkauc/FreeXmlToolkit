package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The FundsXML data-quality checks as a document validator: each finding keeps its check's severity. */
@DisplayName("FundsXML data-quality validator")
class FundsXmlDataQualityValidatorTest {

    @Test
    @DisplayName("findings become problems with the severity of their check")
    void severityOfTheCheck() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/fundsxml/dq-fixture.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        xml = xml.replace("<Identifiers><LEI>529900T8BM49AURSDO55</LEI></Identifiers>", "")       // hint
                .replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2999-01-01</ContentDate>") // warning
                .replace("</AssetMasterData>", "<Asset><UniqueID>P1</UniqueID><Currency>EUR</Currency>"
                        + "<Name>APPLE INC.</Name><AssetType>EQ</AssetType></Asset></AssetMasterData>");   // error
        var problems = new FundsXmlPlugin.DataQualityValidator().validate(xml, "fund.xml");
        assertTrue(problems.stream().anyMatch(p -> p.severity().equals("info")
                && p.message().startsWith("Funds have a LEI, share classes an ISIN: Fund Test Fund has no LEI")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.severity().equals("warning")
                && p.message().startsWith("Content date not in the future")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.severity().equals("error")
                && p.message().equals("No duplicate assets: Asset P1 appears 2 times in AssetMasterData") && p.line() > 0),
                problems.toString());
    }
}
