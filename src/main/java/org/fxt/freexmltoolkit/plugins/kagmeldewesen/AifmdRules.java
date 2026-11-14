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

import static org.fxt.freexmltoolkit.plugins.regreport.FieldRules.in;
import static org.fxt.freexmltoolkit.plugins.regreport.FieldRules.notIn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.FieldRules;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * The FMA's rules ("Prüfregeln AIF/AIFM Reporting File", Stand 29.01.2024) for the AIFMD reporting
 * (ESMA XML schemas v1.2), on top of the schema. Rule ids are {@code FMA-AIF-<field>} and
 * {@code FMA-AIFM-<field>} after the field number of the ESMA tables. The checks of the national
 * codes against the lists of the Incoming Platform cannot run here and are left out.
 */
public final class AifmdRules {

    static final String AIF_ROOT = "AIFReportingInfo";
    static final String AIFM_ROOT = "AIFMReportingInfo";
    static final String AIF_RECORD = AIF_ROOT + "/AIFRecordInfo";
    static final String AIFM_RECORD = AIFM_ROOT + "/AIFMRecordInfo";

    private static final BigDecimal ROUNDING = new BigDecimal("0.5");

    private AifmdRules() {
    }

    // ---------------------------------------------------------------- shared

    /** ReportingMemberState must be AT (the FMA accepts only Austrian reports). */
    private static RegRuleSet.Rule memberStateAt(String id) {
        return (root, ctx, out) -> {
            String state = root.attributes().getOrDefault("ReportingMemberState", "");
            if (!"AT".equals(state)) {
                out.error(id, root.line(), "ReportingMemberState must be AT for the FMA (is \"" + state + "\").");
            }
        };
    }

    /** First day of the reporting period of each period type. */
    static LocalDate periodStart(String type, int year) {
        return switch (type) {
            case "Q1", "H1", "Y1", "X1" -> LocalDate.of(year, 1, 1);
            case "Q2" -> LocalDate.of(year, 4, 1);
            case "Q3", "H2" -> LocalDate.of(year, 7, 1);
            case "Q4" -> LocalDate.of(year, 10, 1);
            default -> null;
        };
    }

    /** Last day of the reporting period of each period type. */
    static LocalDate periodEnd(String type, int year) {
        return switch (type) {
            case "Q1" -> LocalDate.of(year, 3, 31);
            case "Q2", "H1" -> LocalDate.of(year, 6, 30);
            case "Q3", "X1" -> LocalDate.of(year, 9, 30);
            case "Q4", "H2", "Y1" -> LocalDate.of(year, 12, 31);
            default -> null;
        };
    }

    /** Start date matches the period type; end date after the start and inside the period. */
    private static RegRuleSet.Rule reportingPeriod(String prefix, FieldRules f) {
        return (rec, ctx, out) -> {
            String type = rec.text("ReportingPeriodType");
            LocalDate start = FieldRules.date(rec.text("ReportingPeriodStartDate"));
            LocalDate end = FieldRules.date(rec.text("ReportingPeriodEndDate"));
            int year;
            try {
                year = Integer.parseInt(rec.text("ReportingPeriodYear"));
            } catch (NumberFormatException e) {
                return;
            }
            LocalDate expectedStart = periodStart(type, year);
            if (start != null && expectedStart != null && !start.equals(expectedStart)) {
                out.error(prefix + "-6", rec.lineOf("ReportingPeriodStartDate"), f.label("ReportingPeriodStartDate") + " "
                        + start + " does not match the period " + type + "/" + year + " (expected " + expectedStart + ").");
            }
            if (start != null && end != null && !end.isAfter(start)) {
                out.error(prefix + "-7", rec.lineOf("ReportingPeriodEndDate"), f.label("ReportingPeriodEndDate") + " "
                        + end + " must lie after the start date " + start + ".");
            }
            LocalDate periodEnd = periodEnd(type, year);
            if (end != null && periodEnd != null && expectedStart != null && (end.isAfter(periodEnd) || end.isBefore(expectedStart))) {
                out.error(prefix + "-7", rec.lineOf("ReportingPeriodEndDate"), f.label("ReportingPeriodEndDate") + " "
                        + end + " lies outside the period " + type + "/" + year + ".");
            }
        };
    }

