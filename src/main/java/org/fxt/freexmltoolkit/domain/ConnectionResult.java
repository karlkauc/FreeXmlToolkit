package org.fxt.freexmltoolkit.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

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
    /**
     * Defensive copy constructor to prevent external mutation of array fields.
     */
    public ConnectionResult {
        resultHeader = resultHeader == null ? null : resultHeader.clone();
    }

    /**
     * Returns a defensive copy of the result headers.
     *
     * @return a copy of the result header array, or null if not set
     */
    @Override
    public String[] resultHeader() {
        return resultHeader == null ? null : resultHeader.clone();
    }

    /**
     * Custom equals that uses Arrays.equals for the resultHeader array field.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionResult other)) return false;
        return Objects.equals(url, other.url)
                && Objects.equals(httpStatus, other.httpStatus)
                && Objects.equals(duration, other.duration)
                && Arrays.equals(resultHeader, other.resultHeader)
                && Objects.equals(resultBody, other.resultBody);
    }

    /**
     * Custom hashCode that uses Arrays.hashCode for the resultHeader array field.
     */
    @Override
    public int hashCode() {
        return Objects.hash(url, httpStatus, duration, Arrays.hashCode(resultHeader), resultBody);
    }
}
