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

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Line numbers of the parts of a PK report, read in one StAX pass: templates by code and VRG,
 * facts of the closed templates by row code, entries of the open lists by asset id (and, in the
 * look-through, by fund and underlying).
 *
 * <p>Keys: {@code tpl|vrg} for a template, {@code tpl|vrg|row} for a fact,
 * {@code tpl|vrg|id} for a list entry and {@code PK.006.03|vrg|fund|underlying} for a
 * look-through row. The first occurrence wins.</p>
 */
public final class PkLocator {

    private final Map<String, Integer> lines = new HashMap<>();
    private int rootLine = 1;

    private PkLocator() {
    }

    /** @return the locator of a report; an unreadable text gives one without entries */
    public static PkLocator of(String xml) {
        PkLocator locator = new PkLocator();
        try {
            locator.scan(xml);
        } catch (XMLStreamException e) {
            // the rule check reports what it can; lines then fall back to the root
        }
        return locator;
    }

    private void scan(String xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        int depth = 0;
        String tpl = null;
        String vrg = null;
        int entryLine = 0;
        String field = null;
        Map<String, String> values = new HashMap<>();
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                int line = reader.getLocation().getLineNumber();
                String name = reader.getLocalName();
                if (depth == 1) {
                    rootLine = line;
                } else if (depth == 2) {
                    int cut = name.indexOf('_');
                    tpl = cut > 0 ? name.substring(0, cut) : name;
                    vrg = reader.getAttributeValue(null, "VRG");
                    vrg = vrg == null ? "" : vrg.strip();
                    lines.putIfAbsent(tpl + "|" + vrg, line);
                } else if (depth == 3) {
                    entryLine = line;
                    values.clear();
                } else if (depth == 4) {
                    field = name;
                    text.setLength(0);
                }
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if (depth == 4) {
                    text.append(reader.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == 4 && field != null) {
                    values.putIfAbsent(field, text.toString().strip());
                    field = null;
                } else if (depth == 3 && tpl != null) {
                    register(tpl, vrg, entryLine, values);
                }
                depth--;
            }
        }
        reader.close();
    }

    private void register(String tpl, String vrg, int line, Map<String, String> values) {
        String row = values.get("RowCode");
        if (row != null && !row.isEmpty()) {
            lines.putIfAbsent(tpl + "|" + vrg + "|" + row, line);
        }
        String id = values.get("Wert_C0010PK");
        if (id != null && !id.isEmpty()) {
            lines.putIfAbsent(tpl + "|" + vrg + "|" + id, line);
            String underlying = values.get("Wert_C1010PK");
            if (underlying != null && !underlying.isEmpty()) {
                lines.putIfAbsent(tpl + "|" + vrg + "|" + id + "|" + underlying, line);
            }
        }
    }

    /** @return the line of the root element */
    public int rootLine() {
        return rootLine;
    }

    /** @return the line of a template ({@code PK.800.01}) of a VRG, 0 when not reported */
    public int template(String tpl, String vrg) {
        return lines.getOrDefault(tpl + "|" + vrg, 0);
    }

    /** @return the line of a fact by row code ({@code R800-110}), 0 when not reported */
    public int fact(String tpl, String vrg, String row) {
        return lines.getOrDefault(tpl + "|" + vrg + "|" + row, 0);
    }

    /** @return the line of an open-list entry by asset id, 0 when not reported */
    public int entry(String tpl, String vrg, String id) {
        return lines.getOrDefault(tpl + "|" + vrg + "|" + id, 0);
    }

    /** @return the line of a look-through row by fund and underlying, 0 when not reported */
    public int lookThrough(String vrg, String fund, String underlying) {
        return lines.getOrDefault("PK.006.03|" + vrg + "|" + fund + "|" + underlying, 0);
    }

    /** @return the template code of a row code ({@code R800-110} → {@code PK.800.01}), or {@code null} */
    static String templateOfRow(String row) {
        if (row == null || row.length() < 4 || row.charAt(0) != 'R') {
            return null;
        }
        return switch (row.substring(1, 4)) {
            case "000" -> "PK.000.01";
            case "800" -> "PK.800.01";
            case "900" -> "PK.900.01";
            default -> null;
        };
    }
}
