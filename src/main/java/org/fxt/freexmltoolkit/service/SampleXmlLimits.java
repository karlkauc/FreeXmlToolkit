package org.fxt.freexmltoolkit.service;

import java.util.Locale;

/**
 * Limits that keep sample XML generation within bounded memory. A schema whose types nest deeply expands into an
 * enormous tree (UBL 2.1, OASIS UCI) and a sample with repeated nested elements can grow exponentially; instead of
 * running out of memory, generation stops and reports the limit as an XML comment.
 */
public final class SampleXmlLimits {

    /** System property: maximum number of nodes one root element may expand to. */
    public static final String MAX_EXPANDED_NODES_PROPERTY = "fxt.sampleXml.maxExpandedNodes";
    /** System property: maximum number of characters of one generated sample. */
    public static final String MAX_OUTPUT_CHARS_PROPERTY = "fxt.sampleXml.maxOutputChars";

    static final int DEFAULT_MAX_EXPANDED_NODES = 1_000_000;
    static final long DEFAULT_MAX_OUTPUT_CHARS = 50_000_000L;

    private SampleXmlLimits() {
    }

    /** @return the node limit for expanding one root element */
    public static int maxExpandedNodes() {
        return Integer.getInteger(MAX_EXPANDED_NODES_PROPERTY, DEFAULT_MAX_EXPANDED_NODES);
    }

    /** @return the character limit for one generated sample */
    public static long maxOutputChars() {
        return Long.getLong(MAX_OUTPUT_CHARS_PROPERTY, DEFAULT_MAX_OUTPUT_CHARS);
    }

    static LimitExceededException nodeLimitExceeded(String rootElementName, int limit) {
        return new LimitExceededException(String.format(Locale.ROOT,
                "Sample XML not generated: the schema expands to more than %,d nodes for root '%s'. "
                        + "Raise the limit with -D%s=<nodes>.", limit, rootElementName, MAX_EXPANDED_NODES_PROPERTY));
    }

    static LimitExceededException outputLimitExceeded(long limit) {
        return new LimitExceededException(String.format(Locale.ROOT,
                "Sample XML not generated: the sample would exceed %,d characters. Raise the limit with -D%s=<characters>.",
                limit, MAX_OUTPUT_CHARS_PROPERTY));
    }

    /** A sample generation limit was exceeded; the message is meant for the user. */
    public static final class LimitExceededException extends RuntimeException {

        LimitExceededException(String message) {
            super(message, null, false, false);
        }

        /** @return the message as an XML comment, the form in which generators report the limit */
        public String toXmlComment() {
            return "<!-- " + getMessage() + " -->";
        }
    }
}
