package org.fxt.freexmltoolkit.service.spreadsheet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.Layout;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** XML → table (Excel / CSV) → XML gives the same document; foreign tables become records. */
class SpreadsheetTableRoundTripTest {

    @TempDir
    Path tmp;

    private final XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();

    static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static ConversionConfig table() {
        ConversionConfig config = new ConversionConfig();
        config.setLayout(Layout.TABLE);
        return config;
    }

    private void assertSameXml(String expected, Document actual) throws Exception {
        String text = service.documentToString(actual, new ConversionConfig());
        var result = XmlStructuralDiff.compare(expected, text, XmlStructuralDiff.Options.DEFAULTS);
        assertTrue(result.equivalent(), () -> "differs:\n" + text + "\n" + result.differences());
    }

    @Test
    void excelTableRoundTripKeepsTheDocument() throws Exception {
        String xml = """
                <Orders version="2">
                  <!-- exported -->
                  <Header><Date>2026-11-01</Date></Header>
                  <Order id="1"><Date>2026-11-01</Date><Customer><Name>Müller</Name></Customer>
                    <Item><Qty>3</Qty></Item><Item><Qty>4</Qty></Item></Order>
                  <Order id="2"><Date>2026-11-02</Date><Customer><Name>Huber</Name></Customer>
                    <Item><Qty>1</Qty></Item></Order>
                  <Trailer count="2"/>
                </Orders>""";
        File out = tmp.resolve("orders.xlsx").toFile();
        service.convertXmlToExcel(parse(xml), out, table());
        try (var in = new FileInputStream(out); var wb = WorkbookFactory.create(in)) {
            assertEquals(XmlSpreadsheetConverterService.RECORDS_SHEET, wb.getSheetAt(0).getSheetName());
            assertEquals("@id", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Huber", wb.getSheetAt(0).getRow(2).getCell(2).getStringCellValue());
            assertNotNull(wb.getSheet(XmlSpreadsheetConverterService.OTHER_SHEET));
            assertNotNull(wb.getSheet(XmlSpreadsheetConverterService.INFO_SHEET));
        }
        assertSameXml(xml, service.convertExcelToXml(out, new ConversionConfig()));
    }

    @Test
    void editedTableRowsComeBackAsRecords() throws Exception {
        String xml = "<Orders><Order id=\"1\"><Qty>3</Qty></Order><Order id=\"2\"><Qty>1</Qty></Order></Orders>";
        File out = tmp.resolve("o.xlsx").toFile();
        service.convertXmlToExcel(parse(xml), out, table());
        try (var in = new FileInputStream(out); var wb = WorkbookFactory.create(in)) {
            var sheet = wb.getSheet(XmlSpreadsheetConverterService.RECORDS_SHEET);
            var row = sheet.createRow(3);
            row.createCell(0).setCellValue("3");
            row.createCell(1).setCellValue("7");
            sheet.getRow(1).getCell(1).setCellValue("30");
            try (var os = Files.newOutputStream(out.toPath())) {
                wb.write(os);
            }
        }
        assertSameXml("<Orders><Order id=\"1\"><Qty>30</Qty></Order><Order id=\"2\"><Qty>1</Qty></Order>"
                + "<Order id=\"3\"><Qty>7</Qty></Order></Orders>", service.convertExcelToXml(out, new ConversionConfig()));
    }

    @Test
    void recordsUnderSeveralParentsComeBackUnderTheirParent() throws Exception {
        String xml = "<Shop><Customer name=\"a\"><Order n=\"1\"/><Order n=\"2\"/></Customer>"
                + "<Customer name=\"b\"><Order n=\"3\"/></Customer></Shop>";
        File out = tmp.resolve("shop.xlsx").toFile();
        ConversionConfig config = table();
        config.setRecordPath("/Shop/Customer/Order");
        service.convertXmlToExcel(parse(xml), out, config);
        assertSameXml(xml, service.convertExcelToXml(out, new ConversionConfig()));
    }

    @Test
    void theFundsXmlExampleSurvivesTheTable() throws Exception {
        Path example = Path.of("release/examples/xml/FundsXML4_Equity_Fund.xml");
        String xml = Files.readString(example);
        File out = tmp.resolve("fund.xlsx").toFile();
        ConversionConfig config = table();
        config.setIncludeComments(false);
        service.convertXmlToExcel(parse(xml), out, config);
        Document back = service.convertExcelToXml(out, new ConversionConfig());
        String text = service.documentToString(back, new ConversionConfig());
        // empty elements inside a record are not kept by the table (an empty cell means "no element")
        var options = new XmlStructuralDiff.Options(true, true, false, false, java.util.List.of())
                .withIgnoreXPaths(java.util.List.of("//*[not(node()) and not(@*)]"));
        var result = XmlStructuralDiff.compare(xml, text, options);
        assertTrue(result.equivalent(), () -> result.differences().stream().limit(10).toList().toString());
    }

    @Test
    void csvTableRoundTripWithTheRecordNames() throws Exception {
        String xml = "<Orders><Order id=\"1\"><Qty>3</Qty></Order><Order id=\"2\"><Qty>1</Qty></Order></Orders>";
        File out = tmp.resolve("o.csv").toFile();
        service.convertXmlToCsv(parse(xml), out, CsvHandler.CsvConfig.semicolon(), table());
        String csv = Files.readString(out.toPath()).replace("﻿", "");
        assertTrue(csv.startsWith("@id;Qty"), csv);
        ConversionConfig names = new ConversionConfig();
        names.setRootName("Orders");
        names.setRecordName("Order");
        assertSameXml(xml, service.convertCsvToXml(out, CsvHandler.CsvConfig.semicolon(), names));
    }

    @Test
    void aForeignCsvBecomesRecords() throws Exception {
        Path csv = Files.writeString(tmp.resolve("people.csv"), "id;Full name;City\n1;Anna Berg;Wien\n2;Ben Ott;\n\n");
        Document doc = service.convertCsvToXml(csv.toFile(), CsvHandler.CsvConfig.semicolon(), new ConversionConfig());
        assertSameXml("<Records><Record><id>1</id><Full_name>Anna Berg</Full_name><City>Wien</City></Record>"
                + "<Record><id>2</id><Full_name>Ben Ott</Full_name></Record></Records>", doc);
    }

    @Test
    void aForeignWorkbookBecomesRecords() throws Exception {
        File xlsx = tmp.resolve("foreign.xlsx").toFile();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Data");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Product");
            header.createCell(1).setCellValue("Price");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Pen");
            row.createCell(1).setCellValue(2.5);
            try (var os = Files.newOutputStream(xlsx.toPath())) {
                wb.write(os);
            }
        }
        ConversionConfig names = new ConversionConfig();
        names.setRootName("Products");
        names.setRecordName("Product");
        assertSameXml("<Products><Product><Product>Pen</Product><Price>2.5</Price></Product></Products>",
                service.convertExcelToXml(xlsx, names));
    }

    @Test
    void theXPathListStillReadsAndGetsAnInfoSheet() throws Exception {
        String xml = "<a x=\"1\"><b>2</b></a>";
        File out = tmp.resolve("list.xlsx").toFile();
        service.convertXmlToExcel(parse(xml), out, new ConversionConfig());
        try (var in = new FileInputStream(out); var wb = WorkbookFactory.create(in)) {
            assertEquals("XPath", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertNotNull(wb.getSheet(XmlSpreadsheetConverterService.INFO_SHEET));
        }
        assertSameXml(xml, service.convertExcelToXml(out, new ConversionConfig()));
    }
}
