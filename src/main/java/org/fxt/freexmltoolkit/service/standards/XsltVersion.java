package org.fxt.freexmltoolkit.service.standards;

/**
 * XSLT language versions. 3.0 runs on Saxon as today; 2.0 runs on Saxon after {@link XsltCompatibilityCheck}
 * found no XSLT 3.0 / XPath 3.x construct; 1.0 runs on the JDK's own XSLT 1.0 processor ({@link Xslt10Engine}).
 */
public enum XsltVersion implements StandardVersion {
    V1_0("1.0"),
    V2_0("2.0"),
    V3_0("3.0");

    private final String value;

    XsltVersion(String value) {
        this.value = value;
    }

    @Override
    public String label() {
        return "XSLT " + value;
    }

    @Override
    public String cliValue() {
        return value;
    }

    @Override
    public boolean isDefault() {
        return this == V3_0;
    }

    /** The XPath version expressions in a stylesheet of this version are written in. */
    public XPathVersion xpathVersion() {
        return switch (this) {
            case V1_0 -> XPathVersion.V1_0;
            case V2_0 -> XPathVersion.V2_0;
            case V3_0 -> XPathVersion.V3_1;
        };
    }

    /** {@code value} parsed, {@link #V3_0} when blank or unknown. */
    public static XsltVersion parse(String value) {
        return StandardVersion.parse(XsltVersion.class, value, V3_0);
    }
}
