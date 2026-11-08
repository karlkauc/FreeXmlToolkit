package org.fxt.freexmltoolkit.plugins;

import java.util.function.UnaryOperator;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/** The identifiers the industry panels can check. */
public enum IdentifierKind {
    IBAN("IBAN", FinancialIdentifiers::checkIban),
    BIC("BIC", FinancialIdentifiers::checkBic),
    LEI("LEI", FinancialIdentifiers::checkLei),
    ISIN("ISIN", FinancialIdentifiers::checkIsin),
    AT_UID("Austrian VAT id (UID)", FinancialIdentifiers::checkAustrianUid);

    private final String label;
    private final UnaryOperator<String> check;

    IdentifierKind(String label, UnaryOperator<String> check) {
        this.label = label;
        this.check = check;
    }

    public String label() {
        return label;
    }

    /** @return why the value is wrong, or {@code null} */
    public String check(String value) {
        return check.apply(value);
    }
}
