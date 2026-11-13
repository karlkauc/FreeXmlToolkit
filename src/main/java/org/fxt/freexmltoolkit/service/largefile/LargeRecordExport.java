package org.fxt.freexmltoolkit.service.largefile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Role;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;

/**
 * Exports all children of one element of a large XML file as records, one row per child, the way
 * the viewer's records table shows them: columns {@code #}, {@code Element}, the attributes
 * ({@code @name}) and the texts of simple child elements, and {@code Text} for children with only
 * text. Two streaming passes: the first collects the columns, the second writes the rows, so memory
 * stays flat whatever the number of children.
 */
public final class LargeRecordExport {

    /** At most this many field columns. */
    public static final int MAX_COLUMNS = 500;
    /** Excel's rows per sheet (header included). */
    static final int EXCEL_ROWS = 1_048_576;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** The format. */
    public enum Format { CSV, XLSX }

    /**
     * The outcome.
     *
     * @param rows    the records written
     * @param columns the field columns written
     * @param sheets  the data sheets (XLSX), 1 for CSV
     */
    public record Result(long rows, int columns, int sheets) {
    }

    private LargeRecordExport() {
    }

    /**
     * Writes the children of {@code parent} to {@code target}.
     *
     * @param progress receives the offset reached in the file, for both passes (may be {@code null})
     */
    public static Result export(LargeXmlStructure structure, Node parent, Format format, Path target,
                                BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        return export(structure, parent, format, target, EXCEL_ROWS, cancelled, progress);
    }

    /** As {@link #export}, with the rows per sheet given (tests). */
    static Result export(LargeXmlStructure structure, Node parent, Format format, Path target, int rowsPerSheet,
                         BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        boolean[] anyText = {false};
        structure.forEachChild(parent, null, FxtExcelTheme.MAX_CELL_LENGTH, child -> {
            for (String key : child.fields().keySet()) {
                if (keys.size() >= MAX_COLUMNS) {
                    break;
                }
                keys.add(key);
            }
            anyText[0] |= !child.text().isEmpty();
            return true;
        }, cancelled, progress);
        List<String> headers = new ArrayList<>();
        headers.add("#");
        headers.add("Element");
        headers.addAll(keys);
        if (anyText[0]) {
            headers.add("Text");
        }
        return format == Format.CSV
                ? writeCsv(structure, parent, target, headers, List.copyOf(keys), anyText[0], cancelled, progress)
                : writeXlsx(structure, parent, target, headers, List.copyOf(keys), anyText[0], rowsPerSheet, cancelled,
                progress);
    }

    private static List<String> values(Node child, List<String> keys, boolean text) {
        List<String> values = new ArrayList<>(keys.size() + 3);
        values.add(Long.toString(child.index() + 1));
        values.add(child.name());
        for (String key : keys) {
            values.add(child.fields().getOrDefault(key, ""));
        }
        if (text) {
            values.add(child.text());
        }
        return values;
    }

    private static Result writeCsv(LargeXmlStructure structure, Node parent, Path target, List<String> headers,
                                   List<String> keys, boolean text, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        long[] rows = {0};
        IOException[] failure = new IOException[1];
        try (Writer out = new BufferedWriter(Files.newBufferedWriter(target, StandardCharsets.UTF_8), 1 << 16)) {
            out.write('﻿'); // lets spreadsheet tools detect UTF-8
            writeCsvLine(out, headers);
            structure.forEachChild(parent, null, FxtExcelTheme.MAX_CELL_LENGTH, child -> {
                try {
                    writeCsvLine(out, values(child, keys, text));
                    rows[0]++;
                    return true;
                } catch (IOException e) {
                    failure[0] = e;
                    return false;
                }
            }, cancelled, progress);
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        return new Result(rows[0], keys.size(), 1);
    }

    /** Writes one RFC 4180 line (CRLF). */
    static void writeCsvLine(Writer out, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(csv(values.get(i)));
        }
        out.write("\r\n");
    }

