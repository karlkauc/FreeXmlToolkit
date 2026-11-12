package org.fxt.freexmltoolkit.service.standards;

/**
 * The query binding a Schematron schema without its own {@code @queryBinding} is compiled with.
 * {@link #DEFAULT} keeps today's choice ({@code xslt2}); a schema's own {@code @queryBinding} always wins.
 */
public enum SchematronBinding implements StandardVersion {
    DEFAULT("default", "Schematron (default)", "xslt2"),
    XSLT("xslt", "Schematron xslt (1.0)", "xslt"),
    XSLT2("xslt2", "Schematron xslt2", "xslt2"),
    XSLT3("xslt3", "Schematron xslt3", "xslt3");

    private final String value;
    private final String label;
    private final String queryBinding;

    SchematronBinding(String value, String label, String queryBinding) {
        this.value = value;
        this.label = label;
        this.queryBinding = queryBinding;
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
        return this == DEFAULT;
    }

    /** The {@code queryBinding} value handed to the Schematron compiler. */
    public String queryBinding() {
        return queryBinding;
    }

    /** {@code value} parsed, {@link #DEFAULT} when blank or unknown. */
    public static SchematronBinding parse(String value) {
        return StandardVersion.parse(SchematronBinding.class, value, DEFAULT);
    }
}
