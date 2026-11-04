package org.fxt.freexmltoolkit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * Turns an Excel cell into the XML lexical form of its value, for the Excel → XML import.
 * <p>
 * Without a schema type ({@link SpreadsheetCellKind#TEXT}) the cell's own type and format decide:
 * a date-formatted number becomes an ISO date / date-time / time, a number keeps the fraction
 * digits its format shows ({@code 10.50}) and loses a spurious {@code .0}. With the schema type of the
 * value, the schema decides instead, so a cell that was entered or reformatted by hand still yields
 * a valid value: a date cell under {@code xs:dateTime} gets a time, a plain serial number under
 * {@code xs:date} becomes a date, a date-formatted number under {@code xs:decimal} stays a number,
 * {@code 1}/{@code 0} under {@code xs:boolean} become {@code true}/{@code false}, a {@code TRUE} text
 * becomes {@code true}, blanks around a typed text value are dropped. A formula contributes its
 * cached result. Text that does not fit its type is kept as written: the validator reports it.
 */
final class ExcelCellLexical {

    /** Within this distance a double is taken as the integer next to it (binary noise of entered values). */
    private static final double INTEGER_TOLERANCE = 1e-9;

    private ExcelCellLexical() {
    }

    /**
     * @param cell the cell, may be null
     * @param kind the schema-derived kind of the value; {@link SpreadsheetCellKind#TEXT} when untyped
     * @return the XML lexical form, {@code ""} for a blank or error cell
     */
    static String lexical(Cell cell, SpreadsheetCellKind kind) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> string(cell.getStringCellValue(), kind);
            case NUMERIC -> numeric(cell, kind);
            case BOOLEAN -> bool(cell.getBooleanCellValue(), kind);
            default -> "";
        };
    }

    private static String string(String text, SpreadsheetCellKind kind) {
        if (kind == SpreadsheetCellKind.TEXT || text == null) {
            return text == null ? "" : text;
        }
        String trimmed = text.strip(); // XSD collapses whitespace in numeric, boolean and date types
        if (kind == SpreadsheetCellKind.BOOLEAN && ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed))) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private static String bool(boolean value, SpreadsheetCellKind kind) {
        return switch (kind) {
            case INTEGER, DECIMAL, DOUBLE -> value ? "1" : "0";
            default -> String.valueOf(value);
        };
    }

    private static String numeric(Cell cell, SpreadsheetCellKind kind) {
        String format = format(cell);
        double value = cell.getNumericCellValue();
        boolean dateFormatted = DateUtil.isCellDateFormatted(cell);
        return switch (kind) {
            case DATE -> DateUtil.isValidExcelDate(value) ? dateTime(value, 0).toLocalDate().toString() : plain(value);
            case DATE_TIME -> {
                if (!DateUtil.isValidExcelDate(value)) {
                    yield plain(value);
                }
                int fraction = dateFormatted ? secondsFraction(format) : millisDigits(value);
                LocalDateTime dateTime = dateTime(value, fraction);
                yield dateTime.toLocalDate() + "T" + dateTime.toLocalTime().format(timeFormat(fraction));
            }
            case TIME -> {
                int fraction = dateFormatted ? secondsFraction(format) : millisDigits(value);
                // the time of day, also of a date-time serial
                double dayFraction = value - Math.floor(value);
                yield dateTime(dayFraction, fraction).toLocalTime().format(timeFormat(fraction));
            }
            case INTEGER -> {
                double rounded = Math.rint(value);
                yield Math.abs(value - rounded) < INTEGER_TOLERANCE && Math.abs(rounded) < 1e15
                        ? Long.toString((long) rounded) : plain(value); // a fraction stays visible to the validator
            }
            case DECIMAL, DOUBLE -> dateFormatted ? plain(value) : number(value, format);
            case BOOLEAN -> value == 1 ? "true" : value == 0 ? "false" : number(value, format);
            case TEXT -> dateFormatted ? asFormattedDate(value, format) : number(value, format);
        };
    }

    /** The untyped reading of a date-formatted cell: date, date-time or time as its format shows. */
    private static String asFormattedDate(double value, String format) {
        int fraction = secondsFraction(format);
        boolean hasDate = format.indexOf('y') >= 0 || format.indexOf('d') >= 0;
        boolean hasTime = format.indexOf('h') >= 0 || format.indexOf('s') >= 0;
        LocalDateTime dateTime = dateTime(value, fraction);
        DateTimeFormatter time = timeFormat(fraction);
        if (hasDate && hasTime) {
            return dateTime.toLocalDate() + "T" + dateTime.toLocalTime().format(time);
        }
        return hasTime ? dateTime.toLocalTime().format(time) : dateTime.toLocalDate().toString();
    }

    /** A number with the fraction digits its format shows, an integral one without {@code .0}. */
    private static String number(double value, String format) {
        int dot = format.indexOf('.');
        if (dot >= 0 && !format.contains("e")) {
            return BigDecimal.valueOf(value).setScale(fractionZeros(format, dot), RoundingMode.HALF_UP).toPlainString();
        }
        return plain(value);
    }

    private static String plain(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** @return the date-time of an Excel serial, rounded to {@code fraction} digits of a second */
    private static LocalDateTime dateTime(double serial, int fraction) {
        LocalDateTime dateTime = DateUtil.getLocalDateTime(serial, false, false);
        // round to the shown precision: day fractions are not exact in binary
        long unit = (long) Math.pow(10, 9 - fraction);
        long nanos = Math.round((double) dateTime.getNano() / unit) * unit;
        return dateTime.withNano(0).plusNanos(nanos);
    }

    /** @return 3 when the serial carries milliseconds, else 0 (for a cell without a time format) */
    private static int millisDigits(double serial) {
        return dateTime(serial, 3).getNano() == 0 ? 0 : 3;
    }

    private static DateTimeFormatter timeFormat(int fraction) {
        return DateTimeFormatter.ofPattern(fraction == 0 ? "HH:mm:ss" : "HH:mm:ss." + "S".repeat(fraction));
    }

    private static String format(Cell cell) {
        String format = cell.getCellStyle() == null ? null : cell.getCellStyle().getDataFormatString();
        return format == null ? "" : format.toLowerCase(Locale.ROOT);
    }

    /** @return the digits of the seconds fraction a date/time format shows ({@code ss.000} → 3) */
    private static int secondsFraction(String format) {
        int seconds = format.indexOf("ss.");
        return seconds < 0 ? 0 : fractionZeros(format, seconds + 2);
    }

    /** @return the {@code 0} digits right after the {@code .} at {@code dot} */
    private static int fractionZeros(String format, int dot) {
        int i = dot + 1;
        int count = 0;
        while (i < format.length() && format.charAt(i) == '0') {
            count++;
            i++;
        }
        return count;
    }
}
