package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.service.XsdLexicalNormalizer.LocaleHint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class XsdLexicalNormalizerTest {

    @ParameterizedTest(name = "{0} {1} [{2}] -> {3}")
    @CsvSource(delimiter = '|', value = {
            // valid values never change (blanks around them aside)
            "DECIMAL   | 10.50                | EUROPEAN | 10.50",
            "DECIMAL   | 1.234                | EUROPEAN | 1.234",
            "INTEGER   | 007                  | US       | 007",
            "BOOLEAN   | 1                    | UNKNOWN  | 1",
            "DATE      | 2024-01-31+01:00     | UNKNOWN  | 2024-01-31+01:00",
            "DATE_TIME | 2024-01-31T10:15:30Z | UNKNOWN  | 2024-01-31T10:15:30Z",
            "INTEGER   | '  42  '             | UNKNOWN  | 42",
            // decimal separators and grouping
            "DECIMAL   | 10,50                | EUROPEAN | 10.50",
            "DECIMAL   | 10,50                | UNKNOWN  | 10.50",
            "DECIMAL   | 1.234,56             | UNKNOWN  | 1234.56",
            "DECIMAL   | 1,234.56             | UNKNOWN  | 1234.56",
            "DECIMAL   | 1 234,56             | EUROPEAN | 1234.56",
            "DECIMAL   | 1'234.50             | US       | 1234.50",
            "DECIMAL   | 1,234                | US       | 1234",
            "DECIMAL   | 1,234                | EUROPEAN | 1.234",
            "DECIMAL   | 1,234                | UNKNOWN  | 1,234",
            "DECIMAL   | 1.5E3                | UNKNOWN  | 1.5E3",
            "INTEGER   | 1.234.567            | EUROPEAN | 1234567",
            "INTEGER   | 1.234                | EUROPEAN | 1234",
            "INTEGER   | 42,0                 | EUROPEAN | 42",
            "INTEGER   | 42,5                 | EUROPEAN | 42,5",
            "DOUBLE    | 1,5E+03              | EUROPEAN | 1.5E+03",
            // booleans
            "BOOLEAN   | TRUE                 | UNKNOWN  | true",
            "BOOLEAN   | Falsch               | UNKNOWN  | false",
            "BOOLEAN   | maybe                | UNKNOWN  | maybe",
            // dates and times
            "DATE      | 31.01.2024           | UNKNOWN  | 2024-01-31",
            "DATE      | 1.2.2024             | US       | 2024-02-01",
            "DATE      | 2024/01/31           | UNKNOWN  | 2024-01-31",
            "DATE      | 31/01/2024           | UNKNOWN  | 2024-01-31",
            "DATE      | 01/31/2024           | UNKNOWN  | 2024-01-31",
            "DATE      | 01/02/2024           | EUROPEAN | 2024-02-01",
            "DATE      | 01/02/2024           | US       | 2024-01-02",
            "DATE      | 01/02/2024           | UNKNOWN  | 01/02/2024",
            "DATE      | 31.02.2024           | UNKNOWN  | 31.02.2024",
            "DATE_TIME | 31.01.2024 10:15     | UNKNOWN  | 2024-01-31T10:15:00",
            "DATE_TIME | 2024-01-31 10:15:30  | UNKNOWN  | 2024-01-31T10:15:30",
            "DATE_TIME | 31.01.2024           | UNKNOWN  | 2024-01-31T00:00:00",
            "TIME      | 9:05                 | UNKNOWN  | 09:05:00",
            "TIME      | 10:15:30.25          | UNKNOWN  | 10:15:30.25",
            // text is never touched
            "TEXT      | '  31.01.2024 '      | EUROPEAN | '  31.01.2024 '"
    })
    void repairsOnlyWhatIsInvalidAndUnambiguous(SpreadsheetCellKind kind, String value, LocaleHint hint, String expected) {
        assertEquals(expected, XsdLexicalNormalizer.normalize(kind, value, hint));
    }
}
