package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Answers whether an XSD declares an element as a global (top-level) element, i.e. whether a
 * document with that root element can be valid against it. Reads the schema with StAX and follows
 * local {@code xs:include} / {@code xs:redefine} / {@code xs:override} / {@code xs:import}
 * references (relative to the referencing file); remote locations are not loaded. Cycle-safe
 * (visited files) with a depth cap of 10. Used to decide whether a transform result may inherit
 * its input's schema.
 */
final class XsdGlobalElements {

    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final int MAX_DEPTH = 10;

    /** A schema referenced by xs:include / redefine / override (same namespace) or xs:import. */
    private record Reference(File file, boolean imported) { }

    private XsdGlobalElements() {
    }

    /**
     * @param xsd       the schema file
     * @param namespace the element's namespace ({@code null} or empty = no namespace)
     * @param localName the element's local name
     * @return {@code true} if the schema (or a locally reachable included / imported one) declares
     *         that element globally; {@code false} otherwise or when the schema cannot be read
     */
    static boolean declares(File xsd, String namespace, String localName) {
        if (xsd == null || localName == null || localName.isEmpty()) {
            return false;
        }
        String ns = namespace == null ? "" : namespace;
        return declares(xsd, null, ns, localName, new HashSet<>(), 0);
    }

    /**
     * @param chameleonNs namespace an included schema without targetNamespace takes over
     *                    ({@code null} for the root schema and imports)
     */
    private static boolean declares(File xsd, String chameleonNs, String ns, String localName,
                                    Set<String> visited, int depth) {
        if (depth > MAX_DEPTH || !xsd.isFile()) {
            return false;
        }
        String key;
        try {
            key = xsd.getCanonicalPath() + "|" + chameleonNs;
        } catch (java.io.IOException e) {
            key = xsd.getAbsolutePath() + "|" + chameleonNs;
        }
        if (!visited.add(key)) {
            return false;
        }
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        java.util.List<Reference> references = new java.util.ArrayList<>();
        String targetNs = null;
        try (InputStream in = Files.newInputStream(xsd.toPath())) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            int depthInDoc = 0;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depthInDoc++;
                    if (!XS.equals(r.getNamespaceURI())) {
                        continue;
                    }
                    String name = r.getLocalName();
                    if (depthInDoc == 1 && "schema".equals(name)) {
                        String tns = r.getAttributeValue(null, "targetNamespace");
                        targetNs = tns != null ? tns : (chameleonNs != null ? chameleonNs : "");
                    } else if (depthInDoc == 2) {
                        switch (name) {
                            case "element" -> {
                                if (localName.equals(r.getAttributeValue(null, "name")) && ns.equals(targetNs)) {
                                    r.close();
                                    return true;
                                }
                            }
                            case "include", "redefine", "override", "import" -> {
                                String location = r.getAttributeValue(null, "schemaLocation");
                                File ref = localFile(xsd, location);
                                if (ref != null) {
                                    references.add(new Reference(ref, "import".equals(name)));
                                }
                            }
                            default -> { }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depthInDoc--;
                }
            }
            r.close();
        } catch (Exception e) {
            return false; // unreadable / not a schema: cannot tell, so it does not declare it
        }
        for (Reference ref : references) {
            // an include / redefine / override without targetNamespace takes over the includer's
            if (declares(ref.file(), ref.imported() ? null : targetNs, ns, localName, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** @return the local file a schemaLocation points to (relative to {@code base}), or null for remote / missing ones */
    private static File localFile(File base, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String loc = location.trim();
        try {
            if (loc.startsWith("file:")) {
                return new File(java.net.URI.create(loc));
            }
            if (loc.matches("(?i)^[a-z][a-z0-9+.-]+://.*")) {
                return null; // http(s) and other remote schemes are not fetched here
            }
            File f = new File(loc);
            return f.isAbsolute() ? f : new File(base.getParentFile(), loc);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param xml an XML document (text)
     * @return the qualified name of its root element, or {@code null} when the text is not XML
     */
    static QName rootElement(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            XMLStreamReader r = factory.createXMLStreamReader(new StringReader(xml.stripLeading()));
            try {
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT) {
                        String uri = r.getNamespaceURI();
                        return new QName(uri == null ? "" : uri, r.getLocalName());
                    }
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
