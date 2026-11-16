package org.fxt.freexmltoolkit.service.finance.isin;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What ESMA FIRDS or OpenFIGI know about one ISIN, or that neither knows it ({@link #notFound}).
 *
 * @param isin            the ISIN
 * @param source          where the answer comes from
 * @param name            the instrument's full name
 * @param cfi             the CFI code (FIRDS)
 * @param issuerLei       the issuer's LEI (FIRDS)
 * @param currency        the notional currency (FIRDS)
 * @param activeVenues    EU trading venues where it is traded now (FIRDS)
 * @param totalVenues     EU trading venues that reported it (FIRDS)
 * @param terminatedSince the end of trading when no venue trades it any more (FIRDS), else {@code null}
 * @param figi            the FIGI (OpenFIGI)
 * @param ticker          the ticker (OpenFIGI)
 * @param securityType    the security type (OpenFIGI)
 * @param openFigiAsked   whether OpenFIGI was asked about an ISIN that FIRDS does not know
 * @param fetchedAt       when the answer arrived
 */
public record IsinRecord(String isin, Source source, String name, String cfi, String issuerLei, String currency,
                         int activeVenues, int totalVenues, LocalDate terminatedSince, String figi, String ticker,
                         String securityType, boolean openFigiAsked, Instant fetchedAt) {

    /** Where an answer comes from. */
    public enum Source { FIRDS, OPENFIGI, NONE }

    /** Severity of a {@link Problem}. */
    public enum Severity { WARNING, INFO }

    /** Why an ISIN deserves a look. */
    public record Problem(Severity severity, String message) {
    }

    /** @return a record for an ISIN neither service knows */
    public static IsinRecord notFound(String isin, boolean openFigiAsked, Instant fetchedAt) {
        return new IsinRecord(isin, Source.NONE, null, null, null, null, 0, 0, null, null, null, null, openFigiAsked, fetchedAt);
    }

    public boolean found() {
        return source != Source.NONE;
    }

    /** @return the page of the instrument: FIRDS search or OpenFIGI */
    public String searchUrl() {
        return source == Source.OPENFIGI
                ? "https://www.openfigi.com/search#!?simpleSearchString=" + isin
                : "https://registers.esma.europa.eu/publication/searchRegister?core=esma_registers_firds";
    }

    /** @return what deserves a look, or {@code null} */
    public Problem problem() {
        if (source == Source.NONE) {
            return openFigiAsked
                    ? new Problem(Severity.WARNING, "ISIN " + isin + " is known neither to ESMA FIRDS nor to OpenFIGI.")
                    : new Problem(Severity.INFO, "ISIN " + isin + " is not in ESMA FIRDS (not traded on an EU trading venue).");
        }
        if (source == Source.FIRDS && activeVenues == 0) {
            return new Problem(Severity.INFO, "ISIN " + isin + (name == null ? "" : " (" + name + ")")
                    + " is no longer traded on any EU trading venue" + (terminatedSince == null ? "." : " (since " + terminatedSince + ")."));
        }
        return null;
    }

    /** @return one line for lists */
    public String summary() {
        return switch (source) {
            case NONE -> isin + ": " + (openFigiAsked ? "unknown to ESMA FIRDS and OpenFIGI" : "not in ESMA FIRDS");
            case FIRDS -> isin + ": " + name + (cfi == null ? "" : " - CFI " + cfi) + (issuerLei == null ? "" : ", issuer LEI " + issuerLei)
                    + (currency == null ? "" : ", " + currency) + " - " + (activeVenues > 0
                    ? "traded on " + activeVenues + " of " + totalVenues + " EU venues" : "no longer traded in the EU");
            case OPENFIGI -> isin + ": " + name + (securityType == null ? "" : " - " + securityType)
                    + (ticker == null ? "" : ", ticker " + ticker) + (figi == null ? "" : ", FIGI " + figi) + " (OpenFIGI; not in ESMA FIRDS)";
        };
    }
}
