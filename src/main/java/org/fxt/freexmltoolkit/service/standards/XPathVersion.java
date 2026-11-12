package org.fxt.freexmltoolkit.service.standards;

/**
 * XPath language versions. 2.0, 3.0 and 3.1 run on Saxon with its parser level and function library set to
 * the version; 1.0 runs on the JDK's own XPath 1.0 engine ({@link Xpath10Engine}).
 */
public enum XPathVersion implements StandardVersion {
    V1_0("1.0"),
    V2_0("2.0"),
    V3_0("3.0"),
    V3_1("3.1");

    private final String value;

    XPathVersion(String value) {
        this.value = value;
    }

    @Override
    public String label() {
        return "XPath " + value;
    }

    @Override
    public String cliValue() {
        return value;
    }

    @Override
    public boolean isDefault() {
        return this == V3_1;
    }

    /** The value for Saxon's {@code XPathCompiler.setLanguageVersion} (1.0 is never given to Saxon to run). */
    public String saxonLanguageVersion() {
        return this == V1_0 ? "2.0" : value;
    }

    /** {@code value} parsed, {@link #V3_1} when blank or unknown. */
    public static XPathVersion parse(String value) {
        return StandardVersion.parse(XPathVersion.class, value, V3_1);
    }
}
