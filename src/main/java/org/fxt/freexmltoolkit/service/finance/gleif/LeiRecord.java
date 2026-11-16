package org.fxt.freexmltoolkit.service.finance.gleif;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * What GLEIF knows about one LEI (a record of {@code api.gleif.org/api/v1/lei-records}), or that it knows nothing
 * ({@link #notFound(String, Instant)}).
 *
 * @param lei                the LEI
 * @param found              whether GLEIF has a record
 * @param legalName          the entity's legal name ({@code null} when not found)
 * @param country            the country of the legal address (ISO 3166)
 * @param city               the city of the legal address
 * @param entityStatus       {@code ACTIVE} / {@code INACTIVE}
 * @param registrationStatus {@code ISSUED}, {@code LAPSED}, {@code RETIRED}, {@code ANNULLED}, {@code PENDING_*} …
 * @param nextRenewal        the next renewal date, or {@code null}
 * @param managingLou        the LEI of the issuing organisation
 * @param fetchedAt          when the answer arrived
 */
public record LeiRecord(String lei, boolean found, String legalName, String country, String city, String entityStatus,
                        String registrationStatus, LocalDate nextRenewal, String managingLou, Instant fetchedAt) {

    /** Registration states of an LEI that must not be used any more. */
    private static final Set<String> INVALID = Set.of("RETIRED", "ANNULLED", "DUPLICATE", "MERGED", "TRANSFERRED");

    /** Severity of a {@link Problem}. */
    public enum Severity { ERROR, WARNING, INFO }

    /** Why an LEI should not be reported as it is. */
    public record Problem(Severity severity, String message) {
    }

    /** @return a record for an LEI GLEIF does not know */
    public static LeiRecord notFound(String lei, Instant fetchedAt) {
        return new LeiRecord(lei, false, null, null, null, null, null, null, null, fetchedAt);
    }

    /** @return the GLEIF search page of the LEI */
    public String searchUrl() {
        return "https://search.gleif.org/#/record/" + lei;
    }

    /** @return why the LEI should not be reported, or {@code null} when it is issued and its entity active */
    public Problem problem() {
        if (!found) {
            return new Problem(Severity.ERROR, "LEI " + lei + " is not registered at GLEIF.");
        }
        String who = legalName == null ? "" : " (" + legalName + ")";
        String reg = registrationStatus == null ? "" : registrationStatus;
        if (INVALID.contains(reg)) {
            return new Problem(Severity.ERROR, "LEI " + lei + who + " is " + reg + " at GLEIF and must not be used any more.");
        }
        if ("LAPSED".equals(reg)) {
            return new Problem(Severity.WARNING, "LEI " + lei + who + " has lapsed: its registration was not renewed"
                    + (nextRenewal == null ? "." : " (renewal was due on " + nextRenewal + ")."));
        }
        if ("INACTIVE".equals(entityStatus)) {
            return new Problem(Severity.WARNING, "The entity of LEI " + lei + who + " is inactive at GLEIF.");
        }
        if (reg.startsWith("PENDING")) {
            return new Problem(Severity.INFO, "LEI " + lei + who + " is " + reg + " at GLEIF.");
        }
        return null;
    }

    /** @return one line for lists: name, country, status */
    public String summary() {
        if (!found) {
            return lei + ": not registered at GLEIF";
        }
        return lei + ": " + legalName + (country == null ? "" : " (" + country + ")") + " - " + registrationStatus
                + (entityStatus == null ? "" : ", entity " + entityStatus)
                + (nextRenewal == null ? "" : ", next renewal " + nextRenewal);
    }
}
