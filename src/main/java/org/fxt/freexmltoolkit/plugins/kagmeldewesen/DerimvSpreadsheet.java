/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.fxt.freexmltoolkit.plugins.regreport.RegReportHtml.esc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.plugins.regreport.RecordReader;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme;

/**
 * The DeRiMV report as a spreadsheet. Risk management usually keeps the report as CSV or Excel in the
 * FMA's former CSV layout - row 1: management company; bank code; period {@code JJJJQ} (20164 = Q4/2016),
 * then one row per fund with the columns A-G FMA code, ISIN, fund name, exposure, maximum, method, own
 * limit (CSV with semicolons and decimal comma) - and IT turns it into the XML. {@link #read} reads that
 * layout and the workbook {@link #writeXlsx} writes (sheets "Funds" with a header row and "Info");
 * {@link #toXml} writes the report. UI-free.
 */
public final class DerimvSpreadsheet {

    /** The root element's attributes. */
    public record Header(String company, String blz, String year, String quarter) {
    }

    /** One {@code InvestmentFonds}; values as they go into the XML (empty = element left out). */
    public record Fund(String fmaCode, String isin, String name, String risk, String max, String method, String limit) {
    }

    /** A report: header and funds. */
    public record Report(Header header, List<Fund> funds) {
    }

    /** Something in a row of the spreadsheet that needs a look (1-based row number). */
    public record Issue(int row, String message) {
    }

    /** The result of an import: the report (also when rows have issues) and the issues. */
    public record Import(Report report, List<Issue> issues) {
    }

    static final String FUNDS_SHEET = "Funds";
    static final String INFO_SHEET = "Info";
    static final String[] COLUMNS = {"FMACode", "ISIN", "FondsName", "Risikoangabe", "MaxRisikoangabe",
            "Berechnungsmethode", "GrenzenGesamtrisiko"};
    private static final int NAME_MAX = 100;
    private static final Pattern PERIOD = Pattern.compile(
            "(?:(\\d{4})\\s*[-/ ]?\\s*Q?([1-4]))|(?:Q([1-4])\\s*[-/ ]\\s*(\\d{4}))", Pattern.CASE_INSENSITIVE);

    private DerimvSpreadsheet() {
    }

    // ------------------------------------------------------------------ import

