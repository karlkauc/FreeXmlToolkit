package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.DifferenceEvaluator;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.ElementSelector;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.diff.NodeFilters;
import org.xmlunit.placeholder.PlaceholderDifferenceEvaluator;

/**
 * Compares two XML documents by structure rather than by lines (XMLUnit 2): attribute order,
 * namespace prefixes and, by option, whitespace, comments and the order of elements do not count.
 * Each {@link Difference} names the node by XPath and carries the line on both sides, so a view
 * can jump there. UI-free; used by the Compare view's XML mode ({@code DiffView}).
 */
public final class XmlStructuralDiff {

    /** What changed. */
    public enum Change { ADDED, REMOVED, CHANGED, MOVED }

    /** The kind of node that changed. */
    public enum NodeKind { ELEMENT, ATTRIBUTE, TEXT, COMMENT, PROCESSING_INSTRUCTION, NAMESPACE, DOCUMENT }

    /**
     * Comparison options.
     *
     * @param normalizeWhitespace   trim text and collapse runs of whitespace; whitespace-only text between
     *                              elements is ignored either way when this is on
     * @param ignoreComments        do not compare comments
     * @param reportPrefixChanges   report a changed namespace prefix (same namespace URI)
     * @param ignoreElementOrder    elements are matched regardless of their position; moves are not reported
     * @param keyAttributes         attributes that identify an element among same-named siblings (e.g. {@code id})
     * @param ignoreXPaths          XPath 1.0 expressions whose nodes (with their content) and attributes are left
     *                              out on both sides, e.g. {@code //@timestamp}; prefixes are those declared on
     *                              either root element
     * @param placeholders          evaluate XMLUnit placeholders in the LEFT (expected) document:
     *                              {@code ${xmlunit.ignore}}, {@code ${xmlunit.isNumber}},
     *                              {@code ${xmlunit.matchesRegex(…)}}, {@code ${xmlunit.isDateTime}}
     * @param numericTolerance      text and attribute values that are both numbers count as equal when they differ
     *                              by at most this much (0: exact)
     */
    public record Options(boolean normalizeWhitespace, boolean ignoreComments, boolean reportPrefixChanges,
                          boolean ignoreElementOrder, List<String> keyAttributes, List<String> ignoreXPaths,
                          boolean placeholders, double numericTolerance) {

        /** Whitespace normalized, comments compared, prefixes ignored, order matters, no keys, placeholders on. */
        public static final Options DEFAULTS = new Options(true, false, false, false, List.of());

        public Options {
            keyAttributes = keyAttributes == null ? List.of()
                    : keyAttributes.stream().map(String::strip).filter(k -> !k.isEmpty()).distinct().toList();
            ignoreXPaths = ignoreXPaths == null ? List.of()
                    : ignoreXPaths.stream().map(String::strip).filter(k -> !k.isEmpty()).distinct().toList();
            numericTolerance = Double.isFinite(numericTolerance) ? Math.max(0, numericTolerance) : 0;
        }

        /** The original five options; no ignored XPaths, placeholders on, exact numbers. */
        public Options(boolean normalizeWhitespace, boolean ignoreComments, boolean reportPrefixChanges,
                       boolean ignoreElementOrder, List<String> keyAttributes) {
            this(normalizeWhitespace, ignoreComments, reportPrefixChanges, ignoreElementOrder, keyAttributes,
                    List.of(), true, 0);
        }

        public Options withIgnoreXPaths(List<String> xpaths) {
            return new Options(normalizeWhitespace, ignoreComments, reportPrefixChanges, ignoreElementOrder,
                    keyAttributes, xpaths, placeholders, numericTolerance);
        }

        public Options withPlaceholders(boolean on) {
            return new Options(normalizeWhitespace, ignoreComments, reportPrefixChanges, ignoreElementOrder,
                    keyAttributes, ignoreXPaths, on, numericTolerance);
        }

        public Options withNumericTolerance(double tolerance) {
            return new Options(normalizeWhitespace, ignoreComments, reportPrefixChanges, ignoreElementOrder,
                    keyAttributes, ignoreXPaths, placeholders, tolerance);
        }

        /** @return the same options with the five basic switches replaced (the rest kept) */
        public Options withBasics(boolean normalize, boolean comments, boolean prefixes, boolean order,
                                  List<String> keys) {
            return new Options(normalize, comments, prefixes, order, keys, ignoreXPaths, placeholders,
                    numericTolerance);
        }

        /**
         * @return why an ignore expression does not compile ({@code expression: reason}), or {@code null};
         *         prefixes are not checked here, they come from the documents
         */
        public String invalidIgnoreXPath() {
            for (String expression : ignoreXPaths) {
                try {
                    XPATHS.newXPath().compile(expression.replaceAll("\\b[\\w.-]+:(?=[\\w*])", ""));
                } catch (XPathExpressionException e) {
                    return expression + ": " + (e.getCause() != null && e.getCause().getMessage() != null
                            ? e.getCause().getMessage() : String.valueOf(e.getMessage()));
                }
            }
            return null;
        }

        /** @return expressions from text, one per line (blank lines skipped) */
        public static List<String> parseXPaths(String text) {
            return text == null || text.isBlank() ? List.of()
                    : Arrays.stream(text.split("\\R")).map(String::strip).filter(l -> !l.isEmpty()).toList();
        }

        /** @return the key attributes from a comma- or space-separated list */
        public static List<String> parseKeys(String text) {
            return text == null || text.isBlank() ? List.of() : Arrays.asList(text.strip().split("[,\\s]+"));
        }
    }

