package org.fxt.freexmltoolkit.service.ai.provider;

/** Token counts a provider reported; -1 when it reported none. */
public record Usage(long inputTokens, long outputTokens) {

    public static final Usage UNKNOWN = new Usage(-1, -1);
}
