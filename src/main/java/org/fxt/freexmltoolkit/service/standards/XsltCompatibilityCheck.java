package org.fxt.freexmltoolkit.service.standards;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;

/**
 * Checks that a stylesheet uses only what XSLT 2.0 has, before it runs on Saxon (an XSLT 3.0 processor that
 * would accept the newer constructs silently). Reported are:
 * <ul>
 *   <li>XSLT 3.0 instructions and declarations ({@code xsl:iterate}, {@code xsl:try}, {@code xsl:mode}, …);</li>
 *   <li>XSLT 3.0 attributes on XSLT elements ({@code expand-text}, {@code visibility}, {@code static},
 *       shadow attributes {@code _name}, …) and a declared {@code version} above 2.0;</li>
 *   <li>every XPath expression, pattern and attribute value template compiled as XPath 2.0 (no
 *       {@code ||}, {@code !}, {@code let}, maps, inline functions …), and calls of functions XPath 2.0 /
 *       XSLT 2.0 does not have.</li>
 * </ul>
 * Included and imported modules are checked too when the stylesheet has a {@code file:} system id.
 */
public final class XsltCompatibilityCheck {

    static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    private static final Processor PROCESSOR = new Processor(false);
    private static final int MAX_MODULES = 50;

    private static final Set<String> XSLT30_ELEMENTS = Set.of("accept", "accumulator", "accumulator-rule",
            "array", "array-member", "assert", "break", "catch", "context-item", "evaluate", "expose", "fork",
            "global-context-item", "iterate", "map", "map-entry", "merge", "merge-action", "merge-key",
            "merge-source", "mode", "next-iteration", "on-completion", "on-empty", "on-non-empty", "override",
            "package", "source-document", "try", "use-package", "where-populated");

    private static final Set<String> XSLT30_ATTRIBUTES = Set.of("expand-text", "visibility", "streamable",
            "static", "on-no-match", "on-multiple-match", "warning-on-no-match", "warning-on-multiple-match",
            "use-accumulators", "declared-modes", "package-version", "composite", "cache", "new-each-time",
            "override-extension-function", "item-separator", "html-version", "json-node-output-method",
            "parameter-document", "build-tree", "allow-duplicate-names", "default-mode", "on-empty",
            "typed", "start-at", "context-item", "for-each-item", "for-each-source", "sort-before-merge",
            "use-character-maps-for-attributes");

    /** XSLT 2.0 functions that are no XPath 2.0 functions (or moved into XPath only in 3.0). */
    private static final Set<String> XSLT20_FUNCTIONS = Set.of("current", "document", "key", "format-number",
            "format-date", "format-dateTime", "format-time", "unparsed-text", "unparsed-text-available",
            "unparsed-entity-uri", "unparsed-entity-public-id", "generate-id", "system-property",
            "element-available", "function-available", "type-available", "current-group", "current-grouping-key",
            "regex-group");

    private static final Set<String> XSLT30_FUNCTIONS = Set.of("current-merge-group", "current-merge-key",
            "accumulator-before", "accumulator-after", "copy-of", "snapshot", "stream-available",
            "current-output-uri", "available-system-properties");

    /** XPath 3.x syntax, scanned when the 2.0 compile stopped early (string literals masked). */
    private static final Map<java.util.regex.Pattern, String> XPATH3_SYNTAX = Map.of(
            java.util.regex.Pattern.compile("\\|\\|"), "The || operator",
            java.util.regex.Pattern.compile("=>"), "The arrow operator =>",
            java.util.regex.Pattern.compile("(?<![!=<>])!(?!=)"), "The simple map operator !",
            java.util.regex.Pattern.compile("\\blet\\s+\\$"), "let",
            java.util.regex.Pattern.compile("\\b(?:map|array)\\s*\\{"), "A map / array constructor",
            java.util.regex.Pattern.compile("\\bfunction\\s*\\("), "An inline function");

    /** Attributes that hold an XPath expression or pattern, per XSLT element ({@code *} = any). */
    private static final Map<String, Set<String>> EXPRESSION_ATTRIBUTES = Map.ofEntries(
            Map.entry("*", Set.of("select", "test", "match", "group-by", "group-adjacent", "group-starting-with",
                    "group-ending-with", "use", "value", "count", "from")));

    /** XSLT attributes that are attribute value templates. */
    private static final Map<String, Set<String>> AVT_ATTRIBUTES = Map.of(
            "element", Set.of("name", "namespace"),
            "attribute", Set.of("name", "namespace", "separator"),
            "processing-instruction", Set.of("name"),
            "namespace", Set.of("name"),
            "result-document", Set.of("href", "format", "method"),
            "number", Set.of("format", "lang", "letter-value", "ordinal", "grouping-separator", "grouping-size"),
            "sort", Set.of("lang", "order", "collation", "case-order", "data-type"));

    private XsltCompatibilityCheck() {
    }

