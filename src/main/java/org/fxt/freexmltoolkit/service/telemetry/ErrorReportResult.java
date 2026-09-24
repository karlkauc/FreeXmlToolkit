package org.fxt.freexmltoolkit.service.telemetry;

/**
 * Outcome of a user-initiated error report ({@code POST /v1/error-report}).
 *
 * @param success    true if the server accepted the report (HTTP 201)
 * @param httpStatus the HTTP status, or 0 if no request was made / the transport failed
 * @param feedbackId the server-assigned id (null unless successful)
 * @param message    a short user-facing English message describing the outcome
 */
public record ErrorReportResult(boolean success, int httpStatus, String feedbackId, String message) {

    /** A failed result without an HTTP exchange. */
    public static ErrorReportResult failure(String message) {
        return new ErrorReportResult(false, 0, null, message);
    }
}
