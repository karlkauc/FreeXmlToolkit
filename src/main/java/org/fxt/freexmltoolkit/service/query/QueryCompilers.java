package org.fxt.freexmltoolkit.service.query;

import org.fxt.freexmltoolkit.service.standards.XPathVersion;

import net.sf.saxon.s9api.XPathCompiler;

/**
 * The XPath compiler settings shared by running a query ({@code XmlServiceImpl}) and checking it while it is
 * typed ({@link QueryErrors#check}), so both report the same static errors.
 */
public final class QueryCompilers {

    private QueryCompilers() {
    }

    /**
     * Sets the language version and, from XPath 3.0 on, pre-declares the {@code map}, {@code array} and
     * {@code math} function namespaces.
     *
     * @param compiler the compiler to configure
     * @param version  the XPath version ({@code null} = 3.1; 1.0 compiles as 2.0)
     * @return {@code compiler}
     */
    public static XPathCompiler configureXPath(XPathCompiler compiler, XPathVersion version) {
        XPathVersion v = version == null ? XPathVersion.V3_1 : version;
        if (v != XPathVersion.V3_1) {
            compiler.setLanguageVersion(v.saxonLanguageVersion());
            if (v == XPathVersion.V2_0 || v == XPathVersion.V1_0) {
                return compiler;
            }
        }
        compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
        compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
        compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
        return compiler;
    }
}
