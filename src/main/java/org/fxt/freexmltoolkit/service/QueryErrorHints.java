package org.fxt.freexmltoolkit.service;

import java.util.Set;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Short explanations of the dynamic errors users most often get from their own XPath / XQuery
 * expressions (telemetry: XPTY0019, XPTY0020, XPTY0004, FORG0001). The processor's message names
 * the rule that was broken; the hint says what usually causes it and how to write it instead.
 *
 * <p>Texts live in the i18n bundles under {@code QueryErrorHints.<code>}.</p>
 */
public final class QueryErrorHints {

    /** The error codes that have a hint. */
    static final Set<String> CODES = Set.of("XPTY0019", "XPTY0020", "XPTY0004", "FORG0001");

    private QueryErrorHints() {
    }

    /**
     * The hint for a processor error code.
     *
     * @param code the error code, optionally prefixed ({@code err:XPTY0019}, {@code Q{ns}XPTY0019}); nullable
     * @return the hint text, or {@code null} when the code has none
     */
    public static String hintFor(String code) {
        String local = localPart(code);
        return local != null && CODES.contains(local) ? I18n.t("QueryErrorHints." + local) : null;
    }

    /**
     * The hint for an error, chosen by its message where one code has several usual causes: an XPTY0004 about
     * "more than one item" is a cardinality mistake, not a wrong type.
     *
     * @param code    the error code; nullable
     * @param message the processor's message; nullable
     * @return the hint text, or {@code null}
     */
    public static String hintFor(String code, String message) {
        if ("XPTY0004".equals(localPart(code)) && message != null && message.contains("more than one item")) {
            return I18n.t("QueryErrorHints.XPTY0004.cardinality");
        }
        return hintFor(code);
    }

    private static String localPart(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.strip();
        int brace = c.lastIndexOf('}');
        if (brace >= 0) {
            c = c.substring(brace + 1);
        }
        int colon = c.lastIndexOf(':');
        return colon >= 0 ? c.substring(colon + 1) : c;
    }
}
