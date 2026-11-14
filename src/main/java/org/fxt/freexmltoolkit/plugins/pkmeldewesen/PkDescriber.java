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

import java.util.Map;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.plugin.ElementDescriber;

/**
 * Describes the elements of a PK report with the FMA's labels: a template element by its
 * template / table name, a fact ({@code Datenpunkt} with {@code RowCode}) by its row and column,
 * a field of an open list ({@code Wert_C0010PK}) by its column, plus the label of a code-list value.
 */
final class PkDescriber implements ElementDescriber {

    /** Explanations longer than this are cut (they can be a page long). */
    static final int MAX_EXPLANATION = 700;

    private final Supplier<PkDictionary> dictionary;

    /** @param dictionary the dictionary in use, or {@code null} while it is not loaded */
    PkDescriber(Supplier<PkDictionary> dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public String describe(ElementInfo element) {
        String root = element.rootName();
        if (!(root.startsWith("Nationale_Meldung_") || root.startsWith("EZB_Meldung_")) || element.path().size() < 2) {
            return null;
        }
        PkDictionary dict = dictionary.get();
        if (dict == null || dict.isEmpty()) {
            return null;
        }
        String template = codeOf(element.path().get(1));
        if (element.path().size() == 2) {
            return templateLine(dict, template);
        }
        String name = element.name();
        // a fact of a closed template, or one of its parts
        Map<String, String> fact = element.childTexts().containsKey("RowCode") ? element.childTexts()
                : element.parentChildTexts().containsKey("RowCode") ? element.parentChildTexts() : null;
        if (fact != null) {
            String value = "Wert".equals(name) ? element.text() : fact.get("Wert");
            return factText(dict, template, fact.get("RowCode"), fact.get("ColumnCode"), value);
        }
        if (name.startsWith("Wert_")) {
            return columnText(dict, template, name.substring("Wert_".length()), element.text());
        }
        return templateLine(dict, template);
    }

    /** @return {@code PK.800.01} of {@code PK.800.01_Vermögensausweis} */
    static String codeOf(String elementName) {
        int cut = elementName.indexOf('_');
        return cut > 0 ? elementName.substring(0, cut) : elementName;
    }

    static String templateLine(PkDictionary dict, String template) {
        String name = dict.template(template);
        return name == null ? null : template + " · " + name;
    }

    static String factText(PkDictionary dict, String template, String rowCode, String columnCode, String value) {
        PkDictionary.Code row = rowCode == null ? null : dict.row(rowCode.strip());
        if (row == null) {
            return templateLine(dict, template);
        }
        StringBuilder text = new StringBuilder(row.code()).append(" · ").append(row.name());
        if (columnCode != null && !columnCode.isBlank()) {
            PkDictionary.Code column = dict.column(template, columnCode.strip());
            text.append('\n').append(columnCode.strip());
            if (column != null && !column.name().isEmpty()) {
                text.append(" · ").append(column.name());
            }
        }
        appendValue(text, dict, row.domain(), value);
        appendDetails(text, row);
        return text.toString();
    }

    static String columnText(PkDictionary dict, String template, String columnCode, String value) {
        PkDictionary.Code column = dict.column(template, columnCode);
        if (column == null) {
            return null;
        }
        StringBuilder text = new StringBuilder(column.code()).append(" · ").append(column.name());
        String templateName = dict.template(template);
        if (templateName != null) {
            text.append("\n").append(template).append(" · ").append(templateName);
        }
        appendValue(text, dict, column.domain(), value);
        appendDetails(text, column);
        return text.toString();
    }

    private static void appendValue(StringBuilder text, PkDictionary dict, String domain, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String label = dict.valueLabel(domain, value.strip());
        if (label != null) {
            text.append('\n').append(value.strip()).append(" = ").append(label);
        }
    }

    private static void appendDetails(StringBuilder text, PkDictionary.Code code) {
        // a code list is worth naming, a built-in type (xs:decimal) is what the schema says anyway
        String domain = code.domain().startsWith("xs:") ? "" : code.domain();
        if (!domain.isEmpty() || !code.mandatory().isEmpty()) {
            text.append('\n');
            if (!domain.isEmpty()) {
                text.append("Domain: ").append(domain);
            }
            if (!code.mandatory().isEmpty()) {
                text.append(domain.isEmpty() ? "" : " · ").append("Meldepflicht: ").append(code.mandatory());
            }
        }
        String explanation = code.explanation().strip();
        if (!explanation.isEmpty()) {
            if (explanation.length() > MAX_EXPLANATION) {
                explanation = explanation.substring(0, MAX_EXPLANATION).strip() + " …";
            }
            text.append("\n\n").append(explanation);
        }
    }
}
