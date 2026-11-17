package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** The lexical split and composition the date/time pickers rely on. */
class XsdTemporalTest {

    @Test
    void datesKeepTheirTimezone() {
        var parts = XsdTemporal.parseDate("2024-01-31-05:00");
        assertEquals(LocalDate.of(2024, 1, 31), parts.date());
        assertEquals("-05:00", parts.zone());
        assertEquals("Z", XsdTemporal.parseDate("2024-01-31Z").zone());
        assertEquals("", XsdTemporal.parseDate("2024-01-31").zone());
        assertNull(XsdTemporal.parseDate("2023-02-29"), "no such day");
        assertNull(XsdTemporal.parseDate("31.01.2024"));
    }

    @Test
    void timesKeepFractionAndTimezone() {
        var parts = XsdTemporal.parseTime("12:30:05.125+02:00");
        assertEquals(LocalTime.of(12, 30, 5), parts.time());
        assertEquals(".125", parts.fraction());
        assertEquals("+02:00", parts.zone());
        assertNull(XsdTemporal.parseTime("24:00:00"), "valid XSD, but kept as written by the pickers");
        assertNull(XsdTemporal.parseTime("12:30"));
    }

    @Test
    void dateTimes() {
        var parts = XsdTemporal.parseDateTime("2024-03-01T08:00:00.5Z");
        assertEquals(LocalDate.of(2024, 3, 1), parts.date());
        assertEquals(LocalTime.of(8, 0), parts.time());
        assertEquals(".5", parts.fraction());
        assertEquals("Z", parts.zone());
        assertNull(XsdTemporal.parseDateTime("2024-03-01 08:00:00"));
    }

    @Test
    void formatsInXsdForm() {
        assertEquals("12:00:00", XsdTemporal.formatTime(12, 0, 0, ""), "seconds are never dropped");
        assertEquals("12:00:00.25", XsdTemporal.formatTime(12, 0, 0, ".25"));
        assertEquals("0042-01-02", XsdTemporal.formatDate(LocalDate.of(42, 1, 2)));
        assertEquals("12024-01-02", XsdTemporal.formatDate(LocalDate.of(12024, 1, 2)), "no ISO '+' sign");
        assertEquals("-0044-03-15", XsdTemporal.formatDate(LocalDate.of(-44, 3, 15)));
    }
}
