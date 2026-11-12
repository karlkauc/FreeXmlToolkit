package org.fxt.freexmltoolkit.service.standards;

/**
 * The standards one validation run uses: XSD version, XML version, Schematron default query binding.
 * {@link #DEFAULT} is today's behaviour (XSD detected, XML from the declaration, Schematron xslt2).
 */
public record ValidationStandards(XsdVersionMode xsd, XmlVersionMode xml, SchematronBinding schematron) {

    public static final ValidationStandards DEFAULT = new ValidationStandards(XsdVersionMode.AUTO,
            XmlVersionMode.AUTO, SchematronBinding.DEFAULT);

    public ValidationStandards {
        xsd = xsd == null ? XsdVersionMode.AUTO : xsd;
        xml = xml == null ? XmlVersionMode.AUTO : xml;
        schematron = schematron == null ? SchematronBinding.DEFAULT : schematron;
    }

    /** The validation part of a (resolved or partial) profile. */
    public static ValidationStandards of(StandardsProfile profile) {
        return profile == null ? DEFAULT
                : new ValidationStandards(profile.xsd(), profile.xml(), profile.schematron());
    }

    /** Whether everything is today's default. */
    public boolean isDefault() {
        return xsd.isDefault() && xml.isDefault() && schematron.isDefault();
    }
}