    /**
     * The XSLT 3.0 / XPath 3.x constructs {@code stylesheet} and its local modules use; empty for
     * {@link XsltVersion#V3_0} (1.0 is checked by the real XSLT 1.0 processor, not here).
     *
     * @param systemId the stylesheet's URI (to find includes / imports), or {@code null}
     */
    public static List<CompatibilityIssue> check(String stylesheet, String systemId, XsltVersion target) {
        if (stylesheet == null || target != XsltVersion.V2_0) {
            return List.of();
        }
        List<CompatibilityIssue> issues = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        if (systemId != null) {
            visited.add(normalize(systemId));
        }
        checkModule(stylesheet, systemId, null, visited, issues);
        return issues;
    }

    /** One line per issue, for an error message. */
    public static String format(List<CompatibilityIssue> issues) {
        return XQueryCompatibilityCheck.format(issues, XsltVersion.V2_0.label());
    }

    private static void checkModule(String text, String systemId, String moduleLabel, Set<String> visited,
                                    List<CompatibilityIssue> issues) {
        List<String> modules = new ArrayList<>();
        try {
            XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text));
            boolean root = true;
            java.util.Deque<Map<String, String>> scopes = new java.util.ArrayDeque<>();
            scopes.push(Map.of());
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    scopes.pop();
                    continue;
                }
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                int line = reader.getLocation().getLineNumber();
                boolean xsl = XSL.equals(reader.getNamespaceURI());
                String local = reader.getLocalName();
                Map<String, String> namespaces = new LinkedHashMap<>(scopes.peek());
                for (int i = 0; i < reader.getNamespaceCount(); i++) {
                    String prefix = reader.getNamespacePrefix(i);
                    if (prefix != null && !prefix.isEmpty()) {
                        namespaces.put(prefix, reader.getNamespaceURI(i));
                    }
                }
                scopes.push(namespaces);
                if (xsl) {
                    if (root && reader.getAttributeValue(null, "version") != null) {
                        checkVersion(reader.getAttributeValue(null, "version"), line, issues, moduleLabel);
                    }
                    if (XSLT30_ELEMENTS.contains(local)) {
                        issues.add(issue(line, "FXT-COMPAT-INSTRUCTION", "xsl:" + local
                                + " is not available in XSLT 2.0 (introduced in XSLT 3.0).", moduleLabel));
                    }
                    if (("include".equals(local) || "import".equals(local))
                            && reader.getAttributeValue(null, "href") != null) {
                        modules.add(reader.getAttributeValue(null, "href"));
                    }
                }
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    String attrNs = reader.getAttributeNamespace(i);
                    String name = reader.getAttributeLocalName(i);
                    String value = reader.getAttributeValue(i);
                    boolean plain = attrNs == null || attrNs.isEmpty();
                    if (xsl && plain) {
                        if (name.startsWith("_")) {
                            issues.add(issue(line, "FXT-COMPAT-ATTRIBUTE", "Shadow attribute " + name + " on xsl:"
                                    + local + " is not available in XSLT 2.0 (introduced in XSLT 3.0).", moduleLabel));
                            continue;
                        }
                        if (XSLT30_ATTRIBUTES.contains(name)) {
                            issues.add(issue(line, "FXT-COMPAT-ATTRIBUTE", "The attribute " + name + " on xsl:"
                                    + local + " is not available in XSLT 2.0 (introduced in XSLT 3.0).", moduleLabel));
                            continue;
                        }
                        if (EXPRESSION_ATTRIBUTES.get("*").contains(name) && !isPlainValue(local, name)) {
                            checkExpression(value, namespaces, line, local, name, issues, moduleLabel);
                        } else if (AVT_ATTRIBUTES.getOrDefault(local, Set.of()).contains(name)) {
                            checkAvt(value, namespaces, line, local, name, issues, moduleLabel);
                        }
                    } else if (!xsl && plain) {
                        checkAvt(value, namespaces, line, local, name, issues, moduleLabel);
                    } else if (XSL.equals(attrNs) && XSLT30_ATTRIBUTES.contains(name)) {
                        issues.add(issue(line, "FXT-COMPAT-ATTRIBUTE", "xsl:" + name
                                + " is not available in XSLT 2.0 (introduced in XSLT 3.0).", moduleLabel));
                    }
                }
                root = false;
            }
        } catch (XMLStreamException e) {
            // not well-formed: the compile reports it with full detail
            return;
        }
        for (String href : modules) {
            checkIncluded(href, systemId, visited, issues);
        }
    }

    private static void checkIncluded(String href, String baseSystemId, Set<String> visited,
                                      List<CompatibilityIssue> issues) {
        if (baseSystemId == null || visited.size() >= MAX_MODULES) {
            return;
        }
        try {
            URI uri = URI.create(baseSystemId).resolve(href);
            if (!"file".equalsIgnoreCase(uri.getScheme()) || !visited.add(normalize(uri.toString()))) {
                return;
            }
            Path path = Path.of(uri);
            if (!Files.isRegularFile(path)) {
                return;
            }
            checkModule(Files.readString(path), uri.toString(), path.getFileName().toString(), visited, issues);
        } catch (Exception e) {
            // an unreadable module is the compile's to report
        }
    }

    private static void checkVersion(String version, int line, List<CompatibilityIssue> issues, String module) {
        try {
            if (Double.parseDouble(version.strip()) > 2.0) {
                issues.add(issue(line, "FXT-COMPAT-VERSION", "The stylesheet declares version=\"" + version.strip()
                        + "\" but XSLT 2.0 is selected.", module));
            }
        } catch (NumberFormatException e) {
            // an invalid version is the compiler's to report
        }
    }

    /** Expression-named attributes that are plain values on this element (e.g. xsl:number/@value is XPath). */
    private static boolean isPlainValue(String element, String attribute) {
        return switch (attribute) {
            case "value" -> !"number".equals(element);
            case "count", "from" -> !"number".equals(element);
            case "use" -> !"key".equals(element);
            case "test" -> !("if".equals(element) || "when".equals(element) || "assert".equals(element));
            default -> false;
        };
    }

    private static void checkAvt(String value, Map<String, String> namespaces, int line, String element,
                                 String attribute, List<CompatibilityIssue> issues, String module) {
        int i = 0;
        int n = value.length();
        while (i < n) {
            char c = value.charAt(i);
            if ((c == '{' || c == '}') && i + 1 < n && value.charAt(i + 1) == c) {
                i += 2;
            } else if (c == '{') {
                int end = value.indexOf('}', i + 1);
                if (end < 0) {
                    return;
                }
                checkExpression(value.substring(i + 1, end), namespaces, line, element, attribute, issues, module);
                i = end + 1;
            } else {
                i++;
            }
        }
    }

    /**
     * Checks one XPath expression of an XSLT 2.0 / Schematron xslt2 context as XPath 2.0 (variables, prefixes and
     * user functions are not known here and not reported; XSLT 2.0's own functions are allowed).
     *
     * @param where what the expression belongs to, prefixed to each message (e.g. {@code rule/@context})
     */
    public static List<CompatibilityIssue> checkXPath20(String expression, Map<String, String> namespaces,
                                                        int line, String where) {
        List<CompatibilityIssue> issues = new ArrayList<>();
        int slash = where.indexOf("/@");
        checkExpression(expression, namespaces, line, slash > 0 ? where.substring(0, slash) : where,
                slash > 0 ? where.substring(slash + 2) : "", issues, null);
        return issues;
    }

    private static void checkExpression(String expression, Map<String, String> namespaces, int line,
                                        String element, String attribute, List<CompatibilityIssue> issues,
                                        String module) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        String where = element + "/@" + attribute;
        XPathCompiler compiler = PROCESSOR.newXPathCompiler();
        compiler.setLanguageVersion("2.0");
        compiler.setAllowUndeclaredVariables(true);
        namespaces.forEach((prefix, uri) -> {
            if (!prefix.isEmpty()) {
                compiler.declareNamespace(prefix, uri);
            }
        });
        boolean syntaxChecked = true;
        try {
            compiler.compile(expression);
        } catch (SaxonApiException e) {
            String code = e.getErrorCode() == null ? "" : e.getErrorCode().getLocalName();
            // Saxon binds function calls while parsing: an unknown (e.g. 3.x) function stops the parse before
            // later 3.x syntax is seen, so that syntax is scanned for below
            syntaxChecked = !"XPST0017".equals(code);
            // unknown prefixes / functions: the function scan below decides about functions
            if (!"XPST0081".equals(code) && !"XPST0017".equals(code) && !"XPST0008".equals(code)
                    && !"XPDY0002".equals(code)) {
                String message = e.getMessage() == null ? "invalid XPath 2.0" : e.getMessage().strip();
                issues.add(issue(line, code.isEmpty() ? "XPST0003" : code, where + ": " + message
                        + " (XPath 2.0, as used by XSLT 2.0)", module));
            }
        }
        SourceMask mask = SourceMask.ofQuery(expression);
        if (!syntaxChecked) {
            for (var construct : XPATH3_SYNTAX.entrySet()) {
                if (construct.getKey().matcher(mask.masked()).find()) {
                    issues.add(issue(line, "XPST0003", where + ": " + construct.getValue()
                            + " is not available in XPath 2.0 (as used by XSLT 2.0).", module));
                }
            }
        }
        for (CompatibilityIssue found : FunctionCatalog.findNewerCalls(mask, XPathVersion.V2_0, "XSLT 2.0")) {
            if (XSLT20_FUNCTIONS.stream().anyMatch(f -> found.message().contains(" " + f + "#")
                    || found.message().contains(" fn:" + f + "#"))) {
                continue;
            }
            issues.add(issue(line, found.code(), where + ": " + found.message(), module));
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![\\w.$:-])([A-Za-z][\\w.-]*)\\s*\\(")
                .matcher(mask.masked());
        while (m.find()) {
            if (XSLT30_FUNCTIONS.contains(m.group(1))) {
                issues.add(issue(line, "FXT-COMPAT-FUNCTION", where + ": The function " + m.group(1)
                        + "() is not available in XSLT 2.0 (introduced in XSLT 3.0).", module));
            }
        }
    }

    private static CompatibilityIssue issue(int line, String code, String message, String module) {
        return new CompatibilityIssue(line, 0, code, message, module);
    }

    private static String normalize(String uri) {
        try {
            return URI.create(uri).normalize().toString();
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }
}
