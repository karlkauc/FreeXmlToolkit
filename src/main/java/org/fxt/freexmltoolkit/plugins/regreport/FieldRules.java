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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builders for the rule patterns that recur in supervisors' rule books, on element paths relative to
 * a record. A <em>scope</em> is the repeated block a rule applies to (e.g. each
 * {@code MainInstrumentTraded}); target and condition paths are relative to it. Messages name the
 * fields with their catalogue label ({@code Field 50 · Base currency / EUR FX rate}).
 */
public final class FieldRules {

    private final FieldCatalog catalog;
    private final String recordPath;

    /**
     * @param catalog    the field catalogue (for labels), may be {@code null}
     * @param recordPath the record's path from the root ({@code AIFReportingInfo/AIFRecordInfo})
     */
    public FieldRules(FieldCatalog catalog, String recordPath) {
        this.catalog = catalog;
        this.recordPath = recordPath;
    }

    /** @return the field's label for a path relative to the record, else its element name */
    public String label(String relativePath) {
        if (catalog != null) {
            FieldCatalog.Field field = catalog.byPath(recordPath + "/" + relativePath);
            if (field != null) {
                return field.label();
            }
        }
        return relativePath.substring(relativePath.lastIndexOf('/') + 1);
    }

    private static String join(String scope, String path) {
        return scope.isEmpty() ? path : scope + "/" + path;
    }

    /** @return a test for a value in a set */
    public static Predicate<String> in(String... values) {
        Set<String> set = Set.of(values);
        return set::contains;
    }

    /** @return a test for a value not in a set (also true for an absent value) */
    public static Predicate<String> notIn(String... values) {
        Set<String> set = Set.of(values);
        return v -> !set.contains(v);
    }

    /**
     * The target must be reported when the condition holds.
     *
     * @param when text of the condition for the message ({@code "the base currency is not EUR"})
     */
    public RegRuleSet.Rule requiredWhen(String id, String scope, String target, String condition,
                                        Predicate<String> test, String when) {
        return (record, ctx, out) -> {
            for (RecordNode block : record.all(scope)) {
                if (test.test(block.text(condition)) && !block.filled(target)) {
                    out.error(id, block.line(), label(join(scope, target)) + " is missing although " + when + ".");
                }
            }
        };
    }

    /** The target must not be reported when the condition holds. */
    public RegRuleSet.Rule forbiddenWhen(String id, String scope, String target, String condition,
                                         Predicate<String> test, String when) {
        return (record, ctx, out) -> {
            for (RecordNode block : record.all(scope)) {
                if (test.test(block.text(condition)) && block.filled(target)) {
                    out.error(id, block.lineOf(target), label(join(scope, target)) + " must not be reported when " + when + ".");
                }
            }
        };
    }

    /** Both fields or neither are reported (in each scope block). */
    public RegRuleSet.Rule together(String id, String scope, String first, String second) {
        return (record, ctx, out) -> {
            for (RecordNode block : record.all(scope)) {
                boolean a = block.filled(first);
                boolean b = block.filled(second);
                if (a != b) {
                    String missing = a ? second : first;
                    out.error(id, block.line(), label(join(scope, missing)) + " is missing: "
                            + label(join(scope, first)) + " and " + label(join(scope, second)) + " are reported together.");
                }
            }
        };
    }

    /**
     * The percentages of a block add up to 100 (± tolerance), checked when any of them is reported.
     *
     * @param zeroAllowed whether a total of 0 is accepted too
     */
    public RegRuleSet.Rule sumIs100(String id, String scope, List<String> parts, BigDecimal tolerance, boolean zeroAllowed) {
        return (record, ctx, out) -> {
            for (RecordNode block : record.all(scope)) {
                BigDecimal sum = BigDecimal.ZERO;
                boolean any = false;
                for (String part : parts) {
                    for (RecordNode node : block.all(part)) {
                        BigDecimal value = node.decimal("");
                        if (value != null) {
                            sum = sum.add(value);
                            any = true;
                        }
                    }
                }
                if (!any) {
                    continue;
                }
                boolean hundred = sum.subtract(BigDecimal.valueOf(100)).abs().compareTo(tolerance) <= 0;
                boolean zero = zeroAllowed && sum.signum() == 0;
                if (!hundred && !zero) {
                    out.error(id, block.line(), "The percentages of " + label(scope.isEmpty() ? parts.getFirst() : scope)
                            + " add up to " + sum.stripTrailingZeros().toPlainString() + " %, not 100 %"
                            + (zeroAllowed ? " (or 0 %)" : "") + ".");
                }
            }
        };
    }

    /** Checks an identifier (LEI, ISIN …) wherever the path occurs. */
    public RegRuleSet.Rule identifier(String id, String path, Function<String, String> check) {
        return (record, ctx, out) -> {
            for (RecordNode node : record.all(path)) {
                if (!node.text().isEmpty()) {
                    String problem = check.apply(node.text());
                    if (problem != null) {
                        out.error(id, node.line(), label(path) + ": " + problem);
                    }
                }
            }
        };
    }

    /** {@code earlier} lies before {@code later} (or on the same day unless strict); both dates in the record. */
    public RegRuleSet.Rule dateOrder(String id, String earlier, String later, boolean strict) {
        return (record, ctx, out) -> {
            LocalDate a = date(record.text(earlier));
            LocalDate b = date(record.text(later));
            if (a != null && b != null && (strict ? !a.isBefore(b) : a.isAfter(b))) {
                out.error(id, record.lineOf(strict ? earlier : later), label(earlier) + " (" + a + ") must lie "
                        + (strict ? "before " : "on or before ") + label(later) + " (" + b + ").");
            }
        };
    }

    /** The values of a ranked list do not increase with the ranking (1 = largest). */
    public RegRuleSet.Rule rankingOrder(String id, String listPath, String ranking, String value) {
        return (record, ctx, out) -> {
            java.util.TreeMap<Integer, RecordNode> ranked = new java.util.TreeMap<>();
            for (RecordNode item : record.all(listPath)) {
                try {
                    ranked.put(Integer.parseInt(item.text(ranking)), item);
                } catch (NumberFormatException e) {
                    // the schema reports it
                }
            }
            BigDecimal previous = null;
            for (RecordNode item : ranked.values()) {
                BigDecimal current = item.decimal(value);
                if (current == null) {
                    continue;
                }
                if (previous != null && current.compareTo(previous) > 0) {
                    out.error(id, item.lineOf(value), label(join(listPath, value)) + " of ranking " + item.text(ranking)
                            + " (" + current.toPlainString() + ") is larger than that of the ranking before ("
                            + previous.toPlainString() + ").");
                }
                previous = current;
            }
        };
    }

    /** @return a date, or {@code null} for an absent or invalid one */
    public static LocalDate date(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip().length() > 10 ? text.strip().substring(0, 10) : text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
