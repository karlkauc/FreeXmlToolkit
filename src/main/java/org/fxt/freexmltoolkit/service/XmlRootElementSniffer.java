package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Cheap StAX sniffing of the document element (namespace, local name) or an XSD's targetNamespace. */
public final class XmlRootElementSniffer {

    /** Document element identity. {@code namespace} is "" when the element has none. */
    public record RootElement(String namespace, String localName) { }

    private XmlRootElementSniffer() { }

    public static Optional<RootElement> sniff(String xml) {
        if (xml == null || xml.isBlank()) return Optional.empty();
        try (Reader r = new StringReader(xml)) {
            XMLStreamReader sr = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(r);
            try {
                while (sr.hasNext()) {
                    if (sr.next() == XMLStreamConstants.START_ELEMENT) {
                        String ns = sr.getNamespaceURI();
                        return Optional.of(new RootElement(ns == null ? "" : ns, sr.getLocalName()));
                    }
                }
            } finally {
                sr.close();
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }

    /** The {@code targetNamespace} of an XSD file, empty when absent or unreadable. */
    public static Optional<String> targetNamespaceOf(Path xsd) {
        try (InputStream in = Files.newInputStream(xsd)) {
            XMLStreamReader sr = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(in);
            try {
                while (sr.hasNext()) {
                    if (sr.next() == XMLStreamConstants.START_ELEMENT) {
                        String tns = sr.getAttributeValue(null, "targetNamespace");
                        return Optional.ofNullable(tns).filter(s -> !s.isBlank());
                    }
                }
            } finally {
                sr.close();
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }
}
