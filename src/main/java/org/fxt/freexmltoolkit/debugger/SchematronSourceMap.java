package org.fxt.freexmltoolkit.debugger;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Maps the lines of the XSLT that SchXslt compiles from a Schematron back to the {@code .sch}
 * source. The compiled stylesheet carries no source locations, but keeps the structure:
 * per {@code sch:rule} one {@code xsl:template match="<context>"}, per {@code sch:assert} /
 * {@code sch:report} one {@code xsl:if} wrapping {@code svrl:failed-assert} /
 * {@code svrl:successful-report} whose {@code <xsl:attribute name="test">} holds the original
 * test, per {@code sch:let} an {@code xsl:variable} of the same name, and {@code xsl:function}s
 * copied verbatim. Both documents are walked in document order and paired on that structure;
 * a pair whose text does not agree is skipped, so a changed compiler shape leaves lines
 * unmapped instead of mapping them wrongly.
 *
 * <p>Lines are those Saxon reports: the line where an element's start tag ends. Compiled lines
 * without a counterpart (SchXslt's own driver templates, SVRL bookkeeping) map to {@code -1},
 * which the debugger never stops at.</p>
 */
public final class SchematronSourceMap implements SourceMapping {

    private static final Logger logger = LogManager.getLogger(SchematronSourceMap.class);

    static final String SCH = "http://purl.oclc.org/dsdl/schematron";
    static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    static final String SVRL = "http://purl.oclc.org/dsdl/svrl";
    /** Nested {@code sch:include}s deeper than this are ignored. */
    private static final int MAX_DEPTH = 10;

    private static final QName CONTEXT = new QName("context");
    private static final QName TEST = new QName("test");
    private static final QName NAME = new QName("name");
    private static final QName HREF = new QName("href");
    private static final QName MATCH = new QName("match");
    private static final QName MODE = new QName("mode");
    private static final QName ABSTRACT = new QName("abstract");

    /** What a Schematron element is. */
    public enum Kind { RULE, ASSERT, REPORT, LET, FUNCTION }

    /**
     * One mapped Schematron element: where it is ({@code file:} URI, start-tag end line), its key
     * text (rule context, test, let / function name) and the rule it belongs to ({@code -1} outside).
     */
    public record SchItem(Kind kind, String systemId, int line, String key, int rule, XdmNode node) {
        /** @return the source file as a normalized path */
        public String path() {
            return SourceLocations.normalize(systemId);
        }
    }

    private final String compiledPath;
    private final String compiledSystemId;
    /** Per compiled line (1-based): the index into {@link #items}, or -1. */
    private final int[] lineToItem;
    private final List<SchItem> items;

    private SchematronSourceMap(String compiledSystemId, int[] lineToItem, List<SchItem> items) {
        this.compiledSystemId = compiledSystemId;
        this.compiledPath = SourceLocations.normalize(compiledSystemId);
        this.lineToItem = lineToItem;
        this.items = List.copyOf(items);
    }

    /**
     * Builds the map.
     *
     * @param processor        parses both documents (with line numbers)
     * @param compiledXslt     the compiled stylesheet's text, exactly as it is run
     * @param compiledSystemId the system id the compiled stylesheet runs under
     * @param schSystemId      the main Schematron's {@code file:} URI
     * @param texts            the text of a Schematron module by normalized path ({@code null} = read the file)
     */
    public static SchematronSourceMap build(Processor processor, String compiledXslt, String compiledSystemId,
                                            String schSystemId, Function<String, String> texts) {
        DocumentBuilder builder = processor.newDocumentBuilder();
        builder.setLineNumbering(true);
        List<SchItem> items = new ArrayList<>();
        try {
            XdmNode sch = parse(builder, schSystemId, texts);
            if (sch != null) {
                collect(builder, element(sch), schSystemId, texts, items, -1, 0);
            }
        } catch (SaxonApiException | RuntimeException e) {
            logger.debug("Cannot read the Schematron {}: {}", schSystemId, e.getMessage());
        }
        int[] lineToItem;
        try {
            builder.setBaseURI(URI.create(compiledSystemId));
            XdmNode compiled = builder.build(new StreamSource(new StringReader(compiledXslt), compiledSystemId));
            lineToItem = new int[countLines(compiledXslt) + 2];
            java.util.Arrays.fill(lineToItem, -1);
            new Pairing(items, lineToItem).walkStylesheet(element(compiled));
        } catch (SaxonApiException | RuntimeException e) {
            logger.debug("Cannot read the compiled Schematron: {}", e.getMessage());
            lineToItem = new int[0];
        }
        return new SchematronSourceMap(compiledSystemId, lineToItem, items);
    }

    @Override
    public Mapped map(String systemId, int line) {
        if (!compiledPath.equals(SourceLocations.normalize(systemId))) {
            return new Mapped(systemId, line);
        }
        SchItem item = itemAtCompiledLine(line);
        return item == null ? new Mapped(compiledSystemId, -1) : new Mapped(item.systemId(), item.line());
    }

    /** @return the Schematron element a compiled line belongs to, or {@code null} */
    public SchItem itemAtCompiledLine(int line) {
        if (line < 1 || line >= lineToItem.length || lineToItem[line] < 0) {
            return null;
        }
        return items.get(lineToItem[line]);
    }

    /** @return every Schematron element found, in document order (includes expanded) */
    public List<SchItem> items() {
        return items;
    }

    /** @return the Schematron line of the first assert / report whose test is {@code test}, or {@code null} */
    public SchItem findTest(String test) {
        String key = normalizeSpace(test);
        for (SchItem item : items) {
            if ((item.kind() == Kind.ASSERT || item.kind() == Kind.REPORT) && item.key().equals(key)) {
                return item;
            }
        }
        return null;
    }

    /** @return how many compiled lines map to {@code item} (0: it is not mapped) */
    public int mappedLines(SchItem item) {
        int index = items.indexOf(item);
        int n = 0;
        for (int i : lineToItem) {
            if (i == index && index >= 0) {
                n++;
            }
        }
        return n;
    }

    // ---- the Schematron side ----

    private static XdmNode parse(DocumentBuilder builder, String systemId, Function<String, String> texts)
            throws SaxonApiException {
        String text = texts == null ? null : texts.apply(SourceLocations.normalize(systemId));
        if (text != null) {
            return builder.build(new StreamSource(new StringReader(text), systemId));
        }
        return builder.build(new StreamSource(systemId));
    }

    private static void collect(DocumentBuilder builder, XdmNode element, String systemId,
                                Function<String, String> texts, List<SchItem> items, int rule, int depth) {
        if (element == null) {
            return;
        }
        String ns = element.getNodeName().getNamespace();
        String local = element.getNodeName().getLocalName();
        int currentRule = rule;
        if (SCH.equals(ns)) {
            switch (local) {
                case "include", "extends" -> {
                    String href = element.getAttributeValue(HREF);
                    if (href != null && !href.isBlank() && depth < MAX_DEPTH) {
                        expand(builder, element, href, texts, items, rule, depth);
                    }
                    return;
                }
                case "rule" -> {
                    if (!"true".equals(element.getAttributeValue(ABSTRACT))) {
                        items.add(item(Kind.RULE, element, systemId, element.getAttributeValue(CONTEXT), -1));
                        currentRule = items.size() - 1;
                    }
                }
                case "assert", "report" -> items.add(item("assert".equals(local) ? Kind.ASSERT : Kind.REPORT,
                        element, systemId, element.getAttributeValue(TEST), rule));
                case "let" -> items.add(item(Kind.LET, element, systemId, element.getAttributeValue(NAME), rule));
                default -> {
                    // pattern, schema, phase, …: only their children matter
                }
            }
        } else if (XSL.equals(ns) && "function".equals(local)) {
            items.add(item(Kind.FUNCTION, element, systemId, element.getAttributeValue(NAME), -1));
            return;
        }
        for (XdmNode child : element.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                collect(builder, child, systemId, texts, items, currentRule, depth);
            }
        }
    }

    private static void expand(DocumentBuilder builder, XdmNode at, String href, Function<String, String> texts,
                               List<SchItem> items, int rule, int depth) {
        try {
            URI base = at.getBaseURI();
            String target = (base == null ? URI.create(href) : base.resolve(href)).toString();
            XdmNode doc = parse(builder, target, texts);
            collect(builder, element(doc), target, texts, items, rule, depth + 1);
        } catch (SaxonApiException | RuntimeException e) {
            logger.debug("Cannot follow sch:include {}: {}", href, e.getMessage());
        }
    }

    private static SchItem item(Kind kind, XdmNode element, String systemId, String key, int rule) {
        return new SchItem(kind, systemId, element.getLineNumber(), normalizeSpace(key), rule, element);
    }

    // ---- the compiled side ----

    /** Pairs compiled elements with Schematron items and fills the line table. */
    private static final class Pairing {
        private final List<SchItem> items;
        private final int[] lineToItem;
        private final boolean[] used;

        Pairing(List<SchItem> items, int[] lineToItem) {
            this.items = items;
            this.lineToItem = lineToItem;
            this.used = new boolean[items.size()];
        }

        void walkStylesheet(XdmNode stylesheet) {
            if (stylesheet == null) {
                return;
            }
            for (XdmNode child : elements(stylesheet)) {
                String local = child.getNodeName().getLocalName();
                if (!XSL.equals(child.getNodeName().getNamespace())) {
                    continue;
                }
                switch (local) {
                    case "template" -> template(child);
                    case "variable", "param" -> variable(child, -1);
                    case "function" -> function(child);
                    default -> {
                        // output, key, … : generated or not executed as instructions
                    }
                }
            }
        }

        /** A rule template: {@code match} = the rule's context, in a pattern's mode (not {@code #all}). */
        private void template(XdmNode template) {
            String match = template.getAttributeValue(MATCH);
            String mode = template.getAttributeValue(MODE);
            if (match == null || mode == null || mode.contains("#all")) {
                return;
            }
            int rule = next(Kind.RULE, normalizeSpace(match), -1);
            if (rule < 0) {
                return;
            }
            mark(template.getLineNumber(), template.getLineNumber(), rule);
            walkRuleBody(template, rule);
        }

        private void walkRuleBody(XdmNode parent, int rule) {
            for (XdmNode child : elements(parent)) {
                boolean xsl = XSL.equals(child.getNodeName().getNamespace());
                String local = child.getNodeName().getLocalName();
                if (xsl && "if".equals(local) && check(child, rule)) {
                    continue;
                }
                if (xsl && "variable".equals(local) && variable(child, rule)) {
                    continue;
                }
                if (xsl || "rule".equals(local) && !SVRL.equals(child.getNodeName().getNamespace())) {
                    walkRuleBody(child, rule); // choose / otherwise / schxslt:rule wrap the checks
                }
            }
        }

        /** An {@code xsl:if} holding a failed-assert / successful-report: pairs it with its assert / report. */
        private boolean check(XdmNode ifElement, int rule) {
            for (XdmNode svrl : elements(ifElement)) {
                if (!SVRL.equals(svrl.getNodeName().getNamespace())) {
                    continue;
                }
                String local = svrl.getNodeName().getLocalName();
                Kind kind = "failed-assert".equals(local) ? Kind.ASSERT
                        : "successful-report".equals(local) ? Kind.REPORT : null;
                if (kind == null) {
                    continue;
                }
                String test = null;
                for (XdmNode attr : elements(svrl)) {
                    if (XSL.equals(attr.getNodeName().getNamespace())
                            && "attribute".equals(attr.getNodeName().getLocalName())
                            && "test".equals(attr.getAttributeValue(NAME))) {
                        test = attr.getStringValue();
                        break;
                    }
                }
                if (test == null) {
                    test = svrl.getAttributeValue(TEST);
                }
                if (test == null) {
                    return false;
                }
                int item = next(kind, normalizeSpace(test), rule);
                if (item < 0) {
                    return false;
                }
                mark(ifElement.getLineNumber(), lastLine(ifElement), item);
                return true;
            }
            return false;
        }

        /** An {@code xsl:variable} named like a {@code sch:let} (of the rule, or global ones outside rules). */
        private boolean variable(XdmNode variable, int rule) {
            String name = variable.getAttributeValue(NAME);
            if (name == null) {
                return false;
            }
            int item = next(Kind.LET, normalizeSpace(name), rule);
            if (item < 0) {
                return false;
            }
            mark(variable.getLineNumber(), lastLine(variable), item);
            return true;
        }

        /** An {@code xsl:function} copied from the Schematron: element by element, in document order. */
        private void function(XdmNode function) {
            String name = function.getAttributeValue(NAME);
            int item = name == null ? -1 : next(Kind.FUNCTION, normalizeSpace(name), -1);
            if (item < 0) {
                return;
            }
            List<XdmNode> compiled = descendantsOrSelf(function);
            List<XdmNode> source = descendantsOrSelf(items.get(item).node());
            if (compiled.size() != source.size()) {
                mark(function.getLineNumber(), lastLine(function), item);
                return;
            }
            SchItem fn = items.get(item);
            for (int i = 0; i < compiled.size(); i++) {
                XdmNode c = compiled.get(i);
                XdmNode s = source.get(i);
                if (!c.getNodeName().equals(s.getNodeName())) {
                    mark(function.getLineNumber(), lastLine(function), item);
                    return;
                }
                int index;
                if (i == 0) {
                    index = item;
                } else {
                    items.add(new SchItem(Kind.FUNCTION, fn.systemId(), s.getLineNumber(), fn.key(), -1, s));
                    index = items.size() - 1;
                }
                mark(c.getLineNumber(), c.getLineNumber(), index);
            }
        }

        /**
         * @return the next unused item of {@code kind} with {@code key} in scope (within
         *         {@code rule} when given; lets outside rules for {@code -1}); for checks also an
         *         unused one anywhere, then a used one in scope (a rule extended twice copies the
         *         same abstract checks); else -1
         */
        private int next(Kind kind, String key, int rule) {
            boolean check = kind == Kind.ASSERT || kind == Kind.REPORT;
            int anywhere = -1;
            int reused = -1;
            for (int i = 0; i < used.length; i++) {
                SchItem item = items.get(i);
                if (item.kind() != kind || !item.key().equals(key)) {
                    continue;
                }
                boolean inScope = rule >= 0 ? item.rule() == rule : kind != Kind.LET || item.rule() < 0;
                if (inScope && !used[i]) {
                    used[i] = true;
                    return i;
                }
                if (!used[i] && anywhere < 0) {
                    anywhere = i;
                }
                if (used[i] && inScope && reused < 0) {
                    reused = i;
                }
            }
            if (check && anywhere >= 0) {
                used[anywhere] = true;
                return anywhere;
            }
            return check ? reused : -1;
        }

        private void mark(int from, int to, int item) {
            for (int line = Math.max(1, from); line <= to && line < lineToItem.length; line++) {
                lineToItem[line] = item;
            }
        }
    }

    // ---- helpers ----

    private static XdmNode element(XdmNode node) {
        if (node == null) {
            return null;
        }
        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            return node;
        }
        for (XdmNode child : node.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return null;
    }

    private static List<XdmNode> elements(XdmNode parent) {
        List<XdmNode> list = new ArrayList<>();
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                list.add(child);
            }
        }
        return list;
    }

    private static List<XdmNode> descendantsOrSelf(XdmNode element) {
        List<XdmNode> list = new ArrayList<>();
        list.add(element);
        for (XdmNode child : elements(element)) {
            list.addAll(descendantsOrSelf(child));
        }
        return list;
    }

    /** @return the largest start-tag line within {@code element} (its last descendant's) */
    private static int lastLine(XdmNode element) {
        int last = element.getLineNumber();
        for (XdmNode child : elements(element)) {
            last = Math.max(last, lastLine(child));
        }
        return last;
    }

    private static int countLines(String text) {
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    static String normalizeSpace(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    /** For tests: a compact view of the line table ({@code compiledLine -> schLine}). */
    Map<Integer, Integer> lineTable() {
        Map<Integer, Integer> table = new HashMap<>();
        for (int line = 1; line < lineToItem.length; line++) {
            if (lineToItem[line] >= 0) {
                table.put(line, items.get(lineToItem[line]).line());
            }
        }
        return table;
    }
}