    /**
     * One difference.
     *
     * @param change      added (only right), removed (only left), changed, moved
     * @param kind        the kind of node
     * @param name        the node's name: element or attribute QName, {@code #text}, {@code #comment}, …
     * @param leftXPath   the node's XPath on the left, or the parent's when it is missing there
     * @param rightXPath  likewise on the right
     * @param leftValue   the value on the left (empty when missing)
     * @param rightValue  the value on the right (empty when missing)
     * @param leftLine    1-based line on the left (of the node, or of its parent when missing), 0 when unknown
     * @param rightLine   likewise on the right
     * @param similar     XMLUnit rates it "similar", not "different": a moved element, CDATA instead of text,
     *                    another schema location — the documents still mean the same
     */
    public record Difference(Change change, NodeKind kind, String name, String leftXPath, String rightXPath,
                             String leftValue, String rightValue, int leftLine, int rightLine, boolean similar) {

        /** A difference that is not merely similar. */
        public Difference(Change change, NodeKind kind, String name, String leftXPath, String rightXPath,
                          String leftValue, String rightValue, int leftLine, int rightLine) {
            this(change, kind, name, leftXPath, rightXPath, leftValue, rightValue, leftLine, rightLine, false);
        }

        /** @return the same difference marked as similar (XMLUnit: the documents still mean the same) */
        Difference asSimilar() {
            return new Difference(change, kind, name, leftXPath, rightXPath, leftValue, rightValue, leftLine,
                    rightLine, true);
        }

        /** @return a one-line description, e.g. {@code Attribute 'sku' changed: "A" → "B"} */
        public String describe() {
            String what = switch (kind) {
                case ELEMENT -> "Element <" + name + ">";
                case ATTRIBUTE -> "Attribute '" + name + "'";
                case TEXT -> "Text";
                case COMMENT -> "Comment";
                case PROCESSING_INSTRUCTION -> "Processing instruction " + name;
                case NAMESPACE -> "Namespace of <" + name + ">";
                case DOCUMENT -> name;
            };
            return switch (change) {
                case ADDED -> what + " added";
                case REMOVED -> what + " removed";
                case MOVED -> what + " moved";
                case CHANGED -> what + " changed: " + quote(leftValue) + " → " + quote(rightValue);
            };
        }

        private static String quote(String value) {
            return "\"" + value + "\"";
        }
    }

