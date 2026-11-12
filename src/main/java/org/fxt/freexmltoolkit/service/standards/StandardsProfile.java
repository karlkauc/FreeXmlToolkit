package org.fxt.freexmltoolkit.service.standards;

/**
 * The XML standards one operation uses. Every component may be {@code null} = "not chosen at this level", so
 * profiles of several levels (global setting, project, tool, command line) can be layered with
 * {@link #overlay}; {@link #resolved()} fills what is still open with today's defaults.
 */
public record StandardsProfile(XPathVersion xpath,
                               XQueryVersion xquery,
                               XsltVersion xslt,
                               XsdVersionMode xsd,
                               XmlVersionMode xml,
                               SchematronBinding schematron) {

    /** Nothing chosen. */
    public static final StandardsProfile EMPTY = new StandardsProfile(null, null, null, null, null, null);

    /** Today's behaviour: the newest versions, XSD/XML detected, Schematron default binding. */
    public static final StandardsProfile LATEST = new StandardsProfile(XPathVersion.V3_1, XQueryVersion.V3_1,
            XsltVersion.V3_0, XsdVersionMode.AUTO, XmlVersionMode.AUTO, SchematronBinding.DEFAULT);

    /** This profile with every value {@code top} chooses replaced by {@code top}'s ({@code null} = this). */
    public StandardsProfile overlay(StandardsProfile top) {
        if (top == null) {
            return this;
        }
        return new StandardsProfile(
                top.xpath != null ? top.xpath : xpath,
                top.xquery != null ? top.xquery : xquery,
                top.xslt != null ? top.xslt : xslt,
                top.xsd != null ? top.xsd : xsd,
                top.xml != null ? top.xml : xml,
                top.schematron != null ? top.schematron : schematron);
    }

    /** This profile laid over {@link #LATEST}: every component is set. */
    public StandardsProfile resolved() {
        return LATEST.overlay(this);
    }

    /** Whether every chosen component is today's default (an empty profile is). */
    public boolean isLatest() {
        return isDefault(xpath) && isDefault(xquery) && isDefault(xslt) && isDefault(xsd) && isDefault(xml)
                && isDefault(schematron);
    }

    private static boolean isDefault(StandardVersion v) {
        return v == null || v.isDefault();
    }

    public StandardsProfile withXpath(XPathVersion v) {
        return new StandardsProfile(v, xquery, xslt, xsd, xml, schematron);
    }

    public StandardsProfile withXquery(XQueryVersion v) {
        return new StandardsProfile(xpath, v, xslt, xsd, xml, schematron);
    }

    public StandardsProfile withXslt(XsltVersion v) {
        return new StandardsProfile(xpath, xquery, v, xsd, xml, schematron);
    }

    public StandardsProfile withXsd(XsdVersionMode v) {
        return new StandardsProfile(xpath, xquery, xslt, v, xml, schematron);
    }

    public StandardsProfile withXml(XmlVersionMode v) {
        return new StandardsProfile(xpath, xquery, xslt, xsd, v, schematron);
    }

    public StandardsProfile withSchematron(SchematronBinding v) {
        return new StandardsProfile(xpath, xquery, xslt, xsd, xml, v);
    }

    /**
     * The layers of one operation, lowest first: the global setting, a project binding, the tool's own choice,
     * an explicit command-line option. {@code null} layers are skipped; the result is {@link #resolved()}.
     */
    public static StandardsProfile resolve(StandardsProfile global, StandardsProfile project,
                                           StandardsProfile tool, StandardsProfile cli) {
        StandardsProfile p = global != null ? global : EMPTY;
        return p.overlay(project).overlay(tool).overlay(cli).resolved();
    }
}
