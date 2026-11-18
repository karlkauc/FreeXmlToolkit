package org.fxt.freexmltoolkit.service.query;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * The namespaces a document's root element declares and is in, read up to the root's start tag only, and the
 * bindings a query on the document gets ({@link #forQueries}).
 */
public final class DocumentNamespaces {

    /** Elements read for {@link #forQueries} (declarations deeper in a large document are rare). */
    static final int SCAN_ELEMENTS = 2_000;

    /** Prefixes a query keeps for their standard meaning, whatever the document binds them to. */
    private static final Map<String, String> RESERVED = Map.of(
            "xml", "http://www.w3.org/XML/1998/namespace",
            "xs", "http://www.w3.org/2001/XMLSchema",
            "xsi", "http://www.w3.org/2001/XMLSchema-instance",
            "fn", "http://www.w3.org/2005/xpath-functions",
            "local", "http://www.w3.org/2005/xquery-local-functions",
            "map", "http://www.w3.org/2005/xpath-functions/map",
            "array", "http://www.w3.org/2005/xpath-functions/array",
            "math", "http://www.w3.org/2005/xpath-functions/math",
            "err", "http://www.w3.org/2005/xqt-errors");

    /**
     * What a query on a document gets declared.
     *
     * @param prefixes         prefix → URI of the prefixes declared in the document (first declaration wins)
     * @param defaultNamespace the root element's namespace when it has no prefix, or {@code null}
     */
    public record Bindings(Map<String, String> prefixes, String defaultNamespace) {
        /** No document, nothing declared. */
        public static final Bindings NONE = new Bindings(Map.of(), null);
    }

    private DocumentNamespaces() {
    }

    /**
     * The prefixes the document declares in its first {@link #SCAN_ELEMENTS} elements (so {@code //fx:Fund} works
     * without a {@code declare namespace}) and its default namespace. A prefix a query reserves ({@code xs},
     * {@code fn}, …) is kept only with its standard URI. A document that stops being well-formed gives what was
     * read until then.
     */
    public static Bindings forQueries(String xml) {
        if (xml == null || xml.isBlank()) {
            return Bindings.NONE;
        }
        Map<String, String> prefixes = new LinkedHashMap<>();
        String defaultNamespace = null;
        XMLStreamReader r = null;
        try {
            r = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(new StringReader(xml));
            int elements = 0;
            while (r.hasNext() && elements < SCAN_ELEMENTS) {
                if (r.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                if (elements++ == 0) {
                    String prefix = r.getPrefix();
                    String uri = r.getNamespaceURI();
                    if ((prefix == null || prefix.isEmpty()) && uri != null && !uri.isEmpty()) {
                        defaultNamespace = uri;
                    }
                }
                for (int i = 0; i < r.getNamespaceCount(); i++) {
                    String prefix = r.getNamespacePrefix(i);
                    String uri = r.getNamespaceURI(i);
                    if (prefix == null || prefix.isEmpty() || uri == null || uri.isEmpty()) {
                        continue;
                    }
                    String reserved = RESERVED.get(prefix);
                    if (reserved == null || reserved.equals(uri)) {
                        prefixes.putIfAbsent(prefix, uri);
                    }
                }
            }
        } catch (Exception e) {
            // not XML or broken further down: what was read so far
        } finally {
            if (r != null) {
                close(r);
            }
        }
        return new Bindings(Map.copyOf(prefixes), defaultNamespace);
    }

    /** @return prefix → URI of the root element's declarations ({@code ""} = default namespace); empty on failure */
    public static Map<String, String> declared(String xml) {
        Map<String, String> out = new LinkedHashMap<>();
        XMLStreamReader r = root(xml);
        if (r != null) {
            try {
                for (int i = 0; i < r.getNamespaceCount(); i++) {
                    String prefix = r.getNamespacePrefix(i);
                    out.put(prefix == null ? "" : prefix, r.getNamespaceURI(i));
                }
            } finally {
                close(r);
            }
        }
        return out;
    }

    /** @return the namespace URI of the root element ({@code ""} for none), or {@code null} when unreadable */
    public static String rootNamespace(String xml) {
        XMLStreamReader r = root(xml);
        if (r == null) {
            return null;
        }
        try {
            String uri = r.getNamespaceURI();
            return uri == null ? "" : uri;
        } finally {
            close(r);
        }
    }

    private static XMLStreamReader root(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            XMLStreamReader r = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(new StringReader(xml));
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT) {
                    return r;
                }
            }
            close(r);
        } catch (Exception e) {
            // not XML (JSON, broken text): no namespaces
        }
        return null;
    }

    private static void close(XMLStreamReader r) {
        try {
            r.close();
        } catch (Exception ignored) {
            // nothing to release
        }
    }
}
