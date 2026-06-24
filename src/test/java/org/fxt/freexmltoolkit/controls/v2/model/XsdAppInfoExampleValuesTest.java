package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code altova:exampleValues} support in {@link XsdAppInfo}.
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
    void rawXml_carriesInlineAltovaNamespace() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setExampleValues(List.of("A"));
        XsdAppInfo.AppInfoEntry entry = appInfo.getEntries().stream()
                .filter(XsdAppInfo::isExampleValuesEntry).findFirst().orElseThrow();
        assertTrue(entry.getRawXml().contains("xmlns:altova=\"" + XsdAppInfo.ALTOVA_NS + "\""));
        assertTrue(entry.getRawXml().contains("<altova:example value=\"A\"/>"));
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
