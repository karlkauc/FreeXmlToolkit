package org.fxt.freexmltoolkit.service.xmledit;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonXPathHelper;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.ElementRegion;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Span;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Step;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;

/**
 * Evaluates an XPath 3.1 expression against a document's raw text and maps
 * every matched node to its exact character span — the search half of
 * XPath-based search/replace. Node→offset correlation reuses the SQF engine's
 * generic {@link XmlNodeSpanLocator} (positional local-name paths resolved by
 * scanning the text; Saxon's column numbers are unreliable). Matches whose span
 * cannot be located (comments, PIs, ambiguous same-local-name namespaces) carry
 * an {@code error} instead of a span and are excluded from replacement.
 */
public final class XPathMatchLocator {

    private XPathMatchLocator() {
    }

    /** The user's XPath with its namespace bindings ({@code ""} = default element namespace). */
    public record XPathQuery(String xpath, Map<String, String> namespaces) {
    }

    /**
     * One matched node. {@code span} is null when {@code error} explains why the
     * node could not be located in the text; {@code node} is the live Saxon node
     * (valid only against the snapshot this locate ran on) used as the context
     * item for computed replacement values.
     */
    public record NodeMatch(Span span, XdmNodeKind kind, int line, List<Step> path,
                            String attrName, int textIndex, String preview, String matchedText,
                            int contentStart, int contentEnd, XdmNode node, String error) {

        public boolean located() {
            return span != null;
        }
    }

    /** Thrown when the XPath is invalid or selects non-node values. */
    public static final class XPathEditException extends Exception {
        public XPathEditException(String message) {
            super(message);
        }

        public XPathEditException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Max preview length shown per match in the results tree. */
    private static final int PREVIEW_LIMIT = 100;

    /**
     * Evaluates {@code query} against {@code xml} and locates each matched node.
     *
     * @throws XPathEditException when the document or the XPath cannot be parsed,
     *                            or the XPath yields only atomic values
     */
    public static List<NodeMatch> locate(String xml, XPathQuery query) throws XPathEditException {
        Processor processor = SaxonXPathHelper.getProcessor();
        XdmNode document;
        try {
            DocumentBuilder builder = processor.newDocumentBuilder();
            builder.setLineNumbering(true);
            document = builder.build(new StreamSource(new StringReader(xml)));
        } catch (SaxonApiException e) {
            throw new XPathEditException("Document is not well-formed XML: " + rootMessage(e), e);
        }
        XdmValue result;
        try {
            XPathSelector selector = newCompiler(query.namespaces()).compile(query.xpath()).load();
            selector.setContextItem(document);
            result = selector.evaluate();
        } catch (SaxonApiException e) {
            throw new XPathEditException("XPath error: " + rootMessage(e), e);
        }
        List<NodeMatch> matches = new ArrayList<>();
        int atomics = 0;
        // Parent paths repeat heavily; memoize the per-path text scan.
        Map<List<Step>, Optional<ElementRegion>> regions = new HashMap<>();
        for (XdmItem item : result) {
            if (!(item instanceof XdmNode node)) {
                atomics++;
                continue;
            }
            matches.add(toMatch(xml, node, regions));
        }
        if (matches.isEmpty() && atomics > 0) {
            throw new XPathEditException("The XPath returns values, not nodes — "
                    + "select elements, attributes or text nodes to search/replace "
                    + "(e.g. //Amount instead of count(//Amount)).");
        }
        return matches;
    }

    /** Builds an XPath 3.1 compiler with the app's standard prefixes plus user bindings. */
    public static XPathCompiler newCompiler(Map<String, String> namespaces) {
        XPathCompiler compiler = SaxonXPathHelper.getProcessor().newXPathCompiler();
        compiler.setLanguageVersion("3.1");
        // Standard function-namespace prefixes, as in XmlServiceImpl.newXPathCompiler.
        compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
        compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
        compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
        compiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
        compiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        if (namespaces != null) {
            namespaces.forEach((prefix, uri) -> {
                if (uri != null && !uri.isBlank()) {
                    compiler.declareNamespace(prefix == null ? "" : prefix, uri);
                }
            });
        }
        return compiler;
    }

