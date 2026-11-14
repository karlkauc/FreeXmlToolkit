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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.FieldRules;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * The application validation rules of ESMA's money market fund report (MMFR Art. 37,
 * auth.093.001.01, validation rules v07) that can be checked on the file alone, on top of the schema.
 * Rule ids are {@code ESMA-MMF-<field code>}. Checks against ESMA's register (authorised MMFs, the
 * reporting history) cannot run here and are left out; for the FMA the sending organisation is AT.
 */
public final class MmfRules {

    /** The namespace of the report (the DRAFT6 identifier is the one ESMA's schema v04.1 uses). */
    static final Set<String> NAMESPACES = Set.of("urn:iso:std:iso:20022:tech:xsd:DRAFT6auth.093.001.01",
            "urn:iso:std:iso:20022:tech:xsd:auth.093.001.01");
    static final List<String> REPORT_PREFIXES = List.of("BizData/Pyld/Document/MnyMktFndRpt", "Document/MnyMktFndRpt");
    private static final String STATE_FROM = "mmf.from";
    private static final String STATE_CREATED = "mmf.created";
    private static final String STATE_KEYS = "mmf.keys";

    private MmfRules() {
    }

    /** @return whether a document is a money market fund report (envelope or bare document) */
    static boolean isMmf(String rootName, String rootNamespace, String content) {
        if (NAMESPACES.contains(rootNamespace)) {
            return true;
        }
        if (!"BizData".equals(rootName)) {
            return false;
        }
        String head = content.length() > 8000 ? content.substring(0, 8000) : content;
        return head.contains("auth.093.001.01") || head.contains("MnyMktFndRpt");
    }

    /** @return the rules of the report */
    public static RegRuleSet rules(FieldCatalog catalog) {
        FieldRules f = new FieldRules(catalog, REPORT_PREFIXES.getFirst() + "/FndRpt");
        RegRuleSet rules = new RegRuleSet("BizData/Hdr/AppHdr", REPORT_PREFIXES.get(0) + "/FndRpt", REPORT_PREFIXES.get(1) + "/FndRpt");
        rules.record(MmfRules::header)
                .record(only("FndRpt", CatalogChecks.of(catalog, "ESMA-MMF", REPORT_PREFIXES)))
                .record(only("FndRpt", MmfRules::period))
                .record(only("FndRpt", MmfRules::duplicates))
                .record(only("FndRpt", MmfRules::fundData))
                .record(only("FndRpt", MmfRules::liquidity))
                .record(only("FndRpt", MmfRules::stressTests))
                .record(only("FndRpt", MmfRules::assets))
                .record(only("FndRpt", MmfRules::baseCurrencyAmounts))
                .record(only("FndRpt", f.identifier("ESMA-MMF-LEI", "Upd/RptData/QttvData/AsstInf/*/PtyData/*/PtyId/LEI",
                        FinancialIdentifiers::checkLei)));
        return rules;
    }

    private static RegRuleSet.Rule only(String recordName, RegRuleSet.Rule rule) {
        return (record, ctx, out) -> {
            if (record.name().equals(recordName)) {
                rule.check(record, ctx, out);
            }
        };
    }

    /** Fields 1, 2, 4: the business application header. */
    private static void header(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        if (!rec.name().equals("AppHdr")) {
            return;
        }
        String from = rec.text("Fr/OrgId/Id/OrgId/Othr/Id");
        ctx.state().put(STATE_FROM, from);
        ctx.state().put(STATE_CREATED, FieldRules.date(rec.text("CreDt")));
        if (!from.isEmpty() && !"AT".equals(from)) {
            out.error("ESMA-MMF-1", rec.lineOf("Fr"), "From organisation must be the country of the supervising NCA - AT for the FMA (is \""
                    + from + "\").");
        }
        String to = rec.text("To/OrgId/Id/OrgId/Othr/Id");
        if (!"EU".equals(to)) {
            out.error("ESMA-MMF-2", rec.lineOf("To"), "To organisation must be \"EU\" (is \"" + to + "\").");
        }
        String msg = rec.text("MsgDefIdr");
        if (!msg.equals("auth.093.001.01") && !msg.equals("DRAFT6auth.093.001.01")) {
            out.error("ESMA-MMF-4", rec.lineOf("MsgDefIdr"), "MsgDefIdr must be auth.093.001.01 or DRAFT6auth.093.001.01 (is \""
                    + msg + "\").");
        }
    }

