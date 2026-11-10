package org.fxt.freexmltoolkit.service.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationExcelExporter;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.KeyValue;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportFormat;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportModel;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportRow;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportSection;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportTable;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportWriters;
import org.fxt.freexmltoolkit.di.FxtTestModule;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.DataDictionaryExcelExporter;
import org.fxt.freexmltoolkit.service.XsdDocumentationHtmlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Every Excel workbook the application writes must look the same ({@link FxtExcelTheme}): slate header
 * row with white bold Calibri 10, zebra rows, no gridlines, coloured tabs, a 16 pt title on info sheets.
 * One test per exporter, all checked by the same assertions.
 */
class ExcelHouseStyleTest {

    private static final Set<String> FONTS = Set.of(FxtExcelTheme.SANS, FxtExcelTheme.MONO);

    @TempDir
    Path dir;

    @BeforeEach
    void registry() {
        new FxtTestModule().configure();
    }

    @AfterEach
    void reset() {
        ServiceRegistry.reset();
    }

    @Test
    void validationProblems() throws Exception {
        Path target = dir.resolve("problems.xlsx");
        ValidationExcelExporter.export(List.of(
                new ValidationProblem("XSD", "error", 3, "cvc-complex-type.2.4.a: Invalid content"),
                new ValidationProblem("Schematron", "warning", 7, "Amount should be positive"),
                new ValidationProblem("XSD", "error", 0, "=SUM(A1) is not a formula here")), "order.xml", target);
        try (XSSFWorkbook workbook = open(target)) {
            assertInfoSheet(workbook.getSheet("Summary"));
            XSSFSheet problems = workbook.getSheet("Problems");
            assertTableSheet(problems, 0);
            // severity as coloured bold text
            XSSFFont error = problems.getRow(1).getCell(2).getCellStyle().getFont();
            XSSFFont warning = problems.getRow(2).getCell(2).getCellStyle().getFont();
            assertTrue(error.getBold());
            assertEquals("FF" + FxtExcelTheme.Accent.ERROR.text(), error.getXSSFColor().getARGBHex());
            assertEquals("FF" + FxtExcelTheme.Accent.WARNING.text(), warning.getXSSFColor().getARGBHex());
            assertEquals("'=SUM(A1) is not a formula here", problems.getRow(3).getCell(4).getStringCellValue());
            assertNotNull(problems.getCTWorksheet().getAutoFilter());
        }
    }

    @Test
    void schemaAnalysisReport() throws Exception {
        ReportTable issues = new ReportTable("issues", "Issues", List.of("Severity", "Message"), List.of(
                ReportRow.of("INFO", "one"), ReportRow.of("INFO", "two"), ReportRow.of("INFO", "three")));
        ReportSection quality = new ReportSection("quality", "Quality Checks", List.of(), List.of(issues), null);
        ReportSection overview = new ReportSection("overview", "Overview",
                List.of(KeyValue.of("XSD version", "1.1")), List.of(), null);
        Path target = dir.resolve("report.xlsx");
        ReportWriters.write(new ReportModel("Schema Analysis Report", "test.xsd", LocalDateTime.now(),
                List.of(overview, quality)), ReportFormat.XLSX, target);
        try (XSSFWorkbook workbook = open(target)) {
            XSSFSheet sheet = workbook.getSheet("Quality Checks");
            assertTitle(sheet.getRow(0).getCell(0));
            // title, subject, generated, blank, section heading, header
            assertTableSheet(sheet, 5);
            assertTitle(workbook.getSheet("Overview").getRow(0).getCell(0));
        }
    }

    @Test
    void xmlToExcel() throws Exception {
        File target = dir.resolve("data.xlsx").toFile();
        var result = SpreadsheetActionRunner.exportToExcel(
                "<root><a>1</a><b>two</b><c>three</c><d>four</d></root>", target);
        assertFalse(result.isError(), String.valueOf(result.error()));
        try (XSSFWorkbook workbook = open(target.toPath())) {
            assertTableSheet(workbook.getSheetAt(0), 0);
        }
    }

