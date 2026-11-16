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

package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * The derivative risk report of a UCITS management company under the 4. DeRiMV (§ 2a, as amended by
 * BGBl. II Nr. 242/2016): one file per management company, one {@code InvestmentFonds} per fund. The
 * FMA publishes no rule catalogue for it, so these rules come from the regulation and the InvFG 2011
 * limits on the global exposure; rule ids are {@code DeRiMV-<number of the item in § 2a Abs. 2>}.
 * The report carries no confidence level, holding period, reference portfolio or net asset value,
 * so those VaR parameters cannot be checked on the file.
 */
public final class DerimvRules {

    /** The root element of the report (no namespace). */
    public static final String ROOT = "Derivatemeldung";
    private static final String RECORD = "InvestmentFonds";
    private static final String STATE_ISINS = "derimv.isins";

    static final BigDecimal COMMITMENT_LIMIT = new BigDecimal(100);
    static final BigDecimal ABSOLUTE_VAR_LIMIT = new BigDecimal(20);
    static final BigDecimal RELATIVE_VAR_LIMIT = new BigDecimal(200);

    private DerimvRules() {
    }

    /**
     * The settings of the rules.
     *
     * @param relativeVarLimit the relative VaR above which a warning is given (% of the reference portfolio's VaR)
     * @param warnAt           utilisation of the legal limit (or, for the absolute VaR, of the fund's own limit) in %
     *                         from which a warning is given, or {@code null} for none
     */
    public record Options(BigDecimal relativeVarLimit, BigDecimal warnAt) {

        /** @return relative VaR 200 %, no utilisation warning */
        public static Options defaults() {
            return new Options(RELATIVE_VAR_LIMIT, null);
        }
    }

    /** @return the rules with the default options; {@code today} gives the date the reporting period must not lie after */
    public static RegRuleSet rules(Supplier<LocalDate> today) {
        return rules(today, Options.defaults());
    }

    /** @return the rules; {@code today} gives the date the reporting period must not lie after */
    public static RegRuleSet rules(Supplier<LocalDate> today, Options options) {
        RegRuleSet rules = new RegRuleSet(ROOT + "/" + RECORD);
        rules.header((root, ctx, out) -> period(root, today.get(), out))
                .record(DerimvRules::isin)
                .record((fund, ctx, out) -> exposure(fund, options, out));
        return rules;
    }

    /** Jahr / Quartal: not in the future; the format applies from the reporting date 31 December 2016. */
    private static void period(RecordNode root, LocalDate today, RegRuleSet.Findings out) {
        int year;
        int quarter;
        try {
            year = Integer.parseInt(root.attributes().getOrDefault("Jahr", "").strip());
            quarter = Integer.parseInt(root.attributes().getOrDefault("Quartal", "").strip());
        } catch (NumberFormatException e) {
            return; // the schema reports it
        }
        if (quarter < 1 || quarter > 4) {
            return;
        }
        LocalDate end = LocalDate.of(year, quarter * 3, 1).plusMonths(1).minusDays(1);
        if (end.isAfter(today)) {
            out.warning("DeRiMV-period", root.line(), "The reporting period Q" + quarter + "/" + year + " ends on " + end
                    + ", which is still in the future.");
        }
        if (end.isBefore(LocalDate.of(2016, 12, 31))) {
            out.warning("DeRiMV-period", root.line(), "The XML format of BGBl. II Nr. 242/2016 applies to reports from the "
                    + "reporting date 31 December 2016; Q" + quarter + "/" + year + " lies before it.");
        }
    }

    /** Item 4: ISIN check digit and one entry per ISIN. */
    @SuppressWarnings("unchecked")
    private static void isin(RecordNode fund, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        String isin = fund.text("ISIN");
        if (isin.isEmpty()) {
            return;
        }
        String problem = FinancialIdentifiers.checkIsin(isin);
        if (problem != null) {
            out.error("DeRiMV-4", fund.lineOf("ISIN"), problem);
            return;
        }
        Map<String, Integer> seen = (Map<String, Integer>) ctx.state().computeIfAbsent(STATE_ISINS, k -> new HashMap<String, Integer>());
        Integer first = seen.putIfAbsent(isin, fund.lineOf("ISIN"));
        if (first != null) {
            out.error("DeRiMV-4", fund.lineOf("ISIN"), "ISIN " + isin + " is reported twice (first in line " + first + ").");
        }
    }

