package org.fxt.freexmltoolkit.service.excel;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.IntPredicate;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * The one look of every Excel workbook FreeXmlToolkit writes, so an exported file is recognisable at a
 * glance: Calibri 10, a dark slate header row with white text, zebra rows with a hairline rule instead of
 * boxed cells, no gridlines, Consolas for XPaths and types, status as coloured text, a 16 pt title and
 * sky-blue section headings on the info sheets, coloured sheet tabs and one-page-wide printing.
 * <p>
 * One instance per workbook ({@code .xlsx}, streaming or not); styles and fonts are created on first use
 * and shared, since a workbook holds a limited number of them. The palette is Tailwind slate/sky, the
 * same as the HTML documentation.
 */
public final class FxtExcelTheme {

    /** slate-800: header fill, info-sheet tabs. */
    public static final String HEADER_FILL = "1E293B";
    /** slate-50: every second data row. */
    public static final String ZEBRA_FILL = "F8FAFC";
    /** slate-200: rule below each row. */
    public static final String RULE = "E2E8F0";
    /** slate-700: body text. */
    public static final String TEXT = "334155";
    /** slate-900: titles, names and XPaths. */
    public static final String STRONG = "0F172A";
    /** slate-500: counters, labels, notes. */
    public static final String MUTED = "64748B";
    /** slate-400: "No", empty values. */
    public static final String FAINT = "94A3B8";
    /** sky-700: section titles. */
    public static final String ACCENT = "0369A1";
    /** sky-600: section rules and the table sheet tabs. */
    public static final String ACCENT_RULE = "0284C7";

    /** The font of all text. */
    public static final String SANS = "Calibri";
    /** The font of XPaths, types and code. */
    public static final String MONO = "Consolas";

    /** Excel's limit for the text of one cell. */
    public static final int MAX_CELL_LENGTH = 32_767;

    /** Width of the label column of an info sheet (1/256 character). */
    public static final int INFO_LABEL_WIDTH = 10_000;
    /** Width of the value column of an info sheet (1/256 character). */
    public static final int INFO_VALUE_WIDTH = 20_000;

    /** How a data cell looks; every role exists plain and striped. */
    public enum Role {
        /** Body text, left. */
        TEXT,
        /** Body text, left, wrapped. */
        WRAP,
        /** Muted text, left (sources, secondary columns). */
        MUTED,
        /** Muted text, centred (counters, occurs, line numbers). */
        CENTER,
        /** Faint text, centred ("No"). */
        FAINT,
        /** Monospace body text (types, patterns). */
        MONO,
        /** Monospace strong text (XPaths). */
        XPATH,
        /** Bold strong text (names). */
        NAME,
        /** Muted italic text ("(none)"). */
        NOTE
    }

    /** A status colour: coloured text, or a light fill behind a whole row. */
    public enum Accent {
        /** red-700 on red-50. */
        ERROR("B91C1C", "FEF2F2"),
        /** amber-700 on amber-50. */
        WARNING("B45309", "FFFBEB"),
        /** green-700 on green-50 (also "Mandatory: Yes"). */
        OK("15803D", "F0FDF4"),
        /** sky-700 on sky-50. */
        INFO("0369A1", "F0F9FF"),
        /** yellow-700 on yellow-50. */
        SUGGESTION("A16207", "FEFCE8");

        private final String text;
        private final String fill;

        Accent(String text, String fill) {
            this.text = text;
            this.fill = fill;
        }

        /** @return the text colour as {@code RRGGBB} */
        public String text() {
            return text;
        }

        /** @return the light row fill as {@code RRGGBB} */
        public String fill() {
            return fill;
        }
    }

    private final Workbook workbook;
    private final Map<String, XSSFFont> fonts = new HashMap<>();
    private final Map<String, CellStyle> styles = new HashMap<>();

    /** @param workbook an {@code .xlsx} workbook ({@code XSSFWorkbook} or {@code SXSSFWorkbook}) */
    public FxtExcelTheme(Workbook workbook) {
        this.workbook = workbook;
    }

    // ------------------------------------------------------------------ styles

    /** @return the header cell of a table: white bold text on slate-800 */
    public CellStyle header() {
        return styles.computeIfAbsent("header", k -> createHeader(HorizontalAlignment.LEFT));
    }

    /** @return the header over a centred column */
    public CellStyle headerCenter() {
        return styles.computeIfAbsent("headerCenter", k -> createHeader(HorizontalAlignment.CENTER));
    }

    /** @return the 16 pt title of a sheet */
    public CellStyle title() {
        return styles.computeIfAbsent("title", k -> {
            XSSFCellStyle style = style(font(SANS, 16, true, false, STRONG));
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        });
    }

    /** @return a muted line below the title (subject, timestamp) */
    public CellStyle subtitle() {
        return styles.computeIfAbsent("subtitle", k -> style(font(SANS, 10, false, false, MUTED)));
    }

