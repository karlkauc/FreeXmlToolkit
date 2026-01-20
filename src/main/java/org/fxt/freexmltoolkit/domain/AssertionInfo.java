package org.fxt.freexmltoolkit.domain;

/**
 * Represents an XSD 1.1 assertion (xs:assert) with its XPath 2.0 test expression.
 * Assertions provide advanced validation beyond what XSD 1.0 can express.
 *
 * @param test The XPath 2.0 test expression
 * @param xpath XPath location of the assertion
 * @param documentation Documentation from xs:annotation
 * @param xpathDefaultNamespace xpath-default-namespace attribute
 */
public record AssertionInfo(
        String test,                    // The XPath 2.0 test expression
        String xpath,                   // XPath location of the assertion
        String documentation,           // Documentation from xs:annotation
        String xpathDefaultNamespace   // xpath-default-namespace attribute
) {
    /**
     * Creates a simple assertion without documentation
     * @param test The test expression
     * @param xpath The XPath location
     */
    public AssertionInfo(String test, String xpath) {
        this(test, xpath, null, null);
    }

    /**
     * Checks if this assertion has documentation
     * @return true if documentation is present
     */
    public boolean hasDocumentation() {
        return documentation != null && !documentation.isEmpty();
    }

    /**
     * Checks if this assertion has a default namespace
     * @return true if default namespace is present
     */
    public boolean hasDefaultNamespace() {
        return xpathDefaultNamespace != null && !xpathDefaultNamespace.isEmpty();
    }

    /**
     * Returns a human-readable description of the assertion
     * @return The description string
     */
    public String getDescription() {
        if (hasDocumentation()) {
            return documentation;
        }
        return "Assertion: " + test;
    }

    /**
     * Returns a shortened version of the test expression for display
     * @param maxLength The maximum length of the string
     * @return The shortened string
     */
    public String getShortTest(int maxLength) {
        if (test == null) {
            return "";
        }
        if (test.length() <= maxLength) {
            return test;
        }
        return test.substring(0, maxLength - 3) + "...";
    }

    @Override
    public String toString() {
        return "Assertion[test=" + getShortTest(50) + ", xpath=" + xpath + "]";
    }
}
