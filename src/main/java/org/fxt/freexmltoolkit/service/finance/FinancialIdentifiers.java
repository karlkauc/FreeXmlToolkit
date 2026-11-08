package org.fxt.freexmltoolkit.service.finance;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Format and check-digit tests of financial identifiers: IBAN (ISO 13616, MOD 97-10), BIC
 * (ISO 9362), LEI (ISO 17442, MOD 97-10) and ISIN (ISO 6166, Luhn). Each test returns why a value
 * is wrong, or {@code null} when it is valid. Used by the industry plugins' validators.
 */
public final class FinancialIdentifiers {

    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final Pattern BIC = Pattern.compile("[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");
    private static final Pattern LEI = Pattern.compile("[A-Z0-9]{18}[0-9]{2}");
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    /** IBAN lengths of the SEPA countries (ISO 13616 registry). */
    private static final Map<String, Integer> IBAN_LENGTHS = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AT", 20), Map.entry("BE", 16), Map.entry("BG", 22),
            Map.entry("CH", 21), Map.entry("CY", 28), Map.entry("CZ", 24), Map.entry("DE", 22),
            Map.entry("DK", 18), Map.entry("EE", 20), Map.entry("ES", 24), Map.entry("FI", 18),
            Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GI", 23), Map.entry("GR", 27),
            Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22), Map.entry("IS", 26),
            Map.entry("IT", 27), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20),
            Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MT", 31), Map.entry("NL", 18),
            Map.entry("NO", 15), Map.entry("PL", 28), Map.entry("PT", 25), Map.entry("RO", 24),
            Map.entry("SE", 24), Map.entry("SI", 19), Map.entry("SK", 24), Map.entry("SM", 27),
            Map.entry("VA", 22));

    private FinancialIdentifiers() {
    }

    /** @return why {@code value} is no valid IBAN, or {@code null} (spaces are allowed and ignored) */
    public static String checkIban(String value) {
        String iban = value == null ? "" : value.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IBAN.matcher(iban).matches()) {
            return "\"" + value + "\" is not an IBAN (country code, 2 check digits, up to 30 letters or digits).";
        }
        Integer length = IBAN_LENGTHS.get(iban.substring(0, 2));
        if (length != null && iban.length() != length) {
            return "IBAN " + value + " has " + iban.length() + " characters; " + iban.substring(0, 2) + " IBANs have "
                    + length + ".";
        }
        if (mod97(iban.substring(4) + iban.substring(0, 4)) != 1) {
            return "IBAN " + value + " has wrong check digits.";
        }
        return null;
    }

    /** @return why {@code value} is no valid BIC, or {@code null} */
    public static String checkBic(String value) {
        String bic = value == null ? "" : value.strip();
        return BIC.matcher(bic).matches() ? null
                : "\"" + value + "\" is not a BIC (8 or 11 characters: bank, country, location, optional branch).";
    }

    /** @return why {@code value} is no valid LEI, or {@code null} */
    public static String checkLei(String value) {
        String lei = value == null ? "" : value.strip();
        if (!LEI.matcher(lei).matches()) {
            return "\"" + value + "\" is not an LEI (20 characters: 18 letters or digits and 2 check digits).";
        }
        return mod97(lei) == 1 ? null : "LEI " + value + " has wrong check digits.";
    }

    /** @return why {@code value} is no valid ISIN, or {@code null} */
    public static String checkIsin(String value) {
        String isin = value == null ? "" : value.strip();
        if (!ISIN.matcher(isin).matches()) {
            return "\"" + value + "\" is not an ISIN (country code, 9 letters or digits, 1 check digit).";
        }
        StringBuilder digits = new StringBuilder();
        for (char c : isin.substring(0, 11).toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        int sum = 0;
        boolean twice = true; // from the right, starting next to the check digit
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        int check = (10 - sum % 10) % 10;
        return check == isin.charAt(11) - '0' ? null : "ISIN " + value + " has a wrong check digit.";
    }

    /**
     * Austrian VAT id: {@code ATU} and 8 digits, the last one a check digit (§ 28 UStG, the
     * algorithm of the Austrian tax administration).
     *
     * @return why {@code value} is no valid Austrian UID, or {@code null}
     */
    public static String checkAustrianUid(String value) {
        String uid = value == null ? "" : value.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!uid.matches("ATU[0-9]{8}")) {
            return "\"" + value + "\" is not an Austrian VAT id (ATU and 8 digits).";
        }
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            int d = uid.charAt(3 + i) - '0';
            if (i % 2 != 0) { // digits 2, 4, 6 count twice (cross sum)
                d *= 2;
                d = d / 10 + d % 10;
            }
            sum += d;
        }
        int check = (10 - (sum + 4) % 10) % 10;
        return check == uid.charAt(10) - '0' ? null : "Austrian VAT id " + value + " has a wrong check digit.";
    }

    /** ISO 7064 MOD 97-10 of letters (A = 10 … Z = 35) and digits. */
    private static int mod97(String text) {
        StringBuilder digits = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue();
    }
}
