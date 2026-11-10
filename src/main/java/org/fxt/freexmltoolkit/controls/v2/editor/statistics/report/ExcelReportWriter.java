package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Accent;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Role;

/**
 * Excel rendering (XSSF) in the shared house style ({@link FxtExcelTheme}): one sheet per section — a
 * title, the subject and timestamp as muted lines, the facts as label/value rows, then each table under a
 * section heading with the slate header row and zebra rows; toned rows get the accent's light fill, toned
 * facts coloured text. A section with a single table gets a frozen header and an auto-filter. Sheet names
 * are made safe and unique, cells are cut to Excel's 32 767-character limit and column widths are set
 * from the content (no {@code autoSizeColumn}, which needs AWT fonts).
 */
public final class ExcelReportWriter implements ReportWriter {

    private static final int MAX_WIDTH_CHARS = 80;

    @Override
    public void write(ReportModel model, Path target) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportMetadata.service().setExcelMetadata(workbook, model.title());
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            Set<String> usedNames = new HashSet<>();
            if (model.sections().isEmpty()) {
                Sheet sheet = workbook.createSheet("Report");
                FxtExcelTheme.tableSheet(sheet);
                theme.writeTitle(sheet, 0, model.title());
            }
            for (ReportSection section : model.sections()) {
                writeSection(workbook.createSheet(sheetName(section, usedNames)), theme, model, section);
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }
    }

    private static void writeSection(Sheet sheet, FxtExcelTheme theme, ReportModel model, ReportSection section) {
        FxtExcelTheme.tableSheet(sheet);
        int[] widths = new int[64];
        int rowNum = theme.writeTitle(sheet, 0, model.title() + " — " + section.title());
        if (!model.subject().isBlank()) {
            text(sheet.createRow(rowNum++), 0, model.subject(), theme.subtitle(), widths);
        }
        text(sheet.createRow(rowNum++), 0, "Generated: " + model.generatedAt().format(ReportWriters.TIMESTAMP),
                theme.subtitle(), widths);
        rowNum++;
        if (!section.facts().isEmpty()) {
            for (KeyValue fact : section.facts()) {
                Row row = sheet.createRow(rowNum++);
                text(row, 0, fact.key(), theme.label(), widths);
                Accent accent = accent(fact.tone());
                text(row, 1, fact.value(), accent == null ? theme.value() : theme.value(accent), widths);
            }
            rowNum++;
        }
        int headerRow = -1;
        int lastRow = -1;
        for (ReportTable table : section.tables()) {
            int lastColumn = Math.max(1, table.headers().size() - 1);
            rowNum = theme.writeSection(sheet, rowNum, table.title(), lastColumn);
            headerRow = rowNum;
            Row header = sheet.createRow(rowNum++);
            header.setHeightInPoints(22);
            for (int i = 0; i < table.headers().size(); i++) {
                text(header, i, table.headers().get(i), theme.header(), widths);
            }
            if (table.isEmpty()) {
                text(sheet.createRow(rowNum++), 0, "(none)", theme.body(Role.NOTE, false), widths);
            }
            for (ReportRow dataRow : table.rows()) {
                boolean stripe = (rowNum - headerRow) % 2 == 0;
                Row row = sheet.createRow(rowNum++);
                Accent accent = accent(dataRow.tone());
                CellStyle style = accent == null ? theme.body(Role.TEXT, stripe) : theme.accentRow(accent);
                for (int i = 0; i < dataRow.cells().size(); i++) {
                    text(row, i, dataRow.cells().get(i), style, widths);
                }
            }
            lastRow = rowNum - 1;
            rowNum++;
        }
        if (section.note() != null && !section.note().isBlank()) {
            text(sheet.createRow(rowNum), 0, section.note(), theme.note(), widths);
        }
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                sheet.setColumnWidth(i, Math.min(MAX_WIDTH_CHARS, Math.max(8, widths[i] + 2)) * 256);
            }
        }
        if (section.tables().size() == 1 && section.facts().isEmpty()) {
            ReportTable only = section.tables().get(0);
            FxtExcelTheme.finishTable(sheet, headerRow, only.isEmpty() ? headerRow : lastRow,
                    Math.max(0, only.headers().size() - 1));
        } else {
            FxtExcelTheme.printOnePageWide(sheet, true);
        }
    }

    /** @return the house-style accent of a report tone, or {@code null} for none */
    static Accent accent(Tone tone) {
        return switch (tone) {
            case NONE -> null;
            case ERROR -> Accent.ERROR;
            case WARNING -> Accent.WARNING;
            case INFO -> Accent.INFO;
            case SUGGESTION -> Accent.SUGGESTION;
            case OK -> Accent.OK;
        };
    }

    private static void text(Row row, int column, String value, CellStyle style, int[] widths) {
        String text = FxtExcelTheme.clip(value);
        FxtExcelTheme.cell(row, column, text, style);
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
}
