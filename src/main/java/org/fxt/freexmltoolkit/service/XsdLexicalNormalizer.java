package org.fxt.freexmltoolkit.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs a text value for the XSD type it has, as it comes out of a spreadsheet application:
 * {@code 31.01.2024} under {@code xs:date} becomes {@code 2024-01-31}, {@code 10,50} under
 * {@code xs:decimal} becomes {@code 10.50}, {@code TRUE} under {@code xs:boolean} becomes {@code true},
 * {@code 10:15} under {@code xs:time} becomes {@code 10:15:00}.
 * <p>
 * A value that already is in its type's lexical space is returned unchanged (only blanks around it are
 * dropped, which the XSD types ignore anyway), so valid data never changes. A value that cannot be read
 * unambiguously is returned as written - the validator then reports it. Where the locale matters (a
 * single comma, a {@code d/m} or {@code m/d} date) the {@link Locale} hint decides; without one only the
 * unambiguous forms are repaired.
 */
final class XsdLexicalNormalizer {

    /** Which decimal separator / day-month order the source probably used. */
    enum LocaleHint {
        /** Decimal comma, day before month (a semicolon-separated CSV, as European Excel writes it). */
        EUROPEAN,
        /** Decimal point, month before day (a comma-separated CSV, as US Excel writes it). */
        US,
        /** Not known: ambiguous values are left as written. */
        UNKNOWN;

        /** @return the hint a CSV delimiter gives */
        static LocaleHint ofCsvDelimiter(char delimiter) {
            return switch (delimiter) {
                case ';' -> EUROPEAN;
                case ',' -> US;
                default -> UNKNOWN;
            };
        }
    }

