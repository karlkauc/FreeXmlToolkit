package org.fxt.freexmltoolkit.service.ai.context;

/**
 * How much of a document may leave the machine in a request to an AI provider, from least to most.
 *
 * <ul>
 *   <li>{@link #SCHEMA}: schema slices and documentation, element and attribute names, the user's own words, error
 *       messages with values removed;</li>
 *   <li>{@link #STRUCTURE}: as above plus the document outline with values replaced by type placeholders;</li>
 *   <li>{@link #EXCERPTS}: as above plus the selected text or the nodes around a problem, within the budget.</li>
 * </ul>
 */
public enum DataLevel {
    SCHEMA, STRUCTURE, EXCERPTS;

    /** The lower of the two levels. */
    public DataLevel min(DataLevel other) {
        return other == null || ordinal() <= other.ordinal() ? this : other;
    }

    /** Whether this level allows at least {@code other}. */
    public boolean allows(DataLevel other) {
        return ordinal() >= other.ordinal();
    }

    /** The level named by {@code text} (case-insensitive), or {@code fallback}. */
    public static DataLevel parse(String text, DataLevel fallback) {
        if (text != null) {
            for (DataLevel level : values()) {
                if (level.name().equalsIgnoreCase(text.strip())) {
                    return level;
                }
            }
        }
        return fallback;
    }
}
