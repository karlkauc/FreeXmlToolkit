package org.fxt.freexmltoolkit.debugger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * Evaluates XPath watch expressions against a {@link PausedSnapshot}: the paused context
 * item is the context item of the expression and every local/global variable of the
 * snapshot is available as {@code $name}.
 *
 * <p>Must be created with the processor that ran the debugged transformation (Saxon rejects
 * context nodes built by another {@code Configuration}). {@code position()} and
 * {@code last()} cannot be injected into an {@code XPathSelector}; the two literal
 * expressions are answered from the snapshot's focus instead.</p>
 */
public final class WatchEvaluator {

    /** Longest rendered watch value. */
    public static final int MAX_RENDERED_LENGTH = 2000;

    private static final Pattern XMLNS = Pattern.compile("xmlns:([A-Za-z_][\\w.-]*)\\s*=\\s*([\"'])(.*?)\\2");

    private final Processor processor;
    private final Map<String, String> namespaces;

    public WatchEvaluator(Processor processor, Map<String, String> namespaces) {
        this.processor = processor;
        this.namespaces = namespaces == null ? Map.of() : new LinkedHashMap<>(namespaces);
    }

    /**
     * Extracts the {@code xmlns:prefix} declarations of a stylesheet's root element, so that
     * watches can use the same prefixes as the stylesheet.
     */
    public static Map<String, String> namespacesOf(String xsltText) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xsltText == null) {
            return result;
        }
        int start = xsltText.indexOf("<xsl:stylesheet");
        if (start < 0) start = xsltText.indexOf("<xsl:transform");
        if (start < 0) start = xsltText.indexOf('<');
        if (start < 0) return result;
        int end = xsltText.indexOf('>', start);
        String rootTag = xsltText.substring(start, end < 0 ? xsltText.length() : end);
        Matcher m = XMLNS.matcher(rootTag);
        while (m.find()) {
            result.put(m.group(1), m.group(3));
        }
        return result;
    }

    /**
     * @return the rendered result, or an {@code ERROR: …} / {@code <not paused>} text
     */
    public String evaluate(String expression, PausedSnapshot snapshot) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        if (snapshot == null) {
            return "<not paused>";
        }
        String expr = expression.trim();
        if ("position()".equals(expr)) {
            return snapshot.position() >= 0 ? String.valueOf(snapshot.position()) : "<no focus>";
        }
        if ("last()".equals(expr)) {
            return snapshot.last() >= 0 ? String.valueOf(snapshot.last()) : "<no focus>";
        }
        try {
            XPathCompiler compiler = processor.newXPathCompiler();
            compiler.setLanguageVersion("3.1");
            compiler.declareNamespace("xsl", "http://www.w3.org/1999/XSL/Transform");
            compiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            compiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
            compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
            compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
            compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
            for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                compiler.declareNamespace(ns.getKey(), ns.getValue());
            }
            // Locals shadow globals of the same name.
            Map<QName, XdmValue> variables = new LinkedHashMap<>(snapshot.globalVariables());
            variables.putAll(snapshot.localVariables());
            for (QName name : variables.keySet()) {
                compiler.declareVariable(name);
            }
            XPathSelector selector = compiler.compile(expr).load();
            if (snapshot.contextXdm() != null) {
                selector.setContextItem(snapshot.contextXdm());
            }
            for (Map.Entry<QName, XdmValue> v : variables.entrySet()) {
                selector.setVariable(v.getKey(), v.getValue());
            }
            return render(selector.evaluate(), MAX_RENDERED_LENGTH);
        } catch (SaxonApiException e) {
            return "ERROR: " + e.getMessage();
        } catch (RuntimeException e) {
            return "ERROR: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** Renders a value for a table cell: atomic values as text, nodes serialised, sequences joined. */
    public static String render(XdmValue value, int maxLength) {
        if (value == null || value.size() == 0) {
            return "<empty sequence>";
        }
        StringBuilder sb = new StringBuilder();
        int n = value.size();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (sb.length() > maxLength) {
                break;
            }
            XdmItem item = value.itemAt(i);
            if (item instanceof XdmNode node) {
                sb.append(node.toString().strip());
            } else if (item instanceof XdmAtomicValue atomic) {
                sb.append(atomic.getStringValue());
            } else {
                sb.append(item.toString());
            }
        }
        if (n > 1) {
            sb.append("  (").append(n).append(" items)");
        }
        if (sb.length() > maxLength) {
            sb.setLength(maxLength);
            sb.append("…");
        }
        return sb.toString();
    }

    /** A short XDM type label for the Variables panel ("xs:integer", "element()", "3 items"). */
    public static String describeType(XdmValue value) {
        if (value == null || value.size() == 0) {
            return "empty-sequence()";
        }
        if (value.size() > 1) {
            return value.size() + " items";
        }
        XdmItem item = value.itemAt(0);
        if (item instanceof XdmAtomicValue atomic) {
            QName type = atomic.getTypeName();
            return type == null ? "atomic" : (type.getPrefix().isEmpty() ? "xs:" : type.getPrefix() + ":")
                    + type.getLocalName();
        }
        if (item instanceof XdmNode node) {
            return switch (node.getNodeKind()) {
                case ELEMENT -> "element(" + node.getNodeName() + ")";
                case ATTRIBUTE -> "attribute(" + node.getNodeName() + ")";
                case DOCUMENT -> "document-node()";
                case TEXT -> "text()";
                case COMMENT -> "comment()";
                case PROCESSING_INSTRUCTION -> "processing-instruction()";
                default -> "node()";
            };
        }
        return item.getClass().getSimpleName().replace("Xdm", "").toLowerCase(java.util.Locale.ROOT);
    }
}
