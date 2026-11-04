package org.fxt.freexmltoolkit.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lexical XML value converted to what an Excel cell can hold without losing it. Only values whose
 * lexical form comes back unchanged on import are typed (no leading zeros or {@code +}, at most 15
 * significant digits, no timezone, years from 1900, at most millisecond precision); everything else is
 * {@link Text}, so the export never silently alters a value.
 */
sealed interface SpreadsheetTypedValue {

    /** Excel stores doubles: more significant digits would be rounded. */
    int MAX_DIGITS = 15;

    /** Kept as a string cell. */
    record Text(String value) implements SpreadsheetTypedValue {
    }

    /**
     * A number.
     *
     * @param decimals the fraction digits to show, {@code -1} for Excel's General format
     */
    record Numeric(double value, int decimals) implements SpreadsheetTypedValue {
    }

    record Bool(boolean value) implements SpreadsheetTypedValue {
    }

    /**
     * A date, date-time or time.
     *
     * @param date            the date part, {@code null} for a time
     * @param time            the time part, {@code null} for a date
     * @param fractionDigits  digits of the seconds fraction (0–3)
     */
    record Temporal(LocalDate date, LocalTime time, int fractionDigits) implements SpreadsheetTypedValue {

        /** @return the Excel number format of this value */
        String format() {
            String seconds = fractionDigits == 0 ? "ss" : "ss." + "0".repeat(fractionDigits);
            if (date == null) {
                return "hh:mm:" + seconds;
            }
            return time == null ? "yyyy-mm-dd" : "yyyy-mm-dd hh:mm:" + seconds;
        }
    }

    Pattern INTEGER = Pattern.compile("-?(0|[1-9]\\d*)");
    Pattern DECIMAL = Pattern.compile("-?(0|[1-9]\\d*)(\\.(\\d+))?");
    Pattern DOUBLE = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    Pattern TIME = Pattern.compile("(\\d{2}:\\d{2}:\\d{2})(\\.(\\d{1,3}))?");

    /**
     * @param kind    the schema-derived kind
     * @param lexical the value as written in the XML (trimmed by the caller)
     * @return the typed value, or {@link Text} when the value does not fit the kind losslessly
     */
    static SpreadsheetTypedValue of(SpreadsheetCellKind kind, String lexical) {
        Text text = new Text(lexical == null ? "" : lexical);
        if (lexical == null || lexical.isEmpty() || kind == null) {
            return text;
        }
        try {
            return switch (kind) {
                case TEXT -> text;
                case INTEGER -> INTEGER.matcher(lexical).matches()
                        && new BigInteger(lexical).abs().toString().length() <= MAX_DIGITS
                        ? new Numeric(Double.parseDouble(lexical), 0) : text;
                case DECIMAL -> decimal(lexical, text);
                case DOUBLE -> DOUBLE.matcher(lexical).matches()
                        ? new Numeric(Double.parseDouble(lexical), -1) : text; // INF, NaN stay text
                case BOOLEAN -> switch (lexical) { // 1/0 would come back as true/false
                    case "true" -> new Bool(true);
                    case "false" -> new Bool(false);
                    default -> text;
                };
                case DATE -> {
                    if (!DATE.matcher(lexical).matches()) {
                        yield text;
                    }
                    LocalDate date = LocalDate.parse(lexical);
                    yield date.getYear() >= 1900 ? new Temporal(date, null, 0) : text;
                }
                case DATE_TIME -> {
                    int t = lexical.indexOf('T');
                    if (t < 0 || !DATE.matcher(lexical.substring(0, t)).matches()) {
                        yield text;
                    }
                    LocalDate date = LocalDate.parse(lexical.substring(0, t));
                    Temporal time = time(lexical.substring(t + 1));
                    yield time != null && date.getYear() >= 1900
                            ? new Temporal(date, time.time(), time.fractionDigits()) : text;
                }
                case TIME -> {
                    Temporal time = time(lexical);
                    yield time != null ? time : text;
                }
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            return text;
        }
    }

    private static SpreadsheetTypedValue decimal(String lexical, Text text) {
        Matcher m = DECIMAL.matcher(lexical);
        if (!m.matches()) {
            return text; // also "+1", "01.5", "1.", ".5"
        }
        BigDecimal value = new BigDecimal(lexical);
        if (value.precision() > MAX_DIGITS) {
            return text;
        }
        return new Numeric(value.doubleValue(), m.group(3) == null ? 0 : m.group(3).length());
    }

    /** @return the time of {@code hh:mm:ss[.f{1,3}]} without timezone, or {@code null} */
    private static Temporal time(String lexical) {
        Matcher m = TIME.matcher(lexical);
        if (!m.matches()) {
            return null; // timezone, 24:00:00 below, more than millisecond precision
        }
        LocalTime time = LocalTime.parse(lexical);
        return new Temporal(null, time, m.group(3) == null ? 0 : m.group(3).length());
    }

    /** @return the date-time of a {@link Temporal} with a date part */
    static LocalDateTime dateTimeOf(Temporal temporal) {
        return temporal.date().atTime(temporal.time() == null ? LocalTime.MIDNIGHT : temporal.time());
    }
}
