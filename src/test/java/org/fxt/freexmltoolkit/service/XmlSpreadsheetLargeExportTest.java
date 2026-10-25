package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Excel export of large documents: telemetry showed a 56 MB XML whose export (in-memory
 * XSSFWorkbook, autoSizeColumn over every row) filled a 4 GB heap and froze the UI for minutes.
 */
class XmlSpreadsheetLargeExportTest {

    @TempDir
    Path tempDir;

    private final int defaultXlsxRows = XmlSpreadsheetConverterService.maxXlsxRows;
    private final int defaultXlsRows = XmlSpreadsheetConverterService.maxXlsRows;

    @AfterEach
    void restoreLimits() {
        XmlSpreadsheetConverterService.maxXlsxRows = defaultXlsxRows;
        XmlSpreadsheetConverterService.maxXlsRows = defaultXlsRows;
    }

    @Test
    void largeXlsxExportStreamsEveryRowWithTextXPathAndSampledWidths() throws Exception {
        int items = 50_000;
        File out = tempDir.resolve("large.xlsx").toFile();

        new XmlSpreadsheetConverterService().convertXmlToExcel(document(items), out, new ConversionConfig());

        try (FileInputStream in = new FileInputStream(out); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            // header + /root + one row per item
            assertEquals(items + 1, sheet.getLastRowNum());
            Row last = sheet.getRow(sheet.getLastRowNum());
            assertEquals("/root/item[" + items + "]", last.getCell(0).getStringCellValue());
            assertEquals("value " + items, last.getCell(1).getStringCellValue());
            assertEquals("@", last.getCell(0).getCellStyle().getDataFormatString());
            assertTrue(sheet.getColumnWidth(0) > "XPath".length() * 256, "XPath column sized from the rows");
        }
    }

    @Test
    void xlsxBeyondTheRowLimitIsRefusedBeforeAnythingIsWritten() throws Exception {
        XmlSpreadsheetConverterService.maxXlsxRows = 10;
        File out = tempDir.resolve("too-many.xlsx").toFile();

        var e = assertThrows(RowLimitExceededException.class, () -> new XmlSpreadsheetConverterService()
                .convertXmlToExcel(document(20), out, new ConversionConfig()));

        assertEquals(22, e.getRows()); // header + /root + 20 items
        assertEquals(10, e.getMaxRows());
        assertFalse(out.exists());
    }

    @Test
    void xlsUsesItsOwnRowLimit() throws Exception {
        XmlSpreadsheetConverterService.maxXlsRows = 5;
        File xls = tempDir.resolve("small.xls").toFile();
        assertThrows(RowLimitExceededException.class, () -> new XmlSpreadsheetConverterService()
                .convertXmlToExcel(document(10), xls, new ConversionConfig()));

        File xlsx = tempDir.resolve("fine.xlsx").toFile();
        new XmlSpreadsheetConverterService().convertXmlToExcel(document(10), xlsx, new ConversionConfig());
        assertTrue(xlsx.length() > 0);
    }

    private static Document document(int items) throws Exception {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 1; i <= items; i++) {
            xml.append("<item>value ").append(i).append("</item>");
        }
        xml.append("</root>");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml.toString())));
    }
}
