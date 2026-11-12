package org.fxt.freexmltoolkit.service.standards;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Checks that the XPath expressions of a Schematron fit its query binding: {@code xslt} = XPath 1.0 (compiled by
 * the JDK's XPath 1.0 engine), {@code xslt2} = XPath 2.0. The Schematron engine runs every binding on an XSLT 3.0
 * processor and would accept newer expressions silently, so this is what makes a binding choice binding.
 * The schema's own {@code @queryBinding} wins over the chosen default; xslt3 is not checked.
 */
public final class SchematronCompatibilityCheck {

    private static final String SCH = "http://purl.oclc.org/dsdl/schematron";

    /** Expression attributes per Schematron element. */
    private static final Map<String, Set<String>> EXPRESSIONS = Map.of(
            "rule", Set.of("context"),
            "assert", Set.of("test"),
            "report", Set.of("test"),
            "let", Set.of("value"),
            "value-of", Set.of("select"),
            "name", Set.of("path"));

    /** XSLT functions a Schematron may call that a plain XPath 1.0 engine does not know. */
    private static final Set<String> XSLT_FUNCTIONS = Set.of("current", "key", "document", "format-number",
            "generate-id", "system-property", "element-available", "function-available", "unparsed-entity-uri");

    private SchematronCompatibilityCheck() {
    }

    /** The binding a schema declares in {@code @queryBinding}, or {@code null}. */
    public static String declaredBinding(String schematron) {
        try {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory()
                    .createXMLStreamReader(new StringReader(schematron));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    String value = reader.getAttributeValue(null, "queryBinding");
                    return value == null || value.isBlank() ? null : value.strip().toLowerCase(java.util.Locale.ROOT);
                }
            }
        } catch (XMLStreamException e) {
            // not well-formed: nothing to say here
        }
        return null;
    }

    /**
     * The expressions of {@code schematron} that do not fit the effective binding (the schema's own, else
     * {@code binding}); empty for xslt3 / unknown bindings.
     */
    public static List<CompatibilityIssue> check(String schematron, SchematronBinding binding) {
        if (schematron == null || binding == null) {
            return List.of();
        }
        String declared = declaredBinding(schematron);
        String effective = declared != null ? declared : binding.queryBinding();
        XPathVersion version = switch (effective) {
            case "xslt", "xpath" -> XPathVersion.V1_0;
            case "xslt2", "xpath2" -> XPathVersion.V2_0;
            default -> null;
        };
        if (version == null) {
            return List.of();
        }
        List<CompatibilityIssue> issues = new ArrayList<>();
        try {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory()
                    .createXMLStreamReader(new StringReader(schematron));
            Deque<Map<String, String>> scopes = new ArrayDeque<>();
            scopes.push(Map.of());
            Map<String, String> declaredNs = new LinkedHashMap<>();
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    scopes.pop();
                    continue;
                }
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                Map<String, String> namespaces = new LinkedHashMap<>(scopes.peek());
                for (int i = 0; i < reader.getNamespaceCount(); i++) {
                    String prefix = reader.getNamespacePrefix(i);
                    if (prefix != null && !prefix.isEmpty()) {
                        namespaces.put(prefix, reader.getNamespaceURI(i));
                    }
                }
                scopes.push(namespaces);
                if (!SCH.equals(reader.getNamespaceURI())) {
                    continue;
                }
                String local = reader.getLocalName();
                if ("ns".equals(local) && reader.getAttributeValue(null, "prefix") != null) {
                    declaredNs.put(reader.getAttributeValue(null, "prefix"), reader.getAttributeValue(null, "uri"));
                    continue;
                }
                for (String attribute : EXPRESSIONS.getOrDefault(local, Set.of())) {
                    String expression = reader.getAttributeValue(null, attribute);
                    if (expression == null || expression.isBlank()) {
                        continue;
                    }
                    int line = reader.getLocation().getLineNumber();
                    Map<String, String> all = new LinkedHashMap<>(namespaces);
                    all.putAll(declaredNs);
                    String where = local + "/@" + attribute;
                    if (version == XPathVersion.V1_0) {
                        check10(expression, all, line, where, issues);
                    } else {
                        issues.addAll(XsltCompatibilityCheck.checkXPath20(expression, all, line, where));
                    }
                }
            }
        } catch (XMLStreamException e) {
            // not well-formed: the compile reports it
        }
        return issues;
    }

    private static void check10(String expression, Map<String, String> namespaces, int line, String where,
                                List<CompatibilityIssue> issues) {
        try {
            Xpath10Engine.check(expression, namespaces);
        } catch (Xpath10Engine.Xpath10Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (XSLT_FUNCTIONS.stream().anyMatch(f -> message.contains(f))) {
                return;
            }
            issues.add(new CompatibilityIssue(line, 0, "XPST0003", where + ": " + message
                    + " (query binding xslt = XPath 1.0)"));
        }
    }
}
