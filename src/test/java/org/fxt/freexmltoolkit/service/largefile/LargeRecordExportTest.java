package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.largefile.LargeRecordExport.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeRecordExport}: columns from all children, CSV quoting, XLSX sheets. */
@DisplayName("LargeRecordExport")
class LargeRecordExportTest {

    @TempDir
    Path dir;

    private LargeXmlStructure structure(int rows) throws Exception {
        StringBuilder xml = new StringBuilder("<r>\n");
        for (int i = 1; i <= rows; i++) {
            xml.append("<row id=\"").append(i).append("\"><name>N, \"").append(i).append("\"</name>");
            if (i == rows) {
                xml.append("<late>").append("y".repeat(300)).append("</late>");
            }
            xml.append("</row>\n");
        }
        xml.append("<tail>t</tail></r>");
        Path file = dir.resolve("r.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return new LargeXmlStructure(LargeFileIndex.build(file, null, null));
    }

    @Test
    @DisplayName("CSV: a column seen only in the last child, quoting, values not cut at 200 characters")
    void csv() throws Exception {
        LargeXmlStructure structure = structure(1500);
        Path target = dir.resolve("out.csv");
        var result = LargeRecordExport.export(structure, structure.root(), Format.CSV, target, null, null);
        assertEquals(1501, result.rows());
        assertEquals(3, result.columns());
        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        assertEquals("﻿#,Element,@id,name,late,Text", lines.get(0));
        assertEquals("1,row,1,\"N, \"\"1\"\"\",,", lines.get(1));
        assertTrue(lines.get(1500).endsWith("," + "y".repeat(300) + ","), "the full value");
        assertEquals("1501,tail,,,,t", lines.get(1501));
    }

    @Test
    @DisplayName("csv quoting rules")
    void quoting() throws Exception {
        assertEquals("", LargeRecordExport.csv(null));
        assertEquals("plain", LargeRecordExport.csv("plain"));
        assertEquals("\" lead\"", LargeRecordExport.csv(" lead"));
        assertEquals("\"a\nb\"", LargeRecordExport.csv("a\nb"));
        StringWriter out = new StringWriter();
        LargeRecordExport.writeCsvLine(out, List.of("a", "b,c"));
        assertEquals("a,\"b,c\"\r\n", out.toString());
    }

    @Test
    @DisplayName("XLSX: summary + records sheets, split when a sheet is full")
    void xlsx() throws Exception {
        LargeXmlStructure structure = structure(25);
        Path target = dir.resolve("out.xlsx");
        var result = LargeRecordExport.export(structure, structure.root(), Format.XLSX, target, 11, null, null);
        assertEquals(26, result.rows());
        assertEquals(3, result.sheets(), "10 rows + header per sheet");
        try (InputStream in = Files.newInputStream(target); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals("Summary", workbook.getSheetName(0));
            assertEquals("Records", workbook.getSheetName(1));
            assertEquals("Records 3", workbook.getSheetName(3));
            assertEquals("@id", workbook.getSheet("Records").getRow(0).getCell(2).getStringCellValue());
            assertEquals("row", workbook.getSheet("Records").getRow(1).getCell(1).getStringCellValue());
            assertEquals(21d, workbook.getSheet("Records 3").getRow(1).getCell(0).getNumericCellValue());
        }
    }
}
