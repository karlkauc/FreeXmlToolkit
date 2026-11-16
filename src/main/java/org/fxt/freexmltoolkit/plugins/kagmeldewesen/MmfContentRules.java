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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.FieldRules;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;

/**
 * The content rules of ESMA's MMF validation rules v07 (application validation rules) that {@link MmfRules} does not
 * cover: consistency of the fund data (LEIs, master/feeder, manager codes), the yield and stress test breakdowns, the
 * assets (CFI code per asset type, credit assessment, valuation, missing LEIs), the liabilities (investor shares,
 * arrangements, monthly figures per quarter) and the LVNAV report data. Rule ids are {@code ESMA-MMF-<field code>};
 * a rule ESMA words as "a warning is raised" is a warning, the others are errors. Rules on data outside the file
 * (GLEIF, ESMA's register of authorised MMFs, earlier reports) are left out.
 */
final class MmfContentRules {

    /** CFI constructs (X = any character) and the asset types allowed for them (sheet "Asset types per CFI code"). */
    static final Map<String, Set<String>> CFI_ASSET_TYPES = cfiTable();
    /** The families of stress test scenario codes (sheet "Stress Test Scenario codes"). */
    static final Set<String> SCENARIO_FAMILIES = Set.of("LST", "CST", "FST", "IST", "RST", "SST", "MST");

    /**
     * Field codes checked under an id computed at run time, or by a grouped rule of {@link MmfRules} under another
     * field's id (58: base-currency amounts, 97: counterparty names, 109: accrued interest, 119: reset dates).
     * {@code scripts/esma-mmf-rules-audit.py} reads this list.
     */
    static final List<String> COVERED_FIELDS = List.of(
            "16", "24", "42", "45", "72", "73", "75", "76",
            "93", "96", "117", "118", "121", "124", "144", "145", "148", "152", "178", "181", "198", "211", "214", "230",
            "239", "240", "241", "243", "244", "245", "246", "247", "248", "249", "250", "259",
            "261", "264", "267", "270", "273", "276", "279", "282", "285", "288", "291", "294", "297", "300", "303", "306",
            "309", "311", "312", "314", "316", "317", "319", "321", "322", "324", "326", "327",
            "337", "338", "339", "340", "348", "349", "350", "351",
            "63", "64", "65", "71", "74", "77", "125", "153", "172", "199", "215",
            "105", "113", "132", "136", "140", "160", "164", "168", "188", "192", "206", "222", "226", "234", "344");

    private static final Set<String> VNAV = Set.of("STVN", "SDVN");
    private static final Set<String> CNAV_LVNAV = Set.of("STCN", "STLV");
    private static final Set<String> EU_PUBLIC = Set.of("SRPB", "SRCB", "SRSN");
    private static final String STATE_PERIODS = "mmf.periods";

    /** Asset block → the field codes of its CFI code, the LEI ESMA warns about, its credit assessment and valuation. */
    private record AssetFields(String cfi, String leiPath, String lei, String credit, String valuation) {
    }

    private static final Map<String, AssetFields> ASSETS = Map.of(
            "MnyMktInstrmHldg", new AssetFields("93", "PtyData/IssrData/PtyId/LEI", "96", "118", "117"),
            "ScrtstnAsstBckdComrclPprHldg", new AssetFields("121", "PtyData/SpnsrData/PtyId/LEI", "124", "145", "144"),
            "DerivHldg", new AssetFields("148", "PtyData/CtrPtyData/PtyId/LEI", "152", null, null),
            "MnyMktFndHldgInf", new AssetFields("178", "AsstId/AsstLEI", "181", null, null),
            "DpstAncllryLqdAsstHldg", new AssetFields(null, "PtyData/CtrPtyData/PtyId/LEI", "198", null, null),
            "RpAgrmtHldg", new AssetFields("211", "PtyData/CtrPtyData/PtyId/LEI", "214", "230", null));

    /** Monthly blocks of the liabilities → the field code of their first month field (quarters follow in steps). */
    private static final Map<String, Integer> MONTHLY = new LinkedHashMap<>();

    static {
        MONTHLY.put("MnthlyNetAsstValPerUnitInf", 261);
        MONTHLY.put("MnthlySbcptInf", 273);
        MONTHLY.put("MnthlyRedInf", 285);
        MONTHLY.put("MnthlyPmtToInvstrInf", 297);
        MONTHLY.put("MnthlyXchgRateInf", 309);
    }

    private static final String[] QUARTERS = {"FrstQrtr", "ScndQrtr", "ThrdQrtr", "FrthQrtr"};

    private MmfContentRules() {
    }

