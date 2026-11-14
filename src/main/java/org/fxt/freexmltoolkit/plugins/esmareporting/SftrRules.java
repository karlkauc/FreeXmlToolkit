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

import org.fxt.freexmltoolkit.plugins.regreport.ActionMatrix;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;

/**
 * ESMA's validation rules of the SFTR securities financing transaction report (auth.052.001.02) that
 * need only the file: for every report the fields that are mandatory or not applicable for its
 * action type, level and SFT type ({@link ActionMatrix}, from ESMA's SFTR validation rules), the
 * UTI format and ISO code / LEI / ISIN checks. Rule ids are {@code SFTR-<table>.<field>}
 * ({@code SFTR-LEI} / {@code SFTR-ISIN} for identifiers).
 */
public final class SftrRules {

    /** Action elements and the action types of ESMA's tables. */
    static final Map<String, String> ACTIONS = Map.of("New", "NEWT", "Mod", "MODI", "Err", "EROR", "EarlyTermntn", "ETRM",
            "PosCmpnt", "POSC", "CollUpd", "COLU", "Crrctn", "CORR", "ValtnUpd", "VALU");
    /** The loan data elements of the SFT types and the names of ESMA's table. */
    static final Map<String, String> SFT_TYPES = Map.of("RpTrad", "Repo", "BuySellBck", "BSB", "SctiesLndg", "SL", "MrgnLndg", "ML");
    private static final Map<String, String> SFT_NAMES = Map.of("Repo", "a repo", "BSB", "a buy-sell back", "SL",
            "securities lending", "ML", "margin lending");

    private SftrRules() {
    }

    /** @return the SFT type of an action element ({@code Repo} …), or {@code null} when it names none */
    static String sftType(RecordNode action) {
        RecordNode loan = action.first("LnData");
        if (loan != null) {
            for (RecordNode child : loan.children()) {
                String type = SFT_TYPES.get(child.name());
                if (type != null) {
                    return type;
                }
            }
        }
        return null;
    }

    /** @return the rules of the SFTR report */
    public static RegRuleSet rules(FieldCatalog catalog, ActionMatrix matrix) {
        RegRuleSet rules = new RegRuleSet("Document/SctiesFincgRptgTxRpt/TradData/Rpt",
                "BizData/Pyld/Document/SctiesFincgRptgTxRpt/TradData/Rpt");
        RegRuleSet.Rule codes = CatalogChecks.of(catalog, "SFTR", List.of("Document/SctiesFincgRptgTxRpt/TradData"));
        RegRuleSet.Rule identifiers = CatalogChecks.identifiersByName("SFTR");
        rules.record((rec, ctx, out) -> {
            RecordNode action = rec.children().isEmpty() ? null : rec.children().getFirst();
            String type = action == null ? null : ACTIONS.get(action.name());
            if (type == null) {
                return;
            }
            String level = "PSTN".equals(action.text("LvlTp")) ? "PSTN" : "TCTN";
            String sft = sftType(action);
            if (sft != null) {
                matrix.check(action, type, level + "." + sft, " for " + SFT_NAMES.get(sft)
                        + (level.equals("PSTN") ? " at position level" : ""), "SFTR", out);
            } else {
                // reports without loan data of an SFT type (errors, collateral updates): the obligations do
                // not depend on the type there; ESMA's securities lending column covers all of them
                matrix.check(action, type, level + ".SL", level.equals("PSTN") ? " at position level" : "", "SFTR", out);
            }
            codes.check(rec, ctx, out);
            identifiers.check(rec, ctx, out);
            for (RecordNode uti : action.all("LnData/UnqTradIdr")) {
                if (!uti.text().isEmpty() && !EmirRules.UTI.matcher(uti.text()).matches()) {
                    out.error("SFTR-2.1", uti.line(), "Field 2.1 · UTI \"" + uti.text()
                            + "\" is no UTI (20 characters of an LEI, then up to 32 letters or digits).");
                }
            }
        });
        return rules;
    }
}