    /** FX rate fields depend on the base currency (fields 49–52 of the AIF file, 35–38 of the AIFM file). */
    private static void baseCurrency(RegRuleSet rules, FieldRules f, String prefix, String block, String ids) {
        String[] id = ids.split(",");
        String notEur = "the base currency is not EUR";
        rules.record((rec, ctx, out) -> {
            for (RecordNode b : rec.all(block)) {
                String currency = b.text("BaseCurrency");
                boolean foreign = !currency.isEmpty() && !"EUR".equals(currency);
                for (String[] fx : new String[][] {{"FXEURReferenceRateType", id[1]}, {"FXEURRate", id[2]}}) {
                    if (foreign && !b.filled(fx[0])) {
                        out.error(prefix + "-" + fx[1], b.line(), f.label(block + "/" + fx[0]) + " is missing although " + notEur + ".");
                    } else if (!foreign && b.filled(fx[0])) {
                        out.error(prefix + "-" + fx[1], b.lineOf(fx[0]), f.label(block + "/" + fx[0])
                                + " must not be reported when the base currency is EUR or missing.");
                    }
                }
            }
        });
        rules.record(f.requiredWhen(prefix + "-" + id[3], block, "FXEUROtherReferenceRateDescription",
                "FXEURReferenceRateType", in("OTH"), "the reference rate type is OTH"));
        rules.record(f.forbiddenWhen(prefix + "-" + id[3], block, "FXEUROtherReferenceRateDescription",
                "FXEURReferenceRateType", notIn("OTH"), "the reference rate type is not OTH"));
    }

    private static RegRuleSet.Rule lei(FieldRules f, String id, String path) {
        return f.identifier(id, path, FinancialIdentifiers::checkLei);
    }

    private static RegRuleSet.Rule isin(FieldRules f, String id, String path) {
        return f.identifier(id, path, FinancialIdentifiers::checkIsin);
    }

    // ---------------------------------------------------------------- AIFM file

    /** @return the rules of the AIFM file ({@code AIFMReportingInfo}) */
    public static RegRuleSet aifm(FieldCatalog catalog) {
        FieldRules f = new FieldRules(catalog, AIFM_RECORD);
        String d = "AIFMCompleteDescription";
        RegRuleSet rules = new RegRuleSet(AIFM_RECORD)
                .header(memberStateAt("FMA-AIFM-1"))
                .record(reportingPeriod("FMA-AIFM", f))
                .record(changeQuarter("FMA-AIFM-12", f, "AIFM"))
                .record(f.together("FMA-AIFM-14", "Assumptions/Assumption", "QuestionNumber", "AssumptionDescription"))
                .record(lei(f, "FMA-AIFM-22", d + "/AIFMIdentifier/AIFMIdentifierLEI"))
                .record(f.together("FMA-AIFM-25", d + "/AIFMIdentifier/OldAIFMIdentifierNCA", "ReportingMemberState", "AIFMNationalCode"));
        String market = d + "/AIFMPrincipalMarkets/AIFMFivePrincipalMarket";
        rules.record(f.requiredWhen("FMA-AIFM-28", market + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        in("MIC"), "the market code type is MIC"))
                .record(f.forbiddenWhen("FMA-AIFM-28", market + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        notIn("MIC"), "the market code type is not MIC"))
                .record(f.requiredWhen("FMA-AIFM-29", market, "AggregatedValueAmount", "MarketIdentification/MarketCodeType",
                        v -> !v.isEmpty() && !"NOT".equals(v), "the market code type is not NOT"))
                .record(f.rankingOrder("FMA-AIFM-29", market, "Ranking", "AggregatedValueAmount"))
                .record(f.rankingOrder("FMA-AIFM-32", d + "/AIFMPrincipalInstruments/AIFMPrincipalInstrument", "Ranking",
                        "AggregatedValueAmount"));
        baseCurrency(rules, f, "FMA-AIFM", d + "/AIFMBaseCurrencyDescription", "35,36,37,38");
        return rules;
    }

    /** The change quarter is required when a change of frequency or contents is reported (field 12). */
    private static RegRuleSet.Rule changeQuarter(String id, FieldRules f, String kind) {
        return (rec, ctx, out) -> {
            String prefix = kind + "ReportingObligationChange";
            if ((rec.filled(prefix + "FrequencyCode") || rec.filled(prefix + "ContentsCode")) && !rec.filled(prefix + "Quarter")) {
                out.error(id, rec.line(), f.label(prefix + "Quarter")
                        + " is missing although a change of the reporting obligation is reported.");
            }
        };
    }

    // ---------------------------------------------------------------- AIF file

