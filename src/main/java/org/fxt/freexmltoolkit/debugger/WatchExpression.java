package org.fxt.freexmltoolkit.debugger;

import java.util.Objects;

/**
 * User-defined XPath expression evaluated against the current context item
 * at every pause point. Mutable so that re-evaluation can update the cached
 * result without re-creating the row in the watch panel.
 */
public class WatchExpression {

    private final String xpath;
    private volatile String lastValue = "<not evaluated>";
    private volatile String lastError = null;

    public WatchExpression(String xpath) {
        this.xpath = Objects.requireNonNull(xpath, "xpath");
    }

    public String getXpath() {
        return xpath;
    }

    public String getLastValue() {
        return lastValue;
    }

    public void setLastValue(String value) {
        this.lastValue = value == null ? "" : value;
        this.lastError = null;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String error) {
        this.lastError = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WatchExpression that)) return false;
        return xpath.equals(that.xpath);
    }

    @Override
    public int hashCode() {
        return xpath.hashCode();
    }

    @Override
    public String toString() {
        return xpath;
    }
}
