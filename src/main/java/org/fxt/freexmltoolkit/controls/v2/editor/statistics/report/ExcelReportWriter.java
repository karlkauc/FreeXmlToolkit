package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel rendering (XSSF): one sheet per section — a title row, the facts as key/value rows,
 * then each table with a bold header row; toned rows get a light fill. Sheet names are made
 * safe and unique, cells are truncated to Excel's 32 767-character limit and column widths are
 * set from the content (no {@code autoSizeColumn}, which needs AWT fonts).
 */
public final class ExcelReportWriter implements ReportWriter {

    private static final int MAX_CELL = 32_767;
    private static final int MAX_WIDTH_CHARS = 80;

    @Override
    public void write(ReportModel model, Path target) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportMetadata.service().setExcelMetadata(workbook, model.title());
            Styles styles = new Styles(workbook);
            Set<String> usedNames = new HashSet<>();
            if (model.sections().isEmpty()) {
                Sheet sheet = workbook.createSheet("Report");
                title(sheet, 0, model.title(), styles.title);
            }
            for (ReportSection section : model.sections()) {
                Sheet sheet = workbook.createSheet(sheetName(section, usedNames));
                int[] widths = new int[64];
                int rowNum = 0;
                rowNum = title(sheet, rowNum, model.title() + " — " + section.title(), styles.title);
                if (!model.subject().isBlank()) {
                    text(sheet.createRow(rowNum++), 0, model.subject(), null, widths);
                }
                text(sheet.createRow(rowNum++), 0, "Generated: " + model.generatedAt().format(ReportWriters.TIMESTAMP), null, widths);
                rowNum++;
                if (!section.facts().isEmpty()) {
                    for (KeyValue fact : section.facts()) {
                        Row row = sheet.createRow(rowNum++);
                        text(row, 0, fact.key(), styles.label, widths);
                        text(row, 1, fact.value(), styles.forTone(fact.tone(), false), widths);
                    }
                    rowNum++;
                }
                for (ReportTable table : section.tables()) {
                    text(sheet.createRow(rowNum++), 0, table.title(), styles.section, widths);
                    Row header = sheet.createRow(rowNum++);
                    for (int i = 0; i < table.headers().size(); i++) {
                        text(header, i, table.headers().get(i), styles.header, widths);
                    }
                    if (table.isEmpty()) {
                        text(sheet.createRow(rowNum++), 0, "(none)", styles.muted, widths);
                    }
                    for (ReportRow dataRow : table.rows()) {
                        Row row = sheet.createRow(rowNum++);
                        CellStyle style = styles.forTone(dataRow.tone(), true);
                        for (int i = 0; i < dataRow.cells().size(); i++) {
                            text(row, i, dataRow.cells().get(i), style, widths);
                        }
                    }
                    rowNum++;
                }
                if (section.note() != null && !section.note().isBlank()) {
                    text(sheet.createRow(rowNum), 0, section.note(), styles.muted, widths);
                }
                for (int i = 0; i < widths.length; i++) {
                    if (widths[i] > 0) {
                        sheet.setColumnWidth(i, Math.min(MAX_WIDTH_CHARS, Math.max(8, widths[i] + 2)) * 256);
                    }
                }
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }
    }

    private static int title(Sheet sheet, int rowNum, String text, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        return rowNum + 1;
    }

    private static void text(Row row, int column, String value, CellStyle style, int[] widths) {
        Cell cell = row.createCell(column);
        String text = value == null ? "" : value;
        if (text.length() > MAX_CELL) {
            text = text.substring(0, MAX_CELL - 1) + "…";
        }
        cell.setCellValue(text);
        if (style != null) {
            cell.setCellStyle(style);
        }
        if (column < widths.length) {
            int longestLine = 0;
            for (String line : text.split("\n")) {
                longestLine = Math.max(longestLine, line.length());
            }
            widths[column] = Math.max(widths[column], Math.min(longestLine, MAX_WIDTH_CHARS));
        }
    }

    static String sheetName(ReportSection section, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(section.title().isBlank() ? section.id() : section.title());
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }
        String name = base;
        int n = 2;
        while (!used.add(name)) {
            String suffix = " (" + n++ + ")";
            name = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
        }
        return name;
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle label;
        final CellStyle muted;
        private final XSSFWorkbook workbook;
        private final java.util.Map<String, CellStyle> toned = new java.util.HashMap<>();

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            title = bold(workbook, 14);
            section = bold(workbook, 11);
            header = bold(workbook, 10);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            label = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            label.setFont(labelFont);
            muted = workbook.createCellStyle();
            Font mutedFont = workbook.createFont();
            mutedFont.setItalic(true);
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            muted.setFont(mutedFont);
        }

        CellStyle forTone(Tone tone, boolean fill) {
            if (tone == Tone.NONE) {
                return null;
            }
            return toned.computeIfAbsent(tone.name() + fill, k -> {
                XSSFCellStyle style = workbook.createCellStyle();
                if (fill) {
                    style.setFillForegroundColor(new XSSFColor(rgb(tone.background()), null));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                } else {
                    XSSFFont font = workbook.createFont();
                    font.setBold(true);
                    font.setColor(new XSSFColor(rgb(tone.foreground()), null));
                    style.setFont(font);
                }
                return style;
            });
        }

        private static CellStyle bold(XSSFWorkbook workbook, int size) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) size);
            style.setFont(font);
            return style;
        }

        private static byte[] rgb(String hex) {
            int value = Integer.parseInt(hex.substring(1), 16);
            return new byte[]{(byte) (value >> 16), (byte) (value >> 8), (byte) value};
        }
    }

}