    /** Reads a {@code .csv}/{@code .txt} or an Excel workbook ({@code .xlsx}/{@code .xls}). */
    public static Import read(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".xlsm")) {
            try (InputStream in = Files.newInputStream(file)) {
                return readWorkbook(in);
            }
        }
        return readCsv(Files.readAllBytes(file));
    }

    /** Reads the CSV layout (semicolons, decimal comma; UTF-8 or Windows-1252). */
    public static Import readCsv(byte[] bytes) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : decode(bytes).split("\\r?\\n", -1)) {
            List<String> cells = new ArrayList<>();
            for (String cell : line.split(";", -1)) {
                cells.add(unquote(cell.strip()));
            }
            rows.add(cells);
        }
        return fromRows(rows);
    }

    /** Reads a workbook: the one {@link #writeXlsx} writes, or the CSV layout on its first sheet. */
    public static Import readWorkbook(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Sheet funds = workbook.getSheet(FUNDS_SHEET);
            Sheet info = workbook.getSheet(INFO_SHEET);
            if (funds != null && info != null) {
                List<List<String>> rows = new ArrayList<>();
                rows.add(List.of(infoValue(info, "Verwaltungsgesellschaft", formatter), infoValue(info, "BLZ", formatter),
                        infoValue(info, "Jahr", formatter) + infoValue(info, "Quartal", formatter)));
                rows.addAll(cells(funds, formatter));
                return fromRows(rows);
            }
            return fromRows(cells(workbook.getSheetAt(0), formatter));
        }
    }

    private static String infoValue(Sheet info, String label, DataFormatter formatter) {
        for (Row row : info) {
            Cell first = row.getCell(0);
            if (first != null && label.equals(formatter.formatCellValue(first).strip())) {
                return text(row.getCell(1), formatter);
            }
        }
        return "";
    }

    private static List<List<String>> cells(Sheet sheet, DataFormatter formatter) {
        List<List<String>> rows = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>();
            if (row != null) {
                for (int c = 0; c < 7; c++) {
                    cells.add(text(row.getCell(c), formatter));
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    /** @return a cell's text; numbers without grouping and with a dot (exactly as stored, not as displayed) */
    private static String text(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).strip();
    }

    /**
     * @param rows the cells per row; row 0 is the header (company, bank code, period)
     */
    private static Import fromRows(List<List<String>> rows) {
        List<Issue> issues = new ArrayList<>();
        List<String> head = rows.isEmpty() ? List.of() : rows.getFirst();
        String company = cell(head, 0);
        String blz = cell(head, 1).replaceAll("\\s", "");
        if (blz.matches("\\d{1,5}")) {
            blz = "0".repeat(5 - blz.length()) + blz;
        } else {
            issues.add(new Issue(1, "Bank code \"" + blz + "\" is not 5 digits."));
        }
        String year = "";
        String quarter = "";
        Matcher period = PERIOD.matcher(cell(head, 2));
        if (period.matches()) {
            year = period.group(1) != null ? period.group(1) : period.group(4);
            quarter = period.group(2) != null ? period.group(2) : period.group(3);
        } else {
            issues.add(new Issue(1, "Period \"" + cell(head, 2) + "\" is not JJJJQ (20164 = Q4/2016)."));
        }
        if (company.isEmpty()) {
            issues.add(new Issue(1, "The management company's name (A1) is empty."));
        }
        List<Fund> funds = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            String isin = cell(row, 1).toUpperCase(Locale.ROOT);
            if (isin.equals("ISIN")) {
                continue; // a header row
            }
            int line = r + 1;
            String name = cell(row, 2);
            if (name.length() > NAME_MAX) {
                issues.add(new Issue(line, "Fund name shortened to " + NAME_MAX + " characters."));
                name = name.substring(0, NAME_MAX).strip();
            }
            String method = method(cell(row, 5));
            if (!Set.of("Comm", "aVaR", "rVaR").contains(method)) {
                issues.add(new Issue(line, "Method \"" + cell(row, 5) + "\" is not Comm, aVaR or rVaR."));
            }
            if (cell(row, 0).isEmpty()) {
                issues.add(new Issue(line, "FMA code (A) is empty."));
            }
            if (isin.isEmpty()) {
                issues.add(new Issue(line, "ISIN (B) is empty."));
            }
            funds.add(new Fund(cell(row, 0), isin, name, number(row, 3, "Risikoangabe", line, true, issues),
                    number(row, 4, "MaxRisikoangabe", line, true, issues), method,
                    number(row, 6, "GrenzenGesamtrisiko", line, false, issues)));
        }
        return new Import(new Report(new Header(company, blz, year, quarter), funds), issues);
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() && row.get(column) != null ? row.get(column).strip() : "";
    }

    private static String method(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "comm", "commitment" -> "Comm";
            case "avar" -> "aVaR";
            case "rvar" -> "rVaR";
            default -> value.strip();
        };
    }

    /** @return the value with two decimals, the text itself when it is no number, or empty */
    private static String number(List<String> row, int column, String field, int line, boolean required, List<Issue> issues) {
        String raw = cell(row, column).replace("%", "").replace(" ", "");
        if (raw.isEmpty()) {
            if (required) {
                issues.add(new Issue(line, field + " is empty."));
            }
            return "";
        }
        // the later of "," and "." is the decimal separator, the other one groups thousands
        String normalised = raw.lastIndexOf(',') > raw.lastIndexOf('.')
                ? raw.replace(".", "").replace(',', '.') : raw.replace(",", "");
        try {
            return new BigDecimal(normalised).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            issues.add(new Issue(line, field + " \"" + raw + "\" is not a number."));
            return raw;
        }
    }

    private static String unquote(String cell) {
        if (cell.length() >= 2 && cell.startsWith("\"") && cell.endsWith("\"")) {
            return cell.substring(1, cell.length() - 1).replace("\"\"", "\"");
        }
        return cell;
    }

    /** @return the text: UTF-8 (with or without BOM) when it decodes cleanly, else Windows-1252 */
    static String decode(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    // ------------------------------------------------------------------ XML

    /** @return the report as XML for the FMA ({@code Derivatemeldung}) */
    public static String toXml(Report report) {
        Header h = report.header();
        StringBuilder xml = new StringBuilder(256 + report.funds().size() * 400);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Derivatemeldung Verwaltungsgesellschaft=\"")
                .append(esc(h.company())).append("\" BLZ=\"").append(esc(h.blz())).append("\" Jahr=\"").append(esc(h.year()))
                .append("\" Quartal=\"").append(esc(h.quarter())).append("\"\n                 ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"Derivatemeldung.xsd\">\n");
        for (Fund f : report.funds()) {
            xml.append("   <InvestmentFonds>\n");
            element(xml, "FMACode", f.fmaCode(), true);
            element(xml, "ISIN", f.isin(), true);
            element(xml, "FondsName", f.name(), true);
            element(xml, "Risikoangabe", f.risk(), false);
            element(xml, "MaxRisikoangabe", f.max(), false);
            element(xml, "Berechnungsmethode", f.method(), true);
            element(xml, "GrenzenGesamtrisiko", f.limit(), false);
            xml.append("   </InvestmentFonds>\n");
        }
        return xml.append("</Derivatemeldung>\n").toString();
    }

    /** Writes an element; an empty optional value is left out, an empty mandatory one stays (the schema reports it). */
    private static void element(StringBuilder xml, String name, String value, boolean keepEmpty) {
        if (value.isEmpty() && !keepEmpty) {
            return;
        }
        xml.append("      <").append(name).append('>').append(esc(value)).append("</").append(name).append(">\n");
    }

    /** @return the report in an XML text */
    public static Report fromXml(String xml) throws XMLStreamException {
        Header[] header = new Header[1];
        List<Fund> funds = new ArrayList<>();
        RecordReader.read(xml, Set.of(DerimvRules.ROOT + "/InvestmentFonds"), root -> {
            var a = root.attributes();
            header[0] = new Header(a.getOrDefault("Verwaltungsgesellschaft", ""), a.getOrDefault("BLZ", ""),
                    a.getOrDefault("Jahr", ""), a.getOrDefault("Quartal", ""));
        }, rec -> funds.add(new Fund(rec.text("FMACode"), rec.text("ISIN"), rec.text("FondsName"), rec.text("Risikoangabe"),
                rec.text("MaxRisikoangabe"), rec.text("Berechnungsmethode"), rec.text("GrenzenGesamtrisiko"))));
        return new Report(header[0] == null ? new Header("", "", "", "") : header[0], funds);
    }

    // ------------------------------------------------------------------ export

    /** @return the report in the CSV layout (semicolons, decimal comma, UTF-8 with BOM for Excel) */
    public static byte[] toCsv(Report report) {
        StringBuilder csv = new StringBuilder("﻿");
        Header h = report.header();
        csv.append(csvCell(h.company())).append(';').append(h.blz()).append(';').append(h.year()).append(h.quarter()).append("\r\n");
        for (Fund f : report.funds()) {
            csv.append(String.join(";", csvCell(f.fmaCode()), csvCell(f.isin()), csvCell(f.name()), comma(f.risk()),
                    comma(f.max()), csvCell(f.method()), comma(f.limit()))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String comma(String number) {
        return number.replace('.', ',');
    }

    private static String csvCell(String value) {
        return value.contains(";") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /** Writes the report as workbook in the application's Excel style: "Funds" (one row per fund) and "Info". */
    public static void writeXlsx(Report report, String sourceName, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            Sheet funds = workbook.createSheet(FUNDS_SHEET);
            FxtExcelTheme.tableSheet(funds);
            theme.writeHeaderRow(funds, 0, COLUMNS, c -> c == 5);
            int r = 1;
            for (Fund f : report.funds()) {
                boolean stripe = r % 2 == 0;
                Row row = funds.createRow(r++);
                FxtExcelTheme.cell(row, 0, f.fmaCode(), theme.body(FxtExcelTheme.Role.MONO, stripe));
                FxtExcelTheme.cell(row, 1, f.isin(), theme.body(FxtExcelTheme.Role.MONO, stripe));
                FxtExcelTheme.cell(row, 2, f.name(), theme.body(FxtExcelTheme.Role.TEXT, stripe));
                numberCell(row, 3, f.risk(), theme, stripe);
                numberCell(row, 4, f.max(), theme, stripe);
                FxtExcelTheme.cell(row, 5, f.method(), theme.body(FxtExcelTheme.Role.CENTER, stripe));
                numberCell(row, 6, f.limit(), theme, stripe);
            }
            int[] widths = {4200, 4200, 12000, 3600, 4200, 4600, 5200};
            for (int c = 0; c < widths.length; c++) {
                funds.setColumnWidth(c, widths[c]);
            }
            FxtExcelTheme.finishTable(funds, 0, r - 1, COLUMNS.length - 1);

            Sheet info = workbook.createSheet(INFO_SHEET);
            FxtExcelTheme.infoSheet(info);
            int i = theme.writeTitle(info, 0, "Derivative risk report (4. DeRiMV)");
            i = theme.writeSection(info, i + 1, "Report", 1);
            Header h = report.header();
            i = theme.writeLabelValue(info, i, "Verwaltungsgesellschaft", h.company());
            i = theme.writeLabelValue(info, i, "BLZ", h.blz());
            i = theme.writeLabelValue(info, i, "Jahr", h.year());
            i = theme.writeLabelValue(info, i, "Quartal", h.quarter());
            i = theme.writeLabelValue(info, i, "Funds", String.valueOf(report.funds().size()));
            i = theme.writeSection(info, i + 1, "Source", 1);
            theme.writeLabelValue(info, i, "File", sourceName == null ? "" : sourceName);
            workbook.write(out);
        }
    }

    private static void numberCell(Row row, int column, String value, FxtExcelTheme theme, boolean stripe) {
        if (value.isEmpty()) {
            FxtExcelTheme.cell(row, column, "", theme.body(FxtExcelTheme.Role.TEXT, stripe));
            return;
        }
        try {
            FxtExcelTheme.cell(row, column, new BigDecimal(value).doubleValue(), theme.body(FxtExcelTheme.Role.TEXT, stripe));
        } catch (NumberFormatException e) {
            FxtExcelTheme.cell(row, column, value, theme.body(FxtExcelTheme.Role.TEXT, stripe));
        }
    }
}
