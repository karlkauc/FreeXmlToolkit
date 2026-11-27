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
        }
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    /** @return "{@code service} could not be reached: why" */
    public static String unreachable(String service, Throwable error) {
        return I18n.t("NetworkErrors.unreachable", service, describe(error));
    }
}
