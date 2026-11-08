package org.fxt.freexmltoolkit.plugins.iso20022;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.XmlScan;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Checks of ISO 20022 messages beyond their schema: IBAN, BIC and LEI check digits, currency
 * codes, the control counts and sums of payment initiations ({@code pain.001}, {@code pain.008})
 * and interbank transfers ({@code pacs.008}), and — for payments with service level {@code SEPA} —
 * the rules of the EPC implementation guidelines (EUR only, amount range, two decimals, the SEPA
 * character set in references, charge bearer {@code SLEV}).
 */
public final class Iso20022Validator implements DocumentValidator {

    /** The namespace prefix of every ISO 20022 message. */
    public static final String NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:";

    private static final Set<String> BIC_ELEMENTS = Set.of("BICFI", "BIC", "AnyBIC", "BICOrBEI");
    private static final Set<String> REFERENCE_ELEMENTS = Set.of("MsgId", "PmtInfId", "InstrId", "EndToEndId", "MndtId");
    /** The Latin character set every SEPA participant must support (EPC guidelines §1.4). */
    private static final Pattern SEPA_CHARACTERS = Pattern.compile("[a-zA-Z0-9/\\-?:().,'+ ]*");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final BigDecimal SEPA_MIN = new BigDecimal("0.01");
    private static final BigDecimal SEPA_MAX = new BigDecimal("999999999.99");

    @Override
    public String name() {
        return "ISO 20022";
    }

    @Override
    public boolean accepts(String content, String documentName) {
        return messageOf(content).isPresent();
    }

    /** @return the message id ({@code pain.001.001.09}) of an ISO 20022 document */
    public static Optional<String> messageOf(String content) {
        return XmlScan.rootOf(content).map(root -> root[1])
                .filter(ns -> ns.startsWith(NAMESPACE_PREFIX))
                .map(ns -> ns.substring(NAMESPACE_PREFIX.length()));
    }

    @Override
    public List<Problem> validate(String content, String documentName) {
        XmlScan scan;
        try {
            scan = XmlScan.of(content);
        } catch (XMLStreamException e) {
            return List.of(new Problem("error", 0, "The message is not well-formed: " + e.getMessage()));
        }
        List<Problem> problems = new ArrayList<>();
        checkIdentifiers(scan, problems);
        String message = messageOf(content).orElse("");
        if (message.startsWith("pain.001")) {
            checkControls(scan, "CdtTrfTxInf", "Amt/InstdAmt", true, problems);
        } else if (message.startsWith("pain.008")) {
            checkControls(scan, "DrctDbtTxInf", "InstdAmt", true, problems);
        } else if (message.startsWith("pacs.008")) {
            checkControls(scan, "CdtTrfTxInf", "IntrBkSttlmAmt", false, problems);
        }
        return problems;
    }

    private static void checkIdentifiers(XmlScan scan, List<Problem> problems) {
        for (XmlScan.Element e : scan.elements()) {
            String problem = null;
            if (e.localName().equals("IBAN")) {
                problem = FinancialIdentifiers.checkIban(e.text());
            } else if (BIC_ELEMENTS.contains(e.localName())) {
                problem = FinancialIdentifiers.checkBic(e.text());
            } else if (e.localName().equals("LEI")) {
                problem = FinancialIdentifiers.checkLei(e.text());
            }
            if (problem != null) {
                problems.add(new Problem("error", e.line(), problem));
            }
            String currency = e.attributes().get("Ccy");
            if (currency != null && !CURRENCY.matcher(currency).matches()) {
                problems.add(new Problem("error", e.line(), "\"" + currency + "\" is not an ISO 4217 currency code."));
            }
        }
    }

    /**
     * Compares {@code NbOfTxs} / {@code CtrlSum} of the group header and of every payment group
     * ({@code PmtInf}, initiations only) with their transactions, and applies the SEPA rules.
     */
    private static void checkControls(XmlScan scan, String transaction, String amountPath, boolean initiation,
                                      List<Problem> problems) {
        List<XmlScan.Element> all = scan.named(transaction);
        scan.named("GrpHdr").stream().findFirst().ifPresent(header ->
                compare(scan, header, all, amountPath, "the message", problems));
        if (initiation) {
            for (XmlScan.Element group : scan.named("PmtInf")) {
                List<XmlScan.Element> inside = scan.within(group).stream()
                        .filter(e -> e.localName().equals(transaction)).toList();
                compare(scan, group, inside, amountPath, "payment group " + text(scan, group, "PmtInfId"), problems);
                if (isSepa(scan, group)) {
                    sepaRules(scan, group, inside, amountPath, problems);
                }
            }
        }
    }

