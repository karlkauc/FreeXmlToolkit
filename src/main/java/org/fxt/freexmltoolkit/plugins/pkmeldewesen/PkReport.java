/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the root element of a PK report says: the module and the attributes the FMA requires
 * on it ({@code PK-Nummer GJ LEI Stichtag Typ_der_Meldung Taxonomie DB}). Read from the start
 * of the text only (the reports are up to ~180 MB), so it is cheap enough for every opened
 * document.
 *
 * @param module     the report kind
 * @param attributes the root element's attributes in document order
 */
public record PkReport(PkModule module, Map<String, String> attributes) {

    /** How much of a document is scanned for its root element. */
    static final int SCAN_LIMIT = 64 * 1024;

    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w.:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    /** @return the pension fund's FMA number ({@code PK-Nummer}), or {@code null} */
    public String pkNumber() {
        return attributes.get("PK-Nummer");
    }

    /** @return the financial year ({@code GJ}), or {@code null} */
    public String year() {
        return attributes.get("GJ");
    }

    /** @return the reporting period ({@code Stichtag}: {@code Q1}..{@code Q4} or a year-end code), or {@code null} */
    public String period() {
        return attributes.get("Stichtag");
    }

    /** @return the taxonomy version the report declares ({@code Taxonomie}), or {@code null} */
    public String taxonomy() {
        return attributes.get("Taxonomie");
    }

    /** @return the kind of report ({@code Typ_der_Meldung}, e.g. {@code Testmeldung}), or {@code null} */
    public String reportType() {
        return attributes.get("Typ_der_Meldung");
    }

    /** @return a one-line label: {@code PK-QMV Q1/2026}, {@code PK-FJMV 2025}, {@code PK-EZB Q4/2025} */
    public String label() {
        StringBuilder text = new StringBuilder(module.shortName());
        String period = period();
        String year = year();
        boolean annual = period == null || period.isBlank() || module == PkModule.PFA
                || period.strip().equalsIgnoreCase("JAHR") || period.strip().equalsIgnoreCase("J");
        if (!annual) {
            text.append(' ').append(period);
            if (year != null && !year.isBlank()) {
                text.append('/').append(year);
            }
        } else if (year != null && !year.isBlank()) {
            text.append(' ').append(year);
        }
        return text.toString();
    }

    /**
     * Recognises a PK report by its root element.
     *
     * @param content the document text (only the first {@value #SCAN_LIMIT} characters are read)
     * @return the report, or {@code null} when the document is no PK report
     */
    public static PkReport detect(String content) {
        String tag = rootStartTag(content);
        if (tag == null) {
            return null;
        }
        int nameEnd = 0;
        while (nameEnd < tag.length() && !Character.isWhitespace(tag.charAt(nameEnd)) && tag.charAt(nameEnd) != '>'
                && tag.charAt(nameEnd) != '/') {
            nameEnd++;
        }
        String name = tag.substring(0, nameEnd);
        PkModule module = PkModule.ofRootElement(name.substring(name.indexOf(':') + 1));
        if (module == null) {
            return null;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(tag.substring(nameEnd));
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
        }
        return new PkReport(module, Map.copyOf(attributes));
    }

    /** @return the root start tag without {@code <} (name and attributes), skipping the prolog; or {@code null} */
    static String rootStartTag(String content) {
        if (content == null) {
            return null;
        }
        int end = Math.min(content.length(), SCAN_LIMIT);
        int i = 0;
        while (i < end) {
            int lt = content.indexOf('<', i);
            if (lt < 0 || lt + 1 >= end) {
                return null;
            }
            if (content.startsWith("<?", lt)) {
                i = skipPast(content, lt, "?>", end);
            } else if (content.startsWith("<!--", lt)) {
                i = skipPast(content, lt, "-->", end);
            } else if (content.startsWith("<!", lt)) {
                i = skipPast(content, lt, ">", end);
            } else {
                int close = content.indexOf('>', lt);
                return close < 0 || close > end ? null : content.substring(lt + 1, close);
            }
            if (i < 0) {
                return null;
            }
        }
        return null;
    }

    private static int skipPast(String content, int from, String marker, int end) {
        int at = content.indexOf(marker, from);
        return at < 0 || at > end ? -1 : at + marker.length();
    }
}
