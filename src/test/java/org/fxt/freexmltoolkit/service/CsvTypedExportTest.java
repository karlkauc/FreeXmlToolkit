package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** Typed XML → CSV export and the CSV quoting of delimiters, quotes and line breaks in values. */
class CsvTypedExportTest {

    private static final String XML = """
            <order id="17" note="a,b;c|d">
              <amount>10.50</amount>
              <qty>007</qty>
              <bad>12a</bad>
              <date>2024-01-31</date>
              <name>Smith, "Jr." ; |x</name>
              <tabbed>left\tright</tabbed>
              <multi>line one
            line two</multi>
            </order>""";

    private static final Map<String, String> TYPES = Map.of(
            "/order@id", "int", "/order/amount", "decimal", "/order/qty", "int", "/order/date", "date",
            "/order/bad", "int");

    private static final SpreadsheetTypeResolver RESOLVER =
            (xpath, attribute) -> TYPES.get(xpath + (attribute == null ? "" : "@" + attribute));

    @TempDir
    Path tempDir;

    @Test
    void typedValuesAreBareTextIsQuotedAndTheDataTypeColumnNamesTheType() throws Exception {
        List<String> lines = Files.readAllLines(export(CsvHandler.CsvConfig.comma(), RESOLVER), StandardCharsets.UTF_8);
        assertEquals("\"XPath\",\"Value\",\"Type\",\"Data Type\"", lines.get(0));
        assertTrue(lines.contains("\"/order/@id\",17,\"attribute\",\"int\""), lines.toString());
        assertTrue(lines.contains("\"/order/amount\",10.50,\"element\",\"decimal\""), lines.toString());
        assertTrue(lines.contains("\"/order/date\",2024-01-31,\"element\",\"date\""), lines.toString());
        // 007 is a valid int (unlike in Excel, CSV keeps it unchanged); a wrong value is quoted text
        assertTrue(lines.contains("\"/order/qty\",007,\"element\",\"int\""), lines.toString());
        assertTrue(lines.contains("\"/order/bad\",\"12a\",\"element\",\"int\""), lines.toString());
        assertTrue(lines.contains("\"/order\",,\"element\","), "container rows have empty fields: " + lines);
    }

    @Test
    void withoutSchemaTheFormatStaysPlain() throws Exception {
        List<String> lines = Files.readAllLines(export(CsvHandler.CsvConfig.comma(), SpreadsheetTypeResolver.NONE),
                StandardCharsets.UTF_8);
        assertEquals("XPath,Value,Type", lines.get(0));
        assertTrue(lines.contains("/order/amount,10.50,element"), lines.toString());
    }

    @ParameterizedTest
    @ValueSource(chars = {',', ';', '\t', '|'})
    void valuesWithDelimitersQuotesAndLineBreaksRoundTrip(char delimiter) throws Exception {
        CsvHandler.CsvConfig config = new CsvHandler.CsvConfig(delimiter);
        for (SpreadsheetTypeResolver types : List.of(SpreadsheetTypeResolver.NONE, RESOLVER)) {
            File csv = export(config, types).toFile();
            Document back = new XmlSpreadsheetConverterService().convertCsvToXml(csv, config, new ConversionConfig());
            assertEquals("a,b;c|d", back.getDocumentElement().getAttribute("note"));
            assertEquals("17", back.getDocumentElement().getAttribute("id"));
            assertEquals("10.50", text(back, "amount"));
            assertEquals("007", text(back, "qty"));
            assertEquals("Smith, \"Jr.\" ; |x", text(back, "name"));
            assertEquals("left\tright", text(back, "tabbed"));
            assertEquals("line one\nline two", text(back, "multi"));
        }
    }

    @Test
    void parserHandlesQuotedLineBreaksCrLfAndBlankLines() throws Exception {
        String csv = "XPath,Value\r\n\"/a\",\"x\r\ny\"\r\n\r\n/b,\"say \"\"hi\"\"\"\r\n/c,plain\"quote\n";
        List<List<String>> records = CsvHandler.readRecords(new StringReader(csv), CsvHandler.CsvConfig.comma());
        assertEquals(List.of(
                List.of("XPath", "Value"),
                List.of("/a", "x\r\ny"),
                List.of("/b", "say \"hi\""),
                List.of("/c", "plain\"quote")), records);
    }

    private Path export(CsvHandler.CsvConfig config, SpreadsheetTypeResolver types) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(XML)));
        Path out = tempDir.resolve("out.csv");
        new XmlSpreadsheetConverterService().convertXmlToCsv(doc, out.toFile(), config, new ConversionConfig(), types);
        return out;
    }

    private static String text(Document doc, String name) {
        return doc.getElementsByTagName(name).item(0).getTextContent();
    }
}
