package org.fxt.freexmltoolkit.service.finance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FinancialIdentifiersTest {

    @Test
    void iban() {
        assertNull(FinancialIdentifiers.checkIban("DE89370400440532013000"));
        assertNull(FinancialIdentifiers.checkIban("DE89 3704 0044 0532 0130 00"), "spaces as printed");
        assertNull(FinancialIdentifiers.checkIban("AT611904300234573201"));
        assertNull(FinancialIdentifiers.checkIban("GB82WEST12345698765432"));
        assertTrue(FinancialIdentifiers.checkIban("DE88370400440532013000").contains("check digits"));
        assertTrue(FinancialIdentifiers.checkIban("AT6119043002345732011").contains("AT IBANs have 20"));
        assertNotNull(FinancialIdentifiers.checkIban("12345"));
    }

    @Test
    void bic() {
        assertNull(FinancialIdentifiers.checkBic("DEUTDEFF"));
        assertNull(FinancialIdentifiers.checkBic("DEUTDEFF500"));
        assertNotNull(FinancialIdentifiers.checkBic("DEUTDEF"));
        assertNotNull(FinancialIdentifiers.checkBic("DEUT12FF"));
    }

    @Test
    void lei() {
        assertNull(FinancialIdentifiers.checkLei("5493001KJTIIGC8Y1R12"));
        assertNull(FinancialIdentifiers.checkLei("529900T8BM49AURSDO55"));
        assertTrue(FinancialIdentifiers.checkLei("5493001KJTIIGC8Y1R13").contains("check digits"));
        assertNotNull(FinancialIdentifiers.checkLei("ABC"));
    }

    @Test
    void isin() {
        assertNull(FinancialIdentifiers.checkIsin("US0378331005"));
        assertNull(FinancialIdentifiers.checkIsin("DE0005140008"));
        assertNull(FinancialIdentifiers.checkIsin("AU0000XVGZA3"));
        assertTrue(FinancialIdentifiers.checkIsin("US0378331006").contains("check digit"));
        assertNotNull(FinancialIdentifiers.checkIsin("US037833100"));
    }
}
