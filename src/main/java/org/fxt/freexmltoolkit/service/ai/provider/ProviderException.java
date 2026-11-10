package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.OptionalInt;

/**
 * A provider request failed. The message is for the user: it never contains the key, and is not logged above DEBUG
 * because a server may echo the request in it.
 */
public final class ProviderException extends Exception {

    public enum Kind {
        AUTH, PERMISSION, NOT_FOUND, RATE_LIMIT, OVERLOADED, BAD_REQUEST, TOO_LARGE, SERVER, UNREACHABLE, TIMEOUT,
        CANCELLED, PROTOCOL;

        /** Whether a later retry may succeed. */
        public boolean retryable() {
            return this == RATE_LIMIT || this == OVERLOADED || this == SERVER || this == TIMEOUT || this == UNREACHABLE;
        }
    }

    private final Kind kind;
    private final int retryAfterSeconds;
    private final String requestId;
    private final int status;

    public ProviderException(Kind kind, String message) {
        this(kind, message, -1, -1, null, null);
    }

    public ProviderException(Kind kind, String message, int status, int retryAfterSeconds, String requestId,
                             Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
        this.requestId = requestId;
    }

    public Kind kind() {
        return kind;
    }

    /** The HTTP status, or -1. */
    public int status() {
        return status;
    }

    public OptionalInt retryAfterSeconds() {
        return retryAfterSeconds < 0 ? OptionalInt.empty() : OptionalInt.of(retryAfterSeconds);
    }

    /** The provider's request id (for support), or {@code null}. */
    public String requestId() {
        return requestId;
    }

    /** The kind for an HTTP status. */
    public static Kind kindOf(int status) {
        return switch (status) {
            case 400, 409, 422 -> Kind.BAD_REQUEST;
            case 401 -> Kind.AUTH;
            case 402, 403 -> Kind.PERMISSION;
            case 404 -> Kind.NOT_FOUND;
            case 408, 504 -> Kind.TIMEOUT;
            case 413 -> Kind.TOO_LARGE;
            case 429 -> Kind.RATE_LIMIT;
            case 503, 529 -> Kind.OVERLOADED;
            default -> status >= 500 ? Kind.SERVER : Kind.BAD_REQUEST;
        };
    }

    /** A sentence for the user per kind. */
    public static String describe(Kind kind) {
        return switch (kind) {
            case AUTH -> "The provider did not accept the API key.";
            case PERMISSION -> "The provider refused the request (no access, region or billing).";
            case NOT_FOUND -> "The provider does not know the model or the address.";
            case RATE_LIMIT -> "The provider's rate limit is reached.";
            case OVERLOADED -> "The provider is overloaded.";
            case BAD_REQUEST -> "The provider rejected the request.";
            case TOO_LARGE -> "The request is too large for the provider.";
            case SERVER -> "The provider had an internal error.";
            case UNREACHABLE -> "The provider could not be reached.";
            case TIMEOUT -> "The provider did not answer in time.";
            case CANCELLED -> "Cancelled.";
            case PROTOCOL -> "The provider's answer could not be read.";
        };
    }
}