    /** Items 6–9: exposure at the reporting date vs. the period maximum, legal and fund-specific limits. */
    private static void exposure(RecordNode fund, Options options, RegRuleSet.Findings out) {
        BigDecimal risk = fund.decimal("Risikoangabe");
        BigDecimal max = fund.decimal("MaxRisikoangabe");
        BigDecimal limit = fund.decimal("GrenzenGesamtrisiko");
        String method = fund.text("Berechnungsmethode");
        String name = fund.text("ISIN").isEmpty() ? "" : " (" + fund.text("ISIN") + ")";
        if (risk != null && max != null && risk.compareTo(max) > 0) {
            out.error("DeRiMV-7", fund.lineOf("MaxRisikoangabe"), "The maximum of the reporting period (" + plain(max)
                    + " %) is lower than the exposure at the reporting date (" + plain(risk) + " %)" + name + ".");
        }
        BigDecimal highest = max != null ? max : risk;
        if (highest == null) {
            return;
        }
        String field = max != null ? "MaxRisikoangabe" : "Risikoangabe";
        utilisation(fund, method, highest, field, limit, options, name, out);
        switch (method) {
            case "Comm" -> {
                if (highest.compareTo(COMMITMENT_LIMIT) > 0) {
                    out.error("DeRiMV-7", fund.lineOf(field), "Commitment approach: the global exposure of " + plain(highest)
                            + " % exceeds 100 % of the net asset value (InvFG 2011)" + name + ".");
                }
            }
            case "aVaR" -> {
                if (highest.compareTo(ABSOLUTE_VAR_LIMIT) > 0) {
                    out.error("DeRiMV-7", fund.lineOf(field), "Absolute VaR: " + plain(highest)
                            + " % exceeds the limit of 20 % of the net asset value" + name + ".");
                }
                if (limit != null) {
                    if (highest.compareTo(limit) > 0) {
                        out.warning("DeRiMV-9", fund.lineOf(field), "Absolute VaR: " + plain(highest)
                                + " % exceeds the limit of the fund rules (" + plain(limit) + " %)" + name + ".");
                    }
                    if (limit.compareTo(ABSOLUTE_VAR_LIMIT) > 0) {
                        out.warning("DeRiMV-9", fund.lineOf("GrenzenGesamtrisiko"), "Absolute VaR: the limit of the fund rules ("
                                + plain(limit) + " %) is above the legal limit of 20 %" + name + ".");
                    }
                }
            }
            case "rVaR" -> {
                if (highest.compareTo(options.relativeVarLimit()) > 0) {
                    out.warning("DeRiMV-7", fund.lineOf(field), "Relative VaR: " + plain(highest) + " % is above "
                            + plain(options.relativeVarLimit()) + " % of the VaR of the reference portfolio (§ 16 4. DeRiMV); "
                            + "check whether the value is reported as the ratio to the reference portfolio" + name + ".");
                }
            }
            default -> {
                // an unknown method is a schema error
            }
        }
    }

    /** Warning when a fund uses {@code warnAt} % of a limit or more, but stays within it (above it is an error / warning already). */
    private static void utilisation(RecordNode fund, String method, BigDecimal highest, String field, BigDecimal ownLimit,
                                    Options options, String name, RegRuleSet.Findings out) {
        if (options.warnAt() == null) {
            return;
        }
        BigDecimal legal = DerimvReport.legalLimit(method, options.relativeVarLimit());
        BigDecimal own = "aVaR".equals(method) ? ownLimit : null;
        for (BigDecimal limit : new BigDecimal[] {own, legal}) {
            if (limit == null || limit.signum() <= 0 || highest.compareTo(limit) > 0) {
                continue;
            }
            BigDecimal use = highest.multiply(BigDecimal.valueOf(100)).divide(limit, 1, java.math.RoundingMode.HALF_UP);
            if (use.compareTo(options.warnAt()) >= 0) {
                out.warning("DeRiMV-use", fund.lineOf(field), method + ": " + plain(highest) + " % uses " + plain(use) + " % of the "
                        + (limit == own ? "fund's own limit" : "legal limit") + " (" + plain(limit) + " %)" + name + ".");
                return;
            }
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