    @Test
    void dataDictionary() throws Exception {
        XsdDocumentationData data = Mockito.mock(XsdDocumentationData.class);
        when(data.getGlobalElements()).thenReturn(Collections.emptyList());
        when(data.getGlobalComplexTypes()).thenReturn(Collections.emptyList());
        when(data.getGlobalSimpleTypes()).thenReturn(Collections.emptyList());
        List<XsdExtendedElement> elements = new ArrayList<>();
        for (String name : List.of("First", "Second", "Third")) {
            XsdExtendedElement element = new XsdExtendedElement();
            element.setElementName(name);
            element.setElementType("xs:string");
            element.setLevel(1);
            element.setCurrentNode(Mockito.mock(org.w3c.dom.Node.class));
            elements.add(element);
        }
        File target = dir.resolve("dictionary.xlsx").toFile();
        new DataDictionaryExcelExporter(data, Mockito.mock(XsdDocumentationHtmlService.class))
                .exportToExcel(target, elements);
        try (XSSFWorkbook workbook = open(target.toPath())) {
            assertInfoSheet(workbook.getSheet("Schema Info"));
            assertTableSheet(workbook.getSheet("DEFAULT"), 0);
        }
    }

    // ------------------------------------------------------------------ shared assertions

    private static XSSFWorkbook open(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        }
    }

    /** A sheet with a table whose header is in {@code headerRow} and at least two data rows below. */
    private static void assertTableSheet(XSSFSheet sheet, int headerRow) {
        assertNotNull(sheet);
        assertFalse(sheet.isDisplayGridlines(), sheet.getSheetName() + ": gridlines");
        assertEquals("FF" + FxtExcelTheme.ACCENT_RULE, sheet.getTabColor().getARGBHex(), sheet.getSheetName() + ": tab");

        XSSFCellStyle header = sheet.getRow(headerRow).getCell(0).getCellStyle();
        assertEquals("FF" + FxtExcelTheme.HEADER_FILL, header.getFillForegroundXSSFColor().getARGBHex(),
                sheet.getSheetName() + ": header fill");
        XSSFFont headerFont = header.getFont();
        assertTrue(headerFont.getBold());
        assertEquals("FFFFFFFF", headerFont.getXSSFColor().getARGBHex());
        assertEquals(FxtExcelTheme.SANS, headerFont.getFontName());
        assertEquals(10, headerFont.getFontHeightInPoints());

        XSSFCellStyle first = sheet.getRow(headerRow + 1).getCell(1).getCellStyle();
        XSSFCellStyle second = sheet.getRow(headerRow + 2).getCell(1).getCellStyle();
        assertTrue(FONTS.contains(first.getFont().getFontName()), sheet.getSheetName() + ": body font");
        assertEquals(10, first.getFont().getFontHeightInPoints());
        assertNull(first.getFillForegroundXSSFColor(), sheet.getSheetName() + ": first row plain");
        assertEquals("FF" + FxtExcelTheme.ZEBRA_FILL, second.getFillForegroundXSSFColor().getARGBHex(),
                sheet.getSheetName() + ": second row striped");
        assertEquals("FF" + FxtExcelTheme.RULE, first.getBottomBorderXSSFColor().getARGBHex());
    }

    private static void assertInfoSheet(XSSFSheet sheet) {
        assertNotNull(sheet);
        assertFalse(sheet.isDisplayGridlines());
        assertEquals("FF" + FxtExcelTheme.HEADER_FILL, sheet.getTabColor().getARGBHex());
        assertTitle(sheet.getRow(0).getCell(0));
        // first section heading two rows below the title
        XSSFFont section = sheet.getRow(2).getCell(0).getCellStyle().getFont();
        assertEquals("FF" + FxtExcelTheme.ACCENT, section.getXSSFColor().getARGBHex());
        assertEquals(FxtExcelTheme.INFO_LABEL_WIDTH, sheet.getColumnWidth(0));
    }

    private static void assertTitle(XSSFCell cell) {
        XSSFFont font = cell.getCellStyle().getFont();
        assertEquals(16, font.getFontHeightInPoints());
        assertTrue(font.getBold());
        assertEquals("FF" + FxtExcelTheme.STRONG, font.getXSSFColor().getARGBHex());
        assertEquals(FxtExcelTheme.SANS, font.getFontName());
    }
}
