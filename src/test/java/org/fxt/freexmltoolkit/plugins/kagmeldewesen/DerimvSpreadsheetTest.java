package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** DeRiMV from and to spreadsheets: the CSV layout of risk management, Excel, and back to XML. */
class DerimvSpreadsheetTest {

    private static final String CSV = """
            Beispiel KAG m.b.H.;1234;20254;;;;
            B1000001;AT0000A1B2C4;Beispiel Anleihefonds (A);12,5;18,75;Comm;50
            B1000002;AT0000A1B2E0;Beispiel Absolute Return (T);4,1;6,85;avar;10
            B1000003;AT0000A1B2F7;Beispiel Aktienfonds Größe (T);105,2;131,4;rVaR;

            """;

    private static String example() throws Exception {
        try (InputStream in = DerimvSpreadsheetTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/derimv-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertSchemaValid(String xml) throws Exception {
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(DerimvSpreadsheetTest.class.getResource("/plugins/kagmeldewesen/derimv/Derivatemeldung.xsd"))
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    @Test
    void csvInWindows1252WithDecimalCommaBecomesAValidReport() throws Exception {
        DerimvSpreadsheet.Import result = DerimvSpreadsheet.readCsv(CSV.getBytes(Charset.forName("windows-1252")));
        assertEquals(List.of(), result.issues());
        DerimvSpreadsheet.Report report = result.report();
        assertEquals(new DerimvSpreadsheet.Header("Beispiel KAG m.b.H.", "01234", "2025", "4"), report.header());
        assertEquals(3, report.funds().size());
        assertEquals(new DerimvSpreadsheet.Fund("B1000002", "AT0000A1B2E0", "Beispiel Absolute Return (T)", "4.10", "6.85",
                "aVaR", "10.00"), report.funds().get(1));
        assertEquals("Beispiel Aktienfonds Größe (T)", report.funds().get(2).name());
        assertEquals("", report.funds().get(2).limit());
        String xml = DerimvSpreadsheet.toXml(report);
        assertSchemaValid(xml);
        assertEquals(List.of(), KagMeldewesenPlugin.rulesFor(org.fxt.freexmltoolkit.plugins.regreport.RegValidator.rootOf(xml), xml)
                .run(xml, "x.xml").stream().filter(f -> !f.ruleId().equals("DeRiMV-period")).toList());
    }

    @Test
    void problemRowsAreReportedAndKept() {
        String csv = "KAG;12x;2025-Q9\nB1;AT0000A1B2C4;" + "N".repeat(120) + ";abc;1;Hedge;\n;;Only a name;1;2;Comm;\n";
        DerimvSpreadsheet.Import result = DerimvSpreadsheet.readCsv(csv.getBytes(StandardCharsets.UTF_8));
        List<String> messages = result.issues().stream().map(i -> i.row() + ": " + i.message()).toList();
        assertTrue(messages.contains("1: Bank code \"12x\" is not 5 digits."), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("1: Period")), messages.toString());
        assertTrue(messages.contains("2: Fund name shortened to 100 characters."), messages.toString());
        assertTrue(messages.contains("2: Risikoangabe \"abc\" is not a number."), messages.toString());
        assertTrue(messages.contains("2: Method \"Hedge\" is not Comm, aVaR or rVaR."), messages.toString());
        assertTrue(messages.contains("3: FMA code (A) is empty.") && messages.contains("3: ISIN (B) is empty."), messages.toString());
        assertEquals(2, result.report().funds().size());
        assertEquals("abc", result.report().funds().getFirst().risk(), "kept, so the schema shows it");
    }

    @Test
    void xmlToExcelAndBackKeepsEveryValue() throws Exception {
        DerimvSpreadsheet.Report report = DerimvSpreadsheet.fromXml(example());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DerimvSpreadsheet.writeXlsx(report, "derimv-example.xml", out);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals("ISIN", workbook.getSheet("Funds").getRow(0).getCell(1).getStringCellValue());
            assertEquals(18.75, workbook.getSheet("Funds").getRow(1).getCell(4).getNumericCellValue());
        }
        DerimvSpreadsheet.Import back = DerimvSpreadsheet.readWorkbook(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(List.of(), back.issues());
        assertEquals(report, back.report());

        DerimvSpreadsheet.Import csv = DerimvSpreadsheet.readCsv(DerimvSpreadsheet.toCsv(report));
        assertEquals(List.of(), csv.issues());
        assertEquals(report, csv.report());
        assertSchemaValid(DerimvSpreadsheet.toXml(csv.report()));
    }

    @Test
    void aWorkbookInTheCsvLayoutIsReadToo() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("DERIVATIVMELDUNG01");
            var head = sheet.createRow(0);
            head.createCell(0).setCellValue("Beispiel KAG");
            head.createCell(1).setCellValue(76260);
            head.createCell(2).setCellValue(20163);
            var row = sheet.createRow(1);
            String[] text = {"B6602495", "AT0000A1B2C4", "Fonds (A)"};
            for (int c = 0; c < text.length; c++) {
                row.createCell(c).setCellValue(text[c]);
            }
            row.createCell(3).setCellValue(0);
            row.createCell(4).setCellValue(5.04);
            row.createCell(5).setCellValue("Comm");
            workbook.write(out);
        }
        DerimvSpreadsheet.Import result = DerimvSpreadsheet.readWorkbook(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(List.of(), result.issues());
        assertEquals(new DerimvSpreadsheet.Header("Beispiel KAG", "76260", "2016", "3"), result.report().header());
        assertEquals(new DerimvSpreadsheet.Fund("B6602495", "AT0000A1B2C4", "Fonds (A)", "0.00", "5.04", "Comm", ""),
                result.report().funds().getFirst());
    }
}
