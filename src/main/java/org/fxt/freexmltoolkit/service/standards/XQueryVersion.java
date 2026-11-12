package org.fxt.freexmltoolkit.service.standards;

/**
 * XQuery language versions. Saxon HE only compiles XQuery 3.1, so 1.0 and 3.0 are enforced by
 * {@link XQueryCompatibilityCheck} before the query runs.
 */
public enum XQueryVersion implements StandardVersion {
    V1_0("1.0"),
    V3_0("3.0"),
    V3_1("3.1");

    private final String value;

    XQueryVersion(String value) {
        this.value = value;
    }

    @Override
    public String label() {
        return "XQuery " + value;
    }

    @Override
    public String cliValue() {
        return value;
    }

    @Override
    public boolean isDefault() {
        return this == V3_1;
    }

    /** The XPath version an XQuery of this version builds on (its function library and expression syntax). */
    public XPathVersion xpathVersion() {
        return switch (this) {
            case V1_0 -> XPathVersion.V2_0;
            case V3_0 -> XPathVersion.V3_0;
            case V3_1 -> XPathVersion.V3_1;
        };
    }

    /** {@code value} parsed, {@link #V3_1} when blank or unknown. */
    public static XQueryVersion parse(String value) {
        return StandardVersion.parse(XQueryVersion.class, value, V3_1);
    }
}
