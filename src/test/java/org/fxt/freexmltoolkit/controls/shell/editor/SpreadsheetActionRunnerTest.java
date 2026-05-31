package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SpreadsheetActionRunner#exportToCsv} (no UI): XML is written to a
 * non-empty CSV file containing the values.
 */
class SpreadsheetActionRunnerTest {

    @Test
    void exportsXmlToCsv(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("out.csv");
        String result = SpreadsheetActionRunner.exportToCsv(
                "<root><a>alpha</a><b>beta</b></root>", csv.toFile());

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(Files.exists(csv), "CSV must be created");
        String content = Files.readString(csv);
        assertTrue(content.contains("alpha") && content.contains("beta"), content);
    }

    @Test
    void invalidXmlReturnsError(@TempDir Path tmp) {
        assertTrue(SpreadsheetActionRunner.exportToCsv("<not-closed>", tmp.resolve("x.csv").toFile())
                .startsWith("ERROR:"));
    }

    @Test
    void exportsXmlToExcel(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("out.xlsx");
        String result = SpreadsheetActionRunner.exportToExcel(
                "<root><a>alpha</a><b>beta</b></root>", xlsx.toFile());

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(Files.exists(xlsx) && Files.size(xlsx) > 0, "xlsx must be created");
        byte[] header = new byte[2];
        try (var in = Files.newInputStream(xlsx)) {
            assertEquals(2, in.read(header));
        }
        assertEquals("PK", new String(header), "xlsx is a zip (PK header)");
    }

    @Test
    void excelRoundTripBackToXml(@TempDir Path tmp) {
        Path xlsx = tmp.resolve("rt.xlsx");
        assertTrue(SpreadsheetActionRunner.exportToExcel(
                "<root><a>alpha</a><b>beta</b></root>", xlsx.toFile()).startsWith("OK:"));

        String xml = SpreadsheetActionRunner.excelToXml(xlsx.toFile());
        assertFalse(xml.startsWith("ERROR:"), xml);
        assertTrue(xml.contains("alpha") && xml.contains("beta"), xml);
    }

    @Test
    void csvBackToXml(@TempDir Path tmp) {
        Path csv = tmp.resolve("rt.csv");
        assertTrue(SpreadsheetActionRunner.exportToCsv(
                "<root><a>alpha</a></root>", csv.toFile()).startsWith("OK:"));

        String xml = SpreadsheetActionRunner.csvToXml(csv.toFile());
        assertFalse(xml.startsWith("ERROR:"), xml);
        assertTrue(xml.contains("alpha"), xml);
    }

    @Test
    void importErrorsAreReported() {
        assertTrue(SpreadsheetActionRunner.excelToXml(new File("/no/such.xlsx")).startsWith("ERROR:"));
    }

    @Test
    void rejectsDoctypeToPreventXxe(@TempDir Path tmp) {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<root>&xxe;</root>";
        String result = SpreadsheetActionRunner.exportToCsv(xxe, tmp.resolve("x.csv").toFile());
        assertTrue(result.startsWith("ERROR:"), "DOCTYPE must be rejected (XXE hardening): " + result);
    }
}
