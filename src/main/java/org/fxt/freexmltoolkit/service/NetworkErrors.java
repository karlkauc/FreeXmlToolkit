package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Readable texts for failed connections. The JDK reports a refused proxy sign-in with bare messages
 * ({@code "Authentication failure"} when the NTLM / Kerberos handshake with the proxy fails,
 * {@code "Unable to tunnel through proxy. Proxy returns "HTTP/1.1 407 …""} when it wants credentials it did not
 * get); those are turned into a hint where to fix it. Every other error keeps its own message.
 */
public final class NetworkErrors {

    /** The JDK's message when the NTLM / Negotiate exchange with the proxy fails. */
    static final String JDK_AUTHENTICATION_FAILURE = "Authentication failure";

    private NetworkErrors() {
    }

    /** @return a text for the user saying why {@code error} happened (never null) */
    public static String describe(Throwable error) {
        if (error == null) {
            return "";
        }
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            if (JDK_AUTHENTICATION_FAILURE.equals(message.trim())) {
                return I18n.t("NetworkErrors.proxy-sign-in-refused");
            }
            if (message.contains("Unable to tunnel through proxy") && message.contains("407")) {
                return I18n.t("NetworkErrors.proxy-needs-sign-in");
            }
            if (message.contains("PKIX path building failed")
                    || message.contains("unable to find valid certification path")) {
                return I18n.t(SystemTrust.isInstalled() ? "NetworkErrors.certificate-untrusted"
                        : "NetworkErrors.certificate-untrusted-system-trust-off");
            }
        }
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    /**
     * @return "{@code service} answered with HTTP {@code status}", for a gateway error (502/503/504) with what it
     * means: an intermediary (the proxy) or the service got no answer in time - usually temporary
     */
    public static String httpStatus(String service, int status) {
        return status == 502 || status == 503 || status == 504
                ? I18n.t("NetworkErrors.gateway", service, String.valueOf(status))
                : I18n.t("NetworkErrors.http-status", service, String.valueOf(status));
    }

    /** @return whether {@code status} is a gateway error worth one more try (502/503/504) */
    public static boolean isGatewayError(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    /** @return "{@code service} could not be reached: why" */
    public static String unreachable(String service, Throwable error) {
        return I18n.t("NetworkErrors.unreachable", service, describe(error));
    }
}