    private static RecordNode block(RecordNode rec) {
        RecordNode upd = rec.first("Upd");
        return upd != null ? upd : rec.first("Cxl");
    }

    static int quarter(String code) {
        return switch (code) {
            case "QTR1" -> 1;
            case "QTR2" -> 2;
            case "QTR3" -> 3;
            case "QTR4" -> 4;
            default -> 0;
        };
    }

    /** Fields 5, 7, 8, 13, 14, 15: reporting year and period, dates, supervising country. */
    private static void period(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode b = block(rec);
        if (b == null) {
            return;
        }
        int year;
        try {
            year = Integer.parseInt(b.text("RptgYr"));
        } catch (NumberFormatException e) {
            return;
        }
        if (year < 2020) {
            out.error("ESMA-MMF-8", b.lineOf("RptgYr"), "The reporting year must be 2020 or later (is " + year + ").");
        }
        int from = quarter(b.text("RptgPrdFrToQrtr/FrPrd"));
        int to = quarter(b.text("RptgPrdFrToQrtr/ToPrd"));
        if (from > 0 && to > 0 && (to < from || (from == 4 && to != 4))) {
            out.error("ESMA-MMF-7", b.lineOf("RptgPrdFrToQrtr/ToPrd"), "The period to QTR" + to + " does not fit the period from QTR"
                    + from + ".");
        }
        String issuer = b.first("Cxl".equals(b.name()) ? "NtlRegnNb" : "FndData/FndNttyId/NtlRegnNb") == null ? ""
                : b.text("Cxl".equals(b.name()) ? "NtlRegnNb/Issr" : "FndData/FndNttyId/NtlRegnNb/Issr");
        Object sender = ctx.state().get(STATE_FROM);
        if (!issuer.isEmpty() && sender instanceof String s && !s.isEmpty() && !issuer.equals(s)) {
            out.error("ESMA-MMF-13", rec.line(), "The country of the authorising CA (" + issuer
                    + ") must equal the supervising CA (From organisation " + s + ").");
        }
        if (!"Upd".equals(b.name()) || from == 0 || to == 0) {
            return;
        }
        LocalDate periodStart = LocalDate.of(year, (from - 1) * 3 + 1, 1);
        LocalDate periodEnd = LocalDate.of(year, to * 3, 1).plusMonths(1).minusDays(1);
        LocalDate start = FieldRules.date(b.text("RptgPrd/FrDtToDt/FrDt"));
        LocalDate end = FieldRules.date(b.text("RptgPrd/FrDtToDt/ToDt"));
        if (start != null && (start.isBefore(periodStart) || start.isAfter(periodEnd))) {
            out.error("ESMA-MMF-14", b.lineOf("RptgPrd/FrDtToDt/FrDt"), "The start date " + start + " lies outside the reporting period "
                    + periodStart + " – " + periodEnd + ".");
        }
        if (end != null && (end.isBefore(periodStart) || end.isAfter(periodEnd))) {
            out.error("ESMA-MMF-15", b.lineOf("RptgPrd/FrDtToDt/ToDt"), "The end date " + end + " lies outside the reporting period "
                    + periodStart + " – " + periodEnd + ".");
        }
        if (start != null && end != null && end.isBefore(start)) {
            out.error("ESMA-MMF-15", b.lineOf("RptgPrd/FrDtToDt/ToDt"), "The end date " + end + " lies before the start date " + start + ".");
        }
        if (ctx.state().get(STATE_CREATED) instanceof LocalDate created && end != null && !created.isAfter(end)) {
            out.error("ESMA-MMF-5", rec.line(), "The file was created on " + created + ", not after the reporting end date " + end + ".");
        }
    }

    /** Field 12: no two records with the same country, national code, year and period range. */
    @SuppressWarnings("unchecked")
    private static void duplicates(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode b = block(rec);
        if (b == null) {
            return;
        }
        String id = "Cxl".equals(b.name()) ? b.text("NtlRegnNb/Id") : b.text("FndData/FndNttyId/NtlRegnNb/Id");
        String issuer = "Cxl".equals(b.name()) ? b.text("NtlRegnNb/Issr") : b.text("FndData/FndNttyId/NtlRegnNb/Issr");
        String key = String.join("|", issuer, id, b.text("RptgYr"), b.text("RptgPrdFrToQrtr/FrPrd"), b.text("RptgPrdFrToQrtr/ToPrd"));
        Set<String> keys = (Set<String>) ctx.state().computeIfAbsent(STATE_KEYS, k -> new HashSet<String>());
        if (!keys.add(key)) {
            out.error("ESMA-MMF-12", rec.line(), "The file holds two reports for MMF " + issuer + " " + id + ", " + b.text("RptgYr") + " "
                    + b.text("RptgPrdFrToQrtr/FrPrd") + "–" + b.text("RptgPrdFrToQrtr/ToPrd") + ".");
        }
    }

