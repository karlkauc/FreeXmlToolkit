package org.fxt.freexmltoolkit.service;

import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * Cell styles of the Data Dictionary workbook, in the look of the HTML documentation (Tailwind slate/sky
 * palette): a dark slate header, zebra rows with a hairline rule instead of boxed cells, monospace for
 * XPaths and types, and "Mandatory" as coloured text rather than a filled block. Created once per workbook.
 */
final class DataDictionaryExcelStyles {

    /** slate-800: header fill. */
    static final String HEADER_FILL = "1E293B";
    /** slate-50: every second data row. */
    static final String ZEBRA_FILL = "F8FAFC";
    /** slate-200: rule below each row. */
    static final String RULE = "E2E8F0";
    /** slate-700: body text. */
    static final String TEXT = "334155";
    /** slate-900: names and XPaths. */
    static final String STRONG = "0F172A";
    /** slate-500: counters, occurs, labels. */
    static final String MUTED = "64748B";
    /** slate-400: "No". */
    static final String FAINT = "94A3B8";
    /** green-700: "Yes". */
    static final String MANDATORY = "15803D";
    /** sky-700: section titles. */
    static final String ACCENT = "0369A1";
    /** sky-600: section rules and the language sheet tabs. */
    static final String ACCENT_RULE = "0284C7";

    private static final String SANS = "Calibri";
    private static final String MONO = "Consolas";

    /** How a body cell looks; every role exists plain and striped. */
    enum Role {
        TEXT, CENTER, MONO, XPATH, NAME, WRAP, MANDATORY_YES, MANDATORY_NO
    }

    final CellStyle header;
    /** The header over a centred column. */
    final CellStyle headerCenter;
    final CellStyle title;
    final CellStyle section;
    final CellStyle label;
    final CellStyle value;
    private final Map<Role, CellStyle> plain = new EnumMap<>(Role.class);
    private final Map<Role, CellStyle> striped = new EnumMap<>(Role.class);

    DataDictionaryExcelStyles(Workbook workbook) {
        XSSFFont text = font(workbook, SANS, 10, false, TEXT);
        XSSFFont muted = font(workbook, SANS, 10, false, MUTED);
        XSSFFont name = font(workbook, SANS, 10, true, STRONG);
        XSSFFont mono = font(workbook, MONO, 10, false, TEXT);
        XSSFFont xpath = font(workbook, MONO, 10, false, STRONG);
        XSSFFont yes = font(workbook, SANS, 10, true, MANDATORY);
        XSSFFont no = font(workbook, SANS, 10, false, FAINT);

        for (boolean stripe : new boolean[] {false, true}) {
            Map<Role, CellStyle> target = stripe ? striped : plain;
            target.put(Role.TEXT, body(workbook, text, stripe, HorizontalAlignment.LEFT, false));
            target.put(Role.CENTER, body(workbook, muted, stripe, HorizontalAlignment.CENTER, false));
            target.put(Role.MONO, body(workbook, mono, stripe, HorizontalAlignment.LEFT, false));
            target.put(Role.XPATH, body(workbook, xpath, stripe, HorizontalAlignment.LEFT, false));
            target.put(Role.NAME, body(workbook, name, stripe, HorizontalAlignment.LEFT, false));
            target.put(Role.WRAP, body(workbook, text, stripe, HorizontalAlignment.LEFT, true));
            target.put(Role.MANDATORY_YES, body(workbook, yes, stripe, HorizontalAlignment.CENTER, false));
            target.put(Role.MANDATORY_NO, body(workbook, no, stripe, HorizontalAlignment.CENTER, false));
        }

        XSSFCellStyle headerStyle = style(workbook, font(workbook, SANS, 10, true, "FFFFFF"));
        headerStyle.setFillForegroundColor(color(HEADER_FILL));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        header = headerStyle;
        XSSFCellStyle headerCenterStyle = (XSSFCellStyle) workbook.createCellStyle();
        headerCenterStyle.cloneStyleFrom(headerStyle);
        headerCenterStyle.setAlignment(HorizontalAlignment.CENTER);
        headerCenter = headerCenterStyle;

        XSSFCellStyle titleStyle = style(workbook, font(workbook, SANS, 16, true, STRONG));
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        title = titleStyle;

        XSSFCellStyle sectionStyle = style(workbook, font(workbook, SANS, 11, true, ACCENT));
        sectionStyle.setVerticalAlignment(VerticalAlignment.BOTTOM);
        sectionStyle.setBorderBottom(BorderStyle.MEDIUM);
        sectionStyle.setBottomBorderColor(color(ACCENT_RULE));
        section = sectionStyle;

        XSSFCellStyle labelStyle = style(workbook, font(workbook, SANS, 10, true, MUTED));
        labelStyle.setVerticalAlignment(VerticalAlignment.TOP);
        labelStyle.setBorderBottom(BorderStyle.THIN);
        labelStyle.setBottomBorderColor(color(RULE));
        label = labelStyle;

        XSSFCellStyle valueStyle = style(workbook, text);
        valueStyle.setVerticalAlignment(VerticalAlignment.TOP);
        valueStyle.setWrapText(true);
        valueStyle.setBorderBottom(BorderStyle.THIN);
        valueStyle.setBottomBorderColor(color(RULE));
        value = valueStyle;
    }

    /** The style of a data cell with {@code role}, on a striped (every second) row or not. */
    CellStyle body(Role role, boolean stripe) {
        return (stripe ? striped : plain).get(role);
    }

    /** @return the opaque colour for a {@code RRGGBB} hex string (written as ARGB, which every Excel reads) */
    static XSSFColor color(String hex) {
        return new XSSFColor(HexFormat.of().parseHex("FF" + hex));
    }

    private static XSSFCellStyle body(Workbook workbook, XSSFFont font, boolean stripe,
                                      HorizontalAlignment alignment, boolean wrap) {
        XSSFCellStyle style = style(workbook, font);
        if (stripe) {
            style.setFillForegroundColor(color(ZEBRA_FILL));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(wrap);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(color(RULE));
        return style;
    }

    private static XSSFCellStyle style(Workbook workbook, XSSFFont font) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static XSSFFont font(Workbook workbook, String name, int points, boolean bold, String hex) {
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontName(name);
        font.setFontHeightInPoints((short) points);
        font.setBold(bold);
        font.setColor(color(hex));
        return font;
    }
}