    /** @return the value quoted when it needs it */
    static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0 || Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1));
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    private static Result writeXlsx(LargeXmlStructure structure, Node parent, Path target, List<String> headers,
                                    List<String> keys, boolean text, int rowsPerSheet, BooleanSupplier cancelled,
                                    LongConsumer progress) throws IOException {
        long[] rows = {0};
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            workbook.setCompressTempFiles(true);
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            Sheet info = workbook.createSheet("Summary");
            String[] headerRow = headers.toArray(String[]::new);
            int columns = headerRow.length;
            List<Sheet> sheets = new ArrayList<>();
            int[] rowNum = {0};
            Sheet[] current = {null};
            Runnable newSheet = () -> {
                if (current[0] != null) {
                    finish(current[0], rowNum[0], columns);
                }
                Sheet sheet = workbook.createSheet(sheets.isEmpty() ? "Records" : "Records " + (sheets.size() + 1));
                FxtExcelTheme.tableSheet(sheet);
                rowNum[0] = theme.writeHeaderRow(sheet, 0, headerRow, i -> i == 0);
                sheets.add(sheet);
                current[0] = sheet;
            };
            newSheet.run();
            structure.forEachChild(parent, null, FxtExcelTheme.MAX_CELL_LENGTH, child -> {
                if (rowNum[0] >= rowsPerSheet) {
                    newSheet.run();
                }
                boolean stripe = rowNum[0] % 2 == 0;
                Row row = current[0].createRow(rowNum[0]++);
                List<String> values = values(child, keys, text);
                FxtExcelTheme.cell(row, 0, child.index() + 1d, theme.body(Role.CENTER, stripe));
                FxtExcelTheme.cell(row, 1, FxtExcelTheme.safe(values.get(1)), theme.body(Role.NAME, stripe));
                for (int i = 2; i < values.size(); i++) {
                    FxtExcelTheme.cell(row, i, FxtExcelTheme.safe(values.get(i)), theme.body(Role.TEXT, stripe));
                }
                rows[0]++;
                return true;
            }, cancelled, progress);
            finish(current[0], rowNum[0], columns);
            writeInfo(info, theme, structure, parent, rows[0], keys.size(), sheets.size());
            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
            return new Result(rows[0], keys.size(), sheets.size());
        }
    }

    private static void finish(Sheet sheet, int nextRow, int columns) {
        sheet.setColumnWidth(0, 2500);
        sheet.setColumnWidth(1, 5000);
        for (int i = 2; i < columns; i++) {
            sheet.setColumnWidth(i, 5000);
        }
        FxtExcelTheme.finishTable(sheet, 0, Math.max(0, nextRow - 1), columns - 1);
    }

    private static void writeInfo(Sheet sheet, FxtExcelTheme theme, LargeXmlStructure structure, Node parent,
                                  long rows, int columns, int sheets) {
        FxtExcelTheme.infoSheet(sheet);
        int row = theme.writeTitle(sheet, 0, "Records — " + FxtExcelTheme.safe(parent.xpath()));
        row++;
        row = theme.writeSection(sheet, row, "Export Information", 1);
        row = theme.writeLabelValue(sheet, row, "Source File",
                FxtExcelTheme.safe(String.valueOf(structure.index().path().getFileName())));
        row = theme.writeLabelValue(sheet, row, "Element", FxtExcelTheme.safe(parent.xpath()));
        row = theme.writeLabelValue(sheet, row, "Export Timestamp", TIMESTAMP.format(LocalDateTime.now()));
        row = theme.writeLabelValue(sheet, row, "Generated by", "FreeXmlToolkit");
        row++;
        row = theme.writeSection(sheet, row, "Counts", 1);
        row = theme.writeLabelValue(sheet, row, "Records", String.valueOf(rows));
        row = theme.writeLabelValue(sheet, row, "Field Columns", String.valueOf(columns));
        theme.writeLabelValue(sheet, row, "Record Sheets", String.valueOf(sheets));
    }
}
