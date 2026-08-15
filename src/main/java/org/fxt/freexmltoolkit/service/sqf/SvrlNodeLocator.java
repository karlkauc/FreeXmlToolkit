package org.fxt.freexmltoolkit.service.sqf;

import java.io.StringReader;
import java.util.Optional;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonXPathHelper;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * Resolves SVRL {@code location} XPaths (the context of a Schematron
 * failed-assert/successful-report) against a validated XML document — to the
 * {@link XdmNode} itself for fix execution, or just to its line number for
 * navigation. The document is parsed once with line numbering enabled; each
 * lookup evaluates the location path against it. Best-effort: unresolvable
 * locations yield {@link Optional#empty()} / line {@code 0}.
 */
public final class SvrlNodeLocator {

    private final XdmNode document;
    private final XPathCompiler compiler;

    /** Parses the XML once (line-numbered); a malformed document disables resolution. */
    public SvrlNodeLocator(String xml) {
        XdmNode parsed = null;
        XPathCompiler xPathCompiler = null;
        try {
            Processor processor = SaxonXPathHelper.getProcessor();
            DocumentBuilder builder = processor.newDocumentBuilder();
            builder.setLineNumbering(true);
            parsed = builder.build(new StreamSource(new StringReader(xml)));
            xPathCompiler = processor.newXPathCompiler();
        } catch (Exception e) {
            // resolution is best-effort; lookups simply stay disabled
        }
        this.document = parsed;
        this.compiler = xPathCompiler;
    }

    /** @return the line-numbered document node, or empty when the XML was malformed */
    public Optional<XdmNode> documentNode() {
        return Optional.ofNullable(document);
    }

    /** @return the first node the location XPath points at, or empty if unresolvable */
    public Optional<XdmNode> locate(String location) {
        if (document == null || location == null || location.isBlank()) {
            return Optional.empty();
        }
        try {
            XdmValue result = compiler.evaluate(location, document);
            for (XdmItem item : result) {
                if (item instanceof XdmNode node) {
                    return Optional.of(node);
                }
            }
        } catch (Exception e) {
            // unresolvable location (unknown prefixes, syntax) — no result
        }
        return Optional.empty();
    }

    /** @return the 1-based line of the node the location XPath points at, or 0 if unknown */
    public int lineOf(String location) {
        return locate(location).map(node -> Math.max(node.getLineNumber(), 0)).orElse(0);
    }
}
