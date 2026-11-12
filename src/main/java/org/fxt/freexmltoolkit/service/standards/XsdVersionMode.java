package org.fxt.freexmltoolkit.service.standards;

/**
 * Which XSD version validation uses: {@link #AUTO} detects XSD 1.1 from the schema (today's behaviour),
 * {@link #V1_0} and {@link #V1_1} force the version.
 */
public enum XsdVersionMode implements StandardVersion {
    AUTO("auto", "XSD (auto)"),
    V1_0("1.0", "XSD 1.0"),
    V1_1("1.1", "XSD 1.1");

    private final String value;
    private final String label;

    XsdVersionMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String cliValue() {
        return value;
    }

    @Override
    public boolean isDefault() {
        return this == AUTO;
    }

    /** {@code value} parsed, {@link #AUTO} when blank or unknown. */
    public static XsdVersionMode parse(String value) {
        return StandardVersion.parse(XsdVersionMode.class, value, AUTO);
    }
}