    /** Adds the rules to the MMF rule set (they run on every {@code FndRpt}). */
    static void addTo(RegRuleSet rules) {
        rules.record(only(MmfContentRules::periods))
                .record(only(MmfContentRules::fundData))
                .record(only(MmfContentRules::yields))
                .record(only(MmfContentRules::stressTests))
                .record(only(MmfContentRules::assets))
                .record(only(MmfContentRules::liabilities))
                .record(only(MmfContentRules::monthly))
                .record(only(MmfContentRules::lowVolatility));
    }

    private static RegRuleSet.Rule only(RegRuleSet.Rule rule) {
        return (record, ctx, out) -> {
            if (record.name().equals("FndRpt")) {
                rule.check(record, ctx, out);
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    /** The reporting period of an update record. */
    private record Period(int year, int from, int to, LocalDate start, LocalDate end) {

        static Period of(RecordNode upd) {
            int year;
            try {
                year = Integer.parseInt(upd.text("RptgYr"));
            } catch (NumberFormatException e) {
                year = 0;
            }
            return new Period(year, MmfRules.quarter(upd.text("RptgPrdFrToQrtr/FrPrd")), MmfRules.quarter(upd.text("RptgPrdFrToQrtr/ToPrd")),
                    FieldRules.date(upd.text("RptgPrd/FrDtToDt/FrDt")), FieldRules.date(upd.text("RptgPrd/FrDtToDt/ToDt")));
        }

        boolean covers(int quarter) {
            return from > 0 && to > 0 && quarter >= from && quarter <= to;
        }
    }

    private static String type(RecordNode upd) {
        return upd.text("FndData/FndAttrbts/MnyMktFndTp/Cd");
    }

    private static BigDecimal sum(List<RecordNode> nodes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (RecordNode n : nodes) {
            BigDecimal v = n.decimal("");
            if (v == null) {
                try {
                    v = new BigDecimal(n.text().strip());
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            sum = sum.add(v);
        }
        return sum;
    }

    private static boolean near100(BigDecimal sum) {
        return sum.compareTo(new BigDecimal("99.99")) >= 0 && sum.compareTo(new BigDecimal("100.01")) <= 0;
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ reporting period (field 6)

    /** Field 6: two reports of one MMF in a file must not overlap within a reporting year. */
    @SuppressWarnings("unchecked")
    private static void periods(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        if (upd == null) {
            return;
        }
        Period p = Period.of(upd);
        if (p.from() == 0 || p.to() == 0) {
            return;
        }
        String key = upd.text("FndData/FndNttyId/NtlRegnNb/Issr") + "|" + upd.text("FndData/FndNttyId/NtlRegnNb/Id") + "|" + p.year();
        Map<String, List<int[]>> seen = (Map<String, List<int[]>>) ctx.state().computeIfAbsent(STATE_PERIODS, k -> new HashMap<>());
        List<int[]> earlier = seen.computeIfAbsent(key, k -> new ArrayList<>());
        for (int[] e : earlier) {
            if (p.from() <= e[1] && e[0] <= p.to()) {
                out.error("ESMA-MMF-6", upd.lineOf("RptgPrdFrToQrtr/FrPrd"), "The reporting period QTR" + p.from() + "–QTR" + p.to()
                        + " overlaps another report of this MMF for " + p.year() + " (QTR" + e[0] + "–QTR" + e[1] + ").");
            }
        }
        earlier.add(new int[] {p.from(), p.to()});
    }

    // ------------------------------------------------------------------ fund data (fields 16–59)

    private static void fundData(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        if (upd == null) {
            return;
        }
        boolean notx = "NOTX".equals(upd.text("RptData/DataSetActn"));
        for (String[] lei : new String[][] {{"FndData/FndNttyId/LEI", "16", "MMF"}, {"FndData/FndMgmtCpny/LEI", "24", "manager of the MMF"}}) {
            if (upd.filled(lei[0])) {
                continue;
            }
            if (notx) {
                out.warning("ESMA-MMF-" + lei[1], upd.line(), "The LEI of the " + lei[2] + " is not reported (no quantitative data, NOTX).");
            } else {
                out.error("ESMA-MMF-" + lei[1], upd.line(), "The LEI of the " + lei[2] + " is mandatory when quantitative data is reported.");
            }
        }
        String mmfLei = upd.text("FndData/FndNttyId/LEI");
        if (!mmfLei.isEmpty()) {
            for (String path : List.of("RptData/QttvData/AsstInf/ScrtstnAsstBckdComrclPprHldg/PtyData/SpnsrData/PtyId/LEI",
                    "RptData/QttvData/AsstInf/DerivHldg/PtyData/CtrPtyData/PtyId/LEI",
                    "RptData/QttvData/AsstInf/DpstAncllryLqdAsstHldg/PtyData/CtrPtyData/PtyId/LEI",
                    "RptData/QttvData/AsstInf/RpAgrmtHldg/PtyData/CtrPtyData/PtyId/LEI",
                    "RptData/QttvData/AsstInf/MnyMktFndHldgInf/AsstId/AsstLEI")) {
                for (RecordNode other : upd.all(path)) {
                    if (mmfLei.equals(other.text())) {
                        out.warning("ESMA-MMF-16", other.line(), "The LEI of the MMF " + mmfLei + " is also reported as "
                                + other.parent().parent().name() + " LEI.");
                    }
                }
            }
        }
        // fields 25–27: manager codes and authorising countries
        String supervising = upd.text("FndData/FndNttyId/NtlRegnNb/Issr");
        List<RecordNode> countries = upd.all("FndData/FndMgmtCpny/FndMgmtCpnyAuthrtyRegnNb/Issr");
        Set<String> seen = new HashSet<>();
        for (RecordNode country : countries) {
            if (!seen.add(country.text())) {
                out.error("ESMA-MMF-27", country.line(), "The country " + country.text() + " is reported twice as authorising CA of the manager.");
            }
        }
        String domicile = upd.text("FndData/FndNttyId/CtryOfDmcl/Ctry");
        if (seen.size() > 1 && CatalogChecks.EU.contains(domicile)) {
            out.error("ESMA-MMF-27", countries.get(1).line(), "Several authorising countries of the manager may only be reported when the "
                    + "MMF is domiciled outside the EU (it is domiciled in " + domicile + ").");
        }
        String managerCode = upd.text("FndData/FndMgmtCpny/FndMgmtCpnyAuthrtyRegnNb/Id");
        String fundManagerCode = upd.text("FndData/FndMgmtCpny/FndAuthrtyRegnNb/Id");
        if (!supervising.isEmpty() && seen.contains(supervising) && !managerCode.isEmpty() && !fundManagerCode.isEmpty()
                && !managerCode.equals(fundManagerCode)) {
            out.error("ESMA-MMF-26", upd.lineOf("FndData/FndMgmtCpny/FndMgmtCpnyAuthrtyRegnNb/Id"), "The manager's national code "
                    + managerCode + " must equal the code " + fundManagerCode + " (field 25): its authorising country is the supervising one.");
        }
        // field 37: depositary LEI
        if (upd.has("FndData/FndAttrbts") && !upd.filled("FndData/FndAttrbts/DpstryId/LEI")) {
            out.error("ESMA-MMF-37", upd.lineOf("FndData/FndAttrbts"), "The LEI of the depositary of the MMF is mandatory.");
        }
        // fields 41, 42, 45: master / feeder
        String master = upd.text("FndData/FndAttrbts/MstrFnd");
        if ("FDER".equals(master) && "false".equals(upd.text("FndData/FndAttrbts/StffWthSvgsPlan"))) {
            out.warning("ESMA-MMF-41", upd.lineOf("FndData/FndAttrbts/MstrFnd"),
                    "A feeder fund (FDER) is not expected when the employee saving scheme indicator is false.");
        }
        for (String[] field : new String[][] {{"FndData/FndAttrbts/MstrFndList/LEI", "42", "LEI"}, {"FndData/FndAttrbts/MstrFndList/Nm", "45", "name"}}) {
            boolean filled = upd.filled(field[0]);
            if ("FDER".equals(master) && !filled) {
                out.error("ESMA-MMF-" + field[1], upd.lineOf("FndData/FndAttrbts/MstrFnd"), "The " + field[2]
                        + " of the master MMF is mandatory for a feeder fund (FDER).");
            } else if (!"FDER".equals(master) && filled) {
                out.error("ESMA-MMF-" + field[1], upd.lineOf(field[0]), "The " + field[2]
                        + " of a master MMF must only be reported for a feeder fund (FDER).");
            }
        }
        // field 56: last report sent
        String events = "FndData/FndAttrbts/FndRltdEvt/";
        boolean last = "true".equals(upd.text(events + "LastRptSnt"));
        boolean ended = upd.filled(events + "LqdtnDt") || upd.filled(events + "MrgrDt");
        if (last && !ended) {
            out.warning("ESMA-MMF-56", upd.lineOf(events + "LastRptSnt"),
                    "Last report sent is true, but neither a liquidation nor a merger date is reported.");
        } else if (!last && ended && upd.has(events + "LastRptSnt")) {
            out.warning("ESMA-MMF-56", upd.lineOf(events + "LastRptSnt"),
                    "A liquidation or merger date is reported, but last report sent is false.");
        }
        // field 59: large MMFs report quarter by quarter
        BigDecimal nav = upd.decimal("RptData/QttvData/PrtflPrfrmnc/FndValtn/NetAsstValPerUnit/RptgCcyAmt");
        Period p = Period.of(upd);
        if (nav != null && nav.compareTo(new BigDecimal("100000000")) > 0 && p.from() > 0 && p.from() != p.to()) {
            out.warning("ESMA-MMF-59", upd.lineOf("RptData/QttvData/PrtflPrfrmnc/FndValtn/NetAsstValPerUnit/RptgCcyAmt"),
                    "The net asset value is above EUR 100 000 000, but the report covers QTR" + p.from() + "–QTR" + p.to()
                            + " instead of one quarter.");
        }
    }

    // ------------------------------------------------------------------ yields (fields 68–77)

    private static void yields(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        RecordNode yld = upd == null ? null : upd.first("RptData/QttvData/PrtflPrfrmnc/Yld");
        if (yld == null) {
            return;
        }
        List<RecordNode> cumulative = yld.all("CmltvRtrsBrkdwn");
        if (!cumulative.isEmpty()) {
            Set<String> types = new HashSet<>();
            for (RecordNode r : cumulative) {
                if (!types.add(r.text("RgTp"))) {
                    out.error("ESMA-MMF-68", r.lineOf("RgTp"), "CmltvRtrsBrkdwn: range type " + r.text("RgTp") + " is reported twice.");
                }
            }
            if (cumulative.size() != 6) {
                out.error("ESMA-MMF-68", cumulative.getFirst().line(), "CmltvRtrsBrkdwn must be reported 6 times, once per range type (is "
                        + cumulative.size() + ").");
            }
            LocalDate inception = FieldRules.date(upd.text("FndData/FndAttrbts/FndRltdEvt/IncptnDt"));
            Period p = Period.of(upd);
            if (inception != null && p.end() != null) {
                Map<String, Boolean> expected = new LinkedHashMap<>();
                expected.put("CYTD", inception.getYear() < p.year());
                expected.put("LT1M", !inception.isAfter(p.end().minusMonths(1)));
                expected.put("LT3M", !inception.isAfter(p.end().minusMonths(3)));
                expected.put("LT1Y", !inception.isAfter(p.end().minusYears(1)));
                expected.put("LT3Y", !inception.isAfter(p.end().minusYears(3)));
                expected.put("LT5Y", !inception.isAfter(p.end().minusYears(5)));
                for (RecordNode r : cumulative) {
                    if (Boolean.TRUE.equals(expected.get(r.text("RgTp"))) && !r.filled("PrfrmncVal/Rate")) {
                        out.warning("ESMA-MMF-68", r.lineOf("RgTp"), "The cumulative return for " + r.text("RgTp")
                                + " is expected: the MMF was launched on " + inception + ".");
                    }
                }
            }
        }
        String type = type(upd);
        checkRates(yld.all("StdPrtflVoltlyBrkdwn"), type, "72", "73", CNAV_LVNAV, VNAV, out);
        checkRates(yld.all("ShdwPrtflVoltlyBrkdwn"), type, "75", "76", VNAV, CNAV_LVNAV, out);
    }

    /** A rate is not expected for {@code noRate} types, "not available" not for {@code noNtav} types (warnings). */
    private static void checkRates(List<RecordNode> items, String type, String rateId, String ntavId, Set<String> noRate,
                                   Set<String> noNtav, RegRuleSet.Findings out) {
        for (RecordNode r : items) {
            if (noRate.contains(type) && r.filled("PrfrmncVal/Rate")) {
                out.warning("ESMA-MMF-" + rateId, r.lineOf("PrfrmncVal/Rate"), r.name() + ": a rate is not expected for an MMF of type " + type + ".");
            }
            if (noNtav.contains(type) && r.filled("PrfrmncVal/NotAvlblVal")) {
                out.warning("ESMA-MMF-" + ntavId, r.lineOf("PrfrmncVal/NotAvlblVal"), r.name() + ": \"not available\" is not expected for an MMF of type "
                        + type + ".");
            }
        }
    }

    // ------------------------------------------------------------------ stress tests (fields 80, 83)

    private static void stressTests(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        if (upd == null) {
            return;
        }
        List<RecordNode> results = upd.all("RptData/QttvData/StrssTst/StrssTstRslt");
        if (results.isEmpty()) {
            return;
        }
        Period p = Period.of(upd);
        Set<LocalDate> dates = new HashSet<>();
        boolean liquidity = false;
        for (RecordNode r : results) {
            LocalDate date = FieldRules.date(r.text("StrssTstDt"));
            String code = r.text("StrssTstScnroCd");
            liquidity |= code.startsWith("LST");
            if (code.length() >= 3 && !SCENARIO_FAMILIES.contains(code.substring(0, 3))) {
                out.warning("ESMA-MMF-83", r.lineOf("StrssTstScnroCd"), "Unknown stress test scenario code " + code + ".");
            }
            if (date == null) {
                continue;
            }
            dates.add(date);
            if (p.end() != null) {
                if (date.isBefore(p.end().minusYears(1))) {
                    out.warning("ESMA-MMF-80", r.lineOf("StrssTstDt"), "The stress test date " + date + " is more than a year before the reporting end date.");
                } else if (date.isBefore(p.end().minusMonths(6))) {
                    out.warning("ESMA-MMF-80", r.lineOf("StrssTstDt"), "The stress test date " + date + " is more than 6 months before the reporting end date.");
                }
            }
            LocalDate expected = p.to() == 4 ? LocalDate.of(p.year(), 12, 31) : p.to() == 2 ? LocalDate.of(p.year(), 6, 30) : null;
            if (expected != null && p.year() > 0 && !date.equals(expected)) {
                out.warning("ESMA-MMF-80", r.lineOf("StrssTstDt"), "The stress test date " + date + " is expected to be " + expected + ".");
            }
        }
        if (dates.size() > 1) {
            out.warning("ESMA-MMF-80", results.getFirst().lineOf("StrssTstDt"), "The stress test dates differ (" + dates.size() + " dates).");
        }
        if (CNAV_LVNAV.contains(type(upd)) && !liquidity) {
            out.error("ESMA-MMF-83", results.getFirst().line(), "A " + type(upd) + " MMF must report the liquidity stress test LST-01.");
        }
    }

    // ------------------------------------------------------------------ assets (fields 92–231)

    private static void assets(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        if (upd == null) {
            return;
        }
        String type = type(upd);
        boolean reverseRepo = false;
        for (RecordNode asset : upd.all("RptData/QttvData/AsstInf/*")) {
            AssetFields f = ASSETS.get(asset.name());
            if (f == null) {
                continue;
            }
            String assetType = asset.text("AsstTp");
            reverseRepo |= "RVPO".equals(assetType);
            if (f.cfi() != null) {
                String cfi = asset.text("AsstId/ClssfctnTp");
                Set<String> allowed = allowedTypes(cfi);
                if (!cfi.isEmpty() && (allowed == null || !allowed.contains(assetType))) {
                    out.error("ESMA-MMF-" + f.cfi(), asset.lineOf("AsstId/ClssfctnTp"), asset.name() + ": the CFI code " + cfi
                            + (allowed == null ? " is not one of the CFI constructs of the guidelines (Annex 5)"
                            : " does not fit the asset type " + assetType + " (allowed: " + String.join(", ", allowed) + ")") + ".");
                }
            }
            if (!asset.filled(f.leiPath())) {
                out.warning("ESMA-MMF-" + f.lei(), asset.line(), asset.name() + ": the LEI is not reported.");
            }
            if (f.credit() != null) {
                String credit = asset.text("AsstValtn/CdtAssmntRslt");
                if ("UFVB".equals(credit) || "NOVF".equals(credit)) {
                    out.warning("ESMA-MMF-" + f.credit(), asset.lineOf("AsstValtn/CdtAssmntRslt"), asset.name()
                            + ": the credit assessment is " + credit + ".");
                }
                boolean mmi = "MnyMktInstrmHldg".equals(asset.name());
                if ("NOAP".equals(credit) && (!mmi || !EU_PUBLIC.contains(asset.text("PtyData/IssrData/Sctr")))) {
                    out.warning("ESMA-MMF-" + f.credit(), asset.lineOf("AsstValtn/CdtAssmntRslt"), asset.name()
                            + ": \"assessment not applicable\" (NOAP) is only expected for money market instruments of EU public issuers.");
                }
            }
            String valuation = asset.text("AsstValtn/ValtnTp");
            if (f.valuation() != null && "AMCS".equals(valuation) && VNAV.contains(type)) {
                out.warning("ESMA-MMF-" + f.valuation(), asset.lineOf("AsstValtn/ValtnTp"), asset.name()
                        + ": amortised cost (AMCS) is not expected for an MMF of type " + type + ".");
            }
            if ("MnyMktInstrmHldg".equals(asset.name()) && "MTMA".equals(valuation) && !asset.has("AsstValtn/AcrdIntrst")) {
                out.error("ESMA-MMF-110", asset.lineOf("AsstValtn/ValtnTp"), asset.name()
                        + ": accrued interest must be reported for the valuation method MTMA.");
            }
        }
        boolean collateral = upd.has("RptData/QttvData/AsstInf/RvsRpAgrmtCollData");
        if (reverseRepo && !collateral) {
            out.error("ESMA-MMF-231", upd.lineOf("RptData/QttvData/AsstInf"),
                    "The collateral of the reverse repurchase agreements (RvsRpAgrmtCollData) must be reported.");
        } else if (!reverseRepo && collateral) {
            out.error("ESMA-MMF-231", upd.lineOf("RptData/QttvData/AsstInf/RvsRpAgrmtCollData"),
                    "RvsRpAgrmtCollData must only be reported with a reverse repurchase agreement (asset type RVPO).");
        }
    }

    /** @return the asset types allowed for a CFI code, or {@code null} when no construct matches */
    static Set<String> allowedTypes(String cfi) {
        if (cfi == null || cfi.length() != 6) {
            return null;
        }
        for (Map.Entry<String, Set<String>> e : CFI_ASSET_TYPES.entrySet()) {
            String construct = e.getKey();
            boolean match = true;
            for (int i = 0; i < 6 && match; i++) {
                match = construct.charAt(i) == 'X' || construct.charAt(i) == cfi.charAt(i);
            }
            if (match) {
                return e.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ liabilities (fields 238–260)

    private static void liabilities(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        RecordNode liab = upd == null ? null : upd.first("RptData/QttvData/LbltyInf");
        if (liab == null) {
            return;
        }
        RecordNode concentration = liab.first("InvstrCncntrtn");
        if (concentration != null) {
            BigDecimal s = sum(concentration.all("*/*"));
            if (!near100(s)) {
                out.error("ESMA-MMF-238", concentration.line(), "The retail and professional investor rates add up to " + plain(s)
                        + " %, not 99.99–100.01 %.");
            }
        }
        RecordNode groups = liab.first("InvstrGrpBrkdwn");
        if (groups != null) {
            BigDecimal s = sum(groups.children());
            if (!near100(s)) {
                out.error("ESMA-MMF-242", groups.line(), "The investor group rates add up to " + plain(s) + " %, not 99.99–100.01 %.");
            }
        }
        List<RecordNode> countries = liab.all("BrkdwnByCtry/Rate");
        if (!countries.isEmpty()) {
            BigDecimal s = sum(countries);
            if (!near100(s)) {
                out.error("ESMA-MMF-255", countries.getFirst().line(), "The rates per country add up to " + plain(s) + " %, not 99.99–100.01 %.");
            }
        }
        if ("0".equals(liab.text("NtceDays").replaceFirst("^0+(?=.)", "")) && "UCIT".equals(upd.text("FndData/FndAttrbts/LglFrmwk/Cd"))) {
            out.warning("ESMA-MMF-257", liab.lineOf("NtceDays"), "A notice period of 0 days is not expected for a UCITS.");
        }
        List<RecordNode> arrangements = liab.all("BrkdwnByArrgmnt");
        if (!arrangements.isEmpty()) {
            Set<String> types = new HashSet<>();
            for (RecordNode a : arrangements) {
                if (!types.add(a.text("ArrgmntTp"))) {
                    out.error("ESMA-MMF-258", a.lineOf("ArrgmntTp"), "The arrangement type " + a.text("ArrgmntTp") + " is reported twice.");
                }
                if (!a.filled("NetAsstValRate")) {
                    out.error("ESMA-MMF-259", a.line(), "The net asset value rate of arrangement " + a.text("ArrgmntTp") + " is missing.");
                }
                BigDecimal rate = a.decimal("NetAsstValRate");
                if ("OTHR".equals(a.text("ArrgmntTp")) && rate != null && rate.signum() != 0 && !liab.filled("OthrArrgmntAddtlInf")) {
                    out.error("ESMA-MMF-260", a.line(), "The other arrangement (OTHR) needs a description (OthrArrgmntAddtlInf).");
                }
            }
            if (arrangements.size() != 4) {
                out.error("ESMA-MMF-258", arrangements.getFirst().line(), "BrkdwnByArrgmnt must be reported 4 times, once per arrangement type (is "
                        + arrangements.size() + ").");
            }
        }
    }

    // ------------------------------------------------------------------ monthly figures (fields 261–327)

    private static void monthly(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        RecordNode liab = upd == null ? null : upd.first("RptData/QttvData/LbltyInf");
        if (liab == null) {
            return;
        }
        Period p = Period.of(upd);
        if (p.from() == 0 || p.to() == 0) {
            return;
        }
        String base = upd.text("FndData/FndAttrbts/BaseCcy");
        for (Map.Entry<String, Integer> block : MONTHLY.entrySet()) {
            RecordNode b = liab.first(block.getKey());
            if (b == null) {
                continue;
            }
            for (int q = 1; q <= 4; q++) {
                RecordNode quarter = b.first(QUARTERS[q - 1]);
                String id = "ESMA-MMF-" + (block.getValue() + (q - 1) * (block.getKey().equals("MnthlyXchgRateInf") ? 5 : 3));
                if (p.covers(q) && quarter == null) {
                    out.error(id, b.line(), block.getKey() + ": the months of quarter " + q + " must be reported (it lies in the reporting period).");
                } else if (!p.covers(q) && quarter != null) {
                    out.error(id, quarter.line(), block.getKey() + ": quarter " + q + " lies outside the reporting period and must not be reported.");
                }
                if (quarter == null) {
                    continue;
                }
                Set<String> months = new HashSet<>();
                quarter.all("MnthInf/Mnth").forEach(m -> months.add(m.text()));
                for (int m = 1; m <= 3; m++) {
                    String month = String.format("MM%02d", (q - 1) * 3 + m);
                    if (!months.contains(month)) {
                        out.error(id, quarter.line(), block.getKey() + ": month " + month + " is missing in quarter " + q + ".");
                    }
                }
                if (block.getKey().equals("MnthlyXchgRateInf")) {
                    for (RecordNode fx : quarter.all("MnthInf/Val/FX")) {
                        String quoted = fx.text("QtdCcy");
                        int qId = block.getValue() + (q - 1) * 5;
                        if (!base.isEmpty() && !quoted.isEmpty() && !quoted.equals(base)) {
                            out.error("ESMA-MMF-" + (qId + 2), fx.lineOf("QtdCcy"), "The quoted currency " + quoted
                                    + " must equal the base currency " + base + ".");
                        }
                        BigDecimal rate = fx.decimal("XchgRate");
                        if ("EUR".equals(quoted) && rate != null && rate.compareTo(BigDecimal.ONE) != 0) {
                            out.error("ESMA-MMF-" + (qId + 3), fx.lineOf("XchgRate"), "With the quoted currency EUR the exchange rate must be 1 (is "
                                    + plain(rate) + ").");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ LVNAV report data (fields 329–352)

    private static void lowVolatility(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode upd = rec.first("Upd");
        RecordNode lv = upd == null ? null : upd.first("RptData/QttvData/LwVoltlyNetAsstValRptData");
        if (lv == null) {
            return;
        }
        if (!"STLV".equals(type(upd))) {
            out.error("ESMA-MMF-329", lv.line(), "LwVoltlyNetAsstValRptData must only be reported for an LVNAV MMF (STLV), not "
                    + type(upd) + ".");
        }
        Period p = Period.of(upd);
        Set<String> portfolioCfis = new HashSet<>();
        upd.all("RptData/QttvData/AsstInf/*/AsstId/ClssfctnTp").forEach(c -> portfolioCfis.add(c.text()));
        for (RecordNode e : lv.all("AmtsdCostMtdPricDvtnEvt")) {
            notAfterEnd(e, "ValtnDt", "329", p, out);
            String cfi = e.text("AsstId/ClssfctnTp");
            if (!cfi.isEmpty() && !portfolioCfis.contains(cfi)) {
                out.warning("ESMA-MMF-331", e.lineOf("AsstId/ClssfctnTp"), "The CFI code " + cfi + " is not one of the portfolio's assets.");
            }
            BigDecimal market = e.decimal("RptgCcyMktPric/Amt");
            BigDecimal amortised = e.decimal("RptgCcyAmtsdCostPric/Amt");
            if (market != null && amortised != null && market.subtract(amortised).abs().compareTo(new BigDecimal("0.1")) < 0) {
                out.warning("ESMA-MMF-332", e.lineOf("RptgCcyMktPric/Amt"), "Market and amortised cost price differ by less than 0.1.");
            }
            basisPoints(e, "AvrgBsisPtSprd", "337", 10, true, out);
            basisPoints(e, "LwstPricDvtn", "338", 10, false, out);
            basisPoints(e, "HghstPricDvtn", "339", 10, false, out);
        }
        for (RecordNode e : lv.all("CstNetAsstValDvtnEvt")) {
            notAfterEnd(e, "ValtnDt", "340", p, out);
            BigDecimal constant = e.decimal("CstNetAsstValPerUnit/BaseCcyAmt");
            BigDecimal nav = e.decimal("NetAsstValPerUnit/BaseCcyAmt");
            if (constant != null && nav != null && constant.subtract(nav).abs().compareTo(new BigDecimal("0.2")) < 0) {
                out.warning("ESMA-MMF-341", e.lineOf("CstNetAsstValPerUnit/BaseCcyAmt"), "Constant NAV and NAV per unit differ by less than 0.2.");
            }
            basisPoints(e, "AvrgBsisPtSprd", "348", 20, true, out);
            basisPoints(e, "LwstPricDvtn", "349", 20, false, out);
            basisPoints(e, "HghstPricDvtn", "350", 20, false, out);
        }
        for (RecordNode e : lv.all("MtrgAsstThrshldEvt")) {
            notAfterEnd(e, "EvtDt", "351", p, out);
            LocalDate measure = FieldRules.date(e.text("MeasrDt"));
            LocalDate event = FieldRules.date(e.text("EvtDt"));
            if (measure != null && p.start() != null && p.end() != null && (measure.isBefore(p.start()) || measure.isAfter(p.end()))) {
                out.error("ESMA-MMF-352", e.lineOf("MeasrDt"), "The measure date " + measure + " lies outside the reporting period.");
            }
            if (measure != null && event != null && !measure.isAfter(event)) {
                out.error("ESMA-MMF-352", e.lineOf("MeasrDt"), "The measure date " + measure + " must lie after the event date " + event + ".");
            }
        }
    }

    private static void notAfterEnd(RecordNode e, String field, String id, Period p, RegRuleSet.Findings out) {
        LocalDate date = FieldRules.date(e.text(field));
        if (date != null && p.end() != null && date.isAfter(p.end())) {
            out.error("ESMA-MMF-" + id, e.lineOf(field), "The date " + date + " must not lie after the reporting end date " + p.end() + ".");
        }
    }

    /** @param strict whether the value must be greater (else greater or equal, by absolute value) */
    private static void basisPoints(RecordNode e, String field, String id, int limit, boolean strict, RegRuleSet.Findings out) {
        BigDecimal v = e.decimal(field);
        if (v == null) {
            return;
        }
        BigDecimal value = strict ? v : v.abs();
        int c = value.compareTo(BigDecimal.valueOf(limit));
        if (strict ? c <= 0 : c < 0) {
            out.error("ESMA-MMF-" + id, e.lineOf(field), field + " must be " + (strict ? "greater than " : "at least ") + limit
                    + " basis points (is " + plain(v) + ").");
        }
    }

    private static Map<String, Set<String>> cfiTable() {
        Map<String, Set<String>> t = new LinkedHashMap<>();
        for (String c : List.of("CEXXXX", "CFXXXX", "CIXXXX", "CMXXXX", "CSXXXX")) {
            t.put(c, Set.of("MMFT"));
        }
        for (String c : List.of("DAXXXX", "DGXXXX")) {
            t.put(c, Set.of("ABCP", "STSA", "MMII"));
        }
        for (String c : List.of("DBXXXX", "DDXXXX", "DNXXXX", "DTXXXX")) {
            t.put(c, Set.of("SCRT", "STSS", "MMII"));
        }
        for (String c : List.of("DEXXXX", "DSXXXX", "DWXXXX")) {
            t.put(c, Set.of("SCRT", "STSS"));
        }
        t.put("DYXXXX", Set.of("MMII"));
        for (String u : List.of("B", "C", "D", "F", "I", "N", "O", "W")) {
            t.put("FF" + u + "XXX", Set.of("RMAD", "RMBD", "RMCD"));
            t.put("OCX" + u + "XX", Set.of("RMAD", "RMBD", "RMCD"));
            t.put("OPX" + u + "XX", Set.of("RMAD", "RMBD", "RMCD"));
        }
        t.put("OMXXXX", Set.of("RMAD", "RMBD", "RMCD"));
        for (String c : List.of("HFXXXX", "HRXXXX", "JFXXXX", "JRXXXX")) {
            t.put(c, Set.of("OTCD"));
        }
        for (String c : List.of("SFXXXX", "SRXXXX")) {
            t.put(c, Set.of("OTCD", "RMAD", "RMBD", "RMCD"));
        }
        t.put("LRXXXX", Set.of("REPO", "RVPO"));
        return java.util.Collections.unmodifiableMap(t);
    }
}
