package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * The schema-wide {@code xs:defaultOpenContent} (XSD 1.1): an {@link XsdOpenContent}
 * declared directly under {@code xs:schema}, with the extra {@code appliesToEmpty} flag.
 *
 * @since 2.6
 */
public class XsdDefaultOpenContent extends XsdOpenContent {

    private boolean appliesToEmpty;

    /**
     * Creates a schema-wide open content declaration.
     *
     * @param mode the mode ({@code interleave} or {@code suffix})
     */
    public XsdDefaultOpenContent(Mode mode) {
        super(mode);
        setName("defaultOpenContent");
    }

    /** @return the {@code appliesToEmpty} flag (default false) */
    public boolean isAppliesToEmpty() {
        return appliesToEmpty;
    }

    /** @param appliesToEmpty the {@code appliesToEmpty} flag */
    public void setAppliesToEmpty(boolean appliesToEmpty) {
        boolean old = this.appliesToEmpty;
        this.appliesToEmpty = appliesToEmpty;
        pcs.firePropertyChange("appliesToEmpty", old, appliesToEmpty);
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdDefaultOpenContent copy = new XsdDefaultOpenContent(getMode());
        copy.appliesToEmpty = appliesToEmpty;
        copyBasicPropertiesTo(copy, suffix);
        return copy;
    }
}
