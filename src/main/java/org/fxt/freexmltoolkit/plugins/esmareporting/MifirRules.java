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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.FieldRules;
import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * ESMA's validation rules of the MiFIR transaction report (auth.016.001.01, esma65-8-2594) that need
 * only the file - no instrument reference data, GLEIF or MIC register: venue and trading capacity
 * rules, LEI / ISIN / country / currency codes, national identifiers and CONCAT codes, dates of birth,
 * the instrument fields that the CFI code (field 43) makes mandatory or forbids, maturity and expiry
 * dates. Rule ids are ESMA's error codes ({@code MIFIR-CON-450}).
 */
public final class MifirRules {

    static final List<String> RECORD_PATHS = List.of("Document/FinInstrmRptgTxRpt/Tx", "BizData/Pyld/Document/FinInstrmRptgTxRpt/Tx");
    private static final Set<String> OFF_VENUE = Set.of("XXXX", "XOFF");
    /** ISO 4217 codes ESMA does not accept as currency of a transaction. */
    static final Set<String> SPECIAL_CURRENCIES = Set.of("XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XDR", "XEU", "XFU",
            "XPD", "XPT", "XXX");
    /** CONCAT: country, date of birth, first five letters of first name and surname ({@code #} padding). */
    static final Pattern CONCAT = Pattern.compile("[A-Z]{2}\\d{8}[A-Z#]{5}[A-Z#]{5}");

    /** A field the CFI code makes mandatory or forbids. */
    record CfiField(String code, String forbiddenCode, String field, String name, String path, List<String> mandatory,
                    List<String> forbidden) {
    }

    private static final List<String> NOT_OPTIONS = List.of("F*****", "S*****", "E*****", "C*****", "D*****", "J*****");

    static final List<CfiField> CFI_FIELDS = List.of(
            new CfiField("CON-441", "CON-441", "44", "Notional currency 1", "FinInstrm/Othr/FinInstrmGnlAttrbts/NtnlCcy",
                    List.of("O*****", "S*****", "C*****", "R*****", "F*****", "E*****", "D*****", "H*****", "J*****"), List.of()),
            new CfiField("CON-452", "CON-453", "45", "Notional currency 2", "FinInstrm/Othr/DerivInstrmAttrbts/AsstClssSpcfcAttrbts/*/OthrNtnlCcy",
                    List.of("SF****", "FFC***", "JF****", "RWC***", "RFC***", "DE***C", "DS***C"),
                    List.of("O**S**", "O**D**", "O**T**", "O**N**", "FFS***", "FFD***", "FFN***", "FFV***", "FC****", "ST****",
                            "HT****", "HE****", "HF****", "RA****", "RS****", "RP****", "RD****", "RM****", "RX****", "RWB***",
                            "RWS***", "RWD***", "RWT***", "RWI***", "RWM***", "RWX***", "RFB***", "RFS***", "RFD***", "RFT***",
                            "RFI***", "RFM***", "RFX***", "E*****", "C*****", "DB****", "DC****", "DW****", "DT****", "DG****",
                            "DA****", "DN****", "DD****", "DM****", "DY****", "DX****", "DE***B", "DE***S", "DE***D", "DE***T",
                            "DE***I", "DE***N", "DE***M", "DE***X", "DS***B", "DS***S", "DS***D", "DS***T", "DS***I", "DS***N",
                            "DS***M", "DS***X", "JE****", "JC****", "JR****", "JT****", "JX****")),
            new CfiField("CON-480", "CON-481", "48", "Underlying index name", "FinInstrm/Othr/DerivInstrmAttrbts/UndrlygInstrm/*/*/Indx/Nm",
                    List.of("O**I**", "O**N**", "FFI***", "FFN***", "RWI***", "RFI***"),
                    List.of("DB****", "DT****", "DY****", "ES****", "EP****", "EC****", "EF****", "EL****", "ED****", "EM****",
                            "EX****", "C*****")),
            new CfiField("CON-490", "CON-490", "49", "Term of the underlying index",
                    "FinInstrm/Othr/DerivInstrmAttrbts/UndrlygInstrm/*/*/Indx/Nm/Term", List.of("O**N**", "FFN***"), List.of()),
            new CfiField("CON-500", "CON-501", "50", "Option type", "FinInstrm/Othr/DerivInstrmAttrbts/OptnTp",
                    List.of("O*****", "H*****", "RW****"), NOT_OPTIONS),
            new CfiField("CON-510", "CON-511", "51", "Strike price", "FinInstrm/Othr/DerivInstrmAttrbts/StrkPric",
                    List.of("O*****", "H*****", "RW****"), NOT_OPTIONS),
            new CfiField("CON-530", "CON-531", "53", "Option exercise style", "FinInstrm/Othr/DerivInstrmAttrbts/OptnExrcStyle",
                    List.of("O*****", "H*****", "RW****", "RF****"), NOT_OPTIONS),
            new CfiField("CON-540", "CON-542", "54", "Maturity date", "FinInstrm/Othr/DebtInstrmAttrbts/MtrtyDt",
                    List.of("D*****"), List.of("R*****", "O*****", "F*****", "S*****", "E*****", "C*****", "H*****", "J*****")),
            new CfiField("CON-550", "CON-551", "55", "Expiry date", "FinInstrm/Othr/DerivInstrmAttrbts/XpryDt",
                    List.of("O*****", "F*****", "JC**F*"), List.of("E*****", "C*****", "D*****")));

