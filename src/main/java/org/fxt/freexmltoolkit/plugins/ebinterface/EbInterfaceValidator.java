package org.fxt.freexmltoolkit.plugins.ebinterface;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.XmlScan;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Checks of ebInterface invoices beyond their schema, after the rules of the ebInterface 6.1
 * specification: Austrian VAT ids (UID, {@code ATU00000000} stands for "none"), IBAN and BIC,
 * line amounts (quantity × unit price), tax amounts (taxable amount × rate), the tax summary
 * against the lines, the gross total against the tax summary, the payable amount
 * ({@code TotalGrossAmount − PrepaidAmount + RoundingAmount}) and the due date. Arithmetic checks
 * allow one cent of rounding and are skipped where surcharges, reductions or other taxes make the
 * formula ambiguous.
 */
public final class EbInterfaceValidator implements DocumentValidator {

    /** The namespace prefix of every ebInterface version. */
    public static final String NAMESPACE_PREFIX = "http://www.ebinterface.at/schema/";
    private static final BigDecimal CENT = new BigDecimal("0.01");

    @Override
    public String name() {
        return "ebInterface";
    }

    @Override
    public boolean accepts(String content, String documentName) {
        return versionOf(content).isPresent();
    }

    /** @return the version ({@code 6p1}) of an ebInterface invoice */
    public static Optional<String> versionOf(String content) {
        return XmlScan.rootOf(content)
                .filter(root -> root[0].equals("Invoice") && root[1].startsWith(NAMESPACE_PREFIX))
                .map(root -> root[1].substring(NAMESPACE_PREFIX.length()).replace("/", ""));
    }

    @Override
    public List<Problem> validate(String content, String documentName) {
        XmlScan scan;
        try {
            scan = XmlScan.of(content);
        } catch (XMLStreamException e) {
            return List.of(new Problem("error", 0, "The invoice is not well-formed: " + e.getMessage()));
        }
        List<Problem> problems = new ArrayList<>();
        identifiers(scan, problems);
        lines(scan, problems);
        taxes(scan, problems);
        totals(scan, problems);
        dueDate(scan, problems);
        return problems;
    }

    private static void identifiers(XmlScan scan, List<Problem> problems) {
        for (XmlScan.Element e : scan.elements()) {
            String problem = switch (e.localName()) {
                case "VATIdentificationNumber" -> e.text().toUpperCase(java.util.Locale.ROOT).startsWith("ATU")
                        && !e.text().equals("ATU00000000") ? FinancialIdentifiers.checkAustrianUid(e.text()) : null;
                case "IBAN" -> FinancialIdentifiers.checkIban(e.text());
                case "BIC" -> FinancialIdentifiers.checkBic(e.text());
                default -> null;
            };
            if (problem != null) {
                problems.add(new Problem("error", e.line(), problem));
            }
        }
    }

    /** {@code LineItemAmount = Quantity × UnitPrice / BaseQuantity} for lines without surcharges or reductions. */
    private static void lines(XmlScan scan, List<Problem> problems) {
        for (XmlScan.Element line : scan.named("ListLineItem")) {
            List<XmlScan.Element> inside = scan.within(line);
            if (inside.stream().anyMatch(e -> e.localName().contains("Reduction") || e.localName().contains("Surcharge")
                    || e.localName().contains("OtherVATableTax"))) {
                continue;
            }
            XmlScan.Element quantity = child(line, inside, "Quantity");
            XmlScan.Element price = child(line, inside, "UnitPrice");
            XmlScan.Element amount = child(line, inside, "LineItemAmount");
            BigDecimal q = number(quantity);
            BigDecimal p = number(price);
            BigDecimal a = number(amount);
            if (q == null || p == null || a == null) {
                continue;
            }
            BigDecimal base = price.attributes().containsKey("BaseQuantity")
                    ? number(price.attributes().get("BaseQuantity")) : BigDecimal.ONE;
            if (base == null || base.signum() == 0) {
                continue;
            }
            BigDecimal expected = q.multiply(p).divide(base, 2, RoundingMode.HALF_UP);
            if (differs(expected, a)) {
                problems.add(new Problem("warning", amount.line(), "LineItemAmount " + amount.text() + " is not quantity × unit price ("
                        + quantity.text() + " × " + price.text() + (base.compareTo(BigDecimal.ONE) == 0 ? "" : " / " + base.toPlainString())
                        + " = " + expected.toPlainString() + ")."));
            }
        }
    }