    /** @return the rules of the AIF file ({@code AIFReportingInfo}) */
    public static RegRuleSet aif(FieldCatalog catalog) {
        FieldRules f = new FieldRules(catalog, AIF_RECORD);
        String c = "AIFCompleteDescription";
        String p = c + "/AIFPrincipalInfo";
        String desc = p + "/AIFDescription";
        String ind = c + "/AIFIndividualInfo";
        String risk = ind + "/RiskProfile";
        RegRuleSet rules = new RegRuleSet(AIF_RECORD)
                .header(memberStateAt("FMA-AIF-1"))
                .record(reportingPeriod("FMA-AIF", f))
                .record(changeQuarter("FMA-AIF-12", f, "AIF"))
                .record(f.together("FMA-AIF-14", "Assumptions/Assumption", "QuestionNumber", "AssumptionDescription"))
                .record(contentBlocks(f))
                .record(f.dateOrder("FMA-AIF-22", "InceptionDate", "ReportingPeriodStartDate", true))
                .record(f.forbiddenWhen("FMA-AIF-23", "", c, "AIFNoReportingFlag", in("true"), "the AIF no reporting flag is true"))
                // identification
                .record(lei(f, "FMA-AIF-24", p + "/AIFIdentification/AIFIdentifierLEI"))
                .record(isin(f, "FMA-AIF-25", p + "/AIFIdentification/AIFIdentifierISIN"))
                .record(f.together("FMA-AIF-32", p + "/AIFIdentification/OldAIFIdentifierNCA", "ReportingMemberState", "AIFNationalCode"))
                .record(f.forbiddenWhen("FMA-AIF-34", p, "ShareClassIdentification", "ShareClassFlag", in("false"),
                        "the share class flag is false"))
                .record(isin(f, "FMA-AIF-35", p + "/ShareClassIdentification/ShareClassIdentifier/ShareClassIdentifierISIN"))
                .record(f.requiredWhen("FMA-AIF-40", p, "ShareClassIdentification/ShareClassIdentifier/ShareClassName",
                        "ShareClassFlag", in("true"), "the share class flag is true"))
                .record(f.forbiddenWhen("FMA-AIF-43", desc, "MasterAIFsIdentification", "AIFMasterFeederStatus",
                        notIn("FEEDER"), "the master feeder status is not FEEDER"))
                .record(lei(f, "FMA-AIF-46", desc + "/PrimeBrokers/PrimeBrokerIdentification/EntityIdentificationLEI"))
                .record(navNotAboveAum(f, desc));
        baseCurrency(rules, f, "FMA-AIF", desc + "/AIFBaseCurrencyDescription", "49,51,50,52");
        strategies(rules, f, desc);
        mainInstruments(rules, f, p);
        rules.record(f.sumIs100("FMA-AIF-78", p + "/NAVGeographicalFocus", List.of("*"), ROUNDING, false))
                .record(f.sumIs100("FMA-AIF-86", p + "/AUMGeographicalFocus", List.of("*"), ROUNDING, false));
        exposures(rules, f, p);
        individual(rules, f, ind, risk, desc);
        leverage(rules, f, c + "/AIFLeverageInfo");
        return rules;
    }

    /** Field 5: the blocks a content type requires (when the no-reporting flag is false). */
    private static RegRuleSet.Rule contentBlocks(FieldRules f) {
        return (rec, ctx, out) -> {
            if (!"false".equals(rec.text("AIFNoReportingFlag"))) {
                return;
            }
            String type = rec.text("AIFContentType");
            String[][] required = {
                    {"AIFCompleteDescription/AIFPrincipalInfo", "1,2,3,4,5"},
                    {"AIFCompleteDescription/AIFIndividualInfo", "2,4"},
                    {"AIFCompleteDescription/AIFLeverageInfo/AIFLeverageArticle24-2", "2,4"},
                    {"AIFCompleteDescription/AIFLeverageInfo/AIFLeverageArticle24-4", "4,5"}};
            for (String[] block : required) {
                if (Set.of(block[1].split(",")).contains(type) && !rec.filled(block[0])) {
                    out.error("FMA-AIF-5", rec.lineOf("AIFContentType"), "The block "
                            + block[0].substring(block[0].lastIndexOf('/') + 1) + " is missing for AIF content type " + type + ".");
                }
            }
        };
    }

    /** Field 53: the NAV is not larger than the AuM. */
    private static RegRuleSet.Rule navNotAboveAum(FieldRules f, String desc) {
        return (rec, ctx, out) -> {
            BigDecimal nav = rec.decimal(desc + "/AIFNetAssetValue");
            BigDecimal aum = rec.decimal(desc + "/AIFBaseCurrencyDescription/AUMAmountInBaseCurrency");
            if (nav != null && aum != null && nav.compareTo(aum) > 0) {
                out.error("FMA-AIF-53", rec.lineOf(desc + "/AIFNetAssetValue"), f.label(desc + "/AIFNetAssetValue") + " "
                        + nav.toPlainString() + " is larger than the AuM " + aum.toPlainString() + ".");
            }
        };
    }

