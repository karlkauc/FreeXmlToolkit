package org.fxt.freexmltoolkit.service.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Accent;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Role;
import org.junit.jupiter.api.Test;

class FxtExcelThemeTest {

    @Test
    void safePrefixesFormulaTriggers() {
        assertEquals("'=1+1", FxtExcelTheme.safe("=1+1"));
        assertEquals("'+x", FxtExcelTheme.safe("+x"));
        assertEquals("'-x", FxtExcelTheme.safe("-x"));
        assertEquals("'@x", FxtExcelTheme.safe("@x"));
        assertEquals("plain", FxtExcelTheme.safe("plain"));
        assertEquals("", FxtExcelTheme.safe(null));
    }

    @Test
    void clipCutsToTheCellLimit() {
        String longText = "x".repeat(FxtExcelTheme.MAX_CELL_LENGTH + 10);
        String clipped = FxtExcelTheme.clip(longText);
        assertEquals(FxtExcelTheme.MAX_CELL_LENGTH, clipped.length());
        assertTrue(clipped.endsWith("…"));
        assertEquals("short", FxtExcelTheme.clip("short"));
        assertEquals("", FxtExcelTheme.clip(null));
    }

    @Test
    void stylesAreSharedPerRole() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            assertSame(theme.header(), theme.header());
            assertSame(theme.body(Role.TEXT, true), theme.body(Role.TEXT, true));
            assertNotSame(theme.body(Role.TEXT, true), theme.body(Role.TEXT, false));
            assertSame(theme.accentText(Accent.ERROR, false), theme.accentText(Accent.ERROR, false));
            int styles = workbook.getNumCellStyles();
            int fonts = workbook.getNumberOfFonts();
            for (int i = 0; i < 100; i++) {
                theme.body(Role.values()[i % Role.values().length], i % 2 == 0);
            }
            assertTrue(workbook.getNumCellStyles() - styles <= Role.values().length * 2);
            assertTrue(workbook.getNumberOfFonts() - fonts <= Role.values().length);
        }
    }

    @Test
    void worksOnStreamingWorkbooks() throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            var sheet = workbook.createSheet("T");
            FxtExcelTheme.tableSheet(sheet);
            int next = theme.writeHeaderRow(sheet, 0, new String[] {"A", "B"}, i -> i == 1);
            assertEquals(1, next);
            assertSame(theme.headerCenter(), sheet.getRow(0).getCell(1).getCellStyle());
            FxtExcelTheme.finishTable(sheet, 0, 0, 1);
        }
    }
}
