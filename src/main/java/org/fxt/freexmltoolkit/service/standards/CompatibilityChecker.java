package org.fxt.freexmltoolkit.service.standards;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;

/**
 * "Check Compatibility": checks a stylesheet, query, XPath expression or Schematron against the chosen (older)
 * language version without running it, and lists every construct that version does not have.
 */
public final class CompatibilityChecker {

    /** What a document is. */
    public enum Kind { XSLT, XQUERY, XPATH, SCHEMATRON }

    /**
     * The outcome.
     *
     * @param target the version checked against (for messages)
     * @param latest the chosen version is the newest: there is nothing to check
     * @param issues the constructs the version does not have (empty = compatible)
     */
    public record Report(String target, boolean latest, List<CompatibilityIssue> issues) {
        public boolean compatible() {
            return issues.isEmpty();
        }
    }

    private CompatibilityChecker() {
    }

    /**
     * @param text     the document text
     * @param systemId its URI (includes / imports of a stylesheet), or {@code null}
     * @param profile  the versions to check against (resolved; {@code null} = the newest)
     */
    public static Report check(Kind kind, String text, String systemId, StandardsProfile profile) {
        StandardsProfile p = (profile == null ? StandardsProfile.LATEST : profile).resolved();
        String source = text == null ? "" : text;
        return switch (kind) {
            case XSLT -> switch (p.xslt()) {
                case V3_0 -> new Report(p.xslt().label(), true, List.of());
                case V2_0 -> new Report(p.xslt().label(), false, XsltCompatibilityCheck.check(source, systemId,
                        XsltVersion.V2_0));
                case V1_0 -> new Report(p.xslt().label(), false, Xslt10Engine.compileIssues(source, systemId));
            };
            case XQUERY -> new Report(p.xquery().label(), p.xquery() == XQueryVersion.V3_1,
                    XQueryCompatibilityCheck.check(source, p.xquery()));
            case XPATH -> new Report(p.xpath().label(), p.xpath() == XPathVersion.V3_1, xpath(source, p.xpath()));
            case SCHEMATRON -> {
                SchematronBinding binding = p.schematron();
                boolean latest = binding == SchematronBinding.DEFAULT || binding == SchematronBinding.XSLT3;
                yield new Report(binding.label(), latest,
                        latest ? List.of() : SchematronCompatibilityCheck.check(source, binding));
            }
        };
    }

    /** An XPath expression (an {@code .xpath} document, comments allowed) compiled in its version. */
    private static List<CompatibilityIssue> xpath(String expression, XPathVersion version) {
        if (version == XPathVersion.V3_1 || expression.isBlank()) {
            return List.of();
        }
        if (version == XPathVersion.V1_0) {
            try {
                Xpath10Engine.check(expression, Map.of());
                return List.of();
            } catch (Xpath10Engine.Xpath10Exception e) {
                return List.of(new CompatibilityIssue(0, 0, "XPST0003", e.getMessage()));
            }
        }
        XPathCompiler compiler = XPathMatchLocator.newCompiler(Map.of(), version);
        compiler.setAllowUndeclaredVariables(true);
        try {
            compiler.compile(expression);
            return List.of();
        } catch (SaxonApiException e) {
            String code = e.getErrorCode() == null ? "XPST0003" : e.getErrorCode().getLocalName();
            if ("XPST0081".equals(code)) {
                return List.of();
            }
            return List.of(new CompatibilityIssue(Math.max(0, e.getLineNumber()), 0, code,
                    e.getMessage() + " (" + version.label() + ")"));
        }
    }
}