    /** @return a section heading: sky-700 bold with a sky rule below */
    public CellStyle section() {
        return styles.computeIfAbsent("section", k -> {
            XSSFCellStyle style = style(font(SANS, 11, true, false, ACCENT));
            style.setVerticalAlignment(VerticalAlignment.BOTTOM);
            style.setBorderBottom(BorderStyle.MEDIUM);
            style.setBottomBorderColor(color(ACCENT_RULE));
            return style;
        });
    }

    /** @return the label column of an info sheet */
    public CellStyle label() {
        return styles.computeIfAbsent("label", k -> {
            XSSFCellStyle style = ruled(style(font(SANS, 10, true, false, MUTED)));
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        });
    }

    /** @return the value column of an info sheet */
    public CellStyle value() {
        return styles.computeIfAbsent("value", k -> createValue(font(SANS, 10, false, false, TEXT)));
    }

    /** @return a value of an info sheet in a status colour (bold) */
    public CellStyle value(Accent accent) {
        return styles.computeIfAbsent("value|" + accent, k -> createValue(font(SANS, 10, true, false, accent.text())));
    }

    /** @return a muted italic note below a table, without a rule */
    public CellStyle note() {
        return styles.computeIfAbsent("note", k -> style(font(SANS, 10, false, true, MUTED)));
    }

    /**
     * @param role   how the cell looks
     * @param stripe whether the row is a striped (every second) row
     * @return the style of a data cell
     */
    public CellStyle body(Role role, boolean stripe) {
        return styles.computeIfAbsent("body|" + role + "|" + stripe, k -> switch (role) {
            case TEXT -> body(font(SANS, 10, false, false, TEXT), stripe, HorizontalAlignment.LEFT, false);
            case WRAP -> body(font(SANS, 10, false, false, TEXT), stripe, HorizontalAlignment.LEFT, true);
            case MUTED -> body(font(SANS, 10, false, false, MUTED), stripe, HorizontalAlignment.LEFT, false);
            case CENTER -> body(font(SANS, 10, false, false, MUTED), stripe, HorizontalAlignment.CENTER, false);
            case FAINT -> body(font(SANS, 10, false, false, FAINT), stripe, HorizontalAlignment.CENTER, false);
            case MONO -> body(font(MONO, 10, false, false, TEXT), stripe, HorizontalAlignment.LEFT, false);
            case XPATH -> body(font(MONO, 10, false, false, STRONG), stripe, HorizontalAlignment.LEFT, false);
            case NAME -> body(font(SANS, 10, true, false, STRONG), stripe, HorizontalAlignment.LEFT, false);
            case NOTE -> body(font(SANS, 10, false, true, MUTED), stripe, HorizontalAlignment.LEFT, false);
        });
    }

    /**
     * @return a centred data cell whose text is a status ("Error", "Yes"): bold, in the accent colour,
     *         on the row's normal background
     */
    public CellStyle accentText(Accent accent, boolean stripe) {
        return styles.computeIfAbsent("accent|" + accent + "|" + stripe,
                k -> body(font(SANS, 10, true, false, accent.text()), stripe, HorizontalAlignment.CENTER, false));
    }

    /** @return a data cell of a row that is marked as a whole: body text on the accent's light fill */
    public CellStyle accentRow(Accent accent) {
        return styles.computeIfAbsent("accentRow|" + accent, k -> {
            XSSFCellStyle style = body(font(SANS, 10, false, false, TEXT), false, HorizontalAlignment.LEFT, false);
            style.setFillForegroundColor(color(accent.fill()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        });
    }

    // ------------------------------------------------------------------ writing

    /** Writes the sheet title (16 pt) into column A of {@code rowNum}. @return the next row */
    public int writeTitle(Sheet sheet, int rowNum, String text) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(28);
        cell(row, 0, text, title());
        return rowNum + 1;
    }

    /** Writes a muted line (subject, timestamp) into column A. @return the next row */
    public int writeSubtitle(Sheet sheet, int rowNum, String text) {
        cell(sheet.createRow(rowNum), 0, text, subtitle());
        return rowNum + 1;
    }

    /** Writes a section heading whose rule spans columns A to {@code lastColumn}. @return the next row */
    public int writeSection(Sheet sheet, int rowNum, String text, int lastColumn) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(22);
        cell(row, 0, text, section());
        for (int column = 1; column <= lastColumn; column++) {
            row.createCell(column).setCellStyle(section());
        }
        return rowNum + 1;
    }

    /** Writes one label/value row of an info sheet. @return the next row */
    public int writeLabelValue(Sheet sheet, int rowNum, String label, String value) {
        return writeLabelValue(sheet, rowNum, label, value, value());
    }

    /** Writes one label/value row of an info sheet with the given value style. @return the next row */
    public int writeLabelValue(Sheet sheet, int rowNum, String label, String value, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        cell(row, 0, label, label());
        cell(row, 1, value, valueStyle);
        return rowNum + 1;
    }

