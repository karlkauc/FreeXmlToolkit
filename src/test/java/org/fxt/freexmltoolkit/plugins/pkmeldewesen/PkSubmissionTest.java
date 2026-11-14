package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The FMA file name, running numbers, the cleaned file and the ZIP of a submission. */
class PkSubmissionTest {

    private static PkReport report(String root, String attributes) {
        return PkReport.detect("<" + root + " " + attributes + "><x/></" + root + ">");
    }

    private static final PkReport Q1 = report("Nationale_Meldung_pfq",
            "PK-Nummer=\"9\" GJ=\"2026\" Stichtag=\"Q1\" Typ_der_Meldung=\"Testmeldung\"");

    @Test
    void referenceDatesOfTheModules() {
        assertEquals("20260331", PkSubmission.referenceDate(Q1));
        assertEquals("20251231", PkSubmission.referenceDate(report("Nationale_Meldung_pfa", "GJ=\"2025\" Stichtag=\"J\"")));
        assertEquals("20250930", PkSubmission.referenceDate(report("EZB_Meldung_pfe", "GJ=\"2025\" Stichtag=\"q3\"")));
        assertNull(PkSubmission.referenceDate(report("Nationale_Meldung_pfq", "GJ=\"2025\" Stichtag=\"X\"")));
        assertNull(PkSubmission.referenceDate(report("Nationale_Meldung_pfq", "Stichtag=\"Q1\"")));
    }

    @Test
    void fileNameFollowsTheConvention() {
        assertEquals("test", PkSubmission.proposedSuffix(Q1));
        assertEquals("09.pfq.20260331.00001.test.xml", PkSubmission.fileName(Q1, 1, "test"));
        assertEquals("09.pfq.20260331.00012.xml", PkSubmission.fileName(Q1, 12, ""));
        assertEquals("09.pfq.20260331.00002.korr.xml", PkSubmission.fileName(Q1, 2, " KORR "));
        assertNull(PkSubmission.fileName(Q1, 0, ""));
        assertNull(PkSubmission.fileName(report("Nationale_Meldung_pfq", "GJ=\"2026\" Stichtag=\"Q1\""), 1, ""));
        assertTrue(PkSubmission.validSuffix(""));
        assertFalse(PkSubmission.validSuffix("te st"));
        assertFalse(PkSubmission.validSuffix("v2"));
    }

    @Test
    void nameCheckComparesWithTheReport() {
        assertTrue(PkSubmission.checkName("09.pfq.20260331.00001.test.xml", Q1).isEmpty());
        assertTrue(PkSubmission.checkName("9.pfq.20260331.00003.zip", Q1).isEmpty());
        assertEquals(1, PkSubmission.checkName("10.pfq.20260331.00001.xml", Q1).size());
        assertEquals(2, PkSubmission.checkName("09.pfa.20260630.00001.xml", Q1).size());
        assertEquals(1, PkSubmission.checkName("09.PFQ.20260331.00001.xml", Q1).size(), "upper case");
        assertFalse(PkSubmission.checkName("report.xml", Q1).isEmpty());
    }

    @Test
    void nextSequenceLooksAtXmlAndZipFilesOfTheSameReport(@TempDir Path dir) throws IOException {
        assertEquals(1, PkSubmission.nextSequence(dir, Q1));
        Files.writeString(dir.resolve("09.pfq.20260331.00001.test.zip"), "");
        Files.writeString(dir.resolve("09.pfq.20260331.00004.xml"), "");
        Files.writeString(dir.resolve("09.pfq.20251231.00009.xml"), "");
        Files.writeString(dir.resolve("11.pfq.20260331.00020.xml"), "");
        Files.writeString(dir.resolve("09.pfa.20260331.00030.xml"), "");
        assertEquals(5, PkSubmission.nextSequence(dir, Q1));
        assertEquals(1, PkSubmission.nextSequence(dir.resolve("missing"), Q1));
    }

    @Test
    void cleanRemovesGridFormulasAndDeclaresUtf8() throws IOException {
        String xml = "<?xml version='1.0' encoding='ISO-8859-1'?>\n<Nationale_Meldung_pfq>"
                + "<?fxt-formula name=\"n\" store=\"false\" expression=\"1\"?><a>ä</a></Nationale_Meldung_pfq>";
        String clean = PkSubmission.clean(xml);
        assertTrue(clean.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), clean);
        assertFalse(clean.contains("fxt-formula"));
        assertTrue(clean.contains("<a>ä</a>"));
        assertEquals("<?xml version='1.0' encoding=\"UTF-8\"?><r/>", PkSubmission.clean("<?xml version='1.0' encoding='windows-1252'?><r/>"));
        assertTrue(PkSubmission.clean("<r/>").startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<r/>"));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><r/>", PkSubmission.clean("﻿<?xml version=\"1.0\"?><r/>"));
    }

    @Test
    void zipHoldsTheXmlInUtf8AndIsNotOverwritten(@TempDir Path dir) throws IOException {
        Path zip = PkSubmission.writeZip("<?xml version=\"1.0\" encoding=\"UTF-8\"?><r>ä</r>", dir, "09.pfq.20260331.00001.xml");
        assertEquals("09.pfq.20260331.00001.zip", zip.getFileName().toString());
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals("09.pfq.20260331.00001.xml", entry.getName());
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><r>ä</r>", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertNull(in.getNextEntry());
        }
        assertThrows(IOException.class, () -> PkSubmission.writeZip("<r/>", dir, "09.pfq.20260331.00001.xml"));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "no temporary file is left behind");
        }
    }
}
