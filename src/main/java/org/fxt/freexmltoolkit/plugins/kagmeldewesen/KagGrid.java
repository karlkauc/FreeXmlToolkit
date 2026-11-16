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

import static org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations.Annotation.filter;
import static org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations.Annotation.formula;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations;

/**
 * The grid formulas and filters of the KAG reports: DeRiMV - funds per method, the highest use of a limit,
 * funds close to a limit (filter); AIFMD AIF file - the sums of the percentage lists that must add up to 100.
 */
final class KagGrid {

    private static final String NUM = " ! xs:decimal(.)";

    private KagGrid() {
    }

    /** @return the annotations for a report kind ({@code DeRiMV}, {@code AIF}), empty for the others */
    static List<GridAnnotations.Annotation> annotations(String kind, KagSettings.Options options) {
        return switch (kind == null ? "" : kind) {
            case "DeRiMV" -> derimv(options);
            case "AIF" -> aif();
            default -> List.of();
        };
    }

    /** @return the XQuery expression of a fund's legal limit (InvestmentFonds as context) */
    static String legalLimit(BigDecimal relativeVarLimit) {
        return "(if (Berechnungsmethode = 'Comm') then 100 else if (Berechnungsmethode = 'aVaR') then 20 else "
                + relativeVarLimit.toPlainString() + ")";
    }

    private static List<GridAnnotations.Annotation> derimv(KagSettings.Options options) {
        String limit = legalLimit(options.relativeVarLimit());
        String highest = "(if (normalize-space(MaxRisikoangabe)) then xs:decimal(MaxRisikoangabe) else xs:decimal(Risikoangabe))";
        String use = "(" + highest + " * 100 div " + limit + ")";
        String warnAt = (options.warn() ? options.warnAt() : KagSettings.DEFAULT_WARN_AT).toPlainString();
        List<GridAnnotations.Annotation> list = new ArrayList<>();
        list.add(filter(DerimvRules.ROOT, "InvestmentFonds[" + use + " ge " + warnAt + "]"));
        list.add(formula(DerimvRules.ROOT, "Fonds", "count(InvestmentFonds)"));
        list.add(formula(DerimvRules.ROOT, "Je_Methode", "string-join(for $m in ('Comm', 'aVaR', 'rVaR') return $m || ': ' "
                + "|| count(InvestmentFonds[Berechnungsmethode = $m]), ' · ')"));
        list.add(formula(DerimvRules.ROOT, "Hoechste_Auslastung", "let $u := max(InvestmentFonds ! " + use + ") return "
                + "if (empty($u)) then '-' else format-number($u, '#.##0,0', 'de') || ' %'"));
        list.add(formula(DerimvRules.ROOT, "Ab_" + warnAt + "_Prozent", "count(InvestmentFonds[" + use + " ge " + warnAt + "])"));
        list.add(formula(DerimvRules.ROOT, "Ueber_Grenze", "count(InvestmentFonds[" + use + " gt 100])"));
        list.add(formula(DerimvRules.ROOT, "Max_aVaR", "let $v := max(InvestmentFonds[Berechnungsmethode = 'aVaR']/MaxRisikoangabe"
                + NUM + ") return if (empty($v)) then '-' else format-number($v, '#.##0,00', 'de') || ' %'"));
        return list;
    }

    private static List<GridAnnotations.Annotation> aif() {
        List<GridAnnotations.Annotation> list = new ArrayList<>();
        String sum = "format-number(sum(%s" + NUM + "), '#.##0,00', 'de')";
        for (String focus : List.of("NAVGeographicalFocus", "AUMGeographicalFocus")) {
            list.add(formula(focus, "Summe", sum.formatted("*[ends-with(local-name(), 'Rate')][normalize-space()]")));
        }
        for (String strategies : List.of("HedgeFundInvestmentStrategies", "PrivateEquityFundInvestmentStrategies",
                "RealEstateFundInvestmentStrategies", "FundOfFundsInvestmentStrategies", "OtherFundInvestmentStrategies")) {
            list.add(formula(strategies, "Summe_NAV", sum.formatted("*/StrategyNAVRate[normalize-space()]")));
        }
        list.add(formula("InvestorConcentration", "Summe", sum.formatted("*[ends-with(local-name(), 'ConcentrationRate')][normalize-space()]")));
        for (String market : List.of("TradedSecurities", "TradedDerivatives")) {
            list.add(formula(market, "Summe", sum.formatted("(RegulatedMarketRate, OTCRate)[normalize-space()]")));
        }
        for (String clearing : List.of("ClearedDerivativesRate", "ClearedReposRate")) {
            list.add(formula(clearing, "Summe", sum.formatted("(CCPRate, BilateralClearingRate, TriPartyRepoClearingRate)[normalize-space()]")));
        }
        list.add(formula("PortfolioLiquidityProfile", "Summe",
                sum.formatted("*[starts-with(local-name(), 'PortfolioLiquidityInDays')][normalize-space()]")));
        list.add(formula("InvestorGroups", "Summe", sum.formatted("InvestorGroup/InvestorGroupRate[normalize-space()]")));
        list.add(formula("FinancingLiquidityProfile", "Summe",
                sum.formatted("*[starts-with(local-name(), 'TotalFinancingInDays')][normalize-space()]")));
        return list;
    }
}
