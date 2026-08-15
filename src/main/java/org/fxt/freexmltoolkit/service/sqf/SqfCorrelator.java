package org.fxt.freexmltoolkit.service.sqf;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SaxonXPathHelper;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.AssertKey;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfFix;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * Walks an SVRL report in document order and matches every
 * {@code svrl:failed-assert} / {@code svrl:successful-report} back to the source
 * assert in the {@link SqfCatalog} — by {@code @id} when present, otherwise by the
 * (fired-rule context, test) pair — yielding the fix suggestions per finding.
 */
public final class SqfCorrelator {

    private static final Logger logger = LogManager.getLogger(SqfCorrelator.class);

    private SqfCorrelator() {
    }

    /**
     * One SVRL finding with its correlated fix suggestions.
     *
     * @param report   {@code true} for a successful-report, {@code false} for a failed-assert
     * @param id       the finding's {@code @id} (copied from the assert), or {@code null}
     * @param test     the finding's test expression
     * @param location the finding's location XPath
     * @param fixes    the fix suggestions (empty when none correlate)
     */
    public record SqfFinding(boolean report, String id, String test, String location,
                             List<SqfFixSuggestion> fixes) {
    }

    /**
     * @param svrl    the raw SVRL report XML
     * @param catalog the parsed SQF catalog of the validating Schematron
     * @return all findings in document order; empty on unparseable SVRL
     */
    public static List<SqfFinding> correlate(String svrl, SqfCatalog catalog) {
        List<SqfFinding> findings = new ArrayList<>();
        if (svrl == null || svrl.isBlank()) {
            return findings;
        }
        XdmNode output;
        try {
            DocumentBuilder builder = SaxonXPathHelper.getProcessor().newDocumentBuilder();
            XdmNode doc = builder.build(new StreamSource(new StringReader(svrl)));
            output = firstElementChild(doc);
        } catch (Exception e) {
            logger.debug("Cannot parse SVRL for fix correlation: {}", e.getMessage());
            return findings;
        }
        if (output == null) {
            return findings;
        }

        String currentRuleContext = "";
        for (XdmSequenceIterator<XdmNode> it = output.axisIterator(Axis.CHILD); it.hasNext(); ) {
            XdmNode child = it.next();
            if (child.getNodeKind() != XdmNodeKind.ELEMENT
                    || !SqfNames.SVRL_NS.equals(child.getNodeName().getNamespaceUri().toString())) {
                continue;
            }
            switch (child.getNodeName().getLocalName()) {
                case "fired-rule" -> currentRuleContext = AssertKey.normalize(attr(child, "context"));
                case "failed-assert" -> findings.add(toFinding(child, false, currentRuleContext, catalog));
                case "successful-report" -> findings.add(toFinding(child, true, currentRuleContext, catalog));
                default -> {
                }
            }
        }
        return findings;
    }

    private static SqfFinding toFinding(XdmNode node, boolean report, String ruleContext,
                                        SqfCatalog catalog) {
        String id = attr(node, "id");
        String test = attr(node, "test");
        String location = attr(node, "location");

        List<String> fixKeys = null;
        if (id != null && !id.isBlank()) {
            fixKeys = catalog.fixKeysByAssertId().get(id);
        }
        if (fixKeys == null) {
            fixKeys = catalog.fixKeysByAssert().get(new AssertKey(ruleContext, AssertKey.normalize(test)));
        }

        List<SqfFixSuggestion> suggestions = new ArrayList<>();
        if (fixKeys != null) {
            for (String key : fixKeys) {
                SqfFix fix = catalog.fixesByKey().get(key);
                if (fix != null) {
                    // generic fixes with default-less params are only reachable via call-fix
                    if (fix.params().stream().anyMatch(p -> p.defaultXPath() == null)) {
                        continue;
                    }
                    suggestions.add(new SqfFixSuggestion(fix.key(), fix.id(), fix.title(),
                            fix.paragraphs().isEmpty() ? "" : fix.paragraphs().get(0),
                            catalog.schematronFile(), location, !fix.userEntries().isEmpty()));
                }
            }
        }
        return new SqfFinding(report, id, test, location, List.copyOf(suggestions));
    }

    private static XdmNode firstElementChild(XdmNode node) {
        for (XdmSequenceIterator<XdmNode> it = node.axisIterator(Axis.CHILD); it.hasNext(); ) {
            XdmNode child = it.next();
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return null;
    }

    private static String attr(XdmNode element, String name) {
        return element.getAttributeValue(new QName(name));
    }
}
