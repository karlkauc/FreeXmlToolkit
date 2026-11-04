package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

/** CSV → XML import of a file saved by a spreadsheet application, repaired with the schema types. */
class CsvTypedImportTest {

    /** What German Excel makes of an exported CSV: decimal commas, dotted dates, TRUE, short times. */
    private static final String EUROPEAN_CSV = """
            XPath;Value;Type
            /order;;element
            /order/@paid;WAHR;attribute
            /order/amount;1.234,50;element
            /order/qty;007;element
            /order/day;31.01.2024;element
            /order/stamp;31.01.2024 10:15;element
            /order/at;9:05;element
            /order/note;"10,50; 31.01.2024";element
            """;

    private static final Map<String, String> TYPES = Map.of(
            "/order@paid", "boolean", "/order/amount", "decimal", "/order/qty", "int",
            "/order/day", "date", "/order/stamp", "dateTime", "/order/at", "time", "/order/note", "string");

    private static final SpreadsheetTypeResolver RESOLVER =
            (xpath, attribute) -> TYPES.get(xpath + (attribute == null ? "" : "@" + attribute));

    @TempDir
    Path tempDir;

    @Test
    void valuesAreRepairedForTheirTypes() throws Exception {
        Document doc = read(EUROPEAN_CSV, CsvHandler.CsvConfig.semicolon(), RESOLVER);
        assertEquals("true", doc.getDocumentElement().getAttribute("paid"));
        assertEquals("1234.50", text(doc, "amount"));
        assertEquals("007", text(doc, "qty"), "a valid value stays as written");
        assertEquals("2024-01-31", text(doc, "day"));
        assertEquals("2024-01-31T10:15:00", text(doc, "stamp"));
        assertEquals("09:05:00", text(doc, "at"));
        assertEquals("10,50; 31.01.2024", text(doc, "note"), "text is not touched");
    }

    @Test
    void withoutSchemaTheValuesStayAsWritten() throws Exception {
        Document doc = read(EUROPEAN_CSV, CsvHandler.CsvConfig.semicolon(), SpreadsheetTypeResolver.NONE);
        assertEquals("1.234,50", text(doc, "amount"));
        assertEquals("31.01.2024", text(doc, "day"));
    }

    @Test
    void aCommaFileReadsUsDates() throws Exception {
        String csv = """
                XPath,Value,Type
                /order,,element
                /order/day,01/02/2024,element
                /order/amount,"1,234",element
                """;
        Document doc = read(csv, CsvHandler.CsvConfig.comma(), RESOLVER);
        assertEquals("2024-01-02", text(doc, "day"));
        assertEquals("1234", text(doc, "amount"));
    }

    private Document read(String csv, CsvHandler.CsvConfig config, SpreadsheetTypeResolver types) throws Exception {
        Path file = Files.writeString(tempDir.resolve("in.csv"), csv, StandardCharsets.UTF_8);
        return new XmlSpreadsheetConverterService().convertCsvToXml(file.toFile(), config, new ConversionConfig(),
                root -> types);
    }

    private static String text(Document doc, String name) {
        return doc.getElementsByTagName(name).item(0).getTextContent();
    }
}
