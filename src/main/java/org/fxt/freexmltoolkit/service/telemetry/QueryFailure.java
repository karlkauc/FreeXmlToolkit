package org.fxt.freexmltoolkit.service.telemetry;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;

/**
 * Why a query-style action (XPath, XQuery, JSONPath, XProc) failed, reduced to what telemetry
 * may carry: a fixed error code, whether the failure is the user's input or the app's fault,
 * and — for failures without a processor code — the stack signature.
 *
 * <p>Processor error codes are fixed identifiers (e.g. {@code XPST0003}); message texts,
 * expressions and document content never enter this record.</p>
 *
 * @param errorCode processor error code or exception class (nullable)
 * @param status    {@code INVALID_INPUT} for a broken expression or document, else {@code ERROR}
 * @param signature stack signature for failures without a processor code (nullable)
 */
public record QueryFailure(String errorCode, TelemetryEvent.Status status, ErrorSignature signature) {

    /** A failure about which nothing is known. */
    public static final QueryFailure UNKNOWN = new QueryFailure(null, TelemetryEvent.Status.ERROR, null);

    /** Code prefixes of static / parse errors: the expression, stylesheet or document is broken. */
    private static final String[] INPUT_CODE_PREFIXES = {"XPST", "XQST", "XTSE", "SXXP", "XS"};

    /**
     * Classifies a thrown failure. A processor error code anywhere in the cause chain wins;
     * otherwise the failure keeps its full stack signature so it can be diagnosed.
     *
     * @param t the failure (nullable)
     * @return the classification, never null
     */
    public static QueryFailure of(Throwable t) {
        if (t == null) {
            return UNKNOWN;
        }
        Throwable last = t;
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            last = c;
            String code = processorCode(c);
            if (code != null) {
                return ofCode(code);
            }
        }
        if (last instanceof org.xml.sax.SAXParseException) {
            return new QueryFailure("SAXParseException", TelemetryEvent.Status.INVALID_INPUT, null);
        }
        ErrorSignature signature = ErrorSignature.of(t);
        return new QueryFailure(signature.errorCode(), TelemetryEvent.Status.ERROR, signature);
    }

    /**
     * Classifies a failure known only by its processor error code.
     *
     * @param code the error code, optionally prefixed ({@code err:XS0001}, {@code Q{ns}XPST0003}); nullable
     * @return the classification, never null
     */
    public static QueryFailure ofCode(String code) {
        String local = localPart(code);
        if (local == null) {
            return UNKNOWN;
        }
        for (String prefix : INPUT_CODE_PREFIXES) {
            if (local.startsWith(prefix)) {
                return new QueryFailure(local, TelemetryEvent.Status.INVALID_INPUT, null);
            }
        }
        return new QueryFailure(local, TelemetryEvent.Status.ERROR, null);
    }

    /**
     * A failure that is by definition the user's input, identified by a fixed code.
     *
     * @param code a fixed identifier, never message text
     * @return the classification
     */
    public static QueryFailure invalidInput(String code) {
        return new QueryFailure(code, TelemetryEvent.Status.INVALID_INPUT, null);
    }

    private static String processorCode(Throwable t) {
        if (t instanceof SaxonApiException e && e.getErrorCode() != null) {
            return e.getErrorCode().getLocalName();
        }
        if (t instanceof XPathException e && e.getErrorCodeQName() != null) {
            return e.getErrorCodeQName().getLocalPart();
        }
        return null;
    }

    private static String localPart(String code) {
        if (code == null) {
            return null;
        }
        String local = code.substring(Math.max(code.lastIndexOf('}'), code.lastIndexOf(':')) + 1).strip();
        return local.isEmpty() ? null : local;
    }
}