    /**
     * The result of a comparison.
     *
     * @param differences the differences in document order of the left side
     * @param leftError   why the left document could not be read, or {@code null}
     * @param rightError  likewise for the right document
     */
    public record Result(List<Difference> differences, String leftError, String rightError) {

        /** @return whether both documents were read */
        public boolean compared() {
            return leftError == null && rightError == null;
        }

        /** @return whether both documents were read and have no differences */
        public boolean equivalent() {
            return compared() && differences.isEmpty();
        }

        /** @return the number of differences XMLUnit rates "similar" */
        public long similarCount() {
            return differences.stream().filter(Difference::similar).count();
        }
    }

    private static final XPathFactory XPATHS = XPathFactory.newInstance();

    /** Line of a node, stored as DOM user data while parsing. */
    static final String LINE = "fxt.line";
    /** Text values longer than this are shortened in a {@link Difference}. */
    static final int MAX_VALUE_LENGTH = 200;

    private XmlStructuralDiff() {
    }

    /** Compares two XML texts. Never throws for bad input: the {@link Result} names the side that failed. */
    public static Result compare(String left, String right, Options options) {
        Document leftDoc;
        Document rightDoc;
        String leftError = null;
        String rightError = null;
        leftDoc = null;
        rightDoc = null;
        try {
            leftDoc = parse(left, options);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            leftError = message(e);
        }
        try {
            rightDoc = parse(right, options);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            rightError = message(e);
        }
        if (leftDoc == null || rightDoc == null) {
            return new Result(List.of(), leftError, rightError);
        }
        return new Result(differences(leftDoc, rightDoc, options), null, null);
    }

    // ---- comparison ---------------------------------------------------------------------------

    private static List<Difference> differences(Document left, Document right, Options options) {
        // DefaultNodeMatcher pairs all nodes with the first selector before it tries the next one
        List<ElementSelector> selectors = new ArrayList<>();
        if (!options.keyAttributes().isEmpty()) {
            selectors.add(ElementSelectors.byNameAndAttributes(options.keyAttributes().toArray(String[]::new)));
        }
        if (options.ignoreElementOrder()) {
            selectors.add(ElementSelectors.byNameAndAllAttributes);
            selectors.add(ElementSelectors.byNameAndText);
        }
        selectors.add(ElementSelectors.byName);
        DefaultNodeMatcher matcher = new DefaultNodeMatcher(selectors.toArray(ElementSelector[]::new));

        Map<Node, Node> pairs = new IdentityHashMap<>();   // matched left node → right node
        List<Comparison> found = new ArrayList<>();
        Set<Comparison> similar = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> ignored = ignoredNodes(left, right, options.ignoreXPaths());
        List<DifferenceEvaluator> evaluators = new ArrayList<>(List.of(DifferenceEvaluators.Default));
        if (options.placeholders()) {
            evaluators.add(new PlaceholderDifferenceEvaluator());
        }
        if (options.numericTolerance() > 0) {
            evaluators.add(numericTolerance(options.numericTolerance()));
        }
        DiffBuilder builder = DiffBuilder.compare(left).withTest(right)
                .withNodeMatcher(matcher)
                .checkForSimilar()
                .withDifferenceEvaluator(DifferenceEvaluators.chain(evaluators.toArray(DifferenceEvaluator[]::new)))
                .withComparisonListeners((comparison, outcome) -> {
                    Node l = comparison.getControlDetails().getTarget();
                    Node r = comparison.getTestDetails().getTarget();
                    if (l != null && r != null && l.getNodeType() == Node.ELEMENT_NODE && r.getNodeType() == Node.ELEMENT_NODE) {
                        pairs.putIfAbsent(l, r);
                    }
                    if (outcome != ComparisonResult.EQUAL && reported(comparison, outcome, options)) {
                        found.add(comparison);
                        if (outcome == ComparisonResult.SIMILAR) {
                            similar.add(comparison);
                        }
                    }
                });
        if (!ignored.isEmpty()) {
            builder.withNodeFilter(n -> NodeFilters.Default.test(n) && !ignored.contains(n))
                    .withAttributeFilter(a -> !ignored.contains(a));
        }
        builder.build();
        pairs.put(left, right);

        Map<Node, Node> reverse = new IdentityHashMap<>();
        pairs.forEach((l, r) -> reverse.putIfAbsent(r, l));
        List<Difference> result = new ArrayList<>(found.size());
        for (Comparison comparison : found) {
            Difference difference = toDifference(comparison, pairs, reverse);
            if (difference != null && similar.contains(comparison)) {
                difference = difference.asSimilar();
            }
            if (difference != null && !result.contains(difference)) {
                result.add(difference);
            }
        }
        result.sort((a, b) -> a.leftLine() != b.leftLine() ? Integer.compare(order(a.leftLine()), order(b.leftLine()))
                : Integer.compare(order(a.rightLine()), order(b.rightLine())));
        return result;
    }

