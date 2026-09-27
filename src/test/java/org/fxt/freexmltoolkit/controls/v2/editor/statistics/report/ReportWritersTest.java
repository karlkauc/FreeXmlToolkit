package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.di.FxtTestModule;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWritersTest {

    @TempDir
    Path dir;

    @BeforeEach
    void registry() {
        new FxtTestModule().configure();
    }

    @AfterEach
    void reset() {
        ServiceRegistry.reset();
    }

    private static ReportModel model() {
        ReportTable refs = new ReportTable("schema-references", "Schema references",
                List.of("Type", "Location", "Status"),
                List.of(ReportRow.of("import", "http://example.com/very/long/path/to/a/schema/file/that/never/ends/common-types.xsd", "Resolved"),
                        ReportRow.withTone(Tone.ERROR, "include", "missing.xsd", "Failed: \"not found\", really")));
        ReportTable empty = new ReportTable("unused", "Unused types", List.of("Name"), List.of());
        ReportSection overview = new ReportSection("overview", "Overview",
                List.of(KeyValue.of("XSD version", "1.1"), KeyValue.of("Target namespace", "urn:test, with comma"),
                        KeyValue.of("Score", "42 / 100", Tone.WARNING)),
                List.of(refs, empty), "A note with <html> & \"quotes\"");
        ReportSection quality = new ReportSection("quality", "Quality Checks", List.of(),
                List.of(new ReportTable("issues", "Issues", List.of("Severity", "Message"),
                        List.of(ReportRow.withTone(Tone.INFO, "INFO", "Line one\nline two")))), null);
        return new ReportModel("Schema Analysis Report", "test.xsd (/tmp/test.xsd)",
                LocalDateTime.of(2026, 9, 27, 10, 30), List.of(overview, quality));
    }

    @Test
    void csvWritesBlocksPerSectionWithRfc4180Quoting() throws Exception {
        Path target = dir.resolve("r.csv");
        ReportWriters.write(model(), ReportFormat.CSV, target);
        String csv = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("# Schema Analysis Report\n# Subject: test.xsd (/tmp/test.xsd)\n# Generated: 2026-09-27 10:30:00\n"));
        assertTrue(csv.contains("\n## Overview\nKey,Value\nXSD version,1.1\nTarget namespace,\"urn:test, with comma\"\nScore,42 / 100\n"));
        assertTrue(csv.contains("\n### Schema references\nType,Location,Status\nimport,"));
        assertTrue(csv.contains("include,missing.xsd,\"Failed: \"\"not found\"\", really\"\n"), csv);
        assertTrue(csv.contains("\n### Unused types\nName\n"), "empty table still has its header");
        assertTrue(csv.contains("INFO,\"Line one\nline two\"\n"));
    }

    @Test
    void jsonIsMachineFriendly() throws Exception {
        Path target = dir.resolve("r.json");
        ReportWriters.write(model(), ReportFormat.JSON, target);
        JsonObject root = JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        assertEquals("Schema Analysis Report", root.get("title").getAsString());
        assertTrue(root.getAsJsonObject("_metadata").has("generator"));
        JsonObject overview = root.getAsJsonObject("sections").getAsJsonObject("overview");
        assertEquals("1.1", overview.getAsJsonObject("facts").get("XSD version").getAsString());
        assertEquals("WARNING", overview.getAsJsonObject("facts").getAsJsonObject("Score").get("tone").getAsString());
        JsonObject refs = overview.getAsJsonObject("tables").getAsJsonObject("schema-references");
        JsonArray rows = refs.getAsJsonArray("rows");
        assertEquals(2, rows.size());
        assertEquals("import", rows.get(0).getAsJsonObject().get("Type").getAsString());
        assertEquals("ERROR", rows.get(1).getAsJsonObject().get("_tone").getAsString());
        assertEquals(0, overview.getAsJsonObject("tables").getAsJsonObject("unused").getAsJsonArray("rows").size());
        assertTrue(overview.get("note").getAsString().contains("<html>"), "no HTML escaping in JSON");
    }

    @Test
    void htmlEscapesAndContainsEveryHeader() throws Exception {
        Path target = dir.resolve("r.html");
        ReportWriters.write(model(), ReportFormat.HTML, target);
        String html = Files.readString(target);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<a href=\"#quality\">Quality Checks</a>"), "table of contents");
        for (String header : List.of("Type", "Location", "Status", "Severity", "Message")) {
            assertTrue(html.contains("<th>" + header + "</th>"), header);
        }
        assertTrue(html.contains("A note with &lt;html&gt; &amp; &quot;quotes&quot;"));
        assertTrue(html.contains("<p class=\"empty\">(none)</p>"));
        assertTrue(html.contains("background:" + Tone.ERROR.background()));
        assertFalse(html.contains("<html> &"), "raw note must be escaped");
    }

    @Test
    void pdfRendersEvenWithEmptyTablesAndLongTokens() throws Exception {
        Path target = dir.resolve("r.pdf");
        ReportWriters.write(model(), ReportFormat.PDF, target);
        byte[] bytes = Files.readAllBytes(target);
        assertTrue(bytes.length > 500, "pdf size " + bytes.length);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        String fo = PdfReportWriter.toXslFo(model());
        assertFalse(fo.contains("<fo:table-body>\n        </fo:table-body>"), "no empty table body");
        assertTrue(fo.contains("(none)"));
        assertTrue(PdfReportWriter.breakable("http://example.com/very/long/path").contains("​"));
        assertEquals("short", PdfReportWriter.breakable("short"));
    }

    @Test
    void excelHasOneSheetPerSectionWithSafeUniqueNames() throws Exception {
        ReportSection a = new ReportSection("a", "Quality: Checks / Issues [long title over thirty-one chars]", List.of(KeyValue.of("k", "v")), List.of(), null);
        ReportSection b = new ReportSection("b", "Quality: Checks / Issues [long title over thirty-one chars]", List.of(), List.of(), null);
        ReportModel model = new ReportModel("T", "", LocalDateTime.now(), List.of(model().sections().get(0), a, b));
        Path target = dir.resolve("r.xlsx");
        ReportWriters.write(model, ReportFormat.XLSX, target);
        try (InputStream in = Files.newInputStream(target); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(3, workbook.getNumberOfSheets());
            Set<String> names = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                String name = workbook.getSheetName(i);
                assertTrue(name.length() <= 31, name);
                assertFalse(name.contains("/") || name.contains(":") || name.contains("["), name);
                assertTrue(names.add(name), "unique: " + name);
            }
            Sheet overview = workbook.getSheet("Overview");
            assertNotNull(overview);
            // title, generated (no subject), blank, 3 facts, blank, table title, header, 2 rows, blank, table title, header, (none)
            assertEquals("Schema references", overview.getRow(7).getCell(0).getStringCellValue());
            assertEquals("Type", overview.getRow(8).getCell(0).getStringCellValue());
            assertEquals("include", overview.getRow(10).getCell(0).getStringCellValue());
            assertEquals("(none)", overview.getRow(14).getCell(0).getStringCellValue());
            assertTrue(overview.getColumnWidth(1) > 8 * 256);
        }
    }

    @Test
    void tableRejectsRowsWithWrongWidthAndModelFiltersSections() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportTable("t", "T", List.of("A", "B"), List.of(ReportRow.of("only-one"))));
        ReportModel only = model().only("quality");
        assertEquals(1, only.sections().size());
        assertEquals("quality", only.sections().get(0).id());
    }
}
