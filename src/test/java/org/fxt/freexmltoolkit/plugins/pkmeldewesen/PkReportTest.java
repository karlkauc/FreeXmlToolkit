package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Tests {@link PkReport}: recognising PK reports by their root element. */
class PkReportTest {

    private static String sample() throws Exception {
        try (var in = PkReportTest.class.getResourceAsStream("/pkmeldewesen/synthetic-pfq.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void quarterlyReportIsRecognisedWithItsAttributes() throws Exception {
        PkReport report = PkReport.detect(sample());
        assertNotNull(report);
        assertEquals(PkModule.PFQ, report.module());
        assertEquals("99", report.pkNumber());
        assertEquals("2026", report.year());
        assertEquals("Q1", report.period());
        assertEquals("2.9.0", report.taxonomy());
        assertEquals("Testmeldung", report.reportType());
        assertEquals("PK-QMV Q1/2026", report.label());
    }

    @Test
    void annualAndEcbReports() {
        PkReport annual = PkReport.detect("<Nationale_Meldung_pfa GJ='2025' Stichtag='J' PK-Nummer=\"11\"><x/></Nationale_Meldung_pfa>");
        assertEquals(PkModule.PFA, annual.module());
        assertEquals("PK-FJMV 2025", annual.label());
        PkReport ecb = PkReport.detect("<!-- c --><?pi x?>\n<EZB_Meldung_pfe GJ=\"2025\" Stichtag=\"Q4\"/>");
        assertEquals(PkModule.PFE, ecb.module());
        assertEquals("PK-EZB Q4/2025", ecb.label());
    }

    @Test
    void otherDocumentsAreNoReports() {
        assertNull(PkReport.detect("<FundsXML4/>"));
        assertNull(PkReport.detect("<Nationale_Meldung_xyz/>"));
        assertNull(PkReport.detect(""));
        assertNull(PkReport.detect(null));
        assertNull(PkReport.detect("not xml"));
    }

    @Test
    void modulesByCodeAndRoot() {
        assertEquals(PkModule.PFA, PkModule.ofCode("PFA"));
        assertEquals(PkModule.PFE, PkModule.ofRootElement("EZB_Meldung_pfe"));
        assertNull(PkModule.ofCode("xyz"));
    }
}