    /**
     * @return the namespace bindings in scope on the root element (prefix → URI,
     *         {@code ""} for the default namespace), or an empty map for
     *         malformed documents — feeds the namespace table's "Detect" action
     */
    public static Map<String, String> extractRootNamespaces(String xml) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        try {
            DocumentBuilder builder = SaxonXPathHelper.getProcessor().newDocumentBuilder();
            XdmNode document = builder.build(new StreamSource(new StringReader(xml)));
            XdmNode root = null;
            for (var it = document.axisIterator(Axis.CHILD); it.hasNext(); ) {
                XdmNode child = it.next();
                if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                    root = child;
                    break;
                }
            }
            if (root != null) {
                for (var it = root.axisIterator(Axis.NAMESPACE); it.hasNext(); ) {
                    XdmNode ns = it.next();
                    String prefix = ns.getNodeName() != null ? ns.getNodeName().getLocalName() : "";
                    if (!"xml".equals(prefix)) {
                        namespaces.put(prefix, ns.getStringValue());
                    }
                }
            }
        } catch (SaxonApiException e) {
            // malformed document — nothing to detect
        }
        return namespaces;
    }

    // ---------------------------------------------------------------------

    private static NodeMatch toMatch(String xml, XdmNode node,
                                     Map<List<Step>, Optional<ElementRegion>> regions) {
        XdmNodeKind kind = node.getNodeKind();
        switch (kind) {
            case ELEMENT -> {
                List<Step> path = XmlNodeSpanLocator.pathOf(node);
                Optional<ElementRegion> region = regionOf(xml, path, regions);
                if (region.isEmpty() || !tagNameMatches(xml, region.get(), node)) {
                    return unlocated(node, kind, path, null,
                            "Cannot locate the element in the document text");
                }
                return located(xml, node, kind, path, null, -1, region.get().span(),
                        region.get().contentStart(), region.get().contentEnd());
            }
            case ATTRIBUTE -> {
                XdmNode parent = node.getParent();
                if (parent == null || parent.getNodeKind() != XdmNodeKind.ELEMENT) {
                    return unlocated(node, kind, List.of(), null, "Attribute without element parent");
                }
                List<Step> path = XmlNodeSpanLocator.pathOf(parent);
                Optional<ElementRegion> region = regionOf(xml, path, regions);
                if (region.isEmpty() || !tagNameMatches(xml, region.get(), parent)) {
                    return unlocated(node, kind, path, node.getNodeName().getLocalName(),
                            "Cannot locate the owning element in the document text");
                }
                String attrName = node.getNodeName().getLocalName();
                Optional<Span> span = XmlNodeSpanLocator.attributeSpan(xml, region.get(), attrName);
                if (span.isEmpty()) {
                    return unlocated(node, kind, path, attrName,
                            "Cannot locate the attribute in the start tag");
                }
                NodeMatch match = located(xml, node, kind, path, attrName, -1, span.get(), -1, -1);
                // attributes have no own line number; use the owning element's
                return match.line() > 0 ? match : withLine(match, parent.getLineNumber());
            }
            case TEXT -> {
                XdmNode parent = node.getParent();
                if (parent == null || parent.getNodeKind() != XdmNodeKind.ELEMENT) {
                    return unlocated(node, kind, List.of(), null, "Text node without element parent");
                }
                List<Step> path = XmlNodeSpanLocator.pathOf(parent);
                Optional<ElementRegion> region = regionOf(xml, path, regions);
                if (region.isEmpty() || !tagNameMatches(xml, region.get(), parent)) {
                    return unlocated(node, kind, path, null,
                            "Cannot locate the owning element in the document text");
                }
                List<Span> segments = XmlNodeSpanLocator.textSegmentSpans(xml, region.get());
                int index = XmlNodeSpanLocator.textNodeIndex(node);
                if (index >= segments.size()) {
                    return unlocated(node, kind, path, null,
                            "Cannot locate the text segment (mixed content with comments/CDATA)");
                }
                return located(xml, node, kind, path, null, index, segments.get(index), -1, -1);
            }
            default -> {
                return unlocated(node, kind, List.of(), null,
                        kind + " nodes cannot be replaced as text ranges");
            }
        }
    }

    private static Optional<ElementRegion> regionOf(String xml, List<Step> path,
                                                    Map<List<Step>, Optional<ElementRegion>> memo) {
        return memo.computeIfAbsent(path, p -> XmlNodeSpanLocator.elementRegion(xml, p));
    }

    /**
     * Guard against the locator's local-name-only paths: the resolved start tag's
     * local name must equal the Saxon node's local name, else the span is refused.
     */
    private static boolean tagNameMatches(String xml, ElementRegion region, XdmNode element) {
        int start = region.span().start();
        int i = start + 1;
        while (i < xml.length()) {
            char c = xml.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                break;
            }
            i++;
        }
        String raw = xml.substring(start + 1, i);
        int colon = raw.indexOf(':');
        String local = colon >= 0 ? raw.substring(colon + 1) : raw;
        return local.equals(element.getNodeName().getLocalName());
    }

    private static NodeMatch located(String xml, XdmNode node, XdmNodeKind kind,
                                     List<Step> path, String attrName, int textIndex, Span span,
                                     int contentStart, int contentEnd) {
        String matched = xml.substring(span.start(), Math.min(span.end(), xml.length()));
        return new NodeMatch(span, kind, Math.max(node.getLineNumber(), 0), path,
                attrName, textIndex, preview(matched), matched, contentStart, contentEnd, node, null);
    }

    private static NodeMatch unlocated(XdmNode node, XdmNodeKind kind, List<Step> path,
                                       String attrName, String reason) {
        return new NodeMatch(null, kind, Math.max(node.getLineNumber(), 0), path,
                attrName, -1, preview(node.toString()), null, -1, -1, node, reason);
    }

    private static NodeMatch withLine(NodeMatch match, int line) {
        return new NodeMatch(match.span(), match.kind(), Math.max(line, 0), match.path(),
                match.attrName(), match.textIndex(), match.preview(), match.matchedText(),
                match.contentStart(), match.contentEnd(), match.node(), match.error());
    }

    private static String preview(String text) {
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= PREVIEW_LIMIT ? oneLine : oneLine.substring(0, PREVIEW_LIMIT) + "…";
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : t.toString();
    }
}