    private static void compare(XmlScan scan, XmlScan.Element holder, List<XmlScan.Element> transactions,
                                String amountPath, String what, List<Problem> problems) {
        XmlScan.Element count = child(scan, holder, "NbOfTxs");
        if (count != null && !count.text().equals(String.valueOf(transactions.size()))) {
            problems.add(new Problem("error", count.line(), "NbOfTxs of " + what + " is " + count.text() + ", but it has "
                    + transactions.size() + " transaction" + (transactions.size() == 1 ? "" : "s") + "."));
        }
        XmlScan.Element sum = child(scan, holder, "CtrlSum");
        if (sum != null) {
            BigDecimal total = BigDecimal.ZERO;
            for (XmlScan.Element tx : transactions) {
                XmlScan.Element amount = descendant(scan, tx, amountPath);
                if (amount == null) {
                    return; // an equivalent amount or another form: no sum to compare
                }
                try {
                    total = total.add(new BigDecimal(amount.text()));
                } catch (NumberFormatException e) {
                    return; // the schema reports it
                }
            }
            try {
                if (new BigDecimal(sum.text()).compareTo(total) != 0) {
                    problems.add(new Problem("error", sum.line(), "CtrlSum of " + what + " is " + sum.text()
                            + ", but its transactions add up to " + total.toPlainString() + "."));
                }
            } catch (NumberFormatException e) {
                // the schema reports it
            }
        }
    }

    /** @return whether the group (or any of its transactions) has service level SEPA */
    private static boolean isSepa(XmlScan scan, XmlScan.Element group) {
        return scan.within(group).stream().anyMatch(e -> e.path().endsWith("SvcLvl/Cd") && e.text().equals("SEPA"));
    }

    private static void sepaRules(XmlScan scan, XmlScan.Element group, List<XmlScan.Element> transactions,
                                  String amountPath, List<Problem> problems) {
        for (XmlScan.Element tx : transactions) {
            XmlScan.Element amount = descendant(scan, tx, amountPath);
            if (amount == null) {
                continue;
            }
            String currency = amount.attributes().get("Ccy");
            if (currency != null && !currency.equals("EUR")) {
                problems.add(new Problem("error", amount.line(), "SEPA: payments are in EUR only, not " + currency + "."));
            }
            try {
                BigDecimal value = new BigDecimal(amount.text());
                if (value.compareTo(SEPA_MIN) < 0 || value.compareTo(SEPA_MAX) > 0) {
                    problems.add(new Problem("error", amount.line(),
                            "SEPA: the amount must be between 0.01 and 999999999.99, not " + amount.text() + "."));
                }
                if (value.scale() > 2) {
                    problems.add(new Problem("error", amount.line(),
                            "SEPA: amounts have at most 2 decimals (" + amount.text() + ")."));
                }
            } catch (NumberFormatException e) {
                // the schema reports it
            }
        }
        List<XmlScan.Element> scope = new ArrayList<>();
        scope.add(group);
        scope.addAll(scan.within(group));
        scan.named("GrpHdr").stream().findFirst().ifPresent(h -> scope.addAll(scan.within(h)));
        for (XmlScan.Element e : scope) {
            if (REFERENCE_ELEMENTS.contains(e.localName())) {
                String reference = e.text();
                if (!SEPA_CHARACTERS.matcher(reference).matches()) {
                    problems.add(new Problem("error", e.line(), "SEPA: " + e.localName() + " \"" + reference
                            + "\" uses characters outside the SEPA character set (a-z A-Z 0-9 / - ? : ( ) . , ' + space)."));
                } else if (reference.startsWith("/") || reference.endsWith("/") || reference.contains("//")) {
                    problems.add(new Problem("error", e.line(), "SEPA: " + e.localName() + " \"" + reference
                            + "\" must not start or end with / or contain //."));
                }
            } else if (e.localName().equals("ChrgBr") && !e.text().equals("SLEV")) {
                problems.add(new Problem("error", e.line(), "SEPA: the charge bearer is SLEV, not " + e.text() + "."));
            }
        }
    }

    private static String text(XmlScan scan, XmlScan.Element holder, String name) {
        XmlScan.Element child = child(scan, holder, name);
        return child == null ? "" : child.text();
    }

    private static XmlScan.Element child(XmlScan scan, XmlScan.Element holder, String name) {
        String path = holder.path() + "/" + name;
        return scan.within(holder).stream().filter(e -> e.path().equals(path)).findFirst().orElse(null);
    }

    private static XmlScan.Element descendant(XmlScan scan, XmlScan.Element holder, String relativePath) {
        String path = holder.path() + "/" + relativePath;
        return scan.within(holder).stream().filter(e -> e.path().equals(path)).findFirst().orElse(null);
    }
}
