package org.fxt.freexmltoolkit.service.ai.context;

/**
 * How much text one request may carry, counted in characters as a proxy for tokens (roughly four characters per
 * token for XML). When the parts do not fit, {@link ContextBuilder} drops them in a fixed order: samples, then
 * outline depth (paths beyond the limit), then sibling declarations of the schema slice.
 */
public record ContextBudget(int maxCharacters) {

    /** About 12,000 tokens of context: fits small local models' windows with room for the answer. */
    public static final ContextBudget DEFAULT = new ContextBudget(48_000);

    public ContextBudget {
        if (maxCharacters < 2_000) {
            throw new IllegalArgumentException("budget too small: " + maxCharacters);
        }
    }
}