    /**
     * The nodes the ignore expressions select on either document, compared by identity. Prefixes are
     * those declared on the root elements (left first); an expression that fails on one side selects
     * nothing there.
     */
    private static Set<Node> ignoredNodes(Document left, Document right, List<String> expressions) {
        Set<Node> nodes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (expressions.isEmpty()) {
            return nodes;
        }
        Map<String, String> namespaces = new java.util.LinkedHashMap<>();
        for (Document document : List.of(left, right)) {
            Element root = document.getDocumentElement();
            if (root == null) {
                continue;
            }
            var attributes = root.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
                        && attribute.getPrefix() != null) {
                    namespaces.putIfAbsent(attribute.getLocalName(), attribute.getNodeValue());
                }
            }
        }
        XPath xpath = XPATHS.newXPath();
        xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public java.util.Iterator<String> getPrefixes(String uri) {
                return java.util.Collections.emptyIterator();
            }
        });
        for (String expression : expressions) {
            for (Document document : List.of(left, right)) {
                try {
                    org.w3c.dom.NodeList list = (org.w3c.dom.NodeList) xpath.evaluate(expression, document,
                            XPathConstants.NODESET);
                    for (int i = 0; i < list.getLength(); i++) {
                        nodes.add(list.item(i));
                    }
                } catch (XPathExpressionException e) {
                    // checked up front by Options.invalidIgnoreXPath; a prefix unknown here selects nothing
                }
            }
        }
        return nodes;
    }

    /** Text and attribute values that are both numbers and differ by at most {@code tolerance} are equal. */
    private static DifferenceEvaluator numericTolerance(double tolerance) {
        BigDecimal limit = BigDecimal.valueOf(tolerance);
        return (comparison, outcome) -> {
            if (outcome == ComparisonResult.EQUAL) {
                return outcome;
            }
            ComparisonType type = comparison.getType();
            if (type != ComparisonType.TEXT_VALUE && type != ComparisonType.ATTR_VALUE) {
                return outcome;
            }
            BigDecimal a = number(comparison.getControlDetails().getValue());
            BigDecimal b = number(comparison.getTestDetails().getValue());
            return a != null && b != null && a.subtract(b).abs().compareTo(limit) <= 0 ? ComparisonResult.EQUAL : outcome;
        };
    }

    private static BigDecimal number(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int order(int line) {
        return line <= 0 ? Integer.MAX_VALUE : line;
    }

    private static boolean reported(Comparison comparison, ComparisonResult outcome, Options options) {
        ComparisonType type = comparison.getType();
        return switch (type) {
            // follow-ups of a lookup difference, or nothing a user would call a change
            case CHILD_NODELIST_LENGTH, ELEMENT_NUM_ATTRIBUTES, ATTR_VALUE_EXPLICITLY_SPECIFIED, XML_ENCODING -> false;
            case NAMESPACE_PREFIX -> options.reportPrefixChanges();
            case CHILD_NODELIST_SEQUENCE -> !options.ignoreElementOrder();
            default -> outcome == ComparisonResult.DIFFERENT || outcome == ComparisonResult.SIMILAR;
        };
    }

    private static Difference toDifference(Comparison comparison, Map<Node, Node> pairs, Map<Node, Node> reverse) {
        Comparison.Detail l = comparison.getControlDetails();
        Comparison.Detail r = comparison.getTestDetails();
        Node ln = l.getTarget();
        Node rn = r.getTarget();
        return switch (comparison.getType()) {
            case CHILD_LOOKUP -> {
                if (ln == null && rn == null) {
                    yield null;
                }
                boolean added = ln == null;
                Node present = added ? rn : ln;
                Node otherParent = added ? reverse.get(present.getParentNode()) : pairs.get(present.getParentNode());
                int presentLine = line(present);
                int otherLine = line(otherParent);
                String otherXPath = added ? l.getParentXPath() : r.getParentXPath();
                String presentXPath = added ? r.getXPath() : l.getXPath();
                yield added
                        ? new Difference(Change.ADDED, kind(present), name(present), nz(otherXPath), nz(presentXPath),
                                "", value(present), otherLine, presentLine)
                        : new Difference(Change.REMOVED, kind(present), name(present), nz(presentXPath), nz(otherXPath),
                                value(present), "", presentLine, otherLine);
            }
            case ATTR_NAME_LOOKUP -> {
                // the targets are the elements; the value is the attribute's QName on the side that has it
                boolean added = l.getValue() == null;
                Object qname = added ? r.getValue() : l.getValue();
                Element owner = (Element) (added ? rn : ln);
                String attributeValue = owner == null ? "" : attributeValue(owner, qname);
                String name = qnameText(qname);
                yield new Difference(added ? Change.ADDED : Change.REMOVED, NodeKind.ATTRIBUTE, name,
                        nz(l.getXPath()), nz(r.getXPath()), added ? "" : attributeValue, added ? attributeValue : "",
                        line(ln), line(rn));
            }
            case ATTR_VALUE, SCHEMA_LOCATION, NO_NAMESPACE_SCHEMA_LOCATION -> {
                String name = ln instanceof Attr a ? a.getName() : rn instanceof Attr a ? a.getName()
                        : comparison.getType() == ComparisonType.SCHEMA_LOCATION ? "xsi:schemaLocation"
                        : comparison.getType() == ComparisonType.NO_NAMESPACE_SCHEMA_LOCATION ? "xsi:noNamespaceSchemaLocation"
                        : "?";
                yield new Difference(Change.CHANGED, NodeKind.ATTRIBUTE, name, nz(l.getXPath()), nz(r.getXPath()),
                        text(l.getValue()), text(r.getValue()), line(ln), line(rn));
            }
            case TEXT_VALUE -> new Difference(Change.CHANGED,
                    ln != null && ln.getNodeType() == Node.COMMENT_NODE ? NodeKind.COMMENT : NodeKind.TEXT,
                    ln != null ? name(ln) : "#text", nz(l.getXPath()), nz(r.getXPath()),
                    text(l.getValue()), text(r.getValue()), line(ln), line(rn));
            case ELEMENT_TAG_NAME, NODE_TYPE -> new Difference(Change.CHANGED, kind(ln != null ? ln : rn),
                    ln != null ? name(ln) : name(rn), nz(l.getXPath()), nz(r.getXPath()),
                    ln != null ? summary(ln) : text(l.getValue()), rn != null ? summary(rn) : text(r.getValue()),
                    line(ln), line(rn));
            case NAMESPACE_URI, NAMESPACE_PREFIX -> new Difference(Change.CHANGED, NodeKind.NAMESPACE,
                    ln != null ? name(ln) : "?", nz(l.getXPath()), nz(r.getXPath()),
                    text(l.getValue()), text(r.getValue()), line(ln), line(rn));
            case CHILD_NODELIST_SEQUENCE -> new Difference(Change.MOVED, kind(ln != null ? ln : rn),
                    ln != null ? name(ln) : name(rn), nz(l.getXPath()), nz(r.getXPath()),
                    "position " + text(l.getValue()), "position " + text(r.getValue()), line(ln), line(rn));
            case PROCESSING_INSTRUCTION_TARGET, PROCESSING_INSTRUCTION_DATA -> new Difference(Change.CHANGED,
                    NodeKind.PROCESSING_INSTRUCTION, ln != null ? name(ln) : name(rn), nz(l.getXPath()),
                    nz(r.getXPath()), text(l.getValue()), text(r.getValue()), line(ln), line(rn));
            default -> new Difference(Change.CHANGED, NodeKind.DOCUMENT,
                    comparison.getType().getDescription(), nz(l.getXPath()), nz(r.getXPath()),
                    text(l.getValue()), text(r.getValue()), line(ln), line(rn));
        };
    }

    private static NodeKind kind(Node node) {
        if (node == null) {
            return NodeKind.DOCUMENT;
        }
        return switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> NodeKind.ELEMENT;
            case Node.ATTRIBUTE_NODE -> NodeKind.ATTRIBUTE;
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> NodeKind.TEXT;
            case Node.COMMENT_NODE -> NodeKind.COMMENT;
            case Node.PROCESSING_INSTRUCTION_NODE -> NodeKind.PROCESSING_INSTRUCTION;
            default -> NodeKind.DOCUMENT;
        };
    }

    private static String name(Node node) {
        if (node == null) {
            return "";
        }
        return switch (node.getNodeType()) {
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> "#text";
            case Node.COMMENT_NODE -> "#comment";
            case Node.DOCUMENT_NODE -> "document";
            default -> node.getNodeName();
        };
    }

    /** The value shown for a whole node: an element as its start tag, other nodes as their text. */
    private static String value(Node node) {
        return node.getNodeType() == Node.ELEMENT_NODE ? summary(node) : shorten(node.getNodeValue());
    }

    private static String summary(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return shorten(node.getNodeValue());
        }
        StringBuilder tag = new StringBuilder("<").append(node.getNodeName());
        var attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            tag.append(' ').append(attribute.getNodeName()).append("=\"").append(attribute.getNodeValue()).append('"');
        }
        String text = node.getTextContent();
        boolean hasChildren = node.hasChildNodes();
        tag.append(hasChildren ? ">" : "/>");
        if (hasChildren && node.getChildNodes().getLength() == 1 && text != null) {
            tag.append(text).append("</").append(node.getNodeName()).append('>');
        } else if (hasChildren) {
            tag.append('…');
        }
        return shorten(tag.toString());
    }

    private static String attributeValue(Element owner, Object qname) {
        if (qname instanceof javax.xml.namespace.QName q) {
            String value = q.getNamespaceURI() == null || q.getNamespaceURI().isEmpty()
                    ? owner.getAttribute(q.getLocalPart()) : owner.getAttributeNS(q.getNamespaceURI(), q.getLocalPart());
            return shorten(value);
        }
        return "";
    }

    private static String qnameText(Object qname) {
        if (qname instanceof javax.xml.namespace.QName q) {
            return q.getPrefix() == null || q.getPrefix().isEmpty() ? q.getLocalPart() : q.getPrefix() + ":" + q.getLocalPart();
        }
        return String.valueOf(qname);
    }

    private static int line(Node node) {
        for (Node n = node; n != null; n = n instanceof Attr a ? a.getOwnerElement() : n.getParentNode()) {
            Object line = n.getUserData(LINE);
            if (line instanceof Integer number) {
                return number;
            }
        }
        return 0;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof javax.xml.namespace.QName) {
            return qnameText(value);
        }
        return shorten(String.valueOf(value));
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return oneLine.length() > MAX_VALUE_LENGTH ? oneLine.substring(0, MAX_VALUE_LENGTH) + "…" : oneLine;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    // ---- parsing ------------------------------------------------------------------------------

    /** Parses with line numbers on every node, without DTDs from outside, normalized as the options say. */
    static Document parse(String xml, Options options) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        Document document = domFactory.newDocumentBuilder().newDocument();
        var factory = SecureXmlFactory.createSecureSAXParserFactory();
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
        SAXParser parser = factory.newSAXParser();
        DomBuilder handler = new DomBuilder(document, options);
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        parser.parse(new InputSource(new StringReader(xml == null ? "" : xml)), handler);
        return document;
    }

    /** Builds a DOM from SAX events, recording the line of every node. */
    private static final class DomBuilder extends DefaultHandler2 {
        private final Document document;
        private final Options options;
        private final Deque<Node> open = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();
        private int textLine;
        private Locator locator;
        private boolean inDtd;

        DomBuilder(Document document, Options options) {
            this.document = document;
            this.options = options;
            open.push(document);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private int currentLine() {
            return locator == null ? 0 : locator.getLineNumber();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            flushText();
            Element element = document.createElementNS(uri == null || uri.isEmpty() ? null : uri, qName);
            for (int i = 0; i < attributes.getLength(); i++) {
                String attributeName = attributes.getQName(i);
                String attributeUri = attributes.getURI(i);
                if (attributeName.equals("xmlns") || attributeName.startsWith("xmlns:")) {
                    attributeUri = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
                }
                element.setAttributeNS(attributeUri == null || attributeUri.isEmpty() ? null : attributeUri,
                        attributeName, attributes.getValue(i));
            }
            element.setUserData(LINE, currentLine(), null);
            open.peek().appendChild(element);
            open.push(element);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            flushText();
            open.pop();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inDtd || open.peek() == document) {
                return;
            }
            if (text.isEmpty()) {
                int newlines = 0;
                for (int i = start; i < start + length; i++) {
                    if (ch[i] == '\n') {
                        newlines++;
                    }
                }
                textLine = Math.max(1, currentLine() - newlines);
            }
            text.append(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
            characters(ch, start, length);
        }

        private void flushText() {
            if (text.isEmpty()) {
                return;
            }
            String value = text.toString();
            text.setLength(0);
            if (options.normalizeWhitespace()) {
                value = value.strip().replaceAll("\\s+", " ");
                if (value.isEmpty()) {
                    return;
                }
            }
            Node node = document.createTextNode(value);
            node.setUserData(LINE, textLine, null);
            open.peek().appendChild(node);
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            if (inDtd || options.ignoreComments()) {
                return;
            }
            flushText();
            Node node = document.createComment(new String(ch, start, length));
            node.setUserData(LINE, currentLine(), null);
            open.peek().appendChild(node);
        }

        @Override
        public void processingInstruction(String target, String data) {
            flushText();
            Node node = document.createProcessingInstruction(target, data);
            node.setUserData(LINE, currentLine(), null);
            open.peek().appendChild(node);
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) {
            inDtd = true;
        }

        @Override
        public void endDTD() {
            inDtd = false;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }

    private static String message(Exception e) {
        if (e instanceof SAXParseException p) {
            return "Not well-formed (line " + p.getLineNumber() + ", column " + p.getColumnNumber() + "): "
                    + Objects.requireNonNullElse(p.getMessage(), "");
        }
        return Objects.requireNonNullElse(e.getMessage(), e.toString());
    }

    /** @return why {@code xml} is not well-formed (with line and column), or {@code null} when it is */
    public static String wellFormednessError(String xml) {
        try {
            parse(xml, Options.DEFAULTS);
            return null;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return message(e);
        }
    }

    /** @return whether the text looks like XML (starts with {@code <} after whitespace and a BOM) */
    public static boolean looksLikeXml(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '﻿' || Character.isWhitespace(c)) {
                continue;
            }
            return c == '<';
        }
        return false;
    }

    /** @return the name of a change for tables ("Added", …) */
    public static String label(Change change) {
        String name = change.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
