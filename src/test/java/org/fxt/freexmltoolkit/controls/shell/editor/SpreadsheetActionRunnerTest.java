package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link SpreadsheetActionRunner} (no UI): XML &harr; CSV/Excel round trips, and
 * failures that keep their exception.
 */
class SpreadsheetActionRunnerTest {

    @Test
    void exportsXmlToCsv(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("out.csv");
        var result = SpreadsheetActionRunner.exportToCsv(
                "<root><a>alpha</a><b>beta</b></root>", csv.toFile());

        assertFalse(result.isError(), result.text());
        assertTrue(result.text().startsWith("OK:"), result.text());
        assertTrue(Files.exists(csv), "CSV must be created");
        String content = Files.readString(csv);
        assertTrue(content.contains("alpha") && content.contains("beta"), content);
    }

    @Test
    void invalidXmlReturnsErrorWithItsException(@TempDir Path tmp) {
        var result = SpreadsheetActionRunner.exportToCsv("<not-closed>", tmp.resolve("x.csv").toFile());
        assertTrue(result.isError());
        assertTrue(result.text().startsWith("ERROR:"), result.text());
        assertInstanceOf(org.xml.sax.SAXParseException.class, result.error(),
                "the exception itself is kept for the error dialog / error report");
    }

    @Test
    void exportsXmlToExcel(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("out.xlsx");
        var result = SpreadsheetActionRunner.exportToExcel(
                "<root><a>alpha</a><b>beta</b></root>", xlsx.toFile());

        assertTrue(result.text().startsWith("OK:"), result.text());
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
                "<root><a>alpha</a><b>beta</b></root>", xlsx.toFile()).text().startsWith("OK:"));

        var result = SpreadsheetActionRunner.excelToXml(xlsx.toFile());
        assertFalse(result.isError(), result.text());
        String xml = result.text();
        assertTrue(xml.contains("alpha") && xml.contains("beta"), xml);
    }

    @Test
    void csvBackToXml(@TempDir Path tmp) {
        Path csv = tmp.resolve("rt.csv");
        assertTrue(SpreadsheetActionRunner.exportToCsv(
                "<root><a>alpha</a></root>", csv.toFile()).text().startsWith("OK:"));

        var result = SpreadsheetActionRunner.csvToXml(csv.toFile());
        assertFalse(result.isError(), result.text());
        String xml = result.text();
        assertTrue(xml.contains("alpha"), xml);
    }

    @Test
    void importErrorsAreReported() {
        var result = SpreadsheetActionRunner.excelToXml(new File("/no/such.xlsx"));
        assertTrue(result.isError());
        assertTrue(result.text().startsWith("ERROR:"), result.text());
        assertNotNull(result.error());
    }

    @Test
    void rejectsDoctypeToPreventXxe(@TempDir Path tmp) {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<root>&xxe;</root>";
        var result = SpreadsheetActionRunner.exportToCsv(xxe, tmp.resolve("x.csv").toFile());
        assertTrue(result.isError(), "DOCTYPE must be rejected (XXE hardening): " + result.text());
    }

    @Test
    void telemetryStatusSeparatesInputAndLimitsFromFailures(@TempDir Path tmp) {
        var ok = SpreadsheetActionRunner.exportToCsv("<a>1</a>", tmp.resolve("ok.csv").toFile());
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.OK, ok.telemetryStatus());

        var malformed = SpreadsheetActionRunner.exportToCsv("<a>", tmp.resolve("bad.csv").toFile());
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT,
                malformed.telemetryStatus());

        var tooManyRows = new SpreadsheetActionRunner.Result("ERROR",
                new org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowLimitExceededException(2_000_000, 1_048_576));
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.UNSUPPORTED,
                tooManyRows.telemetryStatus());

        var outOfMemory = new SpreadsheetActionRunner.Result("ERROR",
                new SpreadsheetActionRunner.InsufficientMemoryException(4L << 30, new OutOfMemoryError()));
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.UNSUPPORTED,
                outOfMemory.telemetryStatus());
        assertTrue(outOfMemory.error().getMessage().contains("4.0 GB"), outOfMemory.error().getMessage());

        var unwritable = new SpreadsheetActionRunner.Result("ERROR", new java.io.IOException("locked"));
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.ERROR, unwritable.telemetryStatus());
    }
}
