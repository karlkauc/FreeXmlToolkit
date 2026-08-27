package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code xmlns} declarations of the instance document being edited, used to turn a
 * schema element's namespace URI into the prefix the document actually uses.
 *
 * <p>This is a lightweight textual scan of the text before the caret (later declarations
 * win, which approximates in-scope resolution for the well-formed prefix of a document).
 * Comments, CDATA sections and processing instructions are ignored.</p>
 */
public final class XmlNamespaceDeclarations {

    private static final Pattern XMLNS = Pattern.compile("\\sxmlns(?::([\\w.-]+))?\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    /** prefix → URI; the default namespace is stored under the empty string. */
    private final Map<String, String> prefixToUri;

    private XmlNamespaceDeclarations(Map<String, String> prefixToUri) {
        this.prefixToUri = prefixToUri;
    }

    /**
     * Scans the given XML text for namespace declarations.
     *
     * @param text the XML text (typically the text before the caret)
     * @return the declarations found (never null)
     */
    public static XmlNamespaceDeclarations scan(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return new XmlNamespaceDeclarations(map);
        }
        String cleaned = text.replaceAll("<!--[\\s\\S]*?(?:-->|$)", " ")
                .replaceAll("<!\\[CDATA\\[[\\s\\S]*?(?:]]>|$)", " ")
                .replaceAll("<\\?[\\s\\S]*?(?:\\?>|$)", " ");
        Matcher m = XMLNS.matcher(cleaned);
        while (m.find()) {
            String prefix = m.group(1) == null ? "" : m.group(1);
            String uri = m.group(2) != null ? m.group(2) : m.group(3);
            map.put(prefix, uri);
        }
        return new XmlNamespaceDeclarations(map);
    }

    /**
     * Returns the prefix the document binds to {@code namespaceUri}; the empty string means
     * the default namespace.
     */
    public Optional<String> prefixFor(String namespaceUri) {
        if (namespaceUri == null) {
            return Optional.empty();
        }
        // Prefer a prefixed binding over the default namespace only if the default does not match:
        // the default namespace is the most natural way to write the element.
        Map<String, String> uriToPrefix = new HashMap<>();
        for (Map.Entry<String, String> e : prefixToUri.entrySet()) {
            if (namespaceUri.equals(e.getValue())) {
                if (e.getKey().isEmpty()) {
                    return Optional.of("");
                }
                uriToPrefix.putIfAbsent(e.getValue(), e.getKey());
            }
        }
        return Optional.ofNullable(uriToPrefix.get(namespaceUri));
    }

    /** Whether a default namespace ({@code xmlns="..."}) is declared. */
    public boolean hasDefaultNamespace() {
        return prefixToUri.containsKey("");
    }

    /**
     * Builds the qualified name to insert for an element.
     *
     * @param localName      the element's local name
     * @param namespaceUri   the namespace the element must be in, or null if unqualified
     * @param fallbackPrefix the prefix to use when the document does not declare the namespace
     *                       (e.g. the prefix used by the schema itself), may be null
     * @return the (possibly prefixed) name
     */
    public String qualify(String localName, String namespaceUri, String fallbackPrefix) {
        if (namespaceUri == null || namespaceUri.isBlank()) {
            return localName;
        }
        Optional<String> prefix = prefixFor(namespaceUri);
        if (prefix.isPresent()) {
            return prefix.get().isEmpty() ? localName : prefix.get() + ":" + localName;
        }
        if (fallbackPrefix != null && !fallbackPrefix.isBlank()) {
            return fallbackPrefix + ":" + localName;
        }
        return localName;
    }
}
