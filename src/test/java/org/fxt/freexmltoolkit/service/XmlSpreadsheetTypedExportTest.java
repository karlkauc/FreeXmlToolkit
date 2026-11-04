package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** The XML → Excel export's look and its schema-typed value cells, and the lossless import of them. */
class XmlSpreadsheetTypedExportTest {

    private static final String XML = """
            <order id="17" paid="true">
              <amount>10.50</amount>
              <qty>3</qty>
              <big>1234567890123456</big>
              <date>2024-01-31</date>
              <stamp>2024-01-31T10:15:30</stamp>
              <stampMs>2024-01-31T10:15:30.125</stampMs>
              <zoned>2024-01-31T10:15:30+01:00</zoned>
              <at>08:05:00</at>
              <bad>abc</bad>
              <note>hello</note>
            </order>""";

    /** What a bound schema would say about the elements/attributes of {@link #XML}. */
    private static final SpreadsheetTypeResolver TYPES = (xpath, attribute) -> {
        Map<String, SpreadsheetCellKind> kinds = new HashMap<>();
        kinds.put("/order@id", SpreadsheetCellKind.INTEGER);
        kinds.put("/order@paid", SpreadsheetCellKind.BOOLEAN);
        kinds.put("/order/amount", SpreadsheetCellKind.DECIMAL);
        kinds.put("/order/qty", SpreadsheetCellKind.INTEGER);
        kinds.put("/order/big", SpreadsheetCellKind.INTEGER);
        kinds.put("/order/date", SpreadsheetCellKind.DATE);
        kinds.put("/order/stamp", SpreadsheetCellKind.DATE_TIME);
        kinds.put("/order/stampMs", SpreadsheetCellKind.DATE_TIME);
        kinds.put("/order/zoned", SpreadsheetCellKind.DATE_TIME);
        kinds.put("/order/at", SpreadsheetCellKind.TIME);
        kinds.put("/order/bad", SpreadsheetCellKind.INTEGER);
        return kinds.getOrDefault(xpath + (attribute == null ? "" : "@" + attribute), SpreadsheetCellKind.TEXT);
    };

    @TempDir
    Path tempDir;

    @Test
    void valuesGetTheSchemaCellTypes() throws Exception {
        File out = export(TYPES);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(out))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(CellType.NUMERIC, value(sheet, "/order/@id").getCellType());
            assertEquals(17, value(sheet, "/order/@id").getNumericCellValue());
            assertEquals(CellType.BOOLEAN, value(sheet, "/order/@paid").getCellType());

            Cell amount = value(sheet, "/order/amount");
            assertEquals(CellType.NUMERIC, amount.getCellType());
            assertEquals(10.5, amount.getNumericCellValue());
            assertEquals("#,##0.00", amount.getCellStyle().getDataFormatString());
            assertEquals("0", value(sheet, "/order/qty").getCellStyle().getDataFormatString());

            Cell date = value(sheet, "/order/date");
            assertTrue(DateUtil.isCellDateFormatted(date));
            assertEquals("yyyy-mm-dd", date.getCellStyle().getDataFormatString());
            assertTrue(DateUtil.isCellDateFormatted(value(sheet, "/order/stamp")));
            assertTrue(DateUtil.isCellDateFormatted(value(sheet, "/order/at")));

            for (String text : new String[] {"/order/big", "/order/zoned", "/order/bad", "/order/note"}) {
                assertEquals(CellType.STRING, value(sheet, text).getCellType(), text);
            }
        }
    }

    @Test
    void withoutSchemaEverythingStaysText() throws Exception {
        File out = export(SpreadsheetTypeResolver.NONE);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(out))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(CellType.STRING, value(sheet, "/order/amount").getCellType());
            assertEquals("10.50", value(sheet, "/order/amount").getStringCellValue());
        }
    }

    @Test
    void sheetHasTheDataDictionaryLook() throws Exception {
        File out = export(TYPES);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(out))) {
            Sheet sheet = workbook.getSheetAt(0);
            XSSFCellStyle header = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertEquals("1E293B", header.getFillForegroundColorColor().getARGBHex().substring(2));
            assertTrue(header.getFont().getBold());
            assertEquals("FFFFFF", header.getFont().getXSSFColor().getARGBHex().substring(2));
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertFalse(sheet.isDisplayGridlines());

            XSSFCellStyle xpath = (XSSFCellStyle) sheet.getRow(1).getCell(0).getCellStyle();
            assertEquals("Consolas", xpath.getFont().getFontName());
            assertEquals("@", xpath.getDataFormatString());
            XSSFCellStyle striped = (XSSFCellStyle) sheet.getRow(2).getCell(0).getCellStyle();
            assertEquals("F8FAFC", striped.getFillForegroundColorColor().getARGBHex().substring(2));
        }
    }

    @Test
    void typedWorkbookImportsBackToTheSameValues() throws Exception {
        File out = export(TYPES);
        XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
        Document back = service.convertExcelToXml(out, new ConversionConfig());
        assertEquals("17", back.getDocumentElement().getAttribute("id"));
        assertEquals("true", back.getDocumentElement().getAttribute("paid"));
        Map<String, String> expected = Map.of(
                "amount", "10.50", "qty", "3", "big", "1234567890123456", "date", "2024-01-31",
                "stamp", "2024-01-31T10:15:30", "stampMs", "2024-01-31T10:15:30.125",
                "zoned", "2024-01-31T10:15:30+01:00", "at", "08:05:00", "bad", "abc");
        expected.forEach((name, value) -> assertEquals(value,
                back.getElementsByTagName(name).item(0).getTextContent(), name));
    }

    private File export(SpreadsheetTypeResolver types) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(XML)));
        File out = tempDir.resolve("typed.xlsx").toFile();
        new XmlSpreadsheetConverterService().convertXmlToExcel(doc, out, new ConversionConfig(), types);
        return out;
    }

    private static Cell value(Sheet sheet, String xpath) {
        for (Row row : sheet) {
            if (row.getRowNum() > 0 && xpath.equals(row.getCell(0).getStringCellValue())) {
                return row.getCell(1);
            }
        }
        throw new AssertionError("no row " + xpath);
    }
}
