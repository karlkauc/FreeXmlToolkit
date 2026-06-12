package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * Resolves an SVRL {@code location} XPath (the context of a Schematron
 * failed-assert/successful-report, e.g.
 * {@code /*[local-name()='FundsXML4']/*[local-name()='ControlData'][1]}) to a line
 * number in the validated XML, so Schematron problems can navigate to their source
 * line just like XSD problems. The document is parsed once with Saxon line
 * numbering enabled; each lookup evaluates the location path against it.
 * Best-effort: any unresolvable location yields {@code 0} (no navigation).
 */
final class SchematronLineResolver {

    private final XdmNode document;
    private final XPathCompiler compiler;

    /** Parses the XML once (line-numbered); a malformed document disables resolution. */
    SchematronLineResolver(String xml) {
        XdmNode parsed = null;
        XPathCompiler xPathCompiler = null;
        try {
            Processor processor = XsltTransformationEngine.getInstance().getSaxonProcessor();
            DocumentBuilder builder = processor.newDocumentBuilder();
            builder.setLineNumbering(true);
            parsed = builder.build(new StreamSource(new StringReader(xml)));
            xPathCompiler = processor.newXPathCompiler();
        } catch (Exception e) {
            // resolution is best-effort; navigation simply stays disabled
        }
        this.document = parsed;
        this.compiler = xPathCompiler;
    }

    /** @return the 1-based line of the node the location XPath points at, or 0 if unknown */
    int lineOf(String location) {
        if (document == null || location == null || location.isBlank()) {
            return 0;
        }
        try {
            XdmValue result = compiler.evaluate(location, document);
            for (XdmItem item : result) {
                if (item instanceof XdmNode node) {
                    return Math.max(node.getLineNumber(), 0);
                }
            }
        } catch (Exception e) {
            // unresolvable location (unknown prefixes, syntax) — no navigation
        }
        return 0;
    }
}
