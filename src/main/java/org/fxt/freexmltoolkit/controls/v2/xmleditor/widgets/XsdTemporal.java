/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical forms of xs:date, xs:time and xs:dateTime: splits a value into its parts — keeping the timezone and the
 * fractional seconds the pickers do not edit — and writes the parts back in XSD form (four-digit years at least,
 * always with seconds, never ISO's {@code +} sign before large years).
 */
final class XsdTemporal {

    private static final String ZONE = "(Z|[+-]\\d{2}:\\d{2})?";
    private static final Pattern DATE = Pattern.compile("(-?\\d{4,})-(\\d{2})-(\\d{2})" + ZONE);
    private static final Pattern TIME = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?" + ZONE);
    private static final Pattern DATE_TIME = Pattern.compile(
            "(-?\\d{4,})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?" + ZONE);

    /**
     * A parsed value.
     *
     * @param date     the date, or {@code null} for xs:time
     * @param time     the time without fraction, or {@code null} for xs:date
     * @param fraction the fractional seconds as written ({@code .125}), or {@code ""}
     * @param zone     the timezone as written ({@code Z}, {@code +01:00}), or {@code ""}
     */
    record Parts(LocalDate date, LocalTime time, String fraction, String zone) {
    }

    private XsdTemporal() {
    }

    /** @return the parts of an xs:date value, or {@code null} when it is not one the picker can show */
    static Parts parseDate(String value) {
        Matcher m = value == null ? null : DATE.matcher(value.strip());
        if (m == null || !m.matches()) {
            return null;
        }
        LocalDate date = date(m.group(1), m.group(2), m.group(3));
        return date == null ? null : new Parts(date, null, "", nz(m.group(4)));
    }

    /** @return the parts of an xs:time value, or {@code null} when it is not one the picker can show */
    static Parts parseTime(String value) {
        Matcher m = value == null ? null : TIME.matcher(value.strip());
        if (m == null || !m.matches()) {
            return null;
        }
        LocalTime time = time(m.group(1), m.group(2), m.group(3));
        return time == null ? null : new Parts(null, time, nz(m.group(4)), nz(m.group(5)));
    }

    /** @return the parts of an xs:dateTime value, or {@code null} when it is not one the picker can show */
    static Parts parseDateTime(String value) {
        Matcher m = value == null ? null : DATE_TIME.matcher(value.strip());
        if (m == null || !m.matches()) {
            return null;
        }
        LocalDate date = date(m.group(1), m.group(2), m.group(3));
        LocalTime time = time(m.group(4), m.group(5), m.group(6));
        return date == null || time == null ? null : new Parts(date, time, nz(m.group(7)), nz(m.group(8)));
    }

    /** @return {@code date} in XSD form ({@code 0042-01-31}, {@code -0044-03-15}, {@code 12024-01-01}) */
    static String formatDate(LocalDate date) {
        int year = date.getYear();
        String y = String.format(Locale.ROOT, "%04d", Math.abs(year));
        return (year < 0 ? "-" : "") + y + String.format(Locale.ROOT, "-%02d-%02d", date.getMonthValue(),
                date.getDayOfMonth());
    }

    /** @return {@code hh:mm:ss} followed by {@code fraction} */
    static String formatTime(int hour, int minute, int second, String fraction) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hour, minute, second) + nz(fraction);
    }

    private static LocalDate date(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static LocalTime time(String hour, String minute, String second) {
        try {
            // 24:00:00 is valid XSD but no LocalTime: such values stay as written
            return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute), Integer.parseInt(second));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
