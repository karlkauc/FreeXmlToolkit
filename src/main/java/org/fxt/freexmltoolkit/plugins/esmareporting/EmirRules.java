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

package org.fxt.freexmltoolkit.plugins.esmareporting;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.plugins.regreport.ActionMatrix;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * ESMA's validation rules of the EMIR Refit trade report (auth.030.001.03) that need only the file:
 * for every report the fields that are mandatory or not applicable for its action type and level
 * ({@link ActionMatrix}, from ESMA's validation rules), the record count of the header, the UTI
 * format, and ISO code / LEI / ISIN checks. Rule ids are {@code EMIR-<field number>}.
 */
public final class EmirRules {

    /** Action elements and the action types of ESMA's tables. */
    static final Map<String, String> ACTIONS = Map.of("New", "NEWT", "Mod", "MODI", "Crrctn", "CORR", "Termntn", "TERM",
            "PosCmpnt", "POSC", "ValtnUpd", "VALU", "Err", "EROR", "Rvv", "REVI");
    static final List<String> REPORT_PATHS = List.of("Document/DerivsTradRpt/TradData/Rpt",
            "BizData/Pyld/Document/DerivsTradRpt/TradData/Rpt");
    /** UTI (ISO 23897): the LEI of the generating entity followed by up to 32 characters. */
    static final Pattern UTI = Pattern.compile("[A-Z0-9]{18}[0-9]{2}[A-Z0-9]{0,32}");
    private static final String STATE_COUNT = "emir.count";

    private EmirRules() {
    }

    /** @return the rules of the EMIR trade report */
    public static RegRuleSet rules(FieldCatalog catalog, ActionMatrix matrix) {
        RegRuleSet rules = new RegRuleSet("Document/DerivsTradRpt/RptHdr", "BizData/Pyld/Document/DerivsTradRpt/RptHdr",
                REPORT_PATHS.get(0), REPORT_PATHS.get(1));
        RegRuleSet.Rule codes = CatalogChecks.of(catalog, "EMIR", List.of("Document/DerivsTradRpt/TradData"));
        rules.record((rec, ctx, out) -> {
            if (rec.name().equals("RptHdr")) {
                ctx.state().put("emir.header", rec);
                return;
            }
            RecordNode action = rec.children().isEmpty() ? null : rec.children().getFirst();
            if (action == null) {
                return;
            }
            ctx.state().merge(STATE_COUNT, 1, (a, b) -> (Integer) a + (Integer) b);
            String type = ACTIONS.get(action.name());
            if (type == null) {
                return;
            }
            String level = "PSTN".equals(action.text("Lvl")) ? "PSTN" : "TRAD";
            matrix.check(action, type, level, level.equals("PSTN") ? " at position level" : "", "EMIR", out);
            codes.check(rec, ctx, out);
            String uti = action.text("CmonTradData/TxData/TxId/UnqTxIdr");
            if (!uti.isEmpty()) {
                if (!UTI.matcher(uti).matches()) {
                    out.error("EMIR-2.1", action.lineOf("CmonTradData/TxData/TxId/UnqTxIdr"), "Field 2.1 · UTI \"" + uti
                            + "\" is no UTI (20 characters of an LEI, then up to 32 letters or digits).");
                } else if (FinancialIdentifiers.checkLei(uti.substring(0, 20)) != null) {
                    out.warning("EMIR-2.1", action.lineOf("CmonTradData/TxData/TxId/UnqTxIdr"),
                            "Field 2.1 · UTI should start with the LEI of the generating entity; " + uti.substring(0, 20)
                                    + " has wrong check digits.");
                }
            }
        });
        // the header's number of records matches the reports
        rules.finish((ctx, out) -> {
            if (ctx.state().get("emir.header") instanceof RecordNode header) {
                Object count = ctx.state().getOrDefault(STATE_COUNT, 0);
                String problem = countProblem(header, (Integer) count);
                if (problem != null) {
                    out.error("EMIR-HDR", header.lineOf("NbRcrds"), problem);
                }
            }
        });
        return rules;
    }

    /** @return why the header's record count does not match the reports, or {@code null} */
    static String countProblem(RecordNode header, int reports) {
        String declared = header.text("NbRcrds");
        try {
            int n = new java.math.BigDecimal(declared).intValueExact();
            return n == reports ? null : "The header declares " + n + " records, the file holds " + reports + ".";
        } catch (ArithmeticException | NumberFormatException e) {
            return "The header's number of records \"" + declared + "\" is no whole number.";
        }
    }
}