    /** Fields 46, 52–55: share classes and fund events. */
    private static void fundData(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode b = rec.first("Upd");
        if (b == null) {
            return;
        }
        RecordNode attrs = b.first("FndData/FndAttrbts");
        if (attrs != null) {
            String indicator = attrs.text("ShrClssInd");
            List<RecordNode> classes = attrs.all("ShrClss/ShrClssList");
            if ("false".equals(indicator) && !attrs.has("ShrClss/NoShrClssInf")) {
                out.error("ESMA-MMF-46", attrs.lineOf("ShrClssInd"), "Without share classes NoShrClssInf must be reported.");
            }
            if ("true".equals(indicator) && classes.size() < 2) {
                out.error("ESMA-MMF-46", attrs.lineOf("ShrClssInd"), "With share classes more than one share class must be reported.");
            }
            if (!classes.isEmpty()) {
                long highest = classes.stream().filter(c -> "true".equals(c.text("HghstNetAsstVal"))).count();
                if (highest != 1) {
                    out.error("ESMA-MMF-52", classes.getFirst().line(), "Exactly one share class must be flagged with the highest net asset value ("
                            + highest + " are).");
                }
            }
        }
        LocalDate start = FieldRules.date(b.text("RptgPrd/FrDtToDt/FrDt"));
        LocalDate end = FieldRules.date(b.text("RptgPrd/FrDtToDt/ToDt"));
        String events = "FndData/FndAttrbts/FndRltdEvt/";
        LocalDate inception = FieldRules.date(b.text(events + "IncptnDt"));
        if (inception != null && end != null && !inception.isBefore(end)) {
            out.error("ESMA-MMF-53", b.lineOf(events + "IncptnDt"), "The inception date " + inception + " must lie before the reporting end date " + end + ".");
        }
        LocalDate merger = FieldRules.date(b.text(events + "MrgrDt"));
        if (merger != null && end != null && !merger.equals(end)) {
            out.error("ESMA-MMF-54", b.lineOf(events + "MrgrDt"), "The merger date " + merger + " must equal the reporting end date " + end + ".");
        }
        LocalDate liquidation = FieldRules.date(b.text(events + "LqdtnDt"));
        if (liquidation != null && start != null && end != null && (liquidation.isBefore(start) || liquidation.isAfter(end))) {
            out.error("ESMA-MMF-55", b.lineOf(events + "LqdtnDt"), "The liquidation date " + liquidation + " lies outside the reporting period.");
        }
    }

    /** Fields 63–77: liquidity rates, liquidity profile and the yield breakdowns. */
    private static void liquidity(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode perf = rec.first("Upd/RptData/QttvData/PrtflPrfrmnc");
        if (perf == null) {
            return;
        }
        for (String[] rate : new String[][] {{"LqdtyInf/DalyMtrgAsstRate", "63"}, {"LqdtyInf/WklyMtrgAsstRate", "64"}}) {
            BigDecimal value = perf.decimal(rate[0]);
            if (value != null && value.compareTo(BigDecimal.valueOf(100)) >= 0) {
                out.error("ESMA-MMF-" + rate[1], perf.lineOf(rate[0]), rate[0].substring(rate[0].indexOf('/') + 1) + " must be less than 100 (is "
                        + value.toPlainString() + ").");
            }
        }
        rangesOnce(perf, "LqdtyInf/PrtflLqdtyBrkdwn", 4, "65", out);
        rangesOnce(perf, "Yld/StdPrtflVoltlyBrkdwn", 3, "71", out);
        rangesOnce(perf, "Yld/ShdwPrtflVoltlyBrkdwn", 3, "74", out);
        rangesOnce(perf, "Yld/CalYrPrfrmncBrkdwn", 3, "77", out);
        List<RecordNode> ranges = perf.all("LqdtyInf/PrtflLqdtyBrkdwn");
        if (!ranges.isEmpty() && ranges.stream().noneMatch(r -> r.has("PrfrmncVal/NotAvlblVal"))) {
            BigDecimal sum = BigDecimal.ZERO;
            for (RecordNode r : ranges) {
                BigDecimal v = r.decimal("PrfrmncVal/Rate");
                if (v != null) {
                    sum = sum.add(v);
                }
            }
            if (sum.compareTo(new BigDecimal("99.99")) < 0 || sum.compareTo(new BigDecimal("100.01")) > 0) {
                out.error("ESMA-MMF-66", ranges.getFirst().line(), "The portfolio liquidity percentages add up to " + sum.toPlainString()
                        + " %, not 99.99–100.01 %.");
            }
        }
    }