    private MifirRules() {
    }

    /** @return whether a CFI code matches a pattern of ESMA's tables ({@code *} = any letter) */
    static boolean cfiMatches(String cfi, String pattern) {
        if (cfi.length() != 6 || pattern.length() != 6) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (pattern.charAt(i) != '*' && pattern.charAt(i) != cfi.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static boolean anyMatch(String cfi, List<String> patterns) {
        return patterns.stream().anyMatch(p -> cfiMatches(cfi, p));
    }

    /** @return the option type the CFI code implies (CON-502), or {@code null} */
    static String optionTypeOf(String cfi) {
        if (anyMatch(cfi, List.of("OC****", "H**A**", "H**B**", "RW**C*", "H**C**"))) {
            return "CALL";
        }
        if (anyMatch(cfi, List.of("OP****", "H**D**", "H**E**", "RW**P*", "H**F**"))) {
            return "PUTO";
        }
        if (anyMatch(cfi, List.of("OM****", "H**G**", "H**H**", "RW**B*", "H**I**"))) {
            return "OTHR";
        }
        return null;
    }

    /** @return the rules of the MiFIR transaction report */
    public static RegRuleSet rules(FieldCatalog catalog) {
        RegRuleSet rules = new RegRuleSet(RECORD_PATHS.get(0), RECORD_PATHS.get(1));
        rules.record((rec, ctx, out) -> {
            RecordNode tx = rec.first("New");
            if (tx == null) {
                RecordNode cancellation = rec.first("Cxl");
                if (cancellation != null) {
                    identifiers(cancellation, out);
                }
                return;
            }
            venueAndCapacity(tx, out);
            identifiers(tx, out);
            people(tx, out);
            currencies(tx, out);
            instrument(tx, out);
        });
        return rules;
    }

    private static void venueAndCapacity(RecordNode tx, RegRuleSet.Findings out) {
        String venue = tx.text("Tx/TradVn");
        boolean offVenue = OFF_VENUE.contains(venue);
        if (offVenue && tx.filled("Tx/TradPlcMtchgId")) {
            out.error("MIFIR-CON-030", tx.lineOf("Tx/TradPlcMtchgId"), "Field 3 · Trading venue transaction identification code must not be "
                    + "reported for venue " + venue + ".");
        }
        if (offVenue && tx.filled("Tx/CtryOfBrnch")) {
            out.error("MIFIR-CON-372", tx.lineOf("Tx/CtryOfBrnch"), "Field 37 · Country of the branch membership must not be reported "
                    + "for venue " + venue + ".");
        }
        if (offVenue && tx.filled("AddtlAttrbts/WvrInd")) {
            out.error("MIFIR-CON-610", tx.lineOf("AddtlAttrbts/WvrInd"), "Field 61 · Waiver indicator must not be reported for venue "
                    + venue + ".");
        }
        String transmission = tx.text("OrdrTrnsmssn/TrnsmssnInd");
        if ("true".equals(transmission) && !venue.isEmpty() && !offVenue) {
            out.error("MIFIR-CON-362", tx.lineOf("Tx/TradVn"), "Field 36 · Venue must be XOFF or XXXX when the transmission of order "
                    + "indicator is true (is " + venue + ").");
        }
        String capacity = tx.text("Tx/TradgCpcty");
        if (("DEAL".equals(capacity) || "MTCH".equals(capacity)) && "true".equals(transmission)) {
            out.error("MIFIR-CON-251", tx.lineOf("OrdrTrnsmssn/TrnsmssnInd"), "Field 25 · Transmission of order indicator must be false "
                    + "when the trading capacity is " + capacity + ".");
        }
        if ("DEAL".equals(capacity)) {
            String executing = tx.text("ExctgPty");
            boolean buyer = tx.all("Buyr/AcctOwnr/Id/LEI").stream().anyMatch(n -> n.text().equals(executing));
            boolean seller = tx.all("Sellr/AcctOwnr/Id/LEI").stream().anyMatch(n -> n.text().equals(executing));
            if (!executing.isEmpty() && !buyer && !seller) {
                out.error("MIFIR-CON-290", tx.lineOf("Tx/TradgCpcty"), "With trading capacity DEAL the buyer or the seller must be the "
                        + "executing entity " + executing + ".");
            }
            if (!tx.filled("OrdrTrnsmssn/TrnsmttgBuyr") && !tx.filled("OrdrTrnsmssn/TrnsmttgSellr") && !tx.filled("InvstmtDcsnPrsn")) {
                out.error("MIFIR-CON-570", tx.line(), "Field 57 · Investment decision within firm is required when dealing on own account "
                        + "(DEAL) without a transmitting firm.");
            }
        }
    }

    private static void identifiers(RecordNode tx, RegRuleSet.Findings out) {
        String[][] leis = {{"ExctgPty", "CON-040", "Field 4 · Executing entity"}, {"SubmitgPty", "CON-060", "Field 6 · Submitting entity"},
                {"OrdrTrnsmssn/TrnsmttgBuyr", "CON-260", "Field 26 · Transmitting firm for the buyer"},
                {"OrdrTrnsmssn/TrnsmttgSellr", "CON-270", "Field 27 · Transmitting firm for the seller"},
                {"Buyr/AcctOwnr/Id/LEI", "CON-070", "Field 7 · Buyer"}, {"Buyr/DcsnMakr/LEI", "CON-120", "Field 12 · Buyer decision maker"},
                {"Sellr/AcctOwnr/Id/LEI", "CON-160", "Field 16 · Seller"}, {"Sellr/DcsnMakr/LEI", "CON-210", "Field 21 · Seller decision maker"}};
        for (String[] lei : leis) {
            for (RecordNode node : tx.all(lei[0])) {
                String problem = FinancialIdentifiers.checkLei(node.text());
                if (!node.text().isEmpty() && problem != null) {
                    out.error("MIFIR-" + lei[1], node.line(), lei[2] + ": " + problem);
                }
            }
        }
        for (String[] isin : new String[][] {{"FinInstrm/Id", "CON-410"}, {"FinInstrm/Othr/FinInstrmGnlAttrbts/Id", "CON-410"}}) {
            for (RecordNode node : tx.all(isin[0])) {
                String problem = FinancialIdentifiers.checkIsin(node.text());
                if (!node.text().isEmpty() && problem != null) {
                    out.error("MIFIR-" + isin[1], node.line(), "Field 41 · Instrument identification code: " + problem);
                }
            }
        }
        List<RecordNode> underlying = new ArrayList<>();
        collect(tx.first("FinInstrm/Othr/DerivInstrmAttrbts/UndrlygInstrm"), "ISIN", underlying);
        for (RecordNode node : underlying) {
            String problem = FinancialIdentifiers.checkIsin(node.text());
            if (problem != null) {
                out.error("MIFIR-CON-470", node.line(), "Field 47 · Underlying instrument code: " + problem);
            }
        }
        List<RecordNode> countries = new ArrayList<>();
        collect(tx, "CtryOfBrnch", countries);
        for (RecordNode node : countries) {
            String problem = CatalogChecks.problem("country", node.text());
            if (problem != null) {
                out.error("MIFIR-CON-080", node.line(), node.parent().name() + " · country of the branch: " + problem);
            }
        }
    }

    private static void collect(RecordNode node, String name, List<RecordNode> into) {
        if (node == null) {
            return;
        }
        for (RecordNode child : node.children()) {
            if (child.name().equals(name) && !child.text().isEmpty()) {
                into.add(child);
            }
            collect(child, name, into);
        }
    }

    /** National identifiers, CONCAT codes and dates of birth of the persons. */
    private static void people(RecordNode tx, RegRuleSet.Findings out) {
        LocalDate trade = FieldRules.date(tx.text("Tx/TradDt"));
        List<RecordNode> persons = new ArrayList<>();
        collectContainers(tx, "Prsn", persons);
        for (RecordNode person : persons) {
            LocalDate birth = FieldRules.date(person.text("BirthDt"));
            if (birth != null && trade != null && birth.isAfter(trade)) {
                out.error("MIFIR-CON-110", person.lineOf("BirthDt"), "The date of birth " + birth + " lies after the trading date " + trade + ".");
            }
            for (RecordNode other : person.all("Othr")) {
                String id = other.text("Id");
                if (id.isEmpty()) {
                    continue;
                }
                if ("CONCAT".equals(other.text("SchmeNm/Prtry"))) {
                    if (!CONCAT.matcher(id).matches()) {
                        out.error("MIFIR-CON-074", other.lineOf("Id"), "CONCAT code \"" + id + "\" must be 20 characters: country, date of "
                                + "birth (YYYYMMDD), five letters of the first name and five of the surname (# fills).");
                    } else if (birth != null && !id.substring(2, 10).equals(birth.toString().replace("-", ""))) {
                        out.error("MIFIR-CON-073", other.lineOf("Id"), "The date of birth in the CONCAT code (" + id.substring(2, 10)
                                + ") differs from the date of birth " + birth + ".");
                    }
                }
                String country = id.length() >= 2 ? id.substring(0, 2) : id;
                String problem = CatalogChecks.problem("country", country);
                if (problem != null) {
                    out.error("MIFIR-CON-071", other.lineOf("Id"), "The national identifier must start with an ISO 3166 country code: " + problem);
                }
            }
        }
    }

    private static void collectContainers(RecordNode node, String name, List<RecordNode> into) {
        for (RecordNode child : node.children()) {
            if (child.name().equals(name) && !child.children().isEmpty()) {
                into.add(child);
            }
            collectContainers(child, name, into);
        }
    }

    /** ISO 4217 currencies (elements and {@code Ccy} attributes) without ESMA's special codes. */
    private static void currencies(RecordNode tx, RegRuleSet.Findings out) {
        List<RecordNode> nodes = new ArrayList<>();
        all(tx, nodes);
        for (RecordNode node : nodes) {
            String code = node.attributes().get("Ccy");
            if (code == null && (node.name().endsWith("Ccy")) && node.children().isEmpty()) {
                code = node.text();
            }
            if (code == null || code.isEmpty()) {
                continue;
            }
            String problem = SPECIAL_CURRENCIES.contains(code) ? "\"" + code + "\" is not accepted as currency."
                    : CatalogChecks.problem("currency", code);
            if (problem != null) {
                out.error("MIFIR-CON-310", node.line(), node.name() + ": " + problem);
            }
        }
        if (tx.filled("FinInstrm/Othr/DerivInstrmAttrbts/AsstClssSpcfcAttrbts/*/OthrNtnlCcy")
                && !tx.filled("FinInstrm/Othr/FinInstrmGnlAttrbts/NtnlCcy")) {
            out.error("MIFIR-CON-450", tx.lineOf("FinInstrm/Othr/DerivInstrmAttrbts/AsstClssSpcfcAttrbts"),
                    "Field 45 · Notional currency 2 may only be reported with field 44 · Notional currency 1.");
        }
    }

    private static void all(RecordNode node, List<RecordNode> into) {
        into.add(node);
        for (RecordNode child : node.children()) {
            all(child, into);
        }
    }

    /** The instrument fields the CFI code (field 43) makes mandatory or forbids; maturity and expiry dates. */
    private static void instrument(RecordNode tx, RegRuleSet.Findings out) {
        String cfi = tx.text("FinInstrm/Othr/FinInstrmGnlAttrbts/ClssfctnTp");
        LocalDate trade = FieldRules.date(tx.text("Tx/TradDt"));
        if (cfi.length() == 6) {
            for (CfiField field : CFI_FIELDS) {
                boolean present = tx.filled(field.path());
                if (!present && anyMatch(cfi, field.mandatory())) {
                    out.error("MIFIR-" + field.code(), tx.lineOf("FinInstrm/Othr"), "Field " + field.field() + " · " + field.name()
                            + " is mandatory for CFI " + cfi + ".");
                } else if (present && anyMatch(cfi, field.forbidden())) {
                    out.error("MIFIR-" + field.forbiddenCode(), tx.lineOf(field.path()), "Field " + field.field() + " · " + field.name()
                            + " must not be reported for CFI " + cfi + ".");
                }
            }
            String expected = optionTypeOf(cfi);
            String optionType = tx.text("FinInstrm/Othr/DerivInstrmAttrbts/OptnTp");
            if (expected != null && !optionType.isEmpty() && !expected.equals(optionType)) {
                out.error("MIFIR-CON-502", tx.lineOf("FinInstrm/Othr/DerivInstrmAttrbts/OptnTp"), "Field 50 · Option type must be "
                        + expected + " for CFI " + cfi + " (is " + optionType + ").");
            }
        }
        for (String[] date : new String[][] {{"FinInstrm/Othr/DebtInstrmAttrbts/MtrtyDt", "CON-541", "54 · Maturity date"},
                {"FinInstrm/Othr/DerivInstrmAttrbts/XpryDt", "CON-552", "55 · Expiry date"}}) {
            LocalDate value = FieldRules.date(tx.text(date[0]));
            if (value != null && trade != null && value.isBefore(trade)) {
                out.error("MIFIR-" + date[1], tx.lineOf(date[0]), "Field " + date[2] + " " + value + " lies before the trading date " + trade + ".");
            }
        }
    }
}
