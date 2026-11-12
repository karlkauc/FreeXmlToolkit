package org.fxt.freexmltoolkit.service.standards;

/**
 * Which XML version documents are checked against: {@link #AUTO} follows each document's declaration (today's
 * behaviour); {@link #V1_0} refuses documents declared as XML 1.1, {@link #V1_1} accepts both.
 */
public enum XmlVersionMode implements StandardVersion {
    AUTO("auto", "XML (auto)"),
    V1_0("1.0", "XML 1.0"),
    V1_1("1.1", "XML 1.1");

    private final String value;
    private final String label;

    XmlVersionMode(String value, String label) {
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
    public static XmlVersionMode parse(String value) {
        return StandardVersion.parse(XmlVersionMode.class, value, AUTO);
    }
}
