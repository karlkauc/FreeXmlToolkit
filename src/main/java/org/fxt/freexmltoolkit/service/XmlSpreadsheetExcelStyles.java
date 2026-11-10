package org.fxt.freexmltoolkit.service;

import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.FAINT;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.HEADER_FILL;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.MONO;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.MUTED;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.RULE;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.SANS;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.STRONG;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.TEXT;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.ZEBRA_FILL;
import static org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.color;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * Cell styles of the XML → Excel workbook in the shared house style
 * ({@link org.fxt.freexmltoolkit.service.excel.FxtExcelTheme}: slate header, zebra rows with a hairline
 * rule, Consolas for XPaths), plus typed number/date formats. Styles are created on first use and cached, since a workbook holds at most 64 000.
 * A legacy {@code .xls} workbook cannot take RGB colours and gets the same fonts and alignment
 * with an indexed-colour header instead.
 */
final class XmlSpreadsheetExcelStyles {


    /** How a body cell looks. */
    enum Role {
        /** XPath of a node with a value. */
        XPATH,
        /** XPath of an element that only contains other nodes. */
        XPATH_CONTAINER,
        /** A text value. */
        TEXT,
        /** A comment's text. */
        COMMENT,
        /** The node-type column. */
        TYPE,
        /** A number (format given separately). */
        NUMBER,
        /** A boolean. */
        BOOLEAN,
        /** A date/time (format given separately). */
        TEMPORAL
    }

    final CellStyle header;
    final CellStyle headerCenter;

    private final Workbook workbook;
    private final boolean themed;
    private final DataFormat dataFormat;
    private final Map<String, Font> fonts = new HashMap<>();
    private final Map<String, CellStyle> bodies = new HashMap<>();

    XmlSpreadsheetExcelStyles(Workbook workbook) {
        this.workbook = workbook;
        this.themed = !(workbook instanceof HSSFWorkbook);
        this.dataFormat = workbook.createDataFormat();

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(font(SANS, true, false, "FFFFFF"));
        if (themed) {
            ((XSSFCellStyle) headerStyle).setFillForegroundColor(color(HEADER_FILL));
        } else {
            headerStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        }
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        header = headerStyle;
        CellStyle centered = workbook.createCellStyle();
        centered.cloneStyleFrom(headerStyle);
        centered.setAlignment(HorizontalAlignment.CENTER);
        headerCenter = centered;
    }

    /**
     * @param role   how the cell looks
     * @param stripe whether the row is a striped (every second) row
     * @param format the number format for {@link Role#NUMBER}/{@link Role#TEMPORAL}, ignored otherwise
     * @return the shared style
     */
    CellStyle body(Role role, boolean stripe, String format) {
        String key = role + (stripe ? "|s|" : "|p|") + (format == null ? "" : format);
        return bodies.computeIfAbsent(key, k -> createBody(role, stripe, format));
    }

    /** @return the Excel number format showing {@code decimals} fraction digits ({@code -1}: General) */
    static String numberFormat(int decimals) {
        if (decimals < 0) {
            return "General";
        }
        return decimals == 0 ? "0" : "#,##0." + "0".repeat(decimals);
    }

    private CellStyle createBody(Role role, boolean stripe, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(switch (role) {
            case XPATH -> font(MONO, false, false, STRONG);
            case XPATH_CONTAINER -> font(MONO, true, false, STRONG);
            case COMMENT -> font(SANS, false, true, MUTED);
            case TYPE -> font(SANS, false, false, FAINT);
            default -> font(SANS, false, false, TEXT);
        });
        style.setAlignment(switch (role) {
            case NUMBER, TEMPORAL -> HorizontalAlignment.RIGHT;
            case BOOLEAN, TYPE -> HorizontalAlignment.CENTER;
            default -> HorizontalAlignment.LEFT;
        });
        style.setVerticalAlignment(VerticalAlignment.TOP);
        String effectiveFormat = switch (role) {
            case NUMBER, TEMPORAL -> format == null ? "General" : format;
            case BOOLEAN -> "General";
            default -> "@"; // text, so Excel does not reinterpret it on edit
        };
        style.setDataFormat(dataFormat.getFormat(effectiveFormat));
        style.setBorderBottom(BorderStyle.THIN);
        if (themed) {
            XSSFCellStyle xssf = (XSSFCellStyle) style;
            xssf.setBottomBorderColor(color(RULE));
            if (stripe) {
                xssf.setFillForegroundColor(color(ZEBRA_FILL));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        } else {
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
        return style;
    }

    private Font font(String name, boolean bold, boolean italic, String hex) {
        return fonts.computeIfAbsent(name + bold + italic + hex, k -> {
            Font font = workbook.createFont();
            font.setFontName(name);
            font.setFontHeightInPoints((short) 10);
            font.setBold(bold);
            font.setItalic(italic);
            if (themed) {
                ((XSSFFont) font).setColor(color(hex));
            } else if ("FFFFFF".equals(hex)) {
                font.setColor(IndexedColors.WHITE.getIndex());
            }
            return font;
        });
    }
}