    /** A breakdown is reported {@code count} times, each range type once. */
    private static void rangesOnce(RecordNode perf, String path, int count, String id, RegRuleSet.Findings out) {
        List<RecordNode> items = perf.all(path);
        if (items.isEmpty()) {
            return;
        }
        Set<String> types = new HashSet<>();
        for (RecordNode item : items) {
            if (!types.add(item.text("RgTp"))) {
                out.error("ESMA-MMF-" + id, item.lineOf("RgTp"), path.substring(path.lastIndexOf('/') + 1) + ": range type "
                        + item.text("RgTp") + " is reported twice.");
            }
        }
        if (items.size() != count) {
            out.error("ESMA-MMF-" + id, items.getFirst().line(), path.substring(path.lastIndexOf('/') + 1) + " must be reported "
                    + count + " times, once per range type (is " + items.size() + ").");
        }
    }

    /** Field 88: the input factor of the macro-economic scenarios. */
    private static void stressTests(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        for (RecordNode result : rec.all("Upd/RptData/QttvData/StrssTst/StrssTstRslt")) {
            String code = result.text("StrssTstScnroCd");
            if ((code.startsWith("MST-001") || code.startsWith("MST-002")) && !result.filled("InptFctr")) {
                out.error("ESMA-MMF-88", result.line(), "The input factor is missing for stress test scenario " + code + ".");
            }
        }
    }

    /** Fields 97 ff., 109/110, 119/172: counterparty names, accrued interest, reset dates. */
    private static void assets(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        RecordNode b = rec.first("Upd");
        if (b == null) {
            return;
        }
        LocalDate end = FieldRules.date(b.text("RptgPrd/FrDtToDt/ToDt"));
        for (RecordNode asset : b.all("RptData/QttvData/AsstInf/*")) {
            for (RecordNode party : asset.all("PtyData/*/PtyId")) {
                if (!party.filled("LEI") && !party.filled("Nm")) {
                    out.error("ESMA-MMF-97", party.line(), asset.name() + ": the name is required when no LEI is reported.");
                }
            }
            String method = asset.text("AsstValtn/ValtnTp");
            if (("MTMO".equals(method) || "AMCS".equals(method)) && asset.has("AsstValtn/AcrdIntrst")) {
                out.error("ESMA-MMF-109", asset.lineOf("AsstValtn/AcrdIntrst"), asset.name()
                        + ": accrued interest must not be reported for the valuation method " + method + ".");
            }
            LocalDate reset = FieldRules.date(asset.text("AsstValtn/RstDt"));
            if (reset != null && end != null && !reset.isAfter(end)) {
                out.error("ESMA-MMF-119", asset.lineOf("AsstValtn/RstDt"), asset.name() + ": the next reset date " + reset
                        + " must lie after the reporting end date " + end + ".");
            }
        }
    }

    /** Fields 58, 105, 113 …: an amount in base currency next to the EUR amount when the base currency is not EUR. */
    private static void baseCurrencyAmounts(RecordNode rec, RegRuleSet.Context ctx, RegRuleSet.Findings out) {
        String base = rec.text("Upd/FndData/FndAttrbts/BaseCcy");
        if (base.isEmpty() || "EUR".equals(base)) {
            return;
        }
        List<RecordNode> amounts = new ArrayList<>();
        collect(rec, amounts);
        for (RecordNode amount : amounts) {
            if (amount.has("RptgCcyAmt") && !amount.filled("BaseCcyAmt")) {
                out.error("ESMA-MMF-58", amount.line(), amount.name() + ": the amount in the base currency " + base
                        + " is missing next to the EUR amount.");
            }
        }
    }

    private static void collect(RecordNode node, List<RecordNode> amounts) {
        boolean hasRptg = false;
        for (RecordNode child : node.children()) {
            if (child.name().equals("RptgCcyAmt")) {
                hasRptg = true;
            } else {
                collect(child, amounts);
            }
        }
        if (hasRptg) {
            amounts.add(node);
        }
    }
}