    /** Fields 58–61: investment strategies of the predominant AIF type. */
    private static void strategies(RegRuleSet rules, FieldRules f, String desc) {
        String[][] kinds = {
                {"HedgeFundInvestmentStrategies", "HedgeFundStrategy", "HedgeFundStrategyType", "MULT_HFND", "OTHR_HFND"},
                {"PrivateEquityFundInvestmentStrategies", "PrivateEquityFundInvestmentStrategy", "PrivateEquityFundStrategyType", "MULT_PEQF", "OTHR_PEQF"},
                {"RealEstateFundInvestmentStrategies", "RealEstateFundStrategy", "RealEstateFundStrategyType", "MULT_REST", "OTHR_REST"},
                {"FundOfFundsInvestmentStrategies", "FundOfFundsStrategy", "FundOfFundsStrategyType", "", "OTHR_FOFS"},
                {"OtherFundInvestmentStrategies", "OtherFundStrategy", "OtherFundStrategyType", "", "OTHR_OTHF"}};
        for (String[] k : kinds) {
            String list = desc + "/" + k[0];
            String item = list + "/" + k[1];
            rules.record((rec, ctx, out) -> {
                List<RecordNode> items = rec.all(item);
                if (items.isEmpty()) {
                    return;
                }
                boolean multi = !k[3].isEmpty() && items.stream().anyMatch(i -> k[3].equals(i.text(k[2])));
                if (multi && items.size() < 3) {
                    out.error("FMA-AIF-58", items.getFirst().line(), "With strategy " + k[3]
                            + " at least two further strategies must be reported.");
                }
                BigDecimal sum = BigDecimal.ZERO;
                boolean any = false;
                for (RecordNode i : items) {
                    boolean isMulti = k[3].equals(i.text(k[2]));
                    if (isMulti && i.filled("StrategyNAVRate")) {
                        out.error("FMA-AIF-60", i.lineOf("StrategyNAVRate"), f.label(item + "/StrategyNAVRate")
                                + " must not be reported for strategy " + k[3] + ".");
                    } else if (!isMulti && !i.filled("StrategyNAVRate")) {
                        out.error("FMA-AIF-60", i.line(), f.label(item + "/StrategyNAVRate") + " is missing for strategy "
                                + i.text(k[2]) + ".");
                    }
                    BigDecimal rate = i.decimal("StrategyNAVRate");
                    if (rate != null && !isMulti) {
                        sum = sum.add(rate);
                        any = true;
                    }
                }
                if (any && sum.subtract(BigDecimal.valueOf(100)).abs().compareTo(ROUNDING) > 0) {
                    out.error("FMA-AIF-60", items.getFirst().line(), "The strategy NAV percentages add up to "
                            + sum.stripTrailingZeros().toPlainString() + " %, not 100 %.");
                }
            });
            rules.record(f.requiredWhen("FMA-AIF-61", item, "StrategyTypeOtherDescription", k[2], in(k[4]),
                    "the strategy type is " + k[4]));
        }
    }

