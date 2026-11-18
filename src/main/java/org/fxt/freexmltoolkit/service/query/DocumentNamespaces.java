package org.fxt.freexmltoolkit.service.query;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/** The namespaces a document's root element declares and is in, read up to the root's start tag only. */
final class DocumentNamespaces {

    private DocumentNamespaces() {
    }

    /** @return prefix → URI of the root element's declarations ({@code ""} = default namespace); empty on failure */
    static Map<String, String> declared(String xml) {
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
    static String rootNamespace(String xml) {
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
