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

import static org.fxt.freexmltoolkit.plugins.regreport.RegReportHtml.esc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RecordReader;
import org.fxt.freexmltoolkit.plugins.regreport.RegReportHtml;

/** The fund table of the DeRiMV check report: exposure per fund against the legal and the fund's own limit. */
final class DerimvReport {

    /** One fund of the report with its limit utilisation (in % of the limit, {@code null} when there is no limit). */
    record Fund(int line, String isin, String name, String method, BigDecimal risk, BigDecimal max, BigDecimal legalLimit,
                BigDecimal legalUse, BigDecimal ownLimit, BigDecimal ownUse) {

        /** @return the higher of the two utilisations, or {@code null} */
        BigDecimal highestUse() {
            if (legalUse == null) {
                return ownUse;
            }
            return ownUse == null || legalUse.compareTo(ownUse) >= 0 ? legalUse : ownUse;
        }
    }

    private DerimvReport() {
    }

    /** @return the legal limit of a method in % ({@code null} for an unknown method) */
    static BigDecimal legalLimit(String method, BigDecimal relativeVarLimit) {
        return switch (method) {
            case "Comm" -> DerimvRules.COMMITMENT_LIMIT;
            case "aVaR" -> DerimvRules.ABSOLUTE_VAR_LIMIT;
            case "rVaR" -> relativeVarLimit;
            default -> null;
        };
    }

    /** @return the funds of a report in document order */
    static List<Fund> funds(String xml, BigDecimal relativeVarLimit) throws XMLStreamException {
        List<Fund> funds = new ArrayList<>();
        RecordReader.read(xml, Set.of(DerimvRules.ROOT + "/InvestmentFonds"), header -> { }, (RecordNode rec) -> {
            BigDecimal risk = rec.decimal("Risikoangabe");
            BigDecimal max = rec.decimal("MaxRisikoangabe");
            String method = rec.text("Berechnungsmethode");
            BigDecimal highest = max != null ? max : risk;
            BigDecimal legal = legalLimit(method, relativeVarLimit);
            // the own limit only means a % limit of the global exposure for the absolute VaR (see the rules)
            BigDecimal own = "aVaR".equals(method) ? rec.decimal("GrenzenGesamtrisiko") : null;
            funds.add(new Fund(rec.line(), rec.text("ISIN"), rec.text("FondsName"), method, risk, max, legal,
                    use(highest, legal), own, use(highest, own)));
        });
        return funds;
    }

    private static BigDecimal use(BigDecimal value, BigDecimal limit) {
        if (value == null || limit == null || limit.signum() <= 0) {
            return null;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
    }

    /**
     * @param warnAt utilisation (in % of a limit) from which a fund is marked, or {@code null} for none
     * @return the section with one row per fund, the most used first
     */
    static RegReportHtml.Section fundTable(String xml, BigDecimal relativeVarLimit, BigDecimal warnAt) throws XMLStreamException {
        List<Fund> funds = new ArrayList<>(funds(xml, relativeVarLimit));
        funds.sort(java.util.Comparator.comparing(Fund::highestUse, java.util.Comparator.nullsLast(
                java.util.Comparator.reverseOrder())));
        long over = funds.stream().filter(f -> f.highestUse() != null && f.highestUse().compareTo(BigDecimal.valueOf(100)) > 0).count();
        long near = warnAt == null ? 0 : funds.stream().filter(f -> f.highestUse() != null
                && f.highestUse().compareTo(warnAt) >= 0 && f.highestUse().compareTo(BigDecimal.valueOf(100)) <= 0).count();
        StringBuilder html = new StringBuilder("<p>").append(funds.size()).append(funds.size() == 1 ? " fund" : " funds");
        html.append(" · ").append(over).append(" above a limit");
        if (warnAt != null) {
            html.append(" · ").append(near).append(" at ").append(plain(warnAt)).append(" % of a limit or more");
        }
        html.append(". Utilisation = maximum of the period (or the exposure when no maximum is given) in % of the limit; "
                + "own limit = Grenzen des Gesamtrisikos (absolute VaR only).</p>\n");
        html.append("<table><thead><tr><th>ISIN</th><th>Fund</th><th>Method</th><th class=\"num\">Exposure %</th>"
                + "<th class=\"num\">Maximum %</th><th class=\"num\">Legal limit %</th><th class=\"num\">Used</th>"
                + "<th class=\"num\">Own limit %</th><th class=\"num\">Used</th><th class=\"num\">Line</th></tr></thead><tbody>\n");
        for (Fund f : funds) {
            html.append("<tr><td>").append(esc(f.isin())).append("</td><td>").append(esc(f.name())).append("</td><td>")
                    .append(esc(f.method())).append("</td>");
            cell(html, f.risk());
            cell(html, f.max());
            cell(html, f.legalLimit());
            useCell(html, f.legalUse(), warnAt);
            cell(html, f.ownLimit());
            useCell(html, f.ownUse(), warnAt);
            html.append("<td class=\"num\">").append(f.line()).append("</td></tr>\n");
        }
        html.append("</tbody></table>");
        return new RegReportHtml.Section("Funds and limits", html.toString());
    }

    private static void cell(StringBuilder html, BigDecimal value) {
        html.append("<td class=\"num\">").append(value == null ? "" : plain(value)).append("</td>");
    }

    private static void useCell(StringBuilder html, BigDecimal use, BigDecimal warnAt) {
        html.append("<td class=\"num\">");
        if (use != null) {
            String kind = use.compareTo(BigDecimal.valueOf(100)) > 0 ? "error"
                    : warnAt != null && use.compareTo(warnAt) >= 0 ? "warning" : "ok";
            html.append("<span class=\"badge ").append(kind).append("\">").append(plain(use)).append(" %</span>");
        }
        html.append("</td>");
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
