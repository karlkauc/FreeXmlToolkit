package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpreadsheetTypedValueTest {

    @Test
    void integersAndDecimalsBecomeNumbersWithTheirFractionDigits() {
        assertEquals(new SpreadsheetTypedValue.Numeric(42, 0), SpreadsheetTypedValue.of(SpreadsheetCellKind.INTEGER, "42"));
        assertEquals(new SpreadsheetTypedValue.Numeric(-7, 0), SpreadsheetTypedValue.of(SpreadsheetCellKind.INTEGER, "-7"));
        assertEquals(new SpreadsheetTypedValue.Numeric(10.5, 2), SpreadsheetTypedValue.of(SpreadsheetCellKind.DECIMAL, "10.50"));
        assertEquals(new SpreadsheetTypedValue.Numeric(3, 0), SpreadsheetTypedValue.of(SpreadsheetCellKind.DECIMAL, "3"));
        assertEquals(new SpreadsheetTypedValue.Numeric(1500, -1), SpreadsheetTypedValue.of(SpreadsheetCellKind.DOUBLE, "1.5E3"));
    }

    @ParameterizedTest
    @CsvSource({
            "INTEGER, 1234567890123456", // more than 15 digits
            "INTEGER, 007", "INTEGER, +5", "INTEGER, abc", "INTEGER, 1.0",
            "DECIMAL, 01.5", "DECIMAL, 1.", "DECIMAL, .5", "DECIMAL, 12345678901234.56",
            "DOUBLE, INF", "DOUBLE, NaN",
            "BOOLEAN, yes", "BOOLEAN, 1", "BOOLEAN, 0",
            "DATE, 2024-01-31Z", "DATE, 2024-01-31+01:00", "DATE, 1899-12-31", "DATE, 2024-02-30",
            "DATE_TIME, 2024-01-31T10:15:30Z", "DATE_TIME, 2024-01-31T10:15:30+01:00",
            "DATE_TIME, 2024-01-31T10:15:30.1234", "DATE_TIME, 2024-01-31",
            "TIME, 24:00:00", "TIME, 10:15", "TIME, 10:15:30Z"
    })
    void valuesThatWouldChangeStayText(SpreadsheetCellKind kind, String lexical) {
        assertEquals(new SpreadsheetTypedValue.Text(lexical), SpreadsheetTypedValue.of(kind, lexical));
    }

    @Test
    void booleansAndTemporals() {
        assertEquals(new SpreadsheetTypedValue.Bool(true), SpreadsheetTypedValue.of(SpreadsheetCellKind.BOOLEAN, "true"));
        assertEquals(new SpreadsheetTypedValue.Bool(false), SpreadsheetTypedValue.of(SpreadsheetCellKind.BOOLEAN, "false"));

        var date = assertInstanceOf(SpreadsheetTypedValue.Temporal.class,
                SpreadsheetTypedValue.of(SpreadsheetCellKind.DATE, "2024-01-31"));
        assertEquals(LocalDate.of(2024, 1, 31), date.date());
        assertEquals("yyyy-mm-dd", date.format());

        var dateTime = assertInstanceOf(SpreadsheetTypedValue.Temporal.class,
                SpreadsheetTypedValue.of(SpreadsheetCellKind.DATE_TIME, "2024-01-31T10:15:30.25"));
        assertEquals(LocalTime.of(10, 15, 30, 250_000_000), dateTime.time());
        assertEquals("yyyy-mm-dd hh:mm:ss.00", dateTime.format());

        var time = assertInstanceOf(SpreadsheetTypedValue.Temporal.class,
                SpreadsheetTypedValue.of(SpreadsheetCellKind.TIME, "08:05:00"));
        assertEquals("hh:mm:ss", time.format());
    }

    @ParameterizedTest
    @CsvSource({
            "INTEGER, 007, true", "INTEGER, +5, true", "INTEGER, 1.0, false",
            "DECIMAL, .5, true", "DECIMAL, 1., true", "DECIMAL, 1e3, false",
            "DOUBLE, -INF, true", "DOUBLE, NaN, true", "DOUBLE, inf, false",
            "BOOLEAN, 1, true", "BOOLEAN, yes, false",
            "DATE, 2024-01-31Z, true", "DATE, 2024-13-01, false", "DATE, 0800-01-01, true",
            "DATE_TIME, 2024-09-26T18:37:12.1559196+02:00, true", "DATE_TIME, 2024-01-31, false",
            "DATE_TIME, 2024-01-31T25:00:00, false",
            "TIME, 24:00:00, true", "TIME, 10:15, false",
            "TEXT, abc, false"
    })
    void lexicalValidityFollowsTheXsdLexicalSpace(SpreadsheetCellKind kind, String lexical, boolean valid) {
        assertEquals(valid, SpreadsheetTypedValue.isLexicallyValid(kind, lexical));
    }

    @Test
    void builtinNamesMapToKinds() {
        assertEquals(SpreadsheetCellKind.INTEGER, SpreadsheetCellKind.fromBuiltin("positiveInteger"));
        assertEquals(SpreadsheetCellKind.DECIMAL, SpreadsheetCellKind.fromBuiltin("decimal"));
        assertEquals(SpreadsheetCellKind.DATE_TIME, SpreadsheetCellKind.fromBuiltin("dateTime"));
        assertEquals(SpreadsheetCellKind.TEXT, SpreadsheetCellKind.fromBuiltin("gYear"));
        assertEquals(SpreadsheetCellKind.TEXT, SpreadsheetCellKind.fromBuiltin(null));
    }
}
