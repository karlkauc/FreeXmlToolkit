package org.fxt.freexmltoolkit.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds structured Javadoc-style information extracted from XSD appinfo tags.
 */
public class XsdDocInfo implements Serializable {

    private String since;
    private final List<String> see = new ArrayList<>();
    private String deprecated;

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public List<String> getSee() {
        return see;
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * Checks if any Javadoc information was found.
     * @return true if any field is populated, false otherwise.
     */
    public boolean hasData() {
        return since != null || !see.isEmpty() || deprecated != null;
    }
}