    /** {@code TaxAmount = TaxableAmount × TaxPercent / 100} for every tax item. */
    private static void taxes(XmlScan scan, List<Problem> problems) {
        for (XmlScan.Element item : scan.elements()) {
            if (!item.localName().equals("TaxItem") && !item.localName().equals("OtherVATableTax")) {
                continue;
            }
            List<XmlScan.Element> inside = scan.within(item);
            XmlScan.Element taxable = child(item, inside, "TaxableAmount");
            XmlScan.Element percent = child(item, inside, "TaxPercent");
            XmlScan.Element tax = child(item, inside, "TaxAmount");
            BigDecimal base = number(taxable);
            BigDecimal rate = number(percent);
            BigDecimal amount = number(tax);
            if (base == null || rate == null || amount == null) {
                continue;
            }
            BigDecimal expected = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (differs(expected, amount)) {
                problems.add(new Problem("warning", tax.line(), "TaxAmount " + tax.text() + " is not " + percent.text()
                        + " % of " + taxable.text() + " (" + expected.toPlainString() + ")."));
            }
        }
    }

    private static void totals(XmlScan scan, List<Problem> problems) {
        List<XmlScan.Element> summary = scan.elements().stream().filter(e -> e.path().endsWith("Invoice/Tax/TaxItem")).toList();
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (XmlScan.Element item : summary) {
            List<XmlScan.Element> inside = scan.within(item);
            BigDecimal t = number(child(item, inside, "TaxableAmount"));
            BigDecimal a = number(child(item, inside, "TaxAmount"));
            taxable = taxable.add(t == null ? BigDecimal.ZERO : t);
            tax = tax.add(a == null ? BigDecimal.ZERO : a);
        }
        boolean rootAdjustments = !scan.named("ReductionAndSurchargeDetails").isEmpty();
        XmlScan.Element firstSummary = summary.isEmpty() ? null : summary.getFirst();
        if (!summary.isEmpty() && !rootAdjustments) {
            BigDecimal lines = BigDecimal.ZERO;
            for (XmlScan.Element e : scan.named("LineItemAmount")) {
                if (e.parentPath().endsWith("ListLineItem")) {
                    BigDecimal v = number(e);
                    lines = lines.add(v == null ? BigDecimal.ZERO : v);
                }
            }
            if (!scan.named("ListLineItem").isEmpty() && differs(lines, taxable)) {
                problems.add(new Problem("warning", firstSummary.line(), "The tax summary's taxable amounts add up to "
                        + taxable.toPlainString() + ", the lines to " + lines.toPlainString() + "."));
            }
        }
        XmlScan.Element gross = root(scan, "TotalGrossAmount");
        if (gross != null && !summary.isEmpty() && !rootAdjustments && scan.named("OtherTax").isEmpty()) {
            BigDecimal expected = taxable.add(tax);
            if (differs(expected, number(gross))) {
                problems.add(new Problem("warning", gross.line(), "TotalGrossAmount " + gross.text()
                        + " is not taxable amounts plus taxes of the tax summary (" + expected.toPlainString() + ")."));
            }
        }
        XmlScan.Element payable = root(scan, "PayableAmount");
        if (gross != null && payable != null && scan.named("BelowTheLineItem").isEmpty()) {
            BigDecimal prepaid = orZero(number(root(scan, "PrepaidAmount")));
            BigDecimal rounding = orZero(number(root(scan, "RoundingAmount")));
            BigDecimal g = number(gross);
            BigDecimal p = number(payable);
            if (g != null && p != null) {
                BigDecimal expected = g.subtract(prepaid).add(rounding);
                if (expected.compareTo(p) != 0) {
                    problems.add(new Problem("error", payable.line(), "PayableAmount " + payable.text()
                            + " is not TotalGrossAmount − PrepaidAmount + RoundingAmount (" + expected.toPlainString() + ")."));
                }
            }
        }
    }

    private static void dueDate(XmlScan scan, List<Problem> problems) {
        XmlScan.Element invoiceDate = root(scan, "InvoiceDate");
        XmlScan.Element due = scan.elements().stream().filter(e -> e.path().endsWith("PaymentConditions/DueDate"))
                .findFirst().orElse(null);
        if (invoiceDate != null && due != null && due.text().compareTo(invoiceDate.text()) < 0
                && due.text().matches("\\d{4}-\\d{2}-\\d{2}") && invoiceDate.text().matches("\\d{4}-\\d{2}-\\d{2}")) {
            problems.add(new Problem("warning", due.line(), "The due date " + due.text() + " is before the invoice date "
                    + invoiceDate.text() + "."));
        }
    }

    private static XmlScan.Element root(XmlScan scan, String name) {
        return scan.elements().stream().filter(e -> e.depth() == 1 && e.localName().equals(name)).findFirst().orElse(null);
    }

    private static XmlScan.Element child(XmlScan.Element parent, List<XmlScan.Element> inside, String name) {
        return inside.stream().filter(e -> e.depth() == parent.depth() + 1 && e.localName().equals(name)).findFirst().orElse(null);
    }

    private static BigDecimal number(XmlScan.Element e) {
        return e == null ? null : number(e.text());
    }

    private static BigDecimal number(String text) {
        try {
            return text == null || text.isBlank() ? null : new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean differs(BigDecimal expected, BigDecimal actual) {
        return actual == null || expected.subtract(actual).abs().compareTo(CENT) > 0;
    }
}
