package org.fxt.freexmltoolkit.domain;

import java.net.URI;

/**
 * Result of a connection attempt.
 *
 * @param url          The URL connected to.
 * @param httpStatus   The HTTP status code.
 * @param duration     The duration of the request in milliseconds.
 * @param resultHeader The response headers.
 * @param resultBody   The response body.
 */
public record ConnectionResult(
        URI url,
        Integer httpStatus,
        Long duration,
        String[] resultHeader,
        String resultBody
) {
}
