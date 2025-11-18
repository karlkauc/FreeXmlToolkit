package org.fxt.freexmltoolkit.controls.v2.editor.core;

/**
 * Editor modes for different XML document types.
 * Each mode can have different completion providers and syntax rules.
 */
public enum EditorMode {
    /**
     * Standard XML mode without schema validation.
     * Provides basic auto-completion and syntax highlighting.
     */
    XML_WITHOUT_XSD,

    /**
     * XML mode with XSD schema validation.
     * Provides context-sensitive IntelliSense based on XSD schema.
     */
    XML_WITH_XSD,

    /**
     * Schematron document mode.
     * Provides Schematron-specific element and pattern completion.
     */
    SCHEMATRON,

    /**
     * XSLT stylesheet mode.
     * Provides XSLT-specific element and template completion.
     */
    XSLT,

    /**
     * XSL-FO stylesheet mode.
     * Provides XSL-FO formatting object completion.
     */
    XSL_FO;

    /**
     * Checks if this mode supports XSD-based IntelliSense.
     *
     * @return true if XSD-based completion is supported
     */
    public boolean supportsXsdIntelliSense() {
        return this == XML_WITH_XSD;
    }

    /**
     * Checks if this mode requires specialized completion providers.
     *
     * @return true if specialized providers are needed
     */
    public boolean requiresSpecializedProviders() {
        return this == SCHEMATRON || this == XSLT || this == XSL_FO;
    }
}