    private static final Pattern DOTTED_DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern YEAR_FIRST = Pattern.compile("(\\d{4})[/.](\\d{1,2})[/.](\\d{1,2})");
    private static final Pattern SLASHED_DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2})(\\.\\d+)?)?");
    private static final Pattern EXPONENT = Pattern.compile("(.*?)([eE][+-]?\\d+)");
    private static final Pattern DIGITS = Pattern.compile("[+-]?\\d*(\\.\\d*)?");

    private XsdLexicalNormalizer() {
    }

    /**
     * @param kind  the schema-derived kind of the value
     * @param value the text as read
     * @param hint  the locale the text was probably written in
     * @return the value in its type's lexical form, or the value as written when it cannot be repaired
     */
    static String normalize(SpreadsheetCellKind kind, String value, LocaleHint hint) {
        if (kind == null || kind == SpreadsheetCellKind.TEXT || value == null) {
            return value;
        }
        String s = value.strip();
        if (s.isEmpty() || SpreadsheetTypedValue.isLexicallyValid(kind, s)) {
            return s;
        }
        String repaired = switch (kind) {
            case BOOLEAN -> bool(s);
            case INTEGER -> {
                BigDecimal number = number(s, hint);
                yield number != null && number.stripTrailingZeros().scale() <= 0
                        ? number.toBigIntegerExact().toString() : null;
            }
            case DECIMAL -> {
                BigDecimal number = number(s, hint);
                yield number == null ? null : number.toPlainString();
            }
            case DOUBLE -> {
                Matcher exp = EXPONENT.matcher(s);
                String mantissa = exp.matches() ? exp.group(1) : s;
                BigDecimal number = number(mantissa, hint);
                yield number == null ? null
                        : number.toPlainString() + (exp.matches() ? exp.group(2).toUpperCase(Locale.ROOT) : "");
            }
            case DATE -> date(s, hint);
            case DATE_TIME -> dateTime(s, hint);
            case TIME -> time(s);
            case TEXT -> s;
        };
        return repaired != null && SpreadsheetTypedValue.isLexicallyValid(kind, repaired) ? repaired : s;
    }

    private static String bool(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "true", "wahr", "yes" -> "true";
            case "false", "falsch", "no" -> "false";
            default -> null;
        };
    }

    /**
     * Reads a number with grouping and either decimal separator: with both {@code .} and {@code ,} the
     * last one is the decimal separator; repeated separators group; a single one is decided by the hint
     * (a single separator followed by exactly three digits is grouping in the other locale).
     *
     * @return the number, or {@code null} when it is not one or the separator is ambiguous
     */
    private static BigDecimal number(String text, LocaleHint hint) {
        // blanks, no-break spaces and apostrophes group digits (1 234 / 1'234)
        String s = text.replaceAll("[\\s\\u00A0\\u202F']", "");
        int dots = count(s, '.');
        int commas = count(s, ',');
        String plain;
        if (dots > 0 && commas > 0) {
            char decimal = s.lastIndexOf('.') > s.lastIndexOf(',') ? '.' : ',';
            char grouping = decimal == '.' ? ',' : '.';
            if (count(s, decimal) > 1) {
                return null;
            }
            plain = s.replace(String.valueOf(grouping), "").replace(decimal, '.');
        } else if (dots + commas == 0) {
            plain = s;
        } else {
            char separator = dots > 0 ? '.' : ',';
            if (dots + commas > 1) {
                plain = s.replace(String.valueOf(separator), ""); // 1.234.567
            } else {
                // a single separator before exactly three digits may group thousands (1,234 / 1.234)
                boolean threeAfter = s.length() - s.indexOf(separator) - 1 == 3;
                if (threeAfter && hint == LocaleHint.UNKNOWN) {
                    return null; // grouping or decimal? not known
                }
                char localeDecimal = hint == LocaleHint.EUROPEAN ? ',' : '.';
                boolean decimal = separator == localeDecimal || !threeAfter;
                plain = decimal ? s.replace(separator, '.') : s.replace(String.valueOf(separator), "");
            }
        }
        if (plain.isEmpty() || plain.equals("+") || plain.equals("-") || !DIGITS.matcher(plain).matches()) {
            return null;
        }
        try {
            return new BigDecimal(plain.startsWith("+") ? plain.substring(1) : plain);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String date(String s, LocaleHint hint) {
        Matcher m;
        int year;
        int month;
        int day;
        if ((m = DOTTED_DATE.matcher(s)).matches()) {
            day = Integer.parseInt(m.group(1));
            month = Integer.parseInt(m.group(2));
            year = Integer.parseInt(m.group(3));
        } else if ((m = YEAR_FIRST.matcher(s)).matches() || (m = ISO_DATE.matcher(s)).matches()) {
            year = Integer.parseInt(m.group(1));
            month = Integer.parseInt(m.group(2));
            day = Integer.parseInt(m.group(3));
        } else if ((m = SLASHED_DATE.matcher(s)).matches()) {
            int first = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            year = Integer.parseInt(m.group(3));
            boolean dayFirst;
            if (first > 12) {
                dayFirst = true;
            } else if (second > 12) {
                dayFirst = false;
            } else if (hint == LocaleHint.UNKNOWN) {
                return null; // 01/02/2024: January 2nd or February 1st?
            } else {
                dayFirst = hint == LocaleHint.EUROPEAN;
            }
            day = dayFirst ? first : second;
            month = dayFirst ? second : first;
        } else {
            return null;
        }
        try {
            return LocalDate.of(year, month, day).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String dateTime(String s, LocaleHint hint) {
        int split = s.indexOf('T') > 0 ? s.indexOf('T') : s.indexOf(' ');
        if (split < 0) {
            String date = date(s, hint);
            return date == null ? null : date + "T00:00:00"; // a date where a date-time is expected
        }
        String date = date(s.substring(0, split).strip(), hint);
        String time = time(s.substring(split + 1).strip());
        return date == null || time == null ? null : date + "T" + time;
    }

    private static String time(String s) {
        Matcher m = TIME.matcher(s);
        if (!m.matches()) {
            return null;
        }
        String seconds = m.group(3) == null ? "00" : m.group(3);
        String fraction = m.group(4) == null ? "" : m.group(4);
        return String.format(Locale.ROOT, "%02d:%s:%s%s", Integer.parseInt(m.group(1)), m.group(2), seconds, fraction);
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
