package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * A global {@code xs:notation} declaration ({@code name}, {@code public}, {@code system}).
 *
 * @since 2.6
 */
public class XsdNotation extends XsdNode {

    private String publicId;
    private String systemId;

    /**
     * Creates a notation.
     *
     * @param name the notation name
     */
    public XsdNotation(String name) {
        super(name);
    }

    /** @return the {@code public} identifier, or null */
    public String getPublicId() {
        return publicId;
    }

    /** @param publicId the {@code public} identifier, null or blank to remove it */
    public void setPublicId(String publicId) {
        String old = this.publicId;
        this.publicId = blankToNull(publicId);
        pcs.firePropertyChange("publicId", old, this.publicId);
    }

    /** @return the {@code system} identifier, or null */
    public String getSystemId() {
        return systemId;
    }

    /** @param systemId the {@code system} identifier, null or blank to remove it */
    public void setSystemId(String systemId) {
        String old = this.systemId;
        this.systemId = blankToNull(systemId);
        pcs.firePropertyChange("systemId", old, this.systemId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.NOTATION;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdNotation copy = new XsdNotation(suffix != null && !suffix.isEmpty() ? getName() + suffix : getName());
        copy.publicId = publicId;
        copy.systemId = systemId;
        copyBasicPropertiesTo(copy, suffix);
        return copy;
    }
}
