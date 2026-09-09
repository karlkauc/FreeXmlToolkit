package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the example-values support in {@link XsdAppInfo}: values are written as
 * {@code fxt:exampleValues}, while the legacy {@code altova:exampleValues} form is still read.
 */
class XsdAppInfoExampleValuesTest {

    @Test
    void setAndGetExampleValues_roundTrip() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setExampleValues(List.of("WBAH", "XLON"));
        assertTrue(appInfo.hasExampleValues());
        assertEquals(List.of("WBAH", "XLON"), appInfo.getExampleValues());
    }

    @Test
    void rawXml_carriesInlineFxtNamespace() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setExampleValues(List.of("A"));
        XsdAppInfo.AppInfoEntry entry = appInfo.getEntries().stream()
                .filter(XsdAppInfo::isExampleValuesEntry).findFirst().orElseThrow();
        assertTrue(entry.getRawXml().contains("xmlns:fxt=\"" + XsdAppInfo.FXT_EXT_NS + "\""));
        assertTrue(entry.getRawXml().contains("<fxt:example value=\"A\"/>"));
        assertFalse(entry.getRawXml().contains("altova"), "new blocks no longer use the Altova namespace");
    }

    @Test
    void legacyAltovaBlock_isStillRecognisedAndRead() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.addEntry(null, "", "<altova:exampleValues xmlns:altova=\"" + XsdAppInfo.ALTOVA_NS + "\">"
                + "<altova:example value=\"WBAH\"/><altova:example value=\"XLON\"/>"
                + "</altova:exampleValues>");

        assertTrue(appInfo.hasExampleValues());
        assertEquals(List.of("WBAH", "XLON"), appInfo.getExampleValues());

        // Untouched, a legacy block round-trips verbatim - it is only rewritten once the values
        // are replaced.
        assertTrue(appInfo.toXmlStrings().getFirst().contains("altova:exampleValues"));
    }

    @Test
    void replacingALegacyBlock_writesTheFxtNamespace() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.addEntry(null, "", "<altova:exampleValues xmlns:altova=\"" + XsdAppInfo.ALTOVA_NS + "\">"
                + "<altova:example value=\"WBAH\"/></altova:exampleValues>");

        appInfo.setExampleValues(List.of("WBAH", "XLON"));

        assertEquals(1, appInfo.getEntries().stream().filter(XsdAppInfo::isExampleValuesEntry).count());
        String xml = appInfo.toXmlStrings().getFirst();
        assertTrue(xml.contains("<fxt:exampleValues"), xml);
        assertFalse(xml.contains("altova"), xml);
    }

    @Test
    void setExampleValues_replacesPreviousBlockAndPreservesOtherTags() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setSince("4.0.0");
        appInfo.setExampleValues(List.of("X"));
        appInfo.setExampleValues(List.of("Y", "Z")); // replace, not append

        assertEquals(List.of("Y", "Z"), appInfo.getExampleValues());
        assertEquals(1, appInfo.getEntries().stream().filter(XsdAppInfo::isExampleValuesEntry).count());
        assertEquals("4.0.0", appInfo.getSince(), "@since must survive example-value edits");
    }

    @Test
    void emptyOrNull_clearsExampleValues() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setExampleValues(List.of("X"));
        appInfo.setExampleValues(List.of());
        assertFalse(appInfo.hasExampleValues());
        appInfo.setExampleValues(List.of("Y"));
        appInfo.setExampleValues(null);
        assertFalse(appInfo.hasExampleValues());
    }

    @Test
    void values_areXmlEscapedAndUnescapedRoundTrip() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setExampleValues(List.of("a<b>&\"c\""));
        assertEquals(List.of("a<b>&\"c\""), appInfo.getExampleValues());
    }
}
