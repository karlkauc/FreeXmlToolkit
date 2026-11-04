package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Excel import finds its XSD (declared next to the workbook, or chosen) and reads the values with it. */
class SpreadsheetTypedImportRunnerTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="day" type="xs:date"/>
                    <xs:element name="flag" type="xs:boolean"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>""";

    @TempDir
    Path tempDir;

    @Test
    void theDeclaredSchemaNextToTheWorkbookIsUsed() throws Exception {
        Files.writeString(tempDir.resolve("order.xsd"), XSD);
        var result = SpreadsheetActionRunner.excelToXml(workbook("order.xsd"), new ConversionConfig(), null);
        assertFalse(result.isError(), result.text());
        assertEquals(tempDir.resolve("order.xsd").toFile().getCanonicalFile(), result.schema().getCanonicalFile());
        assertTrue(result.text().contains("<day>2024-01-31</day>"), result.text());
        assertTrue(result.text().contains("<flag>true</flag>"), result.text());
    }

    @Test
    void aChosenSchemaWinsAndWithoutAnyTheValuesAreUntyped() throws Exception {
        Path chosen = Files.writeString(tempDir.resolve("chosen.xsd"), XSD);
        var typed = SpreadsheetActionRunner.excelToXml(workbook("missing.xsd"), new ConversionConfig(), chosen.toFile());
        assertEquals(chosen.toFile(), typed.schema());
        assertTrue(typed.text().contains("<day>2024-01-31</day>"), typed.text());

        var untyped = SpreadsheetActionRunner.excelToXml(workbook("missing.xsd"), new ConversionConfig(), null);
        assertFalse(untyped.isError(), untyped.text());
        assertNull(untyped.schema());
        assertTrue(untyped.text().contains("<day>45322</day>"), untyped.text());
    }

    @Test
    void anUnreadableChosenSchemaFailsTheImport() throws Exception {
        Path broken = Files.writeString(tempDir.resolve("broken.xsd"), "<xs:schema");
        var result = SpreadsheetActionRunner.excelToXml(workbook("order.xsd"), new ConversionConfig(), broken.toFile());
        assertTrue(result.isError());
        assertTrue(result.text().contains("broken.xsd"), result.text());
    }

    private File workbook(String schemaLocation) throws Exception {
        File file = tempDir.resolve("order.xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            String[][] rows = {
                    {"XPath", "Value", "Type"},
                    {"/order", "", "element"},
                    {"/order/@xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "attribute"},
                    {"/order/@xsi:noNamespaceSchemaLocation", schemaLocation, "attribute"},
                    {"/order/day", null, "element"},
                    {"/order/flag", null, "element"}};
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(rows[i][0]);
                if (rows[i][1] != null) {
                    row.createCell(1).setCellValue(rows[i][1]);
                }
                row.createCell(2).setCellValue(rows[i][2]);
            }
            sheet.getRow(4).createCell(1).setCellValue(45322); // a date typed in as a plain number
            sheet.getRow(5).createCell(1).setCellValue(1);
            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }
}
