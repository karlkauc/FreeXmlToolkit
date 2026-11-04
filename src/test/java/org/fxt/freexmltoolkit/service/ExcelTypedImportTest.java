package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

/** Excel → XML import of hand-edited cells, read with the schema types of the values. */
class ExcelTypedImportTest {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("/order@paid", "boolean"),
            Map.entry("/order/stamp", "dateTime"),
            Map.entry("/order/day", "date"),
            Map.entry("/order/flag", "boolean"),
            Map.entry("/order/flagText", "boolean"),
            Map.entry("/order/qty", "int"),
            Map.entry("/order/amount", "decimal"),
            Map.entry("/order/rate", "decimal"),
            Map.entry("/order/at", "time"),
            Map.entry("/order/sum", "int"),
            Map.entry("/order/padded", "int"),
            Map.entry("/order/name", "string"));

    private static final SpreadsheetTypeResolver RESOLVER =
            (xpath, attribute) -> TYPES.get(xpath + (attribute == null ? "" : "@" + attribute));

    @TempDir
    Path tempDir;

    @Test
    void cellsAreReadAsTheirSchemaTypes() throws Exception {
        Document doc = new XmlSpreadsheetConverterService()
                .convertExcelToXml(workbook(), new ConversionConfig(), root -> RESOLVER);
        assertEquals("true", doc.getDocumentElement().getAttribute("paid"));
        assertEquals("2024-01-31T00:00:00", text(doc, "stamp"), "a date cell under xs:dateTime");
        assertEquals("2024-01-31", text(doc, "day"), "a General serial under xs:date");
        assertEquals("true", text(doc, "flag"), "1 under xs:boolean");
        assertEquals("false", text(doc, "flagText"), "FALSE text under xs:boolean");
        assertEquals("42", text(doc, "qty"));
        assertEquals("10.5", text(doc, "amount"));
        assertEquals("45322", text(doc, "rate"), "a date-formatted number under xs:decimal stays a number");
        assertEquals("10:15:30.125", text(doc, "at"), "the time of a date-time cell under xs:time");
        assertEquals("3", text(doc, "sum"), "a formula contributes its cached result");
        assertEquals("42", text(doc, "padded"), "blanks around a typed value are dropped");
        assertEquals(" 42 ", text(doc, "name"), "text keeps its blanks");
    }

    @Test
    void withoutSchemaTheCellsDecide() throws Exception {
        Document doc = new XmlSpreadsheetConverterService().convertExcelToXml(workbook(), new ConversionConfig());
        assertEquals("2024-01-31", text(doc, "stamp"), "the date format shows no time");
        assertEquals("45322", text(doc, "day"));
        assertEquals("1", text(doc, "flag"));
        assertEquals("FALSE", text(doc, "flagText"));
        assertEquals("2024-01-31", text(doc, "rate"));
        assertEquals("3", text(doc, "sum"), "the cached result, not the formula text");
    }

    @Test
    void theResolverSeesTheDocumentElementWithItsSchemaDeclaration() throws Exception {
        String[] root = new String[1];
        new XmlSpreadsheetConverterService().convertExcelToXml(workbook(), new ConversionConfig(), xml -> {
            root[0] = xml;
            return null;
        });
        org.junit.jupiter.api.Assertions.assertTrue(
                root[0].contains("xsi:noNamespaceSchemaLocation=\"order.xsd\""), root[0]);
        org.junit.jupiter.api.Assertions.assertFalse(root[0].contains("stamp"), root[0]);
    }

    /** A workbook as a user would leave it after editing: mixed cell types and formats. */
    private File workbook() throws Exception {
        File file = tempDir.resolve("order.xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("XML Structure");
            CellStyle germanDate = wb.createCellStyle();
            germanDate.setDataFormat(wb.createDataFormat().getFormat("dd.mm.yyyy"));
            CellStyle dateTimeMs = wb.createCellStyle();
            dateTimeMs.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss.000"));
            int[] r = {0};
            row(sheet, r, "XPath", "Value", "Type");
            row(sheet, r, "/order", "", "element");
            row(sheet, r, "/order/@xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "attribute");
            row(sheet, r, "/order/@xsi:noNamespaceSchemaLocation", "order.xsd", "attribute");
            row(sheet, r, "/order/@paid", "TRUE", "attribute");
            row(sheet, r, "/order/stamp", null, "element").getCell(1).setCellValue(LocalDateTime.of(2024, 1, 31, 0, 0));
            sheet.getRow(r[0] - 1).getCell(1).setCellStyle(germanDate);
            row(sheet, r, "/order/day", null, "element").getCell(1).setCellValue(45322);
            row(sheet, r, "/order/flag", null, "element").getCell(1).setCellValue(1);
            row(sheet, r, "/order/flagText", "FALSE", "element");
            row(sheet, r, "/order/qty", null, "element").getCell(1).setCellValue(42.0);
            row(sheet, r, "/order/amount", null, "element").getCell(1).setCellValue(10.5);
            Cell rate = row(sheet, r, "/order/rate", null, "element").getCell(1);
            rate.setCellValue(45322);
            rate.setCellStyle(germanDate);
            Cell at = row(sheet, r, "/order/at", null, "element").getCell(1);
            at.setCellValue(LocalDateTime.of(2024, 1, 31, 10, 15, 30, 125_000_000));
            at.setCellStyle(dateTimeMs);
            row(sheet, r, "/order/sum", null, "element").getCell(1).setCellFormula("1+2");
            row(sheet, r, "/order/padded", " 42 ", "element");
            row(sheet, r, "/order/name", " 42 ", "element");
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    private static Row row(Sheet sheet, int[] r, String xpath, String value, String type) {
        Row row = sheet.createRow(r[0]++);
        row.createCell(0).setCellValue(xpath);
        Cell cell = row.createCell(1);
        if (value != null) {
            cell.setCellValue(value);
        }
        row.createCell(2).setCellValue(type);
        return row;
    }

    private static String text(Document doc, String name) {
        return doc.getElementsByTagName(name).item(0).getTextContent();
    }
}
