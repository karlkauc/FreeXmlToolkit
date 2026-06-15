package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;

/**
 * Exports the Validation activity's {@link ValidationProblem} list to an Excel
 * (.xlsx) workbook: a "Summary" sheet (counts per severity / source) and a
 * "Problems" sheet (one row per problem, with auto-filter and a frozen header).
 */
public final class ValidationExcelExporter {

    private static final Logger logger = LogManager.getLogger(ValidationExcelExporter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ValidationExcelExporter() {
    }

    /**
     * Writes the given problems to {@code outputPath} as an .xlsx workbook.
     *
     * @param problems       the problems to export (may be empty)
     * @param sourceFileName the validated document's name (for the summary), or {@code null}
     * @param outputPath     the target file
     * @throws IOException if writing fails
     */
    public static void export(List<ValidationProblem> problems, String sourceFileName, Path outputPath)
            throws IOException {
        logger.info("Exporting {} validation problem(s) to Excel: {}", problems.size(), outputPath);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            metadataService.setExcelMetadata(workbook, "Validation Problems");

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle sectionStyle = sectionStyle(workbook);
            CellStyle errorStyle = severityStyle(workbook, IndexedColors.RED);
            CellStyle warningStyle = severityStyle(workbook, IndexedColors.ORANGE);

            long errors = problems.stream().filter(p -> !"warning".equalsIgnoreCase(p.severity())).count();
            long warnings = problems.size() - errors;

            // --- Summary sheet ---------------------------------------------------
            Sheet summary = workbook.createSheet("Summary");
            int row = 0;
            Cell title = summary.createRow(row++).createCell(0);
            title.setCellValue("Validation Problems");
            title.setCellStyle(headerStyle);
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            if (sourceFileName != null && !sourceFileName.isBlank()) {
                writeRow(summary, row++, "Source file", sourceFileName);
            }
            writeRow(summary, row++, "Generated", DATE_FORMATTER.format(LocalDateTime.now()));
            row++;
            row = writeSection(summary, row, "Counts", sectionStyle);
            writeRow(summary, row++, "Total problems", String.valueOf(problems.size()));
            writeRow(summary, row++, "Errors", String.valueOf(errors));
            writeRow(summary, row++, "Warnings", String.valueOf(warnings));
            summary.setColumnWidth(0, 8000);
            summary.setColumnWidth(1, 14000);

            // --- Problems sheet --------------------------------------------------
            Sheet sheet = workbook.createSheet("Problems");
            String[] headers = {"#", "Source", "Severity", "Line", "Message"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (ValidationProblem problem : problems) {
                Row dataRow = sheet.createRow(r);
                dataRow.createCell(0).setCellValue(r);
                dataRow.createCell(1).setCellValue(problem.source() != null ? problem.source() : "");
                boolean warning = "warning".equalsIgnoreCase(problem.severity());
                Cell severityCell = dataRow.createCell(2);
                severityCell.setCellValue(problem.severity() != null ? problem.severity() : "");
                severityCell.setCellStyle(warning ? warningStyle : errorStyle);
                if (problem.line() > 0) {
                    dataRow.createCell(3).setCellValue(problem.line());
                } else {
                    dataRow.createCell(3).setCellValue("");
                }
                dataRow.createCell(4).setCellValue(problem.message() != null ? problem.message() : "");
                r++;
            }
            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 4000);
            sheet.setColumnWidth(2, 4000);
            sheet.setColumnWidth(3, 2500);
            sheet.setColumnWidth(4, 24000);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, headers.length - 1));

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }

        logger.info("Excel export completed: {}", outputPath);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle sectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle severityStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(color.getIndex());
        style.setFont(font);
        return style;
    }

    private static int writeSection(Sheet sheet, int rowNum, String title, CellStyle style) {
        Cell cell = sheet.createRow(rowNum).createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 1));
        return rowNum + 1;
    }

    private static void writeRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