    /** Fields 64–77: the main instruments traded. */
    private static void mainInstruments(RegRuleSet rules, FieldRules f, String p) {
        String item = p + "/MainInstrumentsTraded/MainInstrumentTraded";
        String nota = "NTA_NTA_NOTA";
        for (String field : new String[] {"InstrumentCodeType", "InstrumentName", "PositionValue"}) {
            String id = switch (field) {
                case "InstrumentCodeType" -> "FMA-AIF-66";
                case "InstrumentName" -> "FMA-AIF-67";
                default -> "FMA-AIF-75";
            };
            rules.record(f.requiredWhen(id, item, field, "SubAssetType", v -> !v.isEmpty() && !nota.equals(v),
                    "the sub-asset type is not " + nota));
            rules.record(f.forbiddenWhen(id, item, field, "SubAssetType", in(nota), "the sub-asset type is " + nota));
        }
        rules.record(f.forbiddenWhen("FMA-AIF-76", item, "PositionType", "SubAssetType", in(nota), "the sub-asset type is " + nota))
                .record(f.requiredWhen("FMA-AIF-68", item, "ISINInstrumentIdentification", "InstrumentCodeType", in("ISIN"),
                        "the instrument code type is ISIN"))
                .record(isin(f, "FMA-AIF-68", item + "/ISINInstrumentIdentification"))
                .record(f.forbiddenWhen("FMA-AIF-77", item, "ShortPositionHedgingRate", "PositionType", notIn("S"),
                        "the position type is not S"));
        for (String aii : new String[] {"AIIExchangeCode", "AIIProductCode", "AIIDerivativeType", "AIIPutCallIdentifier",
                "AIIExpiryDate", "AIIStrikePrice"}) {
            rules.record(f.requiredWhen("FMA-AIF-69", item, "AIIInstrumentIdentification/" + aii, "InstrumentCodeType",
                    in("AII"), "the instrument code type is AII"));
        }
        // the main instruments' values do not exceed the AuM (1 % tolerance for rounding)
        rules.record((rec, ctx, out) -> {
            BigDecimal aum = rec.decimal("AIFCompleteDescription/AIFPrincipalInfo/AIFDescription/AIFBaseCurrencyDescription/AUMAmountInBaseCurrency");
            List<RecordNode> items = rec.all(item);
            if (aum == null || items.isEmpty()) {
                return;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (RecordNode i : items) {
                BigDecimal v = i.decimal("PositionValue");
                if (v != null) {
                    sum = sum.add(v.abs());
                }
            }
            if (sum.compareTo(aum.multiply(new BigDecimal("1.01"))) > 0) {
                out.error("FMA-AIF-76", items.getFirst().line(), "The position values of the main instruments ("
                        + sum.toPlainString() + ") exceed the AuM (" + aum.toPlainString() + ").");
            }
        });
    }

    /** Fields 94–120: principal exposures, portfolio concentrations, principal markets, investor concentration. */
    private static void exposures(RegRuleSet rules, FieldRules f, String p) {
        String exposure = p + "/PrincipalExposures/PrincipalExposure";
        for (String[] field : new String[][] {{"SubAssetType", "96"}, {"PositionType", "97"}, {"AggregatedValueAmount", "98"},
                {"AggregatedValueRate", "99"}}) {
            rules.record(f.requiredWhen("FMA-AIF-" + field[1], exposure, field[0], "AssetMacroType",
                    v -> !v.isEmpty() && !"NTA".equals(v), "the asset macro type is not NTA"));
            rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], exposure, field[0], "AssetMacroType", in("NTA"),
                    "the asset macro type is NTA"));
        }
        rules.record(f.rankingOrder("FMA-AIF-98", exposure, "Ranking", "AggregatedValueAmount"));

        String conc = p + "/MostImportantConcentration";
        String portfolio = conc + "/PortfolioConcentrations/PortfolioConcentration";
        for (String[] field : new String[][] {{"PositionType", "105"}, {"MarketIdentification", "106"}, {"AggregatedValueRate", "109"}}) {
            rules.record(f.requiredWhen("FMA-AIF-" + field[1], portfolio, field[0], "AssetType",
                    v -> !v.isEmpty() && !"NTA_NTA".equals(v), "the asset type is not NTA_NTA"));
            rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], portfolio, field[0], "AssetType", in("NTA_NTA"),
                    "the asset type is NTA_NTA"));
        }
        rules.record(f.forbiddenWhen("FMA-AIF-108", portfolio, "AggregatedValueAmount", "AssetType", in("NTA_NTA"),
                        "the asset type is NTA_NTA"))
                .record(f.rankingOrder("FMA-AIF-108", portfolio, "Ranking", "AggregatedValueAmount"))
                .record(f.requiredWhen("FMA-AIF-107", portfolio + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        in("MIC"), "the market code type is MIC"))
                .record(f.forbiddenWhen("FMA-AIF-107", portfolio + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        notIn("MIC"), "the market code type is not MIC"))
                .record(f.forbiddenWhen("FMA-AIF-110", portfolio, "CounterpartyIdentification",
                        "MarketIdentification/MarketCodeType", notIn("OTC"), "the market code type is not OTC"));

        String market = conc + "/AIFPrincipalMarkets/AIFPrincipalMarket";
        rules.record(f.requiredWhen("FMA-AIF-116", market + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        in("MIC"), "the market code type is MIC"))
                .record(f.forbiddenWhen("FMA-AIF-116", market + "/MarketIdentification", "MarketCode", "MarketCodeType",
                        notIn("MIC"), "the market code type is not MIC"))
                .record(f.requiredWhen("FMA-AIF-117", market, "AggregatedValueAmount", "MarketIdentification/MarketCodeType",
                        v -> !v.isEmpty() && !"NOT".equals(v), "the market code type is not NOT"))
                .record(f.forbiddenWhen("FMA-AIF-117", market, "AggregatedValueAmount", "MarketIdentification/MarketCodeType",
                        in("NOT"), "the market code type is NOT"))
                .record(f.rankingOrder("FMA-AIF-117", market, "Ranking", "AggregatedValueAmount"))
                .record(f.requiredWhen("FMA-AIF-113", p, "MostImportantConcentration/TypicalPositionSize",
                        "AIFDescription/PredominantAIFType", in("PEQF"), "the predominant AIF type is PEQF"))
                .record(f.forbiddenWhen("FMA-AIF-113", p, "MostImportantConcentration/TypicalPositionSize",
                        "AIFDescription/PredominantAIFType", notIn("PEQF"), "the predominant AIF type is not PEQF"))
                .record(f.sumIs100("FMA-AIF-119", conc + "/InvestorConcentration",
                        List.of("ProfessionalInvestorConcentrationRate", "RetailInvestorConcentrationRate"), ROUNDING, true));
    }

    /** Fields 121–280: the AIF's individual information (exposures, risk profile). */
    private static void individual(RegRuleSet rules, FieldRules f, String ind, String risk, String desc) {
        String exposure = ind + "/IndividualExposure";
        Set<String> gross = Set.of("DER_FEX_INVT", "DER_FEX_HEDG", "DER_IRD_INTR");
        String typeExposure = exposure + "/AssetTypeExposures/AssetTypeExposure";
        rules.record(f.forbiddenWhen("FMA-AIF-122", typeExposure, "GrossValue", "SubAssetType", v -> !gross.contains(v),
                        "the sub-asset type is not DER_FEX_INVT, DER_FEX_HEDG or DER_IRD_INTR"))
                .record(f.requiredWhen("FMA-AIF-122", typeExposure, "GrossValue", "SubAssetType", gross::contains,
                        "the sub-asset type is " + String.join(", ", gross)));
        for (String[] field : new String[][] {{"LongValue", "123"}, {"ShortValue", "124"}}) {
            rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], typeExposure, field[0], "SubAssetType", gross::contains,
                    "the sub-asset type is DER_FEX_INVT, DER_FEX_HEDG or DER_IRD_INTR"));
        }
        Set<String> notional = Set.of("DER_EQD_EQD", "DER_FID_FID", "DER_CDS_CDS", "DER_FEX_INV", "DER_FEX_HED",
                "DER_IRD_IRD", "DER_CTY_CTY", "DER_OTH_OTH");
        rules.record(f.forbiddenWhen("FMA-AIF-127", exposure + "/AssetTypeTurnovers/AssetTypeTurnover", "NotionalValue",
                "TurnoverSubAssetType", v -> !notional.contains(v), "the sub-asset type is not a derivative"));
        // companies with dominant influence: private equity only
        String dominant = exposure + "/CompaniesDominantInfluence/CompanyDominantInfluence";
        rules.record((rec, ctx, out) -> {
            String type = rec.text(desc + "/PredominantAIFType");
            if (!"PEQF".equals(type) && rec.filled(exposure + "/CompaniesDominantInfluence")) {
                out.error("FMA-AIF-131", rec.lineOf(exposure + "/CompaniesDominantInfluence"),
                        "Companies with dominant influence must not be reported when the predominant AIF type is not PEQF.");
            }
        });
        rules.record(f.requiredWhen("FMA-AIF-135", dominant, "OtherTransactionTypeDescription", "TransactionType", in("OTHR"),
                        "the transaction type is OTHR"))
                .record(f.forbiddenWhen("FMA-AIF-135", dominant, "OtherTransactionTypeDescription", "TransactionType",
                        notIn("OTHR"), "the transaction type is not OTHR"));

        // market risk measures
        String measures = risk + "/MarketRiskProfile/MarketRiskMeasures";
        String measure = measures + "/MarketRiskMeasure";
        rules.record((rec, ctx, out) -> {
            if (!rec.has(measures)) {
                return;
            }
            Set<String> present = new java.util.HashSet<>();
            rec.all(measure).forEach(m -> present.add(m.text("RiskMeasureType")));
            for (String required : List.of("NET_EQTY_DELTA", "NET_CS01", "NET_DV01")) {
                if (!present.contains(required)) {
                    out.error("FMA-AIF-138", rec.lineOf(measures), "The market risk measure " + required + " is missing.");
                }
            }
        });
        Set<String> deltas = Set.of("NET_EQTY_DELTA", "NET_FX_DELTA", "NET_CTY_DELTA");
        Set<String> buckets = Set.of("NET_DV01", "NET_CS01");
        rules.record(f.forbiddenWhen("FMA-AIF-139", measure, "RiskMeasureValue", "RiskMeasureType", v -> !deltas.contains(v),
                        "the risk measure type is not NET_EQTY_DELTA, NET_FX_DELTA or NET_CTY_DELTA"))
                .record(f.requiredWhen("FMA-AIF-140", measure, "BucketRiskMeasureValues", "RiskMeasureType", buckets::contains,
                        "the risk measure type is NET_DV01 or NET_CS01"))
                .record(f.forbiddenWhen("FMA-AIF-140", measure, "BucketRiskMeasureValues", "RiskMeasureType",
                        v -> !buckets.contains(v), "the risk measure type is not NET_DV01 or NET_CS01"))
                .record(f.requiredWhen("FMA-AIF-143", measure, "VegaRiskMeasureValues", "RiskMeasureType", in("VEGA_EXPO"),
                        "the risk measure type is VEGA_EXPO"))
                .record(f.forbiddenWhen("FMA-AIF-143", measure, "VegaRiskMeasureValues", "RiskMeasureType", notIn("VEGA_EXPO"),
                        "the risk measure type is not VEGA_EXPO"))
                .record(f.requiredWhen("FMA-AIF-146", measure, "VARRiskMeasureValues", "RiskMeasureType", in("VAR"),
                        "the risk measure type is VAR"))
                .record(f.forbiddenWhen("FMA-AIF-146", measure, "VARRiskMeasureValues", "RiskMeasureType", notIn("VAR"),
                        "the risk measure type is not VAR"))
                .record(f.requiredWhen("FMA-AIF-147", measure, "RiskMeasureDescription", "RiskMeasureValue",
                        v -> !v.isEmpty() && new BigDecimal(v.matches("-?[0-9.]+") ? v : "1").signum() == 0,
                        "the risk measure value is 0"));

        // counterparty risk
        String cp = risk + "/CounterpartyRiskProfile";
        String trading = cp + "/TradingClearingMechanism";
        rules.record(f.sumIs100("FMA-AIF-148", trading + "/TradedSecurities", List.of("RegulatedMarketRate", "OTCRate"), ROUNDING, false))
                .record(f.sumIs100("FMA-AIF-150", trading + "/TradedDerivatives", List.of("RegulatedMarketRate", "OTCRate"), ROUNDING, false))
                .record(f.sumIs100("FMA-AIF-152", trading + "/ClearedDerivativesRate",
                        List.of("CCPRate", "BilateralClearingRate", "TriPartyRepoClearingRate"), ROUNDING, false))
                .record(f.sumIs100("FMA-AIF-154", trading + "/ClearedReposRate",
                        List.of("CCPRate", "BilateralClearingRate", "TriPartyRepoClearingRate"), ROUNDING, false));
        for (String[] list : new String[][] {{"FundToCounterpartyExposures/FundToCounterpartyExposure", "162", "165"},
                {"CounterpartyToFundExposures/CounterpartyToFundExposure", "168", "171"}}) {
            String item = cp + "/" + list[0];
            for (String[] field : new String[][] {{"CounterpartyIdentification", list[1]}, {"CounterpartyTotalExposureRate", list[2]}}) {
                rules.record(f.requiredWhen("FMA-AIF-" + field[1], item, field[0], "CounterpartyExposureFlag", in("true"),
                        "the counterparty exposure flag is true"));
                rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], item, field[0], "CounterpartyExposureFlag", notIn("true"),
                        "the counterparty exposure flag is not true"));
            }
            rules.record(f.rankingOrder("FMA-AIF-" + list[2], item, "Ranking", "CounterpartyTotalExposureRate"));
        }
        rules.record((rec, ctx, out) -> {
            String flag = rec.text(cp + "/ClearTransactionsThroughCCPFlag");
            boolean exposures = rec.filled(cp + "/CCPExposures/CCPExposure");
            if ("true".equals(flag) && !exposures) {
                out.error("FMA-AIF-173", rec.lineOf(cp + "/ClearTransactionsThroughCCPFlag"),
                        "At least one CCP exposure must be reported when the direct clearing flag is true.");
            } else if ("false".equals(flag) && rec.has(cp + "/CCPExposures")) {
                out.error("FMA-AIF-173", rec.lineOf(cp + "/CCPExposures"),
                        "CCP exposures must not be reported when the direct clearing flag is false.");
            }
        });

        // liquidity
        String liquidity = risk + "/LiquidityRiskProfile";
        rules.record(f.sumIs100("FMA-AIF-178", liquidity + "/PortfolioLiquidityProfile", List.of(
                        "PortfolioLiquidityInDays0to1Rate", "PortfolioLiquidityInDays2to7Rate", "PortfolioLiquidityInDays8to30Rate",
                        "PortfolioLiquidityInDays31to90Rate", "PortfolioLiquidityInDays91to180Rate",
                        "PortfolioLiquidityInDays181to365Rate", "PortfolioLiquidityInDays365MoreRate"), ROUNDING, false))
                .record((rec, ctx, out) -> {
                    RecordNode profile = rec.first(liquidity + "/PortfolioLiquidityProfile");
                    if (profile != null && profile.children().stream().anyMatch(ch -> ch.name().startsWith("PortfolioLiquidity"))
                            && !profile.filled("UnencumberedCash")) {
                        out.error("FMA-AIF-185", profile.line(), f.label(liquidity + "/PortfolioLiquidityProfile/UnencumberedCash")
                                + " is missing although a portfolio liquidity profile is reported.");
                    }
                })
                .record((rec, ctx, out) -> {
                    RecordNode redemption = rec.first(liquidity + "/InvestorRedemption");
                    if (redemption != null && !redemption.filled("ProvideWithdrawalRightsFlag")) {
                        out.error("FMA-AIF-193", redemption.line(), "ProvideWithdrawalRightsFlag must be reported.");
                    }
                });
        for (String[] field : new String[][] {{"InvestorRedemptionFrequency", "194"}, {"InvestorRedemptionNoticePeriod", "195"},
                {"InvestorRedemptionLockUpPeriod", "196"}}) {
            rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], liquidity + "/InvestorRedemption", field[0],
                    "ProvideWithdrawalRightsFlag", in("false"), "the withdrawal rights flag is false"));
        }
        rules.record(f.together("FMA-AIF-200", liquidity + "/InvestorArrangement/InvestorIlliquidAssetArrangement/OtherArrangement",
                        "OtherArrangementType", "OtherArrangementRate"))
                .record(f.sumIs100("FMA-AIF-209", liquidity + "/InvestorGroups", List.of("InvestorGroup/InvestorGroupRate"), ROUNDING, false));
        String financing = liquidity + "/FinancingLiquidityProfile";
        List<String> financingRates = List.of("TotalFinancingInDays0to1Rate", "TotalFinancingInDays2to7Rate",
                "TotalFinancingInDays8to30Rate", "TotalFinancingInDays31to90Rate", "TotalFinancingInDays91to180Rate",
                "TotalFinancingInDays181to365Rate", "TotalFinancingInDays365MoreRate");
        rules.record(f.sumIs100("FMA-AIF-211", financing, financingRates, ROUNDING, false))
                .record((rec, ctx, out) -> {
                    RecordNode profile = rec.first(financing);
                    if (profile != null && profile.filled("TotalFinancingAmount")
                            && financingRates.stream().noneMatch(profile::filled)) {
                        out.error("FMA-AIF-211", profile.line(),
                                "The financing liquidity percentages are missing although an available financing amount is reported.");
                    }
                });
        // historical risk profile: only months of the reporting period
        rules.record(monthsInPeriod(risk + "/OperationalRisk/HistoricalRiskProfile"));
    }

    /** Fields 219–278: monthly values only for months inside the reporting period. */
    private static RegRuleSet.Rule monthsInPeriod(String profilePath) {
        return (rec, ctx, out) -> {
            LocalDate start = FieldRules.date(rec.text("ReportingPeriodStartDate"));
            LocalDate end = FieldRules.date(rec.text("ReportingPeriodEndDate"));
            RecordNode profile = rec.first(profilePath);
            if (start == null || end == null || profile == null) {
                return;
            }
            for (RecordNode series : profile.children()) {
                for (RecordNode value : series.children()) {
                    Month month = monthOf(value.name());
                    if (month == null) {
                        continue;
                    }
                    YearMonth ym = YearMonth.of(month.getValue() >= start.getMonthValue() ? start.getYear() : end.getYear(), month);
                    if (ym.isBefore(YearMonth.from(start)) || ym.isAfter(YearMonth.from(end))) {
                        out.error("FMA-AIF-219", value.line(), series.name() + ": " + value.name()
                                + " lies outside the reporting period " + start + " – " + end + ".");
                    }
                }
            }
        };
    }

    /** @return the month in an element name like {@code RateJanuary} / {@code QuantityMarch}, or {@code null} */
    static Month monthOf(String name) {
        for (Month month : Month.values()) {
            String m = month.name().charAt(0) + month.name().substring(1).toLowerCase(Locale.ROOT);
            if (name.endsWith(m)) {
                return month;
            }
        }
        return null;
    }

    /** Fields 281–301: leverage under Article 24(2) and 24(4). */
    private static void leverage(RegRuleSet rules, FieldRules f, String lev) {
        String a242 = lev + "/AIFLeverageArticle24-2";
        rules.record(f.forbiddenWhen("FMA-AIF-282", a242, "AllCounterpartyCollateralRehypothecatedRate",
                "AllCounterpartyCollateralRehypothecationFlag", in("false"), "the rehypothecation flag is false"));
        for (String[] field : new String[][] {{"SecuritiesCashBorrowing/UnsecuredBorrowingAmount", "283"},
                {"SecuritiesCashBorrowing/SecuredBorrowingPrimeBrokerageAmount", "284"},
                {"SecuritiesCashBorrowing/SecuredBorrowingReverseRepoAmount", "285"},
                {"SecuritiesCashBorrowing/SecuredBorrowingOtherAmount", "286"},
                {"ShortPositionBorrowedSecuritiesValue", "289"}}) {
            rules.record((rec, ctx, out) -> {
                RecordNode block = rec.first(a242);
                if (block != null && !block.filled(field[0])) {
                    out.error("FMA-AIF-" + field[1], block.line(), f.label(a242 + "/" + field[0]) + " must be reported.");
                }
            });
        }
        String structure = a242 + "/ControlledStructures/ControlledStructure";
        rules.record(f.requiredWhen("FMA-AIF-293", structure, "ControlledStructureExposureValue",
                "ControlledStructureIdentification/EntityName", v -> !v.isEmpty(), "a controlled structure is named"));
        String source = lev + "/AIFLeverageArticle24-4/BorrowingSource";
        for (String[] field : new String[][] {{"SourceIdentification", "298"}, {"LeverageAmount", "301"}}) {
            rules.record(f.requiredWhen("FMA-AIF-" + field[1], source, field[0], "BorrowingSourceFlag", in("true"),
                    "the borrowing source flag is true"));
            rules.record(f.forbiddenWhen("FMA-AIF-" + field[1], source, field[0], "BorrowingSourceFlag", notIn("true"),
                    "the borrowing source flag is not true"));
        }
        rules.record(f.rankingOrder("FMA-AIF-301", source, "Ranking", "LeverageAmount"));
    }
}
