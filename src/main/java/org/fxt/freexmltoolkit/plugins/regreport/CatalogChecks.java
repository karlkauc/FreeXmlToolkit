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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * The plain code checks of a field catalogue ({@link FieldCatalog.Field#check()}): every element of a
 * record whose field has a check is tested - ISO 4217 currency, ISO 3166 country, EU country, ISIN
 * and LEI check digits. Rule ids are {@code <prefix>-<field id>}.
 */
public final class CatalogChecks {

    /** The member states of the European Union (ISO 3166 codes; Greece as GR). */
    public static final Set<String> EU = Set.of("AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
            "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE");

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private CatalogChecks() {
    }

    /**
     * @param prefixes the paths from the root to the record's parent, one per document layout
     *                 ({@code BizData/Pyld/Document/MnyMktFndRpt}, {@code Document/MnyMktFndRpt})
     */
    public static RegRuleSet.Rule of(FieldCatalog catalog, String rulePrefix, List<String> prefixes) {
        return (record, ctx, out) -> walk(record, catalog, rulePrefix, prefixes, out);
    }

    private static void walk(RecordNode node, FieldCatalog catalog, String rulePrefix, List<String> prefixes,
                             RegRuleSet.Findings out) {
        if (node.children().isEmpty()) {
            if (!node.text().isEmpty()) {
                FieldCatalog.Field field = null;
                for (String prefix : prefixes) {
                    field = catalog.byPath(prefix + "/" + node.path());
                    if (field != null) {
                        break;
                    }
                }
                if (field != null && !field.check().isEmpty()) {
                    String problem = problem(field.check(), node.text());
                    if (problem != null) {
                        out.error(rulePrefix + "-" + field.id(), node.line(), field.label() + ": " + problem);
                    }
                }
            }
            return;
        }
        for (RecordNode child : node.children()) {
            walk(child, catalog, rulePrefix, prefixes, out);
        }
    }

    /**
     * Checks every element named {@code LEI} or {@code ISIN} of a record (for formats whose field
     * tables number the parent elements only). Rule ids are {@code <prefix>-LEI} / {@code <prefix>-ISIN}.
     */
    public static RegRuleSet.Rule identifiersByName(String rulePrefix) {
        return (record, ctx, out) -> byName(record, rulePrefix, out);
    }

    private static void byName(RecordNode node, String rulePrefix, RegRuleSet.Findings out) {
        if (node.children().isEmpty()) {
            String check = switch (node.name()) {
                case "LEI" -> "lei";
                case "ISIN" -> "isin";
                default -> null;
            };
            if (check != null && !node.text().isEmpty()) {
                String problem = problem(check, node.text());
                if (problem != null) {
                    out.error(rulePrefix + "-" + node.name(), node.line(), node.parent() == null ? problem
                            : node.parent().name() + ": " + problem);
                }
            }
            return;
        }
        for (RecordNode child : node.children()) {
            byName(child, rulePrefix, out);
        }
    }

    /** @return why the value fails the check, or {@code null} */
    public static String problem(String check, String value) {
        return switch (check) {
            case "currency" -> isCurrency(value) ? null : "\"" + value + "\" is no ISO 4217 currency code.";
            case "country" -> COUNTRIES.contains(value) ? null : "\"" + value + "\" is no ISO 3166 country code.";
            case "eu-country" -> EU.contains(value) ? null : "\"" + value + "\" is no country of the European Union.";
            case "isin" -> FinancialIdentifiers.checkIsin(value);
            case "lei" -> FinancialIdentifiers.checkLei(value);
            default -> null;
        };
    }

    private static boolean isCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
