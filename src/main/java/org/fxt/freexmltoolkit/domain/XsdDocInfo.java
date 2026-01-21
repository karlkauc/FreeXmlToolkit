package org.fxt.freexmltoolkit.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds structured Javadoc-style information extracted from XSD appinfo tags.
 * This class captures common documentation annotations like {@code @since},
 * {@code @see}, and {@code @deprecated} from XSD schema annotations.
 */
public class XsdDocInfo implements Serializable {

    private String since;
    private final List<String> see = new ArrayList<>();
    private String deprecated;

    /**
     * Gets the version when this element was introduced.
     *
     * @return the version string, or null if not specified
     */
    public String getSince() {
        return since;
    }

    /**
     * Sets the version when this element was introduced.
     *
     * @param since the version string
     */
    public void setSince(String since) {
        this.since = since;
    }

    /**
     * Gets the list of related references.
     *
     * @return a list of reference strings (never null)
     */
    public List<String> getSee() {
        return see;
    }

    /**
     * Gets the deprecation message.
     *
     * @return the deprecation message, or null if not deprecated
     */
    public String getDeprecated() {
        return deprecated;
    }

    /**
     * Sets the deprecation message.
     *
     * @param deprecated the deprecation message
     */
    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * Checks if any Javadoc information was found.
     *
     * @return true if any field is populated, false otherwise
     */
    public boolean hasData() {
        return since != null || !see.isEmpty() || deprecated != null;
    }
}