    /**
     * Writes the header row of a table.
     *
     * @param centered which columns are centred (their header is centred too)
     * @return the next row
     */
    public int writeHeaderRow(Sheet sheet, int rowNum, String[] headers, IntPredicate centered) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            cell(row, i, headers[i], centered.test(i) ? headerCenter() : header());
        }
        return rowNum + 1;
    }

    /**
     * Writes a text cell cut to Excel's cell limit ({@code null} as empty). Text from outside sources
     * that ends up in files people open in other spreadsheet tools goes through {@link #safe} first.
     *
     * @return the cell
     */
    public static Cell cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(clip(value));
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    /** Writes a numeric cell. @return the cell */
    public static Cell cell(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    // ------------------------------------------------------------------ sheets

    /** Prepares a sheet that holds a table: no gridlines, sky tab. */
    public static void tableSheet(Sheet sheet) {
        sheet.setDisplayGridlines(false);
        setTabColor(sheet, ACCENT_RULE);
    }

    /** Prepares an info/summary sheet (title, sections, label/value rows): no gridlines, slate tab, two columns, portrait. */
    public static void infoSheet(Sheet sheet) {
        sheet.setDisplayGridlines(false);
        setTabColor(sheet, HEADER_FILL);
        sheet.setColumnWidth(0, INFO_LABEL_WIDTH);
        sheet.setColumnWidth(1, INFO_VALUE_WIDTH);
        printOnePageWide(sheet, false);
    }

    /**
     * Finishes a table whose header is in {@code headerRow}: rows above and including it stay visible
     * when scrolling and repeat on every printed page, the columns get an auto-filter, and the sheet
     * prints one page wide in landscape.
     *
     * @param lastRow    the last data row (0-based; equal to {@code headerRow} for an empty table)
     * @param lastColumn the last column of the table (0-based)
     */
    public static void finishTable(Sheet sheet, int headerRow, int lastRow, int lastColumn) {
        sheet.createFreezePane(0, headerRow + 1);
        sheet.setRepeatingRows(CellRangeAddress.valueOf((headerRow + 1) + ":" + (headerRow + 1)));
        if (lastRow > headerRow) {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, lastRow, 0, lastColumn));
        }
        printOnePageWide(sheet, true);
    }

    /** Prints the sheet one page wide (as many pages tall as needed). */
    public static void printOnePageWide(Sheet sheet, boolean landscape) {
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(landscape);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    /** Colours the sheet's tab ({@code RRGGBB}); ignored for sheet kinds without RGB tabs. */
    public static void setTabColor(Sheet sheet, String hex) {
        if (sheet instanceof XSSFSheet xssf) {
            xssf.setTabColor(color(hex));
        } else if (sheet instanceof SXSSFSheet streaming) {
            streaming.setTabColor(color(hex));
        }
    }

    // ------------------------------------------------------------------ values

    /** @return the opaque colour for a {@code RRGGBB} hex string (written as ARGB, which every Excel reads) */
    public static XSSFColor color(String hex) {
        return new XSSFColor(HexFormat.of().parseHex("FF" + hex));
    }

    /**
     * Guards against spreadsheet formula injection (CWE-1236): a text whose first character a
     * spreadsheet treats as a formula trigger is prefixed with an apostrophe so it stays literal text.
     *
     * @return the safe text ({@code ""} for {@code null})
     */
    public static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        return (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') ? "'" + value : value;
    }

    /** @return the text cut to Excel's 32 767-character cell limit (ending in "…" when cut) */
    public static String clip(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_CELL_LENGTH ? value.substring(0, MAX_CELL_LENGTH - 1) + "…" : value;
    }

    // ------------------------------------------------------------------ internals

    private XSSFCellStyle createHeader(HorizontalAlignment alignment) {
        XSSFCellStyle style = style(font(SANS, 10, true, false, "FFFFFF"));
        style.setFillForegroundColor(color(HEADER_FILL));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(alignment);
        return style;
    }

    private XSSFCellStyle createValue(XSSFFont font) {
        XSSFCellStyle style = ruled(style(font));
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private XSSFCellStyle body(XSSFFont font, boolean stripe, HorizontalAlignment alignment, boolean wrap) {
        XSSFCellStyle style = ruled(style(font));
        if (stripe) {
            style.setFillForegroundColor(color(ZEBRA_FILL));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(wrap);
        return style;
    }

    private static XSSFCellStyle ruled(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(color(RULE));
        return style;
    }

    private XSSFCellStyle style(XSSFFont font) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private XSSFFont font(String name, int points, boolean bold, boolean italic, String hex) {
        return fonts.computeIfAbsent(name + '|' + points + '|' + bold + '|' + italic + '|' + hex, k -> {
            XSSFFont font = (XSSFFont) workbook.createFont();
            font.setFontName(name);
            font.setFontHeightInPoints((short) points);
            font.setBold(bold);
            font.setItalic(italic);
            font.setColor(color(hex));
            return font;
        });
    }
}
